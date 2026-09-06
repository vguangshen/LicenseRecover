#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src/main/java/LicenseRecover.java"
TEST = ROOT / "src/test/java/RefactorSmokeTest.java"


def replace_once(text, old, new, desc):
    if old not in text:
        raise RuntimeError("Unable to patch %s; source anchor not found" % desc)
    return text.replace(old, new, 1)


source = SOURCE.read_text(encoding="utf-8")

if "boolean rootConfigStyle = usesRootConfigApp(appRoot);" not in source:
    source = replace_once(
        source,
        "        boolean newStyle = isNewStyleApp(appRoot);",
        "        boolean newStyle = isNewStyleApp(appRoot);\n"
        "        boolean rootConfigStyle = usesRootConfigApp(appRoot);",
        "separate product style and root-config style",
    )

if "info.setRegStr(resolveJavaRegStr(appRoot, softId));" not in source:
    source = replace_once(
        source,
        "        info.setRegStr(ALL_NUMS);",
        "        info.setRegStr(resolveJavaRegStr(appRoot, softId));",
        "product-specific RegStr",
    )

for old, new in [
    (
        '            if (newStyle) System.out.println("目标文件(新架构webapp根) : " + new File(appRoot, "config.xml").getAbsolutePath());',
        '            if (rootConfigStyle) System.out.println("目标文件(webapp根) : " + new File(appRoot, "config.xml").getAbsolutePath());',
    ),
    ("        if (backupCfg && newStyle) {", "        if (backupCfg && rootConfigStyle) {"),
    (
        "        String[] checkDirs = newStyle ? new String[]{ libDir, appRoot } : new String[]{ libDir };",
        "        String[] checkDirs = rootConfigStyle ? new String[]{ libDir, appRoot } : new String[]{ libDir };",
    ),
]:
    if old in source:
        source = replace_once(source, old, new, "root-config condition")

old_root_write = (
    "            if (newStyle) {\n"
    "                // 新架构 QT3xxx：应用在 webapp 根读 config.xml（RegisterMain basePath=getRealPath(\"/\")）\n"
)
new_root_write = (
    "            if (rootConfigStyle) {\n"
    "                // 多代新平台均可能把 getRealPath(\"/\") 作为 RegisterMain basePath，授权写入 webapp 根 config.xml。\n"
)
if old_root_write in source:
    source = replace_once(source, old_root_write, new_root_write, "root config write condition")

# XMT0107 stores SoftVersionID in WEB-INF/classes/config.xml rather than data/config.xml.
if "classesSoftId = readJavaConfigElement" not in source:
    tail = (
        "        } catch (Exception e) {\n"
        "            // ignore\n"
        "        }\n"
        "        return null;\n"
        "    }\n\n"
        "    /** 新架构 QT3xxx 标志：webapp 根存在 data/config.xml（其 SystemSoft/SoftVersionID 决定产品号与 PRODUCT_ALL_NUM）。 */\n"
    )
    replacement = (
        "        } catch (Exception e) {\n"
        "            // ignore\n"
        "        }\n"
        "        String classesSoftId = readJavaConfigElement(\n"
        "                new File(appRoot, \"WEB-INF\" + File.separator + \"classes\" + File.separator + \"config.xml\"), \"SoftVersionID\");\n"
        "        if (classesSoftId != null && !classesSoftId.trim().isEmpty()) return classesSoftId.trim();\n"
        "        return null;\n"
        "    }\n\n"
        "    /** 新架构 QT3xxx 标志：webapp 根存在 data/config.xml（其 SystemSoft/SoftVersionID 决定产品号与 PRODUCT_ALL_NUM）。 */\n"
    )
    source = replace_once(source, tail, replacement, "XMT classes SoftVersionID fallback")

if "static String resolveJavaRegStr(String appRoot, String softId)" not in source:
    anchor = "    /** 新架构 QT3xxx 标志：webapp 根存在 data/config.xml（其 SystemSoft/SoftVersionID 决定产品号与 PRODUCT_ALL_NUM）。 */"
    helpers = r'''    static String readJavaConfigElement(File file, String element) {
        if (file == null || !file.isFile() || element == null || element.trim().isEmpty()) return null;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(?is)<" + java.util.regex.Pattern.quote(element) + "\\b[^>]*>\\s*([^<]*?)\\s*</"
                            + java.util.regex.Pattern.quote(element) + "\\s*>").matcher(text);
            return m.find() ? m.group(1).trim() : null;
        } catch (Exception ignore) { return null; }
    }

    static String normalizeCsv(String value) {
        if (value == null) return null;
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        for (String part : value.split(",")) {
            String x = part == null ? "" : part.trim();
            if (!x.isEmpty()) out.add(x);
        }
        if (out.isEmpty()) return null;
        StringBuilder b = new StringBuilder();
        for (String x : out) { if (b.length() > 0) b.append(','); b.append(x); }
        return b.toString();
    }

    static String resolveJavaRegStr(String appRoot, String softId) {
        File root = new File(appRoot == null ? "." : appRoot);
        String data = normalizeCsv(readJavaConfigElement(
                new File(root, "data" + File.separator + "config.xml"), "regInfo"));
        if (data != null) return data;
        String classes = normalizeCsv(readJavaConfigElement(
                new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml"), "regInfo"));
        if (classes != null) return classes;
        if ("YT00129".equalsIgnoreCase(softId)) return "QT0420";
        return ALL_NUMS;
    }

    static boolean usesRootConfigApp(String appRoot) {
        if (appRoot == null) return false;
        File root = new File(appRoot);
        return new File(root, "data" + File.separator + "config.xml").isFile()
                || new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml").isFile();
    }

'''
    source = replace_once(
        source,
        anchor,
        helpers + "    /** data/config.xml 同时具有 regInfo 才属于 QT30xxx 原始 SoftVersionID=ProName 模式。 */",
        "Java profile helpers",
    )

