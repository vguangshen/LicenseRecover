import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** Synthetic on-disk method candidates are never promoted into Patch or license proof. */
public final class LicenseRecoverReadOnlyDotNetCodeEvidenceTest {
    private static int checks;
    private static void check(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
    private static byte[] fixture(String ns,boolean wrongOwner,String method,boolean byref,boolean fat,int... code)throws Exception {
        return LicenseRecoverReadOnlyPeMetadataTest.booleanBodyFixture(false,ns,wrongOwner,method,byref,fat,code);
    }
    private static void candidate(Path root,String reason) {
        LicenseRecoverReadOnlyCodeEvidence result=LicenseRecoverReadOnlyDotNetCodeEvidence.inspect(root.toFile());
        check(result.component==LicenseRecoverReadOnlyCodeEvidence.Component.REPLACEMENT_SHAPE_CANDIDATE,reason);
        check(result.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED&&result.readOnlyAudited,
                "a local body shape cannot establish a whole-application static bypass");
        check(result.componentEvidence.contains("初始化器")&&result.componentEvidence.contains("没有可信原版/Patch 对照"),
                "initializer/dependency and Patch provenance gaps must be explicit");
        check(!result.componentHashes.isEmpty()&&result.componentHashes.keySet().iterator().next().startsWith("bin/"),
                "the candidate carries immutable root-relative component receipts");
    }
    private static void unassessed(Path root,String reason) {
        LicenseRecoverReadOnlyCodeEvidence result=LicenseRecoverReadOnlyDotNetCodeEvidence.inspect(root.toFile());
        check(result.component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED
                &&result.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED,reason);
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("lr-dotnet-code-evidence-");
        try {
            Path bin=Files.createDirectories(root.resolve("bin")),upper=bin.resolve("ITMC.Regedit.dll"),lower=bin.resolve("itmcRegedit.dll");
            byte[] tiny=fixture("ITMC.Regedit",false,"CheckReInfo",true,false,0x16,0x2a);
            Files.write(upper,tiny);candidate(root,"exact owned tiny bool false is only a replacement candidate");
            check(Arrays.equals(tiny,Files.readAllBytes(upper)),"candidate detection does not rewrite the component");
            Files.write(upper,fixture("ITMC.Regedit",false,"CheckNet",false,true,0x17,0x2a));
            candidate(root,"fat no-argument CheckNet is recognized without interpreting its boolean as authorized");
            for(boolean fat:new boolean[]{false,true})for(boolean stat:new boolean[]{false,true}) {
                Files.write(upper,LicenseRecoverReadOnlyPeMetadataTest.booleanBodyFixture(false,"ITMC.Regedit",false,"CheckNet",true,fat,stat,
                        stat?0x02:0x03,0x14,0x51,0x16,0x2a));
                candidate(root,"the repo's exact byref-null-false template stays a candidate for declared instance/static tiny/fat methods");
                Files.write(upper,LicenseRecoverReadOnlyPeMetadataTest.booleanBodyFixture(false,"ITMC.Regedit",false,"CheckNet",true,fat,stat,
                        stat?0x03:0x02,0x14,0x51,0x16,0x2a));
                unassessed(root,"wrong instance/static argument index cannot become a repo replacement candidate");
            }
            Files.write(upper,LicenseRecoverReadOnlyPeMetadataTest.externalRefBodyFixture());
            unassessed(root,"an external same-name class cannot impersonate the local reference parameter");
            Files.write(upper,fixture("ITMC.Regedit",true,"CheckNet",true,false,0x03,0x14,0x51,0x16,0x2a));
            unassessed(root,"the null/false shape on a different owner is not a reviewed registration method");
            byte[] valueRef=fixture("ITMC.Regedit",false,"CheckNet",true,false,0x03,0x14,0x51,0x16,0x2a);valueRef[0x880+69]=0x11;
            Files.write(upper,valueRef);unassessed(root,"value-type parameters cannot count as writable class references");
            Files.write(upper,fixture("ITMC.Regedit",false,"CheckNet",true,false,0x17,0x26,0x03,0x14,0x51,0x16,0x2a));
            unassessed(root,"matching byref-null-false suffix is not sufficient");
            byte[] refEh=fixture("ITMC.Regedit",false,"CheckNet",true,true,0x03,0x14,0x51,0x16,0x2a);refEh[0xd00]=0x0b;
            Files.write(upper,refEh);unassessed(root,"an EH flag prevents the byref-null-false candidate too");
            Files.delete(upper);Files.write(lower,fixture("itmcRegedit",false,"CheckReInfo",true,true,0x17,0x2a));
            candidate(root,"lower registration namespace reuses the same exact body rule");Files.delete(lower);
            Files.write(upper,fixture("ITMC.Regedit",false,"CheckReInfo",true,false,0x17,0x26,0x16,0x2a));
            unassessed(root,"long method ending false cannot use the legacy helper tail heuristic");
            Files.write(upper,fixture("ITMC.Regedit",false,"CheckReInfo",true,false,0x2b,0,0x16,0x2a));
            unassessed(root,"branching control flow is outside this exact two-instruction rule");
            Files.write(upper,fixture("ITMC.Regedit",true,"CheckReInfo",true,false,0x16,0x2a));
            unassessed(root,"correct names on the wrong TypeDef owner never count");
            Files.write(upper,fixture("Other.Namespace",false,"CheckReInfo",true,false,0x16,0x2a));
            unassessed(root,"unrelated namespaces cannot impersonate the reviewed registration owner");
            byte[] badSignature=tiny.clone();badSignature[0x880+67]=14;Files.write(upper,badSignature);
            unassessed(root,"a string return signature cannot count as a bool body");
            byte[] eh=fixture("ITMC.Regedit",false,"CheckReInfo",true,true,0x16,0x2a);eh[0xd00]=0x0b;Files.write(upper,eh);
            unassessed(root,"exception sections are never ignored to acquire a candidate");
            Files.write(upper,new byte[64]);unassessed(root,"damaged PE remains unassessed rather than Patch proof");
            Files.write(upper,tiny);
            LicenseRecoverReadOnlyCodeEvidence changed=LicenseRecoverReadOnlyDotNetCodeEvidence.inspectReviewed(root.toFile(),()->{
                try{Files.write(upper,badSignature);}catch(IOException e){throw new RuntimeException(e);}
            });
            check(changed.component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED&&changed.componentEvidence.contains("变化"),
                    "a candidate is revoked when component bytes change before recheck");
            Files.write(upper,tiny);
            changed=LicenseRecoverReadOnlyDotNetCodeEvidence.inspectReviewed(root.toFile(),()->{
                try{Files.write(lower,tiny);}catch(IOException e){throw new RuntimeException(e);}
            });
            check(changed.component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "a newly appearing optional registration component invalidates the prior observation");Files.delete(lower);
            Files.write(upper,tiny);
            try(RandomAccessFile file=new RandomAccessFile(upper.toFile(),"rw")){file.setLength(16L*1024*1024);}
            candidate(root,"exact 16MiB input budget remains bounded and readable");
            try(RandomAccessFile file=new RandomAccessFile(upper.toFile(),"rw")){file.setLength(16L*1024*1024+1);}
            unassessed(root,"over-limit component is rejected before allocating its advertised size");
            Files.write(upper,tiny);Path external=root.resolve("external.dll");Files.write(external,tiny);Files.delete(upper);
            boolean linked=false;
            try{Files.createSymbolicLink(upper,external);linked=true;}
            catch(UnsupportedOperationException|IOException|SecurityException unsupported){System.out.println("SKIP: platform cannot create the synthetic symlink");}
            if(linked)unassessed(root,"linked components cannot acquire a replacement candidate");
            check(LicenseRecoverReadOnlyDotNetCodeEvidence.inspect(null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "missing application context remains unassessed");
            System.out.println("PASS: strict .NET method-body candidates without Patch promotion ("+checks+" checks)");
        } finally {
            try(java.util.stream.Stream<Path> files=Files.walk(root)) {
                for(Path path:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }
}
