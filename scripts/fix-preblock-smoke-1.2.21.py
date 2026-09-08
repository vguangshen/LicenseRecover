from pathlib import Path
p=Path('src/test/java/RefactorSmokeTest.java')
s=p.read_text(encoding='utf-8')
for old,new in [
('autoRecoverySource.indexOf("generate.add(\\\\\\\"gencode\\\\\\\")")','autoRecoverySource.indexOf("generate.add(\\\"gencode\\\")")'),
('autoRecoverySource.indexOf("apply.add(\\\\\\\"doreg\\\\\\\")")','autoRecoverySource.indexOf("apply.add(\\\"doreg\\\")")'),
('autoRecoverySource.indexOf("verify.add(\\\\\\\"verify\\\\\\\")")','autoRecoverySource.indexOf("verify.add(\\\"verify\\\")")')]:
    if old not in s: raise SystemExit('missing anchor: '+old)
    s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8',newline='\n')
