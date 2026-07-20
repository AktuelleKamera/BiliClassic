@ECHO OFF
REM palm-install.bat - 一键安�?IPK �?webOS TouchPad
REM 用法: palm-install.bat [ipk文件路径]

SET NOVACOM="C:\Program Files\Palm, Inc\novacom.exe"
SET PWD=webos20090606

REM 默认 IPK 路径
IF "%1"=="" (
    SET IPK=bin\tv.biliclassic.webos_0.3.0_all.ipk
) ELSE (
    SET IPK=%1
)

IF "%IPK%"=="--list" (
    ECHO 列出设备...
    %NOVACOM% -l
    EXIT /B 0
)

IF NOT EXIST %IPK% (
    ECHO 错误: 找不到文�?%IPK%
    ECHO 请先运行 build.bat 编译，或指定 IPK 路径
    EXIT /B 1
)

ECHO [1/5] 认证...
%NOVACOM% -c login -r %PWD% -w
IF ERRORLEVEL 1 (
    ECHO 错误: 认证失败，请检�?USB 连接
    EXIT /B 1
)

ECHO [2/5] 发�?IPK 到设�?..
%NOVACOM% put "file:///tmp/tvbili.ipk" < %IPK%
IF ERRORLEVEL 1 (
    ECHO 错误: 发送失�?    EXIT /B 1
)

ECHO [3/5] 安装...
> "%TEMP%\bili_install.txt" ECHO ipkg-cl remove tv.biliclassic.webos 2^>nul
>> "%TEMP%\bili_install.txt" ECHO ipkg-cl install /tmp/tvbili.ipk
>> "%TEMP%\bili_install.txt" ECHO rm -rf "/media/cryptofs/apps/usr/palm/applications/tv.biliclassic.webos"
>> "%TEMP%\bili_install.txt" ECHO cp -r "/usr/palm/applications/tv.biliclassic.webos" "/media/cryptofs/apps/usr/palm/applications/"
>> "%TEMP%\bili_install.txt" ECHO echo INSTALL_DONE

TYPE "%TEMP%\bili_install.txt" | %NOVACOM% open tty://

ECHO [4/5] 刷新应用列表...
> "%TEMP%\bili_scan.txt" ECHO luna-send -n 1 palm://com.palm.applicationManager/rescan '{}'
>> "%TEMP%\bili_scan.txt" ECHO exit
TYPE "%TEMP%\bili_scan.txt" | %NOVACOM% open tty://

ECHO [5/5] 启动应用...
> "%TEMP%\bili_launch.txt" ECHO luna-send -n 1 palm://com.palm.applicationManager/launch '{"id":"tv.biliclassic.webos"}'
>> "%TEMP%\bili_launch.txt" ECHO exit
TYPE "%TEMP%\bili_launch.txt" | %NOVACOM% open tty://

ECHO.
ECHO 完成！应用已安装并启动�?
