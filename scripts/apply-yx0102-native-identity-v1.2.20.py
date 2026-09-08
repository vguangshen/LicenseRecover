#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/LicenseRecoverModernGUIAutoRecovery.java"
TEST = ROOT / "src/test/java/RefactorSmokeTest.java"


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"anchor not found: {label}")
    if text.count(old) != 1:
        raise SystemExit(f"anchor not unique: {label} ({text.count(old)})")
    return text.replace(old, new, 1)


src = SRC.read_text(encoding="utf-8")

src = replace_once(src,
'''            String version = readVersion(root, bin);\n            String product = detectProduct(new File(bin,"ITMC.Web.dll"), version);''',
'''            String version = readVersion(root, bin);\n            String configuredProduct = detectConfiguredDotNetProduct(root, bin, version);\n            String product = !blank(configuredProduct)\n                    ? configuredProduct : detectProduct(new File(bin,"ITMC.Web.dll"), version);''',
"detect configured .NET product")

insert_anchor = '''    static String detectProduct(File webDll,String version) {'''
insert_block = r'''    /**
     * Resolve the direct .NET registration ProName from this application's own
     * Web.config.  The configuration value is executable evidence only when the
     * same target ITMC.Web.dll also contains that product token and the native
     * local-registration/check flow.  Folder names and VersionID prefixes are
     * deliberately not used as proof here.
     */
    static String detectConfiguredDotNetProduct(File root, File bin, String version) {
        if (root == null || bin == null || blank(version)) return null;
        File webDll = new File(bin, "ITMC.Web.dll");
        File webConfig = new File(root, "Web.config");
        String product = readAppSetting(webConfig, "productName");
        if (blank(product) || !product.matches("(?i)[A-Z0-9][A-Z0-9._-]{1,63}")) return null;
        if (!targetContainsAll(webDll, product, "RegeditNew", "NewRegistry", "DoRegistry",
                "ProName", "SoftVersionID", "GetProVersion", "CheckSoftVersionID")) return null;
        return product.trim();
    }

    private static String readAppSetting(File webConfig, String wantedKey) {
        if (webConfig == null || !webConfig.isFile() || blank(wantedKey)) return null;
        try {
            String xml = readUtf8(webConfig);
            Matcher add = Pattern.compile("(?is)<add\\b([^>]*)>").matcher(xml);
            while (add.find()) {
                String attrs = add.group(1);
                String key = xmlAttribute(attrs, "key");
                if (!wantedKey.equalsIgnoreCase(key)) continue;
                String value = xmlAttribute(attrs, "value");
                return blank(value) ? null : unxml(value.trim());
            }
        } catch (Throwable ignore) { }
        return null;
    }

    private static String xmlAttribute(String attrs, String name) {
        if (attrs == null || name == null) return null;
        Matcher m = Pattern.compile("(?is)\\b" + Pattern.quote(name)
                + "\\s*=\\s*([\\\"'])(.*?)\\1").matcher(attrs);
        return m.find() ? m.group(2) : null;
    }

    /**
     * Direct local-registration identity used by Regester.Page_Load/Button1_Click:
     * configured ProName + current config.xml SoftVersionID.  This is kept separate
     * from RegisterVersion.db, whose parent product/version rows are compatibility
     * aliases consumed by GetProVersion()/CheckReg rather than the direct page identity.
     */
    static String detectDirectDotNetRegStr(Detection d) {
        if (d == null || d.appRoot == null || d.runtimeDir == null
                || blank(d.productName) || blank(d.versionId)) return null;
        String configured = detectConfiguredDotNetProduct(d.appRoot, d.runtimeDir, d.versionId);
        if (blank(configured) || !configured.equalsIgnoreCase(d.productName.trim())) return null;
        String current = readVersion(d.appRoot, d.runtimeDir);
        if (blank(current) || !current.trim().equalsIgnoreCase(d.versionId.trim())) return null;
        String token = d.versionId.trim();
        return token.matches("(?i)[A-Z0-9][A-Z0-9._-]{1,63}") ? token.toUpperCase(Locale.ROOT) : null;
    }

    static final class RegisterVersionEvidence {
        final String versionId;
        final LinkedHashMap<String, List<String>> compatibility;
        final int relationCount;

        RegisterVersionEvidence(String versionId, LinkedHashMap<String, LinkedHashSet<String>> rows) {
            this.versionId = versionId;
            this.compatibility = new LinkedHashMap<String, List<String>>();
            int count = 0;
            for (Map.Entry<String, LinkedHashSet<String>> e : rows.entrySet()) {
                ArrayList<String> values = new ArrayList<String>(e.getValue());
                this.compatibility.put(e.getKey(), Collections.unmodifiableList(values));
                count += values.size();
            }
            this.relationCount = count;
        }

        String summary() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, List<String>> e : compatibility.entrySet()) {
                if (b.length() > 0) b.append("; ");
                b.append(e.getKey()).append(" -> ");
                for (int i = 0; i < e.getValue().size(); i++) {
                    if (i > 0) b.append(',');
                    b.append(e.getValue().get(i));
                }
            }
            return b.toString();
        }
    }

    private static final class EncryptedRegisterVersionRow {
        final String versionId, parentProductId, parentVersionId;
        EncryptedRegisterVersionRow(String v, String p, String pv) {
            versionId = v; parentProductId = p; parentVersionId = pv;
        }
    }

    /**
     * Parse target RegisterVersion.db read-only.  The TripleDES key is never fixed in
     * LicenseRecover: 24-byte candidates are collected from this target ITMC.Web.dll
     * and accepted only when exactly one candidate decrypts rows whose versionID is
     * the current target SoftVersionID and whose parent fields are valid product tokens.
     */
    static RegisterVersionEvidence readRegisterVersionEvidence(Detection d) {
        if (d == null || d.appRoot == null || d.runtimeDir == null || blank(d.versionId)) return null;
        File db = new File(d.appRoot, "RegisterVersion.db");
        File webDll = new File(d.runtimeDir, "ITMC.Web.dll");
        if (!db.isFile() || !webDll.isFile()) return null;
        if (!targetContainsAll(webDll, "Encrypt3Des", "Decrypt3Des", "GetRegisterVersionList",
                "GetProVersion", "CheckSoftVersionID")) return null;
        try {
            List<EncryptedRegisterVersionRow> encryptedRows = scanRegisterVersionRows(
                    Files.readAllBytes(db.toPath()));
            if (encryptedRows.isEmpty()) return null;

            LinkedHashSet<String> keyCandidates = new LinkedHashSet<String>();
            for (String text : extractUtf16Ascii(webDll)) {
                if (text == null || text.length() != 24) continue;
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (bytes.length != 24) continue;
                boolean printable = true;
                for (byte value : bytes) {
                    int c = value & 255;
                    if (c < 32 || c > 126) { printable = false; break; }
                }
                if (printable) keyCandidates.add(text);
            }

            RegisterVersionEvidence winner = null;
            int winnerCount = 0;
            for (String key : keyCandidates) {
                LinkedHashMap<String, LinkedHashSet<String>> matches =
                        new LinkedHashMap<String, LinkedHashSet<String>>();
                for (EncryptedRegisterVersionRow row : encryptedRows) {
                    String version = decryptRegisterVersionToken(row.versionId, key);
                    if (version == null || !version.equalsIgnoreCase(d.versionId.trim())) continue;
                    String parentProduct = decryptRegisterVersionToken(row.parentProductId, key);
                    String parentVersion = decryptRegisterVersionToken(row.parentVersionId, key);
                    if (!validRegistrationToken(parentProduct) || !validRegistrationToken(parentVersion)) continue;
                    LinkedHashSet<String> versions = matches.get(parentProduct);
                    if (versions == null) {
                        versions = new LinkedHashSet<String>();
                        matches.put(parentProduct, versions);
                    }
                    versions.add(parentVersion);
                }
                if (!matches.isEmpty()) {
                    winner = new RegisterVersionEvidence(d.versionId.trim(), matches);
                    winnerCount++;
                    if (winnerCount > 1) return null; // ambiguous target-owned key evidence
                }
            }
            return winnerCount == 1 ? winner : null;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static boolean validRegistrationToken(String value) {
        return value != null && value.matches("(?i)[A-Z0-9][A-Z0-9._-]{1,63}");
    }

    private static String decryptRegisterVersionToken(String encrypted, String key) {
        try {
            if (blank(encrypted) || key == null || key.getBytes(StandardCharsets.UTF_8).length != 24) return null;
            Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "DESede"));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encrypted.trim()));
            String value = new String(plain, StandardCharsets.UTF_8).trim();
            return validRegistrationToken(value) ? value : null;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * LiteDB stores these relation documents as BSON-style string fields.  We scan
     * only the three named fields and require their BSON string type/length framing;
     * crypto + current VersionID validation below prevents unrelated byte matches
     * from becoming executable evidence.
     */
    private static List<EncryptedRegisterVersionRow> scanRegisterVersionRows(byte[] data) {
        ArrayList<EncryptedRegisterVersionRow> out = new ArrayList<EncryptedRegisterVersionRow>();
        if (data == null || data.length < 32) return out;
        byte[] versionField = "versionID\0".getBytes(StandardCharsets.US_ASCII);
        byte[] productField = "parentProID\0".getBytes(StandardCharsets.US_ASCII);
        byte[] parentVersionField = "parentVersionID\0".getBytes(StandardCharsets.US_ASCII);
        HashSet<String> seen = new HashSet<String>();
        int from = 0;
        while (from < data.length) {
            int versionPos = indexOfBytes(data, versionField, from, data.length);
            if (versionPos < 0) break;
            int nextVersion = indexOfBytes(data, versionField, versionPos + versionField.length, data.length);
            int limit = Math.min(data.length, versionPos + 768);
            if (nextVersion >= 0) limit = Math.min(limit, nextVersion);
            String version = readBsonStringField(data, versionPos, versionField.length, limit);
            int productPos = indexOfBytes(data, productField, versionPos + versionField.length, limit);
            int parentVersionPos = indexOfBytes(data, parentVersionField,
                    productPos < 0 ? versionPos + versionField.length : productPos + productField.length, limit);
            String product = productPos < 0 ? null
                    : readBsonStringField(data, productPos, productField.length, limit);
            String parentVersion = parentVersionPos < 0 ? null
                    : readBsonStringField(data, parentVersionPos, parentVersionField.length, limit);
            if (!blank(version) && !blank(product) && !blank(parentVersion)) {
                String identity = version + "\u0000" + product + "\u0000" + parentVersion;
                if (seen.add(identity)) out.add(new EncryptedRegisterVersionRow(version, product, parentVersion));
            }
            from = versionPos + versionField.length;
        }
        return out;
    }

    private static String readBsonStringField(byte[] data, int namePos, int nameLength, int limit) {
        try {
            if (namePos <= 0 || (data[namePos - 1] & 255) != 0x02) return null;
            int lengthPos = namePos + nameLength;
            if (lengthPos + 4 > limit) return null;
            int length = le32(data, lengthPos);
            if (length < 2 || length > 1024) return null;
            int valuePos = lengthPos + 4;
            if (valuePos + length > limit || data[valuePos + length - 1] != 0) return null;
            return new String(data, valuePos, length - 1, StandardCharsets.UTF_8).trim();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static int le32(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) return -1;
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16) | ((data[offset + 3] & 255) << 24);
    }

    private static int indexOfBytes(byte[] data, byte[] needle, int start, int limit) {
        if (data == null || needle == null || needle.length == 0) return -1;
        int last = Math.min(data.length, limit) - needle.length;
        outer: for (int i = Math.max(0, start); i <= last; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static boolean targetContainsAll(File file, String... tokens) {
        if (file == null || !file.isFile() || tokens == null) return false;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            for (String token : tokens) {
                if (blank(token)) return false;
                byte[] ascii = token.getBytes(StandardCharsets.US_ASCII);
                byte[] utf16 = token.getBytes(StandardCharsets.UTF_16LE);
                if (indexOfBytes(data, ascii, 0, data.length) < 0
                        && indexOfBytes(data, utf16, 0, data.length) < 0) return false;
            }
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

'''
src = replace_once(src, insert_anchor, insert_block + insert_anchor, "insert YX0102 target identity resolver")

