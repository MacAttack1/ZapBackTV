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

rem ---- Java: Gradle 8.11 runs on Java 17 to 23 only (newer ones fail with just "25.0.4").
rem      Prefer Android Studio's bundled runtime, then JAVA_HOME, then common JDK install folders.
set "PICK="
for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "%ProgramFiles%\Android\Android Studio\jre") do (
    if not defined PICK call :tryjava "%%~P"
)
if not defined PICK if defined JAVA_HOME call :tryjava "%JAVA_HOME%"
for %%R in ("%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Java" "%ProgramFiles%\Zulu" "%ProgramFiles%\Amazon Corretto") do (
    if exist "%%~R" for /d %%D in ("%%~R\jdk-21*" "%%~R\jdk-17*" "%%~R\jdk21*" "%%~R\jdk17*" "%%~R\zulu-21*" "%%~R\zulu-17*") do (
        if not defined PICK call :tryjava "%%~D"
    )
)
if not defined PICK (
    echo.
    echo  [!] No compatible Java found. This project needs Java 17 to 23
    if defined FOUNDJV echo      ^(found Java !FOUNDJV!, which is too new for the build tool^).
    echo.
    echo      Easiest fix - install Java 21 alongside it ^(your other Java keeps working^):
    echo          winget install EclipseAdoptium.Temurin.21.JDK
    echo      or download it from https://adoptium.net/temurin/releases/?version=21
    echo      Then run this again.
    echo.
    pause
    exit /b 1
)
set "JAVA_HOME=%PICK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo  Java: %JAVA_HOME%  (version !PICKV!)

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
exit /b 0

rem ---- :tryjava <folder>  -> sets PICK / PICKV if that folder holds Java 17-23
:tryjava
if not exist "%~1\bin\java.exe" exit /b 0
set "JV="
for /f "tokens=3" %%v in ('""%~1\bin\java.exe" -version 2>&1 | findstr /i "version""') do if not defined JV set "JV=%%~v"
if not defined JV exit /b 0
set "JM="
for /f "delims=.-+" %%m in ("%JV%") do set "JM=%%m"
if not defined JM exit /b 0
if "%JM%"=="1" exit /b 0
if %JM% LSS 17 exit /b 0
if %JM% GTR 23 ( set "FOUNDJV=%JV%" & exit /b 0 )
set "PICK=%~1"
set "PICKV=%JV%"
exit /b 0
