from pathlib import Path

# Version metadata
version = Path('VERSION.txt').read_text(encoding='utf-8').strip()
if version != '1.2.46':
    raise SystemExit(f'expected VERSION 1.2.46, got {version!r}')
Path('VERSION.txt').write_text('1.2.47\n', encoding='utf-8')

# README metadata and updater/runtime documentation
readme = Path('README.md').read_text(encoding='utf-8')
old = '当前稳定版本：**v1.2.46**'
if readme.count(old) != 1:
    raise SystemExit(f'README stable marker count={readme.count(old)}')
readme = readme.replace(old, '当前稳定版本：**v1.2.47**', 1)
anchor = '- v1.2.18 起下载链升级为多线路：Java 系统代理 / HTTPS_PROXY / 直接连接，并手动安全跟随 GitHub HTTPS 重定向；Java 链全部失败后自动切换 Windows WinINet 系统网络栈，继续保留文件长度与 SHA-256 双校验。\n'
addition = anchor + '- v1.2.47 起自更新增加 `LicenseRecoverRuntime.jar` 过渡运行层：原生入口与二级 JVM 优先加载该文件，旧 `LicenseRecoverOverlay.jar` 仅作兼容回退；更新器在关键运行文件逐字节校验通过后才最后推进 `VERSION.txt`，避免“版本号已更新但运行字节码仍是旧版”。\n'
if readme.count(anchor) != 1:
    raise SystemExit(f'README updater anchor count={readme.count(anchor)}')
readme = readme.replace(anchor, addition, 1)
dist_anchor = 'LicenseRecoverOverlay.jar\n'
if readme.count(dist_anchor) != 1:
    raise SystemExit(f'README distribution overlay count={readme.count(dist_anchor)}')
readme = readme.replace(dist_anchor, 'LicenseRecoverRuntime.jar          # 更新过渡安全运行层（与 Overlay 字节一致，优先加载）\nLicenseRecoverOverlay.jar\n', 1)
Path('README.md').write_text(readme, encoding='utf-8')

# Changelog
changelog_path = Path('CHANGELOG.md')
changelog = changelog_path.read_text(encoding='utf-8')
entry = '''## 1.2.47 - 2026-09-13

### Fixed
- 使用用户提供的真实 `DS50109(3).zip` 与 `YX030506.zip` 在本地直接运行正式构建逻辑，二者均可得到 `automaticRecoveryReady=true`；因此截图中“v1.2.46 但运行校验ID仍为空”被确认不是样本识别规则本身，而是安装目录可能出现 `VERSION.txt` 已更新、运行 Overlay 仍旧的组件不一致状态。
- 新增 `LicenseRecoverRuntime.jar` 作为过渡安全运行层，Windows 原生入口、兼容 BAT 与所有二级 JVM 均优先加载它，`LicenseRecoverOverlay.jar` 保持兼容回退。
- 从本版本开始，自更新安装完成前逐字节核对 Runtime/Overlay/Core/GUI/EXE 五个关键运行文件，并将 `VERSION.txt` 改为最后写入；关键文件不一致则回滚而不是留下“假升级”状态。

### Real-sample verification
- `DS50109(3).zip`: `AuthorizationFamily=DS501`, `RuntimeProductID=DS50109`, `RegStr=DS50109`, `READY`。
- `YX030506.zip`: `AuthorizationFamily=YX0305`, `RuntimeProductID=YX030506`, `RegStr=QT100101,QT100102`, `READY`，注册基目录为 `WEB-INF/classes (ProjectSourcesPath)`。
- 额外模拟 v1.2.46 旧更新器 + 陈旧 Overlay 的升级路径：旧更新器能够新增新的 Runtime jar；升级后两份真实样本仍均为 `READY`。

'''
if changelog.startswith('## 1.2.47 '):
    raise SystemExit('CHANGELOG already has 1.2.47')
changelog_path.write_text(entry + changelog, encoding='utf-8')

notes = '''# LicenseRecover v1.2.47

这次不是再放宽 DS501/YX0305 的识别规则，而是修复“界面显示新版本、实际仍加载旧运行字节码”的更新一致性问题。

- 已用用户提供的完整 `DS50109(3).zip` 和 `YX030506.zip` 做本地实包测试，而不是只测人工夹具。
- DS50109 实测结果：`AuthorizationFamily=DS501`、`RuntimeProductID=DS50109`、`RegStr=DS50109`、`automaticRecoveryReady=true`。
- YX030506 实测结果：`AuthorizationFamily=YX0305`、`RuntimeProductID=YX030506`、`RegStr=QT100101,QT100102`、`automaticRecoveryReady=true`，并识别 `ProjectSourcesPath -> WEB-INF/classes`。
- 新增 `LicenseRecoverRuntime.jar`。新原生 EXE、兼容启动器和二级 JVM 优先从该文件加载当前验证过的运行类；旧 `LicenseRecoverOverlay.jar` 只作为兼容回退。
- 该新文件名专门用于修复 v1.2.46 -> v1.2.47 的过渡：即使旧安装目录里的 Overlay 仍是陈旧/被锁定版本，旧更新器也可以新增 Runtime jar，而新版 EXE 会优先加载 Runtime jar。
- 从 v1.2.47 开始，新的更新器会先更新并逐字节验证 `LicenseRecoverRuntime.jar`、`LicenseRecoverOverlay.jar`、`LicenseRecover.jar`、`LicenseRecoverGUI.jar`、`LicenseRecoverGUI.exe`，最后才写入 `VERSION.txt`；校验失败会回滚。
- 发布前另外模拟了“v1.2.46 旧更新器 + 故意损坏的旧 Overlay”升级场景，更新后 Runtime/Overlay 哈希完全一致，两份真实样本均重新得到 `READY`。
'''
notes_path = Path('release-notes/v1.2.47.md')
if notes_path.exists():
    raise SystemExit('release-notes/v1.2.47.md already exists')
notes_path.write_text(notes, encoding='utf-8')

probe = Path('.ci/runtime-transition-probe.txt')
if probe.exists():
    probe.unlink()
print('prepared v1.2.47 stable metadata')
