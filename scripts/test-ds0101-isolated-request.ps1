[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SiteRoot,
    [string]$ToolRoot = '',
    [switch]$KeepIsolatedCopy
)
# DS0101 test-only preflight. Never calls DoRegistry or changes the input site.
# Run on Windows with the CI artifact, not on a running production IIS directory.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function HashOf([string]$p) {
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { return '<absent>' }
    return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash
}
function RootFrom([string]$p) {
    $full = (Resolve-Path -LiteralPath $p -ErrorAction Stop).Path
    if ((Split-Path $full -Leaf) -ieq 'bin') { return Split-Path $full -Parent }
    return $full
}
function NativeStage([string]$exe,[string[]]$arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        # A nonzero exit and stderr can be expected for incompatible binaries.
        # Capture that result instead of terminating before reporting it.
        $ErrorActionPreference = 'Continue'
        $output = & $exe @arguments 2>&1 | Out-String
        $exitCode = $LASTEXITCODE
        return @{ ExitCode = $exitCode; Text = [string]$output }
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}
$root = RootFrom $SiteRoot
$bin = Join-Path $root 'bin'
if (-not (Test-Path -LiteralPath $bin -PathType Container)) {
    throw 'DS0101 site bin directory not found'
}
$config = Join-Path $root 'config.xml'
if (-not (Test-Path -LiteralPath $config -PathType Leaf) -or
    (Get-Content -LiteralPath $config -Raw) -notmatch '(?i)<SoftVersionID>\s*DS0101\s*</SoftVersionID>') {
    throw 'Only verified DS0101 site layouts are supported by this preflight'
}
if (-not $ToolRoot) { $ToolRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path }
$nativeDir = Join-Path $ToolRoot 'LicenseRecover.NET'
$hostExe = Join-Path $nativeDir 'LicenseRecover.NET.AspNetHost.exe'
$bridgeDll = Join-Path $nativeDir 'LicenseRecover.NET.AspNetBridge.dll'
$legacyDll = Join-Path $nativeDir 'LicenseRecover.NET.Legacy.dll'
foreach ($required in @($hostExe,$bridgeDll,$legacyDll)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw ('Test tool file missing: ' + (Split-Path $required -Leaf))
    }
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ('DS0101-isolated-' + [guid]::NewGuid().ToString('N'))
$copy = Join-Path $temp 'site'
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
    Copy-Item -LiteralPath $root -Destination $copy -Recurse -Force
    $testBin = Join-Path $copy 'bin'
    $watched = @('config.xml','Register.xml','notRegFile.xml','bin\config.xml',
                 'bin\Register.xml','bin\notRegFile.xml')
    $before = @{}
    foreach ($path in $watched) { $before[$path] = HashOf (Join-Path $copy $path) }
    $probe = NativeStage -exe $hostExe -arguments @('legacy-probe',$testBin)
    $gencode = @{ ExitCode = -1; Text = '' }
    if ($probe.ExitCode -eq 0 -and $probe.Text -match 'LEGACY_SIGNATURES_VERIFIED_ONLY') {
        $gencode = NativeStage -exe $hostExe -arguments @('legacy-gencode',$testBin,'--product','itmcIEC')
    }
    $after = @{}
    foreach ($path in $watched) { $after[$path] = HashOf (Join-Path $copy $path) }
    $changed = @($watched | Where-Object { $before[$_] -ne $after[$_] })
    # Never save the request number, machine information, vendor exceptions
    # or the copied config contents in a diagnostic result.
    $match = [regex]::Match($gencode.Text,'(?im)注册申请号\s*[:：]\s*([0-9A-Fa-f]+)')
    $requestFormat = $match.Success -and $match.Groups[1].Value.Length -ge 32
    $report = [ordered]@{
        product = 'DS0101'
        sourceSiteUntouched = $true
        signatureProbePassed = ($probe.ExitCode -eq 0 -and $probe.Text -match 'LEGACY_SIGNATURES_VERIFIED_ONLY')
        nativeRequestGenerated = ($gencode.ExitCode -eq 0 -and
                                  $gencode.Text -match 'LEGACY_NATIVE_REQUEST_ONLY' -and $requestFormat)
        probeExitCode = $probe.ExitCode
        requestExitCode = $gencode.ExitCode
        copiedSiteFilesChanged = @($changed)
        siteHasOriginalOfflineCache = (Test-Path -LiteralPath (Join-Path $root 'notRegFile.xml') -PathType Leaf)
        actualLicenseVerified = $false
        note = 'Isolated request-path diagnostic only; no DoRegistry, CheckReInfo, CheckReg or activation was attempted.'
    }
    $report | ConvertTo-Json -Depth 4
}
finally {
    if ($KeepIsolatedCopy) {
        Write-Warning "Isolated test copy retained at: $copy (contains original site files)"
    } else {
        Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
    }
}
