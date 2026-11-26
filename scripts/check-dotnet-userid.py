#!/usr/bin/env python3
"""Fail closed if a shipped .NET helper loses the validated fwq UserID path.

The pinned native helper has an IL dictionary entry mapping set_UserID to fwq.
The modern adapter must preserve the same instruction sequence. The DS01xx
Legacy helper is compiled from source and additionally tested with synthetic
native assemblies (scripts/test-ds0101-native-helper-smoke.sh).
"""
from pathlib import Path
import sys

NATIVE_USERID_MAPPING_IL = bytes.fromhex(
    "72 67 17 00 70 72 7d 17 00 70 28 28 00 00 06"
)
FWQ_UTF16 = "fwq".encode("utf-16le")
SOURCE_CONSTANT = 'private const string LocalAuthorizationUserId = "fwq";'
SOURCE_ASSIGNMENT = "userID.SetValue(instance, LocalAuthorizationUserId, null);"
SOURCE_VERIFICATION = 'LocalStateFlag(info, "UserID", LocalAuthorizationUserId, false) == "match"'


def check_native(path: Path) -> None:
    data = path.read_bytes()
    count = data.count(NATIVE_USERID_MAPPING_IL)
    if count != 1:
        raise SystemExit(
            f"{path}: expected one compiled set_UserID -> fwq mapping, got {count}"
        )
    print(f"PASS: {path.name} retains the verified fwq mapping")


def check_legacy(path: Path) -> None:
    source = Path("src/dotnet/LicenseRecover.LegacyHelper.cs").read_text(
        encoding="utf-8"
    )
    for required in (SOURCE_CONSTANT, SOURCE_ASSIGNMENT, SOURCE_VERIFICATION):
        if required not in source:
            raise SystemExit("Legacy helper source is missing required fwq assignment/check")
    if FWQ_UTF16 not in path.read_bytes():
        raise SystemExit(f"{path}: compiled Legacy helper does not contain fwq")
    print(f"PASS: {path.name} uses source-level fwq assignment and validation")


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit(
            "usage: check-dotnet-userid.py SOURCE.exe MODERN.exe LEGACY.dll"
        )
    check_native(Path(sys.argv[1]))
    check_native(Path(sys.argv[2]))
    check_legacy(Path(sys.argv[3]))


if __name__ == "__main__":
    main()
