import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Strict, read-only SHA-256 comparison against TWO independently reviewed,
 * version-specific artifacts. This class does not discover trusted baselines:
 * callers must supply audited original and patched fingerprints. Without both,
 * no file is declared original or patched. No executable is loaded or invoked.
 */
public final class LicenseRecoverReadOnlyFingerprint {
    private LicenseRecoverReadOnlyFingerprint() { }

    private static boolean isSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    private static String digest(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(path,
                java.nio.file.StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count > 0) sha.update(buffer, 0, count);
            }
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : sha.digest()) {
            out.append(Character.forDigit((b >>> 4) & 15, 16));
            out.append(Character.forDigit(b & 15, 16));
        }
        return out.toString();
    }

    /**
     * The version and both fingerprints must be independently established.
     * An arbitrary changed file is NOT a confirmed patch. A hash mismatch
     * returns UNVERIFIED, never PATCHED. This method never writes files.
     */
    public static LicenseRecoverLicenseState compare(File artifact, String detectedVersion,
            String auditedVersion, String originalSha256, String patchedSha256) {
        if (detectedVersion == null || auditedVersion == null
                || !detectedVersion.equalsIgnoreCase(auditedVersion)
                || !isSha256(originalSha256) || !isSha256(patchedSha256)
                || originalSha256.equalsIgnoreCase(patchedSha256)) {
            return LicenseRecoverLicenseState.unverified("缺少版本匹配的已审核原版/补丁双指纹");
        }
        if (artifact == null) return LicenseRecoverLicenseState.unverified("目标文件不存在");
        Path path = artifact.toPath();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            return LicenseRecoverLicenseState.unverified("目标不是常规文件或为符号链接");
        try {
            String actual = digest(path);
            String version = auditedVersion.trim();
            if (actual.equals(originalSha256.toLowerCase(Locale.ROOT)))
                return LicenseRecoverLicenseState.codeComparison(false,
                        "已审核原版 SHA-256 精确匹配；版本 " + version + "；SHA-256 " + actual);
            if (actual.equals(patchedSha256.toLowerCase(Locale.ROOT)))
                return LicenseRecoverLicenseState.codeComparison(true,
                        "已审核补丁 SHA-256 精确匹配；版本 " + version + "；SHA-256 " + actual);
            return LicenseRecoverLicenseState.unverified(
                    "SHA-256 不匹配已审核原版或补丁，不能据此确认 Patch；版本 " + version);
        } catch (IOException | NoSuchAlgorithmException ex) {
            return LicenseRecoverLicenseState.scanError(
                    "只读 SHA-256 检测失败: " + ex.getClass().getSimpleName());
        }
    }
}
