import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Test exactly the same runtime-jar-first classpath as the Windows GUI.
 * The old GUI JAR may contain an older LegacyDotNetProtocol ABI: linking
 * must not accidentally use build/verify compilation outputs.
 */
public final class LegacyProtocolDistributionSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected runtime jar path");
        File expectedRuntime = new File(args[0]).getCanonicalFile();
        Class<?> protocol = Class.forName("LegacyDotNetProtocol");
        Class<?> codeResult = Class.forName("LegacyDotNetProtocol$CodeResult");
        Class<?> coordinator = Class.forName("LicenseRecoverModernGUIAutoRecovery");
        requireFromRuntime(protocol, expectedRuntime);
        requireFromRuntime(codeResult, expectedRuntime);
        requireFromRuntime(coordinator, expectedRuntime);
        Method method = protocol.getDeclaredMethod("generateAuthorizationCode",
                String.class, String.class, String.class);
        if (!Modifier.isPublic(method.getModifiers())
                || !Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != codeResult) {
            throw new AssertionError("Wrong legacy protocol method ABI in runtime JAR");
        }
        // Reproduce the JVM binary link requested by the recovery coordinator:
        // return type is part of the method descriptor.
        MethodHandles.publicLookup().findStatic(protocol, "generateAuthorizationCode",
                MethodType.methodType(codeResult, String.class, String.class, String.class));
        System.out.println("PASS: GUI runtime resolves exact LegacyDotNetProtocol "
                + "generateAuthorizationCode(String,String,String):CodeResult from overlay");
    }

    private static void requireFromRuntime(Class<?> target, File expected) throws Exception {
        File loadedFrom = new File(target.getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getCanonicalFile();
        if (!loadedFrom.equals(expected))
            throw new AssertionError("Runtime class mismatch for " + target.getName()
                    + ": loaded from " + loadedFrom.getName());
    }
}
