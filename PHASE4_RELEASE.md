# Phase 4 — Repository slimming and rolling Release delivery

## Scope

Phase 4 separates generated release archives from the Git source tree while preserving the current runtime layout and launcher compatibility.

## Repository slimming

`LicenseRecover-latest.zip` is no longer tracked at the repository root. The archive is generated under `build/` by the same verification pipeline used locally and in GitHub Actions.

This prevents future release ZIPs from repeatedly inflating the current tree. It does **not** rewrite existing Git history: the historical 52.9 MB blob remains reachable from older commits. Rewriting repository history is intentionally out of scope because it would change commit SHAs and is much riskier for existing clones and references.

## Verified release outputs

`scripts/verify.ps1` now creates:

- `build/LicenseRecover-latest.zip`
- `build/SHA256SUMS.txt`

The SHA-256 file contains the digest of the exact ZIP produced by the verification run.

## Rolling GitHub Release

After a successful verification of a push to `main`, GitHub Actions updates a prerelease tagged:

`rolling-latest`

The release contains the verified ZIP and checksum file. The `rolling-latest` tag intentionally moves to the newest successfully verified `main` commit.

Pull requests and `refactor/**` branch pushes never publish a Release. Refactor branches may still synchronize a changed deterministic `LicenseRecoverOverlay.jar` after successful verification.

## Compatibility boundary

Phase 4 does not move or rename runtime compatibility files such as:

- `LicenseRecover.jar`
- `LicenseRecoverGUI.jar`
- `LicenseRecoverOverlay.jar`
- `LicenseRecoverGUI.exe`
- `LicenseRecover.NET/`
- `run*.bat`
- runtime assets

Existing users can continue to launch the tool in the same way. Only the location of the generated all-in-one ZIP changes: it is now a Release asset instead of a tracked repository blob.

## Future stable releases

The rolling prerelease provides a continuously verified download. A future versioning phase can add immutable tags such as `v1.0.0` and stable Releases without changing the build pipeline introduced here.
