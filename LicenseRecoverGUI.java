/*
 * ITMC 云实训平台 - 离线授权恢复工具 (GUI 版)
 *
 * 兼容: Windows 7 / Windows Server 2008 / JRE 8 (Swing/AWT)
 * 功能: 选择应用根目录 -> 自动识别产品号 -> Java 写入本地授权；.NET 方式一
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

    static final String ALL_NUMS =
        "QT100103,QT100107,QT100105,QT100109,QT100112,QT100108,"
      + "QT0436,QT0421,QT0420,QT0437,QT0424,QT0438,QT0435,QT0445,QT0441,ZGZF11,"
      + "PT0212,PT0208,PT0210,QT0454,PT0202,QT0456,PT0301,QT0447,PT0303,"
      + "YT00124,YT00125,YT00142,YT00143,YT00123,YT00147,YT00148,YT00149,YT00150,"
      + "YT00128,YT00127,YT00129,YT00139,YT00132,YT00141,BKSM4,YT00126,YT00154,YT001";

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
                        // 兼容 "global.system.VersionID: xxx"（冒号）与 "global.system.VersionID=xxx"（等号）
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

    /** 新架构 QT3xxx 标志：webapp 根存在 data/config.xml（其 SystemSoft/SoftVersionID 决定产品号与 PRODUCT_ALL_NUM）。 */
    static boolean isNewStyleApp(File appRoot) {
        return new File(appRoot, "data" + File.separator + "config.xml").isFile();
    }

    static String productMainFor(String softId) {
        if (softId == null) return "QT1001";
        switch (softId) {
            case "YT00128": case "YT00127": case "YT00129":
            case "YT00139": case "YT00132": case "YT00141":
            case "YT00126": case "YT00154": case "BKSM4":
                return "QT04";
            default:
                return "QT1001";
        }
    }

    /**
     * 方式二: 生成离线授权码。
     * @return {申请号, 授权码, 主板号, 申请时间}
     */
    static String[] genRegisterCode(String seq, String registerProductID) throws Exception {
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
        String plain = "00" + time19 + "00" + sn + "00" + end + "00" + "1" + "00" + "-001" + "00" + "-1" + "00" + ALL_NUMS;
        String code = rc.encrypt("itmc" + registerProductID, plain);
        return new String[]{seq, code, sn, time19};
    }

    // ---- GUI ----

    static JFrame frame;
    static String uiFontName = null;
    static JTextField appRootField = new JTextField(38);
    static JTextField productField = new JTextField(8);
    /** 产品号是否为「检测环境」自动填充的（自动填充的不作为批量/恢复的全局覆盖；仅手动输入才传 -p）。 */
    static boolean productAutoFilled;
    static JCheckBox backupCheck = new JCheckBox("写入前备份原配置", true);
    static JCheckBox blockNetCheck = new JCheckBox("Java方式一把授权服务地址指向本地", true);
    static JCheckBox dryRunCheck = new JCheckBox("只预览不写入");
    static JTextArea logArea = new JTextArea();
    // 日志文件流：所有日志同时写入 logs 目录下的日期文件，真实记录每次成功/失败
    static java.io.OutputStream logFileStream;
    static java.io.PrintStream logFilePrint;
    static JLabel statusLabel = new JLabel("就绪");
    static JButton runBtn = new JButton("生成离线授权并写入");
    static JButton detectBtn = new JButton("检测环境");
    static TitledBorder mode1Border;
    static JLabel mode1Hint;
    static JPanel mode1Panel;

    // 方式二: 生成离线授权码
    static JTextField seqField = new JTextField(34);
    static JTextField codeField = new JTextField();
    static JButton genBtn = new JButton("生成离线授权码");
    static JButton copyBtn = new JButton("复制授权码");
    static JCheckBox blockNetGenCheck = new JCheckBox("生成时顺带写入「阻止联网」配置", true);
    static volatile String lastCode = "";
    static final Pattern GENERATED_CODE_PATTERN = Pattern.compile(
            "(?m)^\\s*离线授权码\\s*[:：]\\s*([0-9A-Fa-f]+)\\s*$");

    // 方式三: 移除联网授权代码
    static JButton scanNetBtn = new JButton("扫描识别联网授权文件");
    static JButton patchNetBtn = new JButton("移除联网授权并回写");

    // 应用类型徽标
    static JLabel appTypeBadge = new JLabel("待检测");
    static String currentAppType = "";   // "JAVA" / "DOTNET" / ""

    // 批量应用面板
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
    // 批量扫描到的应用: {name, type, appRoot, libDir, binDir}
    static java.util.List<Object[]> batchTargets = new java.util.ArrayList<>();

    // 高分屏 DPI 缩放系数（Java 8 需手动缩放字体；Java 9+ 由 JVM 自动缩放，此处保持 1）
    static float UI_SCALE = 1.0f;

    /** 计算 UI 缩放系数：Windows DPI / 96。Java 9+ 返回 1（JVM 自带 DPI 缩放，避免双倍缩放）。 */
    static float uiScaleFactor() {
        try {
            String v = System.getProperty("java.version", "8");
            int major;
            if (v.startsWith("1.")) major = Integer.parseInt(v.substring(2, v.indexOf('.', 2)));
            else major = Integer.parseInt(v.substring(0, v.indexOf('.')));
            if (major > 8) return 1.0f;                       // Java 9+ 自动缩放
            double s = Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
            return (float) Math.max(1.0, Math.min(s, 2.5));   // 100%..250%
        } catch (Exception e) { return 1.0f; }
    }

    public static void main(String[] args) {
        // 显示修复: 文字抗锯齿 + 高DPI感知 (必须在 AWT 初始化前设置)
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.dpiaware", "true");
        UI_SCALE = uiScaleFactor();
        // 选中一个带中文字形的字体(微软雅黑/宋体等), 避免中文显示成方框
        uiFontName = pickCjkFont();
        // 打开日志文件（logs 目录），之后所有输出都记录
        initLogFile();

        // 捕获应用类打到 System.out/err 的输出显示到日志区; 显式指定 UTF-8,
        // 避免直接双击 jar 运行时(未设 -Dfile.encoding)按 GBK 输出导致乱码
        try {
            System.setOut(new PrintStream(new LogOutputStream(), true, "UTF-8"));
            System.setErr(new PrintStream(new LogOutputStream(), true, "UTF-8"));
        } catch (Exception ignore) { }

        try {
            installLookAndFeel();
        } catch (Exception ignore) { }
        if (uiFontName != null) {
            installUiFonts(new Font(uiFontName, Font.PLAIN, Math.round(13 * UI_SCALE)));
        }
        // 静态字段组件在 installUiFonts 之前初始化，字体仍是默认小字号，这里统一补齐缩放
        applyStaticFonts();
        applyScaledIcons();

        SwingUtilities.invokeLater(() -> {
            buildUI();
            autoDetect();
            frame.setVisible(true);
        });
    }

    /** 从候选列表中挑一个本机存在、且带中文字形的字体 */
    static String pickCjkFont() {
        try {
            String[] candidates = {"Microsoft YaHei UI", "Microsoft YaHei", "微软雅黑",
                    "SimSun", "宋体", "NSimSun", "Noto Sans CJK SC", "PingFang SC"};
            String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String c : candidates) {
                for (String a : available) {
                    if (a.equalsIgnoreCase(c)) return c;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    /** 使用系统原生外观(Windows) */
    static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) { }
    }

    /** 统一设置界面字体(含按钮/标签/输入框等), 保证中文正常显示且观感一致 */
    static void installUiFonts(Font f) {
        FontUIResource fr = new FontUIResource(f);
        String[] keys = {"defaultFont", "Button.font", "ToggleButton.font", "RadioButton.font",
            "CheckBox.font", "Label.font", "TextField.font", "TextArea.font", "PasswordField.font",
            "ComboBox.font", "List.font", "Table.font", "TableHeader.font", "Tree.font",
            "TabbedPane.font", "Menu.font", "MenuItem.font", "CheckBoxMenuItem.font",
            "ToolTip.font", "OptionPane.font", "TitledBorder.font", "Panel.font"};
        for (String k : keys) {
            try { UIManager.put(k, fr); } catch (Exception ignore) { }
        }
    }

    /** 静态字段组件在 installUiFonts 之前创建，字体仍是默认小字号；这里统一补上 DPI 缩放字体（保留各自风格）。 */
    static void applyStaticFonts() {
        Font scaled = new Font(uiFontName != null ? uiFontName : "Dialog", Font.PLAIN, Math.round(13 * UI_SCALE));
        JComponent[] comps = {
            runBtn, detectBtn, genBtn, copyBtn, scanNetBtn, patchNetBtn,
            batchScanBtn, batchRunBtn, batchMethodCfg, batchMethodPatch,
            productField, appRootField, seqField, codeField, backupCheck,
            blockNetCheck, dryRunCheck, blockNetGenCheck, appTypeBadge, statusLabel, batchRootField
        };
        for (JComponent c : comps) {
            if (c != null) c.setFont(scaled.deriveFont(c.getFont().getStyle()));
        }
    }

    /** 自绘标准大小勾选框/单选框图标（尺寸=文字字号，比例与原生一致），随 DPI 缩放。 */
    static class OptionIcon implements Icon {
        final int size;
        final boolean radio;
        OptionIcon(int size, boolean radio) { this.size = size; this.radio = radio; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            boolean sel = (c instanceof AbstractButton) && ((AbstractButton) c).isSelected();
            float stroke = Math.max(1.1f, size / 14f);
            if (radio) {
                g2.setColor(new Color(0xffffff));
                g2.fillOval(0, 0, size, size);
                g2.setColor(new Color(0x666666));
                g2.setStroke(new BasicStroke(stroke));
                g2.drawOval(0, 0, size, size);
                if (sel) {
                    g2.setColor(new Color(0x2b7cd6));
                    g2.fillOval(Math.round(size * 0.25f), Math.round(size * 0.25f),
                                Math.round(size * 0.5f), Math.round(size * 0.5f));
                }
            } else {
                int r = Math.round(size * 0.18f);
                g2.setColor(new Color(0xffffff));
                g2.fillRoundRect(0, 0, size, size, r, r);
                g2.setColor(new Color(0x666666));
                g2.setStroke(new BasicStroke(stroke));
                g2.drawRoundRect(0, 0, size, size, r, r);
                if (sel) {
                    g2.setColor(new Color(0x2b7cd6));
                    g2.setStroke(new BasicStroke(Math.max(1.6f, size / 7f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int[] xs = { Math.round(size*0.22f), Math.round(size*0.42f), Math.round(size*0.82f) };
                    int[] ys = { Math.round(size*0.52f), Math.round(size*0.72f), Math.round(size*0.30f) };
                    g2.drawPolyline(xs, ys, 3);
                }
            }
            g2.dispose();
        }
    }

    static void applyScaledIcons() {
        int box = Math.max(13, Math.round(13 * UI_SCALE));   // 方框尺寸=文字字号，随 DPI 缩放
        UIManager.put("CheckBox.icon", new OptionIcon(box, false));
        UIManager.put("RadioButton.icon", new OptionIcon(box, true));
        for (AbstractButton c : new AbstractButton[]{ backupCheck, blockNetCheck, dryRunCheck, blockNetGenCheck })
            if (c != null) c.setIcon(new OptionIcon(box, false));
        for (AbstractButton c : new AbstractButton[]{ batchMethodCfg, batchMethodPatch })
            if (c != null) c.setIcon(new OptionIcon(box, true));
    }

    static void buildUI() {
        frame = new JFrame("ITMC 离线授权恢复工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 顶部横幅: 标题 + 应用类型徽标
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("ITMC 离线授权恢复工具（Java 版 / .NET 版）");
        title.setFont(new Font(uiFontName != null ? uiFontName : "Dialog", Font.BOLD, Math.round(16 * UI_SCALE)));
        appTypeBadge.setOpaque(true);
        appTypeBadge.setBackground(new Color(0x4a6fa5));
        appTypeBadge.setForeground(Color.WHITE);
        appTypeBadge.setBorder(BorderFactory.createEmptyBorder(Math.round(4*UI_SCALE), Math.round(12*UI_SCALE), Math.round(4*UI_SCALE), Math.round(12*UI_SCALE)));
        appTypeBadge.setFont(new Font(uiFontName != null ? uiFontName : "Dialog", Font.BOLD, Math.round(13 * UI_SCALE)));
        header.add(title, BorderLayout.WEST);
        header.add(appTypeBadge, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("单个应用", new JScrollPane(buildSinglePanel()));
        tabs.addTab("批量应用", buildBatchPanel());
        root.add(tabs, BorderLayout.CENTER);

        // 底部: 共享日志 + 状态
        logArea.setEditable(false);
        logArea.setFont(new Font(uiFontName != null ? uiFontName : "Monospaced", Font.PLAIN, Math.round(13 * UI_SCALE)));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(Math.round(780*UI_SCALE), Math.round(190*UI_SCALE)));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(scroll, BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(Math.round(4*UI_SCALE), 2, 0, 0));
        bottom.add(statusLabel, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.pack();
        // 高分屏/小屏适配: 窗口不超出屏幕
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(Math.min(frame.getWidth(), scr.width - 40),
                      Math.min(frame.getHeight(), scr.height - 60));
        frame.setLocationRelativeTo(null);
    }

    static JPanel buildSinglePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        panel.add(new JLabel("应用目录:"), c);
        c.gridx = 1; c.weightx = 1;
        panel.add(appRootField, c);
        c.gridx = 2; c.weightx = 0;
        JButton browseBtn = new JButton("选择...");
        browseBtn.addActionListener(LicenseRecoverGUI::browse);
        panel.add(browseBtn, c);
        c.gridx = 3;
        detectBtn.addActionListener(LicenseRecoverGUI::detect);
        panel.add(detectBtn, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        panel.add(new JLabel("产品号:"), c);
        c.gridx = 1; c.weightx = 1;
        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p2.add(productField);
        p2.add(backupCheck);
        p2.add(blockNetCheck);
        p2.add(dryRunCheck);
        panel.add(p2, c);
        // 用户手动编辑产品号 → 视为全局覆盖（恢复/批量时作为 -p 传给 CLI）；自动填充的不覆盖
        productField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { productAutoFilled = false; }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { productAutoFilled = false; }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { productAutoFilled = false; }
        });
        c.gridx = 2; c.gridwidth = 2; c.weightx = 0;
        panel.add(new JLabel("(自动识别, 可改)"), c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 4; c.weightx = 1;
        JPanel w1 = new JPanel(new BorderLayout());
        mode1Panel = w1;
        mode1Border = BorderFactory.createTitledBorder("方式一：直接写入本地授权（Java 版）");
        w1.setBorder(mode1Border);
        JPanel w1b = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        runBtn.addActionListener(LicenseRecoverGUI::run);
        runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD));
        w1b.add(runBtn);
        mode1Hint = new JLabel("写 config.xml（regType=1 + regName）");
        w1b.add(mode1Hint);
        w1.add(w1b, BorderLayout.WEST);
        panel.add(w1, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 4; c.weightx = 1;
        JPanel w2 = new JPanel(new GridBagLayout());
        w2.setBorder(BorderFactory.createTitledBorder("方式二：生成离线授权码（在应用「本地注册」界面填写）"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        w2.add(new JLabel("注册申请号（Java 可留空；ASP.NET 需先从网站获取）:"), g);
        g.gridx = 1; g.weightx = 1;
        w2.add(seqField, g);
        g.gridx = 2; g.weightx = 0;
        JPanel gb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        genBtn.addActionListener(LicenseRecoverGUI::genCode);
        copyBtn.addActionListener(LicenseRecoverGUI::copyCode);
        gb.add(genBtn);
        w2.add(gb, g);
        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        w2.add(new JLabel("离线授权码:"), g);
        g.gridx = 1; g.gridwidth = 2; g.weightx = 1;
        codeField.setEditable(false);
        JPanel codeBox = new JPanel(new BorderLayout(6, 0));
        codeBox.add(codeField, BorderLayout.CENTER);
        codeBox.add(copyBtn, BorderLayout.EAST);
        w2.add(codeBox, g);
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3; g.weightx = 1;
        JPanel gopt = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        gopt.add(blockNetGenCheck);
        w2.add(gopt, g);
        panel.add(w2, c);

        c.gridx = 0; c.gridy = 4; c.gridwidth = 4; c.weightx = 1;
        JPanel w3 = new JPanel(new BorderLayout());
        w3.setBorder(BorderFactory.createTitledBorder("方式三：移除联网授权代码（暴力，改写字节码）"));
        JPanel w3b = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        scanNetBtn.addActionListener(LicenseRecoverGUI::scanNet);
        patchNetBtn.addActionListener(LicenseRecoverGUI::removeNet);
        patchNetBtn.setFont(patchNetBtn.getFont().deriveFont(Font.BOLD));
        w3b.add(scanNetBtn);
        w3b.add(patchNetBtn);
        w3b.add(new JLabel("(执行前请停止应用服务; 自动备份后替换)"));
        w3.add(w3b, BorderLayout.WEST);
        panel.add(w3, c);

        return panel;
    }

    static JPanel buildBatchPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        top.add(new JLabel("父目录(每个子目录=一个软件):"), c);
        c.gridx = 1; c.weightx = 1;
        top.add(batchRootField, c);
        c.gridx = 2; c.weightx = 0;
        JButton bbrowse = new JButton("选择...");
        bbrowse.addActionListener(LicenseRecoverGUI::batchBrowse);
        top.add(bbrowse, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        top.add(new JLabel("方式:"), c);
        c.gridx = 1; c.weightx = 1;
        JPanel meth = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        batchMethodGroup.add(batchMethodCfg); batchMethodGroup.add(batchMethodPatch);
        meth.add(batchMethodCfg); meth.add(batchMethodPatch);
        top.add(meth, c);
        c.gridx = 2; c.weightx = 0;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        batchScanBtn.addActionListener(LicenseRecoverGUI::batchScan);
        batchRunBtn.addActionListener(LicenseRecoverGUI::batchRun);
        batchRunBtn.setFont(batchRunBtn.getFont().deriveFont(Font.BOLD));
        btns.add(batchScanBtn); btns.add(batchRunBtn);
        top.add(btns, c);
        panel.add(top, BorderLayout.NORTH);

        batchTable.setModel(batchModel);
        try {
            int[] w = { 90, 60, 80, 430 };
            for (int i = 0; i < w.length; i++)
                batchTable.getColumnModel().getColumn(i).setPreferredWidth(Math.round(w[i] * UI_SCALE));
        } catch (Exception ignore) { }
        JScrollPane ts = new JScrollPane(batchTable);
        ts.setBorder(BorderFactory.createTitledBorder("识别到的应用"));
        panel.add(ts, BorderLayout.CENTER);
        return panel;
    }

    static void browse(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择应用目录（Java: 含 WEB-INF；.NET: 含 itmcRegedit.dll 的 bin）");
        String cur = appRootField.getText().trim();
        if (!cur.isEmpty()) chooser.setSelectedFile(new File(cur));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            appRootField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    static void autoDetect() {
        // 从当前工作目录向上查找
        File start = new File(System.getProperty("user.dir"));
        String lib = locateLibDir(start);
        if (lib != null) {
            File appRoot = new File(lib).getParentFile().getParentFile();
            appRootField.setText(appRoot.getAbsolutePath());
            detect(null);
        } else {
            appendLog("提示: 请在下方选择应用根目录(部署了软件的目录，包含 WEB-INF)，然后点[检测环境]。\n");
        }
    }

    static void setAppTypeBadge(String type) {
        currentAppType = type;
        updateMode1Ui(type);
        // 可能在后台线程调用（detect 的 SwingWorker），组件更新必须回到 EDT
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

    /** 方式一随应用类型切换含义：.NET 只阻断配置中的联网校验，Java 仍写入本地授权。 */
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
                mode1Hint.setText("写 config.xml（regType=1 + regName）");
                runBtn.setText("生成离线授权并写入");
                blockNetCheck.setEnabled(true);
                blockNetGenCheck.setEnabled(true);
            }
            mode1Panel.revalidate();
            mode1Panel.repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    static void detect(ActionEvent e) {
        runBtn.setEnabled(false);
        detectBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                String appRoot = appRootField.getText().trim();
                File rootFile = new File(appRoot);
                String lib = locateLibDir(rootFile);
                if (lib == null) {
                    String dotnetBin = locateDotNetBin(rootFile);
                    if (dotnetBin != null) {
                        setAppTypeBadge("DOTNET");
                        appendLog("[识别] 检测到 .NET 版应用，bin 目录 = " + dotnetBin + "\n");
                        String legacyVersion = LegacyDotNetProtocol.readSoftVersion(new File(dotnetBin));
                        if (LegacyDotNetProtocol.isLegacyVersion(legacyVersion)) {
                            appendLog("[识别] " + legacyVersion + " 使用旧版 itmcIEC 授权协议；方式二将使用 itmc + itmcIEC 密钥生成授权码。\n");
                            SwingUtilities.invokeLater(() -> { productField.setText(LegacyDotNetProtocol.PRODUCT_NAME); productAutoFilled = true; });
                        } else {
                            appendLog("[识别] 产品号默认 YX0302（若为其他产品请在「产品号」手动填写后，将自动传给 .NET 助手）\n");
                            SwingUtilities.invokeLater(() -> { productField.setText("YX0302"); productAutoFilled = true; });
                        }
                        return null;
                    }
                    setAppTypeBadge("");
                    appendLog("[错误] 未在 " + appRoot + " 找到授权文件（Java: WEB-INF/lib/ITMCReg*.jar；.NET: itmcRegedit.dll）\n");
                    return null;
                }
                setAppTypeBadge("JAVA");
                File appRootDir = new File(lib).getParentFile().getParentFile();
                String softId = readSoftId(appRootDir);
                boolean newStyle = isNewStyleApp(appRootDir);
                String mapped;
                if (newStyle && softId != null && !softId.isEmpty()) {
                    mapped = softId.trim();   // 新架构 QT3xxx：直接用原始产品号
                    appendLog("[识别] 新架构(data/config.xml 存在)，产品号 = " + mapped + "\n");
                } else {
                    mapped = productMainFor(softId);
                    if (softId != null && !softId.isEmpty()) {
                        appendLog("[识别] systemConfig.yml VersionID = " + softId + "  -> 产品主编号 " + mapped + "\n");
                    } else {
                        appendLog("[识别] 未找到 systemConfig.yml，使用默认产品主编号 " + mapped + "\n");
                    }
                }
                SwingUtilities.invokeLater(() -> { productField.setText(mapped); productAutoFilled = true; });
                try {
                    String sn = DesUtil.getMotherboardSN();
                    appendLog("主机码(主板号)   : " + sn + "\n");
                } catch (Exception ex) {
                    appendLog("主机码获取异常: " + ex + "\n");
                }
                appendLog("lib 目录         : " + lib + "\n");
                return null;
            }
            protected void done() {
                runBtn.setEnabled(true);
                detectBtn.setEnabled(true);
            }
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
                appendLog("[错误] 请先选择有效的应用根目录（Java: 含 WEB-INF/lib/ITMCReg*.jar；.NET: 含 itmcRegedit.dll 的 bin）。\n");
                return;
            }
        }
        if (productMain.isEmpty()) productMain = (dotnetBin != null) ? "YX0302" : "QT1001";
        runBtn.setEnabled(false);
        detectBtn.setEnabled(false);
        final String fLib = lib;
        final String fDotnet = dotnetBin;
        final String fProduct = productMain;
        new SwingWorker<Boolean, Void>() {
            protected Boolean doInBackground() {
                appendLog("==========================================================\n");
                appendLog(" 应用目录   : " + appRoot + "\n");
                appendLog(" 应用类型   : " + (fDotnet != null ? ".NET 版" : "Java 版") + "\n");
                if (fDotnet != null) {
                    appendLog("方式一：防止软件自动联网校验（只修改授权配置文件，不修改 DLL）\n");
                    return runDotNetBlockNet(appRoot, dryRunCheck.isSelected(), backupCheck.isSelected());
                }
                appendLog(" 产品主编号 : " + fProduct + "\n");
                // Java 版走 CLI 子进程（带该应用 WEB-INF/lib classpath）：复用完整恢复逻辑，
                // 避免 GUI 进程内加载应用 itmc.regedit 所需的 axis/dom4j 等依赖缺失。
                try {
                    java.util.List<String> cmd = new java.util.ArrayList<>();
                    cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
                    cmd.add("-Dfile.encoding=UTF-8");
                    cmd.add("-cp");
                    cmd.add(fLib + File.separator + "*" + File.pathSeparator + guiJarDir() + File.separator + "LicenseRecover.jar");
                    cmd.add("LicenseRecover");
                    cmd.add(appRoot);
                    // 仅手动输入的产品号作为 -p 覆盖；自动识别的不传，让 CLI 按应用自身识别
                    if (!productAutoFilled && !fProduct.isEmpty()) { cmd.add("-p"); cmd.add(fProduct); }
                    if (dryRunCheck.isSelected()) cmd.add("--dry-run");
                    if (!blockNetCheck.isSelected()) cmd.add("--no-block-net");
                    if (!backupCheck.isSelected()) cmd.add("--no-backup");
                    return runGuiProcess(cmd) == 0;
                } catch (Exception ex) {
                    appendLog("[异常] " + ex + "\n");
                    return false;
                }
            }
            protected void done() {
                boolean ok = false;
                try { ok = Boolean.TRUE.equals(get()); } catch (Exception ignore) { }
                statusLabel.setText(ok ? "状态: 完成 (OK)" : "状态: 失败/预览");
                statusLabel.setForeground(ok ? new Color(0x1a7f37) : new Color(0xb51d1d));
                runBtn.setEnabled(true);
                detectBtn.setEnabled(true);
            }
        }.execute();
    }

    static void genCode(ActionEvent e) {
        String appRoot = appRootField.getText().trim();
        String seq = seqField.getText().trim();
        genBtn.setEnabled(false);
        copyBtn.setEnabled(false);
        lastCode = "";
        codeField.setText("");
        final String fDotnet = appRoot.isEmpty() ? null : locateDotNetBin(new File(appRoot));
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                try {
                    if (fDotnet != null) {
                        appendLog("--- 方式二：生成离线授权码（.NET 版） ---\n");
                        if (LegacyDotNetProtocol.isLegacyTarget(new File(fDotnet))) {
                            if (seq.trim().isEmpty()) {
                                appendLog("[提示] 检测到 DS01xx 旧协议。请先在应用「本地注册」页获取申请号，再填入上方申请号后生成授权码。\n");
                                return null;
                            }
                            LegacyDotNetProtocol.CodeResult result =
                                    LegacyDotNetProtocol.generateAuthorizationCode(seq.trim(),
                                            LegacyDotNetProtocol.readSoftVersion(new File(fDotnet)));
                            appendLog("[识别] DS01xx 旧协议，产品标识 = "
                                    + LegacyDotNetProtocol.PRODUCT_NAME
                                    + "，授权版本 = " + result.authorization.product + "\n");
                            appendLog("申请主机码       : " + result.request.regId + "\n");
                            appendLog("申请时间         : " + result.request.requestTime + "\n");
                            appendLog("授权截止日期     : " + result.endDate + "\n");
                            appendLog("注册申请号       : " + result.request.ciphertext + "\n");
                            appendLog("离线授权码       : " + result.code + "\n");
                            appendLog("RESULT: OK —— 将上面的离线授权码粘贴到该 DS01xx 应用「本地注册」页提交。\n");
                            publishGeneratedCode(result.code);
                            return null;
                        }
                        String product = productField.getText().trim();
                        if (seq.trim().isEmpty() && isAspNetDotNetBin(fDotnet)) {
                            appendLog("[提示] ASP.NET 项目不能在工具进程内自动取申请号；请先在网站的本地注册页获取申请号，再填入申请号生成离线授权码。\n");
                            return null;
                        }
                        java.util.List<String> cmd = new java.util.ArrayList<>();
                        cmd.add("gencode");
                        cmd.add(fDotnet);
                        if (!seq.trim().isEmpty()) { cmd.add("--seq"); cmd.add(seq.trim()); }
                        if (!product.isEmpty() && !product.equals("YX0302")) { cmd.add("--product"); cmd.add(product); }
                        if (blockNetGenCheck.isSelected()) {
                            appendLog("（提示：.NET 方式二只生成授权码；如需阻止自动联网校验，请执行方式一。）\n");
                        }
                        String helperOutput = runDotNetCapture(cmd, "");
                        String generated = extractGeneratedCode(helperOutput);
                        if (generated != null) {
                            publishGeneratedCode(generated);
                            appendLog("已将生成的授权码填入下方结果框，可直接点击「复制授权码」。\n");
                        } else if (helperOutput != null) {
                            appendLog("[提示] 未从助手输出中识别到授权码；请检查上方生成结果。\n");
                        }
                        return null;
                    }
                    String registerPid = "YT001";
                    File appRootDir = new File(appRoot);
                    if (!appRoot.isEmpty()) {
                        String softId = readSoftId(appRootDir);
                        if (softId != null && !softId.isEmpty()) {
                            // 新架构直接用原始产品号；经典仍用 YT001/DS26（注册界面专用产品）
                            if (isNewStyleApp(appRootDir)) {
                                registerPid = softId.trim();
                            } else {
                                registerPid = "DS2601".equals(softId) ? "DS26" : "YT001";
                            }
                        }
                    }
                    appendLog("--- 方式二：生成离线授权码 ---\n");
                    appendLog("注册码产品号     : " + registerPid + "\n");
                    boolean hasSeq = !seq.trim().isEmpty();
                    String[] r = genRegisterCode(hasSeq ? seq : null, registerPid);
                    String genSeq = r[0], code = r[1], sn = r[2], time19 = r[3];
                    // 本机主板号仅用于"是否跨机器"的提示文案；读取失败(WMI/wmic 受限)时置空，
                    // 绝不能抛出导致已生成的授权码结果丢失
                    String localSn;
                    try { localSn = DesUtil.getMotherboardSN(); }
                    catch (Exception ex) { localSn = ""; }
                    if (hasSeq) {
                        if (sn.equals(localSn)) {
                            appendLog("申请号来自本机，授权码将绑定本机主机码 " + sn + "。\n");
                        } else {
                            appendLog("申请号主机码是 " + sn + "，与本机(" + localSn + ")不同——跨机器使用，属正常。\n");
                            appendLog("授权码将绑定申请号所属的那台服务器。\n");
                        }
                    } else {
                        appendLog("（未填申请号，授权码已绑定本机主机码 " + sn + "，仅能在本机激活；\n");
                        appendLog("  若目标软件在云服务器：先在服务器「本地注册」页点\"获取申请号\"，\n");
                        appendLog("  把申请号填到上方输入框再点生成。）\n");
                    }
                    appendLog("申请时间         : " + time19 + "\n");
                    appendLog("注册申请号       : " + genSeq + "\n");
                    appendLog("离线授权码       : " + code + "\n");
                    if (hasSeq && !sn.equals(localSn)) {
                        appendLog("RESULT: OK —— 复制上面的 离线授权码，到服务器「本地注册」页的授权码框粘贴提交即可\n");
                        appendLog("  （申请号用服务器页面上获取的那个，无需再填工具输出的申请号）。\n");
                    } else {
                        appendLog("RESULT: OK —— 把上面的 注册申请号 + 离线授权码 填入应用「本地注册」界面提交。\n");
                    }
                    publishGeneratedCode(code);
                    if (blockNetGenCheck.isSelected() && !appRoot.isEmpty()) {
                        String lib = locateLibDir(appRootDir);
                        if (lib != null) {
                            String sep = lib.endsWith(File.separator) ? lib : lib + File.separator;
                            new XMLFiles("config.xml", sep).writeConfig("reg", "Service", "http://127.0.0.1:9/Service.asmx");
                            appendLog("[可选] 已写入 reg/Service=127.0.0.1:9，阻止残留的联网请求。\n");
                        }
                    }
                } catch (Exception ex) {
                    appendLog("[错误] " + ex + "\n");
                }
                return null;
            }
            protected void done() {
                genBtn.setEnabled(true);
            }
        }.execute();
    }

    static void copyCode(ActionEvent e) {
        String c = codeField.getText().trim();
        if (c.isEmpty()) c = lastCode == null ? "" : lastCode.trim();
        if (c.isEmpty()) {
            appendLog("(当前没有可复制的授权码，请先生成)\n");
            return;
        }
        java.awt.datatransfer.StringSelection selection =
                new java.awt.datatransfer.StringSelection(c);
        Exception lastError = null;
        for (int i = 0; i < 5; i++) {
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(selection, null);
                appendLog("已复制离线授权码到剪贴板。\n");
                return;
            } catch (IllegalStateException ex) {
                // Windows 剪贴板被其它程序短暂占用时稍等后重试，避免一次失败让按钮看起来失效。
                lastError = ex;
                try { Thread.sleep(80L); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception ex) {
                lastError = ex;
                break;
            }
        }
        appendLog("[错误] 复制授权码失败：系统剪贴板当前不可用" +
                (lastError == null ? "" : "（" + lastError.getMessage() + "）") + "。请重试。\n");
    }

    /** 统一更新授权码结果框；同时保留缓存，避免生成完成与 EDT 刷新之间点击复制时取到空值。 */
    static void publishGeneratedCode(String code) {
        if (code == null || code.trim().isEmpty()) return;
        final String value = code.trim();
        lastCode = value;
        Runnable update = () -> {
            codeField.setText(value);
            codeField.setCaretPosition(0);
            copyBtn.setEnabled(true);
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    /** 从内嵌 .NET 助手的标准输出提取授权码，兼容方式二的长十六进制结果。 */
    static String extractGeneratedCode(String output) {
        if (output == null || output.isEmpty()) return null;
        Matcher m = GENERATED_CODE_PATTERN.matcher(output);
        String found = null;
        while (m.find()) found = m.group(1);
        return found;
    }

    static String resolveAppRoot() {
        String appRoot = appRootField.getText().trim();
        File root = new File(appRoot);
        if (new File(root, "WEB-INF").isDirectory()) return root.getAbsolutePath();
        String lib = locateLibDir(root);
        if (lib != null) return new File(lib).getParentFile().getParentFile().getAbsolutePath();
        appendLog("[错误] 请先选择有效的应用根目录(包含 WEB-INF)。\n");
        return null;
    }

    // ---- .NET 版支持（派发给内嵌 C# 助手） ----

    /** 定位 .NET 应用的 bin 目录（需同时含 ITMC.Web.dll 与 itmcRegedit.dll）。 */
    static String locateDotNetBin(File start) {
        File cur = start;
        if (cur == null || !cur.isDirectory()) return null;
        while (cur != null) {
            if (hasDotNetFiles(cur))
                return cur.getAbsolutePath();
            File bin = cur.getName().equalsIgnoreCase("bin") ? cur : new File(cur, "bin");
            if (hasDotNetFiles(bin))
                return bin.getAbsolutePath();
            cur = cur.getParentFile();
        }
        return null;
    }

    /** 当前 .NET 版本固定使用小写授权程序集，避免误改同目录的大写兼容程序集。 */
    static boolean hasDotNetFiles(File dir) {
        return dir != null && dir.isDirectory()
                && new File(dir, "ITMC.Web.dll").isFile()
                && new File(dir, "itmcRegedit.dll").isFile();
    }

    /** 检测给定目录自身（或其 bin/WEB-INF 子目录）是否为一个 ITMC 应用。
        返回 {type, 关键目录}: JAVA=lib 目录, DOTNET=bin 目录；或 null。
        .NET 以 ITMC.Web.dll+itmcRegedit.dll 为标志。 */
    static String[] detectAppLocal(File dir) {
        if (hasDotNetFiles(dir))
            return new String[]{ "DOTNET", dir.getAbsolutePath() };
        File bin = new File(dir, "bin");
        if (hasDotNetFiles(bin))
            return new String[]{ "DOTNET", bin.getAbsolutePath() };
        for (String rel : new String[]{ "WEB-INF", "WEB-INF" + File.separator + "WEB-INF" }) {
            File lib = new File(dir, rel + File.separator + "lib");
            if (findItmcRegJar(lib) != null)
                return new String[]{ "JAVA", lib.getAbsolutePath() };
        }
        return null;
    }

    /** 判断 .NET bin 是否属于 ASP.NET 应用（父目录存在 Web.config）。 */
    static boolean isAspNetDotNetBin(String binDir) {
        if (binDir == null || binDir.trim().isEmpty()) return false;
        File parent = new File(binDir).getAbsoluteFile().getParentFile();
        return parent != null && new File(parent, "Web.config").isFile();
    }

    /** 删除助手自校验生成的空占位 XML；用户原有文件不触碰。 */
    static void cleanupGeneratedDotNetFiles(String binDir, boolean hadConfig, boolean hadRegister) {
        if (binDir == null) return;
        if (!hadConfig) deleteEmptyGeneratedXml(new File(binDir, "config.xml"));
        if (!hadRegister) deleteEmptyGeneratedXml(new File(binDir, "Register.xml"));
    }

    static void deleteEmptyGeneratedXml(File file) {
        if (!file.isFile()) return;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").replace("\r\n", "\n").trim();
            if ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root>\n</root>".equals(text))
                file.delete();
        } catch (Exception ignore) { }
    }

    /** 定位 .NET 助手: 工具目录(本 jar/类所在目录)下的 LicenseRecover.NET 子目录。 */
    static File locateNetHelper() {
        String dir = guiJarDir();
        File exe = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.exe");
        if (exe.isFile()) return exe;
        File exe2 = new File(dir, "LicenseRecover.NET.exe");
        return exe2.isFile() ? exe2 : null;
    }

    /** 运行 .NET 助手进程，UTF-8 输出追加到日志区并返回完整输出；失败返回 null。 */
    static String runDotNetCapture(java.util.List<String> cmd, String logTitle) {
        File helper = locateNetHelper();
        if (helper == null) {
            appendLog("[错误] 未找到 LicenseRecover.NET.exe（应在 jar 旁的 LicenseRecover.NET 子目录）\n");
            appendLog("RESULT: FAILED\n");
            return null;
        }
        java.util.List<String> full = new java.util.ArrayList<>();
        full.add(helper.getAbsolutePath());
        full.addAll(cmd);
        String binDir = (cmd.size() > 1 && ("config".equals(cmd.get(0))
                || "patch".equals(cmd.get(0)) || "scan".equals(cmd.get(0)))) ? cmd.get(1) : null;
        boolean hadConfig = binDir != null && new File(binDir, "config.xml").isFile();
        boolean hadRegister = binDir != null && new File(binDir, "Register.xml").isFile();
        try {
            appendLog(logTitle);
            ProcessBuilder pb = new ProcessBuilder(full);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.append(line).append('\n');
                    appendLog(line + "\n");
                }
            }
            int rc = p.waitFor();
            cleanupGeneratedDotNetFiles(binDir, hadConfig, hadRegister);
            return rc == 0 ? output.toString() : null;
        } catch (Exception ex) {
            cleanupGeneratedDotNetFiles(binDir, hadConfig, hadRegister);
            appendLog("[错误] 调用 .NET 助手失败: " + ex + "\n");
            appendLog("RESULT: FAILED\n");
            return null;
        }
    }

    /** 运行 .NET 助手并只返回成功状态，供扫描/补丁/配置路径使用。 */
    static boolean runDotNet(java.util.List<String> cmd, String logTitle) {
        return runDotNetCapture(cmd, logTitle) != null;
    }

    static void scanNet(ActionEvent e) {
        String appRoot = appRootField.getText().trim();
        String dotnetBin = locateDotNetBin(new File(appRoot));
        if (dotnetBin != null) {
            appendLog("=== 方式三：扫描联网授权验证文件（.NET 版）===\n");
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add("scan");
            cmd.add(dotnetBin);
            runDotNet(cmd, "");
            return;
        }
        String javaRoot = resolveAppRoot();
        if (javaRoot == null) return;
        appendLog("=== 方式三：扫描联网授权验证文件 ===\n");
        appendLog("应用根目录: " + javaRoot + "\n\n");
        try {
            NetRemover.scan(javaRoot, LicenseRecoverGUI::appendLog);
        } catch (Throwable t) {
            appendLog("[错误] " + t + "\n");
        }
    }

    static void removeNet(ActionEvent e) {
        String appRoot = appRootField.getText().trim();
        String dotnetBin = locateDotNetBin(new File(appRoot));
        if (dotnetBin != null) {
            appendLog("=== 方式三：移除联网授权代码（.NET 版）===\n");
            appendLog("（执行前请先停止应用服务；工具只会备份并替换 itmcRegedit.dll）\n");
            scanNetBtn.setEnabled(false);
            patchNetBtn.setEnabled(false);
            final String fBin = dotnetBin;
            final String fProduct = productField.getText().trim();
            new SwingWorker<Boolean, Void>() {
                protected Boolean doInBackground() {
                    java.util.List<String> cmd = new java.util.ArrayList<>();
                    cmd.add("patch");
                    cmd.add(fBin);
                    if (!fProduct.isEmpty() && !fProduct.equals("YX0302")) { cmd.add("--product"); cmd.add(fProduct); }
                    boolean ok = runDotNet(cmd, "");
                    return ok;
                }
                protected void done() {
                    scanNetBtn.setEnabled(true);
                    patchNetBtn.setEnabled(true);
                    boolean ok = false;
                    try { ok = Boolean.TRUE.equals(get()); } catch (Exception ignore) { }
                    statusLabel.setText(ok ? "状态: 完成 (OK)" : "状态: 失败");
                    statusLabel.setForeground(ok ? new Color(0x1a7f37) : new Color(0xb51d1d));
                }
            }.execute();
            return;
        }
        String javaRoot = resolveAppRoot();
        if (javaRoot == null) return;
        scanNetBtn.setEnabled(false);
        patchNetBtn.setEnabled(false);
        final String fAppRoot = javaRoot;
        new SwingWorker<Boolean, Void>() {
            protected Boolean doInBackground() {
                appendLog("=== 方式三：移除联网授权代码 ===\n");
                appendLog("应用根目录: " + fAppRoot + "\n\n");
                boolean ok;
                try {
                    ok = NetRemover.removeNetValidation(fAppRoot, LicenseRecoverGUI::appendLog);
                } catch (Throwable t) {
                    appendLog("[错误] " + t + "\n");
                    ok = false;
                }
                appendLog(ok ? "\nRESULT: OK —— 联网授权代码已移除\n" : "\nRESULT: FAILED\n");
                return ok;
            }
            protected void done() {
                scanNetBtn.setEnabled(true);
                patchNetBtn.setEnabled(true);
                boolean ok = false;
                try { ok = Boolean.TRUE.equals(get()); } catch (Exception ignore) { }
                statusLabel.setText(ok ? "状态: 完成 (OK)" : "状态: 失败");
                statusLabel.setForeground(ok ? new Color(0x1a7f37) : new Color(0xb51d1d));
            }
        }.execute();
    }

    // ---- 批量应用 ----

    static void batchBrowse(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择父目录（每个子目录是一个 ITMC 软件）");
        String cur = batchRootField.getText().trim();
        if (!cur.isEmpty()) chooser.setSelectedFile(new File(cur));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            batchRootField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    static void batchScan(ActionEvent e) {
        String parent = batchRootField.getText().trim();
        batchTargets.clear();
        batchModel.setRowCount(0);
        File dir = new File(parent);
        File[] subs = dir.isDirectory() ? dir.listFiles() : null;
        if (subs == null) {
            appendLog("[错误] 无法读取目录: " + parent + "\n");
            return;
        }
        java.util.Arrays.sort(subs);
        int found = 0, skipped = 0;
        for (File sub : subs) {
            if (!sub.isDirectory()) continue;
            // 顶层残留目录（WEB-INF/classes 等）不是产品，跳过
            if ("WEB-INF".equals(sub.getName()) || "classes".equals(sub.getName())
                    || "META-INF".equals(sub.getName())) {
                batchTargets.add(new Object[]{ sub.getName(), "NONE", null, null, null });
                batchModel.addRow(new Object[]{ sub.getName(), "—", "跳过(非ITMC)", sub.getAbsolutePath() });
                skipped++;
                continue;
            }
            String[] d = detectAppLocal(sub);
            if (d == null) {
                // 非 ITMC 目录：显示并跳过
                batchTargets.add(new Object[]{ sub.getName(), "NONE", null, null, null });
                batchModel.addRow(new Object[]{ sub.getName(), "—", "跳过(非ITMC)", sub.getAbsolutePath() });
                skipped++;
                continue;
            }
            if ("DOTNET".equals(d[0])) {
                batchTargets.add(new Object[]{ sub.getName(), "DOTNET", null, null, d[1] });
                batchModel.addRow(new Object[]{ sub.getName(), ".NET", "待处理", d[1] });
            } else {
                File libF = new File(d[1]);
                File appRoot = libF.getParentFile() != null ? libF.getParentFile().getParentFile() : libF;
                batchTargets.add(new Object[]{ sub.getName(), "JAVA", appRoot.getAbsolutePath(), libF.getAbsolutePath(), null });
                batchModel.addRow(new Object[]{ sub.getName(), "Java", "待处理", appRoot.getAbsolutePath() });
            }
            found++;
        }
        appendLog("批量扫描完成：ITMC 应用 " + found + " 个，非 ITMC 目录 " + skipped + " 个（跳过），共 " + subs.length + " 个子目录。\n");
        statusLabel.setText("状态: 扫描完成，ITMC " + found + " 个 / 跳过 " + skipped + " 个");
        statusLabel.setForeground(new Color(0x1a7f37));
    }

    /** 运行任意进程并把 UTF-8 输出追加到日志区。返回退出码。 */
    static int runGuiProcess(java.util.List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) appendLog(line + "\n");
            }
            return p.waitFor();
        } catch (Exception ex) {
            appendLog("[错误] 进程执行失败: " + ex + "\n");
            return 1;
        }
    }

    /** GUI 方式一的 .NET 路径：调用 CLI 的配置阻断模式，不加载或修改目标 DLL。 */
    static boolean runDotNetBlockNet(String appPath, boolean dryRun, boolean backup) {
        File cli = new File(guiJarDir(), "LicenseRecover.jar");
        if (!cli.isFile()) {
            appendLog("[错误] 未找到 LicenseRecover.jar（应与 GUI JAR 位于同一目录）\n");
            appendLog("RESULT: FAILED\n");
            return false;
        }
        File javaFile = new File(System.getProperty("java.home"), "bin" + File.separator + "java.exe");
        if (!javaFile.isFile()) javaFile = new File(System.getProperty("java.home"), "bin" + File.separator + "java");
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaFile.getAbsolutePath());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-jar");
        cmd.add(cli.getAbsolutePath());
        cmd.add("--block-net");
        cmd.add(appPath);
        if (dryRun) cmd.add("--dry-run");
        if (!backup) cmd.add("--no-backup");
        return runGuiProcess(cmd) == 0;
    }

    static void batchRun(ActionEvent e) {
        if (batchTargets.isEmpty()) {
            appendLog("[错误] 请先点击「扫描子目录」识别应用。\n");
            return;
        }
        String method = batchMethodPatch.isSelected() ? "patch" : "config";
        // 仅手动输入的产品号作为全批量覆盖；自动识别的不传（每个应用由 CLI 按自身识别）
        String productOverride = productAutoFilled ? "" : productField.getText().trim();
        batchRunBtn.setEnabled(false);
        batchScanBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                int ok = 0, fail = 0;
                for (int i = 0; i < batchTargets.size(); i++) {
                    Object[] t = batchTargets.get(i);
                    String name = (String) t[0];
                    String type = (String) t[1];
                    if ("NONE".equals(type)) {
                        final int row = i;
                        SwingUtilities.invokeLater(() -> batchModel.setValueAt("跳过", row, 2));
                        continue;
                    }
                    appendLog("\n===== [" + name + "]  (" + type + " 版) 开始 =====\n");
                    int rc;
                    if ("DOTNET".equals(type)) {
                        String dotnetBin = (String) t[4];
                        if ("config".equals(method)) {
                            appendLog("方式一：防止软件自动联网校验（只修改授权配置文件，不修改 DLL）\n");
                            rc = runDotNetBlockNet(dotnetBin, dryRunCheck.isSelected(), backupCheck.isSelected()) ? 0 : 1;
                        } else {
                            java.util.List<String> cmd = new java.util.ArrayList<>();
                            cmd.add("patch");
                            cmd.add(dotnetBin);
                            if (!productOverride.isEmpty() && !productOverride.equals("YX0302")) {
                                cmd.add("--product"); cmd.add(productOverride);
                            }
                            rc = runDotNet(cmd, "") ? 0 : 1;
                        }
                    } else {
                        // Java: 派生子进程（带该应用 lib classpath；打包 jar 由子进程自动脱壳）
                        String appRoot = (String) t[2];
                        String libDir = (String) t[3];
                        String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                        java.util.List<String> cmd = new java.util.ArrayList<>();
                        cmd.add(javaExe);
                        cmd.add("-Dfile.encoding=UTF-8");
                        cmd.add("-cp");
                        if ("patch".equals(method)) {
                            // 方式三用最小 classpath（本工具已内嵌 javassist），避免锁住 ITMCReg.jar 无法替换
                            cmd.add(guiJarDir() + File.separator + "LicenseRecover.jar");
                            cmd.add("LicenseRecover");
                            cmd.add("--remove-net");
                            cmd.add(appRoot);
                            rc = runGuiProcess(cmd);
                        } else {
                            cmd.add(libDir + File.separator + "*" + File.pathSeparator + guiJarDir() + File.separator + "LicenseRecover.jar");
                            cmd.add("LicenseRecover");
                            cmd.add(appRoot);
                            if (!productOverride.isEmpty() && !productOverride.equals("YX0302")) { cmd.add("-p"); cmd.add(productOverride); }
                            rc = runGuiProcess(cmd);
                        }
                    }
                    String status = rc == 0 ? "OK" : "FAILED";
                    if (rc == 0) ok++; else fail++;
                    final String fStatus = status;
                    final int row = i;
                    SwingUtilities.invokeLater(() -> batchModel.setValueAt(fStatus, row, 2));
                    appendLog("[" + name + "] 结果: " + status + "\n");
                }
                appendLog("\n===== 批量汇总: 成功 " + ok + " / 失败 " + fail + " / 共 " + batchTargets.size() + " =====\n");
                final int okF = ok, failF = fail;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("批量完成: " + okF + " 成功 / " + failF + " 失败");
                    statusLabel.setForeground(failF == 0 ? new Color(0x1a7f37) : new Color(0xb51d1d));
                });
                return null;
            }
            protected void done() {
                batchRunBtn.setEnabled(true);
                batchScanBtn.setEnabled(true);
            }
        }.execute();
    }

    /** LicenseRecoverGUI.jar（或类目录）所在目录，兼容 jar 与解包目录两种运行方式。 */
    static String guiJarDir() {
        try {
            File loc = new File(LicenseRecoverGUI.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (loc.isFile()) return loc.getParent();
            return loc.getAbsolutePath();
        } catch (Exception e) {
            return ".";
        }
    }

    // ---- 日志 ----

    static void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        writeLogFile(text);
    }

    /** 写日志文件（带时间戳，每行一条）。 */
    static void writeLogFile(String text) {
        if (logFileStream == null) return;
        try {
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            for (String line : text.split("\\r?\\n")) {
                if (line.trim().length() == 0 && !line.contains("RESULT")) continue;
                byte[] b = ("[" + ts + "] " + line + "\n").getBytes(StandardCharsets.UTF_8);
                synchronized (logFileStream) { logFileStream.write(b); logFileStream.flush(); }
            }
        } catch (Exception ignore) { }
    }

    /** 打开日志文件（logs/LicenseRecover_日期.log），供 appendLog 写入。 */
    static void initLogFile() {
        try {
            File dir = new File(guiJarDir(), "logs");
            dir.mkdirs();
            File f = new File(dir, "LicenseRecover_"
                    + new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date()) + ".log");
            logFileStream = new FileOutputStream(f, true);
            String line = "===== GUI 启动 =====\n";
            byte[] b = ("[" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
                    + "] " + line).getBytes(StandardCharsets.UTF_8);
            logFileStream.write(b);
            logFileStream.flush();
        } catch (Exception ignore) { }
    }

    /** 把 System.out/err 的字节流转成日志文本 */
    static class LogOutputStream extends OutputStream {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        public synchronized void write(int b) {
            buf.write(b);
        }

        public synchronized void write(byte[] b, int off, int len) {
            buf.write(b, off, len);
        }

        public synchronized void flush() throws IOException {
            byte[] bytes = buf.toByteArray();
            if (bytes.length > 0) {
                String s = new String(bytes, StandardCharsets.UTF_8);
                appendLog(s.endsWith("\n") ? s : s + "\n");
                buf.reset();
            }
        }
    }
}
