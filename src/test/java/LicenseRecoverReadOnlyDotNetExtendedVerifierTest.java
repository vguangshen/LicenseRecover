import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Synthetic legacy local evidence and VM boundaries; never executes a target assembly. */
public final class LicenseRecoverReadOnlyDotNetExtendedVerifierTest {
    private static int checks;
    private static final String MACHINE="1234567812345678";
    private static final LocalDate TODAY=LocalDate.of(2031,1,2);
    private static LicenseRecoverReadOnlyMachineIdentity.Snapshot identity() {
        return new LicenseRecoverReadOnlyMachineIdentity.Snapshot(Arrays.asList("ABCDEF0123456789"),
                Arrays.asList(new LicenseRecoverReadOnlyMachineIdentity.Disk("0","synthetic-zero"),
                        new LicenseRecoverReadOnlyMachineIdentity.Disk("123456789","synthetic-disk")),
                Collections.<String>emptyList(),"SYNTHETIC-HOST");
    }
    private static String plain(String machine,LocalDate date) {
        char[] payload=new char[70];Arrays.fill(payload,'x');
        System.arraycopy(machine.toCharArray(),0,payload,6,16);
        System.arraycopy(date.toString().toCharArray(),0,payload,30,10);
        return new String(payload);
    }
    private static String fixedFields(String machine,LocalDate date) {
        char[] p=plain(machine,date).toCharArray();
        System.arraycopy("0020".toCharArray(),0,p,24,4);
        System.arraycopy(TODAY.minusYears(1).toString().toCharArray(),0,p,42,10);
        p[54]='0';p[57]='0';p[58]='1';for(int i=61;i<p.length-2;i++)p[i]='0';
        return new String(p);
    }
    private static String encrypt(String text,String password)throws Exception {
        byte[] md5=MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8));
        StringBuilder sum=new StringBuilder();for(byte b:md5)sum.append(String.format(Locale.ROOT,"%02X",b&255));
        byte[] key=sum.substring(0,8).getBytes(StandardCharsets.US_ASCII);
        Cipher c=Cipher.getInstance("DES/CBC/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"DES"),new IvParameterSpec(key));
        StringBuilder out=new StringBuilder();for(byte b:c.doFinal(text.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02X",b&255));return out.toString();
    }
    private static byte[] config(String value,String mode) {
        return ("<ROOT><reg><regType>"+mode+"</regType><regName>"+value+"</regName></reg></ROOT>").getBytes(StandardCharsets.UTF_8);
    }
    private static void expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.Result r,
            LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus status,LicenseRecoverLicenseState.LocalCondition local,String label) {
        if(r.localStatus!=status||r.state.localCondition!=local)throw new AssertionError(label+": "+r.state.evidence);
        if(r.fullAuthorizationPathAudited||r.state.authorization!=LicenseRecoverLicenseState.Authorization.UNVERIFIED
                ||r.state.acceptance!=LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                ||r.state.codeIntegrity!=LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN)
            throw new AssertionError("local observation must not promote global/native/Patch status");
        if(r.state.visualStatus()!=LicenseRecoverLicenseState.VisualStatus.UNVERIFIED)throw new AssertionError("local failures remain gray");
        checks++;
    }
    private static LicenseRecoverReadOnlyDotNetExtendedVerifier.Result run(byte[] config,boolean legacy) {
        return LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config,legacy,identity(),TODAY);
    }
    public static void main(String[] args)throws Exception {
        String ciphertext=encrypt(plain(MACHINE,TODAY.plusDays(1)),"*MarketOK*");
        byte[] valid=config(ciphertext,"1"),original=valid.clone();
        expect(run(valid,true),LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_LOCAL_CONDITIONS_MATCHED,
                LicenseRecoverLicenseState.LocalCondition.SATISFIED,"local positive awaits website/mode proof");
        expect(run(config(encrypt(plain("OTHER_SYNTHETIC_",TODAY.plusYears(1)),"*MarketOK*"),"1"),true),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_MACHINE_MISMATCH,LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,"wrong machine is a local fact only");
        expect(run(config(encrypt(plain(MACHINE,TODAY),"*MarketOK*"),"1"),true),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_EXPIRED,LicenseRecoverLicenseState.LocalCondition.EXPIRED,"lowercase rejects on expiry day");
        expect(run(config(encrypt(plain(MACHINE,TODAY.minusDays(1)),"*MarketOK*"),"3"),true),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_EXPIRED,LicenseRecoverLicenseState.LocalCondition.EXPIRED,"local expired cannot reject earlier network path");
        expect(run(config("","3"),false),LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.NO_LOCAL_RECORD,
                LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"no local record does not prove all paths reject");
        expect(run(config("","1"),true),LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.NO_LOCAL_RECORD,
                LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"legacy empty local record");
        expect(run(config("BAD-CIPHER","1"),true),LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_FORMAT_UNSUPPORTED,
                LicenseRecoverLicenseState.LocalCondition.MALFORMED,"malformed ciphertext is not globally invalid");
        String modernCandidate=encrypt("123456{\"unreviewed\":true}654321","*ITMCYS01OK*");
        expect(run(config(modernCandidate,"1"),true),LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.MODERN_RECORD_CANDIDATE,
                LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"YS01 modern candidate must not be decoded only as a broken legacy record");
        expect(run(config(encrypt("123456{\"unreviewed\":}654321","*ITMCYS01OK*"),"1"),true),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.MODERN_RECORD_CANDIDATE,
                LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"outer JSON shape alone cannot assert valid JSON or authorization");
        expect(run(config(encrypt("123456UNREVIEWED654321","*ITMCYS01OK*"),"1"),true),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_FORMAT_UNSUPPORTED,
                LicenseRecoverLicenseState.LocalCondition.MALFORMED,"decryptable non-record is not a modern format candidate");
        if(run(config(modernCandidate,"1"),true).state.evidence.contains(modernCandidate)
                ||run(config(modernCandidate,"1"),true).state.evidence.contains("unreviewed"))
            throw new AssertionError("format diagnostics must not disclose ciphertext or plaintext");checks++;
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(valid,true,null,TODAY),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.IDENTITY_UNAVAILABLE,LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,"no identity cannot establish mismatch");
        String vmRecord=encrypt(plain(MACHINE,TODAY.plusYears(1)),"*ITMCYX0302OK*");
        LicenseRecoverReadOnlyDotNetCatalog.LocalFormat recovered=LicenseRecoverReadOnlyDotNetCatalog.LocalFormat.RECOVERED_FIXED_FIELDS;
        String recoveredRecord=encrypt(fixedFields(MACHINE,TODAY.plusYears(1)),"*ITMCYX0302OK*");
        LicenseRecoverReadOnlyDotNetExtendedVerifier.Result recoveredPositive=LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config(recoveredRecord,"3"),recovered,identity(),TODAY);
        expect(recoveredPositive,LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_LOCAL_CONDITIONS_MATCHED,
                LicenseRecoverLicenseState.LocalCondition.SATISFIED,"recovered fixed fields are local evidence despite unresolved native and dispatch paths");
        if(!recoveredPositive.state.evidence.contains("托管分支")||!recoveredPositive.state.evidence.contains("本扫描没有调用或写回"))
            throw new AssertionError("managed recovery boundary or native write gap lost");checks++;
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config(vmRecord,"1"),recovered,identity(),TODAY),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_FORMAT_UNSUPPORTED,LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                "machine/date success cannot hide unsupported numeric/date conversion fields");
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config(encrypt(plain("OTHER_SYNTHETIC_",TODAY.plusDays(1)),"*ITMCYX0302OK*"),"3"),recovered,identity(),TODAY),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_MACHINE_MISMATCH,LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,
                "network-first recovered machine mismatch is never whole invalidity");
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config(encrypt(plain(MACHINE,TODAY),"*ITMCYX0302OK*"),"1"),recovered,identity(),TODAY),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_EXPIRED,LicenseRecoverLicenseState.LocalCondition.EXPIRED,"recovered fixed record rejects equality date locally");
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config(vmRecord,"1"),recovered,null,TODAY),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.IDENTITY_UNAVAILABLE,LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,
                "recovered record cannot compare a missing independent identity");
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config("","3"),recovered,identity(),TODAY),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.NO_LOCAL_RECORD,LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                "recovered family can report an absent local record without evaluating native code");
        LicenseRecoverReadOnlyDotNetExtendedVerifier.Result vm=run(config(vmRecord,"3"),false);
        expect(vm,LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.UPPER_JSON_FORMAT_MISMATCH_LOWER_VM_PENDING,
                LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"upper JSON mismatch never implies lower VM rejection");
        if(!vm.dispatch.contains("network→local→HASP")||!vm.lowerLocal.equals("VM_NOT_EVALUATED"))throw new AssertionError("VM and dispatch evidence");checks++;
        String nestedJson=encrypt("123456{\"synthetic\":true}654321","*ITMCYX0302OK*");
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.diagnose(config(nestedJson,"1"),recovered,identity(),TODAY),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.MODERN_RECORD_CANDIDATE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                "same cipher key does not force JSON bytes into a fixed-record decoder");
        expect(run(config(nestedJson,"3"),false),LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LOWER_VM_PENDING,
                LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"unreviewed JSON contents never become accepted");
        expect(run("<!DOCTYPE ROOT [<!ENTITY x SYSTEM 'file:///SYNTHETIC_MISSING'>]><ROOT>&x;</ROOT>".getBytes("UTF-8"),true),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_FORMAT_UNSUPPORTED,LicenseRecoverLicenseState.LocalCondition.MALFORMED,"no XML external entity resolution");
        expect(run(new String(valid,StandardCharsets.UTF_8).replace("</reg>","<regName>duplicate</regName></reg>").getBytes("UTF-8"),true),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.LEGACY_FORMAT_UNSUPPORTED,LicenseRecoverLicenseState.LocalCondition.MALFORMED,"duplicate XML evidence rejected");
        if(!Arrays.equals(valid,original))throw new AssertionError("predicate modified input");checks++;
        expect(LicenseRecoverReadOnlyDotNetExtendedVerifier.retainIfStable(run(valid,true),false),
                LicenseRecoverReadOnlyDotNetExtendedVerifier.LocalStatus.INPUT_CHANGED,LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,"changed evidence revokes observations instead of claiming malformed");
        LicenseRecoverReadOnlyDotNetExtendedVerifier.Result unchanged=run(valid,true);
        if(LicenseRecoverReadOnlyDotNetExtendedVerifier.retainIfStable(unchanged,true)!=unchanged)throw new AssertionError("stable observation retained");checks++;
        if(run(valid,true).state.evidence.contains(MACHINE)||run(valid,true).state.evidence.contains(ciphertext))throw new AssertionError("sensitive evidence leakage");checks++;
        System.out.println("LicenseRecoverReadOnlyDotNetExtendedVerifierTest: "+checks+" checks passed");
    }
}