old_reg = '''    static String detectDotNetRegStr(Detection d) {\n        if (d == null || blank(d.productName) || d.runtimeDir == null) return null;\n        String local = recoverDotNetLocalRegStr(d.appRoot, d.runtimeDir, d.productName);\n        if (!blank(local)) return local;\n        return detectProductList(new File(d.runtimeDir, "ITMC.Web.dll"), d.productName);\n    }'''
new_reg = '''    static String detectDotNetRegStr(Detection d) {\n        if (d == null || blank(d.productName) || d.runtimeDir == null) return null;\n        String direct = detectDirectDotNetRegStr(d);\n        if (!blank(direct)) return direct;\n        String local = recoverDotNetLocalRegStr(d.appRoot, d.runtimeDir, d.productName);\n        if (!blank(local)) return local;\n        return detectProductList(new File(d.runtimeDir, "ITMC.Web.dll"), d.productName);\n    }'''
src = replace_once(src, old_reg, new_reg, "prefer target direct .NET RegStr")

old_log = '''        if (blank(regStr))\n            return Result.fail("Target ITMC.Web.dll did not prove RegStr products; VersionID/default-list fallback is disabled.", d);\n\n        List<String> generate = new ArrayList<String>();'''
new_log = '''        if (blank(regStr))\n            return Result.fail("Target application directory did not prove RegStr products; VersionID/default-list fallback is disabled.", d);\n\n        RegisterVersionEvidence compatibility = readRegisterVersionEvidence(d);\n        if (compatibility != null && compatibility.relationCount > 0)\n            log.accept("[one-click] target RegisterVersion.db compatibility: " + compatibility.summary() + "\\n");\n\n        List<String> generate = new ArrayList<String>();'''
src = replace_once(src, old_log, new_log, "log target RegisterVersion compatibility")

