#!/usr/bin/env python3
from pathlib import Path
p = Path('src/main/java/LicenseRecoverModernGUIJavaPlan.java')
text = p.read_text(encoding='utf-8')
old = '''        boolean binaryIdentity = !blank(binaryFamily) && !blank(binaryRuntime);\n        boolean directoryIdentity = directoryMapping != null\n                || directDataIdentity || !blank(confirmedClassesFamily) || binaryIdentity;'''
new = '''        boolean binaryIdentity = !blank(binaryFamily)\n                && (!blank(binaryRuntime) || !blank(configDrivenRuntime));\n        boolean directoryIdentity = directoryMapping != null\n                || directDataIdentity || !blank(confirmedClassesFamily) || binaryIdentity;'''
if old not in text:
    raise SystemExit('directoryIdentity anchor not found')
p.write_text(text.replace(old, new, 1), encoding='utf-8')
print('config-driven runtime now participates in directory identity')
