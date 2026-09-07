#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src/main/java/LicenseRecover.java"
PLAN = ROOT / "src/main/java/LicenseRecoverModernGUIJavaPlan.java"
TEST = ROOT / "src/test/java/RefactorSmokeTest.java"

source = SOURCE.read_text(encoding="utf-8")
plan = PLAN.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

# This script used to patch old product-specific fallback rules into the source at
# CI time. That is intentionally forbidden now: verified source must already be
# fail-closed and derive executable registration identity from the selected app.
required_source = [
    "LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect",
    "注册ID、运行校验ID与 RegStr 必须从目标软件目录确认",
    "genRegisterCode(seq, registerPid, regStr)",
]
for marker in required_source:
    if marker not in source:
        raise RuntimeError("Directory-only Java registration policy marker missing: %s" % marker)

required_plan = [
    "directoryIdentity",
    "directoryRegStr",
    "注册ID/RegStr均来自目标目录",
    "缺少目标目录证据",
]
for marker in required_plan:
    if marker not in plan:
        raise RuntimeError("Java directory-evidence plan marker missing: %s" % marker)

for forbidden in [
    "+ ALL_NUMS",
    "info.setRegStr(ALL_NUMS)",
    "genRegisterCode(seq, registerPid)",
]:
    if forbidden in source:
        raise RuntimeError("Legacy Java registration fallback reintroduced: %s" % forbidden)

for marker in [
    "legacy YT fixture without class-level mapping is fail-closed",
    "QT30xxx automatic recovery is allowed only from direct data/config.xml evidence",
]:
    if marker not in test:
        raise RuntimeError("Strict Java smoke-test marker missing: %s" % marker)

print("Java sample policy verified: registration identity and RegStr are directory-evidence only")
