# Java 真实样本只读审计（2026-10-30）

静态阶段对本地 26 个安装目录中的 **16 个 Java 应用**进行了文件读取、SHA-256、class 结构解析及静态反汇编，没有加载或执行厂商类，没有调用注册、验证、恢复或 Patch 接口。Virbox Code 的静态还原、反汇编输出及完整私有清单均位于仓库外的审计工作目录；本文只记录脱敏结论，不包含注册码、机器标识、配置内容或厂商二进制。

用户随后明确批准直接调用原生方法后，增加了一个仅在私有独立副本内的 Java 8 受限调用对照，详见 [受限实验记录](READ_ONLY_NATIVE_BOUNDED_PROBE.md)。它在执行 `checkReInfo` 时被安全限制中断，没有得到授权有效/无效结论，也没有接入生产扫描器。

**结果：16 个应用的当前原生授权状态仍不能可靠判定。** 已能确认三种注册组件均存在验证时写入、联网或硬件访问路径，应拒绝直接接入批量只读扫描。这里的组件哈希仅用于限定此次副作用审计适用范围，绝不是可信原版/Patch 指纹对。

后续新增了 [DS24 当前授权门槛静态正向证明](JAVA_STATIC_ACCEPTANCE_PROOF.md)，可以对匹配完整代码和 wiring 的构建确认“授权检查通过（激活或 Patch）”，仍不声称原生授权有效或可信 Patch 来源。该文同时记录了辅助分析发生的一次真实 XML 误写及恢复事故；不能将本轮描述为全程只读。

## 清单与版本边界

所有应用自身的 `WEB-INF/classes` class 均为 major **52**，即 Java 8 字节码；这证明运行时下限，不证明当前实际服务 JVM 版本。框架版本来自安装文件名。未获取来源明确的产品发布版本元数据，因此下表不把目录产品码、`VersionID`、注册模式或 Spring 版本伪装成软件发布版本。

| 应用标识 | 产品发布版本 | class 数量 | Java 下限 | 框架版本线索 | 注册组件族 | 关键启动/注册入口 | 当前状态 |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| DS110102 | 未证明 | 509 | 8 | Spring Boot 2.0.5 | J1 | `com.common.sys.configuration.SysParamInit` | 未验证 |
| DS2406 | 未证明 | 329 | 8 | 传统 WAR；组件 Build-Jdk 1.8.0_181 | J2 | `listener.SystemSetListener`、`action.SoftRegAction` | 未验证 |
| DS2801 | 未证明 | 323 | 8 | Spring Boot 2.0.5 | J1 | `com.common.sys.configuration.SysParamInit`、`SoftRegisterServiceImpl` | 未验证 |
| DS3402 | 未证明 | 325 | 8 | Spring Boot 2.0.5 | J1 | `com.common.sys.configuration.SysParamInit` | 未验证 |
| DS3503 | 未证明 | 324 | 8 | Spring Boot 2.0.5 | J1 | `com.common.sys.configuration.SysParamInit` | 未验证 |
| DS3504 | 未证明 | 324 | 8 | Spring Boot 2.0.5 | J1 | `com.common.sys.configuration.SysParamInit` | 未验证 |
| DS3505 | 未证明 | 312 | 8 | Spring Boot 2.0.5 | J1 | `com.common.sys.configuration.SysParamInit` | 未验证 |
| DS50111 | 未证明 | 965 | 8 | Spring Boot 2.2.1 | J1 | `com.itmc.register.service.RegisterListener`、`com.itmc.v2.utils.RegisterUtil` | 未验证 |
| DS50112 | 未证明 | 965 | 8 | Spring Boot 2.2.1 | J1 | `com.itmc.register.service.RegisterListener`、`com.itmc.v2.utils.RegisterUtil` | 未验证 |
| DS50113 | 未证明 | 979 | 8 | Spring Boot 2.2.1 | J1 | `com.itmc.register.service.RegisterListener`、`com.itmc.v2.utils.RegisterUtil` | 未验证 |
| QT100101 | 未证明 | 281 | 8 | Spring Boot 2.2.1 | J1 | `com.itmc.register.service.RegisterListener` | 未验证 |
| QT30103 | 未证明 | 377 | 8 | Spring Boot 2.0.5 | J3 | `cn.itmc.analysis.common.sys.configuration.SysParamInit` | 未验证 |
| QT30104 | 未证明 | 377 | 8 | Spring Boot 2.0.5 | J3 | `cn.itmc.analysis.common.sys.configuration.SysParamInit` | 未验证 |
| XMT0106 | 未证明 | 225 | 8 | Spring Boot 2.0.5 | J1 | `com.itmc.register.service.RegisterListener`、`RegisterHttpServlet` | 未验证 |
| XMT0107 | 未证明 | 259 | 8 | Spring Boot 2.2.1 | J1 | `com.itmc.register.service.RegisterListener`、`com.itmc.framework.utils.RegisterUtil` | 未验证 |
| YT00138 | 未证明 | 664 | 8 | Spring Boot 2.0.5 | J1 | `com.common.global.Global`、`com.common.utils.RegisterUtil` | 未验证 |

