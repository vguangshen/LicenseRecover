#!/usr/bin/env python3
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
        raise SystemExit(f"{rel}: expected one match, found {count}: {old[:120]!r}")
    write(rel, text.replace(old, new, 1))


if read("VERSION.txt").strip() != "1.2.29":
    raise SystemExit("Expected VERSION.txt=1.2.29")

# Version metadata.
write("VERSION.txt", "1.2.30\n")
for rel in ("README.md", "README.txt"):
    text = read(rel)
    if "v1.2.29" in text:
        text = text.replace("v1.2.29", "v1.2.30")
    write(rel, text)

changelog = read("CHANGELOG.md")
entry = """## [1.2.30] - 2026-09-09

### Fixed

- 修复小写 `itmcRegedit.dll` 注册链在独立 helper 进程中调用 `NewRegistry/getRegNo/DoRegistry/CheckReInfo` 时缺少 ASP.NET `HttpContext`，导致真实 YX030101 在 `GENCODE` 阶段以 `TargetInvocationException`（“调用的目标发生了异常”）失败。
- 新增 `LicenseRecover.NET.AspNetHost.exe`：只为小写 `itmcRegedit.dll` 链建立最小 `System.Web` 宿主上下文，使用目标站点物理根目录初始化 `SimpleWorkerRequest`，再在同一 AppDomain 内转交原 `LicenseRecover.NET.exe`。目标 DLL 不修改，申请号/授权码/DoRegistry/CheckReInfo 仍全部由目标注册组件执行。
- `.NET` 选择规则保持“小写优先、大写回退”：小写链改用 ASP.NET host helper；仅大写 `ITMC.Regedit.dll` 时继续使用 `LicenseRecover.NET.Modern.exe`。
- 修复一键恢复 UI 覆盖层日志只写屏幕、不写 `logs/LicenseRecoverGUI-YYYYMMDD.log` 的问题；v1.2.29 新增的“打开日志”现在能看到完整 one-click 原生日志。

### Regression

- CI 新增 ASP.NET host 源码与产物检查，要求 `SimpleWorkerRequest`、`HttpContext.Current`、目标站点物理根目录以及 helper EntryPoint 转交链都存在。
- 保持 PRE-BLOCK FIRST：防火墙规则现在绑定真正承载目标 DLL 的 `LicenseRecover.NET.AspNetHost.exe` 进程；失败仍回滚配置文件。

"""
if not changelog.startswith("## [1.2.29]"):
    raise SystemExit("Unexpected CHANGELOG head")
write("CHANGELOG.md", entry + changelog)

release_notes = """# LicenseRecover v1.2.30

本版修复 v1.2.29 在真实 YX030101 上暴露出的下一层问题：小写 `itmcRegedit.dll` 虽然已经被正确选为权威注册链，但它的注册方法本身依赖 ASP.NET `HttpContext.Current.Server.MapPath(...)`。独立运行的 `LicenseRecover.NET.exe` 没有 IIS/ASP.NET 请求上下文，因此 `NewRegistry -> getRegNo` 会在 `GENCODE` 阶段抛出反射包装异常。

## 修复

- 新增 `LicenseRecover.NET.AspNetHost.exe`，为小写 `itmcRegedit.dll` 链创建最小 ASP.NET 宿主上下文。
- 宿主以目标 `bin` 的父目录作为 Web 应用物理根目录，使目标组件自己的 `~/Register.xml`、`~/config.xml` 路径解析与 IIS 运行时保持一致。
- 宿主只负责提供 `HttpContext` 并转交现有 helper EntryPoint；不修改目标 DLL，不替换目标注册算法。
- 小写链仍按目标组件自己的 `registrationProduct` 生成动态申请号、离线授权码，并继续调用目标 `DoRegistry()` / `CheckReInfo()`。
- 大写 `ITMC.Regedit.dll` 回退链不变，继续使用 `LicenseRecover.NET.Modern.exe`。
- 修复 one-click 覆盖层持久日志，失败时“打开日志”能直接看到完整阶段输出。

## 验证重点

真实 YX030101 应从原来的 `GENCODE exit=1 / 调用的目标发生了异常` 进入正常的 `GENCODE -> DOREG -> VERIFY` 链；若目标组件仍有其它内部异常，新宿主会输出内层异常类型和消息，便于继续定位。
"""
write("release-notes/v1.2.30.md", release_notes)

