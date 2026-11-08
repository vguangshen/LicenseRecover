import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Shared protocol classification and audited data bindings. Neither identifies activation by name. */
public final class LicenseRecoverReadOnlyDotNetCatalog {
    private LicenseRecoverReadOnlyDotNetCatalog() { }
    private static final long MAX_SET_BYTES=192L*1024*1024;
    private static final int MAX_METADATA_BYTES=16*1024*1024;
    public enum Family { MODERN_JSON_DES, LEGACY_FIXED_RECORD, PROTECTED_COMPATIBILITY,
        REGINFO_API_CANDIDATE, COMPATIBILITY_API_CANDIDATE, UNKNOWN }
    enum LocalFormat { FIXED_MARKET_RECORD, PROTECTED_VM, RECOVERED_FIXED_FIELDS }
    static final class UpperBinding {
        final String web,registration,product,cryptoFile,cryptoHash,jsonHash,logHash,compatibilityHash;
        final boolean positiveEntryAudited;final String auditGap;
        UpperBinding(String web,String registration,String product,String file,String crypto,String json,String log) {
            this(web,registration,product,file,crypto,json,log,true,"");
        }
        UpperBinding(String web,String registration,String product,String file,String crypto,String json,String log,
                boolean positiveEntryAudited,String auditGap) {
            this(web,registration,product,file,crypto,json,log,positiveEntryAudited,auditGap,null);
        }
        UpperBinding(String web,String registration,String product,String file,String crypto,String json,String log,
                String compatibilityHash) {
            this(web,registration,product,file,crypto,json,log,true,"",compatibilityHash);
        }
        UpperBinding(String web,String registration,String product,String file,String crypto,String json,String log,
                boolean positiveEntryAudited,String auditGap,String compatibilityHash) {
            this.web=web;this.registration=registration;this.product=product;cryptoFile=file;
            cryptoHash=crypto;jsonHash=json;logHash=log;
            this.compatibilityHash=compatibilityHash;
            this.positiveEntryAudited=positiveEntryAudited;this.auditGap=auditGap;
            if(!positiveEntryAudited&&(auditGap==null||auditGap.trim().isEmpty()))throw new IllegalArgumentException("local audit requires an explicit entry gap");
        }
        boolean matches(Map<String,String> h) {
            return eq(h,"ITMC.Web.dll",web)&&eq(h,"ITMC.Regedit.dll",registration)
                    &&eq(h,cryptoFile,cryptoHash)&&eq(h,"Newtonsoft.Json.dll",jsonHash)
                    &&(logHash==null?!h.containsKey("log4net.dll"):eq(h,"log4net.dll",logHash))&&eq(h,"LiteDB.dll",LITE)
                    &&(compatibilityHash==null||eq(h,"itmcRegedit.dll",compatibilityHash));
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
        boolean fixedFields(){return legacy()||format==LocalFormat.RECOVERED_FIXED_FIELDS;}
    }
    private static final String LITE="4091c455a882e3e44c8dc0c4034e6b2536f180ce019695769ef72497a90467ec";
    private static final String MODE_WEB_A="929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4";
    private static final String MODE_WEB_B="07183f09c19b69c5c4ddcdcb43ad4b8d91cf56f629f6a3d7d675dfc84f761079";
    private static final String MODE_WEB_C="d22c923d7f11b084e2839b3d0d6ee572e06ad5d41200ff6c8215757f54e7501e";
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
            "ITMC.DBUtility.dll","f8c2ff0b28b03bfc33ce61e32f63d8b179c0c213a4ccab8ba748aa4dd73d5c22",JSON7,LOG_NEW),
        // The shared local predicate is audited; the protected website result constructor is not.
        new UpperBinding("929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725","YX0302",
            "ITMC.DBUtility.dll","dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",JSON6,
            "c228ad98c76a3b40bbd1bc950b81feff977646b2b99a21310164d4d310c7ea75",false,
            "网站初始化依赖受保护的原生桥接，产品/父版本数据库映射也未闭合；见 docs/DOTNET_YX0302_UPPER_LOCAL_AUDIT.md"),
        new UpperBinding("07183f09c19b69c5c4ddcdcb43ad4b8d91cf56f629f6a3d7d675dfc84f761079",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725","YX0302",
            "ITMC.DBUtility.dll","dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",JSON6,
            "c228ad98c76a3b40bbd1bc950b81feff977646b2b99a21310164d4d310c7ea75",false,
            "网站初始化依赖受保护的原生桥接，产品/父版本数据库映射也未闭合；见 docs/DOTNET_YX0302_UPPER_LOCAL_AUDIT.md"),
        // YS01 shares the local JSON predicate, but the compatibility module has native initialization.
        // The lazy business fallback alone does not close module/JIT interposition; keep local-only.
        new UpperBinding("1790526c91b0dc51aa914d80604e600175cfb3d8899c8299bb3b2c85684b9b20",
            "c942c3c21466979e63eee1ffadbfe7273c83c2dc0a455f324c9da7e187953f46","YS01",
            "ITMC.DBUtility.dll","236cfe610b87fb7a4f6589cb2a9e319a048b8a8e2a1b79d3028d4d7e1b5fcb6b",JSON7,null,false,
            "兼容模块包含原生 JIT 初始化路径，其回调及跨模块作用范围尚未闭合；见 docs/DOTNET_YS01_UPPER_AUDIT_20261107.md",
            "36f58b8657bce75a19356796f21c135104e28f5abc4bc9007f0d4ec517f479df"),
        new UpperBinding("d22c923d7f11b084e2839b3d0d6ee572e06ad5d41200ff6c8215757f54e7501e",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725","YX0301",
            "ITMC.DBUtility.dll","dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",JSON6,
            "c228ad98c76a3b40bbd1bc950b81feff977646b2b99a21310164d4d310c7ea75",false,
            "网站先读取 Data/db/RegisterVersion.db 的当前模式/父产品映射；数据库查询、解密及模式选择尚未纳入完整入口证明；见 docs/DOTNET_YX0301_LOCAL_AUDIT_20261108.md",
            "e684d265332af25ccc3449c38a38f024ecbf87353b9c48710c5ae2b2433c5183")
    };
    private static final ExtendedBinding[] EXTENDED={
        new ExtendedBinding("1790526c91b0dc51aa914d80604e600175cfb3d8899c8299bb3b2c85684b9b20",
            "c942c3c21466979e63eee1ffadbfe7273c83c2dc0a455f324c9da7e187953f46",
            "36f58b8657bce75a19356796f21c135104e28f5abc4bc9007f0d4ec517f479df",
            "236cfe610b87fb7a4f6589cb2a9e319a048b8a8e2a1b79d3028d4d7e1b5fcb6b",LocalFormat.FIXED_MARKET_RECORD),
        new ExtendedBinding("929d67295961ed453202e4ea44bbeb2614543b7212c50717d58a455558067be4",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725",
            "35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f",
            "dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",LocalFormat.RECOVERED_FIXED_FIELDS),
        new ExtendedBinding("07183f09c19b69c5c4ddcdcb43ad4b8d91cf56f629f6a3d7d675dfc84f761079",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725",
            "35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f",
            "dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",LocalFormat.RECOVERED_FIXED_FIELDS),
        new ExtendedBinding("d22c923d7f11b084e2839b3d0d6ee572e06ad5d41200ff6c8215757f54e7501e",
            "222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725",
            "e684d265332af25ccc3449c38a38f024ecbf87353b9c48710c5ae2b2433c5183",
            "dc7e1806270ae5dc54c073f417949f7a3b1f24731e70925d16027e645ce150bc",LocalFormat.RECOVERED_FIXED_FIELDS)
    };
    private static final String[] COMPONENTS={"config.xml","ITMC.Web.dll","ITMC.Regedit.dll","itmcRegedit.dll",
            "ItmcBtoB.DBUtility.dll","ITMC.DBUtility.dll","Newtonsoft.Json.dll","log4net.dll","LiteDB.dll"};
    private static final String MODE_DB="Data/db/RegisterVersion.db";
    private static final String[] MODE_FILES={MODE_DB,"Data/db/RegisterVersion-log.db","Data/db/RegisterVersion-tmp.db"};
    private static final int MODE_FILE_LIMIT=16*1024*1024;
    private static final String[] MODE_CONSUMERS={MODE_WEB_A,MODE_WEB_B,MODE_WEB_C};
    // Reviewed current data, not keys or executable license overrides. See the independent audit.
    private static final ModeReceipt[] MODE_RECEIPTS={
        new ModeReceipt("MODE-RELATIONS-20261109-1",MODE_WEB_A,LITE,
            "dd74a18a833f862c21639755742069f1cba8469d669cd1d51c0f2e20136e71e3","YX030107","QT1001:QT100111,QT100113"),
        new ModeReceipt("MODE-RELATIONS-20261109-2",MODE_WEB_C,LITE,
            "6d0312991988f6b40686597e42b682c0bc9fedadad9c6cb444265ae2dd892c2b","YX030118","QT04:QT0440,QT0460,QT0462,QT0463;PT02:PT0205"),
        new ModeReceipt("MODE-RELATIONS-20261109-3",MODE_WEB_A,LITE,
            "30806a2634fb1d3dd80aac01bea6fc39b0bef8eda75c4a5136b0ef8cc8f6e543","YX030201","QT1001:QT100113;PT02:PT0213"),
        new ModeReceipt("MODE-RELATIONS-20261109-4",MODE_WEB_B,LITE,
            "f8a7cd1532ffbc2d243ff4d007e04c2e8cec1b35a8afaa8caf03a02c69887ac0","YX030211","QT04:QT0424;PT02:PT0213")
    };
    static final class ModeReceipt {
        final String id,web,lite,database,mode;final Map<String,List<String>> parents;
        ModeReceipt(String id,String web,String lite,String database,String mode,String groups) {
            this.id=id;this.web=web;this.lite=lite;this.database=database;this.mode=mode;
            Map<String,List<String>> parsed=new LinkedHashMap<String,List<String>>();
            for(String group:groups.split(";")) {
                String[] pair=group.split(":",-1);if(pair.length!=2||!pair[0].matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException("mode-receipt");
                List<String> values=new ArrayList<String>();for(String value:pair[1].split(",")) {
                    if(!value.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException("mode-receipt");values.add(value);
                }
                if(values.isEmpty()||parsed.put(pair[0],Collections.unmodifiableList(values))!=null)throw new IllegalArgumentException("mode-receipt");
            }
            parents=Collections.unmodifiableMap(parsed);
        }
    }
    /** Immutable relation-data diagnostic; it never contains or produces a license state. */
    public static final class ModeRelations {
        public final String status,profileId,databaseSHA256,currentMode,reason;
        public final String auditScope;
        public final Map<String,List<String>> parentModes;
        public final List<String> companionFiles;
        public final boolean inspectionReadOnlyAudited=true,fullAuthorizationAudited=false;
        private ModeRelations(String status,String profile,String database,String mode,String reason,
                Map<String,List<String>> parents,List<String> companions) {
            this.status=status;profileId=profile;databaseSHA256=database;currentMode=mode;this.reason=reason;
            auditScope="EXACT_REVIEWED_DATA".equals(status)?"EXACT_REVIEWED_RELATION_DATA_ONLY"
                    :"MISSING_DATABASE".equals(status)?"REVIEWED_MISSING_DATABASE_BRANCH":"OBSERVATION_ONLY";
            Map<String,List<String>> copy=new LinkedHashMap<String,List<String>>();
            for(Map.Entry<String,List<String>> e:parents.entrySet())copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
            parentModes=Collections.unmodifiableMap(copy);companionFiles=Collections.unmodifiableList(new ArrayList<String>(companions));
        }
        static ModeRelations unassessed(){return modeResult("NOT_ASSESSED","none","","","未收集模式关系的当前输入",null,Collections.<String>emptyList());}
        static ModeRelations revoked(){return modeResult("CHANGED_OR_REVOKED","none","","","读取期间输入或消费组件绑定变化，已撤回模式关系结论",null,Collections.<String>emptyList());}
        public String summary() {
            return "模式关系："+reason+(parentModes.isEmpty()?"":"；先行父产品/模式="+parentModes)
                    +"；仅为关系数据证据，未验证父产品授权或实际运行顺序";
        }
    }
    private static ModeRelations modeResult(String status,String id,String database,String mode,String reason,
            Map<String,List<String>> parents,List<String> companions) {
        return new ModeRelations(status,id,database,mode,reason,parents==null?Collections.<String,List<String>>emptyMap():parents,companions);
    }
    private static boolean modeConsumer(String web,String[] consumers) {return web!=null&&Arrays.asList(consumers).contains(web);}
    static ModeRelations modeRelations(Map<String,String> hashes,ModeReceipt[] receipts,String[] consumers,String lite,boolean consumerBinding) {
        String web=hashes.get("ITMC.Web.dll"),database=hashes.get(MODE_DB),mode=hashes.get("@mode-current");
        String dbHash=database==null||"absent".equals(database)?"":database;
        mode=mode==null?"":mode;
        List<String> companions=new ArrayList<String>();
        for(int i=1;i<MODE_FILES.length;i++)if(hashes.containsKey(MODE_FILES[i])&&!"absent".equals(hashes.get(MODE_FILES[i])))companions.add(MODE_FILES[i]);
        if(!consumerBinding||!modeConsumer(web,consumers)||!lite.equals(hashes.get("LiteDB.dll")))
            return modeResult("UNREVIEWED_CONSUMER","none",dbHash,mode,"当前 Web/LiteDB 消费组件不匹配模式关系审核绑定",null,companions);
        if(!hashes.containsKey("@mode-observed"))return ModeRelations.unassessed();
        if(!companions.isEmpty())return modeResult("COMPANION_PRESENT","none",dbHash,mode,"检测到数据库伴随文件，未按主文件推断当前关系："+companions,null,companions);
        if("absent".equals(database))return modeResult("MISSING_DATABASE","none","",mode,"已审核入口在关系数据库缺失时直接返回空字典；不表示本地或整体授权无效",null,companions);
        if(mode.isEmpty())return modeResult("CURRENT_MODE_UNKNOWN","none",dbHash,"","当前模式配置缺失、歧义或格式未审核，未选用其他模式的关系",null,companions);
        for(ModeReceipt receipt:receipts)if(receipt.web.equals(web)&&receipt.lite.equals(hashes.get("LiteDB.dll"))
                &&receipt.database.equals(database)&&receipt.mode.equals(mode))
            return modeResult("EXACT_REVIEWED_DATA",receipt.id,dbHash,mode,"数据库、消费构建与当前模式精确匹配已审核关系；网站先尝试父产品，再检查自身产品",receipt.parents,companions);
        return modeResult("UNKNOWN_DATABASE_OR_MODE","none",dbHash,mode,"数据库或当前模式没有成套审核凭据；未知关系不能按空字典处理",null,companions);
    }
    static final class Changed extends IOException {
        final boolean modeInputs;
        Changed(){this(false);}Changed(boolean modeInputs){this.modeInputs=modeInputs;}
    }
    /** Code-set coverage only. An exact diagnostic binding is not a complete license proof. */
    public static final class FingerprintMatch {
        public final String profileId, auditScope;
        public final int requiredComponents, matchedComponents;
        public final List<String> missingComponents, differentComponents, requiredAbsentComponents;
        public final boolean exact;
        FingerprintMatch(String id,String scope,Map<String,String> required,Map<String,String> observed) {
            profileId=id;auditScope=scope;requiredComponents=required.size();int matched=0;
            List<String> missing=new ArrayList<String>(),different=new ArrayList<String>(),absent=new ArrayList<String>();
            for(Map.Entry<String,String> e:required.entrySet()) {
                String actual=observed.get(e.getKey());
                if(e.getValue()==null) {absent.add("bin/"+e.getKey());if(actual==null)matched++;else different.add("bin/"+e.getKey());}
                else if(actual==null)missing.add("bin/"+e.getKey());
                else if(e.getValue().equals(actual))matched++;else different.add("bin/"+e.getKey());
            }
            matchedComponents=matched;exact=matched==requiredComponents;
            missingComponents=Collections.unmodifiableList(missing);differentComponents=Collections.unmodifiableList(different);
            requiredAbsentComponents=Collections.unmodifiableList(absent);
        }
    }
    public static final class Assessment {
        public final Family upperFamily,compatibilityFamily;
        public final boolean auditedBinding;
        public final String fingerprintStatus, auditScope, profileId;
        public final List<FingerprintMatch> fingerprintMatches;
        public final ModeRelations modeRelations;
        final UpperBinding upper;final ExtendedBinding extended;final Map<String,String> hashes;
        Assessment(Family a,Family b,UpperBinding u,ExtendedBinding e,Map<String,String> hashes) {
            upperFamily=a;compatibilityFamily=b;upper=u;extended=e;auditedBinding=u!=null||e!=null;
            this.hashes=Collections.unmodifiableMap(new TreeMap<String,String>(hashes));
            modeRelations=modeRelations(this.hashes,MODE_RECEIPTS,MODE_CONSUMERS,LITE,u!=null&&e!=null);
            fingerprintMatches=fingerprintMatches(this.hashes);
            // The same build may support a reviewed modern positive path AND a legacy
            // diagnostic. Those are different scopes, not competing Patch provenances.
            String selectedScope=u!=null?u.positiveEntryAudited?"LOCAL_POSITIVE_PREDICATE":"MODERN_LOCAL_DIAGNOSTIC":e!=null
                    ?e.legacy()?"LEGACY_LOCAL_DIAGNOSTIC":"PROTECTED_LOCAL_DIAGNOSTIC":"NONE";
            FingerprintMatch exact=null;int exactCount=0;
            for(FingerprintMatch match:fingerprintMatches)if(match.exact&&selectedScope.equals(match.auditScope)){exact=match;exactCount++;}
            fingerprintStatus=exactCount==1?"EXACT_REVIEWED_SET":exactCount>1?"AMBIGUOUS_REVIEWED_SETS"
                    :!fingerprintMatches.isEmpty()?"PARTIAL_REVIEWED_SET"
                    :a==Family.REGINFO_API_CANDIDATE||b==Family.COMPATIBILITY_API_CANDIDATE?"API_CANDIDATE":"UNKNOWN";
            profileId=exactCount==1?exact.profileId:"none";auditScope=exactCount==1?exact.auditScope:"NONE";
        }
        public String summary() {
            String family=upperFamily==Family.MODERN_JSON_DES?"现代本地记录协议"
                    :upperFamily==Family.REGINFO_API_CANDIDATE?"同系列注册接口候选":"未识别的大写注册协议";
            if(compatibilityFamily!=Family.UNKNOWN)family+="及兼容注册组件";
            return family+"；"+fingerprintSummary()
                    +("UNREVIEWED_CONSUMER".equals(modeRelations.status)?"":"；"+modeRelations.summary());
        }
        public String fingerprintSummary() {
            if("EXACT_REVIEWED_SET".equals(fingerprintStatus))return "构建指纹 "+profileId
                    +("LOCAL_POSITIVE_PREDICATE".equals(auditScope)?" 已匹配本地接受判据的审核组件集合"
                    :" 已匹配局部诊断的审核组件集合，完整接受路径尚未闭合")
                    +(upper!=null&&!upper.positiveEntryAudited?"；缺口："+upper.auditGap:"");
            StringBuilder result=new StringBuilder("当前构建未匹配完整审核组件集合");
            for(FingerprintMatch match:fingerprintMatches)result.append("；").append(match.profileId)
                    .append(" 匹配 ").append(match.matchedComponents).append('/').append(match.requiredComponents)
                    .append("，缺少 ").append(match.missingComponents).append("，不同 ").append(match.differentComponents);
            return result.toString();
        }
        boolean sameInputs(Assessment other) {return other!=null&&hashes.equals(other.hashes);}
    }
    private static List<FingerprintMatch> fingerprintMatches(Map<String,String> observed) {
        List<FingerprintMatch> result=new ArrayList<FingerprintMatch>();int index=0;
        for(UpperBinding binding:UPPER) {
            Map<String,String> required=new TreeMap<String,String>();
            required.put("ITMC.Web.dll",binding.web);required.put("ITMC.Regedit.dll",binding.registration);
            required.put(binding.cryptoFile,binding.cryptoHash);required.put("Newtonsoft.Json.dll",binding.jsonHash);
            required.put("log4net.dll",binding.logHash);required.put("LiteDB.dll",LITE);
            if(binding.compatibilityHash!=null)required.put("itmcRegedit.dll",binding.compatibilityHash);
            addCoverage(result,"DOTNET-JSON-DES-"+(++index),binding.positiveEntryAudited?"LOCAL_POSITIVE_PREDICATE":"MODERN_LOCAL_DIAGNOSTIC",required,observed);
        }
        index=0;
        for(ExtendedBinding binding:EXTENDED) {
            Map<String,String> required=new TreeMap<String,String>();
            required.put("ITMC.Web.dll",binding.web);required.put("ITMC.Regedit.dll",binding.upper);
            required.put("itmcRegedit.dll",binding.lower);required.put("ITMC.DBUtility.dll",binding.crypto);
            addCoverage(result,"DOTNET-COMPAT-"+(++index),binding.legacy()?"LEGACY_LOCAL_DIAGNOSTIC":"PROTECTED_LOCAL_DIAGNOSTIC",required,observed);
        }
        return Collections.unmodifiableList(result);
    }
    private static void addCoverage(List<FingerprintMatch> result,String id,String scope,Map<String,String> required,
            Map<String,String> observed) {
        // Only a matching site/registration component admits an evidence candidate, not a ubiquitous dependency.
        for(String anchor:new String[]{"ITMC.Web.dll","ITMC.Regedit.dll","itmcRegedit.dll"})
            if(required.containsKey(anchor)&&required.get(anchor).equals(observed.get(anchor))) {
                result.add(new FingerprintMatch(id,scope,required,observed));return;
            }
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
        captureModeInputs(app,hashes,MODE_CONSUMERS,known.upper!=null&&known.extended!=null,MAX_SET_BYTES-total);
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
        final long size;final String hash,identity;
        Observed(long size,String hash,String identity){this.size=size;this.hash=hash;this.identity=identity;}
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
        return new Observed(count,hex(digest.digest()),count+":"+before.lastModifiedTime()+":"+before.fileKey());
    }
    private static LicenseRecoverReadOnlyPeMetadata.ApiShape apiShape(Path p,Observed observed)throws IOException {
        return LicenseRecoverReadOnlyPeMetadata.parseApiShape(readObserved(p,observed,MAX_METADATA_BYTES));
    }
    private static byte[] readObserved(Path p,Observed observed,int limit)throws IOException {
        if(observed.size>limit)throw new IOException("protocol-metadata-limit");
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
            return bytes;
        }catch(NoSuchFileException missing){throw new Changed();}
    }
    /** Captures only the audited consumer's data inputs; synthetic seams never enter the production registry. */
    static void captureModeInputs(Path app,Map<String,String> hashes,String[] consumers,boolean consumerBinding,long remaining)throws IOException {
        if(!consumerBinding||!modeConsumer(hashes.get("ITMC.Web.dll"),consumers))return;
        hashes.put("@mode-observed","true");
        for(String relative:MODE_FILES) {
            try {
                Observed value=observe(app.resolve(relative),MODE_FILE_LIMIT,remaining);remaining-=value.size;
                hashes.put(relative,value.hash);hashes.put("@mode-identity:"+relative,value.identity);
            }catch(NoSuchFileException missing){hashes.put(relative,"absent");}
            catch(Changed changed){throw new Changed(true);}
        }
        if(hashes.containsKey("config.xml")) {
            try {
                Path p=app.resolve("config.xml");Observed current=observe(p,4*1024*1024,4*1024*1024);
                if(!current.hash.equals(hashes.get("config.xml")))throw new Changed(true);
                hashes.put("@mode-current",modeVersion(readObserved(p,current,4*1024*1024)));
            }catch(NoSuchFileException missing){throw new Changed(true);}
            catch(Changed changed){throw new Changed(true);}
        }else hashes.put("@mode-current","");
    }
    private static String modeVersion(byte[] xml) {
        try {
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            f.setXIncludeAware(false);f.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder=f.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Element root=builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
            if(root==null||!("ROOT".equals(root.getTagName())||"root".equals(root.getTagName())))return "";
            Element section=null;
            for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&"SystemSoft".equals(n.getNodeName())) {
                if(section!=null)return "";section=(Element)n;
            }
            if(section==null)return "";
            String version=null;
            for(Node n=section.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&"SoftVersionID".equals(n.getNodeName())) {
                if(version!=null)return "";
                for(Node child=n.getFirstChild();child!=null;child=child.getNextSibling())if(child instanceof Element)return "";
                version=n.getTextContent();
            }
            return version!=null&&version.matches("[A-Za-z0-9._-]{1,64}")?version:"";
        }catch(Exception malformed){return "";}
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
