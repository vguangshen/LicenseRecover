# .NET 模式关系的只读证据（2026-11-09）

网站的本地记录不是唯一注册候选。已审核的 Web A/B/C 构建先读取 `Data/db/RegisterVersion.db`，按当前模式查找父产品和父模式，再尝试父产品注册路径；没有父产品接受后才检查自身产品。本轮只补充这份关系数据的可信识别，继续使用既有 `.NET` 共享扫描入口，不产生新的授权状态策略。

## 静态审核来源

四份数据库均从用户当前安装目录只读取得，另建物理副本。它们是 LiteDB 格式 8，每份 4 个 8192 字节页。离线解析按已部署 LiteDB 5.0.8 的页结构核对集合、有效数据槽、BSON 边界，并沿 `_id` 唯一索引验证每条有效记录恰好可达一次；没有把任意字符串命中当作数据库行。

恢复后的 `GetCurrRegVersionSettingDic` 使用 `RegisterVersionClass` 集合及 `versionID == Encrypt3Des(当前 SoftVersionID)` 查询。3DES 参数直接绑定当前 Web 方法中的常量与调用链，全部关系列解密及回加密一致。密钥、密文、数据库和厂商二进制均未提交，生产检测器也不需要这些密钥。

| Web 构建 | SHA-256 |
| --- | --- |
| A | `929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4` |
| B | `07183f09c19b69c5c4ddcdcb43ad4b8d91cf56f629f6a3d7d675dfc84f761079` |
| C | `d22c923d7f11b084e2839b3d0d6ee572e06ad5d41200ff6c8215757f54e7501e` |

三者均绑定 LiteDB SHA-256 `4091c455a882e3e44c8dc0c4034e6b2536f180ce019695769ef72497a90467ec`。生产识别还要求现有完整现代与兼容组件审核集合同时匹配；单独的 Web、数据库文件或库名不能借用这些语义。

| 审核记录 | Web | 配置当前模式 | 数据库 SHA-256 | 父产品及父模式集合 |
| --- | --- | --- | --- | --- |
| MODE-RELATIONS-20261109-1 | A | YX030107 | `dd74a18a833f862c21639755742069f1cba8469d669cd1d51c0f2e20136e71e3` | QT1001：QT100111、QT100113 |
| MODE-RELATIONS-20261109-2 | C | YX030118 | `6d0312991988f6b40686597e42b682c0bc9fedadad9c6cb444265ae2dd892c2b` | QT04：QT0440、QT0460、QT0462、QT0463；PT02：PT0205 |
| MODE-RELATIONS-20261109-3 | A | YX030201 | `30806a2634fb1d3dd80aac01bea6fc39b0bef8eda75c4a5136b0ef8cc8f6e543` | QT1001：QT100113；PT02：PT0213 |
| MODE-RELATIONS-20261109-4 | B | YX030211 | `f8a7cd1532ffbc2d243ff4d007e04c2e8cec1b35a8afaa8caf03a02c69887ac0` | QT04：QT0424；PT02：PT0213 |

配置模式是输入绑定条件，不是目录名称判断。相同文件和模式放到其他目录仍适用。查询没有显式排序；表中的关系集合和 JSON 数组不代表任意运行环境下保证的父产品优先顺序。

## 生产检测的范围

`LicenseRecoverReadOnlyDotNetCatalog` 仅比较已审核的数据库、消费组件、完整依赖与配置模式凭据，不解析任意新 LiteDB 文件，不运行数据库引擎或厂商方法。未知数据库、未知当前模式、缺失或替换依赖均不能借用其他记录，也不能推断为没有父产品。

- `EXACT_REVIEWED_DATA`：完整元组匹配，只报告已审核父产品/模式集合。
- `MISSING_DATABASE`：已审核入口的数据库确实缺失，代码直接返回空字典；不表示整体未激活。
- `COMPANION_PRESENT`：观察到 `RegisterVersion-log.db` 或 `RegisterVersion-tmp.db`，即使为空也不按主文件给出关系集合。
- `CURRENT_MODE_UNKNOWN` / `UNKNOWN_DATABASE_OR_MODE` / `UNREVIEWED_CONSUMER`：仅保留具体缺口，不猜测关系。
- `CHANGED_OR_REVOKED`：读取期间数据、配置或组件绑定变化，撤回关系集合及原审核凭据。

数据库及两种伴随文件的存在/缺失、哈希、大小、修改时间和文件身份进入现有前后回执；最终外层组件复核后仍重读完整协议输入。真实 I/O 错误保留扫描错误，不能用 `Files.exists` 将权限失败当作缺失。读取采用有界流式哈希，不读取或输出许可载荷。

原 `Query` 只有 `finally`，读取、映射和解密异常会传播，不能当作空列表。其宿主还有创建目录路径，并按 `Connection=shared`、未启用 `ReadOnly` 打开数据库，所以本轮没有直接调用原方法。观察不到伴随文件也不是完整实时数据库快照证明。

这些证据不会修改 `Acceptance`、`Authorization`、`CodeIntegrity` 或稳定的 `LocalCondition`。已有扫描协调器在输入变化时仍撤回当前结果，这是防止保留过期证据的安全例外。原生/JIT、先行父产品后备、组合来源及运行缓存尚未闭合；精确关系匹配不证明父产品授权有效、原生激活、确认 Patch 或全部业务可运行。

## 验证边界

共享扫描器测试使用自编字节和合成审核凭据，覆盖成套匹配、未知数据库/模式、错误消费构建/依赖、重复配置、伴随文件、数据库缺失、新建/同大小变化/仅时间变化、重命名目录、不可变集合、读取预算及 JSON 证明范围。生产入口只能使用内置审核表，没有导入外部成功凭据的路径。最终两包与全部实际应用的运行结果由发布验证另外记录。

格式依据：[LiteDB 5.0.8 HeaderPage](https://github.com/litedb-org/LiteDB/blob/v5.0.8/LiteDB/Engine/Pages/HeaderPage.cs)、[BasePage](https://github.com/litedb-org/LiteDB/blob/v5.0.8/LiteDB/Engine/Pages/BasePage.cs)、[CollectionIndex](https://github.com/litedb-org/LiteDB/blob/v5.0.8/LiteDB/Engine/Structures/CollectionIndex.cs)、[IndexNode](https://github.com/litedb-org/LiteDB/blob/v5.0.8/LiteDB/Engine/Structures/IndexNode.cs)。网站静态范围另见 [YX0302 审核](DOTNET_YX0302_UPPER_LOCAL_AUDIT.md) 和 [YX0301 审核](DOTNET_YX0301_LOCAL_AUDIT_20261108.md)；本轮没有因此闭合它们的完整入口。
