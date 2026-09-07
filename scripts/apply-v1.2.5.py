from pathlib import Path
import re


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise SystemExit("pattern not found in %s: %r" % (path, old[:160]))
    write(path, text.replace(old, new, 1))


def replace_re(path, pattern, replacement, flags=re.S):
    text = read(path)
    new_text, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit("regex pattern count %s in %s: %r" % (count, path, pattern[:160]))
    write(path, new_text)


# -----------------------------------------------------------------------------
# Java GUI plan: distinguish vendor local-registration family from runtime ID.
# -----------------------------------------------------------------------------
plan = "src/main/java/LicenseRecoverModernGUIJavaPlan.java"
replace_once(
    plan,
    """    public final String generation;\n    public final String productName;\n    public final String regStr;\n""",
    """    public final String generation;\n    /** Vendor local-registration family, e.g. DS501 / YX0305. */\n    public final String authorizationFamily;\n    /** Concrete id used by application startup RegisterMain.checkReInfo(). */\n    public final String runtimeProductId;\n    /** Backward-compatible alias for direct-rebuild ProName. */\n    public final String productName;\n    public final String regStr;\n""",
)
replace_once(
    plan,
    """    private LicenseRecoverModernGUIJavaPlan(boolean detected, File appRoot, File libDir, File regJar,\n                                            String softVersionId, String generation, String productName,\n                                            String regStr, String configTargets, String verificationPlan,\n                                            boolean rootConfigStyle, boolean packedRegistrationJar) {\n""",
    """    private LicenseRecoverModernGUIJavaPlan(boolean detected, File appRoot, File libDir, File regJar,\n                                            String softVersionId, String generation, String authorizationFamily,\n                                            String runtimeProductId, String regStr, String configTargets,\n                                            String verificationPlan, boolean rootConfigStyle,\n                                            boolean packedRegistrationJar) {\n""",
)
replace_once(
    plan,
    """        this.generation = generation;\n        this.productName = productName;\n        this.regStr = regStr;\n""",
    """        this.generation = generation;\n        this.authorizationFamily = authorizationFamily;\n        this.runtimeProductId = runtimeProductId;\n        this.productName = runtimeProductId;\n        this.regStr = regStr;\n""",
)

