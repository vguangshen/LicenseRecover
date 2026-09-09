#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected one match, found {count}: {old[:120]!r}")
    write(rel, text.replace(old, new, 1))


version = read("VERSION.txt").strip()
if version == "1.2.27":
    print("v1.2.27 already applied; nothing to do.")
    raise SystemExit(0)
if version != "1.2.26":
    raise SystemExit(f"Expected VERSION.txt=1.2.26, found {version!r}")

# v1.2.26 proved the modern-only helper concept on a real machine, but the
# adapter changed only one of several executable ldstr references. Patch every
# old assembly/dll/RegeditMain ldstr in the modern copy and fail closed if the
# tracked helper layout changes.
replace_once(
    "scripts/prepare-dotnet-modern-helper.py",
    "separate modern-only copy by patching three AppReflection #US token operands after\nstrictly verifying the source binary hash. The original helper is never modified.",
    "separate modern-only copy by patching every executable ldstr reference to the\nlegacy assembly/dll/RegeditMain identifiers after strictly verifying the source\nbinary hash. The original helper is never modified."
)
replace_once(
    "scripts/prepare-dotnet-modern-helper.py",
    'EXPECTED_OUTPUT_SHA256 = "aef8bcc41471de2ae361f3f8cd21978f498bd9f7ffa45c86db848dc8da096f36"',
    'EXPECTED_OUTPUT_SHA256 = "75becdebb3772c56ccd10b25537c1f72f6877def844041a6270a3f9e197d2bd4"'
)
replace_once(
    "scripts/prepare-dotnet-modern-helper.py",
    '''TOKEN_PATCHES = (\n    (0x04E9, bytes.fromhex("01000070"), bytes.fromhex("9d000070")),\n    (0x0503, bytes.fromhex("19000070"), bytes.fromhex("e9170070")),\n    (0x02CA, bytes.fromhex("39000070"), bytes.fromhex("db150070")),\n)''',
    '''LDSTR_PATCHES = (\n    # Full IL instruction (0x72 = ldstr) + 4-byte #US token.  Counts are from the\n    # hash-pinned source helper and intentionally make layout drift a hard failure.\n    (bytes.fromhex("72 01 00 00 70"), bytes.fromhex("72 9d 00 00 70"), 1, "assembly itmcRegedit"),\n    (bytes.fromhex("72 19 00 00 70"), bytes.fromhex("72 e9 17 00 70"), 4, "dll itmcRegedit.dll"),\n    (bytes.fromhex("72 39 00 00 70"), bytes.fromhex("72 db 15 00 70"), 6, "type itmcRegedit.RegeditMain"),\n)'''
)
replace_once(
    "scripts/prepare-dotnet-modern-helper.py",
    '''    for offset, before, after in TOKEN_PATCHES:\n        current = bytes(data[offset:offset + len(before)])\n        if current != before:\n            raise SystemExit(\n                f"IL token precondition failed at 0x{offset:x}: "\n                f"expected {before.hex()}, found {current.hex()}"\n            )\n        data[offset:offset + len(before)] = after\n\n    out = bytes(data)''',
    '''    for before, after, expected_count, label in LDSTR_PATCHES:\n        if len(before) != len(after):\n            raise SystemExit(f"{label}: replacement changes PE size")\n        count = bytes(data).count(before)\n        if count != expected_count:\n            raise SystemExit(\n                f"{label}: expected {expected_count} executable ldstr reference(s), found {count}"\n            )\n        data[:] = bytes(data).replace(before, after)\n\n    out = bytes(data)\n    for before, _after, _expected_count, label in LDSTR_PATCHES:\n        if before in out:\n            raise SystemExit(f"{label}: legacy executable ldstr reference remains after patch")'''
)

