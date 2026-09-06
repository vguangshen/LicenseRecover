import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ITMC 应用结构识别的单一入口。
 * 这里只负责识别，不执行授权写入/补丁，便于 GUI、CLI 和批量模式复用。
 */
public final class AppDetector {
    private static final Pattern SOFT_VERSION = Pattern.compile(
            "(?is)<SoftVersionID\\b[^>]*>\\s*([^<]+?)\\s*</SoftVersionID\\s*>");

    private AppDetector() { }

    /** 单应用检测：允许用户选到 bin/lib/WEB-INF 或其下层后向上寻找应用。 */
    public static AppInfo detect(File selectedPath) {
        if (selectedPath == null) return AppInfo.unknown(null);
        File selected = selectedPath.getAbsoluteFile();
        if (!selected.isDirectory()) return AppInfo.unknown(selected);

        File dotNetBin = findDotNetBin(selected);
        if (dotNetBin != null) return fromDotNetBin(selected, dotNetBin);

        File lib = findJavaLib(selected);
        if (lib != null) return fromJavaLib(selected, lib);

        return AppInfo.unknown(selected);
    }

    /**
     * 批量检测：只检查当前子目录本身及其标准 bin/WEB-INF 子路径，不向父级回溯。
     * 避免父目录本身是应用时，把其任意普通子目录都误判成同一个应用。
     */
    public static AppInfo detectLocal(File selectedPath) {
        if (selectedPath == null) return AppInfo.unknown(null);
        File selected = selectedPath.getAbsoluteFile();
        if (!selected.isDirectory()) return AppInfo.unknown(selected);

        File dotNetBin = findDotNetBinLocal(selected);
        if (dotNetBin != null) return fromDotNetBin(selected, dotNetBin);

        File lib = findJavaLibLocal(selected);
        if (lib != null) return fromJavaLib(selected, lib);

        return AppInfo.unknown(selected);
    }

    private static AppInfo fromDotNetBin(File selected, File dotNetBin) {
        File root = dotNetBin.getParentFile() != null ? dotNetBin.getParentFile() : dotNetBin;
        return AppInfo.dotNetApp(selected, root, dotNetBin, readDotNetSoftVersion(dotNetBin));
    }

    private static AppInfo fromJavaLib(File selected, File lib) {
        File root = deriveJavaRoot(lib);
        return AppInfo.javaApp(selected, root, lib, readJavaSoftVersion(root));
    }

    public static File findItmcRegJar(File lib) {
        if (lib == null || !lib.isDirectory()) return null;
        File exact = new File(lib, "ITMCReg.jar");
        if (exact.isFile()) return exact;
        File[] files = lib.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.startsWith("itmcreg") && lower.endsWith(".jar");
        });
        return files != null && files.length > 0 ? files[0] : null;
    }

    public static boolean hasDotNetFiles(File dir) {
        return dir != null && dir.isDirectory()
                && new File(dir, "ITMC.Web.dll").isFile()
                && new File(dir, "itmcRegedit.dll").isFile();
    }

    public static File findDotNetBin(File start) {
        File cur = start;
        while (cur != null) {
            File found = findDotNetBinLocal(cur);
            if (found != null) return found;
            cur = cur.getParentFile();
        }
        return null;
    }

    static File findDotNetBinLocal(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        if (hasDotNetFiles(dir)) return dir;
        File bin = dir.getName().equalsIgnoreCase("bin") ? dir : new File(dir, "bin");
        return hasDotNetFiles(bin) ? bin : null;
    }

    public static File findJavaLib(File start) {
        File cur = start;
        while (cur != null) {
            File found = findJavaLibLocal(cur);
            if (found != null) return found;
            cur = cur.getParentFile();
        }
        return null;
    }

    static File findJavaLibLocal(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        if (dir.getName().equalsIgnoreCase("lib") && findItmcRegJar(dir) != null) return dir;

        File standard = new File(dir, "WEB-INF" + File.separator + "lib");
        if (findItmcRegJar(standard) != null) return standard;

        File nested = new File(dir, "WEB-INF" + File.separator + "WEB-INF"
                + File.separator + "lib");
        if (findItmcRegJar(nested) != null) return nested;
        return null;
    }

    static File deriveJavaRoot(File lib) {
        File webInf = lib == null ? null : lib.getParentFile();
        if (webInf == null) return lib;
        File parent = webInf.getParentFile();
        if ("WEB-INF".equalsIgnoreCase(webInf.getName()) && parent != null
                && "WEB-INF".equalsIgnoreCase(parent.getName())) {
            return parent.getParentFile() != null ? parent.getParentFile() : parent;
        }
        return parent != null ? parent : webInf;
    }

    public static String readJavaSoftVersion(File appRoot) {
        if (appRoot == null) return null;
        try {
            File yml = new File(appRoot, "systemConfig.yml");
            if (yml.isFile()) {
                List<String> lines = Files.readAllLines(yml.toPath(), StandardCharsets.UTF_8);
                for (String raw : lines) {
                    String line = raw.trim();
                    if (!line.toLowerCase().contains("versionid")) continue;
                    int split = line.indexOf(':');
                    if (split < 0) split = line.indexOf('=');
                    if (split > 0) return emptyToNull(line.substring(split + 1).trim());
                }
            }
        } catch (Exception ignore) { }

        return readSoftVersionXml(new File(appRoot, "data" + File.separator + "config.xml"));
    }

    public static String readDotNetSoftVersion(File binDir) {
        if (binDir == null) return null;
        String value = readSoftVersionXml(new File(binDir, "config.xml"));
        if (value != null) return value;
        File root = binDir.getParentFile();
        return root == null ? null : readSoftVersionXml(new File(root, "config.xml"));
    }

    static String readSoftVersionXml(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Matcher matcher = SOFT_VERSION.matcher(text);
            return matcher.find() ? emptyToNull(matcher.group(1).trim()) : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
