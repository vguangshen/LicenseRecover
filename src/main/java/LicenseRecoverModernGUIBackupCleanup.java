import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe manual cleanup for backup files created by LicenseRecover. */
public final class LicenseRecoverModernGUIBackupCleanup {
    private static final String CONTROL_NAME = "LicenseRecoverBackupCleanupControl";
    private static final Pattern MARKED = Pattern.compile("(?i)^(.+)\\.(prewrite|prepatch)\\.(\\d{14})\\.bak$");
    private static final Pattern PRE_ONE_CLICK = Pattern.compile("(?i)^(.+)\\.(\\d{14})(?:-(\\d+))?\\.preoneclick\\.bak$");
    private static final Pattern LEGACY = Pattern.compile(
            "(?i)^(config\\.xml|Register\\.xml|RegisterUtil\\.class|ITMCReg[^\\\\/]*\\.jar|itmcRegedit\\.dll|ITMC\\.Regedit\\.dll)\\.(\\d{14})\\.bak$");

    private LicenseRecoverModernGUIBackupCleanup() { }

    static final class Candidate {
        final Path path; final String originalName; final String timestamp; final int sequence; final long size;
        Candidate(Path p, String n, String t, int s, long z) { path=p; originalName=n; timestamp=t; sequence=s; size=z; }
        String groupKey() {
            Path parent = path.getParent();
            String p = parent == null ? "" : parent.toAbsolutePath().normalize().toString();
            return p.toLowerCase(Locale.ROOT) + "\u0000" + originalName.toLowerCase(Locale.ROOT);
        }
    }

    public static final class Plan {
        public final File root; public final int keepNewest; public final List<File> matched, delete;
        public final int keepCount; public final long deleteBytes;
        Plan(File r, int k, List<File> m, List<File> d, int kept, long bytes) {
            root=r; keepNewest=k; matched=Collections.unmodifiableList(m); delete=Collections.unmodifiableList(d);
            keepCount=kept; deleteBytes=bytes;
        }
    }

    public static final class DeleteResult {
        public final int deleted, failed; public final long freedBytes;
        DeleteResult(int d, int f, long b) { deleted=d; failed=f; freedBytes=b; }
    }

