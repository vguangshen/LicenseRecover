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
        boolean rejected = false;
        try { LicenseRecoverLicenseState.nativeVerification(true, ""); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "missing evidence must be rejected");
    }
}
