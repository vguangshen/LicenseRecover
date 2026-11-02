import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Synthetic output/resolver tests. Does not query or expose a real CPU. */
public final class LicenseRecoverReadOnlyWmicCpuIdentityTest {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static LicenseRecoverReadOnlyWmicCpuIdentity.Identity parse(String text){return LicenseRecoverReadOnlyWmicCpuIdentity.parse(text.getBytes(StandardCharsets.US_ASCII));}
    public static void main(String[] args) {
        String secret="SYNTHETIC_CPU_SENTINEL";
        LicenseRecoverReadOnlyWmicCpuIdentity.Identity one=parse("ProcessorId  \r\n "+secret+" \r\n\r\n");
        check(one.complete&&secret.equals(one.value),"precise title then trimmed next line");
        check(!one.reason.contains(secret)&&!one.toString().contains(secret)&&!one.scope.contains(secret),"identity absent from diagnostics/toString");
        check("FIRST".equals(parse("ProcessorId\r\nFIRST\r\nSECOND\r\n").value),"multiple CPU rows use first after header, never WMI last");
        check("SECOND".equals(parse("ProcessorId\r\nFIRST\r\nProcessorId\r\nSECOND\r\n").value),"repeated header follows native later assignment");
        check("unknow".equals(parse("processorid\r\nVALUE\r\n").value),"header match remains case sensitive");
        check("unknow".equals(parse("ProcessorId\r\n   \r\nVALUE\r\n").value),"whitespace line is not removed by RemoveEmptyEntries");
        check("VALUE".equals(parse("ProcessorId\r\n\r\nVALUE\r\n").value),"truly empty lines are removed");
        check("unknow".equals(parse("ProcessorId\r\nFIRST\r\nProcessorId\r\n").value),"trailing header takes native exception fallback");
        check("unknow".equals(parse("ProcessorId\nVALUE\n").value),"bare LF is not normalized into native Windows CRLF");
        check("unknow".equals(parse("").value),"proven completed empty stdout has native unknow result");
        byte[] completed="ProcessorId\r\nSYNTHETIC_CPU_SENTINEL\r\n".getBytes(StandardCharsets.US_ASCII);
        check(LicenseRecoverReadOnlyWmicCpuIdentity.parseQueryResult(completed,0,0).complete,
                "successful WMIC invocation permits native stdout interpretation");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parseQueryResult(completed,1,0).complete,
                "failed WMIC exit cannot establish identity even with plausible stdout");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parseQueryResult(completed,0,1).complete,
                "WMIC stderr makes stdout interpretation ambiguous");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parseQueryResult(new byte[0],1,0).complete,
                "failed empty WMIC query cannot become proven native unknow fallback");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parse(new byte[32769]).complete,"bounded stdout");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parse("ProcessorId\r\nVALUE\r\n".getBytes(StandardCharsets.UTF_16LE)).complete,"no heuristic UTF16 decoding");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parse(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,80}).complete,"UTF8 BOM not silently stripped");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parse(new byte[]{(byte)0xff,(byte)0xfe,80,0}).complete,"UTF16 BOM does not expand audit scope");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.parse(new byte[]{0}).complete,"NUL not normalized");
        StringBuilder longValue=new StringBuilder("ProcessorId\r\n");for(int i=0;i<257;i++)longValue.append('A');
        check(!parse(longValue.toString()).complete,"oversized identity cannot pass");
        Path expected=Paths.get(System.getProperty("java.io.tmpdir"),"synthetic-system32","wmic.exe").toAbsolutePath().normalize();
        String quoted='"'+expected.toString()+"\"\r\n";
        check(LicenseRecoverReadOnlyWmicCpuIdentity.onlyTrustedResolution(quoted.getBytes(StandardCharsets.US_ASCII),expected),"trusted fixed path only");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.onlyTrustedResolution((quoted+'"'+expected.resolveSibling("wmic.cmd").toString()+"\"\r\n").getBytes(StandardCharsets.US_ASCII),expected),"alternate candidate cannot be picked opportunistically");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.onlyTrustedResolution(expected.toString().getBytes(StandardCharsets.US_ASCII),expected),"malformed WHERE framing rejected");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.onlyTrustedResolution(new byte[0],expected),"missing executable is not proof of native unknow environment");
        check(!LicenseRecoverReadOnlyWmicCpuIdentity.onlyTrustedResolution(new byte[32769],expected),"resolver output bounded");
        check(LicenseRecoverReadOnlyWmicCpuIdentity.policyProblem("LRW1-CLEAR\r\n".getBytes(StandardCharsets.US_ASCII),0,0)==null,"fixed read-only policy must explicitly clear before query");
        check(LicenseRecoverReadOnlyWmicCpuIdentity.policyProblem("LRW1-AUTORUN\r\n".getBytes(StandardCharsets.US_ASCII),0,0)!=null,"AutoRun never reaches execution");
        check(LicenseRecoverReadOnlyWmicCpuIdentity.policyProblem("LRW1-PATH\r\n".getBytes(StandardCharsets.US_ASCII),0,0)!=null,"remote or unaudited path policy cannot pass");
        check(LicenseRecoverReadOnlyWmicCpuIdentity.policyProblem("LRW1-CLEAR\r\n".getBytes(StandardCharsets.US_ASCII),1,0)!=null,"policy execution failure cannot pass");
        check(LicenseRecoverReadOnlyWmicCpuIdentity.policyProblem("LRW1-CLEAR\r\n".getBytes(StandardCharsets.US_ASCII),0,1)!=null,"policy stderr prevents an ambiguous clear");
        System.out.println("PASS: independent WMIC parser/resolver, exact native text boundaries and identity privacy ("+checks+" checks)");
    }
}
