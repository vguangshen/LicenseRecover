from pathlib import Path

readme = Path('README.md')
s = readme.read_text(encoding='utf-8')
s = s.replace('当前稳定版本：**v1.2.40**', '当前稳定版本：**v1.2.41**', 1)
java_marker = '### Java 应用\n\n'
java_note = ('v1.2.41 起，Java 一键恢复改为按目标应用真实启动链执行：目标 `GetRegisterCode` / `RegisterMain.doRegistry()` 完成写入后，必须在全新 JVM 中按启动代码证明的构造器与路径重新执行目标校验，并核对持久化 `RegStr`/`ProName`。只有 fresh-JVM 验证全部通过才显示最终 **OK**；任何阶段失败都会保持失败状态并回滚注册文件快照。当前回归矩阵覆盖 DS50109、DS2406、QT30103、DS2802、DS3110、YT00138、YT00129、QT40101、XMT0102、XMT0103 等不同 ABI、路径与 RegStr 消费方式。\n\n')
if java_note not in s:
    if java_marker not in s:
        raise SystemExit('README Java marker not found')
    s = s.replace(java_marker, java_marker + java_note, 1)
readme.write_text(s, encoding='utf-8')

readme_txt = Path('README.txt')
t = readme_txt.read_text(encoding='utf-8')
t = t.replace('LicenseRecover v1.2.40', 'LicenseRecover v1.2.41', 1)
section = '''\nv1.2.41 Java target-native 一键恢复\n----------------------------------\n- Java 授权恢复按目标应用真实 RegisterMain 构造器、路径来源和 RegStr 消费方式执行；\n- 支持 webapp 根、WEB-INF/classes、CodeSource/default 等已验证路径语义；\n- Virbox 注册类只在隔离 helper 内存中恢复，原目标 JAR 不改写，并保留原始 CodeSource；\n- 授权族、Tomcat 实际 Runtime ProductID 与 RegStr token 分开验证；\n- doRegistry 成功不再等于最终成功：必须 fresh JVM 重建目标启动链并通过 checkReInfo/RegInfo、RegStr 与 ProName 校验后才显示 OK；\n- 任一阶段失败保持失败状态并回滚注册文件快照；\n- 回归覆盖 DS50109、DS2406、QT30103、DS2802、DS3110、YT00138、YT00129、QT40101、XMT0102、XMT0103 等样本模型。\n'''
if 'v1.2.41 Java target-native 一键恢复' not in t:
    insert_at = t.find('\nv1.2.40 .NET 注册链一致性修复')
    if insert_at < 0:
        t += section
    else:
        t = t[:insert_at] + section + '\n' + t[insert_at:]
readme_txt.write_text(t, encoding='utf-8')

changelog = Path('CHANGELOG.md')
c = changelog.read_text(encoding='utf-8')
entry = '''## [1.2.41] - 2026-09-11

### Changed
- Java 一键恢复重构为 target-native 运行链：按目标应用自身启动字节码证明的 `RegisterMain` 构造器、路径语义和 RegStr 消费方式执行，不再把所有 Java 产品当成同一注册布局。
- 目标 `WEB-INF/classes` / `WEB-INF/lib` 改由隔离 child-first loader 加载；Virbox 注册类仅在 helper 内存中恢复，并保留原始注册 JAR CodeSource。
- 授权族、具体 Runtime ProductID 与目标启动所需 RegStr token 分离，支持 `SoftVersionID != RegStr token` 以及 Fastjson `regStr` 消费等真实变体。

### Verification
- Java `doRegistry()` 成功不再直接视为最终成功。写入后必须启动全新 JVM，按目标启动 profile 重新创建 `RegisterMain` 并完成目标校验。
- 只有 fresh-JVM 验证确认注册状态、持久化 RegStr 包含全部目标证明 token、且非空 ProName 与 Runtime ProductID 一致后才显示最终 `OK`；失败时保持非 OK 并回滚注册文件快照。

### Regression
- 回归矩阵覆盖 DS50109、DS2406、QT30103、DS2802、DS3110、YT00138、YT00129、QT40101、QT100101、YX030506、XMT0102、XMT0103 以及未知布局 fail-closed。
- 增加源码门禁，禁止 GUI 在 fresh-JVM 验证前返回最终成功，并禁止 JavaHost 在 `checkReInfo` / RegStr / ProName 验证之前输出最终 `RESULT: OK`。

'''
if not c.startswith('## [1.2.41]'):
    c = entry + c
changelog.write_text(c, encoding='utf-8')
