import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * 方式三的破坏性写入前置安全检查。
 * 在真正调用 NetRemover 之前先建立独立 prepatch 备份，并校验文件大小。
 */
public final class PatchSafety {
    private PatchSafety() { }

    public static boolean prepareJavaPatch(String appRoot, Consumer<String> log) {
        Consumer<String> sink = log == null ? s -> { } : log;
        String[] paths = NetRemover.findPaths(appRoot);
        if (paths == null) {
            sink.accept("[错误] 安全预检失败：未找到 Java 授权文件。\n");
            return false;
        }

        File libDir = new File(paths[0]);
        File classesDir = new File(paths[1]);
        File registerUtil = new File(classesDir, NetRemover.RU_REL);
        File regJar = NetRemover.findItmcRegJar(libDir);
        if (!registerUtil.isFile() && (regJar == null || !regJar.isFile())) {
            sink.accept("[错误] 安全预检失败：没有可备份的补丁目标。\n");
            return false;
        }

        String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        sink.accept("--- 安全预检：建立独立 prepatch 备份 ---\n");
        try {
            if (registerUtil.isFile()) {
                File backup = new File(registerUtil.getParentFile(),
                        registerUtil.getName() + ".prepatch." + stamp + ".bak");
                SafetyBackup.requireCopy(registerUtil.toPath(), backup.toPath(), sink);
            }
            if (regJar != null && regJar.isFile()) {
                File backup = new File(regJar.getParentFile(),
                        regJar.getName() + ".prepatch." + stamp + ".bak");
                SafetyBackup.requireCopy(regJar.toPath(), backup.toPath(), sink);
            }
            sink.accept("安全预检通过：补丁目标已建立可校验备份。\n");
            return true;
        } catch (Exception ex) {
            sink.accept("[错误] 安全预检未通过，方式三已取消。\n");
            return false;
        }
    }
}
