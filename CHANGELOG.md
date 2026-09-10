## [1.2.39] - 2026-09-10

### Fixed

- 修复 .NET 本地注册能够成功创建/写入授权数据，但当前站点 `SoftVersionID` 未必进入最终 `RegStr`，导致应用启动后仍显示“系统不支持任何模式”的问题。现在只从目标站点自己的 `config.xml` / `bin/config.xml` 读取当前 `SoftVersionID`，验证它与检测结果一致后，将其作为 `RegStr` 首个主模式。
- 不再让已有本地 RegStr 或 `ITMC.Web.dll` 字符串扫描结果覆盖当前系统版本；两者改为兼容模式来源，与当前 `SoftVersionID` 去重合并。仍不使用目录名、全局默认列表或跨项目推断作为执行证据。
- `.NET` CLI `gencode` 改为与 GUI 一键恢复共用 `detectDotNetRegStr()`，避免不同入口生成不同的模式集合。
- 生成前新增当前 `SoftVersionID` 必须存在于最终 `RegStr` 的硬性检查；若缺失则以 `[MODE]` 失败，不再继续进入目标 `gencode / DoRegistry / CheckReInfo`。

### Regression

- 新增 YX030308 风格回归夹具：目标 `ITMC.Web.dll` 只包含 `YX030301`、`YX030321` 兼容模式而故意不包含当前 `YX030308`，测试要求最终 RegStr 精确为 `YX030308,YX030301,YX030321`。
- CI 增加统一 RegStr 解析器、当前版本包含检查和目标 DLL 兼容模式合并的源码门禁。

## [1.2.38] - 2026-09-10

### Fixed

- 根据重新恢复的大写 `ITMC.Regedit.dll` 结构，修复仅大写注册组件的 .NET 应用仍沿用旧 Modern helper 独立运行环境的问题。大写组件会直接通过 `AppDomain.CurrentDomain.BaseDirectory` 访问站点根 `config.xml` / `Register.xml`，并使用 `NewRegistry()`、`DoRegistry(string,string)`、`CheckReInfo(ref RegeditInfo)`、`RegID/getRegNo()` 等自己的 API 表面，不能按小写组件的路径假设处理。
- `.NET` 选择优先级保持不变：存在小写 `itmcRegedit.dll` 时继续小写优先；仅小写不存在时才选择大写 `ITMC.Regedit.dll`。不同点是大写链现在也由 `LicenseRecover.NET.AspNetHost.exe` 建立真实 ASP.NET Hosted AppDomain，再在站点域内派发 `LicenseRecover.NET.Modern.exe`。
- 大写链执行时强制 `HostingEnvironment.ApplicationPhysicalPath`、`HttpRuntime.AppDomainAppPath`、`AppDomain.BaseDirectory` 都指向网站根目录，同时从目标 `bin` 解析 `ITMC.Regedit.dll`；不再让大写组件把 `bin` 当成应用根目录。
- PRE-BLOCK FIRST、失败回滚、默认 Service 本地不可达端点重写、写后再次确认联网阻断以及“不修改目标注册 DLL”均保持不变。

### Regression

- 新增 Windows/.NET Framework 大写专用集成测试，构造仅含 `ITMC.Regedit.dll` 的 YX030308 风格站点，按恢复后的大写 API 表面实际执行 `gencode -> DoRegistry -> CheckReInfo`。
- 集成测试要求大写 helper 明确在真实 Hosted AppDomain 内运行，`BaseDirectory` 必须是站点根，禁止读取 `bin\config.xml`；并验证 RegID/request-code、DoRegistry、`CheckReInfo(ref RegeditInfo)` 和临时 Bridge 清理。该链已在 Windows runner 上完整通过。

## [1.2.37] - 2026-09-09

### Fixed

