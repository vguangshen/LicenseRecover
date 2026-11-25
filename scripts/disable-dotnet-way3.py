#!/usr/bin/env python3
"""Remove the retired way-three writer from the two hash-pinned .NET helpers.

Run prepare-dotnet-modern-helper.py FIRST: its input/output hash checks remain
unchanged. Then run this script separately on the original and modern helpers.
Never use the tracked input as the destination. The distribution and the copy
embedded in LicenseRecoverGUI.jar must receive the resulting disabled helper.

The original helper has no maintained source. Its actual dispatch is
Program.RunDirect -> Program.PatchMode -> FoapNet.Patch. Program.Run is also
guarded before it enters the target AppDomain: that domain's failure cleanup can
otherwise restore an existing itmcRegedit.dll.restore file. FoapNet's methods from
Patch through AddSetDateTime are exclusively the old bytecode-rewrite engine:
file replacement, sanitizing/writing IL, removing network calls, and constructing
replacement authorization objects. We erase those complete method bodies,
including exception sections, and retain compatibility stubs. Both public patch
entrypoints return exit code 2; internal bool/object/void helpers return false,
null, or no result. The old executable writer IL is not retained behind a flag.

FoapNet.Scan and its preceding read-only helpers are preserved. AppReflection,
FoapUnpack/FoapStage2, Program.ConfigMode/VerifyMode/GenCodeMode/DoRegMode and the
native registration method bodies are preserved byte-for-byte. Four existing
Scan/ConfigMode messages are changed in-place so these retained commands no
longer recommend the removed writer. No target application is opened or modified
by this build-time transformation.

This is deliberately NOT a general binary patcher. A supported whole-file hash,
the exact owner/name/RVA/signature for every removed method, valid PE/CLI ranges,
non-overlapping bodies, and unchanged non-target bytes are all mandatory.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import struct
import tempfile
from pathlib import Path


SOURCE_HASHES = {
    "b83832e9be135d977a16b9faf8bea4f87e645734691ee0125480513116508d23": "original",
    "75becdebb3772c56ccd10b25537c1f72f6877def844041a6270a3f9e197d2bd4": "modern",
}
OUTPUT_HASHES = {
    "original": "4a77ad8ad942b2b2abbf91a8b74480d885b6a4163dda41a8d422cba850ac5945",
    "modern": "544d27b0cdca55c3617994b3e1bb40b44807e1a493be078f16ae6b9cf2860089",
}

# owner, method, RVA, exact ECMA-335 signature, replacement return value.
# 0x1244 is this helper's Options class; 0x1221/29/2d are dnlib model types.
DISABLED_METHODS = (
    ("LicenseRecoverNet.Program", "PatchMode", 0x2273, "0001081244", "error"),
    ("LicenseRecoverNet.FoapNet", "Patch", 0x2C04, "0001081244", "error"),
    ("LicenseRecoverNet.FoapNet", "ReplaceWithRetry", 0x3308, "0003020e0e0e", "false"),
    ("LicenseRecoverNet.FoapNet", "CleanupTemps", 0x3390, "0003010e0e0e", "void"),
    ("LicenseRecoverNet.FoapNet", "PatchRegeditMain", 0x3400, "0006020e0e0e0e0e02", "false"),
    ("LicenseRecoverNet.FoapNet", "WriteWithSanitize", 0x3E58, "00030212210e1225", "false"),
    ("LicenseRecoverNet.FoapNet", "FindMethodByRid", 0x3F74, "0002121d122108", "null"),
    ("LicenseRecoverNet.FoapNet", "ExtractMethodRid", 0x4008, "0001080e", "error"),
    ("LicenseRecoverNet.FoapNet", "CleanDeadNetwork", 0x4050, "0001011221", "void"),
    ("LicenseRecoverNet.FoapNet", "CallsRuntimeOrService", 0x42B0, "000102121d", "false"),
    ("LicenseRecoverNet.FoapNet", "IsReferenced", 0x4344, "0002021221121d", "false"),
    ("LicenseRecoverNet.FoapNet", "InertUrl", 0x44A4, "00010e0e", "null"),
    ("LicenseRecoverNet.FoapNet", "AddSetStr", 0x44F0, "0004011229122d0e0e", "void"),
    ("LicenseRecoverNet.FoapNet", "AddSetI4", 0x454C, "0004011229122d0e08", "void"),
    ("LicenseRecoverNet.FoapNet", "AddSetBool", 0x45A8, "0004011229122d0e02", "void"),
    ("LicenseRecoverNet.FoapNet", "BuildPerpetualInfo", 0x460C, "000512291221122d0e0e0e", "null"),
    ("LicenseRecoverNet.FoapNet", "AddSetDateTime", 0x4700, "00070112291221122d0e080808", "void"),
)

# Tiny IL bodies: ldc.i4.2 / ldc.i4.0 / ldnull / ret, with no locals or EH.
STUBS = {
    "error": bytes.fromhex("0a 18 2a"),
    "false": bytes.fromhex("0a 16 2a"),
    "null": bytes.fromhex("0a 14 2a"),
    "void": bytes.fromhex("06 2a"),
}

# Active diagnostic strings, not code or metadata tokens. Padding preserves the
# #US heap layout and all existing ldstr operands. Old write-engine-only strings
# may remain as inert metadata; no executable body references the removed engine.
DIAGNOSTIC_TEXT = (
    (" ITMC .NET 版 - 方式三：扫描联网授权验证文件",
     " ITMC .NET 版 - 只读扫描授权验证文件"),
    (" 个加密方法体，执行补丁时会先自动脱壳）",
     " 个加密方法体；扫描不会修改文件）"),
    ("  ★ 已脱壳或加壳，但仍含联网授权方法 → 点「移除联网授权并回写」即可清理（已脱壳的直接补丁，加壳的先自动脱壳）",
     "  ★ 检测到联网授权方法；本工具仅报告代码状态，不修改 DLL。"),
    ("RESULT: FAILED —— 所有候选产品号均未通过自校验（可改用方式三移除联网授权代码）。",
     "RESULT: FAILED —— 所有候选产品号均未通过自校验；请核对目标产品与现有授权。"),
)

RUN_KEY = ("LicenseRecoverNet.Program", "Run", "0001081244")
RUN_RVA = 0x7098
# Run's third comparison is Options.Mode == 4 (the original "patch" command).
# It used to branch to target-bin validation and RunInBinDomain. Repoint that
# single branch to the existing ldc.i4.1; ret block at IL offsets 106/107. This
# returns failure before target inspection, AppDomain creation, or .restore
# cleanup. The other command comparisons and all their bytecode are unchanged.
RUN_PREFIX = bytes.fromhex(
    "02 7b 23 00 00 04 2c 34 "
    "02 7b 23 00 00 04 18 2e 2b "
    "02 7b 23 00 00 04 1a 2e 22"
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class ManagedImage:
    """Small bounds-checked reader for this hash-pinned PE and CLI metadata."""

    def __init__(self, data: bytes):
        self.data = data
        if self.take(0, 2) != b"MZ":
            raise ValueError("missing DOS signature")
        pe = self.uint(0x3C, 4)
        if self.take(pe, 4) != b"PE\0\0":
            raise ValueError("missing PE signature")
        count = self.uint(pe + 6, 2)
        optional = pe + 24
        optional_size = self.uint(pe + 20, 2)
        if self.uint(optional, 2) != 0x10B:
            raise ValueError("expected pinned PE32 helper")
        if optional_size < 216:
            raise ValueError("missing CLI data directory")
        section_table = optional + optional_size
        self.sections = []
        for index in range(count):
            p = section_table + 40 * index
            self.take(p, 40)
            self.sections.append((self.uint(p + 12, 4), self.uint(p + 20, 4), self.uint(p + 16, 4)))
        cli = self.file_offset(self.uint(optional + 96 + 14 * 8, 4), 16)
        metadata_size = self.uint(cli + 12, 4)
        metadata = self.file_offset(self.uint(cli + 8, 4), metadata_size)
        if self.take(metadata, 4) != b"BSJB":
            raise ValueError("missing CLI metadata signature")
        p = self.align4(metadata + 16 + self.uint(metadata + 12, 4))
        stream_count = self.uint(p + 2, 2)
        p += 4
        self.streams = {}
        for _ in range(stream_count):
            start, size = self.uint(p, 4), self.uint(p + 4, 4)
            name_end = self.data.find(b"\0", p + 8, min(p + 41, len(data)))
            if name_end < 0 or start + size > metadata_size:
                raise ValueError("invalid metadata stream")
            name = self.take(p + 8, name_end - p - 8).decode("ascii")
            self.streams[name] = (metadata + start, size)
            self.take(metadata + start, size)
            p = self.align4(name_end + 1)
        self.methods = self.read_methods()

    @staticmethod
    def align4(value: int) -> int:
        return (value + 3) & ~3

    def take(self, offset: int, size: int) -> bytes:
        if offset < 0 or size < 0 or offset + size > len(self.data):
            raise ValueError("PE/CLI range is out of bounds")
        return self.data[offset:offset + size]

    def uint(self, offset: int, size: int) -> int:
        return int.from_bytes(self.take(offset, size), "little")

    def file_offset(self, rva: int, size: int = 1) -> int:
        for virtual, raw, raw_size in self.sections:
            if virtual <= rva and rva + size <= virtual + raw_size:
                offset = raw + rva - virtual
                self.take(offset, size)
                return offset
        raise ValueError("RVA does not refer to complete file-backed data")

    def heap(self, name: str, index: int, size: int) -> bytes:
        start, length = self.streams[name]
        if index < 0 or index + size > length:
            raise ValueError("metadata heap index is out of bounds")
        return self.take(start + index, size)

    def string(self, index: int) -> str:
        start, size = self.streams["#Strings"]
        self.heap("#Strings", index, 1)
        end = self.data.find(b"\0", start + index, start + size)
        if end < 0:
            raise ValueError("unterminated metadata string")
        return self.take(start + index, end - start - index).decode("utf-8")

    def blob(self, index: int) -> bytes:
        first = self.heap("#Blob", index, 1)[0]
        if first < 0x80:
            length, prefix = first, 1
        elif first < 0xC0:
            length, prefix = ((first & 0x3F) << 8) | self.heap("#Blob", index + 1, 1)[0], 2
        elif first < 0xE0:
            length, prefix = int.from_bytes(self.heap("#Blob", index, 4), "big") & 0x1FFFFFFF, 4
        else:
            raise ValueError("invalid compressed metadata length")
        return self.heap("#Blob", index + prefix, length)

    def read_methods(self) -> dict:
        table, table_size = self.streams["#~"]
        heaps = self.uint(table + 6, 1)
        valid = self.uint(table + 8, 8)
        p, rows = table + 24, {}
        for index in range(64):
            if valid & (1 << index):
                rows[index] = self.uint(p, 4)
                p += 4
        idx = lambda t: 2 if rows.get(t, 0) < 65536 else 4
        coded = lambda ts, bits: 2 if max(rows.get(t, 0) for t in ts) < (1 << (16 - bits)) else 4
        strings = 4 if heaps & 1 else 2
        guid = 4 if heaps & 2 else 2
        blob = 4 if heaps & 4 else 2
        type_ref = coded((2, 1, 27), 2)
        sizes = (
            2 + strings + 3 * guid,
            coded((0, 26, 35, 1), 2) + 2 * strings,
            4 + 2 * strings + type_ref + idx(4) + idx(6),
            idx(4), 2 + strings + blob, idx(6), 8 + strings + blob + idx(8),
        )
        offsets = []
        for index, size in enumerate(sizes):
            offsets.append(p)
            p += rows.get(index, 0) * size
        if p > table + table_size:
            raise ValueError("metadata tables exceed stream bounds")
        types = []
        for index in range(rows.get(2, 0)):
            p = offsets[2] + index * sizes[2]
            name = self.string(self.uint(p + 4, strings))
            namespace = self.string(self.uint(p + 4 + strings, strings))
            first_method = self.uint(p + 4 + 2 * strings + type_ref + idx(4), idx(6))
            # The removed type is top-level. Other compiler-generated nested
            # types may share an empty namespace/name; retain their TypeDef RID
            # for uniqueness without needing unrelated NestedClass table parsing.
            owner = namespace + "." + name if namespace else "TypeDef[" + str(index + 1) + "]." + name
            types.append((owner, first_method))
        methods = {}
        for index in range(rows.get(6, 0)):
            rid = index + 1
            p = offsets[6] + index * sizes[6]
            owner = next((name for (name, first), (_, last) in zip(types, types[1:] + [("", rows[6] + 1)])
                          if first <= rid < last), None)
            if owner is None:
                raise ValueError("method has no declaring type")
            name = self.string(self.uint(p + 8, strings))
            signature = self.blob(self.uint(p + 8 + strings, blob))
            key = (owner, name, signature.hex())
            if key in methods:
                raise ValueError("duplicate method signature")
            methods[key] = (self.uint(p, 4), self.uint(p + 6, 2))
        return methods

    def body_range(self, rva: int) -> tuple[int, int]:
        start = self.file_offset(rva)
        first = self.uint(start, 1)
        if first & 3 == 2:
            end = start + 1 + (first >> 2)
        elif first & 3 == 3:
            header = self.uint(start, 2)
            header_size = (header >> 12) * 4
            if header_size != 12:
                raise ValueError("unexpected fat method header")
            end = start + header_size + self.uint(start + 4, 4)
            if header & 8:
                end = self.align4(end)
                while True:
                    section = self.uint(end, 1)
                    if section & 0x3F != 1:
                        raise ValueError("unexpected method data section")
                    size = self.uint(end + 1, 3 if section & 0x40 else 1)
                    if size < 4:
                        raise ValueError("invalid exception section")
                    end += size
                    if not section & 0x80:
                        break
                    end = self.align4(end)
        else:
            raise ValueError("invalid method body header")
        self.file_offset(rva, end - start)
        return start, end


def disable(data: bytes) -> tuple[bytes, str]:
    actual = sha256(data)
    if actual not in SOURCE_HASHES:
        raise ValueError("unsupported helper SHA-256: " + actual)
    image = ManagedImage(data)
    ranges = []
    targets = set()
    for owner, name, expected_rva, signature, result in DISABLED_METHODS:
        key = (owner, name, signature)
        if key not in image.methods:
            raise ValueError("missing exact retired method signature: " + owner + "." + name)
        rva, flags = image.methods[key]
        if rva != expected_rva or not flags & 0x10:
            raise ValueError("retired method RVA/static flag mismatch: " + name)
        start, end = image.body_range(rva)
        stub = STUBS[result]
        if end - start < len(stub):
            raise ValueError("retired method body is too small: " + name)
        ranges.append((start, end, stub))
        targets.add(key)
    ranges.sort()
    for previous, current in zip(ranges, ranges[1:]):
        if previous[1] > current[0]:
            raise ValueError("retired method bodies overlap")
    for key, (rva, _flags) in image.methods.items():
        if key in targets or rva == 0:
            continue
        start, end = image.body_range(rva)
        if any(start < stop and begin < end for begin, stop, _ in ranges):
            raise ValueError("retired method overlaps a retained method: " + key[1])

    body_ranges = list(ranges)
    run_rva, run_flags = image.methods.get(RUN_KEY, (0, 0))
    if run_rva != RUN_RVA or not run_flags & 0x10:
        raise ValueError("Program.Run signature/RVA/static flag mismatch")
    run_start, _ = image.body_range(run_rva)
    if image.uint(run_start, 2) >> 12 != 3 or image.uint(run_start + 4, 4) != 183:
        raise ValueError("Program.Run body layout changed")
    run_code = run_start + 12
    if image.take(run_code, len(RUN_PREFIX)) != RUN_PREFIX or image.take(run_code + 106, 2) != b"\x17\x2a":
        raise ValueError("Program.Run patch comparison/early-failure IL changed")
    ranges.append((run_code + 25, run_code + 26, b"\x50"))
    us_start, us_size = image.streams["#US"]
    for previous, current in DIAGNOSTIC_TEXT:
        old = previous.encode("utf-16le")
        new = current.encode("utf-16le")
        if len(new) > len(old) or data.count(old) != 1:
            raise ValueError("diagnostic string is not the pinned, same-size slot")
        start = data.find(old)
        end = start + len(old)
        if not us_start <= start < end < us_start + us_size or data[end] != 1:
            raise ValueError("diagnostic string is not in the expected Unicode heap")
        new += b" \0" * ((len(old) - len(new)) // 2)
        ranges.append((start, end, new))
    ranges.sort()
    for previous, current in zip(ranges, ranges[1:]):
        if previous[1] > current[0]:
            raise ValueError("replacement ranges overlap")

    output = bytearray(data)
    for start, end, stub in ranges:
        output[start:end] = stub + bytes(end - start - len(stub))
    output = bytes(output)
    cursor = 0
    for start, end, stub in ranges:
        if output[cursor:start] != data[cursor:start]:
            raise ValueError("non-target binary data changed")
        if output[start:end] != stub + bytes(end - start - len(stub)):
            raise ValueError("a replacement did not match the expected bytes")
        cursor = end
    if len(output) != len(data) or output[cursor:] != data[cursor:]:
        raise ValueError("non-target binary data changed")
    if ManagedImage(output).methods != image.methods:
        raise ValueError("method metadata changed")
    for start, end, stub in body_ranges:
        if output[start:end] != stub + bytes(end - start - len(stub)):
            raise ValueError("retired method still contains original executable code")
    variant = SOURCE_HASHES[actual]
    if sha256(output) != OUTPUT_HASHES[variant]:
        raise ValueError("disabled helper output hash mismatch")
    return output, variant


def transform(source: Path, destination: Path) -> None:
    if source.resolve() == destination.resolve() or (
        destination.exists() and os.path.samefile(source, destination)
    ):
        raise ValueError("source helper must not be overwritten")
    output, variant = disable(source.read_bytes())
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(prefix=destination.name + ".", dir=destination.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(output)
        os.replace(temporary, destination)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    print("Way-three writer removed from " + variant + " helper: " + str(destination))
    print("Retired methods: " + str(len(DISABLED_METHODS)))
    print("SHA-256: " + sha256(output))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    try:
        transform(args.source, args.destination)
    except (ValueError, OSError, KeyError, UnicodeError, struct.error) as error:
        raise SystemExit(str(error)) from error


if __name__ == "__main__":
    main()
