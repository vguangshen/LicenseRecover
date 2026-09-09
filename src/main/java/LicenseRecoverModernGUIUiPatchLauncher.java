import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds one-click recovery and visible update controls to the modern GUI while
 * preserving the older manual paths as a collapsed compatibility section.
 */
public final class LicenseRecoverModernGUIUiPatchLauncher {
    private static final String UPDATE_CONTROL_NAME = "LicenseRecoverUpdateControl";
    private static final String ONE_CLICK_CONTROL_NAME = "LicenseRecoverOneClickControl";

    private LicenseRecoverModernGUIUiPatchLauncher() { }

    public static void main(String[] args) {
        LicenseRecoverModernGUILauncher.main(args);
        if (contains(args, "--update-only")) return;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = findMainFrame();
            installOneClickRecovery(frame);
            installBottomUpdateBar(frame);
        });
    }

    static void installOneClickRecovery(final JFrame frame) {
        if (frame == null) return;
        final JPanel card = findTitledPanel(frame.getContentPane(), "常用操作");
        if (card == null || findNamed(card, ONE_CLICK_CONTROL_NAME) != null) return;

        LayoutManager oldLayout = card.getLayout();
        Component[] oldChildren = card.getComponents();
        final JPanel manual = new JPanel();
        if (oldLayout instanceof GridBagLayout) {
            GridBagLayout src = (GridBagLayout) oldLayout;
            GridBagLayout dst = new GridBagLayout();
            manual.setLayout(dst);
            for (Component child : oldChildren) {
                GridBagConstraints gc = src.getConstraints(child);
                card.remove(child);
                manual.add(child, gc);
            }
        } else {
            manual.setLayout(new BoxLayout(manual, BoxLayout.Y_AXIS));
            for (Component child : oldChildren) {
                card.remove(child);
                manual.add(child);
            }
        }
        manual.setVisible(false);
        manual.setBorder(BorderFactory.createTitledBorder("手动兼容工具（原方式一 / 方式二）"));

        card.removeAll();
        card.setLayout(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createTitledBorder("一键恢复授权（推荐）"));

        JPanel primary = new JPanel(new BorderLayout(8, 8));
        primary.setName(ONE_CLICK_CONTROL_NAME);
        JLabel help = new JLabel("自动识别软件 → 展示授权计划 → 获取本机标识 → 写入 → 原生重新读取验证");
        help.setForeground(new Color(0x57606a));

        final JLabel generationValue = new JLabel("—");
        final JLabel familyValue = new JLabel("—");
        final JLabel runtimeValue = new JLabel("—");
        final JTextArea regStrValue = detailArea();
        final JTextArea targetValue = detailArea();
        final JLabel jarValue = new JLabel("—");
        final JLabel verifyValue = new JLabel("待执行");
        JPanel details = new JPanel(new GridBagLayout());
        details.setBorder(BorderFactory.createTitledBorder("授权识别详情"));
        addDetailRow(details, 0, "授权代际", generationValue);
        addDetailRow(details, 1, "授权族", familyValue);
        addDetailRow(details, 2, "运行校验ID", runtimeValue);
        addDetailRow(details, 3, "RegStr", regStrValue);
        addDetailRow(details, 4, "配置目标", targetValue);
        addDetailRow(details, 5, "授权组件", jarValue);
        addDetailRow(details, 6, "原生校验", verifyValue);

        JPanel overview = new JPanel(new BorderLayout(0, 6));
        overview.add(help, BorderLayout.NORTH);
        overview.add(details, BorderLayout.CENTER);
        primary.add(overview, BorderLayout.NORTH);

        final JButton recover = new JButton("一键恢复授权");
        recover.setFont(recover.getFont().deriveFont(Font.BOLD, 15f));
        recover.setPreferredSize(new Dimension(220, 42));
        recover.setToolTipText("Java 会展示授权代际 / 授权族 / 运行校验ID / RegStr / config 目标，并在写入后执行 RegisterMain 原生校验");
        recover.addActionListener(e -> runOneClick(frame, recover,
                generationValue, familyValue, runtimeValue, regStrValue, targetValue, jarValue, verifyValue));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        buttonRow.add(recover);
        primary.add(buttonRow, BorderLayout.CENTER);

        final JTextField planRoot = findTextFieldInTitledPanel(frame.getContentPane(), "应用目录");
        final Runnable refresh = () -> refreshPlan(frame, generationValue, familyValue, runtimeValue,
                regStrValue, targetValue, jarValue, verifyValue);
        if (planRoot != null) {
            planRoot.getDocument().addDocumentListener(new DocumentListener() {
                private void changed() { SwingUtilities.invokeLater(refresh); }
                public void insertUpdate(DocumentEvent e) { changed(); }
                public void removeUpdate(DocumentEvent e) { changed(); }
                public void changedUpdate(DocumentEvent e) { changed(); }
            });
        }
        SwingUtilities.invokeLater(refresh);

        final JButton manualToggle = new JButton("显示手动兼容工具");
        manualToggle.addActionListener(e -> {
            boolean show = !manual.isVisible();
            manual.setVisible(show);
            manualToggle.setText(show ? "隐藏手动兼容工具" : "显示手动兼容工具");
            card.revalidate();
            card.repaint();
        });
        JPanel lower = new JPanel(new BorderLayout(0, 6));
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        toggleRow.add(manualToggle);
        lower.add(toggleRow, BorderLayout.NORTH);
        lower.add(manual, BorderLayout.CENTER);

        card.add(primary, BorderLayout.NORTH);
        card.add(lower, BorderLayout.CENTER);
        card.revalidate();
        card.repaint();
    }

    private static void addDetailRow(JPanel panel, int row, String name, Component value) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 5, 2, 5);
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        JLabel label = new JLabel(name + ":");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, c);
    }

    private static JTextArea detailArea() {
        JTextArea area = new JTextArea(2, 48);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(UIManager.getFont("Label.font"));
        return area;
    }

    private static void refreshPlan(JFrame frame, JLabel generation, JLabel family, JLabel runtime,
                                    JTextArea regStr, JTextArea targets, JLabel jar, JLabel verify) {
        JTextField appRoot = findTextFieldInTitledPanel(frame.getContentPane(), "应用目录");
        if (appRoot == null || appRoot.getText().trim().isEmpty()) {
            generation.setText("—"); family.setText("—"); runtime.setText("—"); regStr.setText("—"); targets.setText("—");
            jar.setText("—"); verify.setText("待选择应用");
            return;
        }
        File selected = new File(appRoot.getText().trim());
        LicenseRecoverModernGUIAutoRecovery.Detection detection = LicenseRecoverModernGUIAutoRecovery.detect(selected);
        if (detection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.JAVA) {
            LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(selected);
            generation.setText(plan.generation);
            family.setText(value(plan.authorizationFamily));
            runtime.setText(value(plan.runtimeProductId));
            regStr.setText(value(plan.regStr)); regStr.setCaretPosition(0);
            targets.setText(plan.configTargets); targets.setCaretPosition(0);
            jar.setText(plan.registrationJarSummary());
            verify.setText(plan.automaticRecoveryReady
                    ? "待执行: " + plan.verificationPlan
                    : "禁止自动写入: " + plan.recoveryReadiness);
        } else if (detection.isDetected()) {
            generation.setText(detection.kind.toString());
            family.setText(value(detection.productName));
            runtime.setText(value(detection.productName));
            regStr.setText(".NET 由对应适配器自动识别");
            targets.setText("config.xml / 授权 sidecar（按检测结果）");
            jar.setText("不适用");
            verify.setText(detection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                    ? "待执行: 写回解密校验" : "Legacy: 仅兼容路径");
        } else {
            generation.setText("未识别"); family.setText("—"); runtime.setText("—"); regStr.setText("—"); targets.setText("—");
            jar.setText("—"); verify.setText("未识别到支持的授权结构");
        }
    }

    private static String value(String text) {
        return text == null || text.trim().isEmpty() ? "—" : text;
    }

    static final class OneClickFailurePresentation {
        final String stage;
        final Integer exitCode;
        final String product;
        final String summary;
        final String details;

        OneClickFailurePresentation(String stage, Integer exitCode, String product,
                                    String summary, String details) {
            this.stage = stage;
            this.exitCode = exitCode;
            this.product = product;
            this.summary = summary;
            this.details = details;
        }
    }

    static OneClickFailurePresentation describeOneClickFailure(String message) {
        String raw = message == null ? "" : message.trim();
        Matcher stageMatch = Pattern.compile("^\\s*\\[([A-Z_]+)\\]").matcher(raw);
        String stage = stageMatch.find() ? stageMatch.group(1) : "UNKNOWN";

        Integer exit = null;
        Matcher exitMatch = Pattern.compile("(?i)\\bexit\\s*=\\s*(-?\\d+)").matcher(raw);
        if (exitMatch.find()) {
            try { exit = Integer.valueOf(exitMatch.group(1)); }
            catch (NumberFormatException ignore) { }
        }

        String nativeOutput = raw;
        int outputAt = raw.indexOf("output=");
        if (outputAt >= 0) nativeOutput = raw.substring(outputAt + "output=".length()).trim();
        String details = formatOneClickFailureDetails(nativeOutput);

        String product = null;
        Matcher productMatch = Pattern.compile("(?i)产品号\\s*[:：]\\s*([A-Z0-9._-]+)").matcher(details);
        if (productMatch.find()) product = productMatch.group(1);

        String summary;
        if ("GENCODE".equals(stage)) {
            summary = "生成离线授权码失败，目标注册组件在生成申请号 / 授权码时抛出异常。";
        } else if ("GENCODE_PARSE".equals(stage)) {
            summary = "生成结果解析失败：辅助程序已返回，但没有读取到完整的申请号或授权码。";
        } else if ("DOREG".equals(stage)) {
            summary = "本地注册提交失败，目标 DoRegistry() 没有接受本次授权数据。";
        } else if ("VERIFY".equals(stage)) {
            summary = "写入后的原生校验失败，目标 CheckReInfo() 没有确认授权状态。";
        } else if ("PRE_BLOCK".equals(stage)) {
            summary = "安全隔离未建立。为避免触发联网授权，工具已停止调用目标注册组件。";
        } else if ("HELPER".equals(stage)) {
            summary = "所需的 .NET 注册辅助程序缺失或无法启动。";
        } else if ("CHAIN".equals(stage)) {
            summary = "无法确认目标程序实际使用的 .NET 注册链。";
        } else {
            summary = "一键恢复在执行过程中失败，请查看下方详细信息。";
        }
        return new OneClickFailurePresentation(stage, exit, product, summary, details);
    }

    static String formatOneClickFailureDetails(String nativeOutput) {
        String text = nativeOutput == null ? "" : nativeOutput.replace('\r', ' ').trim();
        if (text.isEmpty()) return "没有返回更多错误信息。";
        text = text.replaceAll("={8,}", "\n");
        text = text.replaceAll("\\s+(产品号\\s*[:：])", "\n$1");
        text = text.replaceAll("\\s+(\\[错误\\])", "\n$1");
        text = text.replaceAll("\\s+(RESULT\\s*[:：])", "\n$1");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\n[ \\t]+", "\n");
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }

    private static String oneClickFailureClipboardText(
            LicenseRecoverModernGUIAutoRecovery.Result result,
            OneClickFailurePresentation p) {
        StringBuilder b = new StringBuilder();
        b.append("LicenseRecover 一键恢复错误\n");
        if (result != null && result.detection != null && result.detection.versionId != null)
            b.append("应用: ").append(result.detection.versionId).append('\n');
        b.append("阶段: ").append(p.stage).append('\n');
        if (p.product != null) b.append("注册产品: ").append(p.product).append('\n');
        if (p.exitCode != null) b.append("退出码: ").append(p.exitCode).append('\n');
        b.append("摘要: ").append(p.summary).append("\n\n详细信息:\n").append(p.details);
        return b.toString();
    }

    private static File currentPersistentLogFile() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return new File(new File(toolDir(), "logs"), "LicenseRecoverGUI-" + day + ".log");
    }

    private static void showOneClickFailureDialog(
            final JFrame frame, final LicenseRecoverModernGUIAutoRecovery.Result result) {
        final OneClickFailurePresentation p = describeOneClickFailure(result == null ? null : result.message);
        final JDialog dialog = new JDialog(frame, "一键恢复未完成", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 0));

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        Icon warningIcon = UIManager.getIcon("OptionPane.warningIcon");
        if (warningIcon != null) heading.add(new JLabel(warningIcon), BorderLayout.WEST);
        JPanel headingText = new JPanel();
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));
        String stageTitle = "GENCODE".equals(p.stage) ? "生成离线授权码失败" : "一键恢复失败";
        JLabel title = new JLabel(stageTitle + "（" + p.stage + "）");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JTextArea summary = new JTextArea(p.summary);
        summary.setEditable(false);
        summary.setOpaque(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setFont(UIManager.getFont("Label.font"));
        summary.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        headingText.add(title);
        headingText.add(summary);
        heading.add(headingText, BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        JPanel info = new JPanel(new GridBagLayout());
        info.setBorder(BorderFactory.createTitledBorder("错误信息"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 8, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0; c.gridx = 0; info.add(new JLabel("应用:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        String app = result != null && result.detection != null ? value(result.detection.versionId) : "—";
        info.add(new JLabel(app), c);
        c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; info.add(new JLabel("阶段:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; info.add(new JLabel(p.stage), c);
        c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; info.add(new JLabel("注册产品:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; info.add(new JLabel(p.product == null ? "—" : p.product), c);
        c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; info.add(new JLabel("退出码:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; info.add(new JLabel(p.exitCode == null ? "—" : String.valueOf(p.exitCode)), c);
        center.add(info, BorderLayout.NORTH);

        JTextArea details = new JTextArea(p.details, 9, 72);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, Math.max(12, details.getFont().getSize()));
        details.setFont(mono);
        JScrollPane scroll = new JScrollPane(details);
        scroll.setBorder(BorderFactory.createTitledBorder("详细信息"));
        center.add(scroll, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton copy = new JButton("复制详情");
        copy.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(oneClickFailureClipboardText(result, p)), null);
                copy.setText("已复制");
            } catch (Exception ex) {
                copy.setText("复制失败");
            }
        });
        final File logFile = currentPersistentLogFile();
        JButton openLog = new JButton("打开日志");
        openLog.setEnabled(logFile.isFile() && Desktop.isDesktopSupported());
        openLog.addActionListener(e -> {
            try { Desktop.getDesktop().open(logFile); }
            catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "无法打开日志文件：" + value(ex.getMessage()),
                        "打开日志", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dialog.dispose());
        buttons.add(copy);
        buttons.add(openLog);
        buttons.add(close);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setSize(Math.max(760, dialog.getWidth()), Math.max(500, dialog.getHeight()));
        dialog.setMinimumSize(new Dimension(680, 430));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static void runOneClick(final JFrame frame, final JButton button,
                                    final JLabel generationValue, final JLabel familyValue, final JLabel runtimeValue,
                                    final JTextArea regStrValue, final JTextArea targetValue,
                                    final JLabel jarValue, final JLabel verifyValue) {
        final JTextField appRoot = findTextFieldInTitledPanel(frame.getContentPane(), "应用目录");
        if (appRoot == null || appRoot.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先选择 ITMC 软件目录。",
                    "一键恢复授权", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final File selected = new File(appRoot.getText().trim());
        final JCheckBox backup = findCheckBox(frame.getContentPane(), "写入前备份");
        final JCheckBox blockNet = findCheckBox(frame.getContentPane(), "Java 方式一同时阻止联网");
        final JCheckBox dryRun = findCheckBox(frame.getContentPane(), "只预览，不写入");
        final JTextArea logArea = findTextAreaInTitledPanel(frame.getContentPane(), "运行日志");

        final boolean doBackup = backup == null || backup.isSelected();
        final boolean doBlock = blockNet == null || blockNet.isSelected();
        final boolean preview = dryRun != null && dryRun.isSelected();
        refreshPlan(frame, generationValue, familyValue, runtimeValue, regStrValue, targetValue, jarValue, verifyValue);
        verifyValue.setText(preview ? "预览执行中..." : "执行并校验中...");
        button.setEnabled(false);
        append(logArea, "\n===== 一键恢复授权 =====\n");

        new SwingWorker<LicenseRecoverModernGUIAutoRecovery.Result, Void>() {
            protected LicenseRecoverModernGUIAutoRecovery.Result doInBackground() {
                return LicenseRecoverModernGUIAutoRecovery.recover(
                        selected, doBackup, doBlock, preview, s -> append(logArea, s));
            }
            protected void done() {
                button.setEnabled(true);
                try {
                    LicenseRecoverModernGUIAutoRecovery.Result result = get();
                    boolean javaTarget = result.detection != null
                            && result.detection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.JAVA;
                    if (javaTarget) {
                        verifyValue.setText(preview ? "PREVIEW: 未执行写后校验"
                                : (result.success ? "PASS: RegisterMain.checkReInfo()" : "FAILED: 查看运行日志"));
                    } else {
                        verifyValue.setText(preview ? "PREVIEW: 未执行写回校验"
                                : (result.success ? "PASS: 写回校验" : "FAILED: 查看运行日志"));
                    }
                    if (result.success) {
                        StringBuilder msg = new StringBuilder(result.message);
                        if (javaTarget) msg.append("\n\n原生校验: ")
                                .append(preview ? "预览模式未执行" : "RegisterMain.checkReInfo() 通过");
                        if (result.machineId != null) msg.append("\n机器标识: ").append(result.machineId);
                        JOptionPane.showMessageDialog(frame, msg.toString(),
                                "一键恢复完成", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        showOneClickFailureDialog(frame, result);
                    }
                } catch (Exception ex) {
                    verifyValue.setText("FAILED: " + value(ex.getMessage()));
                    LicenseRecoverModernGUIAutoRecovery.Detection d =
                            LicenseRecoverModernGUIAutoRecovery.detect(selected);
                    LicenseRecoverModernGUIAutoRecovery.Result failure =
                            LicenseRecoverModernGUIAutoRecovery.Result.fail(
                                    "[UNKNOWN] 一键恢复失败; output=" + value(ex.getMessage()), d);
                    showOneClickFailureDialog(frame, failure);
                }
            }
        }.execute();
    }

    static void installBottomUpdateBar(JFrame frame) {
        if (frame == null) return;
        Container content = frame.getContentPane();
        if (!(content.getLayout() instanceof BorderLayout)) return;
        if (findNamed(content, UPDATE_CONTROL_NAME) != null) return;

        BorderLayout layout = (BorderLayout) content.getLayout();
        Component oldSouth = layout.getLayoutComponent(BorderLayout.SOUTH);
        JPanel stack = new JPanel(new BorderLayout(0, 4));
        if (oldSouth != null) {
            content.remove(oldSouth);
            stack.add(oldSouth, BorderLayout.CENTER);
        }

        JPanel footer = new JPanel(new BorderLayout());
        footer.setName(UPDATE_CONTROL_NAME);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JLabel version = new JLabel("v" + LicenseRecoverModernGUIGitHubUpdateService
                .readCurrentVersion(toolDir()));
        JButton update = new JButton("检查更新");
        update.setToolTipText("从 GitHub 检查最新正式版本");
        update.addActionListener(e -> LicenseRecoverModernGUILauncher.main(
                new String[]{"--update-only"}));
        right.add(version);
        right.add(update);
        footer.add(right, BorderLayout.EAST);
        stack.add(footer, BorderLayout.SOUTH);
        content.add(stack, BorderLayout.SOUTH);
        content.revalidate();
        content.repaint();
    }

    private static JPanel findTitledPanel(Container root, String title) {
        for (Component child : root.getComponents()) {
            if (child instanceof JPanel) {
                JPanel p = (JPanel) child;
                if (p.getBorder() instanceof TitledBorder
                        && title.equals(((TitledBorder) p.getBorder()).getTitle())) return p;
            }
            if (child instanceof Container) {
                JPanel nested = findTitledPanel((Container) child, title);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static JTextField findTextFieldInTitledPanel(Container root, String title) {
        JPanel panel = findTitledPanel(root, title);
        return panel == null ? null : findTextField(panel);
    }

    private static JTextField findTextField(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTextField) return (JTextField) child;
            if (child instanceof Container) {
                JTextField field = findTextField((Container) child);
                if (field != null) return field;
            }
        }
        return null;
    }

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JCheckBox && text.equals(((JCheckBox) child).getText())) return (JCheckBox) child;
            if (child instanceof Container) {
                JCheckBox box = findCheckBox((Container) child, text);
                if (box != null) return box;
            }
        }
        return null;
    }

    private static JTextArea findTextAreaInTitledPanel(Container root, String title) {
        JPanel panel = findTitledPanel(root, title);
        return panel == null ? null : findTextArea(panel);
    }

    private static JTextArea findTextArea(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTextArea) return (JTextArea) child;
            if (child instanceof Container) {
                JTextArea area = findTextArea((Container) child);
                if (area != null) return area;
            }
        }
        return null;
    }

    private static void append(final JTextArea area, final String text) {
        if (area == null || text == null || text.isEmpty()) return;
        appendPersistentOneClickLog(text);
        SwingUtilities.invokeLater(() -> {
            area.append(text);
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    private static final Object ONE_CLICK_LOG_LOCK = new Object();

    private static void appendPersistentOneClickLog(String text) {
        try {
            synchronized (ONE_CLICK_LOG_LOCK) {
                File dir = new File(toolDir(), "logs");
                Files.createDirectories(dir.toPath());
                String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
                File file = new File(dir, "LicenseRecoverGUI-" + day + ".log");
                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String entry = "[" + stamp + "] " + text;
                Files.write(file.toPath(), entry.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Throwable ignore) {
            // Diagnostic persistence must never turn recovery into a failure.
        }
    }

    private static Component findNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent && name.equals(((JComponent) child).getName())) return child;
            if (child instanceof Container) {
                Component nested = findNamed((Container) child, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static JFrame findMainFrame() {
        for (Frame candidate : Frame.getFrames()) {
            if (candidate instanceof JFrame && candidate.isDisplayable()) {
                JFrame f = (JFrame) candidate;
                String title = f.getTitle();
                if (title != null && title.contains("离线授权恢复工具")) return f;
            }
        }
        return null;
    }

    private static File toolDir() {
        try {
            File location = new File(LicenseRecoverModernGUIUiPatchLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return location.isFile() ? location.getParentFile() : location;
        } catch (Exception ex) {
            return new File(".").getAbsoluteFile();
        }
    }

    private static boolean contains(String[] args, String value) {
        return args != null && Arrays.asList(args).contains(value);
    }
}

/**
 * Compatibility launcher for the original full-featured GUI shown by the
 * legacy JAR. It constrains long hexadecimal fields after the original UI is
 * built so their contents are clipped inside the text box instead of widening
 * the whole panel, then adds the same bottom-right update control.
 */
final class LicenseRecoverModernGUILegacyUiLauncher {
    private LicenseRecoverModernGUILegacyUiLauncher() { }

    public static void main(String[] args) {
        LicenseRecoverGUI.main(args);
        SwingUtilities.invokeLater(() -> {
            constrainLongField(LicenseRecoverGUI.seqField, 28, false);
            constrainLongField(LicenseRecoverGUI.codeField, 28, true);
            if (LicenseRecoverGUI.frame != null) {
                LicenseRecoverModernGUIUiPatchLauncher.installBottomUpdateBar(LicenseRecoverGUI.frame);
                LicenseRecoverGUI.frame.getContentPane().revalidate();
                LicenseRecoverGUI.frame.getContentPane().repaint();
            }
        });
    }

    static void constrainLongField(final JTextField field, int columns, boolean keepStartVisible) {
        if (field == null) return;
        field.setColumns(columns);
        Dimension preferred = field.getPreferredSize();
        field.setMinimumSize(new Dimension(Math.max(80, preferred.height * 4), preferred.height));
        field.setHorizontalAlignment(JTextField.LEFT);
        if (keepStartVisible) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                private void reset() {
                    SwingUtilities.invokeLater(() -> {
                        try { field.setCaretPosition(0); }
                        catch (Exception ignore) { }
                    });
                }
                public void insertUpdate(DocumentEvent e) { reset(); }
                public void removeUpdate(DocumentEvent e) { reset(); }
                public void changedUpdate(DocumentEvent e) { reset(); }
            });
            try { field.setCaretPosition(0); }
            catch (Exception ignore) { }
        }
        field.revalidate();
    }
}
