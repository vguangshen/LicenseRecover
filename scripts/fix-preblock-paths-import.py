from pathlib import Path
p=Path('src/test/java/RefactorSmokeTest.java')
s=p.read_text(encoding='utf-8')
anchor='import java.nio.file.Path;\n'
if anchor not in s: raise SystemExit('Path import anchor missing')
if 'import java.nio.file.Paths;\n' not in s:
    s=s.replace(anchor,anchor+'import java.nio.file.Paths;\n',1)
p.write_text(s,encoding='utf-8',newline='\n')
