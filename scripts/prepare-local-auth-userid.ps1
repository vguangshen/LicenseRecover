[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourcePath = Join-Path $repoRoot 'src/main/java/LicenseRecover.java'
$testPath = Join-Path $repoRoot 'src/test/java/RefactorSmokeTest.java'
$guiRuntimeJar = Join-Path $repoRoot 'LicenseRecoverGUI.jar'
$cliJar = Join-Path $repoRoot 'LicenseRecover.jar'
$outDir = Join-Path $repoRoot 'build/cli-runtime-patch'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Replace-Required {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Old,
        [Parameter(Mandatory = $true)][string]$New,
        [Parameter(Mandatory = $true)][string]$Description
    )
    $index = $Text.IndexOf($Old, [StringComparison]::Ordinal)
    if ($index -lt 0) {
        throw "Unable to patch $Description; expected source anchor was not found."
    }
    return $Text.Substring(0, $index) + $New + $Text.Substring($index + $Old.Length)
}

Write-Host '== Prepare local authorization identity =='

$source = [IO.File]::ReadAllText($sourcePath, [Text.Encoding]::UTF8)

if ($source.IndexOf('static final String LOCAL_AUTH_USER_ID = "fwq";', [StringComparison]::Ordinal) -lt 0) {
    $source = Replace-Required $source `
        '    static boolean backupCfg = true;' `
        @' 
    static boolean backupCfg = true;

    /** 本地授权写入 regName 的运行时 UserID；与外层 WebSerUserID 是两个独立字段。 */
    static final String LOCAL_AUTH_USER_ID = "fwq";

    static void applyLocalAuthIdentity(RegeditInfo info) {
        if (info == null) throw new IllegalArgumentException("RegeditInfo 不能为空");
        info.setUserID(LOCAL_AUTH_USER_ID);
    }
'@ `
        'local authorization UserID constant/helper'
}

if ($source.IndexOf('        applyLocalAuthIdentity(info);', [StringComparison]::Ordinal) -lt 0) {
    $source = Replace-Required $source `
        '        RegeditInfo info = new RegeditInfo();' `
        "        RegeditInfo info = new RegeditInfo();`n        applyLocalAuthIdentity(info);" `
        'RegeditInfo local identity assignment'
}

if ($source.IndexOf('regName.UserID       = ', [StringComparison]::Ordinal) -lt 0) {
    $source = Replace-Required $source `
        '            System.out.println("  写 reg/regName      = " + (encrypted.length() > 48 ? encrypted.substring(0, 48) + "..." : encrypted));' `
        "            System.out.println(\"  写 reg/regName      = \" + (encrypted.length() > 48 ? encrypted.substring(0, 48) + \"...\" : encrypted));`n            System.out.println(\"  regName.UserID       = \" + LOCAL_AUTH_USER_ID);" `
        'dry-run local authorization UserID output'
}

if ($source.IndexOf('"  userID=" + gi.getUserID()', [StringComparison]::Ordinal) -lt 0) {
    $source = Replace-Required $source `
        '                        + "  maxCon=" + gi.getMaxCon());' `
        '                        + "  maxCon=" + gi.getMaxCon() + "  userID=" + gi.getUserID());' `
        'self-check UserID output'
}

[IO.File]::WriteAllText($sourcePath, $source, $utf8NoBom)

$test = [IO.File]::ReadAllText($testPath, [Text.Encoding]::UTF8)
if ($test.IndexOf('local authorization embeds fixed UserID=fwq', [StringComparison]::Ordinal) -lt 0) {
    $anchor = '        Path base = Files.createTempDirectory("licenserecover-smoke");'
    $replacement = @'
        Path base = Files.createTempDirectory("licenserecover-smoke");

        itmc.regedit.webservice.RegeditInfo localAuthIdentity =
                new itmc.regedit.webservice.RegeditInfo();
        LicenseRecover.applyLocalAuthIdentity(localAuthIdentity);
        check("fwq".equals(localAuthIdentity.getUserID()),
                "local authorization embeds fixed UserID=fwq");
'@
    $test = Replace-Required $test $anchor $replacement 'local authorization regression smoke test'
    [IO.File]::WriteAllText($testPath, $test, $utf8NoBom)
}

if (Test-Path -LiteralPath $outDir) {
    Remove-Item -LiteralPath $outDir -Recurse -Force
}
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$mainSources = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src/main/java') -Filter '*.java' -File |
        Sort-Object Name | ForEach-Object { $_.FullName })
if ($mainSources.Count -eq 0) { throw 'No Java sources found.' }

Write-Host "Compiling patched Java sources ($($mainSources.Count) files)..."
& javac -encoding UTF-8 -cp $guiRuntimeJar -d $outDir @mainSources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

$runtimeClasses = @(Get-ChildItem -LiteralPath $outDir -Filter 'LicenseRecover*.class' -File |
        Where-Object { $_.Name -eq 'LicenseRecover.class' -or $_.Name.StartsWith('LicenseRecover$') } |
        Sort-Object Name)
if ($runtimeClasses.Count -eq 0) { throw 'Compiled LicenseRecover runtime classes were not found.' }

Write-Host "Updating LicenseRecover.jar with $($runtimeClasses.Count) compiled CLI classes..."
foreach ($classFile in $runtimeClasses) {
    & jar uf $cliJar -C $outDir $classFile.Name
    if ($LASTEXITCODE -ne 0) { throw "jar update failed for $($classFile.Name)" }
}

$javapCode = (& javap -classpath $cliJar -c -p LicenseRecover | Out-String)
if ($LASTEXITCODE -ne 0 -or $javapCode.IndexOf('RegeditInfo.setUserID', [StringComparison]::Ordinal) -lt 0) {
    throw 'Patched LicenseRecover.jar does not call RegeditInfo.setUserID.'
}
$javapVerbose = (& javap -classpath $cliJar -v -p LicenseRecover | Out-String)
if ($LASTEXITCODE -ne 0 -or $javapVerbose.IndexOf('String fwq', [StringComparison]::Ordinal) -lt 0) {
    throw 'Patched LicenseRecover.jar does not contain the fwq local authorization identity.'
}

Write-Host 'Local authorization identity verified: RegeditInfo.UserID = fwq'
