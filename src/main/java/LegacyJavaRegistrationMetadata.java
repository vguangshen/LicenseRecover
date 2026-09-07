import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Reads the legacy Java application's own registration metadata instead of guessing
 * a product family in LicenseRecover.
 *
 * The classic applications build Global.registerProductBeans in Global.<clinit>.
 * Some production builds protect method Code bytes with the same Virbox XOR stream
 * already shipped beside LicenseRecover. This reader parses the target class file
 * directly and, when needed, restores only the in-memory Code bytes before looking
 * for RegisterProductBean setter calls. Nothing in the target application is changed.
 */
public final class LegacyJavaRegistrationMetadata {
    private LegacyJavaRegistrationMetadata() { }

    public static final class Mapping {
        public final String productMain;
        public final String productMainNum;
        public final String productNums;
        public final String source;

        Mapping(String productMain, String productMainNum, String productNums, String source) {
            this.productMain = clean(productMain);
            this.productMainNum = clean(productMainNum);
            this.productNums = clean(productNums);
            this.source = source == null ? "target application" : source;
        }

        public String toString() {
            return "productMain=" + productMain + ", productMainNum=" + productMainNum
                    + ", productNums=" + productNums + " (" + source + ")";
        }
    }

    private static final class GlobalScan {
        final boolean present;
        final boolean complete;
        final Mapping mapping;
        GlobalScan(boolean present, boolean complete, Mapping mapping) {
            this.present = present;
            this.complete = complete;
            this.mapping = mapping;
        }
    }

    /**
     * Resolve the exact runtime registration family and authorization item from the
     * selected application's own classes. Explicit Global.registerProductBeans rows
     * win. If the target's Global table was successfully inspected but contains no
     * row for this VersionID, use the fallback family literally embedded in that
     * target's RegisterUtil.class and keep the concrete VersionID as productMainNum.
     */
    public static Mapping inspect(File root, String softId) {
        if (root == null || blank(softId)) return null;
        File appRoot = root.getAbsoluteFile();
        File global = new File(appRoot, "WEB-INF" + File.separator + "classes"
                + File.separator + "com" + File.separator + "common" + File.separator
                + "global" + File.separator + "Global.class");
        GlobalScan scan = scanGlobal(global, softId.trim());
        if (scan.mapping != null) return scan.mapping;
        // If a protected Global.class exists but cannot be inspected safely, do not
        // silently fall through to a guessed family.
        if (scan.present && !scan.complete) return null;

        File registerUtil = new File(appRoot, "WEB-INF" + File.separator + "classes"
                + File.separator + "com" + File.separator + "common" + File.separator
                + "utils" + File.separator + "RegisterUtil.class");
        String fallback = fallbackFamilyFromTarget(registerUtil, softId.trim());
        if (blank(fallback)) return null;
        return new Mapping(fallback, softId.trim(), softId.trim(),
                "RegisterUtil.class fallback");
    }

    /** Legacy YT platforms with their own RegisterUtil must never use a hard-coded fallback. */
    public static boolean requiresDirectoryMapping(File root, String softId) {
        if (root == null || blank(softId)) return false;
        String id = softId.trim().toUpperCase(Locale.ROOT);
        if (!id.startsWith("YT001")) return false;
        return new File(root, "WEB-INF" + File.separator + "classes"
                + File.separator + "com" + File.separator + "common" + File.separator
                + "utils" + File.separator + "RegisterUtil.class").isFile();
    }

    static String selectFallbackPrefix(Collection<String> targetStrings, String softId) {
        if (targetStrings == null || blank(softId)) return null;
        String id = softId.trim().toUpperCase(Locale.ROOT);
        String best = null;
        for (String raw : targetStrings) {
            if (blank(raw)) continue;
            String candidate = raw.trim().toUpperCase(Locale.ROOT);
            if (candidate.equals(id) || candidate.length() >= id.length()) continue;
            if (!id.startsWith(candidate)) continue;
            if (!candidate.matches("[A-Z]{2,8}[0-9]{2,8}")) continue;
            if (best == null || candidate.length() > best.length()) best = candidate;
        }
        return best;
    }

