import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 对会改写目标文件的操作提供强制备份。
 * 备份失败或内容校验不一致必须作为硬失败处理，调用方不得继续修改源文件。
 */
public final class SafetyBackup {
    private SafetyBackup() { }

    public static void requireCopy(Path source, Path backup, Consumer<String> log) throws Exception {
        Consumer<String> sink = log == null ? s -> { } : log;
        if (source == null || backup == null) {
            throw new IllegalArgumentException("备份源或目标为空");
        }
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("待备份文件不存在: " + source);
        }
        try {
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            sink.accept("  [错误] 备份失败，已取消后续修改: " + ex.getMessage() + "\n");
            throw ex;
        }

        boolean valid = Files.isRegularFile(backup)
                && Files.size(backup) == Files.size(source)
                && Arrays.equals(sha256(source), sha256(backup));
        if (!valid) {
            try { Files.deleteIfExists(backup); } catch (Exception ignore) { }
            throw new IllegalStateException("备份内容校验失败，已取消后续修改: " + backup);
        }
        sink.accept("  已备份并校验 -> " + backup.getFileName() + "\n");
    }

    private static byte[] sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }
}
