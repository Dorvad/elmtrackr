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
echo Running: gradlew clean assembleRelease >> %LOG%
call gradlew.bat clean assembleRelease >> %LOG% 2>&1
echo Exit code: %ERRORLEVEL% >> %LOG%

if exist "app\build\outputs\apk\release\app-release.apk" (
    echo BUILD SUCCESS - APK produced, signed with the key in local.properties >> %LOG%
    echo BUILD SUCCESS
    echo APK: %CD%\app\build\outputs\apk\release\app-release.apk
) else if exist "app\build\outputs\apk\release\app-release-unsigned.apk" (
    echo BUILD partial - unsigned APK only >> %LOG%
    echo UNSIGNED APK produced (signing may have failed)
    echo APK: %CD%\app\build\outputs\apk\release\app-release-unsigned.apk
) else (
    echo BUILD FAILED - no APK found >> %LOG%
    echo BUILD FAILED
)
echo DONE %time% >> %LOG%
pause
