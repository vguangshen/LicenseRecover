import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Synthetic PE metadata only; no vendor binaries/configuration are fixtures. */
public final class LicenseRecoverReadOnlyPeMetadataTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (boolean plus : new boolean[]{false, true}) {
            byte[] valid = fixture(plus);
            LicenseRecoverReadOnlyPeMetadata m = LicenseRecoverReadOnlyPeMetadata.parse(valid);
            check(m.pe32Plus == plus && "v4.0.30319".equals(m.runtimeVersion), "PE kind and CLR metadata runtime");
            check("2.7.19.4".equals(m.assemblyVersion), "Assembly version read from table 32");
            check(m.describe().contains("metadata-only"), "No license or installed runtime assertion");
            invalid(Arrays.copyOf(valid, 100), "truncated PE");
            byte[] b = valid.clone(); put32(b, 60, 0xfffffff0L); invalid(b, "PE offset overflow");
            b = valid.clone(); put16(b, 0x98, 0x123); invalid(b, "invalid optional header");
            b = valid.clone(); put32(b, 0x98 + (plus ? 112 : 96) + 14 * 8, 0); invalid(b, "native PE is not CLI");
            b = valid.clone(); put32(b, 0x20c, 0xfffffff0L); invalid(b, "out of range metadata size");
            b = valid.clone(); put32(b, 0x280, 0); invalid(b, "invalid metadata signature");
            b = valid.clone(); put32(b, 0x28c, 0xfffffff0L); invalid(b, "oversized metadata runtime");
            b = valid.clone(); b[0x290] = '<'; invalid(b, "runtime is canonical metadata only");
            b = valid.clone(); b[0x29b] = 'a'; invalid(b, "nonzero runtime padding");
            b = valid.clone(); put32(b, 0x2ac, 0x1000); invalid(b, "heap outside metadata");
            b = valid.clone(); put32(b, 0x2a0, 8); invalid(b, "heap aliases directory");
            b = valid.clone(); put32(b, 0x2a4, 4); invalid(b, "truncated table header");
            b = valid.clone(); put32(b, 0x338, 2); invalid(b, "multiple Assembly rows");
            b = valid.clone(); put32(b, 0x338, 0); check("unknown-netmodule".equals(
                    LicenseRecoverReadOnlyPeMetadata.parse(b).assemblyVersion), "netmodule is explicit unknown assembly version");
        }
        invalid(null, "null input");
        apiChecks();
        apiBudgetChecks();
        booleanBodyChecks();
        System.out.println("LicenseRecoverReadOnlyPeMetadataTest: " + checks + " checks passed");
    }
    private static void apiChecks()throws Exception {
        for(boolean plus:new boolean[]{false,true}) {
            ApiFixture fixture=apiFixture(plus,"ITMC.Regedit",false,false);
            LicenseRecoverReadOnlyPeMetadata.ApiShape shape=LicenseRecoverReadOnlyPeMetadata.parseApiShape(fixture.data);
            check("Synthetic.Registry".equals(shape.assemblyName)&&"2.7.19.4".equals(shape.assemblyVersion),"actual Assembly row identity");
            check(shape.upperRegistrationApi&&shape.upperJsonInfoModel&&!shape.lowerRegistrationApi,"owned modern registration model is a structural candidate");
            check(shape.hasMethod("ITMC.Regedit.RegeditMain","CheckReInfo","System.Boolean","ITMC.Regedit.RegeditInfo&"),"method signature is resolved through local TypeDef");
            check(!shape.hasMethod("ITMC.Regedit.RegeditMain","CheckReInfo","System.String","ITMC.Regedit.RegeditInfo&"),"return type matters");
            check(shape.hasField("ITMC.Regedit.RegeditInfo","RegID","System.String")&&!shape.hasField("ITMC.Regedit.RegeditMain","RegID","System.String"),"field owner matters");
            check(shape.summary().endsWith("candidate-only"),"metadata never asserts a license or reviewed semantics");
            LicenseRecoverReadOnlyPeMetadata.ApiShape wrong=LicenseRecoverReadOnlyPeMetadata.parseApiShape(apiFixture(plus,"ITMC.Regedit",true,false).data);
            check(wrong.hasType("ITMC.Regedit.RegeditMain")&&!wrong.upperRegistrationApi,"same method names on wrong TypeDef do not form the API");
            check(wrong.hasMethod("ITMC.Regedit.RegeditInfo","CheckLocalReg","System.Boolean","ITMC.Regedit.RegeditInfo&"),"wrong-owner method remains correctly owned");
            check(!LicenseRecoverReadOnlyPeMetadata.parseApiShape(apiFixture(plus,"ITMC.Regedit",false,true).data).upperRegistrationApi,"external same-name TypeRef cannot substitute for local RegeditInfo");
            byte[] b=fixture.data.clone();b[fixture.stringStart+fixture.namespaceIndex]=(byte)0xff;invalidApi(b,"malformed UTF8 string heap");
            b=fixture.data.clone();b[fixture.blobStart+1]=127;invalidApi(b,"signature length exceeds blob heap");
            b=fixture.data.clone();put16(b,fixture.methodTable+8,fixture.localMethodName);invalidApi(b,"duplicate owned signature is ambiguous");
            b=fixture.data.clone();put16(b,fixture.typeTable+14+12,4);invalidApi(b,"method owner range outside table");
            b=fixture.data.clone();b[fixture.fieldSignatures[4]+1]=9;
            shape=LicenseRecoverReadOnlyPeMetadata.parseApiShape(b);
            check(shape.upperRegistrationApi&&!shape.upperJsonInfoModel,"changed model type cannot reuse a typed schema");
            Path path=Files.createTempFile("lr-synthetic-cli-api-",".dll");
            try {
                Files.write(path,fixture.data);check(LicenseRecoverReadOnlyPeMetadata.readApiShape(path).upperRegistrationApi,"file API is bounded metadata-only");
                check("2.7.19.4".equals(LicenseRecoverReadOnlyPeMetadata.read(path).assemblyVersion),"basic file reader observes the exact bounded PE bytes");
                Files.write(path,apiFixture(plus,"Other.Namespace",false,false).data);
                check(!LicenseRecoverReadOnlyPeMetadata.readApiShape(path).upperRegistrationApi,"file changes cannot reuse stale shape");
            }finally{Files.delete(path);}
        }
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(apiFixture(false,"itmcRegedit",false,false).data).lowerRegistrationApi,"lower interface stays distinct from the upper JSON model");
        ApiFixture noise=apiFixture(false,"Other.Namespace",false,false);
        // Actual #US content is unreferenced by any TypeDef or MethodDef.
        put16(noise.data,0x29e,4);put32(noise.data,0x2d0,0x700);put32(noise.data,0x2d4,256);ascii(noise.data,0x2d8,"#US");
        byte[] text="ITMC.Regedit.RegeditMain CheckReInfo CheckLocalReg RegeditInfo ProName RegID RegStr".getBytes(StandardCharsets.UTF_16LE);
        int length=text.length+1;noise.data[0x980]=0;noise.data[0x981]=(byte)(0x80|(length>>>8));noise.data[0x982]=(byte)length;
        System.arraycopy(text,0,noise.data,0x983,text.length);
        check(!LicenseRecoverReadOnlyPeMetadata.parseApiShape(noise.data).upperRegistrationApi,"matching user-string tokens alone never establish an owned API");
        String unicodeName="研究 团队.授权组件";
        check(unicodeName.equals(LicenseRecoverReadOnlyPeMetadata.parseApiShape(assemblyFixture(unicodeName)).assemblyName),
                "Assembly table identity preserves normal Unicode and spaces");
        char[] maximumName=new char[1024];Arrays.fill(maximumName,'A');
        check(new String(maximumName).equals(LicenseRecoverReadOnlyPeMetadata.parseApiShape(assemblyFixture(new String(maximumName))).assemblyName),
                "bounded observed assembly names are not limited to 128 ASCII characters");
        invalidApi(assemblyFixture(new String(maximumName)+"A"),"observed assembly identity has a 1024-character bound");
        invalidApi(assemblyFixture(""),"empty Assembly name");
        invalidApi(assemblyFixture("Registry\nControl"),"control characters are rejected from observed Assembly identity");
    }
    private static final class ApiFixture {
        byte[] data;int stringStart,namespaceIndex,blobStart,methodTable,typeTable,localMethodName;int[] fieldSignatures;
    }
    private static int string(ByteArrayOutputStream heap,String value)throws Exception {
        int index=heap.size();heap.write(value.getBytes(StandardCharsets.UTF_8));heap.write(0);return index;
    }
    private static int blob(ByteArrayOutputStream heap,int... bytes) {
        int index=heap.size();compressed(heap,bytes.length);for(int b:bytes)heap.write(b);return index;
    }
    private static void compressed(ByteArrayOutputStream b,int value) {
        if(value<128)b.write(value);
        else if(value<16384){b.write(0x80|(value>>>8));b.write(value&255);}
        else{b.write(0xc0|(value>>>24));b.write((value>>>16)&255);b.write((value>>>8)&255);b.write(value&255);}
    }
    private static void u16(ByteArrayOutputStream b,int value){b.write(value&255);b.write((value>>>8)&255);}
    private static void u32(ByteArrayOutputStream b,int value){u16(b,value);u16(b,value>>>16);}
    static byte[] apiFixtureBytes(boolean plus,String ns,boolean wrongOwner,boolean externalInfo)throws Exception {
        return apiFixture(plus,ns,wrongOwner,externalInfo).data.clone();
    }
    /** Owned synthetic methods with independently constructed PE32/PE32+ CIL bodies. */
    static byte[] booleanBodyFixture(boolean plus,String ns,boolean wrongOwner,String name,boolean byref,boolean fat,int... code)throws Exception {
        return booleanBodyFixture(plus,ns,wrongOwner,name,byref,fat,false,code);
    }
    static byte[] booleanBodyFixture(boolean plus,String ns,boolean wrongOwner,String name,boolean byref,boolean fat,boolean staticMethod,int... code)throws Exception {
        ApiFixture f=apiFixture(plus,ns,wrongOwner,false);
        installBooleanBody(f,fat,code);
        int nameIndex=ByteBuffer.wrap(f.data).order(ByteOrder.LITTLE_ENDIAN).getShort(f.methodTable+8)&0xffff;
        byte[] methodName=(name+"\0").getBytes(StandardCharsets.UTF_8);
        if(methodName.length>12)throw new IllegalArgumentException("fixture method name");
        System.arraycopy(methodName,0,f.data,f.stringStart+nameIndex,methodName.length);
        int[] signature=byref?new int[]{0x20,1,2,0x10,0x12,12}:new int[]{0x20,0,2};
        if(staticMethod){signature[0]=0;put16(f.data,f.methodTable+6,0x16);}
        f.data[f.blobStart+64]=(byte)signature.length;
        for(int i=0;i<signature.length;i++)f.data[f.blobStart+65+i]=(byte)signature[i];
        put32(f.data,0x2c4,65+signature.length);put16(f.data,f.methodTable+10,64);
        return f.data;
    }
    static byte[] externalRefBodyFixture()throws Exception {
        ApiFixture f=apiFixture(false,"ITMC.Regedit",false,true);installBooleanBody(f,false,0x03,0x14,0x51,0x16,0x2a);
        int name=ByteBuffer.wrap(f.data).order(ByteOrder.LITTLE_ENDIAN).getShort(f.methodTable+8)&0xffff;
        byte[] bytes="CheckNet\0".getBytes(StandardCharsets.UTF_8);System.arraycopy(bytes,0,f.data,f.stringStart+name,bytes.length);
        return f.data;
    }
    private static void installBooleanBody(ApiFixture f,boolean fat,int... code) {
        put32(f.data,0x210,1);put32(f.data,f.methodTable,0x2b00);
        int body=0xd00;
        if(fat){put16(f.data,body,0x3003);put16(f.data,body+2,8);put32(f.data,body+4,code.length);put32(f.data,body+8,0);body+=12;}
        else {if(code.length>63)throw new IllegalArgumentException("tiny fixture length");f.data[body++]=(byte)((code.length<<2)|2);}
        for(int b:code)f.data[body++]=(byte)b;
    }
    private static void booleanBodyChecks()throws Exception {
        String main="ITMC.Regedit.RegeditMain",info="ITMC.Regedit.RegeditInfo&";
        for(boolean plus:new boolean[]{false,true})for(boolean fat:new boolean[]{false,true}) {
            byte[] bytes=booleanBodyFixture(plus,"ITMC.Regedit",false,"CheckReInfo",true,fat,0x16,0x2a);
            check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckReInfo",info)
                    ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.CONSTANT_FALSE,"complete tiny/fat body false");
            bytes=booleanBodyFixture(plus,"ITMC.Regedit",false,"CheckNet",false,fat,0x17,0x2a);
            check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet")
                    ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.CONSTANT_TRUE,"complete owned bool no-argument body true");
            check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet",info)
                    ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"complete parameter signature is required");
        }
        byte[] bytes=booleanBodyFixture(false,"ITMC.Regedit",false,"CheckReInfo",true,false,0x17,0x26,0x16,0x2a);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckReInfo",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"a false ending is not a complete constant body");
        bytes=booleanBodyFixture(false,"ITMC.Regedit",false,"CheckReInfo",true,false,0x2b,0,0x16,0x2a);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckReInfo",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"even simple branches are outside the supported exact shape");
        ApiFixture f=apiFixture(false,"ITMC.Regedit",false,false);installBooleanBody(f,true,0x16,0x2a);
        put16(f.data,0xd00,0x300b);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(f.data).booleanBodyShape(main,"CheckReInfo",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"EH or extra sections revoke the exact body observation");
        installBooleanBody(f,true,0x16,0x2a);put32(f.data,0xd08,0x11000001);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(f.data).booleanBodyShape(main,"CheckReInfo",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"local signature bodies remain outside exact shape");
        installBooleanBody(f,false,0x16,0x2a);put16(f.data,f.methodTable+4,3);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(f.data).booleanBodyShape(main,"CheckReInfo",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"runtime/protected method implementations are not CIL evidence");
        f=apiFixture(false,"ITMC.Regedit",false,true);installBooleanBody(f,false,0x16,0x2a);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(f.data).booleanBodyShape(main,"CheckReInfo",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"external same-name ref parameter cannot establish local method identity");
        f=apiFixture(false,"ITMC.Regedit",false,false);installBooleanBody(f,false,0x16,0x2a);
        put32(f.data,f.methodTable,0xfffffff0L);
        try{LicenseRecoverReadOnlyPeMetadata.parseApiShape(f.data).booleanBodyShape(main,"CheckReInfo",info);throw new AssertionError("unmapped body RVA");}
        catch(IOException expected){checks++;}
        f=apiFixture(false,"ITMC.Regedit",false,false);installBooleanBody(f,true,0x16,0x2a);put32(f.data,0xd04,0xffffffffL);
        try{LicenseRecoverReadOnlyPeMetadata.parseApiShape(f.data).booleanBodyShape(main,"CheckReInfo",info);throw new AssertionError("overflow body extent");}
        catch(IOException expected){checks++;}
        for(boolean fat:new boolean[]{false,true})for(boolean stat:new boolean[]{false,true}) {
            bytes=booleanBodyFixture(false,"ITMC.Regedit",false,"CheckNet",true,fat,stat,stat?0x02:0x03,0x14,0x51,0x16,0x2a);
            check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet",info)
                    ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.BYREF_NULL_FALSE,"tiny/fat exact local ref null false follows the declared argument index");
            bytes=booleanBodyFixture(false,"ITMC.Regedit",false,"CheckNet",true,fat,stat,stat?0x03:0x02,0x14,0x51,0x16,0x2a);
            check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet",info)
                    ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"wrong instance/static argument index does not match the replacement");
        }
        bytes=externalRefBodyFixture();
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"same-named external class reference is not the owned local class");
        bytes=booleanBodyFixture(false,"ITMC.Regedit",false,"CheckNet",true,false,0x03,0x14,0x51,0x16,0x2a);bytes[0x880+69]=0x11;
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"valuetype byref is not a local class-reference parameter");
        bytes=booleanBodyFixture(false,"ITMC.Regedit",false,"CheckNet",true,true,0x03,0x14,0x51,0x16,0x2a);put16(bytes,0xd02,1);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"the ref/null body requires its declared stack depth");
        bytes=booleanBodyFixture(false,"ITMC.Regedit",false,"CheckNet",true,false,0x17,0x26,0x03,0x14,0x51,0x16,0x2a);
        check(LicenseRecoverReadOnlyPeMetadata.parseApiShape(bytes).booleanBodyShape(main,"CheckNet",info)
                ==LicenseRecoverReadOnlyPeMetadata.BooleanBodyShape.UNASSESSED,"a matching null/false tail is not the complete method");
    }
    private static ApiFixture apiFixture(boolean plus,String ns,boolean wrongOwner,boolean externalInfo)throws Exception {
        ApiFixture out=new ApiFixture();out.data=Arrays.copyOf(fixture(plus),4096);
        ByteArrayOutputStream strings=new ByteArrayOutputStream();strings.write(0);
        int assembly=string(strings,"Synthetic.Registry"),module=string(strings,"<Module>"),main=string(strings,"RegeditMain"),info=string(strings,"RegeditInfo");
        out.namespaceIndex=string(strings,ns);
        int datetime=string(strings,"DateTime"),system=string(strings,"System"),check=string(strings,"CheckReInfo");
        out.localMethodName=string(strings,"CheckLocalReg");
        int[] names=new int[5];String[] fieldNames={"ProName","RegID","RegStr","EndDate","TotalTimes"};
        for(int i=0;i<names.length;i++)names[i]=string(strings,fieldNames[i]);
        ByteArrayOutputStream blobs=new ByteArrayOutputStream();blobs.write(0);
        int strSig=blob(blobs,6,14),dateSig=blob(blobs,6,17,5),intSig=blob(blobs,6,8);
        int methodSig=blob(blobs,0x20,1,2,0x10,0x12,externalInfo?9:12);
        ByteArrayOutputStream table=new ByteArrayOutputStream();
        u32(table,0);table.write(2);table.write(0);table.write(0);table.write(1);
        long valid=(1L<<1)|(1L<<2)|(1L<<4)|(1L<<6)|(1L<<32);
        u32(table,(int)valid);u32(table,(int)(valid>>>32));u32(table,0);u32(table,0);
        u32(table,externalInfo?2:1);u32(table,3);u32(table,5);u32(table,2);u32(table,1);
        u16(table,0);u16(table,datetime);u16(table,system);
        if(externalInfo){u16(table,0);u16(table,info);u16(table,out.namespaceIndex);}
        out.typeTable=0x380+table.size();
        u32(table,0);u16(table,module);u16(table,0);u16(table,0);u16(table,1);u16(table,1);
        u32(table,1);u16(table,main);u16(table,out.namespaceIndex);u16(table,0);u16(table,1);u16(table,1);
        u32(table,1);u16(table,info);u16(table,out.namespaceIndex);u16(table,0);u16(table,1);u16(table,wrongOwner?1:3);
        int[] fieldSigs={strSig,strSig,strSig,dateSig,intSig};out.fieldSignatures=new int[5];
        for(int i=0;i<5;i++){u16(table,6);u16(table,names[i]);u16(table,fieldSigs[i]);out.fieldSignatures[i]=0x880+fieldSigs[i]+1;}
        out.methodTable=0x380+table.size();
        for(int name:new int[]{check,out.localMethodName}){u32(table,0);u16(table,0);u16(table,6);u16(table,name);u16(table,methodSig);u16(table,1);}
        u32(table,0x8004);u16(table,2);u16(table,7);u16(table,19);u16(table,4);u32(table,0);u16(table,0);u16(table,assembly);u16(table,0);
        int sec=0x98+(plus?240:224);put32(out.data,sec+8,3584);put32(out.data,sec+16,3584);
        put32(out.data,0x20c,0xa00);
        put32(out.data,0x2a0,0x100);put32(out.data,0x2a4,table.size());
        put32(out.data,0x2ac,0x300);put32(out.data,0x2b0,strings.size());
        put32(out.data,0x2c0,0x600);put32(out.data,0x2c4,blobs.size());
        out.stringStart=0x580;out.blobStart=0x880;
        System.arraycopy(table.toByteArray(),0,out.data,0x380,table.size());
        System.arraycopy(strings.toByteArray(),0,out.data,out.stringStart,strings.size());
        System.arraycopy(blobs.toByteArray(),0,out.data,out.blobStart,blobs.size());
        return out;
    }
    private static void invalidApi(byte[] data,String why)throws Exception {
        try{LicenseRecoverReadOnlyPeMetadata.parseApiShape(data);throw new AssertionError(why);}catch(IOException expected){checks++;}
    }
    private static void apiBudgetChecks()throws Exception {
        int[][] fields={{6,18,6},{6,18,6}};
        byte[] valid=typeSpecFixture(new int[][]{generic(2,2),{14}},fields);
        LicenseRecoverReadOnlyPeMetadata.ApiShape shape=LicenseRecoverReadOnlyPeMetadata.parseApiShape(valid);
        String type="Synthetic.Container<System.String,System.String>";
        check(shape.hasField("Synthetic.Container","Field0",type)&&shape.hasField("Synthetic.Container","Field1",type),
                "shared TypeSpec DAG is cached without losing the resolved signature");
        invalidApiReason(typeSpecFixture(new int[][]{{18,10},{18,6}},new int[][]{{6,18,6}}),
                "cyclic-cli-api-typespec","mutually recursive TypeSpecs fail before recursion can exhaust the stack");
        invalidApiReason(typeSpecFixture(new int[][]{{18,6}},new int[][]{{6,18,6}}),
                "cyclic-cli-api-typespec","self-referencing TypeSpec fails deterministically");
        byte[] dag=typeSpecFixture(new int[][]{generic(2,64),generic(3,64),generic(4,64),{14}},new int[][]{{6,18,6}});
        check(dag.length<8192,"exponential-expansion fixture is a small input");
        invalidApiReason(dag,"cli-api-result-too-long","small repeated TypeSpec DAG cannot expand a giant result string");
        int[] shallow=new int[10];Arrays.fill(shallow,15);shallow[9]=8;
        int[] deepField=new int[23];deepField[0]=6;Arrays.fill(deepField,1,21,15);deepField[21]=18;deepField[22]=6;
        invalidApi(typeSpecFixture(new int[][]{shallow},new int[][]{{6,18,6},deepField}),
                "cached TypeSpec still enforces the logical depth at a deeper call site");
        int[][] repeated=new int[800][];for(int i=0;i<repeated.length;i++)repeated[i]=new int[]{6,29,18,6};
        byte[] expansion=typeSpecFixture(new int[][]{generic(2,8),generic(0,64)},repeated);
        check(expansion.length<40000,"cumulative-expansion fixture stays compact");
        invalidApiReason(expansion,"cli-api-text-budget","many separately bounded results share one module text budget");
        invalidApiReason(tooManyRowsFixture(),"cli-api-node-budget","row counts share one module node budget");
    }
    /** GenericInst local Container with repeated primitive or TypeSpec arguments. */
    private static int[] generic(int target,int count) {
        int[] b=new int[4+count*(target==0?1:2)];b[0]=21;b[1]=18;b[2]=8;b[3]=count;
        int p=4;for(int i=0;i<count;i++){if(target==0)b[p++]=14;else{b[p++]=18;b[p++]=(target<<2)|2;}}
        return b;
    }
    private static byte[] typeSpecFixture(int[][] specs,int[][] fieldSignatures)throws Exception {
        ByteArrayOutputStream strings=new ByteArrayOutputStream();strings.write(0);
        int assembly=string(strings,"Synthetic.Registry"),module=string(strings,"<Module>");
        int container=string(strings,"Container"),namespace=string(strings,"Synthetic");
        int[] names=new int[fieldSignatures.length];for(int i=0;i<names.length;i++)names[i]=string(strings,"Field"+i);
        ByteArrayOutputStream blobs=new ByteArrayOutputStream();blobs.write(0);
        int[] fields=new int[fieldSignatures.length],types=new int[specs.length];
        for(int i=0;i<fields.length;i++)fields[i]=blob(blobs,fieldSignatures[i]);
        for(int i=0;i<types.length;i++)types[i]=blob(blobs,specs[i]);
        ByteArrayOutputStream table=new ByteArrayOutputStream();long valid=(1L<<2)|(1L<<4)|(1L<<27)|(1L<<32);
        tableHeader(table,valid);u32(table,2);u32(table,fields.length);u32(table,types.length);u32(table,1);
        u32(table,0);u16(table,module);u16(table,0);u16(table,0);u16(table,1);u16(table,1);
        u32(table,1);u16(table,container);u16(table,namespace);u16(table,0);u16(table,1);u16(table,1);
        for(int i=0;i<fields.length;i++){u16(table,6);u16(table,names[i]);u16(table,fields[i]);}
        for(int type:types)u16(table,type);
        assemblyRow(table,assembly);
        return metadataImage(table,strings,blobs);
    }
    private static byte[] tooManyRowsFixture()throws Exception {
        ByteArrayOutputStream table=new ByteArrayOutputStream(),strings=new ByteArrayOutputStream(),blobs=new ByteArrayOutputStream();
        strings.write(0);blobs.write(0);int assembly=string(strings,"Synthetic.Registry");
        tableHeader(table,(1L<<8)|(1L<<32)|(1L<<42));u32(table,100000);u32(table,1);u32(table,100000);
        table.write(new byte[600000]);assemblyRow(table,assembly);table.write(new byte[800000]);
        return metadataImage(table,strings,blobs);
    }
    private static byte[] assemblyFixture(String name)throws Exception {
        ByteArrayOutputStream table=new ByteArrayOutputStream(),strings=new ByteArrayOutputStream(),blobs=new ByteArrayOutputStream();
        strings.write(0);blobs.write(0);int assembly=string(strings,name);
        tableHeader(table,1L<<32);u32(table,1);assemblyRow(table,assembly);return metadataImage(table,strings,blobs);
    }
    private static void tableHeader(ByteArrayOutputStream table,long valid) {
        u32(table,0);table.write(2);table.write(0);table.write(0);table.write(1);
        u32(table,(int)valid);u32(table,(int)(valid>>>32));u32(table,0);u32(table,0);
    }
    private static void assemblyRow(ByteArrayOutputStream table,int name) {
        u32(table,0x8004);u16(table,2);u16(table,7);u16(table,19);u16(table,4);u32(table,0);u16(table,0);u16(table,name);u16(table,0);
    }
    private static byte[] metadataImage(ByteArrayOutputStream table,ByteArrayOutputStream strings,ByteArrayOutputStream blobs) {
        int stringsAt=(0x380+table.size()+3)&~3,blobsAt=(stringsAt+strings.size()+3)&~3;
        byte[] b=Arrays.copyOf(fixture(false),(blobsAt+blobs.size()+1023)&~511);
        int sec=0x98+224;put32(b,sec+8,b.length-512);put32(b,sec+16,b.length-512);put32(b,0x20c,b.length-0x280);
        put32(b,0x2a0,0x100);put32(b,0x2a4,table.size());
        put32(b,0x2ac,stringsAt-0x280);put32(b,0x2b0,strings.size());
        put32(b,0x2c0,blobsAt-0x280);put32(b,0x2c4,blobs.size());
        System.arraycopy(table.toByteArray(),0,b,0x380,table.size());System.arraycopy(strings.toByteArray(),0,b,stringsAt,strings.size());
        System.arraycopy(blobs.toByteArray(),0,b,blobsAt,blobs.size());return b;
    }
    private static void invalidApiReason(byte[] data,String reason,String why)throws Exception {
        try{LicenseRecoverReadOnlyPeMetadata.parseApiShape(data);throw new AssertionError(why);}
        catch(IOException expected){check(reason.equals(expected.getMessage()),why+": "+expected.getMessage());}
    }
    static byte[] fixture(boolean plus) {
        byte[] b = new byte[1536];
        put16(b, 0, 0x5a4d); put32(b, 60, 0x80); put32(b, 0x80, 0x4550);
        put16(b, 0x86, 1); int optSize = plus ? 240 : 224;
        put16(b, 0x94, optSize); put16(b, 0x98, plus ? 0x20b : 0x10b);
        put32(b, 0x98 + 60, 512); int dd = 0x98 + (plus ? 112 : 96);
        put32(b, dd - 4, 16); put32(b, dd + 14 * 8, 0x2000); put32(b, dd + 14 * 8 + 4, 72);
        int sec = 0x98 + optSize;
        put32(b, sec + 8, 1024); put32(b, sec + 12, 0x2000);
        put32(b, sec + 16, 1024); put32(b, sec + 20, 512);
        put32(b, 0x200, 72); put32(b, 0x208, 0x2080); put32(b, 0x20c, 512);
        put32(b, 0x280, 0x424a5342); put32(b, 0x28c, 12);
        byte[] version = "v4.0.30319".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(version, 0, b, 0x290, version.length);
        put16(b, 0x29e, 3);
        // Three non-overlapping streams with a minimal Assembly table.
        put32(b, 0x2a0, 0xa0); put32(b, 0x2a4, 64); ascii(b, 0x2a8, "#~");
        put32(b, 0x2ac, 0xe0); put32(b, 0x2b0, 4); ascii(b, 0x2b4, "#Strings");
        put32(b, 0x2c0, 0xe4); put32(b, 0x2c4, 4); ascii(b, 0x2c8, "#Blob");
        put32(b, 0x328, 0); put32(b, 0x32c, 1); // valid bit 32
        put32(b, 0x338, 1);
        put16(b, 0x340, 2); put16(b, 0x342, 7); put16(b, 0x344, 19); put16(b, 0x346, 4);
        return b;
    }
    private static void ascii(byte[] b, int p, String text) {
        byte[] s = text.getBytes(StandardCharsets.US_ASCII); System.arraycopy(s, 0, b, p, s.length);
    }
    private static void put16(byte[] b, int p, int n) { ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putShort(p, (short)n); }
    private static void put32(byte[] b, int p, long n) { ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(p, (int)n); }
    private static void invalid(byte[] b, String label) throws Exception {
        try { LicenseRecoverReadOnlyPeMetadata.parse(b); throw new AssertionError(label); }
        catch (IOException expected) { checks++; }
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
}
