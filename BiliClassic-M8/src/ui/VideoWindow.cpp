/* =====================================================================
 * VideoWindow.cpp - 内嵌视频播放窗口实现
 * ===================================================================== */
#include "VideoWindow.h"
#include "../app/AppContext.h"
#include "LoadingWnd.h"
#include "ScreenRot.h"

MZ_IMPLEMENT_DYNAMIC(VideoWindow)

enum
{
    MZ_ID_BACK = 9101
};

/* 自定义消息：等消息循环起来后再开播放器 / 失败时自动关窗 */
#define MZ_WM_CLOSE_SELF (WM_APP + 0x501)
#define MZ_WM_OPEN_PLAYER (WM_APP + 0x502)

/* 弹幕重绘定时器（约 30fps） */
#define DM_TIMER_ID 0xD2

/* PlatformApi.lib：注册 shell 锁屏通知 + 屏幕常亮控制 */
BOOL RegisterShellMessage(HWND hWnd, UINT uMsg);
BOOL UnRegisterShellMessage(HWND hWnd, UINT uMsg);
UINT GetShellNotifyMsg_EntryLockPhone(void);
UINT GetShellNotifyMsg_LeaveLockPhone(void);
BOOL SetScreenAlwaysOn(HWND hWnd);
BOOL SetScrennAutoOff(void);
BOOL IsLockPhoneStatus(void);
#define WM_MZSH_ENTRY_LOCKPHONE 0x00000004
#define WM_MZSH_LEAVE_LOCKPHONE 0x00000008

#ifndef PBT_APMSUSPEND
#define PBT_APMSUSPEND 0x0004
#endif
#ifndef WM_POWERBROADCAST
#define WM_POWERBROADCAST 0x0218
#endif
#ifndef PBT_APMRESUMESUSPEND
#define PBT_APMRESUMESUSPEND 0x0007
#endif
#ifndef PBT_APMRESUMEAUTOMATIC
#define PBT_APMRESUMEAUTOMATIC 0x0012
#endif

VideoWindow::VideoWindow()
{
    m_ok = false;
    m_closing = false;
    m_rotated = 0;
    m_overlayOn = 0;
    m_ovW = 0;
    m_ovH = 0;
    m_playStart = 0;
    m_lastPos = 0;
    m_msgLock = 0;
    m_msgUnlock = 0;
    m_lockRegistered = 0;
    m_unrotated = 0;
    m_smoothSaved = 0;
    m_oldSmooth = 0;
    m_screenOn = 0;
    m_ctrlShown = 0;
    m_ctrlHideAt = 0;
    m_playing = 0;
    m_volume = 60;
    m_ctrlDC = NULL;
    m_ctrlBmp = NULL;
    m_ctrlOldBmp = NULL;
    m_ctrlBits = NULL;
    m_ctrlPitch = 0;
    m_ctrlBw = 0;
    m_ctrlBh = 0;
    m_ctrlFont = NULL;
}

VideoWindow::~VideoWindow()
{
    m_player.Close();
    FreeControls();
}