    private static String fallbackFamilyFromTarget(File registerUtil, String softId) {
        if (registerUtil == null || !registerUtil.isFile()) return null;
        try {
            ClassFile cf = new ClassFile(Files.readAllBytes(registerUtil.toPath()));
            return selectFallbackPrefix(cf.stringConstants(), softId);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static GlobalScan scanGlobal(File global, String softId) {
        if (global == null || !global.isFile()) return new GlobalScan(false, true, null);
        try {
            ClassFile cf = new ClassFile(Files.readAllBytes(global.toPath()));
            byte[] code = cf.clinitCode;
            if (code == null) return new GlobalScan(true, false, null);
            if (cf.minor == 32768) {
                byte[] key = readVirboxKey();
                if (key == null || key.length < code.length) return new GlobalScan(true, false, null);
                code = code.clone();
                for (int i = 0; i < code.length; i++) code[i] ^= key[i];
            }

            String productMain = null;
            String productMainNum = null;
            for (int i = 0; i < code.length - 4; i++) {
                int op = code[i] & 0xff;
                int cpIndex;
                int invokePos;
                if (op == 0x12) { // ldc
                    cpIndex = code[i + 1] & 0xff;
                    invokePos = i + 2;
                } else if (op == 0x13) { // ldc_w
                    cpIndex = u2(code, i + 1);
                    invokePos = i + 3;
                } else {
                    continue;
                }
                if (invokePos + 2 >= code.length || (code[invokePos] & 0xff) != 0xb6) continue;
                String value = cf.stringAt(cpIndex);
                String method = cf.methodName(u2(code, invokePos + 1));
                if (value == null || method == null) continue;
                if ("setProductMain".equals(method)) {
                    productMain = value.trim();
                } else if ("setProductMainNum".equals(method)) {
                    productMainNum = value.trim();
                } else if ("setProductNums".equals(method)) {
                    String productNums = value.trim();
                    if (containsCsv(productNums, softId)
                            && !blank(productMain) && !blank(productMainNum)) {
                        return new GlobalScan(true, true,
                                new Mapping(productMain, productMainNum, productNums,
                                        "Global.registerProductBeans"));
                    }
                }
            }
            return new GlobalScan(true, true, null);
        } catch (Throwable ignore) {
            return new GlobalScan(true, false, null);
        }
    }

    private static byte[] readVirboxKey() {
        for (File f : new File[]{
                new File(toolDir(), "virbox_keystream.bin"),
                new File(toolDir(), "virbox_keystream_exact_22937.bin")
        }) {
            try { if (f.isFile()) return Files.readAllBytes(f.toPath()); }
            catch (Exception ignore) { }
        }
        return null;
    }

    private static File toolDir() {
        try {
            File loc = new File(LegacyJavaRegistrationMetadata.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return loc.isFile() ? loc.getParentFile() : loc;
        } catch (Exception ignore) {
            return new File(".").getAbsoluteFile();
        }
    }

    private static boolean containsCsv(String csv, String wanted) {
        if (blank(csv) || blank(wanted)) return false;
        for (String x : csv.split(",")) if (wanted.equalsIgnoreCase(x.trim())) return true;
        return false;
    }

    private static int u2(byte[] b, int p) {
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }

    private static String clean(String s) { return s == null ? null : s.trim(); }
    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    /** Minimal class-file reader sufficient for constant strings, method refs and <clinit> Code. */
    private static final class ClassFile {
        final int minor;
        final int[] tag;
        final int[] a;
        final int[] b;
        final String[] utf8;
        byte[] clinitCode;

        ClassFile(byte[] raw) throws Exception {
            Reader r = new Reader(raw);
            if (r.u4() != 0xCAFEBABEL) throw new IllegalArgumentException("not a Java class");
            minor = r.u2();
            r.u2(); // major
            int count = r.u2();
            tag = new int[count]; a = new int[count]; b = new int[count]; utf8 = new String[count];
            for (int i = 1; i < count; i++) {
                int t = r.u1(); tag[i] = t;
                switch (t) {
                    case 1: {
                        int n = r.u2();
                        utf8[i] = new String(r.bytes(n), StandardCharsets.UTF_8);
                        break;
                    }
                    case 3: case 4: r.skip(4); break;
                    case 5: case 6: r.skip(8); i++; break;
                    case 7: case 8: case 16: case 19: case 20: a[i] = r.u2(); break;
                    case 9: case 10: case 11: case 12: case 17: case 18:
                        a[i] = r.u2(); b[i] = r.u2(); break;
                    case 15: r.skip(3); break;
                    default: throw new IllegalArgumentException("unknown constant-pool tag " + t);
                }
            }
            r.skip(6); // access, this, super
            int interfaces = r.u2(); r.skip(interfaces * 2);
            skipMembers(r, false);
            skipMembers(r, true);
            // class attributes are irrelevant
        }

        private void skipMembers(Reader r, boolean methods) throws Exception {
            int count = r.u2();
            for (int i = 0; i < count; i++) {
                r.u2();
                int nameIndex = r.u2();
                r.u2();
                String memberName = utf(nameIndex);
                int attrs = r.u2();
                for (int j = 0; j < attrs; j++) {
                    int attrNameIndex = r.u2();
                    long lenLong = r.u4();
                    if (lenLong > Integer.MAX_VALUE) throw new IllegalArgumentException("attribute too large");
                    int len = (int) lenLong;
                    String attrName = utf(attrNameIndex);
                    if (methods && "<clinit>".equals(memberName) && "Code".equals(attrName)) {
                        int start = r.pos;
                        r.u2(); r.u2();
                        long codeLenLong = r.u4();
                        if (codeLenLong > Integer.MAX_VALUE) throw new IllegalArgumentException("code too large");
                        clinitCode = r.bytes((int) codeLenLong);
                        r.pos = start + len;
                    } else {
                        r.skip(len);
                    }
                }
            }
        }

        String utf(int index) {
            return index > 0 && index < utf8.length ? utf8[index] : null;
        }

        String stringAt(int index) {
            if (index <= 0 || index >= tag.length || tag[index] != 8) return null;
            return utf(a[index]);
        }

        String methodName(int index) {
            if (index <= 0 || index >= tag.length || (tag[index] != 10 && tag[index] != 11)) return null;
            int nat = b[index];
            if (nat <= 0 || nat >= tag.length || tag[nat] != 12) return null;
            return utf(a[nat]);
        }

        List<String> stringConstants() {
            List<String> values = new ArrayList<String>();
            for (int i = 1; i < tag.length; i++) {
                if (tag[i] == 8) {
                    String s = utf(a[i]);
                    if (s != null) values.add(s);
                }
            }
            return values;
        }
    }

    private static final class Reader {
        final byte[] data;
        int pos;
        Reader(byte[] data) { this.data = data; }
        int u1() { return data[pos++] & 0xff; }
        int u2() { int v = ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff); pos += 2; return v; }
        long u4() {
            long v = ((long)(data[pos] & 0xff) << 24) | ((long)(data[pos + 1] & 0xff) << 16)
                    | ((long)(data[pos + 2] & 0xff) << 8) | (long)(data[pos + 3] & 0xff);
            pos += 4; return v;
        }
        byte[] bytes(int n) {
            byte[] out = new byte[n];
            System.arraycopy(data, pos, out, 0, n); pos += n; return out;
        }
        void skip(int n) { pos += n; }
    }
}
