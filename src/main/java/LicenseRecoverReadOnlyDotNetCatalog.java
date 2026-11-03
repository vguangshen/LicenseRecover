import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Shared protocol classification and audited data bindings. Neither identifies activation by name. */
public final class LicenseRecoverReadOnlyDotNetCatalog {
    private LicenseRecoverReadOnlyDotNetCatalog() { }
    private static final long MAX_SET_BYTES=192L*1024*1024;
    private static final int MAX_METADATA_BYTES=16*1024*1024;
    public enum Family { MODERN_JSON_DES, LEGACY_FIXED_RECORD, PROTECTED_COMPATIBILITY,
        REGINFO_API_CANDIDATE, COMPATIBILITY_API_CANDIDATE, UNKNOWN }
    enum LocalFormat { FIXED_MARKET_RECORD, PROTECTED_VM }
    static final class UpperBinding {
        final String web,registration,product,cryptoFile,cryptoHash,jsonHash,logHash;
        UpperBinding(String web,String registration,String product,String file,String crypto,String json,String log) {
            this.web=web;this.registration=registration;this.product=product;cryptoFile=file;
            cryptoHash=crypto;jsonHash=json;logHash=log;
        }
        boolean matches(Map<String,String> h) {
            return eq(h,"ITMC.Web.dll",web)&&eq(h,"ITMC.Regedit.dll",registration)
                    &&eq(h,cryptoFile,cryptoHash)&&eq(h,"Newtonsoft.Json.dll",jsonHash)
                    &&eq(h,"log4net.dll",logHash)&&eq(h,"LiteDB.dll",LITE);
        }
    }
    static final class ExtendedBinding {
        final String web,upper,lower,crypto;final LocalFormat format;
        ExtendedBinding(String web,String upper,String lower,String crypto,LocalFormat format) {
            this.web=web;this.upper=upper;this.lower=lower;this.crypto=crypto;this.format=format;
        }
        boolean matches(Map<String,String> h) {return eq(h,"ITMC.Web.dll",web)&&eq(h,"ITMC.Regedit.dll",upper)
                &&eq(h,"itmcRegedit.dll",lower)&&eq(h,"ITMC.DBUtility.dll",crypto);}
        boolean legacy(){return format==LocalFormat.FIXED_MARKET_RECORD;}
    }
    private static final String LITE="4091c455a882e3e44c8dc0c4034e6b2536f180ce019695769ef72497a90467ec";
    private static final String JSON6="78bedd9fce877a71a8d8ff9a813662d8248361e46705c4ef7afc61d440ff2eeb";
    private static final String JSON7="9eca2c5ed187b5e1319c359aabf8382992131efe1854b899c23a93fe227496e3";
    private static final String LOG_OLD="af5610c515d2244db98c662636264c8177e89b1afe407f88fd18a41d66f6e7e2";
    private static final String LOG_NEW="124b4cd7d62656698d43d5adc3645eab88199717bb99497549d2c74a26c68e1d";
    // All three use the SAME JSON/DES predicate. These are evidence/parameter records, not strategies.
    private static final UpperBinding[] UPPER={
        new UpperBinding("16b13b547893b160ef928fd6f06568770da1fdd2326a1dfdd4bc5c54e047562c",
            "735a7f868825111cd437cb5093eaa79f0a977330a79121fb2d18885290da743f","itmcIEC",
            "ItmcBtoB.DBUtility.dll","9430693eea94e18077fb1fa0e187a0d4cb6c4bf472eca4cdd075366e9b8eb92c",JSON6,LOG_OLD),
        new UpperBinding("21464d180e30ed1f10c0c6b20e56381191ecd70aab06595f07a27dc4d559961a",
            "25b6b2939534b8266d843f7bd7b942d9ee87fd48296c8ed05828ffc3de76c0d8","itmcIEC",
            "ItmcBtoB.DBUtility.dll","7d880b03364e4c31c39c47a053144345b5b02804ac7a0e80a87ebf24096fd999",JSON6,LOG_OLD),
        new UpperBinding("57fa95aa72a15bfd569834374860f29dbef22a48294f7010e94846023a94ce35",
            "f5a2c38dee0f694c33fdac2c5570c2431b955c594e244e87596f120c0eb07f99","YX0303",
            "ITMC.DBUtility.dll","f8c2ff0b28b03bfc33ce61e32f63d8b179c0c213a4ccab8ba748aa4dd73d5c22",JSON7,LOG_NEW)
    };
    private static final ExtendedBinding[] EXTENDED={
        new ExtendedBinding("1790526c91b0dc51aa914d80604e600175cfb3d8899c8299bb3b2c85684b9b20",
            "c942c3c21466979e63eee1ffadbfe7273c83c2dc0a455f324c9da7e187953f46",
            "36f58b8657bce75a19356796f21c135104e28f5abc4bc9007f0d4ec517f479df",
            "236cfe610b87fb7a4f6589cb2a9e319a048b8a8e2a1b79d3028d4d7e1b5fcb6b",LocalFormat.FIXED_MARKET_RECORD),
        new ExtendedBinding("929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725",
            "35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f",
            "dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",LocalFormat.PROTECTED_VM),
        new ExtendedBinding("07183f09c19b69c5c4ddcdcb43ad4b8d91cf56f629f6a3d7d675dfc84f761079",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725",
            "35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f",
            "dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",LocalFormat.PROTECTED_VM)
    };
    private static final String[] COMPONENTS={"config.xml","ITMC.Web.dll","ITMC.Regedit.dll","itmcRegedit.dll",
            "ItmcBtoB.DBUtility.dll","ITMC.DBUtility.dll","Newtonsoft.Json.dll","log4net.dll","LiteDB.dll"};
    static final class Changed extends IOException { }
    public static final class Assessment {
        public final Family upperFamily,compatibilityFamily;
        public final boolean auditedBinding;
        final UpperBinding upper;final ExtendedBinding extended;final Map<String,String> hashes;
        Assessment(Family a,Family b,UpperBinding u,ExtendedBinding e,Map<String,String> hashes) {
            upperFamily=a;compatibilityFamily=b;upper=u;extended=e;auditedBinding=u!=null||e!=null;
            this.hashes=Collections.unmodifiableMap(new TreeMap<String,String>(hashes));
        }
        public String summary() {
            String family=upperFamily==Family.MODERN_JSON_DES?"现代本地记录协议"
                    :upperFamily==Family.REGINFO_API_CANDIDATE?"同系列注册接口候选":"未识别的大写注册协议";
            if(compatibilityFamily!=Family.UNKNOWN)family+="及兼容注册组件";
            return family+(auditedBinding?"；当前入口与依赖已匹配审核证据":"；当前完整授权逻辑尚未获得审核证明");
        }
        boolean sameInputs(Assessment other) {return other!=null&&hashes.equals(other.hashes);}
    }
    static Assessment assess(File root,boolean structuralCandidates)throws IOException {
        if(root==null)return classify(Collections.<String,String>emptyMap(),null,null);
        Path app=root.toPath().toAbsolutePath().normalize(),bin=app.resolve("bin");
        Map<String,String> hashes=new TreeMap<String,String>();Map<String,Observed> observations=new HashMap<String,Observed>();
        long total=0;
        for(String file:COMPONENTS) {
            Path p=file.equals("config.xml")?app.resolve(file):bin.resolve(file);Observed observed;
            try{observed=observe(p,file.equals("config.xml")?4*1024*1024:128*1024*1024,MAX_SET_BYTES-total);}
            catch(NoSuchFileException missing){continue;}
            total+=observed.size;hashes.put(file,observed.hash);observations.put(file,observed);
        }
        Assessment known=classify(hashes,null,null);
        LicenseRecoverReadOnlyPeMetadata.ApiShape upper=null,lower=null;
        // Hash every input in constant space first. Unknown API metadata is then read one
        // component at a time, with a smaller cap and the original digest as its receipt.
        if(structuralCandidates&&known.upperFamily==Family.UNKNOWN&&observations.containsKey("ITMC.Regedit.dll"))
            upper=apiShape(bin.resolve("ITMC.Regedit.dll"),observations.get("ITMC.Regedit.dll"));
        if(structuralCandidates&&known.compatibilityFamily==Family.UNKNOWN&&observations.containsKey("itmcRegedit.dll"))
            lower=apiShape(bin.resolve("itmcRegedit.dll"),observations.get("itmcRegedit.dll"));
        return classify(hashes,upper,lower);
    }
    /** Metadata shape admits a candidate only. It can never create an audited binding. */
    static Assessment classify(Map<String,String> hashes,LicenseRecoverReadOnlyPeMetadata.ApiShape shape,
            LicenseRecoverReadOnlyPeMetadata.ApiShape lowerShape) {
        UpperBinding upper=null;ExtendedBinding extended=null;Family f=Family.UNKNOWN,lower=Family.UNKNOWN;
        for(UpperBinding b:UPPER) {if(eq(hashes,"ITMC.Regedit.dll",b.registration))f=Family.MODERN_JSON_DES;
            if(b.matches(hashes)){if(upper!=null)throw new IllegalStateException("conflicting-modern-audit-bindings");upper=b;}}
        for(ExtendedBinding b:EXTENDED) {
            if(eq(hashes,"ITMC.Regedit.dll",b.upper))f=Family.MODERN_JSON_DES;
            if(eq(hashes,"itmcRegedit.dll",b.lower))lower=b.legacy()?Family.LEGACY_FIXED_RECORD:Family.PROTECTED_COMPATIBILITY;
            if(b.matches(hashes)){if(extended!=null)throw new IllegalStateException("conflicting-compatibility-audit-bindings");extended=b;}
        }
        if(f==Family.UNKNOWN&&shape!=null&&shape.upperRegistrationApi&&shape.upperJsonInfoModel)f=Family.REGINFO_API_CANDIDATE;
        if(lower==Family.UNKNOWN&&lowerShape!=null&&lowerShape.lowerRegistrationApi)lower=Family.COMPATIBILITY_API_CANDIDATE;
        return new Assessment(f,lower,upper,extended,hashes);
    }
    private static boolean eq(Map<String,String> h,String file,String sha) {return sha.equals(h.get(file));}
    private static final class Observed {
        final long size;final String hash;Observed(long size,String hash){this.size=size;this.hash=hash;}
    }
    private static BasicFileAttributes attributes(Path p)throws IOException {
        Path cursor=p.getRoot();for(Path part:p){cursor=cursor.resolve(part);BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(a.isSymbolicLink()||a.isOther())throw new IOException("linked-protocol-component");}
        return Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
    }
    private static void unchanged(Path p,BasicFileAttributes before,long count)throws IOException {
        BasicFileAttributes after=attributes(p);
        if(!after.isRegularFile()||count!=before.size()||before.size()!=after.size()
                ||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))
            throw new Changed();
    }
    private static Observed observe(Path p,int limit,long remaining)throws IOException {
        BasicFileAttributes before=attributes(p);
        if(!before.isRegularFile()||before.size()>limit)throw new IOException("protocol-component-limit");
        if(before.size()>remaining)throw new IOException("protocol-set-limit");
        MessageDigest digest=sha256();long count=0;
        try {
            try(InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){
                    count+=n;if(count>before.size())throw new Changed();digest.update(b,0,n);
                }
            }
            unchanged(p,before,count);
        }catch(NoSuchFileException missing){throw new Changed();}
        return new Observed(count,hex(digest.digest()));
    }
    private static LicenseRecoverReadOnlyPeMetadata.ApiShape apiShape(Path p,Observed observed)throws IOException {
        if(observed.size>MAX_METADATA_BYTES)throw new IOException("protocol-metadata-limit");
        try {
            BasicFileAttributes before=attributes(p);
            if(!before.isRegularFile()||before.size()!=observed.size)throw new Changed();
            // Exact bounded allocation avoids ByteArrayOutputStream growth and copy peaks.
            byte[] bytes=new byte[(int)observed.size];int count=0;MessageDigest digest=sha256();
            try(InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                while(count<bytes.length) {
                    int n=in.read(bytes,count,bytes.length-count);if(n==-1)throw new Changed();
                    digest.update(bytes,count,n);count+=n;
                }
                if(in.read()!=-1)throw new Changed();
            }
            unchanged(p,before,count);
            if(!observed.hash.equals(hex(digest.digest())))throw new Changed();
            return LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes);
        }catch(NoSuchFileException missing){throw new Changed();}
    }
    private static MessageDigest sha256() {
        try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static String hex(byte[] d) {
        StringBuilder s=new StringBuilder();for(byte x:d)s.append(Character.forDigit((x>>>4)&15,16)).append(Character.forDigit(x&15,16));return s.toString();
    }
    static String hash(byte[] b) {return hex(sha256().digest(b));}
}
