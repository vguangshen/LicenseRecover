#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'anchor not found in {path}: {old[:120]!r}')
    if text.count(old) != 1:
        raise SystemExit(f'anchor not unique in {path}: count={text.count(old)}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# 1) A plain Global.class is not automatically a registerProductBeans dispatcher.
replace_once(
    'src/main/java/LegacyJavaRegistrationMetadata.java',
'''        return registerUtil.isFile() || global.isFile();
    }

    static String selectFallbackPrefix''',
'''        return registerUtil.isFile() || globalHasRegistrationDispatch(global);
    }

    /**
     * Some generations (for example DS28) keep only a direct PRODUCT_NUM constant
     * in Global.class.  Virbox protects method Code bytes but leaves constant-pool
     * names intact, so require the Global mapping parser only when the target class
     * itself advertises the registerProductBeans setter trio.  Read failure remains
     * fail-closed.
     */
    static boolean globalHasRegistrationDispatch(File global) {
        if (global == null || !global.isFile()) return false;
        try {
            String pool = new String(Files.readAllBytes(global.toPath()), StandardCharsets.ISO_8859_1);
            return pool.contains("setProductMain")
                    && pool.contains("setProductMainNum")
                    && pool.contains("setProductNums");
        } catch (Throwable ex) {
            return true;
        }
    }

    static String selectFallbackPrefix''')

# 2) Allow a concrete runtime ProductID when the target's own bytecode proves
#    config.xml -> SystemSoft -> registerId -> RegisterMain and versionID RegStr check.
replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
'''        String binaryRuntime = confirmedBinaryRuntimeProduct(
                root, lib, soft, binaryFamily, newStyle, classesConfig.isFile());
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : binaryRuntime));''',
'''        String binaryRuntime = confirmedBinaryRuntimeProduct(
                root, lib, soft, binaryFamily, newStyle, classesConfig.isFile());
        String configDrivenRuntime = confirmedConfigDrivenRuntimeProduct(root, soft, family);
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim()
                : (!blank(configDrivenRuntime) ? configDrivenRuntime : binaryRuntime)));''')

replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
'''    static boolean hasDirectoryBinaryToken(File root, File lib, String token) {''',
'''    /**
     * Prove a config-driven concrete runtime ProductID without guessing it from the
     * VersionID.  The concrete value is accepted only when the selected application's
     * own classes/config.xml supplies that same SoftVersionID and its own bytecode
     * proves the data flow into RegisterMain plus the RegStr/versionID membership check.
     */
    static String confirmedConfigDrivenRuntimeProduct(File root, String softId, String confirmedFamily) {
        if (root == null || blank(softId) || blank(confirmedFamily)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        if (!concrete.toUpperCase(Locale.ROOT).startsWith(family.toUpperCase(Locale.ROOT))) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File systemInfo = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "register" + File.separator + "utils" + File.separator + "SystemInfo.class");
        File listener = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "register" + File.separator + "service" + File.separator + "RegisterListener.class");
        if (!classFileContainsAll(systemInfo, "config.xml", "SystemSoft", "registerId")) return null;
        if (!classFileContainsAll(listener, "com/itmc/register/utils/SystemInfo", "registerId",
                "itmc/regedit/RegisterMain", "getRegStr", "versionID", "contains")) return null;
        return concrete;
    }

    private static boolean classFileContainsAll(File file, String... tokens) {
        if (file == null || !file.isFile() || tokens == null) return false;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            for (String token : tokens) {
                if (!bytesContainExactAsciiToken(data, token)) return false;
            }
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean hasDirectoryBinaryToken(File root, File lib, String token) {''')

# 3) Existing local RegStr probe: 3-arg=(product,token,path), 2-arg=(product,path),
#    1-arg=(product) only when the target's default lib config is the selected config.
replace_once(
    'src/main/java/ExistingLocalRegStrProbe.java',
'''final class ExistingLocalRegStrProbe {
    private ExistingLocalRegStrProbe() { }

    static boolean isSafeLocalConfig''',
'''final class ExistingLocalRegStrProbe {
    private ExistingLocalRegStrProbe() { }

    static Object instantiateTargetRegisterMain(Class<?> mainType, String product,
                                                String token, String configDir,
                                                boolean allowDefaultPath) throws Exception {
        try {
            java.lang.reflect.Constructor<?> ctor = mainType.getConstructor(
                    String.class, String.class, String.class);
            return ctor.newInstance(product, token, configDir);
        } catch (NoSuchMethodException noThreeArg) { }
        try {
            java.lang.reflect.Constructor<?> ctor = mainType.getConstructor(String.class, String.class);
            // Real legacy generations (DS2406/DS50109 included) define the second
            // argument as ConfigPath, not an encrypted registration token.
            return ctor.newInstance(product, configDir);
        } catch (NoSuchMethodException noTwoArg) { }
        if (allowDefaultPath) {
            try {
                java.lang.reflect.Constructor<?> ctor = mainType.getConstructor(String.class);
                return ctor.newInstance(product);
            } catch (NoSuchMethodException noOneArg) { }
        }
        throw new NoSuchMethodException("RegisterMain requires supported 3/2/1-arg constructor");
    }

    static boolean isSafeLocalConfig''')

