@ECHO OFF
REM _send.cmd - 发送 IPK 到 TouchPad
REM 将 bin\tv.biliclassic.webos_1.0.0_all.ipk 发送到设备 /tmp/tvbili.ipk

"C:\Program Files\Palm, Inc\novacom.exe" -c login -r webos20090606 -w
IF ERRORLEVEL 1 (
    ECHO 认证失败，请检查 USB 连接
    EXIT /B 1
)

"C:\Program Files\Palm, Inc\novacom.exe" put "file:///tmp/tvbili.ipk" < "%~dp0bin\tv.biliclassic.webos_1.0.0_all.ipk"
IF ERRORLEVEL 1 (
    ECHO 发送失败
    EXIT /B 1
)

ECHO 发送成功
EXIT /B 0
