from pathlib import Path
import re


def load(path):
    return Path(path).read_text(encoding='utf-8')


def save(path, text):
    Path(path).write_text(text, encoding='utf-8', newline='')


def rep(text, old, new, label):
    if old not in text:
        raise SystemExit('missing patch anchor: ' + label)
    if text.count(old) != 1:
        raise SystemExit('non-unique patch anchor: ' + label + ' count=' + str(text.count(old)))
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Core Java mapping: YT00138 production code uses RegisterMain("YT001", ...)
# and requires RegStr.contains("YT00138").
# -----------------------------------------------------------------------------
p = 'src/main/java/LicenseRecover.java'
s = load(p)
s = rep(s,
'''        if ("DS2406".equalsIgnoreCase(softId)) return "DS2406";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();''',
'''        if ("DS2406".equalsIgnoreCase(softId)) return "DS2406";\n        // YT00138 production RegisterUtil falls back to RegisterMain("YT001", rootPath)\n        // and requires the returned RegStr to contain the concrete VersionID.\n        if ("YT00138".equalsIgnoreCase(softId)) return "YT00138";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();''',
'core YT00138 RegStr')
s = rep(s,
'''        if (softId != null && "DS2406".equalsIgnoreCase(softId.trim())) return "DS24";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();''',
'''        if (softId != null && "DS2406".equalsIgnoreCase(softId.trim())) return "DS24";\n        // Exact YT00138 sample: SysParamInit -> RegisterUtil.checkRegister() falls through\n        // to productMain=YT001 and productMainNum=YT00138. Using QT1001 creates a\n        // regName that the real Tomcat startup path cannot decrypt and can NPE in CheckLocalReg.\n        if (softId != null && "YT00138".equalsIgnoreCase(softId.trim())) return "YT001";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();''',
'core YT00138 runtime product')
s = rep(s,
'''        if ("DS2406".equals(id)) return "DS24";\n        if (id.startsWith("DS501")) return "DS501";''',
'''        if ("DS2406".equals(id)) return "DS24";\n        if ("YT00138".equals(id)) return "YT001";\n        if (id.startsWith("DS501")) return "DS501";''',
'core YT00138 local registration family')
save(p, s)

# -----------------------------------------------------------------------------
# GUI Java plan: expose the same exact YT00138 decision before batch execution.
# -----------------------------------------------------------------------------
p = 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
s = load(p)
s = rep(s,
'''            generation = "DS2406".equals(upper)\n                    ? "DS24 / data-config"\n                    : (upper.matches("DS28\\\\d{2}") ? "DS28 / data-config" : "YT/兼容根配置 / data-config");''',
'''            generation = "DS2406".equals(upper)\n                    ? "DS24 / data-config"\n                    : ("YT00138".equals(upper)\n                        ? "YT001 / data-config"\n                        : (upper.matches("DS28\\\\d{2}") ? "DS28 / data-config" : "YT/兼容根配置 / data-config"));''',
'GUI YT00138 generation')
s = rep(s,
'''        if ("DS2406".equalsIgnoreCase(softId)) return "DS2406";\n        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("DS501")) return softId.trim();''',
'''        if ("DS2406".equalsIgnoreCase(softId)) return "DS2406";\n        if ("YT00138".equalsIgnoreCase(softId)) return "YT00138";\n        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("DS501")) return softId.trim();''',
'GUI YT00138 RegStr')
s = rep(s,
'''        if ("DS2406".equals(id)) return "DS24";\n        if (id.startsWith("DS501")) return "DS501";''',
'''        if ("DS2406".equals(id)) return "DS24";\n        if ("YT00138".equals(id)) return "YT001";\n        if (id.startsWith("DS501")) return "DS501";''',
'GUI YT00138 family')
s = rep(s,
'''        if ("DS2406".equals(id)) return "DS24";\n        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();''',
'''        if ("DS2406".equals(id)) return "DS24";\n        if ("YT00138".equals(id)) return "YT001";\n        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();''',
'GUI YT00138 runtime')
save(p, s)

