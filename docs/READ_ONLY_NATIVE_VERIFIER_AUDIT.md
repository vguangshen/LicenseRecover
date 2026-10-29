# 原生授权验证器：只读接入前审计记录

> 2026-10-30 实样补充：已对 16 个 Java、10 个 .NET 应用静态审计，发现具体的验证写回、网络/硬件回退与保护体缺口。见 [Java 实样审计](REAL_SAMPLE_JAVA_AUDIT.md)、[.NET 实样审计](REAL_SAMPLE_DOTNET_AUDIT.md)、[文件证据扫描与测试边界](READ_ONLY_EVIDENCE_SCAN.md)。下面 2026-10-29 记录保留为历史基线；实样审计没有批准现有目标原生方法接入只读扫描。

> 2026-10-29，审计范围：LicenseRecover 仓库中 Java/.NET 宿主与批量扫描的调用路径。
> **没有**获取、反编译或独立审核目标厂商的全部二进制样本；本记录不是对实际原生验证器无副作用的证明。

## 当前结论：禁止在批量扫描中调用现有原生验证路径

1. **Java：`LicenseRecoverJavaHost.verify()` 不是已经审计的纯只读接口。**
   - `LicenseRecoverJavaHost.main()` 在进入 `verify` 前调用 `openTarget()`，而 `verify()` 实例化目标 `RegisterMain` 并可反射调用 `checkReInfo()`、`getRegInfo()`。目标构造器和这些方法可能读写本地配置、状态或调用第三方代码。
   - `RecoverySecurityManager.checkPermission(...)` 当前允许一般权限；安全管理器主要拦截非本地 `checkConnect()` 与 `checkExit()`，**不阻止本地文件写入**。代理设置也不能证明无网络副作用。
   - 在 `--reginfo-only` 模式下，`checkReInfo()` 被跳过；验证流程仍可能在 `RegStr` 非空时输出 `RESULT: OK`。这个结果不等于当前授权已获目标程序原生校验确认，不能转化为深绿色。
   - 现有 `verify` 的语义是**写回后的恢复链检查**，不是无副作用、独立的批量状态探针。

2. **.NET：ASP.NET Host 的多数命令明确有目标目录副作用。**
   - `LicenseRecover.AspNetHost.Main()` 只有 `legacy-probe` 通过纯元数据分支直接返回；它仅能验证反射签名兼容，不是授权有效性。
   - 其他命令（包括 `legacy-verify` 和 `verify`）可经 `EnsureBridgeInTargetBin()` 将桥接 DLL 复制到目标 `bin`，并创建/关闭 ASP.NET 托管 AppDomain。即使结束时删除临时文件，也**不属于只读**。
   - `LicenseRecoverModernGUIAutoRecovery.snapshotDotNetRegistrationFiles()` 的注释明确指出 DS01xx 的 `CheckServerID` 可创建/更新 `notRegFile.xml`，`CheckLocalReg` 可在验证时更新 `config.xml`。快照/回滚不能把这条路径变成只读验证器。

3. **批量扫描保留证据分级。**
   - 目前只允许运行元数据识别和 `LicenseRecoverReadOnlyPatchScanner.scan()`；真实审核指纹库仍为空，未知保持灰色。是否调用过恢复、是否存在配置、是否曾识别 DLL/方法、是否有 `RegStr`，均不能升格为原生有效或已确认 Patch。
   - `scripts/verify.ps1` 增加了源码级保护：批量检测方法必须调用 Java/.NET 两个只读指纹扫描入口，并拒绝直接接入尚未获审计批准的 Java Host、原生恢复/校验、子进程命令入口。**这是回归防线，不是完整静态程序分析，也不是安全沙箱。**
   - 只读扫描实际文件失败为红色；没有验证证据为灰色。已确认 Patch 的浅绿色仅说明代码比对，不代表原生授权有效。

## 允许接入“原生有效/无效”判定前的验收条件

- **独立接口与行为边界**：先明确 Java/.NET 每个版本的目标原生判定方法、布尔返回语义、产品/机器身份绑定、到期/试用/联网依赖，避免将“配置存在”“注册申请号已生成”或“检查未执行”误认为有效。
- **静态审计**：分别审查目标二进制的构造器、静态初始化、所有传递调用以及平台文件/注册表/网络相关 API。对无法完整证明的分支标记不支持，不降级为“有效/无效”。
- **隔离验证**：在可重建的 Windows/Java 测试环境中，对目标安装目录、关联配置路径、注册表及进程/网络活动实施只读/拒写限制与系统级监测；包括失败、超时、异常分支，验证程序不会创建、修改、删除目标状态，也不触发第三方副作用。前后哈希相同、自动回滚或屏蔽网络**单独都不构成只读证明**。
- **真实证据与测试**：真实原版及明确变更版本必须经过独立审核，建立精确版本 + 平台 + 目标文件的原始/Patch 双指纹，测试多文件一致性、混合/未知样本及重复扫描一致性。缺少样本时继续保持生产指纹库为空。
- **最终 UI 策略**：只有独立、可复现的当前原生校验证明有效，才显示深绿；明确验证无效才显示白色。确认 Patch 为浅绿；无法验证为灰色；读取/验证失败为红色。不同证据维度必须可追溯，不能相互推断。

## 后续实施顺序

1. 保持现有扫描为只读，防止不经批准的原生接口被接入。
2. 收集获授权的、版本与来源可追溯的原版及变更文件样本，建立并复核证据，不使用历史操作日志当作样本来源证明。
3. 独立审计不同 Java/.NET 注册族的原生方法并以受限环境验证无副作用；只有通过验收的单独验证器才可接入批量扫描。

## 2026-10-30：修复 GUI 计划解析的传递原生调用

本轮最终审查发现：仅在批量检测方法中禁止直接调用 Host 不够。原有 `LicenseRecoverModernGUIJavaPlan.inspect()` 在缺少静态 RegStr 时可能调用 `ExistingLocalRegStrProbe.recover()`，该探针会初始化目标类、构造 `RegisterMain` 并调用 `getRegInfo()`。配置中的 `regType=1` 和非空 `regName` 不能证明这些方法或初始化器只读。

新增 `inspectReadOnly()`，在进入共同计划解析前明确关闭原生探针。批量扫描、单应用被动预览及旧 GUI 的检测入口使用此方法。恢复动作的兼容入口 `inspect()` 保留既有探针行为，并明确标注不属于只读验证 API；它不能用于授权状态扫描。

新增合成回归测试 `LicenseRecoverJavaPlanReadOnlyTest`：目标类静态初始化器、构造器和 `getRegInfo()` 都会写测试标记；只读计划仍能识别产品身份且不产生标记。正控制调用兼容入口确实写入标记并返回 RegStr，证明测试覆盖了原有危险回退分支。该测试不包含或执行真实厂商代码。CI 还要求批量方法调用 `inspectReadOnly()`；这仍是特定调用链的回归约束，不是通用静态分析证明。
