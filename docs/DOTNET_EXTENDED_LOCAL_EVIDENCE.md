# YX 三项的当前本地证据（2026-10-31）

本轮只读取 `scan-inputs` 中的独立副本及前轮私有静态 CIL，没有访问真实软件目录，也没有执行目标程序集、注册/补丁方法、网络请求或授权写回。

`LicenseRecoverReadOnlyDotNetExtendedVerifier` 补充结构化 `LocalCondition` 和具体原因。**本地分支失败不等于全部授权失败。** 三个当前样本都没有完整接受或全路径拒绝证明，因此仍是全局未知；不会因为机器不匹配、没有本地记录或大写 JSON 解析失败就显示白色。

| 应用 | 当前本地观察 | 全局结论 | 尚未覆盖 |
| --- | --- | --- | --- |
| YX0102 | 小写旧格式记录可解码；其机器字段与当前独立 WMI 规则不匹配 | 未知；`MACHINE_MISMATCH` 仅指该本地分支 | 其后的硬件狗/联网、网站兼容产品和最终模式判定 |
| YX030107 | 根记录与大写验证器要求的 JSON RegeditInfo 不符；小写验证仍为 VM 分派 | 未知；`UNSUPPORTED`，不能称整份旧记录损坏 | 小写 VM 中真实判据、网络/硬件狗、网站兼容路径 |
| YX030204 | 当前根配置未包含本地记录 | 未知；`NO_RECORD` 仅指根配置 | 网络优先路径、硬件狗、小写 VM 及网站兼容路径 |

没有新增任何真实原版/Patch 双指纹，也没有推断 Patch 来源。

## 精确适用范围

按当前网站、上下两种注册程序集和密码依赖的 SHA-256 组合复用策略，不按目录名返回状态。观察哈希仅绑定审核过的当前语义：

| 构建 | Website SHA-256 | Upper SHA-256 | Lower SHA-256 |
| --- | --- | --- | --- |
| YX0102 | `1790526c91b0dc51aa914d80604e600175cfb3d8899c8299bb3b2c85684b9b20` | `c942c3c21466979e63eee1ffadbfe7273c83c2dc0a455f324c9da7e187953f46` | `36f58b8657bce75a19356796f21c135104e28f5abc4bc9007f0d4ec517f479df` |
| YX030107 | `929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4` | `222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725` | `35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f` |
| YX030204 | `07183f09c19b69c5c4ddcdcb43ad4b8d91cf56f629f6a3d7d675dfc84f761079` | 同上 | 同上 |

额外绑定 `ITMC.DBUtility.dll`（类型命名空间 `Maticsoft.DBUtility`）：YX0102 为 `236cfe610b87fb7a4f6589cb2a9e319a048b8a8e2a1b79d3028d4d7e1b5fcb6b`，YX030107/204 为 `dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc`。未知、缺失或变化的组件不保留这些局部观察。

## YX0102：读取旧固定位置记录，而非套用大写 JSON

大写注册族使用 JSON；该小写组件的 `RegeditMain.CheckLocalReg`（MethodDef `0x0600005A`）读取映射后的 **`~/config.xml`**，对 `reg/regName` 去除 ASCII 首尾空白并调用审核的 `Maticsoft.DBUtility.DESEncrypt.Decrypt`。独立实现使用相同 DES/CBC/PKCS7 和 MD5 派生语义，但从不调用目标方法。

- CIL `0x0171..0x0182`：将 `getRegNo()` 与明文 `Substring(6,16)` 比较，机器不同则本地返回 `false`。
- CIL `0x01E6..0x0212`：读取 `Substring(30,10)` 的日期并比较 `.Date <= DateTime.Now.Date`，到期日当天也属于该小写分支的拒绝条件。不能套用上一阶段大写分支的 `<` 边界。
- 成功后调用 `getLocalRegInfo()`，继续构造 RegInfo 并写注册 XML。独立诊断既不写文件，也不因前两项满足就把网站最终授权升绿。

该小写构建的 `Computer.GetCpuID/GetDiskID/GetBoardID/getRegNo` 已独立静态核对：CPU 最后行、磁盘首个正 UInt32 Signature、主板首行，`unknow`/`None` 回退、补零和截取规则与公共本机快照对应。仅在完整快照和审核的 ASCII 规范下比较，结果不输出机器号、密码、授权明文或其字段值。

当前样本的机器比较失败已得到明确局部证据；观察到的记录日期尚在未来，因此没有把该记录称为“过期”。也没有为了通过验证去改写该绑定。

### 真实失败回退

该小写 `CheckReInfo(ref info)`（`0x06000052`）的 `regType=1` 分支为：

1. `0x0080` 本地；成功则外层返回 `false`。
2. 本地失败后 `0x008B` 调用硬件狗。
3. 狗失败后 `0x0096` 调用联网。
4. 全部失败才返回 `true`。

因此目前只证明第 1 项失败，不能宣称第 2、3 项失败。网站 `ITMC.funpublic.CheckReg()` 还会先检查大写实现、回退小写，并遍历兼容产品/版本；最终 `CheckSoftVersionID` 也必须满足。独立诊断没有将上位 JSON 解密失败当成整站拒绝。

## YX030107/204：恢复外层保护后仍存在 VM 边界

前轮 FOAP 工具将此小写 DLL 判为不支持。本轮在分析副本中确认原因是部分 MethodBody 在 `VBPD` 尾标记前有 **1–3 字节且全部为零的四字节对齐填充**。

私有恢复脚本只在 CIL/EH 结构长度已确定、剩余字节全部为零且恰好满足四字节对齐时裁去填充；全部 323 条记录随后通过长度验证。输出只用于静态分析，没有执行、部署或提交厂商 DLL，也没有改变任何注册判定指令。

恢复后，`CheckReInfo`（`0x0600005A`）、`CheckLocalReg`（`0x06000062`）等真实 CIL仍转入混淆的 VM dispatcher，包含 `MethodBase.GetCurrentMethod()`、数值参数和虚拟调用。**恢复 FOAP 不等于已经恢复 VM 中的业务判据。** 不能以旧家族的固定位置规则替代这一构建的真实语义。

大写 DLL 的当前 `regType=3` 分派则已明确：先联网（`CheckReInfo` 的 `0x0115`），失败才本地（`0x0120`），再 HASP（`0x012B`）。根配置中的用户名/密码字段为空不能单独证明联网一定失败；存在被屏蔽的服务地址也不能充当无效授权证据。

YX030107 当前 `regName` 可按大写自身密码派生规则解码，但剥除包装后不是其 JSON RegeditInfo。这只是**大写本地结构不适用**，不证明旧格式记录损坏，更不证明小写 VM 会拒绝。因此使用 `LocalCondition.UNSUPPORTED`。

YX030204 当前根 `regName` 为空，可以直接报告“未发现本地记录”。但当前模式先走网络，小写仍有 VM，不能把空记录转成全局未激活。

## 测试和安全边界

合成测试覆盖：旧格式机器不符、日期边界、局部条件匹配仍不升绿、网络优先时局部失败、空本地记录、错误密文、身份不可用、上位格式与下位 VM 分离、重复 XML、外部实体拒绝和敏感值不进入证据。所有局部结论均断言全局 `Authorization.UNVERIFIED`、`Acceptance.UNVERIFIED`、`CodeIntegrity.UNKNOWN` 和灰色展示，不产生白色或绿色。

代码及配置前后复读；发生变化则撤销局部观察。日志、注册文件存在、历史操作和备份不参与接受或拒绝判定。完整路径仍需 VM 静态恢复/审核及独立网络和硬件授权证据，不能通过执行有写入的原生方法补齐。
