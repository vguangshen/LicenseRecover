from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')


# 1) Secondary JVMs must prefer the transition-safe runtime jar.  Keep the
# legacy overlay as a fallback for older/full folders.
replace_once(
    'src/main/java/LicenseRecover.java',
    '''    /** Build the tool-side runtime classpath for every secondary JVM.\n     * New source lives in LicenseRecoverOverlay.jar, so it must precede the legacy core jar. */\n    static String toolRuntimeClasspath() {\n        return toolRuntimeClasspath(new File(jarDir()));\n    }\n\n    static String toolRuntimeClasspath(File toolDir) {\n        File dir = toolDir == null ? new File(".") : toolDir.getAbsoluteFile();\n        File core = new File(dir, "LicenseRecover.jar");\n        File overlay = new File(dir, "LicenseRecoverOverlay.jar");\n        if (overlay.isFile()) {\n            return overlay.getAbsolutePath() + File.pathSeparator + core.getAbsolutePath();\n        }\n        return core.getAbsolutePath();\n    }\n''',
    '''    /** Build the tool-side runtime classpath for every secondary JVM.\n     * LicenseRecoverRuntime.jar is a transition-safe copy of the verified overlay.\n     * It is preferred so an older, still-locked LicenseRecoverOverlay.jar cannot make\n     * VERSION.txt report a new build while secondary JVMs execute stale classes. */\n    static String toolRuntimeClasspath() {\n        return toolRuntimeClasspath(new File(jarDir()));\n    }\n\n    static String toolRuntimeClasspath(File toolDir) {\n        File dir = toolDir == null ? new File(".") : toolDir.getAbsoluteFile();\n        File core = new File(dir, "LicenseRecover.jar");\n        File runtime = new File(dir, "LicenseRecoverRuntime.jar");\n        File overlay = new File(dir, "LicenseRecoverOverlay.jar");\n        if (runtime.isFile()) {\n            return runtime.getAbsolutePath() + File.pathSeparator + core.getAbsolutePath();\n        }\n        if (overlay.isFile()) {\n            return overlay.getAbsolutePath() + File.pathSeparator + core.getAbsolutePath();\n        }\n        return core.getAbsolutePath();\n    }\n''',
    'LicenseRecover runtime classpath')


# 2) Native launcher prefers the new runtime copy.  This is crucial for the
# v1.2.46 -> next-release transition: the old updater can ADD this new filename
# even if an old overlay jar was the file that became stale/locked.
replace_once(
    'src/native/LicenseRecoverGUI.c',
    '''static const wchar_t *kMainClass = L"LicenseRecoverModernGUILauncherUiPatch";\nstatic const wchar_t *kOverlayJar = L"LicenseRecoverOverlay.jar";\nstatic const wchar_t *kGuiJar = L"LicenseRecoverGUI.jar";\n''',
    '''static const wchar_t *kMainClass = L"LicenseRecoverModernGUILauncherUiPatch";\nstatic const wchar_t *kRuntimeJar = L"LicenseRecoverRuntime.jar";\nstatic const wchar_t *kOverlayJar = L"LicenseRecoverOverlay.jar";\nstatic const wchar_t *kGuiJar = L"LicenseRecoverGUI.jar";\n''',
    'native constants')