- 根据真实 YX030101 在 v1.2.36 的 Windows/.NET Framework 失败日志，确认人工 `AppDomain + SimpleWorkerRequest` 即使预置 `.appDomain/.appPath/.appVPath`，`HttpServerUtility.MapPath("~/Register.xml")` 仍会在 `FileIOPermission/PathDiscovery` 处拿到空物理路径；失败仍发生在 LicenseRecover 宿主层，目标 `itmcRegedit.dll` 尚未执行。
- 小写 `itmcRegedit.dll` 链改为由 `System.Web.Hosting.ApplicationManager` 创建真实 ASP.NET `HostingEnvironment`。Host 临时将受校验的 `LicenseRecover.NET.AspNetBridge.dll` 放入目标 `bin`，由 ASP.NET 自己创建站点 AppDomain，再在该域中执行 `MapPath -> getRegNo -> ParseArgs -> RunDirect`。
- Bridge 进入目标组件前强制核对 `HostingEnvironment.ApplicationPhysicalPath`、`HttpRuntime.AppDomainAppPath`、`~/Register.xml` 与 `~/config.xml` 必须全部指向目标站点；随后仍使用目标小写 DLL 自己的申请号、`DoRegistry()` 与 `CheckReInfo()`，不修改目标 DLL。
- Host 结束后关闭对应临时 HostingEnvironment 并删除临时 Bridge；若目标 `bin` 已存在同名但不同哈希文件则 fail-closed，绝不覆盖。

### Regression

- 新增真实 Windows Server / .NET Framework 集成测试，使用 YX030101 风格 `web.config`（.NET 4.5、`identity impersonate=true`）、目标 `bin`、小写 `itmcRegedit.RegeditMain.getRegNo()` 和 `~/Register.xml` / `~/config.xml`。
- Windows 集成测试已实际通过：`HostingEnvironment` 物理根、`HttpRuntime` 应用根、两项 MapPath、16 位 request-code probe 和 `RunDirect/hosted-AppDomain` 全部成功；v1.2.36 的 `FileIOPermission/PathDiscovery` 异常已在旧宿主回归中先复现后消除。

## [1.2.35] - 2026-09-09

### Fixed

- 继续修复真实 YX030101 小写 `itmcRegedit.dll` 链在 v1.2.34 中仍停留于 `GENCODE / TargetInvocationException`：目标 `getRegNo()` 首先依赖 `HttpContext.Current.Server.MapPath("~/Register.xml")`，旧三参数 `SimpleWorkerRequest` 在手工目标 AppDomain 中仍可能无法提供可用的站点物理路径映射。
- ASP.NET Host 改为在全新的目标 AppDomain 中使用显式虚拟根 `/` + 目标物理根的五参数 `SimpleWorkerRequest`，并在调用目标注册组件前强制校验 `~/Register.xml` 与 `~/config.xml` 必须精确映射到目标网站根目录。
- `gencode` 无显式申请号时新增只读 request-code probe：直接调用 helper 的 `AppReflection.GetRegNo()` 并递归拆开 `TargetInvocationException` / `TypeInitializationException`。若目标组件仍失败，日志会给出真实内部异常，不再只显示“调用的目标发生了异常”。
- 正常 `gencode -> doreg -> verify` 算法不变；不修改目标 DLL，大写 `ITMC.Regedit.dll` 回退链保持不变。

### Regression

- 二进制产物检查只验证稳定的 Host 运行时标志；`ParseArgs/RunDirect`、MapPath 自检与 request-code probe 改由源码级断言验证，避免 UTF-16 metadata 偏移变化造成二进制字符串误报。

## [1.2.34] - 2026-09-09

### Fixed

