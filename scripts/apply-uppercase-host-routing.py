from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'missing expected block in {path}')
    if text.count(old) != 1:
        raise SystemExit(f'expected one block in {path}, found {text.count(old)}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

path = 'src/main/java/LicenseRecoverModernGUIAutoRecovery.java'
old = '''        boolean lowercaseChain = "itmcRegedit.dll".equals(targetRegedit.getName());
        File helper = lowercaseChain ? findDotNetAspNetHostHelper() : findDotNetModernNativeHelper();
        if (helper == null) {
            String expected = lowercaseChain ? "LicenseRecover.NET.AspNetHost.exe" : "LicenseRecover.NET.Modern.exe";
            return Result.fail("[HELPER] " + expected + " was not found; selected target registration chain cannot run.", d);
        }
'''
new = '''        boolean lowercaseChain = "itmcRegedit.dll".equals(targetRegedit.getName());
        // Both lowercase and uppercase registration components must execute inside the
        // target web application's hosted AppDomain.  The two generations resolve
        // paths differently (lowercase uses HttpContext.MapPath, uppercase uses
        // AppDomain.BaseDirectory), but both expect the site root rather than the
        // LicenseRecover tool directory or target bin as their application base.
        File helper = findDotNetAspNetHostHelper();
        if (helper == null) {
            return Result.fail("[HELPER] LicenseRecover.NET.AspNetHost.exe was not found; selected target registration chain cannot run.", d);
        }
'''
replace_once(path, old, new)

old2 = '''        } else {
            log.accept("[dotnet-chain] lowercase itmcRegedit.dll absent; uppercase ITMC.Regedit.dll fallback; product="
                    + appProduct + "\\n");
        }
'''
new2 = '''        } else {
            log.accept("[dotnet-chain] lowercase itmcRegedit.dll absent; uppercase ITMC.Regedit.dll fallback via hosted AppDomain; product="
                    + appProduct + "\\n");
        }
'''
replace_once(path, old2, new2)
print('uppercase host routing patch applied')
