import java.nio.file.*;
import java.util.*;

/** One family recognizer, one modern predicate, and no promotion from candidate API to authorization. */
public final class LicenseRecoverReadOnlyDotNetScannerTest {
    private static int checks;
    private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static byte[] api(boolean wrongOwner)throws Exception {
        return LicenseRecoverReadOnlyPeMetadataTest.apiFixtureBytes(false,"ITMC.Regedit",wrongOwner,false);
    }
    private static Map<String,String> binding(String site,String reg,String crypto) {
        Map<String,String> m=new HashMap<String,String>();m.put("ITMC.Web.dll",site);m.put("ITMC.Regedit.dll",reg);
        m.put("ItmcBtoB.DBUtility.dll",crypto);m.put("Newtonsoft.Json.dll","78bedd9fce877a71a8d8ff9a813662d8248361e46705c4ef7afc61d440ff2eeb");
        m.put("log4net.dll","af5610c515d2244db98c662636264c8177e89b1afe407f88fd18a41d66f6e7e2");
        m.put("LiteDB.dll","4091c455a882e3e44c8dc0c4034e6b2536f180ce019695769ef72497a90467ec");return m;
    }
    public static void main(String[] args)throws Exception {
        if(args.length==2&&"--resource-probe".equals(args[0])){resourceProbe(Paths.get(args[1]));return;}
        Map<String,String> ds01=binding("16b13b547893b160ef928fd6f06568770da1fdd2326a1dfdd4bc5c54e047562c",
                "735a7f868825111cd437cb5093eaa79f0a977330a79121fb2d18885290da743f","9430693eea94e18077fb1fa0e187a0d4cb6c4bf472eca4cdd075366e9b8eb92c");
        Map<String,String> ds05=binding("21464d180e30ed1f10c0c6b20e56381191ecd70aab06595f07a27dc4d559961a",
                "25b6b2939534b8266d843f7bd7b942d9ee87fd48296c8ed05828ffc3de76c0d8","7d880b03364e4c31c39c47a053144345b5b02804ac7a0e80a87ebf24096fd999");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment a=LicenseRecoverReadOnlyDotNetCatalog.classify(ds01,null,null),b=LicenseRecoverReadOnlyDotNetCatalog.classify(ds05,null,null);
        check(a.upperFamily==b.upperFamily&&a.upperFamily==LicenseRecoverReadOnlyDotNetCatalog.Family.MODERN_JSON_DES,"different products/builds share one modern protocol family");
        check(a.auditedBinding&&b.auditedBinding&&a.upper.product.equals(b.upper.product),"explicit audited parameters share the same product engine");
        check("EXACT_REVIEWED_SET".equals(a.fingerprintStatus)&&"LOCAL_POSITIVE_PREDICATE".equals(a.auditScope)
                &&a.fingerprintMatches.size()==1,"exact code-set evidence reports its audit scope");
        check(a.fingerprintMatches.get(0).requiredComponents==6&&a.fingerprintMatches.get(0).matchedComponents==6,
                "the complete six-component modern binding is distinguished from a DLL match");
        Map<String,String> future=new HashMap<String,String>(ds05);future.put("ITMC.Web.dll",LicenseRecoverReadOnlyDotNetCatalog.hash(new byte[]{1}));
        LicenseRecoverReadOnlyDotNetCatalog.Assessment variant=LicenseRecoverReadOnlyDotNetCatalog.classify(future,null,null);
        check(variant.upperFamily==a.upperFamily&&!variant.auditedBinding,"known protocol component in a future site is identified but not certified");
        check("PARTIAL_REVIEWED_SET".equals(variant.fingerprintStatus)&&"NONE".equals(variant.auditScope)
                &&variant.fingerprintMatches.get(0).differentComponents.equals(Collections.singletonList("bin/ITMC.Web.dll")),
                "an altered website shows the specific unreviewed component without borrowing its audit scope");
        future=new HashMap<String,String>(ds05);future.put("ItmcBtoB.DBUtility.dll","changed");
        check(!LicenseRecoverReadOnlyDotNetCatalog.classify(future,null,null).auditedBinding,"dependency changes cannot inherit license semantics");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment dependency=LicenseRecoverReadOnlyDotNetCatalog.classify(future,null,null);
        check(dependency.fingerprintMatches.get(0).differentComponents.equals(Collections.singletonList("bin/ItmcBtoB.DBUtility.dll")),
                "crypto changes are disclosed as a different required dependency");
        future.remove("ItmcBtoB.DBUtility.dll");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment missing=LicenseRecoverReadOnlyDotNetCatalog.classify(future,null,null);
        check(missing.fingerprintMatches.get(0).missingComponents.equals(Collections.singletonList("bin/ItmcBtoB.DBUtility.dll"))
                &&missing.fingerprintMatches.get(0).matchedComponents==5,"a missing dependency is not a bad license or a known code set");
        boolean immutable=false;try{a.fingerprintMatches.clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"fingerprint coverage collection is immutable");
        immutable=false;try{missing.fingerprintMatches.get(0).missingComponents.clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"component mismatch receipts are immutable");
        Map<String,String> onlyDependency=new HashMap<String,String>();onlyDependency.put("LiteDB.dll",ds01.get("LiteDB.dll"));
        check(LicenseRecoverReadOnlyDotNetCatalog.classify(onlyDependency,null,null).fingerprintMatches.isEmpty(),
                "a ubiquitous dependency cannot select a registration-family audit candidate");
        Map<String,String> protectedSet=new HashMap<String,String>();
        protectedSet.put("ITMC.Web.dll","929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4");
        protectedSet.put("ITMC.Regedit.dll","222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725");
        protectedSet.put("itmcRegedit.dll","35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f");
        protectedSet.put("ITMC.DBUtility.dll","dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment protectedBuild=LicenseRecoverReadOnlyDotNetCatalog.classify(protectedSet,null,null);
        check(protectedBuild.auditedBinding&&"PROTECTED_LOCAL_DIAGNOSTIC".equals(protectedBuild.auditScope)
                &&protectedBuild.fingerprintSummary().contains("完整接受路径尚未闭合"),"exact protected fingerprints never masquerade as a fully audited positive predicate");
        protectedSet.put("Newtonsoft.Json.dll",ds01.get("Newtonsoft.Json.dll"));protectedSet.put("LiteDB.dll",ds01.get("LiteDB.dll"));
        protectedSet.put("log4net.dll","c228ad98c76a3b40bbd1bc950b81feff977646b2b99a21310164d4d310c7ea75");
        protectedBuild=LicenseRecoverReadOnlyDotNetCatalog.classify(protectedSet,null,null);
        check(protectedBuild.upper!=null&&protectedBuild.extended!=null&&!protectedBuild.upper.positiveEntryAudited
                &&"MODERN_LOCAL_DIAGNOSTIC".equals(protectedBuild.auditScope),"one dual-DLL build can expose modern local evidence while its protected website proof stays open");
        Map<String,String> yx0301=new HashMap<String,String>(protectedSet);
        yx0301.put("ITMC.Web.dll","d22c923d7f11b084e2839b3d0d6ee572e06ad5d41200ff6c8215757f54e7501e");
        yx0301.put("itmcRegedit.dll","e684d265332af25ccc3449c38a38f024ecbf87353b9c48710c5ae2b2433c5183");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment yx0301Binding=LicenseRecoverReadOnlyDotNetCatalog.classify(yx0301,null,null);
        check(yx0301Binding.upper!=null&&!yx0301Binding.upper.positiveEntryAudited&&"YX0301".equals(yx0301Binding.upper.product)
                &&"MODERN_LOCAL_DIAGNOSTIC".equals(yx0301Binding.auditScope),"a reviewed new product reuses the core local engine without borrowing full entry proof");
        check(yx0301Binding.fingerprintSummary().contains("RegisterVersion.db"),"an exact local-only binding retains its concrete entry gap for the GUI");
        yx0301.remove("itmcRegedit.dll");
        check(LicenseRecoverReadOnlyDotNetCatalog.classify(yx0301,null,null).upper==null,"the new website's explicit compatibility dependency is required");
        Map<String,String> ys=new HashMap<String,String>();
        ys.put("ITMC.Web.dll","1790526c91b0dc51aa914d80604e600175cfb3d8899c8299bb3b2c85684b9b20");
        ys.put("ITMC.Regedit.dll","c942c3c21466979e63eee1ffadbfe7273c83c2dc0a455f324c9da7e187953f46");
        ys.put("ITMC.DBUtility.dll","236cfe610b87fb7a4f6589cb2a9e319a048b8a8e2a1b79d3028d4d7e1b5fcb6b");
        ys.put("Newtonsoft.Json.dll","9eca2c5ed187b5e1319c359aabf8382992131efe1854b899c23a93fe227496e3");ys.put("LiteDB.dll",ds01.get("LiteDB.dll"));
        ys.put("itmcRegedit.dll","36f58b8657bce75a19356796f21c135104e28f5abc4bc9007f0d4ec517f479df");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment ysBinding=LicenseRecoverReadOnlyDotNetCatalog.classify(ys,null,null);
        check(ysBinding.upper!=null&&!ysBinding.upper.positiveEntryAudited&&"YS01".equals(ysBinding.upper.product)
                &&"MODERN_LOCAL_DIAGNOSTIC".equals(ysBinding.auditScope)
                &&ysBinding.fingerprintMatches.get(0).requiredAbsentComponents.equals(Collections.singletonList("bin/log4net.dll")),
                "reviewed YS01 local-only binding records dependency absence without claiming native initialization was closed");
        Map<String,String> ysMissingLower=new HashMap<String,String>(ys);ysMissingLower.remove("itmcRegedit.dll");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment missingLower=LicenseRecoverReadOnlyDotNetCatalog.classify(ysMissingLower,null,null);
        check(missingLower.upper==null&&"PARTIAL_REVIEWED_SET".equals(missingLower.fingerprintStatus)
                &&missingLower.fingerprintMatches.get(0).missingComponents.equals(Collections.singletonList("bin/itmcRegedit.dll")),
                "a missing website-referenced compatibility dependency withdraws the reviewed modern build binding");
        Map<String,String> ysChangedLower=new HashMap<String,String>(ys);ysChangedLower.put("itmcRegedit.dll","different-compatibility-build");
        check(LicenseRecoverReadOnlyDotNetCatalog.classify(ysChangedLower,null,null).upper==null,
                "replacing a compatibility dependency cannot inherit the reviewed modern build scope");
        check(ysBinding.fingerprintMatches.get(0).requiredComponents==7,
                "YS01 whole-entry coverage includes six files plus the reviewed log absence condition");
        ys.put("log4net.dll","unexpected-file");
        check(LicenseRecoverReadOnlyDotNetCatalog.classify(ys,null,null).upper==null,"adding an absent-bound dependency withdraws the exact code-set proof");
        LicenseRecoverLicenseState noRecord=LicenseRecoverLicenseState.localObservation(
                LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"synthetic missing root record");
        LicenseRecoverLicenseState both=LicenseRecoverReadOnlyDotNetScanner.combineDiagnostics(
                LicenseRecoverLicenseState.unverified("synthetic modern format not matched"),noRecord);
        check(both.localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD
                &&both.evidence.contains("synthetic modern format not matched")&&both.evidence.contains("synthetic missing root record"),
                "unresolved dual components retain both attempted paths and scoped compatibility finding");
        both=LicenseRecoverReadOnlyDotNetScanner.combineDiagnostics(noRecord,LicenseRecoverLicenseState.localObservation(
                LicenseRecoverLicenseState.LocalCondition.MALFORMED,"synthetic legacy format not matched"));
        check(both.localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD
                &&both.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"a legacy parser failure does not discard a recognized modern diagnostic");
        both=LicenseRecoverReadOnlyDotNetScanner.combineDiagnostics(LicenseRecoverLicenseState.localObservation(
                LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"synthetic dispatch not audited"),noRecord);
        check(both.localCondition==LicenseRecoverLicenseState.LocalCondition.NO_RECORD&&both.evidence.contains("synthetic dispatch not audited"),
                "unsupported modern dispatch does not hide an independently observed missing local record");
        check(LicenseRecoverReadOnlyDotNetScanner.combineDiagnostics(noRecord,LicenseRecoverLicenseState.scanError("synthetic read failure")).isScanError(),
                "real fallback read errors are never hidden by partial findings");
        boolean refused=false;try{LicenseRecoverReadOnlyDotNetScanner.combineDiagnostics(
                LicenseRecoverLicenseState.authorizationAcceptance("synthetic audited positive"),noRecord);}
        catch(IllegalArgumentException expected){refused=true;}
        check(refused,"diagnostic combination cannot downgrade or fabricate a proven acceptance");
        LicenseRecoverReadOnlyDotNetScanner.Result revoked=LicenseRecoverReadOnlyDotNetScanner.retainIfStable(noRecord,a,b);
        check(revoked.state.localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED&&revoked.protocol==null,
                "changed evidence revokes both a local negative finding and its current audit flag");
        Map<String,String> changedConfig=new HashMap<String,String>(ds01);changedConfig.put("config.xml","changed root record");
        revoked=LicenseRecoverReadOnlyDotNetScanner.retainIfStable(
                LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,"synthetic mismatch"),
                a,LicenseRecoverReadOnlyDotNetCatalog.classify(changedConfig,null,null));
        check(revoked.state.localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED&&revoked.protocol==null,
                "a root configuration change cannot retain stale machine mismatch or binding provenance");
        byte[] payload=LicenseRecoverReadOnlyDotNetVerifierTest.config(
                LicenseRecoverReadOnlyDotNetVerifierTest.payload().replace("DS0101,DS0109","FUTURE_MODE"),"1","");
        String text=new String(payload,java.nio.charset.StandardCharsets.UTF_8).replace("<SoftVersionID>DS0109</SoftVersionID>","<SoftVersionID>FUTURE_MODE</SoftVersionID>");
        check(LicenseRecoverReadOnlyDotNetVerifierTest.run(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),LicenseRecoverReadOnlyDotNetVerifierTest.hardware()).acceptance
                ==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE,"one predicate accepts a new covered mode without any product-name switch");
        check(LicenseRecoverReadOnlyDotNetVerifierTest.run(payload,LicenseRecoverReadOnlyDotNetVerifierTest.hardware()).acceptance
                ==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,"recognizing the family does not bypass mode coverage");
        Path root=Files.createTempDirectory("lr-protocol-candidate-");
        try {
            Path one=Files.createDirectories(root.resolve("DS0105/bin")),two=Files.createDirectories(root.resolve("UnrelatedName/bin"));
            byte[] reg=api(false),web=LicenseRecoverReadOnlyPeMetadataTest.fixture(false);
            for(Path bin:Arrays.asList(one,two)){Files.write(bin.resolve("ITMC.Regedit.dll"),reg);Files.write(bin.resolve("ITMC.Web.dll"),web);}
            LicenseRecoverReadOnlyDotNetScanner.Result first=LicenseRecoverReadOnlyDotNetScanner.inspect(one.getParent().toFile());
            LicenseRecoverReadOnlyDotNetScanner.Result second=LicenseRecoverReadOnlyDotNetScanner.inspect(two.getParent().toFile());
            check(first.protocol.upperFamily==LicenseRecoverReadOnlyDotNetCatalog.Family.REGINFO_API_CANDIDATE,"typed unseen assembly is recognized as a family candidate");
            check(first.protocol.upperFamily==second.protocol.upperFamily,"directory names do not choose the protocol");
            check(!first.protocol.auditedBinding&&first.state.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                    &&first.state.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED,"a compatible API with no body proof is never green or native-invalid");
            check(first.state.evidence.contains("同系列")&&!first.protocol.summary().contains("授权通过"),"specific family-candidate scope is visible");
            check(Arrays.equals(reg,Files.readAllBytes(one.resolve("ITMC.Regedit.dll"))),"no loader or target modification");
            LicenseRecoverReadOnlyDotNetCatalog.Assessment prior=first.protocol;
            Files.write(one.getParent().resolve("config.xml"),"<ROOT/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            check(!prior.sameInputs(LicenseRecoverReadOnlyDotNetCatalog.assess(one.getParent().toFile(),true)),
                    "the common snapshot includes newly created root configuration, not only DLLs");
            Files.write(one.resolve("ITMC.Regedit.dll"),api(true));
            check(LicenseRecoverReadOnlyDotNetScanner.inspect(one.getParent().toFile()).protocol.upperFamily==LicenseRecoverReadOnlyDotNetCatalog.Family.UNKNOWN,"wrong-owner names cannot form the family API");
            Files.write(one.resolve("ITMC.Regedit.dll"),new byte[]{1});
            check(LicenseRecoverReadOnlyDotNetScanner.inspect(one.getParent().toFile()).state.isScanError(),"damaged metadata is a scan error rather than license invalidity");
        }finally {try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});}}
        modeRelationChecks();resourceChecks();
        System.out.println("PASS: common .NET family classification, parameterized modes and candidate/proof separation ("+checks+" checks)");
    }
    private static void modeRelationChecks()throws Exception {
        Path root=Files.createTempDirectory("lr-mode-relations-");
        String web=LicenseRecoverReadOnlyDotNetCatalog.hash(new byte[]{31}),lite=LicenseRecoverReadOnlyDotNetCatalog.hash(new byte[]{32});
        String[] consumers={web};byte[] database={41,42,43};String dbHash=LicenseRecoverReadOnlyDotNetCatalog.hash(database);
        LicenseRecoverReadOnlyDotNetCatalog.ModeReceipt[] receipts={new LicenseRecoverReadOnlyDotNetCatalog.ModeReceipt(
                "SYNTHETIC-MODE",web,lite,dbHash,"TEST_MODE","PARENT_A:MODE_A,MODE_B;PARENT_B:MODE_C")};
        Path db=Files.createDirectories(root.resolve("Data/db")).resolve("RegisterVersion.db"),log=db.resolveSibling("RegisterVersion-log.db"),tmp=db.resolveSibling("RegisterVersion-tmp.db");
        try {
            modeConfig(root,"TEST_MODE");Files.write(db,database);
            Map<String,String> first=modeSnapshot(root,web,lite,consumers,true);
            LicenseRecoverReadOnlyDotNetCatalog.ModeRelations match=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(first,receipts,consumers,lite,true);
            check("EXACT_REVIEWED_DATA".equals(match.status)&&match.parentModes.size()==2&&match.parentModes.get("PARENT_A").equals(Arrays.asList("MODE_A","MODE_B")),
                    "synthetic exact tuple selects relationship data without a database engine");
            check(match.inspectionReadOnlyAudited&&!match.fullAuthorizationAudited&&match.summary().contains("未验证父产品授权"),
                    "exact relationship data has no native or whole-authorization proof");
            boolean immutable=false;try{match.parentModes.clear();}catch(UnsupportedOperationException expected){immutable=true;}
            check(immutable,"parent grouping is immutable");immutable=false;try{match.parentModes.get("PARENT_A").add("OTHER");}catch(UnsupportedOperationException expected){immutable=true;}
            check(immutable,"parent modes are deeply immutable");
            for(LicenseRecoverLicenseState.LocalCondition condition:new LicenseRecoverLicenseState.LocalCondition[]{
                    LicenseRecoverLicenseState.LocalCondition.SATISFIED,LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,LicenseRecoverLicenseState.LocalCondition.NO_RECORD}) {
                LicenseRecoverLicenseState state=LicenseRecoverLicenseState.localObservation(condition,"synthetic scoped condition");
                LicenseRecoverReadOnlyInspection.Result result=new LicenseRecoverReadOnlyInspection.Result(root.toFile(),"TEST_MODE","DOTNET",state,
                        Collections.<LicenseRecoverReadOnlyInspection.Artifact>emptyList(),"synthetic reason",null,
                        LicenseRecoverReadOnlyCodeEvidence.unassessed("synthetic"),"synthetic code","synthetic local",match);
                String json=result.toJson();
                check(result.state==state&&result.state.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                        &&result.state.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED&&result.state.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,
                        "adding relation evidence does not promote, reject or replace a local condition");
                check(json.contains("\"modeRelationStatus\":\"EXACT_REVIEWED_DATA\"")&&json.contains("\"product\":\"PARENT_A\"")
                        &&json.contains("\"modeRelationFullAuthorizationAudited\":false"),"JSON exposes typed relation scope without authorization promotion");
            }
            Files.write(db,new byte[]{44});
            LicenseRecoverReadOnlyDotNetCatalog.ModeRelations unknown=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(modeSnapshot(root,web,lite,consumers,true),receipts,consumers,lite,true);
            check("UNKNOWN_DATABASE_OR_MODE".equals(unknown.status)&&unknown.parentModes.isEmpty(),"an unknown database is not guessed to have no parents");Files.write(db,database);
            modeConfig(root,"OTHER_MODE");unknown=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(modeSnapshot(root,web,lite,consumers,true),receipts,consumers,lite,true);
            check("UNKNOWN_DATABASE_OR_MODE".equals(unknown.status)&&unknown.parentModes.isEmpty(),"a known database cannot lend relationships to an unreviewed config mode");
            Files.write(root.resolve("config.xml"),"<ROOT><SystemSoft><SoftVersionID>TEST_MODE</SoftVersionID><SoftVersionID>OTHER_MODE</SoftVersionID></SystemSoft></ROOT>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            unknown=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(modeSnapshot(root,web,lite,consumers,true),receipts,consumers,lite,true);
            check("CURRENT_MODE_UNKNOWN".equals(unknown.status)&&unknown.parentModes.isEmpty(),"duplicate mode configuration cannot choose a favorable relation receipt");modeConfig(root,"TEST_MODE");
            modeConfig(root,new String(new char[65]).replace('\0','A'));
            unknown=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(modeSnapshot(root,web,lite,consumers,true),receipts,consumers,lite,true);
            check("CURRENT_MODE_UNKNOWN".equals(unknown.status)&&unknown.currentMode.isEmpty(),"oversized mode text is neither trusted nor copied to diagnostics");modeConfig(root,"TEST_MODE");
            for(Path companion:Arrays.asList(log,tmp)) {
                Files.write(companion,new byte[0]);Map<String,String> current=modeSnapshot(root,web,lite,consumers,true);
                unknown=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(current,receipts,consumers,lite,true);
                check("COMPANION_PRESENT".equals(unknown.status)&&unknown.parentModes.isEmpty()&&unknown.companionFiles.size()==1,
                        "even an empty WAL/tmp companion prevents a main-file-only relationship claim");
                modeRevoked(first,current,"new companion withdraws previous relationships");Files.delete(companion);
            }
            Map<String,String> changed=new HashMap<String,String>(first);changed.put("ITMC.Web.dll",LicenseRecoverReadOnlyDotNetCatalog.hash(new byte[]{33}));
            check("UNREVIEWED_CONSUMER".equals(LicenseRecoverReadOnlyDotNetCatalog.modeRelations(changed,receipts,consumers,lite,true).status),
                    "a database hash cannot identify an unreviewed website consumer");
            changed=new HashMap<String,String>(first);changed.put("LiteDB.dll",LicenseRecoverReadOnlyDotNetCatalog.hash(new byte[]{34}));
            check("UNREVIEWED_CONSUMER".equals(LicenseRecoverReadOnlyDotNetCatalog.modeRelations(changed,receipts,consumers,lite,true).status),
                    "changing the database reader withdraws relation semantics");
            check("UNREVIEWED_CONSUMER".equals(LicenseRecoverReadOnlyDotNetCatalog.modeRelations(first,receipts,consumers,lite,false).status),
                    "the rest of the reviewed consumer dependency set is mandatory");
            modeRevoked(first,changed,"a changed reader withdraws all current binding receipts");
            Files.delete(db);Map<String,String> absent=modeSnapshot(root,web,lite,consumers,true);
            unknown=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(absent,receipts,consumers,lite,true);
            check("MISSING_DATABASE".equals(unknown.status)&&unknown.parentModes.isEmpty()&&unknown.reason.contains("直接返回空字典")
                    &&"REVIEWED_MISSING_DATABASE_BRANCH".equals(unknown.auditScope),"only an observed missing database on a reviewed consumer gets empty-return evidence");
            Files.write(db,database);modeRevoked(absent,modeSnapshot(root,web,lite,consumers,true),"newly created database revokes missing-branch evidence");
            first=modeSnapshot(root,web,lite,consumers,true);Files.write(db,new byte[]{41,42,44});
            modeRevoked(first,modeSnapshot(root,web,lite,consumers,true),"same-size database mutation is not hidden by matching component hashes");Files.write(db,database);
            first=modeSnapshot(root,web,lite,consumers,true);Files.setLastModifiedTime(db,java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(db).toMillis()+2000));
            modeRevoked(first,modeSnapshot(root,web,lite,consumers,true),"database identity receipts retain same-byte modification evidence");
            modeConfig(root,"OTHER_MODE");modeRevoked(first,modeSnapshot(root,web,lite,consumers,true),"configuration mode changes revoke prior matching relationship data");modeConfig(root,"TEST_MODE");
            Path renamed=root.resolveSibling(root.getFileName()+"-UnrelatedName");Files.move(root,renamed);root=renamed;
            unknown=LicenseRecoverReadOnlyDotNetCatalog.modeRelations(modeSnapshot(root,web,lite,consumers,true),receipts,consumers,lite,true);
            check("EXACT_REVIEWED_DATA".equals(unknown.status),"application directory names do not select a mode relationship strategy");
            Map<String,String> budget=new HashMap<String,String>();budget.put("ITMC.Web.dll",web);budget.put("LiteDB.dll",lite);
            boolean bounded=false;try{LicenseRecoverReadOnlyDotNetCatalog.captureModeInputs(root,budget,consumers,true,2);}catch(java.io.IOException expected){bounded=true;}
            check(bounded,"mode files participate in the bounded aggregate metadata read budget");
        }finally {try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});}}
    }
    private static void modeConfig(Path root,String mode)throws Exception {
        Files.write(root.resolve("config.xml"),("<ROOT><SystemSoft><SoftVersionID>"+mode+"</SoftVersionID></SystemSoft></ROOT>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static Map<String,String> modeSnapshot(Path root,String web,String lite,String[] consumers,boolean binding)throws Exception {
        Map<String,String> hashes=new TreeMap<String,String>();hashes.put("ITMC.Web.dll",web);hashes.put("LiteDB.dll",lite);
        if(Files.isRegularFile(root.resolve("config.xml")))hashes.put("config.xml",LicenseRecoverReadOnlyDotNetCatalog.hash(Files.readAllBytes(root.resolve("config.xml"))));
        LicenseRecoverReadOnlyDotNetCatalog.captureModeInputs(root,hashes,consumers,binding,32*1024*1024);
        return hashes;
    }
    private static void modeRevoked(Map<String,String> before,Map<String,String> after,String reason) {
        LicenseRecoverReadOnlyDotNetScanner.Result result=LicenseRecoverReadOnlyDotNetScanner.retainIfStable(
                LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.SATISFIED,"synthetic"),
                LicenseRecoverReadOnlyDotNetCatalog.classify(before,null,null),LicenseRecoverReadOnlyDotNetCatalog.classify(after,null,null));
        check(result.protocol==null&&"CHANGED_OR_REVOKED".equals(result.modeRelations.status)&&result.modeRelations.parentModes.isEmpty(),reason);
    }
    /** A small heap must be sufficient even when an unrelated component is near the file limit. */
    private static void resourceChecks()throws Exception {
        Path root=Files.createTempDirectory("lr-protocol-heap-");
        try {
            Path bin=Paths.get(System.getProperty("java.home"),"bin");
            Path javaExecutable=bin.resolve(Files.isRegularFile(bin.resolve("java.exe"))?"java.exe":"java");
            Process child=new ProcessBuilder(javaExecutable.toString(),"-Xmx48m","-cp",System.getProperty("java.class.path"),
                    LicenseRecoverReadOnlyDotNetScannerTest.class.getName(),"--resource-probe",root.toString())
                    .redirectErrorStream(true).start();
            if(!child.waitFor(45,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("bounded heap probe timed out");}
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream in=child.getInputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1){
                if(output.size()+n>65536)throw new AssertionError("heap probe output limit");output.write(block,0,n);
            }}
            String text=new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            check(child.exitValue()==0&&text.contains("PASS: bounded protocol resource probe"),
                    "96MiB streaming hash, cumulative set cap and metadata cap pass under -Xmx48m: "+text);
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});}}
    }
    private static void resourceProbe(Path root)throws Exception {
        Path bin=Files.createDirectories(root.resolve("bin")),web=bin.resolve("ITMC.Web.dll"),reg=bin.resolve("ITMC.Regedit.dll");
        resize(web,96L*1024*1024);
        LicenseRecoverReadOnlyDotNetCatalog.Assessment a=LicenseRecoverReadOnlyDotNetCatalog.assess(root.toFile(),false);
        check(a.hashes.size()==1&&a.upperFamily==LicenseRecoverReadOnlyDotNetCatalog.Family.UNKNOWN,"large file is streamed rather than buffered");
        resize(reg,97L*1024*1024);
        resourceLimit(root,false,"protocol-set-limit");
        resize(web,0);resize(reg,16L*1024*1024+1);
        resourceLimit(root,true,"protocol-metadata-limit");
        System.out.println("PASS: bounded protocol resource probe");
    }
    private static void resourceLimit(Path root,boolean structural,String message)throws Exception {
        try{LicenseRecoverReadOnlyDotNetCatalog.assess(root.toFile(),structural);throw new AssertionError(message);}
        catch(java.io.IOException expected){check(message.equals(expected.getMessage()),"specific preallocation resource limit: "+expected.getMessage());}
    }
    private static void resize(Path p,long length)throws Exception {
        try(java.io.RandomAccessFile file=new java.io.RandomAccessFile(p.toFile(),"rw")){file.setLength(length);}
    }
}
