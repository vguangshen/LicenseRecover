import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Default GUI launcher with a GitHub Releases based self-update check.
 * The updater keeps Java 8 compatibility and uses a verified slim package when available.
 */
public final class LicenseRecoverModernGUILauncher {
    private static final AtomicBoolean UPDATE_RUNNING = new AtomicBoolean(false);
    private static final String[] OBSOLETE_ENTRYPOINTS = {
            "LicenseRecoverGUI-legacy.exe",
            "run.bat",
            "run_gui.bat",
            "run_gui_modern.bat",
            "run_gui_legacy.bat",
            "run_removenet.bat",
            "run_removenet_safe.bat",
            "run_removenet_legacy.bat"
    };

    private LicenseRecoverModernGUILauncher() { }

    public static void main(String[] args) {
        final boolean updateOnly = contains(args, "--update-only");
        if (!updateOnly) {
            LicenseRecoverModernGUI.main(args);
            scheduleObsoleteEntrypointCleanup(toolDir());
        }

        Thread checker = new Thread(() -> {
            if (!updateOnly) {
                try { Thread.sleep(1200L); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
            }
            checkForUpdates(updateOnly);
        }, "LicenseRecover-GitHub-Update");
        checker.setDaemon(!updateOnly);
        checker.start();
    }

    private static void checkForUpdates(boolean manual) {
        if (!UPDATE_RUNNING.compareAndSet(false, true)) {
            if (manual) showMessage("已有更新检查或下载任务正在进行。",
                    "检查更新", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File toolDir = toolDir();
        LicenseRecoverModernGUIUpdateProgressDialog progressDialog = null;
        try {
            LicenseRecoverModernGUIUpdateInfo info =
                    LicenseRecoverModernGUIGitHubUpdateService.checkLatest(toolDir);
            if (!info.isUpdateAvailable()) {
                if (manual) showMessage("当前版本 v" + info.currentVersion + " 已是最新正式版。",
                        "检查更新", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int choice = showConfirm(
                    "发现新版本 v" + info.latestVersion + "\n"
                            + "当前版本: v" + info.currentVersion + "\n\n"
                            + "是否从 GitHub 下载并自动安装？\n"
                            + "下载过程会显示实时进度，完成后校验 SHA-256，校验通过才会覆盖当前文件。",
                    "发现新版本");
            if (choice != JOptionPane.OK_OPTION) return;

            progressDialog = LicenseRecoverModernGUIUpdateProgressDialog.open(info.latestVersion);
            File zip = LicenseRecoverModernGUIGitHubUpdateService.downloadVerified(
                    info, s -> System.out.print(s), progressDialog);
            if (progressDialog != null) {
                progressDialog.close();
                progressDialog = null;
            }

            int install = showConfirm(
                    "v" + info.latestVersion + " 已下载并通过 SHA-256 校验。\n\n"
                            + "点击“确定”后软件将退出，自动覆盖更新并直接重新启动 LicenseRecoverGUI.exe。",
                    "准备安装更新");
            if (install != JOptionPane.OK_OPTION) return;

            LicenseRecoverModernGUIGitHubUpdateService.launchInstaller(
                    zip, toolDir, javaExe());
            System.exit(0);
        } catch (Exception ex) {
            if (manual) {
                showMessage("检查更新失败：\n" + safeMessage(ex),
                        "检查更新", JOptionPane.ERROR_MESSAGE);
            } else {
                System.err.println("[更新] 自动检查失败: " + safeMessage(ex));
            }
        } finally {
            if (progressDialog != null) progressDialog.close();
            UPDATE_RUNNING.set(false);
        }
    }

    private static int showConfirm(final String message, final String title) throws Exception {
        final int[] value = {JOptionPane.CANCEL_OPTION};
        Runnable task = () -> value[0] = JOptionPane.showConfirmDialog(null, message, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
        return value[0];
    }

    private static void showMessage(final String message, final String title, final int type) {
        Runnable task = () -> JOptionPane.showMessageDialog(null, message, title, type);
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else {
            try { SwingUtilities.invokeAndWait(task); }
            catch (Exception ignore) { }
        }
    }

    private static File toolDir() {
        try {
            File location = new File(LicenseRecoverModernGUILauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return location.isFile() ? location.getParentFile() : location;
        } catch (Exception ex) {
            return new File(".").getAbsoluteFile();
        }
    }

    private static String javaExe() {
        File exe = new File(System.getProperty("java.home"), "bin" + File.separator + "java.exe");
        if (!exe.isFile()) exe = new File(System.getProperty("java.home"), "bin" + File.separator + "java");
        return exe.getAbsolutePath();
    }

    private static boolean contains(String[] args, String value) {
        return args != null && Arrays.asList(args).contains(value);
    }

    private static String safeMessage(Throwable ex) {
        String text = ex == null ? "未知错误" : ex.getMessage();
        if (text == null || text.trim().isEmpty()) text = String.valueOf(ex);
        return text.length() > 1200 ? text.substring(0, 1200) + "..." : text;
    }

    private static void scheduleObsoleteEntrypointCleanup(final File dir) {
        Thread cleanup = new Thread(() -> {
            try { Thread.sleep(2500L); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
            cleanupObsoleteEntrypoints(dir);
        }, "LicenseRecover-Entrypoint-Cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    static void cleanupObsoleteEntrypoints(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        for (String name : OBSOLETE_ENTRYPOINTS) {
            File file = new File(dir, name);
            if (!file.exists()) continue;
            boolean removed = false;
            for (int i = 0; i < 8 && !removed; i++) {
                removed = file.delete();
                if (!removed) {
                    try { Thread.sleep(300L); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
                }
            }
            if (!removed && file.exists()) {
                file.deleteOnExit();
                System.err.println("[更新] 旧入口稍后删除: " + file.getName());
            }
        }
    }
}

final class LicenseRecoverModernGUIUpdateInfo {
    final String currentVersion;
    final String latestVersion;
    final String tagName;
    final String releasePageUrl;
    final String zipUrl;
    final String updateZipUrl;
    final String checksumUrl;
    final long zipSize;
    final long updateZipSize;

    LicenseRecoverModernGUIUpdateInfo(String currentVersion, String latestVersion, String tagName,
                                      String releasePageUrl, String zipUrl, String updateZipUrl,
                                      String checksumUrl, long zipSize, long updateZipSize) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.tagName = tagName;
        this.releasePageUrl = releasePageUrl;
        this.zipUrl = zipUrl;
        this.updateZipUrl = updateZipUrl;
        this.checksumUrl = checksumUrl;
        this.zipSize = zipSize;
        this.updateZipSize = updateZipSize;
    }

    boolean isUpdateAvailable() {
        return LicenseRecoverModernGUIGitHubUpdateService.compareVersions(
                latestVersion, currentVersion) > 0;
    }
}

interface LicenseRecoverModernGUIUpdateProgress {
    void status(String text);
    void bytes(long downloaded, long total);
}

final class LicenseRecoverModernGUIUpdateProgressDialog implements LicenseRecoverModernGUIUpdateProgress {
    private final JDialog dialog;
    private final JLabel statusLabel;
    private final JLabel detailLabel;
    private final JProgressBar progressBar;

    private LicenseRecoverModernGUIUpdateProgressDialog(JDialog dialog, JLabel statusLabel,
                                                         JLabel detailLabel, JProgressBar progressBar) {
        this.dialog = dialog;
        this.statusLabel = statusLabel;
        this.detailLabel = detailLabel;
        this.progressBar = progressBar;
    }

    static LicenseRecoverModernGUIUpdateProgressDialog open(final String version) {
        if (GraphicsEnvironment.isHeadless()) return null;
        final LicenseRecoverModernGUIUpdateProgressDialog[] ref = new LicenseRecoverModernGUIUpdateProgressDialog[1];
        Runnable create = () -> {
            JDialog dialog = new JDialog((Frame) null, "软件更新", false);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.setResizable(false);

            JLabel title = new JLabel("正在下载 LicenseRecover v" + version);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
            JLabel status = new JLabel("正在获取更新信息...");
            JLabel detail = new JLabel(" ");
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setIndeterminate(false);
            bar.setValue(0);
            bar.setStringPainted(true);
            bar.setString("0%");

            JPanel panel = new JPanel();
            panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            status.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            bar.setMaximumSize(new Dimension(420, 24));
            panel.add(title);
            panel.add(Box.createVerticalStrut(12));
            panel.add(status);
            panel.add(Box.createVerticalStrut(8));
            panel.add(bar);
            panel.add(Box.createVerticalStrut(6));
            panel.add(detail);

            dialog.setContentPane(panel);
            dialog.pack();
            dialog.setSize(Math.max(dialog.getWidth(), 470), dialog.getHeight());
            dialog.setLocationRelativeTo(null);
            ref[0] = new LicenseRecoverModernGUIUpdateProgressDialog(dialog, status, detail, bar);
            dialog.setVisible(true);
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) create.run();
            else SwingUtilities.invokeAndWait(create);
        } catch (Exception ex) {
            System.err.println("[更新] 无法创建下载进度窗口: " + ex.getMessage());
            return null;
        }
        return ref[0];
    }

    public void status(final String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text == null ? "" : text));
    }

    public void bytes(final long downloaded, final long total) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                int percent = (int) Math.max(0, Math.min(100,
                        Math.round((downloaded * 100.0) / total)));
                progressBar.setIndeterminate(false);
                progressBar.setValue(percent);
                progressBar.setString(percent + "%");
                detailLabel.setText(formatBytes(downloaded) + " / " + formatBytes(total));
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setString("下载中");
                detailLabel.setText(formatBytes(downloaded));
            }
        });
    }

    void close() {
        SwingUtilities.invokeLater(() -> dialog.dispose());
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb);
        return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
    }
}

