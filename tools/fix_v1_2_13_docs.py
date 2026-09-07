from pathlib import Path
for name in ('README.md','README.txt'):
    p=Path(name)
    s=p.read_text(encoding='utf-8')
    if 'v1.2.12' not in s:
        raise SystemExit(name + ': v1.2.12 marker not found')
    s=s.replace('v1.2.12','v1.2.13',1)
    p.write_text(s,encoding='utf-8',newline='')
print('docs updated')
