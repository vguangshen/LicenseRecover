/** Shared registration inspection and packed-class loading utilities. */
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NetRemover {

    public static final int MINOR_VIRBOX = 32768;
    static final String[] NET_MARKERS = {"checkRegNew", "getReditInfo", "checkNOReg", "regservice", "ITMCServiceLocator", "appReg"};
    // Registration method names used only for read-only inspection.
    static final String[] REGISTRATION_METHODS = {"CheckNet", "getNetRegInfo", "doNetRegistry",
        "newAppNetRegistry", "RegNOWebCheck", "writeRegisterUser", "checkReInfo", "getRegInfo"};
    static final String RU_REL = "com/common/utils/RegisterUtil.class";
    static final String RM_ENTRY = "itmc/regedit/RegisterMain.class";

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    static boolean hasMarker(byte[] data, String s) {
        if (data == null) return false;
        byte[] m = s.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= data.length - m.length; i++) {
            for (int j = 0; j < m.length; j++) if (data[i + j] != m[j]) continue outer;
            return true;
        }
        return false;
    }

    static int minorVersion(byte[] cls) {
        if (cls == null || cls.length < 6) return -1;
        return ((cls[4] & 0xFF) << 8) | (cls[5] & 0xFF);
    }

    static boolean isPacked(byte[] cls) {
        return cls != null && minorVersion(cls) == MINOR_VIRBOX;
    }

    /** 在 lib 目录中定位授权 jar（ITMCReg.jar 或带版本号的 ITMCReg-*.jar），找不到返回 null。 */
    static File findItmcRegJar(File lib) {
        if (lib == null || !lib.isDirectory()) return null;
        File exact = new File(lib, "ITMCReg.jar");
        if (exact.isFile()) return exact;
        File[] fs = lib.listFiles((d, n) -> n.toLowerCase().startsWith("itmcreg") && n.toLowerCase().endsWith(".jar"));
        if (fs != null && fs.length > 0) return fs[0];
        return null;
    }

    /** 在应用根目录中定位 {lib目录, classes目录}。兼容嵌套 WEB-INF 等结构。找不到返回 null。 */
    static String[] findPaths(String appRoot) {
        File root = new File(appRoot);
        String[][] rels = {
            {"WEB-INF", "lib"}, {"WEB-INF", "WEB-INF", "lib"}, {"lib"}, {""}
        };
        for (String[] rel : rels) {
            File d = root;
            for (String part : rel) if (!part.isEmpty()) d = new File(d, part);
            if (findItmcRegJar(d) != null) {
                File lib = d;
                File webinf = lib.getParentFile();
                File classes;
                if (webinf != null && "WEB-INF".equals(webinf.getName())) {
                    // 标准 WEB-INF/lib 或嵌套 WEB-INF/WEB-INF/lib：classes 是 webinf/classes
                    classes = new File(webinf, "classes");
                    if (!new File(classes, RU_REL).isFile() && webinf.getParentFile() != null) {
                        File c2 = new File(webinf.getParentFile(), "classes");
                        if (new File(c2, RU_REL).isFile()) classes = c2;
                    }
                } else {
                    // jar 直接放在所选目录（rel={} 分支）：classes 是该目录下的 classes（或目录本身）
                    classes = new File(lib, "classes");
                    if (!new File(classes, RU_REL).isFile() && new File(lib, RU_REL).isFile()) classes = lib;
                }
                return new String[]{lib.getAbsolutePath(), classes.getAbsolutePath()};
            }
        }
        File cur = root;
        while (cur != null) {
            File lib = new File(cur, "WEB-INF" + File.separator + "lib");
            if (findItmcRegJar(lib) != null) {
                return new String[]{lib.getAbsolutePath(), new File(lib.getParentFile(), "classes").getAbsolutePath()};
            }
            cur = cur.getParentFile();
        }
        return null;
    }

    /** 识别并报告联网授权验证文件。返回是否有目标文件。 */
    public static boolean scan(String appRoot, Consumer<String> log) {
        String[] paths = findPaths(appRoot);
        if (paths == null) {
            log.accept("  [错误] 未找到 ITMCReg*.jar（请选择应用根目录，即含 WEB-INF 的目录）");
            return false;
        }
        File lib = new File(paths[0]);
        File classes = new File(paths[1]);
        File ruFile = new File(classes, RU_REL);
        File jarFile = findItmcRegJar(lib);

        log.accept("  定位: " + (jarFile != null ? jarFile.getAbsolutePath() : lib.getAbsolutePath()));
        boolean hasRu = ruFile.isFile();
        boolean hasJar = jarFile != null && jarFile.isFile();
        log.accept("  启动注册校验总入口  RegisterUtil.class : " + (hasRu ? "找到" : "未找到(可能打包在jar内)"));
        log.accept("  授权联网方法所在    " + (jarFile != null ? jarFile.getName() : "ITMCReg*.jar") + "\\RegisterMain.class : " + (hasJar ? "找到" : "未找到"));

        if (hasRu) {
            byte[] b = readClass(ruFile);
            boolean packed = isPacked(b);
            log.accept("    RegisterUtil.class minor=" + minorVersion(b) + (packed ? "  <- Virbox BCE 保护" : " (已脱壳)"));
        }
        if (hasJar) {
            byte[] b = readJarEntry(jarFile, RM_ENTRY);
            if (b != null) {
                boolean packed = isPacked(b);
                log.accept("    RegisterMain.class  minor=" + minorVersion(b) + (packed ? "  <- Virbox BCE 保护" : " (已脱壳)"));
                StringBuilder sb = new StringBuilder();
                for (String m : NET_MARKERS) if (hasMarker(b, m)) sb.append(m).append(" ");
                if (sb.length() > 0) log.accept("    含联网授权标记: " + sb.toString().trim());
                StringBuilder pm = new StringBuilder();
                for (String m : REGISTRATION_METHODS) if (hasMarker(b, m)) pm.append(m).append(" ");
                if (pm.length() > 0) {
                    log.accept("    可识别的注册方法: " + pm.toString().trim());
                    // Missing strings are only a heuristic: an original build may
                    // never have contained these markers. No original-version
                    // fingerprint or reviewed patch signature is checked here.
                    log.accept("    ★ 只读扫描：发现可识别的注册方法，但不能确认已 Patch。");
                    log.accept("      需要匹配已审核的原版指纹与补丁字节码特征；联网标记缺失不等于 Patch 成功。");
                } else {
                    log.accept("    未发现已知注册方法（保持未验证）");
                }
            } else {
                log.accept("    [警告] jar 内未找到 RegisterMain.class");
            }
        }
        if (!hasRu && !hasJar) {
            log.accept("  [错误] 未找到任何授权验证文件，请确认选择的是应用根目录(含 WEB-INF)");
            return false;
        }
        return true;
    }

    static byte[] readClass(File f) {
        try { return Files.readAllBytes(f.toPath()); } catch (Exception e) { return null; }
    }

    static byte[] readJarEntry(File jar, String entry) {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals(entry)) return readAll(zin);
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    /** Compatibility guard for callers compiled against the retired write API. */
    @Deprecated
    public static boolean removeNetValidation(String appRoot, Consumer<String> log) {
        if (log != null) log.accept(SafeNetRemoverCLI.REMOVED_MESSAGE);
        return false;
    }

    static byte[] loadKeystream() {
        try (InputStream in = NetRemover.class.getResourceAsStream("/virbox_keystream.bin")) {
            if (in != null) return readAll(in);
        } catch (Exception ignore) { /* Try user-supplied local keystream below. */ }
        // Protected PT/other Java generations may need a locally supplied
        // keystream. Never embed or publish the user's binary in the repository.
        // Resolve relative to this running tool, not the target app directory.
        try {
            File location = new File(NetRemover.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File toolDirectory = location.isFile() ? location.getParentFile() : location;
            if (toolDirectory == null) return null;
            for (String name : new String[]{"virbox_keystream.bin", "virbox_keystream_exact_22937.bin"}) {
                File candidate = new File(toolDirectory, name);
                if (candidate.isFile() && candidate.length() > 0 && candidate.length() <= 1024L * 1024L)
                    return Files.readAllBytes(candidate.toPath());
            }
        } catch (Exception ignore) { /* Fail closed: no key available. */ }
        return null;
    }

    static int u1(byte[] b, int[] p) { return b[p[0]++] & 0xFF; }

    static int u2(byte[] b, int[] p) {
        int v = ((b[p[0]] & 0xFF) << 8) | (b[p[0] + 1] & 0xFF);
        p[0] += 2;
        return v;
    }

    static int u4(byte[] b, int[] p) {
        int v = ((b[p[0]] & 0xFF) << 24) | ((b[p[0] + 1] & 0xFF) << 16)
              | ((b[p[0] + 2] & 0xFF) << 8) | (b[p[0] + 3] & 0xFF);
        p[0] += 4;
        return v;
    }

    /** Virbox BCE 脱壳: 恢复 minor=0，并用密钥流从0开始逐字节 XOR 还原每个 Code 属性。失败返回 null。 */
    static byte[] unpackVirbox(byte[] cls, byte[] key) {
        try {
            int[] p = {0};
            if (u4(cls, p) != 0xCAFEBABE) return null;
            u2(cls, p); // minor
            u2(cls, p); // major
            int cpCount = u2(cls, p);
            String[] cp = new String[cpCount];
            int index = 1;
            while (index < cpCount) {
                int tag = u1(cls, p);
                if (tag == 1) {
                    int len = u2(cls, p);
                    cp[index] = new String(cls, p[0], len, StandardCharsets.UTF_8);
                    p[0] += len;
                } else if (tag == 3 || tag == 4 || tag == 9 || tag == 10 || tag == 11 || tag == 12 || tag == 17 || tag == 18) {
                    p[0] += 4;
                } else if (tag == 5 || tag == 6) {
                    p[0] += 8;
                    index += 1;
                } else if (tag == 7 || tag == 8 || tag == 16 || tag == 19 || tag == 20) {
                    p[0] += 2;
                } else if (tag == 15) {
                    p[0] += 3;
                } else {
                    return null;
                }
                index += 1;
            }
            p[0] += 6; // access_flags, this_class, super_class
            int ifaces = u2(cls, p);
            for (int i = 0; i < ifaces; i++) p[0] += 2;
            int fields = u2(cls, p);
            for (int i = 0; i < fields; i++) {
                p[0] += 6; // access_flags, name_index, descriptor_index
                int fattr = u2(cls, p);
                for (int j = 0; j < fattr; j++) {
                    int ni = u2(cls, p);  // name_index
                    int alen = u4(cls, p); // attribute_length
                    p[0] += alen;          // 跳过属性数据
                }
            }
            java.util.List<int[]> spans = new ArrayList<>();
            int methods = u2(cls, p);
            for (int i = 0; i < methods; i++) {
                p[0] += 6; // access_flags, name_index, descriptor_index
                int mattr = u2(cls, p);
                for (int j = 0; j < mattr; j++) {
                    int nameIndex = u2(cls, p);
                    int attrLen = u4(cls, p);
                    int attrStart = p[0];
                    if (nameIndex < cp.length && "Code".equals(cp[nameIndex])) {
                        p[0] += 4; // max_stack, max_locals
                        int codeLen = u4(cls, p);
                        if (codeLen <= attrLen - 8 && codeLen >= 0) {
                            spans.add(new int[]{p[0], codeLen});
                        }
                    }
                    p[0] = attrStart;
                    p[0] += attrLen;
                }
            }
            byte[] out = cls.clone();
            out[4] = 0; out[5] = 0; // 恢复 minor=0
            for (int[] span : spans) {
                int cs = span[0], clen = span[1];
                if (clen > key.length) return null;
                if (cs < 0 || cs + clen > out.length) return null;
                for (int i = 0; i < clen; i++) out[cs + i] ^= key[i];
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ================= jar 级脱壳（方式一在打包 jar 上无法加载应用类时自动执行） =================

    /** 判断 ITMCReg.jar 是否含 Virbox 保护类。 */
    public static boolean jarIsPacked(File jar) {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().endsWith(".class")) {
                    byte[] b = readAll(zin);
                    if (isPacked(b)) return true;
                }
            }
        } catch (Exception e) { /* ignore */ }
        return false;
    }

    /** 把 jar 内所有类脱壳写入 outDir（保持包结构），返回脱壳类数量。 */
    public static int unpackPackedJar(File jar, File outDir) throws IOException {
        byte[] key = loadKeystream();
        if (key == null) throw new IOException("缺少 virbox_keystream.bin（脱壳密钥流）");
        int n = 0;
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (name.endsWith(".class")) {
                    byte[] b = readAll(zin);
                    if (isPacked(b)) {
                        byte[] u = unpackVirbox(b, key);
                        if (u == null) throw new IOException("脱壳失败: " + name);
                        b = u;
                        n++;
                    }
                    File out = new File(outDir, name);
                    out.getParentFile().mkdirs();
                    Files.write(out.toPath(), b);
                }
            }
        }
        return n;
    }

}