    public static Plan scan(File root, int keepNewest) throws IOException {
        if (root==null || !root.isDirectory()) throw new IOException("清理根目录无效");
        if (keepNewest<0) throw new IllegalArgumentException("keepNewest 不能小于 0");
        final Path rootPath=root.toPath().toAbsolutePath().normalize();
        final List<Candidate> all=new ArrayList<Candidate>();
        Files.walkFileTree(rootPath,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) {
                return !dir.equals(rootPath)&&Files.isSymbolicLink(dir)?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) {
                if (attrs!=null&&attrs.isRegularFile()&&!Files.isSymbolicLink(file)) { Candidate c=parse(file,attrs.size()); if(c!=null) all.add(c); }
                return FileVisitResult.CONTINUE;
            }
        });
        Map<String,List<Candidate>> groups=new LinkedHashMap<String,List<Candidate>>();
        for(Candidate c:all) groups.computeIfAbsent(c.groupKey(),k->new ArrayList<Candidate>()).add(c);
        Comparator<Candidate> newest=(a,b)->{ int x=b.timestamp.compareTo(a.timestamp); if(x!=0)return x; x=Integer.compare(b.sequence,a.sequence); return x!=0?x:b.path.toString().compareToIgnoreCase(a.path.toString()); };
        List<File> del=new ArrayList<File>(); int kept=0; long bytes=0;
        for(List<Candidate> g:groups.values()) { Collections.sort(g,newest); for(int i=0;i<g.size();i++) { Candidate c=g.get(i); if(i<keepNewest)kept++; else {del.add(c.path.toFile());bytes+=c.size;} } }
        List<File> matched=new ArrayList<File>(); for(Candidate c:all) matched.add(c.path.toFile());
        Comparator<File> byPath=Comparator.comparing(File::getAbsolutePath,String.CASE_INSENSITIVE_ORDER);
        Collections.sort(matched,byPath); Collections.sort(del,byPath);
        return new Plan(rootPath.toFile(),keepNewest,matched,del,kept,bytes);
    }

    public static DeleteResult execute(Plan plan, Consumer<String> log) {
        if(plan==null||plan.root==null)return new DeleteResult(0,0,0);
        Consumer<String> out=log==null?s->{}:log; Path root=plan.root.toPath().toAbsolutePath().normalize();
        int deleted=0,failed=0; long freed=0;
        for(File f:plan.delete) try {
            Path p=f.toPath().toAbsolutePath().normalize();
            if(!p.startsWith(root)||Files.isSymbolicLink(p)||!Files.isRegularFile(p)||parse(p,Files.size(p))==null) {failed++;out.accept("[备份清理] 跳过已变化/不安全路径: "+p+"\n");continue;}
            long size=Files.size(p); Files.delete(p); deleted++; freed+=size; out.accept("[备份清理] 已删除: "+p+"\n");
        } catch(Exception ex) {failed++;out.accept("[备份清理] 删除失败: "+f.getAbsolutePath()+" : "+safe(ex)+"\n");}
        return new DeleteResult(deleted,failed,freed);
    }

    public static boolean isManagedBackupName(String name) {
        return name!=null&&(MARKED.matcher(name).matches()||PRE_ONE_CLICK.matcher(name).matches()||LEGACY.matcher(name).matches());
    }
    private static Candidate parse(Path path,long size) {
        String n=path.getFileName().toString(); Matcher m=MARKED.matcher(n);
        if(m.matches())return new Candidate(path,m.group(1),m.group(3),0,size);
        m=PRE_ONE_CLICK.matcher(n); if(m.matches()){int seq=0;try{if(m.group(3)!=null)seq=Integer.parseInt(m.group(3));}catch(Exception ignore){}return new Candidate(path,m.group(1),m.group(2),seq,size);}
        m=LEGACY.matcher(n); return m.matches()?new Candidate(path,m.group(1),m.group(2),0,size):null;
    }

    static String formatBytes(long b){if(b<1024)return b+" B";double v=b;String[]u={"KB","MB","GB","TB"};int i=-1;do{v/=1024;i++;}while(v>=1024&&i<u.length-1);return String.format(Locale.ROOT,"%.2f %s",v,u[i]);}
    private static boolean contains(String[]a,String s){if(a!=null)for(String x:a)if(s.equals(x))return true;return false;}
    private static String safe(Throwable e){Throwable x=e;while(x!=null&&x.getCause()!=null)x=x.getCause();String s=x==null?"未知错误":x.getMessage();return s==null||s.trim().isEmpty()?String.valueOf(x):s;}

    /** CI self-test: matching + retention + deletion + unrelated-file safety. */
    public static void main(String[] args)throws Exception{
        if(!contains(args,"--self-test"))return; Path root=Files.createTempDirectory("lrc-backup-cleanup-");
        try{
            Path app=root.resolve("app-a/WEB-INF/lib");Files.createDirectories(app);
            write(app.resolve("config.xml.20260901010101.bak"),11);write(app.resolve("config.xml.prewrite.20260902010101.bak"),12);
            write(app.resolve("config.xml.20260903010101.preoneclick.bak"),13);write(app.resolve("config.xml.20260904010101-2.preoneclick.bak"),14);
            write(app.resolve("ITMCReg.jar.20260901010101.bak"),21);write(app.resolve("ITMCReg.jar.prepatch.20260902010101.bak"),22);write(app.resolve("ITMCReg.jar.20260903010101.preoneclick.bak"),23);
            Path unrelated=app.resolve("database.20260901010101.bak"),ordinary=app.resolve("notes.bak");write(unrelated,31);write(ordinary,32);
            Plan p=scan(root.toFile(),2); require(p.matched.size()==7,"managed backup match count");require(p.delete.size()==3,"retention delete count");require(p.keepCount==4,"retention keep count");
            require(isManagedBackupName("x.prepatch.20260910121212.bak"),"prepatch matcher");require(isManagedBackupName("config.xml.20260910121212.preoneclick.bak"),"preoneclick matcher");require(!isManagedBackupName("database.20260910121212.bak"),"unrelated timestamp backup excluded");require(!isManagedBackupName("notes.bak"),"ordinary bak excluded");
            DeleteResult r=LicenseRecoverModernGUIBackupCleanup.execute(p,System.out::print);require(r.deleted==3&&r.failed==0,"cleanup execution");
            require(Files.exists(app.resolve("config.xml.20260904010101-2.preoneclick.bak")),"newest config backup retained");require(Files.exists(app.resolve("config.xml.20260903010101.preoneclick.bak")),"second newest config backup retained");require(!Files.exists(app.resolve("config.xml.20260901010101.bak")),"old config backup deleted");require(Files.exists(unrelated)&&Files.exists(ordinary),"unrelated backups preserved");
            System.out.println("BACKUP CLEANUP SELF-TEST PASSED");
        }finally{deleteTree(root);}
    }
    private static void write(Path p,int n)throws IOException{byte[]b=new byte[Math.max(1,n)];b[0]=(byte)n;Files.write(p,b);}
    private static void require(boolean ok,String s){if(!ok)throw new AssertionError(s);System.out.println("PASS: "+s);}
    private static void deleteTree(Path root){if(root==null||!Files.exists(root))return;try{Files.walkFileTree(root,new SimpleFileVisitor<Path>(){@Override public FileVisitResult visitFile(Path f,BasicFileAttributes a)throws IOException{Files.deleteIfExists(f);return FileVisitResult.CONTINUE;}@Override public FileVisitResult postVisitDirectory(Path d,IOException e)throws IOException{Files.deleteIfExists(d);return FileVisitResult.CONTINUE;}});}catch(IOException ignore){}}
}