final class LicenseRecoverModernGUIGitHubUpdateService {
    static final String REPOSITORY = "vguangshen/LicenseRecover";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int MAX_REDIRECTS = 8;
    private static final long NATIVE_DOWNLOAD_TIMEOUT_MS = 180000L;
    private static final LicenseRecoverModernGUIUpdateProgress NO_PROGRESS =
            new LicenseRecoverModernGUIUpdateProgress() {
                public void status(String text) { }
                public void bytes(long downloaded, long total) { }
            };

    private LicenseRecoverModernGUIGitHubUpdateService() { }

    static LicenseRecoverModernGUIUpdateInfo checkLatest(File toolDir) throws IOException {
        String current = readCurrentVersion(toolDir);
        String json = getText(API_LATEST);
        String tag = jsonString(json, "tag_name");
        String page = jsonString(json, "html_url");
        if (tag == null || tag.trim().isEmpty())
            throw new IOException("GitHub latest release 缺少 tag_name。");
        String latest = normalizeVersion(tag);
        if (!SEMVER.matcher(latest).matches())
            throw new IOException("不支持的 GitHub Release 标签: " + tag);
        String base = "https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/";
        long portableSize = parseAssetSize(json, "LicenseRecover-latest.zip");
        long updateSize = parseAssetSize(json, "LicenseRecover-update.zip");
        return new LicenseRecoverModernGUIUpdateInfo(current, latest, tag, page,
                base + "LicenseRecover-latest.zip",
                base + "LicenseRecover-update.zip",
                base + "SHA256SUMS.txt", portableSize, updateSize);
    }