SRC.write_text(src, encoding="utf-8", newline="\n")


test = TEST.read_text(encoding="utf-8")
test = replace_once(test,
'''import java.io.BufferedOutputStream;\nimport java.io.File;\nimport java.io.IOException;''',
'''import javax.crypto.Cipher;\nimport javax.crypto.spec.SecretKeySpec;\nimport java.io.BufferedOutputStream;\nimport java.io.ByteArrayOutputStream;\nimport java.io.File;\nimport java.io.IOException;''',
"test crypto imports")
test = replace_once(test,
'''import java.util.Arrays;\nimport java.util.List;''',
'''import java.util.Arrays;\nimport java.util.Base64;\nimport java.util.List;''',
"test Base64 import")

old_test = r'''        Path yx0102Root = base.resolve("dotnet-YX0102");
        Path yx0102Bin = yx0102Root.resolve("bin");
        Files.createDirectories(yx0102Bin);
        writeUtf16Fixture(yx0102Bin.resolve("ITMC.Web.dll"), "YX0102", "ProName", "RegStr");
        Files.write(yx0102Bin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        String yx0102Plain = "877842{\"RegStr\":\"YX0102\",\"RegID\":\"F000606A59904719\",\"UserID\":\"fwq\",\"ProName\":\"YX0102\"}708027";
        String yx0102Cipher = LicenseRecoverModernGUIAutoRecovery.desEncryptHex(
                yx0102Plain, "*ITMCYX0102OK*");
        Files.write(yx0102Root.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>1</regType><regName>" + yx0102Cipher + "</regName></reg>",
                "<SystemSoft><SoftVersionID>YX0102</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        LicenseRecoverModernGUIAutoRecovery.Detection yx0102Detection =
                LicenseRecoverModernGUIAutoRecovery.detect(yx0102Root.toFile());
        check("YX0102".equals(yx0102Detection.productName)
                        && "YX0102".equals(LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102Detection)),
                "YX0102 recovers RegStr from target-local regName only after ProName-key verification");
        LicenseRecoverModernGUIAutoRecovery.Detection yx0102WrongProduct =
                new LicenseRecoverModernGUIAutoRecovery.Detection(
                        LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN,
                        yx0102Root.toFile(), yx0102Root.toFile(), yx0102Bin.toFile(),
                        "YX0102", "YX0103");
        String yx0102WrongRegStr = LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102WrongProduct);
        check(yx0102WrongRegStr == null || yx0102WrongRegStr.trim().isEmpty(),
                "target-local .NET regName is rejected when its cryptographic ProName does not match");
'''
new_test = r'''        Path yx0102Root = base.resolve("dotnet-YX0102");
        Path yx0102Bin = yx0102Root.resolve("bin");
        Files.createDirectories(yx0102Bin);
        String yx0102DbKey = "ABCDEFGHijklmnop12345678";
        writeUtf16Fixture(yx0102Bin.resolve("ITMC.Web.dll"),
                "YS01", "RegeditNew", "NewRegistry", "DoRegistry", "ProName", "SoftVersionID",
                "GetProVersion", "CheckSoftVersionID", "Encrypt3Des", "Decrypt3Des",
                "GetRegisterVersionList", yx0102DbKey);
        Files.write(yx0102Bin.resolve("ITMC.Regedit.dll"), new byte[]{1});
        Files.write(yx0102Root.resolve("Web.config"), Arrays.asList(
                "<configuration><appSettings><add key=\"productName\" value=\"YS01\" />",
                "</appSettings></configuration>"), StandardCharsets.UTF_8);
        Files.write(yx0102Root.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>3</regType></reg>",
                "<SystemSoft><SoftVersionID>YX0102</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        writeRegisterVersionFixture(yx0102Root.resolve("RegisterVersion.db"), yx0102DbKey,
                "YX0102", "qt1001", "QT100106", "QT100110");

        LicenseRecoverModernGUIAutoRecovery.Detection yx0102Detection =
                LicenseRecoverModernGUIAutoRecovery.detect(yx0102Root.toFile());
        check("YS01".equals(yx0102Detection.productName),
                "YX0102 direct ProName comes from target Web.config and is proven by target ITMC.Web.dll");
        check("YX0102".equals(LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102Detection)),
                "YX0102 direct RegStr is current target SoftVersionID, matching the native registration-page flow");
        LicenseRecoverModernGUIAutoRecovery.RegisterVersionEvidence yx0102Compatibility =
                LicenseRecoverModernGUIAutoRecovery.readRegisterVersionEvidence(yx0102Detection);
        check(yx0102Compatibility != null
                        && yx0102Compatibility.compatibility.containsKey("qt1001")
                        && yx0102Compatibility.compatibility.get("qt1001").equals(
                                Arrays.asList("QT100106", "QT100110")),
                "YX0102 RegisterVersion.db compatibility rows are decrypted with a key extracted from target DLL");

        Files.write(yx0102Root.resolve("Web.config"), Arrays.asList(
                "<configuration><appSettings><add key=\"productName\" value=\"BAD01\" />",
                "</appSettings></configuration>"), StandardCharsets.UTF_8);
        check(LicenseRecoverModernGUIAutoRecovery.detectConfiguredDotNetProduct(
                        yx0102Root.toFile(), yx0102Bin.toFile(), "YX0102") == null,
                "configured .NET ProName is rejected when target DLL does not contain the same identity");
        Files.write(yx0102Root.resolve("Web.config"), Arrays.asList(
                "<configuration><appSettings><add key=\"productName\" value=\"YS01\" />",
                "</appSettings></configuration>"), StandardCharsets.UTF_8);

        writeUtf16Fixture(yx0102Bin.resolve("ITMC.Web.dll"),
                "YS01", "RegeditNew", "NewRegistry", "DoRegistry", "ProName", "SoftVersionID",
                "GetProVersion", "CheckSoftVersionID", "Encrypt3Des", "Decrypt3Des",
                "GetRegisterVersionList");
        LicenseRecoverModernGUIAutoRecovery.Detection yx0102NoKeyDetection =
                LicenseRecoverModernGUIAutoRecovery.detect(yx0102Root.toFile());
        check("YS01".equals(yx0102NoKeyDetection.productName)
                        && "YX0102".equals(LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102NoKeyDetection)),
                "direct YS01/YX0102 native identity does not depend on compatibility database aliases");
        check(LicenseRecoverModernGUIAutoRecovery.readRegisterVersionEvidence(yx0102NoKeyDetection) == null,
                "RegisterVersion.db compatibility evidence fails closed when its key is not proven by target DLL");
'''
test = replace_once(test, old_test, new_test, "replace synthetic YX0102 regression")

