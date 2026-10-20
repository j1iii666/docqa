@echo off
chcp 65001 >nul
title docqa - start MySQL 84
echo ============================================
echo  Starting MySQL84 service ...
echo  (this window needs Administrator rights)
echo ============================================
echo.

net start MySQL84
set RC=%ERRORLEVEL%

echo.
if "%RC%"=="0" (
    echo [OK] MySQL84 started.
) else if "%RC%"=="2" (
    echo [OK] MySQL84 was already running.
) else (
    echo [FAIL] could not start MySQL84, error code %RC%
    echo Check D:\MySQL\data\mysql-error.log for the reason.
)

echo.
echo Status:
sc query MySQL84 | findstr /C:"STATE"
echo.
pause