    static String readCurrentVersion(File toolDir) {
        File versionFile = toolDir == null ? null : new File(toolDir, "VERSION.txt");
        if (versionFile != null && versionFile.isFile()) {
            try {
                String value = new String(Files.readAllBytes(versionFile.toPath()),
                        StandardCharsets.UTF_8).trim();
                value = normalizeVersion(value);
                if (SEMVER.matcher(value).matches()) return value;
            } catch (Exception ignore) { }
        }
        return "0.0.0";
    }

    static int compareVersions(String left, String right) {
        long[] a = parseVersion(left);
        long[] b = parseVersion(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] < b[i]) return -1;
            if (a[i] > b[i]) return 1;
        }
        return 0;
    }

    private static long[] parseVersion(String value) {
        String normalized = normalizeVersion(value);
        if (!SEMVER.matcher(normalized).matches())
            throw new IllegalArgumentException("无效版本号: " + value);
        String[] parts = normalized.split("\\.");
        return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
    }

    private static String normalizeVersion(String value) {
        if (value == null) return "";
        String v = value.trim();
        return (v.startsWith("v") || v.startsWith("V")) ? v.substring(1) : v;
    }

    static File downloadVerified(LicenseRecoverModernGUIUpdateInfo info,
                                 Consumer<String> log) throws IOException {
        return downloadVerified(info, log, null);
    }

    static File downloadVerified(LicenseRecoverModernGUIUpdateInfo info,
                                 Consumer<String> log,
                                 LicenseRecoverModernGUIUpdateProgress progress) throws IOException {
        Consumer<String> sink = log == null ? s -> { } : log;
        LicenseRecoverModernGUIUpdateProgress indicator = progress == null ? NO_PROGRESS : progress;

        indicator.status("正在获取 SHA-256 校验信息...");
        sink.accept("[更新] 下载 SHA256SUMS.txt...\n");
        String sums = getText(info.checksumUrl);

        String fileName = "LicenseRecover-update.zip";
        String downloadUrl = info.updateZipUrl;
        long expectedSize = info.updateZipSize;
        String expected = parseChecksum(sums, fileName);
        if (expected == null) {
            fileName = "LicenseRecover-latest.zip";
            downloadUrl = info.zipUrl;
            expectedSize = info.zipSize;
            expected = parseChecksum(sums, fileName);
        }
        if (expected == null) throw new IOException("校验文件中未找到可用的 LicenseRecover 更新包。");

        File dir = Files.createTempDirectory("LicenseRecover-update-").toFile();
        File zip = new File(dir, fileName);
        indicator.status("正在连接 GitHub 下载节点...");
        indicator.bytes(0L, expectedSize);
        sink.accept("[更新] 下载 " + fileName + "...\n");
        download(downloadUrl, zip, expectedSize, indicator, sink);
        if (expectedSize > 0L && zip.length() != expectedSize) {
            long actualSize = zip.length();
            zip.delete();
            throw new IOException("更新包下载不完整：" + actualSize + " / " + expectedSize + " 字节。");
        }

        indicator.status("下载完成，正在校验 SHA-256...");
        String actual = sha256(zip);
        sink.accept("[更新] SHA-256: " + actual + "\n");
        if (!expected.equalsIgnoreCase(actual)) {
            zip.delete();
            throw new SecurityException("下载包 SHA-256 与 GitHub 校验文件不一致。");
        }
        indicator.bytes(zip.length(), zip.length());
        indicator.status("SHA-256 校验通过");
        sink.accept("[更新] SHA-256 校验通过。\n");
        return zip;
    }

    static String parseChecksum(String text) {
        return parseChecksum(text, "LicenseRecover-latest.zip");
    }

    static String parseChecksum(String text, String fileName) {
        if (text == null || fileName == null || fileName.trim().isEmpty()) return null;
        Matcher m = Pattern.compile(
                "(?im)^\\s*([0-9a-f]{64})\\s+\\*?" + Pattern.quote(fileName) + "\\s*$")
                .matcher(text);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    static void launchInstaller(File zip, File installDir, String javaExe) throws IOException {
        if (zip == null || !zip.isFile()) throw new IOException("已校验更新包不存在。");
        if (installDir == null || !installDir.isDirectory()) throw new IOException("安装目录无效。");
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
            throw new IOException("自动覆盖更新当前仅支持 Windows。");

        File helperDir = Files.createTempDirectory("LicenseRecover-installer-").toFile();
        File helperClass = new File(helperDir, "LicenseRecoverModernGUIUpdateInstaller.class");
        copyResource("/LicenseRecoverModernGUIUpdateInstaller.class", helperClass);
        File restartTarget = new File(installDir, "LicenseRecoverGUI.exe");
        File helperLog = new File(helperDir, "installer.log");
        ProcessBuilder pb = new ProcessBuilder(javaExe, "-Dfile.encoding=UTF-8", "-cp",
                helperDir.getAbsolutePath(), "LicenseRecoverModernGUIUpdateInstaller",
                zip.getAbsolutePath(), installDir.getAbsolutePath(), restartTarget.getAbsolutePath());
        pb.directory(installDir);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(helperLog));
        pb.start();
    }

    private static void copyResource(String name, File target) throws IOException {
        InputStream in = LicenseRecoverModernGUIGitHubUpdateService.class.getResourceAsStream(name);
        if (in == null) throw new IOException("更新器资源缺失: " + name);
        try (InputStream input = in; OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
    }

    private static String getText(String url) throws IOException {
        IOException javaFailure = null;
        for (NetworkRoute route : networkRoutes()) {
            HttpURLConnection c = null;
            try {
                c = openFollowingRedirects(url, -1L, route);
                try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            } catch (IOException ex) {
                javaFailure = ex;
            } finally {
                if (c != null) c.disconnect();
            }
        }

        File temp = Files.createTempFile("LicenseRecover-update-text-", ".tmp").toFile();
        try {
            if (nativeDownload(url, temp, -1L, NO_PROGRESS, s -> { })) {
                return new String(Files.readAllBytes(temp.toPath()), StandardCharsets.UTF_8);
            }
        } finally {
            temp.delete();
        }
        throw new IOException("GitHub 文本请求在 Java 与 Windows 网络栈均失败。最后 Java 错误: "
                + (javaFailure == null ? "未知" : safeNetworkMessage(javaFailure)), javaFailure);
    }

    private static void download(String url, File target, long expectedTotal,
                                 LicenseRecoverModernGUIUpdateProgress progress,
                                 Consumer<String> log) throws IOException {
        long downloaded = target.isFile() ? target.length() : 0L;
        if (expectedTotal > 0L && downloaded > expectedTotal) {
            if (!target.delete() && target.exists()) throw new IOException("无法重置异常的更新临时文件。");
            downloaded = 0L;
        }
        progress.bytes(downloaded, expectedTotal);
        IOException last = null;
        List<NetworkRoute> routes = networkRoutes();

        for (int i = 0; i < routes.size(); i++) {
            if (expectedTotal > 0L && downloaded == expectedTotal) return;
            NetworkRoute route = routes.get(i);
            HttpURLConnection c = null;
            try {
                progress.status("正在连接 GitHub 下载节点（" + route.label + "，线路 "
                        + (i + 1) + "/" + routes.size() + "）...");
                c = openFollowingRedirects(url, downloaded > 0L ? downloaded : -1L, route);
                int code = c.getResponseCode();
                boolean append = downloaded > 0L && code == HttpURLConnection.HTTP_PARTIAL;
                if (downloaded > 0L && !append) {
                    if (!target.delete() && target.exists()) throw new IOException("无法重置部分下载文件。");
                    downloaded = 0L;
                }

                long responseLength = c.getContentLengthLong();
                long total = expectedTotal;
                if (total <= 0L) {
                    total = parseContentRangeTotal(c.getHeaderField("Content-Range"));
                    if (total <= 0L && responseLength >= 0L) total = downloaded + responseLength;
                }
                progress.status("正在下载 " + target.getName() + "（" + route.label + "）...");
                progress.bytes(downloaded, total);

                try (InputStream in = new BufferedInputStream(c.getInputStream());
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(target, append))) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        if (n == 0) continue;
                        out.write(buffer, 0, n);
                        downloaded += n;
                        progress.bytes(downloaded, total);
                    }
                }

                if (total > 0L && downloaded != total)
                    throw new EOFException("下载连接提前结束：" + downloaded + " / " + total + " 字节。");
                return;
            } catch (IOException ex) {
                last = ex;
                log.accept("[更新] Java 下载线路 " + route.label + " 失败: "
                        + safeNetworkMessage(ex) + "\n");
            } finally {
                if (c != null) c.disconnect();
            }
        }

        progress.status("Java 下载线路不可用，正在切换 Windows 系统网络栈...");
        log.accept("[更新] Java 下载线路全部失败，切换 LicenseRecoverGUI.exe / WinINet。\n");
        if (nativeDownload(url, target, expectedTotal, progress, log)) return;

        String reason = last == null ? "未知网络错误" : safeNetworkMessage(last);
        throw new IOException("GitHub 更新下载失败：Java 多线路与 Windows WinINet 均未成功。最后 Java 错误: "
                + reason, last);
    }

    private static final class NetworkRoute {
        final String label;
        final Proxy proxy;
        final boolean system;
        NetworkRoute(String label, Proxy proxy, boolean system) {
            this.label = label;
            this.proxy = proxy;
            this.system = system;
        }
        HttpURLConnection open(URL url) throws IOException {
            return (HttpURLConnection) (system ? url.openConnection() : url.openConnection(proxy));
        }
    }

    private static List<NetworkRoute> networkRoutes() {
        List<NetworkRoute> routes = new ArrayList<NetworkRoute>();
        routes.add(new NetworkRoute("Windows/Java 系统代理", null, true));
        Proxy env = environmentProxy();
        if (env != null) routes.add(new NetworkRoute("HTTPS_PROXY 环境代理", env, false));
        routes.add(new NetworkRoute("直接连接", Proxy.NO_PROXY, false));
        return routes;
    }

    static Proxy parseProxy(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            String raw = value.trim();
            if (!raw.contains("://")) raw = "http://" + raw;
            URI uri = new URI(raw);
            if (uri.getUserInfo() != null || uri.getHost() == null) return null;
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            int port = uri.getPort();
            if (port <= 0) port = "https".equals(scheme) ? 443 : 80;
            return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), port));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Proxy environmentProxy() {
        String[] names = {"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"};
        for (String name : names) {
            Proxy proxy = parseProxy(System.getenv(name));
            if (proxy != null) return proxy;
        }
        return null;
    }

    static boolean isRedirectCode(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307 || code == 308;
    }

    private static HttpURLConnection openFollowingRedirects(String url, long rangeStart,
                                                             NetworkRoute route) throws IOException {
        URL current = new URL(url);
        if (!"https".equalsIgnoreCase(current.getProtocol()))
            throw new IOException("拒绝非 HTTPS 更新地址。");
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpURLConnection c = route.open(current);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "LicenseRecover-Updater");
            c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream;q=0.9, */*;q=0.8");
            c.setRequestProperty("Accept-Encoding", "identity");
            c.setRequestProperty("Connection", "close");
            if (rangeStart >= 0L) c.setRequestProperty("Range", "bytes=" + rangeStart + "-");
            int code = c.getResponseCode();
            if (isRedirectCode(code)) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null || location.trim().isEmpty())
                    throw new IOException("GitHub 重定向缺少 Location。");
                URL next = new URL(current, location);
                if (!"https".equalsIgnoreCase(next.getProtocol()))
                    throw new IOException("更新下载被重定向到非 HTTPS 地址，已拒绝。");
                current = next;
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new IOException("GitHub 请求失败，HTTP " + code + "。");
            }
            if (!"https".equalsIgnoreCase(c.getURL().getProtocol())) {
                c.disconnect();
                throw new IOException("更新下载最终地址不是 HTTPS，已拒绝。");
            }
            return c;
        }
        throw new IOException("GitHub 重定向次数超过 " + MAX_REDIRECTS + " 次。");
    }

    private static HttpURLConnection connection(String url) throws IOException {
        return connection(url, -1L);
    }

    private static HttpURLConnection connection(String url, long rangeStart) throws IOException {
        IOException last = null;
        for (NetworkRoute route : networkRoutes()) {
            try { return openFollowingRedirects(url, rangeStart, route); }
            catch (IOException ex) { last = ex; }
        }
        throw new IOException("GitHub Java 网络线路均不可用: "
                + (last == null ? "未知" : safeNetworkMessage(last)), last);
    }

    private static boolean nativeDownload(String url, File target, long expectedTotal,
                                          LicenseRecoverModernGUIUpdateProgress progress,
                                          Consumer<String> log) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        File launcher = new File(runtimeDir(), "LicenseRecoverGUI.exe");
        if (!launcher.isFile()) return false;
        if (target.exists() && !target.delete()) return false;
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(launcher.getAbsolutePath(), "--native-download",
                    url, target.getAbsolutePath());
            pb.directory(runtimeDir());
            pb.redirectErrorStream(true);
            process = pb.start();
            final Process p = process;
            Thread drainer = new Thread(() -> {
                try (InputStream in = p.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    while (in.read(buffer) >= 0) { }
                } catch (IOException ignore) { }
            }, "LicenseRecover-NativeDownload-Drain");
            drainer.setDaemon(true);
            drainer.start();

            long started = System.currentTimeMillis();
            for (;;) {
                try {
                    int rc = process.exitValue();
                    long size = target.isFile() ? target.length() : 0L;
                    progress.bytes(size, expectedTotal);
                    if (rc == 0 && target.isFile() && size > 0L) {
                        log.accept("[更新] Windows WinINet 下载成功。\n");
                        return true;
                    }
                    if (target.exists()) target.delete();
                    log.accept("[更新] Windows WinINet 下载失败，退出码 " + rc + "。\n");
                    return false;
                } catch (IllegalThreadStateException stillRunning) {
                    long size = target.isFile() ? target.length() : 0L;
                    progress.status("正在使用 Windows 系统网络栈下载 " + target.getName() + "...");
                    progress.bytes(size, expectedTotal);
                    if (System.currentTimeMillis() - started > NATIVE_DOWNLOAD_TIMEOUT_MS) {
                        process.destroy();
                        if (target.exists()) target.delete();
                        log.accept("[更新] Windows WinINet 下载超时。\n");
                        return false;
                    }
                    try { Thread.sleep(250L); }
                    catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        process.destroy();
                        return false;
                    }
                }
            }
        } catch (Exception ex) {
            if (target.exists()) target.delete();
            log.accept("[更新] 无法启动 Windows WinINet 兜底下载: " + safeNetworkMessage(ex) + "\n");
            return false;
        }
    }

    private static File runtimeDir() {
        try {
            File location = new File(LicenseRecoverModernGUIGitHubUpdateService.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.isFile() ? location.getParentFile() : location;
        } catch (Exception ignore) {
            return new File(".").getAbsoluteFile();
        }
    }

    private static String safeNetworkMessage(Throwable ex) {
        String value = ex == null ? "未知错误" : ex.getMessage();
        if (value == null || value.trim().isEmpty()) value = ex == null ? "未知错误" : ex.toString();
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }

    static long parseAssetSize(String json, String fileName) {
        if (json == null || fileName == null || fileName.trim().isEmpty()) return -1L;
        int namePos = json.indexOf("\"" + fileName + "\"");
        if (namePos < 0) return -1L;
        int sizeKey = json.indexOf("\"size\"", namePos);
        if (sizeKey < 0 || sizeKey - namePos > 8192) return -1L;
        int colon = json.indexOf(':', sizeKey);
        if (colon < 0) return -1L;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int begin = i;
        while (i < json.length() && Character.isDigit(json.charAt(i))) i++;
        if (begin == i) return -1L;
        try { return Long.parseLong(json.substring(begin, i)); }
        catch (NumberFormatException ex) { return -1L; }
    }

    static long parseContentRangeTotal(String value) {
        if (value == null) return -1L;
        Matcher m = Pattern.compile("/(\\d+)\\s*$").matcher(value.trim());
        if (!m.find()) return -1L;
        try { return Long.parseLong(m.group(1)); }
        catch (NumberFormatException ex) { return -1L; }
    }

    private static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json == null ? "" : json);
        return m.find() ? m.group(1).replace("\\/", "/") : null;
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception ex) {
            if (ex instanceof IOException) throw (IOException) ex;
            throw new IOException("无法计算 SHA-256。", ex);
        }
    }
}

/** Separate helper copied to a temp classpath before replacing the running overlay JAR. */
final class LicenseRecoverModernGUIUpdateInstaller {
    private static final int COPY_RETRIES = 30;
    private LicenseRecoverModernGUIUpdateInstaller() { }