# The process-level firewall rule is authoritative. Some real .NET applications
# contain a root config.xml used only for SystemSoft/version data and a separate
# registration config. Do not abort merely because an unrelated config.xml has no
# <reg> node; skip only structurally unrelated files and keep malformed registration
# XML fail-closed.
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''    private static void blockPrimaryDotNetConfigs(Detection d, Consumer<String> log) throws Exception {\n        File[] files = new File[]{ new File(d.appRoot, "config.xml"), new File(d.runtimeDir, "config.xml") };\n        HashSet<String> seen = new HashSet<String>();\n        for (File f : files) {\n            if (!f.isFile()) continue;\n            String key = f.getCanonicalPath().toLowerCase(Locale.ROOT);\n            if (!seen.add(key)) continue;\n            String original = readUtf8(f);\n            String updated = putElement(original, "Service", BLOCK_ENDPOINT);\n            validateXml(updated);\n            if (!original.equals(updated)) {\n                Files.write(f.toPath(), updated.getBytes(StandardCharsets.UTF_8));\n                if (log != null) log.accept("[block-net] " + f.getAbsolutePath() + "\\n");\n            }\n        }\n    }''',
    '''    private static void blockPrimaryDotNetConfigs(Detection d, Consumer<String> log) throws Exception {\n        File[] files = new File[]{ new File(d.appRoot, "config.xml"), new File(d.runtimeDir, "config.xml") };\n        HashSet<String> seen = new HashSet<String>();\n        for (File f : files) {\n            if (!f.isFile()) continue;\n            String key = f.getCanonicalPath().toLowerCase(Locale.ROOT);\n            if (!seen.add(key)) continue;\n            String original = readUtf8(f);\n            if (!hasDotNetAuthorizationConfigStructure(original)) {\n                validateXml(original);\n                if (log != null) log.accept("[pre-block-skip] " + f.getAbsolutePath()\n                        + " has no authorization <reg>/Service node; process firewall guard remains active.\\n");\n                continue;\n            }\n            String updated = putElement(original, "Service", BLOCK_ENDPOINT);\n            validateXml(updated);\n            if (!original.equals(updated)) {\n                Files.write(f.toPath(), updated.getBytes(StandardCharsets.UTF_8));\n                if (log != null) log.accept("[block-net] " + f.getAbsolutePath() + "\\n");\n            }\n        }\n    }\n\n    static boolean hasDotNetAuthorizationConfigStructure(String xml) {\n        if (xml == null) return false;\n        if (Pattern.compile("(?is)<Service\\\\b[^>]*>.*?</Service\\\\s*>").matcher(xml).find()) return true;\n        if (Pattern.compile("(?is)<reg\\\\b[^>]*>.*?</reg\\\\s*>").matcher(xml).find()) return true;\n        return Pattern.compile("(?is)<reg\\\\b[^>]*/\\\\s*>").matcher(xml).find();\n    }'''
)

# Also support a self-closing <reg/> registration container if a target uses one.
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''        Pattern reg=Pattern.compile("(?is)<reg\\\\b[^>]*>.*?</reg\\\\s*>"); Matcher r=reg.matcher(xml);\n        if (r.find()) {\n            String b=r.group(), sep=xml.contains("\\r\\n")?"\\r\\n":"\\n"; int close=b.toLowerCase(Locale.ROOT).lastIndexOf("</reg");\n            String rep=b.substring(0,close)+sep+"    <"+name+">"+xmlEscape(value)+"</"+name+">"+sep+b.substring(close);\n            return xml.substring(0,r.start())+rep+xml.substring(r.end());\n        }\n        throw new IOException("config.xml has no <reg> node");''',
    '''        Pattern reg=Pattern.compile("(?is)<reg\\\\b[^>]*>.*?</reg\\\\s*>"); Matcher r=reg.matcher(xml);\n        if (r.find()) {\n            String b=r.group(), sep=xml.contains("\\r\\n")?"\\r\\n":"\\n"; int close=b.toLowerCase(Locale.ROOT).lastIndexOf("</reg");\n            String rep=b.substring(0,close)+sep+"    <"+name+">"+xmlEscape(value)+"</"+name+">"+sep+b.substring(close);\n            return xml.substring(0,r.start())+rep+xml.substring(r.end());\n        }\n        Pattern selfReg=Pattern.compile("(?is)<reg\\\\b([^>]*)/\\\\s*>"); Matcher sr=selfReg.matcher(xml);\n        if (sr.find()) {\n            String attrs=sr.group(1), sep=xml.contains("\\r\\n")?"\\r\\n":"\\n";\n            String rep="<reg"+attrs+">"+sep+"    <"+name+">"+xmlEscape(value)+"</"+name+">"+sep+"</reg>";\n            return xml.substring(0,sr.start())+rep+xml.substring(sr.end());\n        }\n        throw new IOException("config.xml has no <reg> node");'''
)

# Regression coverage for the exact v1.2.26 real-machine failure shape.
test_anchor = '''        check(autoRecoverySource.contains("action=block")\n                        && autoRecoverySource.contains("program=\\\" + helper.getAbsolutePath()")\n                        && autoRecoverySource.contains("HTTP_PROXY")\n                        && autoRecoverySource.contains("127.0.0.1:9"),\n                ".NET native helper has application firewall guard plus proxy defense in depth");\n'''
test_add = test_anchor + '''\n        String unrelatedDotNetConfig =\n                "<ROOT><SystemSoft><SoftVersionID>YX030204</SoftVersionID></SystemSoft></ROOT>";\n        check(!LicenseRecoverModernGUIAutoRecovery.hasDotNetAuthorizationConfigStructure(unrelatedDotNetConfig),\n                ".NET pre-block skips unrelated config.xml without authorization reg/Service structure");\n        String registrationDotNetConfig = "<ROOT><reg><regType>3</regType></reg></ROOT>";\n        check(LicenseRecoverModernGUIAutoRecovery.hasDotNetAuthorizationConfigStructure(registrationDotNetConfig),\n                ".NET pre-block recognizes registration config.xml before adding Service isolation");\n        String selfClosingReg = LicenseRecoverModernGUIAutoRecovery.updateLocalLicenseXml(\n                "<ROOT><reg/></ROOT>", "TEST", true);\n        check(selfClosingReg.contains("<reg>")\n                        && selfClosingReg.contains("<regName>TEST</regName>")\n                        && selfClosingReg.contains("<Service>http://127.0.0.1:9/Service.asmx</Service>"),\n                ".NET config writer expands self-closing reg containers safely");\n'''
replace_once("src/test/java/RefactorSmokeTest.java", test_anchor, test_add)

