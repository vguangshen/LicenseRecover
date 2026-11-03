import java.io.File;

/**
 * The Java read-only scan entry point. Protocol/layout providers share the same
 * evidence model; a directory name or a partially recognized family is never a verdict.
 */
public final class LicenseRecoverReadOnlyJavaScanner {
    private LicenseRecoverReadOnlyJavaScanner() { }

    public static final class Result {
        public final LicenseRecoverLicenseState state;
        public final LicenseRecoverReadOnlyCodeEvidence codeEvidence;
        private Result(LicenseRecoverLicenseState state,LicenseRecoverReadOnlyCodeEvidence evidence) {
            this.state=state;this.codeEvidence=evidence;
        }
    }

    public static Result inspect(File appRoot) {
        return inspectWith(appRoot,LicenseRecoverReadOnlyJavaVerifier::verify,
                LicenseRecoverReadOnlyJavaLocalVerifier::verify,
                LicenseRecoverReadOnlyJavaAlternateVerifier::verify,
                LicenseRecoverReadOnlyJavaCodeEvidence::inspect);
    }

    public static LicenseRecoverLicenseState scan(File appRoot) {
        return inspect(appRoot).state;
    }

    interface Provider { LicenseRecoverLicenseState inspect(File root); }
    interface ComponentProvider { LicenseRecoverReadOnlyCodeEvidence inspect(File root); }

    /** Production uses only the fixed providers above; fixtures can distinguish their proof channels. */
    static Result inspectWith(File appRoot,Provider gateProvider,Provider localProvider,
            Provider startupProvider,ComponentProvider componentProvider) {
        if(componentProvider==null)throw new IllegalArgumentException("Java component provider is required");
        // Observe components BEFORE the gate. The gate's complete double-read
        // proof must see any code change during that observation; no component
        // file is read after static-gate acceptance. This avoids an extra full
        // DS24 snapshot and a verify-to-receipt gap.
        LicenseRecoverReadOnlyCodeEvidence component=componentProvider.inspect(appRoot);
        if(component==null)throw new IllegalArgumentException("Java component provider returned no evidence");
        Decision decision=decide(appRoot,gateProvider,localProvider,startupProvider);
        if(decision.gateAccepted)component=LicenseRecoverReadOnlyCodeEvidence.staticBypass(component,
                decision.state.evidence,"LicenseRecoverReadOnlyJavaVerifier.verify; docs/JAVA_STATIC_ACCEPTANCE_PROOF.md");
        return new Result(decision.state,component);
    }

    /** Synthetic orchestration seam; production providers and audit catalogs stay fixed. */
    static LicenseRecoverLicenseState scanWith(File appRoot,Provider gateProvider,
            Provider localProvider,Provider startupProvider) {
        return decide(appRoot,gateProvider,localProvider,startupProvider).state;
    }

    private static final class Decision {
        final LicenseRecoverLicenseState state;final boolean gateAccepted;
        Decision(LicenseRecoverLicenseState state,boolean gateAccepted){this.state=state;this.gateAccepted=gateAccepted;}
    }
    private static Decision decide(File appRoot,Provider gateProvider,
            Provider localProvider,Provider startupProvider) {
        if(appRoot==null)return new Decision(LicenseRecoverLicenseState.unverified("缺少 Java 应用目录"),false);
        if(gateProvider==null||localProvider==null||startupProvider==null)
            throw new IllegalArgumentException("all Java evidence providers are required");
        LicenseRecoverLicenseState gate=required(gateProvider.inspect(appRoot));
        if(finalEvidence(gate))return new Decision(gate,
                gate.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE);
        LicenseRecoverLicenseState local=required(localProvider.inspect(appRoot));
        if(finalEvidence(local))return new Decision(local,false);
        LicenseRecoverLicenseState startup=required(startupProvider.inspect(appRoot));
        if(finalEvidence(startup)||startup.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED)
            return new Decision(startup,false);
        return new Decision(local.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED
                ?local:unresolved(gate,local,startup),false);
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
