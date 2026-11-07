import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/**
 * Current-state diagnostics for the reviewed YX legacy/VM registration builds.
 * Local failure is never promoted to global invalidity: these builds retain other paths.
 * No target assembly, native registration method, network or registry operation is run.
 */
public final class LicenseRecoverReadOnlyDotNetExtendedVerifier {
    private LicenseRecoverReadOnlyDotNetExtendedVerifier() { }
    private static final int MAX_CONFIG=4*1024*1024, MAX_BINARY=128*1024*1024;
    public enum LocalStatus {
        NOT_APPLICABLE, NO_LOCAL_RECORD, LEGACY_MACHINE_MISMATCH, LEGACY_EXPIRED,
        LEGACY_LOCAL_CONDITIONS_MATCHED, LEGACY_FORMAT_UNSUPPORTED, MODERN_RECORD_CANDIDATE, IDENTITY_UNAVAILABLE,
        UPPER_JSON_FORMAT_MISMATCH_LOWER_VM_PENDING, LOWER_VM_PENDING, INPUT_CHANGED, INSPECTION_ERROR
    }
    public static final class Result {
        public final boolean applies, readOnlyAudited=true, fullAuthorizationPathAudited=false;
        public final LocalStatus localStatus;
        public final LicenseRecoverLicenseState state;
        public final String dispatch, upperLocal, lowerLocal;
        private Result(boolean applies,LocalStatus localStatus,String reason,String dispatch,String upperLocal,String lowerLocal) {
            this.applies=applies;this.localStatus=localStatus;this.dispatch=dispatch;
            this.upperLocal=upperLocal;this.lowerLocal=lowerLocal;
            String evidence=reason;
            state=applies&&condition(localStatus)!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED
                    ?LicenseRecoverLicenseState.localObservation(condition(localStatus),evidence)
                    :LicenseRecoverLicenseState.unverified(evidence);
        }
        private Result(LicenseRecoverLicenseState error) {
            applies=true;localStatus=LocalStatus.INSPECTION_ERROR;state=error;
            dispatch="unknown";upperLocal="unknown";lowerLocal="unknown";
        }
    }
    public static LicenseRecoverLicenseState verify(File root) {return inspect(root).state;}
    public static Result inspect(File root) {
        try {
            LicenseRecoverReadOnlyDotNetCatalog.ExtendedBinding b=build(root);
            if(b==null)return result(false,LocalStatus.NOT_APPLICABLE,"未匹配本次审核的 legacy/VM 构建组合","unknown","unknown","unknown");
            // VM/format diagnostics do not use machine identity; avoid a needless OS query.
            return inspect(root,b.legacy()?LicenseRecoverReadOnlyMachineIdentity.current():null);
        }catch(NoSuchFileException missing){return result(false,LocalStatus.NOT_APPLICABLE,"缺少该协议的审核组件或根配置","unknown","unknown","unknown");}
        catch(LicenseRecoverReadOnlyDotNetCatalog.Changed e){return result(true,LocalStatus.INPUT_CHANGED,"协议组件读取期间变化","unknown","unknown","unknown");}
        catch(IOException|SecurityException e){return new Result(LicenseRecoverLicenseState.scanError("只读 legacy/VM 组件检查失败: "+e.getClass().getSimpleName()));}
        catch(Exception e){return result(false,LocalStatus.NOT_APPLICABLE,"当前构建尚不能应用此局部诊断","unknown","unknown","unknown");}
    }
    static Result inspect(File root,LicenseRecoverReadOnlyMachineIdentity.Snapshot identity) {
        try {
            LicenseRecoverReadOnlyDotNetCatalog.ExtendedBinding b=build(root);
            if(b==null)return result(false,LocalStatus.NOT_APPLICABLE,"未匹配本次审核的 legacy/VM 构建组合","unknown","unknown","unknown");
            return inspectMatched(root,b,identity);
        } catch(LicenseRecoverReadOnlyDotNetCatalog.Changed e){return result(true,LocalStatus.INPUT_CHANGED,"协议组件读取期间变化","unknown","unknown","unknown");}
        catch(IOException|SecurityException e) {
            return new Result(LicenseRecoverLicenseState.scanError("兼容协议读取失败: "+e.getClass().getSimpleName()));
        } catch(Exception e) {return result(false,LocalStatus.NOT_APPLICABLE,"当前兼容协议未获得审核证据","unknown","unknown","unknown");}
    }
    static Result inspectMatched(File root,LicenseRecoverReadOnlyDotNetCatalog.ExtendedBinding b,
            LicenseRecoverReadOnlyMachineIdentity.Snapshot identity) {
        try {
            Path cfg=root.toPath().resolve("config.xml");byte[] before=read(cfg,MAX_CONFIG);
            LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot registryBefore=LicenseRecoverReadOnlyDotNetRegistrySource.readCurrent();
            Result observed=diagnose(before,b.legacy(),identity,LocalDate.now());
            boolean stable=Arrays.equals(before,read(cfg,MAX_CONFIG))&&build(root)==b;
            LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot registryAfter=LicenseRecoverReadOnlyDotNetRegistrySource.readCurrent();
            return withRegistryContext(retainIfStable(observed,stable),registryBefore,registryAfter);
        } catch(NoSuchFileException missing) {
            return result(false,LocalStatus.NOT_APPLICABLE,"缺少该协议的审核组件或根配置","unknown","unknown","unknown");
        } catch(LicenseRecoverReadOnlyDotNetCatalog.Changed e){return result(true,LocalStatus.INPUT_CHANGED,"协议组件读取期间变化","unknown","unknown","unknown");}
        catch(IOException|SecurityException e) {
            return new Result(LicenseRecoverLicenseState.scanError("只读 legacy/VM 组件检查失败: "+e.getClass().getSimpleName()));
        } catch(Exception e) {
            return result(true,LocalStatus.LEGACY_FORMAT_UNSUPPORTED,"根配置或局部协议结构无法核实；未判全套授权无效","unknown","unknown","unknown");
        }
    }
    /** A changed evidence set revokes every local conclusion, including a prior mismatch. */
    static Result retainIfStable(Result observed,boolean stable) {
        if(!stable)return result(true,LocalStatus.INPUT_CHANGED,"检查期间代码或根配置变化；已撤销局部结论","unknown","unknown","unknown");
        return observed;
    }
    /** Optional context never upgrades a local observation into an accepted/invalid license. */
    static Result withRegistryContext(Result observed,LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot before,
            LicenseRecoverReadOnlyDotNetRegistrySource.Snapshot after) {
        if(!observed.applies||observed.state.isScanError())return observed;
        String context=before!=null&&before.same(after)?after.observation()
                :"组合注册来源未完成一致的双重读取，未用其内容推断站点授权";
        return result(observed.applies,observed.localStatus,observed.state.evidence+"；"+context,
                observed.dispatch,observed.upperLocal,observed.lowerLocal);
    }
    private static LicenseRecoverReadOnlyDotNetCatalog.ExtendedBinding build(File root) throws Exception {
        return LicenseRecoverReadOnlyDotNetCatalog.assess(root,false).extended;
    }

