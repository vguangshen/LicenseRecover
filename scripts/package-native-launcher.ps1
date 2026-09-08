[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildRoot = Join-Path $repoRoot 'build'
$nativeDir = Join-Path $buildRoot 'native-launcher'
$source = Join-Path $repoRoot 'src/native/LicenseRecoverGUI.c'
$resource = Join-Path $repoRoot 'src/native/LicenseRecoverGUI.rc'
$launcher = Join-Path $nativeDir 'LicenseRecoverGUI.exe'
$resourceObj = Join-Path $nativeDir 'LicenseRecoverGUI-res.o'
$portableZip = Join-Path $buildRoot 'LicenseRecover-latest.zip'
$updateZip = Join-Path $buildRoot 'LicenseRecover-update.zip'
$checksumFile = Join-Path $buildRoot 'SHA256SUMS.txt'

function Require-Command([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        throw "Missing native launcher tool: $Name. CI installs gcc/binutils-mingw-w64-x86-64 before this step."
    }
    return $cmd.Source
}

function Invoke-Checked([string]$Command, [object[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command"
    }
}

function Repack-WithLauncher([string]$Archive, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Archive)) { throw "Missing $Label archive: $Archive" }
    $temp = Join-Path $nativeDir ($Label + '-repack')
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
    New-Item -ItemType Directory -Path $temp -Force | Out-Null
    try {
        Expand-Archive -LiteralPath $Archive -DestinationPath $temp -Force
        Copy-Item -LiteralPath $launcher -Destination (Join-Path $temp 'LicenseRecoverGUI.exe') -Force

        $legacyExe = Join-Path $temp 'LicenseRecoverGUI-legacy.exe'
        if (Test-Path -LiteralPath $legacyExe) {
            Remove-Item -LiteralPath $legacyExe -Force
        }

        $rootBatFiles = @(Get-ChildItem -LiteralPath $temp -Filter '*.bat' -File)
        if ($Label -eq 'portable') {
            foreach ($bat in $rootBatFiles) {
                Remove-Item -LiteralPath $bat.FullName -Force
            }
        } elseif ($Label -eq 'update') {
            foreach ($bat in $rootBatFiles) {
                if ($bat.Name -ne 'run_gui.bat') {
                    Remove-Item -LiteralPath $bat.FullName -Force
                }
            }
            if (-not (Test-Path -LiteralPath (Join-Path $temp 'run_gui.bat'))) {
                throw 'Update archive must keep run_gui.bat as a transition trampoline for pre-v1.2.1 updaters.'
            }
        } else {
            throw "Unknown archive label: $Label"
        }

        Remove-Item -LiteralPath $Archive -Force
        Compress-Archive -Path (Join-Path $temp '*') -DestinationPath $Archive -CompressionLevel Optimal -Force
    } finally {
        if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
    }
}

if (-not (Test-Path -LiteralPath $source)) { throw "Missing native launcher source: $source" }
if (-not (Test-Path -LiteralPath $resource)) { throw "Missing native launcher resource: $resource" }
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'LicenseRecoverGUI.ico'))) { throw 'Missing LicenseRecoverGUI.ico.' }

$gcc = Require-Command 'x86_64-w64-mingw32-gcc'
$windres = Require-Command 'x86_64-w64-mingw32-windres'
$objdump = Require-Command 'x86_64-w64-mingw32-objdump'
$strings = Require-Command 'x86_64-w64-mingw32-strings'

if (Test-Path -LiteralPath $nativeDir) { Remove-Item -LiteralPath $nativeDir -Recurse -Force }
New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null

Write-Host '== Build Windows native GUI launcher =='
Push-Location $repoRoot
try {
    Invoke-Checked $windres @('src/native/LicenseRecoverGUI.rc', '-O', 'coff', '-o', $resourceObj)
    Invoke-Checked $gcc @(
        '-std=c99', '-Os', '-s', '-fno-ident', '-municode', '-mwindows', '-static-libgcc',
        'src/native/LicenseRecoverGUI.c', $resourceObj,
        '-o', $launcher, '-lshell32', '-luser32', '-lwininet'
    )
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $launcher)) { throw 'Native launcher EXE was not created.' }
$exeLength = (Get-Item -LiteralPath $launcher).Length
if ($exeLength -lt 10240 -or $exeLength -gt 2097152) {
    throw "Unexpected native launcher size: $exeLength bytes"
}