replace_re(
    plan,
    r'''        String generation;\n        if \(newStyle\) \{.*?        String products = resolveRegStr\(root, soft\);\n''',
    '''        String upper = soft == null ? "" : soft.toUpperCase(Locale.ROOT);\n        String generation;\n        if (newStyle) {\n            generation = "QT30xxx / data-config";\n        } else if (classesConfig.isFile()) {\n            if (upper.startsWith("XMT01")) generation = "XMT / classes-config";\n            else if (upper.startsWith("DS501")) generation = "DS501 / classes-config";\n            else if (upper.startsWith("YX0305")) generation = "YX0305 / classes-config";\n            else generation = "通用 / classes-config";\n        } else if (dataConfig.isFile()) {\n            generation = upper.matches("DS28\\\\d{2}")\n                    ? "DS28 / data-config" : "YT/兼容根配置 / data-config";\n        } else if (relative(root, lib).toLowerCase(Locale.ROOT)\n                .contains("web-inf" + File.separator + "web-inf")) {\n            generation = "经典 Java / nested WEB-INF";\n        } else {\n            generation = "经典 Java / WEB-INF/lib";\n        }\n\n        String family = authorizationFamilyFor(soft, newStyle, classesConfig.isFile());\n        String runtimeProduct = newStyle && !blank(soft) ? soft.trim() : runtimeProductFor(soft);\n        String products = resolveRegStr(root, soft);\n''',
)
replace_once(
    plan,
    """        return new LicenseRecoverModernGUIJavaPlan(true, root, lib, jar,\n                soft, generation, product, products, targets, verify, rootConfig, packed);\n""",
    """        return new LicenseRecoverModernGUIJavaPlan(true, root, lib, jar,\n                soft, generation, family, runtimeProduct, products, targets, verify, rootConfig, packed);\n""",
)
replace_once(
    plan,
    """        out.append("[java-plan] SoftVersionID=").append(value(softVersionId))\n                .append(" ProName=").append(value(productName)).append('\\n');\n""",
    """        out.append("[java-plan] SoftVersionID=").append(value(softVersionId))\n                .append(" AuthorizationFamily=").append(value(authorizationFamily))\n                .append(" RuntimeProductID/ProName=").append(value(runtimeProductId)).append('\\n');\n""",
)
replace_once(
    plan,
    """        if (classes != null) return classes;\n        if (softId != null && softId.toUpperCase(Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();\n""",
    """        if (classes != null) return classes;\n        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("DS501")) return softId.trim();\n        if (softId != null && softId.toUpperCase(Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();\n""",
)
replace_re(
    plan,
    r'''    private static String productMainFor\(String softId\) \{.*?\n    \}\n\n    private static String readElement''',
    '''    static String authorizationFamilyFor(String softId, boolean newStyle, boolean classesStyle) {\n        if (blank(softId)) return newStyle ? "QT30xxx" : (classesStyle ? "未确认" : "QT1001");\n        String id = softId.toUpperCase(Locale.ROOT);\n        if (id.startsWith("DS501")) return "DS501";\n        if (id.startsWith("YX0305")) return "YX0305";\n        if (id.startsWith("XMT01")) return "XMT01";\n        if (id.matches("DS28\\\\d{2}")) return "DS28";\n        if (newStyle) return softId.trim();\n        if ("YT00128".equals(id) || "YT00127".equals(id) || "YT00129".equals(id)\n                || "YT00139".equals(id) || "YT00132".equals(id) || "YT00141".equals(id)\n                || "YT00126".equals(id) || "YT00154".equals(id) || "BKSM4".equals(id)) return "QT04";\n        if (classesStyle) return "未确认";\n        return "QT1001";\n    }\n\n    static String runtimeProductFor(String softId) {\n        if (blank(softId)) return "QT1001";\n        String id = softId.toUpperCase(Locale.ROOT);\n        // Real DS501/YX0305 samples use a broader id on the local-registration page,\n        // while application startup checks the concrete SoftVersionID.\n        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();\n        if (id.matches("DS28\\\\d{2}")) return "DS28";\n        if (id.startsWith("XMT01")) return "XMT01";\n        if ("YT00128".equals(id) || "YT00127".equals(id) || "YT00129".equals(id)\n                || "YT00139".equals(id) || "YT00132".equals(id) || "YT00141".equals(id)\n                || "YT00126".equals(id) || "YT00154".equals(id) || "BKSM4".equals(id)) return "QT04";\n        return "QT1001";\n    }\n\n    private static String readElement''',
)
replace_once(
    plan,
    """        return new LicenseRecoverModernGUIJavaPlan(false, null, null, null,\n                null, "未识别", null, null, "—", "—", false, false);\n""",
    """        return new LicenseRecoverModernGUIJavaPlan(false, null, null, null,\n                null, "未识别", null, null, null, "—", "—", false, false);\n""",
)

# -----------------------------------------------------------------------------
# CLI: direct rebuild uses runtime ID; --gencode uses local-registration family.
# -----------------------------------------------------------------------------
cli = "src/main/java/LicenseRecover.java"
replace_once(
    cli,
    """        if (classes != null) return classes;\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();\n""",
    """        if (classes != null) return classes;\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();\n""",
)
replace_once(
    cli,
    """    // 与 Global.registerProductBeans 的首个命中规则保持一致\n    static String productMainFor(String softId) {\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return "DS28";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("XMT01")) return "XMT01";\n        if (softId == null) return "QT1001";\n""",
    """    // Direct config rebuild must use the id application startup passes to RegisterMain.checkReInfo().\n    static String productMainFor(String softId) {\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("YX0305")) return softId.trim();\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return "DS28";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("XMT01")) return "XMT01";\n        if (softId == null) return "QT1001";\n""",
)
replace_once(
    cli,
    """                if (isNewStyleApp(appArg)) {\n                    registerPid = softId.trim();\n                    System.out.println("[识别] 新架构(data/config.xml 存在)，注册码产品号 = " + registerPid);\n                } else {\n                    registerPid = "DS2601".equals(softId) ? "DS26" : "YT001";\n                    System.out.println("[识别] systemConfig.yml VersionID = " + softId + "  -> 注册码产品号 " + registerPid);\n                }\n""",
    """                boolean dataStyle = isNewStyleApp(appArg);\n                registerPid = localRegisterProductFor(softId, dataStyle);\n                System.out.println("[识别] VersionID = " + softId + "  -> 本地注册产品族 " + registerPid);\n""",
)
replace_once(
    cli,
    """    // ================= 方式三: 移除联网授权代码 (暴力) =================\n""",
    """    /** Product family used by the vendor local-registration page / doRegistry path. */\n    static String localRegisterProductFor(String softId, boolean dataStyle) {\n        if (softId == null || softId.trim().isEmpty()) return "YT001";\n        String id = softId.toUpperCase(java.util.Locale.ROOT);\n        if (id.startsWith("DS501")) return "DS501";\n        if (id.startsWith("YX0305")) return "YX0305";\n        if ("DS2601".equals(id)) return "DS26";\n        if (dataStyle) return softId.trim();\n        return "YT001";\n    }\n\n    // ================= 方式三: 移除联网授权代码 (暴力) =================\n""",
)

