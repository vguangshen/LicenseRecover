from pathlib import Path


def rd(path):
    return Path(path).read_text(encoding="utf-8")


def wr(path, content):
    Path(path).write_text(content, encoding="utf-8", newline="\n")


def one(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 match, found {n}")
    return text.replace(old, new, 1)


def replace_block(text, start_marker, end_marker, replacement, label):
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker not found")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:start] + replacement + text[end:]


# ---------------------------------------------------------------------------
# Java isolated host: exact constructor ABI + Virbox CodeSource preservation.
# ---------------------------------------------------------------------------
p = "src/main/java/LicenseRecoverJavaHost.java"
s = rd(p)
s = one(s, "import java.security.Permission;\n", "import java.security.Permission;\nimport java.security.CodeSource;\nimport java.security.ProtectionDomain;\nimport java.security.cert.Certificate;\n", "JavaHost security imports")
s = one(s, "import java.util.LinkedHashSet;\n", "import java.util.LinkedHashMap;\nimport java.util.LinkedHashSet;\nimport java.util.Map;\n", "JavaHost map imports")
s = one(s, "import java.util.Locale;\n", "import java.util.Locale;\nimport java.util.jar.JarEntry;\nimport java.util.jar.JarFile;\n", "JavaHost jar imports")
s = one(s, "        String code;\n", "        String code;\n        String ctorMode;\n", "JavaHost ctor option field")

child_loader = r'''    static final class ChildFirstLoader extends URLClassLoader {
        final Map<String, byte[]> restoredRegistrationClasses;
        final ProtectionDomain restoredRegistrationDomain;

        ChildFirstLoader(URL[] urls, ClassLoader parent, Map<String, byte[]> restoredRegistrationClasses,
                         File originalRegistrationJar) throws Exception {
            super(urls, parent);
            this.restoredRegistrationClasses = restoredRegistrationClasses == null
                    ? Collections.<String, byte[]>emptyMap() : restoredRegistrationClasses;
            this.restoredRegistrationDomain = originalRegistrationJar == null ? null
                    : new ProtectionDomain(new CodeSource(originalRegistrationJar.toURI().toURL(),
                    (Certificate[]) null), null, this, null);
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (parentFirst(name)) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try { loaded = findClass(name); }
                catch (ClassNotFoundException miss) { loaded = super.loadClass(name, false); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] restored = restoredRegistrationClasses.get(name);
            if (restored != null) {
                int split = name.lastIndexOf('.');
                if (split > 0) {
                    String pkg = name.substring(0, split);
                    if (getPackage(pkg) == null) {
                        try { definePackage(pkg, null, null, null, null, null, null, null); }
                        catch (IllegalArgumentException ignore) { }
                    }
                }
                return defineClass(name, restored, 0, restored.length, restoredRegistrationDomain);
            }
            return super.findClass(name);
        }

        private static boolean parentFirst(String name) {
            return name.startsWith("java.") || name.startsWith("javax.")
                    || name.startsWith("sun.") || name.startsWith("com.sun.")
                    || name.startsWith("jdk.") || name.startsWith("org.w3c.")
                    || name.startsWith("org.xml.");
        }
    }

'''
s = replace_block(s,
    "    static final class ChildFirstLoader extends URLClassLoader {",
    "    /**\n     * Java 8 process-local network guard.",
    child_loader,
    "JavaHost ChildFirstLoader")

s = one(s,
    '            else if ("--code".equals(a) && i + 1 < args.length) o.code = args[++i];\n',
    '            else if ("--code".equals(a) && i + 1 < args.length) o.code = args[++i];\n            else if ("--ctor".equals(a) && i + 1 < args.length) o.ctorMode = args[++i];\n',
    "JavaHost parse ctor")
s = one(s,
    '        if (blank(o.product)) throw new IllegalArgumentException("--product is required");\n',
    '        if (blank(o.product)) throw new IllegalArgumentException("--product is required");\n        if (blank(o.ctorMode)) throw new IllegalArgumentException("--ctor is required; constructor guessing is disabled");\n        JavaRegistrationRuntimeProfile.Mode.fromCli(o.ctorMode);\n',
    "JavaHost require ctor")

open_target = r'''    static TargetRuntime openTarget(File appRoot, File runtimeDir) throws Exception {
        ArrayList<URL> urls = new ArrayList<URL>();
        File[] jars = runtimeDir.listFiles();
        if (jars == null) jars = new File[0];
        Arrays.sort(jars, new Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });

        File registrationJar = JavaRegistrationRuntimeProfile.findRegistrationJar(runtimeDir);
        Map<String, byte[]> restored = Collections.emptyMap();
        if (registrationJar != null && NetRemover.jarIsPacked(registrationJar)) {
            restored = loadRestoredRegistrationClasses(registrationJar);
            System.out.println("[java-host] restored packed target registration classes=" + restored.size()
                    + " with original CodeSource=" + registrationJar.getAbsolutePath());
        }

        File classes = JavaRegistrationRuntimeProfile.findClassesDir(appRoot);
        if (classes != null && classes.isDirectory()) urls.add(classes.toURI().toURL());
        for (File jar : jars) {
            if (jar.isFile() && jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
                urls.add(jar.toURI().toURL());
        }
        ClassLoader platformParent = ClassLoader.getSystemClassLoader().getParent();
        ChildFirstLoader loader = new ChildFirstLoader(urls.toArray(new URL[urls.size()]), platformParent,
                restored, registrationJar);
        Thread.currentThread().setContextClassLoader(loader);
        System.out.println("[java-host] target classloader=child-first/platform-parent; target URLs=" + urls.size());
        return new TargetRuntime(loader, null);
    }

    static Map<String, byte[]> loadRestoredRegistrationClasses(File jar) throws Exception {
        byte[] key = NetRemover.loadKeystream();
        if (key == null) throw new IOException("missing virbox_keystream.bin for packed target registration jar");
        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        JarFile jf = new JarFile(jar);
        try {
            java.util.Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                java.io.InputStream in = jf.getInputStream(e);
                byte[] bytes;
                try {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
                    bytes = bos.toByteArray();
                } finally { in.close(); }
                if (NetRemover.isPacked(bytes)) {
                    bytes = NetRemover.unpackVirbox(bytes, key);
                    if (bytes == null) throw new IOException("Virbox restore failed: " + e.getName());
                }
                out.put(e.getName().substring(0, e.getName().length() - 6).replace('/', '.'), bytes);
            }
        } finally { jf.close(); }
        return out;
    }

'''
s = replace_block(s,
    "    static TargetRuntime openTarget(File appRoot, File runtimeDir) throws Exception {",
    "    static int probe(TargetRuntime rt, Options o) throws Exception {",
    open_target,
    "JavaHost openTarget")

s = s.replace("newRegisterMain(rt.loader, o.product, o.baseDir)", "newRegisterMain(rt.loader, o.product, o.baseDir, o.ctorMode)")
if s.count("newRegisterMain(rt.loader, o.product, o.baseDir, o.ctorMode)") != 3:
    raise SystemExit("JavaHost constructor call count mismatch")

s = one(s,
    '        catch (InvocationTargetException ex) { throw rethrow(ex); }\n        System.out.println("[java-stage] DOREG native-return=" + String.valueOf(result));\n',
    '        catch (InvocationTargetException ex) { throw rethrow(ex); }\n        if (result instanceof Boolean && !((Boolean) result).booleanValue())\n            throw new IllegalStateException("target RegisterMain.doRegistry() returned false");\n        System.out.println("[java-stage] DOREG native-return=" + String.valueOf(result));\n',
    "JavaHost doreg boolean result")

new_rm = r'''    static Object newRegisterMain(ClassLoader loader, String product, File baseDir, String ctorMode) throws Exception {
        JavaRegistrationRuntimeProfile.Mode mode = JavaRegistrationRuntimeProfile.Mode.fromCli(ctorMode);
        Class<?> rm = Class.forName("itmc.regedit.RegisterMain", true, loader);
        String path = withSeparator(baseDir);
        if (mode == JavaRegistrationRuntimeProfile.Mode.ONE_ARG_DEFAULT) {
            Constructor<?> c = rm.getConstructor(String.class);
            return construct(c, product);
        }
        if (mode == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_PATH) {
            Constructor<?> c = rm.getConstructor(String.class, String.class);
            return construct(c, product, path);
        }

        Class<?> codeClass = Class.forName("itmc.regedit.GetRegisterCode", true, loader);
        Object codeHelper = newInstance(codeClass);
        String token = str(invoke(codeHelper, "encrypt",
                new Class<?>[]{String.class, String.class},
                new Object[]{product + "RegeditNew", "itmcsoft"}));
        if (blank(token)) throw new IllegalStateException("target GetRegisterCode could not build RegisterMain token");
        if (mode == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_TOKEN_DEFAULT) {
            Constructor<?> c = rm.getConstructor(String.class, String.class);
            return construct(c, product, token);
        }
        if (mode == JavaRegistrationRuntimeProfile.Mode.THREE_ARG_TOKEN_PATH) {
            Constructor<?> c = rm.getConstructor(String.class, String.class, String.class);
            return construct(c, product, token, path);
        }
        throw new IllegalArgumentException("unsupported constructor mode: " + mode);
    }

    static Object construct(Constructor<?> c, Object... args) throws Exception {
        try { return c.newInstance(args); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
    }

'''
s = replace_block(s,
    "    static Object newRegisterMain(ClassLoader loader, String product, File baseDir) throws Exception {",
    "    static Object instantiateRegisterMainCompatible(Class<?> rm, String product, String token, String path) throws Exception {",
    new_rm,
    "JavaHost exact RegisterMain")
wr(p, s)


# ---------------------------------------------------------------------------
# One-click coordinator: only runtime-derived constructor/path attempts.
# ---------------------------------------------------------------------------
p = "src/main/java/LicenseRecoverModernGUIAutoRecovery.java"
s = rd(p)
recover = r'''    private static Result recoverJava(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(d.appRoot);
        if (plan.detected) log.accept(plan.logSummary());
        if (!plan.detected || !plan.automaticRecoveryReady)
            return Result.fail("自动恢复已阻止：" + (plan.detected ? plan.recoveryReadiness : "未识别 Java 注册结构") + "。未修改任何文件。", d);
        if (blank(plan.runtimeProductId))
            return Result.fail("[JAVA_CHAIN] 目标目录未确认运行注册ID。", d);

        JavaRegistrationRuntimeProfile runtimeProfile = JavaRegistrationRuntimeProfile.inspect(d.appRoot, d.runtimeDir);
        log.accept(runtimeProfile.logSummary());
        if (!runtimeProfile.supported)
            return Result.fail("[JAVA_RUNTIME_ABI] " + runtimeProfile.failureReason + "。未修改任何文件。", d);
        List<JavaRegistrationRuntimeProfile.Attempt> attempts = runtimeProfile.attempts;

        String product = plan.runtimeProductId.trim();
        String version = blank(plan.softVersionId) ? d.versionId : plan.softVersionId.trim();
        log.accept("[java-chain] target-native isolated helper; product=" + product
                + " version=" + valueOrPending(version) + " runtime=" + runtimeProfile.evidence + "\n");
        log.accept("[java-chain] helper system classpath=tool-only; target WEB-INF/classes/lib are child-first isolated\n");

        String regStr = normalizeProductCsv(plan.regStr);
        if (blank(regStr)) {
            for (JavaRegistrationRuntimeProfile.Attempt attempt : attempts) {
                log.accept("[java-stage] PROBE: start ctor=" + attempt.summary() + "\n");
                NativeProcessResult probed = runJavaHost(d, "probe", attempt, product, version,
                        null, null, null, log);
                if (probed.exitCode != 0) {
                    log.accept("[java-stage] PROBE: rejected ctor=" + attempt.summary()
                            + " output=" + compactNativeOutput(probed.output) + "\n");
                    continue;
                }
                String candidate = normalizeProductCsv(firstValue(probed.output, "TARGET_REGSTR="));
                if (blank(candidate)) continue;
                if (!blank(version) && !containsRegStrToken(candidate, version)) {
                    log.accept("[java-stage] PROBE: RegStr missing current SoftVersionID at ctor="
                            + attempt.summary() + ": " + candidate + "\n");
                    continue;
                }
                regStr = candidate;
                log.accept("[java-stage] PROBE: OK RegStr=" + regStr + "\n");
                break;
            }
        }
        if (blank(regStr))
            return Result.fail("[JAVA_MODE] 目标目录及目标 RegisterMain 均未证明可用 RegStr。", d);
        if (!blank(version) && !containsRegStrToken(regStr, version))
            return Result.fail("[JAVA_MODE] 当前 SoftVersionID 未进入 RegStr: " + version + "; RegStr=" + regStr, d);
        log.accept("[java-mode] current SoftVersionID=" + valueOrPending(version) + " RegStr=" + regStr + "\n");

        JavaRegistrationRuntimeProfile.Attempt primary = attempts.get(0);
        log.accept("[java-stage] GENCODE: start\n");
        NativeProcessResult generated = runJavaHost(d, "gencode", primary, product, version,
                regStr, null, null, log);
        if (generated.exitCode != 0)
            throw nativeFailure("JAVA_GENCODE", "target-native request/auth generation failed", generated);
        String request = findLabeledHex(generated.output, "注册申请号", "申请号");
        String auth = findLabeledHex(generated.output, "离线授权码", "授权码");
        if (blank(request) || blank(auth))
            throw new IOException("[JAVA_GENCODE_PARSE] helper returned success but request/auth fields were not parsed; output="
                    + compactNativeOutput(generated.output));
        String machine = firstValue(generated.output, "TARGET_MACHINE=");
        log.accept("[java-stage] GENCODE_PARSE: OK\n");

        if (dryRun) {
            log.accept("[dry-run] Java target-native codes generated under process isolation; no target file was changed.\n");
            return new Result(true, "Java target-native preview completed; no file was changed.",
                    d, machine, request, auth);
        }

        LinkedHashMap<File, byte[]> originals = snapshotJavaRegistrationFiles(d);
        if (backup) backupJavaRegistrationFiles(originals, log);
        IOException lastFailure = null;
        for (int i = 0; i < attempts.size(); i++) {
            JavaRegistrationRuntimeProfile.Attempt writeAttempt = attempts.get(i);
            if (i > 0) restoreJavaRegistrationFiles(originals, log);
            try {
                log.accept("[java-stage] DOREG: start ctor=" + writeAttempt.summary() + "\n");
                NativeProcessResult applied = runJavaHost(d, "doreg", writeAttempt, product, version,
                        regStr, request, auth, log);
                if (applied.exitCode != 0)
                    throw nativeFailure("JAVA_DOREG", "target RegisterMain.doRegistry() failed for ctor="
                            + writeAttempt.summary(), applied);
                log.accept("[java-stage] DOREG: OK ctor=" + writeAttempt.summary() + "\n");

                if (blockNet) blockJavaAuthorizationConfigs(d, log);

                // Re-run the exact startup constructor sequence in fresh JVMs. For
                // generations such as DS28, Tomcat itself tries explicit-root first and
                // target-default-path second; only those proven call-site modes are allowed.
                NativeProcessResult checked = null;
                JavaRegistrationRuntimeProfile.Attempt verifiedAttempt = null;
                for (JavaRegistrationRuntimeProfile.Attempt verifyAttempt : attempts) {
                    log.accept("[java-stage] VERIFY_FRESH: start ctor=" + verifyAttempt.summary() + "\n");
                    checked = runJavaHost(d, "verify", verifyAttempt, product, version,
                            regStr, null, null, log);
                    if (checked.exitCode == 0) {
                        verifiedAttempt = verifyAttempt;
                        break;
                    }
                    log.accept("[java-stage] VERIFY_FRESH: rejected ctor=" + verifyAttempt.summary()
                            + " output=" + compactNativeOutput(checked.output) + "\n");
                }
                if (verifiedAttempt == null)
                    throw nativeFailure("JAVA_VERIFY", "fresh target startup constructor sequence rejected write-back", checked);
                String persisted = normalizeProductCsv(firstValue(checked.output, "TARGET_REGSTR="));
                if (blank(persisted) || (!blank(version) && !containsRegStrToken(persisted, version)))
                    throw new IOException("[JAVA_MODE_VERIFY] fresh verifier did not expose current SoftVersionID; RegStr="
                            + valueOrPending(persisted));
                log.accept("[java-stage] VERIFY_FRESH: OK ctor=" + verifiedAttempt.summary()
                        + " persisted RegStr=" + persisted + "\n");
                return new Result(true,
                        "Java local authorization was applied through the target-native registration chain and passed the target startup constructor sequence in a fresh JVM. Restart Tomcat.",
                        d, machine, request, auth);
            } catch (IOException ex) {
                lastFailure = ex;
                log.accept("[java-candidate-failed] " + ex.getMessage() + "\n");
            }
        }

        restoreJavaRegistrationFiles(originals, log);
        if (lastFailure != null) throw lastFailure;
        throw new IOException("[JAVA_CHAIN] no runtime-derived registration constructor completed write-back + fresh verification");
    }

'''
s = replace_block(s,
    "    private static Result recoverJava(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {",
    "    static String buildJavaRecoveryClasspath(File toolDir, File runtimeDir) {",
    recover,
    "AutoRecovery recoverJava")

run_host = r'''    private static NativeProcessResult runJavaHost(Detection d, String mode,
                                                    JavaRegistrationRuntimeProfile.Attempt attempt,
                                                    String product, String version, String regStr,
                                                    String request, String auth, Consumer<String> log) throws Exception {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add(javaExe());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(buildJavaRecoveryClasspath(toolDir(), d.runtimeDir));
        cmd.add("LicenseRecoverJavaHost");
        cmd.add(mode);
        cmd.add(d.appRoot.getAbsolutePath());
        cmd.add(d.runtimeDir.getAbsolutePath());
        if (attempt != null && attempt.baseDir != null) {
            cmd.add("--base"); cmd.add(attempt.baseDir.getAbsolutePath());
        }
        if (attempt == null) throw new IllegalArgumentException("Java runtime constructor attempt is required");
        cmd.add("--ctor"); cmd.add(attempt.mode.cliName);
        cmd.add("--product"); cmd.add(product);
        if (!blank(version)) { cmd.add("--version"); cmd.add(version); }
        if (!blank(regStr)) { cmd.add("--regstr"); cmd.add(regStr); }
        if (!blank(request)) { cmd.add("--seq"); cmd.add(request); }
        if (!blank(auth)) { cmd.add("--code"); cmd.add(auth); }
        return runNativeCapture(cmd, d.appRoot, log);
    }

'''
s = replace_block(s,
    "    private static NativeProcessResult runJavaHost(Detection d, String mode, File base,",
    "    static List<File> javaRegistrationBases(Detection d, LicenseRecoverModernGUIJavaPlan plan) {",
    run_host,
    "AutoRecovery runJavaHost")
wr(p, s)


# ---------------------------------------------------------------------------
# Smoke tests for the five constructor patterns observed in real samples.
# ---------------------------------------------------------------------------
p = "src/test/java/RefactorSmokeTest.java"
s = rd(p)
s = one(s, "import java.util.List;\n", "import java.util.List;\nimport java.util.LinkedHashSet;\nimport java.util.Set;\n", "Smoke imports")
anchor = '''        check("fwq".equals(LicenseRecover.LOCAL_AUTH_USER_ID),
                "local authorization UserID fixed to fwq");
'''
insert = anchor + '''        Set<String> oneDefault = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D1));
        Set<String> legacyPath = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D2));
        Set<String> modernRoot = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D3));
        Set<String> modernRootFallback = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D3, JavaRegistrationRuntimeProfile.D2));
        Set<String> modernDefault = new LinkedHashSet<String>(Arrays.asList(JavaRegistrationRuntimeProfile.D2));
        check(JavaRegistrationRuntimeProfile.chooseModes(false, oneDefault, false).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.ONE_ARG_DEFAULT,
                "DS50109-style startup keeps legacy one-arg target-default constructor");
        check(JavaRegistrationRuntimeProfile.chooseModes(false, legacyPath, true).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_PATH,
                "DS2406-style startup keeps legacy two-arg explicit root path");
        check(JavaRegistrationRuntimeProfile.chooseModes(true, modernRoot, true).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.THREE_ARG_TOKEN_PATH,
                "QT30103-style startup keeps modern three-arg token/root constructor");
        List<JavaRegistrationRuntimeProfile.Mode> ds28Modes =
                JavaRegistrationRuntimeProfile.chooseModes(true, modernRootFallback, true);
        check(ds28Modes.size() == 2
                        && ds28Modes.get(0) == JavaRegistrationRuntimeProfile.Mode.THREE_ARG_TOKEN_PATH
                        && ds28Modes.get(1) == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_TOKEN_DEFAULT,
                "DS2802-style startup preserves explicit-root then default-path fallback order");
        check(JavaRegistrationRuntimeProfile.chooseModes(true, modernDefault, false).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_TOKEN_DEFAULT,
                "DS3110-style startup keeps modern two-arg target-default constructor");
'''
s = one(s, anchor, insert, "Smoke constructor ABI tests")
wr(p, s)


# ---------------------------------------------------------------------------
# CI/source invariants and overlay packaging.
# ---------------------------------------------------------------------------
p = "scripts/verify.ps1"
s = rd(p)
s = one(s,
    "$javaHostSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverJavaHost.java') -Raw\n",
    "$javaHostSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverJavaHost.java') -Raw\n$javaRuntimeProfileSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'JavaRegistrationRuntimeProfile.java') -Raw\n",
    "verify profile source")
old_checks = '''if (-not $javaHostSource.Contains('class ChildFirstLoader') -or -not $javaHostSource.Contains('checkConnect(String host, int port)')) { throw 'Java target-native isolated host/network guard is missing.' }
if (-not $autoSource.Contains('[java-stage] VERIFY_FRESH: start') -or -not $autoSource.Contains('LicenseRecoverJavaHost')) { throw 'Java one-click is not wired to fresh-JVM target-native verification.' }
if ($autoSource.Contains('runtimeDir.getAbsolutePath() + File.separator + "*";')) { throw 'Java helper system classpath still contains target jars.' }
'''
new_checks = '''if (-not $javaHostSource.Contains('class ChildFirstLoader') -or -not $javaHostSource.Contains('checkConnect(String host, int port)')) { throw 'Java target-native isolated host/network guard is missing.' }
if (-not $javaHostSource.Contains('ProtectionDomain') -or -not $javaHostSource.Contains('NetRemover.unpackVirbox')) { throw 'Packed Java registration host does not preserve original target CodeSource.' }
if (-not $javaHostSource.Contains('target RegisterMain.doRegistry() returned false')) { throw 'Java native doRegistry false-return guard is missing.' }
if (-not $javaRuntimeProfileSource.Contains('RegisterMain') -or -not $javaRuntimeProfileSource.Contains('checkReInfo') -or -not $javaRuntimeProfileSource.Contains('getRealPath')) { throw 'Java startup constructor ABI profile is incomplete.' }
if (-not $autoSource.Contains('JavaRegistrationRuntimeProfile.inspect') -or -not $autoSource.Contains('[java-stage] VERIFY_FRESH: start')) { throw 'Java one-click is not wired to runtime-derived fresh-JVM verification.' }
if ($autoSource.Contains('runtimeDir.getAbsolutePath() + File.separator + "*";')) { throw 'Java helper system classpath still contains target jars.' }
'''
s = one(s, old_checks, new_checks, "verify Java runtime profile checks")
s = one(s,
    "    'LicenseRecoverJavaHost',\n",
    "    'LicenseRecoverJavaHost',\n    'JavaRegistrationRuntimeProfile',\n",
    "verify overlay prefix")
s = one(s,
    "if ($overlayEntries -notcontains 'LicenseRecoverJavaHost.class') {\n    throw 'Overlay is missing LicenseRecoverJavaHost.class; Java target-native recovery cannot run.'\n}\n",
    "if ($overlayEntries -notcontains 'LicenseRecoverJavaHost.class') {\n    throw 'Overlay is missing LicenseRecoverJavaHost.class; Java target-native recovery cannot run.'\n}\nif ($overlayEntries -notcontains 'JavaRegistrationRuntimeProfile.class') {\n    throw 'Overlay is missing JavaRegistrationRuntimeProfile.class; Tomcat constructor ABI detection cannot run.'\n}\n",
    "verify overlay profile class")
wr(p, s)
