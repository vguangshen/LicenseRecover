#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(rel): return (ROOT / rel).read_text(encoding='utf-8')
def write(rel, text): (ROOT / rel).write_text(text, encoding='utf-8')
def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing replacement anchor: {label}')
    return text.replace(old, new, 1)
def replace_between(text, start, end, replacement, label):
    a = text.find(start)
    if a < 0: raise SystemExit(f'missing start: {label}')
    b = text.find(end, a)
    if b < 0: raise SystemExit(f'missing end: {label}')
    return text[:a] + replacement + text[b:]

# 1) Any legacy Java target that carries its own Global/RegisterUtil registration logic
# must be resolved from those target files; no family fallback is allowed.
rel = 'src/main/java/LegacyJavaRegistrationMetadata.java'
t = read(rel)
old = '''    /** Legacy YT platforms with their own RegisterUtil must never use a hard-coded fallback. */
    public static boolean requiresDirectoryMapping(File root, String softId) {
        if (root == null || blank(softId)) return false;
        String id = softId.trim().toUpperCase(Locale.ROOT);
        if (!id.startsWith("YT001")) return false;
        return new File(root, "WEB-INF" + File.separator + "classes"
                + File.separator + "com" + File.separator + "common" + File.separator
                + "utils" + File.separator + "RegisterUtil.class").isFile();
    }
'''
new = '''    /**
     * A target that ships its own registration dispatch classes must be resolved from
     * those files. Automatic recovery is fail-closed if they cannot be inspected;
     * callers must never fall back to a built-in family table.
     */
    public static boolean requiresDirectoryMapping(File root, String softId) {
        if (root == null) return false;
        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File registerUtil = new File(classes, "com" + File.separator + "common"
                + File.separator + "utils" + File.separator + "RegisterUtil.class");
        File global = new File(classes, "com" + File.separator + "common"
                + File.separator + "global" + File.separator + "Global.class");
        return registerUtil.isFile() || global.isFile();
    }
'''
t = must_replace(t, old, new, 'requiresDirectoryMapping')
write(rel, t)

# 2) Java preview/automatic plan may retain old inferred labels for diagnostics, but
# automaticRecoveryReady is true only when BOTH identity and RegStr have file evidence.
rel = 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
t = read(rel)
old = '''        String products = directoryMapping != null ? directoryMapping.productMainNum
                : resolveRegStr(root, soft, runtimeProduct, lib);
        boolean ready = true;
        String readiness = "可安全自动恢复";
        if (requiresDirectoryMapping && directoryMapping == null) {
            ready = false;
            readiness = "目标软件目录未解析出注册ID映射";
        } else if (blank(family) || "未确认".equals(family)) {
            ready = false;
            readiness = "授权族未确认";
        } else if (blank(runtimeProduct)) {
            ready = false;
            readiness = "运行校验ID未确认";
        } else if (blank(products)) {
            ready = false;
            readiness = "RegStr 未静态声明；需从现有授权动态恢复";
        }
'''
new = '''        String products = directoryMapping != null ? directoryMapping.productMainNum
                : resolveRegStr(root, soft, runtimeProduct, lib);

        // Automatic write-back is allowed only when the selected application's own
        // directory proves both the registration identity and authorization items.
        // Legacy compatibility tables may still populate preview labels, but they
        // are never sufficient to make a target executable.
        String dataSoft = readElement(dataConfig, "SoftVersionID");
        String dataRegInfo = normalizeCsv(readElement(dataConfig, "regInfo"));
        String classesRegInfo = normalizeCsv(readElement(classesConfig, "regInfo"));
        String recoveredLocalRegStr = blank(runtimeProduct)
                ? null : ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
        boolean directoryIdentity = directoryMapping != null
                || (newStyle && !blank(soft) && !blank(dataSoft)
                    && soft.trim().equalsIgnoreCase(dataSoft.trim()) && dataRegInfo != null)
                || !blank(confirmedClassesFamily);
        boolean directoryRegStr = directoryMapping != null
                || dataRegInfo != null || classesRegInfo != null || recoveredLocalRegStr != null;

        boolean ready = true;
        String readiness = "可安全自动恢复（注册ID/RegStr均来自目标目录）";
        if (blank(soft)) {
            ready = false;
            readiness = "目标软件目录未找到 SoftVersionID";
        } else if (requiresDirectoryMapping && directoryMapping == null) {
            ready = false;
            readiness = "目标软件自带注册分派类，但未解析出注册ID映射";
        } else if (!directoryIdentity) {
            ready = false;
            readiness = "注册ID仅能由旧兼容规则推测，缺少目标目录证据";
        } else if (blank(family) || "未确认".equals(family) || blank(runtimeProduct)) {
            ready = false;
            readiness = "目标软件目录未确认授权族/运行注册ID";
        } else if (!directoryRegStr || blank(products)) {
            ready = false;
            readiness = "目标软件目录未声明或恢复出 RegStr，禁止使用默认授权项";
        }
'''
t = must_replace(t, old, new, 'JavaPlan readiness')
write(rel, t)

