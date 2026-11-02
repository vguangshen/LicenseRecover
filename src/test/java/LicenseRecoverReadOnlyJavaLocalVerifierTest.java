import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Synthetic fixtures only: no installed vendor code, real keys or target writes. */
public final class LicenseRecoverReadOnlyJavaLocalVerifierTest {
    private static int checks;
    private static final long NOW=1800000000000L;
    private static final String ID="SYNTHETIC0000001", PRODUCT="TEST", MODE="TEST01";
    private static final LicenseRecoverReadOnlyJavaMachineIdentity.Identity MACHINE=
            new LicenseRecoverReadOnlyJavaMachineIdentity.Identity(true,ID,"synthetic","fixture");
    public static void main(String[] args)throws Exception {
        String payload=payload(ID,NOW+86400000L,-1,0,MODE);
        byte[] valid=config(payload,"1");
        condition(valid,MACHINE,LicenseRecoverLicenseState.LocalCondition.SATISFIED);
        condition(config(payload,"3"),MACHINE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED);
        condition("<root/>".getBytes(StandardCharsets.UTF_8),MACHINE,LicenseRecoverLicenseState.LocalCondition.NO_RECORD);
        condition("<root><reg><regType>1</regType><regName/></reg></root>".getBytes(StandardCharsets.UTF_8),MACHINE,LicenseRecoverLicenseState.LocalCondition.NO_RECORD);
        condition(valid,null,LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE);
        condition(config(payload("OTHER",NOW+86400000L,-1,0,MODE),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH);
        condition(config(payload(ID,NOW-86400000L*3,-1,0,MODE),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.EXPIRED);
        condition(config(payload(ID,NOW-1,-1,0,MODE),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED);
        LicenseRecoverReadOnlyJavaLocalVerifier.LocalCheck finite=local(config(payload(ID,NOW+86400000L,10,1,MODE),"1"),MACHINE);
        require(finite.writesRequired&&!finite.nativeLocalPass,"finite-counter write path cannot become a read-only success");
        condition(config(payload(ID,NOW+86400000L,1,1,MODE),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.QUOTA_EXCEEDED);
        condition(config(payload(ID,NOW+86400000L,Integer.MAX_VALUE,(long)Integer.MAX_VALUE,MODE),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED);
        condition(config(payload.replace("\"classNum\":1,",""),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED);
        condition(config(payload.replace("\"regStr\":\"TEST01\"","\"regStr\":null"),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED);
        condition(config(payload(ID,NOW+86400000L,-1,0,"OTHER"),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH);
        condition(config(payload(ID,NOW+86400000L,-1,0,"PREFIX_TEST01_SUFFIX"),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.SATISFIED);
        condition("<root><reg><regType>1</regType><regName>zz</regName></reg></root>".getBytes(StandardCharsets.UTF_8),MACHINE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED);
        condition("<root><reg><regType>1</regType><regName>0000000000000000</regName></reg></root>".getBytes(StandardCharsets.UTF_8),MACHINE,LicenseRecoverLicenseState.LocalCondition.MALFORMED);
        condition(config(payload.replace("\"regID\":", "\"regID\":\"duplicate\",\"regID\":"),"1"),MACHINE,LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED);
        condition(("<!DOCTYPE r SYSTEM 'file:///this-must-never-be-read'><r/>").getBytes(StandardCharsets.UTF_8),MACHINE,LicenseRecoverLicenseState.LocalCondition.MALFORMED);
        LicenseRecoverReadOnlyJavaLocalVerifier.LocalCheck mismatch=local(config(payload(ID,NOW+86400000L,-1,0,"OTHER"),"1"),MACHINE);
        require(LicenseRecoverReadOnlyJavaLocalVerifier.decision(mismatch,"synthetic audit",false).acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,
                "local product failure cannot reject unclosed fallback");
        LicenseRecoverLicenseState rejected=LicenseRecoverReadOnlyJavaLocalVerifier.decision(mismatch,"synthetic complete entry audit",true);
        require(rejected.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_REJECTED
                &&rejected.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED,"complete entry rejection is separate from target-native execution");
        require(LicenseRecoverReadOnlyJavaLocalVerifier.decision(finite,"synthetic audit",true).acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,
                "write-dependent path cannot be promoted by closed-product flag");
        require(LicenseRecoverReadOnlyJavaLocalVerifier.decision(local(config(payload(ID,NOW-86400000L*3,-1,0,MODE),"1"),MACHINE),
                "synthetic audit",true).acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"expiry cannot bypass network/HASP fallback proof");
        endToEnd();
        System.out.println("PASS: Java local predicate, complete entry scope, bootstrap changes, damaged inputs and read-only boundaries ("+checks+" checks)");
    }
    private static void endToEnd()throws Exception {
        Path root=Files.createTempDirectory("lr-j1-fixture-");
        Path classes=root.resolve("WEB-INF/classes"),lib=root.resolve("WEB-INF/lib");
        Files.createDirectories(classes.resolve("com/common/sys/configuration"));Files.createDirectories(lib);Files.createDirectories(root.resolve("data"));
        Files.write(classes.resolve("com/common/sys/configuration/SysParamInit.class"),new byte[]{1,2,3});
        Files.write(lib.resolve("ITMCReg.jar"),new byte[]{4,5,6});
        byte[] properties="global.system.VersionID=TEST01\n".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("systemConfig.yml"),properties);
        Files.write(root.resolve("data/config.xml"),"<root/>".getBytes(StandardCharsets.UTF_8));
        byte[] record=config(payload(ID,System.currentTimeMillis()+86400000L*5,-1,0,MODE),"1");Files.write(root.resolve("config.xml"),record);
        LicenseRecoverReadOnlyJavaLocalVerifier.Profile profile=LicenseRecoverReadOnlyJavaLocalVerifier.fixture(root.toFile(),PRODUCT,"config.xml","data/config.xml",false,true);
        require(evaluate(root,profile,null).acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,"complete synthetic input accepted");
        Files.write(classes.resolve("Business.class"),new byte[]{7,8,9});
        LicenseRecoverReadOnlyJavaLocalVerifier.Profile nextBuild=LicenseRecoverReadOnlyJavaLocalVerifier.fixture(root.toFile(),PRODUCT,"config.xml","data/config.xml",false,true);
        require(LicenseRecoverReadOnlyJavaLocalVerifier.evaluateReviewed(root.toFile(),MACHINE,
                new LicenseRecoverReadOnlyJavaLocalVerifier.Profile[]{profile,nextBuild},null).state.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,
                "an unchanged entry class must not let the first complete-build mismatch hide the next reviewed build");
        require(LicenseRecoverReadOnlyJavaLocalVerifier.evaluateReviewed(root.toFile(),MACHINE,
                new LicenseRecoverReadOnlyJavaLocalVerifier.Profile[]{nextBuild,profile},null).state.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,
                "candidate table order cannot determine which full build is accepted");
        requireUnknown(LicenseRecoverReadOnlyJavaLocalVerifier.evaluateReviewed(root.toFile(),MACHINE,
                new LicenseRecoverReadOnlyJavaLocalVerifier.Profile[]{nextBuild,nextBuild},null).state,
                "ambiguous complete-build audit records cannot choose favorable parameters");
        Files.delete(classes.resolve("Business.class"));
        Files.write(root.resolve("WEB-INF/web.xml"),"<web-app/>".getBytes(StandardCharsets.UTF_8));
        requireUnknown(evaluate(root,profile,null),"new web.xml cannot reuse absent bootstrap proof");Files.delete(root.resolve("WEB-INF/web.xml"));
        Files.write(classes.resolve("new-spring.xml"),"<beans/>".getBytes(StandardCharsets.UTF_8));
        requireUnknown(evaluate(root,profile,null),"new classpath resource cannot reuse old proof");Files.delete(classes.resolve("new-spring.xml"));
        Files.createDirectories(root.resolve("META-INF"));Files.write(root.resolve("META-INF/context.xml"),"<Context/>".getBytes(StandardCharsets.UTF_8));
        requireUnknown(evaluate(root,profile,null),"new context cannot reuse absent META-INF proof");Files.delete(root.resolve("META-INF/context.xml"));Files.delete(root.resolve("META-INF"));
        Files.write(root.resolve("systemConfig.yml"),"global.system.VersionID=TEST01\nspring.config.location=elsewhere\n".getBytes(StandardCharsets.UTF_8));
        requireUnknown(evaluate(root,profile,null),"unreviewed bootstrap property cannot retain green");Files.write(root.resolve("systemConfig.yml"),properties);
        requireUnknown(evaluate(root,profile,()->write(root.resolve("config.xml"),new byte[]{0})),"registration changed during proof cannot retain green");Files.write(root.resolve("config.xml"),record);
        requireUnknown(evaluate(root,profile,()->write(classes.resolve("late.xml"),new byte[]{1})),"late startup resource cannot retain green");Files.delete(classes.resolve("late.xml"));
        requireUnknown(evaluate(root,profile,()->write(lib.resolve("ITMCReg.jar"),new byte[]{7,8,9})),"same-size code change cannot retain green");Files.write(lib.resolve("ITMCReg.jar"),new byte[]{4,5,6});
        Files.delete(root.resolve("config.xml"));
        require(evaluate(root,profile,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"only actual registration missing reports no record");
        Files.write(root.resolve("config.xml"),record);Files.delete(root.resolve("data/config.xml"));
        require(evaluate(root,profile,null).localCondition!=LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"metadata missing is not missing authorization");
        require(LicenseRecoverReadOnlyJavaLocalVerifier.verify(root.toFile()).acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,
                "synthetic profile never enters production registry");
    }
    private static LicenseRecoverLicenseState evaluate(Path root,LicenseRecoverReadOnlyJavaLocalVerifier.Profile profile,Runnable recheck) {
        return LicenseRecoverReadOnlyJavaLocalVerifier.evaluateReviewed(root.toFile(),MACHINE,new LicenseRecoverReadOnlyJavaLocalVerifier.Profile[]{profile},recheck).state;
    }
    private static void write(Path path,byte[] bytes){try{Files.write(path,bytes);}catch(Exception e){throw new RuntimeException(e);}}
    private static void requireUnknown(LicenseRecoverLicenseState s,String reason){require(s.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,reason);}
    private static String payload(String id,long end,long total,long used,String modes){return "{\"regID\":\""+id+"\",\"endDate\":"+end+",\"totalTimes\":"+total+",\"userTimes\":"+used+",\"classNum\":1,\"maxCon\":10,\"regStr\":\""+modes+"\"}";}
    private static byte[] config(String payload,String mode)throws Exception {
        String hash=hex(MessageDigest.getInstance("MD5").digest(("*ITMC"+PRODUCT+"OK*").getBytes(StandardCharsets.US_ASCII)));
        byte[] key=hash.substring(0,8).toUpperCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        Cipher cipher=Cipher.getInstance("DES/CBC/PKCS5Padding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"DES"),new IvParameterSpec(key));
        return ("<root><reg><regType>"+mode+"</regType><regName>"+hex(cipher.doFinal(("ABCDEF"+payload+"UVWXYZ").getBytes(StandardCharsets.US_ASCII)))+"</regName></reg></root>").getBytes(StandardCharsets.UTF_8);
    }
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return b.toString();}
    private static LicenseRecoverReadOnlyJavaLocalVerifier.LocalCheck local(byte[] bytes,LicenseRecoverReadOnlyJavaMachineIdentity.Identity machine){return LicenseRecoverReadOnlyJavaLocalVerifier.checkLocal(bytes,PRODUCT,MODE,machine,NOW);}
    private static void condition(byte[] bytes,LicenseRecoverReadOnlyJavaMachineIdentity.Identity machine,LicenseRecoverLicenseState.LocalCondition condition){
        require(local(bytes,machine).condition==condition,"unexpected local condition: expected "+condition+", got "+local(bytes,machine).condition);
    }
    private static void require(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
}
