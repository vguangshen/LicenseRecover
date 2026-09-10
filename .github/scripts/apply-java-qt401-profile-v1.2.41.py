from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    if old not in s:
        raise SystemExit(f"marker not found in {path}: {old[:120]!r}")
    s2 = s.replace(old, new, 1)
    if s2 == s:
        raise SystemExit(f"no change in {path}")
    p.write_text(s2, encoding="utf-8")


# 1) Runtime profile: let each proven startup attempt carry its real verification API.
p = "src/main/java/JavaRegistrationRuntimeProfile.java"
replace_once(
    p,
    '''    public static final class Attempt {
        public final Mode mode;
        public final File baseDir;
        public final String sourceClass;
        Attempt(Mode mode, File baseDir, String sourceClass) {
            this.mode = mode;
            this.baseDir = baseDir;
            this.sourceClass = sourceClass;
        }
        public String summary() {
            return mode.cliName + (baseDir == null ? "(target-default-path)" : "(" + baseDir.getAbsolutePath() + ")");
        }
    }
''',
    '''    public static final class Attempt {
        public final Mode mode;
        public final File baseDir;
        public final String sourceClass;
        /** True only when the target startup path itself invokes RegisterMain.checkReInfo(). */
        public final boolean checkReInfo;
        Attempt(Mode mode, File baseDir, String sourceClass) {
            this(mode, baseDir, sourceClass, true);
        }
        Attempt(Mode mode, File baseDir, String sourceClass, boolean checkReInfo) {
            this.mode = mode;
            this.baseDir = baseDir;
            this.sourceClass = sourceClass;
            this.checkReInfo = checkReInfo;
        }
        public String summary() {
            return mode.cliName + (baseDir == null ? "(target-default-path)" : "(" + baseDir.getAbsolutePath() + ")")
                    + (checkReInfo ? "[checkReInfo]" : "[getRegInfo-only]");
        }
    }
''')

replace_once(
    p,
    '''            Candidate best = findStartupCandidate(classes);
            if (best == null) return fail(jar, "未找到同时调用 RegisterMain.<init> 与 checkReInfo() 的启动类");

            // Some Spring/Tomcat generations split the startup chain across classes:
''',
    '''            Candidate best = findStartupCandidate(classes);
            ClasspathFactoryRelay classpathRelay = best == null ? findClasspathFactoryRelay(classes) : null;
            if (best == null && classpathRelay == null)
                return fail(jar, "未找到可证明的 RegisterMain 启动链（checkReInfo/getRegInfo）");

            // QT401/RuoYi-style generations obtain the exploded WEB-INF/classes path via
            // ClassUtils.getDefaultClassLoader().getResource("").getPath(), relay that
            // path into a target-owned getRegisterMain(product,path) factory, and then
            // consume getRegInfo() directly. Mirror that exact path and verification API.
            if (classpathRelay != null) {
                ArrayList<Attempt> classpathAttempts = new ArrayList<Attempt>();
                if (modern && classpathRelay.ctorDescriptors.contains(D3)) {
                    classpathAttempts.add(new Attempt(Mode.THREE_ARG_TOKEN_PATH, classes,
                            classpathRelay.factoryClass, false));
                } else {
                    return fail(jar, "classpath-root RegisterMain factory relay 与目标构造器代际不匹配: "
                            + classpathRelay.ctorDescriptors);
                }
                String ev = classpathRelay.callerClass
                        + " [ClassUtils.getDefaultClassLoader().getResource().getPath -> WEB-INF/classes] -> "
                        + classpathRelay.factoryClass + ".getRegisterMain -> "
                        + classpathAttempts.get(0).summary() + "; RegisterMain constructors=" + declared;
                return new JavaRegistrationRuntimeProfile(true, jar, modern, classpathAttempts, ev, null);
            }

            // Some Spring/Tomcat generations split the startup chain across classes:
''')

