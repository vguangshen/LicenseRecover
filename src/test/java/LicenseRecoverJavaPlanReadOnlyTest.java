import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
            System.out.println("PASS: read-only Java batch planning skips target initialization/constructor/getRegInfo; positive control reached legacy probe");
        } finally {
            if (oldSentinel == null) System.clearProperty("lr.plan.sentinel"); else System.setProperty("lr.plan.sentinel", oldSentinel);
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); }
                    catch (IOException e) { throw new UncheckedIOException(e); } });
            }
        }
    }
}
