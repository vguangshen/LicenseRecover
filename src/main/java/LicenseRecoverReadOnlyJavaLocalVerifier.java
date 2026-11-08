import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Independently implements an audited J1 local acceptance predicate; never runs vendor code. */
public final class LicenseRecoverReadOnlyJavaLocalVerifier {
    private LicenseRecoverReadOnlyJavaLocalVerifier() { }
    private static final String J1 = "ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa";
    private static final String COMMON = "com/common/sys/configuration/SysParamInit.class";
    private static final String LISTENER = "com/itmc/register/service/RegisterListener.class";

    /** No plaintext payload, machine identifier or authorization value escapes this result. */
    static final class LocalCheck {
        final LicenseRecoverLicenseState.LocalCondition condition;
        final String reason;
        final boolean nativeLocalPass, productPass, currentLocalSelected, writesRequired;
        LocalCheck(LicenseRecoverLicenseState.LocalCondition c, String r, boolean nativePass,
                   boolean productPass, boolean selected, boolean writes) {
            condition=c;reason=r;nativeLocalPass=nativePass;this.productPass=productPass;
            currentLocalSelected=selected;writesRequired=writes;
        }
    }
    public static final class Result {
        public final LicenseRecoverLicenseState state;
        public final LicenseRecoverLicenseState.LocalCondition condition;
        public final String reason;
        Result(LicenseRecoverLicenseState s) {state=s;condition=s.localCondition;reason=s.evidence;}
    }