# ASP.NET host wrapper. It forwards the exact existing helper command line after
# constructing the System.Web context required by lowercase itmcRegedit.dll.
host_source = r'''using System;
using System.IO;
using System.Reflection;
using System.Text;
using System.Web;
using System.Web.Hosting;

internal static class LicenseRecoverAspNetHost
{
    private static string WithTrailingSeparator(string path)
    {
        string full = Path.GetFullPath(path);
        if (!full.EndsWith(Path.DirectorySeparatorChar.ToString(), StringComparison.Ordinal))
            full += Path.DirectorySeparatorChar;
        return full;
    }

    private static string ResolveAppRoot(string runtimeDir)
    {
        string full = Path.GetFullPath(runtimeDir);
        string leaf = new DirectoryInfo(full).Name;
        if (string.Equals(leaf, "bin", StringComparison.OrdinalIgnoreCase))
        {
            DirectoryInfo parent = Directory.GetParent(full);
            if (parent != null) return parent.FullName;
        }
        return full;
    }

    private static HttpContext CreateContext(string appRoot)
    {
        string physical = WithTrailingSeparator(appRoot);
        AppDomain.CurrentDomain.SetData(".appPath", physical);
        AppDomain.CurrentDomain.SetData(".appVPath", "/");
        SimpleWorkerRequest worker = new SimpleWorkerRequest(
            "/", physical, "default.aspx", "", TextWriter.Null);
        return new HttpContext(worker);
    }

    public static int Main(string[] args)
    {
        try { Console.OutputEncoding = Encoding.UTF8; } catch { }
        if (args == null || args.Length < 2)
        {
            Console.Error.WriteLine("[ASPNET_HOST] expected: <command> <target-bin> ...");
            return 2;
        }

        string runtimeDir = Path.GetFullPath(args[1]);
        string appRoot = ResolveAppRoot(runtimeDir);
        if (!Directory.Exists(runtimeDir) || !Directory.Exists(appRoot))
        {
            Console.Error.WriteLine("[ASPNET_HOST] target directory does not exist: " + runtimeDir);
            return 2;
        }

        string helper = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "LicenseRecover.NET.exe");
        if (!File.Exists(helper))
        {
            Console.Error.WriteLine("[ASPNET_HOST] LicenseRecover.NET.exe not found beside host executable.");
            return 2;
        }

        HttpContext previous = HttpContext.Current;
        try
        {
            HttpContext.Current = CreateContext(appRoot);
            Console.WriteLine("[ASPNET_HOST] appRoot=" + appRoot);
            Console.WriteLine("[ASPNET_HOST] runtimeDir=" + runtimeDir);

            Assembly assembly = Assembly.LoadFrom(helper);
            MethodInfo entry = assembly.EntryPoint;
            if (entry == null)
                throw new InvalidOperationException("LicenseRecover.NET.exe has no EntryPoint.");

            object[] invokeArgs = entry.GetParameters().Length == 0
                ? null
                : new object[] { args };
            object value = entry.Invoke(null, invokeArgs);
            if (entry.ReturnType == typeof(int) && value != null)
                return (int)value;
            return 0;
        }
        catch (TargetInvocationException ex)
        {
            Exception inner = ex.InnerException ?? ex;
            Console.Error.WriteLine("[ASPNET_HOST] helper invocation failed: "
                + inner.GetType().FullName + ": " + inner.Message);
            if (!string.IsNullOrEmpty(inner.StackTrace))
                Console.Error.WriteLine(inner.StackTrace);
            return 1;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("[ASPNET_HOST] " + ex.GetType().FullName + ": " + ex.Message);
            if (!string.IsNullOrEmpty(ex.StackTrace))
                Console.Error.WriteLine(ex.StackTrace);
            return 1;
        }
        finally
        {
            HttpContext.Current = previous;
        }
    }
}
'''
write("src/dotnet/LicenseRecover.AspNetHost.cs", host_source)

# AutoRecovery: lowercase chain must run inside the ASP.NET host wrapper.
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    'File helper = lowercaseChain ? findDotNetNativeHelper() : findDotNetModernNativeHelper();\n        if (helper == null) {\n            String expected = lowercaseChain ? "LicenseRecover.NET.exe" : "LicenseRecover.NET.Modern.exe";',
    'File helper = lowercaseChain ? findDotNetAspNetHostHelper() : findDotNetModernNativeHelper();\n        if (helper == null) {\n            String expected = lowercaseChain ? "LicenseRecover.NET.AspNetHost.exe" : "LicenseRecover.NET.Modern.exe";'
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    'log.accept("[dotnet-chain] lowercase itmcRegedit.dll preferred; appProduct=" + appProduct\n                    + " registrationProduct=" + registrationProduct + "\\n");',
    'log.accept("[dotnet-chain] lowercase itmcRegedit.dll preferred via ASP.NET host; appProduct=" + appProduct\n                    + " registrationProduct=" + registrationProduct + "\\n");'
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '    private static File findDotNetModernNativeHelper() {\n        File dir = toolDir();',
    '    private static File findDotNetAspNetHostHelper() {\n        File dir = toolDir();\n        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.AspNetHost.exe");\n        if (nested.isFile()) return nested;\n        File flat = new File(dir, "LicenseRecover.NET.AspNetHost.exe");\n        return flat.isFile() ? flat : null;\n    }\n\n    private static File findDotNetModernNativeHelper() {\n        File dir = toolDir();'
)

