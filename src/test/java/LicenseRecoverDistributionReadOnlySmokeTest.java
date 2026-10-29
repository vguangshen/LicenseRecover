import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Integration regression: load and call the read-only scanner via exactly the
 * runtime-JAR-first classpath shipped to Windows users, never build/verify.
 *
 * v1.2.66 compiled source tests successfully, but did not copy scanner nested
 * classes into LicenseRecoverRuntime.jar. Every real batch scan consequently
 * raised NoClassDefFoundError: LicenseRecoverReadOnlyPatchScanner$Runtime.
 */
public final class LicenseRecoverDistributionReadOnlySmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Expected runtime JAR and optional compiled-main directory");
        File runtimeJar = new File(args[0]).getCanonicalFile();
        verifyCompleteRuntime(runtimeJar, args.length == 2 ? new File(args[1]) : null);

        String[] required = {
            "LicenseRecoverReadOnlyFingerprint",
            "LicenseRecoverReadOnlyPatchScanner",
            "LicenseRecoverReadOnlyPatchScanner$Runtime",
            "LicenseRecoverReadOnlyPatchScanner$Reviewed",
            "LicenseRecoverLicenseState",
            "LicenseRecoverLicenseState$Authorization",
            "LicenseRecoverLicenseState$CodeIntegrity",
            "LicenseRecoverLicenseState$VisualStatus"
        };
        for (String name : required) requireFromRuntime(Class.forName(name), runtimeJar);

        Class<?> scanner = Class.forName("LicenseRecoverReadOnlyPatchScanner");
        Class<?> runtime = Class.forName("LicenseRecoverReadOnlyPatchScanner$Runtime");
        Class<?> licenseState = Class.forName("LicenseRecoverLicenseState");
        Method scan = scanner.getMethod("scan", File.class, runtime, String.class);
        Method visualStatus = licenseState.getMethod("visualStatus");
        Method display = licenseState.getMethod("display");
        int inspected = 0;

        // Production registry is intentionally empty until real original and
        // patched samples have been independently reviewed. These calls must
        // link without missing classes, and must NOT fabricate green evidence.
        for (Object platform : runtime.getEnumConstants()) {
            Object state = scan.invoke(null, new File("."), platform, "DS0101");
            if (state == null || !licenseState.isInstance(state))
                throw new AssertionError("Runtime scan returned unexpected state");
            if (!"UNVERIFIED".equals(String.valueOf(visualStatus.invoke(state))))
                throw new AssertionError("Empty trusted registry must remain gray for " + platform);
            if (!"未验证".equals(String.valueOf(display.invoke(state))))
                throw new AssertionError("Empty trusted registry must be labeled unverified");
            inspected++;
        }
        if (inspected != 2)
            throw new AssertionError("Both Java and .NET scanner enum entries required");
        System.out.println("PASS: both Java/.NET scans link from shipped runtime JAR; "
                + "no NoClassDefFoundError and no fabricated activation evidence");
    }

    private static void verifyCompleteRuntime(File runtime, File compiledMain) throws Exception {
        Set<String> declared = new HashSet<String>();
        try (JarFile jar = new JarFile(runtime)) {
            JarEntry manifest = jar.getJarEntry("META-INF/LicenseRecover-classes.sha256");
            if (manifest == null) throw new AssertionError("Missing compiled main-class inventory");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    jar.getInputStream(manifest), StandardCharsets.US_ASCII))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.matches("[0-9a-f]{64}  [A-Za-z0-9_$/]+\\.class"))
                        throw new AssertionError("Malformed compiled-class inventory entry");
                    String name = line.substring(66);
                    if (!declared.add(name)) throw new AssertionError("Duplicate class: " + name);
                    JarEntry entry = jar.getJarEntry(name);
                    if (entry == null) throw new AssertionError("Missing compiled class: " + name);
                    byte[] actual;
                    try (InputStream input = jar.getInputStream(entry)) { actual = digest(input); }
                    if (!hex(actual).equals(line.substring(0, 64)))
                        throw new AssertionError("Runtime class differs from fresh compilation: " + name);
                    // Resolve all main and nested classes without initializing them. In
                    // particular, no GUI threads, target loaders or native host may run.
                    Class<?> loaded = Class.forName(name.substring(0, name.length() - 6)
                            .replace('/', '.'), false, LicenseRecoverDistributionReadOnlySmokeTest.class.getClassLoader());
                    requireFromRuntime(loaded, runtime);
                    if (compiledMain != null) {
                        File source = new File(compiledMain, name);
                        try (InputStream input = Files.newInputStream(source.toPath())) {
                            if (!MessageDigest.isEqual(actual, digest(input)))
                                throw new AssertionError("Stale packaged class: " + name);
                        }
                    }
                }
            }
            if (declared.isEmpty()) throw new AssertionError("Empty compiled-class inventory");
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !declared.contains(name))
                    throw new AssertionError("Unlisted runtime class: " + name);
            }
        }
        if (compiledMain != null) requireAllCompiledClasses(compiledMain, compiledMain, declared);
        System.out.println("PASS: all " + declared.size()
                + " compiled main/nested classes are byte-exact and load from the shipped runtime");
    }

    private static void requireAllCompiledClasses(File root, File current, Set<String> declared) {
        File[] children = current.listFiles();
        if (children == null) throw new AssertionError("Unreadable compiled-main directory");
        for (File child : children) {
            if (child.isDirectory()) requireAllCompiledClasses(root, child, declared);
            else if (child.getName().endsWith(".class")) {
                String name = root.toURI().relativize(child.toURI()).getPath();
                if (!declared.contains(name)) throw new AssertionError("Omitted compiled class: " + name);
            }
        }
    }

    private static byte[] digest(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        return digest.digest();
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte b : value) result.append(String.format("%02x", b & 0xff));
        return result.toString();
    }

    private static void requireFromRuntime(Class<?> target, File expected) throws Exception {
        if (target.getProtectionDomain() == null
                || target.getProtectionDomain().getCodeSource() == null)
            throw new AssertionError("Missing runtime code source: " + target.getName());
        File loadedFrom = new File(target.getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getCanonicalFile();
        if (!loadedFrom.equals(expected))
            throw new AssertionError("Wrong runtime class source: " + target.getName()
                    + " from " + loadedFrom + ", expected " + expected);
    }
}
