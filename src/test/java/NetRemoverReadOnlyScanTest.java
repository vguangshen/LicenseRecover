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
            Path lib = Files.createDirectories(root.resolve("WEB-INF").resolve("lib"));
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
            check(NetRemover.scan(root.toString(), line -> log.append(line).append('\n')),
                    "read-only scan should recognize a synthetic registration JAR");
            String report = log.toString();
            check(report.contains("CheckNet"), "test fixture must exercise method detection");
            check(report.contains("不能确认已 Patch"), "missing markers must remain unverified");
            check(!report.contains("该 RegisterMain 已补丁"), "must not claim patch from missing strings");
            check(!report.contains("无需重复修补"), "must not infer successful prior patch");
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
