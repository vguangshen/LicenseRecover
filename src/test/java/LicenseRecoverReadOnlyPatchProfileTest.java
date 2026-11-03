import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Synthetic byte fixtures only. No vendor binaries, target execution or production baselines. */
public final class LicenseRecoverReadOnlyPatchProfileTest {
    private static int checks;
    private static final LicenseRecoverReadOnlyPatchScanner.Runtime JAVA=LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA;
    private static final LicenseRecoverReadOnlyPatchScanner.Runtime DOTNET=LicenseRecoverReadOnlyPatchScanner.Runtime.DOTNET;
    private static final String MODE="MODE_X",CLASS="WEB-INF/classes/example/RegisterGate.class",JAR="WEB-INF/lib/ITMCReg.jar",WEB="WEB-INF/web.xml";
    private static final String ORIGINAL="synthetic-original-class",PATCHED="synthetic-patched-class",ORIG_JAR="synthetic-original-jar",PATCH_JAR="synthetic-patched-jar";
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static String hash(String value)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();
        for(byte x:digest)b.append(Character.forDigit((x>>>4)&15,16)).append(Character.forDigit(x&15,16));return b.toString();
    }
    private static LicenseRecoverReadOnlyPatchProfile.FixedBinding fixed(String role,String path,String contents)throws Exception {
        return new LicenseRecoverReadOnlyPatchProfile.FixedBinding(role,path,hash(contents));
    }
    private static LicenseRecoverReadOnlyPatchProfile.TargetPair target(String role,String path,String original,String patched)throws Exception {
        return new LicenseRecoverReadOnlyPatchProfile.TargetPair(role,path,hash(original),hash(patched));
    }
    private static LicenseRecoverReadOnlyPatchProfile profile(String id,String build,String recipe,String startup,
            String original,String patched,String originalJar,String patchedJar)throws Exception {
        return new LicenseRecoverReadOnlyPatchProfile(id,JAVA,MODE,build,recipe,"docs/synthetic-audit.md",
                new LicenseRecoverReadOnlyPatchProfile.FixedBinding[]{fixed("startup",WEB,startup)},
                new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("application-class",CLASS,original,patched),target("registration-jar",JAR,originalJar,patchedJar)});
    }
    private static void write(Path root,String relative,String contents)throws Exception {
        Path p=root.resolve(relative);Files.createDirectories(p.getParent());Files.write(p,contents.getBytes(StandardCharsets.UTF_8));
    }
    private static void original(Path root)throws Exception {write(root,WEB,"startup-a");write(root,CLASS,ORIGINAL);write(root,JAR,ORIG_JAR);}
    private static void patched(Path root)throws Exception {write(root,WEB,"startup-a");write(root,CLASS,PATCHED);write(root,JAR,PATCH_JAR);}
    private static LicenseRecoverLicenseState scan(Path root,LicenseRecoverReadOnlyPatchProfile... profiles) {
        return LicenseRecoverReadOnlyPatchScanner.scanApplicationReviewed(root.toFile(),JAVA,MODE,profiles);
    }
    private static void state(Path root,LicenseRecoverLicenseState.CodeIntegrity expected,String why,LicenseRecoverReadOnlyPatchProfile... profiles) {
        LicenseRecoverLicenseState state=scan(root,profiles);
        check(state.codeIntegrity==expected&&!state.isScanError(),why+": "+state.evidence);
        check(state.authorization==LicenseRecoverLicenseState.Authorization.UNVERIFIED&&state.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED,
                "code comparisons never manufacture activation or current-entry acceptance");
    }
    private static void schema(Path root,String why,LicenseRecoverReadOnlyPatchProfile... profiles) {
        check(scan(root,profiles).isScanError(),why);
    }
    public static void main(String[] args)throws Exception {
        Path sandbox=Files.createTempDirectory("lr-patch-profiles-"),root=Files.createDirectories(sandbox.resolve("app"));
        try {
            LicenseRecoverReadOnlyPatchProfile a=profile("profile-a","audited-build-a","recipe-a","startup-a",ORIGINAL,PATCHED,ORIG_JAR,PATCH_JAR);
            original(root);state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,"application-root original class and JAR match",a);
            patched(root);state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,"class outside libDir and registration JAR form one complete Patch",a);
            check(scan(root,a).evidence.contains("build=audited-build-a")&&scan(root,a).evidence.contains("audit=docs/synthetic-audit.md"),"build/recipe/provenance remain traceable");
            check(!scan(root,a).evidence.contains("版本 "+MODE),"product mode is not a vendor release version");
            state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"production catalog contains no synthetic or invented pair");
            check(LicenseRecoverReadOnlyPatchScanner.scanApplication(root.toFile(),JAVA,MODE).codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"public production scanner remains unverified without audited samples");
            check(LicenseRecoverReadOnlyPatchScanner.applicationArtifactPaths(JAVA,MODE).isEmpty(),"empty production catalog exports no synthetic path");
            check(LicenseRecoverReadOnlyPatchScanner.scanApplicationReviewed(new File("\0bad-root"),JAVA,MODE,new LicenseRecoverReadOnlyPatchProfile[]{a}).isScanError(),
                    "invalid application-root syntax is a scan error rather than an uncaught exception");
            check(LicenseRecoverReadOnlyPatchScanner.scanApplicationReviewed(root.toFile(),JAVA,"audited-build-a",new LicenseRecoverReadOnlyPatchProfile[]{a}).codeIntegrity
                    ==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"build identity cannot substitute for product/mode applicability");
            write(root,CLASS,ORIGINAL);state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"mixed original/Patch target group is incomplete",a);
            Files.delete(root.resolve(JAR));state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"missing required target cannot be hidden by another match",a);
            write(root,JAR,PATCH_JAR);write(root,CLASS,"damaged-or-unreviewed-class");state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"unknown bytes are not proof of a Patch or invalid authorization",a);
            patched(root);write(root,WEB,"different startup wiring");state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"fixed startup binding must match",a);
            original(root);

            LicenseRecoverReadOnlyPatchProfile b=profile("profile-b","audited-build-b","recipe-a","startup-b","original-b","patched-b","jar-b","jar-patched-b");
            state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,"different builds are alternatives instead of one impossible AND set",b,a);
            write(root,WEB,"startup-b");write(root,CLASS,"patched-b");write(root,JAR,"jar-patched-b");
            state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,"same product's second audited build is recognized",a,b);
            LicenseRecoverReadOnlyPatchProfile recipe=profile("profile-recipe-b","audited-build-a","recipe-b","startup-a",ORIGINAL,"another-patch",ORIG_JAR,"another-jar-patch");
            original(root);state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,"multiple recipes sharing exactly one original build can merge",recipe,a);
            patched(root);state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,"an unmatched alternative recipe does not invalidate a complete match",recipe,a);
            write(root,CLASS,"another-patch");write(root,JAR,"another-jar-patch");state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,"second recipe is not hidden by first-match selection",a,recipe);
            LicenseRecoverReadOnlyPatchProfile ambiguousPatch=profile("ambiguous-patch","audited-build-a","alias-recipe","startup-a",ORIGINAL,PATCHED,ORIG_JAR,PATCH_JAR);
            patched(root);state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"multiple matching Patch recipes require provenance resolution",a,ambiguousPatch);
            state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"ambiguous outcome is independent of order",ambiguousPatch,a);
            LicenseRecoverReadOnlyPatchProfile ambiguousOriginal=profile("ambiguous-original","another-build","recipe-a","startup-a",ORIGINAL,PATCHED,ORIG_JAR,PATCH_JAR);
            original(root);state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"same bytes with conflicting build identities do not choose a build",a,ambiguousOriginal);
            LicenseRecoverReadOnlyPatchProfile conflict=profile("conflicting-origin","other-build","recipe-z","startup-a","older-original",ORIGINAL,"older-jar",ORIG_JAR);
            state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"original versus Patch interpretation is ambiguous",a,conflict);

            List<String> paths=LicenseRecoverReadOnlyPatchScanner.applicationArtifactPathsReviewed(JAVA,MODE,new LicenseRecoverReadOnlyPatchProfile[]{a,recipe,b});
            check(paths.size()==3&&paths.contains(CLASS)&&paths.contains(JAR)&&paths.contains(WEB),"artifact discovery includes all root-relative targets and fixed bindings, deduplicated");
            try{paths.add("unsafe");throw new AssertionError("immutable paths");}catch(UnsupportedOperationException expected){checks++;}
            check(LicenseRecoverReadOnlyPatchScanner.applicationArtifactPathsReviewed(DOTNET,MODE,new LicenseRecoverReadOnlyPatchProfile[]{a}).isEmpty(),"artifact paths are runtime scoped");
            changeChecks(root,a);
            schemaChecks(root,a);
            aliasChecks(sandbox,root,a);
            readErrorChecks(root);
            immutableChecks(root);
            System.out.println("PASS: application-root grouped Patch profiles, provenance, alternatives, stable receipts and read-only paths ("+checks+" checks)");
        }finally{cleanup(sandbox);}
    }
    private static void changeChecks(Path root,LicenseRecoverReadOnlyPatchProfile profile)throws Exception {
        patched(root);LicenseRecoverReadOnlyPatchProfile[] profiles={profile};
        LicenseRecoverReadOnlyPatchScanner.ProfileSnapshot first=LicenseRecoverReadOnlyPatchScanner.applicationSnapshotReviewed(root.toFile(),JAVA,MODE,profiles);
        write(root,CLASS,"changed during scan");
        check(LicenseRecoverReadOnlyPatchScanner.recheckApplicationReviewed(root.toFile(),JAVA,MODE,profiles,first).codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"changed class revokes first-pass Patch");
        original(root);first=LicenseRecoverReadOnlyPatchScanner.applicationSnapshotReviewed(root.toFile(),JAVA,MODE,profiles);
        write(root,WEB,"changed binding");
        check(LicenseRecoverReadOnlyPatchScanner.recheckApplicationReviewed(root.toFile(),JAVA,MODE,profiles,first).codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"changed fixed binding revokes original");
        original(root);Files.delete(root.resolve(JAR));first=LicenseRecoverReadOnlyPatchScanner.applicationSnapshotReviewed(root.toFile(),JAVA,MODE,profiles);
        write(root,JAR,ORIG_JAR);
        check(LicenseRecoverReadOnlyPatchScanner.recheckApplicationReviewed(root.toFile(),JAVA,MODE,profiles,first).codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"a newly appearing target cannot upgrade a stale incomplete scan");
        original(root);first=LicenseRecoverReadOnlyPatchScanner.applicationSnapshotReviewed(root.toFile(),JAVA,MODE,profiles);
        profiles[0]=profile("new-profile","audited-build-a","recipe-a","startup-a",ORIGINAL,PATCHED,ORIG_JAR,PATCH_JAR);
        check(LicenseRecoverReadOnlyPatchScanner.recheckApplicationReviewed(root.toFile(),JAVA,MODE,profiles,first).codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"changing catalog evidence revokes the receipt");
        check(LicenseRecoverReadOnlyPatchScanner.recheckApplicationReviewed(root.toFile(),JAVA,MODE,new LicenseRecoverReadOnlyPatchProfile[]{profile},null).codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"missing first-pass receipt cannot authorize a comparison");
    }
    private static void schemaChecks(Path root,LicenseRecoverReadOnlyPatchProfile valid)throws Exception {
        original(root);LicenseRecoverReadOnlyPatchProfile.FixedBinding[] fixed={fixed("startup",WEB,"startup-a")};
        LicenseRecoverReadOnlyPatchProfile.TargetPair good=target("class",CLASS,ORIGINAL,PATCHED);
        for(String path:new String[]{"../escape.class","/absolute.class","C:relative.class","bin/file.dll:stream","WEB-INF/./classes/X.class","WEB-INF/classes/../X.class","WEB-INF//classes/X.class","WEB-INF\\classes\\X.class","bin/CON.dll","bin/file.dll."," bin/file.dll","bin/control\n.class"}) {
            schema(root,"unsafe or aliased manifest path is a schema error: "+path,new LicenseRecoverReadOnlyPatchProfile("bad-path",JAVA,MODE,"build","recipe","docs/audit.md",fixed,
                    new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("class",path,ORIGINAL,PATCHED)}));
        }
        schema(root,"null record cannot be skipped",valid,null);
        schema(root,"missing fixed array",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",null,new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{good}));
        schema(root,"missing target array",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",fixed,null));
        schema(root,"empty target array",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",fixed,new LicenseRecoverReadOnlyPatchProfile.TargetPair[0]));
        schema(root,"invalid provenance reference",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit\n.md",fixed,new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{good}));
        schema(root,"missing build id",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,null,"recipe","docs/audit.md",fixed,new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{good}));
        schema(root,"duplicate profile id",valid,profile(valid.profileId,"another-build","another-recipe","startup-a",ORIGINAL,PATCHED,ORIG_JAR,PATCH_JAR));
        schema(root,"duplicate build/recipe group",valid,profile("another-id",valid.buildId,valid.recipeId,"startup-a",ORIGINAL,PATCHED,ORIG_JAR,PATCH_JAR));
        schema(root,"duplicate roles",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",fixed,
                new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("startup",CLASS,ORIGINAL,PATCHED)}));
        schema(root,"case-only duplicate paths",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",fixed,
                new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{good,target("other",CLASS.toUpperCase(Locale.ROOT),ORIGINAL,PATCHED)}));
        schema(root,"target repeats fixed path",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",fixed,
                new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("other",WEB,ORIGINAL,PATCHED)}));
        schema(root,"equal original/modified hashes are not a Patch pair",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",fixed,
                new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("class",CLASS,ORIGINAL,ORIGINAL)}));
        schema(root,"invalid fixed hash",new LicenseRecoverReadOnlyPatchProfile("bad",JAVA,MODE,"build","recipe","docs/audit.md",
                new LicenseRecoverReadOnlyPatchProfile.FixedBinding[]{new LicenseRecoverReadOnlyPatchProfile.FixedBinding("startup",WEB,"not-a-hash")},new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{good}));
        schema(root,"schema errors in nonapplicable rows cannot be silently dropped",valid,new LicenseRecoverReadOnlyPatchProfile("bad",DOTNET,"OTHER","build",null,"docs/audit.md",fixed,new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{good}));
    }
    private static void aliasChecks(Path sandbox,Path root,LicenseRecoverReadOnlyPatchProfile valid)throws Exception {
        patched(root);Path hard=root.resolve("WEB-INF/lib/Alias.jar");
        try {
            Files.createLink(hard,root.resolve(CLASS));
            LicenseRecoverReadOnlyPatchProfile alias=new LicenseRecoverReadOnlyPatchProfile("hardlink",JAVA,MODE,"build","recipe","docs/audit.md",
                    new LicenseRecoverReadOnlyPatchProfile.FixedBinding[]{fixed("startup",WEB,"startup-a")},
                    new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("class",CLASS,ORIGINAL,PATCHED),target("jar","WEB-INF/lib/Alias.jar",ORIGINAL,PATCHED)});
            state(root,LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN,"hard-link targets are not two independent artifacts",alias);
        }catch(UnsupportedOperationException|FileSystemException unavailable){System.out.println("SKIP: filesystem hard-link fixture unavailable");}
        Path relative=root.resolve("WEB-INF/linked-classes"),rootAlias=sandbox.resolve("linked-app");
        try {
            Files.createSymbolicLink(relative,root.resolve("WEB-INF/classes"));
            LicenseRecoverReadOnlyPatchProfile linked=new LicenseRecoverReadOnlyPatchProfile("symlink",JAVA,MODE,"build","recipe","docs/audit.md",
                    new LicenseRecoverReadOnlyPatchProfile.FixedBinding[]{fixed("startup",WEB,"startup-a")},
                    new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("class","WEB-INF/linked-classes/example/RegisterGate.class",ORIGINAL,PATCHED)});
            check(scan(root,linked).isScanError(),"symbolic-link ancestor cannot confirm code provenance");
            Files.createSymbolicLink(rootAlias,root);
            check(scan(rootAlias,valid).isScanError(),"symbolic-link application root cannot confirm code provenance");
        }catch(UnsupportedOperationException|FileSystemException|SecurityException unavailable){System.out.println("SKIP: filesystem symbolic-link fixture unavailable");}
    }
    private static void readErrorChecks(Path root)throws Exception {
        original(root);String path="bin/broken.dll";Files.createDirectories(root.resolve(path));
        LicenseRecoverReadOnlyPatchProfile p=new LicenseRecoverReadOnlyPatchProfile("bad-file",JAVA,MODE,"build","recipe","docs/audit.md",
                new LicenseRecoverReadOnlyPatchProfile.FixedBinding[]{fixed("startup",WEB,"startup-a")},new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("binary",path,ORIGINAL,PATCHED)});
        check(scan(root,p).isScanError(),"a required non-file is a read error, not invalid authorization");
        Path large=root.resolve("bin/oversized.dll");try(RandomAccessFile file=new RandomAccessFile(large.toFile(),"rw")){file.setLength(128L*1024*1024+1);}
        try {
            p=new LicenseRecoverReadOnlyPatchProfile("large-file",JAVA,MODE,"build","recipe","docs/audit.md",
                    new LicenseRecoverReadOnlyPatchProfile.FixedBinding[]{fixed("startup",WEB,"startup-a")},new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("binary","bin/oversized.dll",ORIGINAL,PATCHED)});
            check(scan(root,p).isScanError(),"oversized target is rejected before hashing or allocation");
        }finally{Files.delete(large);}
        write(root,"bin/ITMC.Regedit.dll",PATCHED);
        p=new LicenseRecoverReadOnlyPatchProfile("net-profile",DOTNET,MODE,"net-audited-build","net-recipe","docs/synthetic-net-audit.md",
                new LicenseRecoverReadOnlyPatchProfile.FixedBinding[]{fixed("startup",WEB,"startup-a")},new LicenseRecoverReadOnlyPatchProfile.TargetPair[]{target("registration","bin/ITMC.Regedit.dll",ORIGINAL,PATCHED)});
        check(LicenseRecoverReadOnlyPatchScanner.scanApplicationReviewed(root.toFile(),DOTNET,MODE,new LicenseRecoverReadOnlyPatchProfile[]{p}).codeIntegrity
                ==LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED,"the same grouped engine supports .NET bin targets");
    }
    private static void immutableChecks(Path root)throws Exception {
        original(root);LicenseRecoverReadOnlyPatchProfile.FixedBinding[] fixed={fixed("startup",WEB,"startup-a")};
        LicenseRecoverReadOnlyPatchProfile.TargetPair[] targets={target("class",CLASS,ORIGINAL,PATCHED)};
        LicenseRecoverReadOnlyPatchProfile p=new LicenseRecoverReadOnlyPatchProfile("immutable",JAVA,MODE,"build","recipe","docs/audit.md",fixed,targets);
        fixed[0]=null;targets[0]=null;
        state(root,LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL,"caller array mutation cannot alter compiled profile evidence",p);
        try{p.targets.clear();throw new AssertionError("immutable target list");}catch(UnsupportedOperationException expected){checks++;}
        try{p.fixedBindings.clear();throw new AssertionError("immutable fixed list");}catch(UnsupportedOperationException expected){checks++;}
    }
    private static void cleanup(Path sandbox)throws Exception {
        Path boundary=Paths.get(System.getProperty("java.io.tmpdir")).toRealPath(),resolved=sandbox.toRealPath();
        if(!resolved.getParent().equals(boundary)||!resolved.getFileName().toString().startsWith("lr-patch-profiles-"))throw new AssertionError("unsafe fixture cleanup path");
        try(java.util.stream.Stream<Path> files=Files.walk(resolved)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException error){throw new RuntimeException(error);}});}
    }
}
