from pathlib import Path

version = '1.2.51'
tag = 'v' + version

Path('VERSION.txt').write_text(version + '\n', encoding='utf-8')

readme = Path('README.md')
text = readme.read_text(encoding='utf-8')
old = '当前稳定版本：**v1.2.50**'
new = '当前稳定版本：**v1.2.51**'
if old not in text:
    raise SystemExit('README stable-version anchor missing')
readme.write_text(text.replace(old, new, 1), encoding='utf-8')

notes = '''# LicenseRecover v1.2.51

本版修复 YX030308 / YX0303xx 系列在 LicenseRecover 中显示“成功”，但真实 ASP.NET 应用仍回到“使用申请 / 本地注册”页面的问题。

## 根因

用户提供的真实 YX030308 组件同时存在两种产品标识：

- `Web.config` 中的宽泛应用组：`productName=YX03`；
- 目标受保护 `ITMC.Web.dll` 的真实注册运行族：`PubBase.ProName=YX0303`。

旧逻辑优先采用 `Web.config` 的 `YX03`，因此生成、写入和 LicenseRecover 自身验证都使用 `YX03`。`ITMC.Regedit.dll` 会据此用 `*ITMCYX03OK*` 加密本地 `regName`，所以工具内部可以得到 `CheckReInfo` 成功。

但真实站点启动时使用 `YX0303` 构造 `RegeditMain`，随后按 `*ITMCYX0303OK*` 解密同一个本地授权。两边密钥不一致，目标 `Register.xml` 因而记录 `解密出现错误：不正确的数据。`，形成“工具显示 OK、站点实际未注册”的假阳性。

## 修复

- 对 `YXdddddd` 形式的现代 YX 模式，当目标 `ITMC.Web.dll` 自身明确证明 `YXdddd` 注册族时，以目标运行程序集的注册族为准，而不是宽泛 `Web.config productName`。
- YX030308 现在解析为：

```text
SoftVersionID=YX030308
Runtime/ProName=YX0303
RegStr=YX030308,...(目标程序集证明的兼容模式)
```

- 写入与验证使用真实运行族 `YX0303`，因此本地 `regName` 使用目标站点实际读取的 `*ITMCYX0303OK*` 密钥。
- 不是硬编码 `YX030308 -> YX0303`：规则来自目标 `ITMC.Web.dll` 的运行族证据，可覆盖同结构 YX0303xx 模式。
- 保留既有例外与直接产品语义：例如目标程序集证明 `YX030107 -> YX0302` 时继续使用 `YX0302`；`YX0102 -> YS01` 仍保持配置中的直接产品身份。

## 真实样本验证

- 使用用户提供的 YX030308 `ITMC.Web.dll` / `ITMC.Regedit.dll` / `Web.config` 解壳后核对真实调用链。
- `ITMC.Web.PubBase::.cctor` 证明运行 `ProName=YX0303`；注册页和启动检查均以该值构造 `RegeditMain`。
- `ITMC.Regedit.dll` 证明本地密钥由 `*ITMC` + `ProName` + `OK*` 组成，因此 `YX03` 与 `YX0303` 的差异会直接导致站点解密失败。
- 修复后的正式构建重新对真实 DLL 做检测，得到 `version=YX030308, product=YX0303`；当前模式 `YX030308` 保留在 RegStr 首位。
- 完整 Verify source、Java 8 回归、备份安全、原生 EXE 打包及 Windows .NET 集成测试通过后发布。
'''
Path('release-notes').mkdir(exist_ok=True)
Path('release-notes/v1.2.51.md').write_text(notes, encoding='utf-8')

changelog = Path('CHANGELOG.md')
old_change = changelog.read_text(encoding='utf-8')
entry = '''## 1.2.51 - 2026-09-14

### Fixed
- 修复 YX030308 / YX0303xx 真实站点“LicenseRecover 显示 OK，但 ASP.NET 页面仍要求使用申请/本地注册”的假阳性。
- 根因是目标 `Web.config productName=YX03` 只是宽泛应用组，而受保护 `ITMC.Web.dll` 的真实注册运行族为 `YX0303`；旧版用 `YX03` 写入和自验，真实站点却用 `YX0303` 解密。
- 现代 `YXdddddd` 模式现在优先采用目标 `ITMC.Web.dll` 证明的 `YXdddd` 运行族，同时保留 `YX0102 -> YS01` 等直接产品语义与 `YX030107 -> YX0302` 等目标程序集例外。

### Verification
- 使用用户提供的 YX030308 核心组件和 FOAP/VBPD MethodBody 恢复数据核对 `PubBase.ProName`、注册页 `RegeditMain` 构造链、`CheckReg` 及 `ITMC.Regedit.dll` 本地密钥派生。
- 修复后对真实 DLL 的正式构建检测结果为 `SoftVersionID=YX030308`, `Product=YX0303`，且 RegStr 含当前 `YX030308` 模式。
- 新增 YX030308 宽泛 `Web.config=YX03`、YX030107/YX0302 以及 YX0102/YS01 正反回归。

'''
changelog.write_text(entry + old_change, encoding='utf-8')
