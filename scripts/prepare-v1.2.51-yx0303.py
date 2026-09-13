from pathlib import Path

src = Path('src/main/java/LicenseRecoverModernGUIAutoRecovery.java')
text = src.read_text(encoding='utf-8')
old_detect = '''            String version = readVersion(root, bin);\n            String configuredProduct = detectConfiguredDotNetProduct(root, bin, version);\n            String product = !blank(configuredProduct)\n                    ? configuredProduct : detectProduct(new File(bin,"ITMC.Web.dll"), version);\n'''
new_detect = '''            String version = readVersion(root, bin);\n            File webDll = new File(bin, "ITMC.Web.dll");\n            String configuredProduct = detectConfiguredDotNetProduct(root, bin, version);\n            String assemblyProduct = detectProduct(webDll, version);\n            String product = selectDotNetRegistrationProduct(version, configuredProduct, assemblyProduct);\n'''
if old_detect not in text:
    raise SystemExit('detect product selection anchor missing')
text = text.replace(old_detect, new_detect, 1)

anchor = '''    private static Detection unknownDetection(File s) { return new Detection(Kind.UNKNOWN,s,null,null,null,null); }\n\n'''
helper = r'''    /**
     * Select the product actually used by the target registration runtime.
     *
     * Some YX03xx sites keep a broad application-group value such as "YX03" in
     * Web.config while the protected ITMC.Web runtime constructs RegeditMain with
     * the concrete registration family (for example YX0303 for YX030308).  Using
     * the broad Web.config value makes the helper write regName with the wrong
     * *ITMC<ProName>OK* key: the helper can verify its own write, but the real web
     * startup later cannot decrypt it.  For the eight-character YX mode family,
     * prefer the family proven by this target ITMC.Web.dll; keep configured product
     * identity authoritative for older/direct products such as YX0102 -> YS01.
     */
    static String selectDotNetRegistrationProduct(String version, String configuredProduct,
                                                   String assemblyProduct) {
        String v = blank(version) ? null : version.trim();
        String configured = blank(configuredProduct) ? null : configuredProduct.trim();
        String assembly = blank(assemblyProduct) ? null : assemblyProduct.trim();
        if (v != null && v.matches("(?i)^YX\\d{6}$")
                && assembly != null && assembly.matches("(?i)^YX\\d{4}$")) {
            return assembly;
        }
        return configured != null ? configured : assembly;
    }

'''
if anchor not in text:
    raise SystemExit('unknownDetection anchor missing')
text = text.replace(anchor, anchor + helper, 1)
src.write_text(text, encoding='utf-8')

test = Path('src/test/java/RefactorSmokeTest.java')
t = test.read_text(encoding='utf-8')
anchor_test = '''        check("YX030301,YX030308,YX030322".equals(\n                        LicenseRecoverModernGUIAutoRecovery.detectProductList(\n                                productFixture.toFile(), "YX0303")),\n                "one-click derives YX0303 local product list");\n'''
block = r'''
        check("YX0303".equals(LicenseRecoverModernGUIAutoRecovery.selectDotNetRegistrationProduct(
                        "YX030308", "YX03", "YX0303")),
                "YX030308 registration uses target ITMC.Web family instead of broad Web.config YX03");
        check("YX0302".equals(LicenseRecoverModernGUIAutoRecovery.selectDotNetRegistrationProduct(
                        "YX030107", "YX03", "YX0302")),
                "YX030107 keeps target-owned YX0302 registration family when version prefix is misleading");
        check("YS01".equals(LicenseRecoverModernGUIAutoRecovery.selectDotNetRegistrationProduct(
                        "YX0102", "YS01", "YX0102")),
                "direct YX0102 keeps configured YS01 identity outside the YX03xx mode-family rule");

        Path yx030308BroadConfigRoot = base.resolve("dotnet-YX030308-broad-webconfig");
        Path yx030308BroadConfigBin = yx030308BroadConfigRoot.resolve("bin");
        Files.createDirectories(yx030308BroadConfigBin);
        writeUtf16Fixture(yx030308BroadConfigBin.resolve("ITMC.Web.dll"),
                "YX03", "YX0303", "YX030308", "RegeditNew", "NewRegistry", "DoRegistry",
                "ProName", "SoftVersionID", "GetProVersion", "CheckSoftVersionID");
        Files.write(yx030308BroadConfigBin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        Files.write(yx030308BroadConfigRoot.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg/><SystemSoft><SoftVersionID>YX030308</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        Files.write(yx030308BroadConfigRoot.resolve("Web.config"), Arrays.asList(
                "<configuration><appSettings><add key=\"productName\" value=\"YX03\" /></appSettings></configuration>"),
                StandardCharsets.UTF_8);
        LicenseRecoverModernGUIAutoRecovery.Detection yx030308BroadDetection =
                LicenseRecoverModernGUIAutoRecovery.detect(yx030308BroadConfigRoot.toFile());
        check("YX03".equals(LicenseRecoverModernGUIAutoRecovery.detectConfiguredDotNetProduct(
                        yx030308BroadConfigRoot.toFile(), yx030308BroadConfigBin.toFile(), "YX030308")),
                "YX030308 regression fixture reproduces misleading broad Web.config productName=YX03");
        check("YX0303".equals(yx030308BroadDetection.productName),
                "YX030308 detection selects the target runtime registration family YX0303");
'''
if anchor_test not in t:
    raise SystemExit('YX0303 test anchor missing')
t = t.replace(anchor_test, anchor_test + block, 1)
test.write_text(t, encoding='utf-8')
