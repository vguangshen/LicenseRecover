import java.io.File;
import java.lang.reflect.Method;

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
        if (args.length != 1)
            throw new IllegalArgumentException("Expected LicenseRecoverRuntime.jar path");
        File runtimeJar = new File(args[0]).getCanonicalFile();

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