old_style = (
    "    static boolean isNewStyleApp(String appRoot) {\n"
    "        return new File(appRoot, \"data\" + File.separator + \"config.xml\").isFile();\n"
    "    }"
)
new_style = (
    "    static boolean isNewStyleApp(String appRoot) {\n"
    "        if (appRoot == null) return false;\n"
    "        String regInfo = readJavaConfigElement(new File(appRoot, \"data\" + File.separator + \"config.xml\"), \"regInfo\");\n"
    "        return regInfo != null && !regInfo.trim().isEmpty();\n"
    "    }"
)
if old_style in source:
    source = replace_once(source, old_style, new_style, "QT30xxx style detection")

if 'startsWith("XMT01")' not in source:
    source = replace_once(
        source,
        "    static String productMainFor(String softId) {\n        switch (softId) {",
        "    static String productMainFor(String softId) {\n"
        "        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith(\"XMT01\")) return \"XMT01\";\n"
        "        if (softId == null) return \"QT1001\";\n"
        "        switch (softId) {",
        "XMT0107 ProName mapping",
    )

SOURCE.write_text(source, encoding="utf-8", newline="\n")

test = TEST.read_text(encoding="utf-8")
if "YT00129 data config is not misclassified as QT30xxx style" not in test:
    tests = r'''        Path yt129Root = base.resolve("java-YT00129");
        Path yt129Lib = yt129Root.resolve("WEB-INF/lib");
        Files.createDirectories(yt129Lib);
        Files.createDirectories(yt129Root.resolve("data"));
        Files.write(yt129Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(yt129Root.resolve("systemConfig.yml"), Arrays.asList("global.system.VersionID=YT00129"), StandardCharsets.UTF_8);
        Files.write(yt129Root.resolve("data/config.xml"), Arrays.asList("<ROOT><SystemSoft><SoftVersionID>YT00129</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("YT00129".equals(LicenseRecover.readSoftId(yt129Root.toString())), "read equals-style YT00129 VersionID");
        check(!LicenseRecover.isNewStyleApp(yt129Root.toString()), "YT00129 data config is not misclassified as QT30xxx style");
        check(LicenseRecover.usesRootConfigApp(yt129Root.toString()), "YT00129 uses webapp-root authorization config");
        check("QT04".equals(LicenseRecover.productMainFor("YT00129")), "YT00129 maps to QT04 ProName");
        check("QT0420".equals(LicenseRecover.resolveJavaRegStr(yt129Root.toString(), "YT00129")), "YT00129 embeds QT0420 authorization product");

        Path qt30103Root = base.resolve("java-QT30103");
        Path qt30103Lib = qt30103Root.resolve("WEB-INF/lib");
        Files.createDirectories(qt30103Lib);
        Files.createDirectories(qt30103Root.resolve("data"));
        Files.write(qt30103Lib.resolve("ITMCReg-1.0.5.jar"), new byte[]{1});
        Files.write(qt30103Root.resolve("systemConfig.yml"), Arrays.asList("global.system.VersionID=QT30103"), StandardCharsets.UTF_8);
        Files.write(qt30103Root.resolve("data/config.xml"), Arrays.asList("<ROOT><SystemSoft><SoftVersionID>QT30103</SoftVersionID><regInfo>QT30101,QT30102,QT30103,QT30104</regInfo></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check(LicenseRecover.isNewStyleApp(qt30103Root.toString()), "QT30103 regInfo selects QT30xxx style");
        check("QT30101,QT30102,QT30103,QT30104".equals(LicenseRecover.resolveJavaRegStr(qt30103Root.toString(), "QT30103")), "QT30103 preserves data regInfo");

        Path xmtRoot = base.resolve("java-XMT0107");
        Path xmtLib = xmtRoot.resolve("WEB-INF/lib");
        Path xmtClasses = xmtRoot.resolve("WEB-INF/classes");
        Files.createDirectories(xmtLib);
        Files.createDirectories(xmtClasses);
        Files.write(xmtLib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(xmtClasses.resolve("config.xml"), Arrays.asList("<ROOT><SystemSoft><SoftVersionID>XMT0107</SoftVersionID><regInfo>QT0423,QT0428,QT0424,QT0425,QT0427,QT0426,QT0406,QT0430</regInfo></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("XMT0107".equals(LicenseRecover.readSoftId(xmtRoot.toString())), "read XMT0107 from WEB-INF/classes/config.xml");
        check("XMT01".equals(LicenseRecover.productMainFor("XMT0107")), "XMT0107 maps to XMT01 ProName");
        check(LicenseRecover.usesRootConfigApp(xmtRoot.toString()), "XMT0107 uses webapp-root authorization config");
        check("QT0423,QT0428,QT0424,QT0425,QT0427,QT0426,QT0406,QT0430".equals(LicenseRecover.resolveJavaRegStr(xmtRoot.toString(), "XMT0107")), "XMT0107 preserves classes regInfo");

'''
    test = replace_once(
        test,
        '        Path nestedRoot = base.resolve("nestedApp");',
        tests + '        Path nestedRoot = base.resolve("nestedApp");',
        "Java sample smoke tests",
    )
TEST.write_text(test, encoding="utf-8", newline="\n")
print("Java profiles patched: XMT0107, YT00129, QT30103")
