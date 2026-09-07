#!/usr/bin/env python3
from pathlib import Path


def replace_once(path, old, new, desc):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    if old not in s:
        raise RuntimeError("anchor not found for %s in %s" % (desc, path))
    p.write_text(s.replace(old, new, 1), encoding="utf-8", newline="\n")


plan = "src/main/java/LicenseRecoverModernGUIJavaPlan.java"
replace_once(
    plan,
    '            if ("QT401".equals(confirmedClassesFamily)) generation = "QT401 / classes-config";\n'
    '            else if (upper.startsWith("XMT01")) generation = "XMT / classes-config";',
    '            if ("QT401".equals(confirmedClassesFamily)) generation = "QT401 / classes-config";\n'
    '            else if ("QT100101".equals(confirmedClassesFamily)) generation = "QT1001系列 / classes-config";\n'
    '            else if (upper.startsWith("XMT01")) generation = "XMT / classes-config";',
    "QT100101 generation",
)

replace_once(
    plan,
    '        if (id.matches("QT401\\\\d{2}")) {\n'
    '            File config1 = new File(root, "WEB-INF" + File.separator + "classes"\n'
    '                    + File.separator + "config1.xml");\n'
    '            String family = readElement(config1, "SoftVersionID");\n'
    '            if ("QT401".equalsIgnoreCase(family)) return "QT401";\n'
    '        }\n'
    '        return null;',
    '        if (id.matches("QT401\\\\d{2}")) {\n'
    '            File config1 = new File(root, "WEB-INF" + File.separator + "classes"\n'
    '                    + File.separator + "config1.xml");\n'
    '            String family = readElement(config1, "SoftVersionID");\n'
    '            if ("QT401".equalsIgnoreCase(family)) return "QT401";\n'
    '        }\n'
    '        if ("QT100101".equals(id)) {\n'
    '            File config = new File(root, "WEB-INF" + File.separator + "classes"\n'
    '                    + File.separator + "config.xml");\n'
    '            String concrete = readElement(config, "SoftVersionID");\n'
    '            if ("QT100101".equalsIgnoreCase(concrete)) return "QT100101";\n'
    '        }\n'
    '        return null;',
    "QT100101 confirmed family",
)

replace_once(
    plan,
    '        if (id.startsWith("DS501")) return "DS501";\n'
    '        if (id.startsWith("YX0305")) return "YX0305";',
    '        if ("QT100101".equals(id)) return "QT100101";\n'
    '        if (id.startsWith("DS501")) return "DS501";\n'
    '        if (id.startsWith("YX0305")) return "YX0305";',
    "QT100101 authorization family",
)

replace_once(
    plan,
    '        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();\n'
    '        if (id.matches("DS28\\\\d{2}")) return "DS28";',
    '        if ("QT100101".equals(id)) return "QT100101";\n'
    '        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();\n'
    '        if (id.matches("DS28\\\\d{2}")) return "DS28";',
    "QT100101 runtime product",
)

core = "src/main/java/LicenseRecover.java"
replace_once(
    core,
    '        if (id.matches("QT401\\\\d{2}")) return "QT401";\n'
    '        if (id.startsWith("DS501")) return "DS501";',
    '        if (id.matches("QT401\\\\d{2}")) return "QT401";\n'
    '        if ("QT100101".equals(id)) return "QT100101";\n'
    '        if (id.startsWith("DS501")) return "DS501";',
    "QT100101 local register product",
)

replace_once(
    core,
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("QT401\\\\d{2}")) return "QT401";\n'
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();',
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("QT401\\\\d{2}")) return "QT401";\n'
    '        if (softId != null && "QT100101".equalsIgnoreCase(softId.trim())) return "QT100101";\n'
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();',
    "QT100101 direct runtime product",
)

test = Path("src/test/java/RefactorSmokeTest.java")
s = test.read_text(encoding="utf-8")
anchor = '        Path genericClassesRoot = base.resolve("java-generic-classes");\n'
if anchor not in s:
    raise RuntimeError("test anchor not found")