# The UI patch appended one-click output directly to the JTextArea, bypassing
# LicenseRecoverModernGUI.appendPersistentLog. Persist that stream here too.
replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    'import java.io.File;\nimport java.text.SimpleDateFormat;\nimport java.util.ArrayList;',
    'import java.io.File;\nimport java.nio.charset.StandardCharsets;\nimport java.nio.file.Files;\nimport java.nio.file.StandardOpenOption;\nimport java.text.SimpleDateFormat;\nimport java.util.ArrayList;'
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''    private static void append(final JTextArea area, final String text) {\n        if (area == null || text == null || text.isEmpty()) return;\n        SwingUtilities.invokeLater(() -> {\n            area.append(text);\n            area.setCaretPosition(area.getDocument().getLength());\n        });\n    }''',
    '''    private static void append(final JTextArea area, final String text) {\n        if (area == null || text == null || text.isEmpty()) return;\n        appendPersistentOneClickLog(text);\n        SwingUtilities.invokeLater(() -> {\n            area.append(text);\n            area.setCaretPosition(area.getDocument().getLength());\n        });\n    }\n\n    private static final Object ONE_CLICK_LOG_LOCK = new Object();\n\n    private static void appendPersistentOneClickLog(String text) {\n        try {\n            synchronized (ONE_CLICK_LOG_LOCK) {\n                File dir = new File(toolDir(), "logs");\n                Files.createDirectories(dir.toPath());\n                String day = new SimpleDateFormat("yyyyMMdd").format(new Date());\n                File file = new File(dir, "LicenseRecoverGUI-" + day + ".log");\n                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());\n                String entry = "[" + stamp + "] " + text;\n                Files.write(file.toPath(), entry.getBytes(StandardCharsets.UTF_8),\n                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);\n            }\n        } catch (Throwable ignore) {\n            // Diagnostic persistence must never turn recovery into a failure.\n        }\n    }'''
)

# CI/toolchain is already present on main.

# verify.ps1: build/package/check ASP.NET host.
replace_once(
    "scripts/verify.ps1",
    "$modernNativeHelper = Join-Path $buildRoot 'LicenseRecover.NET.Modern.exe'",
    "$modernNativeHelper = Join-Path $buildRoot 'LicenseRecover.NET.Modern.exe'\n$aspNetHostSource = Join-Path $repoRoot 'src/dotnet/LicenseRecover.AspNetHost.cs'\n$aspNetHostHelper = Join-Path $buildRoot 'LicenseRecover.NET.AspNetHost.exe'"
)
replace_once(
    "scripts/verify.ps1",
    "if (-not (Test-Path -LiteralPath $modernNativeHelper)) { throw 'Modern .NET helper adapter was not created.' }",
    "if (-not (Test-Path -LiteralPath $modernNativeHelper)) { throw 'Modern .NET helper adapter was not created.' }\n\nWrite-Host 'Building lowercase itmcRegedit ASP.NET host helper...'\nif (-not (Test-Path -LiteralPath $aspNetHostSource)) { throw 'Missing src/dotnet/LicenseRecover.AspNetHost.cs.' }\n$mcs = Get-Command mcs -ErrorAction SilentlyContinue\nif ($null -eq $mcs) { throw 'Mono mcs compiler is required to build the ASP.NET host helper.' }\nInvoke-External -Command $mcs.Source -ArgumentList @(\n    '-nologo', '-target:exe', '-optimize+', '-r:System.Web.dll',\n    ('-out:' + $aspNetHostHelper), $aspNetHostSource\n)\nif (-not (Test-Path -LiteralPath $aspNetHostHelper)) { throw 'ASP.NET host helper was not created.' }\n$aspHostBytes = [IO.File]::ReadAllBytes($aspNetHostHelper)\n$aspHostUnicode = [Text.Encoding]::Unicode.GetString($aspHostBytes)\nforeach ($marker in @('SimpleWorkerRequest','HttpContext','LicenseRecover.NET.exe','ASPNET_HOST')) {\n    if ($aspHostUnicode.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {\n        throw \"ASP.NET host helper is missing marker: $marker\"\n    }\n}"
)
replace_once(
    "scripts/verify.ps1",
    "if (-not $autoSource.Contains('LicenseRecover.NET.exe') -or -not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw '.NET one-click must retain lowercase native helper plus uppercase fallback adapter.' }",
    "if (-not $autoSource.Contains('LicenseRecover.NET.AspNetHost.exe') -or -not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw '.NET one-click must use the ASP.NET host for lowercase chain plus uppercase fallback adapter.' }"
)
replace_once(
    "scripts/verify.ps1",
    "if (-not $modernGuiSource.Contains('appendPersistentLog')) { throw 'Modern GUI persistent diagnostics are missing.' }",
    "if (-not $modernGuiSource.Contains('appendPersistentLog')) { throw 'Modern GUI persistent diagnostics are missing.' }\nif (-not $uiPatchSource.Contains('appendPersistentOneClickLog')) { throw 'One-click overlay persistent diagnostics are missing.' }"
)
# The previous replacement references uiPatchSource before its old assignment; move the assignment earlier.
text = read("scripts/verify.ps1")
old = "$modernGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUI.java') -Raw\nif ($coreSource.Contains"
new = "$modernGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUI.java') -Raw\n$uiPatchSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIUiPatchLauncher.java') -Raw\nif ($coreSource.Contains"
if old not in text:
    raise SystemExit("verify.ps1: could not move uiPatchSource assignment")
