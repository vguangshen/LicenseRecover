@echo off
chcp 65001 >nul
setlocal

set "JAVA="
rem 默认启动第二代薄 GUI；旧界面可使用 run_gui_legacy.bat 回退
if exist "%~dp0jre\bin\javaw.exe" set "JAVA=%~dp0jre\bin\javaw.exe"
if not defined JAVA if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"
if not defined JAVA where javaw >nul 2>nul && set "JAVA=javaw"
if not defined JAVA where java >nul 2>nul && set "JAVA=java"
if not defined JAVA if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA (
  echo.
  echo   [错误] 未找到 Java 运行环境。
  echo.
  pause
  exit /b 1
)

start "" "%JAVA%" -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=true -Dsun.java2d.noddraw=true -cp "%~dp0LicenseRecoverGUI.jar" LicenseRecoverModernGUI