    public static void main(String[] args) {
        if (args.length < 3) System.exit(2);
        File zip = new File(args[0]);
        File installDir = new File(args[1]);
        File restartTarget = new File(args[2]);
        File result = new File(installDir, "update-result.txt");
        try {
            Thread.sleep(1500L);
            applyUpdate(zip, installDir);
            if (!restartTarget.isFile())
                throw new IOException("更新完成但缺少 LicenseRecoverGUI.exe。");
            write(result, "UPDATE_OK " + new Date() + System.lineSeparator());
            zip.delete();
            new ProcessBuilder(restartTarget.getAbsolutePath()).directory(installDir).start();
        } catch (Throwable ex) {
            try { write(result, "UPDATE_FAILED " + new Date() + System.lineSeparator()
                    + ex + System.lineSeparator()); } catch (Exception ignore) { }
            ex.printStackTrace();
            System.exit(1);
        }
    }

    static void applyUpdate(File zip, File installDir) throws IOException {
        if (zip == null || !zip.isFile()) throw new IOException("更新 ZIP 不存在。");
        if (installDir == null || !installDir.isDirectory()) throw new IOException("安装目录不存在。");
        File work = Files.createTempDirectory("LicenseRecover-apply-").toFile();
        File staging = new File(work, "staging");
        File backup = new File(work, "backup");
        if (!staging.mkdirs() || !backup.mkdirs()) throw new IOException("无法创建更新临时目录。");
        try {
            unzipSafe(zip, staging);
            if (!new File(staging, "VERSION.txt").isFile())
                throw new IOException("更新包缺少 VERSION.txt。");
            backupExisting(staging, installDir, backup, "");
            try { copyTree(staging, installDir); }
            catch (IOException failed) {
                try { copyTree(backup, installDir); }
                catch (IOException restore) { failed.addSuppressed(restore); }
                throw failed;
            }
            cleanupObsoleteEntrypoints(installDir);
        } finally { deleteTree(work); }
    }

