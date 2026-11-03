import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;

/** Synthetic receipts and a synthetic local result; never loads a vendor class or real license. */
public final class LicenseRecoverReadOnlyJavaAlternateVerifierTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void put(Path root,String path,String text)throws Exception {
        Path p=root.resolve(path);Files.createDirectories(p.getParent());Files.write(p,text.getBytes(StandardCharsets.UTF_8));
    }
    private static LicenseRecoverReadOnlyJavaAlternateVerifier.Profile profile(Path root,
            LicenseRecoverReadOnlyJavaAlternateVerifier.Layout layout)throws Exception {
        return LicenseRecoverReadOnlyJavaAlternateVerifier.snapshot(root).fixture("synthetic-alternate-entry","Gate.class",layout);
    }
    private static LicenseRecoverLicenseState inspect(Path root,LicenseRecoverReadOnlyJavaAlternateVerifier.Profile profile,
            LicenseRecoverReadOnlyJavaAlternateVerifier.LocalEvaluator engine,Runnable hook) {
        return LicenseRecoverReadOnlyJavaAlternateVerifier.verifyReviewed(root.toFile(),
                new LicenseRecoverReadOnlyJavaAlternateVerifier.Profile[]{profile},engine,1L,
                new LicenseRecoverReadOnlyJavaMachineIdentity.Identity(true,"synthetic-machine","synthetic","synthetic identity"),hook);
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("lr-alternate-synthetic-");
        try {
            Path classes=Files.createDirectories(root.resolve("WEB-INF/classes"));
            Files.createDirectories(root.resolve("WEB-INF/lib"));
            Files.write(classes.resolve("Gate.class"),new byte[]{(byte)0xca,(byte)0xfe,(byte)0xba,(byte)0xbe,0,0,0,52});
            put(root,"WEB-INF/lib/support.jar","synthetic-code-receipt");
            put(root,"WEB-INF/classes/application.properties","spring.datasource.password=PRIVATE_DB_SECRET\nspring.main.sources=fixture.Gate\n");
            LicenseRecoverReadOnlyJavaAlternateVerifier.Profile missing=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.DEFAULT_LIBRARY);
            put(root,"WEB-INF/classes/build-marker.txt","synthetic second build with the same entry class");
            LicenseRecoverReadOnlyJavaAlternateVerifier.Profile second=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.DEFAULT_LIBRARY);
            check(LicenseRecoverReadOnlyJavaAlternateVerifier.verifyAnchored(root.toFile(),
                    new LicenseRecoverReadOnlyJavaAlternateVerifier.Profile[]{missing,second})
                    .localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                    "a first anchor match with a different full build cannot hide a later reviewed build");
            Files.delete(root.resolve("WEB-INF/classes/build-marker.txt"));
            LicenseRecoverLicenseState noRecord=inspect(root,missing,null,null);
            check(noRecord.localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"missing effective library record is explicit local evidence");
            check(noRecord.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED
                    &&noRecord.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"missing record cannot exclude network or hardware fallback");
            put(root,"Register.xml","<root>PRIVATE_MACHINE_LOG_SENTINEL</root>");
            check(inspect(root,missing,null,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                    "a machine log named Register.xml cannot substitute for the effective authorization record");
            check(!inspect(root,missing,null,null).evidence.contains("PRIVATE_MACHINE_LOG_SENTINEL"),
                    "machine log contents never appear in evidence");
            put(root,"WEB-INF/classes/config.xml","<root><reg><regType>1</regType><regName>UNUSED_SECRET</regName></reg></root>");
            check(inspect(root,missing,null,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                    "a populated wrong-directory record must not stand in for the effective file");
            put(root,"WEB-INF/lib/config.xml","<root><reg><regType>1</regType><regName>REAL_LOCATION_SECRET</regName></reg></root>");
            check(inspect(root,missing,null,null).localCondition==LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "record presence alone never creates acceptance");
            Files.delete(root.resolve("WEB-INF/lib/config.xml"));
            check(inspect(root,missing,null,()->{try{put(root,"WEB-INF/lib/config.xml","<root/>");}catch(Exception e){throw new RuntimeException(e);}})
                    .localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,"a new record between passes invalidates a stale missing-record diagnosis");
            Files.delete(root.resolve("WEB-INF/lib/config.xml"));
            put(root,"config.xml","<root><reg><regType>1</regType><regName>PRIVATE_LICENSE_SENTINEL</regName></reg></root>");
            put(root,"WEB-INF/classes/config.xml","<root><SystemSoft><SoftVersionID>PRODUCT</SoftVersionID><regInfo>MODE-A,MODE-B</regInfo></SystemSoft></root>");
            put(root,"systemConfig.yml","global.system.VersionID=UNLICENSED-VERSION\nglobal.system.MySQLPassword=PRIVATE_DB_SECRET\n");
            LicenseRecoverReadOnlyJavaAlternateVerifier.Profile any=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.XMT_ANY_MODE);
            LicenseRecoverReadOnlyJavaAlternateVerifier.LocalEvaluator engine=(bytes,product,mode,machine,now)->{
                check("PRODUCT".equals(product),"constructor product comes from actual startup XML");
                return new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(
                        "MODE-B".equals(mode)?LicenseRecoverLicenseState.LocalCondition.SATISFIED:LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH,
                        "synthetic current native conditions",true,"MODE-B".equals(mode),true,false);
            };
            LicenseRecoverLicenseState accepted=inspect(root,any,engine,null);
            check(accepted.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,"any configured product mode can accept even if the display version is absent");
            check(accepted.localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED,"accepted local predicate stays independently visible");
            check(accepted.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED
                    &&accepted.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"acceptance does not fabricate native activation or Patch provenance");
            check(!accepted.evidence.contains("PRIVATE_")&&!accepted.evidence.contains("synthetic-machine"),"no license, database password or machine value in result");
            final int[] clockReads={0};
            LicenseRecoverLicenseState expiredDuringRecheck=LicenseRecoverReadOnlyJavaAlternateVerifier.verifyReviewed(root.toFile(),
                    new LicenseRecoverReadOnlyJavaAlternateVerifier.Profile[]{any},
                    (a,b,c,d,time)->new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(
                            time<2L?LicenseRecoverLicenseState.LocalCondition.SATISFIED:LicenseRecoverLicenseState.LocalCondition.EXPIRED,
                            "synthetic expiry boundary",time<2L,time<2L,true,false),
                    ()->clockReads[0]++==0?1L:3L,
                    new LicenseRecoverReadOnlyJavaMachineIdentity.Identity(true,"synthetic-machine","synthetic","synthetic identity"),null);
            check(clockReads[0]==2,"production-style clock is observed before and after the complete receipt recheck");
            check(expiredDuringRecheck.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                    &&expiredDuringRecheck.localCondition==LicenseRecoverLicenseState.LocalCondition.EXPIRED,
                    "crossing an expiry boundary revokes the earlier positive rather than keeping a stale green");
            String before=LicenseRecoverReadOnlyJavaAlternateVerifier.inputSnapshot(root.toFile());
            check(inspect(root,any,(a,b,c,d,e)->new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(
                    LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,"machine mismatch",false,false,true,false),null)
                    .localCondition==LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,"machine mismatch remains a local diagnostic");
            check(inspect(root,any,(a,b,c,d,e)->new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(
                    LicenseRecoverLicenseState.LocalCondition.QUOTA_EXCEEDED,"finite quota",true,true,true,true),null)
                    .acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"a result requiring counter consumption cannot accept");
            check(inspect(root,any,engine,()->{try{put(root,"systemConfig.yml","global.system.VersionID=CHANGED\n");}catch(Exception e){throw new RuntimeException(e);}})
                    .acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"configuration mutation invalidates a previously positive result");
            check(!before.equals(LicenseRecoverReadOnlyJavaAlternateVerifier.inputSnapshot(root.toFile())),"input receipt changes with consumed configuration");
            put(root,"systemConfig.yml","global.system.VersionID=UNLICENSED-VERSION\nspring.main.sources=other.Unknown\n");
            check(inspect(root,any,engine,null).acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,
                    "unreviewed root bootstrap properties cannot inherit an authorization proof");
            put(root,"systemConfig.yml","global.system.VersionID=UNLICENSED-VERSION\n");
            put(root,"WEB-INF/classes/application.properties","spring.datasource.password=OTHER_DB_SECRET\nspring.main.sources=fixture.Gate\n");
            check(inspect(root,any,engine,null).acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,
                    "irrelevant datasource credential changes do not falsely invalidate the authorization entry");
            put(root,"WEB-INF/classes/application.properties","spring.main.sources=other.Unknown\n");
            check(inspect(root,any,engine,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,
                    "changed bootstrap wiring cannot inherit a reviewed proof");
            put(root,"WEB-INF/classes/application.properties","spring.datasource.password=OTHER_DB_SECRET\nspring.main.sources=fixture.Gate\n");
            check(inspect(root,any,engine,null).acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,
                    "restored bootstrap wiring remains applicable before testing a new container resource");
            put(root,"WEB-INF/web.xml","<web-app/>");
            check(inspect(root,any,engine,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,
                    "new container wiring invalidates a reviewed absent-web.xml layout");
            Files.delete(root.resolve("WEB-INF/web.xml"));
            put(root,"META-INF/context.xml","<Context/>");
            check(inspect(root,any,engine,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,
                    "new container context invalidates the reviewed loading environment");
            Files.delete(root.resolve("META-INF/context.xml"));
            put(root,"config.xml","<root><reg><regType>1</regType><regType>3</regType><regName>x</regName></reg></root>");
            check(inspect(root,any,engine,null).localCondition==LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                    "duplicate record fields cannot select an ambiguous branch");
            put(root,"config.xml","<!DOCTYPE x [<!ENTITY leak SYSTEM 'file:///not-read'>]><root/>");
            check(inspect(root,any,engine,null).localCondition==LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                    "external entities are rejected without opening resources");
            put(root,"config.xml","<root><reg><regType>1</regType><regName>x</regName></reg></root>");
            put(root,"WEB-INF/classes/config.xml","<root><outer><SystemSoft><SoftVersionID>PRODUCT</SoftVersionID><regInfo>MODE-B</regInfo></SystemSoft></outer></root>");
            check(inspect(root,any,engine,null).acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,
                    "a nested SystemSoft section ignored by native startup cannot create acceptance");
            put(root,"WEB-INF/classes/Extra.class","broken");
            check(inspect(root,any,engine,null).isScanError(),"actual malformed application class is a scan error");
            Files.delete(root.resolve("WEB-INF/classes/Extra.class"));
            check(LicenseRecoverReadOnlyJavaAlternateVerifier.verify(root.toFile()).localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,
                    "production does not import synthetic receipts or select by directory name");
            System.out.println("LicenseRecoverReadOnlyJavaAlternateVerifierTest: "+checks+" checks passed");
        }finally {
            try(java.util.stream.Stream<Path> stream=Files.walk(root)){
                stream.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});
            }
        }
    }
}
