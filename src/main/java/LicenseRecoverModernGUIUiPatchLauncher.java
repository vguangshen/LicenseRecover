import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * GUI overlay patch：
 * 1. 提供“选择目录 -> 一键恢复 -> 自动验证”的主入口；
 * 2. 原方式一/方式二保留为可展开的手工回退；
 * 3. 保留底部版本号与 GitHub 在线更新入口。
 *
 * 类名前缀保持 LicenseRecoverModernGUI，确保 deterministic overlay 构建自动打包。
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

    static void installOneClickRecovery(JFrame frame) {
        if (frame == null) return;
        Container content = frame.getContentPane();
        if (findNamed(content, ONE_CLICK_CONTROL_NAME) != null) return;

        JPanel pathCard = findTitledPanel(content, "应用目录");
        JPanel manualCard = findTitledPanel(content, "常用操作");
        JTextArea logArea = findFirst(content, JTextArea.class);
        if (pathCard == null || manualCard == null) return;
        JTextField appPath = findFirst(pathCard, JTextField.class);
        if (appPath == null) return;

        updateSubtitle(content);

        final Container parent = manualCard.getParent();
        if (parent == null) return;
        int oldIndex = indexOf(parent, manualCard);
        if (oldIndex < 0) return;

        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setName(ONE_CLICK_CONTROL_NAME);
        card.setBorder(BorderFactory.createTitledBorder("一键恢复授权"));

        JLabel description = new JLabel(
                "自动识别 Java / .NET，获取本机身份、生成并提交本地授权，最后重新读取并自校验。" );
        card.add(description, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 0));
        JButton recover = new JButton("一键恢复授权");
        recover.setFont(recover.getFont().deriveFont(Font.BOLD, 15f));
        recover.setPreferredSize(new Dimension(190, 40));
        center.add(recover, BorderLayout.WEST);

        JLabel status = new JLabel("选择应用目录后即可开始");
        center.add(status, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        JCheckBox backup = new JCheckBox("自动备份", true);
        JCheckBox dryRun = new JCheckBox("只预览，不写入", false);
        JCheckBox showManual = new JCheckBox("显示手工工具（旧方式一 / 方式二）", false);
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.add(backup);
        options.add(dryRun);
        options.add(showManual);
        card.add(options, BorderLayout.SOUTH);

        manualCard.setVisible(false);
        showManual.addActionListener(e -> {
            manualCard.setVisible(showManual.isSelected());
            parent.revalidate();
            parent.repaint();
        });

        recover.addActionListener(e -> {
            String path = appPath.getText() == null ? "" : appPath.getText().trim();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请先选择应用目录。", "一键恢复",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            final File selected = new File(path);
            recover.setEnabled(false);
            status.setText("正在自动识别并恢复...");
            appendLog(logArea, "\n========== 一键恢复授权 ==========" + System.lineSeparator());

            new SwingWorker<OperationResult, String>() {
                protected OperationResult doInBackground() {
                    return LicenseRecoverModernGUIOneClickRecovery.recover(
                            selected, dryRun.isSelected(), backup.isSelected(), this::publish);
                }

                protected void process(List<String> chunks) {
                    for (String chunk : chunks) appendLog(logArea, chunk);
                }

                protected void done() {
                    recover.setEnabled(true);
                    try {
                        OperationResult result = get();
                        status.setText(result.isSuccess() ? "完成：" + result.message : "失败：" + result.message);
                        appendLog(logArea, "[一键恢复] " + result.status + " - " + result.message + "\n");
                        if (result.isSuccess()) {
                            JOptionPane.showMessageDialog(frame, result.message, "一键恢复",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(frame, result.message + "\n可展开手工工具继续排查。",
                                    "一键恢复失败", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        status.setText("失败：" + ex.getMessage());
                        appendLog(logArea, "[一键恢复/错误] " + ex + "\n");
                        JOptionPane.showMessageDialog(frame, ex.toString(), "一键恢复失败",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        parent.add(card, oldIndex);
        parent.revalidate();
        parent.repaint();
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

    private static void updateSubtitle(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel) {
                JLabel label = (JLabel) child;
                if ("检测 → 推荐操作 → 高级操作".equals(label.getText())) {
                    label.setText("选择目录 → 一键恢复 → 自动验证");
                    return;
                }
            }
            if (child instanceof Container) updateSubtitle((Container) child);
        }
    }

    private static void appendLog(JTextArea area, String text) {
        if (area == null || text == null) return;
        area.append(text);
        area.setCaretPosition(area.getDocument().getLength());
    }

    private static int indexOf(Container parent, Component child) {
        Component[] all = parent.getComponents();
        for (int i = 0; i < all.length; i++) if (all[i] == child) return i;
        return -1;
    }

    private static JPanel findTitledPanel(Container root, String title) {
        for (Component child : root.getComponents()) {
            if (child instanceof JPanel) {
                JPanel panel = (JPanel) child;
                if (panel.getBorder() instanceof TitledBorder
                        && title.equals(((TitledBorder) panel.getBorder()).getTitle())) {
                    return panel;
                }
            }
            if (child instanceof Container) {
                JPanel nested = findTitledPanel((Container) child, title);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static <T extends Component> T findFirst(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container) {
                T nested = findFirst((Container) child, type);
                if (nested != null) return nested;
            }
        }
        return null;
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
