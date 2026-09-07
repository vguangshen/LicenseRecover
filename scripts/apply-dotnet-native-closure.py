#!/usr/bin/env python3
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'src/main/java/LicenseRecoverModernGUIAutoRecovery.java'
TEST = ROOT / 'src/test/java/RefactorSmokeTest.java'
VERIFY = ROOT / 'scripts/verify.ps1'

subprocess.run(['git','fetch','origin','fix/yt00138-dotnet-native-1.2.13','--depth=1'], check=True)
pr = subprocess.check_output(['git','show','FETCH_HEAD:src/main/java/LicenseRecoverModernGUIAutoRecovery.java'], text=True, encoding='utf-8')
cur = TARGET.read_text(encoding='utf-8')

def section(text, start, end):
    a = text.index(start)
    b = text.index(end, a)
    return text[a:b]

def replace_section(text, start, end, replacement):
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + replacement + text[b:]

# 1) Replace only the .NET native one-click implementation and helper methods.
start = '    private static Result recoverDotNet(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {'
end = '    private static void verifyPersisted(File cfg,String product,String machine) throws Exception {'
cur = replace_section(cur, start, end, section(pr, start, end))

# 2) Replace only .NET ProName detection logic; do not touch Java mapping code.
start2 = '    static String detectProduct(File webDll,String version) {'
end2 = '    static String detectProductList(File webDll,String product) {'
cur = replace_section(cur, start2, end2, section(pr, start2, end2))

assert 'gencode -> DoRegistry -> CheckReInfo' in cur
assert 'target RegeditMain.DoRegistry() rejected the generated code' in cur
assert 'target RegeditMain.CheckReInfo() did not confirm the native write-back' in cur
assert 'snapshotDotNetRegistrationFiles' in cur
assert 'blockPrimaryDotNetConfigs' in cur
TARGET.write_text(cur, encoding='utf-8')

# 3) Add focused regression coverage without importing PR #23 Java/YT00138 fixed mappings.
test = TEST.read_text(encoding='utf-8')
marker = '        Path dsFixture = base.resolve("DS01-Web.dll");'
if 'native .NET one-click parses target request code' not in test:
    block = '''        Path yx302CrossFixture = base.resolve("YX030107-Web.dll");\n        writeUtf16Fixture(yx302CrossFixture, "YX030107", "YX0302", "YX030201", "YX030204", "YX030219");\n        check("YX0302".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(\n                        yx302CrossFixture.toFile(), "YX030107")),\n                "YX030107 does not get misclassified as YX0301 when target DLL proves YX0302 family");\n        check("YX030201,YX030204,YX030219".equals(\n                        LicenseRecoverModernGUIAutoRecovery.detectProductList(yx302CrossFixture.toFile(), "YX0302")),\n                "YX030107/YX0302 sample derives the target-local product list");\n\n        String nativeOut = "注册申请号     : A1B2C3D4\\n离线授权码     : 001122AABB\\n";\n        check("A1B2C3D4".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "注册申请号")),\n                "native .NET one-click parses target request code");\n        check("001122AABB".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "离线授权码")),\n                "native .NET one-click parses target authorization code");\n\n'''
    if marker not in test:
        raise SystemExit('test insertion marker not found')
    test = test.replace(marker, block + marker, 1)
    TEST.write_text(test, encoding='utf-8')

# 4) CI static guard: bundled native helper must expose all three command entry points.
verify = VERIFY.read_text(encoding='utf-8')
anchor = "Write-Host 'Building deterministic runtime overlay...'"
if 'LicenseRecover.NET helper command surface' not in verify:
    guard = '''Write-Host 'Verifying LicenseRecover.NET helper command surface...'\n$nativeHelper = Join-Path $repoRoot 'LicenseRecover.NET/LicenseRecover.NET.exe'\nif (-not (Test-Path -LiteralPath $nativeHelper)) { throw 'Missing LicenseRecover.NET.exe.' }\n$nativeBytes = [IO.File]::ReadAllBytes($nativeHelper)\n$nativeAscii = [Text.Encoding]::ASCII.GetString($nativeBytes)\n$nativeUnicode = [Text.Encoding]::Unicode.GetString($nativeBytes)\nforeach ($commandName in @('gencode','doreg','verify')) {\n    if (($nativeAscii.IndexOf($commandName, [StringComparison]::OrdinalIgnoreCase) -lt 0) -and\n        ($nativeUnicode.IndexOf($commandName, [StringComparison]::OrdinalIgnoreCase) -lt 0)) {\n        throw "LicenseRecover.NET helper command surface is missing: $commandName"\n    }\n}\n\n'''
    if anchor not in verify:
        raise SystemExit('verify insertion marker not found')
    verify = verify.replace(anchor, guard + anchor, 1)
    VERIFY.write_text(verify, encoding='utf-8')

print('Applied .NET native registration closure transplant.')
