import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only Java authorization plan used by both the single-app and batch GUI.
 *
 * Important: this class must remain independent from LicenseRecover.class because
 * the GUI process intentionally does not load the selected application's ITMCReg
 * classes into its own classpath. The actual recovery still runs in a child JVM
 * with the application's WEB-INF/lib classpath (and the existing Virbox unpack
 * compatibility path). Keeping the preview file-only prevents the first Java
 * target in a batch from failing with NoClassDefFoundError before execution.
 */
public final class LicenseRecoverModernGUIJavaPlan {
    private static final Pattern XML_ELEMENT_TEMPLATE = Pattern.compile("a");

    public final boolean detected;
    public final File appRoot;
    public final File libDir;
    public final File regJar;
    public final String softVersionId;
    public final String generation;
    /** Vendor local-registration family, e.g. DS501 / YX0305. */
    public final String authorizationFamily;
    /** Concrete id used by application startup RegisterMain.checkReInfo(). */
    public final String runtimeProductId;
    /** Backward-compatible alias for direct-rebuild ProName. */
    public final String productName;
    public final String regStr;
    public final String configTargets;
    public final String verificationPlan;
    public final boolean rootConfigStyle;
    public final boolean packedRegistrationJar;
    /** True only when all identifiers needed for automatic write-back are confirmed. */
    public final boolean automaticRecoveryReady;
    /** Human-readable explanation shown by single-app and batch UI. */
    public final String recoveryReadiness;

    private LicenseRecoverModernGUIJavaPlan(boolean detected, File appRoot, File libDir, File regJar,
                                            String softVersionId, String generation, String authorizationFamily,
                                            String runtimeProductId, String regStr, String configTargets,
                                            String verificationPlan, boolean rootConfigStyle,
                                            boolean packedRegistrationJar, boolean automaticRecoveryReady,
                                            String recoveryReadiness) {
        this.detected = detected;
        this.appRoot = appRoot;
        this.libDir = libDir;
        this.regJar = regJar;
        this.softVersionId = softVersionId;
        this.generation = generation;
        this.authorizationFamily = authorizationFamily;
        this.runtimeProductId = runtimeProductId;
        this.productName = runtimeProductId;
        this.regStr = regStr;
        this.configTargets = configTargets;
        this.verificationPlan = verificationPlan;
        this.rootConfigStyle = rootConfigStyle;
        this.packedRegistrationJar = packedRegistrationJar;
        this.automaticRecoveryReady = automaticRecoveryReady;
        this.recoveryReadiness = recoveryReadiness;
    }

