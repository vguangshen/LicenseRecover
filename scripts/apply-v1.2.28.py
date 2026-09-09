#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected one match, found {count}: {old[:160]!r}")
    write(rel, text.replace(old, new, 1))


version = read("VERSION.txt").strip()
if version == "1.2.28":
    print("v1.2.28 already applied; nothing to do.")
    raise SystemExit(0)
if version != "1.2.27":
    raise SystemExit(f"Expected VERSION.txt=1.2.27, found {version!r}")

AUTO = "src/main/java/LicenseRecoverModernGUIAutoRecovery.java"

# Non-DS01 .NET targets can legitimately expose only the lowercase itmcRegedit.dll.
# Treat either registration assembly as an automatic native-registration target;
# the actual authoritative chain is selected later with lowercase-first precedence.
replace_once(
    AUTO,
    '''            Kind k = LegacyDotNetProtocol.isLegacyVersion(version)\n                    ? Kind.DOTNET_LEGACY\n                    : (new File(bin,"ITMC.Regedit.dll").isFile()\n                    ? Kind.DOTNET_MODERN : Kind.DOTNET_LEGACY);''',
    '''            File targetRegedit = selectDotNetRegeditAssembly(bin);\n            Kind k = LegacyDotNetProtocol.isLegacyVersion(version)\n                    ? Kind.DOTNET_LEGACY\n                    : (targetRegedit != null ? Kind.DOTNET_MODERN : Kind.DOTNET_LEGACY);'''
)

