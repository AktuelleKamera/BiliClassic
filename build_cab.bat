@echo off
setlocal enabledelayedexpansion

rem ===== BiliClassic WM - CAB Builder =====
set "SRC=%~dp0BiliClassic_WM\"
set "CABWIZ=L:\Software\Microsoft Visual Studio 9.0\SmartDevices\SDK\SDKTools\cabwiz.exe"
set "MSBUILD=C:\Windows\Microsoft.NET\Framework\v3.5\MSBuild.exe"
set "OUT=%TEMP%\biliclassic_cab_build\"

if not exist "%OUT%" mkdir "%OUT%"

set "VERSION="
for /f "delims=" %%A in ('type "%SRC%My Project\AssemblyInfo.vb" 2^>nul') do (
  set "LINE=%%A"
  set "HDR=!LINE:~0,27!"
  if "!HDR!"=="<Assembly: AssemblyVersion(" (
    set "TAIL=!LINE:~27!"
    set "V1=!TAIL:~1!"
    set "V1=!V1:"=!"
    set "V1=!V1:)=!"
    set "V1=!V1:>=!"
    set "VERSION=!V1!"
  )
)
if "%VERSION%"=="" (
  echo ERROR: could not read AssemblyVersion
  exit /b 1
)

echo [1/3] Building...
"%MSBUILD%" "%SRC%BiliClassic_WM.vbproj" /nologo /t:Rebuild /p:Configuration=Debug /v:minimal
if errorlevel 1 goto fail

echo [2/3] Copying executable...
copy /y "%SRC%bin\Debug\BiliClassic.exe" "%OUT%\" >nul
if errorlevel 1 goto fail

echo [3/3] Packaging CAB...
"%CABWIZ%" "%SRC%BiliClassic_WM.inf" /dest "%OUT%" /compress

if not exist "%OUT%BiliClassic_WM.CAB" goto fail

copy /y "%OUT%BiliClassic_WM.CAB" "%SRC%BiliClassic %VERSION%.cab" >nul
if errorlevel 1 goto fail

echo.
echo Done: "%SRC%BiliClassic %VERSION%.cab"
echo.
goto end

:fail
echo.
echo BUILD FAILED
exit /b 1

:end
exit /b 0
