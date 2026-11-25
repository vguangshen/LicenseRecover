import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Synthetic PT recovery integration: executes the current coordinator and real
 * helper child JVMs against a deliberately small, generated registration API.
 * It is not a real PT0405 product or a claim of real-server compatibility.
 *
 * The tool runtime is built from this invocation's compiled production classes;
 * the tracked compatibility JAR can supply dependencies but cannot supply the
 * coordinator/host. No target registration class comes from a bundled tool JAR.
 */
public final class LicenseRecoverPtRecoveryTest {
    private static int checks;
    private static final String EXISTING_SCOPE = "YT00102,PT0401";

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && "--worker".equals(args[0])) {
            run(Paths.get(args[1]).toAbsolutePath(), Paths.get(args[2]).toAbsolutePath());
            return;
        }
        if (args.length != 2)
            throw new IllegalArgumentException("Expected current compiled-main directory and repository root");
        // Only this test worker and its helper children receive the JDK17+ opt-in.
        // Java 8 interprets this property differently, so never set it there.
        ProcessBuilder builder = new ProcessBuilder(javaExe(), "-cp", System.getProperty("java.class.path"),
                LicenseRecoverPtRecoveryTest.class.getName(), "--worker", args[0], args[1]);
        if (javaMajor() >= 17) {
            String previous = builder.environment().get("JAVA_TOOL_OPTIONS");
            builder.environment().put("JAVA_TOOL_OPTIONS", (previous == null ? "" : previous + " ")
                    + "-Djava.security.manager=allow");
        }
        Process worker = builder.inheritIO().start();
        if (!worker.waitFor(120, TimeUnit.SECONDS)) {
            worker.destroyForcibly();
            throw new AssertionError("Synthetic PT recovery worker timed out");
        }
        check(worker.exitValue() == 0, "Synthetic PT recovery worker failed: " + worker.exitValue());
    }

    private static void run(Path compiledMain, Path repo) throws Exception {
        long started = System.nanoTime();
        Path temporary = Files.createTempDirectory("lr-pt-recovery-");
        try {
            Path tool = Files.createDirectories(temporary.resolve("tool"));
            Path runtime = tool.resolve("LicenseRecoverRuntime.jar");
            jarDirectory(compiledMain, runtime);
            Files.copy(repo.resolve("LicenseRecover.jar"), tool.resolve("LicenseRecover.jar"));
            Path fixtureClasses = compileFixture(temporary);
            try (URLClassLoader loader = new URLClassLoader(new URL[]{
                    runtime.toUri().toURL(), tool.resolve("LicenseRecover.jar").toUri().toURL()
            }, ClassLoader.getSystemClassLoader().getParent())) {
                Class<?> coordinator = Class.forName("LicenseRecoverModernGUIAutoRecovery", true, loader);
                for (String name : Arrays.asList("LicenseRecoverModernGUIAutoRecovery", "LicenseRecover",
                        "LicenseRecoverJavaHost", "LicenseRecoverModernGUIJavaPlan", "NetRemover")) {
                    Class<?> type = Class.forName(name, false, loader);
                    check(new File(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                                    .getCanonicalFile().equals(runtime.toFile().getCanonicalFile()),
                            "Current production class must load from the freshly built runtime: " + name);
                }
                Method recover = coordinator.getMethod("recover", File.class, boolean.class,
                        boolean.class, boolean.class, Consumer.class);
                noRecordUsesDirectoryFallback(temporary, fixtureClasses, runtime, recover);
                nativeScopeWins(temporary, fixtureClasses, runtime, recover);
                configuredScopeWins(temporary, fixtureClasses, runtime, recover);
                failedProbeUsesDirectoryFallback(temporary, fixtureClasses, runtime, recover);
                freshVerificationFailureRollsBack(temporary, fixtureClasses, runtime, recover);
                previewDoesNotWrite(temporary, fixtureClasses, runtime, recover);
            }
            System.out.println("PT_SYNTHETIC_RECOVERY_CHECKS_PASSED=" + checks);
            System.out.println("PT_SYNTHETIC_RECOVERY_CASES=6 elapsed_ms="
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } finally {
            deleteTree(temporary);
        }
    }

    private static void noRecordUsesDirectoryFallback(Path work, Path fixtureClasses, Path runtime,
                                                       Method recover) throws Exception {
        Path root = fixture(work.resolve("empty-record"), fixtureClasses);
        byte[] metadata = Files.readAllBytes(root.resolve("data/config.xml"));
        Outcome result = recover(recover, root, false);
        check(result.success, "PT with no record must use directory fallback: " + result.message + "\n" + result.log);
        check(result.log.contains("source=pt-directory-fallback"), "Fallback provenance must be explicit");
        check(result.log.indexOf("PROBE: start") >= 0
                        && result.log.indexOf("PROBE: start") < result.log.indexOf("REGSTR_FALLBACK"),
                "Native RegStr must be probed before directory fallback is chosen");
        check(result.log.contains("RESULT: NO_REGSTR"), "Fixture must actually have no original native RegStr");
        check(read(root.resolve("config.xml")).contains("<FixtureRegStr>PT0405</FixtureRegStr>"),
                "Directory fallback must persist exactly PT0405, not the advertised module list");
        check(result.log.contains("persisted RegStr=PT0405"), "Fresh verifier must confirm the chosen scope");
        assertNativeStages(result.log, runtime);
        check(Arrays.equals(metadata, Files.readAllBytes(root.resolve("data/config.xml"))),
                "Product metadata must not be rewritten to manufacture RegStr");
        check(hasPreOneClickBackup(root.resolve("data")), "PT recovery must retain its mandatory pre-write backup");
    }

    private static void nativeScopeWins(Path work, Path fixtureClasses, Path runtime,
                                        Method recover) throws Exception {
        Path root = fixture(work.resolve("existing-record"), fixtureClasses);
        write(root.resolve("config.xml"), record(EXISTING_SCOPE));
        Outcome result = recover(recover, root, false);
        check(result.success, "Existing PT native scope must remain usable: " + result.message + "\n" + result.log);
        check(result.log.contains("PROBE: OK RegStr=" + EXISTING_SCOPE),
                "The target's existing scope must be read through the native probe");
        check(!result.log.contains("source=pt-directory-fallback"), "Existing native scope must win over the directory rule");
        check(read(root.resolve("config.xml")).contains("<FixtureRegStr>" + EXISTING_SCOPE + "</FixtureRegStr>"),
                "An existing scope without SoftVersionID must be preserved, not replaced or expanded");
        check(result.log.contains("persisted RegStr=" + EXISTING_SCOPE), "Fresh verifier must preserve all native scope tokens");
        assertNativeStages(result.log, runtime);
    }

    private static void configuredScopeWins(Path work, Path fixtureClasses, Path runtime,
                                             Method recover) throws Exception {
        Path root = fixture(work.resolve("configured-scope"), fixtureClasses);
        String configured = "PT0401,PT0402";
        Path metadataFile = root.resolve("data/config.xml");
        write(metadataFile, read(metadataFile).replace("</ROOT>", "<regInfo>" + configured + "</regInfo></ROOT>"));
        byte[] metadata = Files.readAllBytes(metadataFile);
        write(root.resolve("config.xml"), record(EXISTING_SCOPE));
        Outcome result = recover(recover, root, false);
        check(result.success, "Explicit config RegStr must remain usable: " + result.message + "\n" + result.log);
        check(result.log.contains("source=target-plan") && !result.log.contains("PROBE: start")
                        && !result.log.contains("source=pt-directory-fallback"),
                "Explicit regInfo must precede both native probing and the directory rule");
        check(read(root.resolve("config.xml")).contains("<FixtureRegStr>" + configured + "</FixtureRegStr>"),
                "Explicit module RegStr must be used as-is even when it omits SoftVersionID");
        check(Arrays.equals(metadata, Files.readAllBytes(metadataFile)), "Recovery must preserve the explicit regInfo source");
        assertNativeStages(result.log, runtime);
    }

    private static void failedProbeUsesDirectoryFallback(Path work, Path fixtureClasses, Path runtime,
                                                          Method recover) throws Exception {
        Path root = fixture(work.resolve("failed-probe"), fixtureClasses);
        write(root.resolve("reject-initial-read.flag"), "synthetic native getRegInfo exception before first write");
        Outcome result = recover(recover, root, false);
        check(result.success, "A failed native read must allow the requested PT fallback: " + result.message + "\n" + result.log);
        check(result.log.contains("JAVA_HOST_ERROR=java.io.IOException: synthetic initial RegStr read failure")
                        && result.log.contains("PROBE: rejected"),
                "The initial probe must actually throw and return nonzero, not merely return an empty scope");
        check(result.log.contains("source=pt-directory-fallback")
                        && result.log.indexOf("PROBE: rejected") < result.log.indexOf("REGSTR_FALLBACK"),
                "A rejected native read must fall back only after the probe finishes");
        check(read(root.resolve("config.xml")).contains("<FixtureRegStr>PT0405</FixtureRegStr>"),
                "The failed-probe fallback must still persist exactly the PT directory name");
        assertNativeStages(result.log, runtime);
    }

    private static void freshVerificationFailureRollsBack(Path work, Path fixtureClasses, Path runtime,
                                                          Method recover) throws Exception {
        Path root = fixture(work.resolve("rejected-verification"), fixtureClasses);
        byte[] original = "<ROOT><Before>existing-root-content</Before></ROOT>".getBytes(StandardCharsets.UTF_8);
        byte[] originalLog = "original-registration-log".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("config.xml"), original);
        Files.write(root.resolve("Register.xml"), originalLog);
        write(root.resolve("reject-verify.flag"), "synthetic verification rejection");
        byte[] metadata = Files.readAllBytes(root.resolve("data/config.xml"));
        Outcome result = recover(recover, root, false);
        check(!result.success, "A failed fresh verification must never be reported as success");
        check(result.log.contains("source=pt-directory-fallback") && result.log.contains("DOREG: OK"),
                "Rollback fixture must reach real fallback write-back before failing verification");
        check(result.log.contains("VERIFY_FRESH: rejected") && result.log.contains("[rollback-java]"),
                "Native verification failure must trigger the coordinator's rollback");
        check(result.log.contains("FIXTURE_FRESH_JVM=true"), "The rejection must be observed in a fresh helper JVM");
        assertHostSource(result.log, runtime);
        check(Arrays.equals(original, Files.readAllBytes(root.resolve("config.xml"))), "Rollback must restore existing root config byte-for-byte");
        check(Arrays.equals(originalLog, Files.readAllBytes(root.resolve("Register.xml"))), "Rollback must restore the prior registration log");
        check(!Files.exists(root.resolve("WEB-INF/lib/Register.xml")), "Rollback must remove a newly created registration log");
        check(Arrays.equals(metadata, Files.readAllBytes(root.resolve("data/config.xml"))), "Rollback must preserve product metadata");
        check(hasPreOneClickBackup(root), "Failed fallback writes must still leave recoverable pre-one-click backups");
    }

    private static void previewDoesNotWrite(Path work, Path fixtureClasses, Path runtime,
                                            Method recover) throws Exception {
        Path root = fixture(work.resolve("preview"), fixtureClasses);
        byte[] metadata = Files.readAllBytes(root.resolve("data/config.xml"));
        Outcome result = recover(recover, root, true);
        check(result.success && result.log.contains("source=pt-directory-fallback"),
                "Preview must exercise the same missing-RegStr fallback decision");
        check(result.log.contains("GENCODE_PARSE: OK") && !result.log.contains("DOREG: start"),
                "Preview may generate test codes but must stop before write-back");
        check(!Files.exists(root.resolve("config.xml")) && !Files.exists(root.resolve("Register.xml"))
                        && !Files.exists(root.resolve("WEB-INF/lib/Register.xml")),
                "Preview must not create registration records or logs");
        check(Arrays.equals(metadata, Files.readAllBytes(root.resolve("data/config.xml"))), "Preview must preserve metadata");
        assertHostSource(result.log, runtime);
    }

    private static final class Outcome {
        final boolean success;
        final String message, log;
        Outcome(boolean success, String message, String log) { this.success = success; this.message = message; this.log = log; }
    }

    private static Outcome recover(Method method, Path root, boolean preview) throws Exception {
        StringBuilder log = new StringBuilder();
        Consumer<String> sink = line -> log.append(line);
        Object result;
        try { result = method.invoke(null, root.toFile(), false, false, preview, sink); }
        catch (InvocationTargetException failure) { throw new AssertionError("Coordinator invocation failed", failure.getCause()); }
        return new Outcome(result.getClass().getField("success").getBoolean(result),
                String.valueOf(result.getClass().getField("message").get(result)), log.toString());
    }

    private static void assertNativeStages(String log, Path runtime) throws Exception {
        check(log.contains("[java-stage] GENCODE: start") && log.contains("DOREG: OK")
                        && log.contains("[java-stage] VERIFY_FRESH: OK"),
                "Recovery must execute generation, native persistence and fresh verification in child JVMs");
        check(log.contains("FIXTURE_FRESH_JVM=true"), "Verification cannot reuse the process that wrote the record");
        assertHostSource(log, runtime);
    }

    private static void assertHostSource(String log, Path runtime) throws Exception {
        String marker = "FIXTURE_HOST_SOURCE=" + runtime.toFile().getCanonicalPath();
        check(log.contains(marker), "Helper children must load the current host from the new runtime, not a tracked old JAR: " + log);
    }

    private static boolean hasPreOneClickBackup(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            return stream.anyMatch(path -> path.getFileName().toString().endsWith(".preoneclick.bak"));
        }
    }

    private static Path fixture(Path parent, Path compiled) throws Exception {
        Path root = Files.createDirectories(parent.resolve("PT0405"));
        Path classes = Files.createDirectories(root.resolve("WEB-INF/classes"));
        Path lib = Files.createDirectories(root.resolve("WEB-INF/lib"));
        Files.createDirectories(root.resolve("data"));
        write(root.resolve("data/config.xml"), "<ROOT><SoftVersionID>PT0405</SoftVersionID>"
                + "<System id='PT0401'/><System id='PT0402'/><System id='PT0403'/><System id='PT0404'/></ROOT>");
        try (java.util.stream.Stream<Path> stream = Files.walk(compiled)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                Path relative = compiled.relativize(file);
                if (relative.toString().replace('\\', '/').startsWith("itmc/")) continue;
                Path destination = classes.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(lib.resolve("ITMCReg.jar")))) {
            try (java.util.stream.Stream<Path> stream = Files.walk(compiled.resolve("itmc"))) {
                for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile).sorted()::iterator) {
                    jar.putNextEntry(new JarEntry(compiled.relativize(file).toString().replace('\\', '/')));
                    jar.write(Files.readAllBytes(file));
                    jar.closeEntry();
                }
            }
        }
        return root;
    }

    private static Path compileFixture(Path work) throws Exception {
        Path source = Files.createDirectories(work.resolve("fixture-source"));
        Path output = Files.createDirectories(work.resolve("fixture-classes"));
        List<String> sources = new ArrayList<String>();
        addSource(source, sources, "itmc/regedit/DesUtil.java",
                "package itmc.regedit; public class DesUtil { public int getRandom(){return 1000;}"
                + "public String getMotherboardSN(){return \"0123456789ABCDEF\";}"
                + "public String getCurrentTime(){return \"2026-01-01 00:00:00\";} }");
        addSource(source, sources, "itmc/regedit/GetRegisterCode.java",
                "package itmc.regedit; public class GetRegisterCode {"
                + "public String buildCiphertext(String s){return encrypt(\"synthetic\",s);}"
                + "public String encrypt(String k,String s){StringBuilder b=new StringBuilder();"
                + "for(byte x:s.getBytes(java.nio.charset.StandardCharsets.UTF_8))b.append(String.format(\"%02X\",x&255));return b.toString();}"
                + "public String decrypt(String k,String s){byte[] b=new byte[s.length()/2];"
                + "for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);"
                + "return new String(b,java.nio.charset.StandardCharsets.UTF_8);} }");
        addSource(source, sources, "itmc/regedit/RegInfo.java",
                "package itmc.regedit; public class RegInfo { private final String scope,product;"
                + "public RegInfo(String s,String p){scope=s;product=p;}"
                + "public String getRegStr(){return scope;} public String getProName(){return product;}"
                + "public String getRegID(){return \"0123456789ABCDEF\";} }");
        addSource(source, sources, "itmc/regedit/RegisterMain.java",
                "package itmc.regedit; import java.nio.file.*; import java.nio.charset.StandardCharsets;"
                + "public class RegisterMain { private final Path root; private final String product;"
                + "private static final String JVM=java.util.UUID.randomUUID().toString();"
                + "public RegisterMain(String p,String token)throws Exception{this(p,token,\".\");}"
                + "public RegisterMain(String p,String token,String path)throws Exception{root=Paths.get(path);product=p;"
                + "Class<?> host=Class.forName(\"LicenseRecoverJavaHost\",false,ClassLoader.getSystemClassLoader());"
                + "System.out.println(\"FIXTURE_HOST_SOURCE=\"+new java.io.File(host.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalPath());}"
                + "private String field(String name)throws Exception{Path f=root.resolve(\"config.xml\");if(!Files.isRegularFile(f))return null;"
                + "String s=new String(Files.readAllBytes(f),StandardCharsets.UTF_8),a=\"<\"+name+\">\",z=\"</\"+name+\">\";"
                + "int start=s.indexOf(a),end=s.indexOf(z);return start>=0&&end>start?s.substring(start+a.length(),end):null;}"
                + "public RegInfo getRegInfo()throws Exception{"
                + "if(Files.exists(root.resolve(\"reject-initial-read.flag\"))&&field(\"FixtureWriterJvm\")==null)"
                + "throw new java.io.IOException(\"synthetic initial RegStr read failure\");"
                + "return new RegInfo(field(\"FixtureRegStr\"),product);}"
                + "public boolean doRegistry(String request,String auth)throws Exception{String plain=new GetRegisterCode().decrypt(\"synthetic\",auth);"
                + "if(plain.length()<66)throw new IllegalStateException(\"bad synthetic payload\");String scope=plain.substring(66);"
                + "String xml=\"<ROOT><reg><regType>1</regType><regName>synthetic-only</regName></reg><FixtureRegStr>\"+scope"
                + "+\"</FixtureRegStr><FixtureWriterJvm>\"+JVM+\"</FixtureWriterJvm></ROOT>\";"
                + "Files.write(root.resolve(\"config.xml\"),xml.getBytes(StandardCharsets.UTF_8));"
                + "Files.write(root.resolve(\"Register.xml\"),\"synthetic native write log\".getBytes(StandardCharsets.UTF_8));"
                + "Files.write(root.resolve(\"WEB-INF/lib/Register.xml\"),\"synthetic new log\".getBytes(StandardCharsets.UTF_8));return true;}"
                + "public boolean checkReInfo()throws Exception{boolean fresh=!JVM.equals(field(\"FixtureWriterJvm\"));"
                + "System.out.println(\"FIXTURE_FRESH_JVM=\"+fresh);return !fresh||Files.exists(root.resolve(\"reject-verify.flag\"))||field(\"FixtureRegStr\")==null;} }");
        addSource(source, sources, "javax/servlet/ServletContext.java",
                "package javax.servlet; public interface ServletContext { String getRealPath(String path); }");
        addSource(source, sources, "com/common/utils/RegisterUtil.java",
                "package com.common.utils; public class RegisterUtil { public static boolean checkRegister(String root)throws Exception {"
                + "return new itmc.regedit.RegisterMain(\"PT0405\",\"synthetic-token\",root).checkReInfo();} }");
        addSource(source, sources, "com/common/sys/configuration/SysParamInit.java",
                "package com.common.sys.configuration; public class SysParamInit { public void run(javax.servlet.ServletContext context)throws Exception {"
                + "com.common.utils.RegisterUtil.checkRegister(context.getRealPath(\"/\"));} }");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "Integration fixture requires a JDK compiler");
        List<String> options = new ArrayList<String>(Arrays.asList("-encoding", "UTF-8", "-source", "8", "-target", "8", "-d", output.toString()));
        options.addAll(sources);
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int exit = compiler.run(null, diagnostics, diagnostics, options.toArray(new String[options.size()]));
        check(exit == 0, "Synthetic target compilation failed: " + diagnostics.toString("UTF-8"));
        return output;
    }

    private static String record(String scope) {
        return "<ROOT><reg><regType>1</regType><regName>synthetic-only</regName></reg>"
                + "<FixtureRegStr>" + scope + "</FixtureRegStr></ROOT>";
    }

    private static void addSource(Path root, List<String> sources, String name, String text) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        write(file, text);
        sources.add(file.toString());
    }

    private static void jarDirectory(Path directory, Path output) throws IOException {
        check(Files.isRegularFile(directory.resolve("LicenseRecoverModernGUIAutoRecovery.class")),
                "Expected this build's compiled production classes");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output));
             java.util.stream.Stream<Path> stream = Files.walk(directory)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class")).sorted()::iterator) {
                jar.putNextEntry(new JarEntry(directory.relativize(file).toString().replace('\\', '/')));
                jar.write(Files.readAllBytes(file));
                jar.closeEntry();
            }
        }
    }

    private static void write(Path path, String text) throws IOException { Files.write(path, text.getBytes(StandardCharsets.UTF_8)); }
    private static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static String javaExe() { return new File(System.getProperty("java.home"), "bin" + File.separator
            + (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java")).getAbsolutePath(); }
    private static int javaMajor() {
        String version = System.getProperty("java.specification.version");
        return Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version.split("\\.")[0]);
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            for (Path path : (Iterable<Path>) stream.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
        }
    }
}
