from pathlib import Path

root = Path('.')
core_path = root / 'src/main/java/LicenseRecover.java'
test_path = root / 'src/test/java/RefactorSmokeTest.java'
verify_path = root / 'scripts/verify.ps1'
version_path = root / 'VERSION.txt'
readme_path = root / 'README.md'
notes_path = root / 'release-notes/v1.2.24.md'

core = core_path.read_text(encoding='utf-8')
old = '''            File jarFile = findItmcRegJar(new File(libDir));
            String cp;
            Path tmp = null;
            if (jarFile != null && NetRemover.jarIsPacked(jarFile)) {
                tmp = Files.createTempDirectory("itmc_unpack");
                int n = NetRemover.unpackPackedJar(jarFile, tmp.toFile());
                System.out.println("[脱壳] " + jarFile.getName() + " 含 " + n + " 个 Virbox 保护类，自动脱壳后重跑 ...");
                cp = tmp.toAbsolutePath() + File.pathSeparator + libDir + File.separator + "*"
                        + File.pathSeparator + jarDir() + File.separator + "LicenseRecover.jar";
            } else {
                cp = libDir + File.separator + "*" + File.pathSeparator + jarDir() + File.separator + "LicenseRecover.jar";
            }
'''
new = '''            File jarFile = findItmcRegJar(new File(libDir));
            String cp;
            String toolCp = toolRuntimeClasspath();
            Path tmp = null;
            if (jarFile != null && NetRemover.jarIsPacked(jarFile)) {
                tmp = Files.createTempDirectory("itmc_unpack");
                int n = NetRemover.unpackPackedJar(jarFile, tmp.toFile());
                System.out.println("[脱壳] " + jarFile.getName() + " 含 " + n + " 个 Virbox 保护类，自动脱壳后重跑 ...");
                cp = tmp.toAbsolutePath() + File.pathSeparator + libDir + File.separator + "*"
                        + File.pathSeparator + toolCp;
            } else {
                cp = libDir + File.separator + "*" + File.pathSeparator + toolCp;
            }
'''
assert old in core, 'reexec classpath anchor missing'
core = core.replace(old, new, 1)

anchor = '''    static void deleteRecursive(File f) {
'''
method = '''    /** Build the tool-side runtime classpath for every secondary JVM.\n     * New source lives in LicenseRecoverOverlay.jar, so it must precede the legacy core jar. */
    static String toolRuntimeClasspath() {
        return toolRuntimeClasspath(new File(jarDir()));
    }

    static String toolRuntimeClasspath(File toolDir) {
        File dir = toolDir == null ? new File(".") : toolDir.getAbsoluteFile();
        File core = new File(dir, "LicenseRecover.jar");
        File overlay = new File(dir, "LicenseRecoverOverlay.jar");
        if (overlay.isFile()) {
            return overlay.getAbsolutePath() + File.pathSeparator + core.getAbsolutePath();
        }
        return core.getAbsolutePath();
    }

'''
assert anchor in core, 'tool cp helper insertion anchor missing'
core = core.replace(anchor, method + anchor, 1)

old = '''        cmd.add("-cp");
        if (patchMode) {
            // 方式三用最小 classpath（本工具已内嵌 javassist），不能带 lib/* 否则 ITMCReg.jar 被本进程占用无法替换
            cmd.add(jarDir() + File.separator + "LicenseRecover.jar");
        } else {
            cmd.add(libDir + File.separator + "*" + File.pathSeparator + jarDir() + File.separator + "LicenseRecover.jar");
        }
'''
new = '''        cmd.add("-cp");
        String toolCp = toolRuntimeClasspath();
        if (patchMode) {
            // 方式三用最小 classpath（本工具已内嵌 javassist），不能带 lib/* 否则 ITMCReg.jar 被本进程占用无法替换
            cmd.add(toolCp);
        } else {
            cmd.add(libDir + File.separator + "*" + File.pathSeparator + toolCp);
        }
'''
assert old in core, 'batch child classpath anchor missing'
core = core.replace(old, new, 1)
core_path.write_text(core, encoding='utf-8')

