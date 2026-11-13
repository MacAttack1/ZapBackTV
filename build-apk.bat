@echo off
rem ============================================================================
rem  Lumen TV - build the APK on Windows
rem
rem    build-apk.bat               release APK (signed with your key in signing\)
rem    build-apk.bat debug         debug APK
rem    build-apk.bat release install   ...and install it on a phone/TV connected with adb
rem
rem  Needs Android Studio installed once (it brings Java and the Android SDK).
rem  The finished APK is copied to the dist\ folder.
rem ============================================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title Lumen TV build

set "TASK=assembleRelease"
set "KIND=release"
if /i "%~1"=="debug" ( set "TASK=assembleDebug" & set "KIND=debug" )

rem ---- Java 17+: JAVA_HOME, otherwise the runtime bundled with Android Studio
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto :java_ok
for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "%ProgramFiles%\Android\Android Studio\jre") do (
    if exist "%%~P\bin\java.exe" (
        set "JAVA_HOME=%%~P"
        goto :java_ok
    )
)
echo.
echo  [!] Java wasn't found.
echo      Install Android Studio from https://developer.android.com/studio and open it once,
echo      or set JAVA_HOME to a JDK 17 (or newer) folder, then run this again.
echo.
pause
exit /b 1
:java_ok
echo  Java: %JAVA_HOME%

rem ---- Android SDK: local.properties, ANDROID_HOME, or Android Studio's default location
if exist local.properties (
    for /f "usebackq tokens=1,* delims==" %%a in ("local.properties") do if "%%a"=="sdk.dir" set "SDK=%%b"
    set "SDK=!SDK:\:=:!"
    set "SDK=!SDK:\\=\!"
    set "SDK=!SDK:/=\!"
) else (
    set "SDK=%ANDROID_HOME%"
    if not defined SDK set "SDK=%ANDROID_SDK_ROOT%"
    if not defined SDK set "SDK=%LOCALAPPDATA%\Android\Sdk"
)
if not exist "!SDK!\platforms" if not exist "!SDK!\platform-tools" (
    echo.
    echo  [!] The Android SDK wasn't found at "!SDK!".
    echo      Open Android Studio once and let it finish the SDK setup, or set ANDROID_HOME.
    echo.
    pause
    exit /b 1
)
if not exist local.properties (
    set "SDKF=!SDK:\=/!"
    > local.properties echo sdk.dir=!SDKF!
)
echo  SDK:  !SDK!

if not exist "signing\keystore.properties" (
    echo.
    echo  [i] No signing key in signing\ - the APK will use a temporary debug key and
    echo      WON'T install over a copy signed with your Lumen TV key. See BUILDING.md.
    echo.
)

echo.
echo  Building %KIND% APK (the first build downloads Gradle and libraries, give it a few minutes)...
echo.
call gradlew.bat %TASK% --console=plain
if errorlevel 1 (
    echo.
    echo  [!] Build failed - see the messages above.
    pause
    exit /b 1
)

if not exist dist mkdir dist
set "OUT="
for %%F in ("app\build\outputs\apk\%KIND%\*.apk") do (
    copy /y "%%F" dist\ >nul
    set "OUT=%%~nxF"
)
echo.
echo  Done: dist\!OUT!

if /i "%~2"=="install" (
    set "ADB=!SDK!\platform-tools\adb.exe"
    if exist "!ADB!" (
        echo  Installing on the connected device...
        "!ADB!" install -r "dist\!OUT!"
    ) else (
        echo  [!] adb wasn't found in the SDK's platform-tools folder.
    )
)
echo.
pause
