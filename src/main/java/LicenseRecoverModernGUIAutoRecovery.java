import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One-click recovery coordinator used by the modern GUI overlay. */
public final class LicenseRecoverModernGUIAutoRecovery {
    static final String USER_ID = "fwq";
    static final String BLOCK_ENDPOINT = "http://127.0.0.1:9/Service.asmx";
    private static final Random RNG = new Random();
    private static final Pattern SOFT_VERSION = Pattern.compile("(?is)<SoftVersionID\\b[^>]*>\\s*([^<]+?)\\s*</SoftVersionID\\s*>");
    private static final Pattern WEB_SOFT_NO = Pattern.compile("(?is)<WebSerSoftNo\\b[^>]*>\\s*([^<]*?)\\s*</WebSerSoftNo\\s*>");

    private LicenseRecoverModernGUIAutoRecovery() { }

    public enum Kind { JAVA, DOTNET_MODERN, DOTNET_LEGACY, UNKNOWN }

    public static final class Detection {
        public final Kind kind;
        public final File selected, appRoot, runtimeDir;
        public final String versionId, productName;
        Detection(Kind k, File s, File r, File d, String v, String p) {
            kind=k; selected=s; appRoot=r; runtimeDir=d; versionId=v; productName=p;
        }
        public boolean isDetected() { return kind != Kind.UNKNOWN; }
        public String summary() {
            if (!isDetected()) return "unknown ITMC application";
            String v = versionId == null ? ("itmcIEC".equals(productName) ? "DS01xx" : "version unknown") : versionId;
            return kind + " / " + v + " / " + (productName == null ? "product pending" : productName);
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final Detection detection;
        public final String machineId, requestCode, authorizationCode;
        Result(boolean ok, String msg, Detection d, String m, String req, String auth) {
            success=ok; message=msg==null?"":msg; detection=d; machineId=m; requestCode=req; authorizationCode=auth;
        }
        static Result fail(String msg, Detection d) { return new Result(false,msg,d,null,null,null); }
    }

    public static Detection detect(File selected) {
        if (selected == null) return unknownDetection(null);
        File s = selected.getAbsoluteFile();
        if (!s.isDirectory()) return unknownDetection(s);
        File bin = findDotNetBin(s);
        if (bin != null) {
            File root = "bin".equalsIgnoreCase(bin.getName()) && bin.getParentFile()!=null ? bin.getParentFile() : bin;
            String version = readVersion(root, bin);
            String configuredProduct = detectConfiguredDotNetProduct(root, bin, version);
            String product = !blank(configuredProduct)
                    ? configuredProduct : detectProduct(new File(bin,"ITMC.Web.dll"), version);
            // DS01xx uses the legacy funpublic protocol. Some deployments also carry an
            // uppercase ITMC.Regedit.dll compatibility assembly, which is not proof of the
            // modern JSON/DoRegistry protocol and must not override the version evidence.
            File targetRegedit = selectDotNetRegeditAssembly(bin);
            Kind k = LegacyDotNetProtocol.isLegacyVersion(version)
                    ? Kind.DOTNET_LEGACY
                    : (targetRegedit != null ? Kind.DOTNET_MODERN : Kind.DOTNET_LEGACY);
            return new Detection(k,s,root,bin,version,product);
        }
        File lib = findJavaLib(s);
        if (lib != null) {
            File wi=lib.getParentFile(), root=wi==null?s:wi.getParentFile();
            if (root!=null && "WEB-INF".equalsIgnoreCase(root.getName()) && root.getParentFile()!=null) root=root.getParentFile();
            return new Detection(Kind.JAVA,s,root,lib,readJavaVersion(root),null);
        }
        return unknownDetection(s);
    }

    private static Detection unknownDetection(File s) { return new Detection(Kind.UNKNOWN,s,null,null,null,null); }

    public static Result recover(File selected, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> logger) {
        Consumer<String> log = logger == null ? x -> { } : logger;
        Detection d = detect(selected);
        log.accept("[one-click] detect: " + d.summary() + "\n");
        if (!d.isDetected()) return Result.fail("No supported ITMC Java/.NET authorization structure was found.", d);
        try {
            if (d.kind == Kind.JAVA) return recoverJava(d,backup,blockNet,dryRun,log);
            if (d.kind == Kind.DOTNET_MODERN) return recoverDotNet(d,backup,blockNet,dryRun,log);
            return Result.fail("This target has only the legacy .NET registration assembly; automatic write-back is not enabled for it yet.", d);
        } catch (Throwable ex) {
            log.accept("[error] " + safe(ex) + "\n");
            return Result.fail(safe(ex), d);
        }
    }

    private static Result recoverJava(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(d.appRoot);
        if (plan.detected) log.accept(plan.logSummary());
        if (plan.detected && !plan.automaticRecoveryReady) {
            return Result.fail("自动恢复已阻止：" + plan.recoveryReadiness + "。未修改任何文件。", d);
        }
        File cli=new File(toolDir(),"LicenseRecover.jar");
        if (!cli.isFile()) return Result.fail("LicenseRecover.jar was not found.",d);
        List<String> cmd=new ArrayList<String>();
        cmd.add(javaExe()); cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-cp");
        String childCp = buildJavaRecoveryClasspath(toolDir(), d.runtimeDir);
        log.accept("[java-plan] child classpath order=tool overlay/core -> target WEB-INF/lib\n");
        cmd.add(childCp);
        cmd.add("LicenseRecover"); cmd.add(d.appRoot.getAbsolutePath());
        if (dryRun) cmd.add("--dry-run"); if (!backup) cmd.add("--no-backup"); if (!blockNet) cmd.add("--no-block-net");
        int rc=run(cmd,log);
        if (dryRun) log.accept("[java-plan] native verify=PREVIEW (write-back check not executed)\n");
        else log.accept(rc==0
                ? "[java-plan] native verify=PASS (RegisterMain.checkReInfo())\n"
                : "[java-plan] native verify=FAILED (see CLI log)\n");
        return new Result(rc==0,rc==0?"Java local authorization recovery completed and native self-check passed.":"Java recovery failed; see log.",d,null,null,null);
    }

    static String buildJavaRecoveryClasspath(File toolDir, File runtimeDir) {
        if (runtimeDir == null) throw new IllegalArgumentException("runtimeDir is required");
        return LicenseRecover.toolRuntimeClasspath(toolDir)
                + File.pathSeparator + runtimeDir.getAbsolutePath() + File.separator + "*";
    }

