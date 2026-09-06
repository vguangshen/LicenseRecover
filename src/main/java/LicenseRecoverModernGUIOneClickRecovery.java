import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一键恢复编排器。
 *
 * UI 只暴露“选择目录 -> 一键恢复”，底层仍保留 Java / .NET 不同适配路径：
 * - Java：复用现有 LicenseRecover CLI 的本地 regName 写入与 checkReInfo 自校验；
 * - .NET YX0302：先调用目标程序自身授权组件生成本机申请号/授权码并取得真实机器标识，
 *   再由本工具按 ITMC.Regedit.dll 原生 DES/regName 格式生成 UserID=fwq 的本地授权，最后 verify；
 * - DS01xx 等旧协议暂时保留为高级/手工回退，避免误用新版协议。
 */
final class LicenseRecoverModernGUIOneClickRecovery {
    private static final long PROCESS_TIMEOUT_SECONDS = 600L;
    private static final Pattern REQUEST_PATTERN = Pattern.compile(
            "(?m)^\\s*注册申请号\\s*[:：]\\s*([0-9A-Fa-f]{16,})\\s*$");
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(?m)^\\s*离线授权码\\s*[:：]\\s*([0-9A-Fa-f]{16,})\\s*$");

    private LicenseRecoverModernGUIOneClickRecovery() { }

    static OperationResult recover(File selectedPath, boolean dryRun, boolean backup,
                                   Consumer<String> log) {
        Consumer<String> sink = log == null ? s -> { } : log;
        AppInfo info = AppDetector.detect(selectedPath);
        if (!info.isDetected()) {
            return OperationResult.failed("未识别到受支持的 ITMC 应用目录", 2);
        }

        sink.accept("[一键恢复] 已识别应用类型: " + info.type + "\n");
        if (info.softVersionId != null) {
            sink.accept("[一键恢复] SoftVersionID: " + info.softVersionId + "\n");
        }
        sink.accept("[一键恢复] 应用根目录: " + info.appRoot + "\n");

        if (info.type == AppInfo.Type.JAVA) {
            return recoverJava(info, dryRun, backup, sink);
        }
        if (info.type == AppInfo.Type.DOTNET) {
            return recoverDotNet(info, dryRun, backup, sink);
        }
        return OperationResult.failed("暂不支持该应用类型", 2);
    }

    private static OperationResult recoverJava(AppInfo info, boolean dryRun, boolean backup,
                                               Consumer<String> log) {
        if (info.appRoot == null || info.libDir == null) {
            return OperationResult.failed("Java 应用结构不完整", 2);
        }
        File toolDir = toolDir();
        File cliJar = new File(toolDir, "LicenseRecover.jar");
        if (!cliJar.isFile()) {
            return OperationResult.failed("缺少 LicenseRecover.jar", 2);
        }

        String javaExe = new File(System.getProperty("java.home"),
                "bin" + File.separator + "java" + (isWindows() ? ".exe" : "")).getAbsolutePath();
        String cp = info.libDir.getAbsolutePath() + File.separator + "*"
                + File.pathSeparator + cliJar.getAbsolutePath();
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add("LicenseRecover");
        cmd.add(info.appRoot.getAbsolutePath());
        if (dryRun) cmd.add("--dry-run");
        if (!backup) cmd.add("--no-backup");

        log.accept("[一键恢复/Java] 自动生成本机 regName -> 写入 -> checkReInfo 自校验。\n");
        OperationResult result = ProcessRunner.run(cmd, log, PROCESS_TIMEOUT_SECONDS);
        if (result.isSuccess()) {
            return dryRun
                    ? OperationResult.preview("Java 本地授权预览完成")
                    : OperationResult.success("Java 本地授权已恢复并完成自校验");
        }
        return result;
    }

