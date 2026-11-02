import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Proves the batch planning path does not initialize or invoke a target registration class. */
public final class LicenseRecoverJavaPlanReadOnlyTest {
    private static final int CONFIG_LIMIT = 4 * 1024 * 1024;
    private static final int CLASS_LIMIT = 16 * 1024 * 1024;
    private static final String TOKEN = "synthetic-boundary-evidence";
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("lr-plan-readonly-");
        String oldSentinel = System.getProperty("lr.plan.sentinel");
        try {
            Path app = temp.resolve("sample"), lib = app.resolve("WEB-INF/lib");
            Path src = temp.resolve("src/itmc/regedit"), compiled = temp.resolve("classes");
            Files.createDirectories(lib); Files.createDirectories(src); Files.createDirectories(compiled);
            Files.createDirectories(app.resolve("data"));
            Files.write(app.resolve("data/config.xml"), Arrays.asList(
                    "<ROOT><SystemSoft><SoftVersionID>XMT0106</SoftVersionID></SystemSoft>",
                    "<reg><regType>1</regType><regName>synthetic-only</regName></reg></ROOT>"), StandardCharsets.UTF_8);
            Path sentinel = temp.resolve("target-was-executed");
            System.setProperty("lr.plan.sentinel", sentinel.toString());
            String touch = "try { java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty(\"lr.plan.sentinel\")),"
                    + " new byte[]{1}); } catch (Exception e) { throw new RuntimeException(e); }";
            Files.write(src.resolve("GetRegisterCode.java"), Arrays.asList(
                    "package itmc.regedit; public class GetRegisterCode {",
                    "public static final String FAMILY=\"XMT01\"; static { " + touch + " }",
                    "public String encrypt(String a,String b) { return \"synthetic-token\"; } }"), StandardCharsets.UTF_8);
            Files.write(src.resolve("RegisterMain.java"), Arrays.asList(
                    "package itmc.regedit; public class RegisterMain {",
                    "public RegisterMain(String a,String b,String c) { " + touch + " }",
                    "public RegInfo getRegInfo() { " + touch + " return new RegInfo(); } }"), StandardCharsets.UTF_8);
            Files.write(src.resolve("RegInfo.java"), Arrays.asList(
                    "package itmc.regedit; public class RegInfo {",
                    "public String getRegStr() { return \"XMT0106\"; }",
                    "public String getProName() { return \"XMT01\"; } }"), StandardCharsets.UTF_8);
            Process compiler = new ProcessBuilder("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                    "-d", compiled.toString(), src.resolve("GetRegisterCode.java").toString(),
                    src.resolve("RegisterMain.java").toString(), src.resolve("RegInfo.java").toString()).inheritIO().start();
            check(compiler.waitFor() == 0, "compile synthetic registration classes");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(lib.resolve("ITMCReg.jar")))) {
                for (String name : new String[]{"GetRegisterCode", "RegisterMain", "RegInfo"}) {
                    String entry = "itmc/regedit/" + name + ".class";
                    jar.putNextEntry(new JarEntry(entry)); jar.write(Files.readAllBytes(compiled.resolve(entry))); jar.closeEntry();
                }
            }
            byte[] configBefore = Files.readAllBytes(app.resolve("data/config.xml"));
            LicenseRecoverModernGUIJavaPlan readonly = LicenseRecoverModernGUIJavaPlan.inspectReadOnly(app.toFile());
            check(readonly.detected && "XMT01".equals(readonly.runtimeProductId), "static plan still recognizes product identity");
            check(!Files.exists(sentinel), "read-only plan must not initialize target class or call constructor/getRegInfo");
            check(readonly.regStr == null, "no target probe result is fabricated in read-only plan");
            // Positive control: this exact fixture reaches the old native fallback.
            LicenseRecoverModernGUIJavaPlan compatibility = LicenseRecoverModernGUIJavaPlan.inspect(app.toFile());
            check(Files.exists(sentinel) && "XMT0106".equals(compatibility.regStr), "explicit recovery planning retains legacy probe compatibility");
            Files.delete(sentinel);
            LicenseRecoverModernGUIJavaPlan.inspectReadOnly(app.toFile());
            check(!Files.exists(sentinel), "read-only path remains separate after a compatible probe");
            check(Arrays.equals(configBefore, Files.readAllBytes(app.resolve("data/config.xml"))), "synthetic config unchanged");
            String gui = new String(Files.readAllBytes(Paths.get("src/main/java/LicenseRecoverModernGUI.java")), StandardCharsets.UTF_8);
            int start = gui.indexOf("private BatchScanInspection inspectBatchDirectory(");
            int end = gui.indexOf("private boolean hasExecutableBatchTargets()", start);
            String batch = gui.substring(start, end);
            check(batch.contains("LicenseRecoverModernGUIJavaPlan.inspectReadOnly(info.appRoot)")
                    && !batch.contains("LicenseRecoverModernGUIJavaPlan.inspect("), "GUI batch must use read-only plan entrypoint");
            String preview = new String(Files.readAllBytes(Paths.get("src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java")), StandardCharsets.UTF_8);
            int previewStart = preview.indexOf("private static void refreshPlan(");
            int previewEnd = preview.indexOf("private static String value(", previewStart);
            String refresh = preview.substring(previewStart, previewEnd);
            check(refresh.contains("LicenseRecoverModernGUIJavaPlan.inspectReadOnly(selected)")
                    && !refresh.contains("LicenseRecoverModernGUIJavaPlan.inspect("), "passive modern preview cannot invoke recovery probe");
            resourceBoundaries(temp.resolve("boundaries"));
            check(!Files.exists(sentinel), "resource-boundary discovery must not execute target code");
            System.out.println("PASS: read-only Java planning skips target execution; config/class/JAR bounds, early-token revocation and fallbacks verified; legacy probe positive control passed");
        } finally {
            if (oldSentinel == null) System.clearProperty("lr.plan.sentinel"); else System.setProperty("lr.plan.sentinel", oldSentinel);
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); }
                    catch (IOException e) { throw new UncheckedIOException(e); } });
            }
        }
    }

    private static void resourceBoundaries(Path root) throws Exception {
        Files.createDirectories(root);
        Path config = root.resolve("config.xml");
        String xml = "<SoftVersionID>DS50101</SoftVersionID><System id='DS50101'/><System id='DS50102'/>";
        paddedFile(config, xml, CONFIG_LIMIT);
        check("DS50101".equals(call("readElement", new Class<?>[]{File.class, String.class},
                config.toFile(), "SoftVersionID")), "config exactly at 4 MiB remains usable");
        check(LicenseRecoverModernGUIJavaPlan.hasEnumeratedClassesFamily(config.toFile(), "DS501", "DS50101"),
                "enumerated family at config boundary remains usable");
        paddedFile(config, xml, CONFIG_LIMIT + 1L);
        check(call("readElement", new Class<?>[]{File.class, String.class},
                config.toFile(), "SoftVersionID") == null, "over-limit config cannot retain an early identity tag");
        check(!LicenseRecoverModernGUIJavaPlan.hasEnumeratedClassesFamily(config.toFile(), "DS501", "DS50101"),
                "over-limit config cannot retain early family entries");

        Path yml = root.resolve("systemConfig.yml");
        paddedFile(yml, "VersionID: SYNTHETIC_YML\n", CONFIG_LIMIT);
        check("SYNTHETIC_YML".equals(call("readSoftId", new Class<?>[]{File.class}, root.toFile())),
                "YAML at config boundary remains usable");
        paddedFile(yml, "VersionID: UNSAFE_EARLY_ID\n", CONFIG_LIMIT + 1L);
        Files.createDirectories(root.resolve("data"));
        Files.write(root.resolve("data/config.xml"),
                "<SoftVersionID>SAFE_FALLBACK</SoftVersionID>".getBytes(StandardCharsets.UTF_8));
        check("SAFE_FALLBACK".equals(call("readSoftId", new Class<?>[]{File.class}, root.toFile())),
                "over-limit YAML is rejected and existing XML fallback remains usable");

        Path classes = root.resolve("classes");
        Files.createDirectories(classes);
        Path classFile = classes.resolve("Candidate.class");
        paddedFile(classFile, TOKEN, CLASS_LIMIT);
        check(classContains(classFile), "class exactly at 16 MiB remains usable");
        check(scanClasses(classes), "directory token discovery accepts class at boundary");
        paddedFile(classFile, TOKEN, CLASS_LIMIT + 1L);
        check(!classContains(classFile), "over-limit class cannot retain an early token");
        check(!scanClasses(classes), "directory token discovery rejects over-limit class");
        Files.write(classes.resolve("Fallback.class"), TOKEN.getBytes(StandardCharsets.ISO_8859_1));
        check(scanClasses(classes), "oversized class does not suppress a separate bounded class fallback");

        Path lib = root.resolve("lib");
        Files.createDirectories(lib);
        Path jar = lib.resolve("candidate.jar");
        writeJar(jar, new String[]{"Boundary.class"}, new int[]{CLASS_LIMIT}, new String[]{TOKEN});
        check(scanJars(lib), "JAR class entry exactly at 16 MiB remains usable");
        writeJar(jar, new String[]{"Early.class", "Oversized.class"},
                new int[]{TOKEN.length(), CLASS_LIMIT + 1}, new String[]{TOKEN, ""});
        check(Files.size(jar) < 1024 * 1024, "compressed oversized fixture has small disk footprint");
        check(!scanJars(lib), "later oversized JAR entry revokes an earlier matching entry");
        writeJar(lib.resolve("fallback.jar"), new String[]{"Fallback.class"},
                new int[]{TOKEN.length()}, new String[]{TOKEN});
        check(scanJars(lib), "invalid JAR candidate does not suppress a separate bounded JAR fallback");

        Path mismatchedLib = root.resolve("mismatched-lib");
        Files.createDirectories(mismatchedLib);
        Path mismatched = mismatchedLib.resolve("mismatched.jar");
        writeJar(mismatched, new String[]{"Candidate.class"},
                new int[]{TOKEN.length() + 8}, new String[]{TOKEN});
        changeDeclaredEntrySize(mismatched, TOKEN.length());
        check(!scanJars(mismatchedLib),
                "JAR declared length cannot hide extra stream bytes after an early token");
        writeJar(mismatched, new String[]{"Candidate.class"},
                new int[]{TOKEN.length()}, new String[]{TOKEN});
        changeDeclaredEntrySize(mismatched, TOKEN.length() + 8);
        check(!scanJars(mismatchedLib),
                "JAR early EOF cannot retain a token from an incomplete declared entry");

        Path packed = root.resolve("packed.jar");
        writeJar(packed, new String[]{"itmc/regedit/RegisterMain.class"},
                new int[]{CLASS_LIMIT}, new String[]{"Virbox"});
        check(LicenseRecoverModernGUIJavaPlan.isVirboxPackedJar(packed.toFile()),
                "packed-class discovery accepts a fully read bounded candidate");
        writeJar(packed, new String[]{"itmc/regedit/RegisterMain.class"},
                new int[]{CLASS_LIMIT + 1}, new String[]{"Virbox"});
        check(!LicenseRecoverModernGUIJavaPlan.isVirboxPackedJar(packed.toFile()),
                "packed-class discovery cannot retain an over-limit early marker");
    }

    private static boolean classContains(Path file) throws Exception {
        return (Boolean) call("classFileContainsAll", new Class<?>[]{File.class, String[].class},
                file.toFile(), new String[]{TOKEN});
    }

    private static boolean scanClasses(Path dir) throws Exception {
        return (Boolean) call("scanFilesForToken", new Class<?>[]{File.class, byte[].class, int.class, int.class},
                dir.toFile(), TOKEN.getBytes(StandardCharsets.ISO_8859_1), 0, 10);
    }

    private static boolean scanJars(Path dir) throws Exception {
        return (Boolean) call("scanJarsForToken", new Class<?>[]{File.class, byte[].class},
                dir.toFile(), TOKEN.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = LicenseRecoverModernGUIJavaPlan.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static void paddedFile(Path file, String prefix, long size) throws IOException {
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(size);
            output.write(prefix.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeJar(Path file, String[] names, int[] sizes, String[] prefixes) throws IOException {
        byte[] padding = new byte[8192];
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < names.length; i++) {
                jar.putNextEntry(new JarEntry(names[i]));
                byte[] prefix = prefixes[i].getBytes(StandardCharsets.ISO_8859_1);
                jar.write(prefix);
                int remaining = sizes[i] - prefix.length;
                while (remaining > 0) {
                    int count = Math.min(remaining, padding.length);
                    jar.write(padding, 0, count);
                    remaining -= count;
                }
                jar.closeEntry();
            }
        }
    }

    private static void changeDeclaredEntrySize(Path file, int size) throws IOException {
        // Alter only the tiny synthetic central-directory record. The entry
        // stream and its descriptor keep their original length and CRC.
        byte[] jar = Files.readAllBytes(file);
        for (int i = 0; i + 28 <= jar.length; i++) {
            if (jar[i] == 'P' && jar[i + 1] == 'K' && jar[i + 2] == 1 && jar[i + 3] == 2) {
                for (int b = 0; b < 4; b++) jar[i + 24 + b] = (byte) (size >>> (8 * b));
                Files.write(file, jar);
                return;
            }
        }
        throw new IOException("synthetic ZIP central directory missing");
    }
}
