/* =====================================================================
 * App.h - Mzfc 应用对象（框架自带 WinMain 调用 Init）
 * ===================================================================== */
#ifndef UI_APP_H
#define UI_APP_H

#include <mzfc_inc.h>
#include "MainWindow.h"

class CBiliApp : public CMzApp
{
public:
    CBiliApp();
    virtual BOOL Init();

private:
    MainWindow m_wnd;
};

/* 启动图：Show() 在窗口创建前调用，Hide() 在首屏内容到达（或超时）后调用 */
void SplashShow();
void SplashHide();

#endif /* UI_APP_H */