# -----------------------------------------------------------------------------
# .NET one-click: stop synthesizing regName in Java. Invoke the target's own
# ITMC.Regedit workflow via the bundled helper: gencode -> doreg -> verify.
# Block cloud endpoints only after native verification succeeds.
# -----------------------------------------------------------------------------
p = 'src/main/java/LicenseRecoverModernGUIAutoRecovery.java'
s = load(p)
start = s.index('    private static Result recoverDotNet(')
end = s.index('    private static void verifyPersisted(', start)
new_block = r'''    private static Result recoverDotNet(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        if (!isWindows()) return Result.fail(".NET native registration runs only on Windows.", d);
        File helper = findDotNetNativeHelper();
        if (helper == null) return Result.fail("LicenseRecover.NET.exe was not found; native IIS registration cannot run.", d);

        String product = d.productName;
        String regStr = blank(product) ? null : detectProductList(new File(d.runtimeDir, "ITMC.Web.dll"), product);
        if (blank(regStr)) regStr = d.versionId;

        List<String> generate = new ArrayList<String>();
        generate.add(helper.getAbsolutePath());
        generate.add("gencode");
        generate.add(d.runtimeDir.getAbsolutePath());
        if (!blank(product)) { generate.add("--product"); generate.add(product); }
        if (!blank(regStr)) { generate.add("--regstr"); generate.add(regStr); }
        // gencode is read-only; it asks the target registration assembly for the
        // local request code and builds the matching offline code.
        NativeProcessResult generated = runNativeCapture(generate, d.appRoot, log);
        if (generated.exitCode != 0)
            return Result.fail("Target-native .NET request-code generation failed; no file was changed.", d);

        String request = findLabeledHex(generated.output, "注册申请号", "申请号");
        String auth = findLabeledHex(generated.output, "离线授权码", "授权码");
        if (blank(request) || blank(auth))
            return Result.fail("Native helper did not return both request code and authorization code; no file was changed.", d);

        String machine = null;
        try {
            String plain = desDecryptHex(request, "itmcsoft");
            if (plain != null && plain.length() >= 20) machine = plain.substring(4, 20);
        } catch (Throwable ignore) { }

        log.accept("[one-click] .NET native registration flow: gencode -> DoRegistry -> CheckReInfo\n");
        log.accept("[one-click] ProName=" + valueOrPending(product) + " products=" + valueOrPending(regStr)
                + " RegID=" + valueOrPending(machine) + "\n");
        if (dryRun) {
            log.accept("[dry-run] native request/auth codes generated; DoRegistry was not called and no file was changed.\n");
            return new Result(true, "Preview completed with target-native request-code generation; no file was changed.",
                    d, machine, request, auth);
        }

        LinkedHashMap<File, byte[]> originals = snapshotDotNetRegistrationFiles(d);
        if (backup) backupDotNetRegistrationFiles(originals, log);
        try {
            List<String> apply = new ArrayList<String>();
            apply.add(helper.getAbsolutePath());
            apply.add("doreg");
            apply.add(d.runtimeDir.getAbsolutePath());
            apply.add("--seq"); apply.add(request);
            apply.add("--code"); apply.add(auth);
            if (!blank(product)) { apply.add("--product"); apply.add(product); }
            // Verify the untouched native local-registration result first; cloud blocking is a separate final step.
            apply.add("--no-block-net");
            if (!backup) apply.add("--no-backup");
            NativeProcessResult applied = runNativeCapture(apply, d.appRoot, log);
            if (applied.exitCode != 0) throw new IOException("target RegeditMain.DoRegistry() rejected the generated code");

            List<String> verify = new ArrayList<String>();
            verify.add(helper.getAbsolutePath());
            verify.add("verify");
            verify.add(d.runtimeDir.getAbsolutePath());
            if (!blank(product)) { verify.add("--product"); verify.add(product); }
            NativeProcessResult checked = runNativeCapture(verify, d.appRoot, log);
            if (checked.exitCode != 0) throw new IOException("target RegeditMain.CheckReInfo() did not confirm the native write-back");

            if (blockNet) {
                blockPrimaryDotNetConfigs(d, log);
                blockSidecars(d, backup, false, log);
            }
            log.accept("[verify] target-native DoRegistry + CheckReInfo passed; authorization cloud blocking applied afterwards.\n");
            return new Result(true,
                    ".NET local authorization was applied by the target registration assembly and native verification passed. Restart the IIS app pool/site.",
                    d, machine, request, auth);
        } catch (Throwable ex) {
            restoreDotNetRegistrationFiles(originals, log);
            if (ex instanceof Exception) throw (Exception) ex;
            throw new Exception(ex);
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

    private static File findDotNetNativeHelper() {
        File dir = toolDir();
        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.exe");
        if (nested.isFile()) return nested;
        File flat = new File(dir, "LicenseRecover.NET.exe");
        return flat.isFile() ? flat : null;
    }

    private static LinkedHashMap<File, byte[]> snapshotDotNetRegistrationFiles(Detection d) throws IOException {
        LinkedHashMap<File, byte[]> out = new LinkedHashMap<File, byte[]>();
        File[] candidates = new File[]{
                new File(d.appRoot, "config.xml"), new File(d.runtimeDir, "config.xml"),
                new File(d.appRoot, "Register.xml"), new File(d.runtimeDir, "Register.xml")
        };
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
            String updated = putElement(original, "Service", BLOCK_ENDPOINT);
            validateXml(updated);
            if (!original.equals(updated)) {
                Files.write(f.toPath(), updated.getBytes(StandardCharsets.UTF_8));
                if (log != null) log.accept("[block-net] " + f.getAbsolutePath() + "\n");
            }
        }
    }

    private static String valueOrPending(String value) { return blank(value) ? "<auto>" : value; }

'''
s = s[:start] + new_block + s[end:]
# Product selection must be supported by strings actually present in the target assembly.
s = rep(s,
'''        if(version!=null && version.matches("YX\\\\d{6}"))return version.substring(0,6);\n        for(String x:s)if(x.matches("YX\\\\d{4}"))return x;''',
'''        if(version!=null && version.matches("YX\\\\d{6}")) {\n            String derived=version.substring(0,6);\n            // Do not trust folder/version prefix alone. YX030107 is a real YX0302-family\n            // sample; its protected ITMC.Web code sets ProName=YX0302. Require the\n            // derived family to exist in this target DLL, otherwise use assembly evidence.\n            if(s.contains(derived))return derived;\n        }\n        for(String x:s)if(x.matches("YX\\\\d{4}"))return x;''',
'.NET evidence-based YX family detection')
save(p, s)

