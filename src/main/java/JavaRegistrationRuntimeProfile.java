import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Read-only description of the RegisterMain constructor ABI actually used by the
 * selected Java application's startup registration check.
 *
 * This deliberately reads class-file constant pools instead of loading application
 * classes. Virbox BCE encrypts Code bytes, but the owner/name/descriptor entries for
 * RegisterMain constructor and checkReInfo references remain available. That lets us
 * mirror Tomcat's real constructor generation without executing protected startup code.
 */
public final class JavaRegistrationRuntimeProfile {
    static final String D1 = "(Ljava/lang/String;)V";
    static final String D2 = "(Ljava/lang/String;Ljava/lang/String;)V";
    static final String D3 = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";

    public enum Mode {
        ONE_ARG_DEFAULT("one-default", false),
        TWO_ARG_PATH("two-path", true),
        TWO_ARG_TOKEN_DEFAULT("two-token", false),
        THREE_ARG_TOKEN_PATH("three-token-path", true);

        public final String cliName;
        public final boolean explicitPath;
        Mode(String cliName, boolean explicitPath) {
            this.cliName = cliName;
            this.explicitPath = explicitPath;
        }
        public static Mode fromCli(String raw) {
            if (raw != null) for (Mode m : values()) if (m.cliName.equalsIgnoreCase(raw.trim())) return m;
            throw new IllegalArgumentException("unknown Java RegisterMain constructor mode: " + raw);
        }
    }

    public static final class Attempt {
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

    public final boolean supported;
    public final File registrationJar;
    public final boolean modernTokenAbi;
    public final List<Attempt> attempts;
    public final String evidence;
    public final String failureReason;

    private JavaRegistrationRuntimeProfile(boolean supported, File registrationJar,
                                           boolean modernTokenAbi, List<Attempt> attempts,
                                           String evidence, String failureReason) {
        this.supported = supported;
        this.registrationJar = registrationJar;
        this.modernTokenAbi = modernTokenAbi;
        this.attempts = attempts == null ? Collections.<Attempt>emptyList()
                : Collections.unmodifiableList(new ArrayList<Attempt>(attempts));
        this.evidence = evidence == null ? "" : evidence;
        this.failureReason = failureReason == null ? "" : failureReason;
    }