    /** Fixtures exercise the actual local predicates without adding synthetic trusted hashes. */
    static Result diagnose(byte[] xml,boolean legacy,LicenseRecoverReadOnlyMachineIdentity.Snapshot identity,LocalDate today) {
        String dispatch="unknown";
        try {
            Document cfg=xml(xml);String mode=element(cfg,"reg","regType");
            dispatch=dispatch(mode,legacy);
            String encrypted=element(cfg,"reg","regName");
            if(encrypted.trim().isEmpty())return result(true,LocalStatus.NO_LOCAL_RECORD,
                    "根 config.xml 未包含本地授权记录；大写本地检查返回失败；网络/硬件狗及小写路径未获全路径结论",
                    dispatch,"NO_LOCAL_RECORD",legacy?"NO_LOCAL_RECORD":"VM_NOT_EVALUATED");
            if(!legacy) {
                try {
                    String plain=decrypt(encrypted,"*ITMCYX0302OK*");
                    if(plain.length()>=12&&ascii(plain)&&!plain.substring(6,plain.length()-6).trim().startsWith("{"))
                        return result(true,LocalStatus.UPPER_JSON_FORMAT_MISMATCH_LOWER_VM_PENDING,
                                "当前本地记录不属于大写注册器格式；可能由另一注册组件处理，其判据尚未完成等价验证。没有据此判为未激活",
                                dispatch,"JSON_REGEDITINFO_REQUIRED","VM_NOT_EVALUATED");
                }catch(GeneralSecurityException|IllegalArgumentException failure) {
                    return result(true,LocalStatus.LOWER_VM_PENDING,
                            "当前本地载荷未通过已审格式；另一注册组件、联网、硬件狗和站点缓存仍待核，不能推出未激活",
                            dispatch,"LOCAL_PAYLOAD_UNMATCHED","VM_NOT_EVALUATED");
                }
                return result(true,LocalStatus.LOWER_VM_PENDING,
                        "现有记录由尚未完成等价审计的注册组件处理；仍需核对其他授权来源，当前没有完整接受或拒绝证明",dispatch,"LOCAL_NOT_FULLY_EVALUATED","VM_NOT_EVALUATED");
            }
            // Reviewed lowercase YX0102 has a fixed-position legacy record, not upper JSON.
            String plain;
            try{plain=decrypt(encrypted.trim(),"*MarketOK*");}
            catch(GeneralSecurityException|IllegalArgumentException failure){
                Result modern=modernCandidate(encrypted,dispatch);if(modern!=null)return modern;
                return result(true,LocalStatus.LEGACY_FORMAT_UNSUPPORTED,
                    "小写旧协议载荷无法按审核格式解码；本地失败不等于网络/硬件狗或网站父产品路径全部失败",
                    dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","LOCAL_PAYLOAD_UNMATCHED");}
            if(!ascii(plain)||plain.length()<40||!plain.substring(30,40).matches("\\d{4}-\\d{2}-\\d{2}")) {
                Result modern=modernCandidate(encrypted,dispatch);if(modern!=null)return modern;
                return result(true,LocalStatus.LEGACY_FORMAT_UNSUPPORTED,"小写记录未匹配审核的固定位置结构；未判断全部授权方式",dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","UNSUPPORTED_RECORD");
            }
            if(identity==null||!identity.complete)return result(true,LocalStatus.IDENTITY_UNAVAILABLE,
                    "旧格式本地记录存在，但独立本机身份不可用；网络/硬件狗尚未核验",dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","IDENTITY_UNAVAILABLE");
            String current=legacyMachine(identity);
            if(current==null)return result(true,LocalStatus.IDENTITY_UNAVAILABLE,"本机身份规范不属于本次审核范围",dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","IDENTITY_UNAVAILABLE");
            if(!plain.substring(6,22).equals(current))return result(true,LocalStatus.LEGACY_MACHINE_MISMATCH,
                    "当前本地记录的机器字段与本次扫描进程取得的标识不匹配；这不是站点运行身份或缓存的观测，其他注册组件、组合来源、硬件狗及联网仍可能接受",
                    dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","MACHINE_MISMATCH");
            LocalDate end;
            try{end=LocalDate.parse(plain.substring(30,40));}
            catch(DateTimeParseException e){return result(true,LocalStatus.LEGACY_FORMAT_UNSUPPORTED,"旧格式日期无法核实；其他路径未判断",dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","DATE_UNSUPPORTED");}
            // Lowercase CheckLocalReg rejects equality too; unlike upper JSON's < comparison.
            if(!end.isAfter(today))return result(true,LocalStatus.LEGACY_EXPIRED,
                    "小写旧协议的本地到期条件未通过（到期日当天也拒绝）；网络/硬件狗及网站兼容路径未判断",
                    dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","LOCAL_DATE_REJECTED");
            return result(true,LocalStatus.LEGACY_LOCAL_CONDITIONS_MATCHED,
                    "小写本地机器与日期条件匹配；后续 RegInfo 构造和网站模式条件仍需完整证明，不能据此提升为授权检查通过",
                    dispatch,"JSON_PROTOCOL_NOT_ESTABLISHED","MACHINE_AND_DATE_MATCHED_WEBSITE_PENDING");
        } catch(Exception e) {
            return result(true,LocalStatus.LEGACY_FORMAT_UNSUPPORTED,"根配置或局部记录结构损坏/不适用；没有得出完整授权拒绝结论",dispatch,"unknown","unknown");
        }
    }
    private static Result result(boolean applies,LocalStatus status,String reason,String dispatch,String upper,String lower) {
        return new Result(applies,status,reason,dispatch,upper,lower);
    }
    /** Format routing only; even malformed JSON with this outer shape must never become accepted. */
    private static Result modernCandidate(String encrypted,String dispatch) {
        try {
            String clear=decrypt(encrypted.trim(),"*ITMCYS01OK*");
            if(!ascii(clear)||clear.length()<14)return null;
            String wrapped=clear.substring(6,clear.length()-6).trim();
            if(!wrapped.startsWith("{")||!wrapped.endsWith("}"))return null;
            return result(true,LocalStatus.MODERN_RECORD_CANDIDATE,
                    "当前载荷可按已审 YS01 现代编码解密并具有 JSON 外形；不能当作小写旧格式损坏。内容、机器、日期和模式必须由独立现代谓词另行验证，格式候选不证明授权通过",
                    dispatch,"MODERN_SHAPE_REQUIRES_VALIDATION","LEGACY_FORMAT_NOT_APPLICABLE");
        }catch(GeneralSecurityException|IllegalArgumentException failure){return null;}
    }
    private static LicenseRecoverLicenseState.LocalCondition condition(LocalStatus status) {
        switch(status) {
            case NO_LOCAL_RECORD:return LicenseRecoverLicenseState.LocalCondition.NO_RECORD;
            case LEGACY_MACHINE_MISMATCH:return LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH;
            case LEGACY_EXPIRED:return LicenseRecoverLicenseState.LocalCondition.EXPIRED;
            case LEGACY_LOCAL_CONDITIONS_MATCHED:return LicenseRecoverLicenseState.LocalCondition.SATISFIED;
            case LEGACY_FORMAT_UNSUPPORTED:return LicenseRecoverLicenseState.LocalCondition.MALFORMED;
            case IDENTITY_UNAVAILABLE:return LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE;
            case UPPER_JSON_FORMAT_MISMATCH_LOWER_VM_PENDING:
            case MODERN_RECORD_CANDIDATE:
            case LOWER_VM_PENDING:return LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED;
            default:return LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED;
        }
    }
    private static String dispatch(String mode,boolean legacy) {
        String lower=legacy?"lower:local→dog→network":"lower:VM未审计";
        if("1".equals(mode))return "upper:local→network→HASP; "+lower;
        if("2".equals(mode))return "upper:HASP→local→network; "+(legacy?"lower:dog→local→network":lower);
        if("3".equals(mode))return "upper:network→local→HASP; "+(legacy?"lower:network→local→dog":lower);
        return legacy?"upper:未审核分派; "+lower:"upper:local→HASP→network; "+lower;
    }
    private static String legacyMachine(LicenseRecoverReadOnlyMachineIdentity.Snapshot s) {
        if(s.hostname==null||!ascii(s.hostname))return null;
        String cpu="";
        for(String value:s.cpuIds){if(value==null){cpu="unknow";break;}if(!ascii(value))return null;cpu=value;}
        String disk="0";
        for(LicenseRecoverReadOnlyMachineIdentity.Disk d:s.disks) {
            if(d==null||d.signature==null){disk="unknow";break;}
            if(!ascii(d.signature))return null;
            try{BigInteger n=new BigInteger(d.signature.trim());if(n.signum()<0||n.bitLength()>32)throw new NumberFormatException();disk=n.toString();if(n.signum()>0)break;}
            catch(NumberFormatException e){disk="unknow";break;}
        }
        String board="unknow";
        if(!s.boards.isEmpty()&&s.boards.get(0)!=null){if(!ascii(s.boards.get(0)))return null;board=s.boards.get(0).trim();}
        if("unknow".equalsIgnoreCase(cpu)&&"unknow".equalsIgnoreCase(disk)){String h=pad(s.hostname,17);return h.substring(h.length()-17,h.length()-1);}
        cpu=pad(cpu,9);disk=pad(disk,9);board="unknow".equals(board)||"None".equals(board)?cpu:pad(board,9);
        return board.substring(board.length()-9,board.length()-1)+disk.substring(disk.length()-9,disk.length()-1);
    }
    private static String pad(String s,int n){StringBuilder b=new StringBuilder(s);while(b.length()<n)b.insert(0,'0');return b.toString();}
    private static boolean ascii(String s){return s!=null&&s.matches("[\\x20-\\x7e]{0,8192}");}
    private static String decrypt(String text,String password)throws GeneralSecurityException {
        if(text==null||text.length()>MAX_CONFIG||(text.length()&1)!=0||!text.matches("[0-9A-Fa-f]+"))throw new IllegalArgumentException();
        byte[] encrypted=new byte[text.length()/2];for(int i=0;i<encrypted.length;i++)encrypted[i]=(byte)Integer.parseInt(text.substring(2*i,2*i+2),16);
        byte[] key=hex(MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8))).toUpperCase(Locale.ROOT).substring(0,8).getBytes(StandardCharsets.US_ASCII);
        Cipher cipher=Cipher.getInstance("DES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"DES"),new IvParameterSpec(key));
        return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
    }
    private static Document xml(byte[] bytes)throws Exception {
        if(bytes==null||bytes.length>MAX_CONFIG)throw new IllegalArgumentException();
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder b=f.newDocumentBuilder();b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());return b.parse(new ByteArrayInputStream(bytes));
    }
    private static String element(Document d,String section,String name) {
        Element root=d.getDocumentElement();if(root==null||!("ROOT".equals(root.getTagName())||"root".equals(root.getTagName())))throw new IllegalArgumentException();
        String found=null;
        for(Node s=root.getFirstChild();s!=null;s=s.getNextSibling())if(!space(s)) {
            if(s.getNodeType()!=Node.ELEMENT_NODE)throw new IllegalArgumentException();if(!section.equals(s.getNodeName()))continue;
            for(Node n=s.getFirstChild();n!=null;n=n.getNextSibling())if(!space(n)) {
                if(n.getNodeType()!=Node.ELEMENT_NODE)throw new IllegalArgumentException();
                if(name.equals(n.getNodeName())){if(found!=null)throw new IllegalArgumentException();found=n.getTextContent();}
            }
        }
        return found==null?"":found;
    }
    private static boolean space(Node n){return n.getNodeType()==Node.TEXT_NODE&&n.getNodeValue().trim().isEmpty();}
    private static byte[] read(Path path,int cap)throws IOException {
        Path p=path.toAbsolutePath().normalize(),cursor=p.getRoot();
        for(Path part:p){cursor=cursor.resolve(part);if(Files.isSymbolicLink(cursor)||Files.readAttributes(cursor,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther())throw new IOException("linked-path");}
        long length=Files.size(p);if(length>cap)throw new IOException("file-limit");
        try(InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);ByteArrayOutputStream out=new ByteArrayOutputStream((int)Math.min(length,65536))) {
            byte[] b=new byte[65536];int n,total=0;while((n=in.read(b))!=-1){if(n>cap-total)throw new IOException("file-limit");total+=n;out.write(b,0,n);}return out.toByteArray();
        }
    }
    private static String hash(byte[] bytes)throws GeneralSecurityException{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
}
