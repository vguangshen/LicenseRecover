from pathlib import Path


def write_if_changed(path, text):
    p = Path(path)
    old = p.read_text(encoding="utf-8")
    if old == text:
        raise SystemExit("no change produced for " + str(path))
    p.write_text(text, encoding="utf-8")


# 1) Runtime profile: recognize Servlet root passed through a RegisterUtil wrapper.
p = Path("src/main/java/JavaRegistrationRuntimeProfile.java")
s = p.read_text(encoding="utf-8")
start = s.index("            Candidate best = findStartupCandidate(classes);")
end = s.index("            if (attempts.isEmpty())", start)
replacement = '''            Candidate best = findStartupCandidate(classes);
            if (best == null) return fail(jar, "未找到同时调用 RegisterMain.<init> 与 checkReInfo() 的启动类");

            // Some Spring/Tomcat generations split the startup chain across classes:
            // SysParamInit obtains ServletContext.getRealPath("/") and passes that root to
            // RegisterUtil.checkRegister(root), while RegisterUtil owns the actual
            // RegisterMain constructor/checkReInfo call. Treat that data-flow as explicit
            // root evidence instead of requiring getRealPath and RegisterMain in one class.
            String rootRelay = null;
            if (!best.servletRootEvidence && (best.ctorDescriptors.contains(D3)
                    || (!modern && best.ctorDescriptors.contains(D2)))) {
                rootRelay = findServletRootRelay(classes, best);
            }
            boolean rootEvidence = best.servletRootEvidence || rootRelay != null;

            ArrayList<Attempt> attempts = new ArrayList<Attempt>();
            if (modern) {
                if (best.ctorDescriptors.contains(D3)) {
                    if (!rootEvidence)
                        return fail(jar, "启动类使用 3 参 RegisterMain，但未证明 ServletContext.getRealPath(\"/\") 根路径: " + best.relativePath);
                    attempts.add(new Attempt(Mode.THREE_ARG_TOKEN_PATH, root, best.relativePath));
                }
                // A proven wrapper relay has a non-empty root argument, so its internal
                // two-arg branch is not a Tomcat fallback. Keep D2 only for call sites
                // that themselves prove both branches (for example DS28).
                if (best.ctorDescriptors.contains(D2) && rootRelay == null)
                    attempts.add(new Attempt(Mode.TWO_ARG_TOKEN_DEFAULT, null, best.relativePath));
                if (best.ctorDescriptors.contains(D1) && has1)
                    attempts.add(new Attempt(Mode.ONE_ARG_DEFAULT, null, best.relativePath));
            } else {
                if (best.ctorDescriptors.contains(D2)) {
                    if (!rootEvidence)
                        return fail(jar, "旧版启动类使用 2 参路径构造器，但未证明 ServletContext.getRealPath(\"/\") 根路径: " + best.relativePath);
                    attempts.add(new Attempt(Mode.TWO_ARG_PATH, root, best.relativePath));
                }
                if (best.ctorDescriptors.contains(D1))
                    attempts.add(new Attempt(Mode.ONE_ARG_DEFAULT, null, best.relativePath));
            }
'''
s = s[:start] + replacement + s[end:]
old = '            StringBuilder ev = new StringBuilder();\n            ev.append(best.relativePath).append(" -> ");'
new = '            StringBuilder ev = new StringBuilder();\n            if (rootRelay != null) ev.append(rootRelay).append(" [ServletContext.getRealPath(/)] -> ");\n            ev.append(best.relativePath).append(" -> ");'
if old not in s:
    raise SystemExit("runtime profile evidence marker not found")
s = s.replace(old, new, 1)

marker = "    private static int startupScore(String simple, String rel) {"
if marker not in s:
    raise SystemExit("startupScore marker missing")
