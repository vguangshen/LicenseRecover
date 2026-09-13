#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
TEST = ROOT / 'src/test/java/RefactorSmokeTest.java'


def replace_once(path, old, new):
    text = path.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit('anchor mismatch in %s: expected 1 got %d' % (path, count))
    path.write_text(text.replace(old, new, 1), encoding='utf-8')

# 1) In config-driven runtime detection, handle the deployed YX030506 contract before
#    the older exact-SoftVersionID branch.
old = '''        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File systemInfo = new File(classes, "com" + File.separator + "itmc" + File.separator
'''
new = '''        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");

        // Deployed YX030506 builds use two different identity layers:
        // systemConfig.yml VersionID=YX030506, while classes/config.xml
        // SoftVersionID=YX0305,QT1001 is a startup RegisterMain product list.
        // Accept a primary runtime product only when target-owned config and bytecode
        // independently prove the PRODUCT_ALL_NUM loop and local registration servlet.
        String productAllPrimary = confirmedProductAllNumPrimaryRuntime(
                root, concrete, family, configured);
        if (!blank(productAllPrimary)) return productAllPrimary;

        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File systemInfo = new File(classes, "com" + File.separator + "itmc" + File.separator
'''
# This short anchor occurs in multiple methods, so scope replacement to method body.
text = PLAN.read_text(encoding='utf-8')
method = 'static String confirmedConfigDrivenRuntimeProduct(File root, String softId, String confirmedFamily) {'
start = text.index(method)
end = text.index('    /**\n     * Confirm that the selected application', start)
segment = text[start:end]
if segment.count(old) != 1:
    raise SystemExit('config-driven method anchor mismatch: %d' % segment.count(old))
segment = segment.replace(old, new, 1)
PLAN.write_text(text[:start] + segment + text[end:], encoding='utf-8')

# 2) Add a fail-closed helper for the real PRODUCT_ALL_NUM layout.
anchor = '''    static boolean usesProjectClasspathRegistrationBase(File root) {
'''
helper = '''    /**
     * Real deployed YX030506 layout: the concrete application mode is YX030506,
     * but classes/config.xml lists startup registration products YX0305,QT1001.
     * The primary local-registration product is accepted only with four independent
     * pieces of target-local evidence, never from the VersionID prefix alone.
     */
    static String confirmedProductAllNumPrimaryRuntime(File root, String softId,
                                                       String confirmedFamily,
                                                       String configuredProducts) {
        if (root == null || blank(softId) || blank(confirmedFamily) || blank(configuredProducts)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        if (!concrete.toUpperCase(Locale.ROOT).startsWith("YX0305")
                || !"YX0305".equalsIgnoreCase(family)) return null;

        String ymlVersion = readSystemConfigVersionId(root);
        if (blank(ymlVersion) || !concrete.equalsIgnoreCase(ymlVersion.trim())) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        if (!csvContainsToken(configuredProducts, family)) return null;
        if (!hasEnumeratedClassesFamily(config, family, concrete)) return null;
        if (!confirmedProductAllNumRuntimeProduct(classes)) return null;

        File servlet = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "sys" + File.separator + "platformregister" + File.separator
                + "RegisterHttpServlet.class");
        if (!classFileContainsAll(servlet, family,
                "com/itmc/utils/ProjectSourcesPath", "projectPath",
                "itmc/regedit/RegisterMain", "newRegistry", "doRegistry")) return null;
        return family;
    }

    static boolean csvContainsToken(String csv, String token) {
        if (blank(csv) || blank(token)) return false;
        String wanted = token.trim();
        for (String part : csv.split(",")) {
            if (part != null && wanted.equalsIgnoreCase(part.trim())) return true;
        }
        return false;
    }

'''
replace_once(PLAN, anchor, helper + anchor)

# 3) Preserve the existing concrete-config variant and add the real deployed variant.
old_test = '''        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && "YX030506".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && yx305Plan.automaticRecoveryReady,
                "YX030506 runtime id is accepted only from target config->PRODUCT_ALL_NUM->RegisterMain flow");

        Path qt401Root = base.resolve("java-QT40101");
'''
new_test = '''        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && "YX030506".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && yx305Plan.automaticRecoveryReady,
                "YX030506 concrete-config variant remains accepted from target config->PRODUCT_ALL_NUM->RegisterMain flow");

        // Real deployed YX030506 variant: systemConfig.yml identifies the concrete
        // application mode, while classes/config.xml SoftVersionID lists RegisterMain products.
        // Use single quotes in XML attributes so this generated Java fixture needs no escaping.
        Files.write(yx305Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>YX0305,QT1001</SoftVersionID><regInfo>QT100101,QT100102</regInfo></SystemSoft>"
                        + "<System id='YX030501'/><System id='YX030502'/><System id='YX030506'/></ROOT>"),
                StandardCharsets.UTF_8);
        Files.write(yx305Runner,
                "PRODUCT_ALL_NUM split itmc/regedit/GetRegisterCode RegeditNew itmcsoft "
                        .concat("itmc/regedit/RegisterMain writeRegisterUser checkReInfo ")
                        .concat("com/itmc/utils/ProjectSourcesPath projectPath")
                        .getBytes(StandardCharsets.ISO_8859_1));
        Path yx305Servlet = yx305Classes.resolve("com/itmc/sys/platformregister/RegisterHttpServlet.class");
        Files.createDirectories(yx305Servlet.getParent());
        Files.write(yx305Servlet,
                "YX0305 com/itmc/utils/ProjectSourcesPath projectPath itmc/regedit/RegisterMain newRegistry doRegistry"
                        .getBytes(StandardCharsets.ISO_8859_1));
        yx305Plan = LicenseRecoverModernGUIJavaPlan.inspect(yx305Root.toFile());
        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && "YX0305".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && yx305Plan.automaticRecoveryReady,
                "YX030506 deployed PRODUCT_ALL_NUM variant confirms YX0305 as primary local runtime product");

        Files.write(yx305Servlet,
                "com/itmc/utils/ProjectSourcesPath projectPath itmc/regedit/RegisterMain newRegistry doRegistry"
                        .getBytes(StandardCharsets.ISO_8859_1));
        yx305Plan = LicenseRecoverModernGUIJavaPlan.inspect(yx305Root.toFile());
        check(yx305Plan.runtimeProductId == null && !yx305Plan.automaticRecoveryReady,
                "YX030506 PRODUCT_ALL_NUM variant stays fail-closed without target servlet proof of YX0305");

        Path qt401Root = base.resolve("java-QT40101");
'''
replace_once(TEST, old_test, new_test)

print('staged YX030506 PRODUCT_ALL_NUM fix')