text = text.replace(old, new, 1)
text = text.replace("$uiPatchSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIUiPatchLauncher.java') -Raw\nif (-not $uiPatchSource.Contains('showOneClickFailureDialog'))", "if (-not $uiPatchSource.Contains('showOneClickFailureDialog'))", 1)
write("scripts/verify.ps1", text)

replace_once(
    "scripts/verify.ps1",
    "Copy-Item -LiteralPath $modernNativeHelper -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe') -Force",
    "Copy-Item -LiteralPath $modernNativeHelper -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe') -Force\nCopy-Item -LiteralPath $aspNetHostHelper -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.AspNetHost.exe') -Force"
)
replace_once(
    "scripts/verify.ps1",
    "    Copy-Item -LiteralPath $nativeConfig -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe.config') -Force",
    "    Copy-Item -LiteralPath $nativeConfig -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe.config') -Force\n    Copy-Item -LiteralPath $nativeConfig -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.AspNetHost.exe.config') -Force"
)

# Smoke tests: source-level guards for the new host + persistent one-click log.
smoke = read("src/test/java/RefactorSmokeTest.java")
anchor = '        System.out.println("ALL REFACTOR SMOKE TESTS PASSED");'
if anchor not in smoke:
    raise SystemExit("Smoke-test success anchor not found")
insert = r'''        String autoSource230 = new String(Files.readAllBytes(Paths.get("src/main/java/LicenseRecoverModernGUIAutoRecovery.java")), StandardCharsets.UTF_8);
        require(autoSource230.contains("LicenseRecover.NET.AspNetHost.exe"), "lowercase .NET chain uses ASP.NET host helper");
        require(autoSource230.contains("findDotNetAspNetHostHelper"), "ASP.NET host helper lookup is present");
        String hostSource230 = new String(Files.readAllBytes(Paths.get("src/dotnet/LicenseRecover.AspNetHost.cs")), StandardCharsets.UTF_8);
        require(hostSource230.contains("SimpleWorkerRequest"), "ASP.NET host creates SimpleWorkerRequest");
        require(hostSource230.contains("HttpContext.Current = CreateContext"), "ASP.NET host installs HttpContext.Current");
        require(hostSource230.contains("Assembly.LoadFrom(helper)"), "ASP.NET host forwards to existing helper assembly");
        require(hostSource230.contains("ResolveAppRoot(runtimeDir)"), "ASP.NET host resolves target web root from runtime dir");
        String uiPatch230 = new String(Files.readAllBytes(Paths.get("src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java")), StandardCharsets.UTF_8);
        require(uiPatch230.contains("appendPersistentOneClickLog"), "one-click overlay writes persistent diagnostics");
'''
smoke = smoke.replace(anchor, insert + anchor, 1)
write("src/test/java/RefactorSmokeTest.java", smoke)

print("v1.2.30 ASP.NET host patch staged")
