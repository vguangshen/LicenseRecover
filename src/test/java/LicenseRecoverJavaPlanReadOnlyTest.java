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
            // A future PT product may put its config in WEB-INF/classes, not data/.
            Path dataConfigPath = app.resolve("data/config.xml");
            Path classesConfigPath = app.resolve("WEB-INF/classes/config.xml");
            Files.delete(dataConfigPath);
            Files.createDirectories(classesConfigPath.getParent());
            Files.write(classesConfigPath,
                    "<ROOT><SoftVersionID>PT0406</SoftVersionID><System id='PT0405'/></ROOT>"
                            .getBytes(StandardCharsets.UTF_8));
            LicenseRecoverModernGUIJavaPlan ptLayout =
                    LicenseRecoverModernGUIJavaPlan.inspectReadOnly(app.toFile());
            check(ptLayout.detected && "PT系列 / classes-config".equals(ptLayout.generation),
                    "future PT series must be recognized in classes-config layout");
            check(!ptLayout.automaticRecoveryReady && !Files.exists(sentinel),
                    "PT series detection must stay read-only and never invent authorization");
            Files.delete(classesConfigPath);
            Files.write(dataConfigPath, configBefore);

            // Product-ID fallback activates only when no target-owned registration ID is known.
            // RegStr directory fallback is a separate explicit-recovery decision.
            // This fixture deliberately differs from SoftVersionID: no cross-check.
            Path fallbackRoot = temp.resolve("PT0405");
            Path fallbackLib = fallbackRoot.resolve("WEB-INF/lib");
            Files.createDirectories(fallbackLib);
            Files.copy(lib.resolve("ITMCReg.jar"), fallbackLib.resolve("ITMCReg.jar"));
            Files.createDirectories(fallbackRoot.resolve("data"));
            Files.write(fallbackRoot.resolve("data/config.xml"),
                    "<ROOT><SoftVersionID>PT0406</SoftVersionID></ROOT>"
                            .getBytes(StandardCharsets.UTF_8));
            LicenseRecoverModernGUIJavaPlan fallbackPlan =
                    LicenseRecoverModernGUIJavaPlan.inspectReadOnly(fallbackRoot.toFile());
            check(fallbackPlan.detected && "PT0405".equals(fallbackPlan.directoryFallbackId),
                    "unresolved PT registrations must expose directory-name fallback candidate");
            check(fallbackPlan.runtimeProductId == null && !fallbackPlan.automaticRecoveryReady
                    && fallbackPlan.recoveryReadiness.contains("备用目录候选注册ID=PT0405"),
                    "read-only planning must not present a directory candidate as existing authorization");
            check(!Files.exists(sentinel), "fallback inspection must not execute registration code");
            check("PT0405".equals(fallbackPlan.directoryDiagnosticId())
                    && fallbackPlan.runtimeProductDisplay().contains("未验证"),
                    "PT directory label must be visibly unverified");
            check(readonly.directoryDiagnosticId() == null
                    && "XMT01".equals(readonly.runtimeProductDisplay()),
                    "verified runtime identity always wins over directory display");
            Path genericRoot = temp.resolve("Custom-Java-App");
            Path genericLib = genericRoot.resolve("WEB-INF/lib");
            Files.createDirectories(genericLib);
            Files.copy(lib.resolve("ITMCReg.jar"), genericLib.resolve("ITMCReg.jar"));
            Files.createDirectories(genericRoot.resolve("data"));
            Files.write(genericRoot.resolve("data/config.xml"),
                    "<ROOT><SoftVersionID>UNKNOWN01</SoftVersionID></ROOT>"
                            .getBytes(StandardCharsets.UTF_8));
            LicenseRecoverModernGUIJavaPlan generic =
                    LicenseRecoverModernGUIJavaPlan.inspectReadOnly(genericRoot.toFile());
            check(generic.detected && "Custom-Java-App".equals(generic.directoryDiagnosticId())
                    && generic.runtimeProductDisplay().contains("未验证"),
                    "non-PT application receives its exact root directory name for display");
            check(generic.runtimeProductId == null && generic.directoryFallbackId == null
                    && !generic.automaticRecoveryReady
                    && LicenseRecoverModernGUIAutoRecovery.javaRecoveryProductId(generic) == null,
                    "diagnostic fallback must never supply an execution ID or authorize recovery");
            check(generic.logSummary().contains("directory diagnostic ID=Custom-Java-App")
                    && !Files.exists(sentinel), "diagnostic logging remains read-only");
            check("PT0405".equals(LicenseRecoverModernGUIAutoRecovery.javaRecoveryProductId(fallbackPlan)),
                    "explicit PT recovery must pass directory ID through the existing Java pipeline");
            check(LicenseRecoverModernGUIAutoRecovery.ptExplicitRecoveryCandidate(fallbackPlan),
                    "PT directory fallback must be eligible for native preflights without SoftVersionID cross-check");
            check("PT0405".equals(LicenseRecoverModernGUIAutoRecovery.ptDirectoryRegStrFallback(fallbackPlan)),
                    "explicit PT recovery can use the directory as RegStr when no existing value is available");
            check(fallbackPlan.regStr == null && !fallbackPlan.automaticRecoveryReady
                            && !Files.exists(sentinel),
                    "computing the explicit PT fallback must not mutate the read-only plan or execute target code");
            check(LicenseRecoverModernGUIAutoRecovery.acceptsProbedJavaRegStr(
                            "YT00102,PT0401", "PT0405", true),
                    "PT native RegStr can contain module IDs without the app SoftVersionID");
            check(!LicenseRecoverModernGUIAutoRecovery.acceptsProbedJavaRegStr(
                            "YT00102,PT0401", "PT0405", false),
                    "non-PT Java dynamic RegStr must retain its previous version-token check");
            check(!LicenseRecoverModernGUIAutoRecovery.acceptsProbedJavaRegStr(
                            "", "PT0405", true),
                    "an empty native result is not proven RegStr; explicit PT directory fallback is separate");
            check("XMT01".equals(LicenseRecoverModernGUIAutoRecovery.javaRecoveryProductId(readonly))
                    && !LicenseRecoverModernGUIAutoRecovery.ptExplicitRecoveryCandidate(readonly),
                    "existing Java products must retain verified IDs and never enter the PT fallback");
            check(LicenseRecoverModernGUIAutoRecovery.ptDirectoryRegStrFallback(readonly) == null
                            && LicenseRecoverModernGUIAutoRecovery.ptDirectoryRegStrFallback(generic) == null
                            && LicenseRecoverModernGUIAutoRecovery.ptDirectoryRegStrFallback(null) == null,
                    "directory RegStr fallback must not affect non-PT or missing plans");
            ptRegStrFallbackScope(temp.resolve("pt-regstr-scope"), lib.resolve("ITMCReg.jar"), sentinel);
            // A name alone cannot reach native registration or modify files:
            // this fixture has no target-proven startup constructor chain.
            LicenseRecoverModernGUIAutoRecovery.Result unproven =
                    LicenseRecoverModernGUIAutoRecovery.recover(fallbackRoot.toFile(), true, true, true, null);
            check(!unproven.success && unproven.message.contains("JAVA_RUNTIME_ABI"),
                    "PT fallback requires the target's actual Java startup ABI before proceeding");
            check(!Files.exists(fallbackRoot.resolve("config.xml")) && !Files.exists(sentinel),
                    "rejected PT native preflight did not write or execute a registration class");
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
            check(batch.contains("plan.runtimeProductDisplay()"), "batch shows the unverified directory fallback");
            String preview = new String(Files.readAllBytes(Paths.get("src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java")), StandardCharsets.UTF_8);
            int previewStart = preview.indexOf("private static void refreshPlan(");
            int previewEnd = preview.indexOf("private static String value(", previewStart);
            String refresh = preview.substring(previewStart, previewEnd);
            check(refresh.contains("LicenseRecoverModernGUIJavaPlan.inspectReadOnly(selected)")
                    && !refresh.contains("LicenseRecoverModernGUIJavaPlan.inspect("), "passive modern preview cannot invoke recovery probe");
            check(refresh.contains("plan.runtimeProductDisplay()"), "single-app preview shows the same fallback");
            qt100LayoutDiagnostics(temp.resolve("qt100-layout"));
            resourceBoundaries(temp.resolve("boundaries"));
            ptSeriesDiagnostics(temp.resolve("pt-series"));
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

    private static void ptRegStrFallbackScope(Path root, Path registrationJar, Path sentinel) throws Exception {
        // A confirmed product ID can differ from both the folder and the selected
        // modules. The fallback is literally the folder; existing scope stays intact.
        for (String directory : new String[]{"PT0407", "pt0408", "Renamed-PT-App"}) {
            Path app = root.resolve(directory);
            Path lib = app.resolve("WEB-INF/lib");
            Path data = app.resolve("data");
            Files.createDirectories(lib);
            Files.createDirectories(data);
            Files.copy(registrationJar, lib.resolve("ITMCReg.jar"));
            byte[] config = ("<ROOT><SoftVersionID>PT0406</SoftVersionID>"
                    + "<regInfo>PT0401,PT0402</regInfo></ROOT>").getBytes(StandardCharsets.UTF_8);
            Files.write(data.resolve("config.xml"), config);
            LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspectReadOnly(app.toFile());
            check(plan.detected && "PT0406".equals(plan.runtimeProductId)
                            && plan.directoryFallbackId == null,
                    "target-owned product identity has priority over the product-ID directory fallback");
            check("PT0401,PT0402".equals(plan.regStr),
                    "read-only planning preserves existing configured module scope");
            String fallback = LicenseRecoverModernGUIAutoRecovery.ptDirectoryRegStrFallback(plan);
            if ("Renamed-PT-App".equals(directory)) {
                check(fallback == null,
                        "a PT runtime product must not masquerade as a valid PT directory RegStr fallback");
            } else {
                check(directory.toUpperCase(java.util.Locale.ROOT).equals(fallback),
                        "RegStr fallback uses the actual directory even when a different runtime product is confirmed");
            }
            check("PT0401,PT0402".equals(plan.regStr) && !Files.exists(sentinel)
                            && !Files.exists(app.resolve("config.xml"))
                            && Arrays.equals(config, Files.readAllBytes(data.resolve("config.xml"))),
                    "fallback selection leaves existing RegStr, target classes and files unchanged");
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

    /** Metadata-only fixtures mirror the observed QT100101 folder layout, not its binaries. */
    private static void qt100LayoutDiagnostics(Path root) throws Exception {
        for (String id : new String[]{"QT100101", "QT100102", "QT100199"}) {
            Path app = root.resolve(id);
            Path lib = app.resolve("WEB-INF/lib");
            Path classes = app.resolve("WEB-INF/classes");
            Files.createDirectories(lib);
            Files.createDirectories(classes);
            Path marker = lib.resolve("ITMCReg.jar");
            try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(marker))) { }
            Files.write(app.resolve("systemConfig.yml"),
                    Arrays.asList("global.system.VersionID=" + id), StandardCharsets.UTF_8);
            Path xml = classes.resolve("config.xml");
            Files.write(xml, Arrays.asList("<ROOT><SystemSoft><SoftVersionID>" + id
                    + "</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
            for (Path selected : new Path[]{app, lib, classes, app.resolve("WEB-INF")}) {
                AppInfo info = AppDetector.detect(selected.toFile());
                check(info.type == AppInfo.Type.JAVA && app.toFile().equals(info.appRoot)
                        && lib.toFile().equals(info.libDir) && id.equals(info.softVersionId),
                        "QT100 metadata layout resolves the root regardless of selected structural directory: " + id);
            }
            check(AppDetector.detectLocal(app.toFile()).type == AppInfo.Type.JAVA,
                    "QT100 root is discoverable in batch mode");
            Files.delete(app.resolve("systemConfig.yml"));
            check(id.equals(AppDetector.readJavaSoftVersion(app.toFile())),
                    "classes configuration remains a metadata fallback");
            Files.delete(xml);
            LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspectReadOnly(app.toFile());
            check(plan.detected && id.equals(plan.directoryDiagnosticId())
                    && plan.runtimeProductId == null && !plan.automaticRecoveryReady,
                    "directory label alone is diagnostic, not verified execution identity");
            Files.delete(marker);
            Files.createDirectory(lib.resolve("ITMCReg-fake.jar"));
            check(AppDetector.findItmcRegJar(lib.toFile()) == null
                    && !AppDetector.detectLocal(app.toFile()).isDetected(),
                    "a directory named like a JAR must not be mistaken for a component file");
        }
    }

    private static void ptSeriesDiagnostics(Path root) throws Exception {
        Path data = root.resolve("data");
        Files.createDirectories(data);
        Path config = data.resolve("config.xml");
        Files.write(config, ("<ROOT><SoftVersionID>PT0405</SoftVersionID>"
                + "<!-- <System id='PT0405'/> -->"
                + "<![CDATA[<System id='PT0405'/>]]>"
                + "<System id='PT0402'/><System id='PT0402'/>"
                + "<system name='module' id = \"pt0403\"/>"
                + "<System id='PT0404\"/></ROOT>")
                .getBytes(StandardCharsets.UTF_8));
        String missing = LicenseRecoverModernGUIJavaPlan.ptSeriesBlockedReason(config.toFile(), "PT0405");
        check(missing != null && missing.contains("PT0402") && missing.contains("PT0403")
                && missing.contains("不包含当前版本") && !missing.contains("还有其他条目"),
                "PT diagnostic must ignore comments, CDATA, malformed quotes, and duplicate modules");
        check("PT0405".equals(LicenseRecoverModernGUIJavaPlan.ptDirectoryIdFallback(
                root.resolve("pt0405").toFile())), "PT folder fallback must be case-insensitive");
        check(LicenseRecoverModernGUIJavaPlan.ptDirectoryIdFallback(
                root.resolve("PT0405-backup").toFile()) == null,
                "non-exact directory names must not become PT registration IDs");
        check(LicenseRecoverModernGUIJavaPlan.ptSeriesBlockedReason(config.toFile(), "DS50101") == null,
                "PT-series diagnostics must not alter unrelated Java families");
        Files.write(config, ("<ROOT><System id='PT0406'/><System id='PT0407'/></ROOT>")
                .getBytes(StandardCharsets.UTF_8));
        String future = LicenseRecoverModernGUIJavaPlan.ptSeriesBlockedReason(config.toFile(), "PT0406");
        check(future != null && future.contains("包含当前版本") && future.contains("只读待核"),
                "future PT versions must be diagnosed without hard-coded product mappings");
        StringBuilder many = new StringBuilder("<ROOT>");
        for (int i = 400; i < 409; i++) many.append("<System id='PT0").append(i).append("'/>");
        many.append("</ROOT>");
        Files.write(config, many.toString().getBytes(StandardCharsets.UTF_8));
        String overflow = LicenseRecoverModernGUIJavaPlan.ptSeriesBlockedReason(config.toFile(), "PT0408");
        check(overflow != null && overflow.contains("还有其他条目") && overflow.contains("包含当前版本"),
                "PT module preview is bounded but still checks the entire module inventory");
        paddedFile(config, "<System id='PT0406'/>", CONFIG_LIMIT + 1L);
        String overLimit = LicenseRecoverModernGUIJavaPlan.ptSeriesBlockedReason(config.toFile(), "PT0406");
        check(overLimit != null && overLimit.contains("无法完整读取") && !overLimit.contains("包含当前版本"),
                "oversized PT configs must not retain partial module evidence");
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
