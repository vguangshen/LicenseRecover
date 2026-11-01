import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Synthetic file-only probes. Native state factories are covered separately; no target code runs. */
public final class LicenseRecoverReadOnlyInspectionTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static void jar(Path p,boolean valid) throws Exception {
        Files.createDirectories(p.getParent());
        try(JarOutputStream j=new JarOutputStream(Files.newOutputStream(p))) {
            j.putNextEntry(new JarEntry("sample.class"));
            j.write(valid?new byte[]{(byte)0xca,(byte)0xfe,(byte)0xba,(byte)0xbe,0,0,0,52}:new byte[]{1,2});j.closeEntry();
        }
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("lr-evidence-");
        try {
            Path app=root.resolve("sample"), lib=app.resolve("WEB-INF/lib");
            Files.createDirectories(app.resolve("data"));
            Files.write(app.resolve("data/config.xml"),"<root><SoftVersionID>TEST01</SoftVersionID><RegStr>PRIVATE_SENTINEL</RegStr></root>".getBytes("UTF-8"));
            Path artifact=lib.resolve("ITMCReg.jar");jar(artifact,true);
            byte[] before=Files.readAllBytes(artifact);
            LicenseRecoverReadOnlyInspection.Result r=LicenseRecoverReadOnlyInspection.inspect(app.toFile());
            check(r.state.visualStatus()==LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,"unknown real files stay gray");
            check(r.artifacts.size()==1&&r.artifacts.get(0).sha256.matches("[0-9a-f]{64}"),"current hash evidence");
            check(r.artifacts.get(0).format.contains("minimum-Java=8"),"bytecode minimum is not deployed JVM version");
            check(r.inspectionReadOnlyAudited&&!r.nativeVerifierReadOnlyAudited,"two distinct safety scopes");
            check(r.reason.contains("静态门槛〔")&&r.reason.contains("本地谓词〔")&&r.reason.contains("备用入口〔"),
                    "unknown Java inspection must retain all attempted strategy reasons");
            check(!r.toJson().contains("PRIVATE_SENTINEL")&&!r.toJson().contains(root.toString()),"no license values or absolute paths");
            check(Arrays.equals(before,Files.readAllBytes(artifact)),"read-only artifact");
            jar(artifact,false);
            check(LicenseRecoverReadOnlyInspection.inspect(app.toFile()).state.isScanError(),"damaged class is scan error");
            Files.write(artifact,new byte[]{1,2,3});
            check(LicenseRecoverReadOnlyInspection.inspect(app.toFile()).state.isScanError(),"damaged JAR is scan error");
            Files.delete(artifact);
            LicenseRecoverLicenseState unknown=LicenseRecoverLicenseState.unverified("no baseline");
            check(LicenseRecoverReadOnlyInspection.inspect(app.toFile(),lib.toFile(),
                    LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA,"TEST01",unknown).state.visualStatus()
                    ==LicenseRecoverLicenseState.VisualStatus.UNVERIFIED,"missing evidence gray");
            Files.delete(lib);
            Files.write(lib,new byte[]{1});
            check(LicenseRecoverReadOnlyInspection.inspect(app.toFile(),lib.toFile(),
                    LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA,"TEST01",unknown).state.isScanError(),"non-directory IO error red");
            Path net=root.resolve("net"),bin=net.resolve("bin");Files.createDirectories(bin);
            byte[] pe=LicenseRecoverReadOnlyPeMetadataTest.fixture(false);
            Files.write(bin.resolve("ITMC.Web.dll"),pe);Files.write(bin.resolve("ITMC.Regedit.dll"),pe);
            LicenseRecoverReadOnlyInspection.Result n=LicenseRecoverReadOnlyInspection.inspect(net.toFile());
            check(n.artifacts.size()==2&&!n.state.isScanError(),"CLI metadata observation accepted");
            check(n.artifacts.get(0).format.contains("assembly-version=2.7.19.4"),"assembly version evidence");
            check(n.state.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED,"PE is not native-valid");
            Files.write(bin.resolve("ITMC.Regedit.dll"),new byte[]{0});
            check(LicenseRecoverReadOnlyInspection.inspect(net.toFile()).state.isScanError(),"damaged PE red");
            check(!r.toJson().contains("nativeVerifierReadOnlyAudited\":true"),"native safety cannot be fabricated");
            check(!LicenseRecoverReadOnlyInspection.sameCodeEvidence(
                    LicenseRecoverLicenseState.codeComparison(true,"old hash"),
                    LicenseRecoverLicenseState.codeComparison(true,"new hash")),"same state with changed evidence must not survive");
            check(!LicenseRecoverReadOnlyInspection.sameCodeEvidence(
                    LicenseRecoverLicenseState.codeComparison(true,"hash"),unknown),"changed code state must not survive");
            LicenseRecoverLicenseState unresolved=LicenseRecoverReadOnlyInspection.unverifiedJavaEvidence(
                    LicenseRecoverLicenseState.unverified("static entry not reviewed"),
                    LicenseRecoverLicenseState.unverified("local build version unsupported"),
                    LicenseRecoverLicenseState.unverified("bootstrap resource set differs from reviewed receipt"));
            LicenseRecoverReadOnlyInspection.Result applicability=new LicenseRecoverReadOnlyInspection.Result(
                    app.toFile(),"TEST01","JAVA",unresolved,Collections.<LicenseRecoverReadOnlyInspection.Artifact>emptyList(),unresolved.evidence);
            check(applicability.toJson().contains("local build version unsupported")
                    &&applicability.toJson().contains("bootstrap resource set differs from reviewed receipt"),
                    "specific version and resource mismatch diagnostics must not be discarded for a generic gate reason");
            check(unresolved.visualStatus()==LicenseRecoverLicenseState.VisualStatus.UNVERIFIED
                    &&unresolved.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED
                    &&unresolved.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN
                    &&unresolved.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                    &&unresolved.localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED,
                    "retaining applicability reasons cannot invent authorization or Patch evidence");
            for(LicenseRecoverLicenseState verdict:new LicenseRecoverLicenseState[]{
                    LicenseRecoverLicenseState.authorizationAcceptance("synthetic independently audited acceptance"),
                    LicenseRecoverLicenseState.scanError("synthetic read error"),
                    LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"synthetic local observation"),
                    LicenseRecoverLicenseState.nativeVerification(false,"synthetic native rejection"),
                    LicenseRecoverLicenseState.codeComparison(true,"synthetic reviewed comparison")}) {
                boolean refused=false;
                try {LicenseRecoverReadOnlyInspection.unverifiedJavaEvidence(unknown,verdict,unknown);}
                catch(IllegalArgumentException expected){refused=true;}
                check(refused,"applicability aggregation must not downgrade stronger evidence or errors");
            }
            java.util.List<LicenseRecoverReadOnlyInspection.Artifact> observed=java.util.Collections.singletonList(
                    new LicenseRecoverReadOnlyInspection.Artifact("ITMCReg.jar",
                    "ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa","JAR",1));
            check(LicenseRecoverReadOnlyVerifierSafety.rejection(observed).startsWith("REJECTED_SIDE_EFFECTS"),
                    "observed unsafe hash denies execution without establishing original/Patch provenance");
            System.out.println("PASS: file evidence, damaged/missing/unknown and privacy boundaries");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});
            }
        }
    }
}
