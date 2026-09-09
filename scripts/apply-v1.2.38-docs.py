from pathlib import Path

readme = Path('README.md')
text = readme.read_text(encoding='utf-8')
old_version = '当前稳定版本：**v1.2.37**'
if text.count(old_version) != 1:
    raise SystemExit(f'expected one README stable version marker, got {text.count(old_version)}')
text = text.replace(old_version, '当前稳定版本：**v1.2.38**', 1)
needle = 'v1.2.37 起，小写 `itmcRegedit.dll` 链不再依赖人工 `AppDomain + SimpleWorkerRequest` 模拟，而由 .NET Framework `ApplicationManager` 建立真实 ASP.NET `HostingEnvironment`，确保 `~/Register.xml` / `~/config.xml`、目标 `web.config`、目标 `bin` 和 `HttpRuntime` 使用同一个站点应用域。'
addition = needle + ' v1.2.38 起，仅存在大写 `ITMC.Regedit.dll` 时也进入同一真实 Hosted AppDomain，但继续使用独立 `LicenseRecover.NET.Modern.exe` 适配大写 API；这样 `AppDomain.BaseDirectory` 保持网站根目录，避免大写组件误从 `bin` 读取 `config.xml` / `Register.xml`。'
if text.count(needle) != 1:
    raise SystemExit(f'expected one README hosted-chain paragraph, got {text.count(needle)}')
text = text.replace(needle, addition, 1)
readme.write_text(text, encoding='utf-8')

changelog = Path('CHANGELOG.md')
old = changelog.read_text(encoding='utf-8')
header = '''## [1.2.38] - 2026-09-10

### Fixed

- 根据重新恢复的大写 `ITMC.Regedit.dll` 结构，修复仅大写注册组件的 .NET 应用仍沿用旧 Modern helper 独立运行环境的问题。大写组件会直接通过 `AppDomain.CurrentDomain.BaseDirectory` 访问站点根 `config.xml` / `Register.xml`，并使用 `NewRegistry()`、`DoRegistry(string,string)`、`CheckReInfo(ref RegeditInfo)`、`RegID/getRegNo()` 等自己的 API 表面，不能按小写组件的路径假设处理。
- `.NET` 选择优先级保持不变：存在小写 `itmcRegedit.dll` 时继续小写优先；仅小写不存在时才选择大写 `ITMC.Regedit.dll`。不同点是大写链现在也由 `LicenseRecover.NET.AspNetHost.exe` 建立真实 ASP.NET Hosted AppDomain，再在站点域内派发 `LicenseRecover.NET.Modern.exe`。
- 大写链执行时强制 `HostingEnvironment.ApplicationPhysicalPath`、`HttpRuntime.AppDomainAppPath`、`AppDomain.BaseDirectory` 都指向网站根目录，同时从目标 `bin` 解析 `ITMC.Regedit.dll`；不再让大写组件把 `bin` 当成应用根目录。
- PRE-BLOCK FIRST、失败回滚、默认 Service 本地不可达端点重写、写后再次确认联网阻断以及“不修改目标注册 DLL”均保持不变。

### Regression

- 新增 Windows/.NET Framework 大写专用集成测试，构造仅含 `ITMC.Regedit.dll` 的 YX030308 风格站点，按恢复后的大写 API 表面实际执行 `gencode -> DoRegistry -> CheckReInfo`。
- 集成测试要求大写 helper 明确在真实 Hosted AppDomain 内运行，`BaseDirectory` 必须是站点根，禁止读取 `bin\\config.xml`；并验证 RegID/request-code、DoRegistry、`CheckReInfo(ref RegeditInfo)` 和临时 Bridge 清理。该链已在 Windows runner 上完整通过。

'''
if old.startswith('## [1.2.38]'):
    raise SystemExit('CHANGELOG already contains 1.2.38')
changelog.write_text(header + old, encoding='utf-8')
print('v1.2.38 docs updated')