helper_anchor = '''    private static void writeUtf16Fixture(Path path, String... values) throws IOException {'''
helper_block = r'''    private static void writeRegisterVersionFixture(Path path, String key, String version,
                                                    String parentProduct, String... parentVersions)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String parentVersion : parentVersions) {
            writeBsonString(out, "versionID", tripleDesEncrypt(version, key));
            writeBsonString(out, "parentProID", tripleDesEncrypt(parentProduct, key));
            writeBsonString(out, "parentVersionID", tripleDesEncrypt(parentVersion, key));
            out.write(0);
        }
        Files.write(path, out.toByteArray());
    }

    private static String tripleDesEncrypt(String value, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "DESede"));
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void writeBsonString(ByteArrayOutputStream out, String name, String value)
            throws IOException {
        out.write(0x02);
        out.write(name.getBytes(StandardCharsets.US_ASCII));
        out.write(0);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeLe32(out, bytes.length + 1);
        out.write(bytes);
        out.write(0);
    }

    private static void writeLe32(ByteArrayOutputStream out, int value) {
        out.write(value & 255);
        out.write((value >>> 8) & 255);
        out.write((value >>> 16) & 255);
        out.write((value >>> 24) & 255);
    }

'''
test = replace_once(test, helper_anchor, helper_block + helper_anchor, "insert RegisterVersion test helpers")
TEST.write_text(test, encoding="utf-8", newline="\n")

print("patched:", SRC.relative_to(ROOT), TEST.relative_to(ROOT))
