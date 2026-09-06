# Changelog

All notable user-visible and engineering changes are tracked here from the first stable release onward.

## [1.1.0] - 2026-09-06

### Added

- GitHub Releases based automatic update check in the default modern launcher.
- Stable-version comparison against GitHub `releases/latest`.
- Download and SHA-256 verification of `LicenseRecover-latest.zip` before installation.
- Temporary external update installer so the active overlay JAR can be replaced on Windows.
- Pre-update backup of runtime files that will be overwritten.
- ZIP path-traversal / zip-slip protection during update extraction.
- Automatic relaunch through `run_gui.bat` after a successful update.
- Manual update-only mode through `run_gui.bat --update-only`.
- Updater smoke coverage for version comparison, checksum parsing, overwrite behavior and zip-slip rejection.

### Changed

- `run_gui.bat` and `run_gui_modern.bat` now start `LicenseRecoverModernGUILauncher`, which immediately starts the modern GUI and performs the update check in the background.
- Automatic updates intentionally consume only stable `vX.Y.Z` Releases; `rolling-latest` remains opt-in for testing.

### Compatibility

- A failed automatic update check does not block normal GUI startup.
- Java 8 and the existing Windows compatibility boundary remain unchanged.

## [1.0.0] - 2026-09-06

### Added

- Modern Swing GUI as the default launcher while keeping the legacy GUI as a fallback.
- Unified application detection for Java / .NET layouts and DS01xx legacy-protocol selection.
- Typed batch targets and common operation-result / process-runner infrastructure.
- Mandatory prewrite / prepatch safety backups for default high-risk write paths.
- Backup verification using file length and SHA-256 content checks.
- Java 8 source compilation and smoke tests in GitHub Actions.
- Deterministic `LicenseRecoverOverlay.jar` build.
- Standard source layout under `src/main/java` and `src/test/java`.
- Shared local/CI verification pipeline in `scripts/verify.ps1`.
- `rolling-latest` verified prerelease with `LicenseRecover-latest.zip` and `SHA256SUMS.txt`.
- Version-controlled stable release metadata through `VERSION.txt` and `release-notes/`.

### Changed

- `run_gui.bat` now starts the modern GUI through `LicenseRecoverOverlay.jar` + `LicenseRecoverGUI.jar`.
- Default mode-three launchers now route through the safe prepatch path; legacy entry points remain available.
- Batch detection no longer walks across unrelated child-directory boundaries into a parent application.
- Generated all-in-one ZIP is no longer tracked at the repository root; it is produced under `build/` and distributed through Actions / Releases.
- Production and test Java sources moved out of the repository root without changing class contents or default-package compatibility.
- User-facing distribution no longer needs development phase documents in the ZIP.

### Compatibility

- Java 8 remains the compile/runtime compatibility baseline for the Java toolchain.
- Windows 7 / Windows Server 2008 compatibility remains a GUI design constraint.
- Existing runtime JARs, .NET helper binaries and legacy BAT entry points remain available for compatibility.
