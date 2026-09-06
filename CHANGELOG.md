# Changelog

All notable user-visible and engineering changes are tracked here from the first stable release onward.

## [1.1.4] - 2026-09-07

### Added

- 新增真正的 Windows x64 `LicenseRecoverGUI.exe` 原生启动器，双击 EXE 会启动与 `run_gui.bat` 相同的 `LicenseRecoverModernGUILauncherUiPatch`。
- 原生启动器优先使用内置 `jre\bin\javaw.exe`，并支持系统 Java / `JAVA_HOME` 回退与命令行参数转发。
- CI 使用 MinGW-w64 从 `src/native/` 源码构建 EXE，并验证 Windows x64 PE / GUI subsystem、主类、JAR 与 JRE 引用。

### Changed

- 完整便携包和轻量更新包都会包含新的 `LicenseRecoverGUI.exe`。
- 原来的旧 EXE 继续以 `LicenseRecoverGUI-legacy.exe` 保留，避免破坏已有兼容回退入口。
- 原生 EXE 注入发行 ZIP 后重新计算 `SHA256SUMS.txt`，确保自更新仍执行最终包的完整性校验。

## [1.1.3] - 2026-09-07

### Changed

- Java 方式一本地授权对象现在固定写入 `RegeditInfo.UserID=fwq`，该值会被序列化并加密进 `regName`。
- 外层 `WebSerUserID` 继续作为独立兼容字段处理，不再被误认为运行时 `RegInfo.UserID`。
- 构建流程会重新编译 `LicenseRecover.class` 并同步进 `LicenseRecover.jar`，避免源码与最终发行 CLI 二进制不一致。

### Tests

- Smoke test 新增本地授权身份回归验证，确认应用到 `RegeditInfo` 后的 `UserID` 为 `fwq`。
- 构建阶段通过 `javap` 同时检查发行 `LicenseRecover.jar` 确实包含 `setUserID` 调用和 `fwq` 常量。

## [1.1.2] - 2026-09-06

### Fixed

- Long registration-request and offline-authorization-code values no longer expand the single-application panel and push action buttons off-screen.
- The legacy/full-featured GUI now constrains those long text fields so overflow stays inside the text box.

### Added

- A visible current-version label and `检查更新` button at the bottom-right of the GUI.
- Modern and legacy script launchers route through a UI patch layer while preserving the existing recovery logic and GitHub updater.

## [1.1.1] - 2026-09-06

### Fixed

- Restored the embedded Java runtime in the portable distribution so users can run the tool without installing Java separately.
- Portable builds now include Amazon Corretto 8.492.09.2 / OpenJDK 1.8.0_492-b09, Windows x64 JRE.
- The build verifies the embedded JRE before publishing.

### Added

- Fixed support Release `runtime-corretto8-8.492.09.2-win-x64` stores the embedded JRE outside the source tree.
- `LicenseRecover-latest.zip` is the portable package with embedded JRE.
- `LicenseRecover-update.zip` is a smaller application-only package for future self-updates.
- `SHA256SUMS.txt` covers both portable and slim update packages.
- The updater prefers `LicenseRecover-update.zip` and falls back to the full portable ZIP for older Releases.

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
- Backup verification using file length + SHA-256 content checks.
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
