[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildRoot = Join-Path $repoRoot 'build'
$verifyDir = Join-Path $buildRoot 'verify'
$testDir = Join-Path $buildRoot 'test'
$overlayDir = Join-Path $buildRoot 'overlay-classes'
$distDir = Join-Path $buildRoot 'dist'
$overlayJar = Join-Path $buildRoot 'LicenseRecoverOverlay.jar'
$archive = Join-Path $buildRoot 'LicenseRecover-test.zip'
$mainSourceDir = Join-Path $repoRoot 'src/main/java'
$testSourceDir = Join-Path $repoRoot 'src/test/java'
$guiRuntimeJar = Join-Path $repoRoot 'LicenseRecoverGUI.jar'
$classpathSeparator = [IO.Path]::PathSeparator

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE: $Command"
    }
}

function Assert-TextContains {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Expected
    )

    $content = Get-Content -LiteralPath $Path -Raw
    if ($content.IndexOf($Expected, [StringComparison]::Ordinal) -lt 0) {
        throw "Expected '$Expected' in $Path"
    }
}

Write-Host '== LicenseRecover source verification =='
Write-Host "Repository: $repoRoot"

$rootJava = @(Get-ChildItem -LiteralPath $repoRoot -Filter '*.java' -File)
if ($rootJava.Count -ne 0) {
    throw 'Java source files must live under src/main/java; root-level .java files were found.'
}

$mainSources = @(Get-ChildItem -LiteralPath $mainSourceDir -Filter '*.java' -File | Sort-Object Name | ForEach-Object { $_.FullName })
if ($mainSources.Count -eq 0) {
    throw 'No Java sources found under src/main/java.'
}

$smokeTest = Join-Path $testSourceDir 'RefactorSmokeTest.java'
if (-not (Test-Path -LiteralPath $smokeTest)) {
    throw 'Missing src/test/java/RefactorSmokeTest.java.'
}

foreach ($dir in @($verifyDir, $testDir, $overlayDir, $distDir)) {
    if (Test-Path -LiteralPath $dir) {
        Remove-Item -LiteralPath $dir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
if (Test-Path -LiteralPath $overlayJar) { Remove-Item -LiteralPath $overlayJar -Force }

Write-Host "Compiling $($mainSources.Count) Java source files with Java 8-compatible sources..."
$compileMainArgs = @('-encoding', 'UTF-8', '-cp', $guiRuntimeJar, '-d', $verifyDir) + $mainSources
Invoke-External 'javac' @compileMainArgs

$testClasspath = $verifyDir + $classpathSeparator + $guiRuntimeJar
Invoke-External 'javac' '-encoding' 'UTF-8' '-cp' $testClasspath '-d' $testDir $smokeTest

Write-Host 'Running refactor smoke tests...'
$runtimeClasspath = $verifyDir + $classpathSeparator + $testDir + $classpathSeparator + $guiRuntimeJar
Invoke-External 'java' '-cp' $runtimeClasspath 'RefactorSmokeTest'

Write-Host 'Building deterministic runtime overlay...'
$classPrefixes = @(
    'AppDetector',
    'AppInfo',
    'BatchTarget',
    'ConfigSafety',
    'LicenseRecoverModernGUI',
    'OperationResult',
    'PatchSafety',
    'ProcessRunner',
    'SafeNetRemoverCLI',
    'SafetyBackup'
)
foreach ($prefix in $classPrefixes) {
    $matches = @(Get-ChildItem -Path (Join-Path $verifyDir ($prefix + '*.class')) -File)
    if ($matches.Count -eq 0) {
        throw "No compiled classes found for overlay prefix: $prefix"
    }
    foreach ($match in $matches) {
        Copy-Item -LiteralPath $match.FullName -Destination $overlayDir -Force
    }
}

$fixedTime = [DateTime]::ParseExact('1980-01-01 00:00:00', 'yyyy-MM-dd HH:mm:ss', [Globalization.CultureInfo]::InvariantCulture)
Get-ChildItem -LiteralPath $overlayDir -File | ForEach-Object { $_.LastWriteTime = $fixedTime }
Invoke-External 'jar' 'cfM' $overlayJar '-C' $overlayDir '.'

$overlayEntries = @(Invoke-External 'jar' 'tf' $overlayJar)
if ($overlayEntries -notcontains 'LicenseRecoverModernGUI.class') {
    throw 'Overlay is missing LicenseRecoverModernGUI.class.'
}
if ($overlayEntries -notcontains 'SafeNetRemoverCLI.class') {
    throw 'Overlay is missing SafeNetRemoverCLI.class.'
}

Write-Host 'Assembling test distribution...'
$distributionFiles = @(
    'LicenseRecover.jar',
    'LicenseRecoverGUI.jar',
    'README.txt',
    'DEVELOPMENT.md',
    'PHASE1_REFACTOR.md',
    'PHASE2_UI.md',
    'PHASE3_STRUCTURE.md',
    'run.bat',
    'run_gui.bat',
    'run_gui_modern.bat',
    'run_gui_legacy.bat',
    'run_removenet.bat',
    'run_removenet_safe.bat',
    'run_removenet_legacy.bat',
    'LicenseRecoverGUI.ico',
    'virbox_keystream.bin'
)
foreach ($file in $distributionFiles) {
    Copy-Item -LiteralPath (Join-Path $repoRoot $file) -Destination $distDir -Force
}
Copy-Item -LiteralPath $overlayJar -Destination $distDir -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecover.NET') -Destination $distDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecoverGUI.exe') -Destination (Join-Path $distDir 'LicenseRecoverGUI-legacy.exe') -Force

Write-Host 'Verifying modern defaults and legacy fallbacks...'
Assert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverOverlay.jar'
Assert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverModernGUI'
Assert-TextContains (Join-Path $distDir 'run_gui_legacy.bat') 'LicenseRecoverGUI.jar'
Assert-TextContains (Join-Path $distDir 'run_removenet.bat') 'run_removenet_safe.bat'
Assert-TextContains (Join-Path $distDir 'run_removenet_safe.bat') 'LicenseRecoverOverlay.jar'
Assert-TextContains (Join-Path $distDir 'run_removenet_safe.bat') 'SafeNetRemoverCLI'

Write-Host 'Creating test distribution archive...'
Compress-Archive -Path (Join-Path $distDir '*') -DestinationPath $archive -CompressionLevel Optimal -Force
if (-not (Test-Path -LiteralPath $archive)) {
    throw 'Distribution archive was not created.'
}

Write-Host "Verified overlay: $overlayJar"
Write-Host "Verified distribution: $archive"
Write-Host 'ALL SOURCE VERIFICATION STEPS PASSED'