J1 共 13 个应用，J2 一个，J3 两个。应用显示名称仍由既有静态元数据检测器解析，本文使用安装目录标识避免混淆产品发布版本与注册族版本。

| 族 | 观察到的文件名 | 原始安装文件 SHA-256 |
| --- | --- | --- |
| J1 | `WEB-INF/lib/ITMCReg.jar` | `ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa` |
| J2 | `WEB-INF/lib/ITMCReg-1.0.2.jar` | `386726692ef56ba3e8953f17423e0e160b67dd5d9eec8ef2194e18d34f8d70e4` |
| J3 | `WEB-INF/lib/ITMCReg-1.0.5.jar` | `05d848f45bd3cc63ea3d5e408e39c924c76e5d2088b73df0737f98a10477169e` |

文件名中的 1.0.2 / 1.0.5 是观察到的组件名称线索，不能独立证明发布来源或原版身份。J1/J3 的受保护 Code 不能直接用普通 javap 可靠解释；此次在独立目录按仓库既有静态 XOR 还原逻辑恢复后反汇编，未运行这些类。

## 验证语义与可复核的副作用

### 三族共有的分派

`RegisterMain.checkReInfo()` 使用反向布尔语义：返回 `true` 表示未注册/检查未通过，`false` 表示某条验证路径接受。它按照配置的注册类型顺序尝试 `CheckLocalReg()`、`CheckNet()`、`CheckHASPDog()`；不能把本地注册失败直接当作最终无效，其他分支仍可能接受。

`getRegInfo()` 是读取/分派接口，不是独立有效性证明：本地分支解密数据，硬件分支读取 HASP，网络分支调用服务；异常处理还会写日志。应用启动又可能检查返回信息中是否包含当前产品授权模式。单独 `RegStr` 非空不能证明产品、机器、期限、计次及其他启动约束均有效。

### J1 与 J3

静态还原后的 `RegisterMain.CheckLocalReg()`：

- 从组件解析出的配置路径读取 `config.xml` 的注册分支，解密后解析 `RegeditInfo`。
- 将当前机器标识与 `RegID` 比较；检查 `EndDate`、`TotalTimes` 和下一次 `UserTimes`。
- 有限计次成功分支会增加 `UserTimes`，重新加密并在字节码偏移 **490** 和 **503** 调用 `XMLFiles.writeConfig(...)`。
- 解密失败、机器不匹配、过期、计次超限和解析异常等分支调用 `XMLFiles.writeLog(...)`，因此“验证失败”也可能写文件。

`CheckNet()` 调用 `ServiceSoap_PortType.checkRegNew(...)`，成功可继续 `getNetRegInfo()` 并写回配置。`getNetRegInfo()` 调用 `ServiceSoap_PortType.getReditInfo(...)`。`CheckHASPDog()` 调用 `HASPDog.ReadRegDog()`，成功路径也会写配置。`CheckServerID()` 自身亦存在写配置与写日志路径。

