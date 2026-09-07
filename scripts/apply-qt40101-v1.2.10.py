#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def replace_once(text, old, new, desc):
    if old not in text:
        raise RuntimeError("Unable to patch %s; anchor not found" % desc)
    return text.replace(old, new, 1)

# 1) GUI/shared Java plan: exact QT40101 sample can use concrete VersionID as the
# deterministic minimum non-empty RegStr. The supplied production bytecode sets
# hasRegister=true and authorizeFlag=true before reading RegStr, so startup does
# not require a ClassPid match; RegStr is only retained as authProducts. Keep the
# rule exact to QT40101 rather than extrapolating to the whole QT401 family.
plan_path = ROOT / "src/main/java/LicenseRecoverModernGUIJavaPlan.java"
plan = plan_path.read_text(encoding="utf-8")
old = '''        String recovered = ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);\n        if (recovered != null) return recovered;\n\n        if ("QT100101".equalsIgnoreCase(softId)\n'''
new = '''        String recovered = ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);\n        if (recovered != null) return recovered;\n\n        // QT40101 production sample: RegisterListener sets hasRegister/authorizeFlag=true\n        // before consuming RegStr, and no application-side startup gate requires a\n        // ClassPid match. Use the concrete VersionID as the deterministic minimum\n        // non-empty RegStr. Do not generalize this rule to other QT401xx products.\n        if ("QT40101".equalsIgnoreCase(softId)\n                && "QT401".equals(confirmedClassesAuthorizationFamily(root, softId))) {\n            return "QT40101";\n        }\n\n        if ("QT100101".equalsIgnoreCase(softId)\n'''
plan = replace_once(plan, old, new, "QT40101 GUI RegStr rule")
plan_path.write_text(plan, encoding="utf-8", newline="\n")

# 2) CLI direct rebuild path: keep exactly the same evidence gate.
cli_path = ROOT / "src/main/java/LicenseRecover.java"
cli = cli_path.read_text(encoding="utf-8")
old = '''        if (libPath != null) {\n            String recovered = ExistingLocalRegStrProbe.recover(root, new File(libPath), runtimeProduct);\n            if (recovered != null) return recovered;\n        }\n\n        // Actual QT100101 business code checks RegStr.contains(RegisterContant.versionID),\n'''
new = '''        if (libPath != null) {\n            String recovered = ExistingLocalRegStrProbe.recover(root, new File(libPath), runtimeProduct);\n            if (recovered != null) return recovered;\n        }\n\n        // QT40101 production sample: application startup sets hasRegister and\n        // authorizeFlag true before it inspects RegStr; no ClassPid match is required\n        // for startup. The concrete VersionID is therefore a deterministic minimum\n        // non-empty RegStr for this exact product.\n        if ("QT40101".equalsIgnoreCase(softId)) {\n            String family = readJavaConfigElement(new File(root, "WEB-INF" + File.separator\n                    + "classes" + File.separator + "config1.xml"), "SoftVersionID");\n            if ("QT401".equalsIgnoreCase(family)) return "QT40101";\n        }\n\n        // Actual QT100101 business code checks RegStr.contains(RegisterContant.versionID),\n'''
cli = replace_once(cli, old, new, "QT40101 CLI RegStr rule")
cli_path.write_text(cli, encoding="utf-8", newline="\n")

# 3) Regression fixture: QT40101 now becomes ready, while the generic unknown
# classes-config protection remains unchanged elsewhere in the suite.
test_path = ROOT / "src/test/java/RefactorSmokeTest.java"
test = test_path.read_text(encoding="utf-8")
old = '''        check(LicenseRecover.resolveJavaRegStr(qt401Root.toString(), "QT40101") == null,\n                "QT40101 does not inherit the 44-item fallback when regInfo is absent");\n'''
new = '''        check("QT40101".equals(LicenseRecover.resolveJavaRegStr(qt401Root.toString(), "QT40101")),\n                "QT40101 uses the exact-sample verified minimum RegStr, not the 44-item fallback");\n'''
test = replace_once(test, old, new, "QT40101 CLI smoke expectation")
old = '''        check(qt401Plan.regStr == null && !qt401Plan.automaticRecoveryReady\n                        && qt401Plan.recoveryReadiness.contains("RegStr"),\n                "QT401 automatic recovery is blocked until dynamic RegStr can be recovered");\n'''
new = '''        check("QT40101".equals(qt401Plan.regStr) && qt401Plan.automaticRecoveryReady\n                        && qt401Plan.recoveryReadiness.contains("可安全"),\n                "QT40101 automatic recovery is ready with the exact-sample verified minimum RegStr");\n'''
test = replace_once(test, old, new, "QT40101 GUI smoke expectation")
test_path.write_text(test, encoding="utf-8", newline="\n")

