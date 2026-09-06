import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .NET 新版本地授权适配器。
 *
 * 算法与 ITMC.Regedit.dll 的 RegeditMain.DoRegistry / DESEncrypt 保持一致：
 * - 注册申请号用 itmcsoft 解密，主机标识位于明文 [4,20)；
 * - 本地 regName 明文为 6 位随机数 + RegeditInfo JSON + 6 位随机数；
 * - 密钥为 *ITMC + ProName + OK*；
 * - DES/CBC/PKCS5Padding，Key/IV 均为 MD5(password) 大写十六进制前 8 字符的 ASCII；
 * - 最终密文输出大写 HEX；
 * - RegeditInfo 的日期、UserTimes、Net、CountDay 等字段按原 DoRegistry/Json.NET 结果序列化。
 *
 * 唯一有意改变的授权身份字段是 UserID=fwq；原 DoRegistry 不调用 set_UserID，生成值为 null。
 */
final class LicenseRecoverModernGUIDotNetLocalReg {
    static final String LOCAL_AUTH_USER_ID = "fwq";
    static final String BLOCK_ENDPOINT = "http://127.0.0.1:9/Service.asmx";
    static final String YX0302_REGSTR =
            "YX030201,YX030202,YX030203,YX030204,YX030210,YX030211,YX030212,YX030213";
    static final String BEGIN_DATE_JSON = "\\/Date(946656000000+0800)\\/";
    static final String END_DATE_JSON = "\\/Date(4102329600000+0800)\\/";

    private static final Pattern REG_BLOCK = Pattern.compile("(?is)<reg\\b[^>]*>.*?</reg\\s*>");
    private static final Pattern ROOT_CLOSE = Pattern.compile("(?is)</ROOT\\s*>");
    private static final Random RANDOM = new Random();

    private LicenseRecoverModernGUIDotNetLocalReg() { }

    static String decodeRequestRegId(String seq) throws Exception {
        if (seq == null || seq.trim().isEmpty()) throw new Exception("注册申请号为空");
        String plain = desDecrypt(seq.trim(), "itmcsoft");
        if (plain == null || plain.length() < 20) {
            throw new Exception("无法解密注册申请号或申请号长度不足");
        }
        String regId = plain.substring(4, 20);
        if (regId.trim().length() != 16) {
            throw new Exception("注册申请号中的主机标识不是 16 位: " + regId);
        }
        return regId;
    }

    static String buildRegName(String regId, String proName) throws Exception {
        String product = (proName == null || proName.trim().isEmpty()) ? "YX0302" : proName.trim();
        String regStr = product.toUpperCase().startsWith("YX0302") ? YX0302_REGSTR : product;
        String json = buildRegInfoJson(regId, product, regStr);
        String plain = sixDigits() + json + sixDigits();
        return desEncrypt(plain, "*ITMC" + product + "OK*");
    }

    static String buildRegInfoJson(String regId, String proName, String regStr) {
        return "{\"RegStr\":\"" + jsonEscape(regStr) + "\""
                + ",\"ClassNum\":-1"
                + ",\"RegID\":\"" + jsonEscape(regId) + "\""
                + ",\"UserID\":\"" + LOCAL_AUTH_USER_ID + "\""
                + ",\"ProName\":\"" + jsonEscape(proName) + "\""
                + ",\"BeginDate\":\"" + BEGIN_DATE_JSON + "\""
                + ",\"beginDate\":0"
                + ",\"endDate\":0"
                + ",\"EndDate\":\"" + END_DATE_JSON + "\""
                + ",\"TotalTimes\":-1"
                + ",\"UserTimes\":1"
                + ",\"Net\":true"
                + ",\"MaxCon\":-1"
                + ",\"CountDay\":10}";
    }

    static OperationResult writeLocalLicense(AppInfo info, String regName, boolean backup,
                                             Consumer<String> log) {
        Consumer<String> sink = log == null ? s -> { } : log;
        try {
            List<File> configs = configCandidates(info);
            if (configs.isEmpty()) {
                return OperationResult.failed("未找到 .NET config.xml", 4);
            }
            String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            for (File cfg : configs) {
                if (backup) {
                    File bak = new File(cfg.getParentFile(), cfg.getName() + ".prewrite." + stamp + ".bak");
                    SafetyBackup.requireCopy(cfg.toPath(), bak.toPath(), sink);
                }
                String original = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
                boolean bom = original.startsWith("\uFEFF");
                String body = bom ? original.substring(1) : original;
                String updated = upsertLicense(body, regName);
                validateXml(updated);
                if (bom) updated = "\uFEFF" + updated;
                Files.write(cfg.toPath(), updated.getBytes(StandardCharsets.UTF_8));
                sink.accept("[一键恢复/.NET] 已写入本地授权: " + cfg.getAbsolutePath() + "\n");
            }
            return OperationResult.success(".NET 本地 regName 已写入");
        } catch (Exception ex) {
            sink.accept("[错误] 写入 .NET 本地授权失败: " + ex.getMessage() + "\n");
            return OperationResult.failed(ex.getMessage(), 4);
        }
    }

