# Java 备用入口的只读审核

`LicenseRecoverReadOnlyJavaAlternateVerifier` 覆盖七个 Java 扫描样本中的六组精确构建（含新增 YT00123）。目录标识仅用于报告，检测依据是 anchor、完整应用 class、全部直接依赖 JAR、启动资源和本次动态输入，不按目录名套用结论。产品发布版本来源尚未证明，不能将产品码、组件文件名或 Manifest 的开发版字符串当作正式产品版本。

这里只计算当前磁盘条件下已审核的授权谓词，不执行目标监听器、构造器、`writeRegisterUser`、注册检查或 Patch。观察指纹用于限定语义，不证明原版/Patch 来源；返回接受时 `authorization` 仍为 `UNVERIFIED`，`codeIntegrity` 仍为 `UNKNOWN`。

## 实际配置路径和产品规则

| 样本 | 审核入口 | 实际本地注册路径 | 产品/后备边界 |
| --- | --- | --- | --- |
| QT100101 | `RegisterListener.run → checkReInfo → getRegisterMain` | `WEB-INF/lib/config.xml` | 两参数构造器按 CodeSource 的 `classes → lib` 默认路径读取；classes/data 同名文件不能替代；该构建未接入正向本地谓词 |
| QT30103 | `SysParamInit.run` | 根 `config.xml` | 三参数构造器明确使用 Servlet 根；遍历产品族，本地/网络/HASP 后备尚未闭合 |
| QT30104 | 同上，与 QT30103 属同组完整构建 | 根 `config.xml` | 同上，不能因为 data 配置存在而认定本地授权有效 |
| XMT0106 | `RegisterListener.checkReInfoNew` | 根 `config.xml` | 构造参数来自 Servlet 根，包含注册用户与后备调用；未接入正向本地谓词 |
| XMT0107 | `RegisterListener.run → checkReInfoNew` | 根 `config.xml` | 构造产品来自 classes `SystemSoft/SoftVersionID`；产品集合为该节 `regInfo` 加系统 `VersionID`；已审核本地优先模式才允许计算正向条件 |
| YT00138 | `SysParamInit → RegisterUtil` | 根 `config.xml` | 启动还读取数据库产品，再尝试产品映射、`data/system.json` 和通用产品后备；局部缺失不能证明全局拒绝 |
| YT00123 | 同一启动/注册链；两处业务 class 与 YT00138 不同，独立绑定完整构建 | 根 `config.xml` | 新安装对照也没有此记录；`Register.xml` 是日志用途，网络/HASP/产品后备和进程缓存尚不能否定 |

QT100101 的默认路径还依赖 URL 路径替换规则；空格、编码或路径名导致不能证明与已审核规则等价时，返回不适用，不寻找其他“看起来有效”的注册文件。

XMT0107 的旧 `checkReInfo()` 虽固定设置注册标志，实际 `run()` 却调用 New 入口，不能拿旧方法证明当前入口放行。其拦截器末尾返回 `true` 前仍可发送未注册或未授权页面重定向。具体反误判原因见 [Patch 证据边界](JAVA_PATCH_EVIDENCE_BOUNDARIES.md)。

## XMT0107 正向谓词

当前注册模式必须选择已审核的本地优先分支。独立实现复用 J1 本地原语，同时满足当前机器绑定、期限、无限计次、完整应用读取字段及实际产品集合匹配，才返回入口接受。没有原生授权证据时，不宣称厂商许可有效；没有双指纹来源时，不宣称确认 Patch。

有限计次、未知机器取得规则、未知动态配置、网络优先模式、产品元数据歧义和未支持协议均不能通过这条正向证明。任何局部失败只报告相应 `LocalCondition`，不否定网络/HASP 后备。当前六个副本中的五个有效本地路径缺少注册记录，可以报告 `NO_RECORD`；这不是“整个软件未激活”的证明。XMT0107 的当前记录可满足正向谓词，完整打包结果以逐应用最终验收为准。

目标 `getRegisterMain` 在显式路径下还可能调用 `writeRegisterUser`。审核的 J1 实现会查询系统 CRM 凭据，可能联网，并写 `WebSerUserID` / `WebSerPsw`；所见成功写入没有改变 `regType` / `regName` 本地载荷。生产检测器不执行它。正向结果不意味着原软件整个启动流程只读、无网络、不会发生日志/业务异常或全部业务可运行。

## 精确代码与启动资源

以下计数和摘要对应补全非 class 资源及容器元数据后的审核副本。集合摘要按相对路径 ordinal 排列：每行 `sha256`、两个空格、使用 `/` 的相对路径、LF；再对完整 UTF-8 文本做 SHA-256。增加、删除或改变任何集合项都会失配。