bool VideoWindow::PlayModal(HWND hwndParent, const char *pathGbk)
{
    int sw;
    int sh;

    m_path = (pathGbk != NULL) ? pathGbk : "";
    m_ok = false;
    m_closing = false;
    g_app.SetVideoOpened(0);

    g_app.GetLogger().Log("VideoWindow: PlayModal 进入 parent=%p 文件=%s",
                          (void *)hwndParent,
                          (pathGbk != NULL) ? pathGbk : "(null)");
    sw = GetSystemMetrics(SM_CXSCREEN);
    sh = GetSystemMetrics(SM_CYSCREEN);
    if (sw <= 0) {
        sw = 480;
    }
    if (sh <= 0) {
        sh = 720;
    }
    if (!Create(0, 0, sw, sh, hwndParent, 0, WS_POPUP)) {
        /* 有些 ROM 不接受带父窗口的 WS_POPUP，换个方式再试一次 */
        g_app.GetLogger().Log("VideoWindow: Create(带父窗) 失败 err=%lu，试无父窗",
                              (unsigned long)GetLastError());
        if (!Create(0, 0, sw, sh, NULL, 0, WS_POPUP)) {
            g_app.GetLogger().Log("VideoWindow: Create(无父窗) 也失败 err=%lu",
                                  (unsigned long)GetLastError());
            return false;
        }
    }

    /*
     * OnInitDialog 是在 CreateWindowEx 内部被调用的，那时窗口还没建完，
     * 不能在那里开播放器（会把 Create 弄失败）。
     * 而且 PlayerCore 打开媒体需要消息循环，所以先把「打开播放器」这个
     * 消息投递给自己，等 DoModal() 的消息循环跑起来之后再处理。
     */
    PostMessage(MZ_WM_OPEN_PLAYER, 0, 0);
    DoModal();

    ::KillTimer(m_hWnd, DM_TIMER_ID);
    if (m_smoothSaved) {
        ::SystemParametersInfoW(SPI_SETFONTSMOOTHING, m_oldSmooth, NULL, 0);
        m_smoothSaved = 0;
    }
    if (m_screenOn) {
        SetScrennAutoOff();
        m_screenOn = 0;
    }
    if (m_lockRegistered) {
        UnRegisterShellMessage(m_hWnd,
                               WM_MZSH_ENTRY_LOCKPHONE | WM_MZSH_LEAVE_LOCKPHONE);
        m_lockRegistered = 0;
    }
    if (m_overlayOn) {
        m_overlay.Clear();
        m_overlayOn = 0;
    }
    /* 退出时恢复原始方向（带重试） */
    bc_screen_portrait();
    m_rotated = 0;
    m_unrotated = 0;
    return m_ok;
}

/* 内置 MzVideo 用 ChangeDisplaySettingsEx 转横屏（二进制里有 "ChangeDisplaySettingsEx
 * error!"）。coredll 导出了它、SDK 头没声明，这里手动声明并做参数矩阵尝试。 */
extern "C" LONG APIENTRY ChangeDisplaySettingsEx(LPCWSTR, LPDEVMODEW, HWND, DWORD, LPVOID);
extern "C" BOOL APIENTRY EnumDisplaySettings(LPCWSTR, DWORD, LPDEVMODEW);

#ifndef ENUM_CURRENT_SETTINGS
#define ENUM_CURRENT_SETTINGS ((DWORD)-1)
#endif
#ifndef DM_DISPLAYORIENTATION
#define DM_DISPLAYORIENTATION 0x00000080
#endif
#ifndef DM_PELSWIDTH
#define DM_PELSWIDTH 0x00080000
#endif
#ifndef DM_PELSHEIGHT
#define DM_PELSHEIGHT 0x00100000
#endif

