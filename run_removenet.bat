@echo off
chcp 65001 >nul
setlocal

set "APP=%~1"
if "%APP%"=="" set "APP=%CD%"

rem 判断应用类型（.NET 方式三只处理小写 itmcRegedit.dll）
set "DOTNET="
set "JAVA="
if exist "%APP%\ITMC.Web.dll" if exist "%APP%\itmcRegedit.dll" set "DOTNET=1"
if exist "%APP%\bin\ITMC.Web.dll" if exist "%APP%\bin\itmcRegedit.dll" set "DOTNET=1"
if not defined DOTNET if exist "%APP%\WEB-INF\lib\ITMCReg*.jar" set "JAVA=1"
if not defined DOTNET if not defined JAVA (
  echo.
  echo   [错误] 未找到授权文件 (ITMCReg*.jar 或 ITMC.Web.dll+itmcRegedit.dll)
  echo   请把应用目录作为参数传入，例如:
  echo     run_removenet.bat D:\server\cloud_training
  echo     run_removenet.bat D:\server\app\bin
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

echo.
echo   ============================================
echo    ITMC 移除联网授权代码 (方式三)
echo   ============================================
if defined DOTNET (
  echo   应用类型: .NET 版   应用目录: %APP%
  echo   提示: 执行前请先停止应用服务(IIS 应用池)，工具只会备份并替换 itmcRegedit.dll。
  echo.
  "%JAVA%" -Dfile.encoding=UTF-8 -jar "%~dp0LicenseRecover.jar" --remove-net "%APP%"
) else (
  echo   应用类型: Java 版   应用根目录: %APP%
  echo   提示: 执行前请先停止应用服务(Tomcat)。
  echo.
  rem 本工具已内嵌 javassist，用最小 classpath 即可（不能带 WEB-INF\lib\* 全量通配，
  rem 否则 ITMCReg.jar 被本进程占用无法替换）
  "%JAVA%" -Dfile.encoding=UTF-8 -cp "%~dp0LicenseRecover.jar" LicenseRecover --remove-net "%APP%"
)
echo.
pause
