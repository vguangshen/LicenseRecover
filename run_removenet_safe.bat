@echo off
chcp 65001 >nul
setlocal

set "APP=%~1"
if "%APP%"=="" set "APP=%CD%"

set "DOTNET="
set "JAVA_APP="
if exist "%APP%\ITMC.Web.dll" if exist "%APP%\itmcRegedit.dll" set "DOTNET=1"
if exist "%APP%\bin\ITMC.Web.dll" if exist "%APP%\bin\itmcRegedit.dll" set "DOTNET=1"
if not defined DOTNET if exist "%APP%\WEB-INF\lib\ITMCReg*.jar" set "JAVA_APP=1"
if not defined DOTNET if exist "%APP%\WEB-INF\WEB-INF\lib\ITMCReg*.jar" set "JAVA_APP=1"
if not defined DOTNET if not defined JAVA_APP (
  echo.
  echo   [错误] 未找到授权文件 (ITMCReg*.jar 或 ITMC.Web.dll+itmcRegedit.dll)
  echo.
  pause
  exit /b 1
)

set "JAVA="
if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"
if not defined JAVA where java >nul 2>nul && set "JAVA=java"
if not defined JAVA if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA (
  echo.
  echo   [错误] 未找到 Java 运行环境。
  echo.
  pause
  exit /b 1
)

echo.
echo   ============================================
echo    ITMC 移除联网授权代码 (方式三 / 安全入口)
echo   ============================================
if defined DOTNET (
  echo   应用类型: .NET 版   应用目录: %APP%
  echo   提示: 请先停止 IIS 应用池；现代 GUI 会额外建立 prepatch 备份。
  echo.
  "%JAVA%" -Dfile.encoding=UTF-8 -jar "%~dp0LicenseRecover.jar" --remove-net "%APP%"
) else (
  echo   应用类型: Java 版   应用根目录: %APP%
  echo   提示: 请先停止 Tomcat；本入口会先建立并校验 prepatch 备份，失败则拒绝修改。
  echo.
  "%JAVA%" -Dfile.encoding=UTF-8 -cp "%~dp0LicenseRecover.jar" SafeNetRemoverCLI "%APP%"
)
echo.
pause
