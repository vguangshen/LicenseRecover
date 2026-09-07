from pathlib import Path
p = Path('src/main/java/LicenseRecoverModernGUIJavaPlan.java')
s = p.read_text(encoding='utf-8')
old = r'DS28\d{2}'
new = r'DS28\\d{2}'
count = s.count(old)
if count != 3:
    raise SystemExit('expected 3 single-escaped DS28 regexes, got %d' % count)
p.write_text(s.replace(old, new), encoding='utf-8')
for f in [Path('scripts/fix-v1.2.5-escapes.py'), Path('.github/workflows/fix-v1.2.5-escapes.yml')]:
    if f.exists():
        f.unlink()
print('fixed', count, 'DS28 regex escapes')