    public static LicenseRecoverModernGUIJavaPlan inspect(File selected) {
        LicenseRecoverModernGUIAutoRecovery.Detection detection =
                LicenseRecoverModernGUIAutoRecovery.detect(selected);
        if (detection.kind != LicenseRecoverModernGUIAutoRecovery.Kind.JAVA
                || detection.appRoot == null || detection.runtimeDir == null) {
            return unknown();
        }

        File root = detection.appRoot.getAbsoluteFile();
        File lib = detection.runtimeDir.getAbsoluteFile();
        String soft = readSoftId(root);
        if (blank(soft)) soft = detection.versionId;

        File dataConfig = new File(root, "data" + File.separator + "config.xml");
        File classesConfig = new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml");
        boolean newStyle = !blank(readElement(dataConfig, "regInfo"));
        boolean rootConfig = dataConfig.isFile() || classesConfig.isFile();

        String upper = soft == null ? "" : soft.toUpperCase(Locale.ROOT);
        LegacyJavaRegistrationMetadata.Mapping directoryMapping =
                LegacyJavaRegistrationMetadata.inspect(root, soft);
        boolean requiresDirectoryMapping =
                LegacyJavaRegistrationMetadata.requiresDirectoryMapping(root, soft);
        String confirmedClassesFamily = confirmedClassesAuthorizationFamily(root, soft);
        String generation;
        if (newStyle) {
            generation = "QT30xxx / data-config";
        } else if (classesConfig.isFile()) {
            if ("QT401".equals(confirmedClassesFamily)) generation = "QT401 / classes-config";
            else if ("QT100101".equals(confirmedClassesFamily)) generation = "QT1001系列 / classes-config";
            else if (upper.startsWith("XMT01")) generation = "XMT / classes-config";
            else if (upper.startsWith("DS501")) generation = "DS501 / classes-config";
            else if (upper.startsWith("YX0305")) generation = "YX0305 / classes-config";
            else generation = "通用 / classes-config";
        } else if (dataConfig.isFile()) {
            generation = "DS2406".equals(upper)
                    ? "DS24 / data-config"
                    : (upper.matches("DS28\\d{2}") ? "DS28 / data-config" : "YT/兼容根配置 / data-config");
        } else if (relative(root, lib).toLowerCase(Locale.ROOT)
                .contains("web-inf" + File.separator + "web-inf")) {
            generation = "经典 Java / nested WEB-INF";
        } else {
            generation = "经典 Java / WEB-INF/lib";
        }

        if (directoryMapping != null) generation = "经典 Java / 应用目录注册映射";
        String dataSoft = readElement(dataConfig, "SoftVersionID");
        String dataRegInfo = normalizeCsv(readElement(dataConfig, "regInfo"));
        String classesRegInfo = normalizeCsv(readElement(classesConfig, "regInfo"));
        boolean directDataIdentity = newStyle && !blank(soft) && !blank(dataSoft)
                && soft.trim().equalsIgnoreCase(dataSoft.trim()) && dataRegInfo != null;

        // No product-family/runtime-id guessing here. Executable identity must be
        // supported by target-directory configuration and/or target-owned bytecode evidence.
        String binaryFamily = confirmedBinaryAuthorizationFamily(
                root, lib, soft, newStyle, classesConfig.isFile());
        String family = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : binaryFamily));
        String binaryRuntime = confirmedBinaryRuntimeProduct(
                root, lib, soft, binaryFamily, newStyle, classesConfig.isFile());
        String configDrivenRuntime = confirmedConfigDrivenRuntimeProduct(root, soft, family);
        String startupRuntime = blank(binaryRuntime) && blank(configDrivenRuntime)
                ? confirmedStartupRuntimeProduct(root, lib, soft, family) : null;
        String confirmedClassesRuntime = ("QT401".equalsIgnoreCase(confirmedClassesFamily)
                || "QT100101".equalsIgnoreCase(confirmedClassesFamily))
                ? confirmedClassesFamily : null;
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (directDataIdentity ? soft.trim()
                : (!blank(configDrivenRuntime) ? configDrivenRuntime
                : (!blank(binaryRuntime) ? binaryRuntime
                : (!blank(startupRuntime) ? startupRuntime : confirmedClassesRuntime))));
        File jar = findRegJar(lib);
        boolean packed = jar != null && isVirboxPackedJar(jar);
        String qt401PrimaryRegStr = confirmedQt401PrimaryRegStr(root, soft, family, runtimeProduct);
        String configDrivenPrimaryRegStr = confirmedConfigDrivenPrimaryRegStr(
                root, soft, family, configDrivenRuntime);
        String dataDrivenPrimaryRegStr = confirmedDataDrivenPrimaryRegStr(
                root, soft, family, runtimeProduct);
        String recoveredLocalRegStr = null;
        String products;
        if (directoryMapping != null) {
            products = directoryMapping.productMainNum;
        } else if (dataRegInfo != null) {
            products = dataRegInfo;
        } else if (classesRegInfo != null) {
            products = classesRegInfo;
        } else if (!blank(qt401PrimaryRegStr)) {
            products = qt401PrimaryRegStr;
        } else if (!blank(configDrivenPrimaryRegStr)) {
            products = configDrivenPrimaryRegStr;
        } else if (!blank(dataDrivenPrimaryRegStr)) {
            products = dataDrivenPrimaryRegStr;
        } else {
            recoveredLocalRegStr = blank(runtimeProduct)
                    ? null : ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
            products = recoveredLocalRegStr;
        }

        boolean binaryIdentity = !blank(binaryFamily)
                && (!blank(binaryRuntime) || !blank(configDrivenRuntime) || !blank(startupRuntime));
        boolean directoryIdentity = directoryMapping != null
                || directDataIdentity || !blank(confirmedClassesFamily) || binaryIdentity;
        boolean directoryRegStr = directoryMapping != null
                || dataRegInfo != null || classesRegInfo != null
                || !blank(qt401PrimaryRegStr) || !blank(configDrivenPrimaryRegStr) || !blank(dataDrivenPrimaryRegStr)
                || recoveredLocalRegStr != null;
        boolean runtimeRegStrProbe = !directoryRegStr && jar != null
                && directoryIdentity && !blank(runtimeProduct);

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
            readiness = "注册ID仅能由旧兼容规则推测，缺少目标目录二进制/配置证据";
        } else if (blank(family) || "未确认".equals(family) || blank(runtimeProduct)) {
            ready = false;
            readiness = "目标软件目录未确认授权族/运行注册ID";
        } else if (!directoryRegStr || blank(products)) {
            if (runtimeRegStrProbe) {
                readiness = "可安全自动恢复（注册ID来自目标目录；RegStr执行时由目标RegisterMain.getRegInfo()读取）";
            } else {
                ready = false;
                readiness = "目标软件目录未声明 RegStr，且无法使用目标注册组件只读获取";
            }
        }
        String libConfig = relative(root, new File(lib, "config.xml"));
        boolean projectClasspathBase = usesProjectClasspathRegistrationBase(root);
        String targets;
        String verify;
        if (projectClasspathBase) {
            targets = relative(root, classesConfig);
            verify = "RegisterMain.checkReInfo(): WEB-INF/classes (ProjectSourcesPath)";
        } else {
            targets = libConfig;
            if (rootConfig) targets += " + config.xml(webapp根)";
            verify = rootConfig
                    ? "RegisterMain.checkReInfo(): lib + webapp根"
                    : "RegisterMain.checkReInfo(): lib";
        }

        return new LicenseRecoverModernGUIJavaPlan(true, root, lib, jar,
                soft, generation, family, runtimeProduct, products, targets, verify, rootConfig, packed,
                ready, readiness);
    }

    public String regStrSummary() {
        if (blank(regStr)) return automaticRecoveryReady ? "目标组件动态读取" : "未静态声明";
        String[] parts = regStr.split(",");
        if (parts.length <= 4) return regStr;
        StringBuilder out = new StringBuilder();
        out.append(parts.length).append(" 项: ");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            if (i > 0) out.append(',');
            out.append(parts[i].trim());
        }
        out.append(" …");
        return out.toString();
    }

    public String registrationJarSummary() {
        if (regJar == null) return "ITMCReg*.jar 未定位";
        return regJar.getName() + (packedRegistrationJar ? "（Virbox，执行时临时脱壳）" : "");
    }

    public String logSummary() {
        if (!detected) return "[java-plan] Java 授权计划未识别\n";
        StringBuilder out = new StringBuilder();
        out.append("[java-plan] generation=").append(generation).append('\n');
        out.append("[java-plan] SoftVersionID=").append(value(softVersionId))
                .append(" AuthorizationFamily=").append(value(authorizationFamily))
                .append(" RuntimeProductID/ProName=").append(value(runtimeProductId)).append('\n');
        out.append("[java-plan] RegStr=").append(value(regStr)).append('\n');
        out.append("[java-plan] config targets=").append(configTargets).append('\n');
        out.append("[java-plan] registration jar=").append(registrationJarSummary()).append('\n');
        out.append("[java-plan] verify=").append(verificationPlan).append('\n');
        out.append("[java-plan] automatic recovery=")
                .append(automaticRecoveryReady ? "READY" : "BLOCKED")
                .append(" (").append(recoveryReadiness).append(")\n");
        return out.toString();
    }

    private static String readSoftId(File root) {
        if (root == null) return null;
        try {
            File yml = new File(root, "systemConfig.yml");
            if (yml.isFile()) {
                for (String raw : Files.readAllLines(yml.toPath(), StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (!line.toLowerCase(Locale.ROOT).contains("versionid")) continue;
                    int split = line.indexOf(':');
                    if (split < 0) split = line.indexOf('=');
                    if (split > 0) {
                        String value = line.substring(split + 1).trim();
                        if (!value.isEmpty()) return value;
                    }
                }
            }
        } catch (Exception ignore) { }

        String data = readElement(new File(root, "data" + File.separator + "config.xml"), "SoftVersionID");
        if (!blank(data)) return data.trim();
        String classes = readElement(new File(root, "WEB-INF" + File.separator + "classes"
                + File.separator + "config.xml"), "SoftVersionID");
        return blank(classes) ? null : classes.trim();
    }

    private static String resolveRegStr(File root, String runtimeProduct, File lib) {
        String data = normalizeCsv(readElement(new File(root, "data" + File.separator + "config.xml"), "regInfo"));
        if (data != null) return data;
        String classes = normalizeCsv(readElement(new File(root, "WEB-INF" + File.separator
                + "classes" + File.separator + "config.xml"), "regInfo"));
        if (classes != null) return classes;
        return blank(runtimeProduct) ? null : ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
    }

    static String confirmedClassesAuthorizationFamily(File root, String softId) {
        if (root == null || blank(softId)) return null;
        String id = softId.toUpperCase(Locale.ROOT);
        if (id.matches("QT401\\d{2}")) {
            File config1 = new File(root, "WEB-INF" + File.separator + "classes"
                    + File.separator + "config1.xml");
            String family = readElement(config1, "SoftVersionID");
            if ("QT401".equalsIgnoreCase(family)) return "QT401";
        }
        File config = new File(root, "WEB-INF" + File.separator + "classes"
                + File.separator + "config.xml");
        String concrete = readElement(config, "SoftVersionID");
        if ("QT100101".equals(id) && "QT100101".equalsIgnoreCase(concrete)) return "QT100101";
        if (!blank(concrete) && id.equalsIgnoreCase(concrete.trim())) {
            if (id.startsWith("DS501") && hasEnumeratedClassesFamily(config, "DS501", id)) return "DS501";
            if (id.startsWith("YX0305") && hasEnumeratedClassesFamily(config, "YX0305", id)) return "YX0305";
        }
        return null;
    }

    static boolean hasEnumeratedClassesFamily(File config, String family, String concrete) {
        if (config == null || !config.isFile() || blank(family) || blank(concrete)) return false;
        try {
            String text = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile(
                    "(?is)<System\\b[^>]*\\bid\\s*=\\s*([\"'])([^\"']+)\\1");
            Matcher matcher = pattern.matcher(text);
            String familyUpper = family.trim().toUpperCase(Locale.ROOT);
            String concreteUpper = concrete.trim().toUpperCase(Locale.ROOT);
            LinkedHashSet<String> siblingIds = new LinkedHashSet<String>();
            boolean containsConcrete = false;
            while (matcher.find()) {
                String value = matcher.group(2) == null ? "" : matcher.group(2).trim().toUpperCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                if (value.equals(concreteUpper)) containsConcrete = true;
                if (value.startsWith(familyUpper)) siblingIds.add(value);
            }
            return containsConcrete && siblingIds.size() >= 2;
        } catch (Throwable ignore) {
            return false;
        }
    }

    static String authorizationFamilyFor(String softId, boolean newStyle, boolean classesStyle) {
        if (blank(softId)) return newStyle ? "QT30xxx" : (classesStyle ? "未确认" : "QT1001");
        String id = softId.toUpperCase(Locale.ROOT);
        if ("QT100101".equals(id)) return "QT100101";
        if ("DS2406".equals(id)) return "DS24";
        if (id.startsWith("DS501")) return "DS501";
        if (id.startsWith("YX0305")) return "YX0305";
        if (id.startsWith("XMT01")) return "XMT01";
        if (id.matches("DS28\\d{2}")) return "DS28";
        if (newStyle) return softId.trim();
        if ("YT00128".equals(id) || "YT00127".equals(id) || "YT00129".equals(id)
                || "YT00139".equals(id) || "YT00132".equals(id) || "YT00141".equals(id)
                || "YT00126".equals(id) || "YT00154".equals(id) || "BKSM4".equals(id)) return "QT04";
        if (classesStyle) return "未确认";
        return "QT1001";
    }

    static String runtimeProductFor(String softId) {
        if (blank(softId)) return "QT1001";
        String id = softId.toUpperCase(Locale.ROOT);
        if ("QT100101".equals(id)) return "QT100101";
        if ("DS2406".equals(id)) return "DS24";
        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();
        if (id.matches("DS28\\d{2}")) return "DS28";
        if (id.startsWith("XMT01")) return "XMT01";
        if ("YT00128".equals(id) || "YT00127".equals(id) || "YT00129".equals(id)
                || "YT00139".equals(id) || "YT00132".equals(id) || "YT00141".equals(id)
                || "YT00126".equals(id) || "YT00154".equals(id) || "BKSM4".equals(id)) return "QT04";
        return "QT1001";
    }

    static String confirmedBinaryAuthorizationFamily(File root, File lib, String softId,
                                                       boolean newStyle, boolean classesStyle) {
        if (blank(softId)) return null;
        String candidate = authorizationFamilyFor(softId, newStyle, classesStyle);
        if (blank(candidate) || "未确认".equals(candidate)) return null;
        String id = softId.trim().toUpperCase(Locale.ROOT);
        String c = candidate.trim().toUpperCase(Locale.ROOT);
        if (!id.equals(c) && !id.startsWith(c)) return null;
        if (newStyle && id.equals(c)) return candidate.trim();
        return hasDirectoryBinaryToken(root, lib, candidate) ? candidate.trim() : null;
    }

    static String confirmedBinaryRuntimeProduct(File root, File lib, String softId,
                                                String confirmedFamily,
                                                boolean newStyle, boolean classesStyle) {
        if (blank(softId) || blank(confirmedFamily)) return null;
        String candidate = runtimeProductFor(softId);
        if (blank(candidate)) return null;
        String id = softId.trim().toUpperCase(Locale.ROOT);
        String c = candidate.trim().toUpperCase(Locale.ROOT);
        if (!id.equals(c) && !id.startsWith(c)) return null;
        if (candidate.equalsIgnoreCase(confirmedFamily)) return confirmedFamily;
        return hasDirectoryBinaryToken(root, lib, candidate) ? candidate.trim() : null;
    }

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

    static String confirmedQt401PrimaryRegStr(File root, String softId,
                                               String confirmedFamily, String runtimeProduct) {
        if (root == null || blank(softId) || blank(confirmedFamily) || blank(runtimeProduct)) return null;
        String concrete = softId.trim();
        if (!concrete.toUpperCase(Locale.ROOT).matches("QT401\\d{2}")) return null;
        if (!"QT401".equalsIgnoreCase(confirmedFamily.trim())
                || !"QT401".equalsIgnoreCase(runtimeProduct.trim())) return null;
        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;
        File config1 = new File(classes, "config1.xml");
        String family = readElement(config1, "SoftVersionID");
        if (blank(family) || !"QT401".equalsIgnoreCase(family.trim())) return null;
        try {
            String text = new String(Files.readAllBytes(config1.toPath()), StandardCharsets.UTF_8);
            if (!text.contains("id=\"" + concrete + "\"") && !text.contains("id='" + concrete + "'")) return null;
        } catch (Exception ex) { return null; }
        File systemInfo = new File(classes, "com" + File.separator + "ruoyi" + File.separator + "web"
                + File.separator + "register" + File.separator + "utils" + File.separator + "SystemInfo.class");
        File initService = new File(classes, "com" + File.separator + "ruoyi" + File.separator + "web"
                + File.separator + "controller" + File.separator + "listener" + File.separator + "service"
                + File.separator + "SystemInitService.class");
        File listener = new File(classes, "com" + File.separator + "ruoyi" + File.separator + "web"
                + File.separator + "register" + File.separator + "service" + File.separator + "RegisterListener.class");
        if (!classFileContainsAll(systemInfo, "config1.xml", "SystemSoft", "SoftVersionID", "registerId")) return null;
        if (!classFileContainsAll(initService, "getRegisterMain", "RegeditNew", "itmcsoft",
                "itmc/regedit/RegisterMain")) return null;
        if (!classFileContainsAll(listener, "org/springframework/util/ClassUtils", "getDefaultClassLoader",
                "java/lang/ClassLoader", "getResource", "java/net/URL", "getPath",
                "getRegisterMain", "getRegInfo", "getRegStr", "ClassPid", "contains")) return null;
        return concrete;
    }

    static String confirmedConfigDrivenRuntimeProduct(File root, String softId, String confirmedFamily) {
        if (root == null || blank(softId) || blank(confirmedFamily)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        if (!concrete.toUpperCase(Locale.ROOT).startsWith(family.toUpperCase(Locale.ROOT))) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");

        String productAllPrimary = confirmedProductAllNumPrimaryRuntime(
                root, concrete, family, configured);
        if (!blank(productAllPrimary)) return productAllPrimary;

        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File systemInfo = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "register" + File.separator + "utils" + File.separator + "SystemInfo.class");
        File listener = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "register" + File.separator + "service" + File.separator + "RegisterListener.class");
        if (classFileContainsAll(systemInfo, "config.xml", "SystemSoft", "registerId")) {
            boolean directGetter = classFileContainsAll(listener,
                    "com/itmc/register/utils/SystemInfo", "registerId",
                    "itmc/regedit/RegisterMain", "getRegStr", "versionID", "contains");
            boolean jsonGetter = classFileContainsAll(listener,
                    "com/itmc/register/utils/SystemInfo", "registerId",
                    "itmc/regedit/RegisterMain", "checkReInfo", "getRegInfo",
                    "com/alibaba/fastjson/JSONObject", "toJSONString", "parseObject",
                    "regStr", "versionID", "contains");
            if (directGetter || jsonGetter) return concrete;
        }

        if (confirmedProductAllNumRuntimeProduct(classes)) return concrete;
        return null;
    }

    static String confirmedConfigDrivenPrimaryRegStr(File root, String softId,
                                                      String confirmedFamily,
                                                      String confirmedRuntimeProduct) {
        if (root == null || blank(softId) || blank(confirmedFamily)
                || blank(confirmedRuntimeProduct)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        String runtime = confirmedRuntimeProduct.trim();
        if (!concrete.equalsIgnoreCase(runtime)) return null;
        if (!concrete.toUpperCase(Locale.ROOT).startsWith(family.toUpperCase(Locale.ROOT))) return null;

        String ymlVersion = readSystemConfigVersionId(root);
        if (blank(ymlVersion) || !concrete.equalsIgnoreCase(ymlVersion.trim())) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File systemInfo = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "register" + File.separator + "utils" + File.separator + "SystemInfo.class");
        File registerContant = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "register" + File.separator + "utils" + File.separator + "RegisterContant.class");
        File listener = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "register" + File.separator + "service" + File.separator + "RegisterListener.class");

        if (!classFileContainsAll(systemInfo, "config.xml", "SystemSoft", "registerId")) return null;
        if (!classFileContainsAll(registerContant, "global.system.VersionID", "versionID",
                "java/util/Properties", "getProperty")) return null;
        if (!classFileContainsAll(listener, "com/itmc/register/utils/SystemInfo", "registerId",
                "itmc/regedit/RegisterMain", "getRegStr",
                "com/itmc/register/utils/RegisterContant", "versionID", "contains")) return null;
        return concrete;
    }

    static String confirmedDataDrivenPrimaryRegStr(File root, String softId,
                                                    String confirmedFamily,
                                                    String confirmedRuntimeProduct) {
        if (root == null || blank(softId) || blank(confirmedFamily)
                || blank(confirmedRuntimeProduct)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        String runtime = confirmedRuntimeProduct.trim();
        if (!family.equalsIgnoreCase(runtime)) return null;
        if (!concrete.toUpperCase(Locale.ROOT).startsWith(family.toUpperCase(Locale.ROOT))) return null;

        String ymlVersion = readSystemConfigVersionId(root);
        if (blank(ymlVersion) || !concrete.equalsIgnoreCase(ymlVersion.trim())) return null;
        File dataConfig = new File(root, "data" + File.separator + "config.xml");
        String configured = readElement(dataConfig, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File xmlUtil = new File(classes, "util" + File.separator + "IXmlUtil.class");
        File iGlobal = new File(classes, "global" + File.separator + "IGlobal.class");
        File listener = new File(classes, "listener" + File.separator + "SystemSetListener.class");
        File softReg = new File(classes, "action" + File.separator + "SoftRegAction.class");

        if (!classFileContainsAll(xmlUtil, "systemConfig.yml", "global.system.VersionID",
                "/data/config.xml", "global/IStatic", "_SYS_CODE")) return null;
        if (!classFileContainsAll(iGlobal, "SYS_PRODUCT_CODE", family)) return null;
        if (!classFileContainsAll(softReg, family, "itmc/regedit/RegisterMain", "doRegistry")) return null;
        if (!classFileContainsAll(listener, family, "itmc/regedit/RegisterMain",
                "checkReInfo", "getRegInfo", "regStr", "global/IStatic", "_SYS_CODE", "contains")) return null;
        return concrete;
    }

    private static String readSystemConfigVersionId(File root) {
        if (root == null) return null;
        File yml = new File(root, "systemConfig.yml");
        if (!yml.isFile()) return null;
        try {
            for (String raw : Files.readAllLines(yml.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (!line.toLowerCase(Locale.ROOT).contains("versionid")) continue;
                int split = line.indexOf(':');
                if (split < 0) split = line.indexOf('=');
                if (split > 0) {
                    String value = line.substring(split + 1).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    static boolean confirmedProductAllNumRuntimeProduct(File classes) {
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
     * Real deployed YX030506 layout verified against the user's reduced production sample:
     * systemConfig.yml identifies concrete mode YX030506 while classes/config.xml lists
     * RegisterMain startup products YX0305,QT1001. The primary product is accepted only
     * when the target's PRODUCT_ALL_NUM runner and local registration servlet agree.
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

    static boolean usesProjectClasspathRegistrationBase(File root) {
        if (root == null) return false;
        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File provider = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "utils" + File.separator + "ProjectSourcesPath.class");
        File runner = new File(classes, "com" + File.separator + "itmc" + File.separator
                + "utils" + File.separator + "ProjectApplicationRunner.class");
        return classFileContainsAll(provider, "classpath:", "org/springframework/util/ResourceUtils",
                        "getURL", "java/net/URL", "getPath", "java/io/File", "getAbsolutePath")
                && classFileContainsAll(runner, "com/itmc/utils/ProjectSourcesPath", "projectPath",
                        "itmc/regedit/RegisterMain", "checkReInfo");
    }

    private static String normalizeCsv(String value) {
        if (blank(value)) return null;
        String[] raw = value.split(",");
        LinkedHashSet<String> dedup = new LinkedHashSet<String>();
        for (String item : raw) {
            if (item == null) continue;
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) dedup.add(trimmed);
        }
        if (dedup.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String item : dedup) {
            if (out.length() > 0) out.append(',');
            out.append(item);
        }
        return out.toString();
    }

    private static File findRegJar(File lib) {
        if (lib == null || !lib.isDirectory()) return null;
        File[] files = lib.listFiles();
        if (files == null) return null;
        File first = null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.equals("itmcreg.jar")) return f;
            if (first == null && n.startsWith("itmcreg") && n.endsWith(".jar")) first = f;
        }
        return first;
    }

    static boolean isVirboxPackedJar(File jar) {
        if (jar == null || !jar.isFile()) return false;
        try (JarFile jf = new JarFile(jar)) {
            JarEntry entry = jf.getJarEntry("itmc/regedit/RegisterMain.class");
            if (entry == null) return false;
            byte[] bytes;
            try (InputStream in = jf.getInputStream(entry)) { bytes = readAll(in); }
            if (containsAscii(bytes, "SenseShield") || containsAscii(bytes, "Virbox")) return true;
            if (bytes.length < 16) return false;
            int magic = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16)
                    | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
            if (magic != 0xCAFEBABE) return true;
            try {
                constantPoolUtf8(bytes);
                return false;
            } catch (Throwable ex) {
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean hasDirectoryBinaryToken(File root, File lib, String token) {
        if (blank(token)) return false;
        byte[] needle = token.getBytes(StandardCharsets.ISO_8859_1);
        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        if (scanFilesForToken(classes, needle, 0, 10)) return true;
        return scanJarsForToken(lib, needle);
    }

    private static boolean scanFilesForToken(File dir, byte[] needle, int depth, int maxDepth) {
        if (dir == null || !dir.isDirectory() || depth > maxDepth) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (scanFilesForToken(f, needle, depth + 1, maxDepth)) return true;
            } else if (f.isFile()) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".class")) continue;
                try {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    if (containsAscii(bytes, needle)) return true;
                } catch (Exception ignore) { }
            }
        }
        return false;
    }

    private static boolean scanJarsForToken(File lib, byte[] needle) {
        if (lib == null || !lib.isDirectory()) return false;
        File[] files = lib.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            try (JarFile jf = new JarFile(f)) {
                java.util.Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                    try (InputStream in = jf.getInputStream(entry)) {
                        byte[] bytes = readAll(in);
                        if (containsAscii(bytes, needle)) return true;
                    }
                }
            } catch (Exception ignore) { }
        }
        return false;
    }

    private static boolean classFileContainsAll(File file, String... tokens) {
        if (file == null || !file.isFile()) return false;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            for (String token : tokens) {
                if (!containsAscii(bytes, token)) return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        return containsAscii(haystack, needle.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static boolean containsAscii(byte[] haystack, byte[] needle) {
        if (haystack == null || needle == null || needle.length == 0 || haystack.length < needle.length) return false;
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static String readElement(File file, String tag) {
        if (file == null || !file.isFile()) return null;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("(?is)<" + Pattern.quote(tag) + "\\b[^>]*>(.*?)</" + Pattern.quote(tag) + ">");
            Matcher m = p.matcher(text);
            if (!m.find()) return null;
            return m.group(1) == null ? null : m.group(1).trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String relative(File root, File child) {
        if (root == null || child == null) return "—";
        try {
            return root.toPath().toAbsolutePath().normalize().relativize(
                    child.toPath().toAbsolutePath().normalize()).toString();
        } catch (Exception ex) {
            return child.getAbsolutePath();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String value(String value) { return blank(value) ? "—" : value; }

    private static LicenseRecoverModernGUIJavaPlan unknown() {
        return new LicenseRecoverModernGUIJavaPlan(false, null, null, null,
                null, "—", null, null, null, "—", "—", false, false, false, "未识别");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static LinkedHashSet<String> constantPoolUtf8(byte[] bytes) throws Exception {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) throw new IllegalArgumentException("not class");
        in.readUnsignedShort(); in.readUnsignedShort();
        int count = in.readUnsignedShort();
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1:
                    out.add(in.readUTF());
                    break;
                case 3: case 4: in.skipBytes(4); break;
                case 5: case 6: in.skipBytes(8); i++; break;
                case 7: case 8: case 16: case 19: case 20: in.skipBytes(2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: in.skipBytes(4); break;
                case 15: in.skipBytes(3); break;
                default: throw new IllegalArgumentException("cp tag " + tag);
            }
        }
        return out;
    }
}
