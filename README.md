# LicenseRecover

ITMC 云实训平台离线授权恢复工具，支持 Java 与 .NET 应用。项目面向原授权服务不可用后的本地恢复场景，保留 Java 8 / Windows 7 / Windows Server 2008 兼容边界。

当前稳定版本：**v1.2.27**

## 下载

- **稳定版**：GitHub Releases 中最新正式 `vX.Y.Z`。
- **滚动版**：`rolling-latest`，对应最近一次通过完整 CI 的 `main` 构建。
- `LicenseRecover-latest.zip`：推荐给普通用户的完整便携版，内置 Windows x64 Java 8 JRE。
- `LicenseRecover-update.zip`：软件自更新使用的轻量包，不重复携带 JRE。
- `SHA256SUMS.txt`：同时校验上述两个 ZIP。

## 快速开始

1. 下载并解压 `LicenseRecover-latest.zip`。
2. Windows 下直接双击 **`LicenseRecoverGUI.exe`**。
3. 选择应用目录并点击“检测环境”。
4. 根据检测结果使用推荐操作。

**无需另外安装 Java。** 便携版自带 `jre/`，原生 `LicenseRecoverGUI.exe` 会优先使用 `jre\bin\javaw.exe`，仅在内置 JRE 缺失时回退到系统 Java / `JAVA_HOME`。

从 v1.2.1 起，完整便携包只保留一个用户可见的根目录 EXE 启动入口：`LicenseRecoverGUI.exe`。旧 `LicenseRecoverGUI-legacy.exe` 与根目录 BAT 启动器不再放入 portable ZIP。

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

软件只跟随 GitHub 最新**正式 Release**，不会自动安装 `rolling-latest`。

- 优先下载 `LicenseRecover-update.zip`；旧 Release 没有轻量包时自动回退到 `LicenseRecover-latest.zip`。
- 下载后必须通过 `SHA256SUMS.txt` 校验才会安装。
- v1.2.1 起下载过程显示实时进度、百分比与已下载/总大小（服务器提供大小时）。
- v1.2.4 起进度总大小直接取 GitHub Release 资产元数据，不再依赖下载节点是否返回 Content-Length；进度条从 0% 到 100% 对应实际收到的字节数。
- v1.2.4 起下载连接启用更长超时、4 次自动重试与 Range 断点续传，并让内置 Java 使用 Windows 系统代理设置。
- v1.2.18 起下载链升级为多线路：Java 系统代理 / HTTPS_PROXY / 直接连接，并手动安全跟随 GitHub HTTPS 重定向；Java 链全部失败后自动切换 Windows WinINet 系统网络栈，继续保留文件长度与 SHA-256 双校验。
- v1.2.1 起新 updater 安装完成后直接重新启动 `LicenseRecoverGUI.exe`，不再通过 `cmd.exe` + BAT 完成重启。
- 为兼容 v1.1.x/v1.2.0 已安装客户端，轻量更新包暂时保留一个最小 `run_gui.bat` 过渡文件；旧 updater 用它完成最后一次 BAT 式重启后，新程序会清理旧入口。
- 因为旧客户端在下载 v1.2.1 时仍运行旧 updater 代码，所以“第一次从旧版升级到 v1.2.1”的下载本身不会凭空出现新进度条；升级成功后，后续更新都会使用新进度 UI。
- 更新器拒绝 ZIP 路径穿越，并在覆盖前保留回滚备份。

仓库为 Public，未登录 GitHub 的客户端也可以直接使用公共 Releases API 检查和下载更新。

## 当前界面与唯一启动入口

**`LicenseRecoverGUI.exe`** 是普通用户唯一需要打开的入口。它会：

- 启动现代 Overlay GUI；
- 显示当前版本和右下角“检查更新”；
- 优先使用内置 JRE；
- 继续加载 Java / .NET 恢复逻辑以及一键恢复功能。

`LicenseRecover.NET/LicenseRecover.NET.exe` 属于内部 .NET 兼容辅助组件，不是用户启动入口。

## 支持范围

### Java 应用

支持包含 `WEB-INF` / `WEB-INF/lib` 的 ITMC Java 应用，能够识别标准布局和已知的嵌套 `WEB-INF/WEB-INF` 布局，并覆盖当前已验证的 YT、XMT、QT30xxx、DS28xx 等产品代际。

从 v1.2.2 起，单个应用的一键恢复区会直接显示 **授权代际、SoftVersionID 对应的 ProName、实际 RegStr、将写入的 config.xml 位置、授权组件以及 RegisterMain 原生校验结果**。批量页使用同一套识别模型，扫描后会逐项展开这些信息，并让批量“一键恢复授权”走与单个应用相同的恢复/校验链路。

v1.2.5 根据实际 DS50109 / YX030506 样本，把 classes-config 平台拆成 **授权族、运行校验ID、RegStr** 三层：DS501xx 的本地注册族为 `DS501`、运行校验使用具体 SoftVersionID；YX0305xx 的本地注册族为 `YX0305`、运行校验同样使用具体 SoftVersionID，并保留 classes/config.xml 明确声明的 RegStr。尚无真实样本证据的 classes-config 产品显示为“通用 / classes-config”，不再笼统称为“新式”或把兼容回退伪装成已确认代际。

