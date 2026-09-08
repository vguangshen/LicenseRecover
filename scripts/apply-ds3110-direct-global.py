#!/usr/bin/env python3
from pathlib import Path

meta = Path('src/main/java/LegacyJavaRegistrationMetadata.java')
text = meta.read_text(encoding='utf-8')
old = '''        GlobalScan scan = scanGlobal(global, softId.trim());
        if (scan.mapping != null) return scan.mapping;
        // If a protected Global.class exists but cannot be inspected safely, do not
        // silently fall through to a guessed family.
        if (scan.present && !scan.complete) return null;
'''
new = '''        GlobalScan scan = scanGlobal(global, softId.trim());
        if (scan.mapping != null) return scan.mapping;

        // A third legacy layout does not build registerProductBeans at all.  Instead,
        // Global.PRODUCT_NUM is passed directly to RegisterMain while the concrete
        // VersionID is loaded into SYS_PRODUCT_NUM and checked against RegStr.  Accept
        // this only when the selected target's own classes prove the whole data flow;
        // merely finding a product-looking string in Global.class is not sufficient.
        if (!globalHasRegistrationDispatch(global)) {
            Mapping direct = directGlobalProductMapping(appRoot, global, softId.trim());
            if (direct != null) return direct;
        }

        // If a protected Global.class exists but cannot be inspected safely, do not
        // silently fall through to a guessed family.
        if (scan.present && !scan.complete) return null;
'''
if old not in text:
    raise SystemExit('inspect anchor not found')
text = text.replace(old, new, 1)

anchor = '''    static String selectFallbackPrefix(Collection<String> targetStrings, String softId) {
'''
insert = r'''    static Mapping directGlobalProductMapping(File root, File global, String softId) {
        if (root == null || global == null || !global.isFile() || blank(softId)) return null;
        if (globalHasRegistrationDispatch(global)) return null;

        String family = directGlobalProductFromGlobal(global, softId);
        if (blank(family)) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File xmlUtil = new File(classes, "com" + File.separator + "common" + File.separator
                + "utils" + File.separator + "IXmlUtil.class");
        File init = new File(classes, "com" + File.separator + "common" + File.separator
                + "sys" + File.separator + "configuration" + File.separator + "SysParamInit.class");

        // IXmlUtil proves that the concrete application VersionID is assigned to
        // SYS_PRODUCT_NUM.  SysParamInit proves that Global.PRODUCT_NUM drives
        // RegisterMain and that the returned RegStr is checked against SYS_PRODUCT_NUM.
        if (!rawClassContainsAll(xmlUtil, "global.system.VersionID", "SYS_PRODUCT_NUM")) return null;
        if (!rawClassContainsAll(init, "itmc/regedit/RegisterMain", "PRODUCT_NUM",
                "SYS_PRODUCT_NUM", "getRegInfo", "contains")) return null;

        return new Mapping(family, softId.trim(), softId.trim(),
                "Global.PRODUCT_NUM + VersionID registration flow");
    }

    static String directGlobalProductFromGlobal(File global, String softId) {
        if (global == null || !global.isFile() || blank(softId)) return null;
        try {
            String raw = new String(Files.readAllBytes(global.toPath()), StandardCharsets.ISO_8859_1);
            if (!raw.contains("PRODUCT_NUM")) return null;
            String id = softId.trim().toUpperCase(Locale.ROOT);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)(?<![A-Za-z0-9])([A-Z]{2,8}[0-9]{2,8})(?![A-Za-z0-9])")
                    .matcher(raw);
            String best = null;
            while (matcher.find()) {
                String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
                if (candidate.equals(id) || candidate.length() >= id.length()) continue;
                if (!id.startsWith(candidate)) continue;
                if (best == null || candidate.length() > best.length()) best = candidate;
            }
            return best;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static boolean rawClassContainsAll(File file, String... tokens) {
        if (file == null || !file.isFile() || tokens == null) return false;
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
            for (String token : tokens) {
                if (blank(token) || !raw.contains(token)) return false;
            }
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

'''
if anchor not in text:
    raise SystemExit('helper insertion anchor not found')
