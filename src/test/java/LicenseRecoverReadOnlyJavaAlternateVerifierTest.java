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
    private static LicenseRecoverReadOnlyJavaAlternateVerifier.Check local(boolean core,boolean scope) {
        return new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(scope?LicenseRecoverLicenseState.LocalCondition.SATISFIED
                :LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH,"synthetic local facts",core,scope,true,false);
    }
    private static void localDiagnostics(Path root)throws Exception {
        put(root,"systemConfig.yml","global.system.VersionID=MODE\n");
        put(root,"WEB-INF/lib/config.xml","<root><reg><regType>1</regType><regName>PRIVATE_RECORD</regName></reg></root>");
        put(root,"WEB-INF/classes/config.xml","<root><SystemSoft><SoftVersionID>FIRST</SoftVersionID><FinalProduct>LAST</FinalProduct></SystemSoft></root>");
        LicenseRecoverReadOnlyJavaAlternateVerifier.Profile library=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.LIBRARY_LOCAL_DIAGNOSTIC);
        LicenseRecoverLicenseState lib=inspect(root,library,(a,product,mode,d,e)->{
            check("LAST".equals(product)&&"MODE".equals(mode),"legacy product is the last direct SystemSoft child, not a guessed field");
            return local(true,true);
        },null);
        check(lib.localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED
                &&lib.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"complete local facts remain diagnostic while whole-entry proof is incomplete");
        check(lib.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED
                &&lib.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"local facts cannot become native activation or Patch provenance");
        check(!lib.evidence.contains("PRIVATE_RECORD")&&!lib.evidence.contains("synthetic-machine"),"new diagnostic paths redact real values");
        check(!root.getFileName().toString().contains("QT100101")&&lib.localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED,
                "an unrelated directory name receives only the content-bound local observation");
        put(root,"WEB-INF/classes/config.xml.preoneclick.bak","sealed backup A");
        check(inspect(root,library,null,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,
                "a newly added config backup is still a changed sealed startup resource");
        LicenseRecoverReadOnlyJavaAlternateVerifier.Profile backup=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.LIBRARY_LOCAL_DIAGNOSTIC);
        put(root,"WEB-INF/classes/config.xml.preoneclick.bak","sealed backup B");
        check(inspect(root,backup,null,null).localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,
                "a same-name changed backup cannot inherit an audited resource variant");
        Files.delete(root.resolve("WEB-INF/classes/config.xml.preoneclick.bak"));
        put(root,"data/config.xml","<root><SystemSoft><SoftVersionID>P1,P2</SoftVersionID><regInfo>A,B</regInfo></SystemSoft></root>");
        LicenseRecoverReadOnlyJavaAlternateVerifier.Profile qt=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.QT30_LOCAL_DIAGNOSTIC);
        final int[] laterProducts={0};
        LicenseRecoverLicenseState firstCore=inspect(root,qt,(a,product,mode,d,e)->{
            if("P2".equals(product))laterProducts[0]++;
            return local(true,"P2".equals(product));
        },null);
        check(laterProducts[0]==0&&firstCore.localCondition==LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH,
                "scope failure after the first constructor's core success cannot jump to a later constructor");
        check(inspect(root,qt,(a,product,b,c,d)->"P1".equals(product)
                ?new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"requires finite write",false,false,true,true)
                :local(true,true),null).localCondition==LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                "an unresolved finite-write candidate cannot be replaced by a later candidate's pass");
        check(inspect(root,qt,(a,product,b,c,d)->"P1".equals(product)
                ?new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,"first local mismatch; other fallbacks unknown",false,false,true,false)
                :local(true,true),null).localCondition==LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,
                "local mismatch cannot prove the earlier constructor's network/HASP failed and select a later candidate");
        LicenseRecoverLicenseState qtScope=inspect(root,qt,(a,product,mode,d,e)->{
            check("P1".equals(product),"QT constructor comes from data SoftVersionID, not classes metadata or the directory");
            return local(true,"B".equals(mode));
        },null);
        check(qtScope.localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED
                &&qtScope.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"QT local facts use any configured product scope without whole-entry promotion");
        put(root,"WEB-INF/classes/config.xml","<root><SystemSoft><SoftVersionID>XMT-PRODUCT</SoftVersionID><regInfo>ALTERNATE-MODE</regInfo></SystemSoft></root>");
        LicenseRecoverReadOnlyJavaAlternateVerifier.Profile xmt=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.XMT_LOCAL_DIAGNOSTIC);
        LicenseRecoverLicenseState alternate=inspect(root,xmt,(a,product,mode,d,e)->{
            check("XMT-PRODUCT".equals(product),"XMT uses classes SoftVersionID despite conflicting data metadata");
            return local(true,"ALTERNATE-MODE".equals(mode));
        },null);
        check(alternate.localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED,"XMT alternative scope can pass while its display mode is absent");
        check(inspect(root,xmt,(a,b,c,d,e)->new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(
                LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,"identity unavailable",false,false,true,false),null)
                .localCondition==LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,"unknown identity is not converted to machine mismatch or acceptance");
        check(inspect(root,xmt,(a,b,c,d,e)->new LicenseRecoverReadOnlyJavaAlternateVerifier.Check(
                LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"requires quota write",false,false,true,true),null)
                .acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"diagnostic entry never consumes finite quota or promotes it");
        put(root,"systemConfig.yml","global.system.VersionID=YT00123\n");
        LicenseRecoverReadOnlyJavaAlternateVerifier.Profile mapped=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.YT_MAPPED_LOCAL_DIAGNOSTIC);
        LicenseRecoverLicenseState mappedScope=inspect(root,mapped,(a,product,mode,d,time)->{
            check("QT1001".equals(product),"YT00123 uses its fixed mapped constructor before generic YT001 fallback");
            check(time==2L,"YT checks its stricter end instant after the observation time");
            return local(true,"QT100109".equals(mode));
        },null);
        check(mappedScope.localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED
                &&mappedScope.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"either mapped YT scope supplies local evidence while database/cache startup remains unproved");
        put(root,"systemConfig.yml","global.system.VersionID=YT00138\n");
        check(inspect(root,mapped,(a,b,c,d,e)->{throw new AssertionError("wrong mapped product must not be evaluated");},null)
                .localCondition==LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"a changed dynamic mode cannot reuse YT00123's fixed mapping");
        LicenseRecoverReadOnlyJavaAlternateVerifier.Profile generic=profile(root,LicenseRecoverReadOnlyJavaAlternateVerifier.Layout.YT_GENERIC_LOCAL_DIAGNOSTIC);
        check(inspect(root,generic,(a,product,mode,d,e)->{
            check("YT001".equals(product)&&"YT00138".equals(mode),"YT generic fallback uses the code-derived product and configured mode");
            return local(true,true);
        },null).localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED,"YT generic local evidence does not depend on a hard-coded application directory");
        check(inspect(root,generic,(a,b,c,d,e)->local(true,true),()->{try{put(root,"systemConfig.yml","global.system.VersionID=CHANGED\n");}catch(Exception e){throw new RuntimeException(e);}})
                .localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,"changing current product configuration between reads revokes the diagnostic as well as positive proofs");
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
            localDiagnostics(root);
            System.out.println("LicenseRecoverReadOnlyJavaAlternateVerifierTest: "+checks+" checks passed");
        }finally {
            try(java.util.stream.Stream<Path> stream=Files.walk(root)){
                stream.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});
            }
        }
    }
}
