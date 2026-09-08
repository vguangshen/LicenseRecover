#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def replace_once(path, old, new, label):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 anchor, found {n}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8', newline='\n')

# 1) .NET: replace the entire native flow so network isolation happens before gencode.
p = ROOT / 'src/main/java/LicenseRecoverModernGUIAutoRecovery.java'
s = p.read_text(encoding='utf-8')
pat = re.compile(r'    private static Result recoverDotNet\(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log\) throws Exception \{.*?\n    \}\n\n    static final class NativeProcessResult', re.S)
m = pat.search(s)
if not m:
    raise SystemExit('recoverDotNet method anchor not found')
new_method = r'''    private static Result recoverDotNet(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        if (!isWindows()) return Result.fail(".NET native registration runs only on Windows.", d);
        File helper = findDotNetNativeHelper();
        if (helper == null) return Result.fail("LicenseRecover.NET.exe was not found; native IIS registration cannot run.", d);

        String product = d.productName;
        if (blank(product))
            return Result.fail("Target ITMC.Web.dll did not prove a ProName; default product fallback is disabled.", d);
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
            // The target ITMC.Regedit assembly is loaded inside LicenseRecover.NET.exe, so an
            // application-specific Windows Firewall rule blocks even hard-coded outbound URLs.
            firewallRule = installTemporaryDotNetNetworkGuard(helper, log);
            if (blank(firewallRule))
                throw new IOException("failed to establish the temporary .NET outbound firewall guard; refusing to call vendor registration code");

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
            generate.add("--product"); generate.add(product);
            generate.add("--regstr"); generate.add(regStr);
            NativeProcessResult generated = runNativeCapture(generate, d.appRoot, log);
            if (generated.exitCode != 0)
                throw new IOException("Target-native .NET request-code generation failed under network isolation");

            String request = findLabeledHex(generated.output, "注册申请号", "申请号");
            String auth = findLabeledHex(generated.output, "离线授权码", "授权码");
            if (blank(request) || blank(auth))
                throw new IOException("Native helper did not return both request code and authorization code under network isolation");

            String machine = null;
            try {
                String plain = desDecryptHex(request, "itmcsoft");
                if (plain != null && plain.length() >= 20) machine = plain.substring(4, 20);
            } catch (Throwable ignore) { }

            log.accept("[one-click] .NET native registration flow: PRE-BLOCK -> gencode -> DoRegistry -> CheckReInfo\n");
            log.accept("[one-click] ProName=" + valueOrPending(product) + " products=" + valueOrPending(regStr)
                    + " RegID=" + valueOrPending(machine) + "\n");
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
            apply.add("--product"); apply.add(product);
            // Keep the helper from changing network settings itself; the outer coordinator
            // already established isolation before the first vendor-code call.
            apply.add("--no-block-net");
            if (!backup) apply.add("--no-backup");
            NativeProcessResult applied = runNativeCapture(apply, d.appRoot, log);
            if (applied.exitCode != 0) throw new IOException("target RegeditMain.DoRegistry() rejected the generated code");

            List<String> verify = new ArrayList<String>();
            verify.add(helper.getAbsolutePath());
            verify.add("verify");
            verify.add(d.runtimeDir.getAbsolutePath());
            verify.add("--product"); verify.add(product);
            NativeProcessResult checked = runNativeCapture(verify, d.appRoot, log);
            if (checked.exitCode != 0) throw new IOException("target RegeditMain.CheckReInfo() did not confirm the native write-back");

            if (blockNet) {
                // Re-assert in case the native writer rewrote config.xml while registering.
                blockPrimaryDotNetConfigs(d, log);
                blockSidecars(d, false, false, log);
            } else {
                restoreDotNetAuthorizationNetworkSettings(originals, d, log);
            }
            log.accept("[verify] target-native DoRegistry + CheckReInfo passed while authorization outbound traffic was isolated.\n");
            return new Result(true,
                    ".NET local authorization was applied and verified while authorization outbound traffic was isolated. Restart the IIS app pool/site.",
                    d, machine, request, auth);
        } catch (Throwable ex) {
            if (!dryRun && originals != null) restoreDotNetRegistrationFiles(originals, log);
            if (ex instanceof Exception) throw (Exception) ex;
            throw new Exception(ex);
        } finally {
            removeTemporaryDotNetNetworkGuard(firewallRule, log);
        }
    }

    static final class NativeProcessResult'''
s = s[:m.start()] + new_method + s[m.end():]
p.write_text(s, encoding='utf-8', newline='\n')

# 2) Add per-process proxy env to every .NET helper invocation.
replace_once('src/main/java/LicenseRecoverModernGUIAutoRecovery.java',
'''        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (workDir != null && workDir.isDirectory()) pb.directory(workDir);
''',
'''        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (workDir != null && workDir.isDirectory()) pb.directory(workDir);
        // Defense in depth for HTTP stacks that honor proxy environment variables.
        // The Windows Firewall application rule remains the authoritative guard.
        Map<String,String> env = pb.environment();
        env.put("HTTP_PROXY", "http://127.0.0.1:9");
        env.put("HTTPS_PROXY", "http://127.0.0.1:9");
        env.put("ALL_PROXY", "http://127.0.0.1:9");
        env.put("NO_PROXY", "localhost,127.0.0.1,::1");
''', 'native proxy environment')

