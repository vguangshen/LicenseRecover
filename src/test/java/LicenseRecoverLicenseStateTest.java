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
