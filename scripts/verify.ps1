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
$portableDistDir = Join-Path $buildRoot 'dist-portable'
$runtimeCacheDir = Join-Path $buildRoot 'runtime-jre'
$embeddedJreDir = Join-Path $runtimeCacheDir 'jre'
$runtimeZip = Join-Path $runtimeCacheDir 'LicenseRecover-jre8-win-x64.zip'
$runtimeChecksum = Join-Path $runtimeCacheDir 'LicenseRecover-jre8-win-x64.sha256'
$runtimeNotice = Join-Path $runtimeCacheDir 'JRE_SOURCE_NOTICE.txt'
$runtimeTag = 'runtime-corretto8-8.492.09.2-win-x64'
$runtimeBaseUrl = 'https://github.com/vguangshen/LicenseRecover/releases/download/' + $runtimeTag + '/'
$overlayJar = Join-Path $buildRoot 'LicenseRecoverOverlay.jar'
$archive = Join-Path $buildRoot 'LicenseRecover-latest.zip'
$updateArchive = Join-Path $buildRoot 'LicenseRecover-update.zip'
$checksumFile = Join-Path $buildRoot 'SHA256SUMS.txt'
$mainSourceDir = Join-Path $repoRoot 'src/main/java'
$testSourceDir = Join-Path $repoRoot 'src/test/java'
$guiRuntimeJar = Join-Path $repoRoot 'LicenseRecoverGUI.jar'
$versionFile = Join-Path $repoRoot 'VERSION.txt'
$classpathSeparator = [IO.Path]::PathSeparator

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][object[]]$ArgumentList
    )

    & $Command @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command"
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

function Ensure-EmbeddedJre {
    $javaExe = Join-Path $embeddedJreDir 'bin/java.exe'
    $javawExe = Join-Path $embeddedJreDir 'bin/javaw.exe'
    if ((Test-Path -LiteralPath $javaExe) -and (Test-Path -LiteralPath $javawExe)) {
        Write-Host "Using cached embedded JRE: $embeddedJreDir"
        return
    }

    if (Test-Path -LiteralPath $runtimeCacheDir) {
        Remove-Item -LiteralPath $runtimeCacheDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $runtimeCacheDir -Force | Out-Null

    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if ($null -ne $gh -and -not [string]::IsNullOrWhiteSpace($env:GH_TOKEN)) {
        Write-Host "Downloading embedded JRE through GitHub CLI from $runtimeTag..."
        Invoke-External -Command $gh.Source -ArgumentList @(
            'release', 'download', $runtimeTag,
            '--repo', 'vguangshen/LicenseRecover',
            '--pattern', 'LicenseRecover-jre8-win-x64.zip',
            '--pattern', 'LicenseRecover-jre8-win-x64.sha256',
            '--pattern', 'JRE_SOURCE_NOTICE.txt',
            '--dir', $runtimeCacheDir
        )
    } else {
        Write-Host "Downloading embedded JRE from public runtime release $runtimeTag..."
        Invoke-WebRequest -Uri ($runtimeBaseUrl + 'LicenseRecover-jre8-win-x64.zip') -OutFile $runtimeZip -UseBasicParsing
        Invoke-WebRequest -Uri ($runtimeBaseUrl + 'LicenseRecover-jre8-win-x64.sha256') -OutFile $runtimeChecksum -UseBasicParsing
        Invoke-WebRequest -Uri ($runtimeBaseUrl + 'JRE_SOURCE_NOTICE.txt') -OutFile $runtimeNotice -UseBasicParsing
    }

    foreach ($required in @($runtimeZip, $runtimeChecksum, $runtimeNotice)) {
        if (-not (Test-Path -LiteralPath $required)) {
            throw "Embedded JRE runtime asset is missing: $required"
        }
    }

    $checksumText = Get-Content -LiteralPath $runtimeChecksum -Raw
    $match = [regex]::Match($checksumText, '(?im)^\s*([0-9a-f]{64})\s+\*?LicenseRecover-jre8-win-x64\.zip\s*$')
    if (-not $match.Success) {
        throw 'Embedded JRE checksum file is invalid.'
    }
    $expected = $match.Groups[1].Value.ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $runtimeZip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expected -ne $actual) {
        throw "Embedded JRE SHA-256 mismatch. Expected $expected, got $actual"
    }

    Expand-Archive -LiteralPath $runtimeZip -DestinationPath $runtimeCacheDir -Force
    foreach ($required in @(
        (Join-Path $embeddedJreDir 'bin/java.exe'),
        (Join-Path $embeddedJreDir 'bin/javaw.exe'),
        (Join-Path $embeddedJreDir 'LICENSE'),
        (Join-Path $embeddedJreDir 'ASSEMBLY_EXCEPTION'),
        (Join-Path $embeddedJreDir 'THIRD_PARTY_README')
    )) {
        if (-not (Test-Path -LiteralPath $required)) {
            throw "Embedded JRE is incomplete: $required"
        }
    }
    Write-Host "Embedded JRE verified: Amazon Corretto 8.492.09.2 / 1.8.0_492-b09"
}

