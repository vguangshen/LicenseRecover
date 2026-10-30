# 当前文件证据扫描（2026-10-30）

> v1.2.69 补充：新增独立 `Acceptance` 授权条件维度，不再全部未知。当前本机副本 8 项满足已审核的正向证明、18 项未验证；激活/Patch/独立条件通过统一绿色，但不混淆证据来源。详情见 [独立 .NET 条件](DOTNET_READ_ONLY_ACCEPTANCE.md) 与 [Java 静态门槛](JAVA_STATIC_ACCEPTANCE_PROOF.md)。以下 26 项均灰与原生副作用说明记录的是 v1.2.68 的旧检测能力。

本轮在 PR #54 基础上新增 `LicenseRecoverReadOnlyInspection`，复用应用检测器、五色模型和严格双指纹扫描器。GUI 批量扫描会显示检测方法、当前组件数、安全审计结论和不能验证的原因。独立命令输出 JSON Lines，每项包含应用目录标识、配置产品码、运行时/组件元数据、状态、方法、证据来源、原因、文件读取与原生验证各自的只读审计结论，以及组件相对路径、大小、SHA-256。

同时修复既有 Java 计划的间接目标调用：批量扫描和被动预览使用 `inspectReadOnly()`，禁用 `ExistingLocalRegStrProbe`。原恢复动作使用的兼容 `inspect()` 可能执行目标探针，不能当作只读验证。新增带写入哨兵的合成组件，证明只读入口不会触发静态初始化、构造器或 `getRegInfo`，并用旧入口作为可达性正对照。

```powershell
java "-Dfile.encoding=UTF-8" -cp "LicenseRecoverRuntime.jar;LicenseRecoverGUI.jar;LicenseRecover.jar" LicenseRecoverReadOnlyInspection --scan-parent D:\IndependentCopies
```

命令只向标准输出报告，不创建结果文件。自行将输出保存到仓库外私有目录。报告不输出注册值、机器标识、配置内容或绝对安装路径；应用目录名和组件哈希仍应按使用环境决定是否公开。

## 证据语义

- `version` 是配置产品标识，`versionMeaning=configuration-product-id`；`releaseVersion=unproven` 明确表示未证明产品发布版本。
- Java 格式检查只读取 ZIP/class 头和所有 class 的最高 major，报告字节码运行时下限；不是完整字节码验证、实际服务 JVM 版本或原版证明。受保护方法不被误判为授权无效。
- .NET 读取 PE32/PE32+、CLI 元数据版本和 Assembly 版本；不是实际 IIS CLR 版本。FOAP 保护体未恢复不等于组件损坏。
- 结构损坏、元数据读取或 I/O 失败为红色；文件缺失/变动、缺少可信双指纹或原生证据为灰色。哈希不命中并不证明 Patch。
- 三次文件观测与末尾双指纹复核降低陈旧证据风险，并校验状态/证据一致性；仍不是原子文件系统快照。文件可在最后一次读取后变化。
- `inspectionReadOnlyAudited=true` 仅指此处独立文件读取代码经过源码审查，不表示厂商原生方法获批。`nativeVerifierReadOnlyAudited=false` 始终单独报告。
- `LicenseRecoverReadOnlyVerifierSafety` 中观察哈希只关联已发现的副作用，输出 `REJECTED_SIDE_EFFECTS`。未命中输出 `NOT_AUDITED`；两者都不允许目标原生执行，也不提升授权或代码完整性状态。这不是原版/Patch 指纹库。

## 真实样本结果与缺口

26 个应用：Java 16，.NET 10。独立扫描输入副本包含本次检测所需的 JAR、class 和相关配置/组件，约 4.9 GB；不包含数据库、媒体等完整业务数据，也不声称它们是可启动的完整安装副本。原目录约 83.3 GB。副本及私有复制清单存放在仓库外；复制前后的源文件 SHA-256/大小/修改时间用于检查观察一致性，不被当作原生只读安全证明。

目前 26 个均有具体副作用拒绝依据，授权状态仍灰色。没有获得来源明确的同版本原版/Patch 对照，用户已确认本轮暂不提供，因此生产双指纹库保持空。真实的深绿、白色、浅绿判定数量均为零；这不是“26 个授权无效”。

逐应用、逐族、运行时线索、启动链与缺口见 [Java 审计](REAL_SAMPLE_JAVA_AUDIT.md)、[.NET 审计](REAL_SAMPLE_DOTNET_AUDIT.md)。需要继续获得：可信原版/修改版来源、尚未恢复的 .NET 小写验证体、机器/产品/期限/计次/缓存/网络/硬件回退等价语义，以及受限环境中失败、异常和超时分支的动态证据。

用户后来批准直接调用后，本轮对 Java DS2406 的独立副本进行了[受限原生调用对照](READ_ONLY_NATIVE_BOUNDED_PROBE.md)。权限限制中断依赖初始化，未得到可用的原生布尔结果；不接入生产状态判定。

## 测试与分发保障

`scripts/verify.ps1` 运行已有状态/双指纹测试，以及新的文件证据和 PE/CLI 测试。合成用例覆盖原生有效/无效的模型语义、原版/Patch、未知、损坏、缺失、混合集、文件变动、I/O 异常和敏感值不泄漏。模型合成有效用例不证明真实样本授权有效。

分发现在自动收录本次编译的全部主类及内部类，生成类名/SHA-256 清单，核对源产物与 JAR 内容，并从实际 runtime-JAR-first 类路径无初始化加载全部类。最终完整 ZIP 和更新 ZIP 解压后再次运行类清单和行为测试；Windows 使用完整包内 JRE 执行，更新包复用该 JRE。测试类仍与主类输出分离，不进入运行时 JAR。

本地可选 `LicenseRecoverRealSampleReadOnlyTest` 直接调用实际 GUI 的目录检查方法，不显示窗口、不触发恢复，对独立的 26 个样本输入副本核对识别数、颜色和副作用原因。它不纳入公共 CI 的真实数据输入；公开 CI 仅使用合成用例。
