@echo off
set LOG=C:\Users\Dor_Va\AndroidStudioProjects\Elmtrackr-native-android\build_log.txt
echo BUILD START %date% %time% > %LOG%

REM Try Android Studio bundled JDK paths (newer versions use 'jbr', older use 'jre')
set AS_ROOT=C:\Program Files\Android\Android Studio

if exist "%AS_ROOT%\jbr\bin\java.exe" (
    set JAVA_HOME=%AS_ROOT%\jbr
    echo Found JDK at %AS_ROOT%\jbr >> %LOG%
) else if exist "%AS_ROOT%\jre\bin\java.exe" (
    set JAVA_HOME=%AS_ROOT%\jre
    echo Found JDK at %AS_ROOT%\jre >> %LOG%
) else (
    echo JDK not found at default paths, trying system java >> %LOG%
    where java >> %LOG% 2>&1
)

if defined JAVA_HOME (
    set PATH=%JAVA_HOME%\bin;%PATH%
    echo JAVA_HOME=%JAVA_HOME% >> %LOG%
) else (
    echo WARNING: JAVA_HOME not set >> %LOG%
)

echo Java version: >> %LOG%
java -version >> %LOG% 2>&1

cd /d "C:\Users\Dor_Va\AndroidStudioProjects\Elmtrackr-native-android\android"
echo Running: gradlew bundleRelease (skip lint vital - locked by AS) >> %LOG%
call gradlew.bat bundleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease >> %LOG% 2>&1
echo Exit code: %ERRORLEVEL% >> %LOG%

if exist "app\build\outputs\bundle\release\app-release.aab" (
    echo BUILD SUCCESS - AAB produced >> %LOG%
    echo BUILD SUCCESS
    echo AAB: %CD%\app\build\outputs\bundle\release\app-release.aab
) else (
    echo BUILD FAILED - no AAB found >> %LOG%
    echo BUILD FAILED
)
echo DONE %time% >> %LOG%
pause
