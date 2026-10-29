import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Read-only integration point for reviewed Java/.NET patch fingerprints.
 *
 * Only pairs compiled into REVIEWED may authorize a "confirmed patch" result.
 * Local config, previous recovery records, user-controlled manifests and a
 * missing network marker cannot populate this registry. An empty registry
 * deliberately leaves real applications UNVERIFIED.
 */
public final class LicenseRecoverReadOnlyPatchScanner {
    public enum Runtime { JAVA, DOTNET }

    static final class Reviewed {
        final Runtime runtime;
        final String version;
        final String relativeArtifact;
        final String originalSha256;
        final String patchedSha256;

        Reviewed(Runtime runtime, String version, String relativeArtifact,
                 String originalSha256, String patchedSha256) {
            this.runtime = runtime;
            this.version = version;
            this.relativeArtifact = relativeArtifact;
            this.originalSha256 = originalSha256;
            this.patchedSha256 = patchedSha256;
        }
    }

    // No real-world patch pair has yet been independently audited.
    // Add only independently reviewed, exact version + original + patch pairs.
    private static final Reviewed[] REVIEWED = new Reviewed[0];

    private LicenseRecoverReadOnlyPatchScanner() { }

    private static boolean isSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    public static LicenseRecoverLicenseState scan(File runtimeRoot, Runtime runtime,
                                                   String detectedVersion) {
        return scanReviewed(runtimeRoot, runtime, detectedVersion, REVIEWED);
    }

    /**
     * All reviewed artifacts for one runtime/version form one evidence set.
     * A single matching DLL/JAR is insufficient if other audited artifacts
     * are missing, unknown or still original. Never use first-match wins.
     */
    static LicenseRecoverLicenseState scanReviewed(File runtimeRoot, Runtime runtime,
            String detectedVersion, Reviewed[] reviewed) {
        if (runtimeRoot == null || runtime == null || detectedVersion == null
                || detectedVersion.trim().isEmpty())
            return LicenseRecoverLicenseState.unverified("缺少运行目录、平台或版本，不能确认 Patch");
        if (reviewed == null)
            return LicenseRecoverLicenseState.unverified("没有可信审核指纹集");
        java.util.Set<String> seenPaths = new java.util.HashSet<String>();
        // Distinct paths can still be hard links to the same physical file.
        // Count only independent reviewed artifacts as evidence.
        java.util.List<Path> verifiedPaths = new java.util.ArrayList<Path>();
        boolean original = false, patched = false, incomplete = false;
        int matched = 0;
        StringBuilder evidence = new StringBuilder();
        for (Reviewed candidate : reviewed) {
            // Fail closed on malformed audited registry entries. Silently
            // skipping a record without a version/runtime could make a
            // partial fingerprint set look complete and turn the row green.
            if (candidate == null || candidate.runtime == null || candidate.version == null
                    || candidate.version.trim().isEmpty())
                return LicenseRecoverLicenseState.scanError("审核指纹集存在缺失平台或版本的记录");
            if (candidate.runtime != runtime || !detectedVersion.equalsIgnoreCase(candidate.version))
                continue;
            matched++;
            // An incomplete or contradictory trusted baseline is a registry
            // error, not an ordinary unknown target. Never silently downgrade
            // corrupt reviewed evidence to an unverified artifact.
            if (candidate.relativeArtifact == null || candidate.relativeArtifact.trim().isEmpty()
                    || !isSha256(candidate.originalSha256)
                    || !isSha256(candidate.patchedSha256)
                    || candidate.originalSha256.equalsIgnoreCase(candidate.patchedSha256))
                return LicenseRecoverLicenseState.scanError("审核指纹记录缺少目标路径或有效的原版/Patch 双指纹");
            // Normalize aliases before counting independent reviewed artifacts.
            // Otherwise "bin/./X.dll" and "bin/X.dll" can count as two files.
            String relative;
            try {
                relative = Paths.get(candidate.relativeArtifact == null ? ""
                        : candidate.relativeArtifact.replace('\\', '/'))
                        .normalize().toString().replace('\\', '/')
                        .toLowerCase(java.util.Locale.ROOT);
            } catch (InvalidPathException ex) {
                return LicenseRecoverLicenseState.scanError("审核指纹包含非法相对路径");
            }
            if (!seenPaths.add(relative)) {
                incomplete = true; // Conflicting or duplicate baselines are not independent evidence.
                continue;
            }
            LicenseRecoverLicenseState result =
                    compareReviewed(runtimeRoot, runtime, detectedVersion, candidate);
            if (result.authorization == LicenseRecoverLicenseState.Authorization.ERROR
                    || result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.ERROR)
                return result;
            if (result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL
                    || result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED) {
                // Normalized path equality does not detect hard-link aliases.
                // Check filesystem identity only after compareReviewed has
                // rejected traversal, symlinks and unreadable artifacts.
                Path current = runtimeRoot.toPath().toAbsolutePath().normalize()
                        .resolve(Paths.get(candidate.relativeArtifact.replace('\\', '/')).normalize()).normalize();
                try {
                    boolean alias = false;
                    for (Path previous : verifiedPaths) {
                        if (Files.isSameFile(previous, current)) {
                            alias = true;
                            break;
                        }
                    }
                    if (alias) {
                        incomplete = true;
                        continue;
                    }
                    verifiedPaths.add(current);
                } catch (java.io.IOException | SecurityException ex) {
                    return LicenseRecoverLicenseState.scanError(
                            "审核文件身份核对失败: " + ex.getClass().getSimpleName());
                }
            }
            if (result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_ORIGINAL)
                original = true;
            else if (result.codeIntegrity == LicenseRecoverLicenseState.CodeIntegrity.CONFIRMED_PATCHED)
                patched = true;
            else incomplete = true;
            if (result.codeIntegrity != LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN) {
                if (evidence.length() > 0) evidence.append("; ");
                evidence.append(relative).append(": ").append(result.evidence);
            }
        }
        if (matched == 0)
            return LicenseRecoverLicenseState.unverified(
                    "未收录经独立审核的 " + runtime + " " + detectedVersion
                    + " 原版与 Patch 双指纹；不能确认 Patch");
        if (incomplete || (original && patched))
            return LicenseRecoverLicenseState.unverified(
                    "已审核文件证据不完整、重复或原版/Patch 混合；不能确认整套 Patch");
        return LicenseRecoverLicenseState.codeComparison(patched,
                "全部 " + matched + " 个审核文件指纹一致；" + evidence);
    }

