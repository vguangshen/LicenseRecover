#!/usr/bin/env python3
"""Build a modern ITMC.Regedit adapter copy of the tracked .NET helper.

The historical helper reflects the lowercase itmcRegedit assembly/type. Modern
applications use the uppercase ITMC.Regedit assembly/type. This script creates a
separate modern-only copy by patching three AppReflection #US token operands after
strictly verifying the source binary hash. The original helper is never modified.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SOURCE_SHA256 = "b83832e9be135d977a16b9faf8bea4f87e645734691ee0125480513116508d23"
EXPECTED_OUTPUT_SHA256 = "aef8bcc41471de2ae361f3f8cd21978f498bd9f7ffa45c86db848dc8da096f36"

DONORS = (
    (bytes([33]) + "not a FOAP table".encode("utf-16le") + b"\x00",
     bytes([33]) + "ITMC.Regedit.dll".encode("utf-16le") + b"\x00",
     "ITMC.Regedit.dll"),
    (bytes([49]) + "  [错误] 中和循环超过 64 次，放弃写回。".encode("utf-16le") + b"\x01",
     bytes([49]) + "ITMC.Regedit.RegeditMain".encode("utf-16le") + b"\x00",
     "ITMC.Regedit.RegeditMain"),
)

TOKEN_PATCHES = (
    (0x04E9, bytes.fromhex("01000070"), bytes.fromhex("9d000070")),
    (0x0503, bytes.fromhex("19000070"), bytes.fromhex("e9170070")),
    (0x02CA, bytes.fromhex("39000070"), bytes.fromhex("db150070")),
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

    for offset, before, after in TOKEN_PATCHES:
        current = bytes(data[offset:offset + len(before)])
        if current != before:
            raise SystemExit(
                f"IL token precondition failed at 0x{offset:x}: "
                f"expected {before.hex()}, found {current.hex()}"
            )
        data[offset:offset + len(before)] = after

    out = bytes(data)
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
