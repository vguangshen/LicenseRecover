import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;

/** Independent read-only evidence collection. Never loads or executes a vendor class/assembly. */
public final class LicenseRecoverReadOnlyInspection {
    private static final long MAX_ARTIFACT = 128L * 1024 * 1024;
    private LicenseRecoverReadOnlyInspection() { }

    public static final class Artifact {
        public final String path, sha256, format;
        public final long size;
        Artifact(String path, String sha256, String format, long size) {
            this.path = path; this.sha256 = sha256; this.format = format; this.size = size;
        }
    }

    public static final class Result {
        public final String name, version, runtime, method, evidenceSource, reason, nativeVerifierSafety;
        public final String codeComparisonEvidence, acceptanceEvidence;
        public final LicenseRecoverReadOnlyCodeEvidence codeEvidence;
        public final boolean inspectionReadOnlyAudited = true;
        public final boolean nativeVerifierReadOnlyAudited = false;
        public final boolean acceptanceVerifierReadOnlyAudited = true;
        public final LicenseRecoverLicenseState state;
        public final List<Artifact> artifacts;
        public final LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol;
        public final LicenseRecoverReadOnlyDotNetCatalog.ModeRelations modeRelations;
        Result(File root, String version, String runtime, LicenseRecoverLicenseState state,
               List<Artifact> artifacts, String reason) {
            this(root,version,runtime,state,artifacts,reason,null);
        }
        Result(File root, String version, String runtime, LicenseRecoverLicenseState state,
               List<Artifact> artifacts, String reason,LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol) {
            this(root,version,runtime,state,artifacts,reason,protocol,
                    LicenseRecoverReadOnlyCodeEvidence.unassessed("没有取得独立代码结构证据"),state.evidence,
                    state.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED?"当前授权入口尚未获得独立证明":state.evidence);
        }
        Result(File root,String version,String runtime,LicenseRecoverLicenseState state,List<Artifact> artifacts,
               String reason,LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol,
               LicenseRecoverReadOnlyCodeEvidence codeEvidence,String codeComparisonEvidence,String acceptanceEvidence) {
            this(root,version,runtime,state,artifacts,reason,protocol,codeEvidence,codeComparisonEvidence,acceptanceEvidence,
                    protocol==null?LicenseRecoverReadOnlyDotNetCatalog.ModeRelations.unassessed():protocol.modeRelations);
        }
        Result(File root,String version,String runtime,LicenseRecoverLicenseState state,List<Artifact> artifacts,
               String reason,LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol,
               LicenseRecoverReadOnlyCodeEvidence codeEvidence,String codeComparisonEvidence,String acceptanceEvidence,
               LicenseRecoverReadOnlyDotNetCatalog.ModeRelations modeRelations) {
            this.protocol=protocol;
            this.modeRelations=modeRelations;
            this.codeEvidence=codeEvidence;this.codeComparisonEvidence=codeComparisonEvidence;this.acceptanceEvidence=acceptanceEvidence;
            this.name = root.getName(); this.version = canonicalVersion(version);
            this.runtime = runtime; this.state = state;
            String authorizationMethod = state.acceptance == LicenseRecoverLicenseState.Acceptance.VERIFIED_REJECTED
                    ? "audited-authorization-rejection" : state.acceptance == LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE
                    ? ("DOTNET".equals(runtime) ? "audited-local-positive-predicate"
                        : (state.localCondition==LicenseRecoverLicenseState.LocalCondition.SATISFIED
                            ? "audited-java-local-positive-predicate" : "audited-static-authorization-gate"))
                    : (state.localCondition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED
                        ? "file-observation+available-audit-profiles" : "audited-local-diagnostic");
            this.method=state.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED
                    ||state.codeIntegrity==LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL
                    ?"reviewed-complete-code-profile; "+authorizationMethod:authorizationMethod;
            this.evidenceSource = state.acceptance != LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                    ? "audited-current-code; current-inputs; independent-read-only-predicate"
                    : "current-files; compiled-reviewed-registry; audited-acceptance-profiles";
            this.reason = reason+"；代码比对："+codeComparisonEvidence
                    +(protocol==null?"":"；构建审核："+protocol.fingerprintSummary())
                    +("NOT_ASSESSED".equals(modeRelations.status)||"UNREVIEWED_CONSUMER".equals(modeRelations.status)?"":"；"+modeRelations.summary());
            this.nativeVerifierSafety = LicenseRecoverReadOnlyVerifierSafety.rejection(artifacts);
            this.artifacts = Collections.unmodifiableList(new ArrayList<Artifact>(artifacts));
        }
        public String summary() {
            return reason + "；代码证据="+codeEvidence.summary()
                    +"；方法=" + method + "；组件=" + artifacts.size()
                    +(protocol==null?"":"；协议识别="+protocol.summary())
                    + "；独立只读判定=" + (state.acceptance == LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE ? "通过"
                        : state.acceptance == LicenseRecoverLicenseState.Acceptance.VERIFIED_REJECTED ? "明确拒绝" : "未确认")
                    + "；未执行目标原生代码；" + nativeVerifierSafety;
        }
        public String toJson() {
            StringBuilder b = new StringBuilder("{");
            pair(b,"name",name); pair(b,"version",version); pair(b,"runtime",runtime);
            pair(b,"versionMeaning","configuration-product-id"); pair(b,"releaseVersion","unproven");
            pair(b,"status",state.visualStatus().name()); pair(b,"label",state.display());
            pair(b,"authorization",state.authorization.name()); pair(b,"codeIntegrity",state.codeIntegrity.name());
            pair(b,"acceptance",state.acceptance.name());
            pair(b,"localCondition",state.localCondition.name());
            pair(b,"pipeline","JAVA".equals(runtime)?"JAVA_SHARED":"DOTNET".equals(runtime)?"DOTNET_SHARED":"UNKNOWN");
            pair(b,"protocolFamily",protocol==null?("DOTNET".equals(runtime)?"UNKNOWN":"not-applicable"):protocol.upperFamily.name());
            pair(b,"compatibilityFamily",protocol==null?("DOTNET".equals(runtime)?"UNKNOWN":"not-applicable"):protocol.compatibilityFamily.name());
            b.append("\"protocolBindingAudited\":").append(protocol!=null&&protocol.auditedBinding).append(',');
            pair(b,"protocolFingerprintStatus",protocol==null?"UNASSESSED":protocol.fingerprintStatus);
            pair(b,"protocolAuditScope",protocol==null?"NONE":protocol.auditScope);
            pair(b,"protocolProfileId",protocol==null?"none":protocol.profileId);
            b.append("\"protocolFingerprintMatches\":[");
            if(protocol!=null)for(int i=0;i<protocol.fingerprintMatches.size();i++) {
                if(i>0)b.append(',');LicenseRecoverReadOnlyDotNetCatalog.FingerprintMatch match=protocol.fingerprintMatches.get(i);
                b.append('{');pair(b,"profileId",match.profileId);pair(b,"auditScope",match.auditScope);
                b.append("\"exact\":").append(match.exact).append(',');
                b.append("\"requiredComponents\":").append(match.requiredComponents).append(',');
                b.append("\"matchedComponents\":").append(match.matchedComponents).append(',');
                stringArray(b,"missingComponents",match.missingComponents);b.append(',');
                stringArray(b,"differentComponents",match.differentComponents);b.append(',');
                stringArray(b,"requiredAbsentComponents",match.requiredAbsentComponents);b.append('}');
            }
            b.append("],");
            pair(b,"modeRelationStatus",modeRelations.status);pair(b,"modeRelationProfileId",modeRelations.profileId);
            pair(b,"modeRelationAuditScope",modeRelations.auditScope);pair(b,"modeRelationCurrentMode",modeRelations.currentMode);
            pair(b,"modeDatabaseSHA256",modeRelations.databaseSHA256);pair(b,"modeRelationReason",modeRelations.reason);
            stringArray(b,"modeRelationCompanionFiles",modeRelations.companionFiles);b.append(',');
            b.append("\"modeRelationParentGroups\":[");boolean firstParent=true;
            for(Map.Entry<String,List<String>> entry:modeRelations.parentModes.entrySet()) {
                if(!firstParent)b.append(',');firstParent=false;b.append('{');pair(b,"product",entry.getKey());
                stringArray(b,"modes",entry.getValue());b.append('}');
            }
            b.append("],\"modeRelationInspectionReadOnlyAudited\":true,\"modeRelationFullAuthorizationAudited\":false,");
            pair(b,"method",method); pair(b,"evidenceSource",evidenceSource); pair(b,"reason",reason);
            pair(b,"codeBehavior",codeEvidence.behavior.name());pair(b,"registrationComponent",codeEvidence.component.name());
            pair(b,"codeEvidenceLabel",codeEvidence.display());pair(b,"codeBehaviorEvidence",codeEvidence.behaviorEvidence);
            pair(b,"componentEvidence",codeEvidence.componentEvidence);pair(b,"codeEvidenceSource",codeEvidence.evidenceSource);
            pair(b,"codeComparisonEvidence",codeComparisonEvidence);pair(b,"acceptanceEvidence",acceptanceEvidence);
            b.append("\"codeInspectionReadOnlyAudited\":true,");
            pair(b,"nativeVerifierSafety",nativeVerifierSafety);
            b.append("\"inspectionReadOnlyAudited\":true,\"nativeVerifierReadOnlyAudited\":false,\"acceptanceVerifierReadOnlyAudited\":true,\"artifacts\":[");
            for (int i=0;i<artifacts.size();i++) {
                if(i>0)b.append(','); Artifact a=artifacts.get(i); b.append('{');
                pair(b,"path",a.path); pair(b,"sha256",a.sha256); pair(b,"format",a.format);
                b.append("\"size\":").append(a.size).append('}');
            }
            return b.append("]}").toString();
        }
    }

