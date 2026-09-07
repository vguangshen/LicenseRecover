#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
TEST = ROOT / 'src/test/java/RefactorSmokeTest.java'
VERIFY = ROOT / 'scripts/verify.ps1'

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit('missing anchor: ' + label)
    return text.replace(old, new, 1)

def replace_between(text, start, end, replacement, label):
    a = text.find(start)
    if a < 0:
        raise SystemExit('missing start: ' + label)
    b = text.find(end, a)
    if b < 0:
        raise SystemExit('missing end: ' + label)
    return text[:a] + replacement + text[b:]

p = PLAN.read_text(encoding='utf-8')
# Remove catch-all fallback data completely.
p = replace_between(
    p,
    '    // Must stay byte-for-byte equivalent in meaning to LicenseRecover.ALL_NUMS.\n',
    '    public final boolean detected;\n',
    '',
    'FALLBACK_ALL_NUMS block')

strict_block = '''        String dataSoft = readElement(dataConfig, "SoftVersionID");
        String dataRegInfo = normalizeCsv(readElement(dataConfig, "regInfo"));
        String classesRegInfo = normalizeCsv(readElement(classesConfig, "regInfo"));
        boolean directDataIdentity = newStyle && !blank(soft) && !blank(dataSoft)
                && soft.trim().equalsIgnoreCase(dataSoft.trim()) && dataRegInfo != null;

        // No product-family/runtime-id guessing here. The executable plan receives
        // identity only from target-directory evidence: parsed Global/RegisterUtil,
        // confirmed classes config, or a self-describing data/config.xml generation.
        String family = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : null));
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : null));
        File jar = findRegJar(lib);
        boolean packed = jar != null && isVirboxPackedJar(jar);
        String products = directoryMapping != null ? directoryMapping.productMainNum
                : resolveRegStr(root, runtimeProduct, lib);

        String recoveredLocalRegStr = blank(runtimeProduct)
                ? null : ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
        boolean directoryIdentity = directoryMapping != null
                || directDataIdentity || !blank(confirmedClassesFamily);
        boolean directoryRegStr = directoryMapping != null
                || dataRegInfo != null || classesRegInfo != null || recoveredLocalRegStr != null;

'''
p = replace_between(
    p,
    '        String family = directoryMapping != null ? directoryMapping.productMain\n',
    '        boolean ready = true;\n',
    strict_block,
    'executable identity block')

strict_regstr = '''    private static String resolveRegStr(File root, String runtimeProduct, File lib) {
        String data = normalizeCsv(readElement(new File(root, "data" + File.separator + "config.xml"), "regInfo"));
        if (data != null) return data;
        String classes = normalizeCsv(readElement(new File(root, "WEB-INF" + File.separator
                + "classes" + File.separator + "config.xml"), "regInfo"));
        if (classes != null) return classes;
        return blank(runtimeProduct) ? null : ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
    }

'''
p = replace_between(
    p,
    '    private static String resolveRegStr(',
    '    static String confirmedClassesAuthorizationFamily(',
    strict_regstr,
    'resolveRegStr')
PLAN.write_text(p, encoding='utf-8', newline='\n')