# -----------------------------------------------------------------------------
# Batch GUI: explicit family and runtime-id columns.
# -----------------------------------------------------------------------------
gui = "src/main/java/LicenseRecoverModernGUI.java"
replace_once(
    gui,
    """    private static final int BATCH_COL_VERIFY = 6;\n    private static final int BATCH_COL_STATUS = 7;\n    private final DefaultTableModel batchModel = new DefaultTableModel(\n            new String[]{"应用", "类型 / 授权代际", "SoftVersionID", "ProName", "RegStr", "配置目标", "原生校验", "状态", "路径"}, 0) {\n""",
    """    private static final int BATCH_COL_VERIFY = 7;\n    private static final int BATCH_COL_STATUS = 8;\n    private final DefaultTableModel batchModel = new DefaultTableModel(\n            new String[]{"应用", "类型 / 授权代际", "SoftVersionID", "授权族", "运行校验ID", "RegStr", "配置目标", "原生校验", "状态", "路径"}, 0) {\n""",
)
replace_once(gui,
             "        int[] widths = new int[]{120, 190, 120, 100, 220, 230, 160, 90, 420};\n",
             "        int[] widths = new int[]{120, 190, 120, 100, 120, 220, 230, 160, 90, 420};\n")
replace_once(
    gui,
    """                            valueOrDash(plan.softVersionId), valueOrDash(plan.productName),\n                            plan.regStrSummary(), plan.configTargets,\n""",
    """                            valueOrDash(plan.softVersionId), valueOrDash(plan.authorizationFamily),\n                            valueOrDash(plan.runtimeProductId), plan.regStrSummary(), plan.configTargets,\n""",
)
replace_once(
    gui,
    """                    batchModel.addRow(new Object[]{child.getName(), kind,\n                            valueOrDash(d.versionId), valueOrDash(d.productName), "—", "config.xml",\n                            verify, "待处理", info.binDir.getAbsolutePath()});\n""",
    """                    batchModel.addRow(new Object[]{child.getName(), kind,\n                            valueOrDash(d.versionId), valueOrDash(d.productName), valueOrDash(d.productName),\n                            "—", "config.xml", verify, "待处理", info.binDir.getAbsolutePath()});\n""",
)
replace_once(
    gui,
    """                    batchModel.addRow(new Object[]{child.getName(), "—", "—", "—", "—", "—", "—", "未识别", child.getAbsolutePath()});\n""",
    """                    batchModel.addRow(new Object[]{child.getName(), "—", "—", "—", "—", "—", "—", "—", "未识别", child.getAbsolutePath()});\n""",
)
replace_once(
    gui,
    """                batchModel.addRow(new Object[]{child.getName(), "检测异常", "—", "—", "—", "—", "—",\n                        reason, child.getAbsolutePath()});\n""",
    """                batchModel.addRow(new Object[]{child.getName(), "检测异常", "—", "—", "—", "—", "—", "—",\n                        reason, child.getAbsolutePath()});\n""",
)

