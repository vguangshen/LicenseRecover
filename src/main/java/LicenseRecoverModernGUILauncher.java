import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Default GUI launcher with a GitHub Releases based self-update check.
 * All updater classes intentionally share the LicenseRecoverModernGUI prefix so
 * the existing deterministic overlay build includes them without changing the
 * legacy runtime JARs.
 */
public final class LicenseRecoverModernGUILauncher {
    private LicenseRecoverModernGUILauncher() {}

    public static void main(String[] args) {
        final boolean updateOnly = contains(args, "--update-only");
        if (!updateOnly) LicenseRecoverModernGUI.main(args);

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
        File toolDir = toolDir();
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
                            + "会优先使用轻量更新包；下载后校验 SHA-256，校验通过才会覆盖当前文件。",
                    "发现新版本");
            if (choice != JOptionPane.OK_OPTION) return;

            showMessage("开始从 GitHub 下载 v" + info.latestVersion
                    + "。下载完成并校验后会再次提示。", "软件更新",
                    JOptionPane.INFORMATION_MESSAGE);
            File zip = LicenseRecoverModernGUIGitHubUpdateService.downloadVerified(
                    info, s -> System.out.print(s));

            int install = showConfirm(
                    "v" + info.latestVersion + " 已下载并通过 SHA-256 校验。\n\n"
                            + "点击“确定”后软件将退出，自动覆盖更新并重新启动。",
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
}

final class LicenseRecoverModernGUIUpdateInfo {
    final String currentVersion;
    final String latestVersion;
    final String tagName;
    final String releasePageUrl;
    final String zipUrl;
    final String updateZipUrl;
    final String checksumUrl;

    LicenseRecoverModernGUIUpdateInfo(String currentVersion, String latestVersion, String tagName,
                                      String releasePageUrl, String zipUrl, String updateZipUrl,
                                      String checksumUrl) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.tagName = tagName;
        this.releasePageUrl = releasePageUrl;
        this.zipUrl = zipUrl;
        this.updateZipUrl = updateZipUrl;
        this.checksumUrl = checksumUrl;
    }

    boolean isUpdateAvailable() {
        return LicenseRecoverModernGUIGitHubUpdateService.compareVersions(
                latestVersion, currentVersion) > 0;
    }
}

final class LicenseRecoverModernGUIGitHubUpdateService {
    static final String REPOSITORY = "vguangshen/LicenseRecover";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 30000;

    private LicenseRecoverModernGUIGitHubUpdateService() {}

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
        return new LicenseRecoverModernGUIUpdateInfo(current, latest, tag, page,
                base + "LicenseRecover-latest.zip",
                base + "LicenseRecover-update.zip",
                base + "SHA256SUMS.txt");
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
        Consumer<String> sink = log == null ? s -> { } : log;
        sink.accept("[更新] 下载 SHA256SUMS.txt...\n");
        String sums = getText(info.checksumUrl);

        String fileName = "LicenseRecover-update.zip";
        String downloadUrl = info.updateZipUrl;
        String expected = parseChecksum(sums, fileName);
        if (expected == null) {
            fileName = "LicenseRecover-latest.zip";
            downloadUrl = info.zipUrl;
            expected = parseChecksum(sums, fileName);
        }
        if (expected == null) throw new IOException("校验文件中未找到可用的 LicenseRecover 更新包。 ");

        File dir = Files.createTempDirectory("LicenseRecover-update-").toFile();
        File zip = new File(dir, fileName);
        sink.accept("[更新] 下载 " + fileName + "...\n");
        download(downloadUrl, zip);
        String actual = sha256(zip);
        sink.accept("[更新] SHA-256: " + actual + "\n");
        if (!expected.equalsIgnoreCase(actual)) {
            zip.delete();
            throw new SecurityException("下载包 SHA-256 与 GitHub 校验文件不一致。 ");
        }
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
        if (zip == null || !zip.isFile()) throw new IOException("已校验更新包不存在。 ");
        if (installDir == null || !installDir.isDirectory()) throw new IOException("安装目录无效。 ");
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
            throw new IOException("自动覆盖更新当前仅支持 Windows。 ");

        File helperDir = Files.createTempDirectory("LicenseRecover-installer-").toFile();
        File helperClass = new File(helperDir, "LicenseRecoverModernGUIUpdateInstaller.class");
        copyResource("/LicenseRecoverModernGUIUpdateInstaller.class", helperClass);
        File launcher = new File(installDir, "run_gui.bat");
        File helperLog = new File(helperDir, "installer.log");
        ProcessBuilder pb = new ProcessBuilder(javaExe, "-Dfile.encoding=UTF-8", "-cp",
                helperDir.getAbsolutePath(), "LicenseRecoverModernGUIUpdateInstaller",
                zip.getAbsolutePath(), installDir.getAbsolutePath(), launcher.getAbsolutePath());
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
        HttpURLConnection c = connection(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally { c.disconnect(); }
    }

    private static void download(String url, File target) throws IOException {
        HttpURLConnection c = connection(url);
        try (InputStream in = c.getInputStream();
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection connection(String url) throws IOException {
        URL u = new URL(url);
        if (!"https".equalsIgnoreCase(u.getProtocol())) throw new IOException("拒绝非 HTTPS 更新地址。 ");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "LicenseRecover-Updater");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream;q=0.9, */*;q=0.8");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IOException("GitHub 请求失败，HTTP " + code + "。仓库需为公开状态。 ");
        }
        return c;
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
    private LicenseRecoverModernGUIUpdateInstaller() {}

    public static void main(String[] args) {
        if (args.length < 3) System.exit(2);
        File zip = new File(args[0]);
        File installDir = new File(args[1]);
        File launcher = new File(args[2]);
        File result = new File(installDir, "update-result.txt");
        try {
            Thread.sleep(1500L);
            applyUpdate(zip, installDir);
            write(result, "UPDATE_OK " + new Date() + System.lineSeparator());
            zip.delete();
            if (launcher.isFile()) new ProcessBuilder("cmd.exe", "/c", "start", "",
                    launcher.getAbsolutePath()).directory(installDir).start();
        } catch (Throwable ex) {
            try { write(result, "UPDATE_FAILED " + new Date() + System.lineSeparator()
                    + ex + System.lineSeparator()); } catch (Exception ignore) { }
            ex.printStackTrace();
            System.exit(1);
        }
    }

    static void applyUpdate(File zip, File installDir) throws IOException {
        if (zip == null || !zip.isFile()) throw new IOException("更新 ZIP 不存在。 ");
        if (installDir == null || !installDir.isDirectory()) throw new IOException("安装目录不存在。 ");
        File work = Files.createTempDirectory("LicenseRecover-apply-").toFile();
        File staging = new File(work, "staging");
        File backup = new File(work, "backup");
        if (!staging.mkdirs() || !backup.mkdirs()) throw new IOException("无法创建更新临时目录。 ");
        try {
            unzipSafe(zip, staging);
            if (!new File(staging, "VERSION.txt").isFile())
                throw new IOException("更新包缺少 VERSION.txt。 ");
            backupExisting(staging, installDir, backup, "");
            try { copyTree(staging, installDir); }
            catch (IOException failed) {
                try { copyTree(backup, installDir); }
                catch (IOException restore) { failed.addSuppressed(restore); }
                throw failed;
            }
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
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建备份目录。 ");
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
