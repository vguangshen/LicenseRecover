#!/usr/bin/env python3
"""Regression checks for retiring the old helper's executable way-three path.

Use --require-mono --host <built-host.exe> --bridge <built-bridge.dll> in CI so
unavailable Mono/mcs or missing host artifacts are failures, never skips.
Local source inspection can run without Mono; only the native execution check
is skipped then. No real application DLL or authorization record is needed.
"""
from __future__ import annotations

import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HOST_PATH = None
BRIDGE_PATH = None


def load_script(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


disabled = load_script("disable_dotnet_way3", ROOT / "scripts/disable-dotnet-way3.py")
modern = load_script("prepare_dotnet_modern", ROOT / "scripts/prepare-dotnet-modern-helper.py")


class DisableDotNetWay3Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="licenserecover-way3-test-")
        cls.work = Path(cls.temporary.name)
        cls.source = ROOT / "LicenseRecover.NET/LicenseRecover.NET.exe"
        cls.original = cls.source.read_bytes()
        modern_path = cls.work / "Modern.exe"
        modern.patch(cls.source, modern_path)
        cls.inputs = {"original": cls.original, "modern": modern_path.read_bytes()}
        cls.outputs = {variant: disabled.disable(data)[0] for variant, data in cls.inputs.items()}

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_two_exact_supported_helpers_have_stable_output(self):
        self.assertEqual(len(disabled.DISABLED_METHODS), 17)
        for variant, data in self.inputs.items():
            with self.subTest(variant=variant):
                self.assertEqual(disabled.SOURCE_HASHES[disabled.sha256(data)], variant)
                self.assertEqual(len(data), len(self.outputs[variant]))
                self.assertEqual(disabled.sha256(self.outputs[variant]), disabled.OUTPUT_HASHES[variant])
        self.assertEqual(self.source.read_bytes(), self.original)

    def test_patch_entrypoints_return_nonzero_and_entire_writer_is_erased(self):
        for variant, data in self.inputs.items():
            image = disabled.ManagedImage(data)
            output = self.outputs[variant]
            for owner, name, rva, signature, result in disabled.DISABLED_METHODS:
                with self.subTest(variant=variant, method=name):
                    self.assertEqual(image.methods[(owner, name, signature)][0], rva)
                    start, end = image.body_range(rva)
                    stub = disabled.STUBS[result]
                    self.assertEqual(output[start:end], stub + bytes(end - start - len(stub)))
                    if name in ("PatchMode", "Patch"):
                        # ECMA-335 tiny header, ldc.i4.2, ret. This cannot succeed,
                        # invoke another method, create a file, or inspect a target.
                        self.assertEqual(stub, bytes.fromhex("0a 18 2a"))

    def test_raw_cli_rejects_patch_before_target_domain_or_restore_cleanup(self):
        for variant, data in self.inputs.items():
            image = disabled.ManagedImage(data)
            start, end = image.body_range(disabled.RUN_RVA)
            original = data[start:end]
            output = self.outputs[variant][start:end]
            changes = [i for i, (a, b) in enumerate(zip(original, output)) if a != b]
            self.assertEqual(changes, [12 + 25])
            code = output[12:]
            self.assertEqual(code[17:26], bytes.fromhex("02 7b 23 00 00 04 1a 2e 50"))
            # beq.s consumes the Mode==4 comparison; target must be ldc.i4.1;ret.
            branch_target = 26 + code[25]
            self.assertEqual(branch_target, 106)
            self.assertEqual(code[branch_target:branch_target + 2], b"\x17\x2a")

    def test_other_method_bodies_and_metadata_are_unchanged(self):
        removed = {(owner, name, signature) for owner, name, _, signature, _ in disabled.DISABLED_METHODS}
        required_preserved = {
            ("LicenseRecoverNet.FoapNet", "Scan"),
            ("LicenseRecoverNet.AppReflection", "DoRegistry"),
            ("LicenseRecoverNet.Program", "RunDirect"),
            ("LicenseRecoverNet.Program", "GenCodeMode"),
            ("LicenseRecoverNet.Program", "DoRegMode"),
            ("LicenseRecoverNet.Program", "ConfigMode"),
            ("LicenseRecoverNet.Program", "VerifyMode"),
            ("LicenseRecoverNet.FoapUnpack", "Unpack"),
            ("LicenseRecoverNet.FoapStage2", "Unpack"),
        }
        for variant, data in self.inputs.items():
            before = disabled.ManagedImage(data)
            after = disabled.ManagedImage(self.outputs[variant])
            self.assertEqual(before.methods, after.methods)
            seen = set()
            for key, (rva, _) in before.methods.items():
                if not rva or key in removed or key == disabled.RUN_KEY:
                    continue
                start, end = before.body_range(rva)
                self.assertEqual(data[start:end], self.outputs[variant][start:end], key)
                seen.add(key[:2])
            self.assertTrue(required_preserved.issubset(seen))

    def test_retained_diagnostics_do_not_recommend_the_removed_writer(self):
        for output in self.outputs.values():
            for old, new in disabled.DIAGNOSTIC_TEXT:
                self.assertNotIn(old.encode("utf-16le"), output)
                self.assertIn(new.encode("utf-16le"), output)

    def test_drift_and_repeated_transformation_are_rejected(self):
        drift = bytearray(self.original)
        drift[-1] ^= 1
        for data in (b"", bytes(drift), *self.outputs.values()):
            with self.assertRaisesRegex(ValueError, "unsupported helper SHA-256"):
                disabled.disable(data)

    def test_method_signature_and_il_mismatches_fail_closed(self):
        old = disabled.DISABLED_METHODS
        try:
            first = old[0]
            disabled.DISABLED_METHODS = ((first[0], first[1], first[2] + 1, first[3], first[4]),) + old[1:]
            with self.assertRaisesRegex(ValueError, "RVA/static flag mismatch"):
                disabled.disable(self.original)
        finally:
            disabled.DISABLED_METHODS = old
        prefix = disabled.RUN_PREFIX
        try:
            disabled.RUN_PREFIX = b"\xff" + prefix[1:]
            with self.assertRaisesRegex(ValueError, "early-failure IL changed"):
                disabled.disable(self.original)
        finally:
            disabled.RUN_PREFIX = prefix

    def test_out_of_bounds_pe_reads_are_rejected(self):
        with self.assertRaises(ValueError):
            disabled.ManagedImage(self.original[:100])
        image = disabled.ManagedImage(self.original)
        with self.assertRaises(ValueError):
            image.file_offset(0xFFFFFFFF, 4)
        with self.assertRaises(ValueError):
            image.take(len(self.original) - 1, 2)

    def test_never_overwrites_input_or_creates_output_for_an_invalid_input(self):
        source = self.work / "input-copy.exe"
        source.write_bytes(self.original)
        with self.assertRaisesRegex(ValueError, "must not be overwritten"):
            disabled.transform(source, source)
        if hasattr(os, "link"):
            alias = self.work / "input-hardlink.exe"
            os.link(source, alias)
            with self.assertRaisesRegex(ValueError, "must not be overwritten"):
                disabled.transform(source, alias)
        self.assertEqual(source.read_bytes(), self.original)
        invalid = self.work / "invalid.exe"
        invalid.write_bytes(b"not a helper")
        destination = self.work / "must-not-exist" / "bad-output.exe"
        with self.assertRaises(ValueError):
            disabled.transform(invalid, destination)
        self.assertFalse(destination.exists())
        self.assertFalse(destination.parent.exists())

    @unittest.skipUnless(shutil.which("mono") and shutil.which("mcs"), "Mono/mcs unavailable for native execution")
    def test_mono_cli_and_hosted_dispatch_reject_without_touching_target(self):
        native = self.work / "native-execution"
        native.mkdir()
        target = native / "target-bin"
        target.mkdir()
        for name in ("itmcRegedit.dll", "ITMC.Regedit.dll", "itmcRegedit.dll.restore", "config.xml", "Register.xml"):
            (target / name).write_bytes(("sentinel: " + name).encode())
        snapshot = {p.name: p.read_bytes() for p in target.iterdir()}
        shutil.copy2(ROOT / "LicenseRecover.NET/dnlib.dll", native / "dnlib.dll")
        # Test RunDirect too: ASP.NET invokes it directly and bypasses Program.Run.
        harness = native / "RejectedWay3.cs"
        harness.write_text('''using System;
using System.Reflection;
class RejectedWay3 {
  static int Main(string[] args) {
    Assembly a = Assembly.LoadFrom(args[0]);
    Type p = a.GetType("LicenseRecoverNet.Program", true);
    BindingFlags f = BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic;
    object o = p.GetMethod("ParseArgs", f).Invoke(null, new object[] { new string[] { "patch", args[1] } });
    if (o == null) throw new Exception("patch parse fixture failed");
    if (Convert.ToInt32(p.GetMethod("RunDirect", f).Invoke(null, new object[] { o })) != 2)
      throw new Exception("hosted patch dispatch was not rejected");
    Type writer = a.GetType("LicenseRecoverNet.FoapNet", true);
    if (Convert.ToInt32(writer.GetMethod("Patch", f).Invoke(null, new object[] { o })) != 2)
      throw new Exception("direct patch writer was not rejected");
    Console.WriteLine("WAY3_REJECTED_WITHOUT_TARGET_WRITES");
    return 0;
  }
}
''', encoding="utf-8")
        built = subprocess.run([shutil.which("mcs"), "-out:" + str(native / "RejectedWay3.exe"), str(harness)],
                               capture_output=True, text=True, timeout=30)
        self.assertEqual(built.returncode, 0, built.stdout + built.stderr)
        for variant, data in self.outputs.items():
            helper = native / (variant + ".exe")
            helper.write_bytes(data)
            for extra in ([], ["--dry-run"]):
                with self.subTest(variant=variant, args=extra):
                    result = subprocess.run([shutil.which("mono"), str(helper), "patch", str(target), *extra],
                                            cwd=native, capture_output=True, text=True, timeout=30)
                    self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                    self.assertEqual({p.name: p.read_bytes() for p in target.iterdir()}, snapshot)
            direct = subprocess.run([shutil.which("mono"), str(native / "RejectedWay3.exe"), str(helper), str(target)],
                                    cwd=native, capture_output=True, text=True, timeout=30)
            self.assertEqual(direct.returncode, 0, direct.stdout + direct.stderr)
            self.assertIn("WAY3_REJECTED_WITHOUT_TARGET_WRITES", direct.stdout)
            self.assertEqual({p.name: p.read_bytes() for p in target.iterdir()}, snapshot)

    @unittest.skipUnless(shutil.which("mono") and shutil.which("mcs"), "Mono/mcs unavailable for native execution")
    def test_aspnet_host_and_bridge_reject_before_staging_or_context_setup(self):
        if HOST_PATH is None or BRIDGE_PATH is None:
            self.skipTest("provide --host and --bridge to validate compiled ASP.NET artifacts")
        self.assertTrue(HOST_PATH.is_file())
        self.assertTrue(BRIDGE_PATH.is_file())
        native = self.work / "host-execution"
        native.mkdir()
        target = native / "target-bin"
        target.mkdir()
        for name in ("itmcRegedit.dll", "config.xml", "itmcRegedit.dll.restore"):
            (target / name).write_bytes(("preserve: " + name).encode())
        snapshot = {p.name: p.read_bytes() for p in target.iterdir()}
        harness = native / "RejectedBridge.cs"
        harness.write_text('''using System;
using System.Reflection;
class RejectedBridge {
  static int Main(string[] args) {
    Assembly a = Assembly.LoadFrom(args[0]);
    Type t = a.GetType("LicenseRecoverAspNetBridgeRunner", true);
    object bridge = Activator.CreateInstance(t);
    object result = t.GetMethod("Run").Invoke(bridge, new object[] {
      "deliberately-missing-root", args[1], "deliberately-missing-helper",
      new string[] { args[2], args[1] }
    });
    if (Convert.ToInt32(result) != 2) throw new Exception("bridge did not reject before context setup");
    Console.WriteLine("BRIDGE_REJECTED_BEFORE_CONTEXT_SETUP");
    return 0;
  }
}
''', encoding="utf-8")
        built = subprocess.run([shutil.which("mcs"), "-out:" + str(native / "RejectedBridge.exe"), str(harness)],
                               capture_output=True, text=True, timeout=30)
        self.assertEqual(built.returncode, 0, built.stdout + built.stderr)
        for command in ("patch", "PaTcH", "--remove-net"):
            host = subprocess.run([shutil.which("mono"), str(HOST_PATH), command, str(target)],
                                  cwd=native, capture_output=True, text=True, timeout=30)
            self.assertEqual(host.returncode, 2, host.stdout + host.stderr)
            self.assertIn("REMOVED_OPERATION", host.stdout + host.stderr)
            bridge = subprocess.run([shutil.which("mono"), str(native / "RejectedBridge.exe"),
                                     str(BRIDGE_PATH), str(target), command],
                                    cwd=native, capture_output=True, text=True, timeout=30)
            self.assertEqual(bridge.returncode, 0, bridge.stdout + bridge.stderr)
            self.assertIn("BRIDGE_REJECTED_BEFORE_CONTEXT_SETUP", bridge.stdout)
            self.assertEqual({p.name: p.read_bytes() for p in target.iterdir()}, snapshot)


if __name__ == "__main__":
    for option, variable in (("--host", "HOST_PATH"), ("--bridge", "BRIDGE_PATH")):
        if option in sys.argv:
            index = sys.argv.index(option)
            if index + 1 >= len(sys.argv):
                raise SystemExit(option + " requires an artifact path")
            globals()[variable] = Path(sys.argv[index + 1]).resolve()
            del sys.argv[index:index + 2]
    if "--require-mono" in sys.argv:
        sys.argv.remove("--require-mono")
        if not shutil.which("mono") or not shutil.which("mcs"):
            raise SystemExit("Mono and mcs are required for the CI native way-three removal gate")
        if HOST_PATH is None or BRIDGE_PATH is None or not HOST_PATH.is_file() or not BRIDGE_PATH.is_file():
            raise SystemExit("CI requires --host and --bridge pointing to built ASP.NET artifacts")
    unittest.main()
