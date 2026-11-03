import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Component-scoped reference evidence only. Never loads or invokes target classes. */
public final class LicenseRecoverReadOnlyJavaCodeEvidence {
    private LicenseRecoverReadOnlyJavaCodeEvidence() { }
    private static final int MAX_COMPONENT_BYTES=16*1024*1024;
    private static final int MAX_DIRECTORY_ENTRIES=4096;
    private static final long MAX_JAR_CONTAINER_BYTES=128L*1024*1024;
    private static final int MAX_CENTRAL_DIRECTORY_BYTES=4*1024*1024;
    private static final long MAX_ZIP_METADATA_SET_BYTES=64L*1024*1024;
    private static final byte[] REGISTER_MAIN="itmc/regedit/RegisterMain.class".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final String J1="ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa";
    private static final String SOURCE="user-attested-YT00123-baseline-20261102; docs/JAVA_PATCH_EVIDENCE_BOUNDARIES.md";

    public static LicenseRecoverReadOnlyCodeEvidence inspect(File root) {
        return inspectReviewed(root,J1,SOURCE,null);
    }

    /** Synthetic seam. Production reference bytes and source are fixed above. */
    static LicenseRecoverReadOnlyCodeEvidence inspectReviewed(File root,String referenceHash,
            String referenceSource,Runnable beforeRecheck) {
        if(root==null)return unknown("缺少 Java 应用目录，无法对照注册组件");
        if(referenceHash==null||!referenceHash.matches("[0-9a-f]{64}")
                ||referenceSource==null||referenceSource.trim().isEmpty())
            throw new IllegalArgumentException("a complete component reference is required");
        try {
            Path selected=root.toPath().toAbsolutePath().normalize();
            Observation first=observe(selected);
            if(beforeRecheck!=null)beforeRecheck.run();
            Observation second=observe(selected);
            if(!first.same(second))return unknown("Java 注册组件或目录身份在读取期间变化，撤销组件对照");
            if(!referenceHash.equals(second.hash))
                return unknown("当前 Java 注册组件未匹配用户提供的原版组件对照；不能据此认定 Patch");
            return LicenseRecoverReadOnlyCodeEvidence.referenceComponent(
                    "注册组件与用户提供的未修改 YT00123 对照逐字节一致（仅组件）；"
                    +"来源为用户说明，尚无厂商安装包签名核验；不证明整站原版、原生激活或历史 Patch；SHA-256 "+second.hash,
                    referenceSource).bindComponents(java.util.Collections.singletonMap("WEB-INF/lib/"+second.name,second.hash));
        }catch(IOException|SecurityException e) {
            return unknown("Java 注册组件对照不可采信："+e.getClass().getSimpleName()+"；"+safeReason(e));
        }
    }

    private static LicenseRecoverReadOnlyCodeEvidence unknown(String reason) {
        return LicenseRecoverReadOnlyCodeEvidence.unassessed(reason);
    }
    private static String safeReason(Exception e) {
        // Exception paths and third-party error text can contain private values.
        return e instanceof Unsupported?e.getMessage():"输入缺失、读取失败或未满足稳定的常规文件要求";
    }
    private static final class Unsupported extends IOException {
        Unsupported(String reason){super(reason);}
    }
    private static final class Observation {
        final String name,hash;final BasicFileAttributes root,lib,file;
        Observation(String name,String hash,BasicFileAttributes root,BasicFileAttributes lib,BasicFileAttributes file) {
            this.name=name;this.hash=hash;this.root=root;this.lib=lib;this.file=file;
        }
        boolean same(Observation other) {
            return name.equals(other.name)&&hash.equals(other.hash)&&sameAttributes(root,other.root)
                    &&sameAttributes(lib,other.lib)&&sameAttributes(file,other.file);
        }
    }
    private static Observation observe(Path root)throws IOException {
        BasicFileAttributes rootBefore=attributes(root);
        Path lib=root.resolve("WEB-INF/lib");
        BasicFileAttributes libBefore=attributes(lib);
        if(!rootBefore.isDirectory()||!libBefore.isDirectory())throw new Unsupported("缺少常规 Java 组件目录");
        List<Path> registrations=new ArrayList<Path>(),jars=new ArrayList<Path>();
        int entries=0;
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(lib)) {
            for(Path p:stream) {
                interrupted();
                if(++entries>MAX_DIRECTORY_ENTRIES)throw new Unsupported("组件目录条目超过只读预算");
                String name=p.getFileName().toString().toLowerCase(Locale.ROOT);
                if(!name.endsWith(".jar"))continue;
                jars.add(p);
                if(name.startsWith("itmcreg"))registrations.add(p);
            }
        }
        if(registrations.size()!=1)throw new Unsupported("需要唯一注册 JAR；缺失或多个候选时不选择其中一份");
        Path file=registrations.get(0);
        BasicFileAttributes before=attributes(file);
        if(!before.isRegularFile()||before.size()>MAX_COMPONENT_BYTES)
            throw new Unsupported("注册组件不是常规文件或超过 16 MiB 只读预算");
        long[] metadataBudget={MAX_ZIP_METADATA_SET_BYTES};
        for(Path other:jars) {
            if(file.equals(other))continue;
            BasicFileAttributes otherAttributes=attributes(other);
            if(!otherAttributes.isRegularFile())throw new Unsupported("组件目录含非普通 JAR 候选");
            if(Files.isSameFile(file,other))throw new Unsupported("注册组件存在同一文件的别名 JAR");
            if(containsRegistrationClass(other,otherAttributes,metadataBudget))
                throw new Unsupported("其他名称 JAR 也包含注册入口，组件选择存在歧义");
        }
        MessageDigest digest=sha256();long count=0;
        try(InputStream input=Files.newInputStream(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[65536];int n;
            while((n=input.read(buffer))!=-1) {
                interrupted();
                count+=n;
                if(count>before.size()||count>MAX_COMPONENT_BYTES)throw new Unsupported("注册组件读取期间长度变化");
                digest.update(buffer,0,n);
            }
        }
        BasicFileAttributes after=attributes(file),rootAfter=attributes(root),libAfter=attributes(lib);
        if(count!=before.size()||!sameAttributes(before,after)||!sameAttributes(rootBefore,rootAfter)
                ||!sameAttributes(libBefore,libAfter))throw new Unsupported("注册组件或目录在读取期间变化");
        return new Observation(file.getFileName().toString(),hex(digest.digest()),rootAfter,libAfter,after);
    }

