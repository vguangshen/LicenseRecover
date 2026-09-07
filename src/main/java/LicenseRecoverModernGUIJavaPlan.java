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

    // Must stay byte-for-byte equivalent in meaning to LicenseRecover.ALL_NUMS.
    private static final String FALLBACK_ALL_NUMS =
            "QT100103,QT100107,QT100105,QT100109,QT100112,QT100108,"
          + "QT0436,QT0421,QT0420,QT0437,QT0424,QT0438,QT0435,QT0445,QT0441,ZGZF11,"
          + "PT0212,PT0208,PT0210,QT0454,PT0202,QT0456,PT0301,QT0447,PT0303,"
          + "YT00124,YT00125,YT00142,YT00143,YT00123,YT00147,YT00148,YT00149,YT00150,"
          + "YT00128,YT00127,YT00129,YT00139,YT00132,YT00141,BKSM4,YT00126,YT00154,YT001";

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
        String family = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily)
                ? confirmedClassesFamily : authorizationFamilyFor(soft, newStyle, classesConfig.isFile()));
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily)
                ? confirmedClassesFamily : (newStyle && !blank(soft) ? soft.trim() : runtimeProductFor(soft)));
        File jar = findRegJar(lib);
        boolean packed = jar != null && isVirboxPackedJar(jar);
        String products = directoryMapping != null ? directoryMapping.productMainNum
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
        String libConfig = relative(root, new File(lib, "config.xml"));
        String targets = libConfig;
        if (rootConfig) targets += " + config.xml(webapp根)";
        String verify = rootConfig
                ? "RegisterMain.checkReInfo(): lib + webapp根"
                : "RegisterMain.checkReInfo(): lib";

        return new LicenseRecoverModernGUIJavaPlan(true, root, lib, jar,
                soft, generation, family, runtimeProduct, products, targets, verify, rootConfig, packed,
                ready, readiness);
    }

    public String regStrSummary() {
        if (blank(regStr)) return "未静态声明";
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

    private static String resolveRegStr(File root, String softId, String runtimeProduct, File lib) {
        String data = normalizeCsv(readElement(new File(root, "data" + File.separator + "config.xml"), "regInfo"));
        if (data != null) return data;
        String classes = normalizeCsv(readElement(new File(root, "WEB-INF" + File.separator
                + "classes" + File.separator + "config.xml"), "regInfo"));
        if (classes != null) return classes;

        String recovered = ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
        if (recovered != null) return recovered;

        // QT40101 production sample: RegisterListener sets hasRegister/authorizeFlag=true
        // before consuming RegStr, and no application-side startup gate requires a
        // ClassPid match. Use the concrete VersionID as the deterministic minimum
        // non-empty RegStr. Do not generalize this rule to other QT401xx products.
        if ("QT40101".equalsIgnoreCase(softId)
                && "QT401".equals(confirmedClassesAuthorizationFamily(root, softId))) {
            return "QT40101";
        }

        if ("QT100101".equalsIgnoreCase(softId)
                && "QT100101".equals(confirmedClassesAuthorizationFamily(root, softId))) {
            return "QT100101";
        }

        if ("DS2406".equalsIgnoreCase(softId)) return "DS2406";
        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("DS501")) return softId.trim();
        if (softId != null && softId.toUpperCase(Locale.ROOT).matches("DS28\\d{2}")) return softId.trim();
        if ("YT00129".equalsIgnoreCase(softId)) return "QT0420";
        if (new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml").isFile())
            return null;
        if (LegacyJavaRegistrationMetadata.requiresDirectoryMapping(root, softId)) return null;
        return FALLBACK_ALL_NUMS;
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
        if ("QT100101".equals(id)) {
            File config = new File(root, "WEB-INF" + File.separator + "classes"
                    + File.separator + "config.xml");
            String concrete = readElement(config, "SoftVersionID");
            if ("QT100101".equalsIgnoreCase(concrete)) return "QT100101";
        }
        return null;
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
        // Real DS501/YX0305 samples use a broader id on the local-registration page,
        // while application startup checks the concrete SoftVersionID.
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

    private static String readElement(File file, String element) {
        if (file == null || !file.isFile() || blank(element)) return null;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile("(?is)<" + Pattern.quote(element)
                    + "\\b[^>]*>\\s*([^<]*?)\\s*</" + Pattern.quote(element) + "\\s*>");
            Matcher matcher = pattern.matcher(text);
            return matcher.find() ? matcher.group(1).trim() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String normalizeCsv(String value) {
        if (value == null) return null;
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (String part : value.split(",")) {
            String x = part == null ? "" : part.trim();
            if (!x.isEmpty()) values.add(x);
        }
        if (values.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String x : values) {
            if (out.length() > 0) out.append(',');
            out.append(x);
        }
        return out.toString();
    }

    private static File findRegJar(File lib) {
        if (lib == null || !lib.isDirectory()) return null;
        File exact = new File(lib, "ITMCReg.jar");
        if (exact.isFile()) return exact;
        File[] files = lib.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.startsWith("itmcreg") && lower.endsWith(".jar");
        });
        return files != null && files.length > 0 ? files[0] : null;
    }

    private static boolean isVirboxPackedJar(File jar) {
        JarFile jf = null;
        try {
            jf = new JarFile(jar);
            java.util.Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                InputStream raw = null;
                DataInputStream in = null;
                try {
                    raw = jf.getInputStream(entry);
                    in = new DataInputStream(raw);
                    if (in.readInt() != 0xCAFEBABE) continue;
                    int minor = in.readUnsignedShort();
                    if (minor == 32768) return true;
                } catch (Exception ignore) {
                    // Keep scanning other classes. A damaged entry should not abort batch preview.
                } finally {
                    try { if (in != null) in.close(); else if (raw != null) raw.close(); }
                    catch (Exception ignore) { }
                }
            }
        } catch (Exception ignore) {
            return false;
        } finally {
            try { if (jf != null) jf.close(); } catch (Exception ignore) { }
        }
        return false;
    }

    private static LicenseRecoverModernGUIJavaPlan unknown() {
        return new LicenseRecoverModernGUIJavaPlan(false, null, null, null,
                null, "未识别", null, null, null, "—", "—", false, false,
                false, "未识别");
    }

    private static String relative(File root, File child) {
        try {
            String text = root.toPath().toAbsolutePath().normalize()
                    .relativize(child.toPath().toAbsolutePath().normalize()).toString();
            return text.isEmpty() ? child.getName() : text;
        } catch (Exception ignore) {
            return child.getAbsolutePath();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String value(String value) {
        return blank(value) ? "—" : value;
    }
}