| 审核构建 | 应用 class | 直接 JAR | wiring 项 | wiring 集合 SHA-256 |
| --- | ---: | ---: | ---: | --- |
| QT1001-entry-20261031 | 281 | 147 | 39 | `cb25ed2030de926c9559fcb8f6f077d5c84a8ed056b50c683eff3c5bfac16e78` |
| QT30-entry-20261031（两个样本） | 377 | 118 | 21 | `a995ce5b0ecd8adefcdce6751db4e010b2ca2fecb8cb50d8366cb54e14d07415` |
| XMT-legacy-entry-20261031 | 225 | 107 | 15 | `9b21ce15dfdb401126debc8e6ed91085542dbd5e7d54c75b4c2bdc7fd90b775a` |
| XMT-any-mode-entry-20261031 | 259 | 154 | 35 | `b2587c8088d33f6cf8775effa3145c0922341b22bbd7bb9155c845b0a4dac7bf` |
| YT-product-chain-entry-20261031 | 664 | 278 | 94 | `79c7a275996e7ab045d0d67ed6052a231e27e9dd3d15252d30b5617a46ebaa40` |
| YT-product-chain-entry-20261102 | 664 | 278 | 94 | `485300fc386d6fcdedc03b05381fe3e0406fc155c6baf0679efd62c97ffbfe83` |

入口 anchor 只筛选候选，不能选定整个构建。YT00123 与 YT00138 的入口 class 完全相同，因此必须收集全部匹配 anchor 的审核记录，再匹配完整 class/JAR/资源集合；首个候选失配不能阻断后面的已审构建。

完整 class/JAR 摘要及 anchor 固定在源码 profile。wiring 包含 classes 下的非 class 资源及实际/缺失 `WEB-INF/web.xml` 证据，以及根 `META-INF` 的全部常规资源。新增 `WEB-INF/tomcat-web.xml` 也进入集合，使未审核容器覆盖失配。classes 根 `config.xml` 作为动态产品输入单独读取。QT100101 存在 classes 根 `license.xml`，已纳入普通 wiring；其余五个副本没有该文件，新增时同样导致指纹失配。不能因文件存在或名字像授权文件就认定有效。

`.properties` 资源仅对明确的单行 datasource 地址、用户名、密码值做规范化，其他原始启动内容参与摘要。续行或不明确的格式保留原始字节；不会把任意新 Spring/loader 属性当作数据库值忽略。动态 `systemConfig.yml` 仅允许本轮审核的 `global.system.*` 命名空间；出现其他启动属性时不能复用 XMT 正向证明。

根/库注册配置、classes 产品配置、data 配置、系统属性和 web 配置均在扫描内读取两遍，完整代码与 wiring 也复读。第二次摘要、输入内容或单文件身份变化会撤销原结果。链接、特殊文件、超限输入和损坏 class 不构成接受证据；这种复核不是原子文件系统快照。

## 容器与 loader 核查范围

本轮补充检查的 DS2406 容器 `META-INF/context.xml` 只有根 Context 定义，没有 Loader、外部资源路径或 JNDI Resource；根 Manifest 与 classes Manifest 的 `Class-Path` 为空。XMT0107 根 Manifest 没有 `Class-Path`，`Main-Class` 是构建占位符，不能证明实际可执行 JAR 入口。

XMT0107 当前应用属性未发现 `spring.config.location` / `import`、`loader.path` / `home` / `main`、外部 initializer/listener 或 main sources 配置；已见 logging、ehcache、MyBatis classpath 引用资源均位于本轮封存的资源集合中。这只说明这些已观察文件没有相应外部入口，不能证明容器全局配置、共享类路径、JVM 参数或已运行进程内存没有其他代码。检测范围是所选磁盘部署的已审核条件。

## 测试边界

合成测试不使用真实密钥或厂商二进制，覆盖：有效本地路径缺失、错误目录记录不能替代、配置存在不足以升绿、多产品任一匹配、原生/代码来源维度不冒充、机器不符、有限计次需要写回、读取期间文件变化、未知启动属性、新增 web/context 资源、重复 XML 字段、外部实体拒绝、嵌套产品节、损坏 class，以及生产不能导入合成 profile。

新增 SQL、Kotlin/Manifest 等非 class 资源会改变 wiring 摘要，不能在资源副本不完整时套用完整安装的结论。最终包还必须在随包 JRE 上运行上述行为测试，并按独立的逐应用期望表验证实际副本；测试正常退出本身不是授权接受证据。