J1 静态还原后的 `DesUtil.getMotherboardSN()` 包含 `getIdentifierByWindows()` / Linux 子进程查询及后备路径：`File.createTempFile`（偏移 **91**）、`FileWriter`（**104**）、`FileWriter.write`（**117**）、`Runtime.exec`（**150**）。所以连现有机器标识取得方法都不能直接充当经过审核的无副作用只读函数。

`XMLFiles.writeConfig()` 最终创建 `FileOutputStream` 并调用 dom4j `XMLWriter.write`。这是实际可达的文件写入 API，不能用方法名中有 “check” 或目录前后哈希一致来忽略。

### J2

`RegisterMain.CheckLocalReg()` 具有相同的机器绑定、期限及计次检查与写回结构；有限计次写回在偏移 **485** 和 **498** 调用 `XMLFiles.writeConfig(...)`。错误分支同样调用 `writeLog`。`CheckNet()`、`getNetRegInfo()` 和 `CheckHASPDog()` 仍包含网络/硬件与写入路径。J2 的构造参数签名与 J1/J3 不完全相同，不能只按类名复用反射调用。

### 启动入口不是安全探针

XMT0106 的 `RegisterListener.checkReInfoNew` 明确依次调用构造器、`writeRegisterUser()`、`checkReInfo()`、`getRegInfo()`；其他多个应用的初始化链也引用 `writeRegisterUser()`。该方法可能读取本机注册信息并触发网络注册与配置写入，因此不能通过执行目标启动监听器获取只读状态。

DS50111/DS50112/DS50113、QT100101、XMT0107、YT00138 的应用代码还可见 `getRegStr()` 与产品模式匹配调用。这些应用层约束必须与组件层验证一起纳入适用性审核；单一共享 JAR 哈希不能证明完整启动行为相同。

## 注册数据位置与证据限制

真实目录观察到三种布局：

- DS110102、DS3402、DS3503/4/5：`data/config.xml` 与 `WEB-INF/lib/config.xml` / `Register.xml`。
- DS50111/12/13、QT100101：`WEB-INF/classes/config.xml`，以及部分 `WEB-INF/lib/config.xml` / `Register.xml`。
- DS2406、DS2801、QT30103/4、XMT0106/7、YT00138：根目录、`data`、`WEB-INF/classes` 等不同组合。

存在多个文件时必须依据每个启动链传给构造器的路径、类加载来源及实际 API 路径规则确定生效位置，不能选择“看起来最像有效”的文件。`Register.xml` 还被当作日志目标使用，存在或内容非空不能证明授权。

本次没有把配置值或解密后的授权载荷写入公开文档。完整路径、class 调用边与观察哈希保留于本地私有审计清单。

## 可以接入的检测与尚缺证据

可以接入的是**静态副作用拒绝说明**：精确匹配上述 JAR 哈希时，解释为什么不能执行现有原生验证，继续保持灰色；未知哈希继续按未审计处理。这不授予原生有效、原生无效或 Patch 状态。

当前没有可信来源的同版本原版/Patch 文件对。无法从单个安装组件、`.svn`、备份名或既有操作历史确认 Patch；本次不往生产双指纹库添加这些安装哈希。

离线重实现需要继续闭合以下证据，才能输出深绿或白色：

1. 明确配置路径及产品模式，并覆盖应用层全部启动约束。
2. 独立实现机器标识取得与规范化，证明与目标版本各平台及后备分支语义等价；不调用厂商的临时脚本/子进程路径。
3. 审核本地载荷解密、期限、时区、计次边界和解析异常语义，以及相关代码的来源/适用范围。
4. 明确网络与 HASP 后备路径：本地校验失败不足以证明最终无效；联网和硬件状态也不能仅从磁盘静态文件推断。
5. 提供可重建的有效/无效授权测试样本与独立期望结果；针对有限计次验证不能消耗真实计次。

未完成这些条件之前，16 个应用的灰色表示证据缺口，不能解释成没有授权。本次静态审计能够证明拒绝执行特定接口的理由；没有完成允许执行厂商验证器的只读安全验收。