relay_helper = r'''    /**
     * Prove an explicit Servlet root that is relayed into the wrapper which owns
     * RegisterMain. This is intentionally narrow/fail-closed: the caller must both
     * obtain ServletContext.getRealPath(String) and invoke wrapper.checkRegister(String).
     */
    private static String findServletRootRelay(File classes, Candidate wrapper) throws IOException {
        if (classes == null || wrapper == null) return null;
        String wrapperOwner = wrapper.relativePath.replace('\\', '/');
        if (wrapperOwner.endsWith(".class")) wrapperOwner = wrapperOwner.substring(0, wrapperOwner.length() - 6);
        ArrayList<File> files = new ArrayList<File>();
        collectClassFiles(classes, files, 0, 14);
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (File f : files) {
            ClassRefs refs;
            try { refs = ClassRefs.parse(Files.readAllBytes(f.toPath())); }
            catch (Throwable ignore) { continue; }
            boolean getsRoot = refs.hasMethodRef("javax/servlet/ServletContext", "getRealPath",
                    "(Ljava/lang/String;)Ljava/lang/String;")
                    || refs.hasMethodRef("jakarta/servlet/ServletContext", "getRealPath",
                    "(Ljava/lang/String;)Ljava/lang/String;");
            if (!getsRoot) continue;
            boolean callsWrapper = refs.hasMethodRef(wrapperOwner, "checkRegister", "(Ljava/lang/String;)V")
                    || refs.hasMethodRef(wrapperOwner, "checkRegister", "(Ljava/lang/String;)Z");
            if (!callsWrapper) continue;
            String rel = relative(classes, f);
            int score = startupScore(f.getName(), rel);
            if (best == null || score > bestScore || (score == bestScore && rel.compareToIgnoreCase(best) < 0)) {
                best = rel;
                bestScore = score;
            }
        }
        return best;
    }

'''
s = s.replace(marker, relay_helper + marker, 1)

choose_start = s.index("    static List<Mode> chooseModes(boolean modern, Set<String> startupDescriptors, boolean servletRootEvidence) {")
choose_end = s.index("    private static JavaRegistrationRuntimeProfile fail(", choose_start)
choose = '''    static List<Mode> chooseModes(boolean modern, Set<String> startupDescriptors, boolean servletRootEvidence) {
        return chooseModes(modern, startupDescriptors, servletRootEvidence, false);
    }

    static List<Mode> chooseModes(boolean modern, Set<String> startupDescriptors,
                                  boolean servletRootEvidence, boolean explicitRootRelay) {
        ArrayList<Mode> out = new ArrayList<Mode>();
        if (startupDescriptors == null) return out;
        if (modern) {
            if (startupDescriptors.contains(D3) && servletRootEvidence) out.add(Mode.THREE_ARG_TOKEN_PATH);
            if (startupDescriptors.contains(D2) && !explicitRootRelay) out.add(Mode.TWO_ARG_TOKEN_DEFAULT);
            if (startupDescriptors.contains(D1)) out.add(Mode.ONE_ARG_DEFAULT);
        } else {
            if (startupDescriptors.contains(D2) && servletRootEvidence) out.add(Mode.TWO_ARG_PATH);
            if (startupDescriptors.contains(D1)) out.add(Mode.ONE_ARG_DEFAULT);
        }
        return out;
    }

'''
s = s[:choose_start] + choose + s[choose_end:]
write_if_changed(p, s)

# 2) Host verification: Java startup may use an alias token (YT00129 -> QT0420).
p = Path("src/main/java/LicenseRecoverJavaHost.java")
s = p.read_text(encoding="utf-8")
old = '''        if (!blank(o.version) && !containsCsv(regStr, o.version))
            throw new IllegalStateException("persisted RegStr does not contain current SoftVersionID=" + o.version + "; RegStr=" + regStr);'''
new = '''        if (!blank(o.regStr) && !containsAllCsv(regStr, o.regStr))
            throw new IllegalStateException("persisted RegStr does not preserve target startup mode token(s)="
                    + o.regStr + "; RegStr=" + regStr);'''
if old not in s:
    raise SystemExit("host version verification marker missing")
s = s.replace(old, new, 1)
marker = '''    static boolean containsCsv(String csv, String wanted) {
        if (blank(csv) || blank(wanted)) return false;
        for (String x : csv.split(",")) if (wanted.trim().equalsIgnoreCase(x.trim())) return true;
        return false;
    }
'''
if marker not in s:
    raise SystemExit("containsCsv marker missing")
