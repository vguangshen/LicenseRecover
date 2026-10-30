import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Positive-only static proof of an audited installed authorization gate.
 * Never loads vendor code, decrypts a license, queries hardware or invokes a process.
 * Acceptance is distinct from issuer authorization and a provenance-backed Patch pair.
 */
public final class LicenseRecoverReadOnlyJavaVerifier {
    private LicenseRecoverReadOnlyJavaVerifier() { }

    static final class Profile {
        final String id, classes, libraries, configurations;
        final int classCount, libraryCount, configurationCount;
        Profile(String id, int classCount, String classes, int libraryCount,
                String libraries, int configurationCount, String configurations) {
            this.id = id; this.classCount = classCount; this.classes = classes;
            this.libraryCount = libraryCount; this.libraries = libraries;
            this.configurationCount = configurationCount; this.configurations = configurations;
        }
        boolean matches(Snapshot s) {
            return classCount == s.classes.items.size() && classes.equals(s.classes.digest())
                    && libraryCount == s.libraries.items.size() && libraries.equals(s.libraries.digest())
                    && configurationCount == s.configurations.items.size()
                    && configurations.equals(s.configurations.digest());
        }
    }

    // Source-path/bytecode review: docs/JAVA_STATIC_ACCEPTANCE_PROOF.md.
    // These observations prove installed gate semantics. They do not establish
    // whether a vendor originally shipped these bytes or a Patch produced them.
    private static final Profile[] REVIEWED = {
        new Profile("DS24-static-gate-20261030", 329,
                "c541462d172101b1b4359006aadc99b91c8882da01dd1b09ec5b8ef8cc01187d", 145,
                "172eff9cdf258f709aae5c2c898a5f545ead3a3226c1a1e073a4e66541d915e3", 9,
                "0df89907d9b0362ce950a96e69bbfa5b51dfb6b6da818990f846b6b200ae5070")
    };

    public static LicenseRecoverLicenseState verify(File appRoot) {
        // Most Java families use a different global class. A cheap positive-only
        // anchor avoids hashing their complete dependency trees for an inapplicable proof.
        if (appRoot == null) return LicenseRecoverLicenseState.unverified("缺少 Java 应用目录");
        try {
            Item anchor = read(appRoot.toPath().resolve("WEB-INF/classes/global/IGlobal.class"), true);
            if (!anchor.sha.equals("55b1a190d74a931bb47972c3bd812eff840d27552d4120cf56be8f3a69f835e6"))
                return LicenseRecoverLicenseState.unverified("Java 授权门槛入口未匹配审核版本");
        } catch (NoSuchFileException | UnsupportedLayout | Changed e) {
            return LicenseRecoverLicenseState.unverified("没有适用的 Java 静态授权门槛证明");
        } catch (IOException | SecurityException e) {
            return LicenseRecoverLicenseState.scanError("Java 授权入口只读检查失败: " + e.getClass().getSimpleName());
        }
        return verifyReviewed(appRoot, REVIEWED);
    }

    /** Opaque code/configuration input receipt for the coordinator's before/after check. */
    public static String inputSnapshot(File appRoot) throws IOException {
        if (appRoot == null) throw new NoSuchFileException("application-root");
        return snapshot(appRoot.toPath()).identity();
    }

    // Synthetic fixtures can supply reviewed receipts without making them a production trust source.
    static LicenseRecoverLicenseState verifyReviewed(File appRoot, Profile[] profiles) {
        if (appRoot == null || profiles == null)
            return LicenseRecoverLicenseState.unverified("缺少 Java 授权门槛审核输入");
        try {
            Snapshot first = snapshot(appRoot.toPath());
            for (Profile profile : profiles) {
                if (profile == null || !validProfile(profile))
                    return LicenseRecoverLicenseState.scanError("Java 授权门槛审核记录损坏");
                if (!profile.matches(first)) continue;
                Snapshot second = snapshot(appRoot.toPath());
                if (!profile.matches(second) || !first.identity().equals(second.identity()))
                    return LicenseRecoverLicenseState.unverified("Java 授权门槛证据读取期间发生变化");
                return LicenseRecoverLicenseState.authorizationAcceptance(
                        "静态授权门槛证明 " + profile.id + "：完整 " + profile.classCount
                        + " 个应用类、" + profile.libraryCount + " 个依赖和 "
                        + profile.configurationCount + " 个配置资源精确匹配；"
                        + "启动注册及产品授权门槛默认放行；未执行目标代码；"
                        + "不区分原生激活与代码修改，不证明厂商授权或可信 Patch 来源");
            }
            return LicenseRecoverLicenseState.unverified("当前 Java 构建未匹配已审核的完整授权门槛证明");
        } catch (NoSuchFileException | UnsupportedLayout | Changed e) {
            return LicenseRecoverLicenseState.unverified("Java 授权门槛证据缺失、链接布局或读取期间变化");
        } catch (IOException | SecurityException e) {
            return LicenseRecoverLicenseState.scanError("Java 授权门槛只读检查失败: "
                    + e.getClass().getSimpleName());
        }
    }