Write-Host '== LicenseRecover source verification =='
Write-Host "Repository: $repoRoot"

$rootJava = @(Get-ChildItem -LiteralPath $repoRoot -Filter '*.java' -File)
if ($rootJava.Count -ne 0) {
    throw 'Java source files must live under src/main/java; root-level .java files were found.'
}

$legacyRootArchive = Join-Path $repoRoot 'LicenseRecover-latest.zip'
if (Test-Path -LiteralPath $legacyRootArchive) {
    throw 'LicenseRecover-latest.zip must be generated under build/ and published as a Release asset, not tracked at repository root.'
}

if (-not (Test-Path -LiteralPath $versionFile)) {
    throw 'Missing VERSION.txt.'
}
$version = (Get-Content -LiteralPath $versionFile -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') {
    throw "VERSION.txt must contain a semantic version such as 1.0.0; found '$version'."
}
$stableTag = 'v' + $version
$releaseNotesPath = Join-Path $repoRoot ('release-notes/' + $stableTag + '.md')
if (-not (Test-Path -LiteralPath $releaseNotesPath)) {
    throw "Missing release notes for ${stableTag}: $releaseNotesPath"
}
Write-Host "Stable version metadata: $stableTag"

$mainSources = @(Get-ChildItem -LiteralPath $mainSourceDir -Filter '*.java' -File | Sort-Object Name | ForEach-Object { $_.FullName })
if ($mainSources.Count -eq 0) {
    throw 'No Java sources found under src/main/java.'
}

$smokeTest = Join-Path $testSourceDir 'RefactorSmokeTest.java'
if (-not (Test-Path -LiteralPath $smokeTest)) {
    throw 'Missing src/test/java/RefactorSmokeTest.java.'
}

Ensure-EmbeddedJre

