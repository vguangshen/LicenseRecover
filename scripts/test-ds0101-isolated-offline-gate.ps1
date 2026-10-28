[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SiteRoot,
    [Parameter(Mandatory=$true)][string]$ToolRoot,
    [Parameter(Mandatory=$true)][string]$SourceRoot
)
# Private sanitized fixture only: never run against a production IIS website.
# Raw machine/request/license data and vendor exceptions never leave the runner.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Snapshot([string]$root) {
    $result = @{}
    $prefix = (Resolve-Path -LiteralPath $root).Path.TrimEnd('\') + '\'
    Get-ChildItem -LiteralPath $root -File -Recurse | ForEach-Object {
        $relative = $_.FullName.Substring($prefix.Length)
        $result[$relative] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
    return $result
}
function Invoke-Native([string]$exe, [string[]]$arguments, [int]$seconds = 45,
                       [string]$stdinText = '') {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $exe
    $info.Arguments = [string]::Join(' ', @($arguments | ForEach-Object {
        '"' + $_.Replace('"', '\"') + '"'
    }))
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.RedirectStandardInput = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'Native process not started' }
        if ($stdinText) { $process.StandardInput.WriteLine($stdinText) }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit($seconds * 1000)) {
            try { $process.Kill() } catch {}
            $process.WaitForExit()
            return @{ ExitCode = 124; Timeout = $true; Out = ''; Err = '' }
        }
        return @{
            ExitCode = $process.ExitCode; Timeout = $false
            Out = $process.StandardOutput.ReadToEnd()
            Err = $process.StandardError.ReadToEnd()
        }
    } finally { $process.Dispose() }
}
$site = (Resolve-Path -LiteralPath $SiteRoot).Path
$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$tool = (Resolve-Path -LiteralPath $ToolRoot).Path
if (-not (Test-Path -LiteralPath (Join-Path $site 'bin\ITMC.Regedit.dll'))) {
    throw 'Restored DS0101 assembly missing'
}
$configText = Get-Content -LiteralPath (Join-Path $site 'config.xml') -Raw
if ($configText -notmatch '(?i)<SoftVersionID>\s*DS0101\s*</SoftVersionID>' -or
    (Test-Path -LiteralPath (Join-Path $site 'notRegFile.xml'))) {
    throw 'Unknown fixture or existing local registration cache; refusing'
}
$hostExe = Join-Path $tool 'LicenseRecover.NET\LicenseRecover.NET.AspNetHost.exe'
$javaSource = Join-Path $source 'src\main\java\LegacyDotNetProtocol.java'
if (-not (Test-Path -LiteralPath $hostExe) -or -not (Test-Path -LiteralPath $javaSource)) {
    throw 'Pinned host or production offline protocol source unavailable'
}