# Replace the whole .NET coordinator so generate/apply/verify all use the same
# target-owned registration chain. Lowercase itmcRegedit.dll is authoritative when
# present; uppercase ITMC.Regedit.dll is a fallback only when lowercase is absent.
text = read(AUTO)
start = text.index("    private static Result recoverDotNet(")
end = text.index("\n    static final class NativeProcessResult", start)
new_func = r'''    private static Result recoverDotNet(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        if (!isWindows()) return Result.fail(".NET native registration runs only on Windows.", d);

        File targetRegedit = selectDotNetRegeditAssembly(d.runtimeDir);
        if (targetRegedit == null)
            return Result.fail("[CHAIN] no target itmcRegedit.dll / ITMC.Regedit.dll registration assembly was found.", d);
        boolean lowercaseChain = "itmcRegedit.dll".equals(targetRegedit.getName());
        File helper = lowercaseChain ? findDotNetNativeHelper() : findDotNetModernNativeHelper();
        if (helper == null) {
            String expected = lowercaseChain ? "LicenseRecover.NET.exe" : "LicenseRecover.NET.Modern.exe";
            return Result.fail("[HELPER] " + expected + " was not found; selected target registration chain cannot run.", d);
        }

        String appProduct = d.productName;
        if (blank(appProduct))
            return Result.fail("Target ITMC.Web.dll did not prove a ProName; default product fallback is disabled.", d);

        String registrationProduct = appProduct;
        if (lowercaseChain) {
            registrationProduct = detectLowercaseDotNetRegistrationProduct(targetRegedit);
            if (blank(registrationProduct))
                return Result.fail("[CHAIN] lowercase itmcRegedit.dll is authoritative, but its registration crypto family "
                        + "could not be proven from matching itmc<family> + *<family>OK* constants.", d);
            log.accept("[dotnet-chain] lowercase itmcRegedit.dll preferred; appProduct=" + appProduct
                    + " registrationProduct=" + registrationProduct + "\n");
        } else {
            log.accept("[dotnet-chain] lowercase itmcRegedit.dll absent; uppercase ITMC.Regedit.dll fallback; product="
                    + appProduct + "\n");
        }

        String regStr = detectDotNetRegStr(d);
        if (blank(regStr))
            return Result.fail("Target application directory did not prove RegStr products; VersionID/default-list fallback is disabled.", d);

        RegisterVersionEvidence compatibility = readRegisterVersionEvidence(d);
        if (compatibility != null && compatibility.relationCount > 0)
            log.accept("[one-click] target RegisterVersion.db compatibility: " + compatibility.summary() + "\n");

        LinkedHashMap<File, byte[]> originals = dryRun ? null : snapshotDotNetRegistrationFiles(d);
        String firewallRule = null;
        try {
            // Network isolation is established BEFORE any vendor registration code is invoked.
            // The selected helper loads the selected target registration assembly; an
            // application-specific Windows Firewall rule blocks even hard-coded outbound URLs.
            firewallRule = installTemporaryDotNetNetworkGuard(helper, log);
            if (blank(firewallRule))
                throw new IOException("[PRE_BLOCK] 无法建立 .NET 临时出站防火墙隔离。请从 LicenseRecoverGUI.exe 启动并通过管理员权限(UAC)，"
                        + "同时确认 Windows Firewall 服务可用；为避免联网校验，已拒绝调用目标注册组件。");
            log.accept("[dotnet-stage] PRE_BLOCK: OK (" + helper.getName() + ")\n");

            if (!dryRun) {
                if (backup) backupDotNetRegistrationFiles(originals, log);
                blockPrimaryDotNetConfigs(d, log);
                blockSidecars(d, false, false, log);
                log.accept("[pre-block] authorization network isolation is active before gencode / DoRegistry / CheckReInfo.\n");
            } else {
                log.accept("[dry-run] temporary process-level outbound guard is active; configuration files remain unchanged.\n");
            }

            List<String> generate = new ArrayList<String>();
            generate.add(helper.getAbsolutePath());
            generate.add("gencode");
            generate.add(d.runtimeDir.getAbsolutePath());
            generate.add("--product"); generate.add(registrationProduct);
            generate.add("--regstr"); generate.add(regStr);
            log.accept("[dotnet-stage] GENCODE: start\n");
            NativeProcessResult generated = runNativeCapture(generate, d.appRoot, log);
            if (generated.exitCode != 0)
                throw nativeFailure("GENCODE", "target-native request-code generation failed", generated);
            log.accept("[dotnet-stage] GENCODE: OK\n");

            String request = findLabeledHex(generated.output, "注册申请号", "申请号");
            String auth = findLabeledHex(generated.output, "离线授权码", "授权码");
            if (blank(request) || blank(auth))
                throw new IOException("[GENCODE_PARSE] helper returned success but request/auth fields were not parsed; output="
                        + compactNativeOutput(generated.output));
            log.accept("[dotnet-stage] GENCODE_PARSE: OK\n");

            String machine = null;
            try {
                String plain = desDecryptHex(request, "itmcsoft");
                if (plain != null && plain.length() >= 20) machine = plain.substring(4, 20);
            } catch (Throwable ignore) { }

            log.accept("[one-click] .NET native registration flow: PRE-BLOCK -> gencode -> DoRegistry -> CheckReInfo\n");
            log.accept("[one-click] appProduct=" + valueOrPending(appProduct)
                    + " registrationProduct=" + valueOrPending(registrationProduct)
                    + " products=" + valueOrPending(regStr) + " RegID=" + valueOrPending(machine) + "\n");
            if (dryRun) {
                log.accept("[dry-run] native request/auth codes generated under outbound isolation; no file was changed.\n");
                return new Result(true, "Preview completed under temporary outbound isolation; no file was changed.",
                        d, machine, request, auth);
            }

            List<String> apply = new ArrayList<String>();
            apply.add(helper.getAbsolutePath());
            apply.add("doreg");
            apply.add(d.runtimeDir.getAbsolutePath());
            apply.add("--seq"); apply.add(request);
            apply.add("--code"); apply.add(auth);
            apply.add("--product"); apply.add(registrationProduct);
            // Keep the helper from changing network settings itself; the outer coordinator
            // already established isolation before the first vendor-code call.
            apply.add("--no-block-net");
            if (!backup) apply.add("--no-backup");
            log.accept("[dotnet-stage] DOREG: start\n");
            NativeProcessResult applied = runNativeCapture(apply, d.appRoot, log);
            if (applied.exitCode != 0)
                throw nativeFailure("DOREG", "target RegeditMain.DoRegistry() rejected the generated code", applied);
            log.accept("[dotnet-stage] DOREG: OK\n");

            List<String> verify = new ArrayList<String>();
            verify.add(helper.getAbsolutePath());
            verify.add("verify");
            verify.add(d.runtimeDir.getAbsolutePath());
            verify.add("--product"); verify.add(registrationProduct);
            log.accept("[dotnet-stage] VERIFY: start\n");
            NativeProcessResult checked = runNativeCapture(verify, d.appRoot, log);
            if (checked.exitCode != 0)
                throw nativeFailure("VERIFY", "selected target RegeditMain.CheckReInfo() did not confirm the native write-back", checked);
            log.accept("[dotnet-stage] VERIFY: OK\n");

            if (blockNet) {
                // Re-assert in case the native writer rewrote config.xml while registering.
                blockPrimaryDotNetConfigs(d, log);
                blockSidecars(d, false, false, log);
            } else {
                restoreDotNetAuthorizationNetworkSettings(originals, d, log);
            }
            log.accept("[verify] selected target registration chain DoRegistry + CheckReInfo passed while outbound traffic was isolated.\n");
            return new Result(true,
                    ".NET local authorization was applied and verified through the target-preferred registration chain while outbound traffic was isolated. Restart the IIS app pool/site.",
                    d, machine, request, auth);
        } catch (Throwable ex) {
            if (!dryRun && originals != null) restoreDotNetRegistrationFiles(originals, log);
            if (ex instanceof Exception) throw (Exception) ex;
            throw new Exception(ex);
        } finally {
            removeTemporaryDotNetNetworkGuard(firewallRule, log);
        }
    }
'''
write(AUTO, text[:start] + new_func + text[end:])

