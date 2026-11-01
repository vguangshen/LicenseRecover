public final class LicenseRecoverLocalObservationTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        for(LicenseRecoverLicenseState.LocalCondition local:LicenseRecoverLicenseState.LocalCondition.values()) {
            if(local==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED)continue;
            LicenseRecoverLicenseState s=LicenseRecoverLicenseState.localObservation(local,"synthetic audited local fact");
            check(s.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED,"local result cannot prove global invalid");
            check(s.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"local result alone cannot turn green");
            check(s.visualStatus()==LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,"fallback unresolved remains gray");
            check(!"未验证".equals(s.display()),"specific local reason visible in primary status");
            check(LicenseRecoverLicenseState.visualStatus(s.display())==s.visualStatus(),"typed and table labels agree");
            LicenseRecoverLicenseState merged=LicenseRecoverLicenseState.combineLocalObservation(s,
                    LicenseRecoverLicenseState.codeComparison(false,"synthetic original comparison"));
            check(merged.localCondition==local&&merged.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,
                    "both local and original evidence remain traceable");
        }
        LicenseRecoverLicenseState pass=LicenseRecoverLicenseState.authorizationAcceptance("complete synthetic entry proof",
                LicenseRecoverLicenseState.LocalCondition.SATISFIED);
        check(pass.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,"complete proof may accept");
        check(LicenseRecoverLicenseState.combineAcceptance(pass,LicenseRecoverLicenseState.unverified("no code baseline"))
                .localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED,"accepted local dimension retained");
        boolean rejected=false;
        try{LicenseRecoverLicenseState.authorizationAcceptance("bad proof",LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"a failed local condition cannot be accepted");
        LicenseRecoverLicenseState fullReject=LicenseRecoverLicenseState.authorizationRejection("complete synthetic no-fallback rejection proof",
                LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH);
        check(fullReject.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_REJECTED,"complete entry rejection separate from local observations");
        check(fullReject.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED,"independent rejection is not literal native execution");
        check(fullReject.visualStatus()==LicenseRecoverLicenseState.VisualStatus.AUTHORIZATION_REJECTED,"complete rejection has white category");
        check(LicenseRecoverLicenseState.visualStatus(fullReject.display())==fullReject.visualStatus(),"rejection table label agrees");
        check(LicenseRecoverLicenseState.combineAcceptance(fullReject,LicenseRecoverLicenseState.unverified("no provenance baseline"))
                .acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_REJECTED,"complete rejection survives code evidence merge");
        check(LicenseRecoverLicenseState.combineLocalObservation(
                LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"no record"),
                LicenseRecoverLicenseState.scanError("read failed")).isScanError(),"scan errors retain priority");
        System.out.println("PASS: specific local observations never masquerade as global activation or rejection");
    }
}