void VideoWindow::OpenPlayer()
{
    int sw;
    int sh;
    long vw;
    long vh;
    int w;
    int h;
    int x;
    int y;
    RECT rc;

    if (!m_player.Prepare(m_hWnd, m_path.c_str())) {
        g_app.GetLogger().Log("VideoWindow: 播放器打开失败 hr=0x%08lX",
                              (unsigned long)m_player.LastError());
        LoadingWnd::Hide(false);
        PostMessage(MZ_WM_CLOSE_SELF, 0, 0);
        return;
    }
    /* 打开媒体期间（Prepare 里会泵消息）用户可能点了返回：放弃开播 */
    if (g_app.IsCancelled()) {
        g_app.GetLogger().Log("VideoWindow: 加载中被取消，放弃开播");
        m_player.Close();
        LoadingWnd::Hide(true);
        PostMessage(MZ_WM_CLOSE_SELF, 0, 0);
        return;
    }

    /*
     * 横屏视频：像内置 MzVideo 那样把屏幕转成横屏（ChangeDisplaySettingsEx
     * orientation=0，只改方向、不动宽高）。竖屏视频保持竖屏。
     */
    vw = m_player.VideoW();
    vh = m_player.VideoH();
    m_rotated = 0;
    if (vw >= vh && !bc_screen_is_landscape()) {
        bc_screen_landscape();
        g_app.GetLogger().Log("转横屏 屏幕 %dx%d",
                              GetSystemMetrics(SM_CXSCREEN),
                              GetSystemMetrics(SM_CYSCREEN));
        if (bc_screen_is_landscape()) {
            m_rotated = 1;
        }
    }

    /*
     * M8 的 PlayerCore 会把画面拉伸铺满宿主窗口（不做等比），所以宿主窗口
     * 本身必须与视频同比例。这里按当前屏幕方向把视频等比放大到最大、居中。
     */
    sw = GetSystemMetrics(SM_CXSCREEN);
    sh = GetSystemMetrics(SM_CYSCREEN);
    if (sw <= 0) {
        sw = 480;
    }
    if (sh <= 0) {
        sh = 720;
    }
    vw = m_player.VideoW();
    vh = m_player.VideoH();
    w = sw;
    h = sh;
    if (vw > 0 && vh > 0) {
        h = (int)((long)sw * vh / vw);
        if (h > sh) {
            h = sh;
            w = (int)((long)sh * vw / vh);
        }
    }
    x = (sw - w) / 2;
    y = (sh - h) / 2;
    g_app.GetLogger().Log("VideoWindow: 视频 %ldx%ld -> 窗口 %dx%d @(%d,%d) 屏幕 %dx%d",
                          vw, vh, w, h, x, y, sw, sh);

    ::SetWindowPos(m_hWnd, NULL, x, y, w, h, SWP_NOZORDER);

    rc.left = 0;
    rc.top = 0;
    rc.right = w;
    rc.bottom = h;

    m_ok = m_player.Start(m_hWnd, rc);
    g_app.GetLogger().Log("VideoWindow: 播放器%s hr=0x%08lX",
                          m_ok ? "启动成功" : "启动失败",
                          (unsigned long)m_player.LastError());
    if (g_app.IsCancelled()) {
        g_app.GetLogger().Log("VideoWindow: 启动后被取消，放弃播出");
        m_player.Close();
        LoadingWnd::Hide(true);
        PostMessage(MZ_WM_CLOSE_SELF, 0, 0);
        return;
    }
    if (m_ok) {
        g_app.SetVideoOpened(1);
        m_playStart = ::GetTickCount();
        m_playing = 1;
        /* 播放期间屏幕常亮：不自动息屏。息屏后继续写 DDraw overlay 会挂死，
         * 且官方 MzVideo 同样不支持锁屏。退出时 SetScrennAutoOff 恢复。 */
        SetScreenAlwaysOn(m_hWnd);
        m_screenOn = 1;
        g_app.GetLogger().Log("VideoWindow: 屏幕常亮（阻止自动息屏，防死机）");
        ShowControls();      /* 开播先亮出控制层 5 秒，提示如何退出 */

        /* 弹幕：取后台拉好的数据，建 DDraw overlay 层（色键透明，硬件叠加） */
        {
            int dn = 0;
            BcDanmaku *dms = g_app.TakeDanmaku(&dn);
            int ok = 0;

            m_danmaku.Load(dms, dn);
            /* 即使没有弹幕也要建 overlay：控制层与弹幕共用这条 overlay */
            ok = InitOverlay() ? 1 : 0;
            g_app.GetLogger().Log("弹幕层：%d 条 overlayInit=%d", dn, ok);
            if (ok) {
                /* 关掉系统字体平滑：避免弹幕字边出现 ClearType 彩边（退出恢复） */
                {
                    UINT on = 0;
                    if (::SystemParametersInfoW(SPI_GETFONTSMOOTHING, 0, &on, 0)) {
                        m_oldSmooth = on;
                        if (on) {
                            ::SystemParametersInfoW(SPI_SETFONTSMOOTHING, FALSE, NULL, 0);
                            m_smoothSaved = 1;
                        }
                    }
                }
                /* 注册锁屏通知：锁屏前清理 overlay，避免无法唤醒 */
                if (RegisterShellMessage(m_hWnd,
                        WM_MZSH_ENTRY_LOCKPHONE | WM_MZSH_LEAVE_LOCKPHONE)) {
                    m_msgLock = GetShellNotifyMsg_EntryLockPhone();
                    m_msgUnlock = GetShellNotifyMsg_LeaveLockPhone();
                    m_lockRegistered = 1;
                }
                ::SetTimer(m_hWnd, DM_TIMER_ID, 33, NULL);   /* 30fps */
            }
        }
    } else {
        /* 打不开就自动关窗，让上层回退 */
        PostMessage(MZ_WM_CLOSE_SELF, 0, 0);
    }
    /* 视频已开播：撤掉加载层，露出画面 */
    LoadingWnd::Hide(false);
}