    private static Result recoverDotNet(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        if (!isWindows()) return Result.fail(".NET native registration runs only on Windows.", d);

        File targetRegedit = selectDotNetRegeditAssembly(d.runtimeDir);
        if (targetRegedit == null)
            return Result.fail("[CHAIN] no target itmcRegedit.dll / ITMC.Regedit.dll registration assembly was found.", d);
        boolean lowercaseChain = "itmcRegedit.dll".equals(targetRegedit.getName());
        // Both lowercase and uppercase registration components must execute inside the
        // target web application's hosted AppDomain.  The two generations resolve
        // paths differently (lowercase uses HttpContext.MapPath, uppercase uses
        // AppDomain.BaseDirectory), but both expect the site root rather than the
        // LicenseRecover tool directory or target bin as their application base.
        File helper = findDotNetAspNetHostHelper();
        if (helper == null) {
            return Result.fail("[HELPER] LicenseRecover.NET.AspNetHost.exe was not found; selected target registration chain cannot run.", d);
        }

        String appProduct = d.productName;
        if (blank(appProduct))
            return Result.fail("Target ITMC.Web.dll did not prove a ProName; default product fallback is disabled.", d);

        String registrationProduct = appProduct;
        if (lowercaseChain) {
            registrationProduct = detectLowercaseDotNetRegistrationProduct(targetRegedit);
            if (blank(registrationProduct))
                return Result.fail("[CHAIN] lowercase itmcRegedit.dll is authoritative, but its registration crypto family "
                        + "could not be proven from matching itmc<family> + *<family>OK* constants.", d);
            log.accept("[dotnet-chain] lowercase itmcRegedit.dll preferred via ASP.NET host; appProduct=" + appProduct
                    + " registrationProduct=" + registrationProduct + "\n");
        } else {
            log.accept("[dotnet-chain] lowercase itmcRegedit.dll absent; uppercase ITMC.Regedit.dll fallback via hosted AppDomain; product="
                    + appProduct + "\n");
        }

        String regStr = detectDotNetRegStr(d);
        if (blank(regStr))
            return Result.fail("Target application directory did not prove RegStr products; VersionID/default-list fallback is disabled.", d);

        RegisterVersionEvidence compatibility = readRegisterVersionEvidence(d);
        if (compatibility != null && compatibility.relationCount > 0)
            log.accept("[one-click] target RegisterVersion.db compatibility: " + compatibility.summary() + "\n");

        LinkedHashMap<File, byte[]> originals = dryRun ? null : snapshotDotNetRegistrationFiles(d);
        String firewallRule = null;
        try {
            // Network isolation is established BEFORE any vendor registration code is invoked.
            // The selected helper loads the selected target registration assembly; an
            // application-specific Windows Firewall rule blocks even hard-coded outbound URLs.
            firewallRule = installTemporaryDotNetNetworkGuard(helper, log);
            if (blank(firewallRule))
                throw new IOException("[PRE_BLOCK] 无法建立 .NET 临时出站防火墙隔离。请从 LicenseRecoverGUI.exe 启动并通过管理员权限(UAC)，"
                        + "同时确认 Windows Firewall 服务可用；为避免联网校验，已拒绝调用目标注册组件。");
            log.accept("[dotnet-stage] PRE_BLOCK: OK (" + helper.getName() + ")\n");

            if (!dryRun) {
                if (backup) backupDotNetRegistrationFiles(originals, log);
                blockPrimaryDotNetConfigs(d, log);
                blockSidecars(d, false, false, log);
                log.accept("[pre-block] authorization network isolation is active before gencode / DoRegistry / CheckReInfo.\n");
            } else {
                log.accept("[dry-run] temporary process-level outbound guard is active; configuration files remain unchanged.\n");
            }

            List<String> generate = new ArrayList<String>();
            generate.add(helper.getAbsolutePath());
            generate.add("gencode");
            generate.add(d.runtimeDir.getAbsolutePath());
            generate.add("--product"); generate.add(registrationProduct);
            generate.add("--regstr"); generate.add(regStr);
            log.accept("[dotnet-stage] GENCODE: start\n");
            NativeProcessResult generated = runNativeCapture(generate, d.appRoot, log);
            if (generated.exitCode != 0)
                throw nativeFailure("GENCODE", "target-native request-code generation failed", generated);
            log.accept("[dotnet-stage] GENCODE: OK\n");

            String request = findLabeledHex(generated.output, "注册申请号", "申请号");
            String auth = findLabeledHex(generated.output, "离线授权码", "授权码");
            if (blank(request) || blank(auth))
                throw new IOException("[GENCODE_PARSE] helper returned success but request/auth fields were not parsed; output="
                        + compactNativeOutput(generated.output));
            log.accept("[dotnet-stage] GENCODE_PARSE: OK\n");

            String machine = null;
            try {
                String plain = desDecryptHex(request, "itmcsoft");
                if (plain != null && plain.length() >= 20) machine = plain.substring(4, 20);
            } catch (Throwable ignore) { }

            log.accept("[one-click] .NET native registration flow: PRE-BLOCK -> gencode -> DoRegistry -> CheckReInfo\n");
            log.accept("[one-click] appProduct=" + valueOrPending(appProduct)
                    + " registrationProduct=" + valueOrPending(registrationProduct)
                    + " products=" + valueOrPending(regStr) + " RegID=" + valueOrPending(machine) + "\n");
            if (dryRun) {
                log.accept("[dry-run] native request/auth codes generated under outbound isolation; no file was changed.\n");
                return new Result(true, "Preview completed under temporary outbound isolation; no file was changed.",
                        d, machine, request, auth);
            }

            List<String> apply = new ArrayList<String>();
            apply.add(helper.getAbsolutePath());
            apply.add("doreg");
            apply.add(d.runtimeDir.getAbsolutePath());
            apply.add("--seq"); apply.add(request);
            apply.add("--code"); apply.add(auth);
            apply.add("--product"); apply.add(registrationProduct);
            // Keep the helper from changing network settings itself; the outer coordinator
            // already established isolation before the first vendor-code call.
            apply.add("--no-block-net");
            if (!backup) apply.add("--no-backup");
            log.accept("[dotnet-stage] DOREG: start\n");
            NativeProcessResult applied = runNativeCapture(apply, d.appRoot, log);
            if (applied.exitCode != 0)
                throw nativeFailure("DOREG", "target RegeditMain.DoRegistry() rejected the generated code", applied);
            log.accept("[dotnet-stage] DOREG: OK\n");

            List<String> verify = new ArrayList<String>();
            verify.add(helper.getAbsolutePath());
            verify.add("verify");
            verify.add(d.runtimeDir.getAbsolutePath());
            verify.add("--product"); verify.add(registrationProduct);
            log.accept("[dotnet-stage] VERIFY: start\n");
            NativeProcessResult checked = runNativeCapture(verify, d.appRoot, log);
            if (checked.exitCode != 0)
                throw nativeFailure("VERIFY", "selected target RegeditMain.CheckReInfo() did not confirm the native write-back", checked);
            log.accept("[dotnet-stage] VERIFY: OK\n");

            if (blockNet) {
                // Re-assert in case the native writer rewrote config.xml while registering.
                blockPrimaryDotNetConfigs(d, log);
                blockSidecars(d, false, false, log);
            } else {
                restoreDotNetAuthorizationNetworkSettings(originals, d, log);
            }
            log.accept("[verify] selected target registration chain DoRegistry + CheckReInfo passed while outbound traffic was isolated.\n");
            return new Result(true,
                    ".NET local authorization was applied and verified through the target-preferred registration chain while outbound traffic was isolated. Restart the IIS app pool/site.",
                    d, machine, request, auth);
        } catch (Throwable ex) {
            if (!dryRun && originals != null) restoreDotNetRegistrationFiles(originals, log);
            if (ex instanceof Exception) throw (Exception) ex;
            throw new Exception(ex);
        } finally {
            removeTemporaryDotNetNetworkGuard(firewallRule, log);
        }
    }

    static final class NativeProcessResult {
        final int exitCode;
        final String output;
        NativeProcessResult(int exitCode, String output) { this.exitCode=exitCode; this.output=output==null?"":output; }
    }

    static NativeProcessResult runNativeCapture(List<String> cmd, File workDir, Consumer<String> log) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (workDir != null && workDir.isDirectory()) pb.directory(workDir);
        // Defense in depth for HTTP stacks that honor proxy environment variables.
        // The Windows Firewall application rule remains the authoritative guard.
        Map<String,String> env = pb.environment();
        env.put("HTTP_PROXY", "http://127.0.0.1:9");
        env.put("HTTPS_PROXY", "http://127.0.0.1:9");
        env.put("ALL_PROXY", "http://127.0.0.1:9");
        env.put("NO_PROXY", "localhost,127.0.0.1,::1");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder all = new StringBuilder();
        try {
            String line;
            while ((line = r.readLine()) != null) {
                all.append(line).append('\n');
                if (log != null) log.accept(line + "\n");
            }
        } finally { try { r.close(); } catch (IOException ignore) { } }
        return new NativeProcessResult(p.waitFor(), all.toString());
    }

