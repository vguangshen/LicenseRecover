import java.nio.charset.StandardCharsets;
import java.util.Base64;
public final class LicenseRecoverReadOnlyJavaMachineIdentityTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static LicenseRecoverReadOnlyJavaMachineIdentity.Identity wire(String value) {
        return LicenseRecoverReadOnlyJavaMachineIdentity.parse(("LRJID1\n"+Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))+"\nEND\n").getBytes(StandardCharsets.US_ASCII));
    }
    public static void main(String[] args) {
        check(("ABCD-1234"+"0000000").equals(wire("ABCD-1234").machineId),"right padding exactly follows native rule");
        check((" ABCD-1234"+"000000").equals(wire(" ABCD-1234").machineId),"leading space must not be trimmed");
        check("1234567890123456".equals(wire("12345678901234567890").machineId),"first sixteen chars only");
        check(!wire("").complete,"empty primary requires unaudited fallback");
        check(!wire("\u00a0ABCD").complete,"non-ASCII unreviewed metadata rejected");
        check(!LicenseRecoverReadOnlyJavaMachineIdentity.parse("LRJID1-AUTORUN\n".getBytes(StandardCharsets.US_ASCII)).complete,"AutoRun never executed");
        check(!LicenseRecoverReadOnlyJavaMachineIdentity.parse("LRJID1-FALLBACK\n".getBytes(StandardCharsets.US_ASCII)).complete,"fallback is not guessed");
        check(!wire("ABCD-1234").reason.contains("ABCD-1234"),"hardware identifier absent from reason");
        check(!LicenseRecoverReadOnlyJavaMachineIdentity.parse(new byte[16385]).complete,"bounded output");
        System.out.println("PASS: independent Java volume identity framing, padding, fallback, privacy and bounds");
    }
}
