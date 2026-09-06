@echo off
rem 默认方式三入口：统一转到带 Java/.NET prepatch 强制备份校验的安全脚本。
call "%~dp0run_removenet_safe.bat" %*
exit /b %ERRORLEVEL%
