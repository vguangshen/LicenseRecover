# Java 当前授权门槛的静态正向证明

## 状态语义

`LicenseRecoverReadOnlyJavaVerifier` 只在完整代码与启动/拦截器配置匹配已审核构建时返回独立蓝色“授权检查通过（激活或 Patch）”。这个维度表示当前磁盘代码的授权门槛会放行，不证明厂商签发的原生授权，也不声称存在来源可信的原版/Patch 文件对。

`authorization` 仍为 `UNVERIFIED`，`codeIntegrity` 仍为 `UNKNOWN`；接受证据单独存放在 `acceptance`。不匹配、缺少输入、代码或配置变化保持未验证；实际读取或格式错误为检测错误。这个检测器不会调用厂商构造器、初始化器、注册方法、硬件方法或子进程，不读取或解密真实注册码。

## DS24 构建的行为证明

审核对象为 DS2406 当前安装构建，应用类均为普通 Java 8 字节码。相同完整构建可位于其他目录；检测器不根据目录名称或一次历史操作决定状态。

1. `global.IGlobal.<clinit>` 的偏移 29/30 无条件写入 `REGISTER_FLAG=true`，37/38 无条件写入 `AUTHORIZE_FLAG=true`。此前仅进行 JDK 字符串与路径分隔符运算，没有读取授权或外部配置。
2. `listener.SystemSetListener.contextInitialized` 首先调用 `IXmlUtil.parseSystemConfigXml`。该方法不引用上述两个字段，也没有反射字段写入；它读取的数据不能改变这两个初始化值。
3. 监听器偏移 29/32 读取 `REGISTER_FLAG` 并在为真时跳至 279，跳过整个原生验证、产品模式检查及其中的标志变更路径。279/285 继续确认两个授权标志为真后进入数据库初始化。
4. `interceptor.AfterInterceptor.intercept` 的偏移 0/3 因 `REGISTER_FLAG=true` 跳过 `reg` 拒绝；9/12 因 `AUTHORIZE_FLAG=true` 跳过 `unauthorized` 拒绝。后续 `CREATE_FLAG` 对应数据库准备情况，不是原生授权证据；本结果也不保证数据库或业务系统可启动。
5. 全部 329 个应用 class 中，只有 `IGlobal`、`SystemSetListener`、`AfterInterceptor` 引用这两个字段；其他直接写入位于已跳过的监听器分支。应用 class 中没有 `Field.set` 写入点。全部 145 个依赖 JAR 中没有这三个目标类的同名副本。
6. 固定的 `web.xml` 绑定了该监听器；固定的 Struts 配置集绑定相应拦截器/provider。配置中的类路径与上述行为共同构成证明，单独一个布尔值、字符串或方法名称不足以得出结果。

这是现有代码默认授权门槛接受的证据。其成因可能是原生配置或代码修改，本次没有足够来源证据细分。它不依赖“注册文件存在”“RegStr 非空”或原生方法执行没有报错。

## 精确输入集合

集合摘要规则：按相对路径的 ordinal 顺序排列，每行是 `sha256`、两个 ASCII 空格、使用 `/` 的相对路径、一个 LF；再对完整 UTF-8 文本做 SHA-256。集合增加、删除或任一文件内容变化都会失配。

| 输入集合 | 数量 | 路径相对根 | SHA-256 |
| --- | ---: | --- | --- |
| 全部应用 class，递归读取，跳过 `.svn` | 329 | `WEB-INF/classes` | `c541462d172101b1b4359006aadc99b91c8882da01dd1b09ec5b8ef8cc01187d` |
| `WEB-INF/lib` 直接包含的全部 JAR | 145 | `WEB-INF/lib` | `172eff9cdf258f709aae5c2c898a5f545ead3a3226c1a1e073a4e66541d915e3` |
| `WEB-INF/web.xml` 与 classes 中的全部 `struts*.xml` / `struts*.properties` | 9 | 应用根目录 | `0df89907d9b0362ce950a96e69bbfa5b51dfb6b6da818990f846b6b200ae5070` |

`WEB-INF/web.xml` 的原始字节 SHA-256 为 `23a521e1f90a8126d8406502a48f082678e192dd180b3d17ea199369e01f9ee2`，大小 2951 字节。

配置摘要只绑定已经审核的启动和 Struts wiring。注册载荷、数据库密码等未被此门槛消费的配置不作为接受条件；其存在或变化不会产生授权接受证据。代码、依赖和 wiring 均需两次完整读取一致，并检查单文件元数据/身份是否在读取期间变化。这仍不是原子文件系统快照。

## 验证与适用限制

`LicenseRecoverReadOnlyJavaVerifierTest` 仅使用合成文件，覆盖完整审核凭据接受、未审核构建、授权/数据库配置无关性、新增或改动 class/JAR/provider、wiring 变动、输入缺失、损坏 class、损坏审核记录及输入快照变化。测试同时断言接受不会伪装成原生有效或可信 Patch 状态。

私有原始扫描输入副本实测匹配此构建并返回 `VERIFIED_USABLE`，一次完整双读约 1.6 秒；厂商文件与配置没有加入测试夹具或提交仓库。其他 15 个 Java 构建尚未匹配此证明，保持未验证。有限计次、机器绑定、产品模式及网络/HASP 后备等本地原生谓词仍需分别审核，不能从此静态放行证明推断其他版本可用。

## 审计执行事故

收集辅助摘要时，一个私有分析脚本因循环变量覆盖，误将审计 JSON 写入真实 DS2406 的 `WEB-INF/web.xml`。主代理已向用户披露，并使用最初已校验的原始副本恢复原字节与原修改时间；恢复后的哈希与上述原始值一致。出错脚本已永久禁用，后续私有输出增加目录边界检查。因此不能声称本轮所有辅助分析操作始终只读。

该事故不应作为任何授权或 Patch 证据。生产检测器没有文件写入代码，也不执行上述私有分析脚本；审核记录绑定的是事故前保留、已经恢复的原始 XML 字节。