# Strengthen CI source assertions so this real-machine regression cannot silently return.
verify_anchor = '''if (-not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw 'Modern .NET one-click is not using the uppercase helper adapter.' }\nif (-not $modernGuiSource.Contains('appendPersistentLog')) { throw 'Modern GUI persistent diagnostics are missing.' }'''
verify_add = '''if (-not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw 'Modern .NET one-click is not using the uppercase helper adapter.' }\nif (-not $autoSource.Contains('hasDotNetAuthorizationConfigStructure')) { throw 'Modern .NET pre-block cannot distinguish unrelated config.xml files.' }\nif ($autoSource.Contains('String updated = putElement(original, "Service", BLOCK_ENDPOINT);\\n            validateXml(updated);') -and -not $autoSource.Contains('[pre-block-skip]')) { throw 'Modern .NET pre-block still aborts on unrelated config.xml files.' }\nif (-not $modernGuiSource.Contains('appendPersistentLog')) { throw 'Modern GUI persistent diagnostics are missing.' }'''
replace_once("scripts/verify.ps1", verify_anchor, verify_add)

write("VERSION.txt", "1.2.27\n")
replace_once("README.md", "当前稳定版本：**v1.2.26**", "当前稳定版本：**v1.2.27**")

changelog = '''## [1.2.27] - 2026-09-09\n\n### Fixed\n\n- 根据 v1.2.26 的真实 43 项回归继续修复剩余 10 个 .NET Modern 失败：其中 7 个在防联网配置预处理阶段被无关 `config.xml` 的“无 `<reg>`”结构误伤，3 个在 `gencode` 阶段仍命中 helper 内未替换完整的小写 `itmcRegedit.dll` 引用。\n- `.NET Modern` 的进程级 Windows Firewall 出站规则继续作为调用目标注册组件前的强制隔离；根目录/`bin` 中不含授权 `<reg>`/`Service` 结构的普通 `config.xml` 现在只校验 XML 后跳过，不再中止原生恢复链。\n- modern-only helper 构建从“固定改 3 个 token 位置”改为“严格计数并替换全部可执行 `ldstr` 引用”：`itmcRegedit` 1 处、`itmcRegedit.dll` 4 处、`itmcRegedit.RegeditMain` 6 处；数量漂移或仍残留旧可执行引用时 CI 直接失败。\n- `putElement` 增加 `<reg/>` 自闭合节点兼容，避免合法但精简的授权配置无法注入本地字段。\n\n### Regression\n\n- 新增无授权结构 `config.xml` 跳过测试、授权 `<reg>` 识别测试和 `<reg/>` 展开写入测试。\n- 保持失败回滚、PRE-BLOCK FIRST、目录证据 fail-closed、写后原生 `CheckReInfo` 验证与 GUI 持久日志不变。\n\n'''
write("CHANGELOG.md", changelog + read("CHANGELOG.md"))

release_notes = '''# LicenseRecover v1.2.27\n\n本版直接针对 v1.2.26 在真实 Windows 环境中再次跑 43 个项目后留下的 10 个 `.NET Modern` 失败。\n\n- **7 个配置预处理失败已修复**：GM00401、YX0102、YX030101、YX030105、YX030107、YX030201、YX030204 的日志都显示 Windows Firewall `PRE_BLOCK` 已成功，但随后被一个不属于授权配置的 `config.xml`（没有 `<reg>`）中止。本版只对存在 `<reg>`/`Service` 授权结构的配置执行 Service 隔离；普通 XML 只校验并跳过，进程级防火墙规则仍保持强制生效。\n- **3 个 gencode 旧 DLL 引用已修复**：YX030308、YX030321、YX030322 已进入 `LicenseRecover.NET.Modern.exe`，但 helper 内仍有 3/4 个 `itmcRegedit.dll` 和 5/6 个 `itmcRegedit.RegeditMain` 的旧可执行 `ldstr` 引用未被 v1.2.26 改掉。本版严格替换全部 1/4/6 处旧 assembly/dll/type 引用，且残留即构建失败。\n- **兼容精简授权 XML**：支持 `<reg/>` 自闭合节点在需要写入字段时安全展开。\n- **安全边界不变**：PRE-BLOCK FIRST、目标目录证据、失败回滚、`gencode -> DoRegistry -> CheckReInfo` 原生闭环与持久日志全部保留。\n\n建议再次对同一批 43 个项目执行批量恢复；若仍有失败，`logs/LicenseRecoverGUI-YYYYMMDD.log` 会继续给出精确阶段。\n'''
write("release-notes/v1.2.27.md", release_notes)

print("v1.2.27 staged changes applied.")