marker = '''    /**
     * Prove an explicit Servlet root that is relayed into the wrapper which owns
'''
insert = '''    /**
     * Prove a QT401/RuoYi-style classpath-root factory relay. The caller must obtain
     * the default ClassLoader resource path, pass it to target-owned
     * getRegisterMain(String,String), and then consume RegisterMain.getRegInfo().
     * No selected-folder or registration-JAR path inference is accepted here.
     */
    private static ClasspathFactoryRelay findClasspathFactoryRelay(File classes) throws IOException {
        if (classes == null || !classes.isDirectory()) return null;
        ArrayList<File> files = new ArrayList<File>();
        collectClassFiles(classes, files, 0, 14);
        ClasspathFactoryRelay best = null;
        for (File factoryFile : files) {
            ClassRefs factoryRefs;
            try { factoryRefs = ClassRefs.parse(Files.readAllBytes(factoryFile.toPath())); }
            catch (Throwable ignore) { continue; }
            Set<String> ctors = factoryRefs.methodRefs("itmc/regedit/RegisterMain", "<init>");
            if (!ctors.contains(D3)) continue;
            String factoryRel = relative(classes, factoryFile);
            String owner = factoryRel.replace('\\\\', '/');
            if (owner.endsWith(".class")) owner = owner.substring(0, owner.length() - 6);
            for (File callerFile : files) {
                ClassRefs callerRefs;
                try { callerRefs = ClassRefs.parse(Files.readAllBytes(callerFile.toPath())); }
                catch (Throwable ignore) { continue; }
                if (!callerRefs.hasMethodRef("org/springframework/util/ClassUtils", "getDefaultClassLoader",
                        "()Ljava/lang/ClassLoader;")) continue;
                if (!callerRefs.hasMethodRef("java/lang/ClassLoader", "getResource",
                        "(Ljava/lang/String;)Ljava/net/URL;")) continue;
                if (!callerRefs.hasMethodRef("java/net/URL", "getPath", "()Ljava/lang/String;")) continue;
                if (!callerRefs.hasMethodRef(owner, "getRegisterMain",
                        "(Ljava/lang/String;Ljava/lang/String;)Litmc/regedit/RegisterMain;")) continue;
                if (!callerRefs.hasMethodRef("itmc/regedit/RegisterMain", "getRegInfo",
                        "()Litmc/regedit/webservice/RegeditInfo;")) continue;
                String callerRel = relative(classes, callerFile);
                int score = startupScore(callerFile.getName(), callerRel);
                ClasspathFactoryRelay hit = new ClasspathFactoryRelay(factoryRel, callerRel, score, ctors);
                if (best == null || hit.score > best.score
                        || (hit.score == best.score && hit.callerClass.compareToIgnoreCase(best.callerClass) < 0))
                    best = hit;
            }
        }
        return best;
    }

'''
ps = Path(p)
s = ps.read_text(encoding="utf-8")
if marker not in s:
    raise SystemExit("runtime-profile relay insertion marker missing")
ps.write_text(s.replace(marker, insert + marker, 1), encoding="utf-8")

replace_once(
    p,
    '''    static final class Candidate {
        final String relativePath;
''',
    '''    static final class ClasspathFactoryRelay {
        final String factoryClass;
        final String callerClass;
        final int score;
        final Set<String> ctorDescriptors;
        ClasspathFactoryRelay(String factoryClass, String callerClass, int score, Set<String> ctorDescriptors) {
            this.factoryClass = factoryClass;
            this.callerClass = callerClass;
            this.score = score;
            this.ctorDescriptors = ctorDescriptors;
        }
    }

    static final class Candidate {
        final String relativePath;
''')

# 2) Host: mirror RegInfo-only startup profiles instead of forcing checkReInfo().
p = "src/main/java/LicenseRecoverJavaHost.java"
replace_once(
    p,
    '''        String code;
        String ctorMode;
''',
    '''        String code;
        String ctorMode;
        boolean checkReInfo = true;
''')
replace_once(
    p,
    '''            else if ("--ctor".equals(a) && i + 1 < args.length) o.ctorMode = args[++i];
            else throw new IllegalArgumentException("unknown/missing option: " + a);
''',
    '''            else if ("--ctor".equals(a) && i + 1 < args.length) o.ctorMode = args[++i];
            else if ("--reginfo-only".equals(a)) o.checkReInfo = false;
            else throw new IllegalArgumentException("unknown/missing option: " + a);
''')
replace_once(
    p,
    '''        Method check = findNoArgMethod(reg.getClass(), "checkReInfo");
        if (check == null) throw new NoSuchMethodException("target RegisterMain has no checkReInfo()");
        check.setAccessible(true);
        Object raw;
        try { raw = check.invoke(reg); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
        if (!(raw instanceof Boolean)) throw new IllegalStateException("checkReInfo() did not return boolean: " + raw);
        boolean unregistered = ((Boolean) raw).booleanValue();
        Object info = invokeOptionalNoArg(reg, "getRegInfo");
''',
    '''        Boolean unregistered = null;
        if (o.checkReInfo) {
            Method check = findNoArgMethod(reg.getClass(), "checkReInfo");
            if (check == null) throw new NoSuchMethodException("target RegisterMain has no checkReInfo()");
            check.setAccessible(true);
            Object raw;
            try { raw = check.invoke(reg); }
            catch (InvocationTargetException ex) { throw rethrow(ex); }
            if (!(raw instanceof Boolean)) throw new IllegalStateException("checkReInfo() did not return boolean: " + raw);
            unregistered = (Boolean) raw;
        }
        Object info = invokeOptionalNoArg(reg, "getRegInfo");
''')
replace_once(
    p,
    '''        System.out.println("TARGET_CHECK_REINFO=" + unregistered);
        if (unregistered) throw new IllegalStateException("target checkReInfo() reports unregistered");
''',
    '''        System.out.println("TARGET_CHECK_REINFO=" + (unregistered == null ? "SKIPPED_BY_STARTUP_PROFILE" : unregistered));
        if (Boolean.TRUE.equals(unregistered)) throw new IllegalStateException("target checkReInfo() reports unregistered");
''')

