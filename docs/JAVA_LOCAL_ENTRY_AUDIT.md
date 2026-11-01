# Java J1 本地授权入口审核与指纹（2026-11-02）

`LicenseRecoverReadOnlyJavaLocalVerifier` 独立重现已审核构建的本地优先判据。它不装载厂商类，不调用 `RegisterMain` 构造器、`checkReInfo`、`doRegistry` 或写回方法。解码授权载荷只在内存中完成，不输出注册码、机器标识或配置值。

## 证据含义

这些 SHA-256 绑定的是**当前安装代码已审查的语义**，不是可信原版/Patch 双版本对照。未获得来源可核验的原版与修改版样本，因此不能将某个安装文件、备份名称、返回常量或历史 Patch 记录登记为“已确认 Patch”。当前结果使用独立的 `Acceptance` 维度；统一绿色不声称厂商签发、未修改原版或全部业务可运行。

本轮从此前私有静态反编译记录核对实际入口：

| 构建族 | 启动及产品规则 | 实际注册位置 |
| --- | --- | --- |
| DS1101 | Spring Boot `SysParamInit.run` 读启动产品模式，遍历 SQLite 平台候选，最终包含当前平台/产品候选；`hasRegByPlatform` 执行注册谓词与当前模式 `String.contains` | `WEB-INF/lib/config.xml` |
| DS28 | `SysParamInit.run` 先用 Servlet 根路径构造注册器；当前本地接受后检查当前产品范围。只有注册检查失败才尝试另一构造路径 | 根 `config.xml` |
| DS34 / DS35 | `SysParamInit` 与完整绑定的辅助类读取平台、启动产品模式并判当前授权范围；失败仍可能回退其他路径 | `WEB-INF/lib/config.xml` |
| DS501 | `RegisterListener.run → checkReInfo → getRegisterMain`，`SystemInfo.getSystemInfo` 的 `SystemSoft` 子节点循环最后赋值决定构造产品，`RegisterContant.versionID` 决定当前模式；本地成功还要求当前模式包含于 `RegStr` | `WEB-INF/lib/config.xml` |

代码、JAR 内 SPI/注解装载实现与启动资源必须同时匹配。九个实际应用的 `WEB-INF/web.xml` 均经主代理精确只读确认不存在，因此缺失也是审核输入；新增 `web.xml` 会失配，不能继续套用原入口。Spring Boot 编程装载逻辑由完整 class/JAR 指纹绑定，不能仅凭文件名或某一个 `checkReInfo` 方法推断实际调用。

实际启动仍可能读写日志、JS、数据库等。扫描器不模拟这些业务副作用，不把授权条件通过等同于目标站点完成启动。SQLite 前置步骤或目标宿主状态未通过运行测试，本轮没有启动目标站点。

## 正向判据

仅在以下项目均满足时，独立接受条件返回 `VERIFIED_USABLE / SATISFIED`：

1. 全部应用 `.class` 与全部顶层依赖 JAR 的数量、相对路径和 SHA-256 集合精确匹配已审核构建，注册组件为已审核 J1；不按应用目录名称返回状态。
2. 启动资源完整集合匹配，包括 `WEB-INF/classes` 下全部非 class 文件、根 `META-INF` 全部内容/缺失，以及 `WEB-INF/web.xml`、`WEB-INF/tomcat-web.xml` 的内容/缺失。`.svn` 被明确排除。DS501 的动态 `classes/config.xml` 单独解析、双读，不将含动态元数据的内容固定为信任前提。
3. 根 `systemConfig.yml` 按目标构建相同的 Java Properties 字符集规则读取，仅允许审核过的六个 `global.system.*` 键。模式来自当前 `global.system.VersionID`，不是目录名。未知装载键保守不升色。
4. 实际注册文件唯一 `reg` 节点选择本地优先模式 `1`，载荷符合审核过的规范编码、包装、扁平 JSON 与整数范围；不将非空载荷直接作为有效依据。
5. 当前机器标识由独立只读规则取得，并匹配记录绑定。J1 首选 Windows `dir C:` 的本地卷序列号规则，和 `.NET` 的 WMI/MD5 规则不同。首选规则拿不到就保持 `IDENTITY_UNAVAILABLE`，没有用历史机器号代替，也没有猜测未审核的平台回退。
6. 期限远离未观测的目标 JVM 当天边界，计次为明确无限 `-1`，应用会读取的数值字段类型有效，授权范围对当前启动产品满足目标 `String.contains` 语义。有限计次成功涉及原生递增写回，扫描器不消耗次数，因此不升绿色。
7. 对完整代码、JAR、启动资源、元数据、注册文件再次读取核对。任何缺失、增删或变化都不能保留绿色；最终内容匹配后重新按当前时间计算期限，避免完整依赖复核跨越到期边界。

