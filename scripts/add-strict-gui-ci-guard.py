#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'scripts/verify.ps1'
s=p.read_text(encoding='utf-8')
anchor="$legacyNetSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LegacyDotNetProtocol.java') -Raw\n"
insert=anchor+"$legacyGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverGUI.java') -Raw\n"
if '$legacyGuiSource =' not in s:
    if anchor not in s: raise SystemExit('legacy source anchor missing')
    s=s.replace(anchor,insert,1)
check_anchor="if ($legacyNetSource.Contains('PRODUCT_NAME = \"itmcIEC\"')) { throw 'Legacy .NET protocol still has a fixed executable product name.' }\n"
checks=check_anchor+'''if ($legacyGuiSource.Contains('LegacyDotNetProtocol.PRODUCT_NAME')) { throw 'Legacy GUI still uses a fixed .NET product identity.' }
if ($legacyGuiSource.Contains('+ ALL_NUMS')) { throw 'Legacy GUI still uses a catch-all Java RegStr.' }
if ($legacyGuiSource.Contains('productMain.isEmpty()) productMain =')) { throw 'Legacy GUI still assigns a default product identity.' }
if (-not $legacyGuiSource.Contains('plan.authorizationFamily, plan.regStr')) { throw 'Legacy GUI Java gencode is not using directory-derived family + RegStr.' }
if (-not $legacyGuiSource.Contains('目标 ITMC.Web.dll 未解析出 ProName')) { throw 'Legacy GUI .NET fail-closed product guard is missing.' }
'''
if 'Legacy GUI still uses a fixed .NET product identity' not in s:
    if check_anchor not in s: raise SystemExit('legacy check anchor missing')
    s=s.replace(check_anchor,checks,1)
p.write_text(s,encoding='utf-8',newline='\n')
print('Added strict legacy GUI CI guard.')
