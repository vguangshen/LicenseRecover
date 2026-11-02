import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.Base64;

/** No registry writes or real license values; framing, privacy and source-only semantics. */
public final class LicenseRecoverReadOnlyDotNetRegistrySourceTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    private static LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot parse(String s) {
        return LicenseRecoverReadOnlyDotNetRegistrySource.parse(s.getBytes(StandardCharsets.US_ASCII));
    }
    private static String encode(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    public static void main(String[] args) {
        LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot absent=parse("LRREG1\r\nABSENT\r\nEND\r\n");
        check(absent.complete&&!absent.present&&!absent.hasOverride(),"explicit absent source is not an activation verdict");
        String text="LRREG1\nPRESENT\nSoftNo\t"+encode("PRIVATE_OVERRIDE_SENTINEL")+"\nLocalSoftNo\t~\nEND\n";
        LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot override=parse(text);
        check(override.complete&&override.hasOverride()&&!override.hasLocalPayload(),"override alone is not a local license");
        check(!override.observation().contains("PRIVATE_")&&!override.toString().contains("PRIVATE_"),"no values in diagnostics");
        LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot candidate=parse(text.replace("LocalSoftNo\t~","LocalSoftNo\t"+encode("PRIVATE_PAYLOAD_SENTINEL")));
        check(candidate.hasLocalPayload()&&candidate.hasOverride(),"candidate can be observed without accepting it");
        check(!candidate.toString().contains("PRIVATE_"),"candidate bytes never enter log");
        check(override.same(parse(text))&&!override.same(candidate),"real second observation detects changed payload");
        check(!parse("LRREG1-UNSUPPORTED\n").complete,"unknown or expanded registry types cannot be used");
        check(!parse(text.replace("SoftNo\t","OTHER\t")).complete,"wrong field names fail closed");
        check(!parse(text+"EXTRA\n").complete,"trailing output cannot be accepted");
        check(!parse(text.replace("\nEND", "\nSoftNo\t~\nEND")).complete,"duplicate fields rejected");
        check(!parse(text.replace(encode("PRIVATE_OVERRIDE_SENTINEL"),"@@")).complete,"invalid base64 rejected");
        check(!parse(text.replace(encode("PRIVATE_OVERRIDE_SENTINEL"),encode("x\0y"))).complete,"NUL rejected");
        check(!LicenseRecoverReadOnlyDotNetRegistrySource.parse(new byte[262145]).complete,"total output bound");
        check(parse(text.replace(encode("PRIVATE_OVERRIDE_SENTINEL"),"")).complete,"empty REG_SZ distinct from absent field");
        LicenseRecoverReadOnlyDotNetExtendedVerifier.Result local=LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(
                "<ROOT><reg><regType>3</regType><regName/></reg></ROOT>".getBytes(StandardCharsets.UTF_8),false,null,java.time.LocalDate.of(2031,1,1));
        LicenseRecoverReadOnlyDotNetExtendedVerifier.Result decorated=LicenseRecoverReadOnlyDotNetExtendedVerifier.withRegistryContext(local,candidate,candidate);
        check(decorated.state.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                &&decorated.state.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED
                &&decorated.state.localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"registry presence cannot override missing root evidence or fabricate acceptance");
        check(!decorated.state.evidence.contains("PRIVATE_"),"combined diagnostic private");
        check(LicenseRecoverReadOnlyDotNetExtendedVerifier.withRegistryContext(local,override,candidate).state.evidence.contains("未完成一致"),"changed context is withdrawn");
        check(!LicenseRecoverReadOnlyDotNetExtendedVerifier.withRegistryContext(local,override,candidate).state.evidence.contains("发现本机"),"stale context presence is not retained");
        try {
            Path temporary=Files.createTempFile("licenserecover-registry-readonly-", ".tmp");
            try {
                check(LicenseRecoverReadOnlyDotNetRegistrySource.unlinkedRegularFile(temporary),"existing unlinked regular file passes path check");
                check(!LicenseRecoverReadOnlyDotNetRegistrySource.unlinkedRegularFile(temporary.resolve("missing")),"nonexistent nested reader fails closed");
            } finally { Files.deleteIfExists(temporary); }
        } catch(IOException e) { throw new AssertionError("synthetic reader path fixture",e); }
        System.out.println("PASS: optional registry source privacy, framing, changes and no fabricated authorization ("+checks+" checks)");
    }
}