    static final class Profile {
        final String id,anchor,anchorHash,classes,libraries,wiring,family,config,metadata,registrationHash;
        final int classCount,libraryCount;
        final boolean dynamicFamily, productFailureClosed;
        Profile(String id,String anchor,String anchorHash,int cc,String classes,int lc,String libraries,
                String family,String config,String metadata,boolean dynamic,boolean closed,String wiring) {
            this(id,anchor,anchorHash,cc,classes,lc,libraries,family,config,metadata,dynamic,closed,wiring,J1);
        }
        Profile(String id,String anchor,String anchorHash,int cc,String classes,int lc,String libraries,
                String family,String config,String metadata,boolean dynamic,boolean closed,String wiring,String registrationHash) {
            this.id=id;this.anchor=anchor;this.anchorHash=anchorHash;classCount=cc;this.classes=classes;
            libraryCount=lc;this.libraries=libraries;this.family=family;this.config=config;
            this.metadata=metadata;dynamicFamily=dynamic;productFailureClosed=closed;this.wiring=wiring;
            this.registrationHash=registrationHash;
        }
    }
    private static final Profile[] REVIEWED = {
        new Profile("DS1101-J1",COMMON,"e4fdeeaa44350d318694fedd5067b2b0e7edb44d8c1bd23ccacfc9dc4e9cf11a",509,
            "e0b829dd7b1de6d3d6c1939cffe39f08327b538dff62e55708dbb6c05480b8f6",312,
            "3131de4bc44ddafa08637bc399d256bf80475af889a09b9edae65058775f7e14","DS1101","WEB-INF/lib/config.xml","data/config.xml",false,false,"daa30344493ce3416871303949b641ae38d4c54ea49080811bcf328b25049529"),
        new Profile("DS28-J1",COMMON,"2c0c585e0bc5e24768b1453332b5fad663f7a054e2b22a24fe812f60aeb20e89",323,
            "522037e6567fb7b50364d85fb2e8c5b84d8cadb94d68446ed7a51fcc59689305",148,
            "fa8c9c576fa05b1c02a551ccc1f9e4f17dd3bfb6c62b363023d4bc98f9c79a82","DS28","config.xml","data/config.xml",false,true,"6d1efd8d56805d69bf9430062c54ef657139e51c561ebee550c72b61cf28f316"),
        new Profile("DS34-J1",COMMON,"1f3c2cd3dc969b4f495d2b2ad508fff9e96e5eb8d174ad9b30de634f3f5680d3",325,
            "2a3b3c36099191c703975b1b7bfddd5d894b49b00f7c30c8f2d5dc8ad9c31920",146,
            "23ab6cd0e0f6da01af9379c1ed90d40da0dc8b839fd3f2b5819b9e052fa53d91","DS34","WEB-INF/lib/config.xml","data/config.xml",false,false,"4cbd9eccf937e62816e7d53858612383e381622eb333526ce9df6b5f3eef1a89"),
        new Profile("DS35-A-J1",COMMON,"a72cfea9b13d4f2f9b2b04718d84582dd483b1dcd53f18d576241dc14d2638d8",324,
            "6f467714cb52101dbc1daaa0c24a1899a77e26f6eb740b1dae85a2db5456f3c6",304,
            "3c4fe44717a256cd750a10f13b79e5f060f40533af906946740762f1d8d135f4","DS35","WEB-INF/lib/config.xml","data/config.xml",false,false,"4987dbbee6343cb4d1a54aa1b688359b40b0629b00726c361ac2d9566926baf9,622a62914e810bddff29d43056fac0c85cfe7c5aa136bf34d36c890b3aadf86c"),
        new Profile("DS35-B-J1",COMMON,"7e3c316ed3e9ff3fb8c8b11fa6a4dd95bf37d334cfd4f7b82c3187b66cbb768e",312,
            "8413d06667497ad6b1bf0d09991510fbf968b2474aa7abb2069baaad3a0c544c",304,
            "a0119ac6c673f7283d719143a887b12b43560404850d33e016fafb2028b9a738","DS35","WEB-INF/lib/config.xml","data/config.xml",false,false,"73fec168bb96c4d41013a15028cf4f7f00384f0b89ba4ebfb5a8b4d9aaf8db5a"),
        new Profile("DS501-A-J1",LISTENER,"e9036df8f40fc800d344a6b8f134fb48ceca75812d48d30e6228f900f976fe12",965,
            "b5a80dc8ccb7aa9bc329a7bcca5bc92d74f098e9224dfe096b2d5241cc48d6fe",160,
            "8117318eb47a1ab4950adf173efc9fec13a7c98632cf86cc741cc5f2855790fc",null,"WEB-INF/lib/config.xml","WEB-INF/classes/config.xml",true,false,"4d782e30e56f362d7cda5c47852b4a9857ab12a478bdd909e1088e6911cdfb52,6e61424d619e24919119813cfbaa6c7492f901e1dfae7449e0683e7dc133eb07"),
        new Profile("DS501-B-J1",LISTENER,"eac016383a69a9ba8bc60dea12d06efb2372989f5c1fc0497f326b33b80a89b6",979,
            "a353bc6cc5eaffaefc9d09da4251edd74c9331f0b30f626bcf73c7a809f5d912",161,
            "27156c8e203f85dc194d564efba197b8d28e03a60fa3a7b624b862d38732e549",null,"WEB-INF/lib/config.xml","WEB-INF/classes/config.xml",true,false,"bf39c97d126e082c17c40fb4b8c6aa222cb9fe08b141b0a7ad4acc101270875f")
    };