# 3) Coordinator propagates the target startup verification contract.
p = "src/main/java/LicenseRecoverModernGUIAutoRecovery.java"
replace_once(
    p,
    '''        cmd.add("--ctor"); cmd.add(attempt.mode.cliName);
        cmd.add("--product"); cmd.add(product);
''',
    '''        cmd.add("--ctor"); cmd.add(attempt.mode.cliName);
        if (!attempt.checkReInfo) cmd.add("--reginfo-only");
        cmd.add("--product"); cmd.add(product);
''')

# 4) Java plan: prove QT401/QT401xx from target-owned classes/config and startup bytes.
p = "src/main/java/LicenseRecoverModernGUIJavaPlan.java"
replace_once(
    p,
    '''        String configDrivenPrimaryRegStr = confirmedConfigDrivenPrimaryRegStr(
                root, soft, family, configDrivenRuntime);
        String dataDrivenPrimaryRegStr = confirmedDataDrivenPrimaryRegStr(
''',
    '''        String qt401PrimaryRegStr = confirmedQt401PrimaryRegStr(root, soft, family, runtimeProduct);
        String configDrivenPrimaryRegStr = confirmedConfigDrivenPrimaryRegStr(
                root, soft, family, configDrivenRuntime);
        String dataDrivenPrimaryRegStr = confirmedDataDrivenPrimaryRegStr(
''')
replace_once(
    p,
    '''        } else if (!blank(configDrivenPrimaryRegStr)) {
            products = configDrivenPrimaryRegStr;
''',
    '''        } else if (!blank(qt401PrimaryRegStr)) {
            products = qt401PrimaryRegStr;
        } else if (!blank(configDrivenPrimaryRegStr)) {
            products = configDrivenPrimaryRegStr;
''')
replace_once(
    p,
    '''                || dataRegInfo != null || classesRegInfo != null
                || !blank(configDrivenPrimaryRegStr) || !blank(dataDrivenPrimaryRegStr)
''',
    '''                || dataRegInfo != null || classesRegInfo != null
                || !blank(qt401PrimaryRegStr) || !blank(configDrivenPrimaryRegStr) || !blank(dataDrivenPrimaryRegStr)
''')