# 3) Core Java execution and --gencode must consume the same fail-closed plan.
rel = 'src/main/java/LicenseRecover.java'
t = read(rel)
start = '''        String productMain = (productOverride != null && !productOverride.trim().isEmpty())
                ? productOverride.trim() : "QT1001";
'''
end = '''        System.out.println("======================================================");
'''
replacement = '''        boolean rootConfigStyle = usesRootConfigApp(appRoot);
        LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(new File(appRoot));
        if (!plan.detected || !plan.automaticRecoveryReady) {
            System.err.println("自动恢复已阻止：" + (plan.detected ? plan.recoveryReadiness : "未识别 Java 注册结构"));
            System.err.println("注册ID、运行校验ID与 RegStr 必须从目标软件目录确认，不再使用 QT1001/QT04/YT001 等默认兜底。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        String softId = plan.softVersionId;
        String productMain = plan.runtimeProductId;
        if (productOverride != null && !productOverride.trim().isEmpty()
                && !productOverride.trim().equalsIgnoreCase(productMain)) {
            System.err.println("自动恢复已阻止：-p 指定值 " + productOverride.trim()
                    + " 与目标目录解析出的运行注册ID " + productMain + " 不一致。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        System.out.println("[识别] 目录证据 -> VersionID=" + softId
                + "  AuthorizationFamily=" + plan.authorizationFamily
                + "  RuntimeProductID=" + productMain
                + "  RegStr=" + plan.regStr);

'''
t = replace_between(t, start, end, replacement, 'recoverJavaSingle identity')
t = must_replace(t, '        String resolvedRegStr = resolveJavaRegStr(appRoot, softId);\n',
                 '        String resolvedRegStr = plan.regStr;\n', 'recoverJavaSingle RegStr')

old = '''    /** 生成 (申请号, 授权码, 主板号, 申请时间)。seq 为空则在本机自动生成申请号。 */
    static String[] genRegisterCode(String seq, String registerProductID) throws Exception {
'''
new = '''    /** 生成 (申请号, 授权码, 主板号, 申请时间)。产品族与 RegStr 必须来自目标目录。 */
    static String[] genRegisterCode(String seq, String registerProductID, String regStr) throws Exception {
        if (registerProductID == null || registerProductID.trim().isEmpty())
            throw new IllegalArgumentException("注册产品族未从目标目录确认");
        if (regStr == null || regStr.trim().isEmpty())
            throw new IllegalArgumentException("RegStr 未从目标目录确认");
'''
t = must_replace(t, old, new, 'genRegisterCode signature')
t = must_replace(t,
    '        String plain = "00" + time19 + "00" + sn + "00" + end + "00" + "1" + "00" + "-001" + "00" + "-1" + "00" + ALL_NUMS;\n',
    '        String plain = "00" + time19 + "00" + sn + "00" + end + "00" + "1" + "00" + "-001" + "00" + "-1" + "00" + regStr.trim();\n',
    'genRegisterCode ALL_NUMS')

