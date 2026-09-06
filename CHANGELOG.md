# Changelog

All notable user-visible and engineering changes are tracked here from the first stable release onward.

## [1.1.1] - 2026-09-06

### Fixed

- Restored the embedded Java runtime in the portable distribution so users can run the tool without installing Java separately.
- Portable builds now include the original runtime family used before the repository refactor: Amazon Corretto 8.492.09.2 / OpenJDK 1.8.0_492-b09, Windows x64 JRE.
- The build verifies `jre/bin/java.exe`, `jre/bin/javaw.exe`, `LICENSE`, `ASSEMBLY_EXCEPTION` and `THIRD_PARTY_README` before publishing.

### Added

- Fixed support Release `runtime-corretto8-8.492.09.2-win-x64` stores the embedded JRE outside the source tree.
- `LicenseRecover-latest.zip` is now the portable package with embedded JRE.
- `LicenseRecover-update.zip` is a smaller application-only package for future self-updates.
- `SHA256SUMS.txt` now covers both portable and slim update packages.
- The v1.1.1 updater prefers `LicenseRecover-update.zip` when available and falls back to the full portable ZIP for older Releases.
- Smoke tests verify that applying the slim update package preserves an existing embedded JRE.

## [1.1.0] - 2026-09-06

### Added

- GitHub stable Release update checks in the default modern GUI launcher.
- SHA-256 verified update download and a temporary external installer for safe Windows replacement/restart.
- `run_gui.bat --update-only` manual update check.
- ZIP path traversal protection and rollback backup support in the updater.

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
- `rolling-latest` verified prerelease with generated ZIP and checksum assets.
- Version-controlled stable release metadata through `VERSION.txt` and `release-notes/`.

### Changed

- `run_gui.bat` starts the modern GUI through `LicenseRecoverOverlay.jar` + `LicenseRecoverGUI.jar`.
- Default mode-three launchers route through the safe prepatch path; legacy entry points remain available.
- Batch detection no longer walks across unrelated child-directory boundaries into a parent application.
- Generated all-in-one ZIP is no longer tracked at the repository root; it is produced under `build/` and distributed through Actions / Releases.
- Production and test Java sources moved out of the repository root without changing default-package compatibility.

### Compatibility

- Java 8 remains the compile/runtime compatibility baseline for the Java toolchain.
- Windows 7 / Windows Server 2008 compatibility remains a GUI design constraint.
- Existing runtime JARs, .NET helper binaries and legacy BAT entry points remain available for compatibility.
