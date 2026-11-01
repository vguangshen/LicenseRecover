import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Audited alternate Java entry paths. No vendor classes, registration writes or target processes. */
public final class LicenseRecoverReadOnlyJavaAlternateVerifier {
    private LicenseRecoverReadOnlyJavaAlternateVerifier() { }
    enum Layout { DEFAULT_LIBRARY, SERVLET_ROOT, DATABASE_PRODUCT_CHAIN, XMT_ANY_MODE }
    static final class Profile {
        final String id, anchor, anchorHash, classes, libraries, wiring;
        final int classCount, libraryCount;
        final Layout layout;
        Profile(String id,String anchor,String anchorHash,int classCount,String classes,
                int libraryCount,String libraries,String wiring,Layout layout) {
            this.id=id;this.anchor=anchor;this.anchorHash=anchorHash;this.classCount=classCount;
            this.classes=classes;this.libraryCount=libraryCount;this.libraries=libraries;
            this.wiring=wiring;this.layout=layout;
        }
        boolean matches(Snapshot s) {
            return classCount==s.classes.size() && libraryCount==s.libraries.size()
                    && classes.equals(digest(s.classes)) && libraries.equals(digest(s.libraries))
                    && wiring.equals(digest(s.wiring));
        }
    }
    // Exact code and bootstrap-resource review: docs/JAVA_ALTERNATE_ENTRY_AUDIT.md.
    private static final Profile[] REVIEWED = {
        new Profile("QT1001-entry-20261031","com/itmc/register/service/RegisterListener.class",
                "0dfd053113cd98d709473a5c52c2d1875108d017e6ea6167a26ff9f145d900d6",281,
                "54f2fd6fa37d7028f7a76c3f8069622574d070fbc0a0a43319fcfa4f3606d592",147,
                "2a793f281f5d6d01f79234161022b02bbd0e6b2881ba585fdad54e31107961f9",
                "cb25ed2030de926c9559fcb8f6f077d5c84a8ed056b50c683eff3c5bfac16e78",Layout.DEFAULT_LIBRARY),
        new Profile("QT30-entry-20261031","cn/itmc/analysis/common/sys/configuration/SysParamInit.class",
                "1864277050a2ce63adeb51a3f5521bf533203c5b3059a5c0cc692b8fba95d618",377,
                "a79473e7892b9c6ef7eaf7ae35f9f2e86799e8755a28400e532eb5d247203fb1",118,
                "34c61eb026103cbe50d657faac21be56670e3fd051a91ed2dad1a78c6867efab",
                "a995ce5b0ecd8adefcdce6751db4e010b2ca2fecb8cb50d8366cb54e14d07415",Layout.SERVLET_ROOT),
        new Profile("XMT-legacy-entry-20261031","com/itmc/register/service/RegisterListener.class",
                "3a60505c228eb529cee51f5be058c9774d2134799b51b28ca8fe0b87073028ce",225,
                "6a5d688cfba6751acd006fb703a0f724e490d5ea0dbf02821f64c33590aba6e9",107,
                "48a6251e4786b5a16d399269e70d35e76248f6478482822611758f1d790964cc",
                "9b21ce15dfdb401126debc8e6ed91085542dbd5e7d54c75b4c2bdc7fd90b775a",Layout.SERVLET_ROOT),
        new Profile("XMT-any-mode-entry-20261031","com/itmc/register/service/RegisterListener.class",
                "4ce4929c691401ec201ae09701fb86ddf35e9f62b511378c362f7447344f7bb9",259,
                "edd7534d2ceeb05f57050eba0020610cf105800dfe6de28d837de276b766f449",154,
                "49b5574dfe3c710dd628bb9e033e47c15d709d6dfb63fea4325f9c5cc3a5bd24",
                "b2587c8088d33f6cf8775effa3145c0922341b22bbd7bb9155c845b0a4dac7bf",Layout.XMT_ANY_MODE),
        new Profile("YT-product-chain-entry-20261031","com/common/sys/configuration/SysParamInit.class",
                "3a376103f2200bdf9dea33be43b8d0e907877701968d1cfc7ec4c8fe8ff9874b",664,
                "a4e9ed3e5410e095cebf07c5d9aba6db5ff911d4f18269bd7f17166424d25192",278,
                "db7e6ddf97496f6a485d923265d5ca440ab78079376bc34c4add846eb8ae672c",
                "79c7a275996e7ab045d0d67ed6052a231e27e9dd3d15252d30b5617a46ebaa40",Layout.DATABASE_PRODUCT_CHAIN)
    };
    private static final String[] INPUTS={"config.xml","WEB-INF/classes/config.xml","data/config.xml",
            "data/system.json","systemConfig.yml","WEB-INF/lib/config.xml","WEB-INF/web.xml"};
    private static final Set<String> DB_VALUES=new HashSet<String>(Arrays.asList(
            "spring.datasource.url","spring.datasource.username","spring.datasource.password",
            "spring.datasource.druid.url","spring.datasource.druid.username","spring.datasource.druid.password"));

