import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第二代薄 GUI：只负责界面、检测、任务编排；协议/恢复逻辑统一交给 CLI。
 * 旧 LicenseRecoverGUI 保留作为回退，不在第一阶段删除。
 */
public final class LicenseRecoverModernGUI {
    private static final long PROCESS_TIMEOUT_SECONDS = 600L;
    private static final Pattern GENERATED_CODE_PATTERN = Pattern.compile(
            "(?m)^\\s*离线授权码\\s*[:：]\\s*([0-9A-Fa-f]+)\\s*$");

    private JFrame frame;
    private final JTextField appRootField = new JTextField();
    private final JTextField productField = new JTextField();
    private final JTextField seqField = new JTextField();
    private final JTextField codeField = new JTextField();
    private final JCheckBox backupCheck = new JCheckBox("写入前备份", true);
    private final JCheckBox blockNetCheck = new JCheckBox("Java 方式一同时阻止联网", true);
    private final JCheckBox dryRunCheck = new JCheckBox("只预览，不写入", false);
    private final JLabel badge = new JLabel("待检测");
    private final JLabel typeValue = new JLabel("—");
    private final JLabel versionValue = new JLabel("—");
    private final JLabel rootValue = new JLabel("—");
    private final JLabel recommendationValue = new JLabel("请选择应用目录并检测");
    private final JLabel statusLabel = new JLabel("就绪");
    private final JTextArea logArea = new JTextArea();
    private final JButton detectButton = new JButton("检测环境");
    private final JButton way1Button = new JButton("执行推荐的方式一");
    private final JButton genButton = new JButton("生成离线授权码");
    private final JButton copyButton = new JButton("复制授权码");
    private final JButton scanButton = new JButton("扫描联网授权文件");
    private final JButton patchButton = new JButton("移除联网授权并回写");
    private final JCheckBox showAdvanced = new JCheckBox("显示高级 / 危险操作");
    private JPanel advancedPanel;

    private final JTextField batchRootField = new JTextField();
    private static final int BATCH_COL_VERIFY = 7;
    private static final int BATCH_COL_STATUS = 8;
    private final DefaultTableModel batchModel = new DefaultTableModel(
            new String[]{"应用", "类型 / 授权代际", "SoftVersionID", "授权族", "运行校验ID", "RegStr", "配置目标", "原生校验", "状态", "路径"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable batchTable = new JTable(batchModel);
    private final List<BatchTarget> batchTargets = new ArrayList<BatchTarget>();
    private final JRadioButton batchWay1 = new JRadioButton("一键恢复授权（推荐）", true);
    private final JRadioButton batchWay3 = new JRadioButton("方式三：移除联网授权代码");
    private final JCheckBox batchBackupCheck = new JCheckBox("写入前备份", true);
    private final JCheckBox batchBlockNetCheck = new JCheckBox("同时阻止残留联网", true);
    private final JCheckBox batchDryRunCheck = new JCheckBox("只预览，不写入", false);
    private final JButton batchScanButton = new JButton("扫描子目录");
    private final JButton batchRunButton = new JButton("批量执行");
    private final JButton batchCancelButton = new JButton("取消");
    private final JProgressBar batchProgress = new JProgressBar();
    private SwingWorker<Void, Void> batchWorker;

    private AppInfo currentInfo = AppInfo.unknown(null);

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.dpiaware", "true");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignore) { }
        installReadableFont();
        SwingUtilities.invokeLater(() -> new LicenseRecoverModernGUI().show());
    }

    private static void installReadableFont() {
        String family = "Dialog";
        try {
            List<String> installed = Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            for (String candidate : new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "微软雅黑", "SimSun"}) {
                if (installed.contains(candidate)) { family = candidate; break; }
            }
        } catch (Exception ignore) { }
        Font font = new Font(family, Font.PLAIN, 13);
        for (Object key : new Object[]{"Button.font", "CheckBox.font", "Label.font", "RadioButton.font",
                "TextField.font", "TextArea.font", "Table.font", "TableHeader.font", "TabbedPane.font",
                "TitledBorder.font", "OptionPane.font"}) {
            UIManager.put(key, font);
        }
    }

