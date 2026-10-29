import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
        System.out.println("LicenseRecoverReadOnlyPeMetadataTest: " + checks + " checks passed");
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
