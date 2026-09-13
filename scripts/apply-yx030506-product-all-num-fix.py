#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'anchor mismatch in {path}: {n}: {old[:140]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

plan = 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
test = 'src/test/java/RefactorSmokeTest.java'

replace_once(plan,
'''    static String confirmedConfigDrivenRuntimeProduct(File root, String softId, String confirmedFamily) {
        if (root == null || blank(softId) || blank(confirmedFamily)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        if (!concrete.toUpperCase(Locale.ROOT).startsWith(family.toUpperCase(Locale.ROOT))) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File systemInfo = new File(classes, "com" + File.separator + "itmc" + File.separator
''',
'''    static String confirmedConfigDrivenRuntimeProduct(File root, String softId, String confirmedFamily) {
        if (root == null || blank(softId) || blank(confirmedFamily)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        if (!concrete.toUpperCase(Locale.ROOT).startsWith(family.toUpperCase(Locale.ROOT))) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");

        // Deployed YX030506 builds use two different identity layers:
        //   systemConfig.yml VersionID = YX030506 (concrete application mode)
        //   classes/config.xml SoftVersionID = YX0305,QT1001 (startup RegisterMain products)
        // Do not force those values to be equal. The primary local-registration product
        // is accepted only when target-owned bytecode proves both PRODUCT_ALL_NUM startup
        // iteration and a local-registration servlet that constructs RegisterMain(YX0305,...).
        String productAllPrimary = confirmedProductAllNumPrimaryRuntime(
                root, concrete, family, configured);
        if (!blank(productAllPrimary)) return productAllPrimary;

        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File systemInfo = new File(classes, "com" + File.separator + "itmc" + File.separator
''')

replace_once(plan,
'''    static boolean confirmedProductAllNumRuntimeProduct(File classes) {
        if (classes == null || !classes.isDirectory()) return false;
        File xmlUtil = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "utils" + File.separator + "IXmlUtil.class");
        File runner = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "utils" + File.separator + "ProjectApplicationRunner.class");
        return classFileContainsAll(xmlUtil, "global.system.VersionID", "/config.xml",
                        "SystemSoft", "SoftVersionID", "regInfo", "SYS_PRODUCT_NUM",
                        "PRODUCT_ALL_NUM", "PRODUCT_INFO")
                && classFileContainsAll(runner, "PRODUCT_ALL_NUM", "split",
                        "itmc/regedit/GetRegisterCode", "RegeditNew", "itmcsoft",
                        "itmc/regedit/RegisterMain", "writeRegisterUser", "checkReInfo");
    }
''',
'''    static boolean confirmedProductAllNumRuntimeProduct(File classes) {
        if (classes == null || !classes.isDirectory()) return false;
        File xmlUtil = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "utils" + File.separator + "IXmlUtil.class");
        File runner = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "utils" + File.separator + "ProjectApplicationRunner.class");
        return classFileContainsAll(xmlUtil, "global.system.VersionID", "/config.xml",
                        "SystemSoft", "SoftVersionID", "regInfo", "SYS_PRODUCT_NUM",
                        "PRODUCT_ALL_NUM", "PRODUCT_INFO")
                && classFileContainsAll(runner, "PRODUCT_ALL_NUM", "split",
                        "itmc/regedit/GetRegisterCode", "RegeditNew", "itmcsoft",
                        "itmc/regedit/RegisterMain", "writeRegisterUser", "checkReInfo");
    }

    /**
     * Prove the primary RegisterMain product for the deployed YX030506 layout where
     * classes/config.xml SoftVersionID is a CSV startup-product list rather than the
     * concrete application VersionID. This is intentionally YX0305-specific until
     * another real target demonstrates the same contract.
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
''')

replace_once(test,
'''        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && "YX030506".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && yx305Plan.automaticRecoveryReady,
                "YX030506 runtime id is accepted only from target config->PRODUCT_ALL_NUM->RegisterMain flow");

        Path qt401Root = base.resolve("java-QT40101");
''',
'''        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && "YX030506".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && yx305Plan.automaticRecoveryReady,
                "YX030506 concrete-config variant remains accepted from target config->PRODUCT_ALL_NUM->RegisterMain flow");

        // Real deployed YX030506 variant: concrete application mode comes from
        // systemConfig.yml/System id, while classes/config.xml SoftVersionID is the
        // startup RegisterMain product list YX0305,QT1001. The local registration
        // servlet independently proves YX0305 is the primary offline product.
        Files.write(yx305Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>YX0305,QT1001</SoftVersionID><regInfo>QT100101,QT100102</regInfo></SystemSoft>"
                        + "<System id=\"YX030501\"/><System id=\"YX030502\"/><System id=\"YX030506\"/></ROOT>"),
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
                "YX030506 PRODUCT_ALL_NUM variant stays fail-closed without target servlet proof of the YX0305 product");

        Path qt401Root = base.resolve("java-QT40101");
''')

print('staged YX030506 PRODUCT_ALL_NUM fix')