# -----------------------------------------------------------------------------
# Single-app overlay: same model as batch.
# -----------------------------------------------------------------------------
ui = "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java"
replace_once(
    ui,
    """        final JLabel generationValue = new JLabel("—");\n        final JLabel productValue = new JLabel("—");\n        final JTextArea regStrValue = detailArea();\n""",
    """        final JLabel generationValue = new JLabel("—");\n        final JLabel familyValue = new JLabel("—");\n        final JLabel runtimeValue = new JLabel("—");\n        final JTextArea regStrValue = detailArea();\n""",
)
replace_once(
    ui,
    """        addDetailRow(details, 0, "授权代际", generationValue);\n        addDetailRow(details, 1, "ProName", productValue);\n        addDetailRow(details, 2, "RegStr", regStrValue);\n        addDetailRow(details, 3, "配置目标", targetValue);\n        addDetailRow(details, 4, "授权组件", jarValue);\n        addDetailRow(details, 5, "原生校验", verifyValue);\n""",
    """        addDetailRow(details, 0, "授权代际", generationValue);\n        addDetailRow(details, 1, "授权族", familyValue);\n        addDetailRow(details, 2, "运行校验ID", runtimeValue);\n        addDetailRow(details, 3, "RegStr", regStrValue);\n        addDetailRow(details, 4, "配置目标", targetValue);\n        addDetailRow(details, 5, "授权组件", jarValue);\n        addDetailRow(details, 6, "原生校验", verifyValue);\n""",
)
replace_once(
    ui,
    """        recover.setToolTipText("Java 会展示授权代际 / ProName / RegStr / config 目标，并在写入后执行 RegisterMain 原生校验");\n        recover.addActionListener(e -> runOneClick(frame, recover,\n                generationValue, productValue, regStrValue, targetValue, jarValue, verifyValue));\n""",
    """        recover.setToolTipText("Java 会展示授权代际 / 授权族 / 运行校验ID / RegStr / config 目标，并在写入后执行 RegisterMain 原生校验");\n        recover.addActionListener(e -> runOneClick(frame, recover,\n                generationValue, familyValue, runtimeValue, regStrValue, targetValue, jarValue, verifyValue));\n""",
)
replace_once(
    ui,
    """        final Runnable refresh = () -> refreshPlan(frame, generationValue, productValue,\n                regStrValue, targetValue, jarValue, verifyValue);\n""",
    """        final Runnable refresh = () -> refreshPlan(frame, generationValue, familyValue, runtimeValue,\n                regStrValue, targetValue, jarValue, verifyValue);\n""",
)
replace_once(
    ui,
    """    private static void refreshPlan(JFrame frame, JLabel generation, JLabel product,\n                                    JTextArea regStr, JTextArea targets, JLabel jar, JLabel verify) {\n""",
    """    private static void refreshPlan(JFrame frame, JLabel generation, JLabel family, JLabel runtime,\n                                    JTextArea regStr, JTextArea targets, JLabel jar, JLabel verify) {\n""",
)
replace_once(ui,
             "            generation.setText(\"—\"); product.setText(\"—\"); regStr.setText(\"—\"); targets.setText(\"—\");\n",
             "            generation.setText(\"—\"); family.setText(\"—\"); runtime.setText(\"—\"); regStr.setText(\"—\"); targets.setText(\"—\");\n")
replace_once(
    ui,
    """            generation.setText(plan.generation);\n            product.setText(value(plan.productName));\n            regStr.setText(value(plan.regStr)); regStr.setCaretPosition(0);\n""",
    """            generation.setText(plan.generation);\n            family.setText(value(plan.authorizationFamily));\n            runtime.setText(value(plan.runtimeProductId));\n            regStr.setText(value(plan.regStr)); regStr.setCaretPosition(0);\n""",
)
replace_once(
    ui,
    """            generation.setText(detection.kind.toString());\n            product.setText(value(detection.productName));\n            regStr.setText(".NET 由对应适配器自动识别");\n""",
    """            generation.setText(detection.kind.toString());\n            family.setText(value(detection.productName));\n            runtime.setText(value(detection.productName));\n            regStr.setText(".NET 由对应适配器自动识别");\n""",
)
replace_once(ui,
             "            generation.setText(\"未识别\"); product.setText(\"—\"); regStr.setText(\"—\"); targets.setText(\"—\");\n",
             "            generation.setText(\"未识别\"); family.setText(\"—\"); runtime.setText(\"—\"); regStr.setText(\"—\"); targets.setText(\"—\");\n")
replace_once(
    ui,
    """    private static void runOneClick(final JFrame frame, final JButton button,\n                                    final JLabel generationValue, final JLabel productValue,\n                                    final JTextArea regStrValue, final JTextArea targetValue,\n""",
    """    private static void runOneClick(final JFrame frame, final JButton button,\n                                    final JLabel generationValue, final JLabel familyValue, final JLabel runtimeValue,\n                                    final JTextArea regStrValue, final JTextArea targetValue,\n""",
)
replace_once(
    ui,
    "        refreshPlan(frame, generationValue, productValue, regStrValue, targetValue, jarValue, verifyValue);\n",
    "        refreshPlan(frame, generationValue, familyValue, runtimeValue, regStrValue, targetValue, jarValue, verifyValue);\n",
)