- 修复 v1.2.33 虽已从目标 `bin` 加载小写 `itmcRegedit.dll`，但目标组件仍在 LicenseRecover 自身 AppDomain 中执行，导致 `BaseDirectory` / `ConfigurationFile` 与真实站点环境不一致并在 `GENCODE` 抛出 `TargetInvocationException`。
- 小写注册链改为目标 AppDomain：`ApplicationBase=站点根目录`、`PrivateBinPath=bin`、`ConfigurationFile=目标 web.config`，并在该目标域内重新建立 `HttpContext`、预加载目标 `itmcRegedit.dll`、调用 helper `ParseArgs -> RunDirect`。
- 不复制、不修改目标 DLL，不改变申请号、离线授权码、`DoRegistry()`、`CheckReInfo()` 算法；大写 `ITMC.Regedit.dll` 回退链保持不变。

## [1.2.32] - 2026-09-09

### Fixed

- 修复 v1.2.31 在真实 YX030101 上进入 ASP.NET host 后的 `DomainRunner` 子域加载错误。原 `LicenseRecover.NET.exe` 的 `Program.Run()` 对小写 `itmcRegedit.dll` 会创建独立 AppDomain，并用 `Assembly.GetEntryAssembly()` 定位 `LicenseRecoverNet.DomainRunner`；被 ASP.NET host 反射调用时 EntryAssembly 实际是 `LicenseRecover.NET.AspNetHost.exe`，因此子域错误地从 host 程序集中查找 `DomainRunner`。
- ASP.NET host 不再调用 helper EntryPoint。它仍加载未修改的原 `LicenseRecover.NET.exe`，使用 helper 自己的私有 `ParseArgs()` 解析完全相同的命令行，然后调用公开 `RunDirect()`，让 `gencode / doreg / verify` 与目标小写注册组件在当前 ASP.NET AppDomain 内执行。
- 该设计同时避免了另一个隐患：新建子 AppDomain 不会继承父域的 `HttpContext.Current`；同域执行可让目标 `itmcRegedit.dll` 的 `Server.MapPath("~/Register.xml")` / `MapPath("~/config.xml")` 持续看到 v1.2.30/31 建立的 ASP.NET 上下文。
- 不修改目标 `itmcRegedit.dll`，不替换目标申请号、离线授权码、`DoRegistry()` 或 `CheckReInfo()` 算法；大写 `ITMC.Regedit.dll` 回退链保持不变。

### Regression

- CI 要求 ASP.NET host 明确包含 `ParseArgs` + `RunDirect` 同域派发，并禁止重新通过 `assembly.EntryPoint` 进入 helper 的 `RunInBinDomain` 路径。
- 保持小写优先、大写回退、PRE-BLOCK FIRST、失败回滚与结构化错误日志不变。

## [1.2.31] - 2026-09-09

### Fixed

- 修复 v1.2.30 在设置 ASP.NET `.appPath/.appVPath` 后又使用五参数 `SimpleWorkerRequest` 覆盖应用路径，导致真实 .NET Framework 抛出“SimpleWorkerRequest 构造函数的无效使用”。
- 改用不覆盖应用路径的三参数 `SimpleWorkerRequest`，继续让 `~/Register.xml` / `~/config.xml` 解析到目标 Web 根目录。

## [1.2.30] - 2026-09-09

### Fixed

- 修复小写 `itmcRegedit.dll` 注册链在独立 helper 进程中调用 `NewRegistry/getRegNo/DoRegistry/CheckReInfo` 时缺少 ASP.NET `HttpContext`，导致真实 YX030101 在 `GENCODE` 阶段以 `TargetInvocationException`（“调用的目标发生了异常”）失败。
- 新增 `LicenseRecover.NET.AspNetHost.exe`：只为小写 `itmcRegedit.dll` 链建立最小 `System.Web` 宿主上下文，使用目标站点物理根目录初始化 `SimpleWorkerRequest`，再在同一 AppDomain 内转交原 `LicenseRecover.NET.exe`。目标 DLL 不修改，申请号/授权码/DoRegistry/CheckReInfo 仍全部由目标注册组件执行。
- `.NET` 选择规则保持“小写优先、大写回退”：小写链改用 ASP.NET host helper；仅大写 `ITMC.Regedit.dll` 时继续使用 `LicenseRecover.NET.Modern.exe`。
- 修复一键恢复 UI 覆盖层日志只写屏幕、不写 `logs/LicenseRecoverGUI-YYYYMMDD.log` 的问题；v1.2.29 新增的“打开日志”现在能看到完整 one-click 原生日志。