# The original helper is the lowercase-native adapter. The v1.2.27 generated helper
# remains the uppercase-only fallback. Registration family inference is target-owned:
# accept only an itmc<family> key that is paired with a *...<family>OK* local-record key.
replace_once(
    AUTO,
    '''    private static File findDotNetModernNativeHelper() {\n        File dir = toolDir();\n        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.Modern.exe");\n        if (nested.isFile()) return nested;\n        File flat = new File(dir, "LicenseRecover.NET.Modern.exe");\n        return flat.isFile() ? flat : null;\n    }''',
    r'''    private static File findDotNetNativeHelper() {
        File dir = toolDir();
        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.exe");
        if (nested.isFile()) return nested;
        File flat = new File(dir, "LicenseRecover.NET.exe");
        return flat.isFile() ? flat : null;
    }

    private static File findDotNetModernNativeHelper() {
        File dir = toolDir();
        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.Modern.exe");
        if (nested.isFile()) return nested;
        File flat = new File(dir, "LicenseRecover.NET.Modern.exe");
        return flat.isFile() ? flat : null;
    }

    static File findExactChild(File dir, String exactName) {
        if (dir == null || !dir.isDirectory() || exactName == null) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && exactName.equals(file.getName())) return file;
        }
        return null;
    }

    static File selectDotNetRegeditAssembly(File runtimeDir) {
        File lower = findExactChild(runtimeDir, "itmcRegedit.dll");
        if (lower != null) return lower;
        return findExactChild(runtimeDir, "ITMC.Regedit.dll");
    }

    static String detectLowercaseDotNetRegistrationProduct(File lowerDll) {
        return detectLowercaseDotNetRegistrationProduct(extractUtf16Ascii(lowerDll));
    }

    static String detectLowercaseDotNetRegistrationProduct(Set<String> strings) {
        if (strings == null || strings.isEmpty()) return null;
        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        for (String raw : strings) {
            if (raw == null) continue;
            String key = raw.trim();
            if (key.length() <= 4 || !key.regionMatches(true, 0, "itmc", 0, 4)) continue;
            String family = key.substring(4).trim();
            if (!validRegistrationToken(family) || "soft".equalsIgnoreCase(family)) continue;
            String familyUpper = family.toUpperCase(Locale.ROOT);
            boolean paired = false;
            for (String markerRaw : strings) {
                if (markerRaw == null) continue;
                String marker = markerRaw.replaceAll("[^A-Za-z0-9._-]", "").toUpperCase(Locale.ROOT);
                if (marker.endsWith(familyUpper + "OK")) {
                    paired = true;
                    break;
                }
            }
            if (paired) candidates.add(family);
        }
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }'''
)

