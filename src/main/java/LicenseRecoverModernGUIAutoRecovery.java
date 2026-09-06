import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-click recovery coordinator used by the modern GUI overlay.
 *
 * The normal path never patches vendor DLLs. It detects the target, obtains the
 * machine identity using the target generation's own algorithm, creates the
 * corresponding local authorization material, writes it with a verified
 * backup, then decrypts the persisted regName again before reporting success.
 */
public final class LicenseRecoverModernGUIAutoRecovery {
    private static final String USER_ID = "fwq";
    private static final String BLOCK_ENDPOINT = "http://127.0.0.1:9/Service.asmx";
    private static final Pattern XML_ELEMENT = Pattern.compile("(?is)<%s\\b[^>]*>.*?</%s\\s*>");
    private static final Pattern SOFT_VERSION = Pattern.compile("(?is)<SoftVersionID\\b[^>]*>\\s*([^<]+?)\\s*</SoftVersionID\\s*>");
    private static final Pattern WEB_SOFT_NO = Pattern.compile("(?is)<WebSerSoftNo\\b[^>]*>\\s*([^<]*?)\\s*</WebSerSoftNo\\s*>");
    private static final Pattern REG_BLOCK = Pattern.compile("(?is)<reg\\b[^>]*>.*?</reg\\s*>");
    private static final Pattern ROOT_CLOSE = Pattern.compile("(?is)</ROOT\\s*>");
    private static final Random RANDOM = new Random();

    private LicenseRecoverModernGUIAutoRecovery() { }

    public enum Kind { JAVA, DOTNET_MODERN, DOTNET_LEGACY, UNKNOWN }

    public static final class Detection {
        public final Kind kind;
        public final File selected;
        public final File appRoot;
        public final File runtimeDir;
        public final String versionId;
        public final String productName;

        Detection(Kind kind, File selected, File appRoot, File runtimeDir,
                  String versionId, String productName) {
            this.kind = kind;
            this.selected = selected;
            this.appRoot = appRoot;
            this.runtimeDir = runtimeDir;
            this.versionId = versionId;
            this.productName = productName;
        }

        public boolean isDetected() { return kind != Kind.UNKNOWN; }

        public String summary() {
            if (!isDetected()) return "未识别 ITMC 应用";
            String p = productName == null ? "产品号待自动解析" : productName;
            String v = versionId == null ? "版本未知" : versionId;
            return kind + " / " + v + " / " + p;
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final Detection detection;
        public final String machineId;
        public final String requestCode;
        public final String authorizationCode;

        Result(boolean success, String message, Detection detection, String machineId,
               String requestCode, String authorizationCode) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.detection = detection;
            this.machineId = machineId;
            this.requestCode = requestCode;
            this.authorizationCode = authorizationCode;
        }

        static Result fail(String message, Detection d) {
            return new Result(false, message, d, null, null, null);
        }
    }

    public static Detection detect(File selected) {
        if (selected == null) return new Detection(Kind.UNKNOWN, null, null, null, null, null);
        File s = selected.getAbsoluteFile();
        if (!s.isDirectory()) return new Detection(Kind.UNKNOWN, s, null, null, null, null);

        File dotnet = findDotNetBin(s);
        if (dotnet != null) {
            File root = "bin".equalsIgnoreCase(dotnet.getName()) && dotnet.getParentFile() != null
                    ? dotnet.getParentFile() : dotnet;
            String version = readVersion(root, dotnet);
            String product = detectDotNetProduct(version, root, dotnet);
            Kind kind = new File(dotnet, "ITMC.Regedit.dll").isFile()
                    ? Kind.DOTNET_MODERN : Kind.DOTNET_LEGACY;
            return new Detection(kind, s, root, dotnet, version, product);
        }

        File lib = findJavaLib(s);
        if (lib != null) {
            File webInf = lib.getParentFile();
            File root = webInf != null ? webInf.getParentFile() : s;
            if (root != null && "WEB-INF".equalsIgnoreCase(root.getName()) && root.getParentFile() != null)
                root = root.getParentFile();
            String version = readJavaVersion(root);
            return new Detection(Kind.JAVA, s, root, lib, version, null);
        }
        return new Detection(Kind.UNKNOWN, s, null, null, null, null);
    }

    public static Result recover(File selected, boolean backup, boolean blockNet,
                                 boolean dryRun, Consumer<String> log) {
        Consumer<String> sink = log == null ? s -> { } : log;
        Detection d = detect(selected);
        sink.accept("[一键恢复] 检测: " + d.summary() + "\n");
        if (!d.isDetected()) return Result.fail("未识别到 ITMC Java / .NET 授权结构。", d);
        try {
            if (d.kind == Kind.JAVA)
                return recoverJava(d, backup, blockNet, dryRun, sink);
            if (d.kind == Kind.DOTNET_MODERN)
                return recoverModernDotNet(d, backup, blockNet, dryRun, sink);
            return Result.fail("当前识别为旧版 .NET 协议；自动写回适配尚未完成，请暂用高级工具。", d);
        } catch (Throwable ex) {
            sink.accept("[错误] " + safe(ex) + "\n");
            return Result.fail(safe(ex), d);
        }
    }

    private static Result recoverJava(Detection d, boolean backup, boolean blockNet,
                                      boolean dryRun, Consumer<String> log) throws Exception {
        File cli = new File(toolDir(), "LicenseRecover.jar");
        if (!cli.isFile()) return Result.fail("未找到 LicenseRecover.jar。", d);
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(d.runtimeDir.getAbsolutePath() + File.separator + "*" + File.pathSeparator + cli.getAbsolutePath());
        cmd.add("LicenseRecover");
        cmd.add(d.appRoot.getAbsolutePath());
        if (dryRun) cmd.add("--dry-run");
        if (!backup) cmd.add("--no-backup");
        if (!blockNet) cmd.add("--no-block-net");
        int rc = run(cmd, log);
        return new Result(rc == 0, rc == 0 ? "Java 本地授权已恢复并完成原生自校验。" : "Java 恢复失败，详见日志。",
                d, null, null, null);
    }

    private static Result recoverModernDotNet(Detection d, boolean backup, boolean blockNet,
                                               boolean dryRun, Consumer<String> log) throws Exception {
        if (!isWindows()) return Result.fail(".NET 机器标识自动获取当前只在 Windows 上执行。", d);
        if (d.productName == null || d.productName.trim().isEmpty())
            return Result.fail("无法从目标程序集安全确定 ProName�D@�	1L�8х9�8