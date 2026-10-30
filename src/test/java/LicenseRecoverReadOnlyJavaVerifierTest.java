import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** Synthetic receipt tests; no vendor classes, license values or target execution. */
public final class LicenseRecoverReadOnlyJavaVerifierTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static boolean accepted(LicenseRecoverLicenseState state) {
        return state.acceptance == LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE;
    }
    private static LicenseRecoverLicenseState inspect(Path app, LicenseRecoverReadOnlyJavaVerifier.Profile proof) {
        return LicenseRecoverReadOnlyJavaVerifier.verifyReviewed(app.toFile(),
                new LicenseRecoverReadOnlyJavaVerifier.Profile[]{proof});
    }
    public static void main(String[] args) throws Exception {
        Path app = Files.createTempDirectory("lr-java-gate-proof-");
        try {
            Path classes = app.resolve("WEB-INF/classes"), lib = app.resolve("WEB-INF/lib");
            Files.createDirectories(classes); Files.createDirectories(lib);
            byte[] code = {(byte)0xca,(byte)0xfe,(byte)0xba,(byte)0xbe,0,0,0,52};
            Path one = classes.resolve("Gate.class"), jar = lib.resolve("registration.jar");
            Path wiring = classes.resolve("struts.xml"), web = app.resolve("WEB-INF/web.xml");
            Files.write(one, code); Files.write(jar, new byte[]{1,2,3});
            Files.write(wiring, "synthetic reviewed wiring".getBytes(StandardCharsets.UTF_8));
            Files.write(web, "synthetic reviewed listener".getBytes(StandardCharsets.UTF_8));
            LicenseRecoverReadOnlyJavaVerifier.Profile proof =
                    LicenseRecoverReadOnlyJavaVerifier.snapshot(app).fixtureProfile();
            String before = LicenseRecoverReadOnlyJavaVerifier.inputSnapshot(app.toFile());
            LicenseRecoverLicenseState state = inspect(app, proof);
            check(accepted(state), "complete reviewed gate receipt accepts");
            check(state.authorization == LicenseRecoverLicenseState.Authorization.UNVERIFIED,
                    "code acceptance is not native authorization");
            check(state.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,
                    "installed semantics is not a trusted original/Patch comparison");
            check(before.equals(LicenseRecoverReadOnlyJavaVerifier.inputSnapshot(app.toFile())), "no input writes");
            check(Arrays.equals(code, Files.readAllBytes(one)), "no target execution or rewrite");
            check(!accepted(LicenseRecoverReadOnlyJavaVerifier.verify(app.toFile())), "synthetic receipt cannot enter production registry");

            Files.write(app.resolve("config.xml"), "PRIVATE_SENTINEL license payload".getBytes(StandardCharsets.UTF_8));
            Files.write(classes.resolve("database.properties"), "PRIVATE_SENTINEL password".getBytes(StandardCharsets.UTF_8));
            check(accepted(inspect(app, proof)), "unconsumed license and DB credentials do not control audited static gate");
            check(!inspect(app, proof).evidence.contains("PRIVATE_SENTINEL"), "no configuration values exposed");

            Files.write(classes.resolve("Added.class"), code);
            check(!accepted(inspect(app, proof)), "new application code invalidates proof");
            Files.delete(classes.resolve("Added.class"));
            Files.write(one, new byte[]{(byte)0xca,(byte)0xfe,(byte)0xba,(byte)0xbe,0,0,0,53});
            check(!accepted(inspect(app, proof)), "changed class cannot reuse gate receipt");
            Files.write(one, code);
            Files.write(lib.resolve("Added.jar"), new byte[]{4,5});
            check(!accepted(inspect(app, proof)), "new class-loading dependency invalidates proof");
            Files.delete(lib.resolve("Added.jar"));
            Files.write(jar, new byte[]{3,2,1});
            check(!accepted(inspect(app, proof)), "changed dependency invalidates proof");
            Files.write(jar, new byte[]{1,2,3});
            Files.write(classes.resolve("struts-plugin.xml"), new byte[]{1});
            check(!accepted(inspect(app, proof)), "new configuration provider invalidates proof");
            Files.delete(classes.resolve("struts-plugin.xml"));
            Files.write(wiring, new byte[]{2});
            check(!accepted(inspect(app, proof)), "changed authorization wiring invalidates proof");
            Files.write(wiring, "synthetic reviewed wiring".getBytes(StandardCharsets.UTF_8));
            Files.delete(web);
            check(!accepted(inspect(app, proof)), "missing listener binding is unknown");
            Files.write(web, "synthetic reviewed listener".getBytes(StandardCharsets.UTF_8));
            Files.write(one, new byte[]{0});
            check(inspect(app, proof).isScanError(), "malformed application class is an inspection error");
            Files.write(one, code);
            check(inspect(app, new LicenseRecoverReadOnlyJavaVerifier.Profile("bad",1,"bad",1,"bad",1,"bad")).isScanError(),
                    "corrupt reviewed registry fails closed");
            String changed = LicenseRecoverReadOnlyJavaVerifier.inputSnapshot(app.toFile());
            Files.write(jar, new byte[]{9,8,7});
            check(!changed.equals(LicenseRecoverReadOnlyJavaVerifier.inputSnapshot(app.toFile())),
                    "opaque snapshot binds all proof dependencies");
            System.out.println("PASS: static Java gate receipts accept only complete audited builds; changed/missing/corrupt inputs fail closed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(app)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                });
            }
        }
    }
}