foreach ($dir in @($verifyDir, $testDir, $overlayDir, $distDir, $portableDistDir)) {
    if (Test-Path -LiteralPath $dir) {
        Remove-Item -LiteralPath $dir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}
foreach ($generated in @($archive, $updateArchive, $checksumFile, $overlayJar)) {
    if (Test-Path -LiteralPath $generated) {
        Remove-Item -LiteralPath $generated -Force
    }
}

Write-Host "Compiling $($mainSources.Count) Java source files with Java 8-compatible sources..."
$compileMainArgs = @('-encoding', 'UTF-8', '-cp', $guiRuntimeJar, '-d', $verifyDir) + $mainSources
Invoke-External -Command 'javac' -ArgumentList $compileMainArgs

$testClasspath = $verifyDir + $classpathSeparator + $guiRuntimeJar
$compileTestArgs = @('-encoding', 'UTF-8', '-cp', $testClasspath, '-d', $testDir, $smokeTest)
Invoke-External -Command 'javac' -ArgumentList $compileTestArgs

Write-Host 'Running refactor smoke tests...'
$runtimeClasspath = $verifyDir + $classpathSeparator + $testDir + $classpathSeparator + $guiRuntimeJar
$runTestArgs = @('-cp', $runtimeClasspath, 'RefactorSmokeTest')
Invoke-External -Command 'java' -ArgumentList $runTestArgs

Write-Host 'Verifying LicenseRecover.NET helper command surface...'
$nativeHelper = Join-Path $repoRoot 'LicenseRecover.NET/LicenseRecover.NET.exe'
if (-not (Test-Path -LiteralPath $nativeHelper)) { throw 'Missing LicenseRecover.NET.exe.' }
$nativeBytes = [IO.File]::ReadAllBytes($nativeHelper)
$nativeAscii = [Text.Encoding]::ASCII.GetString($nativeBytes)
$nativeUnicode = [Text.Encoding]::Unicode.GetString($nativeBytes)
foreach ($commandName in @('gencode','doreg','verify')) {
    if (($nativeAscii.IndexOf($commandName, [StringComparison]::OrdinalIgnoreCase) -lt 0) -and
        ($nativeUnicode.IndexOf($commandName, [StringComparison]::OrdinalIgnoreCase) -lt 0)) {
        throw "LicenseRecover.NET helper command surface is missing: $commandName"
    }
}

Write-Host 'Building uppercase ITMC.Regedit modern native helper adapter...'
$modernNativeHelper = Join-Path $buildRoot 'LicenseRecover.NET.Modern.exe'
$aspNetHostSource = Join-Path $repoRoot 'src/dotnet/LicenseRecover.AspNetHost.cs'
$aspNetHostHelper = Join-Path $buildRoot 'LicenseRecover.NET.AspNetHost.exe'
Invoke-External -Command 'python3' -ArgumentList @(
    (Join-Path $repoRoot 'scripts/prepare-dotnet-modern-helper.py'),
    $nativeHelper,
    $modernNativeHelper
)
if (-not (Test-Path -LiteralPath $modernNativeHelper)) { throw 'Modern .NET helper adapter was not created.' }

Write-Host 'Building lowercase itmcRegedit ASP.NET host helper...'
if (-not (Test-Path -LiteralPath $aspNetHostSource)) { throw 'Missing src/dotnet/LicenseRecover.AspNetHost.cs.' }
$mcs = Get-Command mcs -ErrorAction SilentlyContinue
if ($null -eq $mcs) { throw 'Mono mcs compiler is required to build the ASP.NET host helper.' }
Invoke-External -Command $mcs.Source -ArgumentList @(
    '-nologo', '-target:exe', '-optimize+', '-r:System.Web.dll',
    ('-out:' + $aspNetHostHelper), $aspNetHostSource
)
if (-not (Test-Path -LiteralPath $aspNetHostHelper)) { throw 'ASP.NET host helper was not created.' }
$aspHostBytes = [IO.File]::ReadAllBytes($aspNetHostHelper)
$aspHostAscii = [Text.Encoding]::ASCII.GetString($aspHostBytes)
$aspHostUnicode = [Text.Encoding]::Unicode.GetString($aspHostBytes)
foreach ($marker in @('SimpleWorkerRequest','HttpContext','LicenseRecover.NET.exe','ASPNET_HOST','ParseArgs','RunDirect')) {
    if (($aspHostAscii.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) -and
        ($aspHostUnicode.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0)) {
        throw "ASP.NET host helper is missing marker: $marker"
    }
}
$aspHostSourceText = Get-Content -LiteralPath $aspNetHostSource -Raw
if ($aspHostSourceText.Contains('assembly.EntryPoint')) { throw 'ASP.NET host must not re-enter helper EntryPoint/child-AppDomain dispatch.' }
if (-not $aspHostSourceText.Contains('helperDispatch=RunDirect/target-AppDomain')) { throw 'ASP.NET host target-AppDomain dispatch marker is missing.' }

Write-Host 'Verifying directory-only registration identity policy...'
$coreSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecover.java') -Raw
$planSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIJavaPlan.java') -Raw
$autoSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIAutoRecovery.java') -Raw
$legacyNetSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LegacyDotNetProtocol.java') -Raw
$legacyGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverGUI.java') -Raw
$modernGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUI.java') -Raw
$uiPatchSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIUiPatchLauncher.java') -Raw
if ($coreSource.Contains('genRegisterCode(seq, registerPid)')) { throw 'Java gencode can still omit directory-derived RegStr.' }
if ($coreSource.Contains('+ ALL_NUMS')) { throw 'Java authorization generation still uses catch-all RegStr.' }
if ($autoSource.Contains('if (blank(regStr)) regStr = d.versionId')) { throw '.NET one-click still falls back to VersionID for RegStr.' }
if ($legacyNetSource.Contains('PRODUCT_NAME = "itmcIEC"')) { throw 'Legacy .NET protocol still has a fixed executable product name.' }
if ($legacyGuiSource.Contains('LegacyDotNetProtocol.PRODUCT_NAME')) { throw 'Legacy GUI still uses a fixed .NET product identity.' }
if ($legacyGuiSource.Contains('+ ALL_NUMS')) { throw 'Legacy GUI still uses a catch-all Java RegStr.' }
if ($legacyGuiSource.Contains('productMain.isEmpty()) productMain =')) { throw 'Legacy GUI still assigns a default product identity.' }
if (-not $legacyGuiSource.Contains('plan.authorizationFamily, plan.regStr')) { throw 'Legacy GUI Java gencode is not using directory-derived family + RegStr.' }
if (-not $legacyGuiSource.Contains('目标 ITMC.Web.dll 未解析出 ProName')) { throw 'Legacy GUI .NET fail-closed product guard is missing.' }
if (-not $coreSource.Contains('LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect')) { throw 'Java core is not using the fail-closed directory plan.' }
if (-not $autoSource.Contains('VersionID/default-list fallback is disabled')) { throw '.NET fail-closed guard is missing.' }
if (-not $autoSource.Contains('LicenseRecover.NET.AspNetHost.exe') -or -not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw '.NET one-click must use the ASP.NET host for lowercase chain plus uppercase fallback adapter.' }
if (-not $autoSource.Contains('selectDotNetRegeditAssembly') -or -not $autoSource.Contains('detectLowercaseDotNetRegistrationProduct')) { throw '.NET target-owned lowercase-first registration-chain policy is missing.' }
if (-not $autoSource.Contains('registrationProduct')) { throw '.NET app product and lowercase registration crypto family are not separated.' }
if (-not $autoSource.Contains('hasDotNetAuthorizationConfigStructure')) { throw 'Modern .NET pre-block cannot distinguish unrelated config.xml files.' }
if ($autoSource.Contains('String updated = putElement(original, "Service", BLOCK_ENDPOINT);\n            validateXml(updated);') -and -not $autoSource.Contains('[pre-block-skip]')) { throw 'Modern .NET pre-block still aborts on unrelated config.xml files.' }
if (-not $modernGuiSource.Contains('appendPersistentLog')) { throw 'Modern GUI persistent diagnostics are missing.' }
if (-not $uiPatchSource.Contains('appendPersistentOneClickLog')) { throw 'One-click overlay persistent diagnostics are missing.' }
if (-not $uiPatchSource.Contains('showOneClickFailureDialog')) { throw 'Structured one-click failure dialog is missing.' }
if (-not $uiPatchSource.Contains('复制详情') -or -not $uiPatchSource.Contains('打开日志')) { throw 'One-click failure dialog diagnostic actions are missing.' }
if ($uiPatchSource.Contains('JOptionPane.showMessageDialog(frame, result.message')) { throw 'One-click failure still renders the raw native message in a one-line JOptionPane.' }
if (-not $planSource.Contains('hasDirectoryBinaryToken')) { throw 'Java target-binary identity proof is missing.' }
if (-not $coreSource.Contains('probeTargetRegStr')) { throw 'Java target RegisterMain.getRegInfo RegStr probe is missing.' }
if (-not $coreSource.Contains('static String toolRuntimeClasspath()')) { throw 'Shared secondary-JVM tool classpath helper is missing.' }
if (-not $coreSource.Contains('String toolCp = toolRuntimeClasspath();')) { throw 'Java secondary JVMs are not using the shared Overlay-first tool classpath.' }

$javaPlanSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIJavaPlan.java') -Raw
if ($javaPlanSource.Contains('FALLBACK_ALL_NUMS')) { throw 'Java plan still contains catch-all fallback RegStr.' }
if ($javaPlanSource.Contains('return "QT0420"')) { throw 'Java plan still synthesizes YT00129 RegStr.' }
if ($javaPlanSource.Contains('return "DS2406"')) { throw 'Java plan still synthesizes DS2406 RegStr.' }
if ($javaPlanSource.Contains('return "QT40101"')) { throw 'Java plan still synthesizes QT40101 RegStr.' }
if (-not $javaPlanSource.Contains('No product-family/runtime-id guessing here')) { throw 'Strict Java directory identity marker missing.' }

Write-Host 'Building deterministic runtime overlay...'
$classPrefixes = @(
    'AppDetector',
    'ExistingLocalRegStrProbe',
    'LegacyJavaRegistrationMetadata',
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
$coreMatches = @(Get-ChildItem -LiteralPath $verifyDir -Filter 'LicenseRecover*.class' -File |
    Where-Object { $_.Name -eq 'LicenseRecover.class' -or $_.Name.StartsWith('LicenseRecover$') })
if ($coreMatches.Count -eq 0) {
    throw 'No compiled LicenseRecover core classes found for overlay.'
}
foreach ($match in $coreMatches) {
    Copy-Item -LiteralPath $match.FullName -Destination $overlayDir -Force
}

$fixedTime = [DateTime]::ParseExact('1980-01-01 00:00:00', 'yyyy-MM-dd HH:mm:ss', [Globalization.CultureInfo]::InvariantCulture)
Get-ChildItem -LiteralPath $overlayDir -File | ForEach-Object { $_.LastWriteTime = $fixedTime }
$createOverlayArgs = @('cfM', $overlayJar, '-C', $overlayDir, '.')
Invoke-External -Command 'jar' -ArgumentList $createOverlayArgs

$listOverlayArgs = @('tf', $overlayJar)
$overlayEntries = @(Invoke-External -Command 'jar' -ArgumentList $listOverlayArgs)
if ($overlayEntries -notcontains 'LicenseRecoverModernGUI.class') {
    throw 'Overlay is missing LicenseRecoverModernGUI.class.'
}
if ($overlayEntries -notcontains 'LicenseRecoverModernGUILauncher.class') {
    throw 'Overlay is missing LicenseRecoverModernGUILauncher.class.'
}
if ($overlayEntries -notcontains 'LicenseRecoverModernGUIUpdateInstaller.class') {
    throw 'Overlay is missing LicenseRecoverModernGUIUpdateInstaller.class.'
}
if ($overlayEntries -notcontains 'SafeNetRemoverCLI.class') {
    throw 'Overlay is missing SafeNetRemoverCLI.class.'
}
if ($overlayEntries -notcontains 'ExistingLocalRegStrProbe.class') {
    throw 'Overlay is missing ExistingLocalRegStrProbe.class.'
}
if ($overlayEntries -notcontains 'LegacyJavaRegistrationMetadata.class') {
    throw 'Overlay is missing LegacyJavaRegistrationMetadata.class.'
}
if ($overlayEntries -notcontains 'LicenseRecover.class') {
    throw 'Overlay is missing updated LicenseRecover.class.'
}
if ($overlayEntries -notcontains 'LicenseRecoverModernGUIJavaPlan.class') {
    throw 'Overlay is missing LicenseRecoverModernGUIJavaPlan.class; Virbox/recovery child JVMs would fail.'
}
if ($overlayEntries -notcontains 'LicenseRecoverModernGUIAutoRecovery.class') {
    throw 'Overlay is missing LicenseRecoverModernGUIAutoRecovery.class.'
}

Write-Host 'Assembling application-only distribution...'
$distributionFiles = @(
    'LicenseRecover.jar',
    'LicenseRecoverGUI.jar',
    'README.md',
    'README.txt',
    'VERSION.txt',
    'CHANGELOG.md',
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
Copy-Item -LiteralPath $releaseNotesPath -Destination (Join-Path $distDir 'RELEASE_NOTES.md') -Force
Copy-Item -LiteralPath $overlayJar -Destination $distDir -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecover.NET') -Destination $distDir -Recurse -Force
$distNativeDir = Join-Path $distDir 'LicenseRecover.NET'
Copy-Item -LiteralPath $modernNativeHelper -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe') -Force
Copy-Item -LiteralPath $aspNetHostHelper -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.AspNetHost.exe') -Force
$nativeConfig = Join-Path $repoRoot 'LicenseRecover.NET/LicenseRecover.NET.exe.config'
if (Test-Path -LiteralPath $nativeConfig) {
    Copy-Item -LiteralPath $nativeConfig -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe.config') -Force
    Copy-Item -LiteralPath $nativeConfig -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.AspNetHost.exe.config') -Force
}
Copy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecoverGUI.exe') -Destination (Join-Path $distDir 'LicenseRecoverGUI-legacy.exe') -Force

Write-Host 'Verifying version metadata and launchers...'
$distVersion = (Get-Content -LiteralPath (Join-Path $distDir 'VERSION.txt') -Raw).Trim()
if ($distVersion -ne $version) {
    throw "Distribution version mismatch: expected $version, got $distVersion"
}
Assert-TextContains (Join-Path $distDir 'README.md') $stableTag
Assert-TextContains (Join-Path $distDir 'run_gui.bat') 'jre\bin\javaw.exe'
Assert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverOverlay.jar'
Assert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverModernGUILauncher'
Assert-TextContains (Join-Path $distDir 'run_gui_legacy.bat') 'LicenseRecoverGUI.jar'
Assert-TextContains (Join-Path $distDir 'run_removenet.bat') 'run_removenet_safe.bat'
Assert-TextContains (Join-Path $distDir 'run_removenet_safe.bat') 'LicenseRecoverOverlay.jar'
Assert-TextContains (Join-Path $distDir 'run_removenet_safe.bat') 'SafeNetRemoverCLI'

Write-Host 'Creating slim self-update archive...'
Compress-Archive -Path (Join-Path $distDir '*') -DestinationPath $updateArchive -CompressionLevel Optimal -Force
if (-not (Test-Path -LiteralPath $updateArchive)) {
    throw 'Slim update archive was not created.'
}

Write-Host 'Assembling portable distribution with embedded JRE...'
Copy-Item -Path (Join-Path $distDir '*') -Destination $portableDistDir -Recurse -Force
Copy-Item -LiteralPath $embeddedJreDir -Destination (Join-Path $portableDistDir 'jre') -Recurse -Force
Copy-Item -LiteralPath $runtimeNotice -Destination (Join-Path $portableDistDir 'JRE_SOURCE_NOTICE.txt') -Force
foreach ($required in @(
    (Join-Path $portableDistDir 'jre/bin/java.exe'),
    (Join-Path $portableDistDir 'jre/bin/javaw.exe'),
    (Join-Path $portableDistDir 'jre/LICENSE'),
    (Join-Path $portableDistDir 'jre/ASSEMBLY_EXCEPTION'),
    (Join-Path $portableDistDir 'jre/THIRD_PARTY_README'),
    (Join-Path $portableDistDir 'JRE_SOURCE_NOTICE.txt')
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Portable distribution is missing embedded JRE component: $required"
    }
}

Write-Host 'Creating portable release archive...'
Compress-Archive -Path (Join-Path $portableDistDir '*') -DestinationPath $archive -CompressionLevel Optimal -Force
if (-not (Test-Path -LiteralPath $archive)) {
    throw 'Portable distribution archive was not created.'
}

$sha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
$updateSha256 = (Get-FileHash -LiteralPath $updateArchive -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath $checksumFile -Value @(
    ($sha256 + '  LicenseRecover-latest.zip'),
    ($updateSha256 + '  LicenseRecover-update.zip')
) -Encoding ASCII
if (-not (Test-Path -LiteralPath $checksumFile)) {
    throw 'SHA256SUMS.txt was not created.'
}

Write-Host "Verified overlay: $overlayJar"
Write-Host "Verified portable distribution: $archive"
Write-Host "Verified slim update: $updateArchive"
Write-Host "Embedded runtime: Amazon Corretto 8.492.09.2 / 1.8.0_492-b09"
Write-Host "Stable version: $stableTag"
Write-Host "Portable SHA-256: $sha256"
Write-Host "Update SHA-256: $updateSha256"
Write-Host 'ALL SOURCE VERIFICATION STEPS PASSED'