    public static Result inspect(File selected) {
        AppInfo app = AppDetector.detectLocal(selected);
        if (!app.isDetected()) return new Result(selected, null, "UNKNOWN",
                LicenseRecoverLicenseState.unverified("未识别应用布局"), Collections.<Artifact>emptyList(),
                "未识别应用布局；未执行目标代码");
        LicenseRecoverReadOnlyPatchScanner.Runtime runtime = app.type == AppInfo.Type.JAVA
                ? LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA : LicenseRecoverReadOnlyPatchScanner.Runtime.DOTNET;
        File dir = app.type == AppInfo.Type.JAVA ? app.libDir : app.binDir;
        String version = app.type == AppInfo.Type.JAVA
                ? AppDetector.readJavaSoftVersion(app.appRoot) : AppDetector.readDotNetSoftVersion(dir);
        return inspect(app.appRoot, dir, runtime, version,
                LicenseRecoverReadOnlyPatchScanner.scanApplication(app.appRoot, runtime, version));
    }

    /** Shared GUI seam: existing detector metadata remains separate from license evidence. */
    static Result inspect(File root, File dir, LicenseRecoverReadOnlyPatchScanner.Runtime runtime,
                          String version, LicenseRecoverLicenseState codeState) {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        LicenseRecoverLicenseState state = codeState;
        String codeComparisonEvidence=codeState.evidence,acceptanceEvidence="当前授权入口尚未获得独立证明";
        LicenseRecoverReadOnlyCodeEvidence codeEvidence=LicenseRecoverReadOnlyCodeEvidence.unassessed("当前组件尚无代码结构对照");
        LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol=null;
        LicenseRecoverReadOnlyDotNetCatalog.ModeRelations modeRelations=LicenseRecoverReadOnlyDotNetCatalog.ModeRelations.unassessed();
        String reason = codeState.evidence + "；"
                + (runtime == LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA
                ? "RegisterMain 构造/初始化及 checkReInfo 传递副作用尚未完整证明只读"
                : "受保护注册程序集及宿主/CheckLocalReg 链尚未完整证明只读")
                + "；当前文件哈希仅作观测，不建立信任";
        try {
            checkPath(dir.toPath());
            List<Path> paths = new ArrayList<Path>();
            if (runtime == LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir.toPath())) {
                    for(Path p:stream) {
                        String n=p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if(n.startsWith("itmcreg") && n.endsWith(".jar"))paths.add(p);
                    }
                }
            } else {
                for(String n:new String[]{"ITMC.Web.dll","ITMC.Regedit.dll","itmcRegedit.dll"}) {
                    Path p=dir.toPath().resolve(n);
                    try { Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS); paths.add(p); }
                    catch(NoSuchFileException missing) { /* Optional registration assembly variant. */ }
                }
            }
            for(String relative:LicenseRecoverReadOnlyPatchScanner.applicationArtifactPaths(runtime,version)) {
                Path p=root.toPath().toAbsolutePath().normalize().resolve(relative).normalize();
                try {Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
                catch(NoSuchFileException absentVariant){continue;}
                if(!paths.contains(p))paths.add(p);
            }
            Collections.sort(paths);
            List<Path> independent = new ArrayList<Path>();
            for(Path p:paths) {
                for(Path other:independent)if(Files.isSameFile(other,p))throw new IOException("aliased-artifact");
                independent.add(p);
                artifacts.add(readArtifact(root,p,runtime));
            }
            if(paths.isEmpty())throw new NoSuchFileException("registration-artifacts");
            // Re-read the entire set, including the format: no promotion from stale observations.
            for(int i=0;i<paths.size();i++) {
                Artifact second=readArtifact(root,paths.get(i),runtime), first=artifacts.get(i);
                if(!first.sha256.equals(second.sha256)||first.size!=second.size||!first.format.equals(second.format))
                    throw new Changed();
            }
            // Tie the state to the observations, not to a scan made before collection.
            // Recheck the reviewed set, then ensure our observed bytes still agree.
            LicenseRecoverLicenseState current = LicenseRecoverReadOnlyPatchScanner.scanApplication(root,runtime,version);
            codeComparisonEvidence=current.evidence;
            if(current.isScanError()) {state=current;reason=current.evidence;}
            else if(!sameCodeEvidence(codeState,current))throw new Changed();
            else state=current;
            for(int i=0;i<paths.size();i++) {
                Artifact currentArtifact=readArtifact(root,paths.get(i),runtime);
                if(!currentArtifact.sha256.equals(artifacts.get(i).sha256))throw new Changed();
            }
        } catch (Changed | NoSuchFileException e) {
            state=LicenseRecoverLicenseState.unverified("扫描期间文件缺失或变化；不能确认当前状态");
            reason=state.evidence;
        } catch (IOException | java.security.GeneralSecurityException | SecurityException e) {
            state=LicenseRecoverLicenseState.scanError("组件只读检查失败: "+e.getClass().getSimpleName());
            reason=state.evidence; // Never expose paths, configuration or exception messages.
        }
        if(!state.isScanError()) {
            LicenseRecoverLicenseState observedCode=state;
            LicenseRecoverLicenseState accepted;
            if(runtime==LicenseRecoverReadOnlyPatchScanner.Runtime.DOTNET) {
                LicenseRecoverReadOnlyDotNetScanner.Result scan=LicenseRecoverReadOnlyDotNetScanner.inspect(root);
                accepted=scan.state;protocol=scan.protocol;codeEvidence=scan.codeEvidence;modeRelations=scan.modeRelations;
            } else {
                LicenseRecoverReadOnlyJavaScanner.Result scan=LicenseRecoverReadOnlyJavaScanner.inspect(root);
                accepted=scan.state;codeEvidence=scan.codeEvidence;
            }
            acceptanceEvidence=accepted.evidence;
            if(accepted.isScanError()) {state=accepted;reason=accepted.evidence;}
            else {
                try {
                    // Every channel must still refer to the same observed code, including class targets.
                    for(Artifact first:artifacts) {
                        Artifact last=readArtifact(root,root.toPath().resolve(first.path),runtime);
                        if(!first.sha256.equals(last.sha256)||first.size!=last.size||!first.format.equals(last.format))
                            throw new Changed();
                    }
                    if(!componentReceiptsMatch(codeEvidence,artifacts))throw new Changed();
                    LicenseRecoverLicenseState finalCode=LicenseRecoverReadOnlyPatchScanner.scanApplication(root,runtime,version);
                    if(!sameCodeEvidence(observedCode,finalCode))throw new Changed();
                    // Recheck every audited dependency and root record after the outer artifact pass,
                    // not only the registration DLLs included in the GUI's artifact inventory.
                    if(protocol!=null&&!protocol.sameInputs(LicenseRecoverReadOnlyDotNetCatalog.assess(root,false)))throw new Changed();
                    codeComparisonEvidence=finalCode.evidence;
                    if(accepted.acceptance!=LicenseRecoverLicenseState.Acceptance.UNVERIFIED)
                        state=LicenseRecoverLicenseState.combineAcceptance(accepted,finalCode);
                    else if(accepted.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED)
                        state=LicenseRecoverLicenseState.combineLocalObservation(accepted,finalCode);
                    else state=finalCode;
                    reason=accepted.evidence;
                } catch(Changed | LicenseRecoverReadOnlyDotNetCatalog.Changed | NoSuchFileException ex) {
                    protocol=null;
                    modeRelations=LicenseRecoverReadOnlyDotNetCatalog.ModeRelations.revoked();
                    codeEvidence=LicenseRecoverReadOnlyCodeEvidence.unassessed("代码证据读取期间变化，已撤销原结构结论");
                    state=LicenseRecoverLicenseState.unverified("接受条件与文件观测不再一致；扫描期间文件发生变化");reason=state.evidence;
                    codeComparisonEvidence=acceptanceEvidence=state.evidence;
                } catch(IOException | java.security.GeneralSecurityException | SecurityException ex) {
                    protocol=null;
                    modeRelations=LicenseRecoverReadOnlyDotNetCatalog.ModeRelations.revoked();
                    codeEvidence=LicenseRecoverReadOnlyCodeEvidence.unassessed("最终代码复核失败，未保留结构结论");
                    state=LicenseRecoverLicenseState.scanError("接受条件最终文件复核失败: "+ex.getClass().getSimpleName());reason=state.evidence;
                    codeComparisonEvidence=acceptanceEvidence=state.evidence;
                }
            }
        }
        if(state.isScanError())codeEvidence=LicenseRecoverReadOnlyCodeEvidence.unassessed("扫描失败，代码结构证据未确认");
        return new Result(root,version,runtime.name(),state,artifacts,reason,protocol,codeEvidence,codeComparisonEvidence,acceptanceEvidence,modeRelations);
    }

    private static void stringArray(StringBuilder b,String name,List<String> values) {
        b.append(quote(name)).append(": [");
        for(int i=0;i<values.size();i++){if(i>0)b.append(',');b.append(quote(values.get(i)));}
        b.append(']');
    }

    /** Compatibility seam for source-level regression fixtures. */
    static LicenseRecoverLicenseState unverifiedJavaEvidence(LicenseRecoverLicenseState gate,
            LicenseRecoverLicenseState local,LicenseRecoverLicenseState alternate) {
        return LicenseRecoverReadOnlyJavaScanner.unresolved(gate,local,alternate);
    }

    static boolean sameCodeEvidence(LicenseRecoverLicenseState first,LicenseRecoverLicenseState last) {
        return first.authorization==last.authorization && first.codeIntegrity==last.codeIntegrity
                && first.acceptance==last.acceptance
                && first.localCondition==last.localCondition
                && first.source.equals(last.source) && first.evidence.equals(last.evidence);
    }
    static boolean componentReceiptsMatch(LicenseRecoverReadOnlyCodeEvidence code,List<Artifact> artifacts) {
        if(code.component!=LicenseRecoverReadOnlyCodeEvidence.Component.UNASSESSED&&code.componentHashes.isEmpty())return false;
        for(Map.Entry<String,String> receipt:code.componentHashes.entrySet()) {
            boolean match=false;for(Artifact artifact:artifacts)
                if(receipt.getKey().equals(artifact.path)&&receipt.getValue().equals(artifact.sha256)){match=true;break;}
            if(!match)return false;
        }
        return true;
    }

    private static final class Changed extends IOException { }
    private static void checkPath(Path p) throws IOException {
        Path absolute=p.toAbsolutePath().normalize(), cursor=absolute.getRoot();
        for(Path part:absolute) {
            cursor=cursor.resolve(part);
            if(Files.isSymbolicLink(cursor)||Files.readAttributes(cursor,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).isOther())throw new IOException("linked-path");
        }
    }
    private static Artifact readArtifact(File root,Path p,LicenseRecoverReadOnlyPatchScanner.Runtime runtime)
            throws IOException, java.security.GeneralSecurityException {
        checkPath(p);
        BasicFileAttributes before=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()>MAX_ARTIFACT)throw new IOException("unsupported-artifact");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[65536]; int n; long count=0;
            while((n=in.read(buffer))!=-1) {count+=n;if(count>MAX_ARTIFACT)throw new IOException("artifact-limit");sha.update(buffer,0,n);}
        }
        String name=p.getFileName().toString().toLowerCase(Locale.ROOT);
        String format=name.endsWith(".jar")?jarFormat(p):name.endsWith(".class")?classFormat(p)
                :name.endsWith(".dll")||name.endsWith(".exe")?peFormat(p):"reviewed-fixed-input; hash-only";
        BasicFileAttributes after=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())
                ||!Objects.equals(before.fileKey(),after.fileKey()))throw new Changed();
        StringBuilder hash=new StringBuilder();
        for(byte b:sha.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
        return new Artifact(root.toPath().toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize())
                .toString().replace('\\','/'),hash.toString(),format,after.size());
    }
    private static String jarFormat(Path p) throws IOException {
        int max=0,count=0;
        try(JarFile jar=new JarFile(p.toFile(),false)) {
            Enumeration<JarEntry> entries=jar.entries(); Set<String> names=new HashSet<String>();
            while(entries.hasMoreElements()) {
                JarEntry e=entries.nextElement();
                if(!names.add(e.getName()))throw new IOException("duplicate-jar-entry");
                if(!e.getName().endsWith(".class"))continue;
                if(++count>100000)throw new IOException("class-limit");
                try(DataInputStream in=new DataInputStream(jar.getInputStream(e))) {
                    if(in.readInt()!=0xcafebabe)throw new IOException("class-header");
                    in.readUnsignedShort();int major=in.readUnsignedShort();
                    if(major<45||major>100)throw new IOException("class-version");
                    max=Math.max(max,major);
                }
            }
        }
        if(count==0)throw new IOException("no-class-evidence");
        return "JAR/class-major-max="+max+"; minimum-Java="+(max<=48?"1."+(max-44):String.valueOf(max-44))
                +"; classes="+count+"; header-only";
    }
    private static String classFormat(Path p)throws IOException {
        try(DataInputStream in=new DataInputStream(Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))) {
            if(in.readInt()!=0xcafebabe)throw new IOException("class-header");
            in.readUnsignedShort();int major=in.readUnsignedShort();if(major<45||major>100)throw new IOException("class-version");
            return "class-major="+major+"; header-only";
        }
    }
    /** PE signature is structural evidence only, never a claim of a loadable CLR assembly. */
    private static String peFormat(Path p) throws IOException {
        return LicenseRecoverReadOnlyPeMetadata.read(p).describe();
    }
    private static String canonicalVersion(String s) {return s!=null&&s.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")?s:"unknown";}
    private static void pair(StringBuilder b,String k,String v) {b.append(quote(k)).append(':').append(quote(v)).append(',');}
    private static String quote(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(char c:(s==null?"":s).toCharArray()) {
            if(c=='"'||c=='\\')b.append('\\').append(c);
            else if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);
        }
        return b.append('"').toString();
    }
    /** JSON Lines on stdout only. No target writes, subprocesses or vendor execution. */
    public static void main(String[] args) throws Exception {
        if(args.length!=2||!"--scan-parent".equals(args[0]))throw new IllegalArgumentException("--scan-parent DIRECTORY");
        Path parent=Paths.get(args[1]);checkPath(parent);
        List<File> children=new ArrayList<File>();
        try(DirectoryStream<Path> dirs=Files.newDirectoryStream(parent)) {
            for(Path p:dirs)if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))children.add(p.toFile());
        }
        Collections.sort(children);
        for(File child:children)System.out.println(inspect(child).toJson());
    }
}
