#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(rel, replacements):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    for old, new in replacements:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{rel}: expected one match, found {count}: {old!r}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


if (ROOT / "VERSION.txt").read_text(encoding="utf-8").strip() != "1.2.29":
    raise SystemExit("Expected VERSION.txt=1.2.29")

patch("src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java", [
    (r'text = text.replaceAll("={8,}", "\\n");', r'text = text.replaceAll("={8,}", "\n");'),
    (r'text = text.replaceAll("\\s+(产品号\\s*[:：])", "\\n$1");', r'text = text.replaceAll("\\s+(产品号\\s*[:：])", "\n$1");'),
    (r'text = text.replaceAll("\\s+(\\[错误\\])", "\\n$1");', r'text = text.replaceAll("\\s+(\\[错误\\])", "\n$1");'),
    (r'text = text.replaceAll("\\s+(RESULT\\s*[:：])", "\\n$1");', r'text = text.replaceAll("\\s+(RESULT\\s*[:：])", "\n$1");'),
    (r'text = text.replaceAll("\\n[ \\t]+", "\\n");', r'text = text.replaceAll("\\n[ \\t]+", "\n");'),
    (r'text = text.replaceAll("\\n{3,}", "\\n\\n");', r'text = text.replaceAll("\\n{3,}", "\n\n");'),
    (r'b.append("LicenseRecover 一键恢复错误\\n");', r'b.append("LicenseRecover 一键恢复错误\n");'),
    (r'.append(result.detection.versionId).append(\'\\n\');', r'.append(result.detection.versionId).append(\'\n\');'),
    (r'b.append("阶段: ").append(p.stage).append(\'\\n\');', r'b.append("阶段: ").append(p.stage).append(\'\n\');'),
    (r'if (p.product != null) b.append("注册产品: ").append(p.product).append(\'\\n\');', r'if (p.product != null) b.append("注册产品: ").append(p.product).append(\'\n\');'),
    (r'if (p.exitCode != null) b.append("退出码: ").append(p.exitCode).append(\'\\n\');', r'if (p.exitCode != null) b.append("退出码: ").append(p.exitCode).append(\'\n\');'),
    (r'b.append("摘要: ").append(p.summary).append("\\n\\n详细信息:\\n").append(p.details);', r'b.append("摘要: ").append(p.summary).append("\n\n详细信息:\n").append(p.details);'),
])

patch("src/test/java/RefactorSmokeTest.java", [
    (r'failureUi.details.contains("\\n产品号:")', r'failureUi.details.contains("\n产品号:")'),
    (r'failureUi.details.contains("\\n[错误]")', r'failureUi.details.contains("\n[错误]")'),
    (r'failureUi.details.contains("\\nRESULT:")', r'failureUi.details.contains("\nRESULT:")'),
])

print("v1.2.29 newline escapes corrected")
