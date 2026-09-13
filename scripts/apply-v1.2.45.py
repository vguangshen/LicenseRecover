#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'anchor mismatch in {path}: {n}: {old[:100]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

plan = 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
test = 'src/test/java/RefactorSmokeTest.java'

replace_once(plan,
'''        String binaryRuntime = confirmedBinaryRuntimeProduct(
                root, lib, soft, binaryFamily, newStyle, classesConfig.isFile());
        String configDrivenRuntime = confirmedConfigDrivenRuntimeProduct(root, soft, family);
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim()
                : (!blank(configDrivenRuntime) ? configDrivenRuntime : binaryRuntime)));
''',
'''        String binaryRuntime = confirmedBinaryRuntimeProduct(
                root, lib, soft, binaryFamily, newStyle, classesConfig.isFile());
        String configDrivenRuntime = confirmedConfigDrivenRuntimeProduct(root, soft, family);
        // QT401/QT100101 use the confirmed classes family as their runtime product.
        // DS501/YX0305 do not: their target startup must prove the concrete SoftVersionID.
        String confirmedClassesRuntime = ("QT401".equalsIgnoreCase(confirmedClassesFamily)
                || "QT100101".equalsIgnoreCase(confirmedClassesFamily))
                ? confirmedClassesFamily : null;
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (directDataIdentity ? soft.trim()
                : (!blank(configDrivenRuntime) ? configDrivenRuntime
                : (!blank(binaryRuntime) ? binaryRuntime : confirmedClassesRuntime)));
''')

replace_once(plan,
'''    static String confirmedClassesAuthorizationFamily(File root, String softId) {
        if (root == null || blank(softId)) return null;
        String id = softId.toUpperCase(Locale.ROOT);
        if (id.matches("QT401\\\\d{2}")) {
            File config1 = new File(root, "WEB-INF" + File.separator + "classes"
                    + File.separator + "config1.xml");
            String family = readElement(config1, "SoftVersionID");
            if ("QT401".equalsIgnoreCase(family)) return "QT401";
        }
        if ("QT100101".equals(id)) {
            File config = new File(root, "WEB-INF" + File.separator + "classes"
                    + File.separator + "config.xml");
            String concrete = readElement(config, "SoftVersionID");
            if ("QT100101".equalsIgnoreCase(concrete)) return "QT100101";
        }
        return null;
    }
''',
'''    static String confirmedClassesAuthorizationFamily(File root, String softId) {
        if (root == null || blank(softId)) return null;
        String id = softId.toUpperCase(Locale.ROOT);
        if (id.matches("QT401\\\\d{2}")) {
            File config1 = new File(root, "WEB-INF" + File.separator + "classes"
                    + File.separator + "config1.xml");
            String family = readElement(config1, "SoftVersionID");
            if ("QT401".equalsIgnoreCase(family)) return "QT401";
        }
        File config = new File(root, "WEB-INF" + File.separator + "classes"
                + File.separator + "config.xml");
        String concrete = readElement(config, "SoftVersionID");
        if ("QT100101".equals(id) && "QT100101".equalsIgnoreCase(concrete)) return "QT100101";
        if (!blank(concrete) && id.equalsIgnoreCase(concrete.trim())) {
            if (id.startsWith("DS501") && hasEnumeratedClassesFamily(config, "DS501", id)) return "DS501";
            if (id.startsWith("YX0305") && hasEnumeratedClassesFamily(config, "YX0305", id)) return "YX0305";
        }
        return null;
    }

    static boolean hasEnumeratedClassesFamily(File config, String family, String concrete) {
        if (config == null || !config.isFile() || blank(family) || blank(concrete)) return false;
        try {
            String text = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile(
                    "(?is)<System\\\\b[^>]*\\\\bid\\\\s*=\\\\s*([\\\"'])([^\\\"']+)\\\\1");
            Matcher matcher = pattern.matcher(text);
            String familyUpper = family.trim().toUpperCase(Locale.ROOT);
            String concreteUpper = concrete.trim().toUpperCase(Locale.ROOT);
            LinkedHashSet<String> siblingIds = new LinkedHashSet<String>();
            boolean containsConcrete = false;
            while (matcher.find()) {
                String value = matcher.group(2) == null ? "" : matcher.group(2).trim().toUpperCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                if (value.equals(concreteUpper)) containsConcrete = true;
                if (value.startsWith(familyUpper)) siblingIds.add(value);
            }
            return containsConcrete && siblingIds.size() >= 2;
        } catch (Throwable ignore) {
            return false;
        }
    }
''')