    private void show() {
        frame = new JFrame("ITMC 离线授权恢复工具");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 680));

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("单个应用", buildSinglePanel());
        tabs.addTab("批量应用", buildBatchPanel());
        root.add(tabs, BorderLayout.CENTER);
        root.add(buildLogPanel(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.pack();
        frame.setSize(980, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JLabel title = new JLabel("ITMC LicenseRecover");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel("检测 → 推荐操作 → 高级操作");
        subtitle.setForeground(new Color(0x666666));
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title);
        text.add(subtitle);

        badge.setOpaque(true);
        badge.setForeground(Color.WHITE);
        badge.setBackground(new Color(0x8b949e));
        badge.setBorder(new EmptyBorder(5, 12, 5, 12));
        badge.setFont(badge.getFont().deriveFont(Font.BOLD));
        panel.add(text, BorderLayout.WEST);
        panel.add(badge, BorderLayout.EAST);
        return panel;
    }

    private JComponent buildSinglePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(8, 4, 8, 4));

        JPanel path = new JPanel(new BorderLayout(6, 0));
        path.setBorder(BorderFactory.createTitledBorder("应用目录"));
        path.add(appRootField, BorderLayout.CENTER);
        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton browse = new JButton("选择...");
        browse.addActionListener(this::browseSingle);
        detectButton.addActionListener(e -> detectCurrent());
        pathButtons.add(browse);
        pathButtons.add(detectButton);
        path.add(pathButtons, BorderLayout.EAST);
        panel.add(path);
        panel.add(Box.createVerticalStrut(8));

        panel.add(buildDetectionCard());
        panel.add(Box.createVerticalStrut(8));
        panel.add(buildRecommendedCard());
        panel.add(Box.createVerticalStrut(8));

        showAdvanced.addActionListener(e -> {
            advancedPanel.setVisible(showAdvanced.isSelected());
            frame.pack();
            frame.setSize(Math.max(frame.getWidth(), 980), Math.max(frame.getHeight(), 760));
        });
        JPanel advancedToggle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        advancedToggle.add(showAdvanced);
        panel.add(advancedToggle);
        advancedPanel = buildAdvancedPanel();
        advancedPanel.setVisible(false);
        panel.add(advancedPanel);
        panel.add(Box.createVerticalGlue());

        return new JScrollPane(panel);
    }

    private JComponent buildDetectionCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createTitledBorder("检测结果"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        addSummaryRow(card, c, 0, "类型", typeValue);
        addSummaryRow(card, c, 1, "SoftVersionID", versionValue);
        addSummaryRow(card, c, 2, "应用根目录", rootValue);
        addSummaryRow(card, c, 3, "建议", recommendationValue);
        return card;
    }

    private void addSummaryRow(JPanel panel, GridBagConstraints c, int row, String name, JLabel value) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        JLabel label = new JLabel(name + ":");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(value, c);
    }

    private JComponent buildRecommendedCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createTitledBorder("常用操作"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        card.add(new JLabel("产品号覆盖:"), c);
        c.gridx = 1; c.weightx = 1;
        productField.setToolTipText("留空时由 CLI 自动识别；仅特殊产品需要手动填写");
        card.add(productField, c);
        c.gridx = 2; c.weightx = 0;
        way1Button.addActionListener(e -> runWay1());
        card.add(way1Button, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        card.add(new JLabel("注册申请号:"), c);
        c.gridx = 1; c.weightx = 1;
        seqField.setToolTipText("ASP.NET / DS01xx 请先从应用本地注册页取得申请号");
        card.add(seqField, c);
        c.gridx = 2; c.weightx = 0;
        genButton.addActionListener(e -> generateCode());
        card.add(genButton, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        card.add(new JLabel("离线授权码:"), c);
        c.gridx = 1; c.weightx = 1;
        codeField.setEditable(false);
        card.add(codeField, c);
        c.gridx = 2; c.weightx = 0;
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyCode());
        card.add(copyButton, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 3; c.weightx = 1;
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        opts.add(backupCheck);
        opts.add(blockNetCheck);
        opts.add(dryRunCheck);
        card.add(opts, c);
        return card;
    }

    private JPanel buildAdvancedPanel() {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBorder(BorderFactory.createTitledBorder("高级 / 危险操作：方式三"));
        JLabel warning = new JLabel("会修改授权字节码；执行前请停止应用服务。新界面会先建立并校验 prepatch 备份。" );
        warning.setForeground(new Color(0xa15c00));
        card.add(warning, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        scanButton.addActionListener(e -> scanNet());
        patchButton.addActionListener(e -> patchNet());
        patchButton.setFont(patchButton.getFont().deriveFont(Font.BOLD));
        buttons.add(scanButton);
        buttons.add(patchButton);
        card.add(buttons, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildBatchPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 4, 8, 4));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        top.add(new JLabel("父目录:"), c);
        c.gridx = 1; c.weightx = 1;
        top.add(batchRootField, c);
        c.gridx = 2; c.weightx = 0;
        JButton browse = new JButton("选择...");
        browse.addActionListener(this::browseBatch);
        top.add(browse, c);
        c.gridx = 3;
        batchScanButton.addActionListener(e -> scanBatch());
        top.add(batchScanButton, c);

        ButtonGroup group = new ButtonGroup();
        group.add(batchWay1);
        group.add(batchWay3);
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        top.add(new JLabel("操作:"), c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1;
        JPanel methods = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        methods.add(batchWay1);
        methods.add(batchWay3);
        top.add(methods, c);
        c.gridx = 3; c.gridwidth = 1; c.weightx = 0;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        batchRunButton.addActionListener(e -> runBatch());
        batchCancelButton.setEnabled(false);
        batchCancelButton.addActionListener(e -> cancelBatch());
        actions.add(batchRunButton);
        actions.add(batchCancelButton);
        top.add(actions, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weightx = 0;
        top.add(new JLabel("选项:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1;
        JPanel batchOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        batchOptions.add(batchBackupCheck);
        batchOptions.add(batchBlockNetCheck);
        batchOptions.add(batchDryRunCheck);
        top.add(batchOptions, c);
        root.add(top, BorderLayout.NORTH);

        batchTable.setFillsViewportHeight(true);
        batchTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = new int[]{120, 190, 120, 100, 120, 220, 230, 160, 90, 420};
        for (int i = 0; i < widths.length; i++) batchTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        root.add(new JScrollPane(batchTable), BorderLayout.CENTER);

        batchProgress.setStringPainted(true);
        batchProgress.setString("未开始");
        root.add(batchProgress, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        panel.setBorder(BorderFactory.createTitledBorder("运行日志"));
        logArea.setEditable(false);
        logArea.setRows(9);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        statusLabel.setBorder(new EmptyBorder(2, 2, 0, 2));
        panel.add(statusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void browseSingle(ActionEvent event) {
        File selected = chooseDirectory(appRootField.getText());
        if (selected != null) {
            appRootField.setText(selected.getAbsolutePath());
            detectCurrent();
        }
    }

    private void browseBatch(ActionEvent event) {
        File selected = chooseDirectory(batchRootField.getText());
        if (selected != null) batchRootField.setText(selected.getAbsolutePath());
    }

    private File chooseDirectory(String current) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (current != null && !current.trim().isEmpty()) chooser.setSelectedFile(new File(current.trim()));
        return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private AppInfo detectCurrent() {
        String path = appRootField.getText().trim();
        currentInfo = AppDetector.detect(path.isEmpty() ? null : new File(path));
        if (!currentInfo.isDetected()) {
            badge.setText("待检测");
            badge.setBackground(new Color(0x8b949e));
            typeValue.setText("未识别");
            versionValue.setText("—");
            rootValue.setText("—");
            recommendationValue.setText("未找到 ITMC Java / .NET 授权结构");
            setStatus("未识别到有效应用", false);
            return currentInfo;
        }

        if (currentInfo.type == AppInfo.Type.DOTNET) {
            badge.setText(".NET");
            badge.setBackground(new Color(0x8250df));
            typeValue.setText(".NET / ASP.NET");
        } else {
            badge.setText("Java");
            badge.setBackground(new Color(0x0969da));
            typeValue.setText("Java Web");
        }
        versionValue.setText(empty(currentInfo.softVersionId) ? "未读取到" : currentInfo.softVersionId);
        rootValue.setText(currentInfo.appRoot == null ? "—" : currentInfo.appRoot.getAbsolutePath());
        recommendationValue.setText(recommendationFor(currentInfo));
        setStatus("检测完成", true);
        appendLog("[识别] " + typeValue.getText() + "，SoftVersionID="
                + (empty(currentInfo.softVersionId) ? "未知" : currentInfo.softVersionId) + "\n");
        return currentInfo;
    }

    private String recommendationFor(AppInfo info) {
        if (info.type == AppInfo.Type.DOTNET && info.softVersionId != null
                && info.softVersionId.toUpperCase().matches("DS01\\d{2}")) {
            return "DS01xx 旧协议：优先使用方式二，在本地注册页取得申请号后生成授权码";
        }
        if (info.type == AppInfo.Type.DOTNET) {
            return ".NET：方式一用于阻止自动联网；方式二生成离线授权码";
        }
        return "Java：优先使用方式一写入本地授权；也可用方式二走本地注册页";
    }

    private void runWay1() {
        AppInfo info = requireDetected();
        if (info == null) return;
        runAsync("方式一", () -> runWay1For(info));
    }

    private OperationResult runWay1For(AppInfo info) {
        File cli = cliJar();
        if (!cli.isFile()) return OperationResult.failed("未找到 LicenseRecover.jar", 1);
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe());
        cmd.add("-Dfile.encoding=UTF-8");
        if (info.type == AppInfo.Type.DOTNET) {
            cmd.add("-jar");
            cmd.add(cli.getAbsolutePath());
            cmd.add("--block-net");
            cmd.add(info.appRoot.getAbsolutePath());
            if (dryRunCheck.isSelected()) cmd.add("--dry-run");
            if (!backupCheck.isSelected()) cmd.add("--no-backup");
        } else {
            cmd.add("-cp");
            cmd.add(info.libDir.getAbsolutePath() + File.separator + "*" + File.pathSeparator + cli.getAbsolutePath());
            cmd.add("LicenseRecover");
            cmd.add(info.appRoot.getAbsolutePath());
            String product = productField.getText().trim();
            if (!product.isEmpty()) { cmd.add("-p"); cmd.add(product); }
            if (dryRunCheck.isSelected()) cmd.add("--dry-run");
            if (!backupCheck.isSelected()) cmd.add("--no-backup");
            if (!blockNetCheck.isSelected()) cmd.add("--no-block-net");
        }
        return ProcessRunner.run(cmd, this::appendLog, PROCESS_TIMEOUT_SECONDS);
    }

    private void generateCode() {
        AppInfo info = currentInfo != null && currentInfo.isDetected() ? currentInfo : detectCurrent();
        if (!info.isDetected()) return;
        final String seq = seqField.getText().trim();
        codeField.setText("");
        copyButton.setEnabled(false);
        runAsync("方式二", () -> {
            File cli = cliJar();
            if (!cli.isFile()) return OperationResult.failed("未找到 LicenseRecover.jar", 1);
            List<String> cmd = new ArrayList<String>();
            cmd.add(javaExe());
            cmd.add("-Dfile.encoding=UTF-8");
            if (info.type == AppInfo.Type.JAVA) {
                cmd.add("-cp");
                cmd.add(info.libDir.getAbsolutePath() + File.separator + "*" + File.pathSeparator + cli.getAbsolutePath());
                cmd.add("LicenseRecover");
                cmd.add("--gencode");
                cmd.add(info.appRoot.getAbsolutePath());
            } else {
                cmd.add("-jar");
                cmd.add(cli.getAbsolutePath());
                cmd.add("--gencode");
                cmd.add(info.binDir.getAbsolutePath());
            }
            if (!seq.isEmpty()) { cmd.add("--seq"); cmd.add(seq); }
            String product = productField.getText().trim();
            if (!product.isEmpty()) { cmd.add("--product"); cmd.add(product); }

            StringBuilder capture = new StringBuilder();
            Consumer<String> sink = s -> { capture.append(s); appendLog(s); };
            OperationResult result = ProcessRunner.run(cmd, sink, PROCESS_TIMEOUT_SECONDS);
            Matcher matcher = GENERATED_CODE_PATTERN.matcher(capture.toString());
            String found = null;
            while (matcher.find()) found = matcher.group(1);
            if (found != null) {
                final String code = found;
                SwingUtilities.invokeLater(() -> {
                    codeField.setText(code);
                    codeField.setCaretPosition(0);
                    copyButton.setEnabled(true);
                });
            }
            return result;
        });
    }

    private void copyCode() {
        String code = codeField.getText().trim();
        if (code.isEmpty()) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            setStatus("授权码已复制", true);
        } catch (Exception ex) {
            appendLog("[错误] 复制失败: " + ex.getMessage() + "\n");
            setStatus("复制失败", false);
        }
    }

    private void scanNet() {
        AppInfo info = requireDetected();
        if (info == null) return;
        runAsync("方式三扫描", () -> runNetFor(info, false));
    }

    private void patchNet() {
        AppInfo info = requireDetected();
        if (info == null) return;
        int answer = JOptionPane.showConfirmDialog(frame,
                "方式三会改写授权文件。请确认应用服务已经停止。\n继续前会先建立并校验 prepatch 备份。",
                "确认危险操作", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        runAsync("方式三", () -> runNetFor(info, true));
    }

    private OperationResult runNetFor(AppInfo info, boolean patch) {
        return runNetFor(info, patch, dryRunCheck.isSelected());
    }

    private OperationResult runNetFor(AppInfo info, boolean patch, boolean preview) {
        File cli = cliJar();
        if (!cli.isFile()) return OperationResult.failed("未找到 LicenseRecover.jar", 1);
        if (patch && !preview && !PatchSafety.prepare(info, this::appendLog)) {
            return OperationResult.failed("安全备份预检失败", 3);
        }
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe());
        cmd.add("-Dfile.encoding=UTF-8");
        if (info.type == AppInfo.Type.DOTNET) {
            cmd.add("-jar");
            cmd.add(cli.getAbsolutePath());
        } else {
            cmd.add("-cp");
            cmd.add(cli.getAbsolutePath());
            cmd.add("LicenseRecover");
        }
        cmd.add(patch ? "--remove-net" : "--scan-net");
        cmd.add((info.type == AppInfo.Type.DOTNET ? info.binDir : info.appRoot).getAbsolutePath());
        if (preview) cmd.add("--dry-run");
        return ProcessRunner.run(cmd, this::appendLog, PROCESS_TIMEOUT_SECONDS);
    }

    private void scanBatch() {
        batchTargets.clear();
        batchModel.setRowCount(0);
        File parent = new File(batchRootField.getText().trim());
        File[] children = parent.isDirectory() ? parent.listFiles(File::isDirectory) : null;
        if (children == null) {
            setStatus("批量父目录无效", false);
            return;
        }
        Arrays.sort(children);
        int detected = 0;
        int skipped = 0;
        int errors = 0;
        for (File child : children) {
            try {
                AppInfo info = AppDetector.detect(child);
                BatchTarget target;
                if (info.type == AppInfo.Type.JAVA) {
                    target = BatchTarget.javaTarget(child.getName(), info.appRoot, info.libDir);
                    LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(info.appRoot);
                    String verifyText = !plan.detected ? "待识别"
                            : (plan.automaticRecoveryReady ? "待执行: RegisterMain"
                            : "未执行: " + plan.recoveryReadiness);
                    String statusText = !plan.detected ? "待识别"
                            : (plan.automaticRecoveryReady ? "待处理" : "待确认");
                    batchModel.addRow(new Object[]{child.getName(),
                            plan.detected ? "Java / " + plan.generation : "Java",
                            valueOrDash(plan.softVersionId), valueOrDash(plan.authorizationFamily),
                            valueOrDash(plan.runtimeProductId), plan.regStrSummary(), plan.configTargets,
                            verifyText, statusText, info.appRoot.getAbsolutePath()});
                    detected++;
                } else if (info.type == AppInfo.Type.DOTNET) {
                    target = BatchTarget.dotNetTarget(child.getName(), info.binDir);
                    LicenseRecoverModernGUIAutoRecovery.Detection d =
                            LicenseRecoverModernGUIAutoRecovery.detect(info.binDir);
                    String kind = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                            ? ".NET Modern" : ".NET Legacy";
                    String verify = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                            ? "待执行: 写回解密校验" : "兼容方式一: 不适用";
                    batchModel.addRow(new Object[]{child.getName(), kind,
                            valueOrDash(d.versionId), valueOrDash(d.productName), valueOrDash(d.productName),
                            "—", "config.xml", verify, "待处理", info.binDir.getAbsolutePath()});
                    detected++;
                } else {
                    target = BatchTarget.skipped(child.getName());
                    batchModel.addRow(new Object[]{child.getName(), "—", "—", "—", "—", "—", "—", "—", "未识别", child.getAbsolutePath()});
                    skipped++;
                }
                batchTargets.add(target);
            } catch (Throwable ex) {
                errors++;
                batchTargets.add(BatchTarget.skipped(child.getName()));
                String msg = ex.getMessage();
                String reason = ex.getClass().getSimpleName() + (msg == null || msg.trim().isEmpty() ? "" : ": " + msg.trim());
                batchModel.addRow(new Object[]{child.getName(), "检测异常", "—", "—", "—", "—", "—", "—",
                        reason, child.getAbsolutePath()});
                appendLog("[批量扫描] " + child.getName() + " 检测异常: " + reason + "\n");
            }
        }
        batchProgress.setMinimum(0);
        batchProgress.setMaximum(batchTargets.size());
        batchProgress.setValue(0);
        batchProgress.setString("扫描完成：" + batchTargets.size() + " 个目录；识别 " + detected
                + "，未识别 " + skipped + "，异常 " + errors);
        setStatus(errors == 0 ? "批量扫描完成" : "批量扫描完成（存在检测异常）", errors == 0);
    }

    private void runBatch() {
        if (batchTargets.isEmpty()) {
            scanBatch();
            if (batchTargets.isEmpty()) return;
        }
        final boolean usePatch = batchWay3.isSelected();
        final boolean backup = batchBackupCheck.isSelected();
        final boolean blockNet = batchBlockNetCheck.isSelected();
        final boolean preview = batchDryRunCheck.isSelected();
        if (usePatch && !preview) {
            int answer = JOptionPane.showConfirmDialog(frame,
                    "批量方式三会逐个修改授权文件，并为每个应用先建立 prepatch 备份。\n请确认相关应用服务已停止。",
                    "确认批量危险操作", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
        }
        batchRunButton.setEnabled(false);
        batchScanButton.setEnabled(false);
        batchCancelButton.setEnabled(true);
        batchWorker = new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                int done = 0;
                for (int row = 0; row < batchTargets.size(); row++) {
                    if (isCancelled()) break;
                    BatchTarget target = batchTargets.get(row);
                    final int currentRow = row;
                    if (target.type == BatchTarget.Type.NONE) {
                        done++;
                        updateBatchProgress(done, "跳过 " + target.name);
                        continue;
                    }
                    if (!usePatch) {
                        final String blockReason = recommendedBatchBlockReason(target);
                        if (blockReason != null) {
                            SwingUtilities.invokeLater(() -> {
                                batchModel.setValueAt("未执行: " + blockReason, currentRow, BATCH_COL_VERIFY);
                                batchModel.setValueAt("跳过: 待确认", currentRow, BATCH_COL_STATUS);
                            });
                            appendLog("[batch] " + target.name + " 自动跳过：" + blockReason + "\n");
                            done++;
                            updateBatchProgress(done, "跳过 " + target.name);
                            continue;
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        batchModel.setValueAt("处理中", currentRow, BATCH_COL_STATUS);
                        batchModel.setValueAt(usePatch ? "方式三" : "执行中", currentRow, BATCH_COL_VERIFY);
                    });
                    AppInfo info = AppDetector.detect(target.type == BatchTarget.Type.JAVA ? target.appRoot : target.binDir);
                    appendLog("\n===== [" + target.name + "] " + (usePatch ? "方式三" : "一键恢复") + " =====\n");
                    OperationResult result = usePatch
                            ? runNetFor(info, true, preview)
                            : runBatchRecommended(target, backup, blockNet, preview);
                    if (isCancelled()) break;
                    final String textResult = result.isSuccess() ? (result.status == OperationResult.Status.PREVIEW ? "PREVIEW" : "OK")
                            : (result.status == OperationResult.Status.CANCELLED ? "取消" : "FAILED");
                    final String verify = batchVerification(target, usePatch, preview, result);
                    SwingUtilities.invokeLater(() -> {
                        batchModel.setValueAt(verify, currentRow, BATCH_COL_VERIFY);
                        batchModel.setValueAt(textResult, currentRow, BATCH_COL_STATUS);
                    });
                    done++;
                    updateBatchProgress(done, target.name + " : " + textResult);
                    if (result.status == OperationResult.Status.CANCELLED) break;
                }
                return null;
            }
            protected void done() {
                batchRunButton.setEnabled(true);
                batchScanButton.setEnabled(true);
                batchCancelButton.setEnabled(false);
                batchProgress.setString(isCancelled() ? "已取消" : "批量执行完成");
                setStatus(isCancelled() ? "批量执行已取消" : "批量执行完成", !isCancelled());
            }
        };
        batchWorker.execute();
    }

    private String recommendedBatchBlockReason(BatchTarget target) {
        if (target == null || target.type == BatchTarget.Type.NONE) return "未识别";
        if (target.type == BatchTarget.Type.JAVA) {
            LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(target.appRoot);
            return plan.detected && plan.automaticRecoveryReady ? null
                    : (plan.detected ? plan.recoveryReadiness : "Java 授权计划未识别");
        }
        LicenseRecoverModernGUIAutoRecovery.Detection d =
                LicenseRecoverModernGUIAutoRecovery.detect(target.binDir);
        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                && (d.productName == null || d.productName.trim().isEmpty()))
            return ".NET 授权产品号未确认";
        return null;
    }

    private OperationResult runBatchRecommended(BatchTarget target, boolean backup, boolean blockNet, boolean preview) {
        File selected = target.type == BatchTarget.Type.JAVA ? target.appRoot : target.binDir;
        LicenseRecoverModernGUIAutoRecovery.Detection d = LicenseRecoverModernGUIAutoRecovery.detect(selected);
        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY) {
            return runLegacyBatchWay1(target.binDir, backup, preview);
        }
        LicenseRecoverModernGUIAutoRecovery.Result result =
                LicenseRecoverModernGUIAutoRecovery.recover(selected, backup, blockNet, preview, this::appendLog);
        if (Thread.currentThread().isInterrupted()) return OperationResult.cancelled("批量任务已取消");
        if (!result.success) return OperationResult.failed(result.message, 1);
        return preview ? OperationResult.preview(result.message) : OperationResult.success(result.message);
    }

    private OperationResult runLegacyBatchWay1(File binDir, boolean backup, boolean preview) {
        File cli = cliJar();
        if (!cli.isFile()) return OperationResult.failed("未找到 LicenseRecover.jar", 1);
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe()); cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-jar"); cmd.add(cli.getAbsolutePath());
        cmd.add("--block-net"); cmd.add(binDir.getAbsolutePath());
        if (preview) cmd.add("--dry-run");
        if (!backup) cmd.add("--no-backup");
        appendLog("[batch] .NET Legacy 使用兼容方式一：仅阻断失效授权服务，不重建本地授权。\n");
        return ProcessRunner.run(cmd, this::appendLog, PROCESS_TIMEOUT_SECONDS);
    }

    private String batchVerification(BatchTarget target, boolean usePatch, boolean preview, OperationResult result) {
        if (usePatch) return preview ? "方式三预览/扫描" : (result.isSuccess() ? "方式三完成" : "方式三失败");
        if (target.type == BatchTarget.Type.JAVA) {
            if (preview) return "预览: 未执行写后校验";
            return result.isSuccess() ? "RegisterMain: OK" : "RegisterMain: FAILED";
        }
        LicenseRecoverModernGUIAutoRecovery.Detection d = LicenseRecoverModernGUIAutoRecovery.detect(target.binDir);
        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY) return "兼容方式一: 不适用";
        if (preview) return "预览: 未执行写回校验";
        return result.isSuccess() ? "写回解密校验: OK" : "写回解密校验: FAILED";
    }

    private void cancelBatch() {
        if (batchWorker != null && !batchWorker.isDone()) {
            batchWorker.cancel(true);
            batchCancelButton.setEnabled(false);
            appendLog("[提示] 已请求取消当前批量任务。\n");
        }
    }

    private void updateBatchProgress(final int value, final String text) {
        SwingUtilities.invokeLater(() -> {
            batchProgress.setValue(value);
            batchProgress.setString(value + " / " + batchTargets.size() + "  " + text);
        });
    }

    private AppInfo requireDetected() {
        AppInfo info = currentInfo != null && currentInfo.isDetected() ? currentInfo : detectCurrent();
        if (!info.isDetected()) {
            JOptionPane.showMessageDialog(frame, "请先选择并检测有效的 ITMC 应用目录。",
                    "未检测到应用", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return info;
    }

    private void runAsync(final String name, final Callable<OperationResult> task) {
        setBusy(true);
        setStatus(name + "执行中...", true);
        appendLog("\n--- " + name + " ---\n");
        new SwingWorker<OperationResult, Void>() {
            protected OperationResult doInBackground() {
                try { return task.call(); }
                catch (Exception ex) {
                    appendLog("[错误] " + ex + "\n");
                    return OperationResult.failed(ex.getMessage(), 1);
                }
            }
            protected void done() {
                OperationResult result;
                try { result = get(); }
                catch (Exception ex) { result = OperationResult.failed(ex.getMessage(), 1); }
                setBusy(false);
                setStatus(name + (result.isSuccess() ? "完成" : "失败")
                        + (result.message.isEmpty() ? "" : "：" + result.message), result.isSuccess());
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        detectButton.setEnabled(!busy);
        way1Button.setEnabled(!busy);
        genButton.setEnabled(!busy);
        scanButton.setEnabled(!busy);
        patchButton.setEnabled(!busy);
    }

    private void setStatus(String text, boolean ok) {
        statusLabel.setText(text);
        statusLabel.setForeground(ok ? new Color(0x1a7f37) : new Color(0xb42318));
    }

    private void appendLog(String text) {
        if (text == null || text.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            logArea.append(text);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private File cliJar() {
        return new File(toolDir(), "LicenseRecover.jar");
    }

    private String javaExe() {
        File exe = new File(System.getProperty("java.home"), "bin" + File.separator + "java.exe");
        if (!exe.isFile()) exe = new File(System.getProperty("java.home"), "bin" + File.separator + "java");
        return exe.getAbsolutePath();
    }

    private File toolDir() {
        try {
            File location = new File(LicenseRecoverModernGUI.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return location.isFile() ? location.getParentFile() : location;
        } catch (Exception ex) {
            return new File(".").getAbsoluteFile();
        }
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String valueOrDash(String value) {
        return empty(value) ? "—" : value;
    }
}
