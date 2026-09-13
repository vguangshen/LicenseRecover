from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)


plan_path = Path("src/main/java/LicenseRecoverModernGUIJavaPlan.java")
text = plan_path.read_text(encoding="utf-8")

old = """        String configDrivenRuntime = confirmedConfigDrivenRuntimeProduct(root, soft, family);
        // QT401/QT100101 use the confirmed classes family as their runtime product.
"""
new = """        String configDrivenRuntime = confirmedConfigDrivenRuntimeProduct(root, soft, family);
        // Real DS501/YX0305 deployments do not always embed the concrete SoftVersionID
        // as an independent constant-pool token. When the concrete id is read from the
        // target's own classes/config.xml, the family is independently proven from the
        // target binaries, and the target startup bytecode itself proves a
        // RegisterMain.checkReInfo() constructor chain, that startup chain is stronger
        // evidence than an artificial exact-string requirement.
        String startupRuntime = blank(binaryRuntime) && blank(configDrivenRuntime)
                ? confirmedStartupRuntimeProduct(root, lib, soft, family) : null;
        // QT401/QT100101 use the confirmed classes family as their runtime product.
"""
text = replace_once(text, old, new, "insert startupRuntime")

old = """        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (directDataIdentity ? soft.trim()
                : (!blank(configDrivenRuntime) ? configDrivenRuntime
                : (!blank(binaryRuntime) ? binaryRuntime : confirmedClassesRuntime)));
"""
new = """        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (directDataIdentity ? soft.trim()
                : (!blank(configDrivenRuntime) ? configDrivenRuntime
                : (!blank(binaryRuntime) ? binaryRuntime
                : (!blank(startupRuntime) ? startupRuntime : confirmedClassesRuntime))));
"""
text = replace_once(text, old, new, "runtimeProduct fallback")

old = """        boolean binaryIdentity = !blank(binaryFamily)
                && (!blank(binaryRuntime) || !blank(configDrivenRuntime));
"""
new = """        boolean binaryIdentity = !blank(binaryFamily)
                && (!blank(binaryRuntime) || !blank(configDrivenRuntime) || !blank(startupRuntime));
"""
text = replace_once(text, old, new, "binaryIdentity startup proof")

marker = """    /**
     * QT401xx/RuoYi generation: config1.xml proves registration product QT401,
"""
helper = """    /**
     * Pure decision helper used by tests and by the target startup proof below.
     * The family must already have independent target-binary evidence; this method
     * never derives DS501/YX0305 merely from the VersionID prefix.
     */
    static String selectStartupConfirmedConcreteRuntime(String softId, String configuredSoftId,
                                                        String confirmedFamily,
                                                        boolean startupCheckReInfo) {
        if (blank(softId) || blank(configuredSoftId) || blank(confirmedFamily)
                || !startupCheckReInfo) return null;
        String concrete = softId.trim();
        String configured = configuredSoftId.trim();
        String family = confirmedFamily.trim();
        if (!concrete.equalsIgnoreCase(configured)) return null;
        String upper = concrete.toUpperCase(Locale.ROOT);
        boolean supported = (upper.startsWith("DS501") && "DS501".equalsIgnoreCase(family))
                || (upper.startsWith("YX0305") && "YX0305".equalsIgnoreCase(family));
        return supported ? concrete : null;
    }

    /**
     * Confirm the concrete DS501xx/YX0305xx runtime product from the selected target's
     * own config plus its actual RegisterMain startup ABI. JavaRegistrationRuntimeProfile
     * parses class files only; it does not load or execute the selected application's code.
     * At least one proven startup attempt must call checkReInfo().
     */
    static String confirmedStartupRuntimeProduct(File root, File lib, String softId,
                                                 String confirmedFamily) {
        if (root == null || lib == null || blank(softId) || blank(confirmedFamily)) return null;
        File config = new File(root, "WEB-INF" + File.separator + "classes"
                + File.separator + "config.xml");
        String configured = readElement(config, "SoftVersionID");
        if (selectStartupConfirmedConcreteRuntime(softId, configured, confirmedFamily, true) == null)
            return null;
        JavaRegistrationRuntimeProfile profile = JavaRegistrationRuntimeProfile.inspect(root, lib);
        if (!profile.supported || profile.attempts == null || profile.attempts.isEmpty()) return null;
        boolean checkReInfo = false;
        for (JavaRegistrationRuntimeProfile.Attempt attempt : profile.attempts) {
            if (attempt != null && attempt.checkReInfo) {
                checkReInfo = true;
                break;
            }
        }
        return selectStartupConfirmedConcreteRuntime(
                softId, configured, confirmedFamily, checkReInfo);
    }

"""
if text.count(marker) != 1:
    raise SystemExit(f"helper insertion marker count={text.count(marker)}")