### Regression

- CI 新增 ASP.NET host 源码与产物检查，要求 `SimpleWorkerRequest`、`HttpContext.Current`、目标站点物理根目录以及 helper EntryPoint 转交链都存在。
- 保持 PRE-BLOCK FIRST：防火墙规则现在绑定真正承载目标 DLL 的 `LicenseRecover.NET.AspNetHost.exe` 进程；失败仍回滚配置文件。

## [1.2.29] - 2026-09-09

### Changed

- 重做“一键恢复未完成”错误弹窗：不再把 `[GENCODE] ... output=...` 整段原生输出挤在单行 `JOptionPane` 中。
- 错误窗口现在按“应用 / 阶段 / 注册产品 / 退出码 / 摘要 / 详细信息”分层展示，详细信息支持自动换行和滚动。
- 增加“复制详情”“打开日志”“关闭”三个操作，方便直接复制诊断内容或打开当天 `logs/LicenseRecoverGUI-YYYYMMDD.log`。
- 针对 GENCODE / GENCODE_PARSE / DOREG / VERIFY / PRE_BLOCK / HELPER / CHAIN 提供简短中文摘要，同时保留完整原生输出用于排查。

### Regression

- 新增真实 Windows `GENCODE exit=1 / 产品号 YX0302 / 调用的目标发生了异常` 长消息解析测试，确保阶段、退出码、产品号和多行详情都能稳定提取。
- CI 明确禁止恢复为直接 `JOptionPane.showMessageDialog(frame, result.message, ...)` 的单行错误弹窗。

## [1.2.28] - 2026-09-09

### Fixed

- 修复 `.NET` 批量结果全绿但部分网站重启 IIS 后仍进入激活页的“错误注册链假 OK”。真实 YX030101 样本同时包含 `itmcRegedit.dll` 与 `ITMC.Regedit.dll`；v1.2.27 统一走大写链，导致大写 `DoRegistry/CheckReInfo` 自己写、自己验通过，但网站实际优先使用的小写链无法读取该授权。
- `.NET` 原生注册链改为目标文件优先级：存在小写 `itmcRegedit.dll` 时必须使用原始 `LicenseRecover.NET.exe`；只有小写文件不存在时才回退 `ITMC.Regedit.dll` + `LicenseRecover.NET.Modern.exe`。`gencode -> DoRegistry -> CheckReInfo` 三阶段始终绑定同一条链。
- 小写链不再把 Web `ProName` 当作授权加密族。根据已恢复的目标组件常量，YX030101 的应用 `ProName=YX0301` 但注册族为 `YX0302`（`itmcYX0302` / `*ITMCYX0302OK*`）；YX0102 的应用 `ProName=YS01` 但注册族为 `market`（`itmcmarket` / `*MarketOK*`）。本版从目标小写 DLL 的成对常量自动证明 registrationProduct，无法唯一证明时 fail-closed。

### Regression

- 新增 YX030101/YX0302 与 YX0102/market 注册族回归、双 DLL 时小写优先、仅大写时回退、无法证明注册族时拒绝执行。
- 保持 PRE-BLOCK FIRST、失败回滚、目录 RegStr 证据、目标原生 `CheckReInfo` 与持久日志不变。

## [1.2.27] - 2026-09-09

### Fixed

