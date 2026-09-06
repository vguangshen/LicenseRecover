# LicenseRecover

ITMC 云实训平台离线授权恢复工具，支持 Java 与 .NET 应用。项目面向原授权服务不可用后的本地恢复场景，保留 Java 8 / Windows 7 / Windows Server 2008 兼容边界。

当前稳定版本：**v1.2.0**

## 下载

- **稳定版**：GitHub Releases 中最新正式 `vX.Y.Z`。
- **滚动版**：`rolling-latest`，对应最近一次通过完整 CI 的 `main` 构建。
- `LicenseRecover-latest.zip`：推荐给普通用户的完整便携版，**内置 Windows x64 Java 8 JRE**。
- `LicenseRecover-update.zip`：软件自更新使用的轻量包，不重复携带 JRE。
- `SHA256SUMS.txt`：同时校验上述两个 ZIP。

## 快速开始

1. 下载并解压 `LicenseRecover-latest.zip`。
2. Windows 下直接双击 **`LicenseRecoverGUI.exe`**；也可以使用 `run_gui.bat` 作为脚本回退入口。
3. 选择应用目录并点击“检测环境”。
4. 根据检测结果使用推荐操作。

**无需另外安装 Java。** 发行包自带 `jre/`；v1.1.4 的原生 EXE 与 BAT 启动器都会优先使用 `jre\bin\javaw.exe`，仅在内置 JRE 缺失时才回退到系统 Java / `JAVA_HOME`。

## 内置 Java 运行时

便携版内置：

- Amazon Corretto 8.492.09.2
- OpenJDK Runtime `1.8.0_492-b09`
- Windows x64 JRE

运行时保留 `LICENSE`、`ASSEMBLY_EXCEPTION`、`THIRD_PARTY_README`，并在发行包中附带 `JRE_SOURCE_NOTICE.txt`。

为了避免把 100 MB+ JRE 文件重新提交进 Git 源码树，固定运行时单独维护在支持型 prerelease：

`runtime-corretto8-8.492.09.2-win-x64`

CI 构建正式 portable ZIP 时会下载该固定 runtime、校验 SHA-256，再将 `jre/` 打入最终包。

## 软件自动更新

从 v1.1.0 起，软件可以检查 GitHub 最新**正式 Release**。

- 只跟随 `vX.Y.Z` 正式版，不自动安装 `rolling-latest`。
- 下载后必须通过 `SHA256SUMS.txt` 校验才会安装。
- v1.1.1 起优先下载 `LicenseRecover-update.zip`，因此以后新增功能时通常不需要重复下载内置 JRE。
- v1.1.2 起 GUI 底部右下角直接显示当前版本与 `检查更新` 按钮。
- v1.1.4 起双击 `LicenseRecoverGUI.exe` 也会进入同一套带更新按钮的 Overlay GUI，不再绕过更新层。
- 如果某个旧 Release 没有轻量更新包，则自动回退到 `LicenseRecover-latest.zip`。
- 更新由独立临时安装器完成，成功后自动重新启动 GUI。
- 也可使用 `run_gui.bat --update-only` 手动检查更新。

仓库现已公开，未登录 GitHub 的客户端可以直接使用公共 Releases API 检查和下载更新。

## 当前界面与运行入口

- **`LicenseRecoverGUI.exe`**：v1.1.4 起的推荐 Windows 原生入口；启动现代 GUI、显示版本与右下角更新按钮，并使用与 `run_gui.bat` 相同的 Java 主类。
- `run_gui.bat`：脚本回退入口；现代 GUI + 后台检查更新 + 底部右下角更新入口。
- `run_gui_modern.bat`：显式启动现代 GUI，同样显示底部更新入口。
- `run_gui_legacy.bat`：旧 GUI 回退入口；v1.1.2 起同样修复长授权码布局并显示更新入口。
- `LicenseRecoverGUI-legacy.exe`：未修改的旧 EXE，仅作为兼容回退保留；它不会加载新的 Overlay UI。
- `run.bat`：CLI 方式一 / 方式二入口。
- `run_removenet.bat`：默认安全方式三入口。
- `run_removenet_safe.bat`：显式安全方式三入口。
- `run_removenet_legacy.bat`：旧方式三回退入口。

新版 GUI 由 `LicenseRecoverOverlay.jar` + `LicenseRecoverGUI.jar` 组合加载。

v1.1.2 对长注册申请号 / 离线授权码字段增加了宽度约束：超出可见区域的内容保留在输入框内部，不再撑宽整个面板或把右侧按钮顶出窗口。