text = text.replace(marker, helper + marker, 1)
plan_path.write_text(text, encoding="utf-8")

test_path = Path("src/test/java/RefactorSmokeTest.java")
test = test_path.read_text(encoding="utf-8")
old = """        check(JavaRegistrationRuntimeProfile.selectExplicitBase(yxRootBase, yxClassesBase, true, true)
                        .equals(yxRootBase),
                "Servlet root remains authoritative when both explicit path proofs are present");
"""
new = old + """        check("DS50109".equals(LicenseRecoverModernGUIJavaPlan.selectStartupConfirmedConcreteRuntime(
                        "DS50109", "DS50109", "DS501", true)),
                "DS501 concrete runtime id may be proven by exact target config plus checkReInfo startup chain");
        check("YX030506".equals(LicenseRecoverModernGUIJavaPlan.selectStartupConfirmedConcreteRuntime(
                        "YX030506", "YX030506", "YX0305", true)),
                "YX0305 concrete runtime id may be proven by exact target config plus checkReInfo startup chain");
        check(LicenseRecoverModernGUIJavaPlan.selectStartupConfirmedConcreteRuntime(
                        "DS50109", "DS50112", "DS501", true) == null,
                "startup runtime proof rejects a mismatched classes/config SoftVersionID");
        check(LicenseRecoverModernGUIJavaPlan.selectStartupConfirmedConcreteRuntime(
                        "DS50109", "DS50109", "DS501", false) == null,
                "startup runtime proof requires a target checkReInfo call site");
        check(LicenseRecoverModernGUIJavaPlan.selectStartupConfirmedConcreteRuntime(
                        "ZZ99999", "ZZ99999", "ZZ999", true) == null,
                "startup runtime proof does not become a generic VersionID-prefix fallback");
"""
test = replace_once(test, old, new, "smoke tests")
test_path.write_text(test, encoding="utf-8")

Path("VERSION.txt").write_text("1.2.46\n", encoding="utf-8")
notes = """# LicenseRecover v1.2.46

修复 v1.2.45 中真实 DS50109 与 YX030506 仍被批量扫描标记为“待确认”的回归。

- 根因：v1.2.45 仍要求具体 DS501xx / YX0305xx RuntimeProductID 作为独立 ASCII token 出现在目标字节码，真实软件会从 `WEB-INF/classes/config.xml` 动态读取具体 `SoftVersionID`，因此该条件过严。
- 新证据链：具体 `SoftVersionID` 必须与目标 `classes/config.xml` 完全一致；DS501 / YX0305 授权族仍必须由目标二进制独立证明；同时目标自身启动字节码必须被 `JavaRegistrationRuntimeProfile` 证明存在 `RegisterMain.checkReInfo()` 调用链。
- DS50109 不再凭版本号合成静态 RegStr；若目录没有静态 RegStr，执行恢复时仍由目标 `RegisterMain.getRegInfo()` 只读探测，再进行写入与 fresh-JVM 校验。
- YX030506 继续使用目标 `classes/config.xml` 中的 `regInfo=QT100101,QT100102`，并保持 `ProjectSourcesPath -> WEB-INF/classes` 注册基目录证据链。
- 新增回归测试：配置 VersionID 不一致、缺少 checkReInfo、未知产品族都必须继续 fail-closed。
"""
notes_path = Path("release-notes/v1.2.46.md")
notes_path.parent.mkdir(parents=True, exist_ok=True)
notes_path.write_text(notes, encoding="utf-8")