/* 建 overlay：铺满整屏、清色键、显示。成功返回 true。 */
bool VideoWindow::InitOverlay()
{
    int sw = GetSystemMetrics(SM_CXSCREEN);
    int sh = GetSystemMetrics(SM_CYSCREEN);
    DDSURFACEDESC ddsd;

    if (sw <= 0) {
        sw = 720;
    }
    if (sh <= 0) {
        sh = 480;
    }
    m_ovW = sw;
    m_ovH = sh;
    m_overlayOn = 0;

    if (!m_overlay.Init(m_hWnd, 0, 0, sw, sh, DM_COLORKEY,
                        15, 0, true, PixFmtRGB565)) {
        return false;
    }
    memset(&ddsd, 0, sizeof(ddsd));
    ddsd.dwSize = sizeof(ddsd);
    if (!m_overlay.LockData(&ddsd) || ddsd.lpSurface == NULL) {
        m_overlay.Clear();
        return false;
    }
    {
        int rows = (ddsd.dwHeight > 0) ? (int)ddsd.dwHeight : sh;
        memset(ddsd.lpSurface, 0, (size_t)ddsd.lPitch * rows);
    }
    m_overlay.UnLockData(true);
    m_overlay.SetPosition(0, 0);
    m_overlay.ShowOverlay();
    m_overlayOn = 1;
    return true;
}

void VideoWindow::DrawDanmaku()
{
    DDSURFACEDESC ddsd;
    long pos;

    if (!m_overlayOn) {
        return;
    }
    /* 控制层到点自动隐藏 */
    if (m_ctrlShown && (long)(::GetTickCount() - m_ctrlHideAt) >= 0) {
        m_ctrlShown = 0;
    }
    if (!m_ctrlShown && !m_danmaku.Ready()) {
        return;
    }

    pos = (long)m_player.CurPosMs();
    if (pos <= 0 && m_playStart != 0) {
        pos = (long)(::GetTickCount() - m_playStart);
    }
    m_lastPos = pos;
    if (m_danmaku.Ready()) {
        /* 先在显存外更新/预渲染（含 CreateDIBSection），避免锁显存期间再碰显示 */
        m_danmaku.Update(pos, m_ovW, m_ovH);
    }

    memset(&ddsd, 0, sizeof(ddsd));
    ddsd.dwSize = sizeof(ddsd);
    if (!m_overlay.LockData(&ddsd) || ddsd.lpSurface == NULL) {
        return;
    }
    /* 双缓冲：每帧整屏清成色键，再贴弹幕/控制层，最后 flip 原子切换（无撕裂） */
    {
        int rows = (ddsd.dwHeight > 0) ? (int)ddsd.dwHeight : m_ovH;
        if (rows > m_ovH) {
            rows = m_ovH;   /* 防越界 */
        }
        memset(ddsd.lpSurface, 0, (size_t)ddsd.lPitch * rows);
    }
    if (m_danmaku.Ready()) {
        m_danmaku.Render(ddsd.lpSurface, (int)ddsd.lPitch, m_ovW, m_ovH);
    }
    if (m_ctrlShown) {
        DrawControls(ddsd.lpSurface, (int)ddsd.lPitch, m_ovW, m_ovH);
    }
    m_overlay.UnLockData(true);
}

/* ---------- 播放器控制层 ---------- */

/* 控制条高度：进度条 + 按钮行 */
#define VC_BAR_H 68

/* 按钮几何：都在 overlay 坐标（全屏），左边界 >=40 以保证落在视频窗
 * 可点区域内（窗口在屏幕 (40,0)，触摸坐标要 +40 才是 overlay 坐标）。 */
static void vc_prog_rect(int w, int h, RECT *r)
{
    r->left = 48;
    r->right = w - 16;
    r->top = h - VC_BAR_H + 8;
    r->bottom = r->top + 10;
}

static void vc_close_rect(int w, int h, RECT *r)
{
    (void)w;
    r->left = 48;
    r->right = 48 + 100;
    r->top = h - VC_BAR_H + 26;
    r->bottom = r->top + 34;
}

static void vc_play_rect(int w, int h, RECT *r)
{
    (void)w;
    r->left = 156;
    r->right = 156 + 72;
    r->top = h - VC_BAR_H + 26;
    r->bottom = r->top + 34;
}

