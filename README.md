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
3. 选择 ITMC 应用目录。
4. 点击 **“一键恢复授权”**。
5. 工具会自动识别应用、生成本机授权、写入并重新读取授权做自校验。

**无需另外安装 Java。** 发行包自带 `jre/`；原生 EXE 与 BAT 启动器都会优先使用 `jre\bin\javaw.exe`，仅在内置 JRE 缺失时才回退到系统 Java / `JAVA_HOME`。

## v1.2.0：一键恢复授权

v1.2.0 起，普通用户不再需要先理解“方式一 / 方式二”的区别。默认 GUI 主流程已经合并为：

```text
选择软件目录
    ↓
自动检测 Java / .NET / 产品版本
    ↓
自动获取本机身份 / 机器码
    ↓
自动生成对应本地授权
    ↓
自动写入
    ↓
重新读取并自校验
```

主界面默认只显示 `一键恢复授权`、`自动备份`、`只预览，不写入` 等常用操作。原方式一、方式二仍保留在 **“显示手工工具（旧方式一 / 方式二）”** 中，供兼容与排障使用。

### Java 一键恢复

- 自动复用目标应用自己的 `itmc.regedit.*` 授权类取得本机身份。
- 自动生成本地 `regName`，其中 `RegeditInfo.UserID=fwq`。
- 自动写入目标 `config.xml`。
- 写入后调用目标应用自己的 `RegisterMain.checkReInfo()` 重新读取并验证授权。

### .NET / YX0302 一键恢复

v1.2.0 首个自动 .NET Adapter 支持已确认的 **`YX0302xx + ITMC.Regedit.dll`** 系列：

- 已经本地激活、网站不再显示注册页也不影响；工具会直接调用目标授权组件重新生成本机注册申请号和离线授权码。
- 从申请号中解析目标程序实际使用的 16 位 `RegID`。
- 按目标 `ITMC.Regedit.dll` 的原始本地授权算法生成 `regName`：`DES/CBC/PKCS5Padding`，密钥 `*ITMCYX0302OK*`，Key/IV 派生方式与原组件一致。
- 本地 `RegeditInfo` 明确写入 `UserID=fwq`；外层 `WebSerUserID` 仍作为独立兼容字段处理。
- 历史 `config.xml` 存在多个 `<reg>` 节点时，会同步已有冲突字段，避免旧 `regName` / `WebSerUserID` 被继续读取。
- 写入前建立 SHA-256 校验备份；写入过程异常会恢复事务前内容。
- 写入后调用 .NET helper 的 `verify` / `CheckReInfo` 路径重新读取授权；验证失败会自动回滚本次修改。

为避免误用协议，**v1.2.0 不会把 YX0302 算法套到未确认的其它 .NET 产品**。DS01xx 和其它尚未接入的一键 Adapter 会停止自动写入并提示使用高级工具。后续只需增加产品 Adapter，不需要再次改变“一键恢复”的用户操作方式。

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

仓库为 Public，未登录 GitHub 的客户端也可以直接使用公共 Releases API 检查和下载更新。

## 当前界面与运行入口

- **`LicenseRecoverGUI.exe`**：推荐 Windows 原生入口；现代 GUI、一键恢复、版本显示和右下角在线更新。
- `run_gui.bat`：脚本回退入口；进入同一套现代 GUI。
- `run_gui_modern.bat`：显式启动现代 GUI。
- `run_gui_legacy.bat`：旧 GUI 回退入口。
- `LicenseRecoverGUI-legacy.exe`：未修改的旧 EXE，仅作为兼容回退保留；它不会加载新的 Overlay UI。
- `run.bat`：CLI / 手工方式一、方式二入口。
- `run_removenet.bat`：默认安全方式三入口。
- `run_removenet_safe.bat`：显式安全方式三入口。
- `run_removenet_legacy.bat`：旧方式三回退入口。

新版 GUI 由 `LicenseRecoverOverlay.jar` + `LicenseRecoverGUI.jar` 组合加载。

## 支持范围

### Java 应用

支持包含 `WEB-INF` / `WEB-INF/lib` 的 ITMC Java 应用，能够识别标准布局和已知的嵌套 `WEB-INF/WEB-INF` 布局。一键恢复直接走现有本地 `regName` 流程并自动验证。

### .NET 应用

基础检测支持包含 `itmcRegedit.dll` / `ITMC.Web.dll` 等组件的 ASP.NET / .NET 应用。v1.2.0 的自动写入 Adapter 首先覆盖已确认的 YX0302 系列；其它版本继续保留现有手工/高级工具，待对应协议静态确认后逐步接入。

## 高级 / 手工恢复方式

一键恢复之外仍保留原有工具：

- **旧方式一**：写入或调整本地授权相关配置；Java 生成本地 `regName`，.NET 手工路径可阻断失效的授权服务地址。
- **旧方式二**：单独生成注册申请号 / 离线授权码，用于排障或兼容仍要求页面提交的旧版本。
- **方式三**：修改授权校验相关字节码 / DLL。默认入口会先建立 `prepatch` 备份并校验后才继续。

方式三属于高风险操作，建议先扫描识别并确认目标文件；.NET 应用执行前应停止可能占用 DLL 的 IIS 应用池或相关进程。

## 安全保护

- 关键写入前强制备份；
- 备份使用文件长度 + SHA-256 内容校验；
- 备份失败时终止高风险写入；
- .NET 一键恢复写入使用事务快照，写入异常或最终自校验失败时自动回滚；
- 未确认的 .NET 协议不会自动写入；
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
src/main/java/   Java 生产源码 / 一键恢复 Adapter 与编排层
src/native/      Windows 原生 EXE 启动器源码 / 资源
src/test/java/   源码级 smoke tests
scripts/         本地 / CI 共用验证与打包脚本
release-notes/   版本化稳定 Release Notes
```

Java 类目前继续使用 default package，以保持与既有 JAR / overlay 的 class 名兼容。

构建流程会把经过 Java 8 编译与 smoke test 验证的 `LicenseRecover.class` 同步回 `LicenseRecover.jar`，避免源码与最终发行 CLI 二进制不一致。CI 还使用 MinGW-w64 从 `src/native/LicenseRecoverGUI.c` 构建 Windows x64 GUI PE，并把最终 EXE 注入 portable 与 slim update 两个发行 ZIP。

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