# -----------------------------------------------------------------------------
# Regression fixtures based on the supplied DS50109/YX030506 samples.
# -----------------------------------------------------------------------------
test = "src/test/java/RefactorSmokeTest.java"
marker = "        Path nestedRoot = base.resolve(\"nestedApp\");\n"
addition = r'''        Path ds501Root = base.resolve("java-DS50109");
        Path ds501Lib = ds501Root.resolve("WEB-INF/lib");
        Path ds501Classes = ds501Root.resolve("WEB-INF/classes");
        Files.createDirectories(ds501Lib);
        Files.createDirectories(ds501Classes);
        Files.write(ds501Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(ds501Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>3</regType></reg><SystemSoft><SoftVersionID>DS50109</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("DS50109".equals(LicenseRecover.productMainFor("DS50109")),
                "DS501 direct rebuild uses concrete runtime check id");
        check("DS501".equals(LicenseRecover.localRegisterProductFor("DS50109", false)),
                "DS501 local-registration page uses DS501 family");
        check("DS50109".equals(LicenseRecover.resolveJavaRegStr(ds501Root.toString(), "DS50109")),
                "DS501 fallback RegStr contains concrete SoftVersionID");
        LicenseRecoverModernGUIJavaPlan ds501Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds501Root.toFile());
        check(ds501Plan.generation.contains("DS501") && "DS501".equals(ds501Plan.authorizationFamily),
                "Java GUI plan names DS501 family instead of generic classes-config");
        check("DS50109".equals(ds501Plan.runtimeProductId) && "DS50109".equals(ds501Plan.regStr),
                "Java GUI plan separates DS501 family from runtime id and RegStr");

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
        check("QT100101,QT100102".equals(LicenseRecover.resolveJavaRegStr(yx305Root.toString(), "YX030506")),
                "YX030506 preserves declared classes-config regInfo");
        LicenseRecoverModernGUIJavaPlan yx305Plan = LicenseRecoverModernGUIJavaPlan.inspect(yx305Root.toFile());
        check(yx305Plan.generation.contains("YX0305") && "YX0305".equals(yx305Plan.authorizationFamily),
                "Java GUI plan names YX0305 family instead of generic classes-config");
        check("YX030506".equals(yx305Plan.runtimeProductId)
                        && "QT100101,QT100102".equals(yx305Plan.regStr),
                "Java GUI plan separates YX0305 family, runtime id and feature RegStr");

'''
replace_once(test, marker, addition + marker)

# -----------------------------------------------------------------------------
# Version / docs.
# -----------------------------------------------------------------------------
Path("VERSION.txt").write_text("1.2.5\n", encoding="utf-8")

readme = "README.md"
text = read(readme)
text = text.replace("当前稳定版本：**v1.2.4**", "当前稳定版本：**v1.2.5**", 1)
old = "从 v1.2.2 起，单个应用的一键恢复区会直接显示 **授权代际、SoftVersionID 对应的 ProName、实际 RegStr、将写入的 config.xml 位置、授权组件以及 RegisterMain 原生校验结果**。批量页使用同一套识别模型，扫描后会逐项展开这些信息，并让批量“一键恢复授权”走与单个应用相同的恢复/校验链路。\n"
new = old + "\nv1.2.5 根据实际 DS50109 / YX030506 样本，把 classes-config 平台拆成 **授权族、运行校验ID、RegStr** 三层：DS501xx 的本地注册族为 `DS501`、运行校验使用具体 SoftVersionID；YX0305xx 的本地注册族为 `YX0305`、运行校验同样使用具体 SoftVersionID，并保留 classes/config.xml 明确声明的 RegStr。尚无真实样本证据的 classes-config 产品显示为“通用 / classes-config”，不再笼统称为“新式”或把兼容回退伪装成已确认代际。\n"
if old not in text:
    raise SystemExit("README Java paragraph not found")
write(readme, text.replace(old, new, 1))

readmetxt = "README.txt"
text = read(readmetxt).replace("LicenseRecover v1.2.4", "LicenseRecover v1.2.5", 1)
anchor = "软件自动更新\n------------\n"
if anchor not in text:
    raise SystemExit("README.txt update anchor not found")
