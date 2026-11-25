import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Retired commands must fail before touching Java/.NET targets, including old launchers. */
public final class LicenseRecoverWay3RemovalTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--dispatch-dotnet".equals(args[0])) {
            LicenseRecover.dotNetMain(new String[]{"--remove-net", "--dry-run"}, args[1]);
            throw new AssertionError("direct .NET dispatcher accepted removed operation");
        }
        if (args.length == 2 && "--dispatch-batch".equals(args[0])) {
            LicenseRecover.batchMain(new String[]{"--remove-net", "--dry-run"}, args[1]);
            throw new AssertionError("direct batch dispatcher accepted removed operation");
        }
        check(args.length == 0 || args.length == 2, "expected optional core and GUI compatibility JARs");
        Path temp = Files.createTempDirectory("lr-way3-removed-");
        try {
            Path targets = Files.createDirectory(temp.resolve("targets"));
            Path javaRoot = Files.createDirectories(targets.resolve("PT0402/WEB-INF/lib")).getParent().getParent();
            Files.write(javaRoot.resolve("WEB-INF/lib/ITMCReg.jar"), new byte[]{1, 3, 5, 7});
            Files.write(javaRoot.resolve("config.xml"), "<ROOT>original-java-config</ROOT>".getBytes(StandardCharsets.UTF_8));
            Path dotnetBin = Files.createDirectories(targets.resolve("dotnet/bin"));
            Files.write(dotnetBin.resolve("ITMC.Web.dll"), new byte[]{2, 4, 6});
            Files.write(dotnetBin.resolve("itmcRegedit.dll"), new byte[]{8, 10, 12});
            Files.write(dotnetBin.resolve("ITMC.Regedit.dll"), new byte[]{14, 16, 18});
            Files.write(dotnetBin.getParent().resolve("config.xml"),
                    "<ROOT>original-dotnet-config</ROOT>".getBytes(StandardCharsets.UTF_8));
            Map<String, byte[]> before = snapshot(targets);
            String classpath = absoluteClasspath();
            List<String> core = Arrays.asList("-cp", classpath, "LicenseRecover");
            rejected(temp, core, "--remove-net", javaRoot.toString());
            rejected(temp, core, "--remove-net", dotnetBin.toString(), "--dry-run");
            rejected(temp, core, "--batch", targets.toString(), "--remove-net");
            rejected(temp, core, "--remove-net", "--batch", targets.toString(), "--dry-run");
            rejected(temp, core, "--scan-net", javaRoot.toString(), "--remove-net");
            rejected(temp, Arrays.asList("-cp", classpath, "SafeNetRemoverCLI"), javaRoot.toString());
            rejected(temp, Arrays.asList("-cp", classpath, "LicenseRecoverWay3RemovalTest"),
                    "--dispatch-dotnet", dotnetBin.toString());
            rejected(temp, Arrays.asList("-cp", classpath, "LicenseRecoverWay3RemovalTest"),
                    "--dispatch-batch", targets.toString());

            check(LicenseRecover.removeNetMode(new String[]{"--remove-net", javaRoot.toString()}, true) == 2,
                    "old direct Java dispatcher must reject");
            check(LicenseRecover.spawnJavaRecover(javaRoot.toString(),
                    javaRoot.resolve("WEB-INF/lib").toString(), null, false, "patch") == 2,
                    "old batch child launcher must reject without starting a child");
            StringBuilder log = new StringBuilder();
            check(!NetRemover.removeNetValidation(javaRoot.toString(), log::append),
                    "old public write API must fail");
            check(log.toString().contains("[REMOVED_OPERATION]"), "old API explains removal");
            check(!NetRemover.removeNetValidation(javaRoot.toString(), null),
                    "old write API remains harmless without a logger");

            if (args.length == 2) {
                String coreJar = new File(args[0]).getAbsolutePath();
                String guiJar = new File(args[1]).getAbsolutePath();
                rejected(temp, Arrays.asList("-jar", coreJar), "--remove-net", javaRoot.toString());
                rejected(temp, Arrays.asList("-jar", coreJar), "--batch", targets.toString(), "--remove-net");
                rejected(temp, Arrays.asList("-cp", coreJar, "SafeNetRemoverCLI"), javaRoot.toString());
                rejected(temp, Arrays.asList("-cp", guiJar, "LicenseRecover"), "--remove-net", dotnetBin.toString());
                rejected(temp, Arrays.asList("-cp", guiJar, "SafeNetRemoverCLI"), dotnetBin.toString());
                try (ZipFile gui = new ZipFile(guiJar)) {
                    ZipEntry nested = gui.getEntry("LicenseRecover.jar");
                    check(nested != null, "GUI compatibility JAR retains its embedded current core");
                    Path extracted = temp.resolve("embedded-core.jar");
                    try (InputStream in = gui.getInputStream(nested)) { Files.copy(in, extracted); }
                    rejected(temp, Arrays.asList("-jar", extracted.toString()), "--remove-net", javaRoot.toString());
                }
            }
            sameSnapshot(before, snapshot(targets));
            System.out.println("Way 3 removal checks passed: commands reject and target trees are unchanged.");
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }

    private static void rejected(Path cwd, List<String> launcher, String... args) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(new File(new File(System.getProperty("java.home"), "bin"), "java").toString());
        command.add("-Dfile.encoding=UTF-8");
        command.addAll(launcher);
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("removed operation did not exit promptly: " + command);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        check(process.exitValue() == 2, "expected removed-operation exit code 2: " + command + "\n" + text);
        check(text.contains("[REMOVED_OPERATION]"), "missing specific removal diagnostic: " + text);
    }

    private static String absoluteClasspath() {
        StringBuilder result = new StringBuilder();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (result.length() > 0) result.append(File.pathSeparator);
            result.append(Paths.get(entry).toAbsolutePath());
        }
        return result.toString();
    }

    private static Map<String, byte[]> snapshot(Path root) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted()::iterator) {
                files.put(root.relativize(path).toString(), Files.isDirectory(path) ? null : Files.readAllBytes(path));
            }
        }
        return files;
    }

    private static void sameSnapshot(Map<String, byte[]> before, Map<String, byte[]> after) {
        check(before.keySet().equals(after.keySet()), "removed operations created or deleted target paths");
        for (String path : before.keySet()) {
            check(Arrays.equals(before.get(path), after.get(path)), "removed operation changed " + path);
        }
    }
}
