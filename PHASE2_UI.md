# Phase 2 — Default modern UI and safe runtime overlay

## Default entrypoints

- `run_gui.bat` now starts `LicenseRecoverModernGUI` by default.
- `run_gui_legacy.bat` keeps the original GUI available as a fallback.
- `run_removenet.bat` now routes to the safe way-3 entrypoint.
- `run_removenet_legacy.bat` keeps the original way-3 script for rollback/testing.

## Runtime overlay

`LicenseRecoverOverlay.jar` contains only the refactored UI, detection, process, result and safety classes. The existing `LicenseRecover.jar` and `LicenseRecoverGUI.jar` remain unchanged and continue to provide the established authorization/protocol implementation.

Runtime classpath order is:

- GUI: `LicenseRecoverOverlay.jar;LicenseRecoverGUI.jar`
- safe way-3 CLI: `LicenseRecoverOverlay.jar;LicenseRecover.jar`

The overlay is built by GitHub Actions from the Java sources after Java 8 compilation and smoke tests. Class-file timestamps are normalized before packaging so the overlay is reproducible. On the phase-2 refactor branch, CI synchronizes the verified overlay only when its bytes differ from the committed file.

## Safety changes

- Java way 1 creates a verified `prewrite` configuration snapshot before modifying configuration.
- Java and .NET way 3 create a verified `prepatch` backup before patching.
- Safety backups validate both file length and SHA-256 content.
- Java way-3 dry-run is normalized to scan-only so the legacy CLI cannot accidentally perform a write.
- Process execution has unified output, exit status and timeout handling.

## Detection boundaries

Single-app detection still recognizes the application root and standard `bin`, `lib`, `WEB-INF` and nested `WEB-INF/WEB-INF` layouts. It no longer walks from an unrelated arbitrary child directory into a parent application. Batch detection is strictly local to each scanned child directory.

## CI verification

The `Verify source` workflow checks:

1. Java 8 compilation of all sources.
2. Refactor smoke tests for Java/.NET detection, backups and CLI normalization.
3. Deterministic overlay generation.
4. Test-distribution assembly.
5. Modern default and legacy fallback entrypoints.
6. Overlay synchronization on the phase-2 branch only when needed.
