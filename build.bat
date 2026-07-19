@ECHO OFF
REM build.bat - 一键编译 + 安装到 webOS TouchPad

ECHO ========================================
ECHO   BiliClassic for webOS - 一键编译安装
ECHO ========================================
ECHO.

ECHO [1/4] 编译 JS/CSS...
node enyo\tools\minifier\minify.js package.js -output build\app
IF ERRORLEVEL 1 (
    ECHO 编译失败
    EXIT /B 1
)

ECHO [2/4] 部署...
node enyo\tools\deploy.js -T -s . -o deploy
IF ERRORLEVEL 1 (
    ECHO 部署失败
    EXIT /B 1
)

ECHO [3/4] 打包 IPK...
python package-ipk.py
IF ERRORLEVEL 1 (
    ECHO 打包失败
    EXIT /B 1
)

ECHO [4/4] 安装到设备...
CALL palm-install.bat

ECHO.
ECHO 全部完成！
PAUSE