    private static void unzipSafe(File zip, File staging) throws IOException {
        String root = staging.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zin.getNextEntry()) != null) {
                File out = new File(staging, entry.getName());
                if (!out.getCanonicalPath().startsWith(root))
                    throw new IOException("ZIP 包含不安全路径: " + entry.getName());
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) throw new IOException("无法创建目录: " + out);
                } else {
                    File parent = out.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建目录: " + parent);
                    try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
                        int n;
                        while ((n = zin.read(buffer)) >= 0) stream.write(buffer, 0, n);
                    }
                }
                zin.closeEntry();
            }
        }
    }

    private static void backupExisting(File sourceRoot, File installRoot, File backupRoot,
                                       String relative) throws IOException {
        File source = relative.isEmpty() ? sourceRoot : new File(sourceRoot, relative);
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            String rel = relative.isEmpty() ? child.getName() : relative + File.separator + child.getName();
            if (child.isDirectory()) backupExisting(sourceRoot, installRoot, backupRoot, rel);
            else {
                File current = new File(installRoot, rel);
                if (current.isFile()) {
                    File target = new File(backupRoot, rel);
                    File parent = target.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建备份目录。");
                    Files.copy(current.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void copyTree(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建目录: " + target);
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(target, child.getName()));
            return;
        }
        IOException last = null;
        for (int i = 0; i < COPY_RETRIES; i++) {
            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return;
            } catch (IOException ex) {
                last = ex;
                try { Thread.sleep(500L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("更新被中断。", interrupted);
                }
            }
        }
        throw new IOException("多次重试后仍无法替换文件: " + target, last);
    }

    private static void cleanupObsoleteEntrypoints(File dir) {
        String[] names = {
                "LicenseRecoverGUI-legacy.exe",
                "run.bat",
                "run_gui.bat",
                "run_gui_modern.bat",
                "run_gui_legacy.bat",
                "run_removenet.bat",
                "run_removenet_safe.bat",
                "run_removenet_legacy.bat"
        };
        for (String name : names) {
            File file = new File(dir, name);
            if (!file.exists()) continue;
            for (int i = 0; i < 8 && file.exists(); i++) {
                if (file.delete()) break;
                try { Thread.sleep(300L); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private static void write(File file, String text) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), "UTF-8")) {
            writer.write(text);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