replace_once(
    'src/native/LicenseRecoverGUI.c',
    '''    wchar_t dir[32768], javaExe[32768], overlay[32768], guiJar[32768], classpath[65536];\n    if (!get_exe_dir(dir, ARRAY_LEN(dir))) {\n        show_error(L"无法确定 LicenseRecover 安装目录。");\n        return 2;\n    }\n    if (!join_path(overlay, ARRAY_LEN(overlay), dir, kOverlayJar) || !file_exists(overlay)) {\n        show_error(L"缺少 LicenseRecoverOverlay.jar。请使用完整发行包或重新执行软件更新。");\n        return 3;\n    }\n    if (!join_path(guiJar, ARRAY_LEN(guiJar), dir, kGuiJar) || !file_exists(guiJar)) {\n''',
    '''    wchar_t dir[32768], javaExe[32768], runtimeJar[32768], overlay[32768], guiJar[32768], classpath[65536];\n    const wchar_t *activeToolJar = NULL;\n    if (!get_exe_dir(dir, ARRAY_LEN(dir))) {\n        show_error(L"无法确定 LicenseRecover 安装目录。");\n        return 2;\n    }\n    if (join_path(runtimeJar, ARRAY_LEN(runtimeJar), dir, kRuntimeJar) && file_exists(runtimeJar)) {\n        activeToolJar = runtimeJar;\n    } else if (join_path(overlay, ARRAY_LEN(overlay), dir, kOverlayJar) && file_exists(overlay)) {\n        activeToolJar = overlay;\n    } else {\n        show_error(L"缺少 LicenseRecoverRuntime.jar / LicenseRecoverOverlay.jar。请使用完整发行包或重新执行软件更新。");\n        return 3;\n    }\n    if (!join_path(guiJar, ARRAY_LEN(guiJar), dir, kGuiJar) || !file_exists(guiJar)) {\n''',
    'native active runtime selection')

replace_once(
    'src/native/LicenseRecoverGUI.c',
    '''    if (_snwprintf(classpath, ARRAY_LEN(classpath), L"%ls;%ls", overlay, guiJar) < 0) {\n''',
    '''    if (_snwprintf(classpath, ARRAY_LEN(classpath), L"%ls;%ls", activeToolJar, guiJar) < 0) {\n''',
    'native classpath')


# 3) Legacy BAT trampoline uses the same preference when the native EXE is
# absent in an incomplete folder.
replace_once(
    'run_gui.bat',
    '''if not exist "%~dp0LicenseRecoverOverlay.jar" (\n  echo [LicenseRecover] LicenseRecoverOverlay.jar is missing.\n  exit /b 1\n)\n\nstart "" "%JAVA%" -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=true -Dsun.java2d.noddraw=true -cp "%~dp0LicenseRecoverOverlay.jar;%~dp0LicenseRecoverGUI.jar" LicenseRecoverModernGUILauncherUiPatch %*\n''',
    '''set "TOOLJAR=%~dp0LicenseRecoverRuntime.jar"\nif not exist "%TOOLJAR%" set "TOOLJAR=%~dp0LicenseRecoverOverlay.jar"\nif not exist "%TOOLJAR%" (\n  echo [LicenseRecover] LicenseRecoverRuntime.jar / LicenseRecoverOverlay.jar is missing.\n  exit /b 1\n)\n\nstart "" "%JAVA%" -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=true -Dsun.java2d.noddraw=true -cp "%TOOLJAR%;%~dp0LicenseRecoverGUI.jar" LicenseRecoverModernGUILauncherUiPatch %*\n''',
    'compat batch runtime selection')


# 4) Build a byte-identical transition-safe runtime jar and include it in both
# the slim updater and portable package.
replace_once(
    'scripts/verify.ps1',
    '''$overlayJar = Join-Path $buildRoot 'LicenseRecoverOverlay.jar'\n$archive = Join-Path $buildRoot 'LicenseRecover-latest.zip'\n''',
    '''$overlayJar = Join-Path $buildRoot 'LicenseRecoverOverlay.jar'\n$runtimeJar = Join-Path $buildRoot 'LicenseRecoverRuntime.jar'\n$archive = Join-Path $buildRoot 'LicenseRecover-latest.zip'\n''',
    'verify runtime variable')