block = """v1.2.5 Java classes-config 授权族识别\n------------------------------------\n- 不再把已确认的 classes-config 平台统称为“新式”；\n- DS501xx：授权族 DS501，运行校验ID 为具体 SoftVersionID，缺少 regInfo 时 RegStr 使用具体 SoftVersionID；\n- YX0305xx：授权族 YX0305，运行校验ID 为具体 SoftVersionID，RegStr 使用 classes/config.xml 显式 regInfo；\n- 单应用与批量表均分别展示“授权族”和“运行校验ID”。\n\n"""
write(readmetxt, text.replace(anchor, block + anchor, 1))

changelog = "CHANGELOG.md"
text = read(changelog)
anchor = "All notable user-visible and engineering changes are tracked here from the first stable release onward.\n\n"
if anchor not in text:
    raise SystemExit("CHANGELOG anchor not found")
entry = """## [1.2.5] - 2026-09-07\n\n### Fixed\n\n- 根据实际 DS50109 样本，将本地注册族 `DS501` 与运行校验ID `DS50109` 分离；直接授权重建使用运行期具体ID，RegStr 未显式配置时使用具体 SoftVersionID。\n- 根据实际 YX030506 样本，将本地注册族 `YX0305` 与运行校验ID `YX030506` 分离；保留 classes-config 中显式 `QT100101,QT100102` RegStr。\n- 修复上述 classes-config 平台此前回退为 `QT1001` ProName/通用 RegStr 的误导性识别。\n\n### Changed\n\n- “新式 / classes-config”改为明确的 `DS501 / classes-config`、`YX0305 / classes-config`；尚未有样本证据的 classes-config 平台显示为“通用 / classes-config”。\n- 单应用授权详情和批量表增加“授权族”和“运行校验ID”两个独立字段。\n\n"""
write(changelog, text.replace(anchor, anchor + entry, 1))

Path("release-notes/v1.2.5.md").write_text("""# LicenseRecover v1.2.5\n\n本版本根据实际 DS50109 与 YX030506 安装样本，修正 Java classes-config 平台的授权模型。\n\n## 授权族 / 运行校验ID / RegStr\n\n此前界面把 classes-config 平台笼统显示为“新式”，并用一个 ProName 字段同时表达本地注册产品族与应用启动时的 RegisterMain 校验ID。实际样本证明两者并不总是相同。\n\n- DS50109：本地注册业务使用 `RegisterMain(\"DS501\")`；应用启动使用具体 `DS50109` 执行 `RegisterMain.checkReInfo()`，且要求 RegStr 包含当前 versionID。\n- YX030506：本地注册页面使用 `RegisterMain(\"YX0305\", ...)`；应用启动从配置的 SoftVersionID 读取具体 `YX030506` 执行 `RegisterMain.checkReInfo()`；配置明确声明 `regInfo=QT100101,QT100102`。\n\n因此 v1.2.5 将 Java 识别拆成三层：\n\n1. **授权族**：厂商本地注册页面 / doRegistry 路径使用的产品族；\n2. **运行校验ID**：应用启动时 RegisterMain.checkReInfo() 使用的具体ID；\n3. **RegStr**：实际授权功能/版本项。\n\n## 界面与批量\n\n- 已确认样本显示 `DS501 / classes-config`、`YX0305 / classes-config`。\n- 单应用与批量页都分别显示“授权族”和“运行校验ID”。\n- 尚未有真实样本支持的 classes-config 产品显示为 `通用 / classes-config`；不会仅凭产品名猜测 QT40101 等平台的授权族。\n\n## 执行逻辑\n\n- DS501xx 直接授权重建使用具体 SoftVersionID 作为运行期 ProName；配置没有 regInfo 时，RegStr 使用具体 SoftVersionID。\n- YX0305xx 直接授权重建使用具体 SoftVersionID 作为运行期 ProName；RegStr 优先使用 classes/config.xml 显式 regInfo。\n- 手动“生成离线授权码”路径分别使用已确认的本地注册族 `DS501` / `YX0305`。\n\n原有 YT、XMT、QT30xxx、DS28、.NET、一键恢复、批量异常隔离与 v1.2.4 更新器逻辑保持不变。\n""", encoding="utf-8")

# Keep the final branch clean: remove staging files before the bot commit.
for staging in [Path("scripts/apply-v1.2.5.py"), Path(".github/workflows/apply-v1.2.5.yml")]:
    if staging.exists():
        staging.unlink()

print("v1.2.5 patch applied")
