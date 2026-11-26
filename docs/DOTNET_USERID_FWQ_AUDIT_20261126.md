# .NET local UserID identity regression check (2026-11-26)

## Scope

The existing hash-pinned `LicenseRecover.NET.exe` already contains the `set_UserID` -> `fwq` mapping, and the derived uppercase `LicenseRecover.NET.Modern.exe` preserves it. No other `itmc` strings in these helpers should be changed: product names, keys and the independent `WebSerUserID` field have separate semantics.

The previously inconsistent `LicenseRecover.NET.Legacy.dll` source set `RegeditMain.UserID=itmc`. The new Legacy path sets that registration property to `fwq`, inspects the resulting local `RegeditInfo.UserID` through the native local-check result, and refuses to classify the record as fully recovered when the persisted identity differs. Diagnostics report only a match/mismatch flag, never the stored UserID.

## Tests

- `scripts/test-ds0101-native-helper-smoke.sh`: synthetic ITMC.Regedit and Web assembly check that request generation receives `fwq`, that the generated user identity is persisted, and that an `itmc` record fails final verification while a `fwq` record proceeds through native checks.
- `scripts/check-dotnet-userid.py`: validates the hash-pinned helper's compiled IL mapping in the original and modern adapters, plus the rebuilt Legacy source and output.
- `scripts/verify.ps1` runs the compiled helper identity audit during distribution assembly.

These synthetic and static checks do not prove acceptance by a real DS01xx or YX/GM004 installation. Deployment on a backed-up isolated authorized test site is still required before a release or a claim of complete business-function compatibility.