static void vc_voldn_rect(int w, int h, RECT *r)
{
    (void)w;
    r->left = 236;
    r->right = 236 + 84;
    r->top = h - VC_BAR_H + 26;
    r->bottom = r->top + 34;
}

static void vc_voup_rect(int w, int h, RECT *r)
{
    (void)w;
    r->left = 328;
    r->right = 328 + 84;
    r->top = h - VC_BAR_H + 26;
    r->bottom = r->top + 34;
}

static void vc_fill(HDC dc, const RECT *r, COLORREF c)
{
    HBRUSH b = CreateSolidBrush(c);
    FillRect(dc, r, b);
    DeleteObject(b);
}

void VideoWindow::ShowControls()
{
    /* 先把控制条位图建好：CreateDIBSection 绝不能放在锁显存期间做 */
    if (m_ctrlBmp == NULL && m_ovW > 0) {
        struct {
            BITMAPINFOHEADER hdr;
            DWORD masks[3];
        } bi;
        HDC dc = ::GetDC(m_hWnd);
        wchar_t face[LF_FACESIZE];
        HDC sdc;

        face[0] = L'\0';
        sdc = ::GetDC(NULL);
        if (sdc != NULL) {
            HGDIOBJ old = SelectObject(sdc, GetStockObject(SYSTEM_FONT));
            GetTextFaceW(sdc, LF_FACESIZE, face);
            SelectObject(sdc, old);
            ReleaseDC(NULL, sdc);
        }
        m_ctrlDC = CreateCompatibleDC(dc);
        {
            LOGFONTW lf;
            memset(&lf, 0, sizeof(lf));
            lf.lfHeight = -22;
            lf.lfWeight = FW_BOLD;
            lf.lfCharSet = DEFAULT_CHARSET;
            lf.lfQuality = NONANTIALIASED_QUALITY;
            lf.lfPitchAndFamily = DEFAULT_PITCH | FF_DONTCARE;
            wcscpy(lf.lfFaceName, (face[0] != L'\0') ? face : L"宋体");
            m_ctrlFont = CreateFontIndirectW(&lf);
        }
        memset(&bi, 0, sizeof(bi));
        bi.hdr.biSize = sizeof(BITMAPINFOHEADER);
        bi.hdr.biWidth = m_ovW;
        bi.hdr.biHeight = -VC_BAR_H;
        bi.hdr.biPlanes = 1;
        bi.hdr.biBitCount = 16;
        bi.hdr.biCompression = BI_BITFIELDS;
        bi.masks[0] = 0xF800;
        bi.masks[1] = 0x07E0;
        bi.masks[2] = 0x001F;
        m_ctrlBmp = CreateDIBSection(dc, (BITMAPINFO *)&bi, DIB_RGB_COLORS,
                                     &m_ctrlBits, NULL, 0);
        if (m_ctrlBmp != NULL) {
            m_ctrlOldBmp = (HBITMAP)SelectObject(m_ctrlDC, m_ctrlBmp);
            m_ctrlBw = m_ovW;
            m_ctrlBh = VC_BAR_H;
            m_ctrlPitch = m_ovW * 2;
        }
        ::ReleaseDC(m_hWnd, dc);
    }
    m_ctrlShown = 1;
    m_ctrlHideAt = ::GetTickCount() + 5000;
}

