# 当前九个 Java 应用的本地证据（2026-11-08）

本轮补充的是实际授权记录判据。读取源为逐文件核对过的私有安装副本；没有启动站点、装载厂商类、调用注册器、写回次数、注册、Patch、连接目标数据库或调用目标网络服务。恢复字节码仅用于静态反汇编。

这九个应用不再统一停在 `NOT_CHECKED`。当前副本有三项生效路径缺少记录，另六项独立本地条件通过，并按同一注册/产品门槛范围接入正向 `Acceptance` 证明。六项正向结果不声称厂商原生校验已经执行；全体 `authorization` 仍为 `UNVERIFIED`、`codeIntegrity` 为 `UNKNOWN`，仍没有可核验的原版/Patch 双版本来源。最终打包文件及新鲜副本的实际回归另行记录。

## 当前真实副本结果

| 应用 | 实际本地文件 | 当前 `LocalCondition` | 具体含义 |
| --- | --- | --- | --- |
| DS50108 | `WEB-INF/lib/config.xml` | `NO_RECORD` | 文件缺失；classes 下 mode 3 的产品配置没有载荷，不能替代 |
| DS50109 | `WEB-INF/lib/config.xml` | `NO_RECORD` | 文件缺失；J3 单参数构造器与 QT1001 平台后备须独立区分 |
| PT0405 | 根 `config.xml` | `NO_RECORD` | 文件缺失；data 产品元数据不是注册记录 |
| QT100101 | `WEB-INF/lib/config.xml` | `SATISFIED` | 构造产品、当前机器、期限、无限计次、启动数值字段及当前产品范围通过 |
| QT30103 | 根 `config.xml` | `SATISFIED` | J2、本次实际构造产品与配置产品范围通过同一独立本地判据 |
| QT30104 | 根 `config.xml` | `SATISFIED` | 与 QT30103 属同一代码/依赖/资源构建，动态配置和授权记录分别读取 |
| XMT0106 | 根 `config.xml` | `SATISFIED` | 构造产品与配置 `regInfo` 范围通过；显示版本不匹配不能覆盖备用产品范围匹配 |
| YT00123 | 根 `config.xml` | `SATISFIED` | 固定映射的 QT1001 构造产品与 QT100105/QT100109 替代范围通过 |
| YT00138 | 根 `config.xml` | `SATISFIED` | YT001 通用产品后备、当前产品范围及严格到期时刻条件通过 |

六项正向证明均要求当前生效文件选择 mode 1、正确载荷解码、机器绑定一致、期限通过、次数明确无限 `-1`、实际启动读取的整数字段有效，以及相应 `String.contains` 产品范围匹配。第一个实际构造产品的本地分支必须已证明成功，不能越过未知的先行网络/HASP 分支挑选后续产品。YT 通用后备还要求先行 PaaS 文件 `data/system.json` 确实缺失，并将缺失纳入前后两次输入回执；存在该文件时只能保留该通用候选的局部 `SATISFIED`，不升入口接受。没有把载荷存在当作有效，也没有输出载荷、密钥、机器标识或凭据。局部缺失或失败均不能否定网络/HASP、其他产品后备或已运行进程缓存。

独立复核另发现完整 Bean 解析边界：目标先构造整个 `RegeditInfo`，再检查机器、期限和计次。即使选择无限计次，`userTimes`、`countDay`、`beginDate` 或 `net` 等其他属性转换失败，仍会使目标解析失败。共享本地原语现先限定已审规范字段与类型，包括所有出现的整数、Calendar 时间戳、字符串和布尔属性；不依赖 FastJSON 的字符串强制转换、智能字段别名或特殊类型键。缺失的可选属性只保留已审构造器的零/null 默认值，必需判定字段仍单独检查。未知字段、别名或类型均保持待核，不用部分字段通过掩盖整条记录无法解析。实际副本字段集合及类型另有无值回执，没有将真实载荷带入公开测试。

## 按代码选产品和路径

