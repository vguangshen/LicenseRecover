@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

set "APP=%~1"
if "%APP%"=="" set "APP=%CD%"

rem 判断应用类型: Java 版含 WEB-INF\lib\ITMCReg*.jar; .NET 版需同时含 ITMC.Web.dll 与 itmcRegedit.dll
set "DOTNET="
set "JAVA="
if exist "%APP%\ITMC.Web.dll" if exist "%APP%\itmcRegedit.dll" set "DOTNET=1"
if exist "%APP%\bin\ITMC.Web.dll" if exist "%APP%\bin\itmcRegedit.dll" set "DOTNET=1"
if not defined DOTNET if exist "%APP%\WEB-INF\lib\ITMCReg*.jar" set "JAVA=1"
if not defined DOTNET if not defined JAVA (
  echo.
  echo   [错误] 未找到授权文件:
  echo     Java 版: "%APP%\WEB-INF\lib\ITMCReg*.jar"
  echo     .NET 版: "%APP%"(或其中 bin 子目录)内的 ITMC.Web.dll 与 itmcRegedit.dll
  echo   请把应用目录作为参数传入，例如:
  echo     run.bat D:\server\cloud_training      (Java 版)
  echo     run.bat D:\server\app\bin             (.NET 版)
  echo   或把 run.bat 放到应用目录后双击运行。
  echo.
  pause
  exit /b 1
)

set "JAVA="
rem 自包含: 优先用内嵌 jre；否则用系统 Java 兜底
if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"
if not defined JAVA where java >nul 2>nul && set "JAVA=java"
if not defined JAVA if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA if exist "C:\Program Files\Java\jre8\bin\java.exe" set "JAVA=C:\Program Files\Java\jre8\bin\java.exe"
if not defined JAVA if exist "C:\Program Files (x86)\Java\jre8\bin\java.exe" set "JAVA=C:\Program Files (x86)\Java\jre8\bin\java.exe"
if not defined JAVA (
  echo.
  echo   [错误] 未找到 java.exe（本工具自带 jre 目录，请确认完整解压后运行）。
  echo.
  pause
  exit /b 1
)

rem 命令行入口必须使用当前源码运行层。安装不完整时停止，避免回退到旧恢复入口。
set "RUNTIMEJAR=%~dp0LicenseRecoverRuntime.jar"
if not exist "%RUNTIMEJAR%" set "RUNTIMEJAR=%~dp0LicenseRecoverOverlay.jar"
if not exist "%RUNTIMEJAR%" (
  echo   [错误] 缺少 LicenseRecoverRuntime.jar / LicenseRecoverOverlay.jar，请重新完整解压工具。
  pause
  exit /b 2
)
set "CORECP=%RUNTIMEJAR%;%~dp0LicenseRecoverGUI.jar;%~dp0LicenseRecover.jar"

echo.
echo   ============================================
echo    ITMC 离线授权恢复工具
echo   ============================================
if defined DOTNET (
  echo   应用类型: .NET 版   应用目录: %APP%
  echo   方式一: 防止软件自动联网校验（只修改授权配置文件，不修改 DLL）
  echo.
  "%JAVA%" -Dfile.encoding=UTF-8 -cp "%CORECP%" LicenseRecover "%APP%"
) else (
  echo   应用类型: Java 版   应用根目录: %APP%
  echo.
  "%JAVA%" -Dfile.encoding=UTF-8 -cp "%CORECP%;%APP%\WEB-INF\lib\*" LicenseRecover "%APP%"
)
echo.
echo   请查看上方 RESULT 行。若为 OK，重启应用服务使配置生效。
echo.
pause