    static String upsertLicense(String xml, String regName) throws Exception {
        if (xml == null) throw new Exception("config.xml 内容为空");
        String lineSep = xml.contains("\r\n") ? "\r\n" : "\n";
        Matcher matcher = REG_BLOCK.matcher(xml);
        List<int[]> ranges = new ArrayList<int[]>();
        List<String> blocks = new ArrayList<String>();
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
            blocks.add(matcher.group());
        }

        if (blocks.isEmpty()) {
            Matcher root = ROOT_CLOSE.matcher(xml);
            if (!root.find()) throw new Exception("config.xml 缺少 ROOT/reg 节点");
            String block = "  <reg>" + lineSep
                    + "    <regType>1</regType>" + lineSep
                    + "    <regName>" + regName + "</regName>" + lineSep
                    + "    <WebSerUserID>itmc</WebSerUserID>" + lineSep
                    + "    <Service>" + BLOCK_ENDPOINT + "</Service>" + lineSep
                    + "  </reg>" + lineSep;
            return xml.substring(0, root.start()) + block + xml.substring(root.start());
        }

        StringBuilder out = new StringBuilder(xml.length() + regName.length() + 256);
        int cursor = 0;
        for (int i = 0; i < blocks.size(); i++) {
            int[] range = ranges.get(i);
            out.append(xml, cursor, range[0]);
            String block = blocks.get(i);
            if (i == 0) {
                block = upsertElement(block, "regType", "1", lineSep);
                block = upsertElement(block, "regName", regName, lineSep);
                block = upsertElement(block, "WebSerUserID", "itmc", lineSep);
                block = upsertElement(block, "Service", BLOCK_ENDPOINT, lineSep);
            } else {
                block = replaceExistingElement(block, "regType", "1");
                block = replaceExistingElement(block, "regName", regName);
                block = replaceExistingElement(block, "WebSerUserID", "itmc");
                block = replaceExistingElement(block, "Service", BLOCK_ENDPOINT);
            }
            out.append(block);
            cursor = range[1];
        }
        out.append(xml.substring(cursor));
        return out.toString();
    }

    private static String upsertElement(String block, String name, String value, String lineSep) {
        String replaced = replaceExistingElement(block, name, value);
        if (!replaced.equals(block)) return replaced;
        Pattern close = Pattern.compile("(?is)</reg\\s*>");
        Matcher m = close.matcher(block);
        if (!m.find()) return block;
        String insertion = "  <" + name + ">" + value + "</" + name + ">" + lineSep;
        return block.substring(0, m.start()) + insertion + block.substring(m.start());
    }

    private static String replaceExistingElement(String block, String name, String value) {
        Pattern p = Pattern.compile("(?is)(<" + Pattern.quote(name)
                + "\\b[^>]*>)(.*?)(</" + Pattern.quote(name) + "\\s*>)");
        Matcher m = p.matcher(block);
        if (!m.find()) return block;
        return block.substring(0, m.start()) + m.group(1) + value + m.group(3) + block.substring(m.end());
    }

    private static List<File> configCandidates(AppInfo info) throws Exception {
        Set<String> seen = new LinkedHashSet<String>();
        List<File> result = new ArrayList<File>();
        if (info != null && info.binDir != null) addConfig(result, seen, new File(info.binDir, "config.xml"));
        if (info != null && info.appRoot != null) addConfig(result, seen, new File(info.appRoot, "config.xml"));
        return result;
    }

    private static void addConfig(List<File> result, Set<String> seen, File file) throws Exception {
        if (file == null || !file.isFile()) return;
        String key = file.getCanonicalPath();
        if (seen.add(key)) result.add(file);
    }

    private static void validateXml(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignore) { }
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignore) { }
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignore) { }
        f.setExpandEntityReferences(false);
        f.setXIncludeAware(false);
        f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    static String desEncrypt(String plain, String password) throws Exception {
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
        byte[] key = passwordKey(password);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DES"), new IvParameterSpec(key));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(encrypted.length * 2);
        for (byte b : encrypted) hex.append(String.format("%02X", b & 0xff));
        return hex.toString();
    }

    static String desDecrypt(String cipherHex, String password) throws Exception {
        if ((cipherHex.length() & 1) != 0) throw new Exception("HEX 密文长度必须为偶数");
        byte[] encrypted = new byte[cipherHex.length() / 2];
        for (int i = 0; i < encrypted.length; i++) {
            encrypted[i] = (byte) Integer.parseInt(cipherHex.substring(i * 2, i * 2 + 2), 16);
        }
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
        byte[] key = passwordKey(password);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "DES"), new IvParameterSpec(key));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static byte[] passwordKey(String password) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(password.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(32);
        for (byte b : digest) hex.append(String.format("%02X", b & 0xff));
        return hex.substring(0, 8).getBytes(StandardCharsets.US_ASCII);
    }

    private static String sixDigits() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