# 3) Expand snapshot to include every file pre-block can touch.
replace_once('src/main/java/LicenseRecoverModernGUIAutoRecovery.java',
'''        File[] candidates = new File[]{
                new File(d.appRoot, "config.xml"), new File(d.runtimeDir, "config.xml"),
                new File(d.appRoot, "Register.xml"), new File(d.runtimeDir, "Register.xml")
        };
''',
'''        List<File> candidates = new ArrayList<File>();
        addIfFile(candidates, new File(d.appRoot, "config.xml"));
        addIfFile(candidates, new File(d.runtimeDir, "config.xml"));
        addIfFile(candidates, new File(d.appRoot, "Register.xml"));
        addIfFile(candidates, new File(d.runtimeDir, "Register.xml"));
        addIfFile(candidates, new File(d.appRoot, "Web.config"));
        addConfigs(candidates, d.appRoot);
        addConfigs(candidates, d.runtimeDir);
''', 'dotnet snapshot candidates')
replace_once('src/main/java/LicenseRecoverModernGUIAutoRecovery.java',
'''        for (File f : candidates) {
''', '''        for (File f : candidates) {
''', 'snapshot loop remains')

# 4) Insert firewall and network-setting restore helpers before valueOrPending.
replace_once('src/main/java/LicenseRecoverModernGUIAutoRecovery.java',
'''    private static String valueOrPending(String value) { return blank(value) ? "<auto>" : value; }
''',
r'''    static String installTemporaryDotNetNetworkGuard(File helper, Consumer<String> log) throws Exception {
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
''', 'network guard helpers')

# 5) Java: install a child-JVM-only authorization network guard before any target RegisterMain probe.
replace_once('src/main/java/LicenseRecover.java',
'''        String softId = plan.softVersionId;
        String productMain = plan.runtimeProductId;
''',
'''        String softId = plan.softVersionId;
        String productMain = plan.runtimeProductId;
        installJavaAuthorizationNetworkGuard();
''', 'java guard install')
replace_once('src/main/java/LicenseRecover.java',
'''    static String probeTargetRegStr(String product, String appRoot, String libDir, boolean rootConfigStyle) {
''',
r'''    static void installJavaAuthorizationNetworkGuard() {
        // This JVM is the dedicated LicenseRecover child process. Route Java HTTP/HTTPS
        // clients to an inert local endpoint before any target RegisterMain code executes.
        // We deliberately do not add a Windows Firewall rule for java.exe because that
        // executable may also host the user's running Tomcat or unrelated Java services.
        System.setProperty("java.net.useSystemProxies", "false");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "9");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "9");
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        System.setProperty("sun.net.client.defaultConnectTimeout", "3000");
        System.setProperty("sun.net.client.defaultReadTimeout", "3000");
        System.out.println("[pre-block] Java Patch 子进程 HTTP/HTTPS 已隔离到 127.0.0.1:9；随后才允许调用目标 RegisterMain。");
    }

    static String probeTargetRegStr(String product, String appRoot, String libDir, boolean rootConfigStyle) {
''', 'java guard helper')

# 6) Tests: assert source-order invariants and Java proxy guard properties without requiring Windows/netsh on CI.
tp = ROOT / 'src/test/java/RefactorSmokeTest.java'
ts = tp.read_text(encoding='utf-8')
anchor = '''        String nativeOut = "注册申请号     : A1B2C3D4\\n离线授权码     : 001122AABB\\n";
'''
if ts.count(anchor) != 1:
    raise SystemExit('test anchor missing')
insert = r'''        LicenseRecover.installJavaAuthorizationNetworkGuard();
        check("127.0.0.1".equals(System.getProperty("http.proxyHost"))
                        && "9".equals(System.getProperty("http.proxyPort"))
                        && "127.0.0.1".equals(System.getProperty("https.proxyHost"))
                        && "9".equals(System.getProperty("https.proxyPort")),
                "Java vendor RegisterMain calls are process-isolated before runtime probing/verification");

        String autoRecoverySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/LicenseRecoverModernGUIAutoRecovery.java")), StandardCharsets.UTF_8);
        int preBlockAt = autoRecoverySource.indexOf("installTemporaryDotNetNetworkGuard(helper, log)");
        int generateAt = autoRecoverySource.indexOf("generate.add(\\\"gencode\\\")");
        int applyAt = autoRecoverySource.indexOf("apply.add(\\\"doreg\\\")");
        int verifyAt = autoRecoverySource.indexOf("verify.add(\\\"verify\\\")");
        check(preBlockAt >= 0 && generateAt > preBlockAt && applyAt > generateAt && verifyAt > applyAt,
                ".NET pre-block guard is established before gencode, DoRegistry and CheckReInfo in source order");
        check(autoRecoverySource.contains("action=block")
                        && autoRecoverySource.contains("program=\" + helper.getAbsolutePath()")
                        && autoRecoverySource.contains("HTTP_PROXY")
                        && autoRecoverySource.contains("127.0.0.1:9"),
                ".NET native helper has application firewall guard plus proxy defense in depth");

'''
ts = ts.replace(anchor, insert + anchor, 1)
tp.write_text(ts, encoding='utf-8', newline='\n')

print('pre-block-first patch applied')
