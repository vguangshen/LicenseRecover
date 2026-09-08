#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / 'src/main/java/LicenseRecoverModernGUILauncher.java'
text = java.read_text(encoding='utf-8')

text = text.replace('import java.net.HttpURLConnection;\nimport java.net.URL;\n',
                    'import java.net.HttpURLConnection;\nimport java.net.InetSocketAddress;\nimport java.net.Proxy;\nimport java.net.URI;\nimport java.net.URL;\n', 1)
text = text.replace('    private static final int CONNECT_TIMEOUT_MS = 30000;\n    private static final int READ_TIMEOUT_MS = 60000;\n    private static final int DOWNLOAD_RETRIES = 4;\n    private static final long RETRY_BACKOFF_MS = 1500L;\n',
                    '    private static final int CONNECT_TIMEOUT_MS = 12000;\n    private static final int READ_TIMEOUT_MS = 60000;\n    private static final int MAX_REDIRECTS = 8;\n    private static final long NATIVE_DOWNLOAD_TIMEOUT_MS = 180000L;\n', 1)

start = text.index('    private static String getText(String url) throws IOException {')
end = text.index('    static long parseAssetSize(String json, String fileName) {', start)
new_block = r'''    private static String getText(String url) throws IOException {
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

'''
text = text[:start] + new_block + text[end:]
java.write_text(text, encoding='utf-8')

native = root / 'src/native/LicenseRecoverGUI.c'
c = native.read_text(encoding='utf-8')
c = c.replace('#include <shellapi.h>\n', '#include <shellapi.h>\n#include <wininet.h>\n', 1)
insert_anchor = 'int APIENTRY wWinMain(HINSTANCE instance, HINSTANCE previous, LPWSTR commandLine, int showCommand) {\n'
native_func = r'''static int native_download(const wchar_t *url, const wchar_t *target) {
    if (!url || !target || _wcsnicmp(url, L"https://", 8) != 0) return 21;

    HINTERNET internet = InternetOpenW(L"LicenseRecover-Updater",
            INTERNET_OPEN_TYPE_PRECONFIG, NULL, NULL, 0);
    if (!internet) return 22;

    DWORD connectTimeout = 12000, receiveTimeout = 60000, sendTimeout = 30000;
    InternetSetOptionW(internet, INTERNET_OPTION_CONNECT_TIMEOUT, &connectTimeout, sizeof(connectTimeout));
    InternetSetOptionW(internet, INTERNET_OPTION_RECEIVE_TIMEOUT, &receiveTimeout, sizeof(receiveTimeout));
    InternetSetOptionW(internet, INTERNET_OPTION_SEND_TIMEOUT, &sendTimeout, sizeof(sendTimeout));

    const wchar_t *headers = L"Accept: application/octet-stream, */*;q=0.8\r\nAccept-Encoding: identity\r\n";
    HINTERNET request = InternetOpenUrlW(internet, url, headers, (DWORD)-1,
            INTERNET_FLAG_RELOAD | INTERNET_FLAG_NO_CACHE_WRITE | INTERNET_FLAG_SECURE | INTERNET_FLAG_NO_UI, 0);
    if (!request) {
        InternetCloseHandle(internet);
        return 23;
    }

    DWORD status = 0, statusLen = sizeof(status);
    if (!HttpQueryInfoW(request, HTTP_QUERY_STATUS_CODE | HTTP_QUERY_FLAG_NUMBER,
                        &status, &statusLen, NULL) || status < 200 || status >= 300) {
        InternetCloseHandle(request);
        InternetCloseHandle(internet);
        return 24;
    }

    HANDLE file = CreateFileW(target, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                              FILE_ATTRIBUTE_TEMPORARY | FILE_ATTRIBUTE_NOT_CONTENT_INDEXED, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        InternetCloseHandle(request);
        InternetCloseHandle(internet);
        return 25;
    }

    BYTE buffer[65536];
    DWORD got = 0;
    int rc = 0;
    for (;;) {
        if (!InternetReadFile(request, buffer, sizeof(buffer), &got)) { rc = 26; break; }
        if (got == 0) break;
        DWORD written = 0;
        if (!WriteFile(file, buffer, got, &written, NULL) || written != got) { rc = 27; break; }
    }
    FlushFileBuffers(file);
    CloseHandle(file);
    InternetCloseHandle(request);
    InternetCloseHandle(internet);
    if (rc != 0) DeleteFileW(target);
    return rc;
}

'''
if insert_anchor not in c:
    raise SystemExit('native wWinMain anchor not found')