replace_once(
    'src/main/java/ExistingLocalRegStrProbe.java',
'''            Class<?> mainType = Class.forName("itmc.regedit.RegisterMain", true, loader);
            java.lang.reflect.Constructor<?> ctor3 = null;
            java.lang.reflect.Constructor<?> ctor2 = null;
            try { ctor3 = mainType.getConstructor(String.class, String.class, String.class); } catch (Exception ignore) { }
            try { ctor2 = mainType.getConstructor(String.class, String.class); } catch (Exception ignore) { }
            java.lang.reflect.Method getRegInfo = mainType.getMethod("getRegInfo");

            LinkedHashSet<String> recovered = new LinkedHashSet<String>();
            for (File config : safe) {
                try {
                    Object main;
                    String dir = config.getParentFile().getAbsolutePath() + File.separator;
                    if (ctor3 != null) {
                        main = ctor3.newInstance(runtimeProductId, token, dir);
                    } else if (ctor2 != null && sameFile(config.getParentFile(), libDir)) {
                        // Older two-argument RegisterMain always resolves config.xml beside ITMCReg.jar.
                        main = ctor2.newInstance(runtimeProductId, token);
                    } else {
                        continue;
                    }
''',
'''            Class<?> mainType = Class.forName("itmc.regedit.RegisterMain", true, loader);
            java.lang.reflect.Method getRegInfo = mainType.getMethod("getRegInfo");

            LinkedHashSet<String> recovered = new LinkedHashSet<String>();
            for (File config : safe) {
                try {
                    String dir = config.getParentFile().getAbsolutePath() + File.separator;
                    Object main = instantiateTargetRegisterMain(mainType, runtimeProductId, token, dir,
                            sameFile(config.getParentFile(), libDir));
''')

# 4) Batch UI: native verification wording + make .NET RegStr fail-closed at preflight too.
replace_once(
    'src/main/java/LicenseRecoverModernGUI.java',
'''        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                && (d.productName == null || d.productName.trim().isEmpty()))
            return ".NET 授权产品号未确认";
        return null;''',
'''        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN) {
            if (d.productName == null || d.productName.trim().isEmpty())
                return ".NET 授权产品号未确认";
            String products = LicenseRecoverModernGUIAutoRecovery.detectProductList(
                    new File(target.binDir, "ITMC.Web.dll"), d.productName);
            if (products == null || products.trim().isEmpty())
                return ".NET RegStr 未从目标 DLL 确认";
        }
        return null;''')

replace_once(
    'src/main/java/LicenseRecoverModernGUI.java',
'''        return result.isSuccess() ? "写回解密校验: OK" : "写回解密校验: FAILED";''',
'''        return result.isSuccess() ? "DoRegistry + CheckReInfo: OK" : "DoRegistry + CheckReInfo: FAILED";''')

# 5) Smoke tests: constructor generations + DS28 non-dispatch Global + DS501 config-driven runtime.
replace_once(
    'src/test/java/RefactorSmokeTest.java',
'''    public static final class LegacyRegisterMain2 {''',
'''    public static final class LegacyRegisterMain1 {
        final String product;
        public LegacyRegisterMain1(String product) { this.product = product; }
    }

    public static final class LegacyRegisterMain2 {''')

replace_once(
    'src/test/java/RefactorSmokeTest.java',
'''        ModernRegisterMain3 modernCtor = (ModernRegisterMain3)
                LicenseRecover.instantiateRegisterMainCompatible(
                        ModernRegisterMain3.class, "QT30103", "encrypted-json", "D:/app/");
        check("QT30103".equals(modernCtor.product)
                        && "encrypted-json".equals(modernCtor.json)
                        && "D:/app/".equals(modernCtor.configPath),
                "modern 3-arg RegisterMain keeps json and configPath arguments");
''',
'''        ModernRegisterMain3 modernCtor = (ModernRegisterMain3)
                LicenseRecover.instantiateRegisterMainCompatible(
                        ModernRegisterMain3.class, "QT30103", "encrypted-json", "D:/app/");
        check("QT30103".equals(modernCtor.product)
                        && "encrypted-json".equals(modernCtor.json)
                        && "D:/app/".equals(modernCtor.configPath),
                "modern 3-arg RegisterMain keeps json and configPath arguments");

        LegacyRegisterMain2 probeCtor2 = (LegacyRegisterMain2)
                ExistingLocalRegStrProbe.instantiateTargetRegisterMain(
                        LegacyRegisterMain2.class, "DS50109", "ignored-token", "D:/app/WEB-INF/lib/", true);
        check("DS50109".equals(probeCtor2.product)
                        && "D:/app/WEB-INF/lib/".equals(probeCtor2.configPath),
                "existing-local RegStr probe passes ConfigPath to 2-arg RegisterMain");
        ModernRegisterMain3 probeCtor3 = (ModernRegisterMain3)
                ExistingLocalRegStrProbe.instantiateTargetRegisterMain(
                        ModernRegisterMain3.class, "DS28", "encrypted-token", "D:/ds28/WEB-INF/lib/", true);
        check("encrypted-token".equals(probeCtor3.json)
                        && "D:/ds28/WEB-INF/lib/".equals(probeCtor3.configPath),
                "existing-local RegStr probe preserves token/path for 3-arg RegisterMain");
        LegacyRegisterMain1 probeCtor1 = (LegacyRegisterMain1)
                ExistingLocalRegStrProbe.instantiateTargetRegisterMain(
                        LegacyRegisterMain1.class, "DS50109", "ignored-token", "D:/app/WEB-INF/lib/", true);
        check("DS50109".equals(probeCtor1.product),
                "existing-local RegStr probe supports 1-arg RegisterMain default-path generation");
''')

