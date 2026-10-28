[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SiteRoot,
    [Parameter(Mandatory=$true)][string]$ToolRoot,
    [Parameter(Mandatory=$true)][string]$SourceRoot
)
# Private Windows runner only: verify the public Java one-click coordinator
# on an ephemeral copy of the original binary fixture. Never publish site data.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path -LiteralPath $SiteRoot).Path
$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$tools = (Resolve-Path -LiteralPath $ToolRoot).Path
$runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd('\') + '\'
if (-not $root.StartsWith($runnerTemp, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'This test is only permitted under the private Windows runner temp folder'
}
$temp = Join-Path $env:RUNNER_TEMP ('DS0101-java-oneclick-' + [guid]::NewGuid().ToString('N'))
$copy = Join-Path $temp 'site'
$classes = Join-Path $temp 'classes'
$srcDir = Join-Path $temp 'driver'
New-Item -ItemType Directory -Path $classes,$srcDir -Force | Out-Null
function HashOrAbsent([string]$p) {
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { return '<absent>' }
    return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash
}
$watched = @('config.xml','Register.xml','notRegFile.xml',
             'bin\config.xml','bin\Register.xml','bin\notRegFile.xml')
$original = @{}
foreach ($name in $watched) { $original[$name] = HashOrAbsent (Join-Path $root $name) }
try {
    Copy-Item -LiteralPath $root -Destination $copy -Recurse -Force
    $guiJar = Join-Path $source 'LicenseRecoverGUI.jar'
    if (-not (Test-Path -LiteralPath $guiJar)) { throw 'Required GUI runtime JAR unavailable' }
    $javaSources = @(Get-ChildItem -LiteralPath (Join-Path $source 'src\main\java') -Filter '*.java' -File | ForEach-Object { $_.FullName })
    & javac -encoding UTF-8 -cp $guiJar -d $classes @javaSources 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Java one-click coordinator did not compile' }
    $nativeSrc = Join-Path $tools 'LicenseRecover.NET'
    $nativeDest = Join-Path $classes 'LicenseRecover.NET'
    Copy-Item -LiteralPath $nativeSrc -Destination $nativeDest -Recurse -Force
    @'
import java.io.File;
public class DS0101NativeOneClickTest {
  public static void main(String[] args) {
    File site = new File(args[0]);
    LicenseRecoverModernGUIAutoRecovery.Detection d = LicenseRecoverModernGUIAutoRecovery.detect(site);
    boolean eligible = LicenseRecoverModernGUIAutoRecovery.supportsDs0101NativeRecovery(d);
    System.out.println("exactSampleEligible=" + eligible);
    if (!eligible) return;
    LicenseRecoverModernGUIAutoRecovery.Result result =
        LicenseRecoverModernGUIAutoRecovery.recover(site, true, true, false, s -> {});
    System.out.println("nativeOneClickReportedSuccess=" + result.success);
  }
}
'@ | Set-Content -LiteralPath (Join-Path $srcDir 'DS0101NativeOneClickTest.java') -Encoding UTF8
    $classpath = $classes + [IO.Path]::PathSeparator + $guiJar
    & javac -encoding UTF-8 -cp $classpath -d $classes (Join-Path $srcDir 'DS0101NativeOneClickTest.java') 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'One-click test driver did not compile' }
    $lines = & java -cp $classpath DS0101NativeOneClickTest $copy 2>$null
    $javaExit = $LASTEXITCODE
    $eligible = @($lines | Where-Object { $_ -eq 'exactSampleEligible=true' }).Count -gt 0
    $success = @($lines | Where-Object { $_ -eq 'nativeOneClickReportedSuccess=true' }).Count -gt 0
    $sourceIntact = $true
    foreach ($name in $watched) {
        if ($original[$name] -ne (HashOrAbsent (Join-Path $root $name))) { $sourceIntact = $false }
    }
    if (-not $sourceIntact) { throw 'Original sample was modified by the disposable one-click test' }
    [ordered]@{
        product = 'DS0101'
        originalSiteUntouched = $sourceIntact
        originalAssemblyHashAccepted = $eligible
        javaCoordinatorExitCode = $javaExit
        nativeOneClickReportedSuccess = $success
        actualIisRestartVerified = $false
        note = 'One-click tested only on an isolated Windows copy; no real IIS deployment tested.'
    } | ConvertTo-Json -Depth 3
} finally {
    Remove-Item -LiteralPath $temp -Force -Recurse -ErrorAction SilentlyContinue
}