路径链接、特殊文件、过大输入、重复/嵌套 XML 字段、不支持的 JSON 扩展等不会调用目标程序补判。读取或验证真正报错走扫描错误；证据不足或版本变化保留未知。

## 当前指纹组合

应用仅用于记录这次验证的样本，生产按内容指纹与元数据选择策略。同一代码构建可以接受多套分别审核过的启动资源组合。

| 构建 | class / JAR 数 | 本轮应用 | 启动资源集合 SHA-256 |
| --- | --- | --- | --- |
| DS1101-J1 | 509 / 312 | DS110102 | `daa30344493ce3416871303949b641ae38d4c54ea49080811bcf328b25049529` |
| DS28-J1 | 323 / 148 | DS2801 | `6d1efd8d56805d69bf9430062c54ef657139e51c561ebee550c72b61cf28f316` |
| DS34-J1 | 325 / 146 | DS3402 | `4cbd9eccf937e62816e7d53858612383e381622eb333526ce9df6b5f3eef1a89` |
| DS35-A-J1 | 324 / 304 | DS3503 | `4987dbbee6343cb4d1a54aa1b688359b40b0629b00726c361ac2d9566926baf9` |
| DS35-A-J1 | 同上 | DS3504 | `622a62914e810bddff29d43056fac0c85cfe7c5aa136bf34d36c890b3aadf86c` |
| DS35-B-J1 | 312 / 304 | DS3505 | `73fec168bb96c4d41013a15028cf4f7f00384f0b89ba4ebfb5a8b4d9aaf8db5a` |
| DS501-A-J1 | 965 / 160 | DS50111 | `4d782e30e56f362d7cda5c47852b4a9857ab12a478bdd909e1088e6911cdfb52` |
| DS501-A-J1 | 同上 | DS50112 | `6e61424d619e24919119813cfbaa6c7492f901e1dfae7449e0683e7dc133eb07` |
| DS501-B-J1 | 979 / 161 | DS50113 | `bf39c97d126e082c17c40fb4b8c6aa222cb9fe08b141b0a7ad4acc101270875f` |

完整 class/JAR 集合的摘要与锚点 SHA-256 位于生产 `REVIEWED` 表；没有把厂商二进制或真实授权文件提交到仓库。

## 局部失败与完整拒绝

无记录、机器不符、过期、计次不足、格式未通过等均返回带明确原因的 `LocalCondition`。注册方法还有网络/HASP 或平台后备；**本地失败不证明整个入口拒绝**。

唯一接入完整拒绝的窄分支是 DS28：本地注册明确通过、无限计次、不需写回，但当前启动产品范围不匹配。该已绑定代码在本地接受后立即进入产品检查，并不再调用备用构造器，因此返回独立 `VERIFIED_REJECTED`。它不调用 `nativeVerification(false)`，不冒充实际执行的目标原生校验。其他族产品不符或 DS28 机器/期限失败都仍为整体未知。

## 本轮验证

`LicenseRecoverReadOnlyJavaLocalVerifierTest` 使用纯合成数据覆盖有效、缺失、机器、期限、计次、产品、损坏、重复 JSON、XML 外部实体拒绝、完整拒绝范围、同大小代码变化、读取期间配置变化、新启动配置与 META-INF 注入等边界。测试生成的源码/载荷不含真实密钥或机器号。

本地独立副本的生产 API 测试核对九个应用的内容绑定与当前本机规则，不执行厂商代码。首次冷读完整双哈希对 DS11/DS35 需要十余秒，GUI 使用有限的 30 秒目录预算；超时不能按成功处理。最终两 ZIP 的实际 JRE 验证、逐应用状态与耗时由主验证报告记录。
