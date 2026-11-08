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
        wholeBeanBoundaries();
        byte[] valid=config(payload(),"1","");byte[] before=valid.clone();
        accepted(run(valid,hardware()),"machine-bound unexpired unlimited current-mode positive");
        LicenseRecoverReadOnlyDotNetCatalog.UpperBinding scoped=new LicenseRecoverReadOnlyDotNetCatalog.UpperBinding(
                "synthetic-site","synthetic-registration",PRODUCT,"synthetic-crypto.dll","synthetic-crypto","synthetic-json","synthetic-log",false,
                "synthetic protected result constructor remains unaudited");
        LicenseRecoverLicenseState localOnly=LicenseRecoverReadOnlyDotNetVerifier.applyAuditScope(run(valid,hardware()),scoped);
        unknown(localOnly,"modern local success must not bypass an opaque website constructor");
        if(localOnly.localCondition!=LicenseRecoverLicenseState.LocalCondition.SATISFIED
                ||!localOnly.evidence.contains(scoped.auditGap)||localOnly.codeIntegrity!=LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN)
            throw new AssertionError("local-only audit scope was discarded");checks++;
        LicenseRecoverLicenseState expired=run(config(payload().replace("1907712000000","1907625600000"),"1",""),hardware());
        if(LicenseRecoverReadOnlyDotNetVerifier.applyAuditScope(expired,scoped)!=expired)
            throw new AssertionError("audit scope must preserve failed local conditions");checks++;
        String ysPayload=payload().replace(PRODUCT,"YS01").replace("DS0101,DS0109","YX0102");
        byte[] ys=productConfig(ysPayload,"YS01","YX0102","1");
        accepted(LicenseRecoverReadOnlyDotNetVerifier.predicate(ys,"YS01",hardware(),NOW,ZONE),"YS01 reuses modern engine with its own product/key/mode binding");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(ys,PRODUCT,hardware(),NOW,ZONE),"shared family does not permit the DS product key for YS01");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(productConfig(ysPayload.replace(MACHINE,"OTHER_SYNTHETIC"),"YS01","YX0102","1"),"YS01",hardware(),NOW,ZONE),"YS01 still enforces independent machine binding");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(productConfig(ysPayload,"YS01","OTHER_MODE","1"),"YS01",hardware(),NOW,ZONE),"YS01 still enforces current-mode coverage");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(productConfig(ysPayload,"YS01","YX0102","3"),"YS01",hardware(),NOW,ZONE),"earlier network/dog dispatch is not promoted by a readable modern record");
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
        LicenseRecoverLicenseState networkFirst=run(config(payload(),"3",""),hardware());
        if(networkFirst.localCondition!=LicenseRecoverLicenseState.LocalCondition.SATISFIED
                ||!networkFirst.evidence.contains("先行联网/硬件狗"))throw new AssertionError("dispatch must not hide independently verified local record conditions");checks++;
        LicenseRecoverLicenseState dogFirst=run(config(payload(),"2",""),hardware());
        unknown(dogFirst,"HASP-first cannot use local success as whole-entry acceptance");
        if(dogFirst.localCondition!=LicenseRecoverLicenseState.LocalCondition.SATISFIED)throw new AssertionError("HASP-first local condition was discarded");checks++;
        LicenseRecoverLicenseState wrongModeMachine=run(config(payload().replace(MACHINE,"OTHER_SYNTHETIC"),"3",""),hardware());
        unknown(wrongModeMachine,"network-first local machine mismatch is not global invalidity");
        if(wrongModeMachine.localCondition!=LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH)throw new AssertionError("dispatch hid specific local mismatch");checks++;
        unknown(run(config(payload()+"trailing","1",""),hardware()),"malformed JSON not accepted");
        unknown(run(config(payload().replace("\"TotalTimes\":-1","\"TotalTimes\":-1,\"TotalTimes\":-1"),"1",""),hardware()),"duplicate fields rejected");
        unknown(run(config(payload().replace("\"TotalTimes\":-1","\"totaltimes\":-1"),"1",""),hardware()),"case-aliased critical fields rejected");
        unknown(run(config(payload().replace("\"TotalTimes\":-1","\"TotalTimes\":{}"),"1",""),hardware()),"unsupported nested field rejected");
        unknown(run("<ROOT><reg><regType>1</regType><regName>FF</regName></reg></ROOT>".getBytes("UTF-8"),hardware()),"file presence is insufficient");
        unknown(run(config(payload(),"1",""),null),"missing independent identity");
        LicenseRecoverLicenseState noRecordWithoutIdentity=run("<ROOT><SystemSoft><SoftVersionID>DS0109</SoftVersionID></SystemSoft><reg><regType>3</regType></reg></ROOT>".getBytes("UTF-8"),null);
        unknown(noRecordWithoutIdentity,"absent local record cannot establish global invalidity");
        if(noRecordWithoutIdentity.localCondition!=LicenseRecoverLicenseState.LocalCondition.NO_RECORD)
            throw new AssertionError("missing hardware metadata must not hide an independently absent local record");checks++;
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
        String cpuToken="SYNTHETIC_WMIC_FIRST_CPU",override="PRIVATE_OVERRIDE_MACHINE";
        String boundSoft=encrypt(override,cpuToken);
        LicenseRecoverReadOnlyWmicCpuIdentity.Identity auditedCpu=new LicenseRecoverReadOnlyWmicCpuIdentity.Identity(
                true,cpuToken,"synthetic audited WMIC framing");
        byte[] overrideConfig=config(payload().replace(MACHINE,override),"1",boundSoft);
        accepted(LicenseRecoverReadOnlyDotNetVerifier.predicate(overrideConfig,PRODUCT,multiple,NOW,ZONE,auditedCpu),
                "audited WMIC token selects the override independently of the WMI last row");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(overrideConfig,PRODUCT,hardware(),NOW,ZONE,
                new LicenseRecoverReadOnlyWmicCpuIdentity.Identity(false,cpuToken,"synthetic incomplete")),
                "incomplete WMIC evidence never selects an override");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(overrideConfig,PRODUCT,hardware(),NOW,ZONE,
                new LicenseRecoverReadOnlyWmicCpuIdentity.Identity(true,"WRONG_WMIC_TOKEN","synthetic")),
                "wrong override binding cannot create acceptance");
        accepted(LicenseRecoverReadOnlyDotNetVerifier.predicate(config(payload(),"1","NOT_HEX"),PRODUCT,hardware(),NOW,ZONE,auditedCpu),
                "audited failed override decryption follows the native normal-machine fallback");
        accepted(LicenseRecoverReadOnlyDotNetVerifier.predicate(config(payload(),"1",encrypt("   ",cpuToken)),PRODUCT,hardware(),NOW,ZONE,auditedCpu),
                "empty override follows the native fallback");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(config(payload().replace(MACHINE,override).replace("\"TotalTimes\":-1","\"TotalTimes\":2"),"1",boundSoft),PRODUCT,hardware(),NOW,ZONE,auditedCpu),
                "a valid override does not bypass finite counters");
        unknown(LicenseRecoverReadOnlyDotNetVerifier.predicate(config(payload().replace(MACHINE,override).replace("DS0101,DS0109","OTHER"),"1",boundSoft),PRODUCT,hardware(),NOW,ZONE,auditedCpu),
                "a valid override does not bypass product coverage");
        LicenseRecoverLicenseState privateEvidence=LicenseRecoverReadOnlyDotNetVerifier.predicate(overrideConfig,PRODUCT,hardware(),NOW,ZONE,auditedCpu);
        if(privateEvidence.evidence.contains(override)||privateEvidence.evidence.contains(cpuToken)||privateEvidence.evidence.contains(boundSoft))
            throw new AssertionError("override and CPU values leaked in diagnostics");checks++;
        String xml=new String(valid,StandardCharsets.UTF_8);
        unknown(run(xml.replace("</reg>","<regName>duplicate</regName></reg>").getBytes("UTF-8"),hardware()),"duplicate XML critical value fails closed");
        unknown(run(xml.replace("<reg>","<!--synthetic comment before reg--><reg>").getBytes("UTF-8"),hardware()),"vendor XML cast semantics fail closed");
        unknown(run(("<!DOCTYPE ROOT [<!ENTITY x SYSTEM 'file:///NONEXISTENT_SYNTHETIC'>]>"+xml).getBytes("UTF-8"),hardware()),"external entities never resolved");
        accepted(run(config(payload().replace("DS0101,DS0109","ds0109"),"1",""),hardware()),"reviewed case-insensitive website mode constraint");
        if(run(valid,hardware()).evidence.contains(MACHINE)||run(valid,hardware()).evidence.contains("SYNTHETIC_ONLY"))throw new AssertionError("sensitive values in evidence");checks++;
        LicenseRecoverLicenseState mismatched=run(config(payload().replace(MACHINE,"SYNTHETIC_OTHER"),"1",""),hardware());
        if(mismatched.localCondition!=LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH)
            throw new AssertionError("synthetic mismatch fixture must produce a scoped local observation");checks++;
        if(LicenseRecoverReadOnlyDotNetVerifier.retainIfStable(mismatched,true)!=mismatched)
            throw new AssertionError("stable local evidence must remain unchanged");checks++;
        LicenseRecoverLicenseState revokedLocal=LicenseRecoverReadOnlyDotNetVerifier.retainIfStable(mismatched,false);
        unknown(revokedLocal,"changed audited bytes revoke a previously observed local mismatch");
        if(revokedLocal.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED
                ||!revokedLocal.evidence.contains("发生变化"))
            throw new AssertionError("stale local observation was not fully revoked");checks++;
        LicenseRecoverLicenseState revokedPositive=LicenseRecoverReadOnlyDotNetVerifier.retainIfStable(run(valid,hardware()),false);
        unknown(revokedPositive,"changed audited bytes revoke a previously accepted entry");
        if(revokedPositive.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED)
            throw new AssertionError("stale positive observation was not fully revoked");checks++;
        System.out.println("LicenseRecoverReadOnlyDotNetVerifierTest: "+checks+" checks passed");
    }
    private static void wholeBeanBoundaries()throws Exception {
        String base=payload();
        String[] malformed={"\"UserTimes\":\"not-int\"","\"UserTimes\":2147483648","\"MaxCon\":\"not-int\"",
                "\"CountDay\":true","\"ClassNum\":null","\"Net\":\"not-bool\"","\"BeginDate\":\"not-date\"",
                "\"BeginDate\":null","\"beginDate\":\"not-long\"","\"endDate\":false"};
        for(String field:malformed) {
            LicenseRecoverLicenseState state=run(config(base.replace("{","{"+field+","),"1",""),hardware());
            unknown(state,"invalid unused bean properties cannot pass an unlimited record");
            if(state.localCondition!=LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED)
                throw new AssertionError("whole-bean type rejection must remain scoped");checks++;
        }
        unknown(run(config(base.replace("\"UserID\":\"SYNTHETIC_ONLY\"","\"UserID\":42"),"1",""),hardware()),
                "numeric coercion for optional strings is outside the canonical audit");
        String full=base.replace("{","{\"ClassNum\":1,\"BeginDate\":\"/Date(1907625600000+0800)/\",\"beginDate\":0,\"endDate\":0,"
                +"\"UserTimes\":0,\"Net\":false,\"MaxCon\":20,\"CountDay\":10,");
        accepted(run(config(full,"1",""),hardware()),"all fourteen canonical reviewed properties remain supported");
        accepted(run(config(full.replace("\"UserID\":\"SYNTHETIC_ONLY\"","\"UserID\":null"),"1",""),hardware()),
                "canonical null optional string used by current records remains supported");
        accepted(run(config(base.replace("{","{\"beginDate\":0,\"endDate\":0,"),"1",""),hardware()),
                "lowercase zero defaults coexist with the distinct DateTime property");
        for(String field:new String[]{"\"endDate\":-1","\"endDate\":9223372036854775807","\"endDate\":1907712000000","\"beginDate\":-1"}) {
            LicenseRecoverLicenseState state=run(config(base.replace("{","{"+field+","),"1",""),hardware());
            unknown(state,"nonzero lazy date overrides cannot borrow the uppercase date pass");
            if(state.localCondition!=LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED||!state.evidence.contains("历史时区"))
                throw new AssertionError("lazy date gap must be explicit rather than a native-invalid verdict");checks++;
        }
        for(String key:new String[]{"regID","RegId","endDATE","NET","userTimes","@type","$ref","UnknownField"}) {
            LicenseRecoverLicenseState state=run(config(base.replace("{","{\""+key+"\":\"SYNTHETIC_UNREVIEWED_VALUE\","),"1",""),hardware());
            unknown(state,"unknown names and special keys cannot override canonical checks");
            if(state.evidence.contains("SYNTHETIC_UNREVIEWED_VALUE"))throw new AssertionError("field values leaked");checks++;
        }
        LicenseRecoverLicenseState invalidBeforeMachine=run(config(base.replace(MACHINE,"OTHER_SYNTHETIC").replace("{","{\"UserTimes\":\"not-int\","),"1",""),hardware());
        if(invalidBeforeMachine.localCondition!=LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED)
            throw new AssertionError("whole-bean parse failures precede machine comparison");checks++;
    }
    static byte[] productConfig(String payload,String product,String version,String mode)throws Exception {
        return ("<ROOT><SystemSoft><SoftVersionID>"+version+"</SoftVersionID></SystemSoft><reg><regType>"+mode
                +"</regType><regName>"+encrypt("123456"+payload+"654321","*ITMC"+product+"OK*")
                +"</regName><WebSerSoftNo></WebSerSoftNo></reg></ROOT>").getBytes(StandardCharsets.UTF_8);
    }
}
