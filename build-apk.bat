@echo off
cd /d "%~dp0android"
echo Building ElmTrackr debug APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% EQU 0 (
    echo.
    echo BUILD SUCCESSFUL
    echo APK: app\build\outputs\apk\debug\app-debug.apk
) else (
    echo.
    echo BUILD FAILED - check output above
)
pause