    private static boolean validProfile(Profile p) {
        return p.id != null && p.id.matches("[A-Za-z0-9._-]+") && p.classCount > 0
                && p.libraryCount > 0 && p.configurationCount > 0
                && isHash(p.classes) && isHash(p.libraries) && isHash(p.configurations);
    }
    private static boolean isHash(String s) { return s != null && s.matches("[0-9a-f]{64}"); }

    static final class Snapshot {
        final Group classes = new Group(), libraries = new Group(), configurations = new Group();
        String identity() {
            return hash((classes.identity() + libraries.identity()
                    + configurations.identity()).getBytes(StandardCharsets.UTF_8));
        }
        Profile fixtureProfile() {
            return new Profile("synthetic-reviewed-gate", classes.items.size(), classes.digest(),
                    libraries.items.size(), libraries.digest(), configurations.items.size(), configurations.digest());
        }
    }
    private static final class Item {
        final String sha, identity;
        Item(String sha, BasicFileAttributes a) {
            this.sha = sha;
            this.identity = sha + ":" + a.size() + ":" + a.lastModifiedTime().toString()
                    + ":" + String.valueOf(a.fileKey());
        }
    }
    private static final class Group {
        final Map<String, Item> items = new TreeMap<String, Item>();
        String digest() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Item> e : items.entrySet())
                b.append(e.getValue().sha).append("  ").append(e.getKey()).append('\n');
            return hash(b.toString().getBytes(StandardCharsets.UTF_8));
        }
        String identity() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Item> e : items.entrySet())
                b.append(e.getKey()).append('\0').append(e.getValue().identity).append('\n');
            return hash(b.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private static final class Changed extends IOException { }
    private static final class UnsupportedLayout extends IOException { }

    static Snapshot snapshot(Path input) throws IOException {
        final Path root = input.toAbsolutePath().normalize();
        final Path classes = root.resolve("WEB-INF/classes"), lib = root.resolve("WEB-INF/lib");
        requireDirectory(root); requireDirectory(classes); requireDirectory(lib);
        final Snapshot out = new Snapshot();
        Files.walkFileTree(classes, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a)
                    throws IOException {
                checkPath(dir);
                if (dir.getFileName().toString().equals(".svn")) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a)
                    throws IOException {
                checkPath(file);
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".class"))
                    out.classes.items.put(relative(classes, file), read(file, true));
                else if (configuration(name))
                    out.configurations.items.put(relative(root, file), read(file, false));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException failure)
                    throws IOException { throw failure; }
        });
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(lib)) {
            List<Path> sorted = new ArrayList<Path>();
            for (Path file : jars) {
                if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) sorted.add(file);
            }
            java.util.Collections.sort(sorted);
            for (Path file : sorted) out.libraries.items.put(relative(lib, file), read(file, false));
        }
        Path web = root.resolve("WEB-INF/web.xml");
        out.configurations.items.put(relative(root, web), read(web, false));
        return out;
    }

    private static boolean configuration(String n) {
        // The audited gate ignores DB credentials and registration XML. Bind only
        // Struts providers/interceptor wiring here, plus WEB-INF/web.xml below.
        return n.startsWith("struts") && (n.endsWith(".xml") || n.endsWith(".properties"));
    }
    private static String relative(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
    private static void requireDirectory(Path p) throws IOException {
        checkPath(p);
        if (!Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory())
            throw new IOException("not-directory");
    }
    private static void checkPath(Path p) throws IOException {
        Path absolute = p.toAbsolutePath().normalize(), cursor = absolute.getRoot();
        for (Path part : absolute) {
            cursor = cursor.resolve(part);
            BasicFileAttributes a = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (a.isSymbolicLink() || a.isOther()) throw new UnsupportedLayout();
        }
    }
    private static Item read(Path p, boolean classFile) throws IOException {
        checkPath(p);
        BasicFileAttributes before = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > 128L * 1024 * 1024) throw new UnsupportedLayout();
        MessageDigest sha = digest();
        byte[] header = new byte[8]; int headerCount = 0;
        try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]; int n; long total = 0;
            while ((n = in.read(buffer)) != -1) {
                if ((total += n) > 128L * 1024 * 1024) throw new IOException("artifact-limit");
                int take = Math.min(n, header.length - headerCount);
                if (take > 0) { System.arraycopy(buffer, 0, header, headerCount, take); headerCount += take; }
                sha.update(buffer, 0, n);
            }
        }
        if (classFile && (headerCount != 8 || header[0] != (byte) 0xca || header[1] != (byte) 0xfe
                || header[2] != (byte) 0xba || header[3] != (byte) 0xbe)) throw new IOException("class-header");
        BasicFileAttributes after = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) throw new Changed();
        return new Item(hex(sha.digest()), after);
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static String hash(byte[] b) { return hex(digest().digest(b)); }
    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(Character.forDigit((x >>> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return s.toString();
    }
}
