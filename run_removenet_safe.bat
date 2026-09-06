@echo off
chcp 65001 >nul
setlocal

set "APP=%~1"
if "%APP%"=="" set "APP=%CD%"

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
echo   应用目录: %APP%
echo   提示: 请先停止对应应用服务；执行前会为 Java/.NET 目标建立并校验 prepatch 备份。
echo.

"%JAVA%" -Dfile.encoding=UTF-8 -cp "%~dp0LicenseRecover.jar" SafeNetRemoverCLI "%APP%"
set "RC=%ERRORLEVEL%"
echo.
if not "%RC%"=="0" echo   执行失败，退出码: %RC%
pause
exit /b %RC%
