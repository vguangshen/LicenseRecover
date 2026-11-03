import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Exact local CIL replacement candidates only; never executes, unpacks or patches an assembly. */
public final class LicenseRecoverReadOnlyDotNetCodeEvidence {
    private static final int LIMIT=16*1024*1024;
    private static final String[] COMPONENTS={"ITMC.Regedit.dll","itmcRegedit.dll"};
    private LicenseRecoverReadOnlyDotNetCodeEvidence() { }
    public static LicenseRecoverReadOnlyCodeEvidence inspect(File appRoot) {return inspectReviewed(appRoot,null);}

    /** A synthetic mutation seam. Production has no callback or imported trust records. */
    static LicenseRecoverReadOnlyCodeEvidence inspectReviewed(File appRoot,Runnable beforeRecheck) {
        if(appRoot==null)return LicenseRecoverReadOnlyCodeEvidence.unassessed("缺少 .NET 应用目录");
        try {
            Observation first=observe(appRoot.toPath(),true);
            if(beforeRecheck!=null)beforeRecheck.run();
            Observation last=observe(appRoot.toPath(),false);
            if(!first.hashes.equals(last.hashes))return LicenseRecoverReadOnlyCodeEvidence.unassessed(
                    ".NET 注册组件在读取期间变化，已撤销局部替换形态观测");
            if(first.candidates.isEmpty())return LicenseRecoverReadOnlyCodeEvidence.unassessed(
                    "未发现所支持的完整托管布尔短路方法形态；保护体、VM、复杂方法及其他替换方式未评估；不代表原版或未 Patch");
            Map<String,String> receipts=new TreeMap<String,String>();
            for(Map.Entry<String,String> item:first.hashes.entrySet())receipts.put("bin/"+item.getKey(),item.getValue());
            return LicenseRecoverReadOnlyCodeEvidence.replacementCandidate(
                    "当前磁盘完整方法体精确匹配标准 CIL 常量/本地引用置空短路形态："+String.join("；",first.candidates)
                    +"；仅为局部替换候选：初始化器、保护器运行态、调用方及依赖未获完整证明；没有可信原版/Patch 对照，未判授权有效或确认 Patch",
                    "bounded-PE-CLI-method-body; SHA-256="+first.hashes.toString()).bindComponents(receipts);
        }catch(Changed e) {
            return LicenseRecoverReadOnlyCodeEvidence.unassessed(".NET 注册组件读取期间变化，局部代码形态已撤销");
        }catch(IOException|SecurityException e) {
            return LicenseRecoverReadOnlyCodeEvidence.unassessed(
                    ".NET 局部代码形态未完成只读核验（文件缺失、链接、超限、损坏或不支持的元数据）；未据此认定 Patch");
        }
    }
    private static final class Observation {
        final Map<String,String> hashes=new TreeMap<String,String>();
        final List<String> candidates=new ArrayList<String>();
    }
    private static Observation observe(Path root,boolean inspect)throws IOException {
        Observation out=new Observation();Path bin=root.toAbsolutePath().normalize().resolve("bin");
        for(String file:COMPONENTS) {
            Read current;
            try{current=read(bin.resolve(file),inspect);}catch(NoSuchFileException absent){continue;}
            out.hashes.put(file,current.hash);
            if(!inspect)continue;
            LicenseRecoverReadOnlyPeMetadata.ApiShape api=LicenseRecoverReadOnlyPeMetadata.parseApiShape(current.bytes);
            String ns=file.equals("ITMC.Regedit.dll")?"ITMC.Regedit":"itmcRegedit";
            String owner=ns+".RegeditMain",info=ns+".RegeditInfo&";
            if(!api.hasType(owner)||!api.hasType(ns+".RegeditInfo"))continue;
            candidate(out,api,file,owner,"CheckReInfo",info);
            candidate(out,api,file,owner,"CheckNet",info);
            candidate(out,api,file,owner,"CheckNet");
        }
        return out;
    }
    private static void candidate(Observation out,LicenseRecoverReadOnlyPeMetadata.ApiShape api,
            String file,String owner,String name,String... parameters)throws IOException {
        LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape shape=api.booleanBodyShape(owner,name,parameters);
        if(shape!=LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED)
            out.candidates.add(file+" "+owner+"."+name+"("+String.join(",",parameters)+"): Boolean="
                    +(shape==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.CONSTANT_TRUE?"true":"false")
                    +(shape==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.BYREF_NULL_FALSE?"、本地 class 引用参数置空":"")
                    +"（布尔含义由调用方决定）");
    }
    private static final class Read {final byte[] bytes;final String hash;Read(byte[] b,String h){bytes=b;hash=h;}}
    private static final class Changed extends IOException { }
    private static Read read(Path path,boolean keep)throws IOException {
        Path cursor=path.getRoot();
        for(Path part:path) {
            cursor=cursor.resolve(part);BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(a.isSymbolicLink()||a.isOther())throw new IOException("linked-code-input");
        }
        BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()<64||before.size()>LIMIT)throw new IOException("code-input-limit");
        byte[] bytes=keep?new byte[(int)before.size()]:null,block=new byte[8192];
        MessageDigest digest=sha();long size=0;
        try(InputStream input=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            int count;while((count=input.read(block))!=-1) {
                if(count>before.size()-size)throw new Changed();
                if(keep)System.arraycopy(block,0,bytes,(int)size,count);size+=count;digest.update(block,0,count);
            }
        }
        BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||size!=before.size()||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())
                ||!Objects.equals(before.fileKey(),after.fileKey()))throw new Changed();
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
        return new Read(bytes,hex.toString());
    }
    private static MessageDigest sha() {try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
