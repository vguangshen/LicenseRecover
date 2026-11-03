import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Read-only integration point for reviewed Java/.NET patch fingerprints.
 *
 * Only pairs compiled into REVIEWED may authorize a "confirmed patch" result.
 * Local config, previous recovery records, user-controlled manifests and a
 * missing network marker cannot populate this registry. An empty registry
 * deliberately leaves real applications UNVERIFIED.
 */
public final class LicenseRecoverReadOnlyPatchScanner {
    public enum Runtime { JAVA, DOTNET }

    static final class Reviewed {
        final Runtime runtime;
        final String version;
        final String relativeArtifact;
        final String originalSha256;
        final String patchedSha256;

        Reviewed(Runtime runtime, String version, String relativeArtifact,
                 String originalSha256, String patchedSha256) {
            this.runtime = runtime;
            this.version = version;
            this.relativeArtifact = relativeArtifact;
            this.originalSha256 = originalSha256;
            this.patchedSha256 = patchedSha256;
        }
    }

    // No real-world patch pair has yet been independently audited.
    // Add only independently reviewed, exact version + original + patch pairs.
    private static final Reviewed[] REVIEWED = new Reviewed[0];
    // A real profile requires independently reviewed original/derived files and provenance.
    // Synthetic regression fixtures must never be added to this production catalog.
    private static final LicenseRecoverReadOnlyPatchProfile[] APPLICATION_PROFILES = new LicenseRecoverReadOnlyPatchProfile[0];

    private LicenseRecoverReadOnlyPatchScanner() { }

    private static boolean isSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    public static LicenseRecoverLicenseState scan(File runtimeRoot, Runtime runtime,
                                                   String detectedVersion) {
        return scanReviewed(runtimeRoot, runtime, detectedVersion, REVIEWED);
    }

    /** Application-relative profiles can cover classes, registration JARs/DLLs and startup bindings. */
    public static LicenseRecoverLicenseState scanApplication(File appRoot,Runtime runtime,String productMode) {
        return scanApplicationReviewed(appRoot,runtime,productMode,APPLICATION_PROFILES);
    }

    /** Only root-relative public evidence paths, never expected hashes or target configuration. */
    public static List<String> applicationArtifactPaths(Runtime runtime,String productMode)throws IOException {
        return applicationArtifactPathsReviewed(runtime,productMode,APPLICATION_PROFILES);
    }
    static List<String> applicationArtifactPathsReviewed(Runtime runtime,String productMode,
            LicenseRecoverReadOnlyPatchProfile[] profiles)throws IOException {
        LicenseRecoverReadOnlyPatchProfile[] records=validateProfiles(profiles);Set<String> paths=new TreeSet<String>();
        if(runtime==null||!identifier(productMode,80))return Collections.emptyList();
        for(LicenseRecoverReadOnlyPatchProfile p:records)if(p.runtime==runtime&&p.productMode.equalsIgnoreCase(productMode)) {
            for(LicenseRecoverReadOnlyPatchProfile.FixedBinding f:p.fixedBindings)paths.add(f.path);
            for(LicenseRecoverReadOnlyPatchProfile.TargetPair t:p.targets)paths.add(t.path);
        }
        return Collections.unmodifiableList(new ArrayList<String>(paths));
    }

    /** Fixed test seam; production always uses the compiled, source-reviewed catalog above. */
    static LicenseRecoverLicenseState scanApplicationReviewed(File appRoot,Runtime runtime,String productMode,
            LicenseRecoverReadOnlyPatchProfile[] profiles) {
        if(appRoot==null||runtime==null||!identifier(productMode,80))
            return LicenseRecoverLicenseState.unverified("缺少应用目录、运行时或明确的产品/模式；不能确认代码来源");
        if(profiles==null)return LicenseRecoverLicenseState.unverified("没有可信应用级双指纹审核目录");
        try {
            ProfileSnapshot first=applicationSnapshotReviewed(appRoot,runtime,productMode,profiles);
            return recheckApplicationReviewed(appRoot,runtime,productMode,profiles,first);
        }catch(ProfileInputChanged changed){return changedProfileInputs();}
        catch(IOException|SecurityException|InvalidPathException error){return profileError(error);}
    }

