#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'src/test/java/RefactorSmokeTest.java'
s=p.read_text(encoding='utf-8')
old='''        check("未确认".equals(genericClassesPlan.authorizationFamily)
                        && genericClassesPlan.regStr == null
                        && !genericClassesPlan.automaticRecoveryReady,
                "unknown classes-config apps stay blocked instead of receiving catch-all RegStr");'''
new='''        check(genericClassesPlan.authorizationFamily == null
                        && genericClassesPlan.runtimeProductId == null
                        && genericClassesPlan.regStr == null
                        && !genericClassesPlan.automaticRecoveryReady,
                "unknown classes-config apps expose no guessed identity or catch-all RegStr");'''
if old not in s: raise SystemExit('generic strict assertion anchor missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8',newline='\n')
print('Updated unknown Java strict smoke assertion.')
