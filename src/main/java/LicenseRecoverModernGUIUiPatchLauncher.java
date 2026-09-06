import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.Arrays;

/**
 * Adds visible update controls to the GUI without changing the recovery logic.
 * The class name intentionally uses the LicenseRecoverModernGUI prefix so the
 * existing deterministic overlay build includes it automatically.
 */
public final class LicenseRecoverModernGUIUiPatchLauncher {
    private static final String UPDATE_CONTROL_NAME = "LicenseRecoverUpdateControl";

    private LicenseRecoverModernGUIUiPatchLauncher() { }

    public static void main(String[] args) {
        LicenseRecoverModernGUILauncher.main(args);
        if (contains(args, "--update-only")) return;
        SwingUtilities.invokeLater(() -> installBottomUpdateBar(findMainFrame()));
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