marker = '''    /**
     * Prove a config-driven concrete runtime ProductID without guessing it from the
'''
helper = '''    /**
     * QT401xx/RuoYi generation: config1.xml proves registration product QT401,
     * classes/config.xml proves the concrete system id, and target startup bytecode
     * proves classpath-root -> getRegisterMain(product,path) -> getRegInfo/RegStr.
     */
    static String confirmedQt401PrimaryRegStr(File root, String softId,
                                               String confirmedFamily, String runtimeProduct) {
        if (root == null || blank(softId) || blank(confirmedFamily) || blank(runtimeProduct)) return null;
        String concrete = softId.trim();
        if (!concrete.toUpperCase(Locale.ROOT).matches("QT401\\d{2}")) return null;
        if (!"QT401".equalsIgnoreCase(confirmedFamily.trim())
                || !"QT401".equalsIgnoreCase(runtimeProduct.trim())) return null;
        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File config = new File(classes, "config.xml");
        String configured = readElement(config, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;
        File config1 = new File(classes, "config1.xml");
        String family = readElement(config1, "SoftVersionID");
        if (blank(family) || !"QT401".equalsIgnoreCase(family.trim())) return null;
        try {
            String text = new String(Files.readAllBytes(config1.toPath()), StandardCharsets.UTF_8);
            if (!text.contains("id=\"" + concrete + "\"") && !text.contains("id='" + concrete + "'")) return null;
        } catch (Exception ex) { return null; }
        File systemInfo = new File(classes, "com" + File.separator + "ruoyi" + File.separator + "web"
                + File.separator + "register" + File.separator + "utils" + File.separator + "SystemInfo.class");
        File initService = new File(classes, "com" + File.separator + "ruoyi" + File.separator + "web"
                + File.separator + "controller" + File.separator + "listener" + File.separator + "service"
                + File.separator + "SystemInitService.class");
        File listener = new File(classes, "com" + File.separator + "ruoyi" + File.separator + "web"
                + File.separator + "register" + File.separator + "service" + File.separator + "RegisterListener.class");
        if (!classFileContainsAll(systemInfo, "config1.xml", "SystemSoft", "SoftVersionID", "registerId")) return null;
        if (!classFileContainsAll(initService, "getRegisterMain", "RegeditNew", "itmcsoft",
                "itmc/regedit/RegisterMain")) return null;
        if (!classFileContainsAll(listener, "org/springframework/util/ClassUtils", "getDefaultClassLoader",
                "java/lang/ClassLoader", "getResource", "java/net/URL", "getPath",
                "getRegisterMain", "getRegInfo", "getRegStr", "ClassPid", "contains")) return null;
        return concrete;
    }

'''
ps = Path(p)
s = ps.read_text(encoding="utf-8")
if marker not in s:
    raise SystemExit("java-plan helper insertion marker missing")
ps.write_text(s.replace(marker, helper + marker, 1), encoding="utf-8")

# 5) CI source-level guards.
p = "scripts/verify.ps1"
ps = Path(p)
s = ps.read_text(encoding="utf-8")
anchor = "if (-not $javaRuntimeProfileSource.Contains('RegisterMain') -or -not $javaRuntimeProfileSource.Contains('checkReInfo') -or -not $javaRuntimeProfileSource.Contains('getRealPath')) { throw 'Java startup constructor ABI profile is incomplete.' }"
extra = anchor + "\nif (-not $javaRuntimeProfileSource.Contains('findClasspathFactoryRelay') -or -not $javaRuntimeProfileSource.Contains('getRegInfo-only')) { throw 'QT401 classpath-root/getRegInfo-only startup profile is missing.' }\nif (-not $javaHostSource.Contains('--reginfo-only')) { throw 'Java target host cannot mirror RegInfo-only startup validation.' }\nif (-not $autoSource.Contains('attempt.checkReInfo')) { throw 'Java coordinator does not pass startup verification semantics.' }\nif (-not $javaPlanSource.Contains('confirmedQt401PrimaryRegStr')) { throw 'QT401 target-owned RegStr proof is missing.' }"
if anchor not in s:
    raise SystemExit("verify guard anchor missing")
ps.write_text(s.replace(anchor, extra, 1), encoding="utf-8")

# 6) Matrix documentation.
p = "docs/java-runtime-profile-v1.2.41.md"
ps = Path(p)
s = ps.read_text(encoding="utf-8")
row = '| YT00129 | same wrapper/root relay and three-arg token/path ABI | explicit webapp-root path; product alias `QT04`, RegStr alias `QT0420` | yes |'
add = row + '\n| QT40101 | `RegisterListener -> SystemInitService.getRegisterMain(product,path) -> (String product, String token, String configPath)` | classpath root (`WEB-INF/classes`); startup consumes `getRegInfo()` without `checkReInfo()`; product `QT401`, RegStr `QT40101` | no (`ITMCReg-1.0.5.jar`) |'
if row not in s:
    raise SystemExit("docs row anchor missing")
s = s.replace(row, add, 1)
s += '''

## QT40101 classpath-root generation

The supplied QT40101 sample stores registration XML under `WEB-INF/classes/config.xml`. `config1.xml` proves registration product `QT401` while the concrete system is `QT40101`. Startup obtains the exploded classpath root with `ClassUtils.getDefaultClassLoader().getResource("").getPath()`, passes that path through `SystemInitService.getRegisterMain(product,path)`, and then consumes `RegisterMain.getRegInfo().getRegStr()`; this startup path does not call `RegisterMain.checkReInfo()`. The target-native verifier therefore mirrors RegInfo-only validation for this proven profile rather than forcing an API the application does not use at startup.
'''
ps.write_text(s, encoding="utf-8")