$headers = (& $objdump -f $launcher | Out-String) + (& $objdump -p $launcher | Out-String)
if ($LASTEXITCODE -ne 0 -or $headers.IndexOf('pei-x86-64', [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw 'Native launcher is not a Windows x64 PE executable.'
}
if ($headers.IndexOf('Windows GUI', [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw 'Native launcher is not using the Windows GUI subsystem.'
}

$wideStrings = (& $strings -el $launcher | Out-String)
foreach ($required in @(
    'LicenseRecoverModernGUILauncherUiPatch',
    'LicenseRecoverOverlay.jar',
    'LicenseRecoverGUI.jar',
    'jre\bin\javaw.exe',
    '--native-download',
    '-Djava.net.preferIPv4Stack=true'
)) {
    if ($wideStrings.IndexOf($required, [StringComparison]::Ordinal) -lt 0) {
        throw "Native launcher binary is missing required runtime reference: $required"
    }
}
Write-Host "Verified native launcher: $launcher ($exeLength bytes)"

Write-Host 'Injecting native launcher and consolidating user entry points...'
Repack-WithLauncher $updateZip 'update'
Repack-WithLauncher $portableZip 'portable'

$portableCheck = Join-Path $nativeDir 'portable-check'
$updateCheck = Join-Path $nativeDir 'update-check'
Expand-Archive -LiteralPath $portableZip -DestinationPath $portableCheck -Force
Expand-Archive -LiteralPath $updateZip -DestinationPath $updateCheck -Force
foreach ($dir in @($portableCheck, $updateCheck)) {
    if (-not (Test-Path -LiteralPath (Join-Path $dir 'LicenseRecoverGUI.exe'))) {
        throw "Repacked archive is missing LicenseRecoverGUI.exe: $dir"
    }
    if (Test-Path -LiteralPath (Join-Path $dir 'LicenseRecoverGUI-legacy.exe')) {
        throw "Repacked archive still contains LicenseRecoverGUI-legacy.exe: $dir"
    }
    $rootExes = @(Get-ChildItem -LiteralPath $dir -Filter '*.exe' -File)
    if ($rootExes.Count -ne 1 -or $rootExes[0].Name -ne 'LicenseRecoverGUI.exe') {
        throw "Archive must expose exactly one root EXE entry point: $dir"
    }
}

$portableBats = @(Get-ChildItem -LiteralPath $portableCheck -Filter '*.bat' -File)
if ($portableBats.Count -ne 0) {
    throw 'Portable archive must not expose BAT launchers; LicenseRecoverGUI.exe is the only user entry point.'
}
$updateBats = @(Get-ChildItem -LiteralPath $updateCheck -Filter '*.bat' -File)
if ($updateBats.Count -ne 1 -or $updateBats[0].Name -ne 'run_gui.bat') {
    throw 'Slim update archive must contain only run_gui.bat as the pre-v1.2.1 transition trampoline.'
}
$compatLauncherText = Get-Content -LiteralPath (Join-Path $updateCheck 'run_gui.bat') -Raw
if ($compatLauncherText.IndexOf('LicenseRecoverGUI.exe', [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw 'Compatibility run_gui.bat must relaunch LicenseRecoverGUI.exe.'
}
if ($compatLauncherText.IndexOf('&&', [StringComparison]::Ordinal) -ge 0) {
    throw 'Compatibility run_gui.bat must not use chained && syntax; older update restarts must be cmd-safe.'
}
if (-not (Test-Path -LiteralPath (Join-Path $portableCheck 'jre/bin/javaw.exe'))) {
    throw 'Portable archive lost the embedded Java runtime.'
}
if (Test-Path -LiteralPath (Join-Path $updateCheck 'jre')) {
    throw 'Slim update archive unexpectedly contains the embedded JRE.'
}

$portableSha = (Get-FileHash -LiteralPath $portableZip -Algorithm SHA256).Hash.ToLowerInvariant()
$updateSha = (Get-FileHash -LiteralPath $updateZip -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath $checksumFile -Encoding ASCII -Value @(
    ($portableSha + '  LicenseRecover-latest.zip'),
    ($updateSha + '  LicenseRecover-update.zip')
)
Write-Host 'Native launcher packaged. Portable exposes one EXE only; update keeps one compatibility BAT for old updaters.'
Write-Host "Portable SHA-256: $portableSha"
Write-Host "Update   SHA-256: $updateSha"