# Regression tests: the two real lower-case families that exposed the false-OK bug,
# exact lower-first assembly precedence, uppercase fallback, and fail-closed ambiguity.
test_anchor = '''        check(LicenseRecoverModernGUIAutoRecovery.readRegisterVersionEvidence(yx0102NoKeyDetection) == null,\n                "RegisterVersion.db compatibility evidence fails closed when its key is not proven by target DLL");\n'''
test_add = test_anchor + r'''

        check("YX0302".equals(LicenseRecoverModernGUIAutoRecovery.detectLowercaseDotNetRegistrationProduct(
                        new java.util.LinkedHashSet<String>(Arrays.asList(
                                "itmcsoft", "itmcYX0302", "*ITMCYX0302OK*", "itmcRegedit")))),
                "lowercase YX030101 registration family is target-owned YX0302, not app ProName YX0301");
        check("market".equalsIgnoreCase(LicenseRecoverModernGUIAutoRecovery.detectLowercaseDotNetRegistrationProduct(
                        new java.util.LinkedHashSet<String>(Arrays.asList(
                                "itmcsoft", "itmcmarket", "*MarketOK*", "itmcRegedit")))),
                "lowercase YX0102 registration family is target-owned market, not app ProName YS01");
        check(LicenseRecoverModernGUIAutoRecovery.detectLowercaseDotNetRegistrationProduct(
                        new java.util.LinkedHashSet<String>(Arrays.asList(
                                "itmcsoft", "itmcRegedit", "itmcService"))) == null,
                "lowercase registration family inference fails closed without a matching local-record key");

        Path chainBin = base.resolve("dotnet-chain-selection");
        Files.createDirectories(chainBin);
        Files.write(chainBin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        Files.write(chainBin.resolve("itmcRegedit.dll"), new byte[]{2});
        check("itmcRegedit.dll".equals(LicenseRecoverModernGUIAutoRecovery.selectDotNetRegeditAssembly(
                        chainBin.toFile()).getName()),
                ".NET registration chain prefers lowercase itmcRegedit.dll when both assemblies exist");
        Files.delete(chainBin.resolve("itmcRegedit.dll"));
        check("ITMC.Regedit.dll".equals(LicenseRecoverModernGUIAutoRecovery.selectDotNetRegeditAssembly(
                        chainBin.toFile()).getName()),
                ".NET registration chain falls back to uppercase ITMC.Regedit.dll only when lowercase is absent");
'''
replace_once("src/test/java/RefactorSmokeTest.java", test_anchor, test_add)

# CI must keep both adapters and the target-owned chain selection/family inference.
replace_once(
    "scripts/verify.ps1",
    '''if (-not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw 'Modern .NET one-click is not using the uppercase helper adapter.' }\nif (-not $autoSource.Contains('hasDotNetAuthorizationConfigStructure')) { throw 'Modern .NET pre-block cannot distinguish unrelated config.xml files.' }''',
    '''if (-not $autoSource.Contains('LicenseRecover.NET.exe') -or -not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw '.NET one-click must retain lowercase native helper plus uppercase fallback adapter.' }\nif (-not $autoSource.Contains('selectDotNetRegeditAssembly') -or -not $autoSource.Contains('detectLowercaseDotNetRegistrationProduct')) { throw '.NET target-owned lowercase-first registration-chain policy is missing.' }\nif (-not $autoSource.Contains('registrationProduct')) { throw '.NET app product and lowercase registration crypto family are not separated.' }\nif (-not $autoSource.Contains('hasDotNetAuthorizationConfigStructure')) { throw 'Modern .NET pre-block cannot distinguish unrelated config.xml files.' }'''
)