# Replace --gencode implementation wholesale, up to the legacy mapping helper.
gs = '    static int genCodeMode(String[] args) throws Exception {'
ge = '    /** Product family used by the vendor local-registration page / doRegistry path. */'
new_gencode = '''    static int genCodeMode(String[] args) throws Exception {
        String seq = null;
        String appArg = null;
        String productOverride = null;
        for (int i = 0; i < args.length; i++) {
            if ("--gencode".equals(args[i])) continue;
            if ("--seq".equals(args[i]) && i + 1 < args.length) {
                seq = args[++i];
            } else if ("-p".equals(args[i]) && i + 1 < args.length) {
                productOverride = args[++i];
            } else if (appArg == null) {
                appArg = args[i];
            }
        }
        if (appArg == null || appArg.trim().isEmpty()) {
            System.err.println("[错误] --gencode 必须提供目标应用目录；不再使用默认 YT001/QT1001 产品号。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(new File(appArg));
        if (!plan.detected || !plan.automaticRecoveryReady) {
            System.err.println("[错误] 目标软件目录未确认注册ID/RegStr："
                    + (plan.detected ? plan.recoveryReadiness : "未识别 Java 注册结构"));
            System.out.println("RESULT: FAILED");
            return 2;
        }
        String registerPid = plan.authorizationFamily;
        String regStr = plan.regStr;
        if (productOverride != null && !productOverride.trim().isEmpty()
                && !productOverride.trim().equalsIgnoreCase(registerPid)) {
            System.err.println("[错误] -p 指定值与目标目录解析出的本地注册产品族不一致："
                    + productOverride.trim() + " != " + registerPid);
            System.out.println("RESULT: FAILED");
            return 2;
        }
        System.out.println("[识别] 目录证据 -> VersionID=" + plan.softVersionId
                + "  本地注册产品族=" + registerPid + "  RegStr=" + regStr);
        System.out.println("======================================================");
        System.out.println(" 生成离线授权码 (应用注册界面 -> 本地注册)");
        System.out.println("======================================================");
        System.out.println("注册码产品号       : " + registerPid);
        try {
            boolean hasSeq = seq != null && !seq.trim().isEmpty();
            String[] r = genRegisterCode(seq, registerPid, regStr);
            String genSeq = r[0], code = r[1], sn = r[2], time19 = r[3];
            String localSn = DesUtil.getMotherboardSN();
            if (hasSeq) {
                if (sn.equals(localSn)) {
                    System.out.println("说明               : 申请号来自本机，授权码将绑定本机主机码 " + sn);
                } else {
                    System.out.println("说明               : 申请号主机码 " + sn + " 与本机(" + localSn + ")不同——");
                    System.out.println("                    这是跨机器使用：授权码将绑定申请号所属的那台服务器，属正常。");
                }
                System.out.println("主板号             : " + sn + "   申请时间: " + time19);
                System.out.println("--- 在服务器应用「本地注册」界面粘贴以下授权码并提交 ---");
                System.out.println("    (申请号用服务器页面上获取的那个，无需再填本工具输出的申请号)");
                System.out.println("离线授权码        : " + code);
                System.out.println("RESULT: OK —— 复制上面的 离线授权码 到服务器注册页面的授权码输入框提交即可。");
            } else {
                System.out.println("说明               : 未提供申请号，授权码绑定本机主机码 " + sn);
                System.out.println("                    仅能在本机激活；若目标软件在云服务器，请加 --seq 传服务器的申请号");
                System.out.println("主板号             : " + sn + "   申请时间: " + time19);
                System.out.println("--- 以下填入本机应用注册界面 ---");
                System.out.println("注册申请号        : " + genSeq);
                System.out.println("离线授权码        : " + code);
                System.out.println("RESULT: OK —— 请把上面的 注册申请号 + 离线授权码 填入本机应用的「本地注册」界面并提交。");
            }
        } catch (Exception e) {
            System.err.println("生成失败: " + e.getMessage());
            System.out.println("RESULT: FAILED");
            return 1;
        }
        return 0;
    }

'''
t = replace_between(t, gs, ge, new_gencode, 'genCodeMode')

# Direct helper method must also fail closed rather than expose ALL_NUMS fallback.
rs = '    static String resolveJavaRegStr(String appRoot, String softId) {'
re_end = '    static boolean usesRootConfigApp(String appRoot) {'
new_rs = '''    static String resolveJavaRegStr(String appRoot, String softId) {
        if (appRoot == null) return null;
        LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(new File(appRoot));
        return plan.detected && plan.automaticRecoveryReady ? plan.regStr : null;
    }

'''
t = replace_between(t, rs, re_end, new_rs, 'resolveJavaRegStr')