    public static JavaRegistrationRuntimeProfile inspect(File appRoot, File runtimeDir) {
        File root = appRoot == null ? null : appRoot.getAbsoluteFile();
        File jar = findRegistrationJar(runtimeDir);
        if (root == null || !root.isDirectory()) return fail(jar, "应用根目录不存在");
        if (jar == null) return fail(null, "目标 WEB-INF/lib 中未找到 ITMCReg*.jar");
        try {
            byte[] rm = readJarEntry(jar, "itmc/regedit/RegisterMain.class");
            if (rm == null) return fail(jar, "ITMCReg*.jar 中没有 RegisterMain.class");
            ClassRefs rmRefs = ClassRefs.parse(rm);
            Set<String> declared = rmRefs.declaredMethods("<init>");
            boolean has1 = declared.contains(D1);
            boolean has2 = declared.contains(D2);
            boolean has3 = declared.contains(D3);
            boolean modern = has3 && has2;
            boolean legacy = !has3 && has2 && has1;
            if (!modern && !legacy) {
                return fail(jar, "无法确认 RegisterMain 构造器代际: " + declared);
            }

            File classes = findClassesDir(root);
            if (classes == null) return fail(jar, "未找到 WEB-INF/classes，无法证明 Tomcat 启动构造器");
            Candidate best = findStartupCandidate(classes);
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
            if (attempts.isEmpty())
                return fail(jar, "启动类构造器与目标 RegisterMain 代际不匹配: startup="
                        + best.ctorDescriptors + " target=" + declared);

            StringBuilder ev = new StringBuilder();
            if (rootRelay != null) ev.append(rootRelay).append(" [ServletContext.getRealPath(/)] -> ");
            ev.append(best.relativePath).append(" -> ");
            for (int i = 0; i < attempts.size(); i++) {
                if (i > 0) ev.append(" -> fallback ");
                ev.append(attempts.get(i).summary());
            }
            ev.append("; RegisterMain constructors=").append(declared);
            return new JavaRegistrationRuntimeProfile(true, jar, modern, attempts, ev.toString(), null);
        } catch (Throwable ex) {
            return fail(jar, ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
        }
    }

    public String logSummary() {
        if (!supported) return "[java-runtime-profile] BLOCKED: " + failureReason + "\n";
        return "[java-runtime-profile] jar=" + registrationJar.getName()
                + " abi=" + (modernTokenAbi ? "modern-token" : "legacy-path")
                + " startup=" + evidence + "\n";
    }

    static List<Mode> chooseModes(boolean modern, Set<String> startupDescriptors, boolean servletRootEvidence) {
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

    private static JavaRegistrationRuntimeProfile fail(File jar, String reason) {
        return new JavaRegistrationRuntimeProfile(false, jar, false, null, null, reason);
    }

    static File findRegistrationJar(File runtimeDir) {
        if (runtimeDir == null || !runtimeDir.isDirectory()) return null;
        File exact = null;
        ArrayList<File> candidates = new ArrayList<File>();
        File[] files = runtimeDir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            if ("ITMCReg.jar".equalsIgnoreCase(n)) exact = f;
            else if (n.toLowerCase(Locale.ROOT).startsWith("itmcreg")
                    && n.toLowerCase(Locale.ROOT).endsWith(".jar")) candidates.add(f);
        }
        if (exact != null) return exact;
        Collections.sort(candidates, new Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    static File findClassesDir(File root) {
        File web = childDirIgnoreCase(root, "WEB-INF");
        if (web != null) {
            File classes = childDirIgnoreCase(web, "classes");
            if (classes != null) return classes;
            File nested = childDirIgnoreCase(web, "WEB-INF");
            if (nested != null) {
                classes = childDirIgnoreCase(nested, "classes");
                if (classes != null) return classes;
            }
        }
        return findClassesRecursive(root, 0, 4);
    }

    private static File childDirIgnoreCase(File parent, String name) {
        if (parent == null || !parent.isDirectory()) return null;
        File[] files = parent.listFiles();
        if (files == null) return null;
        for (File f : files) if (f.isDirectory() && name.equalsIgnoreCase(f.getName())) return f;
        return null;
    }

    private static File findClassesRecursive(File dir, int depth, int maxDepth) {
        if (dir == null || !dir.isDirectory() || depth > maxDepth) return null;
        if ("classes".equalsIgnoreCase(dir.getName()) && dir.getParentFile() != null
                && "WEB-INF".equalsIgnoreCase(dir.getParentFile().getName())) return dir;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) if (f.isDirectory()) {
            File hit = findClassesRecursive(f, depth + 1, maxDepth);
            if (hit != null) return hit;
        }
        return null;
    }

    private static Candidate findStartupCandidate(File classes) throws IOException {
        ArrayList<File> files = new ArrayList<File>();
        collectClassFiles(classes, files, 0, 14);
        Candidate best = null;
        for (File f : files) {
            byte[] bytes;
            try { bytes = Files.readAllBytes(f.toPath()); }
            catch (Throwable ignore) { continue; }
            ClassRefs refs;
            try { refs = ClassRefs.parse(bytes); }
            catch (Throwable ignore) { continue; }
            Set<String> ctors = refs.methodRefs("itmc/regedit/RegisterMain", "<init>");
            if (ctors.isEmpty() || !refs.hasMethodRef("itmc/regedit/RegisterMain", "checkReInfo", "()Z")) continue;
            String rel = relative(classes, f);
            int score = startupScore(f.getName(), rel);
            boolean rootEvidence = refs.hasMethodRef("javax/servlet/ServletContext", "getRealPath",
                    "(Ljava/lang/String;)Ljava/lang/String;")
                    || refs.hasMethodRef("jakarta/servlet/ServletContext", "getRealPath",
                    "(Ljava/lang/String;)Ljava/lang/String;");
            Candidate c = new Candidate(rel, score, ctors, rootEvidence);
            if (best == null || c.score > best.score
                    || (c.score == best.score && c.relativePath.compareToIgnoreCase(best.relativePath) < 0)) best = c;
        }
        return best;
    }

    /**
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
            String owner = factoryRel.replace('\\', '/');
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

    /**
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

    private static int startupScore(String simple, String rel) {
        if ("SysParamInit.class".equalsIgnoreCase(simple)) return 1000;
        if ("SystemSetListener.class".equalsIgnoreCase(simple)) return 980;
        if ("RegisterListener.class".equalsIgnoreCase(simple)) return 960;
        if ("RegisterUtil.class".equalsIgnoreCase(simple)) return 600;
        String x = rel.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (x.contains("/listener/") || x.contains("/configuration/")) return 400;
        return 100;
    }

    private static void collectClassFiles(File dir, List<File> out, int depth, int maxDepth) {
        if (dir == null || !dir.isDirectory() || depth > maxDepth) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectClassFiles(f, out, depth + 1, maxDepth);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".class")) out.add(f);
        }
    }

    private static String relative(File base, File child) {
        try { return base.toPath().toAbsolutePath().normalize().relativize(child.toPath().toAbsolutePath().normalize()).toString(); }
        catch (Throwable ex) { return child.getAbsolutePath(); }
    }

    private static byte[] readJarEntry(File jar, String entry) throws IOException {
        JarFile jf = new JarFile(jar);
        try {
            JarEntry e = jf.getJarEntry(entry);
            if (e == null) return null;
            DataInputStream in = new DataInputStream(jf.getInputStream(e));
            try {
                byte[] buf = new byte[8192];
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                return out.toByteArray();
            } finally { in.close(); }
        } finally { jf.close(); }
    }

    static final class ClasspathFactoryRelay {
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
        final int score;
        final Set<String> ctorDescriptors;
        final boolean servletRootEvidence;
        Candidate(String relativePath, int score, Set<String> ctorDescriptors, boolean servletRootEvidence) {
            this.relativePath = relativePath;
            this.score = score;
            this.ctorDescriptors = ctorDescriptors;
            this.servletRootEvidence = servletRootEvidence;
        }
    }

    /** Minimal constant-pool parser; Code attributes are intentionally never decoded. */
    static final class ClassRefs {
        final int[] tags;
        final int[] a;
        final int[] b;
        final String[] utf;
        final LinkedHashSet<String> declaredConstructorDescriptors;

        ClassRefs(int[] tags, int[] a, int[] b, String[] utf,
                  LinkedHashSet<String> declaredConstructorDescriptors) {
            this.tags = tags;
            this.a = a;
            this.b = b;
            this.utf = utf;
            this.declaredConstructorDescriptors = declaredConstructorDescriptors;
        }

        static ClassRefs parse(byte[] data) throws IOException {
            if (data == null || data.length < 10) throw new IOException("short class file");
            Cursor c = new Cursor(data);
            if (c.u4() != 0xCAFEBABEL) throw new IOException("bad class magic");
            c.u2();
            c.u2();
            int count = c.u2();
            int[] tags = new int[count];
            int[] a = new int[count];
            int[] b = new int[count];
            String[] utf = new String[count];
            for (int i = 1; i < count; i++) {
                int tag = c.u1();
                tags[i] = tag;
                switch (tag) {
                    case 1: {
                        int n = c.u2();
                        utf[i] = new String(c.bytes(n), StandardCharsets.UTF_8);
                        break;
                    }
                    case 3:
                    case 4:
                        c.skip(4);
                        break;
                    case 5:
                    case 6:
                        c.skip(8);
                        i++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        a[i] = c.u2();
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        a[i] = c.u2();
                        b[i] = c.u2();
                        break;
                    case 15:
                        a[i] = c.u1();
                        b[i] = c.u2();
                        break;
                    default:
                        throw new IOException("unsupported constant-pool tag " + tag);
                }
            }
            c.skip(6);
            int interfaces = c.u2();
            c.skip(interfaces * 2);
            skipMembers(c);
            int methods = c.u2();
            LinkedHashSet<String> declared = new LinkedHashSet<String>();
            for (int i = 0; i < methods; i++) {
                c.u2();
                int nameIndex = c.u2();
                int descIndex = c.u2();
                if ("<init>".equals(value(utf, nameIndex))) declared.add(value(utf, descIndex));
                int attrs = c.u2();
                skipAttributes(c, attrs);
            }
            return new ClassRefs(tags, a, b, utf, declared);
        }

        Set<String> declaredMethods(String name) {
            return "<init>".equals(name)
                    ? Collections.unmodifiableSet(declaredConstructorDescriptors)
                    : Collections.<String>emptySet();
        }

        Set<String> methodRefs(String owner, String name) {
            LinkedHashSet<String> out = new LinkedHashSet<String>();
            for (int i = 1; i < tags.length; i++) {
                if (tags[i] != 10 && tags[i] != 11) continue;
                if (!owner.equals(className(a[i]))) continue;
                String method = natName(b[i]);
                if (name.equals(method)) out.add(natDesc(b[i]));
            }
            return out;
        }

        boolean hasMethodRef(String owner, String name, String desc) {
            for (int i = 1; i < tags.length; i++) {
                if (tags[i] != 10 && tags[i] != 11) continue;
                if (owner.equals(className(a[i])) && name.equals(natName(b[i])) && desc.equals(natDesc(b[i]))) return true;
            }
            return false;
        }

        private String className(int classIndex) {
            if (classIndex <= 0 || classIndex >= tags.length || tags[classIndex] != 7) return null;
            return value(utf, a[classIndex]);
        }

        private String natName(int natIndex) {
            if (natIndex <= 0 || natIndex >= tags.length || tags[natIndex] != 12) return null;
            return value(utf, a[natIndex]);
        }

        private String natDesc(int natIndex) {
            if (natIndex <= 0 || natIndex >= tags.length || tags[natIndex] != 12) return null;
            return value(utf, b[natIndex]);
        }

        private static String value(String[] values, int index) {
            return index > 0 && index < values.length ? values[index] : null;
        }

        private static void skipMembers(Cursor c) throws IOException {
            int count = c.u2();
            for (int i = 0; i < count; i++) {
                c.skip(6);
                int attrs = c.u2();
                skipAttributes(c, attrs);
            }
        }

        private static void skipAttributes(Cursor c, int count) throws IOException {
            for (int i = 0; i < count; i++) {
                c.u2();
                long n = c.u4();
                if (n > Integer.MAX_VALUE) throw new IOException("oversized class attribute");
                c.skip((int) n);
            }
        }
    }

    static final class Cursor {
        final byte[] data;
        int p;

        Cursor(byte[] data) {
            this.data = data;
        }

        int u1() throws IOException {
            need(1);
            return data[p++] & 255;
        }

        int u2() throws IOException {
            need(2);
            int v = ((data[p] & 255) << 8) | (data[p + 1] & 255);
            p += 2;
            return v;
        }

        long u4() throws IOException {
            need(4);
            long v = ((long) (data[p] & 255) << 24)
                    | ((long) (data[p + 1] & 255) << 16)
                    | ((long) (data[p + 2] & 255) << 8)
                    | (long) (data[p + 3] & 255);
            p += 4;
            return v;
        }

        byte[] bytes(int n) throws IOException {
            need(n);
            byte[] out = new byte[n];
            System.arraycopy(data, p, out, 0, n);
            p += n;
            return out;
        }

        void skip(int n) throws IOException {
            if (n < 0) throw new IOException("negative skip");
            need(n);
            p += n;
        }

        void need(int n) throws IOException {
            if (p + n > data.length) throw new IOException("truncated class file");
        }
    }
}