    public static LicenseRecoverLicenseState verify(File appRoot) {
        if(appRoot==null)return LicenseRecoverLicenseState.unverified("缺少 Java 应用目录");
        try {
            for(Profile profile:REVIEWED) {
                Path anchor=appRoot.toPath().resolve("WEB-INF/classes").resolve(profile.anchor);
                Item item=optional(anchor,false,false);
                if(item!=null && profile.anchorHash.equals(item.sha))
                    return verifyReviewed(appRoot,new Profile[]{profile},null,System.currentTimeMillis(),null,null);
            }
            return LicenseRecoverLicenseState.unverified("Java 备用授权入口未匹配已审核的完整构建");
        }catch(LinkedInput|Changed e){return LicenseRecoverLicenseState.unverified("Java 备用入口链接布局或读取期间变化");}
        catch(IOException|SecurityException e){return LicenseRecoverLicenseState.scanError("Java 备用入口读取失败: "+e.getClass().getSimpleName());}
    }

    /** Opaque current code/configuration receipt, also used by the coordinator's final recheck. */
    public static String inputSnapshot(File root)throws IOException {return snapshot(root.toPath()).identity();}
    static final class Check {
        final LicenseRecoverLicenseState.LocalCondition condition;
        final String reason;final boolean nativePass,productPass,selected,writes;
        Check(LicenseRecoverLicenseState.LocalCondition condition,String reason,boolean nativePass,
              boolean productPass,boolean selected,boolean writes) {
            this.condition=condition;this.reason=reason;this.nativePass=nativePass;
            this.productPass=productPass;this.selected=selected;this.writes=writes;
        }
    }
    interface LocalEvaluator {
        Check check(byte[] config,String product,String mode,LicenseRecoverReadOnlyJavaMachineIdentity.Identity identity,long now);
    }
    // Package-private synthetic seam; production cannot import a profile or a successful result.
    static LicenseRecoverLicenseState verifyReviewed(File root,Profile[] profiles,LocalEvaluator evaluator,
            long now,LicenseRecoverReadOnlyJavaMachineIdentity.Identity identity,Runnable beforeRecheck) {
        try {
            Snapshot first=snapshot(root.toPath());
            for(Profile profile:profiles) {
                if(!valid(profile))return LicenseRecoverLicenseState.scanError("Java 备用入口审核凭据损坏");
                if(!profile.matches(first))continue;
                if(profile.layout==Layout.DEFAULT_LIBRARY && (!root.getAbsolutePath().matches("[A-Za-z0-9_:/\\\\.\\-]+")
                        || root.getAbsolutePath().contains("classes")))
                    return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                            "默认注册组件的 CodeSource 路径替换需要额外审核，未选择看似有效的其他注册文件");
                LicenseRecoverLicenseState result=evaluate(profile,first,evaluator,now,identity);
                if(beforeRecheck!=null)beforeRecheck.run();
                Snapshot second=snapshot(root.toPath());
                if(!profile.matches(second)||!first.identity().equals(second.identity()))
                    return LicenseRecoverLicenseState.unverified("Java 备用入口代码或配置在读取期间变化");
                return result;
            }
            return LicenseRecoverLicenseState.unverified("当前 Java 代码、依赖或启动资源不匹配已审核的备用入口");
        }catch(NoSuchFileException|LinkedInput|Changed e){return LicenseRecoverLicenseState.unverified("Java 备用入口证据缺失、链接布局或读取期间变化");}
        catch(IOException|SecurityException e){return LicenseRecoverLicenseState.scanError("Java 备用入口只读读取失败: "+e.getClass().getSimpleName());}
        catch(Exception e){return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                "当前 Java 授权配置无法按已审核结构解析；没有推断其他授权分支");}
    }
    private static LicenseRecoverLicenseState evaluate(Profile p,Snapshot s,LocalEvaluator evaluator,long now,
            LicenseRecoverReadOnlyJavaMachineIdentity.Identity identity)throws Exception {
        String effective=p.layout==Layout.DEFAULT_LIBRARY?"WEB-INF/lib/config.xml":"config.xml";
        Item record=s.inputs.get(effective);
        String prefix="审核入口 "+p.id+"：";
        if(record==null)return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                prefix+"当前生效路径 "+effective+" 缺失；data/config.xml 与 classes/config.xml 不能替代它；"
                +(p.layout==Layout.DATABASE_PRODUCT_CHAIN?"启动还读取数据库产品信息并尝试产品映射、data/system.json 与通用产品后备；":"")
                +"网络/HASP 或其他注册后备尚未证明失败，整体授权仍待核");
        Document config=xml(record.bytes);
        String type=element(config,"reg","regType"),name=element(config,"reg","regName");
        if(name==null||name.isEmpty())return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.NO_RECORD,
                prefix+"已定位 "+effective+"，但没有本地授权载荷；其他授权分支仍待核");
        if(p.layout!=Layout.XMT_ANY_MODE || !"1".equals(type))
            return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    prefix+"已定位当前注册文件；该构建的本地、网络/HASP 与产品后备尚未全部闭合，不能依据载荷存在认定有效");
        Item system=s.inputs.get("WEB-INF/classes/config.xml"),properties=s.inputs.get("systemConfig.yml");
        if(system==null||properties==null)return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                prefix+"缺少确定构造产品与实际产品模式的启动配置");
        Document productConfig=xml(system.bytes);
        String product=element(productConfig,"SystemSoft","SoftVersionID");
        String configured=element(productConfig,"SystemSoft","regInfo");
        Properties props=properties(properties.bytes);
        for(String key:props.stringPropertyNames())if(!key.matches("global\\.system\\.[A-Za-z0-9._-]+"))
            return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED,
                    prefix+"系统属性包含未审核的启动配置键，不能复用当前入口证明");
        String version=props.getProperty("global.system.VersionID");
        if(!token(product)||!token(version)||configured==null||!configured.matches("[A-Za-z0-9._-]+(,[A-Za-z0-9._-]+)*"))
            return LicenseRecoverLicenseState.localObservation(LicenseRecoverLicenseState.LocalCondition.MALFORMED,
                    prefix+"产品选择配置缺失、重复或包含未审核形式，未调用注册组件");
        if(identity==null)identity=LicenseRecoverReadOnlyJavaMachineIdentity.current();
        LinkedHashSet<String> modes=new LinkedHashSet<String>(Arrays.asList(configured.split(",")));modes.add(version);
        Check local=null;
        for(String mode:modes) {
            Check check=evaluator==null?checkLocal(record.bytes,product,mode,identity,now)
                    :evaluator.check(record.bytes,product,mode,identity,now);
            local=check;
            if(check.nativePass && check.productPass && check.selected && !check.writes)
                return LicenseRecoverLicenseState.authorizationAcceptance(prefix
                        +"完整应用类、依赖及启动资源精确匹配；独立本地机器/期限/无限计次条件通过；"
                        +"实际配置产品集合的任一模式匹配，当前授权入口接受；未执行厂商代码或消耗计次；不证明厂商签发或 Patch 来源",
                        LicenseRecoverLicenseState.LocalCondition.SATISFIED);
        }
        return LicenseRecoverLicenseState.localObservation(local==null?LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED:local.condition,
                prefix+(local==null?"没有可审核的本地结果":local.reason)+"；未证明网络/HASP 后备与运行态，整体仍待核");
    }
    private static Check checkLocal(byte[] bytes,String product,String mode,LicenseRecoverReadOnlyJavaMachineIdentity.Identity identity,long now) {
        LicenseRecoverReadOnlyJavaLocalVerifier.LocalCheck result=
                LicenseRecoverReadOnlyJavaLocalVerifier.checkLocal(bytes,product,mode,identity,now);
        return new Check(result.condition,result.reason,result.nativeLocalPass,result.productPass,
                result.currentLocalSelected,result.writesRequired);
    }
    private static boolean token(String s){return s!=null&&s.matches("[A-Za-z0-9._-]+");}
    private static boolean valid(Profile p){return p!=null&&token(p.id)&&p.layout!=null&&p.anchor!=null
            &&p.anchor.matches("[A-Za-z0-9_$/]+\\.class")&&p.classCount>0&&p.libraryCount>0
            &&hashText(p.anchorHash)&&hashText(p.classes)&&hashText(p.libraries)&&hashText(p.wiring);}
    private static boolean hashText(String s){return s!=null&&s.matches("[0-9a-f]{64}");}
    private static Document xml(byte[] data)throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder b=f.newDocumentBuilder();b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        return b.parse(new ByteArrayInputStream(data));
    }
    private static final class MalformedConfig extends Exception { }
    private static String element(Document d,String section,String key)throws MalformedConfig {
        Node selected=null;NodeList sections=d.getDocumentElement().getChildNodes();
        for(int i=0;i<sections.getLength();i++)if(sections.item(i) instanceof Element&&section.equals(sections.item(i).getNodeName())){
            if(selected!=null)throw new MalformedConfig();selected=sections.item(i);}
        if(selected==null)return null;
        NodeList children=selected.getChildNodes();String out=null;boolean found=false;
        for(int i=0;i<children.getLength();i++){Node n=children.item(i);if(n instanceof Element&&key.equals(n.getNodeName())){
            if(found)throw new MalformedConfig();NodeList content=n.getChildNodes();
            for(int j=0;j<content.getLength();j++)if(content.item(j) instanceof Element)throw new MalformedConfig();
            out=n.getTextContent();found=true;}}
        return out;
    }
    private static Properties properties(byte[] bytes)throws IOException {
        Properties p=new Properties();p.load(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8));return p;
    }
    static final class Item {
        final String sha,identity;final byte[] bytes;
        Item(String sha,BasicFileAttributes a,byte[] bytes){this.sha=sha;this.bytes=bytes;
            identity=sha+":"+a.size()+":"+a.lastModifiedTime()+":"+a.fileKey();}
        Item(String sha,String identity,byte[] bytes){this.sha=sha;this.identity=identity;this.bytes=bytes;}
    }
    static final class Snapshot {
        final Map<String,Item> classes=new TreeMap<String,Item>(),libraries=new TreeMap<String,Item>(),wiring=new TreeMap<String,Item>(),inputs=new TreeMap<String,Item>();
        String identity(){StringBuilder b=new StringBuilder();for(Map<String,Item> group:Arrays.asList(classes,libraries,wiring,inputs))
            for(Map.Entry<String,Item> e:group.entrySet())b.append(e.getKey()).append('\0').append(e.getValue()==null?"missing":e.getValue().identity).append('\n');return sha(b.toString().getBytes(StandardCharsets.UTF_8));}
        Profile fixture(String id,String anchor,Layout layout){return new Profile(id,anchor,classes.get(anchor).sha,classes.size(),digest(classes),libraries.size(),digest(libraries),digest(wiring),layout);}
    }
    static Snapshot snapshot(Path input)throws IOException {
        Path root=input.toAbsolutePath().normalize(),classes=root.resolve("WEB-INF/classes"),lib=root.resolve("WEB-INF/lib");
        checkPath(root);checkPath(classes);checkPath(lib);Snapshot s=new Snapshot();
        try(java.util.stream.Stream<Path> tree=Files.walk(classes)) {
            Iterator<Path> files=tree.iterator();int count=0;
            while(files.hasNext()) {
                Path p=files.next();if(p.toString().contains(File.separator+".svn"+File.separator))continue;
                checkPath(p);if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;
                String rel=classes.relativize(p).toString().replace('\\','/'),lower=rel.toLowerCase(Locale.ROOT);
                if(lower.endsWith(".class")){if(++count>20000)throw new IOException("class-limit");s.classes.put(rel,read(p,true,false));}
                else if(!lower.equals("config.xml")) {
                    Item resource=read(p,false,lower.endsWith(".properties"));
                    if(lower.endsWith(".properties")) {
                        resource=new Item(sha(bootstrapContent(resource.bytes)),resource.identity,null);
                    }
                    s.wiring.put("classes/"+rel,resource);
                }
            }
        }catch(java.io.UncheckedIOException e){throw e.getCause();}
        try(DirectoryStream<Path> jars=Files.newDirectoryStream(lib)) {
            for(Path p:jars)if(p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                s.libraries.put(p.getFileName().toString(),read(p,false,false));
        }
        for(String rel:INPUTS)s.inputs.put(rel,optional(root.resolve(rel),false,true));
        Item web=s.inputs.get("WEB-INF/web.xml");
        s.wiring.put("web/WEB-INF/web.xml",web==null?new Item(sha("absent".getBytes(StandardCharsets.UTF_8)),"missing",null):web);
        Item tomcatWeb=optional(root.resolve("WEB-INF/tomcat-web.xml"),false,false);
        if(tomcatWeb!=null)s.wiring.put("web/WEB-INF/tomcat-web.xml",tomcatWeb);
        Path meta=root.resolve("META-INF");
        if(Files.exists(meta,LinkOption.NOFOLLOW_LINKS))try(java.util.stream.Stream<Path> tree=Files.walk(meta)) {
            Iterator<Path> files=tree.iterator();int count=0;
            while(files.hasNext()) {
                Path p=files.next();checkPath(p);if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;
                if(++count>20000)throw new IOException("resource-limit");
                s.wiring.put("META-INF/"+meta.relativize(p).toString().replace('\\','/'),read(p,false,false));
            }
        }
        return s;
    }
    private static final class Changed extends IOException { }
    private static final class LinkedInput extends IOException { }
    /** Preserve raw bootstrap bytes; exclude only simple, single-line datasource values. */
    private static byte[] bootstrapContent(byte[] bytes)throws IOException {
        StringBuilder out=new StringBuilder();boolean continued=false;
        for(String line:new String(bytes,StandardCharsets.ISO_8859_1).split("\\r\\n|\\r|\\n",-1)) {
            boolean excluded=false;
            for(String key:DB_VALUES)if(line.startsWith(key+"=")||line.startsWith(key+":")) {
                int slashes=0;for(int i=line.length()-1;i>=0&&line.charAt(i)=='\\';i--)slashes++;
                // If a credential spans lines, seal the entire raw resource. Never
                // discard text that may continue a preceding bootstrap property.
                if(continued||(slashes&1)!=0)return bytes;
                out.append(key).append("=<excluded-datasource-value>\n");excluded=true;break;
            }
            if(!excluded)out.append(line).append('\n');
            int slashes=0;for(int i=line.length()-1;i>=0&&line.charAt(i)=='\\';i--)slashes++;
            continued=(slashes&1)!=0;
        }
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
    private static void checkPath(Path p)throws IOException {
        Path absolute=p.toAbsolutePath().normalize(),cursor=absolute.getRoot();
        for(Path part:absolute){cursor=cursor.resolve(part);BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(a.isSymbolicLink()||a.isOther())throw new LinkedInput();}
    }
    private static Item optional(Path p,boolean isClass,boolean keep)throws IOException {
        try{return read(p,isClass,keep);}catch(NoSuchFileException missing){return null;}
    }
    private static Item read(Path p,boolean isClass,boolean keep)throws IOException {
        checkPath(p);BasicFileAttributes before=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        long limit=keep?4L*1024*1024:128L*1024*1024;
        if(!before.isRegularFile()||before.size()>limit)throw new IOException("unsupported-file");
        MessageDigest digest=md();ByteArrayOutputStream data=keep?new ByteArrayOutputStream():null;byte[] header=new byte[8];int headerSize=0;long size=0;
        try(InputStream in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){size+=n;if(size>limit)throw new IOException("file-limit");
                digest.update(buffer,0,n);if(data!=null)data.write(buffer,0,n);int h=Math.min(n,8-headerSize);if(h>0){System.arraycopy(buffer,0,header,headerSize,h);headerSize+=h;}}
        }
        BasicFileAttributes after=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))throw new Changed();
        if(isClass&&(headerSize<8||(header[0]&255)!=202||(header[1]&255)!=254||(header[2]&255)!=186||(header[3]&255)!=190))throw new IOException("damaged-class");
        return new Item(hex(digest.digest()),after,data==null?null:data.toByteArray());
    }
    private static String digest(Map<String,Item> group){StringBuilder b=new StringBuilder();for(Map.Entry<String,Item> e:group.entrySet())
        b.append(e.getValue().sha).append("  ").append(e.getKey()).append('\n');return sha(b.toString().getBytes(StandardCharsets.UTF_8));}
    private static MessageDigest md(){try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static String sha(byte[] bytes){return hex(md().digest(bytes));}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte b:bytes)
        out.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));return out.toString();}
}
