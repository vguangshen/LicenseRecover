# .NET 独立只读授权条件判定

本检测器证明的状态是：**当前安装代码所对应的本地授权核心条件和网站当前产品/模式条件，通过独立只读重实现检查**。界面可以以独立蓝色显示“授权检查通过（激活或 Patch）”，但不区分授权来源，也不证明厂商原版、可信 Patch 来源或所有业务功能正常。`Authorization` 和 `CodeIntegrity` 仍为未验证；结果使用独立的 `Acceptance.VERIFIED_USABLE` 维度。

扫描器没有启动网站、加载目标程序集、调用 `RegisterMain`、写注册表、修改授权文件或消耗授权次数。原有厂商 `CheckLocalReg` 仍存在写入，不能直接接入只读扫描。

## 审核范围与精确构建绑定

`LicenseRecoverReadOnlyDotNetVerifier` 仅支持以下 3 组当前构建；按代码组合复用策略，不按目录名设状态：

| 当前安装应用 | Website SHA-256 | Registration SHA-256 | 审核产品 |
| --- | --- | --- | --- |
| DS0101、DS0109 | `16b13b547893b160ef928fd6f06568770da1fdd2326a1dfdd4bc5c54e047562c` | `735a7f868825111cd437cb5093eaa79f0a977330a79121fb2d18885290da743f` | itmcIEC |
| DS0105、DS0107 | `21464d180e30ed1f10c0c6b20e56381191ecd70aab06595f07a27dc4d559961a` | `25b6b2939534b8266d843f7bd7b942d9ee87fd48296c8ed05828ffc3de76c0d8` | itmcIEC |
| YX030308、YX030321、YX030322 | `57fa95aa72a15bfd569834374860f29dbef22a48294f7010e94846023a94ce35` | `f5a2c38dee0f694c33fdac2c5570c2431b955c594e244e87596f120c0eb07f99` | YX0303 |

这些是语义审核的**当前构建绑定**，不是“原版/Patch 双指纹”。另对直接参与语义的密码实现、JSON 日期解析和依赖精确绑定：

- DS01xx 的 `ItmcBtoB.DBUtility.dll`：DS0101/09 为 `9430693eea94e18077fb1fa0e187a0d4cb6c4bf472eca4cdd075366e9b8eb92c`，DS0105/07 为 `7d880b03364e4c31c39c47a053144345b5b02804ac7a0e80a87ebf24096fd999`。
- YX0303 的 `ITMC.DBUtility.dll`（类型命名空间为 `Maticsoft.DBUtility`）：`f8c2ff0b28b03bfc33ce61e32f63d8b179c0c213a4ccab8ba748aa4dd73d5c22`。
- DS01xx 的 `Newtonsoft.Json.dll` 6.0：`78bedd9fce877a71a8d8ff9a813662d8248361e46705c4ef7afc61d440ff2eeb`；YX0303 的 7.0：`9eca2c5ed187b5e1319c359aabf8382992131efe1854b899c23a93fe227496e3`。
- `LiteDB.dll`：`4091c455a882e3e44c8dc0c4034e6b2536f180ce019695769ef72497a90467ec`；日志 DLL 也分别绑定当前 DS/YX 构建，见检测器 `Build`。未知依赖不提升状态。

缺少组件、构建不同、未审核的数据结构或条件未满足，都保持未知；不能由单一路径失败推出整套授权无效。

## 本地正向谓词

依据恢复后的 CIL，审核版本的 `RegeditMain.CheckReInfo(ref info)`（代表样本 MethodDef `0x06000063`）在 `regType=1` 时首先检查本地授权。本地成功直接使外层返回 `false`，表示无需注册；不会进入网络/硬件狗回退。当前只实现该优先分支。

`CheckLocalReg`（`0x06000070`）的当前本地接受条件包括：

1. 从**网站根目录 `config.xml`** 的直接 `reg` 节点读取 `regName`；没有回退到 `bin/config.xml` 或备份文件。
2. 根据本次网站固定产品身份，以 CIL 对应的 DES/CBC/PKCS7、MD5 派生规则独立解密；剥去前后各 6 个包装字符，严格解析 JSON。不会只因解密成功判为通过。
3. 核对载荷 `RegID` 与独立读取的当前本机身份；另外保守要求 `ProName` 与已审核网站产品一致。
4. 检查 `EndDate.Date >= 当前本机日期`。只接受已经审核的 Microsoft `/Date(milliseconds[+/-offset])/` 结构；拒绝 ISO 日期、越出 .NET DateTime 范围、未审核偏移和歧义 JSON。
5. 仅支持 `TotalTimes=-1` 的无限次数分支。有限次数分支原生会递增并写回，当前保持未知。
6. 要求非空 ASCII `RegStr`，并按照已审核网站的大小写无关子串条件，覆盖当前根配置 `SystemSoft/SoftVersionID`。额外拒绝未知模式标识、重复关键字段和不明确载荷。