- 根据 v1.2.26 的真实 43 项回归继续修复剩余 10 个 .NET Modern 失败：其中 7 个在防联网配置预处理阶段被无关 `config.xml` 的“无 `<reg>`”结构误伤，3 个在 `gencode` 阶段仍命中 helper 内未替换完整的小写 `itmcRegedit.dll` 引用。
- `.NET Modern` 的进程级 Windows Firewall 出站规则继续作为调用目标注册组件前的强制隔离；根目录/`bin` 中不含授权 `<reg>`/`Service` 结构的普通 `config.xml` 现在只校验 XML 后跳过，不再中止原生恢复链。
- modern-only helper 构建从“固定改 3 个 token 位置”改为“严格计数并替换全部可执行 `ldstr` 引用”：`itmcRegedit` 1 处、`itmcRegedit.dll` 4 处、`itmcRegedit.RegeditMain` 6 处；数量漂移或仍残留旧可执行引用时 CI 直接失败。
- `putElement` 增加 `<reg/>` 自闭合节点兼容，避免合法但精简的授权配置无法注入本地字段。

### Regression

- 新增无授权结构 `config.xml` 跳过测试、授权 `<reg>` 识别测试和 `<reg/>` 展开写入测试。
- 保持失败回滚、PRE-BLOCK FIRST、目录证据 fail-closed、写后原生 `CheckReInfo` 验证与 GUI 持久日志不变。

## [1.2.26] - 2026-09-09

### Fixed

- 修复 .NET Modern 一键恢复公共链：旧 `LicenseRecover.NET.exe` 的 `gencode/doreg/verify` 仍反射小写 `itmcRegedit.RegeditMain`，而现代 YX0301/YX0302/YX0303/GM004 样本使用大写 `ITMC.Regedit.RegeditMain`。构建时现在从已校验原始 helper 生成独立 `LicenseRecover.NET.Modern.exe` 适配副本；旧 helper 保持不变。
- YX030308/YX030322 这类没有小写 `itmcRegedit.dll` 的目标不再从错误程序集入口失败。
- .NET Modern 批量失败现在区分 `PRE-BLOCK / gencode / gencode parse / DoRegistry / CheckReInfo` 阶段，不再统一显示 `DoRegistry + CheckReInfo: FAILED`。
- 现代 GUI 运行日志同步持久化到 `logs/LicenseRecoverGUI-YYYYMMDD.log`，便于下一轮真实机回归定位。

### Safety

- 现代 helper 仅在构建时对固定 SHA-256 的内部兼容组件做三处受校验的元数据字符串 token 适配；源 helper 二进制不修改，hash 或 IL 前置条件不一致时 CI 直接失败。
- PRE-BLOCK FIRST、目录证据 fail-closed、失败回滚与写后 `CheckReInfo` 校验保持不变。

## v1.2.12 — 修复旧版 ITMCReg 两参数 RegisterMain 路径传参

- 根据 DS2406 真实 `ITMCReg-1.0.2.jar` 核对：其 `RegisterMain(String, String)` 第二参数是 `ConfigPath`。
- 修复旧 fallback 把加密 `json` 当成配置路径的问题；这会让 `checkReInfo()` 看不到刚写入的本地授权并错误跌落到 HASP。
- 3 参数新版仍按 `(product, json, configPath)` 调用，2 参数旧版改为 `(product, configPath)`。
- 新增双代构造器回归测试，避免以后再次把二参语义写反。
- 保留 v1.2.11 的失败回滚保护：原生校验最终未通过时恢复写入前配置。

## v1.2.11 — DS2406 真实授权族与失败回滚修复

- 依据 DS2406 生产包业务代码，将启动/本地注册授权产品号从通用 `QT1001` 修正为 `DS24`。
- DS2406 `RegStr` 使用业务启动门实际要求的具体 `VersionID=DS2406`，不再写入 44 项通用列表。
- Java 原生校验捕获 `LinkageError/NoClassDefFoundError`，避免缺可选 HASP 依赖时子进程直接崩溃。
- 原生校验最终未通过时，默认自动恢复本次写入前的 lib 与 webapp 根 `config.xml` 备份。
- 新增 DS2406 回归测试，覆盖 GUI 识别、运行 ProName、本地注册族与 RegStr。

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
