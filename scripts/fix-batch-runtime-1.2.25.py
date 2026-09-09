from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one anchor, found {count}: {old[:100]!r}")
    write(rel, text.replace(old, new, 1))


# 1) Java child JVMs must always load the tool overlay/core before target WEB-INF/lib.
replace_once(
    "src/main/java/LicenseRecover.java",
    '''                cp = tmp.toAbsolutePath() + File.pathSeparator + libDir + File.separator + "*"
                        + File.pathSeparator + toolCp;
            } else {
                cp = libDir + File.separator + "*" + File.pathSeparator + toolCp;
''',
    '''                cp = toolCp + File.pathSeparator + tmp.toAbsolutePath()
                        + File.pathSeparator + libDir + File.separator + "*";
            } else {
                cp = toolCp + File.pathSeparator + libDir + File.separator + "*";
''')
replace_once(
    "src/main/java/LicenseRecover.java",
    '''        if (patchMode) {
            // 方式三用最小 classpath（本工具已内嵌 javassist），不能带 lib/* 否则 ITMCReg.jar 被本进程占用无法替换
            cmd.add(toolCp);
        } else {
            cmd.add(libDir + File.separator + "*" + File.pathSeparator + toolCp);
        }
''',
    '''        if (patchMode) {
            // 方式三用最小 classpath（本工具已内嵌 javassist），不能带 lib/* 否则 ITMCReg.jar 被本进程占用无法替换
            cmd.add(toolCp);
        } else {
            // Tool classes first: an application lib must never shadow LicenseRecover/overlay classes.
            cmd.add(toolCp + File.pathSeparator + libDir + File.separator + "*");
        }
''')

# 2) Modern GUI: DS01xx remains the legacy protocol even when both registration DLL spellings exist.
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''            Kind k = new File(bin,"ITMC.Regedit.dll").isFile() ? Kind.DOTNET_MODERN : Kind.DOTNET_LEGACY;
            return new Detection(k,s,root,bin,version,product);
''',
    '''            // DS01xx uses the legacy funpublic protocol. Some deployments also carry an
            // uppercase ITMC.Regedit.dll compatibility assembly, which is not proof of the
            // modern JSON/DoRegistry protocol and must not override the version evidence.
            Kind k = LegacyDotNetProtocol.isLegacyVersion(version)
                    ? Kind.DOTNET_LEGACY
                    : (new File(bin,"ITMC.Regedit.dll").isFile()
                    ? Kind.DOTNET_MODERN : Kind.DOTNET_LEGACY);
            return new Detection(k,s,root,bin,version,product);
