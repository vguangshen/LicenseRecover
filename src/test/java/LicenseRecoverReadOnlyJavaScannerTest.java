import java.io.File;
import java.util.Arrays;

/** Synthetic evidence only; the common Java entry must not promote or lose provider facts. */
public final class LicenseRecoverReadOnlyJavaScannerTest {
    private static int checks;
    private static final File ROOT=new File("synthetic-java-root");
    private static final LicenseRecoverLicenseState GATE=LicenseRecoverLicenseState.unverified("gate build not reviewed");
    private static final LicenseRecoverLicenseState LOCAL=LicenseRecoverLicenseState.unverified("local protocol not reviewed");
    private static final LicenseRecoverLicenseState STARTUP=LicenseRecoverLicenseState.unverified("startup resource changed");
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static LicenseRecoverReadOnlyJavaScanner.Provider provider(LicenseRecoverLicenseState state,int[] calls,int index) {
        return root->{check(root==ROOT,"one selected root must reach all providers");calls[index]++;return state;};
    }
    private static LicenseRecoverLicenseState run(LicenseRecoverLicenseState gate,LicenseRecoverLicenseState local,
            LicenseRecoverLicenseState startup,int[] calls) {
        return LicenseRecoverReadOnlyJavaScanner.scanWith(ROOT,provider(gate,calls,0),provider(local,calls,1),provider(startup,calls,2));
    }
    public static void main(String[] args) {
        LicenseRecoverLicenseState accepted=LicenseRecoverLicenseState.authorizationAcceptance("synthetic complete current condition");
        LicenseRecoverLicenseState rejected=LicenseRecoverLicenseState.authorizationRejection("synthetic complete entry rejection",LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH);
        LicenseRecoverLicenseState error=LicenseRecoverLicenseState.scanError("synthetic read error");
        for(LicenseRecoverLicenseState decisive:new LicenseRecoverLicenseState[]{accepted,rejected,error}) {
            int[] calls=new int[3];check(run(decisive,LOCAL,STARTUP,calls)==decisive,"first decisive proof must be retained");
            check(Arrays.equals(calls,new int[]{1,0,0}),"decisive gate must not execute other providers");
            calls=new int[3];check(run(GATE,decisive,STARTUP,calls)==decisive,"local complete result must be retained");
            check(Arrays.equals(calls,new int[]{1,1,0}),"decisive local proof must short-circuit layouts");
            calls=new int[3];check(run(GATE,LOCAL,decisive,calls)==decisive,"decisive layout result must be retained");
            check(Arrays.equals(calls,new int[]{1,1,1}),"all earlier applicability checks must run");
        }
        LicenseRecoverLicenseState mismatch=LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,"synthetic local mismatch; fallback unverified");
        LicenseRecoverLicenseState missing=LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"synthetic selected startup record missing");
        check(run(GATE,mismatch,missing,new int[3])==missing,"reviewed startup facts retain existing priority over generic local observations");
        check(run(GATE,mismatch,STARTUP,new int[3])==mismatch,"inapplicable layout cannot discard local evidence");
        check(run(GATE,mismatch,accepted,new int[3])==accepted,"later complete acceptance can settle an earlier local-only failure");
        LicenseRecoverLicenseState unknown=run(GATE,LOCAL,STARTUP,new int[3]);
        check(unknown.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                &&unknown.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED
                &&unknown.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"unresolved families remain unresolved");
        check(unknown.evidence.contains(GATE.evidence)&&unknown.evidence.contains(LOCAL.evidence)
                &&unknown.evidence.contains(STARTUP.evidence),"all applicability reasons survive");
        for(LicenseRecoverLicenseState stronger:new LicenseRecoverLicenseState[]{accepted,rejected,error,mismatch,
                LicenseRecoverLicenseState.nativeVerification(true,"synthetic native fact"),LicenseRecoverLicenseState.codeComparison(true,"synthetic pair")}) {
            try {LicenseRecoverReadOnlyJavaScanner.unresolved(GATE,stronger,STARTUP);throw new AssertionError("strong evidence silently downgraded");}
            catch(IllegalArgumentException expected){checks++;}
        }
        LicenseRecoverLicenseState noRoot=LicenseRecoverReadOnlyJavaScanner.scanWith(null,root->{throw new AssertionError("must not inspect absent root");},null,null);
        check(noRoot.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED&&noRoot.evidence.contains("缺少"),"absent root is an explicit applicability result");
        try {run(GATE,null,STARTUP,new int[3]);throw new AssertionError("missing provider result silently ignored");}
        catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS: unified Java pipeline preserves proof, fallback priority and unresolved reasons ("+checks+" checks)");
    }
}
