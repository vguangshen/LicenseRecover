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
        Attempt(Mode mode, File baseDir, String sourceClass) {
            this.mode = mode;
            this.baseDir = baseDir;
            this.sourceClass = sourceClass;
        }
        public String summary() {
            return mode.cliName + (baseDir == null ? "(target-default-path)" : "(" + baseDir.getAbsolutePath() + ")");
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
            if (best == null) return fail(jar, "未找到同时调用 RegisterMain.<init> 与 checkReInfo() 的启动类");

            ArrayList<Attempt> attempts = new ArrayList<Attempt>();
            if (modern) {
                if (best.ctorDescriptors.contains(D3)) {
                    if (!best.servletRootEvidence)
                        return fail(jar, "启动类使用 3 参 RegisterMain，但未证明 ServletContext.getRealPath(\"/\") 根路径: " + best.relativePath);
                    attempts.add(new Attempt(Mode.THREE_ARG_TOKEN_PATH, root, best.relativePath));
                }
                if (best.ctorDescriptors.contains(D2))
                    attempts.add(new Attempt(Mode.TWO_ARG_TOKEN_DEFAULT, null, best.relativePath));
                if (best.ctorDescriptors.contains(D1) && has1)
                    attempts.add(new Attempt(Mode.ONE_ARG_DEFAULT, null, best.relativePath));
            } else {
                if (best.ctorDescriptors.contains(D2)) {
                    if (!best.servletRootEvidence)
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
        ArrayList<Mode> out = new ArrayList<Mode>();
        if (startupDescriptors == null) return out;
        if (modern) {
            if (startupDescriptors.contains(D3) && servletRootEvidence) out.add(Mode.THREE_ARG_TOKEN_PATH);
            if (startupDescriptors.contains(D2)) out.add(Mode.TWO_ARG_TOKEN_DEFAULT);
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