text = text.replace(anchor, insert + anchor, 1)
meta.write_text(text, encoding='utf-8')

smoke = Path('src/test/java/RefactorSmokeTest.java')
s = smoke.read_text(encoding='utf-8')
anchor2 = '''        Path qt30103Root = base.resolve("java-QT30103");
'''
block = r'''        Path ds3110Root = base.resolve("java-DS3110");
        Path ds3110Lib = ds3110Root.resolve("WEB-INF/lib");
        Path ds3110Classes = ds3110Root.resolve("WEB-INF/classes");
        Path ds3110Global = ds3110Classes.resolve("com/common/global/Global.class");
        Path ds3110XmlUtil = ds3110Classes.resolve("com/common/utils/IXmlUtil.class");
        Path ds3110RegisterUtil = ds3110Classes.resolve("com/common/utils/RegisterUtil.class");
        Path ds3110Init = ds3110Classes.resolve("com/common/sys/configuration/SysParamInit.class");
        Files.createDirectories(ds3110Lib);
        Files.createDirectories(ds3110Global.getParent());
        Files.createDirectories(ds3110XmlUtil.getParent());
        Files.createDirectories(ds3110Init.getParent());
        Files.createDirectories(ds3110Root.resolve("data"));
        Files.write(ds3110Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(ds3110Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=DS3110"), StandardCharsets.UTF_8);
        Files.write(ds3110Root.resolve("data/config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS3110</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        // DS2901 intentionally models an unrelated application.yml authorization code.
        // It must not win over the target Global.PRODUCT_NUM prefix evidence.
        Files.write(ds3110Global, "PRODUCT_NUM DS31 DS2901".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds3110XmlUtil,
                "global.system.VersionID SYS_PRODUCT_NUM".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds3110RegisterUtil,
                "itmc/regedit/RegisterMain getRegStr contains".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds3110Init,
                "itmc/regedit/RegisterMain PRODUCT_NUM SYS_PRODUCT_NUM getRegInfo regStr contains"
                        .getBytes(StandardCharsets.ISO_8859_1));
        check(LegacyJavaRegistrationMetadata.requiresDirectoryMapping(ds3110Root.toFile(), "DS3110"),
                "DS3110 still requires target-directory mapping because RegisterUtil is present");
        LegacyJavaRegistrationMetadata.Mapping ds3110Mapping =
                LegacyJavaRegistrationMetadata.inspect(ds3110Root.toFile(), "DS3110");
        check(ds3110Mapping != null
                        && "DS31".equals(ds3110Mapping.productMain)
                        && "DS3110".equals(ds3110Mapping.productMainNum)
                        && "DS3110".equals(ds3110Mapping.productNums),
                "DS3110 direct Global.PRODUCT_NUM flow resolves DS31 / DS3110 from target classes");
        LicenseRecoverModernGUIJavaPlan ds3110Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(ds3110Root.toFile());
        check("DS31".equals(ds3110Plan.authorizationFamily)
                        && "DS31".equals(ds3110Plan.runtimeProductId)
                        && "DS3110".equals(ds3110Plan.regStr)
                        && ds3110Plan.automaticRecoveryReady,
                "DS3110 executable plan is READY only from direct target registration-flow evidence");

        Path ds3110Weak = base.resolve("java-DS3110-weak");
        Path ds3110WeakGlobal = ds3110Weak.resolve("WEB-INF/classes/com/common/global/Global.class");
        Files.createDirectories(ds3110WeakGlobal.getParent());
        Files.write(ds3110WeakGlobal, "PRODUCT_NUM DS31".getBytes(StandardCharsets.ISO_8859_1));
        check(LegacyJavaRegistrationMetadata.directGlobalProductMapping(
                        ds3110Weak.toFile(), ds3110WeakGlobal.toFile(), "DS3110") == null,
                "Global PRODUCT_NUM string alone is not sufficient without VersionID/RegisterMain data-flow proof");

'''
if anchor2 not in s:
    raise SystemExit('smoke insertion anchor not found')
s = s.replace(anchor2, block + anchor2, 1)
smoke.write_text(s, encoding='utf-8')
print('patched DS3110 direct Global registration flow')