    /** Only bounded ZIP central-directory metadata is read; entry payloads are never inflated. */
    private static boolean containsRegistrationClass(Path path,BasicFileAttributes before,long[] budget)throws IOException {
        if(before.size()<22||before.size()>MAX_JAR_CONTAINER_BYTES)
            throw new Unsupported("依赖 JAR 超过元数据预算或缺少 ZIP 目录");
        try(SeekableByteChannel input=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            int tailLength=(int)Math.min(before.size(),65557);
            if(tailLength>budget[0])throw new Unsupported("依赖 JAR 元数据总量超过只读预算");
            budget[0]-=tailLength;
            byte[] tail=readAt(input,before.size()-tailLength,tailLength);
            int end=-1;
            for(int at=tail.length-22;at>=0;at--)
                if(u4(tail,at)==0x06054b50L&&at+22+u2(tail,at+20)==tail.length){end=at;break;}
            if(end<0||u2(tail,end+4)!=0||u2(tail,end+6)!=0
                    ||u2(tail,end+8)!=u2(tail,end+10))throw new Unsupported("依赖 JAR 目录格式不受支持");
            int entries=u2(tail,end+10);long length=u4(tail,end+12),offset=u4(tail,end+16);
            if(entries==65535||length>MAX_CENTRAL_DIRECTORY_BYTES||length>budget[0]||offset==0xffffffffL
                    ||offset+length>before.size()-tailLength+end)
                throw new Unsupported("依赖 JAR 目录超过元数据预算或使用未审 ZIP64");
            budget[0]-=length;
            byte[] directory=readAt(input,offset,(int)length);int at=0;boolean found=false;
            for(int entry=0;entry<entries;entry++) {
                interrupted();
                if(at+46>directory.length||u4(directory,at)!=0x02014b50L)
                    throw new Unsupported("依赖 JAR 目录条目损坏");
                int names=u2(directory,at+28),extra=u2(directory,at+30),comment=u2(directory,at+32);
                long next=(long)at+46+names+extra+comment;
                if(next>directory.length)throw new Unsupported("依赖 JAR 目录名称越界");
                if(names==REGISTER_MAIN.length) {
                    boolean equal=true;for(int i=0;i<names;i++)if(directory[at+46+i]!=REGISTER_MAIN[i]){equal=false;break;}
                    found|=equal;
                }
                at=(int)next;
            }
            if(at!=directory.length||!sameAttributes(before,attributes(path)))
                throw new Unsupported("依赖 JAR 元数据在读取期间变化或未完整解析");
            return found;
        }
    }
    private static byte[] readAt(SeekableByteChannel input,long position,int length)throws IOException {
        byte[] bytes=new byte[length];ByteBuffer buffer=ByteBuffer.wrap(bytes);input.position(position);
        while(buffer.hasRemaining()){interrupted();if(input.read(buffer)<0)throw new Unsupported("JAR 元数据读取期间截断");}
        return bytes;
    }
    private static int u2(byte[] b,int at){return(b[at]&255)|((b[at+1]&255)<<8);}
    private static long u4(byte[] b,int at){return(u2(b,at)&65535L)|((long)u2(b,at+2)<<16);}
    private static BasicFileAttributes attributes(Path path)throws IOException {
        interrupted();
        Path absolute=path.toAbsolutePath().normalize(),cursor=absolute.getRoot();
        for(Path part:absolute) {
            cursor=cursor.resolve(part);
            BasicFileAttributes attrs=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(attrs.isSymbolicLink()||attrs.isOther())throw new Unsupported("链接或特殊文件布局不建立组件信任");
        }
        return Files.readAttributes(absolute,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
    }
    private static boolean sameAttributes(BasicFileAttributes a,BasicFileAttributes b) {
        return a.isDirectory()==b.isDirectory()&&a.isRegularFile()==b.isRegularFile()&&a.size()==b.size()
                &&a.lastModifiedTime().equals(b.lastModifiedTime())&&a.creationTime().equals(b.creationTime())
                &&Objects.equals(a.fileKey(),b.fileKey());
    }
    private static void interrupted()throws IOException {
        if(Thread.currentThread().isInterrupted())throw new Unsupported("只读组件检查已取消");
    }
    private static MessageDigest sha256() {
        try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes)out.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
        return out.toString();
    }
}
