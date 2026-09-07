# Changelog

All notable user-visible and engineering changes are tracked here from the first stable release onward.

## [1.2.10] - 2026-09-07

### Fixed

- 根据 QT40101 真实生产样本的 RegisterListener / RegisterInterceptor 字节码，确认启动阶段在读取 RegStr 前即将 `hasRegister` 与 `authorizeFlag` 置为 true，启动授权不要求 RegStr 命中 `StoreIPAddress.json` 的 ClassPid。
- QT40101 在没有旧本地授权可动态恢复时，使用具体 VersionID `QT40101` 作为经过该样本验证的最小非空 RegStr，不再停留在“待确认”。
- 规则仅对 `QT40101 + config1.xml SoftVersionID=QT401` 精确生效，不推广到其它 QT401xx 产品。

### Safety

- 仍优先使用 v1.2.9 的旧本地授权动态 RegStr 恢复；若能恢复真实 RegStr，则真实值优先于最小回退。
- 未确认的其它 classes-config 产品仍保持自动写入阻止，不恢复 44 项通用 fallback。

## [1.2.9] - 2026-09-07

### Added

- 新增现有本地授权 RegStr 只读恢复：仅当配置明确为 `regType=1` 且存在 `regName` 时，使用应用自己的 `RegisterMain.getRegInfo()` 离线读取原 RegStr。
- GUI 扫描与 CLI 一键恢复共用同一安全探测器；多个本地配置恢复出的 RegStr 不一致时保持待确认，不猜测。

### Fixed

- 根据 QT100101 实际业务代码确认其授权门槛为 `RegStr.contains(VersionID)`，且真实 VersionID 为 `QT100101`；因此无旧本地授权时也可使用经过样本验证的最小 RegStr `QT100101`，不再长期停留在待确认。

### Safety

- 动态探测绝不对 `regType=3` 调用 `getRegInfo()`，避免进入厂商网络授权分支；探测过程也不会调用 `checkReInfo()`。
- QT40101 若存在有效旧本地 `regName`，可自动恢复原 RegStr；若没有，仍保持待确认。

## [1.2.8] - 2026-09-07

### Fixed

- 根据 QT100101 实际 Java 样本确认：应用启动与本地注册均使用具体 `QT100101` 作为 RegisterMain 产品 ID，不再错误回退为 `QT1001`。
- QT100101 在 `config.xml` 没有静态 `regInfo` 时继续保持 RegStr 未确认，不猜测 `QT100101`，也不使用 44 项通用列表。

### Changed

- 批量/单应用将 QT100101 显示为 `Java / QT1001系列 / classes-config`，授权族与运行校验ID均为 `QT100101`。
- QT100101 继续由执行资格保护层阻止推荐自动写入，直到能从有效现有授权中可靠恢复动态 RegStr。

### Tests

- 新增 QT100101 `systemConfig.yml + WEB-INF/classes/config.xml` fixture，验证启动产品ID、本地注册产品ID、动态 RegStr 与自动恢复阻止。

## [1.2.7] - 2026-09-07

### Fixed

- 根据 QT40101 实际样本确认 `QT40101 → QT401` 授权族/运行校验ID，不再显示 `QT1001`。
- QT40101 及其它没有显式 `regInfo` 的未确认 classes-config 项目不再继承 44 项通用 RegStr fallback。
- 核心 Java CLI 在 RegStr 未确认时会在任何写入前失败，避免绕过 GUI 后误写。

### Changed

- Java 授权计划新增自动恢复执行资格与原因；单应用界面直接显示“禁止自动写入”原因。
- 批量扫描将信息不足的项目标为“待确认”；推荐批量一键恢复会自动跳过这些项目。
- .NET Modern 产品号未确认时也纳入推荐批量执行保护。
- 方式三保持独立高级操作，不受 RegStr 执行资格判断影响。

### Tests

- 新增 QT40101 `systemConfig.yml + config.xml + config1.xml` fixture，验证 `QT401` 识别、动态 RegStr 和自动恢复阻止。
- 新增未知 classes-config fixture，验证不会再得到 44 项 fallback。

## [1.2.6] - 2026-09-07

### Fixed

- 根据 GM00401 实际安装样本补齐 .NET `GM004` 产品族识别；此前 detector 只覆盖 itmcIEC / YX 家族，所以只能显示 SoftVersionID。
- 只有目标 ITMC.Web.dll 同时出现具体 GM00401 与家族 GM004 时才确认 ProName=GM004。
- GM004 家族 RegStr 从 DLL 内同族具体产品项提取；当前样本为 GM00401。
- 不采用样本 Web.config 中与 GM00401 冲突的遗留 productName=YX0301。