replace_once(test,
'''        Files.write(ds501Classes.resolve("RegistrationEvidence.class"),
                "DS501".getBytes(StandardCharsets.US_ASCII));
        Path ds501SystemInfo = ds501Classes.resolve("com/itmc/register/utils/SystemInfo.class");
''',
'''        Files.write(ds501Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>3</regType></reg><SystemSoft><SoftVersionID>DS50109</SoftVersionID></SystemSoft>"
                        + "<System id=\\\"DS50101\\\"/><System id=\\\"DS50105\\\"/><System id=\\\"DS50109\\\"/></ROOT>"),
                StandardCharsets.UTF_8);
        Path ds501SystemInfo = ds501Classes.resolve("com/itmc/register/utils/SystemInfo.class");
''')

replace_once(test,
'''        Path yx305Servlet = yx305Classes.resolve("com/itmc/sys/platformregister/RegisterHttpServlet.class");
        Path yx305XmlUtil = yx305Classes.resolve("com/itmc/utils/IXmlUtil.class");
        Path yx305Runner = yx305Classes.resolve("com/itmc/utils/ProjectApplicationRunner.class");
        Files.createDirectories(yx305Servlet.getParent());
        Files.createDirectories(yx305XmlUtil.getParent());
        Files.write(yx305Servlet,
                "YX0305 itmc/regedit/RegisterMain doRegistry".getBytes(StandardCharsets.ISO_8859_1));
''',
'''        Files.write(yx305Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>YX030506</SoftVersionID><regInfo>QT100101,QT100102</regInfo></SystemSoft>"
                        + "<System id=\\\"YX030501\\\"/><System id=\\\"YX030502\\\"/><System id=\\\"YX030506\\\"/></ROOT>"),
                StandardCharsets.UTF_8);
        Path yx305XmlUtil = yx305Classes.resolve("com/itmc/utils/IXmlUtil.class");
        Path yx305Runner = yx305Classes.resolve("com/itmc/utils/ProjectApplicationRunner.class");
        Files.createDirectories(yx305XmlUtil.getParent());
''')

(ROOT / 'VERSION.txt').write_text('1.2.45\n', encoding='utf-8')
replace_once('README.md', '当前稳定版本：**v1.2.44**', '当前稳定版本：**v1.2.45**')

cp = ROOT / 'CHANGELOG.md'
old = cp.read_text(encoding='utf-8')
cp.write_text('''## 1.2.45 - 2026-09-13\n\n### Fixed\n- 修复 DS50109 / YX030506 在 v1.2.44 批量扫描中再次显示“待确认”的回归。\n- classes/config.xml 只有在 SoftVersionID 与当前应用一致、并枚举当前模式及至少一个同族 sibling System id 时，才可确认 DS501 / YX0305 family。\n- family 与 runtime ID 继续分离；DS501/YX0305 仍必须由目标启动/注册链证明具体运行 ID，保持 fail-closed。\n- 保持 v1.2.44 的 YX030506 ProjectSourcesPath -> WEB-INF/classes 与 fresh-JVM 原生校验门禁。\n\n### Regression\n- DS50109 / YX030506 测试夹具改为真实 classes/config System 枚举，不再依赖人工独立 family token。\n\n''' + old, encoding='utf-8')

(ROOT / 'release-notes' / 'v1.2.45.md').write_text('''# LicenseRecover v1.2.45\n\n修复 v1.2.44 中 DS50109 与 YX030506 批量扫描重新落入“待确认”的回归。\n\n- 根因：后续 fail-closed 加固要求目标二进制出现独立 DS501 / YX0305 ASCII token，而真实样本主要保存具体 mode 与同族 System 列表。\n- 修复：classes/config.xml 必须明确给出当前 SoftVersionID，并同时枚举当前 System id 和至少一个同族 sibling id，才确认 local authorization family。\n- DS501/YX0305 family 不能直接充当 runtime ProductID；具体 DS50109 / YX030506 仍由目标注册/启动数据流证明。\n- DS50109 的 RegStr 仍需 SystemInfo/RegisterContant/RegisterListener 链证明。\n- YX030506 仍需 IXmlUtil -> PRODUCT_ALL_NUM -> ProjectApplicationRunner -> RegisterMain 链证明，且继续使用 WEB-INF/classes ProjectSourcesPath 配置基目录和 fresh-JVM 校验。\n''', encoding='utf-8')

print('v1.2.45 patch applied')
