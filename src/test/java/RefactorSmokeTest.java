import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RefactorSmokeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS: " + message);
    }

    public static final class LegacyRegisterMain1 {
        final String product;
        public LegacyRegisterMain1(String product) { this.product = product; }
    }

    public static final class LegacyRegisterMain2 {
        final String product;
        final String configPath;
        public LegacyRegisterMain2(String product, String configPath) {
            this.product = product;
            this.configPath = configPath;
        }
    }

    public static final class ModernRegisterMain3 {
        final String product;
        final String json;
        final String configPath;
        public ModernRegisterMain3(String product, String json, String configPath) {
            this.product = product;
            this.json = json;
            this.configPath = configPath;
        }
    }

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("licenserecover-smoke");

        check("fwq".equals(LicenseRecover.LOCAL_AUTH_USER_ID),
                "local authorization UserID fixed to fwq");
        Set<String> oneDefault = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D1));
        Set<String> legacyPath = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D2));
        Set<String> modernThreeArg = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D3));
        Set<String> modernRootFallback = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D3, JavaRegistrationRuntimeProfile.D2));
        Set<String> modernDefault = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D2));
        check(JavaRegistrationRuntimeProfile.chooseModes(false, oneDefault, false).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.ONE_ARG_DEFAULT,
                "DS50109-style startup keeps legacy one-arg target-default constructor");
        check(JavaRegistrationRuntimeProfile.chooseModes(false, legacyPath, true).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_PATH,
                "DS2406-style startup keeps legacy two-arg explicit root path");
        check(JavaRegistrationRuntimeProfile.chooseModes(true, modernThreeArg, true).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.THREE_ARG_TOKEN_PATH,
                "QT30103-style startup keeps modern three-arg token/root constructor");
        List<JavaRegistrationRuntimeProfile.Mode> ds28Modes =
                JavaRegistrationRuntimeProfile.chooseModes(true, modernRootFallback, true);
        check(ds28Modes.size() == 2
                        && ds28Modes.get(0) == JavaRegistrationRuntimeProfile.Mode.THREE_ARG_TOKEN_PATH
                        && ds28Modes.get(1) == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_TOKEN_DEFAULT,
                "DS2802-style startup preserves explicit-root then default-path fallback order");
        check(JavaRegistrationRuntimeProfile.chooseModes(true, modernDefault, false).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_TOKEN_DEFAULT,
                "DS3110-style startup keeps modern two-arg target-default constructor");
        List<JavaRegistrationRuntimeProfile.Mode> ytWrapperModes =
                JavaRegistrationRuntimeProfile.chooseModes(true, modernRootFallback, true, true);
        check(ytWrapperModes.size() == 1
                        && ytWrapperModes.get(0) == JavaRegistrationRuntimeProfile.Mode.THREE_ARG_TOKEN_PATH,
                "YT001xx wrapper consumes proven servlet root without inventing two-token fallback");
        File yxRootBase = base.resolve("yx030506-root").toFile();
        File yxClassesBase = base.resolve("yx030506-root/WEB-INF/classes").toFile();
        check(JavaRegistrationRuntimeProfile.selectExplicitBase(yxRootBase, yxClassesBase, false, true)
                        .equals(yxClassesBase),
                "YX030506 ProjectSourcesPath classpath evidence selects WEB-INF/classes as RegisterMain base");
        check(JavaRegistrationRuntimeProfile.selectExplicitBase(yxRootBase, yxClassesBase, true, true)
                        .equals(yxRootBase),
                "Servlet root remains authoritative when both explicit path proofs are present");
        check(LicenseRecoverJavaHost.containsAllCsv("QT0420,QT0437", "QT0420"),
                "Java persisted-mode verification accepts target-proven alias token");
        check(!LicenseRecoverJavaHost.containsAllCsv("QT0420", "YT00129"),
                "YT00129 SoftVersionID is distinct from its QT0420 startup registration alias");
        check("YT001".equals(LegacyJavaRegistrationMetadata.selectFallbackPrefix(
                        Arrays.asList("QT1001", "DS26", "YT001", "YT00129"), "YT00138")),
                "legacy runtime family is derived from target RegisterUtil constants");

        LegacyRegisterMain2 legacyCtor = (LegacyRegisterMain2)
                LicenseRecover.instantiateRegisterMainCompatible(
                        LegacyRegisterMain2.class, "DS24", "encrypted-json", "D:/app/WEB-INF/lib/");
        check("DS24".equals(legacyCtor.product)
                        && "D:/app/WEB-INF/lib/".equals(legacyCtor.configPath),
                "legacy 2-arg RegisterMain receives configPath as second argument");

        ModernRegisterMain3 modernCtor = (ModernRegisterMain3)
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

        Path javaRoot = base.resolve("javaApp");
        Path javaLib = javaRoot.resolve("WEB-INF/lib");
        Files.createDirectories(javaLib);
        Files.write(javaLib.resolve("ITMCReg.jar"), new byte[]{1, 2, 3});
        Files.write(javaLib.resolve("config.xml"), Arrays.asList("<ROOT><reg/></ROOT>"));
        Files.write(javaRoot.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID: YT00123"));
        AppInfo javaInfo = AppDetector.detect(javaRoot.toFile());
        check(javaInfo.type == AppInfo.Type.JAVA, "detect Java app");
        check("YT00123".equals(javaInfo.softVersionId), "read Java SoftVersionID");
        check(javaInfo.appRoot.getCanonicalFile().equals(javaRoot.toFile().getCanonicalFile()),
                "resolve Java app root");
        check(ConfigSafety.prepareJavaWay1(javaInfo, System.out::print),
                "Java way-1 prewrite backup succeeds");
        check(Files.list(javaLib).anyMatch(p -> p.getFileName().toString().contains("prewrite")),
                "Java way-1 prewrite backup exists");

        Path yt129Root = base.resolve("java-YT00129");
        Path yt129Lib = yt129Root.resolve("WEB-INF/lib");
        Files.createDirectories(yt129Lib);
        Files.createDirectories(yt129Root.resolve("data"));
        Files.write(yt129Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(yt129Root.resolve("systemConfig.yml"), Arrays.asList("global.system.VersionID=YT00129"), StandardCharsets.UTF_8);
        Files.write(yt129Root.resolve("data/config.xml"), Arrays.asList("<ROOT><SystemSoft><SoftVersionID>YT00129</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("YT00129".equals(LicenseRecover.readSoftId(yt129Root.toString())), "read equals-style YT00129 VersionID");
        check(!LicenseRecover.isNewStyleApp(yt129Root.toString()), "YT00129 data config is not misclassified as QT30xxx style");
        check(LicenseRecover.usesRootConfigApp(yt129Root.toString()), "YT00129 uses webapp-root authorization config");
        check("QT04".equals(LicenseRecover.productMainFor("YT00129")), "YT00129 maps to QT04 ProName");
        check(LicenseRecover.resolveJavaRegStr(yt129Root.toString(), "YT00129") == null,
                "YT00129 inferred QT0420 is not accepted without target-directory evidence");
        LicenseRecoverModernGUIJavaPlan yt129Plan = LicenseRecoverModernGUIJavaPlan.inspect(yt129Root.toFile());
        check(yt129Plan.detected && yt129Plan.productName == null
                        && yt129Plan.authorizationFamily == null,
                "Java executable plan does not map YT00129 to QT04 without directory evidence");
        check(yt129Plan.regStr == null && yt129Plan.rootConfigStyle
                        && !yt129Plan.automaticRecoveryReady,
                "YT00129 stays fail-closed instead of exposing inferred QT0420 RegStr");

        Path ds2406Root = base.resolve("java-DS2406");
        Path ds2406Lib = ds2406Root.resolve("WEB-INF/lib");
        Files.createDirectories(ds2406Lib);
        Files.createDirectories(ds2406Root.resolve("data"));
        Files.write(ds2406Lib.resolve("ITMCReg-1.0.2.jar"), new byte[]{1});
        Files.write(ds2406Root.resolve("systemConfig.yml"), Arrays.asList(
                "global.system.VersionID=DS2406",
                "global.system.VersionName=跨境电子商务数据分析与应用系统"), StandardCharsets.UTF_8);
        Files.write(ds2406Root.resolve("data/config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS2406</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("DS24".equals(LicenseRecover.productMainFor("DS2406")),
                "DS2406 startup RegisterMain uses DS24 family");
        check("DS24".equals(LicenseRecover.localRegisterProductFor("DS2406", false)),
                "DS2406 local-registration page uses DS24 family");
        check(LicenseRecover.resolveJavaRegStr(ds2406Root.toString(), "DS2406") == null,
                "DS2406 does not invent RegStr from VersionID");
        LicenseRecoverModernGUIJavaPlan ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check(ds2406Plan.generation.contains("DS24")
                        && ds2406Plan.authorizationFamily == null
                        && ds2406Plan.runtimeProductId == null,
                "DS2406 generation label does not become executable identity without directory proof");
        check(!ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.recoveryReadiness.contains("目录"),
                "DS2406 stays blocked when fixture lacks directory registration-id evidence");
        Files.createDirectories(ds2406Root.resolve("WEB-INF/classes"));
        Files.write(ds2406Root.resolve("WEB-INF/classes/RegistrationEvidence.class"),
                "DS24".getBytes(StandardCharsets.US_ASCII));
        ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check("DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId)
                        && ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.regStr == null
                        && ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 accepts target-binary family evidence and defers RegStr to target component");

        Path ds2406XmlUtil = ds2406Root.resolve("WEB-INF/classes/util/IXmlUtil.class");
        Path ds2406IGlobal = ds2406Root.resolve("WEB-INF/classes/global/IGlobal.class");
        Path ds2406Listener = ds2406Root.resolve("WEB-INF/classes/listener/SystemSetListener.class");
        Path ds2406SoftReg = ds2406Root.resolve("WEB-INF/classes/action/SoftRegAction.class");
        Files.createDirectories(ds2406XmlUtil.getParent());
        Files.createDirectories(ds2406IGlobal.getParent());
        Files.createDirectories(ds2406Listener.getParent());
        Files.createDirectories(ds2406SoftReg.getParent());
        Files.write(ds2406XmlUtil,
                "systemConfig.yml global.system.VersionID /data/config.xml global/IStatic _SYS_CODE"
                        .getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds2406IGlobal,
                "SYS_PRODUCT_CODE DS24".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds2406SoftReg,
                "DS24 itmc/regedit/RegisterMain doRegistry".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds2406Listener,
                "DS24 itmc/regedit/RegisterMain checkReInfo getRegInfo regStr global/IStatic _SYS_CODE"
                        .getBytes(StandardCharsets.ISO_8859_1));
        ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check(ds2406Plan.regStr == null && ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 incomplete startup membership evidence still defers RegStr dynamically");
        Files.write(ds2406Listener,
                "DS24 itmc/regedit/RegisterMain checkReInfo getRegInfo regStr global/IStatic _SYS_CODE contains"
                        .getBytes(StandardCharsets.ISO_8859_1));
        ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check("DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId)
                        && "DS2406".equals(ds2406Plan.regStr)
                        && ds2406Plan.automaticRecoveryReady
                        && !ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 primary RegStr is static only after target VersionID->_SYS_CODE->RegStr.contains flow is complete");

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

        Path ds3110Root = base.resolve("java-DS3110");
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

        Path qt30103Root = base.resolve("java-QT30103");
        Path qt30103Lib = qt30103Root.resolve("WEB-INF/lib");
        Files.createDirectories(qt30103Lib);
        Files.createDirectories(qt30103Root.resolve("data"));
        Files.write(qt30103Lib.resolve("ITMCReg-1.0.5.jar"), new byte[]{1});
        Files.write(qt30103Root.resolve("systemConfig.yml"), Arrays.asList("global.system.VersionID=QT30103"), StandardCharsets.UTF_8);
        Files.write(qt30103Root.resolve("data/config.xml"), Arrays.asList("<ROOT><SystemSoft><SoftVersionID>QT30103</SoftVersionID><regInfo>QT30101,QT30102,QT30103,QT30104</regInfo></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check(LicenseRecover.isNewStyleApp(qt30103Root.toString()), "QT30103 regInfo selects QT30xxx style");
        check("QT30101,QT30102,QT30103,QT30104".equals(LicenseRecover.resolveJavaRegStr(qt30103Root.toString(), "QT30103")), "QT30103 preserves data regInfo");
        LicenseRecoverModernGUIJavaPlan qtPlan = LicenseRecoverModernGUIJavaPlan.inspect(qt30103Root.toFile());
        check(qtPlan.detected && "QT30103".equals(qtPlan.productName),
                "Java GUI plan keeps QT30xxx SoftVersionID as ProName");
        check(qtPlan.generation.contains("QT30xxx") && qtPlan.configTargets.contains("webapp根"),
                "Java GUI plan reports QT30xxx generation and dual config targets");
        check(qtPlan.automaticRecoveryReady
                        && qtPlan.recoveryReadiness.contains("目标目录"),
                "QT30xxx automatic recovery is allowed only from direct data/config.xml evidence");
        check(!LicenseRecoverModernGUIJavaPlan.inspect(yt129Root.toFile()).automaticRecoveryReady,
                "legacy YT fixture without class-level mapping is fail-closed");

        Path xmtRoot = base.resolve("java-XMT0107");
        Path xmtLib = xmtRoot.resolve("WEB-INF/lib");
        Path xmtClasses = xmtRoot.resolve("WEB-INF/classes");
        Files.createDirectories(xmtLib);
        Files.createDirectories(xmtClasses);
        Files.write(xmtLib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(xmtClasses.resolve("config.xml"), Arrays.asList("<ROOT><SystemSoft><SoftVersionID>XMT0107</SoftVersionID><regInfo>QT0423,QT0428,QT0424,QT0425,QT0427,QT0426,QT0406,QT0430</regInfo></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("XMT0107".equals(LicenseRecover.readSoftId(xmtRoot.toString())), "read XMT0107 from WEB-INF/classes/config.xml");
        check("XMT01".equals(LicenseRecover.productMainFor("XMT0107")), "XMT0107 maps to XMT01 ProName");
        AppInfo xmtDetected = AppDetector.detect(xmtRoot.toFile());
        check("XMT0107".equals(xmtDetected.softVersionId), "AppDetector reads XMT0107 classes config");
        LicenseRecoverModernGUIAutoRecovery.Detection xmtOneClick =
                LicenseRecoverModernGUIAutoRecovery.detect(xmtRoot.toFile());
        check("XMT0107".equals(xmtOneClick.versionId), "one-click detector reads XMT0107 classes config");
        check(LicenseRecover.usesRootConfigApp(xmtRoot.toString()), "XMT0107 uses webapp-root authorization config");
        check(LicenseRecover.resolveJavaRegStr(xmtRoot.toString(), "XMT0107") == null,
                "XMT0107 classes RegStr alone is insufficient without directory-proven identity");
        LicenseRecoverModernGUIJavaPlan xmtPlan = LicenseRecoverModernGUIJavaPlan.inspect(xmtRoot.toFile());
        check(xmtPlan.detected && xmtPlan.productName == null
                        && xmtPlan.authorizationFamily == null,
                "Java executable plan does not infer XMT01 family from VersionID");
        check(xmtPlan.generation.contains("XMT") && xmtPlan.regStrSummary().startsWith("8 项")
                        && !xmtPlan.automaticRecoveryReady,
                "XMT directory RegStr may be displayed but execution stays blocked until identity is proven");
        Files.write(xmtClasses.resolve("RegistrationEvidence.class"),
                "XMT01".getBytes(StandardCharsets.US_ASCII));
        xmtPlan = LicenseRecoverModernGUIJavaPlan.inspect(xmtRoot.toFile());
        check("XMT01".equals(xmtPlan.authorizationFamily)
                        && "XMT01".equals(xmtPlan.runtimeProductId)
                        && xmtPlan.automaticRecoveryReady,
                "XMT family becomes executable only after exact token appears in target bytecode");

        Path xmt0102Root = base.resolve("java-XMT0102-json-regstr");
        Path xmt0102Lib = xmt0102Root.resolve("WEB-INF/lib");
        Path xmt0102Classes = xmt0102Root.resolve("WEB-INF/classes");
        Files.createDirectories(xmt0102Lib);
        Files.createDirectories(xmt0102Classes.resolve("com/itmc/register/utils"));
        Files.createDirectories(xmt0102Classes.resolve("com/itmc/register/service"));
        Files.write(xmt0102Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(xmt0102Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=XMT0102"), StandardCharsets.UTF_8);
        Files.write(xmt0102Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>XMT0102</SoftVersionID><regInfo>QT100110,QT100106</regInfo></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        Files.write(xmt0102Classes.resolve("RegistrationEvidence.class"),
                "XMT01".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0102Classes.resolve("com/itmc/register/utils/SystemInfo.class"),
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0102Classes.resolve("com/itmc/register/service/RegisterListener.class"),
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain checkReInfo getRegInfo "
                        .concat("com/alibaba/fastjson/JSONObject toJSONString parseObject regStr versionID contains")
                        .getBytes(StandardCharsets.US_ASCII));
        LicenseRecoverModernGUIJavaPlan xmt0102Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(xmt0102Root.toFile());
        check("XMT01".equals(xmt0102Plan.authorizationFamily)
                        && "XMT0102".equals(xmt0102Plan.runtimeProductId)
                        && "QT100110,QT100106".equals(xmt0102Plan.regStr)
                        && xmt0102Plan.automaticRecoveryReady,
                "XMT0102 proves concrete startup ProductID when RegStr is consumed through JSONObject");

        Path xmt0103Root = base.resolve("java-XMT0103-json-regstr");
        Path xmt0103Lib = xmt0103Root.resolve("WEB-INF/lib");
        Path xmt0103Classes = xmt0103Root.resolve("WEB-INF/classes");
        Files.createDirectories(xmt0103Lib);
        Files.createDirectories(xmt0103Classes.resolve("com/itmc/register/utils"));
        Files.createDirectories(xmt0103Classes.resolve("com/itmc/register/service"));
        Files.write(xmt0103Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(xmt0103Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=XMT0103"), StandardCharsets.UTF_8);
        Files.write(xmt0103Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>XMT0103</SoftVersionID><regInfo>QT100102</regInfo></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        Files.write(xmt0103Classes.resolve("RegistrationEvidence.class"),
                "XMT01".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0103Classes.resolve("com/itmc/register/utils/SystemInfo.class"),
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0103Classes.resolve("com/itmc/register/service/RegisterListener.class"),
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain checkReInfo getRegInfo "
                        .concat("com/alibaba/fastjson/JSONObject toJSONString parseObject regStr versionID contains")
                        .getBytes(StandardCharsets.US_ASCII));
        LicenseRecoverModernGUIJavaPlan xmt0103Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(xmt0103Root.toFile());
        check("XMT01".equals(xmt0103Plan.authorizationFamily)
                        && "XMT0103".equals(xmt0103Plan.runtimeProductId)
                        && "QT100102".equals(xmt0103Plan.regStr)
                        && xmt0103Plan.automaticRecoveryReady,
                "XMT0103 reuses the XMT Fastjson startup profile without a product-specific exception");

        Path ds501Root = base.resolve("java-DS50109");
        Path ds501Lib = ds501Root.resolve("WEB-INF/lib");
        Path ds501Classes = ds501Root.resolve("WEB-INF/classes");
        Files.createDirectories(ds501Lib);
        Files.createDirectories(ds501Classes);
        Files.write(ds501Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(ds501Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>3</regType></reg><SystemSoft><SoftVersionID>DS50109</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        Files.write(ds501Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=DS50109"), StandardCharsets.UTF_8);
        check("DS50109".equals(LicenseRecover.productMainFor("DS50109")),
                "DS501 direct rebuild uses concrete runtime check id");
        check("DS501".equals(LicenseRecover.localRegisterProductFor("DS50109", false)),
                "DS501 local-registration page uses DS501 family");
        check(LicenseRecover.resolveJavaRegStr(ds501Root.toString(), "DS50109") == null,
                "DS501 does not use concrete SoftVersionID as fallback RegStr");
        LicenseRecoverModernGUIJavaPlan ds501Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds501Root.toFile());
        check(ds501Plan.generation.contains("DS501") && ds501Plan.authorizationFamily == null,
                "DS501 generation label does not imply an executable family");
        check(ds501Plan.runtimeProductId == null && ds501Plan.regStr == null
                        && !ds501Plan.automaticRecoveryReady,
                "DS501 stays fail-closed without directory identity/RegStr evidence");
        Files.write(ds501Classes.resolve("RegistrationEvidence.class"),
                "DS501".getBytes(StandardCharsets.US_ASCII));
        Path ds501SystemInfo = ds501Classes.resolve("com/itmc/register/utils/SystemInfo.class");
        Path ds501Contant = ds501Classes.resolve("com/itmc/register/utils/RegisterContant.class");
        Path ds501Listener = ds501Classes.resolve("com/itmc/register/service/RegisterListener.class");
        Files.createDirectories(ds501SystemInfo.getParent());
        Files.createDirectories(ds501Listener.getParent());
        Files.write(ds501SystemInfo,
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(ds501Contant,
                "global.system.VersionID versionID java/util/Properties getProperty"
                        .getBytes(StandardCharsets.US_ASCII));
        Files.write(ds501Listener,
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain getRegStr "
                        .concat("com/itmc/register/utils/RegisterContant versionID contains")
                        .getBytes(StandardCharsets.US_ASCII));
        ds501Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds501Root.toFile());
        check("DS501".equals(ds501Plan.authorizationFamily)
                        && "DS50109".equals(ds501Plan.runtimeProductId)
                        && "DS50109".equals(ds501Plan.regStr)
                        && ds501Plan.automaticRecoveryReady
                        && !ds501Plan.regStrSummary().contains("动态"),
                "DS50109 derives primary RegStr statically only from target VersionID->registerId->RegisterMain->RegStr.contains(versionID) flow");

        Path ds501WeakRoot = base.resolve("java-DS50112-weak-primary-regstr");
        Path ds501WeakLib = ds501WeakRoot.resolve("WEB-INF/lib");
        Path ds501WeakClasses = ds501WeakRoot.resolve("WEB-INF/classes");
        Files.createDirectories(ds501WeakLib);
        Files.createDirectories(ds501WeakClasses.resolve("com/itmc/register/utils"));
        Files.createDirectories(ds501WeakClasses.resolve("com/itmc/register/service"));
        Files.write(ds501WeakLib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(ds501WeakRoot.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=DS50112"), StandardCharsets.UTF_8);
        Files.write(ds501WeakClasses.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS50112</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        Files.write(ds501WeakClasses.resolve("RegistrationEvidence.class"),
                "DS501".getBytes(StandardCharsets.US_ASCII));
        Files.write(ds501WeakClasses.resolve("com/itmc/register/utils/SystemInfo.class"),
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(ds501WeakClasses.resolve("com/itmc/register/service/RegisterListener.class"),
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain getRegStr "
                        .concat("com/itmc/register/utils/RegisterContant versionID contains")
                        .getBytes(StandardCharsets.US_ASCII));
        LicenseRecoverModernGUIJavaPlan ds501WeakPlan =
                LicenseRecoverModernGUIJavaPlan.inspect(ds501WeakRoot.toFile());
        check("DS50112".equals(ds501WeakPlan.runtimeProductId)
                        && ds501WeakPlan.regStr == null
                        && ds501WeakPlan.regStrSummary().contains("动态")
                        && ds501WeakPlan.automaticRecoveryReady,
                "DS501 runtime proof alone does not synthesize static RegStr when RegisterContant VersionID source proof is missing");

        Path ds50113Root = base.resolve("java-DS50113");
        Path ds50113Lib = ds50113Root.resolve("WEB-INF/lib");
        Path ds50113Classes = ds50113Root.resolve("WEB-INF/classes");
        Files.createDirectories(ds50113Lib);
        Files.createDirectories(ds50113Classes.resolve("com/itmc/register/utils"));
        Files.createDirectories(ds50113Classes.resolve("com/itmc/register/service"));
        Files.write(ds50113Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(ds50113Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=DS50113"), StandardCharsets.UTF_8);
        Files.write(ds50113Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS50113</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        Files.write(ds50113Classes.resolve("RegistrationEvidence.class"),
                "DS501".getBytes(StandardCharsets.US_ASCII));
        Files.write(ds50113Classes.resolve("com/itmc/register/utils/SystemInfo.class"),
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(ds50113Classes.resolve("com/itmc/register/utils/RegisterContant.class"),
                "global.system.VersionID versionID java/util/Properties getProperty"
                        .getBytes(StandardCharsets.US_ASCII));
        Files.write(ds50113Classes.resolve("com/itmc/register/service/RegisterListener.class"),
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain getRegStr "
                        .concat("com/itmc/register/utils/RegisterContant versionID contains")
                        .getBytes(StandardCharsets.US_ASCII));
        LicenseRecoverModernGUIJavaPlan ds50113Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(ds50113Root.toFile());
        check("DS501".equals(ds50113Plan.authorizationFamily)
                        && "DS50113".equals(ds50113Plan.runtimeProductId)
                        && "DS50113".equals(ds50113Plan.regStr)
                        && ds50113Plan.automaticRecoveryReady,
                "DS50113 independently proves concrete primary RegStr from its own target-directory registration flow");

        Path yx305Root = base.resolve("java-YX030506");
        Path yx305Lib = yx305Root.resolve("WEB-INF/lib");
        Path yx305Classes = yx305Root.resolve("WEB-INF/classes");
        Files.createDirectories(yx305Lib);
        Files.createDirectories(yx305Classes);
        Files.write(yx305Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(yx305Root.resolve("systemConfig.yml"), Arrays.asList("global.system.VersionID=YX030506"), StandardCharsets.UTF_8);
        Files.write(yx305Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>YX030506</SoftVersionID><regInfo>QT100101,QT100102</regInfo></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("YX030506".equals(LicenseRecover.productMainFor("YX030506")),
                "YX0305 direct rebuild uses concrete runtime check id");
        check("YX0305".equals(LicenseRecover.localRegisterProductFor("YX030506", false)),
                "YX0305 local-registration page uses YX0305 family");
        check(LicenseRecover.resolveJavaRegStr(yx305Root.toString(), "YX030506") == null,
                "YX030506 classes RegStr alone cannot authorize execution without identity evidence");
        LicenseRecoverModernGUIJavaPlan yx305Plan = LicenseRecoverModernGUIJavaPlan.inspect(yx305Root.toFile());
        check(yx305Plan.generation.contains("YX0305") && yx305Plan.authorizationFamily == null,
                "YX0305 generation label does not imply executable identity");
        check(yx305Plan.runtimeProductId == null
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && !yx305Plan.automaticRecoveryReady,
                "YX0305 directory RegStr is retained for diagnostics but execution is blocked without identity proof");
        Path yx305Servlet = yx305Classes.resolve("com/itmc/sys/platformregister/RegisterHttpServlet.class");
        Path yx305XmlUtil = yx305Classes.resolve("com/itmc/utils/IXmlUtil.class");
        Path yx305Runner = yx305Classes.resolve("com/itmc/utils/ProjectApplicationRunner.class");
        Files.createDirectories(yx305Servlet.getParent());
        Files.createDirectories(yx305XmlUtil.getParent());
        Files.write(yx305Servlet,
                "YX0305 itmc/regedit/RegisterMain doRegistry".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(yx305XmlUtil,
                "global.system.VersionID /config.xml SystemSoft SoftVersionID regInfo SYS_PRODUCT_NUM PRODUCT_ALL_NUM PRODUCT_INFO"
                        .getBytes(StandardCharsets.ISO_8859_1));
        Files.write(yx305Runner,
                "PRODUCT_ALL_NUM split itmc/regedit/RegisterMain checkReInfo"
                        .getBytes(StandardCharsets.ISO_8859_1));
        yx305Plan = LicenseRecoverModernGUIJavaPlan.inspect(yx305Root.toFile());
        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && yx305Plan.runtimeProductId == null
                        && !yx305Plan.automaticRecoveryReady,
                "YX030506 family evidence alone stays blocked when startup PRODUCT_ALL_NUM flow is incomplete");
        Files.write(yx305Runner,
                "PRODUCT_ALL_NUM split itmc/regedit/GetRegisterCode RegeditNew itmcsoft itmc/regedit/RegisterMain writeRegisterUser checkReInfo"
                        .getBytes(StandardCharsets.ISO_8859_1));
        yx305Plan = LicenseRecoverModernGUIJavaPlan.inspect(yx305Root.toFile());
        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && "YX030506".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && yx305Plan.automaticRecoveryReady,
                "YX030506 runtime id is accepted only from target config->PRODUCT_ALL_NUM->RegisterMain flow");

        Path qt401Root = base.resolve("java-QT40101");
        Path qt401Lib = qt401Root.resolve("WEB-INF/lib");
        Path qt401Classes = qt401Root.resolve("WEB-INF/classes");
        Files.createDirectories(qt401Lib);
        Files.createDirectories(qt401Classes);
        Files.write(qt401Lib.resolve("ITMCReg-1.0.5.jar"), new byte[]{1});
        Files.write(qt401Root.resolve("systemConfig.yml"), Arrays.asList(
                "global.system.VersionID=QT40101",
                "global.system.VersionName=paas管理平台"), StandardCharsets.UTF_8);
        Files.write(qt401Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>QT40101</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        Files.write(qt401Classes.resolve("config1.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>QT401</SoftVersionID></SystemSoft>"
                        + "<System id=\"QT40101\"><SoftName>paas管理平台</SoftName></System></ROOT>"),
                StandardCharsets.UTF_8);
        check("QT401".equals(LicenseRecover.productMainFor("QT40101")),
                "QT40101 direct runtime family maps to QT401");
        check("QT401".equals(LicenseRecover.localRegisterProductFor("QT40101", false)),
                "QT40101 local family maps to QT401");
        check(LicenseRecover.resolveJavaRegStr(qt401Root.toString(), "QT40101") == null,
                "QT40101 no longer synthesizes minimum RegStr from a known sample");
        LicenseRecoverModernGUIJavaPlan qt401Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(qt401Root.toFile());
        check(qt401Plan.generation.contains("QT401")
                        && "QT401".equals(qt401Plan.authorizationFamily)
                        && "QT401".equals(qt401Plan.runtimeProductId),
                "Java GUI plan confirms QT401 family from config1.xml evidence");
        check(qt401Plan.regStr == null && qt401Plan.automaticRecoveryReady
                        && qt401Plan.regStrSummary().contains("动态")
                        && qt401Plan.recoveryReadiness.contains("RegisterMain.getRegInfo"),
                "QT40101 never invents RegStr and may read it from the target registration component before write");

        Path qt100101Root = base.resolve("java-QT100101");
        Path qt100101Lib = qt100101Root.resolve("WEB-INF/lib");
        Path qt100101Classes = qt100101Root.resolve("WEB-INF/classes");
        Files.createDirectories(qt100101Lib);
        Files.createDirectories(qt100101Classes);
        Files.write(qt100101Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(qt100101Root.resolve("systemConfig.yml"), Arrays.asList(
                "global.system.VersionID=QT100101",
                "global.system.VersionName=互联网营销师大赛平台"), StandardCharsets.UTF_8);
        Files.write(qt100101Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>3</regType></reg><SystemSoft><SoftVersionID>QT100101</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        check("QT100101".equals(LicenseRecover.productMainFor("QT100101")),
                "QT100101 startup RegisterMain uses concrete QT100101 id");
        check("QT100101".equals(LicenseRecover.localRegisterProductFor("QT100101", false)),
                "QT100101 local-registration page uses concrete QT100101 id");
        check(LicenseRecover.resolveJavaRegStr(qt100101Root.toString(), "QT100101") == null,
                "QT100101 no longer synthesizes minimum RegStr from a known sample");
        LicenseRecoverModernGUIJavaPlan qt100101Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(qt100101Root.toFile());
        check(qt100101Plan.generation.contains("QT1001系列")
                        && "QT100101".equals(qt100101Plan.authorizationFamily)
                        && "QT100101".equals(qt100101Plan.runtimeProductId),
                "Java GUI plan confirms QT100101 family and runtime id");
        check(qt100101Plan.regStr == null && qt100101Plan.automaticRecoveryReady
                        && qt100101Plan.regStrSummary().contains("动态")
                        && qt100101Plan.recoveryReadiness.contains("RegisterMain.getRegInfo"),
                "QT100101 keeps RegStr unset until the target registration component returns it before write");

        check(!ExistingLocalRegStrProbe.isSafeLocalConfig(qt100101Classes.resolve("config.xml").toFile()),
                "regType=3 is never eligible for local RegStr probing");
        Path localProbeCfg = base.resolve("existing-local-config.xml");
        Files.write(localProbeCfg, Arrays.asList(
                "<ROOT><reg><regType>1</regType><regName>LOCAL-CIPHER-TEXT</regName></reg></ROOT>"),
                StandardCharsets.UTF_8);
        check(ExistingLocalRegStrProbe.isSafeLocalConfig(localProbeCfg.toFile()),
                "only regType=1 with non-empty regName is eligible for offline RegStr probing");

        Path genericClassesRoot = base.resolve("java-generic-classes");
        Path genericClassesLib = genericClassesRoot.resolve("WEB-INF/lib");
        Path genericClassesCfg = genericClassesRoot.resolve("WEB-INF/classes");
        Files.createDirectories(genericClassesLib);
        Files.createDirectories(genericClassesCfg);
        Files.write(genericClassesLib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(genericClassesCfg.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>ZZ99999</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        LicenseRecoverModernGUIJavaPlan genericClassesPlan =
                LicenseRecoverModernGUIJavaPlan.inspect(genericClassesRoot.toFile());
        check(genericClassesPlan.authorizationFamily == null
                        && genericClassesPlan.runtimeProductId == null
                        && genericClassesPlan.regStr == null
                        && !genericClassesPlan.automaticRecoveryReady,
                "unknown classes-config apps expose no guessed identity or catch-all RegStr");

        Path nestedRoot = base.resolve("nestedApp");
        Path nestedLib = nestedRoot.resolve("WEB-INF/WEB-INF/lib");
        Files.createDirectories(nestedLib);
        Files.write(nestedLib.resolve("ITMCReg-v.jar"), new byte[]{4});
        AppInfo nestedInfo = AppDetector.detect(nestedRoot.toFile());
        check(nestedInfo.type == AppInfo.Type.JAVA, "detect nested WEB-INF Java app");
        check(nestedInfo.appRoot.getCanonicalFile().equals(nestedRoot.toFile().getCanonicalFile()),
                "resolve nested Java app root");

        Path dotnetRoot = base.resolve("dotnetApp");
        Path dotnetBin = dotnetRoot.resolve("bin");
        Files.createDirectories(dotnetBin);
        Files.write(dotnetBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(dotnetBin.resolve("itmcRegedit.dll"), new byte[]{2, 3});
        Files.write(dotnetRoot.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS0101</SoftVersionID></SystemSoft></ROOT>"));
        AppInfo dotnetInfo = AppDetector.detect(dotnetRoot.toFile());
        check(dotnetInfo.type == AppInfo.Type.DOTNET, "detect .NET app");
        check("DS0101".equals(dotnetInfo.softVersionId), "read .NET SoftVersionID");
        check(PatchSafety.prepare(dotnetInfo, System.out::print), ".NET prepatch backup succeeds");
        check(Files.list(dotnetBin).anyMatch(p -> p.getFileName().toString().contains("prepatch")),
                ".NET prepatch backup exists");

        Path modernRoot = base.resolve("modernUppercaseOnly");
        Path modernBin = modernRoot.resolve("bin");
        Files.createDirectories(modernBin);
        Files.write(modernBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(modernBin.resolve("ITMC.Regedit.dll"), new byte[]{2});
        AppInfo modernInfo = AppDetector.detect(modernRoot.toFile());
        check(modernInfo.type == AppInfo.Type.DOTNET,
                "detect modern .NET app with uppercase ITMC.Regedit.dll only");
        check(!Files.exists(modernBin.resolve("itmcRegedit.dll")),
                "uppercase-only detector does not require legacy lowercase assembly");

        Path productFixture = base.resolve("YX0303-Web.dll");
        writeUtf16Fixture(productFixture, "YX0303", "YX030301", "YX030308", "YX030322");
        check("YX0303".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        productFixture.toFile(), null)),
                "one-click detects YX0303 ProName from target assembly strings");
        check("YX030301,YX030308,YX030322".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                productFixture.toFile(), "YX0303")),
                "one-click derives YX0303 local product list");
        Path noRegStrFixture = base.resolve("YX0303-no-products-Web.dll");
        writeUtf16Fixture(noRegStrFixture, "YX0303", "SoftVersionID", "ProName");
        check(LicenseRecoverModernGUIAutoRecovery.detectProductList(
                        noRegStrFixture.toFile(), "YX0303").isEmpty(),
                ".NET directory parser returns empty instead of inventing RegStr");

        Path yx030308ModeRoot = base.resolve("dotnet-YX030308-mode-merge");
        Path yx030308ModeBin = yx030308ModeRoot.resolve("bin");
        Files.createDirectories(yx030308ModeBin);
        writeUtf16Fixture(yx030308ModeBin.resolve("ITMC.Web.dll"),
                "YX0303", "YX030301", "YX030321", "SoftVersionID", "ProName");
        Files.write(yx030308ModeBin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        Files.write(yx030308ModeRoot.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg/><SystemSoft><SoftVersionID>YX030308</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        LicenseRecoverModernGUIAutoRecovery.Detection yx030308ModeDetection =
                LicenseRecoverModernGUIAutoRecovery.detect(yx030308ModeRoot.toFile());
        check("YX030308".equals(yx030308ModeDetection.versionId)
                        && "YX0303".equals(yx030308ModeDetection.productName),
                ".NET mode-merge fixture proves current SoftVersionID and YX0303 family from target directory");
        check("YX030301,YX030321".equals(LicenseRecoverModernGUIAutoRecovery.detectProductList(
                        yx030308ModeBin.resolve("ITMC.Web.dll").toFile(), "YX0303")),
                "legacy DLL mode scan intentionally lacks the current YX030308 token");
        check("YX030308,YX030301,YX030321".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx030308ModeDetection)),
                ".NET RegStr injects current SoftVersionID first and preserves proven compatibility modes");
        check(LicenseRecoverModernGUIAutoRecovery.containsRegStrToken(
                        LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx030308ModeDetection), "YX030308"),
                ".NET final RegStr explicitly contains the current system version mode");

        Path persistedModeConfig = base.resolve("persisted-modern-root-config.xml");
        String persistedModeJson = "{\"RegStr\":\"YX030101,YX030102\",\"ProName\":\"YX0301\"}";
        String persistedModeCipher = LicenseRecoverModernGUIAutoRecovery.desEncryptHex(
                "12345678" + persistedModeJson, "*ITMCYX0301OK*");
        Files.write(persistedModeConfig, Arrays.asList(
                "<ROOT><reg><regType>1</regType><regName>" + persistedModeCipher + "</regName></reg></ROOT>"), StandardCharsets.UTF_8);
        check("YX030101,YX030102".equals(LicenseRecoverModernGUIAutoRecovery.recoverDotNetLocalRegStrFromConfig(
                        persistedModeConfig.toFile(), "YX0301")),
                ".NET mode verifier reads persisted RegStr from exact site-root modern config");
        check(LicenseRecoverModernGUIAutoRecovery.recoverDotNetLocalRegStrFromConfig(
                        persistedModeConfig.toFile(), "YX0302") == null,
                ".NET mode verifier rejects persisted RegStr with wrong ProName");

        Path yx0102Root = base.resolve("dotnet-YX0102");
        Path yx0102Bin = yx0102Root.resolve("bin");
        Files.createDirectories(yx0102Bin);
        String yx0102DbKey = "ABCDEFGHijklmnop12345678";
        writeUtf16Fixture(yx0102Bin.resolve("ITMC.Web.dll"),
                "YS01", "RegeditNew", "NewRegistry", "DoRegistry", "ProName", "SoftVersionID",
                "GetProVersion", "CheckSoftVersionID", "Encrypt3Des", "Decrypt3Des",
                "GetRegisterVersionList", yx0102DbKey);
        Files.write(yx0102Bin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        Files.write(yx0102Root.resolve("Web.config"), Arrays.asList(
                "<configuration><appSettings><add key=\"productName\" value=\"YS01\" />",
                "</appSettings></configuration>"), StandardCharsets.UTF_8);
        Files.write(yx0102Root.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>3</regType></reg>",
                "<SystemSoft><SoftVersionID>YX0102</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        writeRegisterVersionFixture(yx0102Root.resolve("RegisterVersion.db"), yx0102DbKey,
                "YX0102", "qt1001", "QT100106", "QT100110");

        LicenseRecoverModernGUIAutoRecovery.Detection yx0102Detection =
                LicenseRecoverModernGUIAutoRecovery.detect(yx0102Root.toFile());
        check("YS01".equals(yx0102Detection.productName),
                "YX0102 direct ProName comes from target Web.config and is proven by target ITMC.Web.dll");
        check("YX0102".equals(LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102Detection)),
                "YX0102 direct RegStr is current target SoftVersionID, matching the native registration-page flow");
        LicenseRecoverModernGUIAutoRecovery.RegisterVersionEvidence yx0102Compatibility =
                LicenseRecoverModernGUIAutoRecovery.readRegisterVersionEvidence(yx0102Detection);
        check(yx0102Compatibility != null
                        && yx0102Compatibility.compatibility.containsKey("qt1001")
                        && yx0102Compatibility.compatibility.get("qt1001").equals(
                                Arrays.asList("QT100106", "QT100110")),
                "YX0102 RegisterVersion.db compatibility rows are decrypted with a key extracted from target DLL");

        Files.write(yx0102Root.resolve("Web.config"), Arrays.asList(
                "<configuration><appSettings><add key=\"productName\" value=\"BAD01\" />",
                "</appSettings></configuration>"), StandardCharsets.UTF_8);
        check(LicenseRecoverModernGUIAutoRecovery.detectConfiguredDotNetProduct(
                        yx0102Root.toFile(), yx0102Bin.toFile(), "YX0102") == null,
                "configured .NET ProName is rejected when target DLL does not contain the same identity");
        Files.write(yx0102Root.resolve("Web.config"), Arrays.asList(
                "<configuration><appSettings><add key=\"productName\" value=\"YS01\" />",
                "</appSettings></configuration>"), StandardCharsets.UTF_8);

        writeUtf16Fixture(yx0102Bin.resolve("ITMC.Web.dll"),
                "YS01", "RegeditNew", "NewRegistry", "DoRegistry", "ProName", "SoftVersionID",
                "GetProVersion", "CheckSoftVersionID", "Encrypt3Des", "Decrypt3Des",
                "GetRegisterVersionList");
        LicenseRecoverModernGUIAutoRecovery.Detection yx0102NoKeyDetection =
                LicenseRecoverModernGUIAutoRecovery.detect(yx0102Root.toFile());
        check("YS01".equals(yx0102NoKeyDetection.productName)
                        && "YX0102".equals(LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102NoKeyDetection)),
                "direct YS01/YX0102 native identity does not depend on compatibility database aliases");
        check(LicenseRecoverModernGUIAutoRecovery.readRegisterVersionEvidence(yx0102NoKeyDetection) == null,
                "RegisterVersion.db compatibility evidence fails closed when its key is not proven by target DLL");


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
        check("ITMC.Regedit.dll".equals(LicenseRecoverModernGUIAutoRecovery.selectDotNetRegeditAssembly(
                        chainBin.toFile()).getName()),
                ".NET registration chain prefers uppercase ITMC.Regedit.dll when both assemblies exist");
        Files.delete(chainBin.resolve("ITMC.Regedit.dll"));
        check("itmcRegedit.dll".equals(LicenseRecoverModernGUIAutoRecovery.selectDotNetRegeditAssembly(
                        chainBin.toFile()).getName()),
                ".NET registration chain falls back to lowercase itmcRegedit.dll only when uppercase is absent");

        Path yx302CrossFixture = base.resolve("YX030107-Web.dll");
        writeUtf16Fixture(yx302CrossFixture, "YX030107", "YX0302", "YX030201", "YX030204", "YX030219");
        check("YX0302".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        yx302CrossFixture.toFile(), "YX030107")),
                "YX030107 does not get misclassified as YX0301 when target DLL proves YX0302 family");
        check("YX030201,YX030204,YX030219".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(yx302CrossFixture.toFile(), "YX0302")),
                "YX030107/YX0302 sample derives the target-local product list");

        LicenseRecover.installJavaAuthorizationNetworkGuard();
        check("127.0.0.1".equals(System.getProperty("http.proxyHost"))
                        && "9".equals(System.getProperty("http.proxyPort"))
                        && "127.0.0.1".equals(System.getProperty("https.proxyHost"))
                        && "9".equals(System.getProperty("https.proxyPort")),
                "Java vendor RegisterMain calls are process-isolated before runtime probing/verification");

        String autoRecoverySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/LicenseRecoverModernGUIAutoRecovery.java")), StandardCharsets.UTF_8);
        int preBlockAt = autoRecoverySource.indexOf("installTemporaryDotNetNetworkGuard(helper, log)");
        int generateAt = autoRecoverySource.indexOf("generate.add(\"gencode\")");
        int applyAt = autoRecoverySource.indexOf("apply.add(\"doreg\")");
        int verifyAt = autoRecoverySource.indexOf("verify.add(\"verify\")");
        check(preBlockAt >= 0 && generateAt > preBlockAt && applyAt > generateAt && verifyAt > applyAt,
                ".NET pre-block guard is established before gencode, DoRegistry and CheckReInfo in source order");

        int javaDoRegAt = autoRecoverySource.indexOf("[java-stage] DOREG: OK");
        int javaFreshVerifyAt = autoRecoverySource.indexOf("[java-stage] VERIFY_FRESH: OK");
        int javaSuccessAt = autoRecoverySource.indexOf("Java local authorization was applied through the target-native registration chain");
        check(javaDoRegAt >= 0 && javaFreshVerifyAt > javaDoRegAt && javaSuccessAt > javaFreshVerifyAt,
                "Java final success is emitted only after fresh-JVM target verification, never after DoRegistry alone");
        check(autoRecoverySource.contains("containsAllRegStrTokens(persisted, regStr)")
                        && autoRecoverySource.contains("verifiedAttempt == null"),
                "Java final success requires persisted target RegStr and a successful startup-derived verifier attempt");

        String javaHostSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/LicenseRecoverJavaHost.java")), StandardCharsets.UTF_8);
        int javaVerifyMethodAt = javaHostSource.indexOf("static int verify");
        int javaVerifyOkAt = javaHostSource.indexOf("[java-stage] VERIFY_FRESH: OK", javaVerifyMethodAt);
        int javaResultOkAt = javaHostSource.indexOf("RESULT: OK", javaVerifyMethodAt);
        check(javaHostSource.contains("Boolean.TRUE.equals(unregistered)")
                        && javaHostSource.contains("containsAllCsv(regStr, o.regStr)")
                        && javaHostSource.contains("persisted ProName mismatch")
                        && javaVerifyMethodAt >= 0 && javaVerifyOkAt > javaVerifyMethodAt
                        && javaResultOkAt > javaVerifyOkAt,
                "Java host prints final RESULT: OK only after checkReInfo/RegStr/ProName verification passes");
        check(autoRecoverySource.contains("action=block")
                        && autoRecoverySource.contains("program=\" + helper.getAbsolutePath()")
                        && autoRecoverySource.contains("HTTP_PROXY")
                        && autoRecoverySource.contains("127.0.0.1:9"),
                ".NET native helper has application firewall guard plus proxy defense in depth");

        String unrelatedDotNetConfig =
                "<ROOT><SystemSoft><SoftVersionID>YX030204</SoftVersionID></SystemSoft></ROOT>";
        check(!LicenseRecoverModernGUIAutoRecovery.hasDotNetAuthorizationConfigStructure(unrelatedDotNetConfig),
                ".NET pre-block skips unrelated config.xml without authorization reg/Service structure");
        String registrationDotNetConfig = "<ROOT><reg><regType>3</regType></reg></ROOT>";
        check(LicenseRecoverModernGUIAutoRecovery.hasDotNetAuthorizationConfigStructure(registrationDotNetConfig),
                ".NET pre-block recognizes registration config.xml before adding Service isolation");
        String selfClosingReg = LicenseRecoverModernGUIAutoRecovery.updateLocalLicenseXml(
                "<ROOT><reg/></ROOT>", "TEST", true);
        check(selfClosingReg.contains("<reg>")
                        && selfClosingReg.contains("<regName>TEST</regName>")
                        && selfClosingReg.contains("<Service>http://127.0.0.1:9/Service.asmx</Service>"),
                ".NET config writer expands self-closing reg containers safely");

        String nativeOut = "注册申请号     : A1B2C3D4\n离线授权码     : 001122AABB\n";
        check("A1B2C3D4".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "注册申请号")),
                "native .NET one-click parses target request code");
        check("001122AABB".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "离线授权码")),
                "native .NET one-click parses target authorization code");

        LicenseRecoverModernGUIUiPatchLauncher.OneClickFailurePresentation failureUi =
                LicenseRecoverModernGUIUiPatchLauncher.describeOneClickFailure(
                        "[GENCODE] target-native request-code generation failed; exit=1; output="
                                + "================ ITMC .NET 版 - 方式二：生成离线授权码 ================"
                                + " 产品号: YX0302 [错误] 生成失败: 调用的目标发生了异常。 RESULT: FAILED");
        check("GENCODE".equals(failureUi.stage)
                        && Integer.valueOf(1).equals(failureUi.exitCode)
                        && "YX0302".equals(failureUi.product),
                "one-click failure dialog extracts stage/exit/product from native GENCODE failure");
        check(failureUi.summary.contains("生成离线授权码失败")
                        && failureUi.details.contains("\n产品号:")
                        && failureUi.details.contains("\n[错误]")
                        && failureUi.details.contains("\nRESULT:"),
                "one-click failure dialog converts long native output into readable multiline details");

        Path dsFixture = base.resolve("DS01-Web.dll");
        writeUtf16Fixture(dsFixture, "itmcIEC", "DS0101", "DS0107", "DS0110", "DS0112");
        check("itmcIEC".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        dsFixture.toFile(), null)),
                "one-click detects itmcIEC DS01xx ProName");
        check("DS0101,DS0107,DS0110,DS0112".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                dsFixture.toFile(), "itmcIEC")),
                "one-click derives DS01xx local product list");
        Path dsAsciiFixture = base.resolve("DS01-Web-ascii.dll");
        Files.write(dsAsciiFixture,
                "ProName=itmcIEC;RegStr=DS0101,DS0105,DS0107".getBytes(StandardCharsets.US_ASCII));
        check("itmcIEC".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        dsAsciiFixture.toFile(), "DS0101"))
                        && "DS0101,DS0105,DS0107".equals(
                                LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                        dsAsciiFixture.toFile(), "itmcIEC")),
                ".NET parser extracts product tokens from ASCII/CSV metadata instead of requiring whole-string equality");

        Path gmFixture = base.resolve("GM004-Web.dll");
        writeUtf16Fixture(gmFixture, "GM004", "GM00401", "SoftVersionID", "ProName", "RegStr");
        check("GM004".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        gmFixture.toFile(), "GM00401")),
                "one-click confirms GM004 family from concrete DLL evidence");
        check("GM00401".equals(LicenseRecoverModernGUIAutoRecovery.detectProductList(
                        gmFixture.toFile(), "GM004")),
                "one-click derives GM00401 local product list");

        String knownPlain = "123456{\"UserID\":\"fwq\"}654321";
        String knownCipher = "9ED04E8D57009B0173A79367751FB10CF6348ACA295E0F1D56826AC5E8CC163D";
        check(knownCipher.equals(LicenseRecoverModernGUIAutoRecovery.desEncryptHex(
                        knownPlain, "*ITMCYX0302OK*")),
                "one-click DES/CBC local-license encryption matches known vector");
        check(knownPlain.equals(LicenseRecoverModernGUIAutoRecovery.desDecryptHex(
                        knownCipher, "*ITMCYX0302OK*")),
                "one-click DES/CBC known vector decrypts correctly");

        String regJson = LicenseRecoverModernGUIAutoRecovery.buildRegInfoJson(
                "F000606A59904719", "YX0302", "YX030201,YX030204");
        check(regJson.contains("\"UserID\":\"fwq\""),
                "one-click RegInfo embeds fwq in encrypted local object");
        check(regJson.contains("\"CountDay\":10"),
                "one-click RegInfo preserves vendor CountDay=10 default");
        check(regJson.contains("\"RegID\":\"F000606A59904719\""),
                "one-click RegInfo embeds target RegID");

        String xml = "<ROOT><reg><regType>3</regType><regName>OLD</regName>"
                + "<WebSerUserID>keep-me</WebSerUserID>"
                + "<Service>http://regservice.itmc.cn/Service.asmx</Service></reg></ROOT>";
        String updatedXml = LicenseRecoverModernGUIAutoRecovery.updateLocalLicenseXml(
                xml, "A1B2C3D4", true);
        check(updatedXml.contains("<regType>1</regType>"),
                "one-click writes local regType=1");
        check(updatedXml.contains("<regName>A1B2C3D4</regName>"),
                "one-click replaces regName");
        check(updatedXml.contains("<WebSerUserID>keep-me</WebSerUserID>"),
                "one-click does not overwrite outer WebSerUserID");
        check(updatedXml.contains("<Service>http://127.0.0.1:9/Service.asmx</Service>"),
                "one-click blocks residual registration service when requested");

        Path parentApp = base.resolve("parentApp");
        Path parentBin = parentApp.resolve("bin");
        Path ordinaryChild = parentApp.resolve("ordinaryChild");
        Files.createDirectories(parentBin);
        Files.createDirectories(ordinaryChild);
        Files.write(parentBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(parentBin.resolve("itmcRegedit.dll"), new byte[]{1});
        check(AppDetector.detect(parentApp.toFile()).type == AppInfo.Type.DOTNET,
                "single-app detection recognizes app root");
        check(AppDetector.detect(parentBin.toFile()).type == AppInfo.Type.DOTNET,
                "single-app detection recognizes standard bin directory");
        check(AppDetector.detect(ordinaryChild.toFile()).type == AppInfo.Type.UNKNOWN,
                "single-app detection does not cross unrelated child boundary");
        check(AppDetector.detectLocal(ordinaryChild.toFile()).type == AppInfo.Type.UNKNOWN,
                "batch local detection does not walk into parent app");

        Path source = base.resolve("source.bin");
        Path backup = base.resolve("source.bak");
        Files.write(source, new byte[]{9, 8, 7});
        SafetyBackup.requireCopy(source, backup, System.out::print);
        check(Files.size(source) == Files.size(backup), "mandatory backup size validation");

        List<String> dryPatch = Arrays.asList("java", "-cp", "x", "LicenseRecover",
                "--remove-net", javaRoot.toString(), "--dry-run");
        List<String> normalized = ProcessRunner.normalizeCommand(dryPatch, System.out::print);
        check(normalized.contains("--scan-net") && !normalized.contains("--remove-net"),
                "Java way-3 dry-run becomes scan-only");

        List<String> productAlias = Arrays.asList("java", "-jar", "/tmp/LicenseRecover.jar",
                "--gencode", dotnetRoot.toString(), "--product", "YX030204");
        List<String> productNormalized = ProcessRunner.normalizeCommand(productAlias, System.out::print);
        check(productNormalized.contains("-p") && !productNormalized.contains("--product"),
                "CLI product override is normalized to -p");

        String javaExe = System.getProperty("java.home") + File.separator + "bin"
                + File.separator + "java";
        OperationResult process = ProcessRunner.run(Arrays.asList(javaExe, "-version"),
                System.out::print, 10);
        check(process.isSuccess(), "process runner executes and returns success");

        check(LicenseRecoverModernGUIGitHubUpdateService.compareVersions("1.1.1", "1.1.0") > 0,
                "GitHub updater semantic version comparison");
        check(LicenseRecoverModernGUIGitHubUpdateService.compareVersions("v1.1.1", "1.1.1") == 0,
                "GitHub updater normalizes v-prefix");
        String checksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String sums = checksum + "  LicenseRecover-latest.zip\n"
                + checksum + "  LicenseRecover-update.zip\n";
        check(checksum.equals(LicenseRecoverModernGUIGitHubUpdateService.parseChecksum(sums)),
                "GitHub updater parses portable checksum");
        check(checksum.equals(LicenseRecoverModernGUIGitHubUpdateService.parseChecksum(
                sums, "LicenseRecover-update.zip")),
                "GitHub updater parses slim update checksum");
        String releaseJson = "{\"assets\":[{\"name\":\"LicenseRecover-update.zip\",\"size\":4416823},"
                + "{\"name\":\"LicenseRecover-latest.zip\",\"size\":53207200}]}";
        check(LicenseRecoverModernGUIGitHubUpdateService.parseAssetSize(
                        releaseJson, "LicenseRecover-update.zip") == 4416823L,
                "GitHub updater reads release asset size for real progress");
        check(LicenseRecoverModernGUIGitHubUpdateService.parseContentRangeTotal(
                        "bytes 1024-2047/4416823") == 4416823L,
                "GitHub updater parses resumable download total");
        check(LicenseRecoverModernGUIGitHubUpdateService.parseProxy("http://127.0.0.1:7890") != null,
                "GitHub updater accepts explicit HTTPS_PROXY-style HTTP proxy");
        check(LicenseRecoverModernGUIGitHubUpdateService.parseProxy("http://user:pass@127.0.0.1:7890") == null,
                "GitHub updater does not silently mishandle authenticated proxy URLs");
        check(LicenseRecoverModernGUIGitHubUpdateService.isRedirectCode(302)
                        && LicenseRecoverModernGUIGitHubUpdateService.isRedirectCode(307)
                        && !LicenseRecoverModernGUIGitHubUpdateService.isRedirectCode(200),
                "GitHub updater recognizes manual HTTPS redirect statuses");

        Path updateInstall = base.resolve("update-install");
        Files.createDirectories(updateInstall);
        Files.write(updateInstall.resolve("VERSION.txt"), Arrays.asList("1.1.0"), StandardCharsets.UTF_8);
        Files.write(updateInstall.resolve("runtime.txt"), Arrays.asList("old"), StandardCharsets.UTF_8);
        Path embeddedJava = updateInstall.resolve("jre/bin/java.exe");
        Files.createDirectories(embeddedJava.getParent());
        Files.write(embeddedJava, Arrays.asList("embedded-jre"), StandardCharsets.UTF_8);
        Path updateZip = base.resolve("update.zip");
        ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(updateZip)));
        try {
            writeZipEntry(zout, "VERSION.txt", "1.1.1\n");
            writeZipEntry(zout, "runtime.txt", "new\n");
            writeZipEntry(zout, "nested/new.txt", "created\n");
        } finally { zout.close(); }
        LicenseRecoverModernGUIUpdateInstaller.applyUpdate(updateZip.toFile(), updateInstall.toFile());
        check("1.1.1".equals(new String(Files.readAllBytes(updateInstall.resolve("VERSION.txt")),
                        StandardCharsets.UTF_8).trim()), "updater replaces VERSION.txt");
        check("new".equals(new String(Files.readAllBytes(updateInstall.resolve("runtime.txt")),
                        StandardCharsets.UTF_8).trim()), "updater overwrites runtime files");
        check(Files.isRegularFile(updateInstall.resolve("nested/new.txt")),
                "updater adds new runtime files");
        check("embedded-jre".equals(new String(Files.readAllBytes(embeddedJava),
                        StandardCharsets.UTF_8).trim()),
                "slim updater preserves existing embedded JRE");

        Path maliciousZip = base.resolve("malicious.zip");
        zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(maliciousZip)));
        try { writeZipEntry(zout, "../escape.txt", "blocked\n"); }
        finally { zout.close(); }
        boolean blocked = false;
        try {
            LicenseRecoverModernGUIUpdateInstaller.applyUpdate(
                    maliciousZip.toFile(), updateInstall.toFile());
        } catch (IOException expected) { blocked = true; }
        check(blocked && !Files.exists(base.resolve("escape.txt")),
                "updater blocks zip-slip entries");

        Path legacyDotNetRoot = base.resolve("dotnet-DS0101");
        Path legacyDotNetBin = legacyDotNetRoot.resolve("bin");
        Files.createDirectories(legacyDotNetBin);
        Files.write(legacyDotNetRoot.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS0101</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        Files.write(legacyDotNetBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(legacyDotNetBin.resolve("itmcRegedit.dll"), new byte[]{1});
        Files.write(legacyDotNetBin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        LicenseRecoverModernGUIAutoRecovery.Detection legacyDotNetDetection =
                LicenseRecoverModernGUIAutoRecovery.detect(legacyDotNetRoot.toFile());
        check(legacyDotNetDetection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY,
                "DS01xx remains legacy even when an uppercase compatibility ITMC.Regedit.dll is present");

        Path toolCpDir = Files.createTempDirectory("lrc-toolcp-");
        Path legacyCore = toolCpDir.resolve("LicenseRecover.jar");
        Path overlayCore = toolCpDir.resolve("LicenseRecoverOverlay.jar");
        Files.write(legacyCore, new byte[]{0});
        String toolCpWithoutOverlay = LicenseRecover.toolRuntimeClasspath(toolCpDir.toFile());
        check(toolCpWithoutOverlay.equals(legacyCore.toFile().getAbsolutePath()),
                "secondary JVM classpath falls back to legacy core when overlay is absent");
        Files.write(overlayCore, new byte[]{0});
        String toolCpWithOverlay = LicenseRecover.toolRuntimeClasspath(toolCpDir.toFile());
        check(toolCpWithOverlay.startsWith(overlayCore.toFile().getAbsolutePath() + File.pathSeparator)
                        && toolCpWithOverlay.endsWith(legacyCore.toFile().getAbsolutePath()),
                "secondary JVM classpath must load LicenseRecoverOverlay.jar before LicenseRecover.jar");
        Path targetRuntime = Files.createTempDirectory("lrc-target-lib-");
        String recoveryCp = LicenseRecoverModernGUIAutoRecovery.buildJavaRecoveryClasspath(
                toolCpDir.toFile(), targetRuntime.toFile());
        check(recoveryCp.equals(toolCpWithOverlay),
                "Java recovery helper system classpath stays tool-only; target jars are isolated child-first");
        check(LicenseRecoverJavaHost.instantiateRegisterMainCompatible(
                        ModernRegisterMain3.class, "QT30103", "native-token", "D:/app/") instanceof ModernRegisterMain3,
                "Java native host supports target 3-arg RegisterMain constructor");
        check(LicenseRecoverJavaHost.instantiateRegisterMainCompatible(
                        LegacyRegisterMain2.class, "DS24", "ignored", "D:/app/WEB-INF/lib/") instanceof LegacyRegisterMain2,
                "Java native host supports target 2-arg RegisterMain constructor");
        check(LicenseRecoverJavaHost.instantiateRegisterMainCompatible(
                        LegacyRegisterMain1.class, "DS50109", "ignored", "D:/app/") instanceof LegacyRegisterMain1,
                "Java native host supports target 1-arg RegisterMain constructor");
        check(LicenseRecoverJavaHost.containsCsv("DS2406,DS2407", "ds2406"),
                "Java native fresh verifier checks exact current SoftVersionID token");

        String autoSource230 = new String(Files.readAllBytes(Paths.get("src/main/java/LicenseRecoverModernGUIAutoRecovery.java")), StandardCharsets.UTF_8);
        check(autoSource230.contains("LicenseRecover.NET.AspNetHost.exe"), "lowercase .NET chain uses ASP.NET host helper");
        check(autoSource230.contains("findDotNetAspNetHostHelper"), "ASP.NET host helper lookup is present");
        String hostSource230 = new String(Files.readAllBytes(Paths.get("src/dotnet/LicenseRecover.AspNetHost.cs")), StandardCharsets.UTF_8);
        String bridgeSource237 = new String(Files.readAllBytes(Paths.get("src/dotnet/LicenseRecover.AspNetBridge.cs")), StandardCharsets.UTF_8);
        check(hostSource230.contains("ApplicationManager.GetApplicationManager()"),
                "ASP.NET host uses the real System.Web ApplicationManager");
        check(hostSource230.contains("CreateObjectWithDefaultAppHostAndAppId"),
                "ASP.NET host creates a real hosted application AppDomain");
        check(hostSource230.contains("LicenseRecover.NET.AspNetBridge.dll")
                        && hostSource230.contains("EnsureBridgeInTargetBin"),
                "ASP.NET host stages the internal bridge safely into target bin");
        check(hostSource230.contains("ShutdownApplication(appId)"),
                "ASP.NET host shuts down the temporary hosted application");
        check(bridgeSource237.contains("HostingEnvironment.IsHosted")
                        && bridgeSource237.contains("HttpRuntime.AppDomainAppPath"),
                "ASP.NET bridge verifies real hosted physical/runtime paths");
        check(bridgeSource237.contains("SimpleWorkerRequest")
                        && bridgeSource237.contains("ValidateMapPaths(HttpContext.Current, appRoot)"),
                "ASP.NET bridge creates request context only inside the real hosted AppDomain");
        check(bridgeSource237.contains("Assembly.LoadFrom(helper)"),
                "ASP.NET bridge loads the existing helper assembly");
        check(bridgeSource237.contains("ParseArgs") && bridgeSource237.contains("RunDirect"),
                "ASP.NET bridge dispatches helper commands directly inside the hosted AppDomain");
        check(!bridgeSource237.contains("assembly.EntryPoint"),
                "ASP.NET bridge does not re-enter helper child-AppDomain dispatch through EntryPoint");
        check(bridgeSource237.contains("helperDispatch=RunDirect/hosted-AppDomain"),
                "ASP.NET bridge emits hosted-AppDomain dispatch diagnostics");
        check(hostSource230.contains("ResolveAppRoot(runtimeDir)"), "ASP.NET host resolves target web root from runtime dir");
        String uiPatch230 = new String(Files.readAllBytes(Paths.get("src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java")), StandardCharsets.UTF_8);
        check(uiPatch230.contains("appendPersistentOneClickLog"), "one-click overlay writes persistent diagnostics");
        System.out.println("ALL REFACTOR SMOKE TESTS PASSED");
    }

    private static void writeRegisterVersionFixture(Path path, String key, String version,
                                                    String parentProduct, String... parentVersions)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String parentVersion : parentVersions) {
            writeBsonString(out, "versionID", tripleDesEncrypt(version, key));
            writeBsonString(out, "parentProID", tripleDesEncrypt(parentProduct, key));
            writeBsonString(out, "parentVersionID", tripleDesEncrypt(parentVersion, key));
            out.write(0);
        }
        Files.write(path, out.toByteArray());
    }

    private static String tripleDesEncrypt(String value, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "DESede"));
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void writeBsonString(ByteArrayOutputStream out, String name, String value)
            throws IOException {
        out.write(0x02);
        out.write(name.getBytes(StandardCharsets.US_ASCII));
        out.write(0);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeLe32(out, bytes.length + 1);
        out.write(bytes);
        out.write(0);
    }

    private static void writeLe32(ByteArrayOutputStream out, int value) {
        out.write(value & 255);
        out.write((value >>> 8) & 255);
        out.write((value >>> 16) & 255);
        out.write((value >>> 24) & 255);
    }

    private static void writeUtf16Fixture(Path path, String... values) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            text.append(value).append('\u0001');
        }
        Files.write(path, text.toString().getBytes(StandardCharsets.UTF_16LE));
    }

    private static void writeZipEntry(ZipOutputStream out, String name, String value)
            throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
