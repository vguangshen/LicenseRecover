import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** Preflight metadata discovery must fail closed before the audited scanner's own limits. */
public final class LicenseRecoverMetadataReadBudgetTest {
    private static int checks;
    private static void check(boolean value,String why) {checks++;if(!value)throw new AssertionError(why);}
    private static void text(Path path,String value)throws Exception {
        Files.createDirectories(path.getParent());Files.write(path,value.getBytes(StandardCharsets.UTF_8));
    }
    private static void oversize(Path path,int limit,String prefix)throws Exception {
        text(path,prefix);
        try(RandomAccessFile file=new RandomAccessFile(path.toFile(),"rw")){file.setLength((long)limit+1);}
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("lr-metadata-budget-");
        try {
            Path javaRoot=root.resolve("java"),lib=javaRoot.resolve("WEB-INF/lib");
            Files.createDirectories(lib);Files.write(lib.resolve("ITMCReg.jar"),new byte[0]);
            Path properties=javaRoot.resolve("systemConfig.yml");
            text(properties,"global.system.VersionID: LIVE_MODE\n");
            text(javaRoot.resolve("data/config.xml"),"<SoftVersionID>DATA_MODE</SoftVersionID>");
            text(javaRoot.resolve("WEB-INF/classes/config.xml"),"<SoftVersionID>CLASSES_MODE</SoftVersionID>");
            check("LIVE_MODE".equals(AppDetector.detectLocal(javaRoot.toFile()).softVersionId),"normal Java property discovery remains available");
            byte[] invalidUtf8=new byte[12000];Arrays.fill(invalidUtf8,(byte)' ');
            byte[] firstLine="global.system.VersionID: UNSAFE_PARTIAL\n".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(firstLine,0,invalidUtf8,0,firstLine.length);invalidUtf8[invalidUtf8.length-1]=(byte)0xff;
            Files.write(properties,invalidUtf8);
            check("DATA_MODE".equals(AppDetector.readJavaSoftVersion(javaRoot.toFile()))
                    &&"DATA_MODE".equals(LicenseRecoverModernGUIAutoRecovery.detect(javaRoot.toFile()).versionId),
                    "malformed UTF8 suffix revokes both readers' otherwise favorable property prefix");
            oversize(properties,AppDetector.MAX_PROPERTIES_BYTES,"global.system.VersionID: UNSAFE_PARTIAL\n");
            check("DATA_MODE".equals(AppDetector.readJavaSoftVersion(javaRoot.toFile())),"oversized properties are rejected in full and data fallback remains");
            check("DATA_MODE".equals(LicenseRecoverModernGUIAutoRecovery.detect(javaRoot.toFile()).versionId),"GUI coordinator preserves the same Java fallback");
            oversize(javaRoot.resolve("data/config.xml"),AppDetector.MAX_CONFIG_BYTES,"<SoftVersionID>UNSAFE_PARTIAL</SoftVersionID>");
            check("CLASSES_MODE".equals(AppDetector.readJavaSoftVersion(javaRoot.toFile())),"oversized data config cannot hide the classes fallback");
            check("CLASSES_MODE".equals(LicenseRecoverModernGUIAutoRecovery.detect(javaRoot.toFile()).versionId),"coordinator also rejects oversized data config");

            Path dotnet=root.resolve("dotnet"),bin=dotnet.resolve("bin");Files.createDirectories(bin);
            Path web=bin.resolve("ITMC.Web.dll");
            byte[] tokens="itmcIEC\0DS0105\0".getBytes(StandardCharsets.UTF_16LE);
            Files.write(web,tokens);Files.write(bin.resolve("ITMC.Regedit.dll"),new byte[0]);
            text(bin.resolve("config.xml"),"<SoftVersionID>BIN_MODE</SoftVersionID>");
            oversize(dotnet.resolve("config.xml"),AppDetector.MAX_CONFIG_BYTES,"<SoftVersionID>UNSAFE_PARTIAL</SoftVersionID>");
            check("BIN_MODE".equals(LicenseRecoverModernGUIAutoRecovery.detect(dotnet.toFile()).versionId),"oversized root config retains the existing bin fallback");
            check("itmcIEC".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(web.toFile(),"DS0105")),"bounded normal assembly token discovery works");
            check("DS0105".equals(LicenseRecoverModernGUIAutoRecovery.detectProductList(web.toFile(),"itmcIEC")),"normal compatibility metadata remains available");
            oversize(web,LicenseRecoverModernGUIAutoRecovery.MAX_METADATA_BINARY_BYTES,"itmcIEC\0");
            check(LicenseRecoverModernGUIAutoRecovery.detectProduct(web.toFile(),"DS0105")==null,"oversized binary cannot supply a favorable early product token");
            check(LicenseRecoverModernGUIAutoRecovery.extractUtf16Ascii(web.toFile()).isEmpty(),"oversized binary is not allocated or partially trusted");
            byte[] longRun=new byte[20000];Arrays.fill(longRun,(byte)'A');
            byte[] prefix="itmcIEC\0".getBytes(StandardCharsets.US_ASCII),expanded=new byte[prefix.length+longRun.length];
            System.arraycopy(prefix,0,expanded,0,prefix.length);System.arraycopy(longRun,0,expanded,prefix.length,longRun.length);
            Files.write(web,expanded);
            check(LicenseRecoverModernGUIAutoRecovery.extractUtf16Ascii(web.toFile()).isEmpty(),"small binary with a huge text run revokes all partial candidate metadata");
            check(Arrays.equals(expanded,Files.readAllBytes(web)),"preflight discovery does not modify target fixture bytes");
            Files.write(web,"Encrypt3Des\0Decrypt3Des\0GetRegisterVersionList\0GetProVersion\0CheckSoftVersionID\0".getBytes(StandardCharsets.US_ASCII));
            oversize(dotnet.resolve("RegisterVersion.db"),LicenseRecoverModernGUIAutoRecovery.MAX_METADATA_DATABASE_BYTES,"candidate-row");
            LicenseRecoverModernGUIAutoRecovery.Detection detection=new LicenseRecoverModernGUIAutoRecovery.Detection(
                    LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN,dotnet.toFile(),dotnet.toFile(),bin.toFile(),"BIN_MODE","itmcIEC");
            check(LicenseRecoverModernGUIAutoRecovery.readRegisterVersionEvidence(detection)==null,"oversized compatibility database does not reach row decryption");
            Path bytes=root.resolve("reader.bin");Files.write(bytes,new byte[]{1,2,3});
            check(AppDetector.readMetadataBytes(bytes.toFile(),3).length==3,"exact byte budget is accepted");
            try {AppDetector.readMetadataBytes(bytes.toFile(),2);throw new AssertionError("over-limit reader accepted");}
            catch(java.io.IOException expected){checks++;}
            System.out.println("PASS: preflight metadata budgets and existing fallbacks ("+checks+" checks)");
        } finally {
            try(java.util.stream.Stream<Path> files=Files.walk(root)) {
                for(Path path:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }
}