## 支持范围

### Java 应用

支持包含 `WEB-INF` / `WEB-INF/lib` 的 ITMC Java 应用，能够识别标准布局和已知的嵌套 `WEB-INF/WEB-INF` 布局。

### .NET 应用

支持包含 `itmcRegedit.dll` / `ITMC.Web.dll` 等组件的 ASP.NET / .NET 应用。DS01xx 系列会按检测到的 `SoftVersionID` 自动选择旧协议适配。

## 三种恢复方式

- **方式一**：写入或调整本地授权相关配置。Java 写入前建立 `prewrite` 备份；v1.1.3 起生成的本地 `regName` 授权对象固定写入 `UserID=fwq`，外层 `WebSerUserID` 仍保持独立兼容字段；.NET 方式一主要用于阻断失效的自动联网授权地址，不修改 DLL。
- **方式二**：根据申请号生成离线授权码，交由应用自身的本地注册页面提交。
- **方式三**：修改授权校验相关字节码 / DLL。默认入口会先建立 `prepatch` 备份并校验后才继续。

方式三属于高风险操作，建议先扫描识别并确认目标文件；.NET 应用执行前应停止可能占用 DLL 的 IIS 应用池或相关进程。

## 安全保护

- 关键写入前强制备份；
- 备份使用文件长度 + SHA-256 内容校验；
- 备份失败时终止高风险写入；
- Java 方式三支持只扫描 / dry-run；
- 子进程统一超时与结果模型；
- modern / legacy 入口并存；
- 自更新 ZIP 做 SHA-256 校验；
- 更新器拒绝 ZIP 路径穿越；
- 轻量更新包不会删除或覆盖现有内置 JRE。

## 发行包主要文件

```text
LicenseRecoverGUI.exe            # 推荐的 Windows 原生启动器
LicenseRecoverGUI-legacy.exe     # 旧 EXE 兼容回退
LicenseRecover.jar
LicenseRecoverGUI.jar
LicenseRecoverOverlay.jar
LicenseRecover.NET/
jre/                             # Amazon Corretto 8 Windows x64 JRE
JRE_SOURCE_NOTICE.txt
run.bat
run_gui.bat
run_gui_modern.bat
run_gui_legacy.bat
run_removenet.bat
run_removenet_safe.bat
run_removenet_legacy.bat
README.md
README.txt
VERSION.txt
CHANGELOG.md
RELEASE_NOTES.md
```

## 源码结构

```text
src/main/java/   Java 生产源码
src/native/      Windows 原生 EXE 启动器源码 / 资源
src/test/java/   源码级 smoke tests
scripts/         本地 / CI 共用验证与打包脚本
release-notes/   版本化稳定 Release Notes
```

Java 类目前继续使用 default package，以保持与既有 JAR / overlay 的 class 名兼容。

v1.1.3 的构建流程会把经过 Java 8 编译与 smoke test 验证的 `LicenseRecover.class` 同步回 `LicenseRecover.jar`，避免“源码已更新但发行包仍携带旧 CLI 类”的情况。

v1.1.4 的 CI 使用 MinGW-w64 从 `src/native/LicenseRecoverGUI.c` 构建 Windows x64 GUI PE，验证其入口类/JAR/JRE 引用后再注入 portable 与 slim update 两个发行 ZIP，并重新生成 SHA-256。

## 本地验证

Windows：

```bat
scripts\verify.cmd
```

PowerShell 7 / Linux / macOS：

```powershell
./scripts/verify.ps1
```

正式 CI 还会执行 `scripts/package-native-launcher.ps1`；该步骤需要 MinGW-w64 的 Windows x64 GCC/binutils 工具链。

首次验证会从固定 runtime Release 获取内置 Corretto JRE；仓库公开后可匿名下载，CI 则使用 GitHub Token 获取。

## 版本与发布模型

- `VERSION.txt` 保存当前稳定版本号。
- 对应稳定发布说明必须存在于 `release-notes/vX.Y.Z.md`。
- `main` 每次完整验证成功后更新 `rolling-latest`。
- 当对应稳定 Release 尚不存在时，验证成功的 `main` 创建固定 `vX.Y.Z` 正式 Release。
- 正式 Release 发布 portable ZIP、轻量 update ZIP 和 `SHA256SUMS.txt`。
- 根目录不再跟踪生成 ZIP 或 JRE；大体积运行时由固定 runtime Release 管理。

更详细的工程说明见 `DEVELOPMENT.md`。
