import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 方式三的破坏性写入前置安全检查。
 * 在真正调用补丁逻辑之前先建立独立 prepatch 备份，并校验文件大小。
 */
public final class PatchSafety {
    private PatchSafety() { }

    public static boolean prepare(AppInfo info, Consumer<String> log) {
        if (info == null || !info.isDetected()) return false;
        if (info.type == AppInfo.Type.DOTNET) return prepareDotNetPatch(info.binDir, log);
        return prepareJavaPatch(info.appRoot.getAbsolutePath(), log);
    }

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

        String stamp = stamp();
        sink.accept("--- 安全预检：建立独立 prepatch 备份 ---\n");
        try {
            if (registerUtil.isFile()) backup(registerUtil, stamp, sink);
            if (regJar != null && regJar.isFile()) backup(regJar, stamp, sink);
            sink.accept("安全预检通过：补丁目标已建立可校验备份。\n");
            return true;
        } catch (Exception ex) {
            sink.accept("[错误] 安全预检未通过，方式三已取消。\n");
            return false;
        }
    }

    public static boolean prepareDotNetPatch(File binDir, Consumer<String> log) {
        Consumer<String> sink = log == null ? s -> { } : log;
        if (binDir == null || !binDir.isDirectory()) {
            sink.accept("[错误] 安全预检失败：.NET bin 目录无效。\n");
            return false;
        }
        // DS01xx deployments can contain BOTH registration generations.
        // The native runtime may load the uppercase assembly, and some patch paths
        // touch ITMC.Web.dll. Protect every existing candidate before any write.
        File web = new File(binDir, "ITMC.Web.dll");
        File upper = new File(binDir, "ITMC.Regedit.dll");
        File lower = new File(binDir, "itmcRegedit.dll");
        if (!upper.isFile() && !lower.isFile()) {
            sink.accept("[错误] 安全预检失败：未找到 ITMC.Regedit.dll / itmcRegedit.dll。\n");
            return false;
        }
        sink.accept("--- 安全预检：建立 .NET prepatch 备份 ---\n");
        try {
            String backupStamp = stamp();
            if (web.isFile()) backup(web, backupStamp, sink);
            if (upper.isFile()) backup(upper, backupStamp, sink);
            if (lower.isFile()) backup(lower, backupStamp, sink);
            sink.accept("安全预检通过：现有 .NET 核心 DLL 已逐一建立可校验备份。\n");
            return true;
        } catch (Exception ex) {
            sink.accept("[错误] 安全预检未通过，方式三已取消。\n");
            return false;
        }
    }

    private static void backup(File source, String stamp, Consumer<String> log) throws Exception {
        File target = new File(source.getParentFile(), source.getName() + ".prepatch." + stamp + ".bak");
        SafetyBackup.requireCopy(source.toPath(), target.toPath(), log);
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date())
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