    /** Opaque first-pass receipt permits deterministic change tests without a production write hook. */
    static final class ProfileSnapshot {
        private final String scope,catalog;
        private final Map<String,ObservedProfileFile> inputs;
        private final LicenseRecoverLicenseState decision;
        private ProfileSnapshot(String scope,String catalog,Map<String,ObservedProfileFile> inputs,
                LicenseRecoverLicenseState decision) {
            this.scope=scope;this.catalog=catalog;this.inputs=Collections.unmodifiableMap(new TreeMap<String,ObservedProfileFile>(inputs));
            this.decision=decision;
        }
    }

    static ProfileSnapshot applicationSnapshotReviewed(File appRoot,Runtime runtime,String productMode,
            LicenseRecoverReadOnlyPatchProfile[] profiles)throws IOException {
        if(appRoot==null||runtime==null||!identifier(productMode,80))throw new ProfileSchemaError("missing-scan-scope");
        LicenseRecoverReadOnlyPatchProfile[] records=validateProfiles(profiles);
        Path root=appRoot.toPath().toAbsolutePath().normalize();ProfileReads reads=new ProfileReads(root);
        List<ProfileMatch> complete=new ArrayList<ProfileMatch>();Set<String> reasons=new TreeSet<String>();int applicable=0;
        for(LicenseRecoverReadOnlyPatchProfile p:records) {
            if(p.runtime!=runtime||!p.productMode.equalsIgnoreCase(productMode))continue;
            applicable++;boolean bound=true;
            for(LicenseRecoverReadOnlyPatchProfile.FixedBinding fixed:p.fixedBindings) {
                ObservedProfileFile file=reads.read(fixed.path);
                if(!file.present){reasons.add("必需启动/依赖文件缺失");bound=false;break;}
                if(!file.sha256.equalsIgnoreCase(fixed.sha256)){reasons.add("启动/依赖绑定不匹配");bound=false;break;}
            }
            if(!bound)continue;
            boolean original=false,patched=false,incomplete=false;List<String> paths=new ArrayList<String>();
            for(LicenseRecoverReadOnlyPatchProfile.FixedBinding fixed:p.fixedBindings)paths.add(fixed.path);
            for(LicenseRecoverReadOnlyPatchProfile.TargetPair target:p.targets) {
                paths.add(target.path);ObservedProfileFile file=reads.read(target.path);
                if(!file.present){reasons.add("必需比对目标缺失");incomplete=true;continue;}
                LicenseRecoverLicenseState.CodeIntegrity state=reads.compare(target,p.buildId,file);
                if(state==LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL)original=true;
                else if(state==LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED)patched=true;
                else{reasons.add("目标不匹配已审核双指纹");incomplete=true;}
            }
            if(original&&patched){reasons.add("同一配方的原版/Patch 目标混合");incomplete=true;}
            if(!incomplete&&reads.aliased(paths)){reasons.add("必需文件互为物理别名");incomplete=true;}
            if(!incomplete)complete.add(new ProfileMatch(p,patched,originalManifest(p)));
        }
        LicenseRecoverLicenseState decision;
        if(complete.isEmpty())decision=LicenseRecoverLicenseState.unverified(applicable==0
                ?"未收录当前产品/模式的来源可核验应用级原版/Patch 双指纹；产品标识不是发行版本"
                :"应用级审核集合未完整匹配："+String.join("、",reasons)+"；不能确认原版或 Patch");
        else {
            ProfileMatch first=complete.get(0);boolean ambiguous=false;
            for(int i=1;i<complete.size();i++) {
                ProfileMatch next=complete.get(i);
                // Multiple recipes may share ONE exact original build. A patched
                // result or conflicting original build is never selected by order.
                if(first.patched||next.patched||!first.profile.buildId.equals(next.profile.buildId)
                        ||!first.originalManifest.equals(next.originalManifest))ambiguous=true;
            }
            decision=ambiguous?LicenseRecoverLicenseState.unverified("多份应用级审核记录同时匹配且来源/配方状态存在歧义；未按首项确认代码来源")
                    :LicenseRecoverLicenseState.codeComparison(first.patched,profileEvidence(complete));
        }
        return new ProfileSnapshot(root.toString()+"\0"+runtime+"\0"+productMode.toLowerCase(Locale.ROOT),
                catalogReceipt(records),reads.files,decision);
    }

