@echo off
chcp 65001 >nul
setlocal

set "APP=%~1"
if "%APP%"=="" set "APP=%CD%"

rem 旧方式三回退入口（不含第二阶段 prepatch 强制预检）
set "DOTNET="
set "JAVA="
if exist "%APP%\ITMC.Web.dll" if exist "%APP%\itmcRegedit.dll" set "DOTNET=1"
if exist "%APP%\bin\ITMC.Web.dll" if exist "%APP%\bin\itmcRegedit.dll" set "DOTNET=1"
if not defined DOTNET if exist "%APP%\WEB-INF\lib\ITMCReg*.jar" set "JAVA=1"
if not defined DOTNET if not defined JAVA (
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

if defined DOTNET (
  "%JAVA%" -Dfile.encoding=UTF-8 -jar "%~dp0LicenseRecover.jar" --remove-net "%APP%"
) else (
  "%JAVA%" -Dfile.encoding=UTF-8 -cp "%~dp0LicenseRecover.jar" LicenseRecover --remove-net "%APP%"
)
echo.
pause
