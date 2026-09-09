#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def collapse_newline_escapes_in_region(rel, start_marker, end_marker):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{rel}: start marker not found: {start_marker!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{rel}: end marker not found: {end_marker!r}")
    block = text[start:end]
    before = block.count("\\\\n")
    if before == 0:
        print(f"{rel}: newline escapes already corrected")
        return
    block = block.replace("\\\\n", "\\n")
    path.write_text(text[:start] + block + text[end:], encoding="utf-8")
    print(f"{rel}: corrected {before} doubled newline escape(s)")


if (ROOT / "VERSION.txt").read_text(encoding="utf-8").strip() != "1.2.29":
    raise SystemExit("Expected VERSION.txt=1.2.29")

collapse_newline_escapes_in_region(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    "    static String formatOneClickFailureDetails(String nativeOutput) {",
    "    private static File currentPersistentLogFile() {",
)

collapse_newline_escapes_in_region(
    "src/test/java/RefactorSmokeTest.java",
    "        LicenseRecoverModernGUIUiPatchLauncher.OneClickFailurePresentation failureUi =",
    "        Path dsFixture = base.resolve(\"DS01-Web.dll\");",
)

print("v1.2.29 newline escapes corrected")
