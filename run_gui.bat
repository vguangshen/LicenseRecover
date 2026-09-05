@echo off
chcp 65001 >nul
setlocal

set "JAVA="
rem 自包含: 优先用内嵌 jre(javaw 不弹黑窗)；否则用系统 Java 兜底
if exist "%~dp0jre\bin\javaw.exe" set "JAVA=%~dp0jre\bin\javaw.exe"
if not defined JAVA if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"
if not defined JAVA where javaw >nul 2>nul && set "JAVA=javaw"
if not defined JAVA where java >nul 2>nul && set "JAVA=java"
if not defined JAVA if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA if exist "C:\Program Files\Java\jre8\bin\javaw.exe" set "JAVA=C:\Program Files\Java\jre8\bin\javaw.exe"
if not defined JAVA if exist "C:\Program Files (x86)\Java\jre8\bin\javaw.exe" set "JAVA=C:\Program Files (x86)\Java\jre8\bin\javaw.exe"
if not defined JAVA (
  echo.
  echo   [错误] 未找到 Java 运行环境（本工具自带 jre 目录，请确认完整解压后运行）。
  echo.
  pause
  exit /b 1
)

start "" "%JAVA%" -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=true -Dsun.java2d.noddraw=true -jar "%~dp0LicenseRecoverGUI.jar"