# Modern .NET CLI gencode must pass target-derived product and RegStr; -p cannot override evidence.
old = '''        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(helper.getAbsolutePath());
        cmd.add(mode);
        cmd.add(binDir);
        String product = getArg(args, "-p");
        if (product != null) { cmd.add("--product"); cmd.add(product); }
        if (seq != null) { cmd.add("--seq"); cmd.add(seq); }
'''
new = '''        String directoryProduct = null;
        String directoryRegStr = null;
        if ("gencode".equals(mode)) {
            LicenseRecoverModernGUIAutoRecovery.Detection nd =
                    LicenseRecoverModernGUIAutoRecovery.detect(dotNetAppRoot(binDir));
            directoryProduct = nd.productName;
            if (directoryProduct == null || directoryProduct.trim().isEmpty()) {
                System.err.println("[错误] 目标 ITMC.Web.dll 未解析出 ProName，禁止使用 helper 默认产品号。");
                System.out.println("RESULT: FAILED");
                System.exit(2);
                return;
            }
            directoryRegStr = LicenseRecoverModernGUIAutoRecovery.detectProductList(
                    new File(binDir, "ITMC.Web.dll"), directoryProduct);
            if (directoryRegStr == null || directoryRegStr.trim().isEmpty()) {
                System.err.println("[错误] 目标 ITMC.Web.dll 未解析出 RegStr 产品项，禁止使用版本号/默认列表兜底。");
                System.out.println("RESULT: FAILED");
                System.exit(2);
                return;
            }
            String override = getArg(args, "-p");
            if (override != null && !override.trim().equalsIgnoreCase(directoryProduct)) {
                System.err.println("[错误] -p 指定值与目标 DLL 解析出的 ProName 不一致："
                        + override.trim() + " != " + directoryProduct);
                System.out.println("RESULT: FAILED");
                System.exit(2);
                return;
            }
        }

        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(helper.getAbsolutePath());
        cmd.add(mode);
        cmd.add(binDir);
        if (directoryProduct != null) { cmd.add("--product"); cmd.add(directoryProduct); }
        if (directoryRegStr != null) { cmd.add("--regstr"); cmd.add(directoryRegStr); }
        if (seq != null) { cmd.add("--seq"); cmd.add(seq); }
'''
t = must_replace(t, old, new, 'dotNetMain gencode evidence')

old = '''        String version = LegacyDotNetProtocol.readSoftVersion(new File(binDir));
        System.out.println("检测到 .NET 旧协议应用（版本: " + (version == null ? "DS01xx" : version)
                + "，产品标识: " + LegacyDotNetProtocol.PRODUCT_NAME + "）");
'''
new = '''        String version = LegacyDotNetProtocol.readSoftVersion(new File(binDir));
        String productName = LegacyDotNetProtocol.readProductName(new File(binDir));
        if (productName == null || productName.trim().isEmpty()) {
            System.err.println("[错误] 目标 ITMC.Web.dll 未解析出旧协议 ProName，禁止使用固定 itmcIEC 兜底。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        System.out.println("检测到 .NET 旧协议应用（版本: " + (version == null ? "未知" : version)
                + "，产品标识(来自目标 DLL): " + productName + "）");
'''
t = must_replace(t, old, new, 'legacy .NET product read')
t = must_replace(t,
    '                    LegacyDotNetProtocol.generateAuthorizationCode(seq.trim(), version);\n',
    '                    LegacyDotNetProtocol.generateAuthorizationCode(seq.trim(), version, productName);\n',
    'legacy .NET generate product')
write(rel, t)

