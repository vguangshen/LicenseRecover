import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/**
 * Independent positive-only transcription of reviewed current .NET acceptance predicates.
 * No assembly is loaded or executed. Observed build hashes are semantic audit bindings,
 * never original/Patch provenance. A failed local condition does not establish invalidity.
 */
public final class LicenseRecoverReadOnlyDotNetVerifier {
    private static final int MAX_CONFIG = 4 * 1024 * 1024;
    private LicenseRecoverReadOnlyDotNetVerifier() { }

    public static LicenseRecoverLicenseState verify(File appRoot) {
        try {
            LicenseRecoverReadOnlyDotNetCatalog.UpperBinding build = currentBuild(appRoot);
            if(build==null)return unknown("当前网站/注册程序集组合没有完成正向接受谓词审计");
            return verifyBuild(appRoot, build, LicenseRecoverReadOnlyMachineIdentity.current());
        } catch (NoSuchFileException missing) { return unknown("缺少当前配置或审核组件"); }
        catch (LicenseRecoverReadOnlyDotNetCatalog.Changed e) { return unknown("协议组件读取期间变化"); }
        catch (IOException | SecurityException e) { return LicenseRecoverLicenseState.scanError("独立 .NET 谓词读取失败: "+e.getClass().getSimpleName()); }
        catch (Exception e) { return unknown("独立本机身份或接受条件暂不能确认"); }
    }

    /** Shared identity snapshot supplied by the batch scanner; no machine values are emitted. */
    static LicenseRecoverLicenseState verify(File appRoot, LicenseRecoverReadOnlyMachineIdentity.Snapshot identity) {
        try {
            LicenseRecoverReadOnlyDotNetCatalog.UpperBinding build=currentBuild(appRoot);
            return build==null?unknown("当前网站/注册程序集组合没有完成正向接受谓词审计")
                    : verifyBuild(appRoot,build,identity);
        } catch (NoSuchFileException missing) { return unknown("缺少当前配置或审核组件"); }
        catch (LicenseRecoverReadOnlyDotNetCatalog.Changed e) { return unknown("协议组件读取期间变化"); }
        catch (IOException | SecurityException e) { return LicenseRecoverLicenseState.scanError("独立 .NET 谓词读取失败: "+e.getClass().getSimpleName()); }
        catch (Exception e) { return unknown("本地正向接受条件未获完整证明；其他授权方式未判断"); }
    }

    private static LicenseRecoverReadOnlyDotNetCatalog.UpperBinding currentBuild(File root) throws Exception {
        return LicenseRecoverReadOnlyDotNetCatalog.assess(root,false).upper;
    }
    /** Unified .NET pipeline has already identified the audited protocol binding. */
    static LicenseRecoverLicenseState verifyMatched(File root,LicenseRecoverReadOnlyDotNetCatalog.UpperBinding binding) {
        if(binding==null)return unknown("当前入口没有可复用的已审核现代协议证据");
        try{return verifyBuild(root,binding,LicenseRecoverReadOnlyMachineIdentity.current());}
        catch(NoSuchFileException e){return unknown("缺少当前配置或审核组件");}
        catch(LicenseRecoverReadOnlyDotNetCatalog.Changed e){return unknown("协议组件读取期间变化");}
        catch(IOException|SecurityException e){return LicenseRecoverLicenseState.scanError("现代协议读取失败: "+e.getClass().getSimpleName());}
        catch(Exception e){return unknown("当前本地接受条件尚不能确认，其他来源未被否定");}
    }
    private static LicenseRecoverLicenseState verifyBuild(File root,LicenseRecoverReadOnlyDotNetCatalog.UpperBinding build,
            LicenseRecoverReadOnlyMachineIdentity.Snapshot identity) throws Exception {
        Path config=root.toPath().resolve("config.xml");
        byte[] before=read(config,MAX_CONFIG);
        LicenseRecoverReadOnlyWmicCpuIdentity.Identity cpu=null;
        if(!element(parseXml(before),"reg","WebSerSoftNo").isEmpty())cpu=LicenseRecoverReadOnlyWmicCpuIdentity.current();
        LicenseRecoverLicenseState state=predicate(before,build.product,identity,Instant.now(),ZoneId.systemDefault(),cpu);
        boolean stable=Arrays.equals(before,read(config,MAX_CONFIG))&&currentBuild(root)==build;
        state=retainIfStable(state,stable);
        if(!stable)return state;
        state=predicate(before,build.product,identity,Instant.now(),ZoneId.systemDefault(),cpu);
        if(state.acceptance!=LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE)return state;
        return applyAuditScope(LicenseRecoverLicenseState.authorizationAcceptance(state.evidence
                +"；site-root/config.xml SHA-256="+sha(before)
                +"；website SHA-256="+build.web+"；registration SHA-256="+build.registration,
                LicenseRecoverLicenseState.LocalCondition.SATISFIED),build);
    }