write("VERSION.txt", "1.2.28\n")
replace_once("README.md", "当前稳定版本：**v1.2.27**", "当前稳定版本：**v1.2.28**")

changelog = '''## [1.2.28] - 2026-09-09\n\n### Fixed\n\n- 修复 `.NET` 批量结果全绿但部分网站重启 IIS 后仍进入激活页的“错误注册链假 OK”。真实 YX030101 样本同时包含 `itmcRegedit.dll` 与 `ITMC.Regedit.dll`；v1.2.27 统一走大写链，导致大写 `DoRegistry/CheckReInfo` 自己写、自己验通过，但网站实际优先使用的小写链无法读取该授权。\n- `.NET` 原生注册链改为目标文件优先级：存在小写 `itmcRegedit.dll` 时必须使用原始 `LicenseRecover.NET.exe`；只有小写文件不存在时才回退 `ITMC.Regedit.dll` + `LicenseRecover.NET.Modern.exe`。`gencode -> DoRegistry -> CheckReInfo` 三阶段始终绑定同一条链。\n- 小写链不再把 Web `ProName` 当作授权加密族。根据已恢复的目标组件常量，YX030101 的应用 `ProName=YX0301` 但注册族为 `YX0302`（`itmcYX0302` / `*ITMCYX0302OK*`）；YX0102 的应用 `ProName=YS01` 但注册族为 `market`（`itmcmarket` / `*MarketOK*`）。本版从目标小写 DLL 的成对常量自动证明 registrationProduct，无法唯一证明时 fail-closed。\n\n### Regression\n\n- 新增 YX030101/YX0302 与 YX0102/market 注册族回归、双 DLL 时小写优先、仅大写时回退、无法证明注册族时拒绝执行。\n- 保持 PRE-BLOCK FIRST、失败回滚、目录 RegStr 证据、目标原生 `CheckReInfo` 与持久日志不变。\n\n'''
write("CHANGELOG.md", changelog + read("CHANGELOG.md"))

release_notes = '''# LicenseRecover v1.2.28\n\n本版修复 v1.2.27 在真实 Windows 上出现的“表格全部 OK，但部分 .NET 网站仍停留在激活页”。\n\n## 根因\n\nYX030101 同时携带两套不同注册程序集：`itmcRegedit.dll`（小写、无点）与 `ITMC.Regedit.dll`（大写、带点）。v1.2.27 为修复 uppercase-only 产品而统一调用大写 helper，因此大写 `DoRegistry()` 与大写 `CheckReInfo()` 能相互验证并返回 OK，但 IIS 中网站的实际授权入口优先走小写 `itmcRegedit.dll`，形成了错误链路的假阳性。\n\n恢复后的目标小写组件进一步证明：应用产品名与注册加密族并不总相同。YX030101 的应用产品为 `YX0301`，小写组件实际使用 `itmcYX0302` / `*ITMCYX0302OK*`；YX0102 的应用产品为 `YS01`，小写组件使用 `itmcmarket` / `*MarketOK*`。因此不能把 Web `ProName` 直接作为小写 helper 的 `--product`。\n\n## 修复\n\n- 小写 `itmcRegedit.dll` 存在：优先使用原始 `LicenseRecover.NET.exe`。\n- 小写不存在、仅有 `ITMC.Regedit.dll`：继续使用 v1.2.27 的 `LicenseRecover.NET.Modern.exe`。\n- 对小写链，从目标 DLL 中要求唯一匹配的 `itmc<family>` + `*...<family>OK*` 成对常量，并将其作为 registrationProduct。\n- `gencode`、`doreg`、`verify` 三步统一使用同一 helper 与 registrationProduct，避免一条链生成/写入、另一条链实际读取。\n- 无法从目标 DLL 唯一证明小写注册族时拒绝执行，而不是再次显示假 OK。\n\n本版不修改目标 DLL，仍然走目标程序自己的“动态申请号 -> 本地授权码 -> DoRegistry -> CheckReInfo”本地注册流程。\n'''
write("release-notes/v1.2.28.md", release_notes)

print("v1.2.28 staged changes applied.")