t = TEST.read_text(encoding='utf-8')
repls = [
('''        check("QT0420".equals(LicenseRecover.resolveJavaRegStr(yt129Root.toString(), "YT00129")), "YT00129 embeds QT0420 authorization product");''',
 '''        check(LicenseRecover.resolveJavaRegStr(yt129Root.toString(), "YT00129") == null,
                "YT00129 inferred QT0420 is not accepted without target-directory evidence");'''),
('''        check(yt129Plan.detected && "QT04".equals(yt129Plan.productName),
                "Java GUI plan maps YT00129 to QT04");
        check("QT0420".equals(yt129Plan.regStr) && yt129Plan.rootConfigStyle,
                "Java GUI plan exposes YT00129 RegStr and root config target");''',
 '''        check(yt129Plan.detected && yt129Plan.productName == null
                        && yt129Plan.authorizationFamily == null,
                "Java executable plan does not map YT00129 to QT04 without directory evidence");
        check(yt129Plan.regStr == null && yt129Plan.rootConfigStyle
                        && !yt129Plan.automaticRecoveryReady,
                "YT00129 stays fail-closed instead of exposing inferred QT0420 RegStr");'''),
('''        check("DS2406".equals(LicenseRecover.resolveJavaRegStr(ds2406Root.toString(), "DS2406")),
                "DS2406 RegStr contains the concrete VersionID required by the application gate");''',
 '''        check(LicenseRecover.resolveJavaRegStr(ds2406Root.toString(), "DS2406") == null,
                "DS2406 does not invent RegStr from VersionID");'''),
('''        check(ds2406Plan.generation.contains("DS24")
                        && "DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId),
                "Java GUI plan models DS2406 as DS24 authorization family");''',
 '''        check(ds2406Plan.generation.contains("DS24")
                        && ds2406Plan.authorizationFamily == null
                        && ds2406Plan.runtimeProductId == null,
                "DS2406 generation label does not become executable identity without directory proof");'''),
('''        check("QT0423,QT0428,QT0424,QT0425,QT0427,QT0426,QT0406,QT0430".equals(LicenseRecover.resolveJavaRegStr(xmtRoot.toString(), "XMT0107")), "XMT0107 preserves classes regInfo");''',
 '''        check(LicenseRecover.resolveJavaRegStr(xmtRoot.toString(), "XMT0107") == null,
                "XMT0107 classes RegStr alone is insufficient without directory-proven identity");'''),
('''        check(xmtPlan.detected && "XMT01".equals(xmtPlan.productName),
                "Java GUI plan maps XMT0107 to XMT01");
        check(xmtPlan.generation.contains("XMT") && xmtPlan.regStrSummary().startsWith("8 项"),
                "Java GUI plan exposes XMT generation and compact RegStr summary");''',
 '''        check(xmtPlan.detected && xmtPlan.productName == null
                        && xmtPlan.authorizationFamily == null,
                "Java executable plan does not infer XMT01 family from VersionID");
        check(xmtPlan.generation.contains("XMT") && xmtPlan.regStrSummary().startsWith("8 项")
                        && !xmtPlan.automaticRecoveryReady,
                "XMT directory RegStr may be displayed but execution stays blocked until identity is proven");'''),
('''        check("DS50109".equals(LicenseRecover.resolveJavaRegStr(ds501Root.toString(), "DS50109")),
                "DS501 fallback RegStr contains concrete SoftVersionID");''',
 '''        check(LicenseRecover.resolveJavaRegStr(ds501Root.toString(), "DS50109") == null,
                "DS501 does not use concrete SoftVersionID as fallback RegStr");'''),
('''        check(ds501Plan.generation.contains("DS501") && "DS501".equals(ds501Plan.authorizationFamily),
                "Java GUI plan names DS501 family instead of generic classes-config");
        check("DS50109".equals(ds501Plan.runtimeProductId) && "DS50109".equals(ds501Plan.regStr),
                "Java GUI plan separates DS501 family from runtime id and RegStr");''',
 '''        check(ds501Plan.generation.contains("DS501") && ds501Plan.authorizationFamily == null,
                "DS501 generation label does not imply an executable family");
        check(ds501Plan.runtimeProductId == null && ds501Plan.regStr == null
                        && !ds501Plan.automaticRecoveryReady,
                "DS501 stays fail-closed without directory identity/RegStr evidence");'''),
('''        check("QT100101,QT100102".equals(LicenseRecover.resolveJavaRegStr(yx305Root.toString(), "YX030506")),
                "YX030506 preserves declared classes-config regInfo");''',
 '''        check(LicenseRecover.resolveJavaRegStr(yx305Root.toString(), "YX030506") == null,
                "YX030506 classes RegStr alone cannot authorize execution without identity evidence");'''),
('''        check(yx305Plan.generation.contains("YX0305") && "YX0305".equals(yx305Plan.authorizationFamily),
                "Java GUI plan names YX0305 family instead of generic classes-config");
        check("YX030506".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr),
                "Java GUI plan separates YX0305 family, runtime id and feature RegStr");''',
 '''        check(yx305Plan.generation.contains("YX0305") && yx305Plan.authorizationFamily == null,
                "YX0305 generation label does not imply executable identity");
        check(yx305Plan.runtimeProductId == null
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && !yx305Plan.automaticRecoveryReady,
                "YX0305 directory RegStr is retained for diagnostics but execution is blocked without identity proof");'''),
('''        check("QT40101".equals(LicenseRecover.resolveJavaRegStr(qt401Root.toString(), "QT40101")),
                "QT40101 uses the exact-sample verified minimum RegStr, not the 44-item fallback");''',
 '''        check(LicenseRecover.resolveJavaRegStr(qt401Root.toString(), "QT40101") == null,
                "QT40101 no longer synthesizes minimum RegStr from a known sample");'''),
('''        check("QT40101".equals(qt401Plan.regStr) && qt401Plan.automaticRecoveryReady
                        && qt401Plan.recoveryReadiness.contains("可安全"),
                "QT40101 automatic recovery is ready with the exact-sample verified minimum RegStr");''',
 '''        check(qt401Plan.regStr == null && !qt401Plan.automaticRecoveryReady
                        && qt401Plan.recoveryReadiness.contains("RegStr"),
                "QT40101 identity may be confirmed by config1.xml but RegStr must still come from the target directory");'''),
('''        check("QT100101".equals(LicenseRecover.resolveJavaRegStr(qt100101Root.toString(), "QT100101")),
                "QT100101 derives the verified minimum RegStr from its real application gate");''',
 '''        check(LicenseRecover.resolveJavaRegStr(qt100101Root.toString(), "QT100101") == null,
                "QT100101 no longer synthesizes minimum RegStr from a known sample");''')
]
for old, new in repls:
    t = replace_once(t, old, new, old.splitlines()[0][:80])
TEST.write_text(t, encoding='utf-8', newline='\n')

v = VERIFY.read_text(encoding='utf-8')
anchor = "Write-Host 'Building deterministic runtime overlay...'"
if 'Java plan still contains catch-all fallback RegStr' not in v:
    guard = '''$javaPlanSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIJavaPlan.java') -Raw
if ($javaPlanSource.Contains('FALLBACK_ALL_NUMS')) { throw 'Java plan still contains catch-all fallback RegStr.' }
if ($javaPlanSource.Contains('return "QT0420"')) { throw 'Java plan still synthesizes YT00129 RegStr.' }
if ($javaPlanSource.Contains('return "DS2406"')) { throw 'Java plan still synthesizes DS2406 RegStr.' }
if ($javaPlanSource.Contains('return "QT40101"')) { throw 'Java plan still synthesizes QT40101 RegStr.' }
if (-not $javaPlanSource.Contains('No product-family/runtime-id guessing here')) { throw 'Strict Java directory identity marker missing.' }

'''
    if anchor not in v:
        raise SystemExit('verify anchor missing')
    v = v.replace(anchor, guard + anchor, 1)
VERIFY.write_text(v, encoding='utf-8', newline='\n')

print('Finalized directory-only Java execution plan and strict smoke tests.')