c = c.replace(insert_anchor, native_func + insert_anchor, 1)
old = '''    (void)instance; (void)previous; (void)commandLine; (void)showCommand;\n\n    wchar_t dir[32768], javaExe[32768], overlay[32768], guiJar[32768], classpath[65536];\n'''
new = '''    (void)instance; (void)previous; (void)commandLine; (void)showCommand;\n\n    int earlyArgc = 0;\n    LPWSTR *earlyArgv = CommandLineToArgvW(GetCommandLineW(), &earlyArgc);\n    if (earlyArgv && earlyArgc == 4 && wcscmp(earlyArgv[1], L"--native-download") == 0) {\n        int rc = native_download(earlyArgv[2], earlyArgv[3]);\n        LocalFree(earlyArgv);\n        return rc;\n    }\n    if (earlyArgv) LocalFree(earlyArgv);\n\n    wchar_t dir[32768], javaExe[32768], overlay[32768], guiJar[32768], classpath[65536];\n'''
if old not in c:
    raise SystemExit('native early-arg anchor not found')
c = c.replace(old, new, 1)
c = c.replace('        !append_arg(cmd, cmdCap, &len, L"-Djava.net.useSystemProxies=true") ||\n',
              '        !append_arg(cmd, cmdCap, &len, L"-Djava.net.useSystemProxies=true") ||\n        !append_arg(cmd, cmdCap, &len, L"-Djava.net.preferIPv4Stack=true") ||\n', 1)
native.write_text(c, encoding='utf-8')

package = root / 'scripts/package-native-launcher.ps1'
p = package.read_text(encoding='utf-8')
p = p.replace("        '-o', $launcher, '-lshell32', '-luser32'\n",
              "        '-o', $launcher, '-lshell32', '-luser32', '-lwininet'\n", 1)
p = p.replace("    'jre\\bin\\javaw.exe'\n", "    'jre\\bin\\javaw.exe',\n    '--native-download',\n    '-Djava.net.preferIPv4Stack=true'\n", 1)
package.write_text(p, encoding='utf-8')

smoke = root / 'src/test/java/RefactorSmokeTest.java'
s = smoke.read_text(encoding='utf-8')
anchor = '''        check(LicenseRecoverModernGUIGitHubUpdateService.parseContentRangeTotal(\n                        "bytes 1024-2047/4416823") == 4416823L,\n                "GitHub updater parses resumable download total");\n'''
extra = '''        check(LicenseRecoverModernGUIGitHubUpdateService.parseContentRangeTotal(\n                        "bytes 1024-2047/4416823") == 4416823L,\n                "GitHub updater parses resumable download total");\n        check(LicenseRecoverModernGUIGitHubUpdateService.parseProxy("http://127.0.0.1:7890") != null,\n                "GitHub updater accepts explicit HTTPS_PROXY-style HTTP proxy");\n        check(LicenseRecoverModernGUIGitHubUpdateService.parseProxy("http://user:pass@127.0.0.1:7890") == null,\n                "GitHub updater does not silently mishandle authenticated proxy URLs");\n        check(LicenseRecoverModernGUIGitHubUpdateService.isRedirectCode(302)\n                        && LicenseRecoverModernGUIGitHubUpdateService.isRedirectCode(307)\n                        && !LicenseRecoverModernGUIGitHubUpdateService.isRedirectCode(200),\n                "GitHub updater recognizes manual HTTPS redirect statuses");\n'''
if anchor not in s:
    raise SystemExit('smoke updater anchor not found')
s = s.replace(anchor, extra, 1)
smoke.write_text(s, encoding='utf-8')

print('patched robust GitHub updater network chain')