test = test_path.read_text(encoding='utf-8')
anchor = '''        System.out.println("ALL REFACTOR SMOKE TESTS PASSED");
'''
insert = '''        Path toolCpDir = Files.createTempDirectory("lrc-toolcp-");
        Path legacyCore = toolCpDir.resolve("LicenseRecover.jar");
        Path overlayCore = toolCpDir.resolve("LicenseRecoverOverlay.jar");
        Files.write(legacyCore, new byte[]{0});
        String toolCpWithoutOverlay = LicenseRecover.toolRuntimeClasspath(toolCpDir.toFile());
        check(toolCpWithoutOverlay.equals(legacyCore.toFile().getAbsolutePath()),
                "secondary JVM classpath falls back to legacy core when overlay is absent");
        Files.write(overlayCore, new byte[]{0});
        String toolCpWithOverlay = LicenseRecover.toolRuntimeClasspath(toolCpDir.toFile());
        check(toolCpWithOverlay.startsWith(overlayCore.toFile().getAbsolutePath() + File.pathSeparator)
                        && toolCpWithOverlay.endsWith(legacyCore.toFile().getAbsolutePath()),
                "secondary JVM classpath must load LicenseRecoverOverlay.jar before LicenseRecover.jar");

'''
assert anchor in test, 'smoke final anchor missing'
test = test.replace(anchor, insert + anchor, 1)
test_path.write_text(test, encoding='utf-8')

verify = verify_path.read_text(encoding='utf-8')
anchor = '''if (-not $coreSource.Contains('probeTargetRegStr')) { throw 'Java target RegisterMain.getRegInfo RegStr probe is missing.' }
'''
add = anchor + '''if (-not $coreSource.Contains('static String toolRuntimeClasspath()')) { throw 'Shared secondary-JVM tool classpath helper is missing.' }
if (-not $coreSource.Contains('String toolCp = toolRuntimeClasspath();')) { throw 'Java secondary JVMs are not using the shared Overlay-first tool classpath.' }
'''
assert anchor in verify, 'verify classpath anchor missing'
verify = verify.replace(anchor, add, 1)
verify_path.write_text(verify, encoding='utf-8')

version_path.write_text('1.2.24\n', encoding='utf-8')
readme = readme_path.read_text(encoding='utf-8')
assert '当前稳定版本：**v1.2.23**' in readme, 'README version anchor missing'
readme = readme.replace('当前稳定版本：**v1.2.23**', '当前稳定版本：**v1.2.24**', 1)
readme_path.write_text(readme, encoding='utf-8')

notes_path.write_text('''# LicenseRecover v1.2.24

本版修复 v1.2.23 批量执行真实日志暴露的 Java Virbox 自动脱壳后二次 JVM classpath 回归。

- **修复 Virbox 脱壳后 `NoClassDefFoundError`**：`maybeReexecForAppClasspath()` 过去只加载 `LicenseRecover.jar`，遗漏承载新版目录证据逻辑的 `LicenseRecoverOverlay.jar`，导致脱壳后的 JVM 找不到 `LicenseRecoverModernGUIJavaPlan`。现在统一使用 Overlay-first 工具 classpath。
- **统一所有 Java 二次启动入口**：旧批量 `spawnJavaRecover()` 同时改用相同的 `toolRuntimeClasspath()`，避免从不同入口再次落回旧 core。
- **Overlay 优先顺序有回归保护**：测试同时覆盖“有 Overlay 时必须 Overlay -> core”和“旧包没有 Overlay 时仍可回落 core”的兼容行为。
- **真实日志结论保持**：DS2406、DS50109 等在错误发生前的 `RegisterMain.checkReInfo()==false` / `RESULT: OK` 已证明本地授权本身有效；本版修的是后续 Virbox 重跑链，不改变既有目录身份、RegStr 与 PRE-BLOCK FIRST 规则。
''', encoding='utf-8')
