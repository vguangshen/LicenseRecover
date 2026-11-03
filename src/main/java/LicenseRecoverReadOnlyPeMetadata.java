import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Objects;
import java.util.Arrays;

/** Bounded PE/CLI metadata reader. Never loads, resolves or executes an assembly. */
public final class LicenseRecoverReadOnlyPeMetadata {
    private static final int LIMIT = 128 * 1024 * 1024;
    public final String runtimeVersion, assemblyVersion;
    public final boolean pe32Plus;

    private LicenseRecoverReadOnlyPeMetadata(String runtime, String assembly, boolean plus) {
        runtimeVersion = runtime; assemblyVersion = assembly; pe32Plus = plus;
    }

    public String describe() {
        return (pe32Plus ? "PE32+" : "PE32") + "/CLI; metadata-runtime=" + runtimeVersion
                + "; assembly-version=" + assemblyVersion + "; metadata-only";
    }

    public static LicenseRecoverReadOnlyPeMetadata read(Path path) throws IOException {
        return parse(readBytes(path));
    }

    private static byte[] readBytes(Path path) throws IOException {
        BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        long size = before.size();
        if(!before.isRegularFile())throw bad();
        if (size < 64 || size > LIMIT) throw bad();
        // Allocate exactly once. A growing file cannot enlarge the allocation;
        // both a short read and an extra byte revoke this metadata observation.
        byte[] bytes=new byte[(int)size];
        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            int total=0;
            while(total<bytes.length) {
                int n=in.read(bytes,total,bytes.length-total);
                if(n==-1)throw new IOException("pe-metadata-input-changed");total+=n;
            }
            if(in.read()!=-1)throw new IOException("pe-metadata-input-changed");
            BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!after.isRegularFile()||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())
                    ||!Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("pe-metadata-input-changed");
            return bytes;
        }
    }

    /** Version metadata describes a build, not the installed CLR or license validity. */
    public static LicenseRecoverReadOnlyPeMetadata parse(byte[] data) throws IOException {
        return layout(data).basic;
    }

    private static Layout layout(byte[] data) throws IOException {
        Reader r = new Reader(data);
        if (data.length < 64 || data.length > LIMIT || r.u16(0) != 0x5a4d) throw bad();
        long pe = r.u32(60);
        r.range(pe, 24);
        if (pe < 64 || r.u32(pe) != 0x4550) throw bad();
        int count = r.u16(pe + 6), optSize = r.u16(pe + 20);
        long opt = pe + 24;
        r.range(opt, optSize);
        int magic = r.u16(opt);
        boolean plus = magic == 0x20b;
        if (magic != 0x10b && !plus) throw bad();
        int directories = plus ? 112 : 96;
        if (count < 1 || count > 96 || optSize < directories + 15 * 8
                || r.u32(opt + directories - 4) < 15) throw bad();
        long headers = r.u32(opt + 60), sectionTable = opt + optSize;
        r.range(sectionTable, count * 40L);
        if (headers < sectionTable + count * 40L || headers > data.length) throw bad();
        long[][] sections = new long[count][4];
        for (int i = 0; i < count; i++) {
            long p = sectionTable + i * 40L;
            long virtualSize = r.u32(p + 8), va = r.u32(p + 12);
            long rawSize = r.u32(p + 16), raw = r.u32(p + 20);
            r.range(raw, rawSize);
            long span = Math.max(virtualSize, rawSize);
            if (va + span > 0x100000000L || rawSize > 0 && raw < headers) throw bad();
            sections[i] = new long[]{va, span, raw, rawSize};
            for (int j = 0; j < i; j++) {
                long[] other = sections[j];
                if (overlap(va, span, other[0], other[1])
                        || overlap(raw, rawSize, other[2], other[3])) throw bad();
            }
        }
        long cliRva = r.u32(opt + directories + 14 * 8L);
        long cliSize = r.u32(opt + directories + 14 * 8L + 4);
        if (cliRva == 0 || cliSize < 72) throw bad();
        long cli = map(sections, cliRva, cliSize);
        if (r.u32(cli) < 72 || r.u32(cli) > cliSize) throw bad();
        long metaSize = r.u32(cli + 12);
        long meta = map(sections, r.u32(cli + 8), metaSize);
        r.range(meta, metaSize);
        if (metaSize < 20 || r.u32(meta) != 0x424a5342) throw bad();
        long versionLength = r.u32(meta + 12);
        if (versionLength < 1 || versionLength > 256) throw bad();
        inside(meta, metaSize, meta + 16, versionLength);
        String runtime = r.ascii(meta + 16, versionLength, true);
        if (!runtime.matches("v[0-9]+(?:\\.[0-9]+){1,3}")) throw bad();
        long p = meta + align(16 + versionLength, 4);
        inside(meta, metaSize, p, 4);
        int streams = r.u16(p + 2); p += 4;
        if (streams < 1 || streams > 32) throw bad();
        Map<String, long[]> heaps = new HashMap<String, long[]>();
        for (int i = 0; i < streams; i++) {
            inside(meta, metaSize, p, 8);
            long offset = r.u32(p), size = r.u32(p + 4), start = p + 8, end = start;
            while (end < meta + metaSize && end - start < 32 && r.u8(end) != 0) end++;
            if (end == meta + metaSize || end - start == 32) throw bad();
            String name = r.ascii(start, end - start, false);
            if (heaps.put(name, new long[]{meta + offset, size}) != null) throw bad();
            inside(meta, metaSize, meta + offset, size);
            p += 8 + align(end - start + 1, 4);
        }
        // A stream must not alias its own metadata directory or another heap.
        for (long[] h : heaps.values()) {
            if (h[1] > 0 && h[0] < p) throw bad();
            for (long[] other : heaps.values())
                if (h != other && overlap(h[0], h[1], other[0], other[1])) throw bad();
        }
        long[] table = heaps.get("#~");
        if (table == null) table = heaps.get("#-");
        else if (heaps.containsKey("#-")) throw bad();
        if (table == null || !heaps.containsKey("#Strings") || !heaps.containsKey("#Blob")) throw bad();
        String assembly = assemblyVersion(r, table);
        return new Layout(r,heaps,table,new LicenseRecoverReadOnlyPeMetadata(runtime, assembly, plus),
                sections,cli,cliSize,meta,metaSize,r.u32(cli+16),r.u32(cli+64),r.u32(cli+68));
    }

    private static final class Layout {
        final Reader reader;final Map<String,long[]> heaps;final long[] table;
        final LicenseRecoverReadOnlyPeMetadata basic;
        final long[][] sections;final long cli,cliSize,metadata,metadataSize,cliFlags,nativeHeader,nativeHeaderSize;
        Layout(Reader r,Map<String,long[]> h,long[] t,LicenseRecoverReadOnlyPeMetadata b,long[][] sections,
                long cli,long cliSize,long metadata,long metadataSize,long flags,long nativeHeader,long nativeHeaderSize){
            reader=r;heaps=h;table=t;basic=b;this.sections=sections;this.cli=cli;this.cliSize=cliSize;
            this.metadata=metadata;this.metadataSize=metadataSize;cliFlags=flags;
            this.nativeHeader=nativeHeader;this.nativeHeaderSize=nativeHeaderSize;
        }
    }

    /** A complete on-disk method body shape, never proof of a Patch or runtime behavior. */
    public enum BooleanBodyShape { UNASSESSED, CONSTANT_FALSE, CONSTANT_TRUE, BYREF_NULL_FALSE }

    /**
     * Owned API declarations only. A shape match is a family candidate, never a
     * license, executable-compatibility, DES-semantic or Patch-origin proof.
     */
    public static final class ApiShape {
        public final String assemblyName,assemblyVersion,runtimeVersion;
        public final boolean upperRegistrationApi,lowerRegistrationApi,upperJsonInfoModel;
        private final Set<String> types;
        private final Map<String,MethodShape> methods;
        private final Map<String,String> fields;
        private final Layout layout;
        private ApiShape(ApiReader api)throws IOException {
            layout=api.layout;
            assemblyName=api.assemblyName();assemblyVersion=api.layout.basic.assemblyVersion;
            runtimeVersion=api.layout.basic.runtimeVersion;
            types=Collections.unmodifiableSet(new HashSet<String>(api.topTypes));
            methods=Collections.unmodifiableMap(new HashMap<String,MethodShape>(api.methods));
            fields=Collections.unmodifiableMap(new HashMap<String,String>(api.fields));
            upperRegistrationApi=registrationApi("ITMC.Regedit");
            lowerRegistrationApi=registrationApi("itmcRegedit");
            String info="ITMC.Regedit.RegeditInfo";
            upperJsonInfoModel=upperRegistrationApi&&property(info,"ProName","System.String")
                    &&property(info,"RegID","System.String")&&property(info,"RegStr","System.String")
                    &&property(info,"EndDate","System.DateTime")&&property(info,"TotalTimes","System.Int32");
        }
        public boolean hasType(String owner){return types.contains(owner);}
        public boolean hasMethod(String owner,String name,String result,String... parameters) {
            return methods.containsKey(methodKey(owner,name,result,parameters));
        }
        public boolean hasField(String owner,String name,String type){return type!=null&&type.equals(fields.get(owner+"\0"+name));}
        /** Whole constant bodies or exact local-reference null/false bodies; never a tail heuristic. */
        public BooleanBodyShape booleanBodyShape(String owner,String name,String... parameters)throws IOException {
            MethodShape method=methods.get(methodKey(owner,name,"System.Boolean",parameters));
            if(method==null||method.rva==0
                    ||(method.flags&(0x8|0x400|0x2000))!=0||(method.implFlags&~0x48)!=0
                    ||(layout.cliFlags&1)==0||(layout.cliFlags&0x10)!=0
                    ||layout.nativeHeader!=0||layout.nativeHeaderSize!=0)return BooleanBodyShape.UNASSESSED;
            if((layout.cliFlags&~0x3000bL)!=0)return BooleanBodyShape.UNASSESSED;
            for(String parameter:parameters)if(parameter.endsWith("&")) {
                String type=parameter.substring(0,parameter.length()-1);
                if(!types.contains(type)||!parameter.equals(method.localClassReferenceParameter))return BooleanBodyShape.UNASSESSED;
            }
            Reader r=layout.reader;long start=map(layout.sections,method.rva,1),headerSize,codeSize;int maxStack=8;
            int first=r.u8(start);
            if((first&3)==2){headerSize=1;codeSize=first>>>2;}
            else if((first&3)==3) {
                if((method.rva&3)!=0)return BooleanBodyShape.UNASSESSED;
                long header=map(layout.sections,method.rva,12);int flags=r.u16(header);
                headerSize=(flags>>>12)*4;codeSize=r.u32(header+4);maxStack=r.u16(header+2);
                if(headerSize<12)throw bad();
                // Unknown extensions, EH, locals and nonstandard fat headers remain unassessed.
                if(headerSize!=12||(flags&0x0fff)!=3&&(flags&0x0fff)!=0x13
                        ||maxStack<1||r.u32(header+8)!=0)return BooleanBodyShape.UNASSESSED;
            } else return BooleanBodyShape.UNASSESSED;
            long complete=headerSize+codeSize;
            map(layout.sections,method.rva,complete);r.range(start,complete);
            if(overlap(start,complete,layout.cli,layout.cliSize)
                    ||overlap(start,complete,layout.metadata,layout.metadataSize))throw bad();
            if(codeSize==5&&"CheckNet".equals(name)&&parameters.length==1&&maxStack>=2
                    &&parameters[0].equals(method.localClassReferenceParameter)
                    &&r.u8(start+headerSize)==(method.instance?0x03:0x02)
                    &&r.u8(start+headerSize+1)==0x14&&r.u8(start+headerSize+2)==0x51
                    &&r.u8(start+headerSize+3)==0x16&&r.u8(start+headerSize+4)==0x2a)
                return BooleanBodyShape.BYREF_NULL_FALSE;
            if(codeSize!=2||!method.instance)return BooleanBodyShape.UNASSESSED;
            int value=r.u8(start+headerSize),end=r.u8(start+headerSize+1);
            if(end!=0x2a)return BooleanBodyShape.UNASSESSED;
            return value==0x16?BooleanBodyShape.CONSTANT_FALSE:value==0x17
                    ?BooleanBodyShape.CONSTANT_TRUE:BooleanBodyShape.UNASSESSED;
        }
        private boolean property(String owner,String name,String type) {
            return hasField(owner,name,type)||(hasMethod(owner,"get_"+name,type)
                    &&hasMethod(owner,"set_"+name,"System.Void",type));
        }
        private boolean registrationApi(String ns) {
            String main=ns+".RegeditMain",info=ns+".RegeditInfo";
            if(!types.contains(main)||!types.contains(info))return false;
            for(String name:new String[]{"CheckReInfo","CheckLocalReg"}) {
                MethodShape m=methods.get(methodKey(main,name,"System.Boolean",new String[]{info+"&"}));
                if(m==null||!m.instance||!m.localTypes.contains(info))return false;
            }
            return true;
        }
        public String summary(){return "metadata-api-shape; upper="+upperRegistrationApi
                +"; lower="+lowerRegistrationApi+"; json-model="+upperJsonInfoModel+"; candidate-only";}
    }
    public static ApiShape readApiShape(Path path)throws IOException{return parseApiShape(readBytes(path));}
    public static ApiShape parseApiShape(byte[] data)throws IOException{return new ApiShape(new ApiReader(layout(data)));}
    private static String methodKey(String owner,String name,String result,String[] parameters) {
        return owner+"\0"+name+"\0"+result+"("+String.join(",",parameters)+")";
    }
    private static final class MethodShape {
        final boolean instance;final Set<String> localTypes;
        final long rva;final int flags,implFlags;final String localClassReferenceParameter;
        MethodShape(boolean instance,Set<String> refs,long rva,int flags,int implFlags,String localClassReferenceParameter){
            this.instance=instance;localTypes=new HashSet<String>(refs);this.rva=rva;this.flags=flags;this.implFlags=implFlags;
            this.localClassReferenceParameter=localClassReferenceParameter;
        }
    }
    private static final class ApiReader {
        final Layout layout;final Reader r;final long[] rows=new long[64],starts=new long[64];
        final int si,gi,bi;
        final ApiBudget budget=new ApiBudget();
        final String[] typeNames;final boolean[] nested;
        final Set<String> topTypes=new HashSet<String>();
        final Map<String,MethodShape> methods=new HashMap<String,MethodShape>();
        final Map<String,String> fields=new HashMap<String,String>();
        final Map<Long,TypeSpecValue> typeSpecs=new HashMap<Long,TypeSpecValue>();
        final Set<Long> activeTypeSpecs=new HashSet<Long>();
        final java.util.List<TypeSpecFrame> typeSpecPath=new java.util.ArrayList<TypeSpecFrame>();
        ApiReader(Layout layout)throws IOException {
            this.layout=layout;r=layout.reader;long origin=layout.table[0],size=layout.table[1];
            int flags=r.u8(origin+6);if((flags&~7)!=0)throw bad();
            si=(flags&1)!=0?4:2;gi=(flags&2)!=0?4:2;bi=(flags&4)!=0?4:2;
            long valid=r.i64(origin+8),p=origin+24;
            for(int i=0;i<64;i++)if((valid&(1L<<i))!=0){inside(origin,size,p,4);rows[i]=r.u32(p);p+=4;}
            // Pointer tables require separate ownership indirection; never guess owners.
            if(rows[3]!=0||rows[5]!=0)throw new IOException("unsupported-cli-api-pointer-table");
            for(int i=45;i<64;i++)if(rows[i]!=0)throw new IOException("unsupported-cli-api-table");
            for(int i=0;i<=44;i++) {
                if(rows[i]>100000)throw bad();budget.nodes(rows[i]);starts[i]=p;
                long bytes=rows[i]*rowSize(i,rows,si,gi,bi);
                inside(origin,size,p,bytes);p+=bytes;
            }
            heapByte("#Strings",0);heapByte("#Blob",0);
            if(heapByte("#Strings",0)!=0||heapByte("#Blob",0)!=0)throw bad();
            typeNames=new String[(int)rows[2]+1];nested=new boolean[typeNames.length];
            long[] fieldStart=new long[typeNames.length+1],methodStart=new long[typeNames.length+1];
            long previousParam=1;
            for(int id=1;id<typeNames.length;id++) {
                long row=offset(2,id),attributes=r.u32(row);
                String name=string(index(row+4,si)),namespace=string(index(row+4+si,si));
                if(name.isEmpty())throw bad();nested[id]=(attributes&7)>1;
                String full=namespace.isEmpty()?name:compose(namespace,".",name);
                typeNames[id]=nested[id]?"<nested#"+id+">":full;
                if(!nested[id]&&!topTypes.add(full))throw new IOException("ambiguous-cli-api-type");
                long list=row+4+2L*si+coded(rows,2,2,1,27);
                fieldStart[id]=index(list,ix(rows,4));methodStart[id]=index(list+ix(rows,4),ix(rows,6));
                if(fieldStart[id]<1||fieldStart[id]>rows[4]+1||methodStart[id]<1||methodStart[id]>rows[6]+1)throw bad();
                if(id>1&&(fieldStart[id]<fieldStart[id-1]||methodStart[id]<methodStart[id-1]))throw bad();
            }
            if(typeNames.length==1&&(rows[4]!=0||rows[6]!=0))throw bad();
            if(typeNames.length>1&&(fieldStart[1]!=1||methodStart[1]!=1))throw bad();
            fieldStart[typeNames.length]=rows[4]+1;methodStart[typeNames.length]=rows[6]+1;
            for(int id=1;id<typeNames.length;id++) {
                String owner=typeNames[id];Set<String> rawMethods=new HashSet<String>();
                for(long fid=fieldStart[id];fid<fieldStart[id+1];fid++) {
                    long row=offset(4,fid);String name=string(index(row+2,si));
                    Blob signature=blob(index(row+2+si,bi));
                    if(signature.u8()!=6)throw bad();String type=signature.type(0);signature.end();
                    if("System.Void".equals(type)||"System.Void&".equals(type))throw bad();
                    if(name.isEmpty()||fields.put(compose(owner,"\0",name),type)!=null)throw new IOException("ambiguous-cli-api-field");
                }
                for(long mid=methodStart[id];mid<methodStart[id+1];mid++) {
                    long row=offset(6,mid);String name=string(index(row+8,si));
                    Blob signature=blob(index(row+8+si,bi));
                    int encodedLength=((signature.limit-signature.p+2)/3)*4;
                    budget.result(encodedLength);budget.text(encodedLength);budget.work(signature.limit-signature.p);
                    byte[] raw=Arrays.copyOfRange(r.data,signature.p,signature.limit);
                    if(name.isEmpty()||!rawMethods.add(compose(name,"\0",signatureKey(raw))))throw new IOException("ambiguous-cli-api-method");
                    long parameter=index(row+8+si+bi,ix(rows,8));
                    if(parameter<previousParam||parameter<1||parameter>rows[8]+1)throw bad();previousParam=parameter;
                    MethodSignature parsed=signature.method(0);signature.end();
                    boolean isStatic=(r.u16(row+6)&0x10)!=0;
                    if(parsed.instance==isStatic)throw bad();
                    if(parsed.generic||parsed.convention!=0)continue;
                    String key=signatureText(owner+"\0"+name+"\0",parsed);
                    budget.nodes(signature.localTypes.size());
                    String localReference=localClassReference(row,raw,parsed);
                    if(methods.put(key,new MethodShape(parsed.instance,signature.localTypes,r.u32(row),r.u16(row+6),r.u16(row+4),localReference))!=null)
                        throw new IOException("ambiguous-cli-api-method");
                }
            }
        }
        /** Exact bool(class TypeDef&) signature, not a same-name external or value-type reference. */
        private String localClassReference(long row,byte[] raw,MethodSignature method)throws IOException {
            if(raw.length<6||raw[0]!=(method.instance?0x20:0)||raw[1]!=1||raw[2]!=2||raw[3]!=0x10||raw[4]!=0x12)return null;
            Blob strict=blob(index(row+8+si,bi));strict.p+=5;long token=strict.number();strict.end();
            if((token&3)!=0)return null;long id=token>>>2;
            if(id<1||id>=typeNames.length||nested[(int)id])return null;
            long definition=offset(2,id);if((r.u32(definition)&0x20)!=0)return null;
            long parent=index(definition+4+2L*si,coded(rows,2,2,1,27));
            if(parent!=0) {
                String base=type(parent,0,new HashSet<String>());
                if("System.ValueType".equals(base)||"System.Enum".equals(base))return null;
            }
            return compose(typeNames[(int)id],"&");
        }
        private static String signatureKey(byte[] b){return java.util.Base64.getEncoder().encodeToString(b);}
        String assemblyName()throws IOException {
            if(rows[32]==0)return "unknown-netmodule";
            if(rows[32]!=1)throw bad();String name=string(index(starts[32]+16+bi,si));
            // This is observed metadata, not a file name or an executable binding.
            if(name.isEmpty()||name.length()>1024)throw bad();
            for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))throw bad();
            return name;
        }
        long offset(int table,long id)throws IOException {
            if(id<1||id>rows[table])throw bad();return starts[table]+(id-1)*rowSize(table,rows,si,gi,bi);
        }
        long index(long p,int size)throws IOException{return size==2?r.u16(p):r.u32(p);}
        int heapByte(String heap,long index)throws IOException {
            long[] h=layout.heaps.get(heap);if(h==null||index<0||index>=h[1])throw bad();return r.u8(h[0]+index);
        }
        String string(long index)throws IOException {
            long[] h=layout.heaps.get("#Strings");if(index<0||index>=h[1])throw bad();
            int start=(int)(h[0]+index),end=start;
            while(end<h[0]+h[1]&&end-start<4096&&r.data[end]!=0)end++;
            budget.work(end-start+1);budget.text(end-start);
            if(end>=h[0]+h[1]||end-start==4096)throw bad();
            try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(r.data,start,end-start)).toString();}
            catch(CharacterCodingException e){throw bad();}
        }
        Blob blob(long index)throws IOException {
            long[] h=layout.heaps.get("#Blob");if(index<1||index>=h[1])throw bad();
            Blob cursor=new Blob((int)(h[0]+index),(int)(h[0]+h[1]));long length=cursor.number();
            if(length>65536||length>cursor.limit-cursor.p)throw bad();
            return new Blob(cursor.p,cursor.p+(int)length);
        }
        String type(long coded,int depth,Set<String> locals)throws IOException {
            checkDepth(depth);int tag=(int)coded&3;long id=coded>>>2;
            if(tag==0){if(id<1||id>=typeNames.length)throw bad();if(!nested[(int)id])addLocal(locals,typeNames[(int)id]);return typeNames[(int)id];}
            if(tag==1) {
                long row=offset(1,id);String name=string(index(row+coded(rows,2,0,26,35,1),si));
                String ns=string(index(row+coded(rows,2,0,26,35,1)+si,si));if(name.isEmpty())throw bad();
                long scope=index(row,coded(rows,2,0,26,35,1));
                if(scope!=0){int table=new int[]{0,26,35,1}[(int)scope&3];if((scope>>>2)>rows[table])throw bad();}
                if((scope&3)==3)return compose(type(((scope>>>2)<<2)|1,depth+1,locals),"+",name);
                return ns.isEmpty()?name:compose(ns,".",name);
            }
            if(tag==2) {
                long row=offset(27,id);TypeSpecValue cached=typeSpecs.get(id);
                if(cached!=null) {
                    // A cache hit still consumes the full logical depth and reference work.
                    checkDepth(depth+cached.depthCost);mergeLocals(locals,cached.localTypes);return cached.type;
                }
                if(!activeTypeSpecs.add(id))throw new IOException("cyclic-cli-api-typespec");
                TypeSpecFrame frame=new TypeSpecFrame(depth);typeSpecPath.add(frame);
                try {
                    Blob b=blob(index(row,bi));String t=b.type(depth+1);b.end();
                    budget.nodes(1L+b.localTypes.size());
                    typeSpecs.put(id,new TypeSpecValue(t,b.localTypes,frame.maximumDepth-depth));
                    mergeLocals(locals,b.localTypes);return t;
                }finally{typeSpecPath.remove(typeSpecPath.size()-1);activeTypeSpecs.remove(id);}
            }
            throw bad();
        }
        private void checkDepth(int depth)throws IOException {
            budget.work(1L+typeSpecPath.size());if(depth>24)throw bad();
            for(TypeSpecFrame frame:typeSpecPath)frame.maximumDepth=Math.max(frame.maximumDepth,depth);
        }
        private void addLocal(Set<String> locals,String type)throws IOException {
            budget.work(1);if(locals.add(type))budget.nodes(1);
        }
        private void mergeLocals(Set<String> locals,Set<String> refs)throws IOException {
            for(String ref:refs)addLocal(locals,ref);
        }
        private void append(StringBuilder b,String text)throws IOException {
            budget.work(1);budget.result((long)b.length()+text.length());budget.text(text.length());b.append(text);
        }
        private String finish(StringBuilder b)throws IOException {budget.text(b.length());return b.toString();}
        private String compose(String... parts)throws IOException {
            StringBuilder b=new StringBuilder();for(String part:parts)append(b,part);return finish(b);
        }
        private String signatureText(String prefix,MethodSignature signature)throws IOException {
            StringBuilder b=new StringBuilder();append(b,prefix);append(b,signature.result);append(b,"(");
            for(int i=0;i<signature.parameters.length;i++){if(i>0)append(b,",");append(b,signature.parameters[i]);}
            append(b,")");return finish(b);
        }
        private final class Blob {
            int p;final int limit;final Set<String> localTypes=new HashSet<String>();
            Blob(int start,int limit){p=start;this.limit=limit;}
            int u8()throws IOException{budget.work(1);if(p>=limit)throw bad();return r.u8(p++);}
            long number()throws IOException {
                int first=u8();if(first<128)return first;
                if(first<192){long v=((first&63)<<8)|u8();if(v<128)throw bad();return v;}
                if(first<224){long v=((long)(first&31)<<24)|((long)u8()<<16)|(u8()<<8)|u8();if(v<16384)throw bad();return v;}
                throw bad();
            }
            void end()throws IOException{if(p!=limit)throw bad();}
            MethodSignature method(int depth)throws IOException {
                checkDepth(depth);int call=u8(),convention=call&15;
                if((call&128)!=0||convention>5||((call&64)!=0&&(call&32)==0))throw bad();boolean generic=(call&16)!=0;
                if(generic&&number()>64)throw bad();long count=number();if(count>256)throw bad();
                budget.nodes(count);String result=type(depth+1);String[] args=new String[(int)count];
                boolean sentinel=false;
                for(int i=0;i<args.length;i++) {
                    if(p<limit&&r.u8(p)==0x41){if(convention!=5||sentinel)throw bad();p++;sentinel=true;}
                    args[i]=type(depth+1);if("System.Void".equals(args[i]))throw bad();
                }
                return new MethodSignature((call&32)!=0,generic,convention,result,args);
            }
            String type(int depth)throws IOException {
                checkDepth(depth);int element=u8();
                switch(element) {
                case 1:return "System.Void";case 2:return "System.Boolean";case 3:return "System.Char";
                case 4:return "System.SByte";case 5:return "System.Byte";case 6:return "System.Int16";case 7:return "System.UInt16";
                case 8:return "System.Int32";case 9:return "System.UInt32";case 10:return "System.Int64";case 11:return "System.UInt64";
                case 12:return "System.Single";case 13:return "System.Double";case 14:return "System.String";
                case 15:return compose(type(depth+1),"*");case 16:return compose(type(depth+1),"&");
                case 17:case 18:return ApiReader.this.type(number(),depth+1,localTypes);
                case 19:return compose("!",Long.toString(number()));case 30:return compose("!!",Long.toString(number()));
                case 20:{String item=type(depth+1);long rank=number(),sizes=number();if(rank>32||sizes>rank)throw bad();for(int i=0;i<sizes;i++)number();long bounds=number();if(bounds>rank)throw bad();for(int i=0;i<bounds;i++)number();return compose(item,"[rank=",Long.toString(rank),"]");}
                case 21:{int kind=u8();if(kind!=17&&kind!=18)throw bad();String owner=ApiReader.this.type(number(),depth+1,localTypes);long count=number();if(count>64)throw bad();StringBuilder b=new StringBuilder();append(b,owner);append(b,"<");for(int i=0;i<count;i++){if(i>0)append(b,",");append(b,type(depth+1));}append(b,">");return finish(b);}
                case 22:return "System.TypedReference";case 24:return "System.IntPtr";case 25:return "System.UIntPtr";
                case 27:return signatureText("methodptr:",method(depth+1));
                case 28:return "System.Object";case 29:return compose(type(depth+1),"[]");
                case 31:case 32:{String modifier=ApiReader.this.type(number(),depth+1,localTypes);return compose(element==31?"modreq(":"modopt(",modifier,")",type(depth+1));}
                case 69:return compose("pinned ",type(depth+1));
                default:throw new IOException("invalid-or-unsupported-cli-api-signature");
                }
            }
        }
    }
    /** Limits are shared by all rows, heap reads and signature expansions in one module. */
    private static final class ApiBudget {
        private long nodes,work,text;
        void nodes(long count)throws IOException {nodes+=count;if(nodes>200000)throw new IOException("cli-api-node-budget");}
        void work(long count)throws IOException {work+=count;if(work>4000000)throw new IOException("cli-api-work-budget");}
        void text(long count)throws IOException {text+=count;if(text>8L*1024*1024)throw new IOException("cli-api-text-budget");}
        void result(long length)throws IOException {if(length>16384)throw new IOException("cli-api-result-too-long");}
    }
    private static final class TypeSpecFrame {
        int maximumDepth;TypeSpecFrame(int depth){maximumDepth=depth;}
    }
    private static final class TypeSpecValue {
        final String type;final Set<String> localTypes;final int depthCost;
        TypeSpecValue(String type,Set<String> refs,int depthCost){this.type=type;localTypes=new HashSet<String>(refs);this.depthCost=depthCost;}
    }
    private static final class MethodSignature {
        final boolean instance,generic;final int convention;final String result;final String[] parameters;
        MethodSignature(boolean i,boolean g,int c,String r,String[] p){instance=i;generic=g;convention=c;result=r;parameters=p;}
    }

    private static String assemblyVersion(Reader r, long[] table) throws IOException {
        long origin = table[0], size = table[1];
        inside(origin, size, origin, 24);
        int flags = r.u8(origin + 6);
        int si = (flags & 1) != 0 ? 4 : 2, gi = (flags & 2) != 0 ? 4 : 2;
        int bi = (flags & 4) != 0 ? 4 : 2;
        long valid = r.i64(origin + 8), p = origin + 24;
        long[] rows = new long[64];
        for (int i = 0; i < 64; i++) if ((valid & (1L << i)) != 0) {
            inside(origin, size, p, 4); rows[i] = r.u32(p); p += 4;
        }
        if (rows[32] == 0) return "unknown-netmodule";
        if (rows[32] != 1) throw bad();
        for (int i = 0; i < 32; i++) {
            long bytes = rows[i] * rowSize(i, rows, si, gi, bi);
            inside(origin, size, p, bytes); p += bytes;
        }
        inside(origin, size, p, 16L + bi + 2 * si);
        return r.u16(p + 4) + "." + r.u16(p + 6) + "." + r.u16(p + 8) + "." + r.u16(p + 10);
    }

    private static int ix(long[] rows, int table) { return rows[table] >= 65536 ? 4 : 2; }
    private static int coded(long[] rows, int bits, int... tables) {
        long limit = 1L << (16 - bits);
        for (int t : tables) if (rows[t] >= limit) return 4;
        return 2;
    }
    private static int rowSize(int t, long[] n, int s, int g, int b) throws IOException {
        int type = coded(n, 2, 2, 1, 27), method = ix(n, 6), field = ix(n, 4);
        switch (t) {
        case 0: return 2 + s + 3 * g;
        case 1: return coded(n, 2, 0, 26, 35, 1) + 2 * s;
        case 2: return 4 + 2 * s + type + field + method;
        case 3: return field;
        case 4: return 2 + s + b;
        case 5: return method;
        case 6: return 8 + s + b + ix(n, 8);
        case 7: return ix(n, 8);
        case 8: return 4 + s;
        case 9: return ix(n, 2) + type;
        case 10: return coded(n, 3, 2, 1, 26, 6, 27) + s + b;
        case 11: return 2 + coded(n, 2, 4, 8, 23) + b;
        case 12: return coded(n, 5, 6, 4, 1, 2, 8, 9, 10, 0, 14, 23, 20, 17, 26, 27, 32, 35, 38, 39, 40, 42, 44, 43)
                + coded(n, 3, 6, 10) + b;
        case 13: return coded(n, 1, 4, 8) + b;
        case 14: return 2 + coded(n, 2, 2, 6, 32) + b;
        case 15: return 6 + ix(n, 2);
        case 16: return 4 + field;
        case 17: return b;
        case 18: return ix(n, 2) + ix(n, 20);
        case 19: return ix(n, 20);
        case 20: return 2 + s + type;
        case 21: return ix(n, 2) + ix(n, 23);
        case 22: return ix(n, 23);
        case 23: return 2 + s + b;
        case 24: return 2 + method + coded(n, 1, 20, 23);
        case 25: return ix(n, 2) + 2 * coded(n, 1, 6, 10);
        case 26: return s;
        case 27: return b;
        case 28: return 2 + coded(n, 1, 4, 6) + s + ix(n, 26);
        case 29: return 4 + field;
        case 30: return 8;
        case 31: return 4;
        case 32: return 16 + b + 2 * s;
        case 33: return 4;
        case 34: return 12;
        case 35: return 12 + 2 * b + 2 * s;
        case 36: return 4 + ix(n,35);
        case 37: return 12 + ix(n,35);
        case 38: return 4 + s + b;
        case 39: return 8 + 2 * s + coded(n,2,38,35,39);
        case 40: return 8 + s + coded(n,2,38,35,39);
        case 41: return 2 * ix(n,2);
        case 42: return 4 + coded(n,1,2,6) + s;
        case 43: return coded(n,1,6,10) + b;
        case 44: return ix(n,42) + type;
        default: throw bad();
        }
    }

    private static boolean overlap(long a, long length, long b, long otherLength) {
        return length > 0 && otherLength > 0 && a < b + otherLength && b < a + length;
    }
    private static long map(long[][] sections, long rva, long size) throws IOException {
        if (rva == 0 || size == 0 || rva + size > 0x100000000L) throw bad();
        for (long[] s : sections) if (rva >= s[0] && rva - s[0] <= s[3]
                && size <= s[3] - (rva - s[0])) return s[2] + rva - s[0];
        throw bad();
    }
    private static long align(long v, int unit) { return (v + unit - 1) / unit * unit; }
    private static void inside(long origin, long size, long p, long n) throws IOException {
        if (p < origin || n < 0 || p - origin > size || n > size - (p - origin)) throw bad();
    }
    private static IOException bad() { return new IOException("invalid-or-unsupported-pe-cli-metadata"); }

    private static final class Reader {
        final byte[] data;
        Reader(byte[] data) throws IOException { if (data == null) throw bad(); this.data = data; }
        void range(long p, long n) throws IOException { inside(0, data.length, p, n); }
        int u8(long p) throws IOException { range(p, 1); return data[(int)p] & 255; }
        int u16(long p) throws IOException { return u8(p) | u8(p + 1) << 8; }
        long u32(long p) throws IOException { return (long)u16(p) | (long)u16(p + 2) << 16; }
        long i64(long p) throws IOException { return u32(p) | u32(p + 4) << 32; }
        String ascii(long p, long length, boolean padded) throws IOException {
            range(p, length); int end = (int)(p + length), stop = end;
            for (int i = (int)p; i < end; i++) {
                int c = data[i] & 255;
                if (padded && c == 0) { stop = Math.min(stop, i); continue; }
                if (c < 32 || c > 126 || stop < end) throw bad();
            }
            if (stop == p) throw bad();
            return new String(data, (int)p, stop - (int)p, StandardCharsets.US_ASCII);
        }
    }
}
