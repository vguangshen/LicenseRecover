/**
 * Read-only evidence model for batch authorization scans.
 *
 * An earlier LicenseRecover run, backup, config file or a network block is NOT
 * proof of either a valid license or a patched executable.
 */
public final class LicenseRecoverLicenseState {
    public enum Authorization { VERIFIED_VALID, VERIFIED_INVALID, UNVERIFIED, ERROR }
    public enum CodeIntegrity { CONFIRMED_PATCHED, CONFIRMED_ORIGINAL, UNKNOWN, ERROR }
    /** Current authorization-entry acceptance does not identify activation versus a Patch. */
    public enum Acceptance { VERIFIED_USABLE, UNVERIFIED }

    public final Authorization authorization;
    public final CodeIntegrity codeIntegrity;
    public final Acceptance acceptance;
    public final String evidence;
    public final String source;

    private LicenseRecoverLicenseState(Authorization authorization,
                                       CodeIntegrity codeIntegrity, String evidence, String source) {
        this(authorization, codeIntegrity, Acceptance.UNVERIFIED, evidence, source);
    }

    private LicenseRecoverLicenseState(Authorization authorization, CodeIntegrity codeIntegrity,
                                       Acceptance acceptance, String evidence, String source) {
        if (authorization == null || codeIntegrity == null || acceptance == null)
            throw new IllegalArgumentException("missing state");
        this.authorization = authorization;
        this.codeIntegrity = codeIntegrity;
        this.acceptance = acceptance;
        this.evidence = evidence == null ? "" : evidence;
        this.source = source == null ? "" : source;
    }

    public static LicenseRecoverLicenseState unverified(String reason) {
        return new LicenseRecoverLicenseState(Authorization.UNVERIFIED, CodeIntegrity.UNKNOWN, reason, "none");
    }

    /** Read-only inspection failed; a failure is not proof of invalid authorization. */
    public static LicenseRecoverLicenseState scanError(String reason) {
        return new LicenseRecoverLicenseState(Authorization.ERROR, CodeIntegrity.ERROR,
                reason == null ? "" : reason, "read-only-scan");
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

    /**
     * A separately audited predicate proves the current authorization entry accepts
     * the observed application state. It may accept a native activation or a Patch;
     * neither origin nor the usability of every business function is established.
     * Existence of configuration, a key, a backup or a previous action is insufficient.
     * The caller must bind its evidence to current files and all audited dependencies.
     */
    public static LicenseRecoverLicenseState authorizationAcceptance(String auditedEvidence) {
        if (auditedEvidence == null || auditedEvidence.trim().isEmpty())
            throw new IllegalArgumentException("current authorization-entry acceptance requires audited evidence");
        return new LicenseRecoverLicenseState(Authorization.UNVERIFIED, CodeIntegrity.UNKNOWN,
                Acceptance.VERIFIED_USABLE, auditedEvidence, "audited-current-authorization-entry");
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

    /** Merge an independently proven current entry result with code evidence, never native activation. */
    public static LicenseRecoverLicenseState combineAcceptance(LicenseRecoverLicenseState acceptedState,
                                                                LicenseRecoverLicenseState codeState) {
        if (acceptedState == null || codeState == null)
            throw new IllegalArgumentException("both evidence channels are required");
        String evidence = "acceptance: " + acceptedState.evidence + "; code: " + codeState.evidence;
        if (acceptedState.isScanError() || codeState.isScanError()) return scanError(evidence);
        if (acceptedState.acceptance != Acceptance.VERIFIED_USABLE
                || !"audited-current-authorization-entry".equals(acceptedState.source))
            throw new IllegalArgumentException("acceptance must come from the audited current-entry predicate");
        if (!"versioned-code-comparison".equals(codeState.source) && !"none".equals(codeState.source))
            throw new IllegalArgumentException("code evidence must come from versioned comparison or remain unknown");
        return new LicenseRecoverLicenseState(Authorization.UNVERIFIED, codeState.codeIntegrity,
                Acceptance.VERIFIED_USABLE, evidence, acceptedState.source + "+" + codeState.source);
    }

    public enum VisualStatus {
        NATIVE_VALID, PATCHED_UNVERIFIED, NATIVE_INVALID, SCAN_ERROR, UNVERIFIED, VERIFIED_USABLE
    }

    /** A failed evidence channel is an inspection error, not a license verdict. */
    public boolean isScanError() {
        return authorization == Authorization.ERROR || codeIntegrity == CodeIntegrity.ERROR;
    }

    /** A typed state is authoritative; the label is only for display. */
    public VisualStatus visualStatus() {
        if (isScanError()) return VisualStatus.SCAN_ERROR;
        if (acceptance == Acceptance.VERIFIED_USABLE) return VisualStatus.VERIFIED_USABLE;
        if (codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED) return VisualStatus.PATCHED_UNVERIFIED;
        if (authorization == Authorization.VERIFIED_VALID) return VisualStatus.NATIVE_VALID;
        if (authorization == Authorization.VERIFIED_INVALID) return VisualStatus.NATIVE_INVALID;
        return VisualStatus.UNVERIFIED;
    }

    /**
     * Compatibility with the table's textual status column. Match only labels
     * emitted by display(); an arbitrary prefix or diagnostic message is NOT
     * authorization evidence and must never acquire a green background.
     */
    public static VisualStatus visualStatus(String label) {
        if (label == null) return VisualStatus.UNVERIFIED;
        if ("检测失败".equals(label)) return VisualStatus.SCAN_ERROR;
        if ("已验证激活（代码已 Patch）".equals(label)
                || "已验证未激活（代码已 Patch）".equals(label)
                || "已确认 Patch（授权未验证）".equals(label))
            return VisualStatus.PATCHED_UNVERIFIED;
        if ("已验证激活".equals(label)) return VisualStatus.NATIVE_VALID;
        if ("授权检查通过（激活或 Patch）".equals(label)
                || "授权检查通过（代码已 Patch）".equals(label)) return VisualStatus.VERIFIED_USABLE;
        if ("已验证未激活".equals(label)) return VisualStatus.NATIVE_INVALID;
        return VisualStatus.UNVERIFIED;
    }

    public static boolean selectedByDefault(String label) {
        VisualStatus status = visualStatus(label);
        return status != VisualStatus.NATIVE_VALID
                && status != VisualStatus.PATCHED_UNVERIFIED
                && status != VisualStatus.VERIFIED_USABLE
                && status != VisualStatus.SCAN_ERROR;
    }

    public boolean selectedByDefault() {
        if (isScanError()) return false;
        if (authorization == Authorization.VERIFIED_VALID) return false;
        if (codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED) return false;
        if (acceptance == Acceptance.VERIFIED_USABLE) return false;
        return true;
    }

    public String display() {
        if (isScanError()) return "检测失败";
        if (acceptance == Acceptance.VERIFIED_USABLE)
            return codeIntegrity == CodeIntegrity.CONFIRMED_PATCHED
                    ? "授权检查通过（代码已 Patch）" : "授权检查通过（激活或 Patch）";
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
