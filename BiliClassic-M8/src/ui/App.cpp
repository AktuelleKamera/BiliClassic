/* =====================================================================
 * App.cpp - 应用对象实现
 * ---------------------------------------------------------------------
 * 不写 WinMain：入口由 mzfc.lib 提供（若设备缺 mzfc.dll 改链 mzfcs.lib，
 * 见 README「真机兜底」）。
 * 启动顺序：启动图 -> 创建窗口 -> 初始化 core(配置/日志/网络, 绑定 sink)
 *           -> 显示 -> 首屏内容到达后收掉启动图
 * ===================================================================== */
#include "App.h"
#include "../app/AppContext.h"
#include <Mzfc/ImagingHelper.h>
#include <string.h>

CBiliApp theApp;

CBiliApp::CBiliApp()
{
}

/* ===================== 启动图 ===================== */

static HWND    s_splash    = NULL;
static HBITMAP s_splashBmp = NULL;
static int     s_splashW   = 0;
static int     s_splashH   = 0;

static LRESULT CALLBACK SplashProc(HWND h, UINT m, WPARAM w, LPARAM l)
{
    switch (m) {
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(h, &ps);

        if (s_splashBmp != NULL) {
            HDC mdc = CreateCompatibleDC(hdc);
            HGDIOBJ old = SelectObject(mdc, s_splashBmp);

            BitBlt(hdc, 0, 0, s_splashW, s_splashH, mdc, 0, 0, SRCCOPY);
            SelectObject(mdc, old);
            DeleteDC(mdc);
        }
        EndPaint(h, &ps);
        return 0;
    }
    default:
        break;
    }
    return DefWindowProcW(h, m, w, l);
}

static void splash_prepare(void)
{
    int sw = GetSystemMetrics(SM_CXSCREEN);
    int sh = GetSystemMetrics(SM_CYSCREEN);
    HDC screen;
    HDC mdc;
    HGDIOBJ old;
    RECT rc;
    struct {
        BITMAPINFOHEADER hdr;
    } bi;
    void *bits = NULL;

    if (s_splashBmp != NULL) {
        return;
    }
    if (sw <= 0) {
        sw = 480;
    }
    if (sh <= 0) {
        sh = 720;
    }

    screen = GetDC(NULL);
    memset(&bi, 0, sizeof(bi));
    bi.hdr.biSize = sizeof(BITMAPINFOHEADER);
    bi.hdr.biWidth = sw;
    bi.hdr.biHeight = -sh;           /* 顶朝下 */
    bi.hdr.biPlanes = 1;
    bi.hdr.biBitCount = 24;
    bi.hdr.biCompression = BI_RGB;
    s_splashBmp = CreateDIBSection(screen, (BITMAPINFO *)&bi, DIB_RGB_COLORS,
                                   &bits, NULL, 0);
    mdc = CreateCompatibleDC(screen);
    if (s_splashBmp == NULL) {
        DeleteDC(mdc);
        ReleaseDC(NULL, screen);
        return;
    }
    old = SelectObject(mdc, s_splashBmp);
    rc.left = 0;
    rc.top = 0;
    rc.right = sw;
    rc.bottom = sh;
    {
        HBRUSH b = CreateSolidBrush(RGB(0, 0, 0));
        FillRect(mdc, &rc, b);
        DeleteObject(b);
    }
    /* 启动图取 exe 里的 RCDATA（2002） */
    ImagingHelper::DrawImage(mdc, &rc, GetModuleHandleW(NULL), RT_RCDATA,
                             MAKEINTRESOURCE(2002), true);
    SelectObject(mdc, old);
    DeleteDC(mdc);
    ReleaseDC(NULL, screen);

    s_splashW = sw;
    s_splashH = sh;
}

void SplashShow(void)
{
    static int s_reg = 0;
    WNDCLASSW wc;

    splash_prepare();
    if (s_splashBmp == NULL) {
        return;
    }
    if (!s_reg) {
        memset(&wc, 0, sizeof(wc));
        wc.lpfnWndProc = SplashProc;
        wc.hInstance = GetModuleHandleW(NULL);
        wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
        wc.lpszClassName = L"BiliClassicSplash";
        RegisterClassW(&wc);
        s_reg = 1;
    }
    s_splash = CreateWindowExW(WS_EX_TOPMOST, L"BiliClassicSplash", L"",
                               WS_POPUP, 0, 0, s_splashW, s_splashH,
                               NULL, NULL, GetModuleHandleW(NULL), NULL);
    if (s_splash != NULL) {
        ShowWindow(s_splash, SW_SHOW);
        UpdateWindow(s_splash);
    }
}

void SplashHide(void)
{
    if (s_splash != NULL) {
        DestroyWindow(s_splash);
        s_splash = NULL;
    }
}

BOOL CBiliApp::Init()
{
    int cx, cy;

    CoInitializeEx(NULL, COINIT_MULTITHREADED);
    g_app.GetLogger().Log("[bc] coinit ok");

    cx = GetSystemMetrics(SM_CXSCREEN);
    cy = GetSystemMetrics(SM_CYSCREEN);
    g_app.GetLogger().Log("[bc] screen %dx%d", cx, cy);

    /* 先顶上启动图，盖住后面建窗口/联网这段 */
    SplashShow();

    if (!m_wnd.Create(0, 0, cx, cy, 0, 0, 0, 0)) {
        g_app.GetLogger().Log("[bc] window create FAILED");
        SplashHide();
        return FALSE;
    }
    g_app.GetLogger().Log("[bc] window create ok");

    /* Create 返回后 m_hWnd 一定有效：把 UI 线程 id/窗口句柄告诉 sink，
     * 后台任务的回调靠它 PostMessage/Drain 回主线程 */
    m_wnd.GetSink().AttachThread(m_wnd.m_hWnd);

    /* 此时控件已建好，日志/状态可以安全转发到界面 */
    g_app.Init(&m_wnd.GetSink());
    m_wnd.RefreshUI();
    g_app.GetLogger().Log("[bc] refreshui ok");

    m_wnd.Show();
    g_app.GetLogger().Log("[bc] show ok");
    /* 启动拉推荐：必须在 Init/Show 之后才挂定时器。
     * 之前挂在 OnInitDialog 里，http_init() 内的 WSAStartup 会泵消息，
     * 于是 WM_TIMER 在 g_app.Init() 还没跑完时就重入 DoSearch -> 崩。 */
    ::SetTimer(m_wnd.m_hWnd, 9, 400, NULL);
    g_app.GetLogger().Log("[bc] startup timer armed");
    /* 兜底：万一首屏一直没回来（断网/请求挂了），8 秒后也必须收掉启动图 */
    ::SetTimer(m_wnd.m_hWnd, 11, 8000, NULL);
    return TRUE;
}
