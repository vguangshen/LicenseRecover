#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'src/test/java/RefactorSmokeTest.java'
s = p.read_text(encoding='utf-8')
repls = [
('''        check(qt401Plan.regStr == null && !qt401Plan.automaticRecoveryReady
                        && qt401Plan.recoveryReadiness.contains("RegStr"),
                "QT40101 identity may be confirmed by config1.xml but RegStr must still come from the target directory");''',
'''        check(qt401Plan.regStr == null && qt401Plan.automaticRecoveryReady
                        && qt401Plan.regStrSummary().contains("动态")
                        && qt401Plan.recoveryReadiness.contains("RegisterMain.getRegInfo"),
                "QT40101 never invents RegStr and may read it from the target registration component before write");'''),
('''        check(!qt100101Plan.automaticRecoveryReady
                        && qt100101Plan.recoveryReadiness.contains("RegStr"),
                "QT100101 stays blocked until RegStr is declared/recovered from target directory");''',
'''        check(qt100101Plan.regStr == null && qt100101Plan.automaticRecoveryReady
                        && qt100101Plan.regStrSummary().contains("动态")
                        && qt100101Plan.recoveryReadiness.contains("RegisterMain.getRegInfo"),
                "QT100101 keeps RegStr unset until the target registration component returns it before write");''')
]
for old, new in repls:
    if old not in s:
        raise SystemExit('dynamic RegStr smoke anchor missing')
    s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8', newline='\n')
print('Updated QT401/QT100101 dynamic target-RegStr smoke expectations.')
