#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
version = root / 'VERSION.txt'
readme = root / 'README.md'
notes = root / 'release-notes' / 'v1.2.15.md'

version.write_text('1.2.15\n', encoding='utf-8')
text = readme.read_text(encoding='utf-8')
old = '当前稳定版本：**v1.2.14**'
new = '当前稳定版本：**v1.2.15**'
if old not in text and new not in text:
    raise SystemExit('README stable-version marker not found')
if old in text:
    text = text.replace(old, new, 1)
readme.write_text(text, encoding='utf-8')

notes.write_text('''# LicenseRecover v1.2.15

本版根据 DS2802 与 DS50109 真实目标目录样本补全 Java 注册证据解析，继续坚持“只认目标目录证据，不使用默认注册代号/RegStr 兜底”。

- DS28：不再因为目标存在 `Global.class` 就一律要求 `registerProductBeans` 映射；只有 `Global.class` 自身包含 `setProductMain / setProductMainNum / setProductNums` 分派结构时才强制走映射解析。像 DS2802 这种只在目标 Global 中直接声明 `PRODUCT_NUM=DS28` 的代际，可把该目标二进制中的 DS28 作为真实目录证据。
- DS501：新增“配置驱动运行注册 ID”证据链。只有目标 `WEB-INF/classes/config.xml` 的 `SoftVersionID` 与当前软件一致，并且目标自己的 `SystemInfo.class` 明确读取 `config.xml/SystemSoft` 写入 `registerId`、`RegisterListener.class` 明确把 `registerId` 传给 `RegisterMain` 且用 `RegStr.contains(versionID)` 校验时，才允许把该具体 SoftVersionID 作为 RuntimeProductID。DS50109 因此可识别为授权族 DS501、运行校验 ID DS50109，而不是硬编码特判。
- Java 本地 RegStr 只读恢复：兼容三代 `RegisterMain` 构造器。3 参数按 `(product, token, configPath)`；2 参数按真实旧代语义 `(product, configPath)`；1 参数仅在目标默认配置路径可用时启用。修复 DS50109 等 2 参数代际此前把 token 错传为 ConfigPath 的问题。
- 批量 .NET：执行前同时要求目标 DLL 已确认 ProName 与 RegStr；成功后的校验文案更新为 `DoRegistry + CheckReInfo: OK`，与当前原生注册闭环一致。
- 新增 DS2802、DS50109 与 RegisterMain 1/2/3 参数回归测试，防止今后重新出现“有目录证据却被误拦”或“构造器参数语义错误”。

> 原则不变：**目录证据足够就识别，证据不足就阻止；不使用 VersionID、固定产品族或通用 RegStr 列表进行执行兜底。**
''', encoding='utf-8')
print('prepared v1.2.15 metadata')