    /**
     * Package-private fixture seam: tests can prove real file comparison,
     * traversal rejection and no writes without adding unreviewed baselines
     * to the production registry.
     */
    static LicenseRecoverLicenseState compareReviewed(File runtimeRoot, Runtime runtime,
            String detectedVersion, Reviewed reviewed) {
        if (reviewed == null || runtime == null || reviewed.runtime != runtime
                || detectedVersion == null || reviewed.version == null
                || !detectedVersion.equalsIgnoreCase(reviewed.version))
            return LicenseRecoverLicenseState.unverified("版本或平台与审核指纹不匹配");
        if (runtimeRoot == null || reviewed.relativeArtifact == null
                || reviewed.relativeArtifact.trim().isEmpty())
            return LicenseRecoverLicenseState.unverified("缺少目标文件路径");
        // Trusted artifact paths must be ordinary portable relative filenames.
        // Reject Windows drive-relative syntax (C:foo.dll) and NTFS alternate
        // data streams (file.dll:stream) before any filesystem access, even
        // when tests run on a non-Windows host.
        if (reviewed.relativeArtifact.indexOf(':') >= 0)
            return LicenseRecoverLicenseState.scanError("审核指纹路径包含盘符或备用数据流语法");
        try {
            Path root = runtimeRoot.toPath().toAbsolutePath().normalize();
            // Audited paths use portable separators. Java on Unix treats a
            // backslash as a filename character, so normalize it before
            // checking absolute/traversal syntax on every host platform.
            Path relative = Paths.get(reviewed.relativeArtifact.replace('\\', '/')).normalize();
            if (relative.isAbsolute() || relative.getNameCount() == 0
                    || relative.startsWith("..") || relative.toString().equals("."))
                return LicenseRecoverLicenseState.scanError("审核指纹包含非法相对路径");
            Path artifact = root.resolve(relative).normalize();
            if (!artifact.startsWith(root))
                return LicenseRecoverLicenseState.scanError("审核指纹路径越过应用目录");
            // A nested runtime root may be inside a symlinked parent.
            // Checking only root and relative components misses that escape.
            Path rootCursor = root.getRoot();
            for (Path part : root) {
                rootCursor = rootCursor == null ? part : rootCursor.resolve(part);
                if (Files.isSymbolicLink(rootCursor))
                    return LicenseRecoverLicenseState.scanError("运行目录或上级目录含符号链接");
            }
            // Reject symlinks at every relative component, not just the file.
            Path cursor = root;
            for (Path part : relative) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor))
                    return LicenseRecoverLicenseState.scanError("审核目标路径含符号链接");
            }
            // Files.exists() can return false on permission or metadata I/O
            // errors. Distinguish a genuinely missing file (gray) from a
            // failed read-only inspection (red).
            try {
                Files.readAttributes(artifact, java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
            } catch (java.nio.file.NoSuchFileException ex) {
                return LicenseRecoverLicenseState.unverified("目标文件缺失，不能确认 Patch");
            } catch (java.io.IOException ex) {
                return LicenseRecoverLicenseState.scanError(
                        "审核目标属性读取失败: " + ex.getClass().getSimpleName());
            }
            return LicenseRecoverReadOnlyFingerprint.compare(
                    artifact.toFile(), detectedVersion, reviewed.version,
                    reviewed.originalSha256, reviewed.patchedSha256);
        } catch (InvalidPathException | SecurityException ex) {
            return LicenseRecoverLicenseState.scanError(
                    "只读指纹路径检查失败: " + ex.getClass().getSimpleName());
        }
    }
}
