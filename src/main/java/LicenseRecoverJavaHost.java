import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Isolated Java registration host.
 *
 * Unlike the legacy Java recovery path, this process does not put the selected
 * application's WEB-INF/lib on the system classpath and does not construct or
 * serialize RegeditInfo itself.  Target classes are loaded through a child-first
 * class loader and the target RegisterMain.doRegistry()/checkReInfo() contract is
 * used for persistence and verification.
 */
public final class LicenseRecoverJavaHost {
    private LicenseRecoverJavaHost() { }

    static final class Options {
        String mode;
        File appRoot;
        File runtimeDir;
        File baseDir;
        String product;
        String version;
        String regStr;
        String seq;
        String code;
    }

    static final class TargetRuntime implements AutoCloseable {
        final ChildFirstLoader loader;
        final File unpackDir;

        TargetRuntime(ChildFirstLoader loader, File unpackDir) {
            this.loader = loader;
            this.unpackDir = unpackDir;
        }

        public void close() throws Exception {
            try { loader.close(); }
            finally { deleteRecursive(unpackDir); }
        }
    }

    /** Prefer every target-owned dependency over the tool's bundled compatibility jars. */
    static final class ChildFirstLoader extends URLClassLoader {
        ChildFirstLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }

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

        private static boolean parentFirst(String name) {
            return name.startsWith("java.") || name.startsWith("javax.")
                    || name.startsWith("sun.") || name.startsWith("com.sun.")
                    || name.startsWith("jdk.") || name.startsWith("org.w3c.")
                    || name.startsWith("org.xml.");
        }
    }

    /**
     * Java 8 process-local network guard.  It is installed before target classes are
     * loaded, so a registration constructor cannot contact the remote service or
     * terminate the helper JVM.  Other permissions remain allowed.
     */
    static final class RecoverySecurityManager extends SecurityManager {
        public void checkPermission(Permission perm) { }
        public void checkPermission(Permission perm, Object context) { }
        public void checkConnect(String host, int port) {
            if (!loopback(host)) throw new SecurityException("[PRE_BLOCK] outbound connection blocked: " + host + ":" + port);
        }
        public void checkConnect(String host, int port, Object context) { checkConnect(host, port); }
        public void checkExit(int status) {
            throw new SecurityException("target System.exit(" + status + ") blocked by Java registration host");
        }
    }

    public static void main(String[] args) {
        int rc = 1;
        try {
            Options o = parse(args);
            installIsolation();
            TargetRuntime target = openTarget(o.appRoot, o.runtimeDir);
            try {
                if ("probe".equals(o.mode)) rc = probe(target, o);
                else if ("gencode".equals(o.mode)) rc = gencode(target, o);
                else if ("doreg".equals(o.mode)) rc = doreg(target, o);
                else if ("verify".equals(o.mode)) rc = verify(target, o);
                else throw new IllegalArgumentException("unknown mode: " + o.mode);
            } finally {
                target.close();
            }
        } catch (Throwable ex) {
            Throwable root = unwrap(ex);
            System.err.println("JAVA_HOST_ERROR=" + root.getClass().getName() + ": " + String.valueOf(root.getMessage()));
            root.printStackTrace(System.err);
            rc = 1;
        }
        try { System.setSecurityManager(null); } catch (Throwable ignore) { }
        System.exit(rc);
    }

    static Options parse(String[] args) {
        if (args == null || args.length < 3)
            throw new IllegalArgumentException("usage: <probe|gencode|doreg|verify> <appRoot> <runtimeDir> [options]");
        Options o = new Options();
        o.mode = args[0].trim().toLowerCase(Locale.ROOT);
        o.appRoot = new File(args[1]).getAbsoluteFile();
        o.runtimeDir = new File(args[2]).getAbsoluteFile();
        for (int i = 3; i < args.length; i++) {
            String a = args[i];
            if ("--base".equals(a) && i + 1 < args.length) o.baseDir = new File(args[++i]).getAbsoluteFile();
            else if ("--product".equals(a) && i + 1 < args.length) o.product = args[++i];
            else if ("--version".equals(a) && i + 1 < args.length) o.version = args[++i];
            else if ("--regstr".equals(a) && i + 1 < args.length) o.regStr = normalizeCsv(args[++i]);
            else if ("--seq".equals(a) && i + 1 < args.length) o.seq = args[++i];
            else if ("--code".equals(a) && i + 1 < args.length) o.code = args[++i];
            else throw new IllegalArgumentException("unknown/missing option: " + a);
        }
        if (!o.appRoot.isDirectory()) throw new IllegalArgumentException("appRoot not found: " + o.appRoot);
        if (!o.runtimeDir.isDirectory()) throw new IllegalArgumentException("runtimeDir not found: " + o.runtimeDir);
        if (blank(o.product)) throw new IllegalArgumentException("--product is required");
        if (o.baseDir == null) o.baseDir = o.appRoot;
        if (!o.baseDir.isDirectory()) throw new IllegalArgumentException("registration base not found: " + o.baseDir);
        return o;
    }

    static void installIsolation() {
        System.setProperty("java.net.useSystemProxies", "false");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "9");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "9");
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        System.setProperty("sun.net.client.defaultConnectTimeout", "3000");
        System.setProperty("sun.net.client.defaultReadTimeout", "3000");
        System.setSecurityManager(new RecoverySecurityManager());
        System.out.println("[java-stage] PRE_BLOCK: OK (process-local SecurityManager + inert proxy)");
    }

    static TargetRuntime openTarget(File appRoot, File runtimeDir) throws Exception {
        ArrayList<URL> urls = new ArrayList<URL>();
        File unpack = null;
        File[] jars = runtimeDir.listFiles();
        if (jars == null) jars = new File[0];
        Arrays.sort(jars, new Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });

        for (File jar : jars) {
            if (!jar.isFile() || !jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            if (!jar.getName().toLowerCase(Locale.ROOT).contains("itmcreg")) continue;
            try {
                if (NetRemover.jarIsPacked(jar)) {
                    Path temp = Files.createTempDirectory("lrc-java-target-unpack-");
                    unpack = temp.toFile();
                    int count = NetRemover.unpackPackedJar(jar, unpack);
                    if (count <= 0) throw new IOException("packed ITMCReg jar produced no restored classes: " + jar.getName());
                    urls.add(unpack.toURI().toURL());
                    System.out.println("[java-host] restored packed target registration classes=" + count + " from " + jar.getName());
                    break;
                }
            } catch (Throwable ex) {
                deleteRecursive(unpack);
                throw new IOException("failed to prepare packed target registration jar " + jar.getName(), ex);
            }
        }

        File classes = new File(appRoot, "WEB-INF" + File.separator + "classes");
        if (classes.isDirectory()) urls.add(classes.toURI().toURL());
        for (File jar : jars) {
            if (jar.isFile() && jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
                urls.add(jar.toURI().toURL());
        }
        ChildFirstLoader loader = new ChildFirstLoader(urls.toArray(new URL[urls.size()]),
                ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        System.out.println("[java-host] target classloader=child-first; target URLs=" + urls.size());
        return new TargetRuntime(loader, unpack);
    }

    static int probe(TargetRuntime rt, Options o) throws Exception {
        Object reg = newRegisterMain(rt.loader, o.product, o.baseDir);
        Object info = invokeOptionalNoArg(reg, "getRegInfo");
        String regStr = normalizeCsv(stringGetter(info, "getRegStr"));
        String proName = stringGetter(info, "getProName");
        System.out.println("TARGET_BASE=" + canonical(o.baseDir));
        System.out.println("TARGET_PRODUCT=" + safe(proName));
        System.out.println("TARGET_REGSTR=" + safe(regStr));
        System.out.println("TARGET_REGISTERMAIN=" + reg.getClass().getName());
        System.out.println("TARGET_DOREG_SIGNATURE=" + methodSummary(reg.getClass(), "doRegistry"));
        System.out.println("RESULT: OK");
        return 0;
    }

    static int gencode(TargetRuntime rt, Options o) throws Exception {
        if (blank(o.regStr)) throw new IllegalArgumentException("--regstr is required for gencode");
        Class<?> desClass = Class.forName("itmc.regedit.DesUtil", true, rt.loader);
        Object des = newInstance(desClass);
        Class<?> codeClass = Class.forName("itmc.regedit.GetRegisterCode", true, rt.loader);
        Object codeHelper = newInstance(codeClass);

        String seq = o.seq;
        if (blank(seq)) {
            String data = str(invokeNoArg(des, "getRandom"))
                    + str(invokeNoArg(des, "getMotherboardSN"))
                    + str(invokeNoArg(des, "getRandom"))
                    + str(invokeNoArg(des, "getCurrentTime"))
                    + str(invokeNoArg(des, "getRandom"));
            seq = str(invoke(codeHelper, "buildCiphertext", new Class<?>[]{String.class}, new Object[]{data}));
        }
        String decoded = str(invoke(codeHelper, "decrypt",
                new Class<?>[]{String.class, String.class}, new Object[]{"itmcsoft", seq}));
        if (decoded.length() < 43) throw new IllegalStateException("target GetRegisterCode could not decode request code");
        String sn = decoded.substring(4, 20);
        String time19 = decoded.substring(24, 43);
        String plain = "00" + time19 + "00" + sn + "00" + "2099-12-31"
                + "00" + "1" + "00" + "-001" + "00" + "-1" + "00" + o.regStr;
        String auth = str(invoke(codeHelper, "encrypt",
                new Class<?>[]{String.class, String.class}, new Object[]{"itmc" + o.product, plain}));
        if (blank(auth)) throw new IllegalStateException("target GetRegisterCode returned empty authorization code");
        System.out.println("注册申请号        : " + seq);
        System.out.println("离线授权码        : " + auth);
        System.out.println("TARGET_MACHINE=" + sn);
        System.out.println("TARGET_REGSTR=" + o.regStr);
        System.out.println("RESULT: OK");
        return 0;
    }

    static int doreg(TargetRuntime rt, Options o) throws Exception {
        if (blank(o.seq) || blank(o.code)) throw new IllegalArgumentException("--seq and --code are required for doreg");
        Object reg = newRegisterMain(rt.loader, o.product, o.baseDir);
        Method method = findStringPairMethod(reg.getClass(), "doRegistry");
        if (method == null) throw new NoSuchMethodException("target RegisterMain has no doRegistry(String,String); methods="
                + methodSummary(reg.getClass(), "doRegistry"));
        method.setAccessible(true);
        Object result;
        try { result = method.invoke(reg, o.seq, o.code); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
        System.out.println("[java-stage] DOREG native-return=" + String.valueOf(result));
        System.out.println("TARGET_BASE=" + canonical(o.baseDir));
        System.out.println("RESULT: OK");
        return 0;
    }

    static int verify(TargetRuntime rt, Options o) throws Exception {
        Object reg = newRegisterMain(rt.loader, o.product, o.baseDir);
        Method check = findNoArgMethod(reg.getClass(), "checkReInfo");
        if (check == null) throw new NoSuchMethodException("target RegisterMain has no checkReInfo()");
        check.setAccessible(true);
        Object raw;
        try { raw = check.invoke(reg); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
        if (!(raw instanceof Boolean)) throw new IllegalStateException("checkReInfo() did not return boolean: " + raw);
        boolean unregistered = ((Boolean) raw).booleanValue();
        Object info = invokeOptionalNoArg(reg, "getRegInfo");
        String regStr = normalizeCsv(stringGetter(info, "getRegStr"));
        String proName = stringGetter(info, "getProName");
        String regId = stringGetter(info, "getRegID");
        System.out.println("TARGET_BASE=" + canonical(o.baseDir));
        System.out.println("TARGET_PRODUCT=" + safe(proName));
        System.out.println("TARGET_REGID=" + safe(regId));
        System.out.println("TARGET_REGSTR=" + safe(regStr));
        System.out.println("TARGET_CHECK_REINFO=" + unregistered);
        if (unregistered) throw new IllegalStateException("target checkReInfo() reports unregistered");
        if (blank(regStr)) throw new IllegalStateException("target getRegInfo().RegStr is empty after native write-back");
        if (!blank(o.version) && !containsCsv(regStr, o.version))
            throw new IllegalStateException("persisted RegStr does not contain current SoftVersionID=" + o.version + "; RegStr=" + regStr);
        if (!blank(proName) && !proName.trim().equalsIgnoreCase(o.product.trim()))
            throw new IllegalStateException("persisted ProName mismatch: expected=" + o.product + " actual=" + proName);
        System.out.println("[java-stage] VERIFY_FRESH: OK");
        System.out.println("RESULT: OK");
        return 0;
    }

    static Object newRegisterMain(ClassLoader loader, String product, File baseDir) throws Exception {
        Class<?> codeClass = Class.forName("itmc.regedit.GetRegisterCode", true, loader);
        Object codeHelper = newInstance(codeClass);
        String token = str(invoke(codeHelper, "encrypt",
                new Class<?>[]{String.class, String.class},
                new Object[]{product + "RegeditNew", "itmcsoft"}));
        Class<?> rm = Class.forName("itmc.regedit.RegisterMain", true, loader);
        String path = withSeparator(baseDir);
        return instantiateRegisterMainCompatible(rm, product, token, path);
    }

    static Object instantiateRegisterMainCompatible(Class<?> rm, String product, String token, String path) throws Exception {
        try {
            Constructor<?> c = rm.getConstructor(String.class, String.class, String.class);
            return c.newInstance(product, token, path);
        } catch (NoSuchMethodException noThree) {
            try {
                Constructor<?> c = rm.getConstructor(String.class, String.class);
                return c.newInstance(product, path);
            } catch (NoSuchMethodException noTwo) {
                Constructor<?> c = rm.getConstructor(String.class);
                return c.newInstance(product);
            }
        } catch (InvocationTargetException ex) {
            throw rethrow(ex);
        }
    }

    static Method findStringPairMethod(Class<?> type, String name) {
        for (Method m : allMethods(type)) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equalsIgnoreCase(name) && p.length == 2
                    && p[0] == String.class && p[1] == String.class) return m;
        }
        return null;
    }

    static Method findNoArgMethod(Class<?> type, String name) {
        for (Method m : allMethods(type))
            if (m.getName().equalsIgnoreCase(name) && m.getParameterTypes().length == 0) return m;
        return null;
    }

    static List<Method> allMethods(Class<?> type) {
        ArrayList<Method> out = new ArrayList<Method>();
        Collections.addAll(out, type.getMethods());
        for (Method m : type.getDeclaredMethods()) if (!out.contains(m)) out.add(m);
        return out;
    }

    static String methodSummary(Class<?> type, String name) {
        StringBuilder b = new StringBuilder();
        for (Method m : allMethods(type)) {
            if (!m.getName().equalsIgnoreCase(name)) continue;
            if (b.length() > 0) b.append(" | ");
            b.append(m.getName()).append('(');
            Class<?>[] p = m.getParameterTypes();
            for (int i = 0; i < p.length; i++) {
                if (i > 0) b.append(',');
                b.append(p[i].getSimpleName());
            }
            b.append(')').append(':').append(m.getReturnType().getSimpleName());
        }
        return b.length() == 0 ? "<none>" : b.toString();
    }

    static Object invokeOptionalNoArg(Object target, String name) throws Exception {
        Method m = findNoArgMethod(target.getClass(), name);
        if (m == null) return null;
        m.setAccessible(true);
        try { return m.invoke(target); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
    }

    static Object invokeNoArg(Object target, String name) throws Exception {
        Method m = findNoArgMethod(target.getClass(), name);
        if (m == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name + "()");
        m.setAccessible(true);
        try { return m.invoke(target); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
    }

    static Object invoke(Object target, String name, Class<?>[] types, Object[] values) throws Exception {
        Method m;
        try { m = target.getClass().getMethod(name, types); }
        catch (NoSuchMethodException ex) {
            m = target.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
        }
        try { return m.invoke(target, values); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
    }

    static Object newInstance(Class<?> type) throws Exception {
        Constructor<?> c = type.getDeclaredConstructor();
        if (!Modifier.isPublic(c.getModifiers()) || !Modifier.isPublic(type.getModifiers())) c.setAccessible(true);
        try { return c.newInstance(); }
        catch (InvocationTargetException ex) { throw rethrow(ex); }
    }

    static String stringGetter(Object info, String name) {
        if (info == null) return null;
        try {
            Object value = invokeOptionalNoArg(info, name);
            return value == null ? null : String.valueOf(value).trim();
        } catch (Throwable ignore) { return null; }
    }

    static Exception rethrow(InvocationTargetException ex) {
        Throwable c = ex.getCause();
        if (c instanceof Exception) return (Exception) c;
        if (c instanceof Error) throw (Error) c;
        return new Exception(c == null ? ex : c);
    }

    static Throwable unwrap(Throwable ex) {
        Throwable x = ex;
        while (x instanceof InvocationTargetException && ((InvocationTargetException) x).getCause() != null)
            x = ((InvocationTargetException) x).getCause();
        return x;
    }

    static String normalizeCsv(String raw) {
        if (blank(raw)) return null;
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (String part : raw.split(",")) {
            String x = part == null ? "" : part.trim();
            if (!x.isEmpty()) values.add(x);
        }
        if (values.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String x : values) {
            if (out.length() > 0) out.append(',');
            out.append(x);
        }
        return out.toString();
    }

    static boolean containsCsv(String csv, String wanted) {
        if (blank(csv) || blank(wanted)) return false;
        for (String x : csv.split(",")) if (wanted.trim().equalsIgnoreCase(x.trim())) return true;
        return false;
    }

    static String withSeparator(File dir) {
        String p = dir.getAbsolutePath();
        return p.endsWith(File.separator) ? p : p + File.separator;
    }

    static boolean loopback(String host) {
        if (host == null) return false;
        String h = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || "::1".equals(h) || "[::1]".equals(h) || h.startsWith("127.");
    }

    static String canonical(File f) {
        try { return f.getCanonicalPath(); }
        catch (IOException ex) { return f.getAbsolutePath(); }
    }

    static String safe(String s) { return blank(s) ? "" : s.trim(); }
    static String str(Object x) { return x == null ? "" : String.valueOf(x); }
    static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) deleteRecursive(child);
        try { Files.deleteIfExists(f.toPath()); } catch (Throwable ignore) { }
    }
}
