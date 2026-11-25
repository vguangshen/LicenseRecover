"""Synthetic distribution regression for retired compatibility entrypoints."""

import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile


SCRIPT = Path(__file__).resolve().parents[1] / "package-compatibility-jars.py"
SPEC = importlib.util.spec_from_file_location("package_compatibility_jars", SCRIPT)
PACKAGER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PACKAGER)


class CompatibilityJarTest(unittest.TestCase):
    def test_current_classes_replace_stale_entrypoints_and_embedded_resources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            classes = root / "classes"
            classes.mkdir()
            current = {}
            for name in ("LicenseRecover", "LicenseRecoverGUI", "NetRemover", "SafeNetRemoverCLI"):
                data = b"\xca\xfe\xba\xbe" + name.encode("ascii") + b" current"
                current[name + ".class"] = data
                (classes / (name + ".class")).write_bytes(data)
            current["LicenseRecoverGUI$1.class"] = b"\xca\xfe\xba\xbe current inner"
            (classes / "LicenseRecoverGUI$1.class").write_bytes(current["LicenseRecoverGUI$1.class"])
            core_source = root / "old-core.jar"
            gui_source = root / "old-gui.jar"
            for path, main in ((core_source, "LicenseRecover"), (gui_source, "LicenseRecoverGUI")):
                with ZipFile(path, "w") as archive:
                    archive.writestr("META-INF/MANIFEST.MF", f"Manifest-Version: 1.0\r\nMain-Class: {main}\r\n\r\n")
                    archive.writestr(main + ".class", b"old entrypoint")
                    archive.writestr(main + "$99.class", b"removed old anonymous writer")
                    archive.writestr("NetRemover.class", b"old patch implementation")
                    archive.writestr("javassist/ClassPool.class", b"dependency must survive")
                    archive.writestr("virbox_keystream.bin", b"shared runtime resource")
                    if path == gui_source:
                        archive.writestr("LicenseRecover.jar", b"old embedded core")
                        archive.writestr("LicenseRecover.NET/LicenseRecover.NET.exe", b"old embedded helper")
            original_core = core_source.read_bytes()
            original_gui = gui_source.read_bytes()
            helper = root / "current-helper.exe"
            helper.write_bytes(b"current helper without patch")
            output = root / "dist"
            PACKAGER.package(core_source, gui_source, classes, helper, output)

            for filename in ("LicenseRecover.jar", "LicenseRecoverGUI.jar"):
                with ZipFile(output / filename) as archive:
                    for name, data in current.items():
                        self.assertEqual(archive.read(name), data)
                    self.assertFalse(any(name.endswith("$99.class") for name in archive.namelist()))
                    self.assertEqual(archive.read("javassist/ClassPool.class"), b"dependency must survive")
                    self.assertEqual(archive.read("virbox_keystream.bin"), b"shared runtime resource")
                    self.assertIn(b"SafeNetRemoverCLI.class", archive.read(PACKAGER.CLASS_MANIFEST))
            with ZipFile(output / "LicenseRecoverGUI.jar") as archive:
                self.assertEqual(archive.read(PACKAGER.CORE_ENTRY), (output / "LicenseRecover.jar").read_bytes())
                self.assertEqual(archive.read(PACKAGER.NATIVE_HELPER_ENTRY), helper.read_bytes())
                with ZipFile(io.BytesIO(archive.read(PACKAGER.CORE_ENTRY))) as embedded:
                    self.assertEqual(embedded.read("NetRemover.class"), current["NetRemover.class"])
            self.assertEqual(core_source.read_bytes(), original_core)
            self.assertEqual(gui_source.read_bytes(), original_gui)
            before = (output / "LicenseRecoverGUI.jar").read_bytes()
            PACKAGER.package(core_source, gui_source, classes, helper, output)
            self.assertEqual((output / "LicenseRecoverGUI.jar").read_bytes(), before)

    def test_refuses_to_overwrite_bundled_input(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "must not overwrite"):
                PACKAGER.package(root / "LicenseRecover.jar", root / "LicenseRecoverGUI.jar",
                                 root / "classes", root / "helper.exe", root)


if __name__ == "__main__":
    unittest.main()
