import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 统一子进程执行器：集中处理 UTF-8 输出、退出码、超时、参数兼容和销毁逻辑。
 */
public final class ProcessRunner {
    private ProcessRunner() { }

    public static OperationResult run(List<String> command, Consumer<String> log,
                                      long timeoutSeconds) {
        if (command == null || command.isEmpty()) {
            return OperationResult.failed("命令为空", 1);
        }
        Consumer<String> sink = log == null ? s -> { } : log;
        List<String> effectiveCommand = normalizeCommand(command, sink);
        OperationResult preflight = preflightCommand(effectiveCommand, sink);
        if (preflight != null) return preflight;

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(effectiveCommand);
            pb.redirectErrorStream(true);
            process = pb.start();

            final Process p = process;
            final Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), Charset.forName("UTF-8")))) {
                    String line;
                    while ((line = r.readLine()) != null) sink.accept(line + "\n");
                } catch (Exception ex) {
                    sink.accept("[警告] 读取子进程输出失败: " + ex.getMessage() + "\n");
                }
            }, "LicenseRecover-process-output");
            reader.setDaemon(true);
            reader.start();

            boolean finished;
            if (timeoutSeconds > 0) {
                finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                process.waitFor();
                finished = true;
            }

            if (!finished) {
                sink.accept("[错误] 子进程执行超时，已终止。\n");
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                reader.join(1000L);
                return OperationResult.failed("执行超时", 124);
            }

            reader.join(1000L);
            int rc = process.exitValue();
            return rc == 0
                    ? OperationResult.success("执行完成")
                    : OperationResult.failed("子进程退出码: " + rc, rc);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return OperationResult.cancelled("执行被中断");
        } catch (Exception ex) {
            if (process != null) process.destroyForcibly();
            sink.accept("[错误] 进程执行失败: " + ex + "\n");
            return OperationResult.failed(ex.getMessage(), 1);
        }
    }

    /** 对薄 GUI 发出的 CLI 命令做兼容与安全归一化。 */
    static List<String> normalizeCommand(List<String> command, Consumer<String> log) {
        List<String> result = new ArrayList<String>(command);
        boolean javaClassMode = result.contains("LicenseRecover");
        boolean licenseRecoverJarMode = false;
        for (String arg : result) {
            if (arg != null && arg.replace('\\', '/').endsWith("/LicenseRecover.jar")) {
                licenseRecoverJarMode = true;
                break;
            }
        }

        // LicenseRecover CLI 对 Java 和 .NET 都以 -p 接收产品覆盖；--product 只属于底层 C# helper。
        if (javaClassMode || licenseRecoverJarMode) {
            for (int i = 0; i < result.size(); i++) {
                if ("--product".equals(result.get(i))) result.set(i, "-p");
            }
        }

        // Java 方式三旧 CLI 不消费 --dry-run。界面选择“只预览”时必须强制降级为扫描。
        int removeIndex = result.indexOf("--remove-net");
        if (javaClassMode && removeIndex >= 0 && result.contains("--dry-run")) {
            result.set(removeIndex, "--scan-net");
            log.accept("[预览] Java 方式三不执行写回；已自动转换为只扫描。\n");
        }
        return result;
    }

    /**
     * 对会写 Java config.xml 的方式一做独立备份预检。
     * CLI 内部备份即便失败也可能继续写，因此薄 GUI 在启动 CLI 前先建立可校验快照。
     */
    static OperationResult preflightCommand(List<String> command, Consumer<String> log) {
        int mainIndex = command.indexOf("LicenseRecover");
        if (mainIndex < 0) return null;
        if (command.contains("--gencode") || command.contains("--remove-net")
                || command.contains("--scan-net") || command.contains("--batch")
                || command.contains("--dry-run") || command.contains("--no-backup")) {
            return null;
        }

        String appPath = firstPositionalAfterMain(command, mainIndex + 1);
        if (appPath == null) return null;
        AppInfo info = AppDetector.detect(new File(appPath));
        if (info.type != AppInfo.Type.JAVA) return null;
        if (!ConfigSafety.prepareJavaWay1(info, log)) {
            return OperationResult.failed("Java 方式一安全备份预检失败", 3);
        }
        return null;
    }

    static String firstPositionalAfterMain(List<String> command, int start) {
        for (int i = start; i < command.size(); i++) {
            String value = command.get(i);
            if ("-p".equals(value) || "--seq".equals(value) || "--product".equals(value)
                    || "--batch".equals(value)) {
                i++;
                continue;
            }
            if (value.startsWith("-")) continue;
            return value;
        }
        return null;
    }

    /** 兼容已有测试/调用点。 */
    static List<String> normalizeSafety(List<String> command, Consumer<String> log) {
        return normalizeCommand(command, log);
    }
}
