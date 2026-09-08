#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]

(root / 'VERSION.txt').write_text('1.2.19\n', encoding='utf-8')

readme = root / 'README.md'
s = readme.read_text(encoding='utf-8')
old = '当前稳定版本：**v1.2.18**'
new = '当前稳定版本：**v1.2.19**'
if s.count(old) != 1:
    raise SystemExit('README stable-version anchor not found or not unique')
readme.write_text(s.replace(old, new, 1), encoding='utf-8')

notes = '''# LicenseRecover v1.2.19

本版根据真实 `YX0102` 与 `YX030506` 目标目录样本补齐最后两类注册证据解析，继续坚持“目录自证、无法确认即阻止”，不恢复任何默认 ProductID / ProName / RuntimeProductID / RegStr 兜底。

- **YX0102 / .NET**：当 `ITMC.Web.dll` 已证明 ProName 后，可只读解密目标目录现有 `config.xml/regName`，仅在密钥有效且授权 JSON 内嵌 `ProName` 与 DLL 证明值完全一致时采用其中 `RegStr`。真实样本由此确认 `ProName=YX0102`、`RegStr=YX0102`；错误产品号解密或身份不一致仍返回无证据并阻止执行。
- .NET 批量扫描、批量预检与实际一键恢复统一使用同一个 RegStr 证据解析器，避免扫描显示“待确认”但执行链采用另一套判断。
- **YX030506 / Java**：新增 `classes/config.xml SoftVersionID -> PRODUCT_ALL_NUM -> ProjectApplicationRunner.split -> RegisterMain.checkReInfo()` 目录数据流识别。只有目标自己的 `IXmlUtil.class` 与 `ProjectApplicationRunner.class` 同时证明完整链路时，才接受具体 RuntimeProductID。
- 真实 YX030506 样本因此解析为：授权族 `YX0305`、运行校验 ID `YX030506`、RegStr `QT100101,QT100102`；本地注册入口的 `YX0305` 与启动校验的具体 `YX030506` 保持双 ID 语义。
- 新增正/负回归：错误 .NET ProName 不得复用本地 regName；YX030506 只有授权族字符串而缺少完整 PRODUCT_ALL_NUM 数据流时必须继续 fail-closed。
- 保留 v1.2.18 的 GitHub 多线路更新链与 Windows WinINet 兜底，不改变 Java/.NET 原生注册闭环、备份/回滚和联网阻断顺序。
'''
(root / 'release-notes' / 'v1.2.19.md').write_text(notes, encoding='utf-8')
print('prepared v1.2.19 metadata')