    private static OperationResult recoverDotNet(AppInfo info, boolean dryRun, boolean backup,
                                                 Consumer<String> log) {
        if (!isWindows()) {
            return OperationResult.failed(".NET 一键恢复需要在目标 Windows 服务器上运行", 2);
        }
        if (info.binDir == null || !info.binDir.isDirectory()) {
            return OperationResult.failed(".NET bin 目录无效", 2);
        }

        String soft = info.softVersionId == null ? "" : info.softVersionId.trim().toUpperCase(Locale.ROOT);
        if (soft.startsWith("DS01")) {
            log.accept("[一键恢复/.NET] 检测到 DS01xx 旧协议；为避免协议误判，暂时切换到高级/手工工具。\n");
            return OperationResult.failed("DS01xx 自动适配器尚未完成，请暂用高级工具", 20);
        }

        File helper = new File(new File(toolDir(), "LicenseRecover.NET"), "LicenseRecover.NET.exe");
        if (!helper.isFile()) {
            return OperationResult.failed("缺少 LicenseRecover.NET.exe", 2);
        }

        String product = dotNetProduct(info.softVersionId);
        log.accept("[一键恢复/.NET] 调用目标授权组件获取本机身份并生成申请号/授权码。\n");
        log.accept("[一键恢复/.NET] 本地授权产品号: " + product + "\n");

        StringBuilder generated = new StringBuilder();
        Consumer<String> capture = s -> {
            generated.append(s);
            log.accept(s);
        };
        List<String> gen = new ArrayList<String>();
        gen.add(helper.getAbsolutePath());
        gen.add("gencode");
        gen.add(info.binDir.getAbsolutePath());
        if (product != null) {
            gen.add("--product");
            gen.add(product);
        }
        OperationResult genResult = ProcessRunner.run(gen, capture, PROCESS_TIMEOUT_SECONDS);
        if (!genResult.isSuccess()) return genResult;

        String seq = firstMatch(REQUEST_PATTERN, generated.toString());
        String code = firstMatch(CODE_PATTERN, generated.toString());
        if (seq == null || code == null) {
            log.accept("[错误] C# 助手已运行，但没有解析到申请号/授权码。\n");
            return OperationResult.failed("无法解析自动生成的 .NET 申请号或授权码", 3);
        }

        final String regId;
        final String regName;
        try {
            regId = LicenseRecoverModernGUIDotNetLocalReg.decodeRequestRegId(seq);
            regName = LicenseRecoverModernGUIDotNetLocalReg.buildRegName(regId, product);
        } catch (Exception ex) {
            log.accept("[错误] 解析本机身份/生成 regName 失败: " + ex.getMessage() + "\n");
            return OperationResult.failed(ex.getMessage(), 3);
        }

        log.accept("[一键恢复/.NET] 本机 RegID: " + regId + "\n");
        log.accept("[一键恢复/.NET] 申请号与授权码已自动生成，无需用户复制粘贴。\n");
        log.accept("[一键恢复/.NET] 本地 regName 将固定写入 UserID="
                + LicenseRecoverModernGUIDotNetLocalReg.LOCAL_AUTH_USER_ID + "。\n");

        if (dryRun) {
            log.accept("[预览] 已完成机器身份解析与 regName 构造，不写入目标文件。\n");
            return OperationResult.preview(".NET 本机授权预览完成（UserID=fwq）");
        }

        OperationResult write = LicenseRecoverModernGUIDotNetLocalReg.writeLocalLicense(
                info, regName, backup, log);
        if (!write.isSuccess()) return write;

        List<String> verify = new ArrayList<String>();
        verify.add(helper.getAbsolutePath());
        verify.add("verify");
        verify.add(info.binDir.getAbsolutePath());
        if (product != null) {
            verify.add("--product");
            verify.add(product);
        }
        log.accept("[一键恢复/.NET] 重新读取授权并执行 CheckReInfo 自校验。\n");
        OperationResult verifyResult = ProcessRunner.run(verify, log, PROCESS_TIMEOUT_SECONDS);
        return verifyResult.isSuccess()
                ? OperationResult.success(".NET 本机授权已自动生成、写入并通过自校验（UserID=fwq）")
                : verifyResult;
    }

    static String dotNetProduct(String softVersionId) {
        if (softVersionId == null || softVersionId.trim().isEmpty()) return "YX0302";
        String id = softVersionId.trim().toUpperCase(Locale.ROOT);
        if (id.startsWith("YX0302")) return "YX0302";
        if (id.startsWith("DS2601")) return "DS26";
        return id;
    }

    static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static File toolDir() {
        try {
            File location = new File(LicenseRecoverModernGUIOneClickRecovery.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return location.isFile() ? location.getParentFile() : location;
        } catch (Exception ex) {
            return new File(".").getAbsoluteFile();
        }
    }
}