- **QT100101：** `RegisterListener.run → checkReInfo → getRegisterMain`。`SystemInfo` 遍历所有直接 `SystemSoft` 子节点并连续赋值，最后一个文本决定构造产品；不是固定猜测某个字段。两参数 J1 构造器使用 CodeSource 的 classes 到 lib 路径替换。当前产品模式来自根 `systemConfig.yml` 的 `global.system.VersionID`。
- **QT30103/04：** `IXmlUtil` 从根系统属性取得当前模式，从 `data/config.xml` 的 `SoftVersionID` 取得构造产品列表、从 `regInfo` 取得产品范围。J2 构造器明确使用 Servlet 根。`checkReInfo` 返回 `false` 表示成功，先选首个核心注册通过的构造产品，再检查 `regInfo` 加当前模式；产品范围失败不能跳到后面的构造产品伪造成功。诊断观察首个实际构造候选；它的本地失败尚未排除其网络/HASP 后备时，同样不猜测后续候选已被选中。
- **XMT0106：** 构造产品来自 classes 产品配置的 `SoftVersionID`，按逗号拆分；授权范围来自 `regInfo` 加当前模式。当前配置中的替代产品范围匹配，所以不能仅检查显示版本就报告整体产品不符。
- **YT00123：** 完整绑定的 `Global` 固定表将当前模式映射至构造产品 QT1001、替代范围 QT100105/QT100109。该分支先于 PaaS 与通用后备。使用 YT001 解密失败不能把此记录整体判成损坏。
- **YT00138：** 当前模式没有命中固定映射表，通用后备构造产品为 YT001。应用还有 `registerTimeValid`，要求到期时刻严格晚于当前时刻；诊断同时保留这个比库内当天边界更严格的条件。
- **DS50109：** J3 是单参数构造器，默认库路径；注册失败后还能尝试 QT1001/QT100101 平台，不能套 J1 的构造签名。
- **PT0405：** 从 `ISystemStatic._SYS_PRODUCT_ID` 选择构造产品，空值才使用 YT001；启动提供显式 Servlet 根。本轮只报告该根路径缺失记录。

共享配置布局在代码/依赖/资源绑定及前后两次动态输入读取均通过后才输出。实际机器仍由生产 `JavaMachineIdentity.current()` 独立读取；本轮实际调用返回 `complete=true`，六项机器绑定均通过。另一个直接从 Python 启动命令处理器的探针输出环境与生产包装不同，未被用来替换生产身份或据此报告机器不符；没有接入猜测的磁盘序列号后备。

## 注册/产品门槛的正向闭合

本轮与既有 J1、XMT0107 的绿色采用同一范围：证明所选磁盘部署的当前注册及产品条件，而不要求先执行完整网站、业务数据库、日志与 JS 写入。此前将这六项统一以“整站、数据库、缓存未测”保持灰色，混淆了许可条件与业务运行健康；现按实际调用链补齐窄正向证明。

| 构建 | 已闭合的成功分支（恢复后字节码偏移） | 不需要猜测的后备 |
| --- | --- | --- |
| QT100101 / J1 | `checkReInfo@14` 的组件结果为 false 时，`@29` 设置 `hasRegister=true`；`getRegInfo@50` 的 mode 1 分支读取同一有效本地载荷，`@78` 检查当前模式范围 | 本地 mode 1 先于联网/HASP；本地成功直接返回，不需证明失败后备 |
| QT30 / J2 | `run@138` 构造首个实际产品，`checkReInfo@150` 成功后 `@187` 跳出产品循环；`@264` 设置 `REGISTER_FLAG`，`RegStr.contains@418` 任一范围匹配后 `@425` 设置 `AUTHORIZE_FLAG` | 不能因为首个产品范围不符改选后续产品；两个应用复用同一完整构建及判据 |
| XMT0106 / J1 | `checkReInfo@164` 成功后 `@374` 选中产品并退出；`@544` 设置 `CHECK_REG`；用同产品与根路径再次构造，`getRegInfo@606` 读取 mode 1 同一载荷；`contains@841` 匹配后 `@848` 设置 `AUTHORIZE_FLAG` | 二次构造不另选注册分支；需要同时审核其 `getRegInfo` 读取，不只依据第一处注册成功 |
| YT 固定映射 / J1 | `Global` 固定表先选当前映射；`registerMethod@197` 设置产品标志，严格 `registerTimeValid` 通过后 `@314` 设置注册标志 | 当前 YT00123 唯一命中映射；后续 PaaS 与通用调用均因注册标志为真跳过 |
| YT 通用 / J1 | 当前 YT00138 不命中固定映射；先行 PaaS 文件确实缺失时 `RegisterPaasTraining@42 → @133`，随后通用产品 `YT001` 可达，再按相同产品与严格期限判据设置两标志 | 文件存在或输入变化不推断先行 PaaS 失败；只能保持局部观察 |

QT100101 的 J1 两参数构造器没有调用注册写回；其 `getRegInfo` 在 mode 1 下转到 `getLocalRegInfo`。XMT/YT 的显式路径及 QT30 的原方法会先调用 `writeRegisterUser`，可能查注册表、联网和写 CRM 用户字段，因此**不能执行原方法作为只读探针**。本轮独立重实现只计算许可条件。静态核查 `writeRegisterUser → doNetRegistry` 的已见成功写入是 `WebSerUserID` / `WebSerPsw`，不改 `regType` / `regName` 或计次；有限计次仍因需要消耗写回而不进入正向分支。

