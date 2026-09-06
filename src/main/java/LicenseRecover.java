/*
 * ITMC 云实训平台 - 离线授权恢复工具
 *
 * 背景: 厂商跑路 / 授权服务器关闭后，已购软件因联网授权校验无法使用。
 * Java 版使用软件自身支持的"本地授权"通道 (config.xml regType=1)，
 * 生成一份永续本地授权并写入 WEB-INF/lib/config.xml；.NET 版方式一
 * 只修改授权配置文件中的服务地址，防止软件自动联网校验，不改 DLL。
 * DS01xx 这类旧版 ASP.NET 应用的方式二单独使用旧协议适配器：申请号
 * 用 itmcsoft 解码，授权码用 itmc + 应用 ProName 生成，避免误用新版 JSON 协议。
 *
 * 生成过程全部复用应用自带的类 (itmc.regedit.* / fastjson)，
 * 保证授权格式与应用预期的完全一致。
 *
 * 用法:  java -cp "<应用根目录>\WEB-INF\lib\*;LicenseRecover.jar" LicenseRecover [应用根目录]
 * 可选:  -p <产品主编号>   强制指定产品主编号(默认自动识别, YT00123 系默认 QT1001)
 *        --dry-run         只预览，不修改文件（.NET 方式一/三均为预览）
 *
 * 方式二(生成离线授权码, 走应用"本地注册"界面):
 *  java -cp "...\WEB-INF\lib\*;LicenseRecover.jar" LicenseRecover --gencode [应用根目录] [--seq <申请号>]
 *  Java/独立 .NET 可不传 --seq 在本机生成申请号；ASP.NET 需先从网站本地注册页取得申请号。
 *  把申请号和输出的授权码填入应用注册界面即可激活。
 *
 * .NET 方式一(防止软件自动联网校验):
 *  java -jar LicenseRecover.jar --block-net [应用根目录或 bin 目录]
 *  修改应用实际使用的授权配置，将服务地址指向本机不可用端口，不修改 DLL。
 *
 * 方式三(暴力: 直接移除联网授权代码, 用 Javassist 改写字节码):
 *  java -cp "...\WEB-INF\lib\*;LicenseRecover.jar" LicenseRecover --remove-net [应用根目录]   # 扫描+移除
 *  java -cp "...\WEB-INF\lib\*;LicenseRecover.jar" LicenseRecover --scan-net [应用根目录]     # 仅扫描识别
 *  注意: .NET 方式三只处理 bin\itmcRegedit.dll；目标类若是 Virbox BCE 保护(minor=32768)
 *  需先脱壳; 执行前请停止应用服务。
 */
import itmc.regedit.DesUtil;
import itmc.regedit.GetRegisterCode;
import itmc.regedit.RegisterMain;
import itmc.regedit.XMLFiles;
import itmc.regedit.webservice.RegeditInfo;
import com.alibaba.fastjson.JSON;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

public class LicenseRecover {

    /** 日志文件流（所有输出同时写控制台和 logs 目录下的日志文件） */
    static java.io.PrintStream teeOut, teeErr;

    /** 是否把授权服务地址指向本地 inert 端口（reg/Service）。默认 true；--no-block-net 可关。 */
    static boolean blockNet = true;

    /** .NET 方式一使用的本地不可用端口；只阻断授权服务请求，不改写 DLL。 */
    static final String DOTNET_BLOCK_ENDPOINT = "http://127.0.0.1:9/Service.asmx";
    static final java.util.regex.Pattern REG_BLOCK_PATTERN =
            java.util.regex.Pattern.compile("(?is)<reg\\b[^>]*>.*?</reg\\s*>");
    static final java.util.regex.Pattern SERVICE_ELEMENT_PATTERN =
            java.util.regex.Pattern.compile("(?is)(<Service\\b[^>]*>)(.*?)(</Service\\s*>)");
    static final java.util.regex.Pattern REG_CLOSE_PATTERN =
            java.util.regex.Pattern.compile("(?m)([ \\t]*)(</reg\\s*>)");
    static final java.util.regex.Pattern ROOT_CLOSE_PATTERN =
            java.util.regex.Pattern.compile("(?m)([ \\t]*)(</ROOT\\s*>)");
    /** .NET 生成的 SOAP/WCF 配置中使用的授权服务地址。只替换授权域名，不碰其它服务（例如 AI 服务）。 */
    static final java.util.regex.Pattern DOTNET_REMOTE_SERVICE_PATTERN =
            java.util.regex.Pattern.compile("(?i)https?://regservice\\.itmc\\.cn(?::\\d+)?(?:/[^\\s<>'\\x22]*)?");

    /** 是否在写入前备份原 config.xml。默认 true；--no-backup 可关。 */
    static boolean backupCfg = true;

    /** 本地授权写入 regName 的运行时 UserID；与外层 WebSerUserID 是两个独立字段。 */
    static final String LOCAL_AUTH_USER_ID = "fwq";

    static void applyLocalAuthIdentity(RegeditInfo info) {
        if (info == null) throw new IllegalArgumentException("RegeditInfo 不能为空");
        info.setUserID(LOCAL_AUTH_USER_ID);
    }