void VideoWindow::DrawControls(void *surface, int pitch, int w, int h)
{
    HDC dc = m_ctrlDC;
    HGDIOBJ oldFont;
    RECT r;
    long len;
    long pos;
    int j;

    if (surface == NULL || m_ctrlBmp == NULL || dc == NULL) {
        return;
    }

    /* 背景条（深灰非纯黑，避免被色键当透明抠掉） */
    r.left = 0;
    r.top = 0;
    r.right = m_ctrlBw;
    r.bottom = m_ctrlBh;
    vc_fill(dc, &r, RGB(30, 30, 30));

    len = m_player.LengthMs();
    pos = m_lastPos;
    if (len < 0) {
        len = 0;
    }
    if (pos < 0) {
        pos = 0;
    }
    if (len > 0 && pos > len) {
        pos = len;
    }

    oldFont = (HGDIOBJ)SelectObject(dc, m_ctrlFont);
    SetBkMode(dc, TRANSPARENT);

    /* 进度条 */
    vc_prog_rect(m_ctrlBw, m_ctrlBh, &r);
    vc_fill(dc, &r, RGB(80, 80, 80));
    if (len > 0 && pos > 0 && r.right > r.left) {
        RECT f = r;
        f.right = r.left +
                  (int)((double)(r.right - r.left) * (double)pos / (double)len);
        vc_fill(dc, &f, RGB(250, 130, 40));
    }

    /* 返回按钮 */
    vc_close_rect(m_ctrlBw, m_ctrlBh, &r);
    vc_fill(dc, &r, RGB(70, 70, 70));
    SetTextColor(dc, RGB(255, 255, 255));
    DrawTextW(dc, L"返回", -1, &r, DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    /* 播放 / 暂停按钮 */
    vc_play_rect(m_ctrlBw, m_ctrlBh, &r);
    vc_fill(dc, &r, RGB(70, 70, 70));
    if (m_playing) {
        RECT a;
        RECT b;
        HBRUSH br = CreateSolidBrush(RGB(255, 255, 255));

        a.left = r.left + 28;
        a.top = r.top + 9;
        a.right = a.left + 6;
        a.bottom = r.bottom - 9;
        b = a;
        b.left = a.right + 8;
        b.right = b.left + 6;
        FillRect(dc, &a, br);
        FillRect(dc, &b, br);
        DeleteObject(br);
    } else {
        POINT p[3];
        int cx = (r.left + r.right) / 2;
        int cy = (r.top + r.bottom) / 2;
        HBRUSH br = CreateSolidBrush(RGB(255, 255, 255));
        HGDIOBJ ob = SelectObject(dc, br);
        HGDIOBJ op = SelectObject(dc, GetStockObject(NULL_PEN));

        p[0].x = cx - 8;
        p[0].y = cy - 11;
        p[1].x = cx - 8;
        p[1].y = cy + 11;
        p[2].x = cx + 12;
        p[2].y = cy;
        Polygon(dc, p, 3);
        SelectObject(dc, op);
        SelectObject(dc, ob);
        DeleteObject(br);
    }

    /* 音量 - / + */
    vc_voldn_rect(m_ctrlBw, m_ctrlBh, &r);
    vc_fill(dc, &r, RGB(70, 70, 70));
    SetTextColor(dc, RGB(255, 255, 255));
    DrawTextW(dc, L"音量 -", -1, &r, DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    vc_voup_rect(m_ctrlBw, m_ctrlBh, &r);
    vc_fill(dc, &r, RGB(70, 70, 70));
    DrawTextW(dc, L"音量 +", -1, &r, DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    /* 当前音量值 */
    {
        wchar_t vt[16];
        RECT vr;

        wsprintfW(vt, L"%d", m_volume);
        vr.left = 420;
        vr.top = r.top;
        vr.right = 470;
        vr.bottom = r.bottom;
        SetTextColor(dc, RGB(250, 200, 90));
        DrawTextW(dc, vt, -1, &vr, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    }

    /* 时间（右对齐） */
    {
        wchar_t t[64];
        long ps = pos / 1000;
        long pl = len / 1000;
        RECT tr;

        if (pl > 0 || ps > 0) {
            wsprintfW(t, L"%d:%02d / %d:%02d",
                      (int)(ps / 60), (int)(ps % 60),
                      (int)(pl / 60), (int)(pl % 60));
        } else {
            t[0] = L'\0';
        }
        tr.left = 480;
        tr.top = r.top;
        tr.right = m_ctrlBw - 16;
        tr.bottom = r.bottom;
        SetTextColor(dc, RGB(230, 230, 230));
        DrawTextW(dc, t, -1, &tr, DT_RIGHT | DT_VCENTER | DT_SINGLELINE);
    }
    SelectObject(dc, oldFont);

    /* 逐行贴到 overlay 显存（同 16bpp RGB565，直接 memcpy） */
    {
        int yy0 = h - m_ctrlBh;
        int rows = m_ctrlBh;
        int cw = m_ctrlBw;

        if (cw > w) {
            cw = w;
        }
        if (yy0 < 0) {
            yy0 = 0;
        }
        if (yy0 + rows > h) {
            rows = h - yy0;
        }
        for (j = 0; j < rows; j++) {
            memcpy((unsigned char *)surface + (size_t)(yy0 + j) * pitch,
                   (unsigned char *)m_ctrlBits + (size_t)j * m_ctrlPitch,
                   (size_t)cw * 2);
        }
    }
}

void VideoWindow::FreeControls()
{
    if (m_ctrlDC != NULL) {
        if (m_ctrlOldBmp != NULL) {
            SelectObject(m_ctrlDC, m_ctrlOldBmp);
            m_ctrlOldBmp = NULL;
        }
        DeleteDC(m_ctrlDC);
        m_ctrlDC = NULL;
    }
    if (m_ctrlBmp != NULL) {
        DeleteObject(m_ctrlBmp);
        m_ctrlBmp = NULL;
    }
    if (m_ctrlFont != NULL) {
        DeleteObject(m_ctrlFont);
        m_ctrlFont = NULL;
    }
    m_ctrlBits = NULL;
    m_ctrlPitch = 0;
    m_ctrlBw = 0;
    m_ctrlBh = 0;
}

int VideoWindow::HandleControlTap(int ox, int oy)
{
    RECT r;

    /* 返回：退出播放 */
    vc_close_rect(m_ovW, m_ovH, &r);
    if (ox >= r.left && ox < r.right && oy >= r.top && oy < r.bottom) {
        if (!m_closing) {
            m_closing = true;
            m_player.Close();
        }
        EndModal(ID_CANCEL);
        return 1;
    }
    /* 播放 / 暂停 */
    vc_play_rect(m_ovW, m_ovH, &r);
    if (ox >= r.left && ox < r.right && oy >= r.top && oy < r.bottom) {
        if (m_player.IsPlaying()) {
            m_player.Pause();
        } else {
            m_player.Resume();
        }
        m_playing = m_player.IsPlaying() ? 1 : 0;
        return 1;
    }
    /* 音量 - */
    vc_voldn_rect(m_ovW, m_ovH, &r);
    if (ox >= r.left && ox < r.right && oy >= r.top && oy < r.bottom) {
        m_volume -= 10;
        if (m_volume < 0) {
            m_volume = 0;
        }
        m_player.SetVolume(m_volume);
        return 1;
    }
    /* 音量 + */
    vc_voup_rect(m_ovW, m_ovH, &r);
    if (ox >= r.left && ox < r.right && oy >= r.top && oy < r.bottom) {
        m_volume += 10;
        if (m_volume > 100) {
            m_volume = 100;
        }
        m_player.SetVolume(m_volume);
        return 1;
    }
    /* 进度条：跳转 */
    vc_prog_rect(m_ovW, m_ovH, &r);
    if (oy >= r.top - 10 && oy <= r.bottom + 10 &&
        ox >= r.left && ox <= r.right) {
        long len = m_player.LengthMs();

        if (len > 0 && r.right > r.left) {
            long t = (long)((double)(ox - r.left) /
                            (double)(r.right - r.left) * (double)len);

            if (t < 0) {
                t = 0;
            }
            if (t > len) {
                t = len;
            }
            m_player.SeekTo(t);
            m_playStart = ::GetTickCount() - (DWORD)t;
            m_lastPos = t;
        }
        return 1;
    }
    return 0;
}

void VideoWindow::OnLButtonDown(UINT fwKeys, int xPos, int yPos)
{
    RECT wr;
    int ox;
    int oy;

    if (!m_overlayOn) {
        CMzWndEx::OnLButtonDown(fwKeys, xPos, yPos);
        return;
    }
    if (!m_ctrlShown) {
        /* 点一下：亮出控制层 */
        ShowControls();
        return;
    }
    /* 已显示：命中按钮就执行，否则点空白处收起 */
    ::GetWindowRect(m_hWnd, &wr);
    ox = xPos + wr.left;   /* overlay 在屏幕 (0,0)，把窗口坐标换到屏幕坐标 */
    oy = yPos + wr.top;
    if (HandleControlTap(ox, oy)) {
        m_ctrlHideAt = ::GetTickCount() + 5000;   /* 操作后再续 5 秒 */
        return;
    }
    m_ctrlShown = 0;
}

BOOL VideoWindow::OnInitDialog()
{
    g_app.GetLogger().Log("VideoWindow: OnInitDialog 进入 hwnd=%p %dx%d",
                          (void *)m_hWnd, GetWidth(), GetHeight());
    if (!CMzWndEx::OnInitDialog()) {
        g_app.GetLogger().Log("VideoWindow: base OnInitDialog 返回 FALSE");
        return FALSE;
    }
    SetBgColor(RGB(0, 0, 0));

    m_btnBack.SetButtonType(MZC_BUTTON_PELLUCID);
    m_btnBack.SetID(MZ_ID_BACK);
    m_btnBack.SetText(L"返回");
    AddUiWin(&m_btnBack);

    UpdateLayout();
    return TRUE;
}

void VideoWindow::UpdateLayout()
{
    int w = GetWidth();
    int h = GetHeight();

    m_btnBack.SetPos(w - 150, h - 92, 130, 62);
}

void VideoWindow::OnSize(int nWidth, int nHeight)
{
    CMzWndEx::OnSize(nWidth, nHeight);
    UpdateLayout();
}

void VideoWindow::OnTimer(UINT_PTR nIDEvent)
{
    if (nIDEvent == DM_TIMER_ID) {
        DrawDanmaku();
        return;
    }
    CMzWndEx::OnTimer(nIDEvent);
}

void VideoWindow::OnMzCommand(WPARAM wParam, LPARAM lParam)
{
    if (LOWORD(wParam) == MZ_ID_BACK) {
        if (!m_closing) {
            m_closing = true;
            m_player.Close();
        }
        EndModal(ID_CANCEL);
    }
}

LRESULT VideoWindow::MzDefWndProc(UINT message, WPARAM wParam, LPARAM lParam)
{
    if (message == WM_KEYDOWN &&
        (wParam == VK_ESCAPE || wParam == VK_BACK)) {
        if (!m_closing) {
            m_closing = true;
            m_player.Close();
        }
        EndModal(ID_CANCEL);
        return 0;
    }
    if (message == MZ_WM_OPEN_PLAYER) {
        OpenPlayer();
        return 0;
    }
    if (m_lockRegistered && m_msgLock != 0 && message == m_msgLock) {
        /* 进入锁屏/息屏：暂停播放、释放 overlay，并把屏幕转回竖屏
         *（M8 锁屏界面是竖屏的，横屏状态下锁屏会无法唤醒） */
        m_player.Pause();
        ::KillTimer(m_hWnd, DM_TIMER_ID);
        if (m_overlayOn) {
            m_overlay.Clear();
            m_overlayOn = 0;
        }
        if (m_rotated && !m_unrotated) {
            bc_screen_portrait();
            m_unrotated = 1;
        }
        /* 关键：隐藏全屏窗口，把屏幕让给 shell 锁屏界面（否则罩住锁屏，
         * 亮屏后出不来锁屏，表现为"无法唤醒"） */
        ::ShowWindow(m_hWnd, SW_HIDE);
        g_app.GetLogger().Log("VideoWindow: 锁屏，暂停播放、释放弹幕层、转竖屏并隐藏窗口");
        return 0;
    }
    if (m_lockRegistered && m_msgUnlock != 0 && message == m_msgUnlock) {
        /* 亮屏：先恢复横屏，再恢复播放、重建弹幕层并显示窗口 */
        if (m_unrotated) {
            bc_screen_landscape();
            m_unrotated = 0;
        }
        ::ShowWindow(m_hWnd, SW_SHOW);
        m_player.Resume();
        if (m_danmaku.Ready() && InitOverlay()) {
            m_playStart = ::GetTickCount() - (DWORD)m_lastPos;
            ::SetTimer(m_hWnd, DM_TIMER_ID, 33, NULL);
        }
        g_app.GetLogger().Log("VideoWindow: 解锁，转回横屏并恢复播放/弹幕层");
        return 0;
    }
    if (message == WM_TIMER && wParam == DM_TIMER_ID) {
        DrawDanmaku();
        return 0;
    }
    if (message == MZ_WM_CLOSE_SELF) {
        if (!m_closing) {
            m_closing = true;
            m_player.Close();
        }
        EndModal(ID_CANCEL);
        return 0;
    }
    return CMzWndEx::MzDefWndProc(message, wParam, lParam);
}
