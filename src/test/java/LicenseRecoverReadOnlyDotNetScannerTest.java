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
        Map<String,String> future=new HashMap<String,String>(ds05);future.put("ITMC.Web.dll",LicenseRecoverReadOnlyDotNetCatalog.hash(new byte[]{1}));
        LicenseRecoverReadOnlyDotNetCatalog.Assessment variant=LicenseRecoverReadOnlyDotNetCatalog.classify(future,null,null);
        check(variant.upperFamily==a.upperFamily&&!variant.auditedBinding,"known protocol component in a future site is identified but not certified");
        future=new HashMap<String,String>(ds05);future.put("ItmcBtoB.DBUtility.dll","changed");
        check(!LicenseRecoverReadOnlyDotNetCatalog.classify(future,null,null).auditedBinding,"dependency changes cannot inherit license semantics");
        LicenseRecoverLicenseState noRecord=LicenseRecoverLicenseState.localObservation(
                LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"synthetic missing root record");
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
        resourceChecks();
        System.out.println("PASS: common .NET family classification, parameterized modes and candidate/proof separation ("+checks+" checks)");
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