# -----------------------------------------------------------------------------
# Regression tests.
# -----------------------------------------------------------------------------
p = 'src/test/java/RefactorSmokeTest.java'
s = load(p)
anchor = '''        check("QT0420".equals(yt129Plan.regStr) && yt129Plan.rootConfigStyle,\n                "Java GUI plan exposes YT00129 RegStr and root config target");\n\n'''
insert = anchor + '''        Path yt138Root = base.resolve("java-YT00138");\n        Path yt138Lib = yt138Root.resolve("WEB-INF/lib");\n        Files.createDirectories(yt138Lib);\n        Files.createDirectories(yt138Root.resolve("data"));\n        Files.write(yt138Lib.resolve("ITMCReg.jar"), new byte[]{1});\n        Files.write(yt138Root.resolve("systemConfig.yml"), Arrays.asList("global.system.VersionID=YT00138"), StandardCharsets.UTF_8);\n        Files.write(yt138Root.resolve("data/config.xml"), Arrays.asList(\n                "<ROOT><SystemSoft><SoftVersionID>YT00138</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);\n        check("YT001".equals(LicenseRecover.productMainFor("YT00138")),\n                "YT00138 runtime RegisterMain uses YT001 family");\n        check("YT001".equals(LicenseRecover.localRegisterProductFor("YT00138", false)),\n                "YT00138 local registration uses YT001 family");\n        check("YT00138".equals(LicenseRecover.resolveJavaRegStr(yt138Root.toString(), "YT00138")),\n                "YT00138 RegStr uses the concrete VersionID required by RegisterUtil");\n        LicenseRecoverModernGUIJavaPlan yt138Plan = LicenseRecoverModernGUIJavaPlan.inspect(yt138Root.toFile());\n        check("YT001".equals(yt138Plan.authorizationFamily) && "YT001".equals(yt138Plan.runtimeProductId),\n                "YT00138 GUI plan matches production startup family");\n        check("YT00138".equals(yt138Plan.regStr) && yt138Plan.automaticRecoveryReady,\n                "YT00138 GUI plan is ready with exact sample-verified RegStr");\n\n'''
s = rep(s, anchor, insert, 'YT00138 smoke tests')
anchor2 = '''        check("YX030301,YX030308,YX030322".equals(\n                        LicenseRecoverModernGUIAutoRecovery.detectProductList(\n                                productFixture.toFile(), "YX0303")),\n                "one-click derives YX0303 local product list");\n\n'''
insert2 = anchor2 + '''        Path yx302CrossFixture = base.resolve("YX030107-YX0302-Web.dll");\n        writeUtf16Fixture(yx302CrossFixture, "YX030107", "YX0302", "YX030201", "YX030204", "YX030219");\n        check("YX0302".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(\n                        yx302CrossFixture.toFile(), "YX030107")),\n                "YX030107 does not get misclassified as YX0301 when target DLL proves YX0302 family");\n        check("YX030201,YX030204,YX030219".equals(\n                        LicenseRecoverModernGUIAutoRecovery.detectProductList(yx302CrossFixture.toFile(), "YX0302")),\n                "YX030107/YX0302 sample derives the target-local product list");\n\n        String nativeOut = "注册申请号     : A1B2C3D4\\n离线授权码     : 001122AABB\\n";\n        check("A1B2C3D4".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "注册申请号")),\n                "native .NET one-click parses target request code");\n        check("001122AABB".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "离线授权码")),\n                "native .NET one-click parses target authorization code");\n\n'''
s = rep(s, anchor2, insert2, '.NET native smoke tests')
save(p, s)