# 4) Modern .NET GUI native closure: both ProName and RegStr must come from ITMC.Web.dll.
rel = 'src/main/java/LicenseRecoverModernGUIAutoRecovery.java'
t = read(rel)
old = '''        String product = d.productName;
        String regStr = blank(product) ? null : detectProductList(new File(d.runtimeDir, "ITMC.Web.dll"), product);
        if (blank(regStr)) regStr = d.versionId;
'''
new = '''        String product = d.productName;
        if (blank(product))
            return Result.fail("Target ITMC.Web.dll did not prove a ProName; default product fallback is disabled.", d);
        String regStr = detectProductList(new File(d.runtimeDir, "ITMC.Web.dll"), product);
        if (blank(regStr))
            return Result.fail("Target ITMC.Web.dll did not prove RegStr products; VersionID/default-list fallback is disabled.", d);
'''
t = must_replace(t, old, new, '.NET GUI fallback removal')
# Always pass both because both are now mandatory.
t = t.replace('        if (!blank(product)) { generate.add("--product"); generate.add(product); }\n        if (!blank(regStr)) { generate.add("--regstr"); generate.add(regStr); }\n',
              '        generate.add("--product"); generate.add(product);\n        generate.add("--regstr"); generate.add(regStr);\n', 1)
t = t.replace('            if (!blank(product)) { apply.add("--product"); apply.add(product); }\n',
              '            apply.add("--product"); apply.add(product);\n', 1)
t = t.replace('            if (!blank(product)) { verify.add("--product"); verify.add(product); }\n',
              '            verify.add("--product"); verify.add(product);\n', 1)
write(rel, t)

# 5) DS01xx legacy .NET protocol: ProName is discovered from the target DLL and
# supplied to crypto helpers; there is no fixed PRODUCT_NAME execution path.
rel = 'src/main/java/LegacyDotNetProtocol.java'
t = read(rel)
t = must_replace(t,
'''    public static final String LOCAL_KEY = "*b2bOK*";
    public static final String PRODUCT_NAME = "itmcIEC";
    public static final String CODE_KEY_PREFIX = "itmc";
    public static final String CODE_KEY = CODE_KEY_PREFIX + PRODUCT_NAME;
''',
'''    public static final String LOCAL_KEY = "*b2bOK*";
    public static final String CODE_KEY_PREFIX = "itmc";
''', 'remove fixed legacy product')
insert_after = '''    public static String readSoftVersion(File binOrRoot) {
'''
# Add product reader before readSoftVersion to avoid duplicate directory resolver code.
pos = t.find(insert_after)
if pos < 0: raise SystemExit('missing readSoftVersion anchor')
product_reader = '''    /** Resolve legacy ProName from the selected application's own ITMC.Web.dll. */
    public static String readProductName(File binOrRoot) {
        if (binOrRoot == null) return null;
        File dir = binOrRoot.getAbsoluteFile();
        if (!dir.isDirectory()) dir = dir.getParentFile();
        if (dir == null) return null;
        File bin = "bin".equalsIgnoreCase(dir.getName()) ? dir : new File(dir, "bin");
        File web = new File(bin, "ITMC.Web.dll");
        if (!web.isFile() && new File(dir, "ITMC.Web.dll").isFile()) web = new File(dir, "ITMC.Web.dll");
        String version = readSoftVersion(binOrRoot);
        return LicenseRecoverModernGUIAutoRecovery.detectProduct(web, version);
    }

'''
t = t[:pos] + product_reader + t[pos:]
t = t.replace('    public static CodeResult generateAuthorizationCode(String sequence, String softVersionId) throws Exception {\n',
              '    public static CodeResult generateAuthorizationCode(String sequence, String softVersionId, String productName) throws Exception {\n', 1)
t = must_replace(t,
    '        String code = encrypt(codeKeyForProduct(PRODUCT_NAME), plaintext);\n        AuthorizationInfo parsed = decodeAuthorizationCode(code);\n',
    '        String code = encrypt(codeKeyForProduct(productName), plaintext);\n        AuthorizationInfo parsed = decodeAuthorizationCode(code, productName);\n',
    'legacy code uses directory product')
t = t.replace('    public static AuthorizationInfo decodeAuthorizationCode(String code) throws Exception {\n        String plain = decrypt(codeKeyForProduct(PRODUCT_NAME), code);\n',
              '    public static AuthorizationInfo decodeAuthorizationCode(String code, String productName) throws Exception {\n        String plain = decrypt(codeKeyForProduct(productName), code);\n', 1)
write(rel, t)

