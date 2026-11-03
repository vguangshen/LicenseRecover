import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Synthetic references only; reference-component identity is not historical Patch proof. */
public final class LicenseRecoverReadOnlyJavaCodeEvidenceTest {
    private static int checks;
    private static final String SOURCE="synthetic-component-reference";
    private static final LicenseRecoverLicenseState UNKNOWN=LicenseRecoverLicenseState.unverified("not audited");
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    public static final class FakeSuccessClass {
        static {System.setProperty("lr.java-code-evidence-target","executed");}
        public boolean checkReInfo(){return false;}
        public boolean CheckNet(){return false;}
    }

    public static void main(String[] args)throws Exception {
        Path temp=Files.createTempDirectory("lr-java-code-evidence-");
        String prior=System.getProperty("lr.java-code-evidence-target");
        try {
            System.clearProperty("lr.java-code-evidence-target");
            Path root=temp.resolve("sample"),lib=root.resolve("WEB-INF/lib"),jar=lib.resolve("ITMCReg.jar");
            Files.createDirectories(lib);
            writeFakeJar(jar);
            byte[] original=Files.readAllBytes(jar);
            String hash=hash(jar);
            LicenseRecoverReadOnlyCodeEvidence matched=review(root,hash,null);
            check(matched.component==LicenseRecoverReadOnlyCodeEvidence.Component.REFERENCE_COMPONENT_MATCH,
                    "reviewed component bytes may match a component reference");
            check(matched.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED,
                    "reference component does not establish actual gate behavior");
            check(matched.componentEvidence.contains("仅组件")&&matched.componentEvidence.contains("不证明整站原版")
                    &&matched.componentEvidence.contains("来源为用户说明"),"component and provenance scope remain explicit");
            check(matched.evidenceSource.equals(SOURCE)&&matched.readOnlyAudited,"reference source and readonly audit retained");
            check(hash.equals(matched.componentHashes.get("WEB-INF/lib/ITMCReg.jar")),
                    "reference proof carries its root-relative content receipt");
            boolean immutable=false;try{matched.componentHashes.clear();}catch(UnsupportedOperationException expected){immutable=true;}
            check(immutable,"component receipts cannot be changed after proof");
            check(LicenseRecoverReadOnlyJavaCodeEvidence.inspect(root.toFile()).component
                    ==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "fake success method names and constants cannot match the fixed production J1 baseline");
            check(System.getProperty("lr.java-code-evidence-target")==null,"component comparison never initializes target class");
            check(review(root,repeat('0',64),null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "unknown component is not a Patch candidate or original");

            FileTime oldTime=Files.getLastModifiedTime(jar);
            LicenseRecoverReadOnlyCodeEvidence changed=review(root,hash,()->{
                try {byte[] bytes=original.clone();bytes[bytes.length-1]^=1;Files.write(jar,bytes);Files.setLastModifiedTime(jar,oldTime);}
                catch(IOException e){throw new RuntimeException(e);}
            });
            check(changed.component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "same-size same-mtime mutation between hashes revokes reference");
            check(changed.componentHashes.isEmpty(),"a revoked component keeps no old receipt");
            Files.write(jar,original);
            Path second=lib.resolve("ITMCReg-1.0.jar");Files.write(second,original);
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "multiple registration JARs cannot select a convenient matching component");
            Files.delete(second);
            check(review(root,hash,()->{try{Files.write(second,original);}catch(IOException e){throw new RuntimeException(e);}}).component
                    ==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,"a new registration JAR between reads revokes reference");
            Files.delete(second);
            Path alias=lib.resolve("unrelated-name.jar");Files.createLink(alias,jar);
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "same-file alias under a different JAR name revokes reference");
            Files.delete(alias);
            writeFakeJar(alias);
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "registration entry in a differently named JAR also makes component selection ambiguous");
            Files.delete(alias);
            try(JarOutputStream output=new JarOutputStream(Files.newOutputStream(alias))) {
                output.putNextEntry(new JarEntry("unrelated/Ordinary.class"));output.write(new byte[]{1,2,3});output.closeEntry();
            }
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.REFERENCE_COMPONENT_MATCH,
                    "bounded ordinary dependency metadata does not hide the unique reference component");
            Files.write(alias,"invalid ZIP metadata".getBytes(StandardCharsets.US_ASCII));
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "unreadable dependency metadata cannot prove absence of a second registration entry");
            try(RandomAccessFile large=new RandomAccessFile(alias.toFile(),"rw")){large.setLength(129L*1024*1024);}
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "oversized dependency metadata is rejected without inflating its entries");
            Files.delete(alias);
            try {
                Files.createSymbolicLink(alias,jar.getFileName());
                check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                        "NOFOLLOW rejects a linked dependency JAR");
            }catch(UnsupportedOperationException|SecurityException|IOException unavailable) {
                System.out.println("SKIP: symbolic-link creation unavailable on this test filesystem");
            }finally {Files.deleteIfExists(alias);}
            Files.delete(jar);Files.createDirectory(jar);
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "directory named ITMCReg.jar is not component evidence");
            Files.delete(jar);Files.write(jar,original);
            Path moved=temp.resolve("moved.jar");
            check(review(root,hash,()->{try{Files.move(jar,moved);Files.write(jar,original);Files.setLastModifiedTime(jar,oldTime);}
                    catch(IOException e){throw new RuntimeException(e);}}).component
                    ==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,"replacement file identity cannot retain an earlier reference");
            Files.delete(moved);
            try(RandomAccessFile large=new RandomAccessFile(jar.toFile(),"rw")){large.setLength(16L*1024*1024+1);}
            check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED,
                    "over-budget component is rejected before hashing a candidate");
            Files.write(jar,original);
            Thread.currentThread().interrupt();
            try {check(review(root,hash,null).component==LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED
                    &&Thread.currentThread().isInterrupted(),"cancellation remains unassessed and preserves interruption");}
            finally {Thread.interrupted();}
            orchestration(root,matched);
            check(Arrays.equals(original,Files.readAllBytes(jar)),"pipeline fixture is unchanged by scanning");
            check(System.getProperty("lr.java-code-evidence-target")==null,"all evidence channels leave target initialization untouched");
            System.out.println("PASS: Java code evidence preserves component scope, bounded stable reads and actual gate provenance ("+checks+" checks)");
        }finally {
            if(prior==null)System.clearProperty("lr.java-code-evidence-target");else System.setProperty("lr.java-code-evidence-target",prior);
            try(java.util.stream.Stream<Path> paths=Files.walk(temp)) {
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }

    private static void orchestration(Path root,LicenseRecoverReadOnlyCodeEvidence component)throws Exception {
        LicenseRecoverLicenseState accepted=LicenseRecoverLicenseState.authorizationAcceptance("synthetic complete audited gate");
        List<String> order=new ArrayList<String>();
        LicenseRecoverReadOnlyJavaScanner.Result gate=LicenseRecoverReadOnlyJavaScanner.inspectWith(root.toFile(),
                file->{order.add("gate");return accepted;},file->{throw new AssertionError("short circuit local");},
                file->{throw new AssertionError("short circuit startup");},file->{order.add("component");return component;});
        check(order.equals(Arrays.asList("component","gate")),"component reads precede complete static proof; none follow it");
        check(gate.state==accepted&&gate.codeEvidence.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.AUDITED_STATIC_BYPASS,
                "only decisive gate-provider acceptance establishes audited static bypass");
        check(gate.codeEvidence.component==LicenseRecoverReadOnlyCodeEvidence.Component.REFERENCE_COMPONENT_MATCH,
                "gate behavior and component fact remain independent");
        check(gate.state.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED
                &&gate.state.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,
                "current static bypass never claims native activation or a historical Patch pair");
        LicenseRecoverLicenseState misleading=LicenseRecoverLicenseState.unverified("静态授权门槛证明 DS24-static-gate-20261030");
        LicenseRecoverReadOnlyJavaScanner.Result local=LicenseRecoverReadOnlyJavaScanner.inspectWith(root.toFile(),
                file->misleading,file->accepted,file->{throw new AssertionError("local short circuit");},file->component);
        check(local.state==accepted&&local.codeEvidence.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED,
                "ordinary local acceptance and convincing text cannot create a bypass fact");
        LicenseRecoverReadOnlyJavaScanner.Result startup=LicenseRecoverReadOnlyJavaScanner.inspectWith(root.toFile(),
                file->UNKNOWN,file->UNKNOWN,file->accepted,file->component);
        check(startup.state==accepted&&startup.codeEvidence.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED,
                "alternate acceptance is not a static bypass");
        for(LicenseRecoverLicenseState decisive:new LicenseRecoverLicenseState[]{LicenseRecoverLicenseState.scanError("synthetic error"),
                LicenseRecoverLicenseState.authorizationRejection("synthetic rejection",LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH)}) {
            LicenseRecoverReadOnlyJavaScanner.Result result=LicenseRecoverReadOnlyJavaScanner.inspectWith(root.toFile(),
                    file->decisive,file->accepted,file->accepted,file->component);
            check(result.state==decisive&&result.codeEvidence.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED,
                    "gate errors/rejection cannot establish bypass");
        }
        Path anchor=root.resolve("gate-anchor");Files.write(anchor,new byte[]{1});
        LicenseRecoverReadOnlyJavaScanner.Result mutation=LicenseRecoverReadOnlyJavaScanner.inspectWith(root.toFile(),
                file->{try{return Files.readAllBytes(anchor)[0]==1?accepted:UNKNOWN;}catch(IOException e){throw new RuntimeException(e);}},
                file->UNKNOWN,file->UNKNOWN,file->{try{Files.write(anchor,new byte[]{2});}catch(IOException e){throw new RuntimeException(e);}return component;});
        check(mutation.state.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                &&mutation.codeEvidence.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED,
                "code change during component observation is seen by subsequent gate proof and cannot retain green");
        Files.delete(anchor);
        check(LicenseRecoverReadOnlyJavaScanner.inspect(null).codeEvidence.behavior==LicenseRecoverReadOnlyCodeEvidence.Behavior.UNASSESSED,
                "absent production root cannot establish bypass");
    }

    private static LicenseRecoverReadOnlyCodeEvidence review(Path root,String hash,Runnable hook) {
        return LicenseRecoverReadOnlyJavaCodeEvidence.inspectReviewed(root.toFile(),hash,SOURCE,hook);
    }
    private static void writeFakeJar(Path jar)throws IOException {
        try(JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar));
                InputStream input=LicenseRecoverReadOnlyJavaCodeEvidenceTest.class.getResourceAsStream(
                    "LicenseRecoverReadOnlyJavaCodeEvidenceTest$FakeSuccessClass.class")) {
            if(input==null)throw new IOException("synthetic target resource missing");
            output.putNextEntry(new JarEntry("itmc/regedit/RegisterMain.class"));
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
            output.closeEntry();
        }
    }
    private static String hash(Path file)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(Files.readAllBytes(file));
        StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));return out.toString();
    }
    private static String repeat(char c,int count){char[] chars=new char[count];Arrays.fill(chars,c);return new String(chars);}
}
