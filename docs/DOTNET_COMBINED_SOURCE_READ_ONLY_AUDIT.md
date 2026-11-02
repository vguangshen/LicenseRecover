# YX0302 组合授权来源与只读边界复核

本轮依据独立副本及静态 CIL 复核 YX030107、YX030204。未加载或执行厂商程序集，没有调用注册、验证、写回、网络或硬件狗方法，也没有访问真实安装目录。本文记录新的来源路径，**不是当前组合授权已经有效的结论**。

## 已绑定的当前构建

| 组件 | SHA-256 |
| --- | --- |
| YX030107 `ITMC.Web.dll` | `929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4` |
| YX030204 `ITMC.Web.dll` | `07183f09c19b69c5c4ddcdcb43ad4b8d91cf56f629f6a3d7d675dfc84f761079` |
| 共同大写 `ITMC.Regedit.dll` | `222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725` |
| 共同小写 `itmcRegedit.dll` | `35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f` |
| 共同 `ITMC.DBUtility.dll` | `dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc` |
| 共同 `NDatabase3.dll` | `e109669167b248d21558bdbd45f03302c4a15679e1e5bc0e8924fccafa1b7890` |
| 共同 `Newtonsoft.Json.dll` | `78bedd9fce877a71a8d8ff9a813662d8248361e46705c4ef7afc61d440ff2eeb` |

这些指纹限定当前代码语义，不是原版/Patch 成对来源证明。

## 实际网站入口与遗漏的来源

两个网站的入口均包括 `PubBase.CheckReg(Boolean)`。YX030107 `Global.Application_Start` (`06000329`) 在偏移 `0000/0001` 传入 `true`；YX030204 对应 `06000350`。多个页面基类也传入 `true`，所以组合来源分支不是孤立的未调用方法。

| YX030107 MethodDef | YX030204 MethodDef | 方法 |
| --- | --- | --- |
| `06001907` | `06001363` | `PubBase.CheckReg(Boolean)` |
| `06001908` | `06001364` | `GetCurrRegVersionSettingDic()` |
| `06001909` | `06001365` | `CheckReg_(String)` |
| `0600190A` | `06001366` | `SetCombineRegInfo()` |
| `0600190B` | `06001367` | `ReadReg(String,String)` |

`CheckReg` 首先检查内存中 `RegInfo.EndDate`，缓存仍可接受时不会重新读取根配置。否则按兼容产品映射检查产品，继而检查本产品。`CheckReg_` 先调用大写注册组件；大写失败才进入小写组件。父产品成功分支还核对映射版本与 `RegStr`，不能把某个父产品记录存在当成当前模式接受。

原检查均未通过且调用者参数为 `true` 时，`CheckReg` 偏移 `00DF..00E8` 调用 `SetCombineRegInfo`，随后以 `false` 递归检查一次。**根 `config.xml` 没有载荷，并不能排除它还会采用另一份本机组合授权来源。** 这也不证明组合来源存在或能通过验证。

## 组合数据的读取与触发条件

`SetCombineRegInfo` 偏移 `0000..000D` 先要求 `GetCurrRegVersionSettingDic().Count > 0`。字典来自 `data/db/RegisterVersion.db` 的查询与解密：按当前版本号筛选、读取父产品及父版本映射。文件存在、非空或包含可搜索字符串不足以证明查询返回有效记录；未闭合 NDatabase 查询时只能报告候选来源，不能宣称该路径已经适用。

偏移 `0014..0029` 根据目标进程 `IntPtr.Size` 选择注册表子路径：

- 64 位进程：`SOFTWARE\Wow6432Node\YunClient\Soft`。
- 32 位进程：`SOFTWARE\YunClient\Soft`。

