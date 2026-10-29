/**
 * Read-only evidence model for batch authorization scans.
 *
 * An earlier LicenseRecover run, backup, config file or a network block is NOT
 * proof of either a valid license or a patched executable.
 */
public final class LicenseRecoverLicenseState {
    public enum Authorization { VERIFIED_VALID, VERIFIED_INVALID, UNVERIFIED, ERROR }
    public enum CodeIntegrity { CONFIRMED_PATCHED, CONFIRMED_ORIGINAL, UNKNOWN, ERROR }

    public final Authorization authorization;
    public final CodeIntegrity codeIntegrity;
    public final String evidence;
    public final String source;

    private LicenseRecoverLicenseState(Authorization authorization,
                                       CodeIntegrity codeIntegrity, String evidence, String source) {
        if (authorization == null || codeIntegrity == null) throw new IllegalArgumentException("missing state");
        this.authorization = authorization;
        this.codeIntegrity = codeIntegrity;
        this.evidence = evidence == null ? "" : evidence;
        this.source = source == null ? "" : source;
    }

    public static LicenseRecoverLicenseState unverified(String reason) {
        return new LicenseRecoverLicenseState(Authorization.UNVERIFIED, CodeIntegrity.UNKNOWN, reason, "none");
    }

    /**
     * Only a separately audited, current, target-native verification may call
     * this factory. No record of previous actions is accepted as evidence.
     */
    public static LicenseRecoverLicenseState nativeVerification(boolean valid, String evidence) {
        if (evidence == null || evidence.trim().isEmpty())
            throw new IllegalArgumentException("native verification requires evidence");
        return new LicenseRecoverLicenseState(valid ? Authorization.VERIFIED_VALID : Authorization.VERIFIED_INVALID,
                CodeIntegrity.UNKNOWN, evidence, "target-native");
    }

    /** Patch status requires a known original version and verified bytecode evidence. */
    public static LicenseRecoverLicenseState codeComparison(boolean patched, String evidence) {
        if (evidence == null || evidence.trim().isEmpty())
            throw new IllegalArgumentException("code comparison requires evidence");
        return new LicenseRecoverLicenseState(Authorization.UNVERIFIED,
                patched ? CodeIntegrity.CONFIRMED_PATCHED : CodeIntegrity.CONFIRMED_ORIGINAL,
                evidence, "versioned-code-comparison");
    }

    /**
     * Merge two independently established facts without promoting a patch to
     * a valid license. Evidence must remain traceable to its verifier.
     */
    public static LicenseRecoverLicenseState combine(LicenseRecoverLicenseState nativeState,
                                                      LicenseRecoverLicenseState codeState) {
        if (nativeState == null || codeState == null)
            throw new IllegalArgumentException("both evidence channels are required");
        if (!"target-native".equals(nativeState.source))
            throw new IllegalArgumentException("native evidence must come from target verifier");
        if (!"versioned-code-comparison".equals(codeState.source))
            throw new IllegalArgumentException("patch evidence must come from versioned comparison");
        return new LicenseRecoverLicenseState(nativeState.authorization, codeState.codeIntegrity,
                "native: " + nativeState.evidence + "; code: " + codeState.evidence,
                "target-native+versioned-code-comparison");
    }

    public enum VisualStatus {
        NATIVE_VALID, PATCHED_UNVERIFIED, NATIVE_INVALID, SCAN_ERROR, UNVERIFIED
    }

    /** A typed state is authoritative; the label is only for display. */
    public VisualStatus visualStatus() {
        if (authorization == Authorization.ERROR || codeIntegrity == CodeIntegrity.ERROR)
            return VisualStatus.SCAN_ERROR;
        if (codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED) return VisualStatus.PATCHED_UNVERIFIED;
        if (authorization == Authorization.VERIFIED_VALID) return VisualStatus.NATIVE_VALID;
        if (authorization == Authorization.VERIFIED_INVALID) return VisualStatus.NATIVE_INVALID;
        return VisualStatus.UNVERIFIED;
    }

    /** Compatibility for the existing table; unrecognized labels stay unverified. */
    public static VisualStatus visualStatus(String label) {
        if (label == null) return VisualStatus.UNVERIFIED;
        if (label.startsWith("检测失败")) return VisualStatus.SCAN_ERROR;
        if (label.equals("已验证激活（代码已 Patch）")
                || label.equals("已验证未激活（代码已 Patch）"))
            return VisualStatus.PATCHED_UNVERIFIED;
        if (label.startsWith("已验证激活")) return VisualStatus.NATIVE_VALID;
        if (label.startsWith("已确认 Patch")) return VisualStatus.PATCHED_UNVERIFIED;
        if (label.startsWith("已验证未激活")) return VisualStatus.NATIVE_INVALID;
        return VisualStatus.UNVERIFIED;
    }

    public static boolean selectedByDefault(String label) {
        VisualStatus status = visualStatus(label);
        return status != VisualStatus.NATIVE_VALID
                && status != VisualStatus.PATCHED_UNVERIFIED
                && status != VisualStatus.SCAN_ERROR;
    }

    public boolean selectedByDefault() {
        if (authorization == Authorization.ERROR || codeIntegrity == CodeIntegrity.ERROR) return false;
        if (authorization == Authorization.VERIFIED_VALID) return false;
        if (codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED) return false;
        return true;
    }

    public String display() {
        if (authorization == Authorization.ERROR || codeIntegrity == CodeIntegrity.ERROR) return "检测失败";
        if (authorization == Authorization.VERIFIED_VALID && codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED)
            return "已验证激活（代码已 Patch）";
        if (authorization == Authorization.VERIFIED_VALID) return "已验证激活";
        if (authorization == Authorization.VERIFIED_INVALID && codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED)
            return "已验证未激活（代码已 Patch）";
        if (authorization == Authorization.VERIFIED_INVALID) return "已验证未激活";
        if (codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED) return "已确认 Patch（授权未验证）";
        if (codeIntegrity == CodeIntegrity.CONFIRMED_ORIGINAL) return "原始代码（授权未验证）";
        return "未验证";
    }
}
