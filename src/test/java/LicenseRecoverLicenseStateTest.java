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
        boolean rejected = false;
        try { LicenseRecoverLicenseState.nativeVerification(true, ""); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "missing evidence must be rejected");
    }
}
