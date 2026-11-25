import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Regression: an original/unknown class with no legacy network strings must
 * never be reported as a confirmed patch by the read-only scanner.
 */
public final class NetRemoverReadOnlyScanTest {
    public static final class NoMarkerRegisterMain {
        public Boolean CheckNet() { return Boolean.TRUE; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("lr-unverified-java-scan-");
        try {
            Path app = Files.createDirectories(root.resolve("QT100101"));
            Path lib = Files.createDirectories(app.resolve("WEB-INF").resolve("lib"));
            Path classes = Files.createDirectories(app.resolve("WEB-INF").resolve("classes"));
            Files.write(app.resolve("systemConfig.yml"), "global.system.VersionID=QT100101".getBytes("UTF-8"));
            Files.write(classes.resolve("config.xml"), "<ROOT><SystemSoft><SoftVersionID>QT100101</SoftVersionID></SystemSoft></ROOT>".getBytes("UTF-8"));
            Path jar = lib.resolve("ITMCReg.jar");
            try (InputStream in = NetRemoverReadOnlyScanTest.class.getResourceAsStream(
                    "NetRemoverReadOnlyScanTest$NoMarkerRegisterMain.class");
                 OutputStream out = Files.newOutputStream(jar);
                 ZipOutputStream zip = new ZipOutputStream(out)) {
                check(in != null, "test fixture class missing");
                zip.putNextEntry(new ZipEntry(NetRemover.RM_ENTRY));
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) zip.write(buf, 0, n);
                zip.closeEntry();
            }

            StringBuilder log = new StringBuilder();
            check(NetRemover.scan(app.toString(), line -> log.append(line).append('\n')),
                    "read-only scan should recognize a synthetic registration JAR");
            String report = log.toString();
            check(report.contains("CheckNet"), "test fixture must exercise method detection");
            check(report.contains("不能确认已 Patch"), "missing markers must remain unverified");
            check(!report.contains("该 RegisterMain 已补丁"), "must not claim patch from missing strings");
            check(!report.contains("无需重复修补"), "must not infer successful prior patch");
            byte[] original = Files.readAllBytes(jar);
            for (Path choice : new Path[]{app, app.resolve("WEB-INF"), classes, lib}) {
                check(app.toFile().equals(LicenseRecover.resolveJavaPatchRoot(choice.toFile())),
                        "QT100 target resolves from all supported structural directories");
                check(LicenseRecover.removeNetMode(new String[]{"--scan-net", choice.toString()}, false) == 0,
                        "QT100 read-only CLI scan uses the same root");
                check(LicenseRecover.removeNetMode(new String[]{"--remove-net", choice.toString(), "--dry-run"}, true) == 0,
                        "QT100 patch preview is actually read-only");
                check(java.util.Arrays.equals(original, Files.readAllBytes(jar)),
                        "dry-run must never modify the registration JAR");
            }
            check(!Files.exists(lib.resolve("ITMCReg.jar.prepatch")),
                    "dry-run must not create a patch backup");
            Path bogus = Files.createDirectories(root.resolve("unrelated"));
            check(LicenseRecover.resolveJavaPatchRoot(bogus.toFile()) == null,
                    "an unrelated child cannot silently target a parent application");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }
}