v1.2.3 修复了批量扫描在第一个 Java 项目处因 GUI classpath 耦合而提前终止的问题，并为每个子目录增加异常隔离；单个异常项目只会显示“检测异常”，不会阻断后续应用。根据 DS2802 实际样本，DS28xx 代际会使用 `ProName=DS28`，在未提供显式 `regInfo` 时把当前 `SoftVersionID`（例如 `DS2802`）作为有效 `RegStr` 产品项。

### .NET 应用

支持包含 `ITMC.Web.dll` 与 `ITMC.Regedit.dll` / `itmcRegedit.dll` 等组件的 ASP.NET / .NET 应用。DS01xx、YX0301/YX0302/YX0303 等已验证家族会按检测结果自动选择兼容路径。 v1.2.6 根据 GM00401 实际样本新增 GM004 家族：config.xml 为 GM00401，且 ITMC.Web.dll 同时包含 GM00401 与 GM004 时确认 ProName=GM004，并提取 RegStr=GM00401；样本 Web.config 中遗留的 productName=YX0301 不参与授权产品识别。

## 三种恢复方式

- **方式一**：写入或调整本地授权相关配置。Java 写入前建立 `prewrite` 备份；生成的本地 `regName` 授权对象固定写入 `UserID=fwq`，外层 `WebSerUserID` 保持独立兼容字段。
- **方式二**：根据申请号生成离线授权码，交由应用自身的本地注册页面提交。
- **方式三**：修改授权校验相关字节码 / DLL。默认路径会先建立 `prepatch` 备份并校验后继续。

方式三属于高风险操作，建议先扫描识别并确认目标文件；.NET 应用执行前应停止可能占用 DLL 的 IIS 应用池或相关进程。

## v1.2.7 QT40101 与执行资格保护

- 根据 QT40101 实际 `systemConfig.yml`、`WEB-INF/classes/config.xml`、`config1.xml` 与业务代码证据，将其识别为 `SoftVersionID=QT40101`、授权族/运行校验ID=`QT401`。
- QT40101 的配置没有静态 `regInfo`；应用启动后通过 `RegisterMain("QT401").getRegInfo().getRegStr()` 读取授权项，因此本工具不会再显示或写入旧的 44 项通用 fallback。
- Java `classes-config` 在授权族或 RegStr 未确认时标记为“待确认”，一键恢复和批量推荐操作都会在写文件前自动阻止/跳过。
- 同样保留 .NET Modern 的产品号安全门：产品号未确认时不会执行自动写回。
- 方式三是独立高级操作，不依赖本地授权重建所需的 RegStr，因此不受上述推荐操作资格判断影响。

## 安全保护

- 关键写入前强制备份；
- 备份使用文件长度 + SHA-256 内容校验；
- 备份失败时终止高风险写入；
- Java 方式三支持只扫描 / dry-run；
- 子进程统一超时与结果模型；
- 自更新 ZIP 做 SHA-256 校验；
- 更新器拒绝 ZIP 路径穿越；
- 轻量更新包不会删除或覆盖现有内置 JRE；
- v1.2.1 起 portable 发行包校验“根目录只有一个 EXE，且没有 BAT 启动入口”。

## 发行包主要文件

```text
LicenseRecoverGUI.exe            # 唯一用户启动入口
LicenseRecover.jar
LicenseRecoverGUI.jar
LicenseRecoverOverlay.jar
LicenseRecover.NET/              # 内部 .NET 兼容辅助组件
jre/                             # Amazon Corretto 8 Windows x64 JRE
JRE_SOURCE_NOTICE.txt
README.md
README.txt
VERSION.txt
CHANGELOG.md
RELEASE_NOTES.md
```

说明：`LicenseRecover-update.zip` 为兼容旧 updater，会暂时额外携带一个最小 `run_gui.bat` 过渡文件；完整 `LicenseRecover-latest.zip` 不含任何根目录 BAT 启动器。

## 源码结构

```text
src/main/java/   Java 生产源码
src/native/      Windows 原生 EXE 启动器源码 / 资源
src/test/java/   源码级 smoke tests
scripts/         本地 / CI 共用验证与打包脚本
release-notes/   版本化稳定 Release Notes
```

Java 类继续使用 default package，以保持与既有 JAR / overlay 的 class 名兼容。

## 本地验证

Windows：

```bat
scripts\verify.cmd
```

PowerShell 7 / Linux / macOS：

```powershell
./scripts/verify.ps1
```

正式 CI 还会执行 `scripts/package-native-launcher.ps1`；该步骤使用 MinGW-w64 构建 Windows x64 GUI PE，并对最终 portable/update ZIP 做入口结构检查后重新生成 SHA-256。

## 版本与发布模型

- `VERSION.txt` 保存当前稳定版本号。
- 对应稳定发布说明必须存在于 `release-notes/vX.Y.Z.md`。
- `main` 每次完整验证成功后更新 `rolling-latest`。
- 当对应稳定 Release 尚不存在时，验证成功的 `main` 创建固定 `vX.Y.Z` 正式 Release。
- 正式 Release 发布 portable ZIP、轻量 update ZIP 和 `SHA256SUMS.txt`。
- 根目录不跟踪生成 ZIP 或 JRE；大体积运行时由固定 runtime Release 管理。

更详细的工程说明见 `DEVELOPMENT.md`。
