import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only recovery of RegStr from an existing local ITMC authorization.
 *
 * Safety invariant: vendor RegisterMain.getRegInfo() is invoked only when the
 * selected config.xml explicitly says regType=1 and contains a non-empty
 * regName. regType=3 is never probed because that branch may contact the vendor
 * authorization web service.
 */
final class ExistingLocalRegStrProbe {
    private ExistingLocalRegStrProbe() { }

    static boolean isSafeLocalConfig(File config) {
        if (config == null || !config.isFile()) return false;
        return "1".equals(readElement(config, "regType")) && !blank(readElement(config, "regName"));
    }

    static String recover(File appRoot, File libDir, String runtimeProductId) {
        if (appRoot == null || libDir == null || !libDir.isDirectory() || blank(runtimeProductId)) return null;
        File regJar = NetRemover.findItmcRegJar(libDir);
        if (regJar == null || !regJar.isFile()) return null;

        ArrayList<File> candidates = new ArrayList<File>();
        addCandidate(candidates, new File(appRoot, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml"));
        addCandidate(candidates, new File(appRoot, "config.xml"));
        addCandidate(candidates, new File(appRoot, "data" + File.separator + "config.xml"));
        addCandidate(candidates, new File(libDir, "config.xml"));

        ArrayList<File> safe = new ArrayList<File>();
        for (File config : candidates) if (isSafeLocalConfig(config)) safe.add(config);
        if (safe.isEmpty()) return null;

        Path unpacked = null;
        URLClassLoader loader = null;
        try {
            ArrayList<URL> urls = new ArrayList<URL>();
            if (NetRemover.jarIsPacked(regJar)) {
                unpacked = Files.createTempDirectory("licenserecover-regstr-unpack-");
                int count = NetRemover.unpackPackedJar(regJar, unpacked.toFile());
                if (count <= 0) return null;
                urls.add(unpacked.toUri().toURL());
            }
            File[] jars = libDir.listFiles();
            if (jars != null) {
                java.util.Arrays.sort(jars, new java.util.Comparator<File>() {
                    public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
                });
                for (File jar : jars) {
                    if (jar.isFile() && jar.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                        urls.add(jar.toURI().toURL());
                    }
                }
            }
            loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), null);

            Class<?> codeType = Class.forName("itmc.regedit.GetRegisterCode", true, loader);
            Object code = codeType.getConstructor().newInstance();
            String token = (String) codeType.getMethod("encrypt", String.class, String.class)
                    .invoke(code, runtimeProductId + "RegeditNew", "itmcsoft");
            if (blank(token)) return null;

            Class<?> mainType = Class.forName("itmc.regedit.RegisterMain", true, loader);
            java.lang.reflect.Constructor<?> ctor3 = null;
            java.lang.reflect.Constructor<?> ctor2 = null;
            try { ctor3 = mainType.getConstructor(String.class, String.class, String.class); } catch (Exception ignore) { }
            try { ctor2 = mainType.getConstructor(String.class, String.class); } catch (Exception ignore) { }
            java.lang.reflect.Method getRegInfo = mainType.getMethod("getRegInfo");

            LinkedHashSet<String> recovered = new LinkedHashSet<String>();
            for (File config : safe) {
                try {
                    Object main;
                    String dir = config.getParentFile().getAbsolutePath() + File.separator;
                    if (ctor3 != null) {
                        main = ctor3.newInstance(runtimeProductId, token, dir);
                    } else if (ctor2 != null && sameFile(config.getParentFile(), libDir)) {
                        // Older two-argument RegisterMain always resolves config.xml beside ITMCReg.jar.
                        main = ctor2.newInstance(runtimeProductId, token);
                    } else {
                        continue;
                    }

                    // Deliberately call getRegInfo() directly. Never call checkReInfo() while probing.
                    Object info = getRegInfo.invoke(main);
                    if (info == null) continue;
                    Class<?> infoType = info.getClass();
                    String regStr = normalizeCsv(String.valueOf(infoType.getMethod("getRegStr").invoke(info)));
                    Object proObj = infoType.getMethod("getProName").invoke(info);
                    String proName = proObj == null ? null : String.valueOf(proObj).trim();
                    if (blank(regStr)) continue;
                    if (!blank(proName) && !runtimeProductId.equalsIgnoreCase(proName)) continue;
                    recovered.add(regStr);
                } catch (Throwable ignore) {
                    // One stale/local config must not make scanning fail; try the next safe candidate.
                }
            }
            return recovered.size() == 1 ? recovered.iterator().next() : null;
        } catch (Throwable ignore) {
            return null;
        } finally {
            if (loader != null) try { loader.close(); } catch (Exception ignore) { }
            if (unpacked != null) deleteTree(unpacked.toFile());
        }
    }

    private static void addCandidate(ArrayList<File> out, File candidate) {
        if (candidate == null) return;
        for (File existing : out) if (sameFile(existing, candidate)) return;
        out.add(candidate);
    }

    private static boolean sameFile(File a, File b) {
        if (a == null || b == null) return false;
        try { return a.getCanonicalFile().equals(b.getCanonicalFile()); }
        catch (Exception ignore) { return a.getAbsoluteFile().equals(b.getAbsoluteFile()); }
    }

    private static String readElement(File file, String element) {
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(?is)<" + Pattern.quote(element)
                    + "\\b[^>]*>\\s*([^<]*?)\\s*</" + Pattern.quote(element) + "\\s*>").matcher(text);
            return m.find() ? m.group(1).trim() : null;
        } catch (Exception ignore) { return null; }
    }

    private static String normalizeCsv(String value) {
        if (value == null) return null;
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (String part : value.split(",")) {
            String x = part == null ? "" : part.trim();
            if (!x.isEmpty()) values.add(x);
        }
        if (values.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String x : values) {
            if (out.length() > 0) out.append(',');
            out.append(x);
        }
        return out.toString();
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        try { Files.deleteIfExists(file.toPath()); } catch (Exception ignore) { }
    }
}
