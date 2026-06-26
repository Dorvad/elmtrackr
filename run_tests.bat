@echo off
set LOG=C:\Users\Dor_Va\AndroidStudioProjects\Elmtrackr-native-android\test_log.txt
echo TEST RUN %date% %time% > %LOG%

set AS_ROOT=C:\Program Files\Android\Android Studio
if exist "%AS_ROOT%\jbr\bin\java.exe" (
    set JAVA_HOME=%AS_ROOT%\jbr
) else if exist "%AS_ROOT%\jre\bin\java.exe" (
    set JAVA_HOME=%AS_ROOT%\jre
)
if defined JAVA_HOME set PATH=%JAVA_HOME%\bin;%PATH%

cd /d "C:\Users\Dor_Va\AndroidStudioProjects\Elmtrackr-native-android\android"
echo Running unit tests... >> %LOG%
call gradlew.bat testDebugUnitTest >> %LOG% 2>&1
echo Exit code: %ERRORLEVEL% >> %LOG%

if %ERRORLEVEL% EQU 0 (
    echo ALL TESTS PASSED >> %LOG%
    echo ALL TESTS PASSED
) else (
    echo TESTS FAILED >> %LOG%
    echo TESTS FAILED - check test_log.txt
)
echo DONE %time% >> %LOG%
pause