    static LicenseRecoverLicenseState recheckApplicationReviewed(File appRoot,Runtime runtime,String productMode,
            LicenseRecoverReadOnlyPatchProfile[] profiles,ProfileSnapshot first) {
        if(first==null)return LicenseRecoverLicenseState.unverified("应用级审核首轮回执缺失");
        try {
            ProfileSnapshot second=applicationSnapshotReviewed(appRoot,runtime,productMode,profiles);
            if(!first.scope.equals(second.scope)||!first.catalog.equals(second.catalog)
                    ||!first.inputs.equals(second.inputs)||first.decision.codeIntegrity!=second.decision.codeIntegrity
                    ||!first.decision.evidence.equals(second.decision.evidence))return changedProfileInputs();
            return second.decision;
        }catch(ProfileInputChanged changed){return changedProfileInputs();}
        catch(IOException|SecurityException|InvalidPathException error){return profileError(error);}
    }

    private static LicenseRecoverLicenseState changedProfileInputs() {
        return LicenseRecoverLicenseState.unverified("应用级双指纹审核期间文件、配置绑定或审核集合变化；已撤销代码来源结论");
    }
    private static LicenseRecoverLicenseState profileError(Exception error) {
        return LicenseRecoverLicenseState.scanError(error instanceof ProfileSchemaError
                ?"应用级双指纹审核记录无效："+error.getMessage():"应用级双指纹只读检查失败: "+error.getClass().getSimpleName());
    }
    private static boolean identifier(String s,int max) {
        return s!=null&&s.length()<=max&&s.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }
    private static boolean auditReference(String s) {
        if(s==null||s.trim().isEmpty()||s.length()>512)return false;
        for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i)))return false;
        return true;
    }
    private static String profilePath(String text)throws ProfileSchemaError {
        if(text==null||text.isEmpty()||text.length()>512||!text.equals(text.trim())
                ||text.indexOf('\\')>=0||text.matches(".*[:*?\"<>|].*"))throw new ProfileSchemaError("invalid-artifact-path");
        for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i)))throw new ProfileSchemaError("invalid-artifact-path");
        try {
            Path relative=Paths.get(text);
            if(relative.isAbsolute()||!relative.normalize().toString().replace('\\','/').equals(text))throw new ProfileSchemaError("noncanonical-artifact-path");
            for(String part:text.split("/",-1)) {
                String base=part.split("\\.",2)[0].toUpperCase(Locale.ROOT);
                if(part.isEmpty()||part.equals(".")||part.equals("..")||part.endsWith(".")||part.endsWith(" ")
                        ||base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new ProfileSchemaError("noncanonical-artifact-path");
            }
            return text;
        }catch(InvalidPathException bad){throw new ProfileSchemaError("invalid-artifact-path");}
    }
    private static LicenseRecoverReadOnlyPatchProfile[] validateProfiles(LicenseRecoverReadOnlyPatchProfile[] profiles)throws IOException {
        if(profiles==null||profiles.length>128)throw new ProfileSchemaError("invalid-profile-catalog");
        LicenseRecoverReadOnlyPatchProfile[] copy=profiles.clone();Set<String> ids=new HashSet<String>(),groups=new HashSet<String>();int entries=0;
        for(LicenseRecoverReadOnlyPatchProfile p:copy) {
            if(p==null||p.runtime==null||!identifier(p.profileId,128)||!identifier(p.productMode,80)
                    ||!identifier(p.buildId,128)||!identifier(p.recipeId,128)||!auditReference(p.auditRef)
                    ||p.fixedBindings==null||p.targets==null||p.targets.isEmpty()
                    ||p.fixedBindings.size()+p.targets.size()>256)throw new ProfileSchemaError("missing-profile-field");
            if(!ids.add(p.profileId.toLowerCase(Locale.ROOT))||!groups.add(p.runtime+"\0"+p.productMode.toLowerCase(Locale.ROOT)
                    +"\0"+p.buildId.toLowerCase(Locale.ROOT)+"\0"+p.recipeId.toLowerCase(Locale.ROOT)))throw new ProfileSchemaError("duplicate-profile-group");
            Set<String> roles=new HashSet<String>(),paths=new HashSet<String>();
            for(LicenseRecoverReadOnlyPatchProfile.FixedBinding fixed:p.fixedBindings) {
                if(fixed==null||!isSha256(fixed.sha256))throw new ProfileSchemaError("invalid-fixed-binding");
                validateRole(fixed.role,fixed.path,roles,paths);entries++;
            }
            for(LicenseRecoverReadOnlyPatchProfile.TargetPair target:p.targets) {
                if(target==null||!isSha256(target.originalSha256)||!isSha256(target.patchedSha256)
                        ||target.originalSha256.equalsIgnoreCase(target.patchedSha256))throw new ProfileSchemaError("invalid-target-pair");
                validateRole(target.role,target.path,roles,paths);entries++;
            }
            if(entries>4096)throw new ProfileSchemaError("profile-catalog-limit");
        }
        return copy;
    }
    private static void validateRole(String role,String path,Set<String> roles,Set<String> paths)throws ProfileSchemaError {
        if(!identifier(role,80)||!roles.add(role.toLowerCase(Locale.ROOT))||!paths.add(profilePath(path).toLowerCase(Locale.ROOT)))
            throw new ProfileSchemaError("duplicate-or-invalid-artifact-field");
    }
    private static String originalManifest(LicenseRecoverReadOnlyPatchProfile p) {
        Map<String,String> files=new TreeMap<String,String>();
        for(LicenseRecoverReadOnlyPatchProfile.FixedBinding b:p.fixedBindings)files.put(b.path,b.sha256.toLowerCase(Locale.ROOT));
        for(LicenseRecoverReadOnlyPatchProfile.TargetPair t:p.targets)files.put(t.path,t.originalSha256.toLowerCase(Locale.ROOT));
        StringBuilder manifest=new StringBuilder();
        for(Map.Entry<String,String> file:files.entrySet())manifest.append(file.getKey()).append('\0').append(file.getValue()).append('\n');
        return manifest.toString();
    }
    private static String catalogReceipt(LicenseRecoverReadOnlyPatchProfile[] records) {
        StringBuilder b=new StringBuilder();
        for(LicenseRecoverReadOnlyPatchProfile p:records) {
            b.append(p.profileId).append('\0').append(p.runtime).append('\0').append(p.productMode).append('\0')
                    .append(p.buildId).append('\0').append(p.recipeId).append('\0').append(p.auditRef).append('\n');
            for(LicenseRecoverReadOnlyPatchProfile.FixedBinding f:p.fixedBindings)b.append(f.role).append('\0').append(f.path).append('\0').append(f.sha256).append('\n');
            for(LicenseRecoverReadOnlyPatchProfile.TargetPair t:p.targets)b.append(t.role).append('\0').append(t.path).append('\0').append(t.originalSha256).append('\0').append(t.patchedSha256).append('\n');
        }
        MessageDigest sha=profileDigest();return hex(sha.digest(b.toString().getBytes(StandardCharsets.UTF_8)));
    }
    private static String profileEvidence(List<ProfileMatch> matches) {
        StringBuilder b=new StringBuilder("应用级完整审核集合两次只读一致；产品/模式仅作候选键，不是发行版本；");int count=0;
        for(ProfileMatch m:matches) {
            if(count++==8){b.append("另有 ").append(matches.size()-8).append(" 份相同原版审核记录；");break;}
            b.append("profile=").append(m.profile.profileId).append("；build=").append(m.profile.buildId)
                    .append("；recipe=").append(m.profile.recipeId).append("；audit=").append(m.profile.auditRef).append("；");
        }
        return b.toString();
    }
    private static final class ProfileMatch {
        final LicenseRecoverReadOnlyPatchProfile profile;final boolean patched;final String originalManifest;
        ProfileMatch(LicenseRecoverReadOnlyPatchProfile p,boolean patched,String manifest){profile=p;this.patched=patched;originalManifest=manifest;}
    }
    private static final class ProfileSchemaError extends IOException {ProfileSchemaError(String reason){super(reason);}}
    private static final class ProfileInputChanged extends IOException { }
    private static final class ObservedProfileFile {
        final boolean present;final String sha256;final long size;final BasicFileAttributes attributes;
        ObservedProfileFile(){present=false;sha256="";size=0;attributes=null;}
        ObservedProfileFile(String hash,BasicFileAttributes attributes){present=true;sha256=hash;this.attributes=attributes;size=attributes.size();}
        @Override public boolean equals(Object other){
            if(!(other instanceof ObservedProfileFile))return false;ObservedProfileFile b=(ObservedProfileFile)other;
            return present==b.present&&(!present||(sha256.equals(b.sha256)&&sameAttributes(attributes,b.attributes)));
        }
        @Override public int hashCode(){return present?Objects.hash(sha256,size,attributes.lastModifiedTime(),attributes.fileKey()):0;}
    }
    private static final class ProfileReads {
        final Path root;final Map<String,ObservedProfileFile> files=new TreeMap<String,ObservedProfileFile>();
        // The byte budget counts unique observations. Recognized targets may also
        // be streamed once by Fingerprint; it is not a cap on all I/O across both passes.
        final Set<String> compared=new HashSet<String>();final Map<String,Boolean> identityPairs=new HashMap<String,Boolean>();long total;
        ProfileReads(Path root){this.root=root;}
        ObservedProfileFile read(String relative)throws IOException {
            if(files.containsKey(relative))return files.get(relative);Path file=root.resolve(relative);BasicFileAttributes before;
            try{before=profileAttributes(file);}catch(java.nio.file.NoSuchFileException absent){ObservedProfileFile missing=new ObservedProfileFile();files.put(relative,missing);return missing;}
            if(!before.isRegularFile()||before.size()>128L*1024*1024||before.size()>512L*1024*1024-total)throw new IOException("profile-input-limit-or-type");
            total+=before.size();MessageDigest sha=profileDigest();long count=0;
            try {
                try(InputStream in=Files.newInputStream(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){count+=n;if(count>before.size())throw new ProfileInputChanged();sha.update(bytes,0,n);}
                }
                BasicFileAttributes after=profileAttributes(file);if(count!=before.size()||!sameAttributes(before,after))throw new ProfileInputChanged();
            }catch(java.nio.file.NoSuchFileException changed){throw new ProfileInputChanged();}
            ObservedProfileFile observed=new ObservedProfileFile(hex(sha.digest()),before);files.put(relative,observed);return observed;
        }
        LicenseRecoverLicenseState.CodeIntegrity compare(LicenseRecoverReadOnlyPatchProfile.TargetPair target,String build,
                ObservedProfileFile observed)throws IOException {
            LicenseRecoverLicenseState.CodeIntegrity expected=observed.sha256.equalsIgnoreCase(target.originalSha256)
                    ?LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL:observed.sha256.equalsIgnoreCase(target.patchedSha256)
                    ?LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED:LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN;
            // Reuse the existing strict comparison for a potentially recognized
            // target, once per path/pass. Product IDs are not passed as versions.
            if(expected!=LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN&&compared.add(target.path)) {
                LicenseRecoverLicenseState result=LicenseRecoverReadOnlyFingerprint.compare(root.resolve(target.path).toFile(),build,build,
                        target.originalSha256,target.patchedSha256);
                if(result.isScanError())throw new IOException("profile-fingerprint-read");
                try{if(!sameAttributes(observed.attributes,profileAttributes(root.resolve(target.path)))||result.codeIntegrity!=expected)throw new ProfileInputChanged();}
                catch(java.nio.file.NoSuchFileException changed){throw new ProfileInputChanged();}
            }
            return expected;
        }
        boolean aliased(List<String> paths)throws IOException {
            for(int i=0;i<paths.size();i++)for(int j=0;j<i;j++) {
                String a=paths.get(i),b=paths.get(j);Object ka=files.get(a).attributes.fileKey(),kb=files.get(b).attributes.fileKey();
                if(ka!=null&&kb!=null&&!ka.equals(kb))continue;
                String pair=a.compareTo(b)<0?a+"\0"+b:b+"\0"+a;Boolean alias=identityPairs.get(pair);
                if(alias!=null){if(alias)return true;continue;}
                if(identityPairs.size()>=65536)throw new IOException("profile-file-identity-work-limit");
                try{boolean same=Files.isSameFile(root.resolve(a),root.resolve(b));identityPairs.put(pair,same);if(same)return true;}
                catch(java.nio.file.NoSuchFileException changed){throw new ProfileInputChanged();}
            }
            return false;
        }
    }
    private static BasicFileAttributes profileAttributes(Path file)throws IOException {
        Path cursor=file.getRoot();
        for(Path part:file){cursor=cursor.resolve(part);BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(a.isSymbolicLink()||a.isOther())throw new IOException("linked-profile-input");
            if(!cursor.equals(file)&&!a.isDirectory())throw new IOException("non-directory-profile-parent");}
        return Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
    }
    private static boolean sameAttributes(BasicFileAttributes a,BasicFileAttributes b) {
        return b.isRegularFile()&&a.size()==b.size()&&a.lastModifiedTime().equals(b.lastModifiedTime())&&Objects.equals(a.fileKey(),b.fileKey());
    }
    private static MessageDigest profileDigest() {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static String hex(byte[] digest) {StringBuilder b=new StringBuilder(64);for(byte x:digest)b.append(Character.forDigit((x>>>4)&15,16)).append(Character.forDigit(x&15,16));return b.toString();}

    /**
     * All reviewed artifacts for one runtime/version form one evidence set.
     * A single matching DLL/JAR is insufficient if other audited artifacts
     * are missing, unknown or still original. Never use first-match wins.
     */
    static LicenseRecoverLicenseState scanReviewed(File runtimeRoot, Runtime runtime,
            String detectedVersion, Reviewed[] reviewed) {
        if (runtimeRoot == null || runtime == null || detectedVersion == null
                || detectedVersion.trim().isEmpty())
            return LicenseRecoverLicenseState.unverified("缺少运行目录、平台或版本，不能确认 Patch");
        if (reviewed == null)
            return LicenseRecoverLicenseState.unverified("没有可信审核指纹集");
        java.util.Set<String> seenPaths = new java.util.HashSet<String>();
        // Distinct paths can still be hard links to the same physical file.
        // Count only independent reviewed artifacts as evidence.
        java.util.List<Path> verifiedPaths = new java.util.ArrayList<Path>();
        java.util.List<Reviewed> firstPassConfirmed = new java.util.ArrayList<Reviewed>();
        boolean original = false, patched = false, incomplete = false;
        int matched = 0;
        StringBuilder evidence = new StringBuilder();
        for (Reviewed candidate : reviewed) {
            // Fail closed on malformed audited registry entries. Silently
            // skipping a record without a version/runtime could make a
            // partial fingerprint set look complete and turn the row green.
            if (candidate == null || candidate.runtime == null || candidate.version == null
                    || candidate.version.trim().isEmpty())
                return LicenseRecoverLicenseState.scanError("审核指纹集存在缺失平台或版本的记录");
            // A malformed version key must not be silently skipped as a
            // different product build. Otherwise one valid reviewed entry
            // could confirm the whole patch while a second required artifact
            // with a whitespace-padded version is never inspected.
            if (!candidate.version.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
                return LicenseRecoverLicenseState.scanError("审核指纹集包含非法版本标识");
            if (candidate.runtime != runtime || !detectedVersion.equalsIgnoreCase(candidate.version))
                continue;
            matched++;
            // An incomplete or contradictory trusted baseline is a registry
            // error, not an ordinary unknown target. Never silently downgrade
            // corrupt reviewed evidence to an unverified artifact.
            if (candidate.relativeArtifact == null || candidate.relativeArtifact.trim().isEmpty()
                    || !isSha256(candidate.originalSha256)
                    || !isSha256(candidate.patchedSha256)
                    || candidate.originalSha256.equalsIgnoreCase(candidate.patchedSha256))
                return LicenseRecoverLicenseState.scanError("审核指纹记录缺少目标路径或有效的原版/Patch 双指纹");
            // Normalize aliases before counting independent reviewed artifacts.
            // Otherwise "bin/./X.dll" and "bin/X.dll" can count as two files.
            String relative;
            try {
                relative = Paths.get(candidate.relativeArtifact == null ? ""
                        : candidate.relativeArtifact.replace('\\', '/'))
                        .normalize().toString().replace('\\', '/')
                        .toLowerCase(java.util.Locale.ROOT);
            } catch (InvalidPathException ex) {
                return LicenseRecoverLicenseState.scanError("审核指纹包含非法相对路径");
            }
            if (!seenPaths.add(relative)) {
                incomplete = true; // Conflicting or duplicate baselines are not independent evidence.
                continue;
            }
            LicenseRecoverLicenseState result =
                    compareReviewed(runtimeRoot, runtime, detectedVersion, candidate);
            if (result.authorization == LicenseRecoverLicenseState.Authorization.ERROR
                    || result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.ERROR)
                return result;
            if (result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL
                    || result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED) {
                // Normalized path equality does not detect hard-link aliases.
                // Check filesystem identity only after compareReviewed has
                // rejected traversal, symlinks and unreadable artifacts.
                Path current = runtimeRoot.toPath().toAbsolutePath().normalize()
                        .resolve(Paths.get(candidate.relativeArtifact.replace('\\', '/')).normalize()).normalize();
                try {
                    boolean alias = false;
                    for (Path previous : verifiedPaths) {
                        if (Files.isSameFile(previous, current)) {
                            alias = true;
                            break;
                        }
                    }
                    if (alias) {
                        incomplete = true;
                        continue;
                    }
                    verifiedPaths.add(current);
                } catch (java.io.IOException | SecurityException ex) {
                    return LicenseRecoverLicenseState.scanError(
                            "审核文件身份核对失败: " + ex.getClass().getSimpleName());
                }
            }
            if (result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL) {
                original = true;
                firstPassConfirmed.add(candidate);
            } else if (result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED) {
                patched = true;
                firstPassConfirmed.add(candidate);
            } else incomplete = true;
            if (result.codeIntegrity != LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN) {
                if (evidence.length() > 0) evidence.append("; ");
                evidence.append(relative).append(": ").append(result.evidence);
            }
        }
        if (matched == 0)
            return LicenseRecoverLicenseState.unverified(
                    "未收录经独立审核的 " + runtime + " " + detectedVersion
                    + " 原版与 Patch 双指纹；不能确认 Patch");
        if (incomplete || (original && patched))
            return LicenseRecoverLicenseState.unverified(
                    "已审核文件证据不完整、重复或原版/Patch 混合；不能确认整套 Patch");
        // A multi-file scan is not an atomic filesystem snapshot. If an
        // installer or another process changes an artifact after its first
        // hash, that stale hash must not confirm a full patched set.
        return recheckReviewedSet(runtimeRoot, runtime, detectedVersion,
                firstPassConfirmed, patched, matched, evidence.toString());
    }

    /**
     * Recheck every previously matched artifact before promoting a multi-file
     * result. This is best-effort consistency, not an atomic filesystem
     * snapshot: a file can still change after the final read.
     * Package-private so tests can deterministically mutate files between
     * the two phases without adding a production write callback.
     */
    static LicenseRecoverLicenseState recheckReviewedSet(File runtimeRoot, Runtime runtime,
            String detectedVersion, java.util.List<Reviewed> confirmed,
            boolean expectedPatched, int matched, String firstPassEvidence) {
        if (runtimeRoot == null || runtime == null || detectedVersion == null
                || confirmed == null || confirmed.isEmpty() || matched != confirmed.size())
            return LicenseRecoverLicenseState.unverified("复核文件集合不完整，不能确认代码状态");
        LicenseRecoverLicenseState.CodeIntegrity expected = expectedPatched
                ? LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED
                : LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL;
        java.util.List<Path> recheckedPaths = new java.util.ArrayList<Path>();
        for (Reviewed candidate : confirmed) {
            LicenseRecoverLicenseState second = compareReviewed(
                    runtimeRoot, runtime, detectedVersion, candidate);
            if (second.isScanError()) return second;
            if (second.codeIntegrity != expected)
                return LicenseRecoverLicenseState.unverified(
                        "扫描期间审核文件状态变化或不再匹配，不能确认整套原版/Patch");
            try {
                Path path = runtimeRoot.toPath().toAbsolutePath().normalize()
                        .resolve(Paths.get(candidate.relativeArtifact.replace('\\', '/')).normalize())
                        .normalize();
                for (Path previous : recheckedPaths) {
                    if (Files.isSameFile(previous, path))
                        return LicenseRecoverLicenseState.unverified(
                                "复核期间审核文件路径指向同一文件，不能确认独立 Patch 证据");
                }
                recheckedPaths.add(path);
            } catch (java.io.IOException | SecurityException | InvalidPathException ex) {
                return LicenseRecoverLicenseState.scanError(
                        "二次文件身份核对失败: " + ex.getClass().getSimpleName());
            }
        }
        return LicenseRecoverLicenseState.codeComparison(expectedPatched,
                "全部 " + matched + " 个审核文件两次只读指纹一致；" + firstPassEvidence);
    }

    /**
     * Package-private fixture seam: tests can prove real file comparison,
     * traversal rejection and no writes without adding unreviewed baselines
     * to the production registry.
     */
    static LicenseRecoverLicenseState compareReviewed(File runtimeRoot, Runtime runtime,
            String detectedVersion, Reviewed reviewed) {
        if (reviewed == null || runtime == null || reviewed.runtime != runtime
                || detectedVersion == null || reviewed.version == null
                || !detectedVersion.equalsIgnoreCase(reviewed.version))
            return LicenseRecoverLicenseState.unverified("版本或平台与审核指纹不匹配");
        if (runtimeRoot == null || reviewed.relativeArtifact == null
                || reviewed.relativeArtifact.trim().isEmpty())
            return LicenseRecoverLicenseState.unverified("缺少目标文件路径");
        // Trusted artifact paths must be ordinary portable relative filenames.
        // Reject Windows drive-relative syntax (C:foo.dll) and NTFS alternate
        // data streams (file.dll:stream) before any filesystem access, even
        // when tests run on a non-Windows host.
        if (reviewed.relativeArtifact.indexOf(':') >= 0)
            return LicenseRecoverLicenseState.scanError("审核指纹路径包含盘符或备用数据流语法");
        try {
            Path root = runtimeRoot.toPath().toAbsolutePath().normalize();
            // Audited paths use portable separators. Java on Unix treats a
            // backslash as a filename character, so normalize it before
            // checking absolute/traversal syntax on every host platform.
            Path relative = Paths.get(reviewed.relativeArtifact.replace('\\', '/')).normalize();
            if (relative.isAbsolute() || relative.getNameCount() == 0
                    || relative.startsWith("..") || relative.toString().equals("."))
                return LicenseRecoverLicenseState.scanError("审核指纹包含非法相对路径");
            Path artifact = root.resolve(relative).normalize();
            if (!artifact.startsWith(root))
                return LicenseRecoverLicenseState.scanError("审核指纹路径越过应用目录");
            // A nested runtime root may be inside a symlinked parent.
            // Checking only root and relative components misses that escape.
            Path rootCursor = root.getRoot();
            for (Path part : root) {
                rootCursor = rootCursor == null ? part : rootCursor.resolve(part);
                if (Files.isSymbolicLink(rootCursor))
                    return LicenseRecoverLicenseState.scanError("运行目录或上级目录含符号链接");
            }
            // Reject symlinks at every relative component, not just the file.
            Path cursor = root;
            for (Path part : relative) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor))
                    return LicenseRecoverLicenseState.scanError("审核目标路径含符号链接");
            }
            // Files.exists() can return false on permission or metadata I/O
            // errors. Distinguish a genuinely missing file (gray) from a
            // failed read-only inspection (red).
            try {
                Files.readAttributes(artifact, java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
            } catch (java.nio.file.NoSuchFileException ex) {
                return LicenseRecoverLicenseState.unverified("目标文件缺失，不能确认 Patch");
            } catch (java.io.IOException ex) {
                return LicenseRecoverLicenseState.scanError(
                        "审核目标属性读取失败: " + ex.getClass().getSimpleName());
            }
            return LicenseRecoverReadOnlyFingerprint.compare(
                    artifact.toFile(), detectedVersion, reviewed.version,
                    reviewed.originalSha256, reviewed.patchedSha256);
        } catch (InvalidPathException | SecurityException ex) {
            return LicenseRecoverLicenseState.scanError(
                    "只读指纹路径检查失败: " + ex.getClass().getSimpleName());
        }
    }
}
