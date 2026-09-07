/*
 * ITMC 云实训平台 - 离线授权恢复工具 (GUI 版)
 *
 * 兼容: Windows 7 / Windows Server 2008 / JRE 8 (Swing/AWT)
 * 功能: 选择应用根目录 -> 从目标目录解析注册身份 -> Java 写入本地授权；.NET 方式一
 *       防止软件自动联网校验（只改 config.xml），方式三移除联网授权代码。
 * 方式一/二的生成与自校验复用应用自带类 (itmc.regedit.* / fastjson / dom4j)，
 * DS01xx 等旧版 .NET 应用的方式二使用内置旧协议适配器；
 * 方式三由内嵌 C# 助手处理小写 itmcRegedit.dll。
 */
import itmc.regedit.DesUtil;
import itmc.regedit.GetRegisterCode;
import itmc.regedit.XMLFiles;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LicenseRecoverGUI {

    // ---- 核心逻辑 (与 CLI 版一致) ----

    /** 在 lib 目录中定位授权 jar（ITMCReg.jar 或带版本号的 ITMCReg-*.jar），找不到返回 null。 */
    static File findItmcRegJar(File lib) {
        if (lib == null || !lib.isDirectory()) return null;
        File exact = new File(lib, "ITMCReg.jar");
        if (exact.isFile()) return exact;
        File[] fs = lib.listFiles((d, n) -> n.toLowerCase().startsWith("itmcreg") && n.toLowerCase().endsWith(".jar"));
        if (fs != null && fs.length > 0) return fs[0];
        return null;
    }

    /** 在给定目录(或其父级)中定位包含 ITMCReg*.jar 的 lib 目录（兼容嵌套 WEB-INF/WEB-INF） */
    static String locateLibDir(File start) {
        File cur = start;
        if (cur == null || !cur.isDirectory()) return null;
        while (cur != null) {
            File lib = cur.getName().equalsIgnoreCase("lib") ? cur
                     : new File(cur, "WEB-INF" + File.separator + "lib");
            if (findItmcRegJar(lib) != null) return lib.getAbsolutePath();
            File nested = new File(cur, "WEB-INF" + File.separator + "WEB-INF" + File.separator + "lib");
            if (findItmcRegJar(nested) != null) return nested.getAbsolutePath();
            cur = cur.getParentFile();
        }
        return null;
    }

    /** 从 systemConfig.yml(VersionID) 或 data/config.xml(SystemSoft/SoftVersionID) 读取软件产品号 */
    static String readSoftId(File appRoot) {
        try {
            File yml = new File(appRoot, "systemConfig.yml");
            if (yml.exists()) {
                for (String line : Files.readAllLines(yml.toPath(), StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.toLowerCase().contains("versionid")) {
                        int eq = line.indexOf(':');
                        if (eq < 0) eq = line.indexOf('=');
                        if (eq > 0) return line.substring(eq + 1).trim();
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        try {
            File cfg = new File(appRoot, "data" + File.separator + "config.xml");
            if (cfg.exists()) {
                for (String line : Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("<SoftVersionID>")) {
                        int end = t.lastIndexOf('<');
                        if (end > 0) return t.substring(t.indexOf('>') + 1, end).trim();
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    /** 仅用于旧界面显示；可执行路径不依赖静态映射，未知值返回 null。 */
    static String productMainFor(String softId) {
        return null;
    }

    /**
     * 方式二: 生成离线授权码。注册产品族和 RegStr 必须由目标目录确认。
     * @return {申请号, 授权码, 主板号, 申请时间}
     */
    static String[] genRegisterCode(String seq, String registerProductID, String regStr) throws Exception {
        if (registerProductID == null || registerProductID.trim().isEmpty())
            throw new IllegalArgumentException("注册产品族未从目标目录确认");
        if (regStr == null || regStr.trim().isEmpty())
            throw new IllegalArgumentException("RegStr 未从目标目录确认");
        GetRegisterCode rc = new GetRegisterCode();
        if (seq == null || seq.trim().isEmpty()) {
            DesUtil d = new DesUtil();
            String data = d.getRandom() + d.getMotherboardSN() + d.getRandom() + d.getCurrentTime() + d.getRandom();
            seq = new GetRegisterCode().buildCiphertext(data);
        }
        String myRegNO = rc.decrypt("itmcsoft", seq);
        if (myRegNO == null || myRegNO.length() < 43) {
            throw new Exception("无法解析注册申请号（申请号格式不正确或已损坏）");
        }
        String sn = myRegNO.substring(4, 20);
        String time19 = myRegNO.substring(24, 43);
        String end = "2099-12-31";
        String plain = "00" + time19 + "00" + sn + "00" + end + "00" + "1" + "00" + "-001" + "00" + "-1" + "00" + regStr.trim();
        String code = rc.encrypt("itmc" + registerProductID.trim(), plain);
        return new String[]{seq, code, sn, time19};
    }

    // ---- GUI ----

    static JFrame frame;
    static String uiFontName = null;
    static JTextField appRootField = new JTextField(38);
    static JTextField productField = new JTextField(8);
    /** 产品号是否为「检测环境」从目标目录自动填充。 */
    static boolean productAutoFilled;
    static JCheckBox backupCheck = new JCheckBox("写入前备份原配置", true);
    static JCheckBox blockNetCheck = new JCheckBox("Java方式一把授权服务地址指向本地", true);
    static JCheckBox dryRunCheck = new JCheckBox("只预览不写入");
    static JTextArea logArea = new JTextArea();
    static java.io.OutputStream logFileStream;
    static java.io.PrintStream logFilePrint;
    static JLabel statusLabel = new JLabel("就绪");
    static JButton runBtn = new JButton("生成离线授权并写入");
    static JButton detectBtn = new JButton("检测环境");
    static TitledBorder mode1Border;
    static JLabel mode1Hint;
    static JPanel mode1Panel;

    static JTextField seqField = new JTextField(34);
    static JTextField codeField = new JTextField();
    static JButton genBtn = new JButton("生成离线授权码");
    static JButton copyBtn = new JButton("复制授权码");
    static JCheckBox blockNetGenCheck = new JCheckBox("生成时顺带写入「阻止联网」配置", true);
    static volatile String lastCode = "";
    static final Pattern GENERATED_CODE_PATTERN = Pattern.compile(
            "(?m)^\\s*离线授权码\\s*[:：]\\s*([0-9A-Fa-f]+)\\s*$");

    static JButton scanNetBtn = new JButton("扫描识别联网授权文件");
    static JButton patchNetBtn = new JButton("移除联网授权并回写");

    static JLabel appTypeBadge = new JLabel("待检测");
    static String currentAppType = "";

    static JTextField batchRootField = new JTextField(30);
    static JButton batchScanBtn = new JButton("扫描子目录");
    static JButton batchRunBtn = new JButton("批量执行");
    static javax.swing.ButtonGroup batchMethodGroup = new javax.swing.ButtonGroup();
    static JRadioButton batchMethodCfg = new JRadioButton("方式一：防止.NET联网校验 / Java写本地授权", true);
    static JRadioButton batchMethodPatch = new JRadioButton("方式三：移除联网授权代码");
    static JTable batchTable = new JTable();
    static javax.swing.table.DefaultTableModel batchModel =
            new javax.swing.table.DefaultTableModel(new String[]{"应用", "类型", "状态", "路径"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };
    static java.util.List<Object[]> batchTargets = new java.util.ArrayList<>();

    static float UI_SCALE = 1.0f;

    static float uiScaleFactor() {
        try {
            String v = System.getProperty("java.version", "8");
            int major;
            if (v.startsWith("1.")) major = Integer.parseInt(v.substring(2, v.indexOf('.', 2)));
            else major = Integer.parseInt(v.substring(0, v.indexOf('.')));
            if (major > 8) return 1.0f;
            double s = Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
            return (float) Math.max(1.0, Math.min(s, 2.5));
        } catch (Exception e) { return 1.0f; }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.dpiaware", "true");
        UI_SCALE = uiScaleFactor();
        uiFontName = pickCjkFont();
        initLogFile();

        try {
            System.setOut(new PrintStream(new LogOutputStream(), true, "UTF-8"));
            System.setErr(new PrintStream(new LogOutputStream(), true, "UTF-8"));
        } catch (Exception ignore) { }

        try { installLookAndFeel(); } catch (Exception ignore) { }
        if (uiFontName != null) installUiFonts(new Font(uiFontName, Font.PLAIN, Math.round(13 * UI_SCALE)));
        applyStaticFonts();
        applyScaledIcons();

        SwingUtilities.invokeLater(() -> {
            buildUI();
            autoDetect();
            frame.setVisible(true);
        });
    }

    static String pickCjkFont() {
        try {
            String[] candidates = {"Microsoft YaHei UI", "Microsoft YaHei", "微软雅黑",
                    "SimSun", "宋体", "NSimSun", "Noto Sans CJK SC", "PingFang SC"};
            String[] have = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            java.util.Set<String> set = new java.util.HashSet<>();
            for (String s : have) set.add(s.toLowerCase());
            for (String c : candidates) if (set.contains(c.toLowerCase())) return c;
            return "Dialog";
        } catch (Exception e) { return "Dialog"; }
    }

    static void installLookAndFeel() throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }

    static void installUiFonts(Font base) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement(); Object v = UIManager.get(k);
            if (v instanceof FontUIResource) UIManager.put(k, new FontUIResource(base));
        }
    }

    static void applyStaticFonts() {
        Font f = new Font(uiFontName == null ? "Dialog" : uiFontName, Font.PLAIN, Math.round(13 * UI_SCALE));
        Font small = f.deriveFont(Math.round(12 * UI_SCALE));
        for (Component c : new Component[]{appRootField, productField, backupCheck, blockNetCheck, dryRunCheck,
                logArea, statusLabel, runBtn, detectBtn, seqField, codeField, genBtn, copyBtn,
                blockNetGenCheck, scanNetBtn, patchNetBtn, appTypeBadge, batchRootField, batchScanBtn,
                batchRunBtn, batchMethodCfg, batchMethodPatch, batchTable}) {
            if (c != null) c.setFont(c == logArea ? small : f);
        }
    }

    static void applyScaledIcons() { }

    static void buildUI() {
        frame = new JFrame("LicenseRecover");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        JPanel main = new JPanel();
        main.setBorder(new EmptyBorder(10, 10, 10, 10));
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("应用目录:")); top.add(appRootField); top.add(detectBtn); top.add(appTypeBadge);
        main.add(top);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.add(new JLabel("产品号(仅显示目录解析结果):")); p.add(productField);
        main.add(p);
        mode1Border = BorderFactory.createTitledBorder("方式一：直接写入本地授权（Java 版）");
        mode1Panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mode1Panel.setBorder(mode1Border);
        mode1Hint = new JLabel("注册ID/RegStr无法从目标目录确认时将拒绝执行");
        mode1Panel.add(runBtn); mode1Panel.add(backupCheck); mode1Panel.add(blockNetCheck); mode1Panel.add(dryRunCheck); mode1Panel.add(mode1Hint);
        main.add(mode1Panel);
        JPanel g = new JPanel(new FlowLayout(FlowLayout.LEFT));
        g.setBorder(BorderFactory.createTitledBorder("方式二：生成离线授权码"));
        g.add(new JLabel("申请号:")); g.add(seqField); g.add(genBtn); g.add(copyBtn); g.add(blockNetGenCheck);
        main.add(g);
        codeField.setEditable(false); main.add(codeField);
        JPanel n = new JPanel(new FlowLayout(FlowLayout.LEFT));
        n.setBorder(BorderFactory.createTitledBorder("联网授权处理"));
        n.add(scanNetBtn); n.add(patchNetBtn); main.add(n);
        logArea.setRows(20); logArea.setLineWrap(true); logArea.setEditable(false);
        main.add(new JScrollPane(logArea)); main.add(statusLabel);
        frame.add(main, BorderLayout.CENTER);
        detectBtn.addActionListener(LicenseRecoverGUI::detect);
        runBtn.addActionListener(LicenseRecoverGUI::run);
        genBtn.addActionListener(LicenseRecoverGUI::genCode);
        copyBtn.addActionListener(LicenseRecoverGUI::copyCode);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    static void autoDetect() { }

    static void setAppTypeBadge(String type) {
        currentAppType = type == null ? "" : type;
        updateMode1Ui(type);
        SwingUtilities.invokeLater(() -> {
            if ("DOTNET".equals(type)) {
                appTypeBadge.setText("  .NET 版  ");
                appTypeBadge.setBackground(new Color(0x8a4a8f));
            } else if ("JAVA".equals(type)) {
                appTypeBadge.setText("  Java 版  ");
                appTypeBadge.setBackground(new Color(0x4a6fa5));
            } else {
                appTypeBadge.setText("  待检测  ");
                appTypeBadge.setBackground(new Color(0x9aa0a6));
            }
        });
    }

    static void updateMode1Ui(String type) {
        Runnable r = () -> {
            if (mode1Border == null || mode1Hint == null) return;
            if ("DOTNET".equals(type)) {
                mode1Border.setTitle("方式一：防止软件自动联网校验");
                mode1Hint.setText("只修改授权配置文件，不修改 DLL；执行后重启应用服务");
                runBtn.setText("防止软件自动联网校验");
                blockNetCheck.setEnabled(false);
                blockNetGenCheck.setEnabled(false);
            } else {
                mode1Border.setTitle("方式一：直接写入本地授权（Java 版）");
                mode1Hint.setText("注册ID/RegStr必须从目标目录确认；否则拒绝写入");
                runBtn.setText("生成离线授权并写入");
                blockNetCheck.setEnabled(true);
                blockNetGenCheck.setEnabled(true);
            }
            mode1Panel.revalidate(); mode1Panel.repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }

    static void detect(ActionEvent e) {
        runBtn.setEnabled(false); detectBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                String appRoot = appRootField.getText().trim();
                File rootFile = new File(appRoot);
                String lib = locateLibDir(rootFile);
                if (lib == null) {
                    String dotnetBin = locateDotNetBin(rootFile);
                    if (dotnetBin != null) {
                        setAppTypeBadge("DOTNET");
                        LicenseRecoverModernGUIAutoRecovery.Detection nd =
                                LicenseRecoverModernGUIAutoRecovery.detect(dotNetAppRoot(dotnetBin));
                        String product = nd.productName;
                        String regStr = product == null ? null :
                                LicenseRecoverModernGUIAutoRecovery.detectProductList(new File(dotnetBin, "ITMC.Web.dll"), product);
                        if (product == null || product.trim().isEmpty()) {
                            appendLog("[阻止] 目标 ITMC.Web.dll 未解析出 ProName；不会填入默认产品号。\n");
                            SwingUtilities.invokeLater(() -> { productField.setText(""); productAutoFilled = true; });
                        } else {
                            appendLog("[识别] .NET ProName=" + product + "（来自目标 ITMC.Web.dll）"
                                    + (regStr == null || regStr.trim().isEmpty() ? "；RegStr 未确认" : "；RegStr=" + regStr) + "\n");
                            final String fp = product;
                            SwingUtilities.invokeLater(() -> { productField.setText(fp); productAutoFilled = true; });
                        }
                        return null;
                    }
                    setAppTypeBadge("");
                    appendLog("[错误] 未在 " + appRoot + " 找到授权文件（Java: WEB-INF/lib/ITMCReg*.jar；.NET: ITMC.Regedit.dll）\n");
                    return null;
                }
                setAppTypeBadge("JAVA");
                File appRootDir = new File(lib).getParentFile().getParentFile();
                LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(appRootDir);
                if (!plan.detected || !plan.automaticRecoveryReady) {
                    appendLog("[阻止] Java 注册身份未从目标目录完整确认："
                            + (plan.detected ? plan.recoveryReadiness : "未识别注册结构") + "\n");
                    SwingUtilities.invokeLater(() -> { productField.setText(""); productAutoFilled = true; });
                } else {
                    appendLog("[识别] Java VersionID=" + plan.softVersionId
                            + " RuntimeProductID=" + plan.runtimeProductId
                            + " AuthorizationFamily=" + plan.authorizationFamily
                            + " RegStr=" + plan.regStr + "（均来自目标目录证据）\n");
                    final String fp = plan.runtimeProductId;
                    SwingUtilities.invokeLater(() -> { productField.setText(fp); productAutoFilled = true; });
                }
                appendLog("lib 目录         : " + lib + "\n");
                return null;
            }
            protected void done() { runBtn.setEnabled(true); detectBtn.setEnabled(true); }
        }.execute();
    }

    static void run(ActionEvent e) {
        String appRoot = appRootField.getText().trim();
        String productMain = productField.getText().trim();
        String dotnetBin = locateDotNetBin(new File(appRoot));
        String lib = null;
        if (dotnetBin == null) {
            lib = locateLibDir(new File(appRoot));
            if (lib == null) {
                appendLog("[错误] 请先选择有效的应用根目录。\n"); return;
            }
        }
        runBtn.setEnabled(false); detectBtn.setEnabled(false);
        final String fLib = lib; final String fDotnet = dotnetBin; final String fProduct = productMain;
        new SwingWorker<Boolean, Void>() {
            protected Boolean doInBackground() {
                appendLog("==========================================================\n");
                appendLog(" 应用目录   : " + appRoot + "\n");
                appendLog(" 应用类型   : " + (fDotnet != null ? ".NET 版" : "Java 版") + "\n");
                if (fDotnet != null) return runDotNetBlockNet(appRoot, dryRunCheck.isSelected(), backupCheck.isSelected());
                try {
                    java.util.List<String> cmd = new java.util.ArrayList<>();
                    cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
                    cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-cp");
                    cmd.add(fLib + File.separator + "*" + File.pathSeparator + guiJarDir() + File.separator + "LicenseRecover.jar");
                    cmd.add("LicenseRecover"); cmd.add(appRoot);
                    if (!productAutoFilled && !fProduct.isEmpty()) { cmd.add("-p"); cmd.add(fProduct); }
                    if (dryRunCheck.isSelected()) cmd.add("--dry-run");
                    if (!blockNetCheck.isSelected()) cmd.add("--no-block-net");
                    if (!backupCheck.isSelected()) cmd.add("--no-backup");
                    return runGuiProcess(cmd) == 0;
                } catch (Exception ex) { appendLog("[异常] " + ex + "\n"); return false; }
            }
            protected void done() {
                boolean ok = false; try { ok = Boolean.TRUE.equals(get()); } catch (Exception ignore) { }
                statusLabel.setText(ok ? "状态: 完成 (OK)" : "状态: 失败/预览");
                statusLabel.setForeground(ok ? new Color(0x1a7f37) : new Color(0xb51d1d));
                runBtn.setEnabled(true); detectBtn.setEnabled(true);
            }
        }.execute();
    }

    static void genCode(ActionEvent e) {
        String appRoot = appRootField.getText().trim();
        String seq = seqField.getText().trim();
        genBtn.setEnabled(false); copyBtn.setEnabled(false); lastCode = ""; codeField.setText("");
        final String fDotnet = appRoot.isEmpty() ? null : locateDotNetBin(new File(appRoot));
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                try {
                    if (fDotnet != null) {
                        appendLog("--- 方式二：生成离线授权码（.NET 版） ---\n");
                        String product = LegacyDotNetProtocol.readProductName(new File(fDotnet));
                        if (product == null || product.trim().isEmpty()) {
                            appendLog("[阻止] 目标 ITMC.Web.dll 未解析出 ProName；不会使用固定 itmcIEC/YX0302。\n"); return null;
                        }
                        if (LegacyDotNetProtocol.isLegacyTarget(new File(fDotnet))) {
                            if (seq.trim().isEmpty()) {
                                appendLog("[提示] DS01xx 旧协议必须先从应用本地注册页获取申请号。\n"); return null;
                            }
                            LegacyDotNetProtocol.CodeResult result = LegacyDotNetProtocol.generateAuthorizationCode(
                                    seq.trim(), LegacyDotNetProtocol.readSoftVersion(new File(fDotnet)), product);
                            appendLog("[识别] DS01xx 产品标识=" + product + "（来自目标 ITMC.Web.dll），授权版本="
                                    + result.authorization.product + "\n");
                            appendLog("申请主机码       : " + result.request.regId + "\n");
                            appendLog("申请时间         : " + result.request.requestTime + "\n");
                            appendLog("离线授权码       : " + result.code + "\n");
                            publishGeneratedCode(result.code); return null;
                        }
                        String regStr = LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                new File(fDotnet, "ITMC.Web.dll"), product);
                        if (regStr == null || regStr.trim().isEmpty()) {
                            appendLog("[阻止] 目标 ITMC.Web.dll 未解析出 RegStr 产品项；不会使用 VersionID/默认列表兜底。\n"); return null;
                        }
                        String shownProduct = productField.getText().trim();
                        if (!shownProduct.isEmpty() && !shownProduct.equalsIgnoreCase(product)) {
                            appendLog("[阻止] 界面产品号与目标 DLL 解析结果不一致：" + shownProduct + " != " + product + "\n"); return null;
                        }
                        if (seq.trim().isEmpty() && isAspNetDotNetBin(fDotnet)) {
                            appendLog("[提示] ASP.NET 项目请先在网站本地注册页获取申请号。\n"); return null;
                        }
                        java.util.List<String> cmd = new java.util.ArrayList<>();
                        cmd.add("gencode"); cmd.add(fDotnet);
                        cmd.add("--product"); cmd.add(product);
                        cmd.add("--regstr"); cmd.add(regStr);
                        if (!seq.trim().isEmpty()) { cmd.add("--seq"); cmd.add(seq.trim()); }
                        String helperOutput = runDotNetCapture(cmd, "");
                        String generated = extractGeneratedCode(helperOutput);
                        if (generated != null) publishGeneratedCode(generated);
                        else appendLog("[提示] 未从助手输出中识别到授权码；请检查上方生成结果。\n");
                        return null;
                    }

                    File appRootDir = new File(appRoot);
                    LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(appRootDir);
                    if (!plan.detected || !plan.automaticRecoveryReady) {
                        appendLog("[阻止] Java 注册ID/RegStr未从目标目录确认："
                                + (plan.detected ? plan.recoveryReadiness : "未识别注册结构") + "\n"); return null;
                    }
                    appendLog("--- 方式二：生成离线授权码（Java 版） ---\n");
                    appendLog("目录解析产品族   : " + plan.authorizationFamily + "\n");
                    appendLog("目录解析 RegStr  : " + plan.regStr + "\n");
                    boolean hasSeq = !seq.trim().isEmpty();
                    String[] r = genRegisterCode(hasSeq ? seq : null, plan.authorizationFamily, plan.regStr);
                    String genSeq = r[0], code = r[1], sn = r[2], time19 = r[3];
                    appendLog("申请时间         : " + time19 + "\n");
                    appendLog("注册申请号       : " + genSeq + "\n");
                    appendLog("离线授权码       : " + code + "\n");
                    publishGeneratedCode(code);
                } catch (Exception ex) { appendLog("[错误] " + ex + "\n"); }
                return null;
            }
            protected void done() { genBtn.setEnabled(true); }
        }.execute();
    }

    static void copyCode(ActionEvent e) {
        String c = codeField.getText().trim();
        if (c.isEmpty()) c = lastCode == null ? "" : lastCode.trim();
        if (c.isEmpty()) { appendLog("(当前没有可复制的授权码，请先生成)\n"); return; }
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(c);
        Exception lastError = null;
        for (int i = 0; i < 5; i++) {
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                appendLog("已复制离线授权码到剪贴板。\n"); return;
            } catch (IllegalStateException ex) {
                lastError = ex; try { Thread.sleep(80L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            } catch (Exception ex) { lastError = ex; break; }
        }
        appendLog("[错误] 复制授权码失败：系统剪贴板当前不可用" +
                (lastError == null ? "" : "（" + lastError.getMessage() + "）") + "。请重试。\n");
    }

    static void publishGeneratedCode(String code) {
        if (code == null || code.trim().isEmpty()) return;
        final String value = code.trim(); lastCode = value;
        Runnable update = () -> { codeField.setText(value); codeField.setCaretPosition(0); copyBtn.setEnabled(true); };
        if (SwingUtilities.isEventDispatchThread()) update.run(); else SwingUtilities.invokeLater(update);
    }

    static String extractGeneratedCode(String output) {
        if (output == null || output.isEmpty()) return null;
        Matcher m = GENERATED_CODE_PATTERN.matcher(output); String found = null;
        while (m.find()) found = m.group(1); return found;
    }

    static String resolveAppRoot() {
        String appRoot = appRootField.getText().trim();
        File root = new File(appRoot);
        if (new File(root, "WEB-INF").isDirectory()) return root.getAbsolutePath();
        String lib = locateLibDir(root);
        if (lib != null) return new File(lib).getParentFile().getParentFile().getAbsolutePath();
        return root.getAbsolutePath();
    }

    // ---- Remaining helper methods kept compact and behavior-compatible ----
    static String locateDotNetBin(File start) { return LicenseRecover.locateDotNetBin(start == null ? null : start.getAbsolutePath()); }
    static File dotNetAppRoot(String binDir) { return LicenseRecover.dotNetAppRoot(binDir); }
    static boolean isAspNetDotNetBin(String binDir) { return LicenseRecover.isAspNetDotNetBin(binDir); }
    static String guiJarDir() { return LicenseRecover.jarDir(); }

    static boolean runDotNetBlockNet(String appRoot, boolean dry, boolean backup) {
        String bin = locateDotNetBin(new File(appRoot));
        return bin != null && LicenseRecover.blockDotNetNet(bin, dry, backup) == 0;
    }

    static int runGuiProcess(java.util.List<String> cmd) { return LicenseRecover.runDotNetProcess(cmd); }

    static String runDotNetCapture(java.util.List<String> args, String ignored) {
        File helper = LicenseRecover.locateNetHelper();
        if (helper == null) { appendLog("[错误] 未找到 LicenseRecover.NET.exe\n"); return null; }
        java.util.List<String> cmd = new java.util.ArrayList<>(); cmd.add(helper.getAbsolutePath()); cmd.addAll(args);
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
            java.io.InputStream in = p.getInputStream(); while ((n = in.read(b)) >= 0) out.write(b, 0, n); p.waitFor();
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8); appendLog(text); return text;
        } catch (Exception ex) { appendLog("[错误] 调用 .NET helper 失败: " + ex + "\n"); return null; }
    }

    static void appendLog(String text) {
        if (text == null) return;
        if (logFilePrint != null) { logFilePrint.print(text); logFilePrint.flush(); }
        Runnable r = () -> { logArea.append(text); logArea.setCaretPosition(logArea.getDocument().getLength()); };
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }

    static void initLogFile() {
        try {
            File dir = new File(LicenseRecover.jarDir(), "logs"); dir.mkdirs();
            File f = new File(dir, "LicenseRecoverGUI_" + new java.text.SimpleDateFormat("yyyyMMdd").format(new Date()) + ".log");
            logFileStream = new FileOutputStream(f, true); logFilePrint = new PrintStream(logFileStream, true, "UTF-8");
        } catch (Exception ignore) { }
    }

    static class LogOutputStream extends OutputStream {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        public void write(int b) throws IOException { if (b == '\n') flush(); else buf.write(b); }
        public void flush() throws IOException { if (buf.size() == 0) return; String s = new String(buf.toByteArray(), StandardCharsets.UTF_8); buf.reset(); appendLog(s + "\n"); }
    }
}