### Tests

- 新增 GM004 / GM00401 UTF-16 程序集字符串回归 fixture。

## [1.2.5] - 2026-09-07

### Fixed

- 根据实际 DS50109 样本，将本地注册族 `DS501` 与运行校验ID `DS50109` 分离；直接授权重建使用运行期具体ID，RegStr 未显式配置时使用具体 SoftVersionID。
- 根据实际 YX030506 样本，将本地注册族 `YX0305` 与运行校验ID `YX030506` 分离；保留 classes-config 中显式 `QT100101,QT100102` RegStr。
- 修复上述 classes-config 平台此前回退为 `QT1001` ProName/通用 RegStr 的误导性识别。

### Changed

- “新式 / classes-config”改为明确的 `DS501 / classes-config`、`YX0305 / classes-config`；尚未有样本证据的 classes-config 平台显示为“通用 / classes-config”。
- 单应用授权详情和批量表增加“授权族”和“运行校验ID”两个独立字段。

## [1.2.4] - 2026-09-07

### Fixed

- 修复 GitHub Release 下载节点连接较慢时 8 秒即报 `connect timed out` 的问题；连接超时提高到 30 秒、读取超时提高到 60 秒。
- 更新包下载增加 4 次自动重试，并在下载节点支持 HTTP Range 时从已下载位置继续。
- 原生 `LicenseRecoverGUI.exe` 启动 Java 时启用 Windows 系统代理发现，避免浏览器走系统代理而 Java 更新器直连失败。

### Changed

- 下载进度总大小改为使用 GitHub Release API 返回的资产 `size`，不再依赖 CDN 的 `Content-Length`，因此进度条可从 0% 到 100% 显示真实下载字节进度。
- 下载完成后额外校验实际文件长度，再继续执行既有 SHA-256 校验。

## [1.2.2] - 2026-09-07

### Added

- Java 单个应用一键恢复区新增授权计划详情：授权代际、ProName、RegStr、实际 config.xml 目标、ITMCReg 组件与原生校验状态。
- 新增共享 `LicenseRecoverModernGUIJavaPlan`，让单个 GUI、批量 GUI 与真实 CLI 使用同一套 Java 产品/配置识别规则。
- 批量表新增 SoftVersionID、ProName、RegStr、配置目标与原生校验列，并提供独立的备份、阻断联网、只预览选项。

### Changed

- 批量默认“方式一”升级为与单个应用相同的一键恢复协调器；Java 项执行后逐行显示 `RegisterMain.checkReInfo()` 结果。
- .NET Modern 批量项使用一键写回校验；.NET Legacy 保持兼容方式一，仅阻断失效授权服务，避免改变旧代协议行为。
- 批量取消会中断并终止当前 Java 子进程，避免取消后恢复任务继续在后台运行。


## [1.2.1] - 2026-09-07

### Fixed

- 修复旧版在线更新完成后通过 `run_gui.bat` 重启时可能出现“文件名/路径语法不正确”“此时不应有 &&”等 CMD 解析错误。
- v1.2.1+ 更新器安装完成后直接重新启动 `LicenseRecoverGUI.exe`，不再依赖 BAT 作为正常重启链路。

### Added

- 在线更新新增下载进度窗口，显示百分比与已下载/总大小，并显示 SHA-256 校验阶段。
- 增加更新任务互斥，避免自动检查和手动检查重复启动下载。
- 启动后自动清理旧 `LicenseRecoverGUI-legacy.exe` 与根目录 `run*.bat` 入口。

### Changed

- `LicenseRecover-latest.zip` 只暴露一个根目录 EXE：`LicenseRecoverGUI.exe`；portable 包不再包含根目录 BAT 启动器或 legacy EXE。
- `LicenseRecover-update.zip` 仅保留一个最小 `run_gui.bat` 作为 pre-v1.2.1 updater 的过渡重启桥接文件。
- CI 新增最终发行包入口结构检查：portable 根目录必须只有一个 EXE 且没有 BAT。

## [1.2.0] - 2026-09-07

### Changed

- 将多条恢复路径整合为一键恢复架构，并扩展多代 Java/.NET 自动识别。
- 增强 XMT0107、YT00129、QT30103、YX0303、DS01xx 等已验证产品代际的配置解析与本地授权兼容。
- 继续使用轻量自更新包、内置 Corretto 8 便携运行时、原生 Windows x64 启动器与完整回归验证。

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
