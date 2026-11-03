import java.io.File;

/**
 * The Java read-only scan entry point. Protocol/layout providers share the same
 * evidence model; a directory name or a partially recognized family is never a verdict.
 */
public final class LicenseRecoverReadOnlyJavaScanner {
    private LicenseRecoverReadOnlyJavaScanner() { }

    public static LicenseRecoverLicenseState scan(File appRoot) {
        return scanWith(appRoot,LicenseRecoverReadOnlyJavaVerifier::verify,
                LicenseRecoverReadOnlyJavaLocalVerifier::verify,
                LicenseRecoverReadOnlyJavaAlternateVerifier::verify);
    }

    interface Provider { LicenseRecoverLicenseState inspect(File root); }

    /** Synthetic orchestration seam; production providers and audit catalogs stay fixed. */
    static LicenseRecoverLicenseState scanWith(File appRoot,Provider gateProvider,
            Provider localProvider,Provider startupProvider) {
        if(appRoot==null)return LicenseRecoverLicenseState.unverified("缺少 Java 应用目录");
        if(gateProvider==null||localProvider==null||startupProvider==null)
            throw new IllegalArgumentException("all Java evidence providers are required");
        LicenseRecoverLicenseState gate=required(gateProvider.inspect(appRoot));
        if(finalEvidence(gate))return gate;
        LicenseRecoverLicenseState local=required(localProvider.inspect(appRoot));
        if(finalEvidence(local))return local;
        LicenseRecoverLicenseState startup=required(startupProvider.inspect(appRoot));
        if(finalEvidence(startup)||startup.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED)
            return startup;
        return local.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED
                ?local:unresolved(gate,local,startup);
    }

    private static LicenseRecoverLicenseState required(LicenseRecoverLicenseState state) {
        if(state==null)throw new IllegalArgumentException("Java evidence provider returned no state");
        return state;
    }
    private static boolean finalEvidence(LicenseRecoverLicenseState state) {
        return state.isScanError()||state.acceptance!=LicenseRecoverLicenseState.Acceptance.UNVERIFIED;
    }

    /** Preserve applicability reasons; this method cannot downgrade or manufacture a verdict. */
    static LicenseRecoverLicenseState unresolved(LicenseRecoverLicenseState gate,
            LicenseRecoverLicenseState local,LicenseRecoverLicenseState startup) {
        for(LicenseRecoverLicenseState state:new LicenseRecoverLicenseState[]{gate,local,startup}) {
            if(state==null||state.authorization!=LicenseRecoverLicenseState.Authorization.UNVERIFIED
                    ||state.codeIntegrity!=LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN
                    ||state.acceptance!=LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                    ||state.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED)
                throw new IllegalArgumentException("only unresolved applicability evidence may be combined");
        }
        return LicenseRecoverLicenseState.unverified("Java 只读规则适用性：静态门槛〔"+gate.evidence
                +"〕；本地谓词〔"+local.evidence+"〕；备用入口〔"+startup.evidence+"〕");
    }
}