YT 的 `SysParamInit@61` 先读业务数据库版本对象，结果影响安装状态、来源和数据库准备，不提供许可构造产品或替代范围。当前模式来自 `IXmlUtil@96` 的系统属性赋值，映射来自完整绑定的 `Global` 固定表。`SystemInfoSetUtil.init` 查询的是登录安全设置，异常被本方法处理，不是额外许可产品来源。入口过滤器按注册标志与产品标志分别拦截；后续数据库/软件初始化页面不作为原生授权证明。额外 GPT 业务标志、动态业务插件和运行进程缓存未被冒称已验证，正向结果也不保证全部业务可运行。

## 精确构建及资源变体

完整 class、直接 JAR、入口 anchor 与资源集合摘要固定在 `LicenseRecoverReadOnlyJavaAlternateVerifier.REVIEWED`。目录名只用于结果显示，不能选择策略；QT30 两个应用共享同一完整构建。

| 应用/构建 | class / JAR 数 | 当前 wiring SHA-256 |
| --- | ---: | --- |
| DS50108 | 966 / 160 | `1949de1b30639a9760d69725d3ab0219de8bcc556f80fa6afa1f31cc33eb5025` |
| DS50109 | 738 / 136 | `540f2eadb2b8741534a03fba5a38dab70f0134e06c4d363f7a37dfe8fe4b8594` |
| PT0405 | 512 / 254 | `529d31c5338ff153ef7cb0f254e0e516c29cee81ceae9b868f99f5d497f3d020` |
| QT100101 | 281 / 147 | `2d93664f32c1728b6a3c3ba63736e34bad5c747081f689363e30fb12b998e52f` |
| QT30（两个样本） | 377 / 118 | `e5c411a91cdb8cb61646f50c7149a5d726c8a11284b91166da04c2ebe480aea1` |
| XMT0106 | 225 / 107 | `e7c5ea418ef9ab3c7cdf04443b71ce0967ef1ca49eee348d2100248d3f44d4bf` |
| YT00123 | 664 / 278 | `ec4eba595c24a3f6a528cc177367247fd61d6ae0c23bad9df86f3add21333503` |
| YT00138 | 664 / 278 | `466760bf23f0331b3800522bda80b6938a9edaee71f5bc0485b54ca34367d120` |

六个已有构建的完整 class/JAR 集合与此前审核相同。当前资源变体包含 datasource 值/格式变化、QT100101/XMT0106 新增的 classes 配置备份，以及 YT 中单独出现的 `&allowPublicKeyRetrieval` 属性行。这些内容按精确资源摘要封存；没有放宽为忽略全部 properties、备份或新启动键。

DS50108 比 DS50111 新增 `ExpertDetail.class`，并改变 `LiveDataTrainService.class`、`RaceService.class` 和 `LiveExamObjectiveScoreDetail.class`。已静态恢复检查这些差异；`RaceService` 在业务方法中读取 `RegisterContant.isKj`。本轮为它建立独立缺失记录诊断指纹，没有因其余文件相同就导入 DS50111 的完整入口接受结论。DS50109 和 PT0405 同样独立绑定。

J1 注册组件 SHA-256 为 `ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa`；QT30 J2 为 `05d848f45bd3cc63ea3d5e408e39c924c76e5d2088b73df0737f98a10477169e`；DS50109 J3 为 `386726692ef56ba3e8953f17423e0e160b67dd5d9eec8ef2194e18d34f8d70e4`。J2 的本地解密、包装、机器、日期与无限计次分支单独静态核查后才复用独立原语；相同文件名不构成协议等价证据。

## 验证和剩余范围

合成测试保留此前 43 项检查，并增加构造产品来源、首个核心通过产品的停止规则、替代范围、YT00123 映射、YT 严格期限、未知身份、有限次数、备份资源变化、动态配置双读变化和无关目录名称等检查。当前合成检查增加至 112 项，包括五种已审配置布局的正向接受与独立授权/代码来源维度、mode 2/3 必须在先行分派处停住、有限计次不升色、先行 PaaS 文件存在与读取期间新增均撤回通用正向结论，以及 YT 严格时刻边界。新鲜真实副本、两份最终 ZIP 和实际 GUI 的完整回归另行记录。

本轮不把完整启动、数据库健康、容器全局/JVM 配置或进程缓存当作已观测事实。`SATISFIED` 表示已绑定的本地判据；只有上表正向成功分支闭合、没有未知先行许可候选且输入稳定时才有 `Acceptance.VERIFIED_USABLE`。这与既有 XMT_ANY_MODE 的磁盘注册/产品门槛证明范围相同，不证明全部业务可运行或厂商签发。