replace_once(
    'scripts/verify.ps1',
    '''Invoke-External -Command 'jar' -ArgumentList $createOverlayArgs\n\n$listOverlayArgs = @('tf', $overlayJar)\n''',
    '''Invoke-External -Command 'jar' -ArgumentList $createOverlayArgs\nCopy-Item -LiteralPath $overlayJar -Destination $runtimeJar -Force\nif ((Get-FileHash -LiteralPath $overlayJar -Algorithm SHA256).Hash -ne\n    (Get-FileHash -LiteralPath $runtimeJar -Algorithm SHA256).Hash) {\n    throw 'LicenseRecoverRuntime.jar must be byte-identical to LicenseRecoverOverlay.jar.'\n}\n\n$listOverlayArgs = @('tf', $overlayJar)\n''',
    'verify runtime copy')

replace_once(
    'scripts/verify.ps1',
    '''Copy-Item -LiteralPath $overlayJar -Destination $distDir -Force\nCopy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecover.NET') -Destination $distDir -Recurse -Force\n''',
    '''Copy-Item -LiteralPath $overlayJar -Destination $distDir -Force\nCopy-Item -LiteralPath $runtimeJar -Destination $distDir -Force\nCopy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecover.NET') -Destination $distDir -Recurse -Force\n''',
    'distribution runtime copy')

replace_once(
    'scripts/verify.ps1',
    '''Assert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverOverlay.jar'\nAssert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverModernGUILauncher'\n''',
    '''Assert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverRuntime.jar'\nAssert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverOverlay.jar'\nAssert-TextContains (Join-Path $distDir 'run_gui.bat') 'LicenseRecoverModernGUILauncher'\nif (-not (Test-Path -LiteralPath (Join-Path $distDir 'LicenseRecoverRuntime.jar'))) {\n    throw 'Distribution is missing LicenseRecoverRuntime.jar.'\n}\nif ((Get-FileHash -LiteralPath (Join-Path $distDir 'LicenseRecoverRuntime.jar') -Algorithm SHA256).Hash -ne\n    (Get-FileHash -LiteralPath (Join-Path $distDir 'LicenseRecoverOverlay.jar') -Algorithm SHA256).Hash) {\n    throw 'Distribution runtime/overlay jars diverged.'\n}\n''',
    'distribution runtime verification')

replace_once(
    'scripts/verify.ps1',
    '''Write-Host "Verified overlay: $overlayJar"\nWrite-Host "Verified portable distribution: $archive"\n''',
    '''Write-Host "Verified overlay: $overlayJar"\nWrite-Host "Verified transition runtime: $runtimeJar"\nWrite-Host "Verified portable distribution: $archive"\n''',
    'verify final log')


# 5) Native package validation must prove the new EXE references the transition
# jar and that both generated archives actually contain it.
replace_once(
    'scripts/package-native-launcher.ps1',
    '''    'LicenseRecoverModernGUILauncherUiPatch',\n    'LicenseRecoverOverlay.jar',\n    'LicenseRecoverGUI.jar',\n''',
    '''    'LicenseRecoverModernGUILauncherUiPatch',\n    'LicenseRecoverRuntime.jar',\n    'LicenseRecoverOverlay.jar',\n    'LicenseRecoverGUI.jar',\n''',
    'native required strings')

replace_once(
    'scripts/package-native-launcher.ps1',
    '''    if (-not (Test-Path -LiteralPath (Join-Path $dir 'LicenseRecoverGUI.exe'))) {\n        throw "Repacked archive is missing LicenseRecoverGUI.exe: $dir"\n    }\n''',
    '''    if (-not (Test-Path -LiteralPath (Join-Path $dir 'LicenseRecoverGUI.exe'))) {\n        throw "Repacked archive is missing LicenseRecoverGUI.exe: $dir"\n    }\n    if (-not (Test-Path -LiteralPath (Join-Path $dir 'LicenseRecoverRuntime.jar'))) {\n        throw "Repacked archive is missing LicenseRecoverRuntime.jar: $dir"\n    }\n    if ((Get-FileHash -LiteralPath (Join-Path $dir 'LicenseRecoverRuntime.jar') -Algorithm SHA256).Hash -ne\n        (Get-FileHash -LiteralPath (Join-Path $dir 'LicenseRecoverOverlay.jar') -Algorithm SHA256).Hash) {\n        throw "Repacked runtime/overlay jars diverged: $dir"\n    }\n''',
    'repacked runtime verification')