helper = marker + '''
    static boolean containsAllCsv(String actual, String expected) {
        if (blank(expected)) return true;
        if (blank(actual)) return false;
        String normalized = normalizeCsv(expected);
        if (blank(normalized)) return true;
        for (String token : normalized.split(","))
            if (!containsCsv(actual, token)) return false;
        return true;
    }
'''
s = s.replace(marker, helper, 1)
write_if_changed(p, s)

# 3) Coordinator: distinguish current SoftVersionID from target startup RegStr aliases.
p = Path("src/main/java/LicenseRecoverModernGUIAutoRecovery.java")
s = p.read_text(encoding="utf-8")
old = "        String regStr = normalizeProductCsv(plan.regStr);\n        if (blank(regStr)) {"
new = "        String regStr = normalizeProductCsv(plan.regStr);\n        boolean regStrFromPlan = !blank(regStr);\n        if (blank(regStr)) {"
if old not in s:
    raise SystemExit("auto regStr marker missing")
s = s.replace(old, new, 1)
old = '''        if (blank(regStr))
            return Result.fail("[JAVA_MODE] 目标目录及目标 RegisterMain 均未证明可用 RegStr。", d);
        if (!blank(version) && !containsRegStrToken(regStr, version))
            return Result.fail("[JAVA_MODE] 当前 SoftVersionID 未进入 RegStr: " + version + "; RegStr=" + regStr, d);
        log.accept("[java-mode] current SoftVersionID=" + valueOrPending(version) + " RegStr=" + regStr + "\\n");'''
new = '''        if (blank(regStr))
            return Result.fail("[JAVA_MODE] 目标目录及目标 RegisterMain 均未证明可用 RegStr。", d);
        // If the target plan proved a startup alias (for example YT00129 -> QT0420),
        // do not replace it with or require the current SoftVersionID. Dynamic probes
        // remain conservative and must contain the current version token.
        if (!regStrFromPlan && !blank(version) && !containsRegStrToken(regStr, version))
            return Result.fail("[JAVA_MODE] 动态 RegStr 未包含当前 SoftVersionID: " + version + "; RegStr=" + regStr, d);
        if (regStrFromPlan && !blank(version) && !containsRegStrToken(regStr, version))
            log.accept("[java-mode] current SoftVersionID=" + version + " startup-mapped RegStr=" + regStr + "\\n");
        else
            log.accept("[java-mode] current SoftVersionID=" + valueOrPending(version) + " RegStr=" + regStr + "\\n");'''
if old not in s:
    raise SystemExit("auto global version guard marker missing")
s = s.replace(old, new, 1)
old = '''                String persisted = normalizeProductCsv(firstValue(checked.output, "TARGET_REGSTR="));
                if (blank(persisted) || (!blank(version) && !containsRegStrToken(persisted, version)))
                    throw new IOException("[JAVA_MODE_VERIFY] fresh verifier did not expose current SoftVersionID; RegStr="
                            + valueOrPending(persisted));'''
new = '''                String persisted = normalizeProductCsv(firstValue(checked.output, "TARGET_REGSTR="));
                if (blank(persisted) || !containsAllRegStrTokens(persisted, regStr))
                    throw new IOException("[JAVA_MODE_VERIFY] fresh verifier did not preserve target startup RegStr="
                            + regStr + "; persisted=" + valueOrPending(persisted));'''
if old not in s:
    raise SystemExit("auto fresh verification marker missing")
s = s.replace(old, new, 1)
marker = "    static boolean containsRegStrToken(String raw, String wanted) {"
if marker not in s:
    raise SystemExit("containsRegStrToken marker missing")
helper = '''    static boolean containsAllRegStrTokens(String actual, String expected) {
        if (blank(expected)) return true;
        if (blank(actual)) return false;
        String normalized = normalizeProductCsv(expected);
        if (blank(normalized)) return true;
        for (String token : normalized.split(","))
            if (!containsRegStrToken(actual, token)) return false;
        return true;
    }

'''
s = s.replace(marker, helper + marker, 1)
write_if_changed(p, s)

# 4) Smoke tests for wrapper-root semantics and alias-mode persistence.
p = Path("src/test/java/RefactorSmokeTest.java")
s = p.read_text(encoding="utf-8")
marker = '''        check(JavaRegistrationRuntimeProfile.chooseModes(true, modernDefault, false).get(0)
                        == JavaRegistrationRuntimeProfile.Mode.TWO_ARG_TOKEN_DEFAULT,
                "DS3110-style startup keeps modern two-arg target-default constructor");
'''
if marker not in s:
    raise SystemExit("smoke insertion marker missing")