`BeginDate`、`UserID` 以及网络地址不是上述本地接受条件，不用于猜测状态。授权次数、日期或机器条件失败也不会转换为“明确无效”，因为其他方式尚未验证。

为避免与厂商 `Encoding.Default` 的代码页解析出现歧义，目前只接受 ASCII 明文载荷（JSON 的 `\uXXXX` 转义可以正常解析）；不适用的真实载荷保持未知。

## 独立本机身份

仅使用本机 Windows WMI 元数据和系统计算机名，不调用厂商实现：

- CPU：按照 WMI 顺序取最后一个 `ProcessorId`；原生异常/空属性路径对应 `unknow`。
- 磁盘：按 `SELECT * FROM Win32_DiskDrive` 顺序，将 `Signature` 转为 UInt32，取第一个大于零的值；出现空属性/转换异常即走 `unknow`，不能跳过异常项取下一盘。
- 主板：取第一行 `SerialNumber.Trim()`；无行/空属性对应 `unknow`。主板为 `unknow` 或 `None` 时使用 CPU。
- 非双方 `unknow` 时，CPU/磁盘/主板补零至至少 9 位，再按 CIL 规则取主板与磁盘各 `Substring(length-9, 8)` 拼接。双方 `unknow` 时才使用计算机名的对应后 16 字符规则。
- 非空 `WebSerSoftNo` 会先尝试 CPU 绑定的覆盖号。原生通过 `cmd` 运行 WMIC 并解析文本，失败时使用 `unknow`。独立 WMI 查询成功不能证明目标 WMIC 同样存在或解析成功；本轮对所有非空覆盖号保持未知，不以 WMI 成功代替该分支。7 个通过的当前样本都不依赖该覆盖号。

只有公共 helper 的身份快照 `complete=true` 才允许通过；当前进一步要求所有非空身份字符串均为可打印 ASCII，避免 Java/.NET Unicode Trim、大小写和规范化差异。本机标识和解密 JSON 不写入报告。

## 网站正向接受链与边界

三个审核网站构建的固定 `ProName` 由 `.cctor` 的明确字段赋值证明。`GetProVersion()` 在兼容关系之后**总会补入本产品和当前版本**：代表 DS0101 网站 `0x060006A1` 的 CIL `0x009D..0x00DC`。`CheckSoftVersionID()` 又向候选版本列表加入当前模式，再做大小写无关子串检查：DS0101 `0x060006A6`，YX0303 `0x06000557`。因此上述本产品、本机、有效期、无限次数和当前模式全部满足时，本产品候选的授权条件通过；无需假设某个父产品已经获得授权。

父产品候选授权条件不匹配时，网站循环继续查找；本产品候选满足后返回“不需要注册”并退出。DS01xx 只有大写条件失败才调用小写组件；本检测器的本地正向证明不依赖小写组件。YX0303 只使用大写组件。

本结论是独立重实现的**授权条件**证明，不是实际执行整站 `CheckReg()` 的返回值证明。网站冷启动、缓存中的旧 `RegInfo`、业务数据库、兼容关系数据库、日志 Appender、线程/内存和文件权限等运行异常没有被模拟。尤其 `GetRegisterVersionList` 的异常路径虽然返回空列表，其 catch 中仍可能调用日志；检测器不会以“绑定了某个日志 DLL”宣称这些 I/O 路径永不抛异常，也不把业务 I/O 健康混入许可条件。数据库损坏或实际网站初始化失败应由对应运行检查报告。

## 实际样本与测试

本轮独立 Windows helper 和 Java 生产谓词均在副本上得到 7 个通过：DS0101/05/07/09、YX030308/321/322。YX0102、YX030107、YX030204 的其他协议/载荷尚未接入，保持未知。没有运行厂商授权验证方法，没有把“无异常”当成有效授权证据。

合成测试覆盖本机不匹配、到期边界、错误产品、当前模式缺失、有限次数、ISO/越界日期、损坏密文、重复字段、XML 外部实体、身份快照缺失及 CPU 覆盖号歧义。状态通过后仍明确断言 `Authorization.UNVERIFIED` 和 `CodeIntegrity.UNKNOWN`，防止接受条件证据被误当原版/Patch 来源或目标原生执行证明。

检测前后复读配置和所有绑定代码组件；任何变化都会撤销通过。该措施不是原子文件系统快照，结果代表检查时观测到的文件条件。
