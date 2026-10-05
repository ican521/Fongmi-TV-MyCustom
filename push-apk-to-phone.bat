@echo off
setlocal
set "BASE=%~dp0"
title Fongmi APK - Push to Phone
echo ============================================
echo   Fongmi APK - push to phone Download folder
echo ============================================
echo.
call powershell -NoProfile -ExecutionPolicy Bypass -File "%BASE%push-apk-to-phone.ps1"
echo.
echo --------------------------------------------
echo  Done. If you see RED errors above, send me a screenshot.
echo --------------------------------------------
pause
endlocal