    static String findLabeledHex(String output, String... labels) {
        if (output == null) return null;
        for (String label : labels) {
            Matcher m = Pattern.compile("(?im)" + Pattern.quote(label) + "\\s*[:：]\\s*([0-9a-f]+)").matcher(output);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private static IOException nativeFailure(String stage, String action, NativeProcessResult result) {
        return new IOException("[" + stage + "] " + action + "; exit=" + result.exitCode
                + "; output=" + compactNativeOutput(result.output));
    }

    static String compactNativeOutput(String output) {
        if (output == null) return "<empty>";
        String s = output.replace('\r', ' ').replace('\n', ' ');
        s = s.replaceAll("(?i)(注册申请号|申请号|离线授权码|授权码)\\s*[:：]\\s*[0-9a-f]{32,}", "$1:<redacted>");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "<empty>";
        return s.length() <= 600 ? s : "..." + s.substring(s.length() - 600);
    }

    private static File findDotNetNativeHelper() {
        File dir = toolDir();
        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.exe");
        if (nested.isFile()) return nested;
        File flat = new File(dir, "LicenseRecover.NET.exe");
        return flat.isFile() ? flat : null;
    }

    private static File findDotNetAspNetHostHelper() {
        File dir = toolDir();
        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.AspNetHost.exe");
        if (nested.isFile()) return nested;
        File flat = new File(dir, "LicenseRecover.NET.AspNetHost.exe");
        return flat.isFile() ? flat : null;
    }

    private static File findDotNetModernNativeHelper() {
        File dir = toolDir();
        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.Modern.exe");
        if (nested.isFile()) return nested;
        File flat = new File(dir, "LicenseRecover.NET.Modern.exe");
        return flat.isFile() ? flat : null;
    }

    static File findExactChild(File dir, String exactName) {
        if (dir == null || !dir.isDirectory() || exactName == null) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && exactName.equals(file.getName())) return file;
        }
        return null;
    }

    static File selectDotNetRegeditAssembly(File runtimeDir) {
        File lower = findExactChild(runtimeDir, "itmcRegedit.dll");
        if (lower != null) return lower;
        return findExactChild(runtimeDir, "ITMC.Regedit.dll");
    }

    static String detectLowercaseDotNetRegistrationProduct(File lowerDll) {
        return detectLowercaseDotNetRegistrationProduct(extractUtf16Ascii(lowerDll));
    }