''')
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''        File overlay = new File(toolDir(), "LicenseRecoverOverlay.jar");
        String childCp = d.runtimeDir.getAbsolutePath()+File.separator+"*";
        if (overlay.isFile()) childCp += File.pathSeparator + overlay.getAbsolutePath();
        childCp += File.pathSeparator + cli.getAbsolutePath();
        cmd.add(childCp);
''',
    '''        String childCp = buildJavaRecoveryClasspath(toolDir(), d.runtimeDir);
        log.accept("[java-plan] child classpath order=tool overlay/core -> target WEB-INF/lib\\n");
        cmd.add(childCp);
''')
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''    private static Result recoverDotNet(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
''',
    '''    static String buildJavaRecoveryClasspath(File toolDir, File runtimeDir) {
        if (runtimeDir == null) throw new IllegalArgumentException("runtimeDir is required");
        return LicenseRecover.toolRuntimeClasspath(toolDir)
                + File.pathSeparator + runtimeDir.getAbsolutePath() + File.separator + "*";
    }

    private static Result recoverDotNet(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
''')
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''            if (blank(firewallRule))
                throw new IOException("failed to establish the temporary .NET outbound firewall guard; refusing to call vendor registration code");
''',
    '''            if (blank(firewallRule))
                throw new IOException("无法建立 .NET 临时出站防火墙隔离。请从 LicenseRecoverGUI.exe 启动并通过管理员权限(UAC)，"
                        + "同时确认 Windows Firewall 服务可用；为避免联网校验，已拒绝调用目标注册组件。");
''')

# 3) Batch UI keeps the concrete per-app failure reason in the visible runtime log.
replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''                    final String textResult = result.isSuccess() ? (result.status == OperationResult.Status.PREVIEW ? "PREVIEW" : "OK")
                            : (result.status == OperationResult.Status.CANCELLED ? "取消" : "FAILED");
                    final String verify = batchVerification(target, usePatch, preview, result);
''',
    '''                    final String textResult = result.isSuccess() ? (result.status == OperationResult.Status.PREVIEW ? "PREVIEW" : "OK")
                            : (result.status == OperationResult.Status.CANCELLED ? "取消" : "FAILED");
                    if (!result.isSuccess() && result.message != null && !result.message.trim().isEmpty()) {
                        appendLog("[batch] " + target.name + " " + textResult + ": " + result.message.trim() + "\\n");
                    }
                    final String verify = batchVerification(target, usePatch, preview, result);
''')

# 4) Native EXE requests elevation up-front. Modern .NET recovery installs a temporary
# application-specific outbound firewall rule before calling the target registration DLL.
manifest = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3">
    <security>
      <requestedPrivileges>
        <requestedExecutionLevel level="requireAdministrator" uiAccess="false" />
      </requestedPrivileges>
    </security>
  </trustInfo>
</assembly>
'''
write("src/native/LicenseRecoverGUI.manifest", manifest)
replace_once(
    "src/native/LicenseRecoverGUI.rc",
    '1 ICON "LicenseRecoverGUI.ico"',
    '1 ICON "LicenseRecoverGUI.ico"\n1 24 "src/native/LicenseRecoverGUI.manifest"')

# 5) Release/CI guards: make the class that failed in the real log an explicit overlay invariant,
# and refuse to package an EXE without the elevation manifest.
replace_once(
    "scripts/verify.ps1",
    '''if ($overlayEntries -notcontains 'LicenseRecover.class') {
    throw 'Overlay is missing updated LicenseRecover.class.'
}
''',
    '''if ($overlayEntries -notcontains 'LicenseRecover.class') {
    throw 'Overlay is missing updated LicenseRecover.class.'
}
if ($overlayEntries -notcontains 'LicenseRecoverModernGUIJavaPlan.class') {
    throw 'Overlay is missing LicenseRecoverModernGUIJavaPlan.class; Virbox/recovery child JVMs would fail.'
}
if ($overlayEntries -notcontains 'LicenseRecoverModernGUIAutoRecovery.class') {
    throw 'Overlay is missing LicenseRecoverModernGUIAutoRecovery.class.'
}
''')
replace_once(
    "scripts/package-native-launcher.ps1",
    '''$source = Join-Path $repoRoot 'src/native/LicenseRecoverGUI.c'
$resource = Join-Path $repoRoot 'src/native/LicenseRecoverGUI.rc'
''',
    '''$source = Join-Path $repoRoot 'src/native/LicenseRecoverGUI.c'
$resource = Join-Path $repoRoot 'src/native/LicenseRecoverGUI.rc'
$manifest = Join-Path $repoRoot 'src/native/LicenseRecoverGUI.manifest'
''')
replace_once(
    "scripts/package-native-launcher.ps1",
    '''if (-not (Test-Path -LiteralPath $source)) { throw "Missing native launcher source: $source" }
if (-not (Test-Path -LiteralPath $resource)) { throw "Missing native launcher resource: $resource" }
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'LicenseRecoverGUI.ico'))) { throw 'Missing LicenseRecoverGUI.ico.' }
''',
    '''if (-not (Test-Path -LiteralPath $source)) { throw "Missing native launcher source: $source" }
if (-not (Test-Path -LiteralPath $resource)) { throw "Missing native launcher resource: $resource" }
if (-not (Test-Path -LiteralPath $manifest)) { throw "Missing native launcher manifest: $manifest" }
$manifestText = Get-Content -LiteralPath $manifest -Raw
if ($manifestText.IndexOf('level="requireAdministrator"', [StringComparison]::Ordinal) -lt 0) {
    throw 'Native launcher manifest must require administrator privileges for fail-closed .NET firewall isolation.'
}
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'LicenseRecoverGUI.ico'))) { throw 'Missing LicenseRecoverGUI.ico.' }
''')

# 6) Regression tests for both real-world failures.
replace_once(
    "src/test/java/RefactorSmokeTest.java",
    '''        Path toolCpDir = Files.createTempDirectory("lrc-toolcp-");
''',
    '''        Path legacyDotNetRoot = base.resolve("dotnet-DS0101");
        Path legacyDotNetBin = legacyDotNetRoot.resolve("bin");
        Files.createDirectories(legacyDotNetBin);
        Files.write(legacyDotNetRoot.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS0101</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        Files.write(legacyDotNetBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(legacyDotNetBin.resolve("itmcRegedit.dll"), new byte[]{1});
        Files.write(legacyDotNetBin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        LicenseRecoverModernGUIAutoRecovery.Detection legacyDotNetDetection =
                LicenseRecoverModernGUIAutoRecovery.detect(legacyDotNetRoot.toFile());
        check(legacyDotNetDetection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY,
                "DS01xx remains legacy even when an uppercase compatibility ITMC.Regedit.dll is present");

        Path toolCpDir = Files.createTempDirectory("lrc-toolcp-");
''')
replace_once(
    "src/test/java/RefactorSmokeTest.java",
    '''        check(toolCpWithOverlay.startsWith(overlayCore.toFile().getAbsolutePath() + File.pathSeparator)
                        && toolCpWithOverlay.endsWith(legacyCore.toFile().getAbsolutePath()),
                "secondary JVM classpath must load LicenseRecoverOverlay.jar before LicenseRecover.jar");

        System.out.println("ALL REFACTOR SMOKE TESTS PASSED");
''',
    '''        check(toolCpWithOverlay.startsWith(overlayCore.toFile().getAbsolutePath() + File.pathSeparator)
                        && toolCpWithOverlay.endsWith(legacyCore.toFile().getAbsolutePath()),
                "secondary JVM classpath must load LicenseRecoverOverlay.jar before LicenseRecover.jar");
        Path targetRuntime = Files.createTempDirectory("lrc-target-lib-");
        String recoveryCp = LicenseRecoverModernGUIAutoRecovery.buildJavaRecoveryClasspath(
                toolCpDir.toFile(), targetRuntime.toFile());
        check(recoveryCp.startsWith(toolCpWithOverlay + File.pathSeparator)
                        && recoveryCp.endsWith(targetRuntime.toFile().getAbsolutePath() + File.separator + "*"),
                "Java recovery child classpath keeps tool overlay/core ahead of target WEB-INF/lib");

        System.out.println("ALL REFACTOR SMOKE TESTS PASSED");
''')

# 7) Stable version metadata. VERSION is changed last inside this script so the repository never
# reaches a v1.2.25 state without its release notes.
replace_once("README.md", "当前稳定版本：**v1.2.24**", "当前稳定版本：**v1.2.25**")
write("release-notes/v1.2.25.md", '''# LicenseRecover v1.2.25

本版根据 v1.2.24 的真实批量运行结果继续修复：Java 项已恢复正常，重点处理批量列表中 .NET Modern 全部失败、DS01xx 误判代际，以及二次 JVM classpath 的防回归。

- **Windows EXE 自动请求管理员权限**：`LicenseRecoverGUI.exe` 现在带 `requireAdministrator` manifest。现代 .NET 一键恢复在调用目标注册组件前必须安装临时的按程序出站防火墙规则；普通双击不再因 `netsh advfirewall` 权限不足而整批失败。
- **DS01xx 不再被误判为 .NET Modern**：SoftVersionID 为 DS01xx 时始终按旧版 funpublic 协议分类，即使目录同时带有大写 `ITMC.Regedit.dll` 兼容程序集。
- **Java 子 JVM 工具 classpath 强制优先**：Overlay/Core 固定排在目标 `WEB-INF/lib/*` 之前，避免目标目录中的同名/旧类遮蔽工具类；Virbox 脱壳重跑同样使用该顺序。
- **Overlay 发布门禁加强**：构建必须显式包含 `LicenseRecoverModernGUIJavaPlan.class` 与 `LicenseRecoverModernGUIAutoRecovery.class`，防止再次出现 `NoClassDefFoundError`。
- **批量失败原因直接写入运行日志**：不再只在表格里显示笼统的 `FAILED`，会记录具体异常原因，便于定位防火墙服务、目标 DLL 或原生校验问题。
''')
write("VERSION.txt", "1.2.25\n")

print("v1.2.25 patch applied")
