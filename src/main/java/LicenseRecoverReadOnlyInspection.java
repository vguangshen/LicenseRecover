import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;

/** File-only evidence collection. Never loads a vendor class/assembly or reads license values. */
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
        public final boolean inspectionReadOnlyAudited = true;
        public final boolean nativeVerifierReadOnlyAudited = false;
        public final LicenseRecoverLicenseState state;
        public final List<Artifact> artifacts;
        Result(File root, String version, String runtime, LicenseRecoverLicenseState state,
               List<Artifact> artifacts, String reason) {
            this.name = root.getName(); this.version = canonicalVersion(version);
            this.runtime = runtime; this.state = state;
            this.method = "file-only-sha256+format+reviewed-pair";
            this.evidenceSource = "current-files; compiled-reviewed-registry";
            this.reason = reason;
            this.nativeVerifierSafety = LicenseRecoverReadOnlyVerifierSafety.rejection(artifacts);
            this.artifacts = Collections.unmodifiableList(new ArrayList<Artifact>(artifacts));
        }
        public String summary() {
            return "方法=" + method + "；组件=" + artifacts.size()
                    + "；文件检查只读审计=通过；原生验证只读审计=未通过；" + nativeVerifierSafety + "；" + reason;
        }
        public String toJson() {
            StringBuilder b = new StringBuilder("{");
            pair(b,"name",name); pair(b,"version",version); pair(b,"runtime",runtime);
            pair(b,"versionMeaning","configuration-product-id"); pair(b,"releaseVersion","unproven");
            pair(b,"status",state.visualStatus().name()); pair(b,"label",state.display());
            pair(b,"authorization",state.authorization.name()); pair(b,"codeIntegrity",state.codeIntegrity.name());
            pair(b,"method",method); pair(b,"evidenceSource",evidenceSource); pair(b,"reason",reason);
            pair(b,"nativeVerifierSafety",nativeVerifierSafety);
            b.append("\"inspectionReadOnlyAudited\":true,\"nativeVerifierReadOnlyAudited\":false,\"artifacts\":[");
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
                LicenseRecoverReadOnlyPatchScanner.scan(dir, runtime, version));
    }

    /** Shared GUI seam: existing detector metadata remains separate from license evidence. */
    static Result inspect(File root, File dir, LicenseRecoverReadOnlyPatchScanner.Runtime runtime,
                          String version, LicenseRecoverLicenseState codeState) {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        LicenseRecoverLicenseState state = codeState;
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
            LicenseRecoverLicenseState current = LicenseRecoverReadOnlyPatchScanner.scan(dir,runtime,version);
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
        return new Result(root,version,runtime.name(),state,artifacts,reason);
    }

    static boolean sameCodeEvidence(LicenseRecoverLicenseState first,LicenseRecoverLicenseState last) {
        return first.authorization==last.authorization && first.codeIntegrity==last.codeIntegrity
                && first.source.equals(last.source) && first.evidence.equals(last.evidence);
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
        String format=runtime==LicenseRecoverReadOnlyPatchScanner.Runtime.JAVA?jarFormat(p):peFormat(p);
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
