import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 统一子进程执行器：集中处理 UTF-8 输出、退出码、超时和销毁逻辑。
 */
public final class ProcessRunner {
    private ProcessRunner() { }

    public static OperationResult run(List<String> command, Consumer<String> log,
                                      long timeoutSeconds) {
        if (command == null || command.isEmpty()) {
            return OperationResult.failed("命令为空", 1);
        }
        Consumer<String> sink = log == null ? s -> { } : log;
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
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
}
