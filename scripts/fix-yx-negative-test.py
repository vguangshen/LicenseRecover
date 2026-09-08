#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'src/test/java/RefactorSmokeTest.java'
s = p.read_text(encoding='utf-8')
old = '''        check(LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102WrongProduct) == null,\n                "target-local .NET regName is rejected when its cryptographic ProName does not match");\n'''
new = '''        String yx0102WrongRegStr = LicenseRecoverModernGUIAutoRecovery.detectDotNetRegStr(yx0102WrongProduct);\n        check(yx0102WrongRegStr == null || yx0102WrongRegStr.trim().isEmpty(),\n                "target-local .NET regName is rejected when its cryptographic ProName does not match");\n'''
if s.count(old) != 1:
    raise SystemExit('negative test anchor not found or not unique')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
