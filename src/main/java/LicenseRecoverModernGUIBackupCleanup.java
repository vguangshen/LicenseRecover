import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe manual cleanup for backup files created by LicenseRecover. */
public final class LicenseRecoverModernGUIBackupCleanup {
    private static final String CONTROL_NAME = "LicenseRecoverBackupCleanupControl";
    private static final Pattern MARKED = Pattern.compile("(?i)^(.+)\\.(prewrite|prepatch)\\.(\\d{14})\\.bak$");
    private static final Pattern PRE_ONE_CLICK = Pattern.compile("(?i)^(.+)\\.(\\d{14})(?:-(\\d+))?\\.preoneclick\\.bak$");
    private static final Pattern LEGACY = Pattern.compile(
            "(?i)^(config\\.xml|Register\\.xml|RegisterUtil\\.class|ITMCReg[^\\\\/]*\\.jar|itmcRegedit\\.dll|ITMC\\.Regedit\\.dll)\\.(\\d{14})\\.bak$");

    private LicenseRecoverModernGUIBackupCleanup() { }

    static final class Candidate {
        final Path path;
        final String originalName;
        final String timestamp;
        final int sequence;
        final long size;
        Candidate(Path path, String originalName, String timestamp, int sequence, long size) {
            this.path = path; this.originalName = originalName; this.timestamp = timestamp;
            this.sequence = sequence; this.size = size;
        }
        String groupKey() {
            Path parent = path.getParent();
            String p = parent == null ? "" : parent.toAbsolutePath().normalize().toString();
            return p.toLowerCase(Locale.ROOT) + "\u0000" + originalName.toLowerCase(Locale.ROOT);
        }
    }

    public static final class Plan {
        public final File root;
        public final int keepNewest;
        public final List<File> matched;
        public final List<File> delete;
        public final int keepCount;
        public final long deleteBytes;
        Plan(File root, int keepNewest, List<File> matched, List<File> delete, int keepCount, long deleteBytes) {
            this.root = root; this.keepNewest = keepNewest;
            this.matched = Collections.unmodifiableList(matched);
            this.delete = Collections.unmodifiableList(delete);
            this.keepCount = keepCount; this.deleteBytes = deleteBytes;
        }
    }

    public static final class DeleteResult {
        public final int deleted;
        public final int failed;
        public final long freedBytes;
        DeleteResult(int deleted, int failed, long freedBytes) {
            this.deleted = deleted; this.failed = failed; this.freedBytes = freedBytes;
        }
    }