`ReadReg` 从 `Registry.LocalMachine` 用默认视图 `OpenSubKey`，随后默认 `GetValue`；缺 key、缺值或异常返回空字符串。微软说明默认视图随进程位数变化，32 位 `HKLM\Software` 映射至 WOW64 视图；独立读取应使用明确视图，不能将任意两份注册表值混合。[Microsoft registry views](https://learn.microsoft.com/en-us/windows/win32/sysinfo/32-bit-and-64-bit-application-data-in-the-registry), [alternate registry views](https://learn.microsoft.com/en-us/windows/win32/winprog64/accessing-an-alternate-registry-view).

该实现按上述两条代码路径意图访问相同的 32 位逻辑产品区。只读重实现可核对 `Registry32` 的逻辑路径及 64 位视图中相应显式路径；视图冲突、未知系统布局或无法确认时保持待核。原方法 `GetValue` 会保留对象类型并可能展开环境变量：初期只支持明确的 `REG_SZ` 字符串；其他类型与 `REG_EXPAND_SZ` 需要另外证明等价，不能猜测 ASP.NET 进程的环境展开结果。

所读字段仅作为内部数据，不得在日志、报告或公开测试夹具输出值：

| 字段 | 当前代码用途 |
| --- | --- |
| `SoftNo` | 非空白才继续组合处理；更新为配置的 `WebSerSoftNo` |
| `LocalSoftNo` | 非空白时更新为 `regName`，再将 `regType` 置为本地优先 `1` |
| CRM 用户与密码字段 | 非空白时更新对应联网凭据；本地独立诊断不需要输出或读取这些秘密 |

`SoftNo` 非空但 `LocalSoftNo` 为空时，不能凭空把模式改为 `1`。上述方法没有验证机器、日期、次数或产品，也没有承诺文件写入成功。实际方法会写 `config.xml`，不能执行它作为只读扫描探针。

## 覆盖机器号的另一个验证层

大写组件 `RegeditMain.getRegNo()` (`06000074`) 先读配置的 `WebSerSoftNo`，用 `GetCpuid.Getid()` 的结果作为 DES 密码解码。解码结果非空白时，`0054..005F` 直接返回完整覆盖机器号；只有失败或空白才回落 WMI 机器号。这条分支不能继续用默认 WMI 拼接规则比较，也不能只检查覆盖号非空。

`GetCpuid.Getid()` (`06000009`) 的原生来源为固定命令 `@wmic cpu get processorId`。它按本机换行分割、去空行，寻找修剪后精确为 `ProcessorId` 的行，取紧随一行并修剪；空白或异常返回字面 `unknow`。这与现有 WMI 机器规则的最后 CPU 行不同，不能直接替换。`InvokeCmd` (`0600000A`) 启动交互 `cmd.exe` 并读输出，存在 AutoRun、PATH、编码及运行身份边界；扫描器不得运行这段厂商方法。

独立支持覆盖号需要取得与该规则等价的可信只读元数据，处理 WMIC 可用性、输出解析、空结果、AutoRun 及目标环境差异。解码覆盖号后，仍须独立核对当前产品的载荷、机器绑定、期限、无限次数和适用的产品范围；无法证明时不能提高为绿色。

## 可复用的安全实现与测试要求

- 保留根配置观察与组合来源候选两个证据渠道，注明实际来源；候选通过不代表当前进程已经使用。
- 只读读取明确视图的必要字符串；在内存中构造候选输入，不调用 `SetCombineRegInfo`、目标构造器或任何写回。
- 完整绑定网站、上下注册组件、密码/JSON 实现；若采用映射适用性证明，还需绑定 NDatabase 读取语义及当前映射数据。
- 完整复核前后代码、配置和注册表观察，变化撤销结论。不能把不同时间或视图的字段拼成一份通过记录。
- 测试缺来源、仅 `SoftNo`、仅 `LocalSoftNo`、类型/视图冲突、未知展开、映射不可证明、覆盖解码失败、有效覆盖、机器/日期/次数/产品失败及扫描期间变化。
- 小写 VM、网络/HASP 和已运行进程缓存仍有未闭合范围；不得把局部不符、缺文件或候选存在推为全局无效/有效。

本轮未恢复小写 VM 的实际业务判据。FOAP 方法体可静态恢复只意味着外层 CIL 可读，不能将 VM 分派桩当成已审核验证器，也不能以其他版本的协议替代它。
