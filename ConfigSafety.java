import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/** Java 方式一写配置前的独立安全快照。 */
public final class ConfigSafety {
    private ConfigSafety() { }

    public static boolean prepareJavaWay1(AppInfo info, Consumer<String> log) {
        Consumer<String> sink = log == null ? s -> { } : log;
        if (info == null || info.type != AppInfo.Type.JAVA || info.libDir == null || info.appRoot == null) {
            sink.accept("[错误] Java 方式一安全预检：应用信息无效。\n");
            return false;
        }
        String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File libConfig = new File(info.libDir, "config.xml");
        File rootConfig = new File(info.appRoot, "config.xml");
        try {
            sink.accept("--- 安全预检：Java 方式一配置快照 ---\n");
            if (libConfig.isFile()) backup(libConfig, stamp, sink);
            else sink.accept("  WEB-INF/lib/config.xml 当前不存在，无原文件需要备份。\n");
            if (rootConfig.isFile() && !sameFile(libConfig, rootConfig)) backup(rootConfig, stamp, sink);
            sink.accept("Java 方式一安全预检通过。\n");
            return true;
        } catch (Exception ex) {
            sink.accept("[错误] Java 方式一备份失败，已取消写入。\n");
            return false;
        }
    }

    private static void backup(File source, String stamp, Consumer<String> log) throws Exception {
        File target = new File(source.getParentFile(), source.getName() + ".prewrite." + stamp + ".bak");
        SafetyBackup.requireCopy(source.toPath(), target.toPath(), log);
    }

    private static boolean sameFile(File a, File b) {
        try { return a.getCanonicalFile().equals(b.getCanonicalFile()); }
        catch (Exception ex) { return a.getAbsolutePath().equalsIgnoreCase(b.getAbsolutePath()); }
    }
}
