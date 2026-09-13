@echo off
setlocal

rem Compatibility trampoline for pre-v1.2.1 self-updaters.
rem New portable releases expose LicenseRecoverGUI.exe as the only user entry point.
if exist "%~dp0LicenseRecoverGUI.exe" (
  start "" "%~dp0LicenseRecoverGUI.exe" %*
  exit /b 0
)

rem Fallback only when the native EXE is missing from an incomplete/legacy folder.
set "JAVA="
if exist "%~dp0jre\bin\javaw.exe" set "JAVA=%~dp0jre\bin\javaw.exe"
if not defined JAVA if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"
if not defined JAVA (
  where javaw >nul 2>nul
  if not errorlevel 1 set "JAVA=javaw"
)
if not defined JAVA (
  where java >nul 2>nul
  if not errorlevel 1 set "JAVA=java"
)
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA (
  echo [LicenseRecover] Java runtime not found.
  exit /b 1
)
set "TOOLJAR=%~dp0LicenseRecoverRuntime.jar"
if not exist "%TOOLJAR%" set "TOOLJAR=%~dp0LicenseRecoverOverlay.jar"
if not exist "%TOOLJAR%" (
  echo [LicenseRecover] LicenseRecoverRuntime.jar / LicenseRecoverOverlay.jar is missing.
  exit /b 1
)

start "" "%JAVA%" -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=true -Dsun.java2d.noddraw=true -cp "%TOOLJAR%;%~dp0LicenseRecoverGUI.jar" LicenseRecoverModernGUILauncherUiPatch %*
exit /b 0