    /** 初始化日志：之后所有 System.out/err 都会写入 logs/LicenseRecover_日期.log。 */
    static void initLog() {
        try {
            java.io.File dir = new java.io.File(jarDir(), "logs");
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, "LicenseRecover_"
                    + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".log");
            java.io.OutputStream shared = new java.io.FileOutputStream(f, true);
            teeOut = new TeePrintStream(System.out, shared);
            teeErr = new TeePrintStream(System.err, shared);
            System.setOut(teeOut);
            System.setErr(teeErr);
        } catch (Exception ignore) { }
    }

    /** 把输出同时送往原流(控制台)和日志文件，日志行带时间戳（print 缓冲、println 输出整行到文件）。 */
    static class TeePrintStream extends PrintStream {
        private final PrintStream orig;
        private final java.io.OutputStream file;
        private final StringBuilder lineBuf = new StringBuilder();
        private final SimpleDateFormat ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        TeePrintStream(PrintStream orig, java.io.OutputStream file) { super(file, true); this.orig = orig; this.file = file; }
        @Override public void print(String x) { orig.print(x); lineBuf.append(x); }
        @Override public void print(Object x) { print(String.valueOf(x)); }
        @Override public void print(char[] x) { orig.print(x); lineBuf.append(x); }
        @Override public void print(int x) { print(String.valueOf(x)); }
        @Override public void print(long x) { print(String.valueOf(x)); }
        @Override public void print(boolean x) { print(String.valueOf(x)); }
        @Override public void print(double x) { print(String.valueOf(x)); }
        @Override public void print(float x) { print(String.valueOf(x)); }
        @Override public void print(char x) { print(String.valueOf(x)); }
        @Override public void write(int b) { orig.write(b); lineBuf.append((char) b); }
        @Override public void println(String x) {
            orig.println(x);
            lineBuf.append(x);
            emit();
        }
        @Override public void println(Object x) { println(String.valueOf(x)); }
        @Override public void println(int x) { println(String.valueOf(x)); }
        @Override public void println(long x) { println(String.valueOf(x)); }
        @Override public void println(boolean x) { println(String.valueOf(x)); }
        @Override public void println(double x) { println(String.valueOf(x)); }
        @Override public void println(float x) { println(String.valueOf(x)); }
        @Override public void println(char x) { println(String.valueOf(x)); }
        @Override public void println() { orig.println(); emit(); }
        private void emit() {
            try {
                byte[] line = ("[" + ts.format(new Date()) + "] " + lineBuf.toString() + "\n").getBytes("UTF-8");
                file.write(line);
                file.flush();
            } catch (Exception ignore) { }
            lineBuf.setLength(0);
        }
    }

    // 覆盖所有产品/子产品的授权串，保证 AUTHORIZE_FLAG 一定能命中
    static final String ALL_NUMS =
        "QT100103,QT100107,QT100105,QT100109,QT100112,QT100108,"
      + "QT0436,QT0421,QT0420,QT0437,QT0424,QT0438,QT0435,QT0445,QT0441,ZGZF11,"
      + "PT0212,PT0208,PT0210,QT0454,PT0202,QT0456,PT0301,QT0447,PT0303,"
      + "YT00124,YT00125,YT00142,YT00143,YT00123,YT00147,YT00148,YT00149,YT00150,"
      + "YT00128,YT00127,YT00129,YT00139,YT00132,YT00141,BKSM4,YT00126,YT00154,YT001";

    public static void main(String[] args) throws Exception {
        initLog();
        // 批量模式：父目录下每个子目录（一个产品代号）逐个恢复
        if (hasArg(args, "--batch")) {
            String parent = getArg(args, "--batch");
            if (parent == null || parent.trim().isEmpty()) {
                System.err.println("用法: --batch <父目录>    （父目录下每个子目录是一个 ITMC 应用）");
                return;
            }
            batchMain(args, parent.trim());
            return;
        }
        // 先识别应用类型：.NET 版 → 派发给内嵌 C# 助手（LicenseRecover.NET.exe）
        String appArg0 = firstPositional(args);
        if (appArg0 != null) {
            String dotnetBin = locateDotNetBin(appArg0);
            if (dotnetBin != null) {
                dotNetMain(args, dotnetBin);
                return;
            }
        }
        // Java 版需要应用 classpath 的路径（方式一/方式二）：若应用类不在当前 classpath，自动重跑
        // （打包 jar 自动脱壳）；方式三（--remove-net/--scan-net）用最小 classpath，不在此列。
        if (!hasArg(args, "--remove-net") && !hasArg(args, "--scan-net")) {
            maybeReexecForAppClasspath(args, appArg0);
        }
        for (String a : args) {
            if ("--gencode".equals(a)) {
                System.exit(genCodeMode(args));
                return;
            }
        }
        for (String a : args) {
            if ("--remove-net".equals(a)) {
                System.exit(removeNetMode(args, true));
                return;
            }
            if ("--scan-net".equals(a)) {
                System.exit(removeNetMode(args, false));
                return;
            }
        }
        String productMain = "QT1001";
        boolean dryRun = false;
        String appArg = null;

        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                productMain = args[++i];
            } else if ("--dry-run".equals(args[i])) {
                dryRun = true;
            } else if ("--no-block-net".equals(args[i])) {
                blockNet = false;
            } else if ("--no-backup".equals(args[i])) {
                backupCfg = false;
            } else if (appArg == null) {
                appArg = args[i];
            }
        }

        String productOverride = hasProductOverride(args) ? productMain : null;
        int rc = recoverJavaSingle(appArg, productOverride, dryRun);
        if (rc != 0) System.exit(rc);
    }

    /**
     * Java 版需要应用 classpath 的路径（方式一/方式二）：若 itmc.regedit 不在当前 classpath，
     * 自动用应用 WEB-INF/lib 重跑；ITMCReg.jar 带 Virbox 壳则先自动脱壳。已可用时直接返回。
     */
    static void maybeReexecForAppClasspath(String[] args, String appArg) {
        // 重跑守卫：若已是重跑进程（lrc.reexec=1）仍加载不到 itmc.regedit，说明该 lib 的 ITMCReg*.jar
        // 损坏或为空壳，直接放弃重跑，避免无限循环。
        if ("1".equals(System.getProperty("lrc.reexec"))) return;
        try { Class.forName("itmc.regedit.DesUtil"); return; } catch (Throwable ignore) { }
        if (appArg == null) return;
        String libDir = locateLibDir(appArg);
        if (libDir == null) return;
        try {
            File jarFile = findItmcRegJar(new File(libDir));
            String cp;
            Path tmp = null;
            if (jarFile != null && NetRemover.jarIsPacked(jarFile)) {
                tmp = Files.createTempDirectory("itmc_unpack");
                int n = NetRemover.unpackPackedJar(jarFile, tmp.toFile());
                System.out.println("[脱壳] " + jarFile.getName() + " 含 " + n + " 个 Virbox 保护类，自动脱壳后重跑 ...");
                cp = tmp.toAbsolutePath() + File.pathSeparator + libDir + File.separator + "*"
                        + File.pathSeparator + jarDir() + File.separator + "LicenseRecover.jar";
            } else {
                cp = libDir + File.separator + "*" + File.pathSeparator + jarDir() + File.separator + "LicenseRecover.jar";
            }
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
            cmd.add("-Dfile.encoding=UTF-8");
            cmd.add("-Dlrc.reexec=1");
            cmd.add("-cp");
            cmd.add(cp);
            cmd.add("LicenseRecover");
            for (String a : args) cmd.add(a);   // 原样传参（含 --gencode/--seq/-p/--dry-run）
            int rc = runDotNetProcess(cmd);
            if (tmp != null) deleteRecursive(tmp.toFile());
            System.exit(rc);
        } catch (Exception ex) {
            System.err.println("[错误] 自动准备应用 classpath 失败: " + ex.getMessage());
            System.out.println("RESULT: FAILED");
            System.exit(1);
        }
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    // ================= 单个 Java 应用恢复（方式一） =================

    /** 对单个 Java 应用执行方式一。productOverride 为空则按 systemConfig.yml 自动识别。返回 0=成功。 */
    static int recoverJavaSingle(String appArg, String productOverride, boolean dryRun) {
        String libDir = locateLibDir(appArg);
        String appRoot = new File(libDir).getParentFile().getParentFile().getAbsolutePath();
        String productMain = (productOverride != null && !productOverride.trim().isEmpty())
                ? productOverride.trim() : "QT1001";
        boolean newStyle = isNewStyleApp(appRoot);

        // 自动识别产品号
        String softId = readSoftId(appRoot);
        if (productOverride == null || productOverride.trim().isEmpty()) {
            if (softId != null && !softId.trim().isEmpty()) {
                if (newStyle) {
                    // 新架构 QT3xxx：加密密钥直接用原始产品号（应用按 PRODUCT_ALL_NUM 逐个尝试匹配 config.xml）
                    productMain = softId.trim();
                    System.out.println("[识别] 新架构(data/config.xml 存在)，产品号 = " + productMain);
                } else {
                    String mapped = productMainFor(softId);
                    System.out.println("[识别] systemConfig.yml VersionID = " + softId + "  -> 产品主编号 " + mapped);
                    productMain = mapped;
                }
            } else {
                System.out.println("[识别] 未找到产品号配置文件，使用默认产品主编号 " + productMain);
            }
        }

        System.out.println("======================================================");
        System.out.println(" ITMC 离线授权恢复工具");
        System.out.println("======================================================");
        System.out.println("应用根目录       : " + appRoot);
        System.out.println("lib 目录         : " + libDir);
        System.out.println("产品主编号       : " + productMain);

        // 1. 主板号
        String sn = DesUtil.getMotherboardSN();
        System.out.println("主机码(主板号)   : " + sn);
        if (sn.length() != 16) {
            System.out.println("  ! 注意: 主机码长度 " + sn.length() + " (期望16)，请确认在目标服务器上运行");
        }

        // 2. 构造永续授权信息 (与厂商 doRegistry 生成的 RegeditInfo 同构)
        RegeditInfo info = new RegeditInfo();
        applyLocalAuthIdentity(info);
        info.setProName(productMain);
        info.setRegID(sn);
        info.setTotalTimes(-1);        // -1 = 不限制连接数
        info.setUserTimes(0);
        info.setMaxCon(-1);            // -1 = 不限并发
        info.setClassNum(-1);          // -1 = 不限班级数
        info.setNet(false);            // 不要求联网
        info.setCountDay(0);
        info.setRegStr(ALL_NUMS);
        Calendar begin = Calendar.getInstance();
        begin.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
        Calendar end = Calendar.getInstance();
        end.set(2099, Calendar.DECEMBER, 31, 23, 59, 59);
        info.setBeginDate(begin);
        info.setEndDate(end);

        // 3. 序列化 + 加密 (完全复用应用自身的类与流程)
        Random rnd = new Random();
        int srd  = rnd.nextInt(999999) % 900000 + 100000;
        int srd2 = rnd.nextInt(999999) % 900000 + 100000;
        String plain = srd + JSON.toJSONString(info) + srd2;
        GetRegisterCode rc = new GetRegisterCode();
        String key = "*ITMC" + productMain + "OK*";
        String encrypted = rc.encrypt(key, plain);
        if (encrypted == null) {
            System.err.println("加密失败");
            System.out.println("RESULT: FAILED");
            return 1;
        }
        System.out.println("本地注册密钥     : " + key);
        System.out.println("授权密文长度     : " + encrypted.length() + " 字节(hex)");

        File cfg = new File(libDir, "config.xml");
        if (dryRun) {
            System.out.println("--- 模拟写入 (dry-run) ---");
            System.out.println("  写 reg/regType      = 1");
            System.out.println("  写 reg/regName      = " + (encrypted.length() > 48 ? encrypted.substring(0, 48) + "..." : encrypted));
            System.out.println("  regName.UserID       = " + LOCAL_AUTH_USER_ID);
            System.out.println("  写 reg/WebSerUserID = itmc");
            if (blockNet) System.out.println("  写 reg/Service      = http://127.0.0.1:9/Service.asmx  (拦截残留联网请求)");
            System.out.println("目标文件           : " + cfg.getAbsolutePath());
            if (newStyle) System.out.println("目标文件(新架构webapp根) : " + new File(appRoot, "config.xml").getAbsolutePath());
            System.out.println("RESULT: dry-run 完成，未写入任何文件。");
            return 0;
        }

        // 4. 备份原 config.xml
        File libBak = null;
        if (backupCfg && cfg.exists()) {
            String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            libBak = new File(libDir, "config.xml." + stamp + ".bak");
            try { Files.copy(cfg.toPath(), libBak.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception e) { System.out.println("[警告] 备份失败: " + e.getMessage()); }
            System.out.println("已备份原配置       : " + libBak.getName());
        }
        if (backupCfg && newStyle) {
            File cfgRoot = new File(appRoot, "config.xml");
            if (cfgRoot.exists()) {
                String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                File bak = new File(appRoot, "config.xml." + stamp + ".bak");
                try { Files.copy(cfgRoot.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                catch (Exception e) { System.out.println("[警告] 备份失败: " + e.getMessage()); }
                System.out.println("已备份原配置(webapp根): " + bak.getName());
            }
        }

        // 5. 写入本地授权 (注意: XMLFiles/RegisterMain 的路径需以分隔符结尾，与应用运行时行为一致)
        try {
            writeRegConfig(libDir, encrypted);
            System.out.println("已写入            : " + cfg.getAbsolutePath());
            if (newStyle) {
                // 新架构 QT3xxx：应用在 webapp 根读 config.xml（RegisterMain basePath=getRealPath("/")）
                try {
                    writeRegConfig(appRoot, encrypted);
                    System.out.println("已写入(新架构webapp根): " + new File(appRoot, "config.xml").getAbsolutePath());
                } catch (Exception e2) {
                    // appRoot 写失败：回滚 libDir 已写的授权，避免 FAILED 却留下半改状态
                    System.err.println("写入 webapp 根 config.xml 失败: " + e2.getMessage());
                    if (libBak != null && libBak.exists()) {
                        try { Files.copy(libBak.toPath(), cfg.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("已从备份恢复 " + cfg.getName() + "（避免半改状态）。"); }
                        catch (Exception e3) { System.out.println("[警告] 恢复备份失败: " + e3.getMessage()); }
                    }
                    System.out.println("RESULT: FAILED");
                    return 1;
                }
            }
        } catch (Exception e) {
            System.err.println("写入 config.xml 失败: " + e.getMessage());
            System.out.println("RESULT: FAILED");
            return 1;
        }

        // 6. 用应用自身的 RegisterMain.checkReInfo() 自校验（新架构对 webapp 根再做一次）
        String jsonForMain = new GetRegisterCode().encrypt(productMain + "RegeditNew", "itmcsoft");
        String[] checkDirs = newStyle ? new String[]{ libDir, appRoot } : new String[]{ libDir };
        boolean anyOk = false;
        for (String cd : checkDirs) {
            String cdSep = cd.endsWith(File.separator) ? cd : cd + File.separator;
            try {
                RegisterMain reg = newRegisterMain(productMain, jsonForMain, cdSep);
                boolean unregistered = reg.checkReInfo();   // false = 注册有效
                RegeditInfo gi = reg.getRegInfo();
                if (gi != null) {
                    System.out.println("授权详情(" + cd + ")    : regID=" + gi.getRegID()
                        + "  endDate=" + (gi.getEndDate() == null ? null : gi.getEndDate().getTime())
                        + "  totalTimes=" + gi.getTotalTimes()
                        + "  classNum=" + gi.getClassNum()
                        + "  maxCon=" + gi.getMaxCon() + "  userID=" + gi.getUserID());
                }
                System.out.println("checkReInfo()(" + cd + ") : " + unregistered + "  (false=注册有效)");
                if (!unregistered) anyOk = true;
            } catch (Exception e) {
                // 单个目录校验异常不阻断另一个目录（新架构有 libDir + appRoot 两处）
                System.err.println("自校验异常(" + cd + "): " + e.getMessage() + "（继续尝试其它目录）");
            }
        }
        if (anyOk) {
            System.out.println("RESULT: OK  —— 离线授权已生效，重启应用即可正常使用，联网校验不再触发。");
            return 0;
        }
        System.out.println("RESULT: FAILED —— 授权未生效，请把输出发给我排查。");
        return 1;
    }

    /**
     * 构造应用自身的 RegisterMain。优先 3 参构造器(显式指定 config 路径，新架构 webapp 根需要)；
     * 部分版本只有 2 参构造器(路径取 getComfigPath()=jar 所在 WEB-INF/lib)，反射回退。
     */
    static RegisterMain newRegisterMain(String product, String json, String path) {
        try {
            return new RegisterMain(product, json, path);
        } catch (NoSuchMethodError e) {
            try {
                Class<?> rm = Class.forName("itmc.regedit.RegisterMain");
                java.lang.reflect.Constructor<?> c = rm.getConstructor(String.class, String.class);
                return (RegisterMain) c.newInstance(product, json);
            } catch (Exception e2) {
                throw new RuntimeException("RegisterMain 构造器不兼容（需 3 参或 2 参）: " + e2, e2);
            }
        }
    }

    /** 把永续授权 reg 段写入指定目录的 config.xml。 */
    static void writeRegConfig(String dir, String encrypted) throws Exception {
        String sep = dir.endsWith(File.separator) ? dir : dir + File.separator;
        XMLFiles xml = new XMLFiles("config.xml", sep);
        xml.writeConfig("reg", "regType", "1");
        xml.writeConfig("reg", "regName", encrypted);
        xml.writeConfig("reg", "WebSerUserID", "itmc");
        // 将授权服务地址指向本地 inert 端口，杜绝任何残留的到 regservice.itmc.cn 的请求（--no-block-net 可关）
        if (blockNet) xml.writeConfig("reg", "Service", DOTNET_BLOCK_ENDPOINT);
    }

    // ================= 方式二: 生成离线授权码 (走应用"本地注册"界面) =================

    /** 生成 (申请号, 授权码, 主板号, 申请时间)。seq 为空则在本机自动生成申请号。 */
    static String[] genRegisterCode(String seq, String registerProductID) throws Exception {
        GetRegisterCode rc = new GetRegisterCode();
        if (seq == null || seq.trim().isEmpty()) {
            DesUtil d = new DesUtil();
            String data = d.getRandom() + d.getMotherboardSN() + d.getRandom() + d.getCurrentTime() + d.getRandom();
            seq = new GetRegisterCode().buildCiphertext(data);
        }
        String myRegNO = rc.decrypt("itmcsoft", seq);
        if (myRegNO == null || myRegNO.length() < 43) {
            throw new Exception("无法解析注册申请号（申请号格式不正确或已损坏）");
        }
        String sn = myRegNO.substring(4, 20);
        String time19 = myRegNO.substring(24, 43);          // "yyyy-MM-dd HH-mm-ss"
        String end = "2099-12-31";
        // 授权码明文固定格式: [0:2]固定 [2:21]申请时间 [23:39]主板号 [41:51]截止 [53:54]联网标志
        //   [56:60]连接数(-001=-1不限) [62:64]班级数(-1不限) [66:]产品串
        String plain = "00" + time19 + "00" + sn + "00" + end + "00" + "1" + "00" + "-001" + "00" + "-1" + "00" + ALL_NUMS;
        String code = rc.encrypt("itmc" + registerProductID, plain);
        return new String[]{seq, code, sn, time19};
    }

    static int genCodeMode(String[] args) throws Exception {
        String seq = null;
        String registerPid = "YT001";
        String appArg = null;
        boolean pGiven = false;
        for (int i = 0; i < args.length; i++) {
            if ("--gencode".equals(args[i])) continue;
            if ("--seq".equals(args[i]) && i + 1 < args.length) {
                seq = args[++i];
            } else if ("-p".equals(args[i]) && i + 1 < args.length) {
                registerPid = args[++i];
                pGiven = true;
            } else if (appArg == null) {
                appArg = args[i];
            }
        }
        // -p 指定优先；否则按应用识别。新架构直接用原始产品号，经典仍用 YT001/DS26。
        if (appArg != null && !pGiven) {
            String softId = readSoftId(appArg);
            if (softId != null && !softId.trim().isEmpty()) {
                if (isNewStyleApp(appArg)) {
                    registerPid = softId.trim();
                    System.out.println("[识别] 新架构(data/config.xml 存在)，注册码产品号 = " + registerPid);
                } else {
                    registerPid = "DS2601".equals(softId) ? "DS26" : "YT001";
                    System.out.println("[识别] systemConfig.yml VersionID = " + softId + "  -> 注册码产品号 " + registerPid);
                }
            }
        }
        System.out.println("======================================================");
        System.out.println(" 生成离线授权码 (应用注册界面 -> 本地注册)");
        System.out.println("======================================================");
        System.out.println("注册码产品号       : " + registerPid);
        try {
            boolean hasSeq = seq != null && !seq.trim().isEmpty();
            String[] r = genRegisterCode(seq, registerPid);
            String genSeq = r[0], code = r[1], sn = r[2], time19 = r[3];
            String localSn = DesUtil.getMotherboardSN();
            if (hasSeq) {
                if (sn.equals(localSn)) {
                    System.out.println("说明               : 申请号来自本机，授权码将绑定本机主机码 " + sn);
                } else {
                    System.out.println("说明               : 申请号主机码 " + sn + " 与本机(" + localSn + ")不同——");
                    System.out.println("                    这是跨机器使用：授权码将绑定申请号所属的那台服务器，属正常。");
                }
                System.out.println("主板号             : " + sn + "   申请时间: " + time19);
                System.out.println("--- 在服务器应用「本地注册」界面粘贴以下授权码并提交 ---");
                System.out.println("    (申请号用服务器页面上获取的那个，无需再填本工具输出的申请号)");
                System.out.println("离线授权码        : " + code);
                System.out.println("RESULT: OK —— 复制上面的 离线授权码 到服务器注册页面的授权码输入框提交即可。");
            } else {
                System.out.println("说明               : 未提供申请号，授权码绑定本机主机码 " + sn);
                System.out.println("                    仅能在本机激活；若目标软件在云服务器，请加 --seq 传服务器的申请号");
                System.out.println("主板号             : " + sn + "   申请时间: " + time19);
                System.out.println("--- 以下填入本机应用注册界面 ---");
                System.out.println("注册申请号        : " + genSeq);
                System.out.println("离线授权码        : " + code);
                System.out.println("RESULT: OK —— 请把上面的 注册申请号 + 离线授权码 填入本机应用的「本地注册」界面并提交。");
            }
        } catch (Exception e) {
            System.err.println("生成失败: " + e.getMessage());
            System.out.println("RESULT: FAILED");
            return 1;
        }
        return 0;
    }

    // ================= 方式三: 移除联网授权代码 (暴力) =================

    static int removeNetMode(String[] args, boolean doPatch) {
        String appArg = null;
        for (int i = 0; i < args.length; i++) {
            if ("--remove-net".equals(args[i]) || "--scan-net".equals(args[i])) continue;
            if ("-p".equals(args[i]) || "--seq".equals(args[i]) || "--product".equals(args[i])) {
                if (i + 1 < args.length) i++;   // 跳过选项及其值
                continue;
            }
            if (appArg == null) appArg = args[i];
        }
        if (appArg == null) appArg = new File(".").getAbsolutePath();
        File root = new File(appArg);
        String appRoot;
        if (new File(root, "WEB-INF").isDirectory()) {
            appRoot = root.getAbsolutePath();
        } else {
            String lib = locateLibDir(root.getAbsolutePath());
            if (lib == null) {
                System.err.println("未找到 WEB-INF 目录: " + appArg);
                return 1;
            }
            appRoot = new File(lib).getParentFile().getParentFile().getAbsolutePath();
        }
        System.out.println("======================================================");
        System.out.println(doPatch ? " 移除联网授权代码 (暴力方式)" : " 扫描联网授权验证文件");
        System.out.println("======================================================");
        System.out.println("应用根目录 : " + appRoot);
        System.out.println();
        if (doPatch) {
            boolean ok = NetRemover.removeNetValidation(appRoot, s -> System.out.println(s));
            System.out.println();
            System.out.println(ok ? "RESULT: OK —— 联网授权代码已移除" : "RESULT: FAILED");
            return ok ? 0 : 1;
        } else {
            NetRemover.scan(appRoot, s -> System.out.println(s));
        }
        return 0;
    }

    static boolean hasProductOverride(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i])) return true;
        }
        return false;
    }

    static String readSoftId(String appRoot) {
        // 1) systemConfig.yml VersionID（经典 + 新架构均可能）
        try {
            File yml = new File(appRoot, "systemConfig.yml");
            if (yml.exists()) {
                for (String line : Files.readAllLines(yml.toPath(), StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.toLowerCase().contains("versionid")) {
                        // 兼容 "global.system.VersionID: xxx"（冒号）与 "global.system.VersionID=xxx"（等号）
                        int sep = line.indexOf(':');
                        if (sep < 0) sep = line.indexOf('=');
                        if (sep > 0) return line.substring(sep + 1).trim();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        // 2) data/config.xml SystemSoft/SoftVersionID（新架构 QT3xxx 的产品号来源）
        try {
            File cfg = new File(appRoot, "data" + File.separator + "config.xml");
            if (cfg.exists()) {
                for (String line : Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("<SoftVersionID>")) {
                        int end = t.lastIndexOf('<');
                        if (end > 0) return t.substring(t.indexOf('>') + 1, end).trim();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** 新架构 QT3xxx 标志：webapp 根存在 data/config.xml（其 SystemSoft/SoftVersionID 决定产品号与 PRODUCT_ALL_NUM）。 */
    static boolean isNewStyleApp(String appRoot) {
        return new File(appRoot, "data" + File.separator + "config.xml").isFile();
    }

    // 与 Global.registerProductBeans 的首个命中规则保持一致
    static String productMainFor(String softId) {
        switch (softId) {
            case "YT00128": case "YT00127": case "YT00129":
            case "YT00139": case "YT00132": case "YT00141":
            case "YT00126": case "YT00154": case "BKSM4":
                return "QT04";
            default:
                return "QT1001"; // YT00123/124/125/142/143/147/148/149/150 及未知
        }
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

    static String locateLibDir(String appArg) {
        if (appArg != null && !appArg.trim().isEmpty()) {
            // 给定路径: 自身是 lib / 是 WEB-INF\lib / 是应用根目录，或向上逐级寻找
            File f = new File(appArg);
            File cur = f.isFile() ? f.getParentFile() : f;
            while (cur != null) {
                File lib = cur.getName().equalsIgnoreCase("lib") ? cur
                         : new File(cur, "WEB-INF" + File.separator + "lib");
                if (findItmcRegJar(lib) != null) {
                    return lib.getAbsolutePath();
                }
                // 嵌套 WEB-INF/WEB-INF/lib（QT100107 等）
                File nested = new File(cur, "WEB-INF" + File.separator + "WEB-INF" + File.separator + "lib");
                if (findItmcRegJar(nested) != null) {
                    return nested.getAbsolutePath();
                }
                cur = cur.getParentFile();
            }
            System.err.println("未找到包含 ITMCReg*.jar 的 lib 目录: " + appArg);
            System.exit(2);
        }
        File cur = new File(".").getAbsoluteFile();
        while (cur != null) {
            File lib = new File(cur, "WEB-INF" + File.separator + "lib");
            if (findItmcRegJar(lib) != null) {
                return lib.getAbsolutePath();
            }
            File nested = new File(cur, "WEB-INF" + File.separator + "WEB-INF" + File.separator + "lib");
            if (findItmcRegJar(nested) != null) {
                return nested.getAbsolutePath();
            }
            cur = cur.getParentFile();
        }
        System.err.println("未找到 WEB-INF\\lib\\ITMCReg*.jar，请传入应用根目录作为参数。");
        System.exit(2);
        return null;
    }

    // ================= .NET 版支持（派发给内嵌 C# 助手） =================

    static String firstPositional(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) {
                // 带取值的选项：跳过其值，避免把 -p QT1001 / --seq <hex> 的值误当应用路径
                if (isValueOption(a) && i + 1 < args.length && !args[i + 1].startsWith("-")) i++;
                continue;
            }
            return a;
        }
        return null;
    }

    /** 取值型选项：-p/--seq/--product（--product=xx 内联形式不消耗下一个参数）。 */
    static boolean isValueOption(String a) {
        return "-p".equals(a) || "--seq".equals(a) || "--product".equals(a);
    }

    /** 定位 .NET 应用的 bin 目录（需同时含 ITMC.Web.dll 与 itmcRegedit.dll）。给定目录或其父级。 */
    static String locateDotNetBin(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File f = new File(path);
        File cur = f.isFile() ? f.getParentFile() : f;
        while (cur != null) {
            if (hasDotNetFiles(cur)) {
                return cur.getAbsolutePath();
            }
            File bin = cur.getName().equalsIgnoreCase("bin") ? cur : new File(cur, "bin");
            if (hasDotNetFiles(bin)) {
                return bin.getAbsolutePath();
            }
            cur = cur.getParentFile();
        }
        return null;
    }

    /** 当前 .NET 版本固定使用小写授权程序集，避免误改同目录的大写兼容程序集。 */
    static boolean hasDotNetFiles(File dir) {
        return dir != null && dir.isDirectory()
                && new File(dir, "ITMC.Web.dll").isFile()
                && new File(dir, "itmcRegedit.dll").isFile();
    }

    /** LicenseRecover.jar（或类目录）所在目录，兼容 jar 与解包目录两种运行方式。 */
    static String jarDir() {
        try {
            File loc = new File(LicenseRecover.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (loc.isFile()) return loc.getParent();
            return loc.getAbsolutePath();
        } catch (Exception e) {
            return ".";
        }
    }

    static File locateNetHelper() {
        String dir = jarDir();
        File exe = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.exe");
        if (exe.isFile()) return exe;
        File exe2 = new File(dir, "LicenseRecover.NET.exe");
        return exe2.isFile() ? exe2 : null;
    }

    static String getArg(String[] args, String key) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith(key + "=")) return args[i].substring(key.length() + 1);
            if (args[i].equals(key) && i + 1 < args.length) return args[i + 1];
        }
        return null;
    }

    static boolean hasArg(String[] args, String key) {
        for (String a : args) if (a.equals(key)) return true;
        return false;
    }

    /** 运行 .NET 助手进程，透传其 stdout（UTF-8），返回退出码。 */
    static int runDotNetProcess(java.util.List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) System.out.println(line);
            return p.waitFor();
        } catch (Exception e) {
            System.err.println("[错误] 调用 .NET 助手失败: " + e.getMessage());
            System.out.println("RESULT: FAILED");
            return 1;
        }
    }

    /** 判断 .NET bin 是否属于 ASP.NET 应用（应用根目录存在 Web.config）。 */
    static boolean isAspNetDotNetBin(String binDir) {
        if (binDir == null || binDir.trim().isEmpty()) return false;
        File bin = new File(binDir).getAbsoluteFile();
        File root = "bin".equalsIgnoreCase(bin.getName()) && bin.getParentFile() != null
                ? bin.getParentFile() : bin;
        return new File(root, "Web.config").isFile();
    }

    /** 删除助手自校验生成的空占位 XML；用户原有文件不触碰。 */
    static void cleanupGeneratedDotNetFiles(String binDir, boolean hadConfig, boolean hadRegister) {
        if (binDir == null) return;
        if (!hadConfig) deleteEmptyGeneratedXml(new File(binDir, "config.xml"));
        if (!hadRegister) deleteEmptyGeneratedXml(new File(binDir, "Register.xml"));
    }

    static void deleteEmptyGeneratedXml(File file) {
        if (!file.isFile()) return;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").replace("\r\n", "\n").trim();
            if ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root>\n</root>".equals(text))
                file.delete();
        } catch (Exception ignore) { }
    }

    /** 获取 .NET 应用根目录；常规部署为 root\\bin，独立程序也允许 DLL 直接位于根目录。 */
    static File dotNetAppRoot(String binDir) {
        File bin = new File(binDir).getAbsoluteFile();
        return "bin".equalsIgnoreCase(bin.getName()) && bin.getParentFile() != null
                ? bin.getParentFile() : bin;
    }

    static void addDotNetConfigFile(java.util.List<File> files, java.util.Set<String> seen, File file) {
        if (file == null || !file.isFile()) return;
        try {
            String key = file.getCanonicalPath().toLowerCase(java.util.Locale.ROOT);
            if (seen.add(key)) files.add(file);
        } catch (Exception e) {
            String key = file.getAbsolutePath().toLowerCase(java.util.Locale.ROOT);
            if (seen.add(key)) files.add(file);
        }
    }

    static void addDotNetSidecarConfigs(java.util.List<File> files, java.util.Set<String> seen, File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File file : children) {
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".dll.config") || name.endsWith(".exe.config"))
                addDotNetConfigFile(files, seen, file);
        }
    }

    /** .NET 方式一实际使用的配置文件：包含自定义 config.xml、网站/程序配置和 DLL 旁的配置。 */
    static java.util.List<File> locateDotNetConfigFiles(String binDir) {
        java.util.List<File> files = new java.util.ArrayList<File>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        File bin = new File(binDir).getAbsoluteFile();
        File root = dotNetAppRoot(binDir);
        File rootCfg = new File(root, "config.xml");
        File binCfg = new File(bin, "config.xml");
        if (isAspNetDotNetBin(binDir)) {
            addDotNetConfigFile(files, seen, rootCfg);
            addDotNetConfigFile(files, seen, new File(root, "Web.config"));
        } else if (binCfg.isFile()) {
            addDotNetConfigFile(files, seen, binCfg);
        } else if (rootCfg.isFile()) {
            addDotNetConfigFile(files, seen, rootCfg);
        }
        addDotNetConfigFile(files, seen, new File(root, "Web.config"));
        addDotNetSidecarConfigs(files, seen, root);
        addDotNetSidecarConfigs(files, seen, bin);
        return files;
    }

    /** 在 XML 的 reg 节点中写入/更新 Service，保留原有 XML 排版和其它配置。 */
    static String replaceDotNetService(String xml) {
        if (xml == null) return null;
        String lineSep = xml.contains("\r\n") ? "\r\n" : "\n";
        java.util.regex.Matcher regs = REG_BLOCK_PATTERN.matcher(xml);
        int fallbackStart = -1, fallbackEnd = -1;
        int selectedStart = -1, selectedEnd = -1;
        while (regs.find()) {
            String block = regs.group();
            if (fallbackStart < 0 || block.toLowerCase(java.util.Locale.ROOT).contains("<regtype")) {
                fallbackStart = regs.start();
                fallbackEnd = regs.end();
            }
            if (SERVICE_ELEMENT_PATTERN.matcher(block).find()) {
                selectedStart = regs.start();
                selectedEnd = regs.end();
                break;
            }
        }
        if (selectedStart < 0) {
            selectedStart = fallbackStart;
            selectedEnd = fallbackEnd;
        }
        if (selectedStart >= 0) {
            String block = xml.substring(selectedStart, selectedEnd);
            java.util.regex.Matcher service = SERVICE_ELEMENT_PATTERN.matcher(block);
            String updatedBlock;
            if (service.find()) {
                updatedBlock = block.substring(0, service.start())
                        + service.group(1) + DOTNET_BLOCK_ENDPOINT + service.group(3)
                        + block.substring(service.end());
            } else {
                java.util.regex.Matcher close = REG_CLOSE_PATTERN.matcher(block);
                if (!close.find()) return null;
                String closeIndent = close.group(1);
                String childIndent = closeIndent + "  ";
                String insertion = lineSep + childIndent + "<Service>" + DOTNET_BLOCK_ENDPOINT
                        + "</Service>" + lineSep + closeIndent + close.group(2);
                updatedBlock = block.substring(0, close.start()) + insertion + block.substring(close.end());
            }
            return xml.substring(0, selectedStart) + updatedBlock + xml.substring(selectedEnd);
        }

        // 极少数 .NET 程序没有预置 reg 节点，补到 ROOT 末尾；不改变其它授权字段。
        java.util.regex.Matcher rootClose = ROOT_CLOSE_PATTERN.matcher(xml);
        if (!rootClose.find()) return null;
        String rootIndent = rootClose.group(1);
        String childIndent = rootIndent + "  ";
        String insertion = lineSep + rootIndent + "<reg>" + lineSep + childIndent
                + "<Service>" + DOTNET_BLOCK_ENDPOINT + "</Service>" + lineSep
                + rootIndent + "</reg>" + lineSep + rootIndent + rootClose.group(2);
        return xml.substring(0, rootClose.start()) + insertion + xml.substring(rootClose.end());
    }

    /** 更新 Web.config、*.exe.config、*.dll.config 中的授权域名，保留其它服务地址。 */
    static String replaceDotNetServiceUrls(String xml) {
        if (xml == null) return null;
        return DOTNET_REMOTE_SERVICE_PATTERN.matcher(xml).replaceAll(DOTNET_BLOCK_ENDPOINT);
    }

    static String updateDotNetConfig(File cfg, String xml) {
        return "config.xml".equalsIgnoreCase(cfg.getName())
                ? replaceDotNetService(xml) : replaceDotNetServiceUrls(xml);
    }

    /** 写入前验证 XML，避免方式一留下格式损坏的配置。 */
    static void validateDotNetXml(String xml) throws Exception {
        String body = xml != null && xml.startsWith("\uFEFF") ? xml.substring(1) : xml;
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignore) { }
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignore) { }
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignore) { }
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    static File backupDotNetConfig(File cfg) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File bak = new File(cfg.getParentFile(), cfg.getName() + "." + stamp + ".bak");
        int n = 1;
        while (bak.exists()) bak = new File(cfg.getParentFile(), cfg.getName() + "." + stamp + "-" + (n++) + ".bak");
        Files.copy(cfg.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return bak;
    }

    /** .NET 方式一：只阻断配置中的授权服务地址，不生成授权、不修改任何 DLL。 */
    static int blockDotNetNet(String binDir, boolean dryRun, boolean backup) {
        java.util.List<File> configs = locateDotNetConfigFiles(binDir);
        if (configs.isEmpty()) {
            System.err.println("[错误] 未找到可处理的 .NET 授权配置（请确认 bin、config.xml 或 Web.config 存在）");
            System.out.println("RESULT: FAILED");
            return 1;
        }
        java.util.List<String> originals = new java.util.ArrayList<String>();
        java.util.List<String> updates = new java.util.ArrayList<String>();
        try {
            for (File cfg : configs) {
                String original = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
                boolean bom = original.startsWith("\uFEFF");
                String body = bom ? original.substring(1) : original;
                String updated = updateDotNetConfig(cfg, body);
                if (updated == null) throw new Exception("无法定位配置节点: " + cfg.getAbsolutePath());
                if (bom) updated = "\uFEFF" + updated;
                validateDotNetXml(updated);
                originals.add(original);
                updates.add(updated);
            }
            for (int i = 0; i < configs.size(); i++) {
                File cfg = configs.get(i);
                String original = originals.get(i);
                String updated = updates.get(i);
                if (original.equals(updated)) {
                    System.out.println("已是阻断状态（无需修改）: " + cfg.getAbsolutePath());
                    continue;
                }
                if (dryRun) {
                    System.out.println("--- 模拟写入（dry-run，未修改文件） ---");
                    System.out.println("将修改: " + cfg.getAbsolutePath());
                    System.out.println("  授权服务地址 = " + DOTNET_BLOCK_ENDPOINT);
                    continue;
                }
                if (backup) {
                    File bak = backupDotNetConfig(cfg);
                    System.out.println("已备份原配置: " + bak.getAbsolutePath());
                }
                Files.write(cfg.toPath(), updated.getBytes(StandardCharsets.UTF_8));
                System.out.println("已写入: " + cfg.getAbsolutePath());
                System.out.println("  授权服务地址 = " + DOTNET_BLOCK_ENDPOINT);
            }
            System.out.println(dryRun
                    ? "RESULT: dry-run 完成，未修改 DLL 或配置文件。"
                    : "RESULT: OK —— .NET 自动联网授权校验已由配置阻断，未修改 DLL。重启应用服务后生效。");
            return 0;
        } catch (Exception e) {
            System.err.println("[错误] 写入 .NET 阻断配置失败: " + e.getMessage());
            System.out.println("RESULT: FAILED");
            return 1;
        }
    }

    /** .NET 版主入口：根据参数拼助手命令并执行。 */
    static void dotNetMain(String[] args, String binDir) {
        String mode = "block-net";
        if (hasArg(args, "--block-net")) mode = "block-net";
        else if (hasArg(args, "--gencode")) mode = "gencode";
        else if (hasArg(args, "--remove-net")) mode = "patch";
        else if (hasArg(args, "--scan-net")) mode = "scan";

        if ("block-net".equals(mode)) {
            System.out.println("检测到 .NET 版应用（bin 目录: " + binDir + "），方式一：防止软件自动联网校验");
            int rc = blockDotNetNet(binDir, hasArg(args, "--dry-run"), !hasArg(args, "--no-backup"));
            if (rc != 0) System.exit(rc);
            return;
        }

        // DS01xx 的 ASP.NET 注册页使用旧版固定字段协议，不能交给
        // 默认按 YX0302/新版产品号生成 JSON 授权码的 C# 助手。
        if ("gencode".equals(mode) && LegacyDotNetProtocol.isLegacyTarget(new File(binDir))) {
            int rc = legacyDotNetCodeMode(args, binDir);
            if (rc != 0) System.exit(rc);
            return;
        }

        File helper = locateNetHelper();
        if (helper == null) {
            System.err.println("[错误] 未找到 LicenseRecover.NET.exe（应在 LicenseRecover.jar 旁的 LicenseRecover.NET 子目录）");
            System.out.println("RESULT: FAILED");
            System.exit(1);
            return;
        }
        String seq = getArg(args, "--seq");
        if ("gencode".equals(mode) && isAspNetDotNetBin(binDir)
                && (seq == null || seq.trim().isEmpty())) {
            System.err.println("[提示] ASP.NET 项目不能在工具进程内自动取申请号；请先在网站的本地注册页获取申请号，再用 --seq 生成离线授权码。");
            System.out.println("RESULT: FAILED");
            System.exit(2);
            return;
        }

        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(helper.getAbsolutePath());
        cmd.add(mode);
        cmd.add(binDir);
        String product = getArg(args, "-p");
        if (product != null) { cmd.add("--product"); cmd.add(product); }
        if (seq != null) { cmd.add("--seq"); cmd.add(seq); }
        if (hasArg(args, "--dry-run")) cmd.add("--dry-run");
        if (hasArg(args, "--no-block-net")) cmd.add("--no-block-net");

        System.out.println("检测到 .NET 版应用（bin 目录: " + binDir + "），使用内嵌 C# 助手 " + mode + " ...");
        boolean hadConfig = new File(binDir, "config.xml").isFile();
        boolean hadRegister = new File(binDir, "Register.xml").isFile();
        int rc = runDotNetProcess(cmd);
        cleanupGeneratedDotNetFiles(binDir, hadConfig, hadRegister);
        if (rc != 0) System.exit(rc);
    }

    /** DS01xx 旧协议方式二：只生成授权码，实际提交仍由应用自己的注册页完成。 */
    static int legacyDotNetCodeMode(String[] args, String binDir) {
        String seq = getArg(args, "--seq");
        String version = LegacyDotNetProtocol.readSoftVersion(new File(binDir));
        System.out.println("检测到 .NET 旧协议应用（版本: " + (version == null ? "DS01xx" : version)
                + "，产品标识: " + LegacyDotNetProtocol.PRODUCT_NAME + "）");
        if (seq == null || seq.trim().isEmpty()) {
            System.err.println("[提示] DS01xx 旧协议必须先在应用「本地注册」页获取申请号，再用 --seq 生成离线授权码。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        try {
            LegacyDotNetProtocol.CodeResult result =
                    LegacyDotNetProtocol.generateAuthorizationCode(seq.trim(), version);
            System.out.println("旧协议申请号      : " + result.request.ciphertext);
            System.out.println("申请主机码        : " + result.request.regId);
            System.out.println("申请时间          : " + result.request.requestTime);
            System.out.println("授权截止日期      : " + result.endDate);
            System.out.println("离线授权码        : " + result.code);
            System.out.println("RESULT: OK —— 将上面的离线授权码粘贴到该 DS01xx 应用「本地注册」页提交。");
            return 0;
        } catch (Exception e) {
            System.err.println("[错误] DS01xx 旧协议申请号解析/授权码生成失败: " + e.getMessage());
            System.out.println("RESULT: FAILED");
            return 1;
        }
    }

    // ================= 批量模式 =================

    static class AppTarget {
        String name, type;    // type: "JAVA" / "DOTNET"
        String appRoot;       // Java: 应用根（含 WEB-INF 的目录）
        String libDir;        // Java: WEB-INF/lib
        String binDir;        // .NET: bin
        AppTarget(String name, String type, String appRoot, String libDir, String binDir) {
            this.name = name; this.type = type; this.appRoot = appRoot; this.libDir = libDir; this.binDir = binDir;
        }
    }

    /** 仅检测给定目录自身（或其 bin/WEB-INF 子目录）是否为一个 ITMC 应用。
        返回 {type, 关键目录}: JAVA=lib 目录, DOTNET=bin 目录；或 null。
        .NET 以 ITMC.Web.dll+itmcRegedit.dll 为标志。 */
    static String[] detectAppLocal(File dir) {
        if (hasDotNetFiles(dir))
            return new String[]{ "DOTNET", dir.getAbsolutePath() };
        File bin = new File(dir, "bin");
        if (hasDotNetFiles(bin))
            return new String[]{ "DOTNET", bin.getAbsolutePath() };
        for (String rel : new String[]{ "WEB-INF", "WEB-INF" + File.separator + "WEB-INF" }) {
            File lib = new File(dir, rel + File.separator + "lib");
            if (findItmcRegJar(lib) != null)
                return new String[]{ "JAVA", lib.getAbsolutePath() };
        }
        return null;
    }

    /** 扫描父目录下每个子目录：ITMC 应用入列；非 ITMC 目录也列入并标 NONE（执行时跳过并显示）。 */
    static java.util.List<AppTarget> scanBatch(String parentDir) {
        java.util.List<AppTarget> apps = new java.util.ArrayList<>();
        File parent = new File(parentDir);
        File[] subs = parent.listFiles();
        if (subs == null) return apps;
        java.util.Arrays.sort(subs);
        for (File sub : subs) {
            if (!sub.isDirectory()) continue;
            // 顶层残留目录（WEB-INF/classes 等）不是产品，跳过
            if ("WEB-INF".equals(sub.getName()) || "classes".equals(sub.getName())
                    || "META-INF".equals(sub.getName())) continue;
            String[] d = detectAppLocal(sub);
            if (d == null) {
                apps.add(new AppTarget(sub.getName(), "NONE", null, null, null));
                continue;
            }
            if ("DOTNET".equals(d[0])) apps.add(new AppTarget(sub.getName(), "DOTNET", null, null, d[1]));
            else {
                File libDir = new File(d[1]);
                File appRoot = libDir.getParentFile() != null ? libDir.getParentFile().getParentFile() : libDir;
                apps.add(new AppTarget(sub.getName(), "JAVA", appRoot.getAbsolutePath(), libDir.getAbsolutePath(), null));
            }
        }
        return apps;
    }

    /** 批量对单个 Java 应用派生子进程（带该应用 WEB-INF/lib classpath），复用单应用流程。 */
    static int spawnJavaRecover(String appRoot, String libDir, String productOverride, boolean dryRun, String method) {
        String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Dfile.encoding=UTF-8");
        boolean patchMode = "patch".equals(method) || "scan".equals(method);
        cmd.add("-cp");
        if (patchMode) {
            // 方式三用最小 classpath（本工具已内嵌 javassist），不能带 lib/* 否则 ITMCReg.jar 被本进程占用无法替换
            cmd.add(jarDir() + File.separator + "LicenseRecover.jar");
        } else {
            cmd.add(libDir + File.separator + "*" + File.pathSeparator + jarDir() + File.separator + "LicenseRecover.jar");
        }
        cmd.add("LicenseRecover");
        if ("patch".equals(method)) cmd.add("--remove-net");
        else if ("scan".equals(method)) cmd.add("--scan-net");
        cmd.add(appRoot);
        if (!patchMode) {
            if (productOverride != null) { cmd.add("-p"); cmd.add(productOverride); }
            if (dryRun) cmd.add("--dry-run");
        }
        return runDotNetProcess(cmd);
    }

    /** 批量主入口：对每个应用执行所选方式。 */
    static void batchMain(String[] args, String parentDir) {
        String method = "config";
        if (hasArg(args, "--remove-net")) method = "patch";
        else if (hasArg(args, "--scan-net")) method = "scan";
        String productOverride = getArg(args, "-p");
        boolean dryRun = hasArg(args, "--dry-run");
        if (hasArg(args, "--gencode")) {
            // 批量模式不消费 --gencode（仅单应用支持方式二）；明确警告，避免误以为在批量生成授权码
            System.err.println("[提示] --gencode（方式二）仅支持单应用模式，批量模式将按所选方式(" + method + ")执行，不生成离线授权码。");
        }

        java.util.List<AppTarget> apps = scanBatch(parentDir);
        int appCnt = 0, noneCnt = 0, failCnt = 0;
        for (AppTarget t : apps) { if ("NONE".equals(t.type)) noneCnt++; else appCnt++; }
        System.out.println("======================================================");
        System.out.println(" ITMC 批量授权恢复");
        System.out.println(" 父目录    : " + parentDir);
        System.out.println(" 方式      : " + method + "   （方式一=.NET防止自动联网校验 / Java写本地授权；--remove-net=方式三 / --scan-net=仅扫描）");
        System.out.println(" 识别      : ITMC 应用 " + appCnt + " 个，非 ITMC 目录 " + noneCnt + " 个（将显示并跳过）");
        System.out.println("======================================================");
        if (apps.isEmpty()) {
            System.err.println("目录为空，未找到任何子目录。");
            System.out.println("RESULT: FAILED");
            System.exit(2);
            return;
        }
        for (AppTarget a : apps) {
            if ("NONE".equals(a.type)) {
                System.out.println();
                System.out.println("======================================================");
                System.out.println(" 目录 [" + a.name + "]   非 ITMC 应用，跳过");
                System.out.println("======================================================");
                System.out.println("[" + a.name + "] 结果: SKIPPED（非 ITMC）");
                continue;
            }
            System.out.println();
            System.out.println("======================================================");
            System.out.println(" 应用 [" + a.name + "]   (" + a.type + " 版)");
            System.out.println("======================================================");
            int rc;
            try {
                if ("DOTNET".equals(a.type)) {
                    if ("config".equals(method)) {
                        // .NET 方式一只修改实际使用的授权配置，避免调用需要 Web 上下文的 DLL 自校验。
                        rc = blockDotNetNet(a.binDir, dryRun, !hasArg(args, "--no-backup"));
                    } else {
                        File helper = locateNetHelper();
                        if (helper == null) {
                            System.err.println("[错误] 未找到 LicenseRecover.NET.exe");
                            rc = 1;
                        } else {
                            java.util.List<String> cmd = new java.util.ArrayList<>();
                            cmd.add(helper.getAbsolutePath());
                            cmd.add(method);
                            cmd.add(a.binDir);
                            if (productOverride != null) { cmd.add("--product"); cmd.add(productOverride); }
                            if (dryRun) cmd.add("--dry-run");
                            boolean hadConfig = new File(a.binDir, "config.xml").isFile();
                            boolean hadRegister = new File(a.binDir, "Register.xml").isFile();
                            rc = runDotNetProcess(cmd);
                            cleanupGeneratedDotNetFiles(a.binDir, hadConfig, hadRegister);
                        }
                    }
                } else {
                    rc = spawnJavaRecover(a.appRoot, a.libDir, productOverride, dryRun, method);
                }
            } catch (Exception ex) {
                System.err.println("[错误] " + ex);
                rc = 1;
            }
            System.out.println("[" + a.name + "] 结果: " + (rc == 0 ? "OK" : "FAILED"));
            if (rc != 0) failCnt++;
        }
        System.out.println();
        System.out.println("===== 批量汇总（请查看上面各应用的 RESULT 行）=====");
        if (failCnt > 0) {
            System.out.println("RESULT: 批量执行完成，失败项目 " + failCnt + " 个");
            System.exit(2);
        }
        System.out.println("RESULT: 批量执行完成");
    }
}
