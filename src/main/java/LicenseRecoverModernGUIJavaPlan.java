import java.io.File;
import java.util.Locale;

/**
 * Read-only Java authorization plan used by both the single-app and batch GUI.
 * It intentionally reuses the same detection/mapping helpers as the real CLI
 * so what the UI previews is the same ProName/RegStr/config layout that will be
 * used when recovery actually runs.
 */
public final class LicenseRecoverModernGUIJavaPlan {
    public final boolean detected;
    public final File appRoot;
    public final File libDir;
    public final File regJar;
    public final String softVersionId;
    public final String generation;
    public final String productName;
    public final String regStr;
    public final String configTargets;
    public final String verificationPlan;
    public final boolean rootConfigStyle;
    public final boolean packedRegistrationJar;

    private LicenseRecoverModernGUIJavaPlan(boolean detected, File appRoot, File libDir, File regJar,
                                            String softVersionId, String generation, String productName,
                                            String regStr, String configTargets, String verificationPlan,
                                            boolean rootConfigStyle, boolean packedRegistrationJar) {
        this.detected = detected;
        this.appRoot = appRoot;
        this.libDir = libDir;
        this.regJar = regJar;
        this.softVersionId = softVersionId;
        this.generation = generation;
        this.productName = productName;
        this.regStr = regStr;
        this.configTargets = configTargets;
        this.verificationPlan = verificationPlan;
        this.rootConfigStyle = rootConfigStyle;
        this.packedRegistrationJar = packedRegistrationJar;
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
        String soft = LicenseRecover.readSoftId(root.getAbsolutePath());
        if (blank(soft)) soft = detection.versionId;

        boolean newStyle = LicenseRecover.isNewStyleApp(root.getAbsolutePath());
        boolean rootConfig = LicenseRecover.usesRootConfigApp(root.getAbsolutePath());
        File dataConfig = new File(root, "data" + File.separator + "config.xml");
        File classesConfig = new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml");

        String generation;
        if (newStyle) {
            generation = "QT30xxx / data-config";
        } else if (classesConfig.isFile()) {
            generation = soft != null && soft.toUpperCase(Locale.ROOT).startsWith("XMT01")
                    ? "XMT / classes-config" : "新式 / classes-config";
        } else if (dataConfig.isFile()) {
            generation = "YT/兼容根配置 / data-config";
        } else if (relative(root, lib).toLowerCase(Locale.ROOT).contains("web-inf" + File.separator + "web-inf")) {
            generation = "经典 Java / nested WEB-INF";
        } else {
            generation = "经典 Java / WEB-INF/lib";
        }

        String product = newStyle && !blank(soft)
                ? soft.trim() : LicenseRecover.productMainFor(soft);
        String products = LicenseRecover.resolveJavaRegStr(root.getAbsolutePath(), soft);
        File jar = LicenseRecover.findItmcRegJar(lib);
        boolean packed = false;
        if (jar != null) {
            try { packed = NetRemover.jarIsPacked(jar); }
            catch (Throwable ignore) { packed = false; }
        }

        String libConfig = relative(root, new File(lib, "config.xml"));
        String targets = libConfig;
        if (rootConfig) targets += " + config.xml(webapp根)";
        String verify = rootConfig
                ? "RegisterMain.checkReInfo(): lib + webapp根"
                : "RegisterMain.checkReInfo(): lib";

        return new LicenseRecoverModernGUIJavaPlan(true, root, lib, jar,
                soft, generation, product, products, targets, verify, rootConfig, packed);
    }

    public String regStrSummary() {
        if (blank(regStr)) return "未识别";
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
                .append(" ProName=").append(value(productName)).append('\n');
        out.append("[java-plan] RegStr=").append(value(regStr)).append('\n');
        out.append("[java-plan] config targets=").append(configTargets).append('\n');
        out.append("[java-plan] registration jar=").append(registrationJarSummary()).append('\n');
        out.append("[java-plan] verify=").append(verificationPlan).append('\n');
        return out.toString();
    }

    private static LicenseRecoverModernGUIJavaPlan unknown() {
        return new LicenseRecoverModernGUIJavaPlan(false, null, null, null,
                null, "未识别", null, null, "—", "—", false, false);
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