# -----------------------------------------------------------------------------
# Version + notes.
# -----------------------------------------------------------------------------
Path('VERSION.txt').write_text('1.2.13\n', encoding='utf-8')
notes = '''# LicenseRecover v1.2.13\n\n## YT00138 Tomcat startup fix\n\n- Re-analyzed the supplied YT00138 production package after restoring 630 protected application classes and all 24 protected ITMCReg classes.\n- The real startup chain `SysParamInit -> RegisterUtil.checkRegister -> registerMethod` falls back to `RegisterMain(\"YT001\", ..., rootPath)` for YT00138, not `QT1001`.\n- YT00138 now uses authorization family/runtime ProductID `YT001`, key `*ITMCYT001OK*`, and minimum RegStr `YT00138`.\n- This prevents the previous false-positive tool check where a `QT1001` regName passed the tool-created RegisterMain but the real Tomcat startup path could not decrypt it and crashed in `CheckLocalReg`.\n\n## IIS/.NET native one-click registration\n\n- Modern .NET one-click no longer treats Java-side synthetic XML encryption/decryption as proof of activation.\n- It now invokes the bundled native helper against the target registration assembly in three phases: `gencode` -> `doreg` -> `verify`.\n- The target application's own local request/machine-code path and `RegeditMain.DoRegistry()` perform the write-back; `CheckReInfo()` must pass before the row is reported successful.\n- Authorization-cloud endpoints are redirected to the local inert endpoint only after native verification succeeds.\n- Existing registration/config files are snapshotted and restored if native submission or verification fails.\n- YX six-digit folder/version prefixes are no longer blindly truncated to choose ProName: the derived family must exist in the target DLL. This fixes the supplied YX030107 sample whose actual ProName is `YX0302`.\n\n## Safety\n\n- Batch mode uses the same one-click coordinator, so these protections apply to both single-app and batch execution.\n- Java 8 compatibility and the existing pre-write backup/rollback behavior are retained.\n'''
Path('release-notes/v1.2.13.md').write_text(notes, encoding='utf-8')

p = 'CHANGELOG.md'
s = load(p)
marker = '# Changelog\n'
if marker not in s:
    raise SystemExit('CHANGELOG marker missing')
entry = '''# Changelog\n\n## v1.2.13\n\n- Fixed YT00138 to use the production `YT001` RegisterMain family and concrete `YT00138` RegStr instead of the generic QT1001 fallback that could crash real Tomcat startup.\n- Reworked modern IIS/.NET one-click recovery to use target-native `gencode -> DoRegistry -> CheckReInfo` and only block the authorization cloud after native verification succeeds.\n- Made YX .NET ProName detection require assembly evidence so YX030107 correctly resolves to the YX0302 family.\n- Added rollback and regression coverage for the new YT00138 and .NET native paths.\n'''
s = s.replace(marker, entry, 1)
save(p, s)

print('v1.2.13 patch applied')
