#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
version = root / 'VERSION.txt'
readme = root / 'README.md'
notes = root / 'release-notes' / 'v1.2.18.md'

if version.read_text(encoding='utf-8').strip() != '1.2.17':
    raise SystemExit('VERSION.txt is not 1.2.17')
version.write_text('1.2.18\n', encoding='utf-8')

text = readme.read_text(encoding='utf-8')
old = '当前稳定版本：**v1.2.17**'
new = '当前稳定版本：**v1.2.18**'
if old not in text:
    raise SystemExit('README stable-version marker not found')
text = text.replace(old, new, 1)
marker = '- v1.2.4 起下载连接启用更长超时、4 次自动重试与 Range 断点续传，并让内置 Java 使用 Windows 系统代理设置。\n'
addition = marker + '- v1.2.18 起下载链升级为多线路：Java 系统代理 / HTTPS_PROXY / 直接连接，并手动安全跟随 GitHub HTTPS 重定向；Java 链全部失败后自动切换 Windows WinINet 系统网络栈，继续保留文件长度与 SHA-256 双校验。\n'
if marker not in text:
    raise SystemExit('README updater marker not found')
text = text.replace(marker, addition, 1)
readme.write_text(text, encoding='utf-8')

notes.write_text('''# LicenseRecover v1.2.18

本版重构 GitHub 在线更新下载链，针对“已检测到新版本和资产大小，但下载长期停在 0 B / 正在连接 GitHub 下载节点”的问题增加真正独立的网络兜底路径。

- 保留 GitHub Releases 正式版本检测、轻量 `LicenseRecover-update.zip` 优先策略、文件长度与 SHA-256 双重校验。
- Java 下载不再对同一网络路径重复重试：依次尝试 Windows/Java 系统代理、`HTTPS_PROXY`/`HTTP_PROXY` 环境代理（可解析时）和直接连接。
- GitHub 301/302/303/307/308 重定向改为逐跳手动处理，每一跳都重新使用当前线路，并拒绝任何非 HTTPS 最终地址。
- 原生启动器增加 `--native-download` 模式，Java 网络线路全部失败后自动调用 Windows WinINet，并使用 Windows 预配置的系统代理/PAC 网络栈下载 Release 资产。
- 原生启动 Java 时增加 `-Djava.net.preferIPv4Stack=true`，降低 Java 8 在异常 IPv6/CDN 路径上长时间卡在连接阶段的概率；原有 `-Djava.net.useSystemProxies=true` 保留。
- 更新窗口会明确显示当前正在尝试的网络线路；切换 WinINet 后继续按临时文件实际字节数刷新下载进度，不再只停留在“正在连接”。
- WinINet 下载成功后仍回到 Java 层执行 Release 资产大小校验和 `SHA256SUMS.txt` 校验，网络兜底不会绕过完整性验证。
- 增加代理解析、重定向识别和原生下载入口的构建/回归检查。

> 本版只修改 GitHub 更新网络与下载容错，不改变 v1.2.17 的 Java/.NET 目录证据识别、fail-closed、原生注册闭环及批量扫描规则。
''', encoding='utf-8')
print('prepared v1.2.18 release metadata')
