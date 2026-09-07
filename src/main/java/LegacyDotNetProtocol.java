/*
 * DS01xx .NET 旧版授权协议适配。
 *
 * 这类 ASP.NET 应用没有使用新版 JSON 授权串：申请号使用 itmcsoft，
 * 离线授权码使用应用自身的动态密钥（"itmc" + ProName），提交成功后由应用
 * 自身用 *b2bOK* 写回本地授权。DS0101 的 ProName 是 itmcIEC，因此密钥为
 * itmcitmcIEC。
 * 这里仅负责按目标程序集的格式生成和校验授权码，不写入目标应用配置。
 */
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** DS01xx（同一 funpublic 协议族）使用的旧版 .NET 授权格式。 */
public final class LegacyDotNetProtocol {
    public static final String REQUEST_KEY = "itmcsoft";
    public static final String LOCAL_KEY = "*b2bOK*";
    public static final String CODE_KEY_PREFIX = "itmc";

    /** DS0101、DS0102 等 DS01xx 系列使用旧版 itmcIEC 协议；不要把新版 YX03xx 混入。 */
    private static final Pattern LEGACY_VERSION = Pattern.compile("(?i)^DS01\\d{2}$");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");
    private static final Pattern SOFT_VERSION = Pattern.compile(
            "(?is)<SoftVersionID\\b[^>]*>\\s*([^<]+?)\\s*</SoftVersionID\\s*>");

    private LegacyDotNetProtocol() { }

