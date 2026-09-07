#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'src/main/java/LicenseRecover.java'
s = p.read_text(encoding='utf-8')
old = '        LinkedHashSet<String> values = new LinkedHashSet<String>();'
new = '        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<String>();'
if old not in s:
    raise SystemExit('LinkedHashSet compile anchor missing')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8', newline='\n')
print('Qualified LinkedHashSet in target RegStr normalizer.')
