#!/usr/bin/env python3
"""Build a modern ITMC.Regedit adapter copy of the tracked .NET helper.

The historical helper reflects the lowercase itmcRegedit assembly/type. Modern
applications use the uppercase ITMC.Regedit assembly/type. This script creates a
separate modern-only copy by patching every executable ldstr reference to the
legacy assembly/dll/RegeditMain identifiers after strictly verifying the source
binary hash. The original helper is never modified.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SOURCE_SHA256 = "b83832e9be135d977a16b9faf8bea4f87e645734691ee0125480513116508d23"
EXPECTED_OUTPUT_SHA256 = "75becdebb3772c56ccd10b25537c1f72f6877def844041a6270a3f9e197d2bd4"

DONORS = (
    (bytes([33]) + "not a FOAP table".encode("utf-16le") + b"\x00",
     bytes([33]) + "ITMC.Regedit.dll".encode("utf-16le") + b"\x00",
     "ITMC.Regedit.dll"),
    (bytes([49]) + "  [错误] 中和循环超过 64 次，放弃写回。".encode("utf-16le") + b"\x01",
     bytes([49]) + "ITMC.Regedit.RegeditMain".encode("utf-16le") + b"\x00",
     "ITMC.Regedit.RegeditMain"),
)

LDSTR_PATCHES = (
    # Full IL instruction (0x72 = ldstr) + 4-byte #US token.  Counts are from the
    # hash-pinned source helper and intentionally make layout drift a hard failure.
    (bytes.fromhex("72 01 00 00 70"), bytes.fromhex("72 9d 00 00 70"), 1, "assembly itmcRegedit"),
    (bytes.fromhex("72 19 00 00 70"), bytes.fromhex("72 e9 17 00 70"), 4, "dll itmcRegedit.dll"),
    (bytes.fromhex("72 39 00 00 70"), bytes.fromhex("72 db 15 00 70"), 6, "type itmcRegedit.RegeditMain"),
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def replace_once(data: bytearray, old: bytes, new: bytes, label: str) -> None:
    if len(old) != len(new):
        raise SystemExit(f"{label}: replacement changes PE size")
    count = bytes(data).count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one donor payload, found {count}")
    pos = bytes(data).find(old)
    data[pos:pos + len(old)] = new


def patch(source: Path, destination: Path) -> None:
    raw = source.read_bytes()
    actual = sha256(raw)
    if actual != SOURCE_SHA256:
        raise SystemExit(
            "Unsupported LicenseRecover.NET.exe build. "
            f"Expected SHA-256 {SOURCE_SHA256}, got {actual}."
        )

    data = bytearray(raw)
    for old_blob, new_blob, label in DONORS:
        replace_once(data, old_blob, new_blob, label)

    for before, after, expected_count, label in LDSTR_PATCHES:
        if len(before) != len(after):
            raise SystemExit(f"{label}: replacement changes PE size")
        count = bytes(data).count(before)
        if count != expected_count:
            raise SystemExit(
                f"{label}: expected {expected_count} executable ldstr reference(s), found {count}"
            )
        data[:] = bytes(data).replace(before, after)

    out = bytes(data)
    for before, _after, _expected_count, label in LDSTR_PATCHES:
        if before in out:
            raise SystemExit(f"{label}: legacy executable ldstr reference remains after patch")
    for required in ("ITMC.Regedit", "ITMC.Regedit.dll", "ITMC.Regedit.RegeditMain"):
        if required.encode("utf-16le") not in out:
            raise SystemExit(f"Patched helper is missing required user string: {required}")

    out_sha = sha256(out)
    if out_sha != EXPECTED_OUTPUT_SHA256:
        raise SystemExit(
            "Patched helper hash mismatch. "
            f"Expected {EXPECTED_OUTPUT_SHA256}, got {out_sha}."
        )

    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(out)
    print(f"Modern helper: {destination}")
    print(f"SHA-256: {out_sha}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    patch(args.source, args.destination)


if __name__ == "__main__":
    main()
