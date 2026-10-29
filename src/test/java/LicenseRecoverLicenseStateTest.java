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
        boolean rejected = false;
        try { LicenseRecoverLicenseState.nativeVerification(true, ""); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "missing evidence must be rejected");
    }
}