# 6) Focused tests: inferred Java fixtures must be blocked; direct data-config and DLL evidence remain ready.
rel = 'src/test/java/RefactorSmokeTest.java'
t = read(rel)
# Change only readiness expectations that previously authorized hard-coded family/RegStr rules.
repls = [
('check("DS2406".equals(ds2406Plan.regStr) && ds2406Plan.automaticRecoveryReady,\n                "DS2406 GUI plan uses sample-verified RegStr and is ready");',
 'check(!ds2406Plan.automaticRecoveryReady\n                        && ds2406Plan.recoveryReadiness.contains("目录"),\n                "DS2406 stays blocked when fixture lacks directory registration-id evidence");'),
('check("QT100101".equals(qt100101Plan.regStr) && qt100101Plan.automaticRecoveryReady\n                        && qt100101Plan.recoveryReadiness.contains("可安全"),\n                "QT100101 automatic recovery is ready with the sample-verified RegStr");',
 'check(!qt100101Plan.automaticRecoveryReady\n                        && qt100101Plan.recoveryReadiness.contains("RegStr"),\n                "QT100101 stays blocked until RegStr is declared/recovered from target directory");')
]
for old,new in repls:
    if old in t: t=t.replace(old,new,1)
# Add safety assertions after QT30xxx direct-file fixture.
anchor = '''        check(qtPlan.generation.contains("QT30xxx") && qtPlan.configTargets.contains("webapp根"),
                "Java GUI plan reports QT30xxx generation and dual config targets");
'''
addition = anchor + '''        check(qtPlan.automaticRecoveryReady
                        && qtPlan.recoveryReadiness.contains("目标目录"),
                "QT30xxx automatic recovery is allowed only from direct data/config.xml evidence");
        check(!LicenseRecoverModernGUIJavaPlan.inspect(yt129Root.toFile()).automaticRecoveryReady,
                "legacy YT fixture without class-level mapping is fail-closed");
'''
t = must_replace(t, anchor, addition, 'add Java strict tests')
# Add .NET evidence no-fallback test next to DLL fixtures.
anchor2 = '''        check("YX030301,YX030308,YX030322".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                productFixture.toFile(), "YX0303")),
                "one-click derives YX0303 local product list");
'''
addition2 = anchor2 + '''        Path noRegStrFixture = base.resolve("YX0303-no-products-Web.dll");
        writeUtf16Fixture(noRegStrFixture, "YX0303", "SoftVersionID", "ProName");
        check(LicenseRecoverModernGUIAutoRecovery.detectProductList(
                        noRegStrFixture.toFile(), "YX0303").isEmpty(),
                ".NET directory parser returns empty instead of inventing RegStr");
'''
t = must_replace(t, anchor2, addition2, 'add .NET strict test')
write(rel, t)

# 7) CI guard against reintroducing automatic fallback paths.
rel = 'scripts/verify.ps1'
t = read(rel)
anchor = "Write-Host 'Building deterministic runtime overlay...'"
if 'Verifying directory-only registration identity policy' not in t:
    guard = '''Write-Host 'Verifying directory-only registration identity policy...'
$coreSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecover.java') -Raw
$planSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIJavaPlan.java') -Raw
$autoSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIAutoRecovery.java') -Raw
$legacyNetSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LegacyDotNetProtocol.java') -Raw
if ($coreSource.Contains('genRegisterCode(seq, registerPid)')) { throw 'Java gencode can still omit directory-derived RegStr.' }
if ($coreSource.Contains('+ ALL_NUMS')) { throw 'Java authorization generation still uses catch-all RegStr.' }
if ($autoSource.Contains('if (blank(regStr)) regStr = d.versionId')) { throw '.NET one-click still falls back to VersionID for RegStr.' }
if ($legacyNetSource.Contains('PRODUCT_NAME = "itmcIEC"')) { throw 'Legacy .NET protocol still has a fixed executable product name.' }
if (-not $coreSource.Contains('LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect')) { throw 'Java core is not using the fail-closed directory plan.' }
if (-not $autoSource.Contains('VersionID/default-list fallback is disabled')) { throw '.NET fail-closed guard is missing.' }

'''
    if anchor not in t: raise SystemExit('verify guard anchor missing')
    t=t.replace(anchor, guard+anchor,1)
write(rel,t)

print('Applied directory-only registration identity hardening.')