    /** Sharing the record predicate does not close an opaque website/initializer path. */
    static LicenseRecoverLicenseState applyAuditScope(LicenseRecoverLicenseState observed,
            LicenseRecoverReadOnlyDotNetCatalog.UpperBinding binding) {
        if(binding.positiveEntryAudited||observed.acceptance!=LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE)return observed;
        return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.SATISFIED,
                "大写现代协议的本地产品、本机、日期、无限计次及当前模式条件满足；完整网站入口仍待核："
                +binding.auditGap+"；没有据此确认原生激活、Patch 或整体入口通过；未执行厂商程序集");
    }

    /** All current-state observations, not just positive acceptance, require stable inputs. */
    static LicenseRecoverLicenseState retainIfStable(LicenseRecoverLicenseState observed,boolean stable) {
        return stable?observed:unknown("只读核验期间配置或代码发生变化；已撤销局部及入口结论");
    }

    /** Synthetic fixtures can exercise the real predicate without adding trusted production builds. */
    static LicenseRecoverLicenseState predicate(byte[] xml,String product,
            LicenseRecoverReadOnlyMachineIdentity.Snapshot identity,Instant now,ZoneId zone) {
        return predicate(xml,product,identity,now,zone,null);
    }
    static LicenseRecoverLicenseState predicate(byte[] xml,String product,
            LicenseRecoverReadOnlyMachineIdentity.Snapshot identity,Instant now,ZoneId zone,
            LicenseRecoverReadOnlyWmicCpuIdentity.Identity cpu) {
        try {
            Document config=parseXml(xml);
            // Record facts are independent of dispatch. Only the audited local-first
            // branch may promote them to entry acceptance; prior network/HASP stays unknown.
            boolean localFirst="1".equals(element(config,"reg","regType"));
            String version=element(config,"SystemSoft","SoftVersionID");
            if(!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))return unknown("网站当前产品/模式标识不明确");
            String encrypted=element(config,"reg","regName");
            if(encrypted.isEmpty())return local(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,"缺少可验证的本地授权载荷；其他授权方式未判断");
            String decrypted=decrypt(encrypted,"*ITMC"+product+"OK*");
            if(decrypted.length()<12||!decrypted.matches("[\\x20-\\x7e\\r\\n\\t]*"))
                return unknown("本地载荷未匹配审核的 ASCII 结构");
            Map<String,Object> info=new Json(decrypted.substring(6,decrypted.length()-6)).object();
            String beanFailure=canonicalBeanFailure(info,zone);
            if(beanFailure!=null)return local(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,beanFailure);
            // ProName is an additional conservative binding, beyond CheckLocalReg's machine check.
            if(!product.equals(info.get("ProName")))return local(LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH,"本地载荷未匹配当前审核产品身份；其他授权方式未判断");
            if(identity==null||!identity.complete)return local(LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,"独立本机身份快照不完整");
            if(!asciiIdentity(identity))return local(LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,"本机身份包含未审核的非 ASCII 规范");
            String machine=machine(config,identity,cpu);
            if(machine==null||machine.isEmpty())return local(LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,
                    "本机覆盖号或身份规则尚未形成独立证明；未判机器不匹配"
                    +(cpu!=null&&!cpu.complete?"；"+cpu.reason:""));
            if(!(info.get("RegID") instanceof String)||!((String)info.get("RegID")).matches("[\\x20-\\x7e]{1,256}"))
                return local(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"授权载荷机器字段类型尚未匹配审核结构；未判机器不匹配");
            LocalDate end=endDate(info.get("EndDate"),zone);
            if(end.isBefore(now.atZone(zone).toLocalDate()))return local(LicenseRecoverLicenseState.LocalCondition.EXPIRED,"本地正向日期条件未满足；其他授权方式未判断");
            // Finite counters mutate during target verification. They require a separate audit.
            if(!Long.valueOf(-1).equals(info.get("TotalTimes")))return local(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"有限计次或缺失计次载荷尚未完成正向审计");
            Object reg=info.get("RegStr");
            if(!(reg instanceof String)||!((String)reg).matches("[A-Za-z0-9,._-]{1,4096}"))
                return local(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"授权产品字段格式尚未匹配审核结构；未判当前产品缺失");
            if(!((String)reg).toLowerCase(Locale.ROOT).contains(version.toLowerCase(Locale.ROOT)))
                return local(LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH,"本地载荷未覆盖网站当前模式；兼容/父版本路径未判断");
            if(!machine.equals(info.get("RegID")))
                return local(LicenseRecoverLicenseState.LocalCondition.SATISFIED_EXCEPT_MACHINE,
                        "本地载荷的产品、期限、无限计次及当前模式匹配；本次扫描的机器标识未匹配，整体入口与其他授权方式仍待核");
            if(!localFirst)return local(LicenseRecoverLicenseState.LocalCondition.SATISFIED,
                    "现代本地载荷的产品、本机、日期、无限计次及当前模式条件匹配；当前分派非已审核的本地优先路径，先行联网/硬件狗及整体入口仍待核；未执行厂商代码");
            return LicenseRecoverLicenseState.authorizationAcceptance(
                    "审核 .NET 当前构建的独立本地正向谓词通过：产品绑定、本机绑定、日期、无限计次及网站当前模式均匹配；"
                    +"未执行厂商程序集；"+(!element(config,"reg","WebSerSoftNo").isEmpty()?"已按审核的只读 WMIC 覆盖号/回退规则核验；":"")
                    +"激活与 Patch 来源未区分；见 docs/DOTNET_READ_ONLY_ACCEPTANCE.md",
                    LicenseRecoverLicenseState.LocalCondition.SATISFIED);
        } catch(Exception e) {return unknown("载荷/身份/日期未匹配审核的正向接受条件；其他授权方式未判断");}
    }

    private static String machine(Document config,LicenseRecoverReadOnlyMachineIdentity.Snapshot s,
            LicenseRecoverReadOnlyWmicCpuIdentity.Identity wmic) throws Exception {
        if(s==null)return null;
        String soft=element(config,"reg","WebSerSoftNo");
        if(!soft.isEmpty()) {
            // A WMI CPU row is not a substitute for the target's WMIC text rule.
            // Only the separately audited system reader may establish its token.
            if(wmic==null||!wmic.complete||wmic.value==null)return null;
            try {
                String decoded=decrypt(soft,wmic.value);
                if(!decoded.trim().isEmpty())return ascii(decoded)?decoded:null;
            }catch(java.security.GeneralSecurityException|IllegalArgumentException e) {
                // Native getRegNo catches failed decoding, then uses the WMI rule.
            }
        }
        return LicenseRecoverReadOnlyMachineIdentity.defaultDotNetId(s);
    }
    private static boolean asciiIdentity(LicenseRecoverReadOnlyMachineIdentity.Snapshot s) {
        if(s.hostname==null||!ascii(s.hostname)||s.cpuIds==null||s.disks==null||s.boards==null)return false;
        for(String cpu:s.cpuIds)if(cpu!=null&&!ascii(cpu))return false;
        for(String board:s.boards)if(board!=null&&!ascii(board))return false;
        for(LicenseRecoverReadOnlyMachineIdentity.Disk disk:s.disks)
            if(disk!=null&&((disk.signature!=null&&!ascii(disk.signature))||(disk.serial!=null&&!ascii(disk.serial))))return false;
        return true;
    }
    private static boolean ascii(String value) {return value.matches("[\\x20-\\x7e]{0,8192}");}
    private static String decrypt(String value,String password) throws Exception {
        if(value.length()>MAX_CONFIG||(value.length()&1)!=0||!value.matches("[0-9A-Fa-f]+"))throw new IllegalArgumentException();
        byte[] cipher=new byte[value.length()/2];
        for(int i=0;i<cipher.length;i++)cipher[i]=(byte)Integer.parseInt(value.substring(2*i,2*i+2),16);
        byte[] md5=MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8));
        byte[] key=hex(md5).toUpperCase(Locale.ROOT).substring(0,8).getBytes(StandardCharsets.US_ASCII);
        Cipher des=Cipher.getInstance("DES/CBC/PKCS5Padding");
        des.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"DES"),new IvParameterSpec(key));
        // Relevant field names, machine IDs and product tokens are ASCII. Reject
        // non-ASCII relevant values below; unrelated display fields are not emitted.
        return new String(des.doFinal(cipher),StandardCharsets.UTF_8);
    }
    /**
     * JSON.NET constructs the entire reviewed bean before CheckLocalReg runs.
     * Unused fields can fail conversion even on the unlimited-counter branch.
     * BeginDate/beginDate and EndDate/endDate are distinct public properties.
     * Nonzero lowercase dates override a getter via an unaudited legacy local epoch.
     */
    private static String canonicalBeanFailure(Map<String,Object> info,ZoneId zone) {
        for(Map.Entry<String,Object> entry:info.entrySet()) {
            Object value=entry.getValue();
            switch(entry.getKey()) {
                case "ProName":case "RegID":case "UserID":case "RegStr":
                    if(value!=null&&!(value instanceof String))return "载荷字符串属性类型未匹配已审核的完整对象结构";
                    break;
                case "ClassNum":case "TotalTimes":case "UserTimes":case "MaxCon":case "CountDay":
                    if(!(value instanceof Long)||(Long)value<Integer.MIN_VALUE||(Long)value>Integer.MAX_VALUE)
                        return "载荷整数属性类型或范围未匹配已审核的完整对象结构";
                    break;
                case "Net":
                    if(!(value instanceof Boolean))return "载荷布尔属性类型未匹配已审核的完整对象结构";
                    break;
                case "BeginDate":case "EndDate":
                    try{endDate(value,zone);}catch(RuntimeException e){return "载荷日期属性未匹配已审核的完整对象解析范围";}
                    break;
                case "beginDate":case "endDate":
                    if(!(value instanceof Long))return "载荷历史日期属性类型未匹配已审核的完整对象结构";
                    if((Long)value!=0)return "非零历史日期字段会覆盖日期 getter；其本地历元和历史时区换算尚未完成等价审核，未沿用大写日期判定";
                    break;
                default:
                    return "载荷包含未审核字段、大小写别名或类型扩展；未采用部分字段的通过结果";
            }
        }
        return null;
    }
    private static LocalDate endDate(Object value,ZoneId zone) {
        if(!(value instanceof String))throw new IllegalArgumentException();
        String s=(String)value;
        Matcher m=Pattern.compile("^/Date\\((-?\\d+)([+-]\\d{4})?\\)/$").matcher(s);
        if(m.matches()) {
            long millis=Long.parseLong(m.group(1));
            if(millis < -62135596800000L || millis > 253402300799999L)throw new IllegalArgumentException();
            Instant i=Instant.ofEpochMilli(millis);
            if(m.group(2)!=null) {
                String offset=m.group(2);int hours=Integer.parseInt(offset.substring(1,3)),minutes=Integer.parseInt(offset.substring(3,5));
                if(hours>14||minutes>59||hours==14&&minutes!=0)throw new IllegalArgumentException();
            }
            // JSON.NET DateTime: an offset marker requests conversion to local time;
            // without it the parsed value remains UTC (Date comparison does not normalize Kind).
            LocalDate date=i.atZone(m.group(2)==null?ZoneOffset.UTC:zone).toLocalDate();
            if(date.getYear()<1||date.getYear()>9999)throw new IllegalArgumentException();
            return date;
        }
        throw new IllegalArgumentException("unsupported-date-format");
    }
    private static Document parseXml(byte[] xml) throws Exception {
        if(xml==null||xml.length>MAX_CONFIG)throw new IllegalArgumentException();
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder=f.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        return builder.parse(new ByteArrayInputStream(xml));
    }
    private static String element(Document doc,String section,String name) {
        Element root=doc.getDocumentElement();
        if(root==null||!("ROOT".equals(root.getTagName())||"root".equals(root.getTagName())))return "";
        String found=null;
        for(Node s=root.getFirstChild();s!=null;s=s.getNextSibling()) {
            if(whitespace(s))continue;
            if(s.getNodeType()!=Node.ELEMENT_NODE)throw new IllegalArgumentException();
            if(!section.equals(s.getNodeName()))continue;
            for(Node n=s.getFirstChild();n!=null;n=n.getNextSibling()) {
                if(whitespace(n))continue;
                if(n.getNodeType()!=Node.ELEMENT_NODE)throw new IllegalArgumentException();
                if(name.equals(n.getNodeName())) {
                    if(found!=null)throw new IllegalArgumentException();found=n.getTextContent();
                }
            }
        }
        return found==null?"":found;
    }
    private static boolean whitespace(Node n) {return n.getNodeType()==Node.TEXT_NODE&&n.getNodeValue().trim().isEmpty();}
    private static byte[] read(Path path,int limit) throws IOException {
        Path p=path.toAbsolutePath().normalize(),cursor=p.getRoot();
        for(Path part:p){cursor=cursor.resolve(part);if(Files.isSymbolicLink(cursor))throw new IOException("linked-path");}
        long length=Files.size(p);if(length>limit)throw new IOException("file-limit");
        try(java.io.InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream((int)Math.min(length,65536))) {
            byte[] b=new byte[65536];int n,total=0;
            while((n=in.read(b))!=-1){if(n>limit-total)throw new IOException("file-limit");total+=n;out.write(b,0,n);}
            return out.toByteArray();
        }
    }
    private static String sha(byte[] bytes) throws Exception {return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static LicenseRecoverLicenseState unknown(String reason){return LicenseRecoverLicenseState.unverified(reason);}
    private static LicenseRecoverLicenseState local(LicenseRecoverLicenseState.LocalCondition condition,String reason){
        return LicenseRecoverLicenseState.localObservation(condition,reason);
    }

    /** Strict flat license JSON: duplicate keys and nested/unsupported structures fail closed. */
    private static final class Json {
        final String s;int p;
        Json(String s){this.s=s;if(s.length()>MAX_CONFIG)throw new IllegalArgumentException();}
        Map<String,Object> object() {
            Map<String,Object> out=new LinkedHashMap<String,Object>();expect('{');space();
            if(take('}')){finish();return out;}
            do {
                String key=string();expect(':');Object value=value();
                if(out.containsKey(key))throw new IllegalArgumentException();out.put(key,value);
                space();if(take('}')){finish();return out;}expect(',');
            } while(out.size()<256);
            throw new IllegalArgumentException();
        }
        Object value(){space();if(p>=s.length())throw new IllegalArgumentException();
            if(s.charAt(p)=='"')return string();
            for(String literal:new String[]{"true","false","null"})if(s.startsWith(literal,p)){p+=literal.length();return "null".equals(literal)?null:Boolean.valueOf(literal);}
            int start=p;if(s.charAt(p)=='-')p++;while(p<s.length()&&Character.isDigit(s.charAt(p)))p++;
            String number=s.substring(start,p);if(!number.matches("-?(?:0|[1-9][0-9]*)"))throw new IllegalArgumentException();return Long.valueOf(number);
        }
        String string(){space();if(!take('"'))throw new IllegalArgumentException();StringBuilder b=new StringBuilder();
            while(p<s.length()){char c=s.charAt(p++);if(c=='"')return b.toString();if(c<32)throw new IllegalArgumentException();
                if(c=='\\'){if(p>=s.length())throw new IllegalArgumentException();c=s.charAt(p++);
                    switch(c){case '"':case '\\':case '/':break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;
                    case 'u':if(p+4>s.length())throw new IllegalArgumentException();c=(char)Integer.parseInt(s.substring(p,p+4),16);p+=4;break;default:throw new IllegalArgumentException();}}
                b.append(c);
            }throw new IllegalArgumentException();
        }
        void space(){while(p<s.length()&&" \r\n\t".indexOf(s.charAt(p))>=0)p++;}
        boolean take(char c){if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}
        void expect(char c){space();if(!take(c))throw new IllegalArgumentException();}
        void finish(){space();if(p!=s.length())throw new IllegalArgumentException();}
    }
}
