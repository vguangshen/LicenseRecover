# DS0101 native helper Windows/.NET Framework integration, using synthetic DLLs.
# No original customer files or proprietary vendor DLLs are uploaded to CI.
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest

$testDir = Join-Path $PWD 'build\ds0101-windows-host-smoke'
$site = Join-Path $testDir 'DS0101'
$bin = Join-Path $site 'bin'
$tool = Join-Path $testDir 'tool'
New-Item -ItemType Directory -Force -Path $bin,$tool | Out-Null

@'
<configuration>
  <system.web>
    <httpRuntime targetFramework="4.5" />
    <compilation targetFramework="4.5" />
  </system.web>
</configuration>
'@ | Set-Content -LiteralPath (Join-Path $site 'web.config') -Encoding UTF8
'<ROOT><SystemSoft><SoftVersionID>DS0101</SoftVersionID></SystemSoft><reg><regType>3</regType></reg></ROOT>' |
    Set-Content -LiteralPath (Join-Path $site 'config.xml') -Encoding UTF8
'<ROOT />' | Set-Content -LiteralPath (Join-Path $site 'Register.xml') -Encoding UTF8

@'
using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace ITMC.Regedit {
  public sealed class RegeditInfo { public string ProName { get; set; } public string RegStr { get; set; } public DateTime EndDate { get; set; } }
  public sealed class RegeditMain {
    public string ProName { get; set; }
    public string UserID { get; set; }

    private static string Root {
      get {
        string path = Environment.GetEnvironmentVariable("DS0101_MOCK_ROOT");
        return Path.GetFullPath(path).TrimEnd(Path.DirectorySeparatorChar) +
               Path.DirectorySeparatorChar;
      }
    }

    private static void EnsureHosted(string stage) {
      string actual = Path.GetFullPath(AppDomain.CurrentDomain.BaseDirectory)
                    .TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
      if (!String.Equals(actual, Root, StringComparison.OrdinalIgnoreCase))
        throw new InvalidOperationException(stage + ": wrong hosted BaseDirectory");
      if (!File.Exists(Path.Combine(Root, "config.xml")))
        throw new InvalidOperationException(stage + ": missing site-root config");
    }

    public RegeditMain(string product, string initializer) {
      EnsureHosted("ctor");
      if (product != "itmcIEC") throw new InvalidOperationException("wrong product");
      byte[] md5 = MD5.Create().ComputeHash(Encoding.UTF8.GetBytes("itmcIECRegeditNew"));
      string hash = BitConverter.ToString(md5).Replace("-", "");
      byte[] key = Encoding.ASCII.GetBytes(hash.Substring(0, 8));
      byte[] data = new byte[initializer.Length / 2];
      for (int i = 0; i < data.Length; i++)
        data[i] = Convert.ToByte(initializer.Substring(i * 2, 2), 16);
      using (DES des = DES.Create()) {
        des.Mode = CipherMode.CBC; des.Padding = PaddingMode.PKCS7;
        des.Key = key; des.IV = key;
        byte[] plaintext = des.CreateDecryptor().TransformFinalBlock(data, 0, data.Length);
        if (Encoding.UTF8.GetString(plaintext) != "itmcsoft")
          throw new InvalidOperationException("incorrect native DES initializer");
      }
    }

    public string NewRegistry() {
      EnsureHosted("NewRegistry");
      if (ProName != "itmcIEC" || UserID != "itmc")
        throw new InvalidOperationException("missing native page property setup");
      return "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";
    }

    public bool DoRegistry(string sequence, string authorizationCode) {
      EnsureHosted("DoRegistry");
      if (sequence != NewRegistry() || authorizationCode != "SYNTHETIC_TEST_CODE")
        return false;
      File.WriteAllText(Path.Combine(Root, "mock-authorized.marker"), "synthetic-only");
      return true;
    }

    public bool CheckLocalReg(ref RegeditInfo info) {
      EnsureHosted("CheckLocalReg");
      bool registered = File.Exists(Path.Combine(Root, "mock-authorized.marker"));
      if (registered) {
        info.ProName = "itmcIEC";
        info.RegStr = Environment.GetEnvironmentVariable("DS0101_MOCK_VERSION") ?? "DS0101";
        info.EndDate = new DateTime(2099,12,31);
      }
      return registered;
    }
    public bool CheckReInfo(ref RegeditInfo info) {
      EnsureHosted("CheckReInfo");
      return !File.Exists(Path.Combine(Root, "mock-authorized.marker"));
    }
  }
}
'@ | Set-Content -LiteralPath (Join-Path $testDir 'FakeRegedit.cs') -Encoding UTF8

@'
using System;
using System.IO;
namespace ITMC.Web {
  public sealed class funpublic {
    public static bool CheckReg() {
      string root = Environment.GetEnvironmentVariable("DS0101_MOCK_ROOT");
      return !(File.Exists(Path.Combine(root, "mock-authorized.marker"))
        && Environment.GetEnvironmentVariable("DS0101_MOCK_WEB_OK") == "yes");
    }
  }
}
'@ | Set-Content -LiteralPath (Join-Path $testDir 'FakeWeb.cs') -Encoding UTF8

$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path -LiteralPath $csc)) {
    $csc = "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe"
}
if (-not (Test-Path -LiteralPath $csc)) { throw 'Missing .NET Framework C# compiler on Windows runner' }
function Compile([string]$kind,[string]$destination,[string]$source) {
    & $csc /nologo "/target:$kind" "/out:$destination" /r:System.Web.dll $source
    if ($LASTEXITCODE -ne 0) { throw "C# compile failed: $source" }
}

