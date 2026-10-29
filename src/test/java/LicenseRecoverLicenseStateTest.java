public final class LicenseRecoverLicenseStateTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        LicenseRecoverLicenseState unknown = LicenseRecoverLicenseState.unverified("config exists");
        check(unknown.authorization == LicenseRecoverLicenseState.Authorization.UNVERIFIED, "config is not activation");
        check(unknown.selectedByDefault(), "unknown remains selectable");
        check("未验证".equals(unknown.display()), "unknown label");
        LicenseRecoverLicenseState valid = LicenseRecoverLicenseState.nativeVerification(true, "target native result");
        check(!valid.selectedByDefault(), "valid must be deselected");
        LicenseRecoverLicenseState invalid = LicenseRecoverLicenseState.nativeVerification(false, "target native result");
        check(invalid.selectedByDefault(), "invalid must be selected");
        LicenseRecoverLicenseState patched = LicenseRecoverLicenseState.codeComparison(true, "versioned original comparison");
        check(!patched.selectedByDefault(), "patched must be deselected");
        check(patched.authorization == LicenseRecoverLicenseState.Authorization.UNVERIFIED, "patch is not activation");
        LicenseRecoverLicenseState both = LicenseRecoverLicenseState.combine(valid, patched);
        check(both.authorization == LicenseRecoverLicenseState.Authorization.VERIFIED_VALID,
                "native evidence must survive merge");
        check(both.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,
                "patch evidence must survive merge");
        check(both.display().contains("代码已 Patch"), "combined status must show both facts");
        boolean badSource = false;
        try { LicenseRecoverLicenseState.combine(unknown, patched); }
        catch (IllegalArgumentException expected) { badSource = true; }
        check(badSource, "unverified state cannot masquerade as native evidence");
        check(LicenseRecoverLicenseState.visualStatus("已验证激活（代码已 Patch）")
                == LicenseRecoverLicenseState.VisualStatus.PATCHED_UNVERIFIED,
                "patched and valid must use the light green patch color");
        check(!LicenseRecoverLicenseState.selectedByDefault("已验证激活（代码已 Patch）"),
                "combined verified state must be deselected");
        check(!LicenseRecoverLicenseState.selectedByDefault("检测失败"),
                "failed inspection must be deselected");
        check(LicenseRecoverLicenseState.selectedByDefault("未验证"),
                "unknown evidence must remain selectable");
        check(LicenseRecoverLicenseState.visualStatus("已确认 Patch（授权未验证）")
                == LicenseRecoverLicenseState.VisualStatus.PATCHED_UNVERIFIED,
                "patch color must not imply native license validity");
        LicenseRecoverLicenseState invalidAndPatched =
                LicenseRecoverLicenseState.combine(
                        LicenseRecoverLicenseState.nativeVerification(false, "native check returned invalid"),
                        LicenseRecoverLicenseState.codeComparison(true, "known-version bytecode diff"));
        check(invalidAndPatched.display().contains("未激活（代码已 Patch）"),
                "a patch must never hide a failed native license check");
        check(invalidAndPatched.visualStatus()
                == LicenseRecoverLicenseState.VisualStatus.PATCHED_UNVERIFIED,
                "confirmed patch uses light green even when native validation differs");
        check(LicenseRecoverLicenseState.visualStatus(invalidAndPatched.display())
                == invalidAndPatched.visualStatus(), "typed and table status must agree");
        check(LicenseRecoverLicenseState.visualStatus("未知异常：代码已 Patch 字样")
                == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                "arbitrary labels must never be promoted to confirmed patch");
        check(LicenseRecoverLicenseState.visualStatus("已验证激活（检测尚未执行）")
                == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                "untrusted native-valid prefix must not turn dark green");
        check(LicenseRecoverLicenseState.visualStatus("已确认 Patch（仅根据历史日志）")
                == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                "untrusted patch prefix must not turn light green");
        check(LicenseRecoverLicenseState.visualStatus("检测失败后又发现状态")
                == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                "diagnostics must not masquerade as a typed scan error");
        check(LicenseRecoverLicenseState.visualStatus("已验证未激活（不明附注）")
                == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                "unknown invalid-status suffix must not be accepted");
        check(LicenseRecoverLicenseState.visualStatus(invalid.display())
                == LicenseRecoverLicenseState.VisualStatus.NATIVE_INVALID,
                "verified-invalid status must retain its white color category");
        check(LicenseRecoverLicenseState.visualStatus(unknown.display())
                == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                "unknown status must retain its gray color category");
        check(LicenseRecoverLicenseState.visualStatus(valid.display()) == valid.visualStatus(),
                "valid status label must agree with typed state");
        check(LicenseRecoverLicenseState.visualStatus(patched.display()) == patched.visualStatus(),
                "patch status label must agree with typed state");
        // Exact, reviewed binary fingerprints are required; history, missing
        // markers and unrelated versions must never turn a row light green.
        try {
            java.nio.file.Path artifact = java.nio.file.Files.createTempFile("lr-readonly-", ".bin");
            String originalHash = "bfc8234d0e0f6184640fec12438757ceac504df4ab8b232921b9bb506c981cb1";
            String patchedHash = "ba15b5958ced9058f5441c1b8929a72905e71c2898b82a9eab307b85f78cab07";
            try {
                java.nio.file.Files.write(artifact, "original-binary".getBytes("UTF-8"));
                LicenseRecoverLicenseState originalFingerprint = LicenseRecoverReadOnlyFingerprint.compare(
                        artifact.toFile(), "DS0101", "DS0101", originalHash, patchedHash);
                check(originalFingerprint.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,
                        "exact audited original fingerprint");
                check(originalFingerprint.authorization == LicenseRecoverLicenseState.Authorization.UNVERIFIED,
                        "original file is not proof of valid license");
                check(LicenseRecoverReadOnlyFingerprint.compare(
                        artifact.toFile(), "DS0107", "DS0101", originalHash, patchedHash)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "a different product version cannot inherit evidence");
                check(LicenseRecoverReadOnlyFingerprint.compare(
                        artifact.toFile(), "DS0101", "DS0101", originalHash, originalHash)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "ambiguous identical fingerprints must be rejected");
                check(LicenseRecoverReadOnlyFingerprint.compare(
                        artifact.toFile(), "DS0101", "DS0101", originalHash, "")
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "missing reviewed patch baseline cannot confirm original or patch");
                java.nio.file.Files.write(artifact, "unknown-build".getBytes("UTF-8"));
                check(LicenseRecoverReadOnlyFingerprint.compare(
                        artifact.toFile(), "DS0101", "DS0101", originalHash, patchedHash)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "unrecognized modified binary is not confirmed patch");
                java.nio.file.Files.write(artifact, "reviewed-patch".getBytes("UTF-8"));
                LicenseRecoverLicenseState patchFingerprint = LicenseRecoverReadOnlyFingerprint.compare(
                        artifact.toFile(), "DS0101", "DS0101", originalHash, patchedHash);
                check(patchFingerprint.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,
                        "exact reviewed patch fingerprint");
                check(patchFingerprint.authorization == LicenseRecoverLicenseState.Authorization.UNVERIFIED,
                        "confirmed patch must not imply native license verification");
                check(patchFingerprint.visualStatus() == LicenseRecoverLicenseState.VisualStatus.PATCHED_UNVERIFIED,
                        "reviewed patch must be light green");
                check("reviewed-patch".equals(new String(java.nio.file.Files.readAllBytes(artifact), "UTF-8")),
                        "read-only comparison must never modify target bytes");
            } finally {
                java.nio.file.Files.deleteIfExists(artifact);
            }
        } catch (java.io.IOException ex) {
            throw new AssertionError("read-only fingerprint fixture failed", ex);
        }
        // Exercise the actual Java/.NET artifact resolver with independently
        // supplied fixture fingerprints. Production has no approved patch pair.
        try {
            java.nio.file.Path root = java.nio.file.Files.createTempDirectory("lr-scanner-");
            java.nio.file.Path bin = java.nio.file.Files.createDirectories(root.resolve("bin"));
            java.nio.file.Path artifact = bin.resolve("RegisterMain.bin");
            java.nio.file.Path companion = bin.resolve("Companion.bin");
            String originalHash = "bfc8234d0e0f6184640fec12438757ceac504df4ab8b232921b9bb506c981cb1";
            String patchedHash = "ba15b5958ced9058f5441c1b8929a72905e71c2898b82a9eab307b85f78cab07";
            LicenseRecoverReadOnlyPatchScanner.Reviewed fixture =
                    new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                            LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                            "bin/RegisterMain.bin", originalHash, patchedHash);
            try {
                java.nio.file.Files.write(artifact, "original-binary".getBytes("UTF-8"));
                check(LicenseRecoverReadOnlyPatchScanner.scan(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101")
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "production registry without independently reviewed pairs must remain gray");
                check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", fixture)
                        .codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,
                        "reviewed fixture resolves the exact original artifact");
                check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.DOTNET, "DS0101", fixture)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "Java fingerprint must not apply to .NET");
                check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0107", fixture)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "other version must not inherit Java fingerprint");
                java.nio.file.Files.write(artifact, "reviewed-patch".getBytes("UTF-8"));
                check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", fixture)
                        .codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,
                        "reviewed fixture resolves the exact patched artifact");
                check("reviewed-patch".equals(new String(
                        java.nio.file.Files.readAllBytes(artifact), "UTF-8")),
                        "fixture scanner must not write the target");
                // A reviewed patch set is atomic: one matching file must not
                // turn the whole application light green when another differs.
                LicenseRecoverReadOnlyPatchScanner.Reviewed companionFixture =
                        new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                                "bin/Companion.bin", originalHash, patchedHash);
                LicenseRecoverReadOnlyPatchScanner.Reviewed[] reviewedPair =
                        new LicenseRecoverReadOnlyPatchScanner.Reviewed[]{fixture, companionFixture};
                java.nio.file.Files.write(companion, "original-binary".getBytes("UTF-8"));
                check(LicenseRecoverReadOnlyPatchScanner.scanReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", reviewedPair)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "one patched artifact and one original must not confirm the full patch");
                java.nio.file.Files.write(companion, "reviewed-patch".getBytes("UTF-8"));
                check(LicenseRecoverReadOnlyPatchScanner.scanReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", reviewedPair)
                        .codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,
                        "all reviewed artifacts patched must confirm patch");
                java.nio.file.Files.write(artifact, "original-binary".getBytes("UTF-8"));
                java.nio.file.Files.write(companion, "original-binary".getBytes("UTF-8"));
                check(LicenseRecoverReadOnlyPatchScanner.scanReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", reviewedPair)
                        .codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,
                        "all reviewed artifacts original must confirm original code only");
                java.nio.file.Files.delete(companion);
                check(LicenseRecoverReadOnlyPatchScanner.scanReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", reviewedPair)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "missing companion artifact must not confirm original or patch");
                check(LicenseRecoverReadOnlyPatchScanner.scanReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                        new LicenseRecoverReadOnlyPatchScanner.Reviewed[]{fixture, fixture})
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "duplicate audited paths must not count as independent evidence");
                // A corrupt reviewed registry must never silently drop a
                // required entry and confirm a partial artifact set.
                for (LicenseRecoverReadOnlyPatchScanner.Reviewed malformedEntry :
                        new LicenseRecoverReadOnlyPatchScanner.Reviewed[]{
                                null,
                                new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                        LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, null,
                                        "bin/Companion.bin", originalHash, patchedHash),
                                new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                        null, "DS0101", "bin/Companion.bin", originalHash, patchedHash),
                                new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                        LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, " ",
                                        "bin/Companion.bin", originalHash, patchedHash)}) {
                    check(LicenseRecoverReadOnlyPatchScanner.scanReviewed(
                            root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                            new LicenseRecoverReadOnlyPatchScanner.Reviewed[]{fixture, malformedEntry})
                            .visualStatus() == LicenseRecoverLicenseState.VisualStatus.SCAN_ERROR,
                            "malformed reviewed registry entry must fail closed");
                }
                java.nio.file.Files.write(artifact, "reviewed-patch".getBytes("UTF-8"));
                // Different spellings of the same file are not two independent
                // audited artifacts. An alias must never turn the row light green.
                for (String alias : new String[]{"./bin/RegisterMain.bin",
                        "bin/../bin/RegisterMain.bin", "bin/./RegisterMain.bin"}) {
                    LicenseRecoverReadOnlyPatchScanner.Reviewed aliasFixture =
                            new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                    LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                                    alias, originalHash, patchedHash);
                    check(LicenseRecoverReadOnlyPatchScanner.scanReviewed(
                            root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                            new LicenseRecoverReadOnlyPatchScanner.Reviewed[]{fixture, aliasFixture})
                            .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                            "alias must not count as a second independent patch artifact: " + alias);
                }
                LicenseRecoverReadOnlyPatchScanner.Reviewed traversal =
                        new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                                "../escape.bin", originalHash, patchedHash);
                check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", traversal)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.SCAN_ERROR,
                        "reviewed path traversal must be rejected");
                // On Unix a path nested beneath a regular file is an I/O
                // error, not a missing artifact. Never silently downgrade it
                // to gray or claim the reviewed patch is valid.
                if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                        .contains("win")) {
                    java.nio.file.Path impossible = artifact.resolve("nested.bin");
                    check(LicenseRecoverReadOnlyFingerprint.compare(
                            impossible.toFile(), "DS0101", "DS0101", originalHash, patchedHash)
                            .visualStatus() == LicenseRecoverLicenseState.VisualStatus.SCAN_ERROR,
                            "fingerprint attribute I/O error must be red");
                    LicenseRecoverReadOnlyPatchScanner.Reviewed invalidParent =
                            new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                    LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                                    "bin/RegisterMain.bin/nested.bin", originalHash, patchedHash);
                    check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                            root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA,
                            "DS0101", invalidParent)
                            .visualStatus() == LicenseRecoverLicenseState.VisualStatus.SCAN_ERROR,
                            "reviewed target attribute I/O error must be red");
                }
                java.nio.file.Files.delete(artifact);
                check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                        root.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101", fixture)
                        .visualStatus() == LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,
                        "missing target must not be marked as original or patched");
            } finally {
                java.nio.file.Files.deleteIfExists(artifact);
                java.nio.file.Files.deleteIfExists(companion);
                java.nio.file.Files.deleteIfExists(bin);
                java.nio.file.Files.deleteIfExists(root);
            }
        } catch (java.io.IOException ex) {
            throw new AssertionError("reviewed scanner fixture failed", ex);
        }
        // A symlinked ancestor is not caught by checking the runtime root
        // itself. Even a byte-perfect reviewed patch must not be confirmed
        // through an alias that escapes the expected physical directory.
        try {
            java.nio.file.Path sandbox = java.nio.file.Files.createTempDirectory("lr-parent-link-");
            java.nio.file.Path real = sandbox.resolve("real");
            java.nio.file.Path app = real.resolve("app");
            java.nio.file.Path bin = app.resolve("bin");
            java.nio.file.Path artifact = bin.resolve("RegisterMain.bin");
            java.nio.file.Path link = sandbox.resolve("linked-parent");
            try {
                java.nio.file.Files.createDirectories(bin);
                java.nio.file.Files.write(artifact, "reviewed-patch".getBytes("UTF-8"));
                boolean linkSupported = true;
                try {
                    java.nio.file.Files.createSymbolicLink(link, real);
                } catch (UnsupportedOperationException | SecurityException
                        | java.nio.file.FileSystemException unavailable) {
                    // Windows environments without symlink privileges can skip
                    // this OS-specific fixture; Linux CI exercises it.
                    linkSupported = false;
                }
                if (linkSupported) {
                    java.nio.file.Path aliasedRoot = link.resolve("app");
                    check(!java.nio.file.Files.isSymbolicLink(aliasedRoot),
                            "fixture must use a symlinked ancestor, not the root itself");
                    LicenseRecoverReadOnlyPatchScanner.Reviewed reviewed =
                            new LicenseRecoverReadOnlyPatchScanner.Reviewed(
                                    LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA, "DS0101",
                                    "bin/RegisterMain.bin",
                                    "bfc8234d0e0f6184640fec12438757ceac504df4ab8b232921b9bb506c981cb1",
                                    "ba15b5958ced9058f5441c1b8929a72905e71c2898b82a9eab307b85f78cab07");
                    check(LicenseRecoverReadOnlyPatchScanner.compareReviewed(
                            aliasedRoot.toFile(), LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA,
                            "DS0101", reviewed).visualStatus()
                            == LicenseRecoverLicenseState.VisualStatus.SCAN_ERROR,
                            "symlinked runtime-root ancestor must not confirm a patch");
                }
            } finally {
                java.nio.file.Files.deleteIfExists(link);
                java.nio.file.Files.deleteIfExists(artifact);
                java.nio.file.Files.deleteIfExists(bin);
                java.nio.file.Files.deleteIfExists(app);
                java.nio.file.Files.deleteIfExists(real);
                java.nio.file.Files.deleteIfExists(sandbox);
            }
        } catch (java.io.IOException ex) {
            throw new AssertionError("symlinked ancestor fixture failed", ex);
        }
        LicenseRecoverLicenseState scanFailure = LicenseRecoverLicenseState.scanError("I/O failure");
        check(scanFailure.visualStatus() == LicenseRecoverLicenseState.VisualStatus.SCAN_ERROR,
                "inspection failure must be red, not invalid white");
        check(!scanFailure.selectedByDefault(), "scan error must not be auto-selected");
        boolean rejected = false;
        try { LicenseRecoverLicenseState.nativeVerification(true, ""); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "missing evidence must be rejected");
    }
}