    public static void installLater(String[] args) {
        if (contains(args, "--update-only")) return;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = findMainFrame();
            if (frame != null) install(frame);
        });
    }

    static void install(JFrame frame) {
        if (frame == null || findNamed(frame.getContentPane(), CONTROL_NAME) != null) return;
        JTabbedPane tabs = findTabs(frame.getContentPane());
        if (tabs == null) return;
        Component batch = null;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if ("批量应用".equals(tabs.getTitleAt(i))) { batch = tabs.getComponentAt(i); break; }
        }
        if (batch == null) return;
        JPanel top = findBatchTopPanel(batch);
        final JTextField rootField = top == null ? null : findFirst(top, JTextField.class);
        if (top == null || rootField == null) return;

        final JSpinner keepSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 50, 1));
        keepSpinner.setToolTipText("按每个原文件计算；0 表示删除全部受管理备份");
        final JButton cleanup = new JButton("批量清理备份...");
        cleanup.setName(CONTROL_NAME);
        cleanup.setToolTipText("仅处理 LicenseRecover 生成的 prewrite / prepatch / preoneclick 和兼容旧版时间戳备份");
        cleanup.addActionListener(e -> runCleanup(frame, rootField, keepSpinner, cleanup));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("每个原文件保留最近"));
        controls.add(keepSpinner);
        controls.add(new JLabel("份"));
        controls.add(cleanup);
        JLabel hint = new JLabel("（先预览，确认后删除；普通 .bak 不处理）");
        hint.setForeground(new Color(0x57606a));
        controls.add(hint);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6); c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 3; c.weightx = 0;
        top.add(new JLabel("备份整理:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1;
        top.add(controls, c);
        top.revalidate(); top.repaint();
    }

    private static void runCleanup(final JFrame frame, final JTextField rootField,
                                   final JSpinner keepSpinner, final JButton button) {
        final File root = new File(rootField.getText().trim());
        if (!root.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "请先在批量应用页选择有效的父目录。", "批量清理备份", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JButton cancel = findButton(frame.getContentPane(), "取消");
        if (cancel != null && cancel.isEnabled()) {
            JOptionPane.showMessageDialog(frame, "当前有批量扫描/执行任务正在运行，请先等待完成或取消任务。", "批量清理备份", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final int keep = ((Number) keepSpinner.getValue()).intValue();
        button.setEnabled(false); button.setText("正在扫描备份...");
        appendGuiLog(frame, "[备份清理] 扫描: " + root.getAbsolutePath() + "；每个原文件保留最近 " + keep + " 份。\n");
        new SwingWorker<Plan, Void>() {
            protected Plan doInBackground() throws Exception { return scan(root, keep); }
            protected void done() {
                Plan plan;
                try { plan = get(); }
                catch (Exception ex) {
                    resetButton(button);
                    JOptionPane.showMessageDialog(frame, "扫描备份失败：\n" + safeMessage(ex), "批量清理备份", JOptionPane.ERROR_MESSAGE);
                    appendGuiLog(frame, "[备份清理] 扫描失败: " + safeMessage(ex) + "\n");
                    return;
                }
                if (plan.matched.isEmpty()) {
                    resetButton(button);
                    JOptionPane.showMessageDialog(frame, "没有找到 LicenseRecover 管理的备份文件。\n普通 .bak 文件不会被匹配。",
                            "批量清理备份", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (plan.delete.isEmpty()) {
                    resetButton(button);
                    JOptionPane.showMessageDialog(frame, "找到 " + plan.matched.size() + " 个 LicenseRecover 备份，当前保留策略无需删除。",
                            "批量清理备份", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                String warning = keep == 0 ? "\n\n注意：当前设置为保留 0 份，将删除扫描范围内全部受管理备份。" : "";
                int answer = JOptionPane.showConfirmDialog(frame,
                        "扫描目录：" + plan.root.getAbsolutePath()
                                + "\n找到受管理备份：" + plan.matched.size() + " 个"
                                + "\n保留：" + plan.keepCount + " 个"
                                + "\n准备删除：" + plan.delete.size() + " 个"
                                + "\n预计释放：" + humanBytes(plan.deleteBytes)
                                + "\n\n仅匹配 LicenseRecover 的 prewrite / prepatch / preoneclick"
                                + "\n以及旧版 config.xml / RegisterUtil / ITMCReg 时间戳备份；不会清理普通 .bak。"
                                + warning + "\n\n确定执行删除吗？",
                        "确认批量清理备份", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.OK_OPTION) {
                    resetButton(button); appendGuiLog(frame, "[备份清理] 用户取消；未删除任何文件。\n"); return;
                }
                executeAsync(frame, plan, button);
            }
        }.execute();
    }

    private static void executeAsync(final JFrame frame, final Plan plan, final JButton button) {
        button.setText("正在清理...");
        new SwingWorker<DeleteResult, Void>() {
            protected DeleteResult doInBackground() { return execute(plan, s -> appendGuiLog(frame, s)); }
            protected void done() {
                resetButton(button);
                try {
                    DeleteResult result = get();
                    int type = result.failed == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;
                    JOptionPane.showMessageDialog(frame,
                            "清理完成。\n已删除：" + result.deleted + " 个\n失败：" + result.failed + " 个\n实际释放：" + humanBytes(result.freedBytes),
                            "批量清理备份", type);
                    appendGuiLog(frame, "[备份清理] 完成：删除 " + result.deleted + "，失败 " + result.failed
                            + "，释放 " + humanBytes(result.freedBytes) + "。\n");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "清理失败：\n" + safeMessage(ex), "批量清理备份", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static void resetButton(JButton button) { button.setEnabled(true); button.setText("批量清理备份..."); }

    public static Plan scan(File root, int keepNewest) throws IOException {
        if (root == null || !root.isDirectory()) throw new IOException("清理根目录无效");
        if (keepNewest < 0) throw new IllegalArgumentException("keepNewest 不能小于 0");
        final Path rootPath = root.toPath().toAbsolutePath().normalize();
        final List<Candidate> candidates = new ArrayList<Candidate>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(rootPath) && Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs != null && attrs.isRegularFile() && !Files.isSymbolicLink(file)) {
                    Candidate candidate = parse(file, attrs.size());
                    if (candidate != null) candidates.add(candidate);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        LinkedHashMap<String, List<Candidate>> groups = new LinkedHashMap<String, List<Candidate>>();
        for (Candidate candidate : candidates) {
            List<Candidate> group = groups.get(candidate.groupKey());
            if (group == null) { group = new ArrayList<Candidate>(); groups.put(candidate.groupKey(), group); }
            group.add(candidate);
        }
        Comparator<Candidate> newestFirst = new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                int byStamp = b.timestamp.compareTo(a.timestamp); if (byStamp != 0) return byStamp;
                int bySeq = Integer.compare(b.sequence, a.sequence); if (bySeq != 0) return bySeq;
                return b.path.toString().compareToIgnoreCase(a.path.toString());
            }
        };
        List<File> delete = new ArrayList<File>(); long bytes = 0L; int kept = 0;
        for (List<Candidate> group : groups.values()) {
            Collections.sort(group, newestFirst);
            for (int i = 0; i < group.size(); i++) {
                Candidate candidate = group.get(i);
                if (i < keepNewest) kept++;
                else { delete.add(candidate.path.toFile()); bytes += candidate.size; }
            }
        }
        List<File> matched = new ArrayList<File>();
        for (Candidate candidate : candidates) matched.add(candidate.path.toFile());
        Comparator<File> byPath = Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(matched, byPath); Collections.sort(delete, byPath);
        return new Plan(rootPath.toFile(), keepNewest, matched, delete, kept, bytes);
    }

    public static DeleteResult execute(Plan plan, Consumer<String> log) {
        if (plan == null || plan.root == null) return new DeleteResult(0, 0, 0L);
        Consumer<String> sink = log == null ? s -> { } : log;
        Path root = plan.root.toPath().toAbsolutePath().normalize();
        int deleted = 0, failed = 0; long freed = 0L;
        for (File file : plan.delete) {
            try {
                Path path = file.toPath().toAbsolutePath().normalize();
                if (!path.startsWith(root) || Files.isSymbolicLink(path) || !Files.isRegularFile(path)
                        || parse(path, Files.size(path)) == null) {
                    failed++; sink.accept("[备份清理] 跳过已变化/不安全路径: " + path + "\n"); continue;
                }
                long size = Files.size(path); Files.delete(path); deleted++; freed += size;
                sink.accept("[备份清理] 已删除: " + path + "\n");
            } catch (Exception ex) {
                failed++; sink.accept("[备份清理] 删除失败: " + file.getAbsolutePath() + " : " + safeMessage(ex) + "\n");
            }
        }
        return new DeleteResult(deleted, failed, freed);
    }

    public static boolean isManagedBackupName(String name) {
        return name != null && (MARKED.matcher(name).matches() || PRE_ONE_CLICK.matcher(name).matches() || LEGACY.matcher(name).matches());
    }

    private static Candidate parse(Path path, long size) {
        String name = path.getFileName().toString();
        Matcher m = MARKED.matcher(name);
        if (m.matches()) return new Candidate(path, m.group(1), m.group(3), 0, size);
        m = PRE_ONE_CLICK.matcher(name);
        if (m.matches()) {
            int seq = 0; try { if (m.group(3) != null) seq = Integer.parseInt(m.group(3)); } catch (NumberFormatException ignore) { }
            return new Candidate(path, m.group(1), m.group(2), seq, size);
        }
        m = LEGACY.matcher(name);
        if (m.matches()) return new Candidate(path, m.group(1), m.group(2), 0, size);
        return null;
    }

    private static JFrame findMainFrame() {
        for (Frame frame : Frame.getFrames()) if (frame instanceof JFrame && frame.isDisplayable()) {
            JFrame f = (JFrame) frame; if ("ITMC 离线授权恢复工具".equals(f.getTitle())) return f;
        }
        return null;
    }
    private static JTabbedPane findTabs(Container root) {
        if (root instanceof JTabbedPane) return (JTabbedPane) root;
        for (Component c : root.getComponents()) if (c instanceof Container) {
            JTabbedPane found = findTabs((Container)c); if (found != null) return found;
        }
        return null;
    }
    private static JPanel findBatchTopPanel(Component root) {
        if (root instanceof JPanel) {
            JPanel p = (JPanel)root; if (p.getLayout() instanceof GridBagLayout && hasLabel(p, "父目录:")) return p;
        }
        if (root instanceof Container) for (Component c : ((Container)root).getComponents()) {
            JPanel found = findBatchTopPanel(c); if (found != null) return found;
        }
        return null;
    }
    private static boolean hasLabel(Container root, String text) {
        for (Component c : root.getComponents()) if (c instanceof JLabel && text.equals(((JLabel)c).getText())) return true;
        return false;
    }
    private static <T extends Component> T findFirst(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container) { T found = findFirst((Container)c, type); if (found != null) return found; }
        }
        return null;
    }
    private static Component findNamed(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container) { Component found = findNamed((Container)c, name); if (found != null) return found; }
        }
        return null;
    }
    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && text.equals(((JButton)c).getText())) return (JButton)c;
            if (c instanceof Container) { JButton found = findButton((Container)c, text); if (found != null) return found; }
        }
        return null;
    }
    private static void appendGuiLog(JFrame frame, final String text) {
        if (frame == null || text == null || text.isEmpty()) return;
        Runnable r = () -> { JTextArea area = findLogArea(frame.getContentPane()); if (area != null) {
            area.append(text); area.setCaretPosition(area.getDocument().getLength());
        }};
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }
    private static JTextArea findLogArea(Container root) {
        if (root instanceof JPanel) {
            JPanel p = (JPanel)root;
            if (p.getBorder() instanceof TitledBorder && "运行日志".equals(((TitledBorder)p.getBorder()).getTitle())) return findFirst(p, JTextArea.class);
        }
        for (Component c : root.getComponents()) if (c instanceof Container) {
            JTextArea found = findLogArea((Container)c); if (found != null) return found;
        }
        return null;
    }
    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes; String[] units = {"KB", "MB", "GB", "TB"}; int unit = -1;
        do { value /= 1024.0; unit++; } while (value >= 1024.0 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }
    private static boolean contains(String[] args, String wanted) {
        if (args != null) for (String arg : args) if (wanted.equals(arg)) return true; return false;
    }
    private static String safeMessage(Throwable ex) {
        Throwable x = ex; while (x != null && x.getCause() != null) x = x.getCause();
        String message = x == null ? "未知错误" : x.getMessage();
        return message == null || message.trim().isEmpty() ? String.valueOf(x) : message;
    }

    /** CI self-test for matching, retention, deletion and unrelated-file safety. */
    public static void main(String[] args) throws Exception {
        if (!contains(args, "--self-test")) return;
        Path root = Files.createTempDirectory("lrc-backup-cleanup-");
        try {
            Path app = root.resolve("app-a/WEB-INF/lib"); Files.createDirectories(app);
            write(app.resolve("config.xml.20260901010101.bak"), 11);
            write(app.resolve("config.xml.prewrite.20260902010101.bak"), 12);
            write(app.resolve("config.xml.20260903010101.preoneclick.bak"), 13);
            write(app.resolve("config.xml.20260904010101-2.preoneclick.bak"), 14);
            write(app.resolve("ITMCReg.jar.20260901010101.bak"), 21);
            write(app.resolve("ITMCReg.jar.prepatch.20260902010101.bak"), 22);
            write(app.resolve("ITMCReg.jar.20260903010101.preoneclick.bak"), 23);
            Path unrelated = app.resolve("database.20260901010101.bak");
            Path ordinary = app.resolve("notes.bak"); write(unrelated, 31); write(ordinary, 32);

            Plan plan = scan(root.toFile(), 2);
            require(plan.matched.size() == 7, "managed backup match count");
            require(plan.delete.size() == 3, "retention delete count");
            require(plan.keepCount == 4, "retention keep count");
            require(isManagedBackupName("x.prepatch.20260910121212.bak"), "prepatch matcher");
            require(isManagedBackupName("config.xml.20260910121212.preoneclick.bak"), "preoneclick matcher");
            require(!isManagedBackupName("database.20260910121212.bak"), "unrelated timestamp backup excluded");
            require(!isManagedBackupName("notes.bak"), "ordinary bak excluded");
            DeleteResult result = execute(plan, System.out::print);
            require(result.deleted == 3 && result.failed == 0, "cleanup execution");
            require(Files.exists(app.resolve("config.xml.20260904010101-2.preoneclick.bak")), "newest config backup retained");
            require(Files.exists(app.resolve("config.xml.20260903010101.preoneclick.bak")), "second newest config backup retained");
            require(!Files.exists(app.resolve("config.xml.20260901010101.bak")), "old config backup deleted");
            require(Files.exists(unrelated) && Files.exists(ordinary), "unrelated backups preserved");
            System.out.println("BACKUP CLEANUP SELF-TEST PASSED");
        } finally { deleteTree(root); }
    }
    private static void write(Path path, int marker) throws IOException {
        byte[] data = new byte[Math.max(1, marker)]; data[0] = (byte)marker; Files.write(path, data);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message); System.out.println("PASS: " + message);
    }
    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try { Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir); return FileVisitResult.CONTINUE;
            }
        }); } catch (IOException ignore) { }
    }
}