Compile library (Join-Path $bin 'ITMC.Regedit.dll') (Join-Path $testDir 'FakeRegedit.cs')
Compile library (Join-Path $bin 'ITMC.Web.dll') (Join-Path $testDir 'FakeWeb.cs')
Compile library (Join-Path $tool 'LicenseRecover.NET.AspNetBridge.dll') 'src\dotnet\LicenseRecover.AspNetBridge.cs'
Compile exe (Join-Path $tool 'LicenseRecover.NET.AspNetHost.exe') 'src\dotnet\LicenseRecover.AspNetHost.cs'
Compile library (Join-Path $tool 'LicenseRecover.NET.Legacy.dll') 'src\dotnet\LicenseRecover.LegacyHelper.cs'

$env:DS0101_MOCK_ROOT = $site
$env:DS0101_MOCK_WEB_OK = 'yes'
$hostExe = Join-Path $tool 'LicenseRecover.NET.AspNetHost.exe'
$marker = Join-Path $site 'mock-authorized.marker'
$bridgeInSite = Join-Path $bin 'LicenseRecover.NET.AspNetBridge.dll'

function Invoke-HostTest([string[]]$commandArgs) {
    $out = & $hostExe @commandArgs 2>&1 | Out-String
    $exit = $LASTEXITCODE
    return @{ ExitCode = $exit; Output = [string]$out }
}
function Assert-Contains([string]$source,[string]$needle) {
    if (-not $source.Contains($needle)) { throw "Missing native result marker: $needle" }
}

$probe = Invoke-HostTest @('legacy-probe',$bin)
if ($probe.ExitCode -ne 0) { throw "Reflection-only probe failed: $($probe.Output)" }
Assert-Contains $probe.Output 'RESULT: LEGACY_SIGNATURES_VERIFIED_ONLY'
if (Test-Path -LiteralPath $bridgeInSite) { throw 'legacy-probe unexpectedly staged a website bridge' }

$request = Invoke-HostTest @('legacy-gencode',$bin,'--product','itmcIEC')
if ($request.ExitCode -ne 0) { throw "Hosted NewRegistry failed: $($request.Output)" }
Assert-Contains $request.Output 'RESULT: LEGACY_NATIVE_REQUEST_ONLY'
Assert-Contains $request.Output 'target registration chain=DS01xx-native-offline'
if (Test-Path -LiteralPath $marker) { throw 'request-only call falsely registered the site' }

$before = Invoke-HostTest @('legacy-verify',$bin,'--product','itmcIEC')
if ($before.ExitCode -eq 0) { throw 'Native verification wrongly accepted an unregistered site' }

$invalid = Invoke-HostTest @('legacy-doreg',$bin,'--product','itmcIEC',
  '--seq','0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF',
  '--code','WRONG_TEST_CODE')
if ($invalid.ExitCode -eq 0) { throw 'DoRegistry rejection was not propagated' }
if (Test-Path -LiteralPath $marker) { throw 'Rejected code wrote synthetic registration state' }

$applied = Invoke-HostTest @('legacy-doreg',$bin,'--product','itmcIEC',
  '--seq','0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF',
  '--code','SYNTHETIC_TEST_CODE')
if ($applied.ExitCode -ne 0) { throw "Hosted DoRegistry failed: $($applied.Output)" }
Assert-Contains $applied.Output 'RESULT: LEGACY_NATIVE_DOREG_ACCEPTED'
if (-not (Test-Path -LiteralPath $marker)) { throw 'Accepted mock registration did not persist' }

# Separate host process and hosted AppDomain must independently validate both
# native registration and the application's own Web.CheckReg front door.
$env:DS0101_MOCK_WEB_OK = 'no'
$refused = Invoke-HostTest @('legacy-verify',$bin,'--product','itmcIEC')
if ($refused.ExitCode -eq 0) { throw 'Web.CheckReg registration-error flag was incorrectly ignored' }

$env:DS0101_MOCK_WEB_OK = 'yes'
$verified = Invoke-HostTest @('legacy-verify',$bin,'--product','itmcIEC')
if ($verified.ExitCode -ne 0) { throw "Fresh-host CheckReg failed: $($verified.Output)" }
Assert-Contains $verified.Output 'RESULT: LEGACY_NATIVE_CHECKREG_OK'

# Same native methods must support another DS01xx version without changing
# the DLL fingerprint or accepting a mismatched version token.
$env:DS0101_MOCK_VERSION = 'DS0107'
$wrongVersion = Invoke-HostTest @('legacy-verify',$bin,'--product','itmcIEC','--version','DS0101')
if ($wrongVersion.ExitCode -eq 0) {
    throw 'Legacy verifier accepted a registration for a different DS01xx version'
}
$versionMatched = Invoke-HostTest @('legacy-verify',$bin,'--product','itmcIEC','--version','DS0107')
if ($versionMatched.ExitCode -ne 0) {
    throw "Shared DS0107 protocol version was rejected: $($versionMatched.Output)"
}
Assert-Contains $versionMatched.Output 'RESULT: LEGACY_NATIVE_CHECKREG_OK'
Remove-Item Env:DS0101_MOCK_VERSION -ErrorAction SilentlyContinue

if (Test-Path -LiteralPath $bridgeInSite) {
    throw 'ASP.NET host left its temporary bridge in the synthetic website'
}
Write-Host 'PASS: Windows .NET Framework DS0101 hosted request / DoRegistry / CheckReInfo / Web.CheckReg integration'
