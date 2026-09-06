# LicenseRecover

ITMC 云实训平台离线授权恢复工具，支持 Java 与 .NET 应用。项目面向原授权服务不可用后的本地恢复场景，保留 Java 8 / Windows 7 / Windows Server 2008 兼容边界。

当前稳定版本：**v1.1.0**

## 下载

- **稳定版**：GitHub Releases 中的最新正式版本（`vX.Y.Z`）。
- **滚动版**：[`rolling-latest`](https://github.com/vguangshen/LicenseRecover/releases/tag/rolling-latest)，对应最近一次通过完整 CI 的 `main` 构建。
- 每个发布都包含 `LicenseRecover-latest.zip` 与 `SHA256SUMS.txt`。

建议普通用户优先使用稳定版；需要验证最新改动时再使用 `rolling-latest`。

## 快速开始

1. 解压 `LicenseRecover-latest.zip`。
2. 确保系统可使用 Java 8 或兼容的 Java 运行时。
3. 双击 `run_gui.bat`。
4. 选择应用目录并点击“检测环境”。
5. 根据检测结果使用推荐操作。

> 新版 GUI 由 `LicenseRecoverOverlay.jar` + `LicenseRecoverGUI.jar` 组合加载。请优先使用 `run_gui.bat`，不要把直接双击旧 `LicenseRecoverGUI.jar` 当作新版 GUI 的启动方式。

## 软件自动更新

v1.1.0 起，默认 modern 启动层会在 GUI 启动后后台请求 GitHub 最新**正式 Release**：

`https://api.github.com/repos/vguangshen/LicenseRecover/releases/latest`

更新规则：

- 只跟随稳定版 `vX.Y.Z`，不会自动安装 `rolling-latest` 预发布；
- 发现新版本才弹窗，不会因为检查失败阻止软件启动；
- 下载 `LicenseRecover-latest.zip` 后同时读取 `SHA256SUMS.txt`；
- SHA-256 不一致时拒绝安装；
- 安装前会把即将覆盖的现有运行文件备份到临时回滚目录；
- 解压时阻止 `../` 等 zip-slip 路径穿越；
- 使用独立临时 Java 更新器覆盖当前目录，成功后自动重新启动 `run_gui.bat`。

手动检查更新可执行：

```bat
run_gui.bat --update-only
```

自动更新依赖 GitHub 无登录访问，因此仓库需要保持 **Public**。将来发布新功能时，只需按正常版本流程提高 `VERSION.txt`、增加对应 `release-notes/vX.Y.Z.md` 并合入 `main`；CI 验证通过后会生成新的稳定 Release，旧版本软件即可发现并更新。

## 当前界面与运行入口

- `run_gui.bat`：默认 modern GUI + GitHub 稳定版更新检查。
- `run_gui_modern.bat`：显式启动 modern GUI + 更新检查。
- `run_gui_legacy.bat`：旧 GUI 回退入口。
- `run.bat`：CLI 方式一 / 方式二入口。
- `run_removenet.bat`：默认安全方式三入口。
- `run_removenet_safe.bat`：显式安全方式三入口。
- `run_removenet_legacy.bat`：旧方式三回退入口。

现代 GUI 使用“应用检测 → 检测结果 → 推荐操作 → 其他方式 / 高级操作 → 日志”的信息层级；批量模式使用类型化目标模型并避免跨目录误识别。

## 支持范围

### Java 应用

支持包含 `WEB-INF` / `WEB-INF/lib` 的 ITMC Java 应用，能够识别标准布局和已知的嵌套 `WEB-INF/WEB-INF` 布局。

### .NET 应用

支持包含 `itmcRegedit.dll` / `ITMC.Web.dll` 等组件的 ASP.NET / .NET 应用。DS01xx 系列会按检测到的 `SoftVersionID` 自动选择旧协议适配，其他产品继续使用新版路径。

## 三种恢复方式

- **方式一**：写入或调整本地授权相关配置。Java 写入前建立 `prewrite` 备份；.NET 方式一主要用于阻断失效的自动联网授权地址，不修改 DLL。
- **方式二**：根据申请号生成离线授权码，交由应用自身的本地注册页面提交。
- **方式三**：修改授权校验相关字节码 / DLL。默认入口会先建立 `prepatch` 备份并校验后才继续。

方式三属于高风险操作，建议先扫描识别并确认目标文件；.NET 应用执行前应停止可能占用 DLL 的 IIS 应用池或相关进程。

## 安全保护

重构后的默认路径增加了以下保护：

- 关键写入前强制备份；
- 备份使用文件长度 + SHA-256 内容校验；
- 备份失败时终止高风险写入；
- Java 方式三支持只扫描 / dry-run 路径；
- 子进程执行统一超时与结果模型；
- modern / legacy 入口并存，可随时回退；
- 更新包在覆盖前必须通过 GitHub SHA-256 校验；
- 更新 ZIP 解压包含路径穿越保护。

## 发行包主要文件

```text
LicenseRecover.jar
LicenseRecoverGUI.jar
LicenseRecoverOverlay.jar
LicenseRecover.NET/
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

`LicenseRecoverGUI-legacy.exe` 仅作为旧 GUI 兼容回退保留。

## 源码结构

```text
src/main/java/   Java 生产源码
src/test/java/   源码级 smoke tests
scripts/         本地 / CI 共用验证脚本
release-notes/   版本化稳定 Release Notes
```

Java 类目前继续使用 default package，以保持与既有 JAR / overlay 的 class 名兼容。

## 本地验证

Windows：

```bat
scripts\verify.cmd
```

PowerShell 7 / Linux / macOS：

```powershell
./scripts/verify.ps1
```

统一验证链路会执行 Java 8 编译、smoke tests、deterministic overlay 构建、发行包组装、入口检查和 SHA-256 生成。

## 版本与发布模型

- `VERSION.txt` 保存当前稳定版本号，例如 `1.1.0`。
- 对应稳定发布说明必须存在于 `release-notes/v1.1.0.md`。
- `main` 每次完整验证成功后更新 `rolling-latest` 预发布。
- 当 `VERSION.txt` 对应的稳定 Release 尚不存在时，验证成功的 `main` 会创建固定的 `vX.Y.Z` 正式 Release；同一版本后续提交不会覆盖该稳定 Release。
- 软件自动更新读取 GitHub 的 latest stable Release，因此后续新增功能只需走同一版本化发布链路，无需再改更新地址。
- 根目录不再跟踪生成的 `LicenseRecover-latest.zip`；发行 ZIP 只存在于 `build/`、Actions Artifact 和 GitHub Release 中。

## 开发说明

更详细的工程结构、验证与发布约束见 [`DEVELOPMENT.md`](DEVELOPMENT.md)。历史重构说明保留在 `PHASE1_REFACTOR.md` ～ `PHASE5_VERSIONING.md`。