    /** DS01xx 注册页使用的授权码密钥：固定前缀 itmc + 应用 ProName。 */
    public static String codeKeyForProduct(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            throw new IllegalArgumentException("旧协议产品标识不能为空");
        }
        return CODE_KEY_PREFIX + productName.trim();
    }

    /** 根据 SoftVersionID 判断是否属于 DS01xx 旧版 itmcIEC 协议。 */
    public static boolean isLegacyVersion(String softVersion) {
        return softVersion != null && LEGACY_VERSION.matcher(softVersion.trim()).matches();
    }

    /** 从 .NET 应用根目录或 bin 目录的 config.xml 读取 SystemSoft/SoftVersionID。 */
    /** Resolve legacy ProName from the selected application's own ITMC.Web.dll. */
    public static String readProductName(File binOrRoot) {
        if (binOrRoot == null) return null;
        File dir = binOrRoot.getAbsoluteFile();
        if (!dir.isDirectory()) dir = dir.getParentFile();
        if (dir == null) return null;
        File bin = "bin".equalsIgnoreCase(dir.getName()) ? dir : new File(dir, "bin");
        File web = new File(bin, "ITMC.Web.dll");
        if (!web.isFile() && new File(dir, "ITMC.Web.dll").isFile()) web = new File(dir, "ITMC.Web.dll");
        String version = readSoftVersion(binOrRoot);
        return LicenseRecoverModernGUIAutoRecovery.detectProduct(web, version);
    }

    public static String readSoftVersion(File binOrRoot) {
        if (binOrRoot == null) return null;
        File dir = binOrRoot.getAbsoluteFile();
        if (!dir.isDirectory()) dir = dir.getParentFile();
        if (dir == null) return null;
        File root = "bin".equalsIgnoreCase(dir.getName()) && dir.getParentFile() != null
                ? dir.getParentFile() : dir;
        File[] candidates = new File[]{
                new File(root, "config.xml"),
                new File(dir, "config.xml")
        };
        for (File cfg : candidates) {
            if (!cfg.isFile()) continue;
            try {
                String xml = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
                if (xml.startsWith("\uFEFF")) xml = xml.substring(1);
                Matcher m = SOFT_VERSION.matcher(xml);
                if (m.find()) return m.group(1).trim();
            } catch (Exception ignore) { }
        }
        return null;
    }

    /** 判断给定 .NET bin/根目录是否应走旧协议适配。 */
    public static boolean isLegacyTarget(File binOrRoot) {
        return isLegacyVersion(readSoftVersion(binOrRoot));
    }

    /** 与 .NET DESEncrypt 兼容：MD5(key) 大写结果的前 8 个字符作为 DES 的 Key 和 IV。 */
    public static String encrypt(String password, String plaintext) throws Exception {
        if (plaintext == null) throw new IllegalArgumentException("明文不能为空");
        byte[] bytes = crypt(password, plaintext.getBytes(StandardCharsets.UTF_8), Cipher.ENCRYPT_MODE);
        return toHex(bytes);
    }

    /** 与 .NET DESEncrypt 兼容的十六进制密文解密。 */
    public static String decrypt(String password, String ciphertext) throws Exception {
        if (ciphertext == null || ciphertext.trim().isEmpty()) {
            throw new IllegalArgumentException("密文不能为空");
        }
        String value = ciphertext.trim();
        if ((value.length() & 1) != 0 || !HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("授权密文必须是偶数长度的十六进制字符串");
        }
        byte[] bytes = crypt(password, fromHex(value), Cipher.DECRYPT_MODE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 解码应用本地注册页产生的申请号。
     * 旧版格式中主机码位于 [4,20)，申请时间位于 [24,43)。
     */
    public static SequenceInfo decodeSequence(String sequence) throws Exception {
        String plain = decrypt(REQUEST_KEY, sequence);
        if (plain.length() < 43) {
            throw new IllegalArgumentException("旧协议申请号明文长度不足，必须使用 DS01xx 注册页生成的申请号");
        }
        String regId = plain.substring(4, 20);
        String requestTime = plain.substring(24, 43);
        validateRequestTime(requestTime);
        return new SequenceInfo(sequence.trim(), plain, regId, requestTime);
    }

    /**
     * 基于注册页取得的申请号生成旧协议离线授权码。
     * 申请号所属主机码和申请时间会原样带入授权码，保证跨机器场景仍绑定服务器。
     *
     * 旧版程序集同时使用两个不同概念：授权码解密密钥固定由产品标识
     * ``itmcIEC`` 组成，而授权内容末尾必须写入应用的 SoftVersionID（例如
     * ``DS0101``）。两者不能混用，否则注册页虽然会提示成功，后续
     * funpublic.CheckReg 仍会把本地注册判定为无效。
     */
    public static CodeResult generateAuthorizationCode(String sequence, String softVersionId, String productName) throws Exception {
        SequenceInfo request = decodeSequence(sequence);
        String versionId = normalizeVersionId(softVersionId);
        String endDate = "2099-12-31";
        // 旧协议固定字段布局：
        // [2,21)申请时间 [23,39)主机码 [41,51)截止日期 [53]联网标志
        // [56,60)并发数 [62,64)班级数 [66,...)版本标识。
        String plaintext = "00" + request.requestTime
                + "00" + request.regId
                + "00" + endDate
                + "00" + "1"
                + "00" + "-001"
                + "00" + "-1"
                + "00" + versionId;
        String code = encrypt(codeKeyForProduct(productName), plaintext);
        AuthorizationInfo parsed = decodeAuthorizationCode(code, productName);
        if (!request.regId.equals(parsed.regId)
                || !request.requestTime.equals(parsed.requestTime)
                || !versionId.equals(parsed.product)) {
            throw new IllegalStateException("旧协议授权码自校验失败：申请号绑定字段不一致");
        }
        return new CodeResult(code, plaintext, request, parsed, endDate);
    }

    private static String normalizeVersionId(String softVersionId) {
        if (softVersionId == null || softVersionId.trim().isEmpty()) {
            throw new IllegalArgumentException("旧协议应用缺少 SoftVersionID");
        }
        String value = softVersionId.trim();
        if (!isLegacyVersion(value)) {
            throw new IllegalArgumentException("不是 DS01xx 旧协议版本: " + value);
        }
        return value;
    }

    /** 解码并检查旧协议授权码的固定字段，便于生成后做本地格式校验。 */
    public static AuthorizationInfo decodeAuthorizationCode(String code, String productName) throws Exception {
        String plain = decrypt(codeKeyForProduct(productName), code);
        if (plain.length() < 66) {
            throw new IllegalArgumentException("旧协议授权码明文长度不足");
        }
        String requestTime = plain.substring(2, 21);
        String regId = plain.substring(23, 39);
        String endDate = plain.substring(41, 51);
        String net = plain.substring(53, 54);
        String maxCon = plain.substring(56, 60);
        String classNum = plain.substring(62, 64);
        validateRequestTime(requestTime);
        if (!isDate(endDate)) throw new IllegalArgumentException("旧协议授权码截止日期格式无效");
        return new AuthorizationInfo(plain, requestTime, regId, endDate, net, maxCon, classNum,
                plain.length() > 66 ? plain.substring(66) : "");
    }

    private static void validateRequestTime(String value) {
        if (value == null || value.length() != 19 || !isDateTime(value)) {
            throw new IllegalArgumentException("申请号中的申请时间格式无效，应为 yyyy-MM-dd HH-mm-ss");
        }
    }

    private static boolean isDateTime(String value) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT);
        f.setLenient(false);
        ParsePosition p = new ParsePosition(0);
        return f.parse(value, p) != null && p.getIndex() == value.length();
    }

    private static boolean isDate(String value) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setLenient(false);
        ParsePosition p = new ParsePosition(0);
        return f.parse(value, p) != null && p.getIndex() == value.length();
    }

    private static byte[] crypt(String password, byte[] input, int mode) throws Exception {
        byte[] key = deriveKey(password);
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "DES");
        cipher.init(mode, keySpec, new IvParameterSpec(key));
        return cipher.doFinal(input);
    }

    private static byte[] deriveKey(String password) throws Exception {
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("DES 密钥不能为空");
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest(password.getBytes(StandardCharsets.UTF_8));
        StringBuilder md5 = new StringBuilder(32);
        for (byte b : digest) {
            int v = b & 0xff;
            md5.append(Character.toUpperCase(Character.forDigit(v >>> 4, 16)));
            md5.append(Character.toUpperCase(Character.forDigit(v & 0x0f, 16)));
        }
        return md5.substring(0, 8).getBytes(StandardCharsets.US_ASCII);
    }

    private static String toHex(byte[] bytes) {
        final char[] digits = "0123456789ABCDEF".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = digits[v >>> 4];
            out[i * 2 + 1] = digits[v & 0x0f];
        }
        return new String(out);
    }

    private static byte[] fromHex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(value.charAt(i * 2), 16);
            int lo = Character.digit(value.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static final class SequenceInfo {
        public final String ciphertext;
        public final String plaintext;
        public final String regId;
        public final String requestTime;

        SequenceInfo(String ciphertext, String plaintext, String regId, String requestTime) {
            this.ciphertext = ciphertext;
            this.plaintext = plaintext;
            this.regId = regId;
            this.requestTime = requestTime;
        }
    }

    public static final class AuthorizationInfo {
        public final String plaintext;
        public final String requestTime;
        public final String regId;
        public final String endDate;
        public final String net;
        public final String maxCon;
        public final String classNum;
        public final String product;

        AuthorizationInfo(String plaintext, String requestTime, String regId, String endDate,
                          String net, String maxCon, String classNum, String product) {
            this.plaintext = plaintext;
            this.requestTime = requestTime;
            this.regId = regId;
            this.endDate = endDate;
            this.net = net;
            this.maxCon = maxCon;
            this.classNum = classNum;
            this.product = product;
        }
    }

    public static final class CodeResult {
        public final String code;
        public final String plaintext;
        public final SequenceInfo request;
        public final AuthorizationInfo authorization;
        public final String endDate;

        CodeResult(String code, String plaintext, SequenceInfo request,
                   AuthorizationInfo authorization, String endDate) {
            this.code = code;
            this.plaintext = plaintext;
            this.request = request;
            this.authorization = authorization;
            this.endDate = endDate;
        }
    }
}
