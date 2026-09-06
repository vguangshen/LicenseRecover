/*
 * 移除联网授权验证代码 (暴力方式)
 *
 * 原理: 用 Javassist(应用自带 javassist-3.21.0-GA.jar) 直接改写字节码:
 *  1) RegisterUtil.checkRegister()   启动+每天定时校验的总入口 -> 直接标记"已注册"
 *  2) RegisterMain (ITMCReg.jar 内)  所有联网授权方法 -> 改为不联网的短路实现
 * 不依赖源码, 不改动程序其它逻辑。
 *
 * 注意: 若目标类仍是 Virbox BCE 保护(minor version=32768), 必须先脱壳,
 * 本类不会修改受保护的类(否则会破坏字节码)。
 */
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class NetRemover {

    public static final int MINOR_VIRBOX = 32768;
    static final String[] NET_MARKERS = {"checkRegNew", "getReditInfo", "checkNOReg", "regservice", "ITMCServiceLocator", "appReg"};
    // 方式三实际按方法名补丁的联网授权方法（与 RegisterMain 的方法对应）
    static final String[] PATCH_METHODS = {"CheckNet", "getNetRegInfo", "doNetRegistry",
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

    static String stamp() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    static void safeCopy(Path src, Path dst, Consumer<String> log) {
        try {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            log.accept("  已备份 -> " + dst.getFileName());
        } catch (Exception e) {
            log.accept("  [警告] 备份失败: " + e.getMessage());
        }
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
        log.accept("  启动注册校验总入口  RegisterUtil.class : " + (hasRu ? "找到" : "未找到(可能打包在jar内,将只处理授权jar)"));
        log.accept("  授权联网方法所在    " + (jarFile != null ? jarFile.getName() : "ITMCReg*.jar") + "\\RegisterMain.class : " + (hasJar ? "找到" : "未找到"));

        if (hasRu) {
            byte[] b = readClass(ruFile);
            boolean packed = isPacked(b);
            log.accept("    RegisterUtil.class minor=" + minorVersion(b) + (packed ? "  <- Virbox BCE 保护(执行时将自动脱壳)" : " (已脱壳)"));
        }
        if (hasJar) {
            byte[] b = readJarEntry(jarFile, RM_ENTRY);
            if (b != null) {
                boolean packed = isPacked(b);
                log.accept("    RegisterMain.class  minor=" + minorVersion(b) + (packed ? "  <- Virbox BCE 保护(执行时将自动脱壳)" : " (已脱壳)"));
                StringBuilder sb = new StringBuilder();
                for (String m : NET_MARKERS) if (hasMarker(b, m)) sb.append(m).append(" ");
                if (sb.length() > 0) log.accept("    含联网授权标记: " + sb.toString().trim());
                StringBuilder pm = new StringBuilder();
                for (String m : PATCH_METHODS) if (hasMarker(b, m)) pm.append(m).append(" ");
                if (pm.length() > 0) {
                    log.accept("    可补丁移除的联网授权方法: " + pm.toString().trim());
                    boolean already = isRmPatched(b, loadKeystream());
                    log.accept(already
                        ? "    ★ 该 RegisterMain 已补丁（联网授权代码已移除），无需重复修补。"
                        : "    ★ 无论是否已脱壳，只要含以上方法即可用「移除联网授权并回写」清理");
                } else {
                    log.accept("    未发现可补丁的联网授权方法（可能已清理过或非授权版本）");
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

    /** 移除联网授权验证代码。返回 true=成功。 */
    public static boolean removeNetValidation(String appRoot, Consumer<String> log) {
        String[] paths = findPaths(appRoot);
        if (paths == null) {
            log.accept("[错误] 未找到 ITMCReg*.jar（请选择应用根目录，即含 WEB-INF 的目录）");
            return false;
        }
        File libDir = new File(paths[0]);
        File classesDir = new File(paths[1]);
        File ruFile = new File(classesDir, RU_REL);
        File jarFile = findItmcRegJar(libDir);

        if (!scan(appRoot, log)) return false;

        boolean hasRu = ruFile.isFile();
        boolean hasJar = jarFile != null && jarFile.isFile();

        // 代码级判断是否已补丁：RegisterMain 联网标记已消失则跳过重复补丁；但若 getRegInfo 仍是原实现
        // （读 config.xml，应用启动会 NPE）或有残留 regservice 域名，则补充补丁/清洗重写。
        if (hasJar) {
            try {
                byte[] rmBytes = readRmClass(jarFile, loadKeystream());
                if (rmBytes != null && isRmPatched(rmBytes, loadKeystream())) {
                    if (supplementPatch(jarFile, new File(appRoot), log)) {
                        log.accept("  RegisterMain 已补丁；已补充 getRegInfo 永续授权 + 清洗 regservice（备份 " + jarFile.getName() + ".*.bak）");
                    } else {
                        log.accept("  RegisterMain 已补丁（联网授权代码已移除），无需重复修补。");
                    }
                    return true;
                }
            } catch (Exception ignore) { }
        }

        String st = stamp();
        log.accept("--- 备份原文件 ---");
        File jarBak = null;
        if (hasRu) safeCopy(ruFile.toPath(), new File(ruFile.getParentFile(), "RegisterUtil.class." + st + ".bak").toPath(), log);
        if (hasJar) {
            jarBak = new File(libDir, jarFile.getName() + "." + st + ".bak");
            safeCopy(jarFile.toPath(), jarBak.toPath(), log);
        }

        try {
            byte[] key = loadKeystream();

            // 1) RegisterUtil 若受 Virbox 保护: 先脱壳写回
            if (hasRu) {
                byte[] ru = readClass(ruFile);
                if (isPacked(ru)) {
                    log.accept("RegisterUtil.class 为 Virbox 保护(minor=32768)，自动脱壳...");
                    if (key == null) { log.accept("[错误] 缺少脱壳密钥流，无法自动脱壳"); return false; }
                    byte[] u = unpackVirbox(ru, key);
                    if (u == null) { log.accept("[错误] RegisterUtil 脱壳失败"); return false; }
                    Files.write(ruFile.toPath(), u);
                    log.accept("  RegisterUtil 脱壳成功");
                }
            }

            // 2) 读入 ITMCReg.jar 全部条目, 自动脱壳所有受保护类
            Map<String, byte[]> jar = null;
            if (hasJar) {
                jar = new LinkedHashMap<String, byte[]>();
                try (ZipInputStream zin = new ZipInputStream(new FileInputStream(jarFile))) {
                    ZipEntry e;
                    while ((e = zin.getNextEntry()) != null) {
                        String n = e.getName();
                        if (n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA"))) continue;
                        if (n.equals("META-INF/MANIFEST.MF")) continue;
                        jar.put(n, readAll(zin));
                    }
                }
                int unpackedCnt = 0;
                for (Map.Entry<String, byte[]> en : jar.entrySet()) {
                    if (en.getKey().endsWith(".class")) {
                        byte[] b = en.getValue();
                        if (isPacked(b)) {
                            if (key == null) { log.accept("[错误] 缺少脱壳密钥流，无法自动脱壳"); return false; }
                            byte[] u = unpackVirbox(b, key);
                            if (u == null) { log.accept("[错误] 脱壳失败: " + en.getKey()); return false; }
                            en.setValue(u);
                            unpackedCnt++;
                        }
                    }
                }
                if (unpackedCnt > 0) log.accept("已自动脱壳 ITMCReg.jar 内 " + unpackedCnt + " 个 Virbox 类");
            }

            // 3) ClassPool: JDK + classes目录 + 除ITMCReg外lib jar; ITMCReg 类用内存 makeClass(避免句柄占用导致无法替换)
            //    new ClassPool(true)=仅含 JDK 类、不含父池/应用 classpath：每次调用全新无状态残留，
            //    既避免 GUI 持久 JVM 连续处理不同应用时 getDefault() 单例池的 "frozen class" 问题，
            //    也避免 makeClass 因父池含同名类抛 "in a parent ClassPool"。
            ClassPool pool = new ClassPool(true);
            pool.appendClassPath(classesDir.getAbsolutePath());
            for (File f : libDir.listFiles()) {
                // 排除授权 jar（含 ITMCReg-*.jar 版本化文件名），其类改由内存 makeClass 提供，避免句柄占用
                boolean isRegJar = f.getName().toLowerCase().startsWith("itmcreg") && f.getName().toLowerCase().endsWith(".jar");
                if (f.isFile() && f.getName().endsWith(".jar") && !isRegJar) {
                    pool.appendClassPath(f.getAbsolutePath());
                }
            }
            if (jar != null) {
                for (Map.Entry<String, byte[]> en : jar.entrySet()) {
                    if (en.getKey().endsWith(".class")) pool.makeClass(new ByteArrayInputStream(en.getValue()));
                }
            }

            // 4) 补丁 RegisterUtil 注册总入口（不同版本方法名不同：checkRegister / registerMethod；缺失则跳过）
            log.accept("--- 补丁: 移除联网授权 ---");
            if (hasRu) {
                boolean ruPatched = false;
                for (String mn : new String[]{ "checkRegister", "registerMethod" }) {
                    try {
                        CtClass cu = pool.get("com.common.utils.RegisterUtil");
                        cu.getDeclaredMethod(mn).setBody(
                            "{ com.common.global.Global.REGISTER_FLAG = true; com.common.global.Global.AUTHORIZE_FLAG = true; }");
                        cu.writeFile(classesDir.getAbsolutePath());
                        log.accept("  已改 RegisterUtil." + mn + "() -> 直接标记已注册");
                        ruPatched = true;
                        break;
                    } catch (javassist.NotFoundException ex) { }
                }
                if (!ruPatched) log.accept("  跳过 RegisterUtil（该版本无 checkRegister/registerMethod 方法，仅补丁 RegisterMain）");
            }

            // 5) 补丁 RegisterMain 联网方法 + 写回 jar（逐方法容错：存在则补，缺失则跳过，至少补 1 个）
            if (hasJar) {
                CtClass rm = pool.get("itmc.regedit.RegisterMain");
                String[][] plans = {
                    {"CheckNet", "{ return Boolean.FALSE; }"},
                    {"getNetRegInfo", "{ return new itmc.regedit.webservice.RegeditInfo(); }"},
                    {"doNetRegistry", "{ return new itmc.regedit.webservice.ResultMs(); }"},
                    {"newAppNetRegistry", "{ return new itmc.regedit.webservice.ResultMs(); }"},
                    {"RegNOWebCheck", "{ return Boolean.FALSE; }"},
                    {"writeRegisterUser", "{ }"},
                    {"checkReInfo", "{ return false; }"},
                };
                int rmPatched = 0;
                for (String[] p : plans) {
                    try {
                        rm.getDeclaredMethod(p[0]).setBody(p[1]);
                        rmPatched++;
                        log.accept("  已改 " + p[0]);
                    } catch (javassist.NotFoundException ex) {
                        log.accept("  跳过（该版本无此方法）: " + p[0]);
                    } catch (Exception ex) {
                        // 方法存在但桩代码编译失败（返回类型/webservice 类与该版本不一致）——
                        // 若写回会造成部分联网方法仍生效，必须拒绝并提示
                        log.accept("[错误] 补丁 " + p[0] + " 失败（该版本方法签名与工具桩不匹配）: " + ex.getMessage());
                        log.accept("      已中止写回（避免留下未补丁的联网方法），请改用方式一。");
                        return false;
                    }
                }
                // getRegInfo → 永续授权信息（关键）：应用的 register.* 包装类（如 Spring Boot
                // RegisterListener/RegisterContant）启动时会调用 getRegInfo() 读 classCount/regStr；
                // 若它仍读 config.xml 且无有效本地授权则返回 null → 应用启动 NPE。
                // regStr 含应用 versionID，保证 hasRegister 判定通过。失败只跳过，不中止补丁。
                try {
                    CtMethod gri = rm.getDeclaredMethod("getRegInfo");
                    gri.setBody(buildPerpetualBody(detectRegStr(new File(appRoot))));
                    rmPatched++;
                    log.accept("  已改 getRegInfo -> 返回永续授权信息");
                } catch (javassist.NotFoundException ex) {
                    log.accept("  跳过（该版本无此方法）: getRegInfo");
                } catch (Exception ex) {
                    log.accept("  跳过 getRegInfo（该版本字段不兼容）: " + ex.getMessage());
                }
                if (rmPatched == 0) {
                    log.accept("[错误] 未补丁任何 RegisterMain 联网方法（方法名与该版本不匹配），拒绝写回。");
                    return false;
                }
                log.accept("  共补丁 " + rmPatched + " 个 RegisterMain 联网方法");
                byte[] newRm = rm.toBytecode();
                jar.put(RM_ENTRY, newRm);

                // 写回 jar: 删原->写新 (重试以避让杀软瞬时扫描)
                Path tmp = Files.createTempFile("ITMCReg", ".jar");
                int cleaned = 0;
                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
                    for (Map.Entry<String, byte[]> en : jar.entrySet()) {
                        byte[] data = en.getValue();
                        if (en.getKey().endsWith(".class") && hasMarker(data, "regservice")) {
                            byte[] c = blankRegserviceDomain(data);
                            if (c != null) { data = c; cleaned++; }
                        }
                        zos.putNextEntry(new ZipEntry(en.getKey()));
                        zos.write(data);
                        zos.closeEntry();
                    }
                }
                if (cleaned > 0)
                    log.accept("  已清理 " + cleaned + " 个类中的 regservice 域名（改为本地 inert 地址）");
                boolean replaced = false;
                for (int attempt = 0; attempt < 10 && !replaced; attempt++) {
                    try {
                        Files.delete(jarFile.toPath());
                        Files.move(tmp, jarFile.toPath());
                        replaced = true;
                    } catch (IOException ex) {
                        try {
                            Thread.sleep(600);
                        } catch (InterruptedException ie) {
                            // 中断只出现在外部线程中断时；恢复中断标志并继续重试，
                            // 绝不能跳出循环绕过下面的 .bak 恢复逻辑（delete 已成功但 move 未成功时 jar 已被删）
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                if (!replaced) {
                    // 若 delete 成功但 move 失败，原 jar 已被删除——先从备份恢复，避免应用丢失授权 jar
                    if (!jarFile.exists() && jarBak != null && jarBak.exists()) {
                        try {
                            Files.copy(jarBak.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            log.accept("  已从备份恢复 " + jarFile.getName() + "（写入失败，未改动）。");
                        } catch (IOException e2) {
                            log.accept("  [警告] 恢复备份失败: " + e2.getMessage());
                        }
                    }
                    Files.copy(tmp, Paths.get(jarFile.getAbsolutePath() + ".patched"), StandardCopyOption.REPLACE_EXISTING);
                    log.accept("  [警告] 无法覆盖 " + jarFile.getName() + "(可能被占用)，已输出 " + jarFile.getName() + ".patched");
                    log.accept("         请停止应用服务后重试，或用 .patched 手动替换。");
                    return false;
                }
                log.accept("  已改 RegisterMain 联网方法(CheckNet/getReditInfo/doNetRegistry/RegNOWebCheck/writeRegisterUser/checkReInfo)");
            }
        } catch (Exception e) {
            log.accept("[错误] 补丁失败: " + e);
            e.printStackTrace();
            return false;
        }

        // 验证
        log.accept("--- 验证: 联网授权标记应全部消失 ---");
        boolean allClean = true;
        if (hasRu) {
            byte[] after = readClass(ruFile);
            StringBuilder sb = new StringBuilder();
            for (String m : NET_MARKERS) if (hasMarker(after, m)) sb.append(m).append(" ");
            if (sb.length() > 0) { allClean = false; log.accept("  RegisterUtil 仍含: " + sb.toString().trim()); }
            else log.accept("  RegisterUtil.class  干净, minor=" + minorVersion(after));
        }
        if (hasJar) {
            byte[] after = readJarEntry(jarFile, RM_ENTRY);
            StringBuilder sb = new StringBuilder();
            for (String m : NET_MARKERS) if (hasMarker(after, m)) sb.append(m).append(" ");
            if (sb.length() > 0) { allClean = false; log.accept("  RegisterMain 仍含: " + sb.toString().trim()); }
            else log.accept("  RegisterMain.class  干净, minor=" + minorVersion(after));
        }

        if (allClean) {
            log.accept("RESULT: OK —— 联网授权代码已移除。重启应用即可正常使用，无需联网校验。");
            log.accept("        (备份文件: RegisterUtil.class.*.bak / ITMCReg.jar.*.bak)");
            return true;
        }
        log.accept("RESULT: FAILED —— 部分联网标记仍存在，请检查。");
        return false;
    }

    // ================= Virbox BCE 自动脱壳 =================

    static byte[] loadKeystream() {
        try (InputStream in = NetRemover.class.getResourceAsStream("/virbox_keystream.bin")) {
            if (in == null) return null;
            return readAll(in);
        } catch (Exception e) { return null; }
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

    /** 代码级判断 RegisterMain 是否已补丁：联网标记(regservice/checkRegNew/...)全部消失即已补丁。
        与验证段一致，比只查单一方法桩更可靠——部分补丁（还有残留标记）不会被误判为已补丁。 */
    public static boolean isRmPatched(byte[] rmClass, byte[] key) {
        try {
            byte[] cls = isPacked(rmClass) ? unpackVirbox(rmClass, key) : rmClass;
            if (cls == null) return false;
            for (String m : NET_MARKERS)
                if (hasMarker(cls, m)) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 从 ITMCReg.jar 中读取 RegisterMain.class（脱壳后返回）。 */
    static byte[] readRmClass(File jar, byte[] key) throws IOException {
        byte[] b = readJarEntry(jar, RM_ENTRY);
        if (b == null) return null;
        return isPacked(b) ? unpackVirbox(b, key) : b;
    }

    /** 已补丁的 jar：若 getRegInfo 仍是原实现（读 config.xml，应用启动会 NPE）则补丁为永续授权，
        同时清洗残留 regservice 域名。任一有变则写回，返回是否写回。 */
    static boolean supplementPatch(File jarFile, File appRoot, Consumer<String> log) {
        Map<String, byte[]> jar = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String n = e.getName();
                if (n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA"))) continue;
                if (n.equals("META-INF/MANIFEST.MF")) continue;
                jar.put(n, readAll(zin));
            }
        } catch (Exception ex) {
            return false;
        }
        byte[] rmBytes = jar.get(RM_ENTRY);
        if (rmBytes == null) return false;
        boolean changed = false;
        try {
            ClassPool pool = new ClassPool(true);   // 含 JDK（java.util.Calendar 等）
            for (Map.Entry<String, byte[]> en : jar.entrySet())
                if (en.getKey().endsWith(".class"))
                    pool.makeClass(new ByteArrayInputStream(en.getValue()));
            CtClass rm = pool.get("itmc.regedit.RegisterMain");
            try {
                CtMethod gri = rm.getDeclaredMethod("getRegInfo");
                int beforeLen = gri.getMethodInfo().getCodeAttribute() != null
                        ? gri.getMethodInfo().getCodeAttribute().getCodeLength() : -1;
                gri.setBody(buildPerpetualBody(detectRegStr(appRoot)));
                int afterLen = gri.getMethodInfo().getCodeAttribute() != null
                        ? gri.getMethodInfo().getCodeAttribute().getCodeLength() : -1;
                if (beforeLen != afterLen) {
                    jar.put(RM_ENTRY, rm.toBytecode());
                    changed = true;
                    log.accept("  getRegInfo 原实现读 config.xml（应用启动会 NPE）→ 已补丁为永续授权信息");
                }
            } catch (javassist.NotFoundException ex) {
                // 该版本无 getRegInfo，跳过
            } catch (Exception ex) {
                log.accept("  [警告] getRegInfo 补充补丁失败（该版本字段不兼容）: " + ex.getMessage());
            }
            // 清洗残留 regservice
            int cleaned = 0;
            for (Map.Entry<String, byte[]> en : jar.entrySet()) {
                if (en.getKey().endsWith(".class") && hasMarker(en.getValue(), "regservice")) {
                    byte[] c = blankRegserviceDomain(en.getValue());
                    if (c != null) { en.setValue(c); cleaned++; changed = true; }
                }
            }
            if (!changed) return false;
            try {
                Files.copy(jarFile.toPath(), new File(jarFile.getParentFile(), jarFile.getName() + "." + stamp() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                log.accept("  [警告] 备份失败: " + ex.getMessage());
            }
            Path tmp = Files.createTempFile("ITMCReg", ".jar");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
                for (Map.Entry<String, byte[]> en : jar.entrySet()) {
                    zos.putNextEntry(new ZipEntry(en.getKey()));
                    zos.write(en.getValue());
                    zos.closeEntry();
                }
            }
            Files.copy(tmp, jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (cleaned > 0) log.accept("  已清理 " + cleaned + " 个类中的 regservice 域名（改为本地 inert 地址）");
            return true;
        } catch (Exception ex) {
            log.accept("  [警告] 补充补丁失败: " + ex.getMessage());
            return false;
        }
    }

    /** 生成 getRegInfo 的永续授权体：默认构造器 + setter 设永续字段，regStr 含应用 versionID，proName 取首个 id。 */
    static String buildPerpetualBody(String regStr) {
        String rs = (regStr == null ? "" : regStr).replace("\"", "\\\"");
        String pro = rs.isEmpty() ? "" : rs.split(",")[0];
        return "{itmc.regedit.webservice.RegeditInfo r=new itmc.regedit.webservice.RegeditInfo();"
            + "r.setProName(\"" + pro + "\");"
            + "r.setRegID(\"1234567890123456\");"
            + "r.setRegStr(\"" + rs + "\");"
            + "r.setClassNum(-1);"
            + "r.setNet(false);"
            + "r.setTotalTimes(-1);"
            + "r.setUserTimes(0);"
            + "r.setMaxCon(-1);"
            + "r.setCountDay(0);"
            + "java.util.Calendar b=java.util.Calendar.getInstance();b.set(2000,0,1);r.setBeginDate(b);"
            + "java.util.Calendar e=java.util.Calendar.getInstance();e.set(2099,11,31);r.setEndDate(e);"
            + "return r;}";
    }

    /** 从应用 config.xml 提取 versionID 相关 id（SystemSoft/SoftVersionID + <System id>），用于 getRegInfo 的 regStr。
        覆盖 WEB-INF/classes、WEB-INF/lib、嵌套 WEB-INF、以及新架构 webapp 根 data/config.xml。 */
    static String detectRegStr(File appRoot) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (String rel : new String[]{"WEB-INF/classes/config.xml", "WEB-INF/lib/config.xml",
                "WEB-INF/WEB-INF/classes/config.xml", "WEB-INF/WEB-INF/lib/config.xml",
                "data/config.xml", "config.xml"}) {
            File f = new File(appRoot, rel);
            if (!f.isFile()) continue;
            try {
                String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("<SoftVersionID>([^<]+)</SoftVersionID>").matcher(s);
                if (m.find()) ids.add(m.group(1).trim());
                m = java.util.regex.Pattern.compile("<System\\s+id=\"([^\"]+)\"").matcher(s);
                while (m.find()) ids.add(m.group(1).trim());
            } catch (Exception e) { }
        }
        if (ids.isEmpty()) {
            // 兜底：DS 系列常见子产品 id + 常用 ITMC 产品，保证 hasRegister 判定通过
            return "DS50101,DS50105,DS50106,DS50109,DS50110,DS50111,DS50112,DS50113,DS50114,DS50115,"
                + "DS50116,DS50117,DS50118,DS50119,DS50120,DS50121,DS50122,DS50123,DS50124,DS50125,"
                + "DS50126,DS50127,DS50128,DS50129,DS50130,DS50131,DS50132,DS50133,DS50134,DS50135,"
                + "QT1001,PT0402,PT1002,YT00123,DS501,DS26";
        }
        return String.join(",", ids);
    }

    /** 把类常量池中所有含 regservice 的 UTF-8 字符串域名替换为 127.0.0.1。
        改长度会安全重建常量池（按索引引用不受字节偏移影响）。从最高偏移条目开始改，低偏移不变。
        返回 null 表示无 regservice 或解析失败（保持原样）。 */
    static byte[] blankRegserviceDomain(byte[] cls) {
        try {
            int[] p = {0};
            if (u4(cls, p) != 0xCAFEBABE) return null;
            u2(cls, p); u2(cls, p);   // minor, major
            int cpCount = u2(cls, p);
            int[] utf8Off = new int[cpCount];
            String[] utf8Val = new String[cpCount];
            int index = 1;
            while (index < cpCount) {
                int tag = u1(cls, p);
                if (tag == 1) {
                    int len = u2(cls, p);
                    utf8Off[index] = p[0] - 2;   // length 前缀偏移
                    utf8Val[index] = new String(cls, p[0], len, StandardCharsets.UTF_8);
                    p[0] += len;
                } else if (tag == 3 || tag == 4 || tag == 9 || tag == 10 || tag == 11 || tag == 12 || tag == 17 || tag == 18) {
                    p[0] += 4;
                } else if (tag == 5 || tag == 6) {
                    p[0] += 8; index += 1;
                } else if (tag == 7 || tag == 8 || tag == 16 || tag == 19 || tag == 20) {
                    p[0] += 2;
                } else if (tag == 15) {
                    p[0] += 3;
                } else {
                    return null;
                }
                index += 1;
            }
            java.util.List<Integer> toReplace = new ArrayList<>();
            for (int i = 1; i < cpCount; i++)
                if (utf8Val[i] != null && utf8Val[i].contains("regservice")) toReplace.add(i);
            if (toReplace.isEmpty()) return null;
            java.util.Collections.sort(toReplace, java.util.Collections.reverseOrder());
            byte[] cur = cls;
            for (int idx : toReplace) {
                String old = utf8Val[idx];
                // 只替换 URL/主机上下文里的 regservice，避免误改类名/方法名等常量池字符串
                String neu = null;
                if (old.contains("regservice.itmc.cn")) {
                    neu = old.replace("regservice.itmc.cn", "127.0.0.1");
                } else if (old.contains("://regservice")) {
                    neu = old.replace("://regservice", "://127.0.0.1");
                } else if (old.startsWith("regservice") && old.length() > "regservice".length()
                        && ":/.".indexOf(old.charAt("regservice".length())) >= 0) {
                    // 裸主机 "regservice:port" / "regservice/path" / "regservice.xxx"
                    neu = "127.0.0.1" + old.substring("regservice".length());
                }
                if (neu == null || neu.equals(old)) continue;
                int off = utf8Off[idx];
                int tagOff = off - 1;
                byte[] oldB = old.getBytes(StandardCharsets.UTF_8);
                byte[] newB = neu.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream out = new ByteArrayOutputStream(cur.length + (newB.length - oldB.length));
                out.write(cur, 0, tagOff);
                out.write(1);   // CONSTANT_Utf8 tag
                out.write((newB.length >> 8) & 0xFF);
                out.write(newB.length & 0xFF);
                out.write(newB);
                out.write(cur, tagOff + 3 + oldB.length, cur.length - (tagOff + 3 + oldB.length));
                cur = out.toByteArray();
            }
            return cur;
        } catch (Exception e) {
            return null;
        }
    }
}
