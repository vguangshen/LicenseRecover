import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
        long size = Files.size(path);
        if (size < 64 || size > LIMIT) throw bad();
        // Bound the read itself as a file may grow after the metadata check.
        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream((int)Math.min(size, 65536))) {
            byte[] buffer = new byte[65536]; int n, total = 0;
            while ((n = in.read(buffer)) != -1) {
                if (n > LIMIT - total) throw bad();
                total += n; bytes.write(buffer, 0, n);
            }
            return parse(bytes.toByteArray());
        }
    }

    /** Version metadata describes a build, not the installed CLR or license validity. */
    public static LicenseRecoverReadOnlyPeMetadata parse(byte[] data) throws IOException {
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
        return new LicenseRecoverReadOnlyPeMetadata(runtime, assembly, plus);
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
