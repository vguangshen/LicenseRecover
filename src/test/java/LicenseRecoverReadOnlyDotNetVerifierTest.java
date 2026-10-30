import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Synthetic current-state predicates; no vendor binaries, real licenses or hardware IDs. */
public final class LicenseRecoverReadOnlyDotNetVerifierTest {
    static int checks;
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    static final Instant NOW=Instant.parse("2030-06-15T08:30:00Z");
    static final String PRODUCT="itmcIEC", MACHINE="1234567812345678";
    static LicenseRecoverReadOnlyMachineIdentity.Snapshot hardware() {
        return new LicenseRecoverReadOnlyMachineIdentity.Snapshot(Arrays.asList("ABCDEF0123456789"),
                Arrays.asList(new LicenseRecoverReadOnlyMachineIdentity.Disk("0","synthetic-zero"),
                        new LicenseRecoverReadOnlyMachineIdentity.Disk("123456789","synthetic-disk")),
                Collections.<String>emptyList(),"SYNTHETIC-HOST");
    }
    static String payload() {
        return "{\"ProName\":\"itmcIEC\",\"RegID\":\""+MACHINE+"\",\"EndDate\":\"/Date(1907712000000+0800)/\","
                +"\"TotalTimes\":-1,\"RegStr\":\"DS0101,DS0109\",\"UserID\":\"SYNTHETIC_ONLY\"}";
    }
    static byte[] config(String payload,String mode,String soft) throws Exception {
        return ("<ROOT><SystemSoft><SoftVersionID>DS0109</SoftVersionID></SystemSoft><reg><regType>"+mode
                +"</regType><regName>"+encrypt("123456"+payload+"654321","*ITMC"+PRODUCT+"OK*")
                +"</regName><WebSerSoftNo>"+soft+"</WebSerSoftNo></reg></ROOT>").getBytes(StandardCharsets.UTF_8);
    }
    static String encrypt(String text,String password) throws Exception {
        byte[] md5=MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash=new StringBuilder();for(byte b:md5)hash.append(String.format(Locale.ROOT,"%02X",b&255));
        byte[] key=hash.substring(0,8).getBytes(StandardCharsets.US_ASCII);
        Cipher c=Cipher.getInstance("DES/CBC/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"DES"),new IvParameterSpec(key));
        StringBuilder out=new StringBuilder();for(byte b:c.doFinal(text.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02X",b&255));return out.toString();
    }
    static LicenseRecoverLicenseState run(byte[] xml,LicenseRecoverReadOnlyMachineIdentity.Snapshot identity) {
        return LicenseRecoverReadOnlyDotNetVerifier.predicate(xml,PRODUCT,identity,NOW,ZONE);
    }
    static void accepted(LicenseRecoverLicenseState s,String label) {
        if(s.acceptance!=LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE)throw new AssertionError(label+": "+s.evidence);
        if(s.authorization!=LicenseRecoverLicenseState.Authorization.UNVERIFIED||s.codeIntegrity!=LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN)
            throw new AssertionError("acceptance must not fabricate native/original/Patch provenance");checks++;
    }
    static void unknown(LicenseRecoverLicenseState s,String label) {
        if(s.acceptance!=LicenseRecoverLicenseState.Acceptance.UNVERIFIED||s.authorization!=LicenseRecoverLicenseState.Authorization.UNVERIFIED)
            throw new AssertionError(label);checks++;
    }
    public static void main(String[] args) throws Exception {
        byte[] valid=config(payload(),"1","");byte[] before=valid.clone();
        accepted(run(valid,hardware()),"machine-bound unexpired unlimited current-mode positive");
        if(!Arrays.equals(valid,before))throw new AssertionError("predicate changed bytes");checks++;
        unknown(run(config(payload().replace(MACHINE,"SYNTHETIC_OTHER"),"1",""),hardware()),"wrong machine stays unknown");
        unknown(run(config(payload().replace("1907712000000","1907625600000"),"1",""),hardware()),"expired local is not a global invalid verdict");
        unknown(run(config(payload().replace("/Date(1907712000000+0800)/","2030-06-15T00:00:00+08:00"),"1",""),hardware()),"unaudited ISO date not accepted");
        unknown(run(config(payload().replace("1907712000000","253402300800000"),"1",""),hardware()),"outside .NET DateTime bounds");
        unknown(run(config(payload().replace("+0800","+9999"),"1",""),hardware()),"unsupported offset fails closed");
        unknown(run(config(payload().replace("\"TotalTimes\":-1","\"TotalTimes\":9"),"1",""),hardware()),"finite counter remains unsupported");
        unknown(run(config(payload().replace("DS0101,DS0109","QT100101"),"1",""),hardware()),"current website mode must be covered");
        unknown(run(config(payload().replace("itmcIEC","OTHER_SYNTHETIC_PRODUCT"),"1",""),hardware()),"product must match reviewed key and website");
        unknown(run(config(payload(),"3",""),hardware()),"unreviewed earlier network branch stays unknown");
        unknown(run(config(payload()+"trailing","1",""),hardware()),"malformed JSON not accepted");
        unknown(run(config(payload().replace("\"TotalTimes\":-1","\"TotalTimes\":-1,\"TotalTimes\":-1"),"1",""),hardware()),"duplicate fields rejected");
        unknown(run(config(payload().replace("\"TotalTimes\":-1","\"totaltimes\":-1"),"1",""),hardware()),"case-aliased critical fields rejected");
        unknown(run(config(payload().replace("\"TotalTimes\":-1","\"TotalTimes\":{}"),"1",""),hardware()),"unsupported nested field rejected");
        unknown(run("<ROOT><reg><regType>1</regType><regName>FF</regName></reg></ROOT>".getBytes("UTF-8"),hardware()),"file presence is insufficient");
        unknown(run(config(payload(),"1",""),null),"missing independent identity");
        LicenseRecoverReadOnlyMachineIdentity.Snapshot incomplete=new LicenseRecoverReadOnlyMachineIdentity.Snapshot(
                hardware().cpuIds,hardware().disks,hardware().boards,hardware().hostname,false,"synthetic incomplete");
        unknown(run(valid,incomplete),"incomplete independent identity cannot promote");
        LicenseRecoverReadOnlyMachineIdentity.Snapshot unicodeIdentity=new LicenseRecoverReadOnlyMachineIdentity.Snapshot(
                hardware().cpuIds,hardware().disks,Arrays.asList("\u2003SYNTHETIC"),hardware().hostname);
        unknown(run(valid,unicodeIdentity),"unicode trimming differences are outside the identity audit");
        String soft=encrypt(MACHINE,hardware().cpuIds.get(0));
        unknown(run(config(payload(),"1",soft),hardware()),"WMI success does not prove the target WMIC override branch");
        LicenseRecoverReadOnlyMachineIdentity.Snapshot multiple=new LicenseRecoverReadOnlyMachineIdentity.Snapshot(
                Arrays.asList("OTHER_CPU",hardware().cpuIds.get(0)),hardware().disks,hardware().boards,hardware().hostname);
        unknown(run(config(payload(),"1",soft),multiple),"ambiguous WMIC software-number binding remains unknown");
        String xml=new String(valid,StandardCharsets.UTF_8);
        unknown(run(xml.replace("</reg>","<regName>duplicate</regName></reg>").getBytes("UTF-8"),hardware()),"duplicate XML critical value fails closed");
        unknown(run(xml.replace("<reg>","<!--synthetic comment before reg--><reg>").getBytes("UTF-8"),hardware()),"vendor XML cast semantics fail closed");
        unknown(run(("<!DOCTYPE ROOT [<!ENTITY x SYSTEM 'file:///NONEXISTENT_SYNTHETIC'>]>"+xml).getBytes("UTF-8"),hardware()),"external entities never resolved");
        accepted(run(config(payload().replace("DS0101,DS0109","ds0109"),"1",""),hardware()),"reviewed case-insensitive website mode constraint");
        if(run(valid,hardware()).evidence.contains(MACHINE)||run(valid,hardware()).evidence.contains("SYNTHETIC_ONLY"))throw new AssertionError("sensitive values in evidence");checks++;
        System.out.println("LicenseRecoverReadOnlyDotNetVerifierTest: "+checks+" checks passed");
    }
}