# Insert DS2802 fixture after DS2406 binary-evidence fixture.
replace_once(
    'src/test/java/RefactorSmokeTest.java',
'''        check("DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId)
                        && ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.regStr == null
                        && ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 accepts target-binary family evidence and defers RegStr to target component");

        Path qt30103Root''',
'''        check("DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId)
                        && ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.regStr == null
                        && ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 accepts target-binary family evidence and defers RegStr to target component");

        Path ds2802Root = base.resolve("java-DS2802");
        Path ds2802Lib = ds2802Root.resolve("WEB-INF/lib");
        Path ds2802Global = ds2802Root.resolve("WEB-INF/classes/com/common/global/Global.class");
        Files.createDirectories(ds2802Lib);
        Files.createDirectories(ds2802Global.getParent());
        Files.write(ds2802Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(ds2802Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=DS2802"), StandardCharsets.UTF_8);
        Files.write(ds2802Global, "PRODUCT_NUM DS28".getBytes(StandardCharsets.US_ASCII));
        check(!LegacyJavaRegistrationMetadata.requiresDirectoryMapping(ds2802Root.toFile(), "DS2802"),
                "DS28 Global with direct PRODUCT_NUM is not mistaken for registerProductBeans dispatch");
        LicenseRecoverModernGUIJavaPlan ds2802Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2802Root.toFile());
        check("DS28".equals(ds2802Plan.authorizationFamily)
                        && "DS28".equals(ds2802Plan.runtimeProductId)
                        && ds2802Plan.automaticRecoveryReady
                        && ds2802Plan.regStrSummary().contains("动态"),
                "DS2802 accepts DS28 only because the selected target Global.class contains that exact token");
        Path dispatchGlobal = base.resolve("dispatch-only/WEB-INF/classes/com/common/global/Global.class");
        Files.createDirectories(dispatchGlobal.getParent());
        Files.write(dispatchGlobal,
                "setProductMain setProductMainNum setProductNums".getBytes(StandardCharsets.US_ASCII));
        check(LegacyJavaRegistrationMetadata.requiresDirectoryMapping(
                        base.resolve("dispatch-only").toFile(), "ANY0001"),
                "Global registerProductBeans setter trio still forces fail-closed directory mapping");

        Path qt30103Root''')

# Replace DS501 exact-runtime-token fixture with config-driven runtime evidence.
replace_once(
    'src/test/java/RefactorSmokeTest.java',
'''        Files.write(ds501Classes.resolve("RegistrationEvidence.class"),
                "DS501 DS50109".getBytes(StandardCharsets.US_ASCII));
        ds501Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds501Root.toFile());
        check("DS501".equals(ds501Plan.authorizationFamily)
                        && "DS50109".equals(ds501Plan.runtimeProductId)
                        && ds501Plan.automaticRecoveryReady
                        && ds501Plan.regStrSummary().contains("动态"),
                "DS501 family/runtime IDs require exact target-binary tokens and use runtime RegStr probe");
''',
'''        Files.write(ds501Classes.resolve("RegistrationEvidence.class"),
                "DS501".getBytes(StandardCharsets.US_ASCII));
        Path ds501SystemInfo = ds501Classes.resolve("com/itmc/register/utils/SystemInfo.class");
        Path ds501Listener = ds501Classes.resolve("com/itmc/register/service/RegisterListener.class");
        Files.createDirectories(ds501SystemInfo.getParent());
        Files.createDirectories(ds501Listener.getParent());
        Files.write(ds501SystemInfo,
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(ds501Listener,
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain getRegStr versionID contains"
                        .getBytes(StandardCharsets.US_ASCII));
        ds501Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds501Root.toFile());
        check("DS501".equals(ds501Plan.authorizationFamily)
                        && "DS50109".equals(ds501Plan.runtimeProductId)
                        && ds501Plan.automaticRecoveryReady
                        && ds501Plan.regStrSummary().contains("动态"),
                "DS501 concrete runtime ID is accepted from target config only when target bytecode proves config->RegisterMain flow");
''')

print('DS2802/DS50109 evidence fix applied')