    public static LicenseRecoverLicenseState verify(File appRoot) { return evaluate(appRoot,null).state; }
    public static Result evaluate(File appRoot, LicenseRecoverReadOnlyJavaMachineIdentity.Identity supplied) {
        return evaluateReviewed(appRoot,supplied,REVIEWED,null);
    }
    /** Synthetic test seam; production always uses the embedded reviewed profile table. */
    static Result evaluateReviewed(File appRoot, LicenseRecoverReadOnlyJavaMachineIdentity.Identity supplied,
            Profile[] profiles,Runnable beforeRecheck) {
        boolean codeAudited=false;
        boolean firstInputsRead=false;
        Path registrationPath=null;
        try {
            if(appRoot==null)return unknown("缺少 Java 应用目录");
            Path root=appRoot.toPath().toAbsolutePath().normalize();
            List<Profile> candidates=new ArrayList<Profile>();
            Map<Path,String> anchors=new HashMap<Path,String>();
            for(Profile p:profiles) {
                Path anchor=root.resolve("WEB-INF/classes").resolve(p.anchor);
                if(!anchors.containsKey(anchor)) {
                    try {anchors.put(anchor,hash(read(anchor,16*1024*1024)));}
                    catch(NoSuchFileException absent){anchors.put(anchor,null);}
                }
                if(p.anchorHash.equals(anchors.get(anchor)))candidates.add(p);
            }
            if(candidates.isEmpty())return unknown("当前 Java 本地校验入口不在已审核构建范围");
            // An entry class is only a candidate filter. Business classes or
            // startup resources may differ while this one class stays unchanged.
            Profile profile=null;Code observedCode=null;boolean unsupportedPath=false;
            Map<Boolean,Code> observations=new HashMap<Boolean,Code>();
            for(Profile p:candidates) {
                // The reviewed default constructor uses URL.getPath().replace
                // without decoding. Another reviewed layout may use the root.
                if(!p.config.equals("config.xml")&&(!root.toString().matches("[A-Za-z0-9_:/\\\\.\\-]+")
                        ||root.toString().toLowerCase(Locale.ROOT).contains("classes"))) {unsupportedPath=true;continue;}
                Code candidate=observations.get(p.dynamicFamily);
                if(candidate==null){candidate=code(root,p.dynamicFamily);observations.put(p.dynamicFamily,candidate);}
                if(!candidate.matches(p))continue;
                if(profile!=null)return unknown("同一完整 Java 构建匹配多份本地审核记录，适用规则存在歧义");
                profile=p;observedCode=candidate;
            }
            if(profile==null)return unknown(unsupportedPath?"当前路径未满足已审核的 J1 CodeSource 配置路径规则"
                    :"应用完整 class/JAR 或启动资源集合不匹配审核指纹，不能复用 Java 本地校验语义");
            registrationPath=root.resolve(profile.config);
            codeAudited=true;
            if(!hash(read(root.resolve("WEB-INF/lib/ITMCReg.jar"),128*1024*1024)).equals(profile.registrationHash))
                return unknown("注册组件不是已审核 J1 构建");
            Input first=input(root,profile,observedCode);
            firstInputsRead=true;
            Properties properties=new Properties();
            if(profile.dynamicFamily)
                properties.load(new InputStreamReader(new ByteArrayInputStream(first.properties),StandardCharsets.UTF_8));
            else properties.load(new ByteArrayInputStream(first.properties));
            if(!validPropertyKeys(properties))return unsupported("启动属性含未审核的装载或配置键，不能复用当前入口语义");
            String mode=properties.getProperty("global.system.VersionID");
            if(!identifier(mode))return unsupported("启动 VersionID 缺失或格式超出审核范围");
            Document metadata=xml(first.metadata);
            String product=profile.family;
            if(profile.dynamicFamily) product=systemSoftLastValue(metadata);
            if(!identifier(product))return unsupported("实际构造器产品标识缺失或存在歧义");
            LicenseRecoverReadOnlyJavaMachineIdentity.Identity machine=supplied==null
                    ?LicenseRecoverReadOnlyJavaMachineIdentity.current():supplied;
            LocalCheck check=checkLocal(first.registration,product,mode,machine,System.currentTimeMillis());
            if(beforeRecheck!=null)beforeRecheck.run();
            Input second=input(root,profile);
            if(!first.same(second)||!second.code.matches(profile))return unknown("Java 本地校验输入读取期间发生变化");
            // A large dependency re-read can cross an expiry boundary. Use the
            // current clock again after the final matching receipt.
            check=checkLocal(second.registration,product,mode,machine,System.currentTimeMillis());
            String scope="独立只读 J1 本地谓词 "+profile.id+"；配置="+profile.config+"；完整代码、依赖与启动资源集合匹配；";
            return new Result(decision(check,scope,profile.productFailureClosed));
        }catch(NoSuchFileException e){
            boolean registrationMissing=codeAudited&&!firstInputsRead&&registrationPath!=null&&e.getFile()!=null
                    &&registrationPath.toAbsolutePath().normalize().toString().equals(
                            Paths.get(e.getFile()).toAbsolutePath().normalize().toString());
            if(registrationMissing)return new Result(LicenseRecoverLicenseState.localObservation(
                    LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                    "所选目录实际构造器使用的注册文件缺失；副本缺失不代表原安装目录没有授权"));
            return codeAudited?unsupported("启动元数据或复核输入缺失，未据此认定本地授权不存在")
                    :unknown("Java 本地校验所需完整构建或输入未提供");
        }catch(Unsupported e){return codeAudited?unsupported(e.reason):unknown(e.reason);
        }catch(IOException|GeneralSecurityException|SecurityException e){
            return new Result(LicenseRecoverLicenseState.scanError("Java 本地只读检查失败: "+e.getClass().getSimpleName()));
        }catch(Exception e){return codeAudited?unsupported("Java 本地数据或元数据超出已审核格式，未执行目标代码")
                :unknown("Java 本地构建未完成语义绑定");}
    }
    private static Result unknown(String reason) {return new Result(LicenseRecoverLicenseState.unverified(reason));}
    private static Result unsupported(String reason) {return new Result(LicenseRecoverLicenseState.localObservation(
            LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,reason));}
    /** Called only after complete before/after input receipts match an audited profile. */
    static LicenseRecoverLicenseState decision(LocalCheck check,String scope,boolean productFailureClosed) {
        if(check.nativeLocalPass&&check.productPass&&check.currentLocalSelected&&!check.writesRequired)
            return LicenseRecoverLicenseState.authorizationAcceptance(scope+check.reason,
                    LicenseRecoverLicenseState.LocalCondition.SATISFIED);
        if(check.nativeLocalPass&&!check.productPass&&!check.writesRequired
                &&check.condition==LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH
                &&check.currentLocalSelected&&productFailureClosed)
            return LicenseRecoverLicenseState.authorizationRejection(scope
                    +"本地校验接受后立即进入当前产品检查且不触发备用路径；授权范围不含当前产品，启动授权门槛明确不通过",
                    LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH);
        return LicenseRecoverLicenseState.localObservation(check.condition,scope+check.reason
                +"；本地分支结果不代表整个授权无效，未执行网络、HASP 或其他平台回退");
    }
    private static boolean identifier(String s){return s!=null&&s.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");}
    private static boolean validPropertyKeys(Properties p) {
        Set<String> allowed=new HashSet<String>(Arrays.asList("global.system.VersionName","global.system.VersionID",
                "global.system.MySQLambient","global.system.MySQLPort","global.system.MySQLUserName","global.system.MySQLPassword"));
        return allowed.containsAll(p.stringPropertyNames());
    }

    /** Shared pure primitive. Callers must independently bind code, constructor and product rules. */
    static LocalCheck checkLocal(byte[] configXml,String product,String expectedMode,
            LicenseRecoverReadOnlyJavaMachineIdentity.Identity machine,long nowMillis) {
        boolean selected=false;
        try {
            Document config=xml(configXml);
            List<Element> regs=children(config.getDocumentElement(),"reg");
            if(regs.isEmpty())return check(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                    "没有本地注册节点",false,false,false,false);
            if(regs.size()!=1)return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "多个注册节点超出无歧义输入范围",false,false,false,false);
            Element reg=regs.get(0);String type=oneText(reg,"regType");selected="1".equals(type);
            if(!selected)return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "当前注册类型未选择已审核的本地优先模式 1，不能以本地记录代替其实际硬件或联网入口",false,false,false,false);
            String encrypted=oneText(reg,"regName");
            if(encrypted==null||encrypted.isEmpty())return check(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                    "本地模式没有注册载荷",false,false,true,false);
            if(!identifier(product)||!identifier(expectedMode))return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "构造器产品或当前启动产品模式没有可靠来源",false,false,true,false);
            if(!encrypted.matches("(?i)([0-9a-f]{2})+")||encrypted.length()>1024*1024)
                return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                        "注册载荷不在已审核的规范十六进制编码范围",false,false,true,false);
            // crypt() returns ASCII hex characters; native DES key is their UTF-8 bytes,
            // not the four decoded digest bytes.
            byte[] key=hashMd5("*ITMC"+product+"OK*").substring(0,8).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            Cipher cipher=Cipher.getInstance("DES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"DES"),new IvParameterSpec(key));
            byte[] decoded;
            try {decoded=cipher.doFinal(hexBytes(encrypted));}
            catch(GeneralSecurityException e){return check(LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                    "已有注册载荷不能按已审核算法解密或填充无效",false,false,true,false);}
            for(byte b:decoded)if((b&255)>127)return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "载荷含非 ASCII 数据，尚未证明目标 JVM 默认字符集等价",false,false,true,false);
            if(decoded.length<14)return check(LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                    "解密载荷缺少完整的原生包装结构",false,false,true,false);
            String raw=new String(decoded,StandardCharsets.US_ASCII);
            Map<String,Object> payload=new FlatJson(raw.substring(6,raw.length()-6)).object();
            canonicalBeanFields(payload);
            Object binding=payload.get("regID"),end=payload.get("endDate"),total=payload.get("totalTimes"),used=payload.get("userTimes");
            if(!(binding instanceof String)||!(end instanceof Long)||!(total instanceof Long))
                return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                        "注册记录缺少原生机器、期限或计次判定字段",false,false,true,false);
            if(machine==null||!machine.complete||machine.machineId==null)
                return check(LicenseRecoverLicenseState.LocalCondition.IDENTITY_UNAVAILABLE,
                        "当前机器标识首选规则未获得可靠结果；未读取旧机器记录代替",false,false,true,false);
            if(!binding.equals(machine.machineId))return check(LicenseRecoverLicenseState.LocalCondition.MACHINE_MISMATCH,
                    "现有本地授权的机器绑定与当前服务器不匹配",false,false,true,false);
            long endMs=(Long)end;
            // Native compares against target-JVM local midnight. These two bounds are
            // independent of the unobserved target timezone; near-boundary values stay unknown.
            if(endMs<nowMillis-48L*60*60*1000)return check(LicenseRecoverLicenseState.LocalCondition.EXPIRED,
                    "现有本地授权期限已超过所有目标时区当天边界",false,false,true,false);
            if(endMs<nowMillis)return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "授权期限位于当天边界附近，需要目标 JVM 时区才能等价判定",false,false,true,false);
            long quota=(Long)total;
            if(quota<Integer.MIN_VALUE||quota>Integer.MAX_VALUE)return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "计次字段超出原生整数范围",false,false,true,false);
            if(quota!=-1) {
                if(!(used instanceof Long)||(Long)used<Integer.MIN_VALUE||(Long)used>=Integer.MAX_VALUE)
                    return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,"已用次数缺失或存在原生溢出边界",false,false,true,false);
                if(quota<(Long)used+1)return check(LicenseRecoverLicenseState.LocalCondition.QUOTA_EXCEEDED,
                        "现有本地授权已无可用计次",false,false,true,false);
                return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                        "有限计次数值可接受，但原生成功分支需要递增并写回；未消耗真实计次",false,false,true,true);
            }
            // The application reads these integer getters before accepting its product scope.
            for(String name:new String[]{"classNum","maxCon"}) {
                Object n=payload.get(name);
                if(!(n instanceof Long)||(Long)n<Integer.MIN_VALUE||(Long)n>Integer.MAX_VALUE)
                    return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                            "应用启动读取的数值字段格式未获等价证明",false,false,true,false);
            }
            Object modes=payload.get("regStr");
            if(!(modes instanceof String))return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    "本地机器、期限和无限计次通过，但产品范围字段缺失或格式未知",true,false,true,false);
            boolean productPass=((String)modes).contains(expectedMode);
            return check(productPass?LicenseRecoverLicenseState.LocalCondition.SATISFIED
                            :LicenseRecoverLicenseState.LocalCondition.PRODUCT_MISMATCH,
                    productPass?"本地优先模式：当前机器绑定、期限、无限计次及启动产品范围全部通过；未调用目标代码或执行写回"
                            :"本地机器、期限和无限计次通过，但授权范围不包含当前启动产品模式",
                    true,productPass,true,false);
        }catch(Unsupported e){return check(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,e.reason,false,false,selected,false);
        }catch(Exception e){return check(LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                "本地注册或元数据结构无法按已审核规则读取",false,false,selected,false);}
    }
    private static LocalCheck check(LicenseRecoverLicenseState.LocalCondition c,String r,boolean n,boolean p,boolean s,boolean w) {
        return new LocalCheck(c,r,n,p,s,w);
    }
    /**
     * The target parses the whole RegeditInfo bean before checking any licence field.
     * An invalid unused property can therefore reject an otherwise valid local record.
     * Accept a canonical typed subset, not FastJSON's coercions, smart-name aliases or
     * special keys; an ignored alias could otherwise overwrite a checked property.
     * Missing optional properties retain the audited bean's zero/null defaults.
     */
    private static void canonicalBeanFields(Map<String,Object> payload)throws Unsupported {
        for(Map.Entry<String,Object> property:payload.entrySet()) {
            Object value=property.getValue();
            switch(property.getKey()) {
                case "regID":case "userID":case "proName":case "regStr":
                    if(value!=null&&!(value instanceof String))throw beanType();
                    break;
                case "classNum":case "totalTimes":case "userTimes":case "maxCon":case "countDay":
                    if(!(value instanceof Long)||(Long)value<Integer.MIN_VALUE||(Long)value>Integer.MAX_VALUE)
                        throw beanType();
                    break;
                case "beginDate":case "endDate":
                    if(value!=null&&!(value instanceof Long))throw beanType();
                    break;
                case "net":
                    if(!(value instanceof Boolean))throw beanType();
                    break;
                default:
                    throw new Unsupported("JSON 注册属性超出已审核的规范字段集合；未采用类型扩展或智能别名");
            }
        }
    }
    private static Unsupported beanType() {
        return new Unsupported("JSON 注册属性类型超出已审核的完整 Bean 构造范围");
    }
    private static String hashMd5(String s)throws GeneralSecurityException {
        return hex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.US_ASCII)));
    }
    private static byte[] hexBytes(String s) {
        byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return b;
    }

    private static final class Unsupported extends Exception {
        final String reason;Unsupported(String r){reason=r;}
    }
    private static Document xml(byte[] bytes)throws Exception {
        if(bytes==null||bytes.length>1024*1024)throw new Unsupported("XML 输入缺失或超出限制");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        javax.xml.parsers.DocumentBuilder b=f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        b.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External entities refused");});
        return b.parse(new ByteArrayInputStream(bytes));
    }
    private static List<Element> children(Element parent,String name) {
        List<Element> out=new ArrayList<Element>();NodeList nodes=parent.getChildNodes();
        for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element
                &&(name==null||name.equals(nodes.item(i).getNodeName())))out.add((Element)nodes.item(i));return out;
    }
    private static String oneText(Element root,String name)throws Unsupported {
        List<Element> matches=children(root,name);if(matches.isEmpty())return null;
        if(matches.size()!=1||!children(matches.get(0),null).isEmpty())throw new Unsupported("配置字段存在重复或嵌套歧义");
        return matches.get(0).getTextContent();
    }
    private static String systemSoftLastValue(Document metadata)throws Unsupported {
        String value=null;
        for(Element group:children(metadata.getDocumentElement(),"SystemSoft"))
            for(Element item:children(group,null)) {
                if(!children(item,null).isEmpty())throw new Unsupported("SystemSoft 元数据存在嵌套歧义");
                value=item.getTextContent();
            }
        return value;
    }

    private static final class Code {
        final int classes,libraries;final String classHash,libraryHash,wiringHash;
        Code(int c,String ch,int l,String lh,String wiring){classes=c;classHash=ch;libraries=l;libraryHash=lh;wiringHash=wiring;}
        boolean matches(Profile p){return classes==p.classCount&&libraries==p.libraryCount
                &&classHash.equals(p.classes)&&libraryHash.equals(p.libraries)&&Arrays.asList(p.wiring.split(",")).contains(wiringHash);}
        String receipt(){return classes+":"+classHash+":"+libraries+":"+libraryHash+":"+wiringHash;}
    }
    private static final class Input {
        final Code code;final byte[] properties,metadata,registration;
        Input(Code c,byte[] p,byte[] m,byte[] r){code=c;properties=p;metadata=m;registration=r;}
        boolean same(Input b){return code.receipt().equals(b.code.receipt())&&Arrays.equals(properties,b.properties)
                &&Arrays.equals(metadata,b.metadata)&&Arrays.equals(registration,b.registration);}
    }
    private static Input input(Path root,Profile profile)throws Exception {
        return input(root,profile,code(root,profile.dynamicFamily));
    }
    private static Input input(Path root,Profile profile,Code code)throws Exception {
        return new Input(code,read(root.resolve("systemConfig.yml"),1024*1024),
                read(root.resolve(profile.metadata),1024*1024),read(root.resolve(profile.config),1024*1024));
    }
    private static Code code(Path root,boolean dynamicFamily)throws Exception {
        final Path classes=root.resolve("WEB-INF/classes");final Map<String,String> cc=new TreeMap<String,String>();
        Files.walkFileTree(classes,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException {
                try{safePath(p);}catch(Unsupported e){throw new IOException("linked-code-input");}
                return p.getFileName().toString().equals(".svn")?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException {
                try {
                    safePath(p);
                    if(p.getFileName().toString().endsWith(".class"))cc.put(rel(classes,p),fileHash(p,16*1024*1024));
                    if(cc.size()>10000)throw new IOException("class-limit");return FileVisitResult.CONTINUE;
                }catch(Unsupported e){throw new IOException("linked-code-input");}
            }
            @Override public FileVisitResult visitFileFailed(Path p,IOException e)throws IOException{throw e;}
        });
        Map<String,String> jars=new TreeMap<String,String>();Path lib=root.resolve("WEB-INF/lib");safePath(lib);
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(lib)) {
            for(Path p:stream)if(p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                jars.put(rel(lib,p),fileHash(p,128*1024*1024));
        }
        return new Code(cc.size(),setHash(cc),jars.size(),setHash(jars),bootstrapReceipt(root.toFile(),dynamicFamily));
    }
    /** Includes absence and every non-class classpath resource; any new bootstrap input invalidates binding. */
    static String bootstrapReceipt(File appRoot,boolean dynamicFamily)throws Exception {
        final Path root=appRoot.toPath().toAbsolutePath().normalize();final Map<String,String> rows=new TreeMap<String,String>();
        resources(root,root.resolve("WEB-INF/classes"),rows,dynamicFamily);
        optionalResource(root,root.resolve("WEB-INF/web.xml"),rows);
        optionalResource(root,root.resolve("WEB-INF/tomcat-web.xml"),rows);
        Path meta=root.resolve("META-INF");
        try {safePath(meta);rows.put("META-INF/", "directory");resources(root,meta,rows,false);}
        catch(NoSuchFileException missing){rows.put("META-INF/","missing");}
        return setHash(rows);
    }
    private static void resources(final Path root,Path directory,final Map<String,String> rows,final boolean dynamicFamily)throws Exception {
        safePath(directory);
        Files.walkFileTree(directory,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException {
                if(p.getFileName().toString().equals(".svn"))return FileVisitResult.SKIP_SUBTREE;
                try{safePath(p);}catch(Unsupported e){throw new IOException("linked-bootstrap");}return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException {
                String relative=rel(root,p);
                if(relative.endsWith(".class")||(dynamicFamily&&relative.equals("WEB-INF/classes/config.xml")))return FileVisitResult.CONTINUE;
                try{rows.put(relative,fileHash(p,32*1024*1024));}catch(Unsupported e){throw new IOException("unsupported-bootstrap");}
                if(rows.size()>10000)throw new IOException("bootstrap-limit");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path p,IOException e)throws IOException{throw e;}
        });
    }
    private static void optionalResource(Path root,Path p,Map<String,String> rows)throws Exception {
        try{rows.put(rel(root,p),fileHash(p,32*1024*1024));}
        catch(NoSuchFileException absent){rows.put(rel(root,p),"missing");}
    }
    static Profile fixture(File root,String product,String config,String metadata,boolean dynamic,boolean closed)throws Exception {
        Code c=code(root.toPath(),dynamic);
        return new Profile("synthetic-J1",COMMON,hash(read(root.toPath().resolve("WEB-INF/classes").resolve(COMMON),16*1024*1024)),
                c.classes,c.classHash,c.libraries,c.libraryHash,product,config,metadata,dynamic,closed,c.wiringHash,
                hash(read(root.toPath().resolve("WEB-INF/lib/ITMCReg.jar"),128*1024*1024)));
    }
    private static String setHash(Map<String,String> rows) {
        StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:rows.entrySet())
            b.append(e.getValue()).append("  ").append(e.getKey()).append('\n');return hash(b.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String rel(Path root,Path p){return root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\','/');}
    private static void safePath(Path input)throws IOException,Unsupported {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("inspection-cancelled");
        Path absolute=input.toAbsolutePath().normalize(),cursor=absolute.getRoot();
        for(Path part:absolute){cursor=cursor.resolve(part);BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(a.isSymbolicLink()||a.isOther())throw new Unsupported("链接或特殊文件不在 Java 本地校验范围");}
    }
    private static byte[] read(Path p,int limit)throws IOException,Unsupported {
        safePath(p);BasicFileAttributes before=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()>limit)throw new Unsupported("Java 证据不是受限常规文件");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("inspection-cancelled");if(out.size()+n>limit)throw new IOException("input-limit");out.write(b,0,n);}
        }
        BasicFileAttributes after=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())
                ||!Objects.equals(before.fileKey(),after.fileKey()))throw new Unsupported("Java 证据读取期间变化");return out.toByteArray();
    }
    private static String fileHash(Path p,int limit)throws IOException,Unsupported {
        safePath(p);BasicFileAttributes before=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()>limit)throw new Unsupported("代码证据不是受限常规文件");
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(GeneralSecurityException e){throw new AssertionError(e);}
        long total=0;
        try(InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("inspection-cancelled");if((total+=n)>limit)throw new IOException("input-limit");digest.update(b,0,n);}
        }
        BasicFileAttributes after=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())
                ||!Objects.equals(before.fileKey(),after.fileKey()))throw new Unsupported("代码证据读取期间变化");return hex(digest.digest());
    }
    private static String hash(byte[] bytes) {
        try{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(GeneralSecurityException e){throw new AssertionError(e);}
    }
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)
        b.append(Character.forDigit((x>>>4)&15,16)).append(Character.forDigit(x&15,16));return b.toString();}

    /** Strict bounded subset; unsupported FastJSON extensions remain unknown rather than accepted. */
    private static final class FlatJson {
        final String s;int p;FlatJson(String s){this.s=s;}
        Map<String,Object> object()throws Unsupported {
            Map<String,Object> out=new HashMap<String,Object>();space();need('{');space();
            if(take('}'))return out;
            do {space();String key=string();space();need(':');space();Object value=value();
                if(out.containsKey(key)||out.size()>64)throw bad();out.put(key,value);space();
                if(take('}')){space();if(p!=s.length())throw bad();return out;}need(',');
            }while(true);
        }
        Object value()throws Unsupported {
            if(p>=s.length())throw bad();char c=s.charAt(p);
            if(c=='"')return string();
            for(String token:new String[]{"true","false","null"})if(s.startsWith(token,p)){p+=token.length();return token.equals("null")?null:Boolean.valueOf(token);}
            int start=p;if(c=='-')p++;if(p>=s.length()||!Character.isDigit(s.charAt(p)))throw bad();
            if(s.charAt(p)=='0')p++;else while(p<s.length()&&s.charAt(p)>='0'&&s.charAt(p)<='9')p++;
            try{return Long.valueOf(s.substring(start,p));}catch(NumberFormatException e){throw bad();}
        }
        String string()throws Unsupported {
            need('"');StringBuilder b=new StringBuilder();
            while(p<s.length()){char c=s.charAt(p++);if(c=='"')return b.toString();if(c<32)throw bad();
                if(c=='\\'){if(p>=s.length())throw bad();char e=s.charAt(p++);
                    if(e=='"'||e=='\\'||e=='/')c=e;
                    else if(e=='b')c='\b';else if(e=='f')c='\f';else if(e=='n')c='\n';else if(e=='r')c='\r';else if(e=='t')c='\t';
                    else if(e=='u'){if(p+4>s.length())throw bad();try{c=(char)Integer.parseInt(s.substring(p,p+4),16);}catch(NumberFormatException x){throw bad();}p+=4;}
                    else throw bad();}
                b.append(c);if(b.length()>65536)throw bad();
            }throw bad();
        }
        void space(){while(p<s.length()&&(s.charAt(p)==' '||s.charAt(p)=='\n'||s.charAt(p)=='\r'||s.charAt(p)=='\t'))p++;}
        boolean take(char c){if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}
        void need(char c)throws Unsupported{if(!take(c))throw bad();}
        Unsupported bad(){return new Unsupported("JSON 载荷不在已审核的无重复扁平标准格式范围");}
    }
}
