import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
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

    private static final class Build {
        final String web, registration, product, cryptoFile, cryptoHash, jsonHash, logHash;
        Build(String web, String registration, String product) {
            this.web=web; this.registration=registration; this.product=product;
            boolean ds="itmcIEC".equals(product);
            cryptoFile=ds?"ItmcBtoB.DBUtility.dll":"ITMC.DBUtility.dll";
            cryptoHash=ds?(web.startsWith("16b13b")?
                    "9430693eea94e18077fb1fa0e187a0d4cb6c4bf472eca4cdd075366e9b8eb92c":
                    "7d880b03364e4c31c39c47a053144345b5b02804ac7a0e80a87ebf24096fd999"):
                    "f8c2ff0b28b03bfc33ce61e32f63d8b179c0c213a4ccab8ba748aa4dd73d5c22";
            jsonHash=ds?"78bedd9fce877a71a8d8ff9a813662d8248361e46705c4ef7afc61d440ff2eeb":
                    "9eca2c5ed187b5e1319c359aabf8382992131efe1854b899c23a93fe227496e3";
            logHash=ds?"af5610c515d2244db98c662636264c8177e89b1afe407f88fd18a41d66f6e7e2":
                    "124b4cd7d62656698d43d5adc3645eab88199717bb99497549d2c74a26c68e1d";
        }
    }
    // Exact current builds whose native local and website positive path were audited.
    private static final Build[] BUILDS = {
        new Build("16b13b547893b160ef928fd6f06568770da1fdd2326a1dfdd4bc5c54e047562c",
                "735a7f868825111cd437cb5093eaa79f0a977330a79121fb2d18885290da743f", "itmcIEC"),
        new Build("21464d180e30ed1f10c0c6b20e56381191ecd70aab06595f07a27dc4d559961a",
                "25b6b2939534b8266d843f7bd7b942d9ee87fd48296c8ed05828ffc3de76c0d8", "itmcIEC"),
        new Build("57fa95aa72a15bfd569834374860f29dbef22a48294f7010e94846023a94ce35",
                "f5a2c38dee0f694c33fdac2c5570c2431b955c594e244e87596f120c0eb07f99", "YX0303")
    };

    public static LicenseRecoverLicenseState verify(File appRoot) {
        try {
            Build build = currentBuild(appRoot);
            if(build==null)return unknown("当前网站/注册程序集组合没有完成正向接受谓词审计");
            return verifyBuild(appRoot, build, LicenseRecoverReadOnlyMachineIdentity.current());
        } catch (NoSuchFileException missing) { return unknown("缺少当前配置或审核组件"); }
        catch (IOException | SecurityException e) { return LicenseRecoverLicenseState.scanError("独立 .NET 谓词读取失败: "+e.getClass().getSimpleName()); }
        catch (Exception e) { return unknown("独立本机身份或接受条件暂不能确认"); }
    }

    /** Shared identity snapshot supplied by the batch scanner; no machine values are emitted. */
    static LicenseRecoverLicenseState verify(File appRoot, LicenseRecoverReadOnlyMachineIdentity.Snapshot identity) {
        try {
            Build build=currentBuild(appRoot);
            return build==null?unknown("当前网站/注册程序集组合没有完成正向接受谓词审计")
                    : verifyBuild(appRoot,build,identity);
        } catch (NoSuchFileException missing) { return unknown("缺少当前配置或审核组件"); }
        catch (IOException | SecurityException e) { return LicenseRecoverLicenseState.scanError("独立 .NET 谓词读取失败: "+e.getClass().getSimpleName()); }
        catch (Exception e) { return unknown("本地正向接受条件未获完整证明；其他授权方式未判断"); }
    }

    private static Build currentBuild(File root) throws Exception {
        if(root==null)return null;
        Path app=root.toPath().toAbsolutePath().normalize();
        String web=sha(read(app.resolve("bin/ITMC.Web.dll"),128*1024*1024));
        String reg=sha(read(app.resolve("bin/ITMC.Regedit.dll"),128*1024*1024));
        for(Build b:BUILDS)if(b.web.equals(web)&&b.registration.equals(reg)) {
            if(!b.cryptoHash.equals(sha(read(app.resolve("bin/"+b.cryptoFile),128*1024*1024)))
                    ||!b.jsonHash.equals(sha(read(app.resolve("bin/Newtonsoft.Json.dll"),128*1024*1024)))
                    ||!b.logHash.equals(sha(read(app.resolve("bin/log4net.dll"),128*1024*1024)))
                    ||!"4091c455a882e3e44c8dc0c4034e6b2536f180ce019695769ef72497a90467ec".equals(
                            sha(read(app.resolve("bin/LiteDB.dll"),128*1024*1024))))return null;
            return b;
        }
        return null;
    }
    private static LicenseRecoverLicenseState verifyBuild(File root,Build build,
            LicenseRecoverReadOnlyMachineIdentity.Snapshot identity) throws Exception {
        Path config=root.toPath().resolve("config.xml");
        byte[] before=read(config,MAX_CONFIG);
        LicenseRecoverLicenseState state=predicate(before,build.product,identity,Instant.now(),ZoneId.systemDefault());
        if(state.acceptance!=LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE)return state;
        if(!Arrays.equals(before,read(config,MAX_CONFIG))||currentBuild(root)!=build)
            return unknown("接受条件检查期间配置或代码发生变化");
        return LicenseRecoverLicenseState.authorizationAcceptance(state.evidence
                +"；site-root/config.xml SHA-256="+sha(before)
                +"；website SHA-256="+build.web+"；registration SHA-256="+build.registration);
    }

    /** Synthetic fixtures can exercise the real predicate without adding trusted production builds. */
    static LicenseRecoverLicenseState predicate(byte[] xml,String product,
            LicenseRecoverReadOnlyMachineIdentity.Snapshot identity,Instant now,ZoneId zone) {
        try {
            if(identity==null||!identity.complete)return unknown("独立本机身份快照不完整");
            if(!asciiIdentity(identity))return unknown("本机身份包含未审核的非 ASCII 规范");
            Document config=parseXml(xml);
            // In the supported cold-start branch, local is first and succeeds before network/HASP.
            if(!"1".equals(element(config,"reg","regType")))return unknown("当前注册分派尚未证明本地优先接受");
            String version=element(config,"SystemSoft","SoftVersionID");
            if(!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))return unknown("网站当前产品/模式标识不明确");
            String encrypted=element(config,"reg","regName");
            if(encrypted.isEmpty())return unknown("缺少可验证的本地授权载荷；其他授权方式未判断");
            String decrypted=decrypt(encrypted,"*ITMC"+product+"OK*");
            if(decrypted.length()<12||!decrypted.matches("[\\x20-\\x7e\\r\\n\\t]*"))
                return unknown("本地载荷未匹配审核的 ASCII 结构");
            Map<String,Object> info=new Json(decrypted.substring(6,decrypted.length()-6)).object();
            for(String critical:new String[]{"ProName","RegID","RegStr","TotalTimes"})
                for(String key:info.keySet())if(key.equalsIgnoreCase(critical)&&!key.equals(critical))
                    return unknown("载荷字段大小写未匹配审核结构");
            for(String key:info.keySet())if(key.equalsIgnoreCase("EndDate")&&!"EndDate".equals(key)
                    &&!("endDate".equals(key)&&info.get(key) instanceof Long))
                return unknown("日期字段大小写未匹配审核结构");
            // ProName is an additional conservative binding, beyond CheckLocalReg's machine check.
            if(!product.equals(info.get("ProName")))return unknown("本地载荷未证明当前审核产品身份");
            String machine=machine(config,identity);
            if(machine==null||machine.isEmpty()||!(info.get("RegID") instanceof String)
                    ||!((String)info.get("RegID")).matches("[\\x20-\\x7e]{1,256}")||!machine.equals(info.get("RegID")))
                return unknown("本地载荷与当前独立本机身份未匹配；其他授权方式未判断");
            LocalDate end=endDate(info.get("EndDate"),zone);
            if(end.isBefore(now.atZone(zone).toLocalDate()))return unknown("本地正向日期条件未满足；其他授权方式未判断");
            // Finite counters mutate during target verification. They require a separate audit.
            if(!Long.valueOf(-1).equals(info.get("TotalTimes")))return unknown("有限计次或缺失计次载荷尚未完成正向审计");
            Object reg=info.get("RegStr");
            if(!(reg instanceof String)||!((String)reg).matches("[A-Za-z0-9,._-]{1,4096}")
                    ||!((String)reg).toLowerCase(Locale.ROOT).contains(version.toLowerCase(Locale.ROOT)))
                return unknown("本地载荷未覆盖网站当前模式；兼容/父版本路径未判断");
            return LicenseRecoverLicenseState.authorizationAcceptance(
                    "审核 .NET 当前构建的独立本地正向谓词通过：产品绑定、本机绑定、日期、无限计次及网站当前模式均匹配；"
                    +"未执行厂商程序集；激活与 Patch 来源未区分；见 docs/DOTNET_READ_ONLY_ACCEPTANCE.md");
        } catch(Exception e) {return unknown("载荷/身份/日期未匹配审核的正向接受条件；其他授权方式未判断");}
    }

    private static String machine(Document config,LicenseRecoverReadOnlyMachineIdentity.Snapshot s) throws Exception {
        if(s==null)return null;
        String soft=element(config,"reg","WebSerSoftNo");
        if(!soft.isEmpty()) {
            // The target first executes cmd/WMIC; independent WMI success does not
            // establish that WMIC exists or that its text parser succeeds. Until
            // that exact branch is separately audited, do not substitute a CPU ID.
            return null;
        }
        String cpu="unknow";
        if(s.cpuIds!=null) {
            cpu="";
            for(String c:s.cpuIds) {if(c==null){cpu="unknow";break;}cpu=c;}
        }
        String disk="0";
        if(s.disks==null)disk="unknow";
        else for(LicenseRecoverReadOnlyMachineIdentity.Disk d:s.disks) {
            if(d==null||d.signature==null){disk="unknow";break;}
            try {
                BigInteger n=new BigInteger(d.signature.trim());
                if(n.signum()<0||n.compareTo(new BigInteger("4294967295"))>0)throw new NumberFormatException();
                disk=n.toString();if(n.signum()>0)break;
            } catch(NumberFormatException e) {disk="unknow";break;}
        }
        String board=s.boards==null||s.boards.isEmpty()||s.boards.get(0)==null?"unknow":s.boards.get(0).trim();
        if("unknow".equalsIgnoreCase(cpu)&&"unknow".equalsIgnoreCase(disk)) {
            if(s.hostname==null)return null;
            String h=pad(s.hostname,17);return h.substring(h.length()-17,h.length()-1);
        }
        cpu=pad(cpu,9);disk=pad(disk,9);
        board="unknow".equals(board)||"None".equals(board)?cpu:pad(board,9);
        return board.substring(board.length()-9,board.length()-1)+disk.substring(disk.length()-9,disk.length()-1);
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
    private static String pad(String s,int length) {StringBuilder b=new StringBuilder(s);while(b.length()<length)b.insert(0,'0');return b.toString();}
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