addition = marker + '''        List<JavaRegistrationRuntimeProfile.Mode> ytWrapperModes =
                JavaRegistrationRuntimeProfile.chooseModes(true, modernRootFallback, true, true);
        check(ytWrapperModes.size() == 1
                        && ytWrapperModes.get(0) == JavaRegistrationRuntimeProfile.Mode.THREE_ARG_TOKEN_PATH,
                "YT001xx wrapper consumes proven servlet root without inventing two-token fallback");
        check(LicenseRecoverJavaHost.containsAllCsv("QT0420,QT0437", "QT0420"),
                "Java persisted-mode verification accepts target-proven alias token");
        check(!LicenseRecoverJavaHost.containsAllCsv("QT0420", "YT00129"),
                "YT00129 SoftVersionID is distinct from its QT0420 startup registration alias");
'''
s = s.replace(marker, addition, 1)
write_if_changed(p, s)

# 5) CI source guards.
p = Path("scripts/verify.ps1")
s = p.read_text(encoding="utf-8")
marker = "if (-not $javaRuntimeProfileSource.Contains('RegisterMain') -or -not $javaRuntimeProfileSource.Contains('checkReInfo') -or -not $javaRuntimeProfileSource.Contains('getRealPath')) { throw 'Java startup constructor ABI profile is incomplete.' }\n"
if marker not in s:
    raise SystemExit("verify guard marker missing")
addition = marker + "if (-not $javaRuntimeProfileSource.Contains('findServletRootRelay')) { throw 'Java runtime profile cannot prove Servlet root relayed through a registration wrapper.' }\n" + "if (-not $javaHostSource.Contains('containsAllCsv(regStr, o.regStr)')) { throw 'Java fresh verifier still assumes SoftVersionID instead of target startup RegStr.' }\n" + "if (-not $autoSource.Contains('containsAllRegStrTokens(persisted, regStr)')) { throw 'Java coordinator does not verify persisted target startup RegStr tokens.' }\n"
s = s.replace(marker, addition, 1)
write_if_changed(p, s)

# 6) Record the two real YT lineages.
p = Path("docs/java-runtime-profile-v1.2.41.md")
s = p.read_text(encoding="utf-8")
row = '| DS3110 | `(String product, String token)` | target/default path derived by registration component | yes |\n'
if row not in s:
    raise SystemExit("sample matrix row marker missing")
s = s.replace(row, row + '| YT00138 | `SysParamInit.getRealPath("/") -> RegisterUtil.checkRegister(root) -> (String product, String token, String configPath)` | explicit webapp-root path; product `YT001`, RegStr `YT00138` | yes |\n' + '| YT00129 | same wrapper/root relay and three-arg token/path ABI | explicit webapp-root path; product alias `QT04`, RegStr alias `QT0420` | yes |\n', 1)
s += '''
## YT001xx wrapper relay evidence

The supplied YT00138 and YT00129 WEB-INF samples use the same packed `ITMCReg.jar` generation as DS2802/DS3110. In this lineage the class that obtains the Servlet root (`SysParamInit`) is not the class that constructs `RegisterMain` (`RegisterUtil`). Runtime profiling therefore proves the cross-class relay `ServletContext.getRealPath("/") -> RegisterUtil.checkRegister(String)` before selecting the three-argument token/path constructor. A proven non-empty root relay must not invent the wrapper's internal two-argument/default-path branch as a Tomcat fallback.

YT00138 falls through the application's own registration mapping to product `YT001` with required startup membership token `YT00138`. YT00129 is different: its application mapping selects product `QT04` and required registration token `QT0420`. Consequently Java persistence verification must preserve the target-proven RegStr token(s), not globally require the current SoftVersionID to appear inside RegStr.

The supplied YT00129 archive is WEB-INF-only and does not itself provide the site-root `systemConfig.yml`/config identity. Production detection remains fail-closed in that incomplete layout; tests may only add `YT00129` root identity as an explicit fixture when modelling the missing deployment root.
'''
write_if_changed(p, s)

print("YT001xx patch applied")