# 4) Release metadata/docs.
(ROOT / "VERSION.txt").write_text("1.2.10\n", encoding="utf-8")

changelog_path = ROOT / "CHANGELOG.md"
changelog = changelog_path.read_text(encoding="utf-8")
entry = '''## [1.2.10] - 2026-09-07\n\n### Fixed\n\n- 根据 QT40101 真实生产样本的 RegisterListener / RegisterInterceptor 字节码，确认启动阶段在读取 RegStr 前即将 `hasRegister` 与 `authorizeFlag` 置为 true，启动授权不要求 RegStr 命中 `StoreIPAddress.json` 的 ClassPid。\n- QT40101 在没有旧本地授权可动态恢复时，使用具体 VersionID `QT40101` 作为经过该样本验证的最小非空 RegStr，不再停留在“待确认”。\n- 规则仅对 `QT40101 + config1.xml SoftVersionID=QT401` 精确生效，不推广到其它 QT401xx 产品。\n\n### Safety\n\n- 仍优先使用 v1.2.9 的旧本地授权动态 RegStr 恢复；若能恢复真实 RegStr，则真实值优先于最小回退。\n- 未确认的其它 classes-config 产品仍保持自动写入阻止，不恢复 44 项通用 fallback。\n\n'''
marker = "## [1.2.9] - 2026-09-07"
changelog = replace_once(changelog, marker, entry + marker, "changelog v1.2.10 entry")
changelog_path.write_text(changelog, encoding="utf-8", newline="\n")

for name in ("README.md", "README.txt"):
    p = ROOT / name
    text = p.read_text(encoding="utf-8")
    if "v1.2.10" not in text:
        if "v1.2.9" in text:
            text = text.replace("v1.2.9", "v1.2.10", 1)
        else:
            text = "当前稳定版：v1.2.10\n\n" + text
    p.write_text(text, encoding="utf-8", newline="\n")

notes = '''# LicenseRecover v1.2.10\n\n本版本根据用户提供的 QT40101 真实生产包，完成该平台 RegStr 的最后一层运行时确认。\n\n## QT40101\n\n真实字节码显示：\n\n- `SystemInfo.registerId` 从 `config1.xml` 读取为 `QT401`，因此 RegisterMain 运行产品号仍为 `QT401`；\n- 当前具体应用由 `systemConfig.yml` / `config.xml` 确认为 `QT40101`；\n- `RegisterListener.run()` 在读取 `getRegInfo().getRegStr()` 之前已经将 `RegisterContant.hasRegister=true` 与 `authorizeFlag=true`；\n- 后续 ClassPid 遍历只可能再次把 `authorizeFlag` 设为 true，不存在把它改回 false 的路径；\n- `RegisterInterceptor` 的访问门槛仅检查 `hasRegister / authorizeFlag / hasInitDatabase`。\n\n因此该真实版本的启动授权并不要求 RegStr 命中特定 ClassPid。v1.2.10 对 **QT40101 这一精确产品** 使用具体 VersionID `QT40101` 作为确定性的最小非空 RegStr。\n\n扫描后预期显示：\n\n- 类型/授权代际：`Java / QT401 / classes-config`\n- SoftVersionID：`QT40101`\n- 授权族：`QT401`\n- 运行校验ID：`QT401`\n- RegStr：`QT40101`\n- 状态：`待处理`\n\n## 优先级与安全边界\n\n若应用中已经存在 `regType=1 + regName` 的旧本地授权，仍优先使用 v1.2.9 的只读动态恢复结果。只有无法恢复旧 RegStr 时才使用上述 QT40101 精确规则。其它 QT401xx 或未知 classes-config 产品不会自动套用此值。\n'''
(ROOT / "release-notes" / "v1.2.10.md").write_text(notes, encoding="utf-8", newline="\n")

# Remove one-shot staging files so the final branch tree remains clean.
for rel in ("scripts/apply-qt40101-v1.2.10.py", ".github/workflows/apply-qt40101-v1.2.10.yml"):
    p = ROOT / rel
    if p.exists():
        p.unlink()

print("Applied v1.2.10 QT40101 exact-sample RegStr rule")