block = r'''        Path qt100101Root = base.resolve("java-QT100101");
        Path qt100101Lib = qt100101Root.resolve("WEB-INF/lib");
        Path qt100101Classes = qt100101Root.resolve("WEB-INF/classes");
        Files.createDirectories(qt100101Lib);
        Files.createDirectories(qt100101Classes);
        Files.write(qt100101Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(qt100101Root.resolve("systemConfig.yml"), Arrays.asList(
                "global.system.VersionID=QT100101",
                "global.system.VersionName=互联网营销师大赛平台"), StandardCharsets.UTF_8);
        Files.write(qt100101Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><reg><regType>3</regType></reg><SystemSoft><SoftVersionID>QT100101</SoftVersionID></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        check("QT100101".equals(LicenseRecover.productMainFor("QT100101")),
                "QT100101 startup RegisterMain uses concrete QT100101 id");
        check("QT100101".equals(LicenseRecover.localRegisterProductFor("QT100101", false)),
                "QT100101 local-registration page uses concrete QT100101 id");
        check(LicenseRecover.resolveJavaRegStr(qt100101Root.toString(), "QT100101") == null,
                "QT100101 keeps dynamic RegStr unresolved when config has no regInfo");
        LicenseRecoverModernGUIJavaPlan qt100101Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(qt100101Root.toFile());
        check(qt100101Plan.generation.contains("QT1001系列")
                        && "QT100101".equals(qt100101Plan.authorizationFamily)
                        && "QT100101".equals(qt100101Plan.runtimeProductId),
                "Java GUI plan confirms QT100101 family and runtime id");
        check(qt100101Plan.regStr == null && !qt100101Plan.automaticRecoveryReady
                        && qt100101Plan.recoveryReadiness.contains("RegStr"),
                "QT100101 automatic recovery stays blocked until dynamic RegStr is recovered");

'''
test.write_text(s.replace(anchor, block + anchor, 1), encoding="utf-8", newline="\n")

Path("VERSION.txt").write_text("1.2.8\n", encoding="utf-8", newline="\n")

p = Path("README.md")
s = p.read_text(encoding="utf-8")
if "当前稳定版本：**v1.2.7**" not in s:
    raise RuntimeError("README version anchor not found")
p.write_text(s.replace("当前稳定版本：**v1.2.7**", "当前稳定版本：**v1.2.8**", 1), encoding="utf-8", newline="\n")

p = Path("README.txt")
s = p.read_text(encoding="utf-8")
if "LicenseRecover v1.2.7" not in s:
    raise RuntimeError("README.txt version anchor not found")
p.write_text(s.replace("LicenseRecover v1.2.7", "LicenseRecover v1.2.8", 1), encoding="utf-8", newline="\n")

p = Path("CHANGELOG.md")
s = p.read_text(encoding="utf-8")
marker = "## [1.2.7] - 2026-09-07\n"
if marker not in s:
    raise RuntimeError("CHANGELOG marker not found")
entry = '''## [1.2.8] - 2026-09-07

### Fixed

- 根据 QT100101 实际 Java 样本确认：应用启动与本地注册均使用具体 `QT100101` 作为 RegisterMain 产品 ID，不再错误回退为 `QT1001`。
- QT100101 在 `config.xml` 没有静态 `regInfo` 时继续保持 RegStr 未确认，不猜测 `QT100101`，也不使用 44 项通用列表。

### Changed

- 批量/单应用将 QT100101 显示为 `Java / QT1001系列 / classes-config`，授权族与运行校验ID均为 `QT100101`。
- QT100101 继续由执行资格保护层阻止推荐自动写入，直到能从有效现有授权中可靠恢复动态 RegStr。

### Tests

- 新增 QT100101 `systemConfig.yml + WEB-INF/classes/config.xml` fixture，验证启动产品ID、本地注册产品ID、动态 RegStr 与自动恢复阻止。

'''
p.write_text(s.replace(marker, entry + marker, 1), encoding="utf-8", newline="\n")

Path("release-notes/v1.2.8.md").write_text('''# LicenseRecover v1.2.8

本版本根据实际 QT100101 Java 平台样本补齐授权产品 ID 识别，同时保持 v1.2.7 的安全执行资格保护。

## QT100101

实机样本确认：

- `systemConfig.yml` 的 VersionID 为 `QT100101`；
- `WEB-INF/classes/config.xml` 的 SoftVersionID 为 `QT100101`；
- 应用启动授权流程使用具体 `QT100101` 作为 RegisterMain 产品 ID；
- 本地注册页同样使用具体 `QT100101`；
- 配置中没有静态 `regInfo`，RegStr 仍按动态授权项处理。

因此 v1.2.8 显示：

- 类型/授权代际：`Java / QT1001系列 / classes-config`
- SoftVersionID：`QT100101`
- 授权族：`QT100101`
- 运行校验ID：`QT100101`
- RegStr：`未静态声明`
- 状态：`待确认`

## 安全策略

- 不再错误回退到 `QT1001`。
- 不把 `QT100101` 猜成 RegStr。
- 不使用 44 项通用 RegStr。
- 推荐一键恢复与批量执行继续自动跳过 QT100101，直到能够可靠恢复原动态 RegStr。
''', encoding="utf-8", newline="\n")

# remove staging-only files from final branch tree
Path("scripts/apply-qt100101-v1.2.8.py").unlink()
Path(".github/workflows/apply-qt100101-v1.2.8.yml").unlink()
print("v1.2.8 QT100101 patch applied")
