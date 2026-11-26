/** Offline, synthetic native .NET RegInfo identity repair regression. */
public final class DotNetNativeRegInfoIdentityRepairTest {
    private static final String PRODUCT = "YX0302";
    private static final String VERSION = "YX030107";
    private static final String PASSWORD = "*ITMC" + PRODUCT + "OK*";
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static String xml(String json) throws Exception {
        // use the existing DES helper for a synthetic vendor-format fixture
        java.lang.reflect.Method enc = LicenseRecoverModernGUIAutoRecovery.class
            .getDeclaredMethod("desEncryptHex", String.class, String.class);
        enc.setAccessible(true);
        String token = (String) enc.invoke(null, "123456" + json + "987654", PASSWORD);
        return "<root><reg><regName>" + token + "</regName><regType>1</regType></reg></root>";
    }
    private static String json(String user) {
        // Mirrors the public schema (not private values) of the supplied
        // YX030107 local record: 14 fields and multiple RegStr tokens.
        return "{\"RegStr\":\"" + VERSION + ",YX030201,YX030202,YX030203,YX030204\""
                + ",\"ClassNum\":-1,\"RegID\":\"SYNTHETIC-MACHINE\",\"UserID\":" + user
                + ",\"ProName\":\"" + PRODUCT + "\""
                + ",\"BeginDate\":\"/Date(1760000000000+0000)/\""
                + ",\"beginDate\":0,\"endDate\":0"
                + ",\"EndDate\":\"/Date(4102358400000+0000)/\""
                + ",\"TotalTimes\":-1,\"UserTimes\":1,\"Net\":true"
                + ",\"MaxCon\":-1,\"CountDay\":10}";
    }

    private static void fails(String xml, String product, String version) throws Exception {
        boolean rejected = false;
        try {
            LicenseRecoverModernGUIAutoRecovery.normalizeNativeRegInfoUserId(xml, product, version);
        } catch (Exception expected) {
            rejected = true;
        }
        check(rejected, "unsupported or ambiguous license identity unexpectedly accepted");
    }
    public static void main(String[] args) throws Exception {
        String before = xml(json("null"));
        String after = LicenseRecoverModernGUIAutoRecovery.normalizeNativeRegInfoUserId(
                before, PRODUCT, VERSION);
        check(!before.equals(after), "empty UserID did not change");
        check(after.equals(LicenseRecoverModernGUIAutoRecovery.normalizeNativeRegInfoUserId(
                after, PRODUCT, VERSION)), "normalization is not idempotent");

        java.lang.reflect.Method dec = LicenseRecoverModernGUIAutoRecovery.class
            .getDeclaredMethod("desDecryptHex", String.class, String.class);
        dec.setAccessible(true);
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("<regName>([^<]+)</regName>").matcher(after);
        check(m.find(), "patched regName missing");
        String clear = (String) dec.invoke(null, m.group(1), PASSWORD);
        check(clear.equals("123456" + json("\"fwq\"") + "987654"),
                "other fields or envelope were changed");
        String empty = xml(json("\"\""));
        String populated = LicenseRecoverModernGUIAutoRecovery.normalizeNativeRegInfoUserId(
                empty, PRODUCT, VERSION);
        check(!empty.equals(populated), "empty-string UserID should be populated");
        String already = xml(json("\"fwq\""));
        check(already.equals(LicenseRecoverModernGUIAutoRecovery.normalizeNativeRegInfoUserId(
                already, PRODUCT, VERSION)), "matching UserID should not be re-encrypted");

        fails(xml(json("\"itmc\"")), PRODUCT, VERSION);
        fails(xml(json("null")), "WRONG", VERSION);
        fails(xml(json("null")), PRODUCT, "OTHER");
        fails(xml(json("null").replace("\"UserID\":null",
                "\"UserID\":null,\"UserID\":null")), PRODUCT, VERSION);
        fails(xml(json("null").replace("\"RegID\":\"TEST-MACHINE\"",
                "\"RegID\":\"\"")), PRODUCT, VERSION);
        System.out.println("PASS: synthetic native .NET RegInfo UserID repair is bounded and lossless");
    }
}
