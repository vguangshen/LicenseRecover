import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.StringReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ITMC 应用结构识别的单一入口。
 * 这里只负责识别，不执行授权写入/补丁，便于 GUI、CLI 和批量模式复用。
 */
public final class AppDetector {
    static final int MAX_CONFIG_BYTES = 4 * 1024 * 1024;
    static final int MAX_PROPERTIES_BYTES = 1024 * 1024;
    private static final Pattern SOFT_VERSION = Pattern.compile(
            "(?is)<SoftVersionID\\b[^>]*>\\s*([^<]+?)\\s*</SoftVersionID\\s*>");

    private AppDetector() { }

    /**
     * 单应用检测。优先只检测当前目录；仅当用户明确选中了 bin/lib/WEB-INF/classes/data
     * 这类应用结构目录时才向父级回溯。这样既兼容手动选到内部目录，又避免批量扫描中的
     * 普通子目录被误识别成父目录应用。
     */
    public static AppInfo detect(File selectedPath) {
        if (selectedPath == null) return AppInfo.unknown(null);
        File selected = selectedPath.getAbsoluteFile();
        if (!selected.isDirectory()) return AppInfo.unknown(selected);

        AppInfo local = detectLocal(selected);
        if (local.isDetected()) return local;
        if (!mayWalkUp(selected)) return AppInfo.unknown(selected);

        File cur = selected.getParentFile();
        while (cur != null) {
            File dotNetBin = findDotNetBinLocal(cur);
            if (dotNetBin != null) return fromDotNetBin(selected, dotNetBin);

            File lib = findJavaLibLocal(cur);
            if (lib != null) return fromJavaLib(selected, lib);
            cur = cur.getParentFile();
        }
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

    private static boolean mayWalkUp(File selected) {
        if (selected == null) return false;
        String name = selected.getName();
        return "bin".equalsIgnoreCase(name)
                || "lib".equalsIgnoreCase(name)
                || "WEB-INF".equalsIgnoreCase(name)
                || "classes".equalsIgnoreCase(name)
                || "data".equalsIgnoreCase(name);
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

    /**
     * 新版 YX0303 系列样本只保留大写 ITMC.Regedit.dll；较旧版本同时带小写
     * itmcRegedit.dll。只要存在 ITMC.Web.dll 且任一注册程序集存在，即视为 .NET 应用。
     */
    public static boolean hasDotNetFiles(File dir) {
        return dir != null && dir.isDirectory()
                && new File(dir, "ITMC.Web.dll").isFile()
                && (new File(dir, "ITMC.Regedit.dll").isFile()
                    || new File(dir, "itmcRegedit.dll").isFile());
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
                String text = StandardCharsets.UTF_8.newDecoder().decode(
                        ByteBuffer.wrap(readMetadataBytes(yml, MAX_PROPERTIES_BYTES))).toString();
                try (BufferedReader lines = new BufferedReader(new StringReader(text))) {
                    String raw;
                    while ((raw = lines.readLine()) != null) {
                        String line = raw.trim();
                        if (!line.toLowerCase().contains("versionid")) continue;
                        int split = line.indexOf(':');
                        if (split < 0) split = line.indexOf('=');
                        if (split > 0) return emptyToNull(line.substring(split + 1).trim());
                    }
                }
            }
        } catch (Exception ignore) { }

        String dataVersion = readSoftVersionXml(new File(appRoot, "data" + File.separator + "config.xml"));
        if (dataVersion != null) return dataVersion;
        // XMT01xx generation keeps SystemSoft in WEB-INF/classes/config.xml.
        String classesVersion = readSoftVersionXml(new File(appRoot, "WEB-INF" + File.separator
                + "classes" + File.separator + "config.xml")); // classes/config.xml fallback
        return classesVersion;
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
            String text = new String(readMetadataBytes(file, MAX_CONFIG_BYTES), StandardCharsets.UTF_8);
            Matcher matcher = SOFT_VERSION.matcher(text);
            return matcher.find() ? emptyToNull(matcher.group(1).trim()) : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    /** Metadata discovery must not allocate from an unbounded file before the audited scanner runs. */
    static byte[] readMetadataBytes(File file, int limit) throws IOException {
        if (file == null || limit < 1 || !Files.isRegularFile(file.toPath()))
            throw new IOException("metadata-input-limit");
        long size = Files.size(file.toPath());
        if (size > limit) throw new IOException("metadata-input-limit");
        // Allocate once from the bounded observation, never a growing buffer
        // plus a full toByteArray copy. A changed length revokes discovery.
        byte[] bytes = new byte[(int)size];
        try (InputStream input = Files.newInputStream(file.toPath())) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new IOException("metadata-input-changed");
                offset += count;
            }
            if (input.read() != -1) throw new IOException("metadata-input-changed");
        }
        return bytes;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