    static String detectLowercaseDotNetRegistrationProduct(Set<String> strings) {
        if (strings == null || strings.isEmpty()) return null;
        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        for (String raw : strings) {
            if (raw == null) continue;
            String key = raw.trim();
            if (key.length() <= 4 || !key.regionMatches(true, 0, "itmc", 0, 4)) continue;
            String family = key.substring(4).trim();
            if (!validRegistrationToken(family) || "soft".equalsIgnoreCase(family)) continue;
            String familyUpper = family.toUpperCase(Locale.ROOT);
            boolean paired = false;
            for (String markerRaw : strings) {
                if (markerRaw == null) continue;
                String marker = markerRaw.replaceAll("[^A-Za-z0-9._-]", "").toUpperCase(Locale.ROOT);
                if (marker.endsWith(familyUpper + "OK")) {
                    paired = true;
                    break;
                }
            }
            if (paired) candidates.add(family);
        }
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private static LinkedHashMap<File, byte[]> snapshotDotNetRegistrationFiles(Detection d) throws IOException {
        LinkedHashMap<File, byte[]> out = new LinkedHashMap<File, byte[]>();
        List<File> candidates = new ArrayList<File>();
        addIfFile(candidates, new File(d.appRoot, "config.xml"));
        addIfFile(candidates, new File(d.runtimeDir, "config.xml"));
        addIfFile(candidates, new File(d.appRoot, "Register.xml"));
        addIfFile(candidates, new File(d.runtimeDir, "Register.xml"));
        addIfFile(candidates, new File(d.appRoot, "Web.config"));
        addConfigs(candidates, d.appRoot);
        addConfigs(candidates, d.runtimeDir);
        HashSet<String> seen = new HashSet<String>();
        for (File f : candidates) {
            String key;
            try { key = f.getCanonicalPath().toLowerCase(Locale.ROOT); }
            catch (IOException ex) { key = f.getAbsolutePath().toLowerCase(Locale.ROOT); }
            if (!seen.add(key)) continue;
            out.put(f, f.isFile() ? Files.readAllBytes(f.toPath()) : null);
        }
        return out;
    }

    private static void backupDotNetRegistrationFiles(LinkedHashMap<File, byte[]> originals, Consumer<String> log) throws IOException {
        for (Map.Entry<File, byte[]> e : originals.entrySet()) {
            if (e.getValue() == null) continue;
            File f = e.getKey();
            File bak = uniqueBackup(f);
            Files.write(bak.toPath(), e.getValue());
            if (log != null) log.accept("[backup] " + bak.getAbsolutePath() + "\n");
        }
    }

    private static void restoreDotNetRegistrationFiles(LinkedHashMap<File, byte[]> originals, Consumer<String> log) {
        for (Map.Entry<File, byte[]> e : originals.entrySet()) {
            try {
                if (e.getValue() == null) Files.deleteIfExists(e.getKey().toPath());
                else Files.write(e.getKey().toPath(), e.getValue());
                if (log != null) log.accept("[rollback] restored " + e.getKey().getAbsolutePath() + "\n");
            } catch (Throwable r) {
                if (log != null) log.accept("[rollback-warning] " + e.getKey().getAbsolutePath() + ": " + safe(r) + "\n");
            }
        }
    }

    private static void blockPrimaryDotNetConfigs(Detection d, Consumer<String> log) throws Exception {
        File[] files = new File[]{ new File(d.appRoot, "config.xml"), new File(d.runtimeDir, "config.xml") };
        HashSet<String> seen = new HashSet<String>();
        for (File f : files) {
            if (!f.isFile()) continue;
            String key = f.getCanonicalPath().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            String original = readUtf8(f);
            if (!hasDotNetAuthorizationConfigStructure(original)) {
                validateXml(original);
                if (log != null) log.accept("[pre-block-skip] " + f.getAbsolutePath()
                        + " has no authorization <reg>/Service node; process firewall guard remains active.\n");
                continue;
            }
            String updated = putElement(original, "Service", BLOCK_ENDPOINT);
            validateXml(updated);
            if (!original.equals(updated)) {
                Files.write(f.toPath(), updated.getBytes(StandardCharsets.UTF_8));
                if (log != null) log.accept("[block-net] " + f.getAbsolutePath() + "\n");
            }
        }
    }

    static boolean hasDotNetAuthorizationConfigStructure(String xml) {
        if (xml == null) return false;
        if (Pattern.compile("(?is)<Service\\b[^>]*>.*?</Service\\s*>").matcher(xml).find()) return true;
        if (Pattern.compile("(?is)<reg\\b[^>]*>.*?</reg\\s*>").matcher(xml).find()) return true;
        return Pattern.compile("(?is)<reg\\b[^>]*/\\s*>").matcher(xml).find();
    }

    static String installTemporaryDotNetNetworkGuard(File helper, Consumer<String> log) throws Exception {
        if (!isWindows() || helper == null || !helper.isFile()) return null;
        String rule = "LicenseRecover-Temp-" + Long.toHexString(System.nanoTime());
        List<String> cmd = Arrays.asList("netsh", "advfirewall", "firewall", "add", "rule",
                "name=" + rule, "dir=out", "action=block", "enable=yes", "profile=any",
                "program=" + helper.getAbsolutePath());
        NativeProcessResult r = runSystemCommand(cmd);
        if (r.exitCode != 0) {
            if (log != null) log.accept("[pre-block-error] Windows Firewall guard failed: " + r.output + "\n");
            return null;
        }
        if (log != null) log.accept("[pre-block] Windows Firewall outbound guard installed for "
                + helper.getName() + " (" + rule + ")\n");
        return rule;
    }

    static void removeTemporaryDotNetNetworkGuard(String rule, Consumer<String> log) {
        if (blank(rule) || !isWindows()) return;
        try {
            NativeProcessResult r = runSystemCommand(Arrays.asList("netsh", "advfirewall", "firewall",
                    "delete", "rule", "name=" + rule));
            if (log != null) log.accept(r.exitCode == 0
                    ? "[pre-block] temporary Windows Firewall guard removed.\n"
                    : "[pre-block-warning] failed to remove temporary firewall guard: " + r.output + "\n");
        } catch (Throwable ex) {
            if (log != null) log.accept("[pre-block-warning] failed to remove temporary firewall guard: " + safe(ex) + "\n");
        }
    }

    static NativeProcessResult runSystemCommand(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = p.getInputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        int rc = p.waitFor();
        Charset cs;
        try { cs = Charset.forName("GBK"); } catch (Throwable ignore) { cs = StandardCharsets.UTF_8; }
        return new NativeProcessResult(rc, new String(out.toByteArray(), cs));
    }

    private static void restoreDotNetAuthorizationNetworkSettings(LinkedHashMap<File, byte[]> originals,
                                                                   Detection d, Consumer<String> log) {
        if (originals == null) return;
        HashSet<String> primary = new HashSet<String>();
        try { primary.add(new File(d.appRoot, "config.xml").getCanonicalPath().toLowerCase(Locale.ROOT)); } catch (Throwable ignore) { }
        try { primary.add(new File(d.runtimeDir, "config.xml").getCanonicalPath().toLowerCase(Locale.ROOT)); } catch (Throwable ignore) { }
        Pattern service = Pattern.compile("(?is)<Service\\b[^>]*>.*?</Service\\s*>");
        for (Map.Entry<File, byte[]> e : originals.entrySet()) {
            File f = e.getKey();
            byte[] originalBytes = e.getValue();
            if (originalBytes == null || f == null || !f.isFile()) continue;
            try {
                String key = f.getCanonicalPath().toLowerCase(Locale.ROOT);
                if (!primary.contains(key)) {
                    // Sidecar/Web.config files are not registration write targets; restore exactly.
                    Files.write(f.toPath(), originalBytes);
                    continue;
                }
                String before = new String(originalBytes, StandardCharsets.UTF_8);
                String current = readUtf8(f);
                Matcher om = service.matcher(before);
                Matcher cm = service.matcher(current);
                String restored;
                if (om.find()) {
                    String originalService = om.group();
                    restored = cm.find()
                            ? current.substring(0, cm.start()) + originalService + current.substring(cm.end())
                            : current;
                } else {
                    restored = cm.replaceFirst("");
                }
                validateXml(restored);
                Files.write(f.toPath(), restored.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ex) {
                if (log != null) log.accept("[pre-block-warning] could not restore original network setting for "
                        + f.getAbsolutePath() + ": " + safe(ex) + "\n");
            }
        }
        if (log != null) log.accept("[pre-block] temporary authorization network settings restored (--no-block-net).\n");
    }

    private static String valueOrPending(String value) { return blank(value) ? "<auto>" : value; }

    private static void verifyPersisted(File cfg,String product,String machine) throws Exception {
        String xml=readUtf8(cfg), enc=firstElement(xml,"regName");
        if (blank(enc)) throw new IOException("regName missing after write");
        String plain=desDecryptHex(enc.trim(),"*ITMC"+product+"OK*");
        if (plain.length()<12) throw new IOException("regName round-trip failed");
        String j=plain.substring(6,plain.length()-6);
        if (!j.contains("\"UserID\":\""+USER_ID+"\"") || !j.contains("\"RegID\":\""+machine+"\"") || !j.contains("\"ProName\":\""+product+"\""))
            throw new IOException("persisted RegInfo identity fields do not match");
        validateXml(xml);
    }

    static String getRegNo(String configXml, boolean useSoftNo, Consumer<String> log) throws Exception {
        String soft=match(WEB_SOFT_NO,configXml);
        if (useSoftNo && !blank(soft)) {
            try {
                String cpu=wmic("cpu","ProcessorId");
                String decoded=desDecryptHex(soft.trim(),cpu);
                if (!blank(decoded)) { if (log!=null) log.accept("[machine-id] reused RegNo decoded from WebSerSoftNo.\n"); return decoded.trim(); }
            } catch (Throwable ignore) { if (log!=null) log.accept("[machine-id] WebSerSoftNo unavailable; falling back to vendor WMI rule.\n"); }
        }
        String cpu=unknown(wmic("cpu","ProcessorId"));
        String disk=unknown(wmic("diskdrive","Signature"));
        String board=unknown(wmic("baseboard","SerialNumber"));
        if (eqUnknown(disk) && eqUnknown(cpu)) {
            String host;
            try { host=InetAddress.getLocalHost().getHostName(); } catch(Exception e){ host=System.getenv("COMPUTERNAME"); }
            if (host==null) host=""; while(host.length()<17) host="0"+host;
            return host.substring(host.length()-17,host.length()-1);
        }
        while(cpu.length()<9) cpu="0"+cpu; while(disk.length()<9) disk="0"+disk;
        if (eqUnknown(board) || "None".equals(board)) board=cpu; else while(board.length()<9) board="0"+board;
        return board.substring(board.length()-9,board.length()-1)+disk.substring(disk.length()-9,disk.length()-1);
    }

    static String buildRegInfoJson(String machine,String product,String regStr) {
        Calendar b=Calendar.getInstance(); b.set(Calendar.HOUR_OF_DAY,0);b.set(Calendar.MINUTE,0);b.set(Calendar.SECOND,0);b.set(Calendar.MILLISECOND,0);
        Calendar e=Calendar.getInstance(); e.clear();e.set(2099,Calendar.DECEMBER,31,0,0,0);
        return "{"+
                "\"RegStr\":\""+json(regStr)+"\","+
                "\"ClassNum\":-1,"+
                "\"RegID\":\""+json(machine)+"\","+
                "\"UserID\":\""+USER_ID+"\","+
                "\"ProName\":\""+json(product)+"\","+
                "\"BeginDate\":\""+dotNetDate(b.getTimeInMillis())+"\","+
                "\"beginDate\":0,\"endDate\":0,"+
                "\"EndDate\":\""+dotNetDate(e.getTimeInMillis())+"\","+
                "\"TotalTimes\":-1,\"UserTimes\":1,\"Net\":true,\"MaxCon\":-1,\"CountDay\":10}";
    }

    private static String dotNetDate(long ms) {
        int off=TimeZone.getDefault().getOffset(ms), mins=Math.abs(off)/60000;
        return "/Date("+ms+(off>=0?"+":"-")+String.format(Locale.ROOT,"%02d%02d",mins/60,mins%60)+")/";
    }

    static String desEncryptHex(String text,String password) throws Exception {
        byte[] k=desKey(password); Cipher c=Cipher.getInstance("DES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k,"DES"),new IvParameterSpec(k)); return hex(c.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }
    static String desDecryptHex(String text,String password) throws Exception {
        byte[] k=desKey(password); Cipher c=Cipher.getInstance("DES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(k,"DES"),new IvParameterSpec(k)); return new String(c.doFinal(unhex(text)),StandardCharsets.UTF_8);
    }
    private static byte[] desKey(String p) throws Exception {
        byte[] md5=MessageDigest.getInstance("MD5").digest((p==null?"":p).getBytes(StandardCharsets.UTF_8));
        return hex(md5).substring(0,8).getBytes(StandardCharsets.US_ASCII);
    }

    static String updateLocalLicenseXml(String original,String regName,boolean blockNet) throws Exception {
        String x=putElement(original,"regName",regName); x=putElement(x,"regType","1");
        if (blockNet) x=putElement(x,"Service",BLOCK_ENDPOINT); return x;
    }
    private static String putElement(String xml,String name,String value) throws Exception {
        Pattern p=Pattern.compile("(?is)<"+Pattern.quote(name)+"\\b[^>]*>.*?</"+Pattern.quote(name)+"\\s*>");
        Matcher m=p.matcher(xml);
        if (m.find()) {
            String old=m.group(); int gt=old.indexOf('>'), lt=old.toLowerCase(Locale.ROOT).lastIndexOf("</"+name.toLowerCase(Locale.ROOT));
            String rep=old.substring(0,gt+1)+xmlEscape(value)+old.substring(lt);
            return xml.substring(0,m.start())+rep+xml.substring(m.end());
        }
        Pattern reg=Pattern.compile("(?is)<reg\\b[^>]*>.*?</reg\\s*>"); Matcher r=reg.matcher(xml);
        if (r.find()) {
            String b=r.group(), sep=xml.contains("\r\n")?"\r\n":"\n"; int close=b.toLowerCase(Locale.ROOT).lastIndexOf("</reg");
            String rep=b.substring(0,close)+sep+"    <"+name+">"+xmlEscape(value)+"</"+name+">"+sep+b.substring(close);
            return xml.substring(0,r.start())+rep+xml.substring(r.end());
        }
        Pattern selfReg=Pattern.compile("(?is)<reg\\b([^>]*)/\\s*>"); Matcher sr=selfReg.matcher(xml);
        if (sr.find()) {
            String attrs=sr.group(1), sep=xml.contains("\r\n")?"\r\n":"\n";
            String rep="<reg"+attrs+">"+sep+"    <"+name+">"+xmlEscape(value)+"</"+name+">"+sep+"</reg>";
            return xml.substring(0,sr.start())+rep+xml.substring(sr.end());
        }
        throw new IOException("config.xml has no <reg> node");
    }

    private static File findDotNetConfig(Detection d) {
        File root=new File(d.appRoot,"config.xml"); if(root.isFile()) return root;
        File bin=new File(d.runtimeDir,"config.xml"); return bin.isFile()?bin:null;
    }

    private static File findDotNetBin(File start) {
        File cur=start;
        while(cur!=null) {
            if(hasDotNet(cur)) return cur;
            File b="bin".equalsIgnoreCase(cur.getName())?cur:new File(cur,"bin"); if(hasDotNet(b)) return b;
            if (!structural(cur.getName())) break; cur=cur.getParentFile();
        }
        return null;
    }
    private static boolean hasDotNet(File d) {
        return d!=null&&d.isDirectory()&&new File(d,"ITMC.Web.dll").isFile()&&(new File(d,"ITMC.Regedit.dll").isFile()||new File(d,"itmcRegedit.dll").isFile());
    }
    private static File findJavaLib(File start) {
        File cur=start;
        while(cur!=null) {
            if("lib".equalsIgnoreCase(cur.getName())&&findRegJar(cur)!=null) return cur;
            File a=new File(cur,"WEB-INF"+File.separator+"lib"); if(findRegJar(a)!=null)return a;
            File b=new File(cur,"WEB-INF"+File.separator+"WEB-INF"+File.separator+"lib"); if(findRegJar(b)!=null)return b;
            if(!structural(cur.getName()))break; cur=cur.getParentFile();
        }
        return null;
    }
    private static File findRegJar(File d) {
        if(d==null||!d.isDirectory())return null; File e=new File(d,"ITMCReg.jar"); if(e.isFile())return e;
        File[] fs=d.listFiles((x,n)->n.toLowerCase(Locale.ROOT).startsWith("itmcreg")&&n.toLowerCase(Locale.ROOT).endsWith(".jar")); return fs!=null&&fs.length>0?fs[0]:null;
    }
    private static boolean structural(String n){return "bin".equalsIgnoreCase(n)||"lib".equalsIgnoreCase(n)||"WEB-INF".equalsIgnoreCase(n)||"classes".equalsIgnoreCase(n)||"data".equalsIgnoreCase(n);}

    private static String readVersion(File root,File bin) { String x=readVersionFile(new File(root,"config.xml")); return x!=null?x:readVersionFile(new File(bin,"config.xml")); }
    private static String readJavaVersion(File root) {
        if(root==null)return null; File y=new File(root,"systemConfig.yml");
        try{if(y.isFile())for(String s:Files.readAllLines(y.toPath(),StandardCharsets.UTF_8)){String t=s.trim();if(t.toLowerCase(Locale.ROOT).contains("versionid")){int i=t.indexOf(':');if(i<0)i=t.indexOf('=');if(i>=0&&!blank(t.substring(i+1)))return t.substring(i+1).trim();}}}catch(Exception ignore){}
        String dataVersion=readVersionFile(new File(root,"data"+File.separator+"config.xml"));
        if(dataVersion!=null)return dataVersion;
        // XMT classes/config.xml fallback
        return readVersionFile(new File(root,"WEB-INF"+File.separator+"classes"+File.separator+"config.xml"));
    }
    private static String readVersionFile(File f) { try{if(!f.isFile())return null;return match(SOFT_VERSION,readUtf8(f));}catch(Exception e){return null;} }

    /**
     * Resolve the direct .NET registration ProName from this application's own
     * Web.config.  The configuration value is executable evidence only when the
     * same target ITMC.Web.dll also contains that product token and the native
     * local-registration/check flow.  Folder names and VersionID prefixes are
     * deliberately not used as proof here.
     */
    static String detectConfiguredDotNetProduct(File root, File bin, String version) {
        if (root == null || bin == null || blank(version)) return null;
        File webDll = new File(bin, "ITMC.Web.dll");
        File webConfig = new File(root, "Web.config");
        String product = readAppSetting(webConfig, "productName");
        if (blank(product) || !product.matches("(?i)[A-Z0-9][A-Z0-9._-]{1,63}")) return null;
        if (!targetContainsAll(webDll, product, "RegeditNew", "NewRegistry", "DoRegistry",
                "ProName", "SoftVersionID", "GetProVersion", "CheckSoftVersionID")) return null;
        return product.trim();
    }

    private static String readAppSetting(File webConfig, String wantedKey) {
        if (webConfig == null || !webConfig.isFile() || blank(wantedKey)) return null;
        try {
            String xml = readUtf8(webConfig);
            Matcher add = Pattern.compile("(?is)<add\\b([^>]*)>").matcher(xml);
            while (add.find()) {
                String attrs = add.group(1);
                String key = xmlAttribute(attrs, "key");
                if (!wantedKey.equalsIgnoreCase(key)) continue;
                String value = xmlAttribute(attrs, "value");
                return blank(value) ? null : unxml(value.trim());
            }
        } catch (Throwable ignore) { }
        return null;
    }

    private static String xmlAttribute(String attrs, String name) {
        if (attrs == null || name == null) return null;
        Matcher m = Pattern.compile("(?is)\\b" + Pattern.quote(name)
                + "\\s*=\\s*([\\\"'])(.*?)\\1").matcher(attrs);
        return m.find() ? m.group(2) : null;
    }

    /**
     * Direct local-registration identity used by Regester.Page_Load/Button1_Click:
     * configured ProName + current config.xml SoftVersionID.  This is kept separate
     * from RegisterVersion.db, whose parent product/version rows are compatibility
     * aliases consumed by GetProVersion()/CheckReg rather than the direct page identity.
     */
    static String detectDirectDotNetRegStr(Detection d) {
        if (d == null || d.appRoot == null || d.runtimeDir == null
                || blank(d.productName) || blank(d.versionId)) return null;
        String configured = detectConfiguredDotNetProduct(d.appRoot, d.runtimeDir, d.versionId);
        if (blank(configured) || !configured.equalsIgnoreCase(d.productName.trim())) return null;
        String current = readVersion(d.appRoot, d.runtimeDir);
        if (blank(current) || !current.trim().equalsIgnoreCase(d.versionId.trim())) return null;
        String token = d.versionId.trim();
        return token.matches("(?i)[A-Z0-9][A-Z0-9._-]{1,63}") ? token.toUpperCase(Locale.ROOT) : null;
    }

    static final class RegisterVersionEvidence {
        final String versionId;
        final LinkedHashMap<String, List<String>> compatibility;
        final int relationCount;

        RegisterVersionEvidence(String versionId, LinkedHashMap<String, LinkedHashSet<String>> rows) {
            this.versionId = versionId;
            this.compatibility = new LinkedHashMap<String, List<String>>();
            int count = 0;
            for (Map.Entry<String, LinkedHashSet<String>> e : rows.entrySet()) {
                ArrayList<String> values = new ArrayList<String>(e.getValue());
                this.compatibility.put(e.getKey(), Collections.unmodifiableList(values));
                count += values.size();
            }
            this.relationCount = count;
        }

        String summary() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, List<String>> e : compatibility.entrySet()) {
                if (b.length() > 0) b.append("; ");
                b.append(e.getKey()).append(" -> ");
                for (int i = 0; i < e.getValue().size(); i++) {
                    if (i > 0) b.append(',');
                    b.append(e.getValue().get(i));
                }
            }
            return b.toString();
        }
    }

    private static final class EncryptedRegisterVersionRow {
        final String versionId, parentProductId, parentVersionId;
        EncryptedRegisterVersionRow(String v, String p, String pv) {
            versionId = v; parentProductId = p; parentVersionId = pv;
        }
    }

    /**
     * Parse target RegisterVersion.db read-only.  The TripleDES key is never fixed in
     * LicenseRecover: 24-byte candidates are collected from this target ITMC.Web.dll
     * and accepted only when exactly one candidate decrypts rows whose versionID is
     * the current target SoftVersionID and whose parent fields are valid product tokens.
     */
    static RegisterVersionEvidence readRegisterVersionEvidence(Detection d) {
        if (d == null || d.appRoot == null || d.runtimeDir == null || blank(d.versionId)) return null;
        File db = new File(d.appRoot, "RegisterVersion.db");
        File webDll = new File(d.runtimeDir, "ITMC.Web.dll");
        if (!db.isFile() || !webDll.isFile()) return null;
        if (!targetContainsAll(webDll, "Encrypt3Des", "Decrypt3Des", "GetRegisterVersionList",
                "GetProVersion", "CheckSoftVersionID")) return null;
        try {
            List<EncryptedRegisterVersionRow> encryptedRows = scanRegisterVersionRows(
                    Files.readAllBytes(db.toPath()));
            if (encryptedRows.isEmpty()) return null;

            LinkedHashSet<String> keyCandidates = new LinkedHashSet<String>();
            for (String text : extractUtf16Ascii(webDll)) {
                if (text == null || text.length() != 24) continue;
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (bytes.length != 24) continue;
                boolean printable = true;
                for (byte value : bytes) {
                    int c = value & 255;
                    if (c < 32 || c > 126) { printable = false; break; }
                }
                if (printable) keyCandidates.add(text);
            }

            RegisterVersionEvidence winner = null;
            int winnerCount = 0;
            for (String key : keyCandidates) {
                LinkedHashMap<String, LinkedHashSet<String>> matches =
                        new LinkedHashMap<String, LinkedHashSet<String>>();
                for (EncryptedRegisterVersionRow row : encryptedRows) {
                    String version = decryptRegisterVersionToken(row.versionId, key);
                    if (version == null || !version.equalsIgnoreCase(d.versionId.trim())) continue;
                    String parentProduct = decryptRegisterVersionToken(row.parentProductId, key);
                    String parentVersion = decryptRegisterVersionToken(row.parentVersionId, key);
                    if (!validRegistrationToken(parentProduct) || !validRegistrationToken(parentVersion)) continue;
                    LinkedHashSet<String> versions = matches.get(parentProduct);
                    if (versions == null) {
                        versions = new LinkedHashSet<String>();
                        matches.put(parentProduct, versions);
                    }
                    versions.add(parentVersion);
                }
                if (!matches.isEmpty()) {
                    winner = new RegisterVersionEvidence(d.versionId.trim(), matches);
                    winnerCount++;
                    if (winnerCount > 1) return null; // ambiguous target-owned key evidence
                }
            }
            return winnerCount == 1 ? winner : null;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static boolean validRegistrationToken(String value) {
        return value != null && value.matches("(?i)[A-Z0-9][A-Z0-9._-]{1,63}");
    }

    private static String decryptRegisterVersionToken(String encrypted, String key) {
        try {
            if (blank(encrypted) || key == null || key.getBytes(StandardCharsets.UTF_8).length != 24) return null;
            Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "DESede"));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encrypted.trim()));
            String value = new String(plain, StandardCharsets.UTF_8).trim();
            return validRegistrationToken(value) ? value : null;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * LiteDB stores these relation documents as BSON-style string fields.  We scan
     * only the three named fields and require their BSON string type/length framing;
     * crypto + current VersionID validation below prevents unrelated byte matches
     * from becoming executable evidence.
     */
    private static List<EncryptedRegisterVersionRow> scanRegisterVersionRows(byte[] data) {
        ArrayList<EncryptedRegisterVersionRow> out = new ArrayList<EncryptedRegisterVersionRow>();
        if (data == null || data.length < 32) return out;
        byte[] versionField = "versionID\0".getBytes(StandardCharsets.US_ASCII);
        byte[] productField = "parentProID\0".getBytes(StandardCharsets.US_ASCII);
        byte[] parentVersionField = "parentVersionID\0".getBytes(StandardCharsets.US_ASCII);
        HashSet<String> seen = new HashSet<String>();
        int from = 0;
        while (from < data.length) {
            int versionPos = indexOfBytes(data, versionField, from, data.length);
            if (versionPos < 0) break;
            int nextVersion = indexOfBytes(data, versionField, versionPos + versionField.length, data.length);
            int limit = Math.min(data.length, versionPos + 768);
            if (nextVersion >= 0) limit = Math.min(limit, nextVersion);
            String version = readBsonStringField(data, versionPos, versionField.length, limit);
            int productPos = indexOfBytes(data, productField, versionPos + versionField.length, limit);
            int parentVersionPos = indexOfBytes(data, parentVersionField,
                    productPos < 0 ? versionPos + versionField.length : productPos + productField.length, limit);
            String product = productPos < 0 ? null
                    : readBsonStringField(data, productPos, productField.length, limit);
            String parentVersion = parentVersionPos < 0 ? null
                    : readBsonStringField(data, parentVersionPos, parentVersionField.length, limit);
            if (!blank(version) && !blank(product) && !blank(parentVersion)) {
                String identity = version + "\u0000" + product + "\u0000" + parentVersion;
                if (seen.add(identity)) out.add(new EncryptedRegisterVersionRow(version, product, parentVersion));
            }
            from = versionPos + versionField.length;
        }
        return out;
    }

    private static String readBsonStringField(byte[] data, int namePos, int nameLength, int limit) {
        try {
            if (namePos <= 0 || (data[namePos - 1] & 255) != 0x02) return null;
            int lengthPos = namePos + nameLength;
            if (lengthPos + 4 > limit) return null;
            int length = le32(data, lengthPos);
            if (length < 2 || length > 1024) return null;
            int valuePos = lengthPos + 4;
            if (valuePos + length > limit || data[valuePos + length - 1] != 0) return null;
            return new String(data, valuePos, length - 1, StandardCharsets.UTF_8).trim();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static int le32(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) return -1;
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16) | ((data[offset + 3] & 255) << 24);
    }

    private static int indexOfBytes(byte[] data, byte[] needle, int start, int limit) {
        if (data == null || needle == null || needle.length == 0) return -1;
        int last = Math.min(data.length, limit) - needle.length;
        outer: for (int i = Math.max(0, start); i <= last; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static boolean targetContainsAll(File file, String... tokens) {
        if (file == null || !file.isFile() || tokens == null) return false;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            for (String token : tokens) {
                if (blank(token)) return false;
                byte[] ascii = token.getBytes(StandardCharsets.US_ASCII);
                byte[] utf16 = token.getBytes(StandardCharsets.UTF_16LE);
                if (indexOfBytes(data, ascii, 0, data.length) < 0
                        && indexOfBytes(data, utf16, 0, data.length) < 0) return false;
            }
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    static String detectProduct(File webDll,String version) {
        Set<String> s=extractUtf16Ascii(webDll);
        if(s.contains("itmcIEC"))return "itmcIEC";
        // GM00401: require evidence from the target assembly itself.
        if(version!=null && version.matches("GM\\d{5}")) {
            String family=version.substring(0,5);
            if(s.contains(version) && s.contains(family)) return family;
        }
        if(version!=null && version.matches("YX\\d{6}")) {
            String derived=version.substring(0,6);
            // Do not trust folder/version prefix alone. YX030107 is a real YX0302-family
            // sample; its protected ITMC.Web code sets ProName=YX0302. Require the
            // derived family to exist in this target DLL, otherwise use assembly evidence.
            if(s.contains(derived))return derived;
        }
        for(String x:s)if(x.matches("YX\\d{4}"))return x;
        TreeSet<String> gmFamilies=new TreeSet<String>();
        for(String x:s)if(x.matches("GM\\d{5}")) {
            String family=x.substring(0,5);
            if(s.contains(family)) gmFamilies.add(family);
        }
        if(gmFamilies.size()==1)return gmFamilies.first();
        TreeSet<String> derived=new TreeSet<String>(); for(String x:s)if(x.matches("YX\\d{6}"))derived.add(x.substring(0,6));
        return derived.size()==1?derived.first():null;
    }
    static String detectProductList(File webDll,String product) {
        Set<String>s=extractUtf16Ascii(webDll);TreeSet<String> out=new TreeSet<String>();
        if("itmcIEC".equals(product)){for(String x:s)if(x.matches("DS\\d{4}"))out.add(x);}
        else if(product!=null&&product.matches("YX\\d{4}")){for(String x:s)if(x.matches(Pattern.quote(product)+"\\d{2}"))out.add(x);}
        else if(product!=null&&product.matches("GM\\d{3}")){for(String x:s)if(x.matches(Pattern.quote(product)+"\\d{2}"))out.add(x);}
        StringBuilder b=new StringBuilder();for(String x:out){if(b.length()>0)b.append(',');b.append(x);}return b.toString();
    }

    /**
     * Resolve .NET RegStr only from the selected application directory.  A valid
     * existing local regName is stronger evidence than string scraping: decrypt it
     * with the ProName already proven by ITMC.Web.dll, require the embedded ProName
     * to match, and then reuse its RegStr.  If no valid local license exists, keep
     * the existing DLL product-list parser as the secondary source.
     */
    static String detectDotNetRegStr(Detection d) {
        if (d == null || blank(d.productName) || d.runtimeDir == null) return null;
        String direct = detectDirectDotNetRegStr(d);
        if (!blank(direct)) return direct;
        String local = recoverDotNetLocalRegStr(d.appRoot, d.runtimeDir, d.productName);
        if (!blank(local)) return local;
        return detectProductList(new File(d.runtimeDir, "ITMC.Web.dll"), d.productName);
    }

    static String recoverDotNetLocalRegStr(File root, File runtimeDir, String product) {
        if (blank(product)) return null;
        File[] candidates = new File[]{
                root == null ? null : new File(root, "config.xml"),
                runtimeDir == null ? null : new File(runtimeDir, "config.xml")
        };
        HashSet<String> seen = new HashSet<String>();
        for (File cfg : candidates) {
            if (cfg == null || !cfg.isFile()) continue;
            try {
                String key = cfg.getCanonicalPath().toLowerCase(Locale.ROOT);
                if (!seen.add(key)) continue;
                String encrypted = firstElement(readUtf8(cfg), "regName");
                if (blank(encrypted)) continue;
                String plain = desDecryptHex(encrypted.trim(), "*ITMC" + product.trim() + "OK*");
                int begin = plain.indexOf('{'), end = plain.lastIndexOf('}');
                if (begin < 0 || end <= begin) continue;
                String json = plain.substring(begin, end + 1);
                String embeddedProduct = jsonStringField(json, "ProName");
                String regStr = normalizeProductCsv(jsonStringField(json, "RegStr"));
                if (product.trim().equalsIgnoreCase(embeddedProduct) && !blank(regStr)) return regStr;
            } catch (Throwable ignore) { }
        }
        return null;
    }

    private static String jsonStringField(String json, String name) {
        if (json == null || name == null) return null;
        String marker = "\"" + name + "\"";
        int key = json.indexOf(marker);
        if (key < 0) return null;
        int colon = json.indexOf(':', key + marker.length());
        if (colon < 0) return null;
        int begin = json.indexOf('\"', colon + 1);
        if (begin < 0) return null;
        int end = json.indexOf('\"', begin + 1);
        if (end < 0) return null;
        return json.substring(begin + 1, end).trim();
    }

    private static String normalizeProductCsv(String raw) {
        if (blank(raw)) return null;
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            if (!token.matches("(?i)[A-Z0-9][A-Z0-9._-]{1,63}")) return null;
            out.add(token.toUpperCase(Locale.ROOT));
        }
        if (out.isEmpty()) return null;
        StringBuilder b = new StringBuilder();
        for (String token : out) { if (b.length() > 0) b.append(','); b.append(token); }
        return b.toString();
    }
    static Set<String> extractUtf16Ascii(File f) {
        LinkedHashSet<String> raw = new LinkedHashSet<String>();
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (f == null || !f.isFile()) return out;
        try {
            byte[] b = Files.readAllBytes(f.toPath());
            // .NET #US strings are commonly UTF-16LE.
            for (int parity = 0; parity < 2; parity++) {
                StringBuilder s = new StringBuilder();
                for (int i = parity; i + 1 < b.length; i += 2) {
                    int c = b[i] & 255, z = b[i + 1] & 255;
                    if (z == 0 && c >= 32 && c <= 126) s.append((char) c);
                    else { if (s.length() >= 4) raw.add(s.toString()); s.setLength(0); }
                }
                if (s.length() >= 4) raw.add(s.toString());
            }
            // Metadata/string heaps can also expose plain ASCII/UTF-8 runs.
            StringBuilder ascii = new StringBuilder();
            for (byte value : b) {
                int c = value & 255;
                if (c >= 32 && c <= 126) ascii.append((char) c);
                else { if (ascii.length() >= 4) raw.add(ascii.toString()); ascii.setLength(0); }
            }
            if (ascii.length() >= 4) raw.add(ascii.toString());
        } catch (Exception ignore) { return out; }

        Pattern token = Pattern.compile("(?i)(?<![A-Z0-9])(itmcIEC|DS\\d{4}|YX\\d{4}(?:\\d{2})?|GM\\d{3}(?:\\d{2})?)(?![A-Z0-9])");
        for (String text : raw) {
            out.add(text);
            Matcher m = token.matcher(text);
            while (m.find()) {
                String x = m.group(1);
                out.add("itmcIEC".equalsIgnoreCase(x) ? "itmcIEC" : x.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }
    private static boolean containsAscii(File f,String text) {
        try{byte[]b=Files.readAllBytes(f.toPath()),n=text.getBytes(StandardCharsets.US_ASCII);outer:for(int i=0;i+n.length<=b.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;return true;}}catch(Exception ignore){}return false;
    }

    private static void blockSidecars(Detection d,boolean backup,boolean dryRun,Consumer<String>log)throws Exception{
        Pattern p=Pattern.compile("(?i)https?://regservice\\.itmc\\.cn(?::\\d+)?(?:/[^\\s<>\\\"']*)?");
        List<File>fs=new ArrayList<File>();addIfFile(fs,new File(d.appRoot,"Web.config"));addConfigs(fs,d.appRoot);addConfigs(fs,d.runtimeDir);
        List<File>changed=new ArrayList<File>();List<String>orig=new ArrayList<String>(),upd=new ArrayList<String>();
        for(File f:fs){String a=readUtf8(f),b=p.matcher(a).replaceAll(BLOCK_ENDPOINT);if(!a.equals(b)){validateXml(b);changed.add(f);orig.add(a);upd.add(b);}}
        if(dryRun){for(File f:changed)log.accept("[dry-run] would block authorization URL in "+f.getAbsolutePath()+"\n");return;}
        try{for(int i=0;i<changed.size();i++){File f=changed.get(i);if(backup){File bk=uniqueBackup(f);Files.copy(f.toPath(),bk.toPath(),StandardCopyOption.REPLACE_EXISTING);log.accept("[backup] "+bk.getAbsolutePath()+"\n");}Files.write(f.toPath(),upd.get(i).getBytes(StandardCharsets.UTF_8));validateXml(readUtf8(f));}}
        catch(Throwable ex){for(int i=0;i<changed.size();i++)try{Files.write(changed.get(i).toPath(),orig.get(i).getBytes(StandardCharsets.UTF_8));}catch(Throwable r){ex.addSuppressed(r);}if(ex instanceof Exception)throw(Exception)ex;throw new Exception(ex);}
    }
    private static void addIfFile(List<File>x,File f){if(f!=null&&f.isFile()&&!x.contains(f))x.add(f);} private static void addConfigs(List<File>x,File d){if(d==null||!d.isDirectory())return;File[]fs=d.listFiles();if(fs==null)return;for(File f:fs){String n=f.getName().toLowerCase(Locale.ROOT);if(n.endsWith(".dll.config")||n.endsWith(".exe.config"))addIfFile(x,f);}}

    private static String wmic(String alias,String property) {
        String v=firstValue(runCapture(new String[]{"wmic",alias,"get",property,"/value"}),property+"=");if(v!=null)return v;
        String cls="cpu".equals(alias)?"Win32_Processor":"diskdrive".equals(alias)?"Win32_DiskDrive":"Win32_BaseBoard";
        String ps="(Get-CimInstance "+cls+" | Select-Object -First 1 -ExpandProperty "+property+")";
        String raw=runCapture(new String[]{"powershell.exe","-NoProfile","-NonInteractive","-Command",ps});if(raw==null)return null;String[]ls=raw.trim().split("\\r?\\n");return ls.length==0?null:ls[0].trim();
    }
    private static String firstValue(String raw,String prefix){if(raw==null)return null;for(String l:raw.split("\\r?\\n")){String s=l.trim();if(s.regionMatches(true,0,prefix,0,prefix.length())){String v=s.substring(prefix.length()).trim();if(!v.isEmpty())return v;}}return null;}
    private static String runCapture(String[]cmd){try{Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();ByteArrayOutputStream o=new ByteArrayOutputStream();InputStream in=p.getInputStream();byte[]b=new byte[4096];int n;while((n=in.read(b))>=0)o.write(b,0,n);p.waitFor();if(p.exitValue()!=0)return null;Charset c;try{c=Charset.forName("GBK");}catch(Exception e){c=StandardCharsets.UTF_8;}return new String(o.toByteArray(),c);}catch(Throwable e){return null;}}

    private static int run(List<String>cmd,Consumer<String>log)throws Exception{
        final Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();
        final BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8));
        Thread pump=new Thread(new Runnable(){
            public void run(){
                try{
                    String s;while((s=r.readLine())!=null)log.accept(s+"\n");
                }catch(IOException ignore){}
            }
        },"LicenseRecover-OneClick-Output");
        pump.setDaemon(true);
        pump.start();
        try{
            while(true){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("cancelled");
                if(p.waitFor(200L,java.util.concurrent.TimeUnit.MILLISECONDS)){
                    try{pump.join(1000L);}catch(InterruptedException ex){throw ex;}
                    return p.exitValue();
                }
            }
        }catch(InterruptedException ex){
            p.destroy();
            try{
                if(!p.waitFor(1000L,java.util.concurrent.TimeUnit.MILLISECONDS))p.destroyForcibly();
            }catch(InterruptedException again){
                p.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw ex;
        }finally{
            try{r.close();}catch(IOException ignore){}
        }
    }
    private static String firstElement(String xml,String name){Matcher m=Pattern.compile("(?is)<"+Pattern.quote(name)+"\\b[^>]*>\\s*([^<]*?)\\s*</"+Pattern.quote(name)+"\\s*>").matcher(xml);return m.find()?unxml(m.group(1).trim()):null;}
    private static void validateXml(String xml)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();try{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Exception ignore){}f.setExpandEntityReferences(false);f.setXIncludeAware(false);f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));}
    private static String readUtf8(File f)throws IOException{return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);} private static String match(Pattern p,String s){Matcher m=p.matcher(s==null?"":s);return m.find()?m.group(1).trim():null;}
    private static File uniqueBackup(File f){String t=new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());File b=new File(f.getParentFile(),f.getName()+"."+t+".preoneclick.bak");int i=1;while(b.exists())b=new File(f.getParentFile(),f.getName()+"."+t+"-"+(i++)+".preoneclick.bak");return b;}
    private static String randomDigits(int n){int min=1;for(int i=1;i<n;i++)min*=10;return String.valueOf(min+RNG.nextInt(min*9));}
    private static byte[] unhex(String s){s=s.trim();if((s.length()&1)!=0)throw new IllegalArgumentException("odd hex length");byte[]b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    private static String hex(byte[]b){StringBuilder s=new StringBuilder(b.length*2);for(byte x:b)s.append(String.format(Locale.ROOT,"%02X",x&255));return s.toString();}
    private static String json(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"");} private static String xmlEscape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");} private static String unxml(String s){return s.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&amp;","&");}
    private static boolean blank(String s){return s==null||s.trim().isEmpty();} private static String unknown(String s){return blank(s)?"unknow":s.trim();} private static boolean eqUnknown(String s){return "unknow".equalsIgnoreCase(s);}
    private static boolean isWindows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");} private static String javaExe(){return new File(System.getProperty("java.home"),"bin"+File.separator+(isWindows()?"java.exe":"java")).getAbsolutePath();}
    private static File toolDir(){try{File f=new File(LicenseRecoverModernGUIAutoRecovery.class.getProtectionDomain().getCodeSource().getLocation().toURI());return f.isFile()?f.getParentFile():f;}catch(Exception e){return new File(".").getAbsoluteFile();}}
    private static String safe(Throwable e){String s=e==null?"unknown error":e.getMessage();return blank(s)?String.valueOf(e):s;}
}