$beforeSource = Snapshot $site
$temp = Join-Path ([IO.Path]::GetTempPath()) ('DS0101-native-gate-' + [guid]::NewGuid().ToString('N'))
$copy = Join-Path $temp 'site'
$javaDir = Join-Path $temp 'java'
New-Item -Path $javaDir -ItemType Directory -Force | Out-Null
try {
    Copy-Item -LiteralPath $site -Destination $copy -Recurse -Force
    $copyBin = Join-Path $copy 'bin'
    $before = Snapshot $copy
    # Only the unused GUI detection dependency is stubbed. Use the actual
    # production legacy protocol implementation for code generation.
    @'
final class LicenseRecoverModernGUIAutoRecovery {
    static String detectProduct(java.io.File f, String version) {
        return "itmcIEC";
    }
}
'@ | Set-Content -LiteralPath (Join-Path $javaDir 'LicenseRecoverModernGUIAutoRecovery.java') -Encoding UTF8
    @'
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class DS0101OfflineProbe {
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String request = reader.readLine();
        if (request == null || request.length() < 32) System.exit(2);
        LegacyDotNetProtocol.CodeResult candidate =
            LegacyDotNetProtocol.generateAuthorizationCode(request, "DS0101", "itmcIEC");
        if (!candidate.request.regId.equals(candidate.authorization.regId) ||
            !candidate.request.requestTime.equals(candidate.authorization.requestTime) ||
            !"DS0101".equals(candidate.authorization.product)) System.exit(3);
        System.out.println(candidate.code);
    }
}
'@ | Set-Content -LiteralPath (Join-Path $javaDir 'DS0101OfflineProbe.java') -Encoding UTF8
    & javac -encoding UTF-8 -d $javaDir $javaSource (Join-Path $javaDir 'LicenseRecoverModernGUIAutoRecovery.java') (Join-Path $javaDir 'DS0101OfflineProbe.java') 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Offline protocol helper failed to compile' }

    $abi = Invoke-Native $hostExe @('legacy-probe', $copyBin)
    $abiOK = $abi.ExitCode -eq 0 -and $abi.Out.Contains('LEGACY_SIGNATURES_VERIFIED_ONLY')
    if (-not $abiOK) { throw 'DS0101 native ABI mismatch' }
    $native = Invoke-Native $hostExe @('legacy-gencode', $copyBin, '--product', 'itmcIEC')
    $match = [regex]::Match($native.Out, '(?im)注册申请号\s*[:：]\s*([0-9A-Fa-f]{32,})')
    if ($native.ExitCode -ne 0 -or -not $match.Success) {
        throw 'Native request generation failed'
    }
    $request = $match.Groups[1].Value
    $generated = Invoke-Native 'java' @('-cp', $javaDir, 'DS0101OfflineProbe') 35 $request
    $candidate = $generated.Out.Trim()
    $codeOK = $generated.ExitCode -eq 0 -and $candidate -match '^[0-9a-fA-F]{64,}$'
    if (-not $codeOK) { throw 'Offline code self-check failed' }

    # Original target untouched: all native writes are confined to the copy.
    # Vendor exception output is deliberately discarded (may embed identifiers).
    $submitted = Invoke-Native $hostExe @(
        'legacy-doreg', $copyBin, '--product', 'itmcIEC',
        '--seq', $request, '--code', $candidate) 45
    $accepted = $submitted.ExitCode -eq 0 -and $submitted.Out.Contains('LEGACY_NATIVE_DOREG_ACCEPTED')
    $verified = Invoke-Native $hostExe @(
        'legacy-verify', $copyBin, '--product', 'itmcIEC') 45
    $valid = $verified.ExitCode -eq 0 -and $verified.Out.Contains('LEGACY_NATIVE_CHECKREG_OK')

    $after = Snapshot $copy
    $changed = @((@($before.Keys) + @($after.Keys)) | Sort-Object -Unique | Where-Object {
        $before[$_] -ne $after[$_]
    })
    $afterSource = Snapshot $site
    $untouched = $beforeSource.Count -eq $afterSource.Count
    foreach ($name in $beforeSource.Keys) {
        if ($beforeSource[$name] -ne $afterSource[$name]) { $untouched = $false; break }
    }
    if (-not $untouched) { throw 'Source fixture was changed' }
    if ($valid -and -not $accepted) { throw 'Verifier accepted without native registration' }
    [ordered]@{
        product = 'DS0101'
        isolatedSite = $true
        sourceSiteUntouched = $untouched
        signatureProbePassed = $abiOK
        nativeRequestGenerated = $true
        offlineCodeSelfCheckPassed = $codeOK
        nativeDoRegistryAccepted = $accepted
        nativeCheckReInfoAndWebCheckRegPassed = $valid
        nativeDoRegistryExitCode = $submitted.ExitCode
        nativeVerifyExitCode = $verified.ExitCode
        nativeRegistrationTimedOut = $submitted.Timeout
        nativeVerificationTimedOut = $verified.Timeout
        copiedSiteFilesChanged = @($changed)
        productionIisVerified = $false
        note = 'Sanitized private fixture only; no production activation or IIS validation.'
    } | ConvertTo-Json -Depth 4
} finally {
    Remove-Item -LiteralPath $temp -Force -Recurse -ErrorAction SilentlyContinue
}