# 6) Strengthen the *new* updater for all later transitions.  This does not need
# to be trusted for the current transition, because the new filename above is
# already safe for the old updater.  From this build onward VERSION.txt is copied
# last and critical runtime files are verified byte-for-byte before it advances.
replace_once(
    'src/main/java/LicenseRecoverModernGUILauncher.java',
    '''            backupExisting(staging, installDir, backup, "");\n            try { copyTree(staging, installDir); }\n            catch (IOException failed) {\n                try { copyTree(backup, installDir); }\n                catch (IOException restore) { failed.addSuppressed(restore); }\n                throw failed;\n            }\n            cleanupObsoleteEntrypoints(installDir);\n''',
    '''            File stagedVersion = new File(staging, "VERSION.txt");\n            File heldVersion = new File(work, "VERSION.txt.new");\n            Files.copy(stagedVersion.toPath(), heldVersion.toPath(), StandardCopyOption.REPLACE_EXISTING);\n            if (!stagedVersion.delete()) throw new IOException("无法暂存 VERSION.txt。");\n\n            backupExisting(staging, installDir, backup, "");\n            File installedVersion = new File(installDir, "VERSION.txt");\n            if (installedVersion.isFile()) {\n                Files.copy(installedVersion.toPath(), new File(backup, "VERSION.txt").toPath(),\n                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);\n            }\n            try {\n                copyTree(staging, installDir);\n                verifyInstalledCriticalFiles(staging, installDir);\n                Files.copy(heldVersion.toPath(), installedVersion.toPath(),\n                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);\n                if (!sameBytes(heldVersion, installedVersion))\n                    throw new IOException("VERSION.txt 写入后校验失败。");\n            } catch (IOException failed) {\n                try { copyTree(backup, installDir); }\n                catch (IOException restore) { failed.addSuppressed(restore); }\n                throw failed;\n            }\n            cleanupObsoleteEntrypoints(installDir);\n''',
    'installer copy/version ordering')

replace_once(
    'src/main/java/LicenseRecoverModernGUILauncher.java',
    '''    private static void copyTree(File source, File target) throws IOException {\n''',
    '''    private static void verifyInstalledCriticalFiles(File staging, File installDir) throws IOException {\n        String[] required = {\n                "LicenseRecoverRuntime.jar",\n                "LicenseRecoverOverlay.jar",\n                "LicenseRecover.jar",\n                "LicenseRecoverGUI.jar",\n                "LicenseRecoverGUI.exe"\n        };\n        for (String name : required) {\n            File expected = new File(staging, name);\n            File actual = new File(installDir, name);\n            if (!expected.isFile()) throw new IOException("更新包缺少关键运行文件: " + name);\n            if (!actual.isFile() || !sameBytes(expected, actual))\n                throw new IOException("更新后关键运行文件校验失败: " + name);\n        }\n    }\n\n    private static boolean sameBytes(File a, File b) throws IOException {\n        if (a == null || b == null || !a.isFile() || !b.isFile() || a.length() != b.length()) return false;\n        try (InputStream left = new BufferedInputStream(new FileInputStream(a));\n             InputStream right = new BufferedInputStream(new FileInputStream(b))) {\n            byte[] x = new byte[65536];\n            byte[] y = new byte[65536];\n            for (;;) {\n                int nx = left.read(x);\n                int ny = right.read(y);\n                if (nx != ny) return false;\n                if (nx < 0) return true;\n                for (int i = 0; i < nx; i++) if (x[i] != y[i]) return false;\n            }\n        }\n    }\n\n    private static void copyTree(File source, File target) throws IOException {\n''',
    'installer critical verification helpers')

print('runtime transition patch prepared successfully')
