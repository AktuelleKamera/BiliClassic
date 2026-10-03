/* =====================================================================
 * LoadingWnd.cpp - 全屏加载层实现（纯 Win32 顶层窗口）
 * ===================================================================== */
#include "LoadingWnd.h"
#include "ScreenRot.h"
#include "loading_frames.h"
#include "../app/AppContext.h"

HWND    LoadingWnd::s_hwnd = NULL;
HBITMAP LoadingWnd::s_frames[5] = { NULL, NULL, NULL, NULL, NULL };
int     LoadingWnd::s_frameCount = 0;
int     LoadingWnd::s_frame = 0;
bool    LoadingWnd::s_show = false;
bool    LoadingWnd::s_cancel = false;
wchar_t LoadingWnd::s_line1[128] = { 0 };
wchar_t LoadingWnd::s_line2[128] = { 0 };
HFONT   LoadingWnd::s_font = NULL;
int     LoadingWnd::s_w = 0;
int     LoadingWnd::s_h = 0;

#define LD_TIMER_ID  1
#define LD_BG        RGB(186, 186, 186)
#define LD_FG        RGB(97, 97, 97)

/* 双缓冲：整窗先画到内存 DC，最后一次 BitBlt（GDI 逐项画会闪） */
static HDC     s_memDC = NULL;
static HBITMAP s_memBmp = NULL;
static HGDIOBJ s_memOld = NULL;
static int     s_memW = 0;
static int     s_memH = 0;

/* 内嵌小电视帧是 16bpp RGB565（120x120 x5）的 base64，运行时解码直接建 DIB，
 * 不再依赖外部 BMP 文件（避免部署/位深问题）。 */
static int b64_val(char c)
{
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

static int b64_decode(const char **lines, unsigned char *out, int cap)
{
    int n = 0;
    int acc = 0;
    int bits = 0;

    while (*lines != NULL && n < cap) {
        const char *s = *lines++;

        while (*s != '\0' && n < cap) {
            int v = b64_val(*s++);

            if (v < 0) {
                continue;    /* 换行/空白忽略 */
            }
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[n++] = (unsigned char)((acc >> bits) & 0xFF);
            }
        }
    }
    return n;
}

void LoadingWnd::EnsureClass()
{
    static bool done = false;
    WNDCLASS wc;

    if (done) {
        return;
    }
    memset(&wc, 0, sizeof(wc));
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = LoadingWnd::WndProc;
    wc.hInstance = GetModuleHandle(NULL);
    wc.hCursor = NULL;
    wc.lpszClassName = L"BcLoadingWnd";
    RegisterClass(&wc);
    done = true;
}

/* 内嵌帧解码后的 24bpp BGR、顶朝下数据（5 帧紧密排列） */
static const unsigned char *g_frameData = NULL;
static const int g_frameW = 120;
static const int g_frameH = 120;

void LoadingWnd::LoadFrames()
{
    static unsigned char buf[120 * 120 * 3 * 5];
    int n;

    if (s_frameCount > 0) {
        return;
    }
    n = b64_decode(bc_tv_b64, buf, (int)sizeof(buf));
    if (n < 120 * 120 * 3 * 5) {
        g_app.GetLogger().Log("LoadingWnd: 内嵌帧解码不足 %d 字节", n);
        return;
    }
    g_frameData = buf;
    s_frameCount = 5;
    g_app.GetLogger().Log("LoadingWnd: 内嵌小电视帧 %d/5", s_frameCount);
}

void LoadingWnd::FreeRes()
{
    g_frameData = NULL;
    s_frameCount = 0;
}

void LoadingWnd::Show()
{
    int sw;
    int sh;

    EnsureClass();
    bc_screen_landscape();       /* 加载层全程横屏（和 WP/WM 版一致） */
    sw = GetSystemMetrics(SM_CXSCREEN);
    sh = GetSystemMetrics(SM_CYSCREEN);
    if (sw <= 0) {
        sw = 480;
    }
    if (sh <= 0) {
        sh = 720;
    }
    s_w = sw;
    s_h = sh;

    if (s_hwnd == NULL) {
        LOGFONTW lf;
        wchar_t face[LF_FACESIZE];
        HDC sdc;

        s_hwnd = CreateWindowEx(WS_EX_TOPMOST, L"BcLoadingWnd", L"",
                                WS_POPUP, 0, 0, sw, sh,
                                NULL, NULL, GetModuleHandle(NULL), NULL);
        if (s_hwnd == NULL) {
            return;
        }
        face[0] = L'\0';
        sdc = GetDC(NULL);
        if (sdc != NULL) {
            HGDIOBJ old = SelectObject(sdc, GetStockObject(SYSTEM_FONT));
            GetTextFaceW(sdc, LF_FACESIZE, face);
            SelectObject(sdc, old);
            ReleaseDC(NULL, sdc);
        }
        memset(&lf, 0, sizeof(lf));
        lf.lfHeight = -20;
        lf.lfCharSet = DEFAULT_CHARSET;
        lf.lfQuality = NONANTIALIASED_QUALITY;
        lf.lfPitchAndFamily = DEFAULT_PITCH | FF_DONTCARE;
        wcscpy(lf.lfFaceName, (face[0] != L'\0') ? face : L"宋体");
        s_font = CreateFontIndirectW(&lf);
        LoadFrames();
    }

    if (!s_show) {
        s_frame = 0;                       /* 只在首次显示时重置动画 */
        SetTimer(s_hwnd, LD_TIMER_ID, 100, NULL);
    }
    s_show = true;
    SetWindowPos(s_hwnd, HWND_TOPMOST, 0, 0, sw, sh,
                 SWP_NOACTIVATE | SWP_SHOWWINDOW);
    InvalidateRect(s_hwnd, NULL, FALSE);
    UpdateWindow(s_hwnd);
}

void LoadingWnd::SetStatus(const wchar_t *text)
{
    if (text == NULL || text[0] == L'\0') {
        return;
    }
    if (wcscmp(text, s_line2) == 0) {
        return;    /* 同一阶段重复设置就不占一行，避免两行一样 */
    }
    wcscpy(s_line1, s_line2);
    wcscpy(s_line2, text);
    if (s_hwnd != NULL && s_show) {
        InvalidateRect(s_hwnd, NULL, FALSE);
    }
}

void LoadingWnd::Hide(bool restorePortrait)
{
    if (!s_show && !restorePortrait) {
        return;
    }
    g_app.GetLogger().Log("LoadingWnd: Hide 显示中=%d 恢复竖屏=%d",
                          s_show ? 1 : 0, restorePortrait ? 1 : 0);
    s_show = false;
    if (s_hwnd != NULL) {
        KillTimer(s_hwnd, LD_TIMER_ID);
        ShowWindow(s_hwnd, SW_HIDE);
    }
    s_line1[0] = L'\0';
    s_line2[0] = L'\0';
    if (restorePortrait) {
        bc_screen_portrait();
    }
}

bool LoadingWnd::IsShowing()
{
    return s_show;
}

bool LoadingWnd::TakeCancel()
{
    bool c = s_cancel;

    s_cancel = false;
    return c;
}

/* 小电视在窗口里的矩形（居中） */
static void tv_rect(HWND hWnd, RECT *tr)
{
    RECT cr;

    GetClientRect(hWnd, &cr);
    tr->left = (cr.right - g_frameW) / 2;
    tr->top = (cr.bottom - g_frameH) / 2;
    tr->right = tr->left + g_frameW;
    tr->bottom = tr->top + g_frameH;
}

LRESULT CALLBACK LoadingWnd::WndProc(HWND hWnd, UINT msg,
                                     WPARAM wParam, LPARAM lParam)
{
    switch (msg) {
    case WM_TIMER:
        if (wParam == LD_TIMER_ID) {
            int sw = GetSystemMetrics(SM_CXSCREEN);
            int sh = GetSystemMetrics(SM_CYSCREEN);

            if (sw > 0 && sh > 0 && (sw != s_w || sh != s_h)) {
                s_w = sw;
                s_h = sh;
                SetWindowPos(hWnd, HWND_TOPMOST, 0, 0, sw, sh,
                             SWP_NOACTIVATE | SWP_SHOWWINDOW);
                InvalidateRect(hWnd, NULL, FALSE);   /* 尺寸变了整屏重画 */
            } else if (s_frameCount > 0) {
                RECT tr;

                s_frame = (s_frame + 1) % s_frameCount;
                /* 只重画小电视那小块，避免整屏重绘闪烁 */
                tv_rect(hWnd, &tr);
                InvalidateRect(hWnd, &tr, FALSE);
            }
        }
        return 0;

    case WM_PAINT:
        {
            PAINTSTRUCT ps;
            HDC pdc = BeginPaint(hWnd, &ps);
            HDC dc;
            RECT rc;
            int pw, ph;

            GetClientRect(hWnd, &rc);
            pw = rc.right - rc.left;
            ph = rc.bottom - rc.top;
            if (s_memDC == NULL) {
                s_memDC = CreateCompatibleDC(pdc);
            }
            if (s_memDC != NULL &&
                (s_memBmp == NULL || s_memW != pw || s_memH != ph)) {
                if (s_memBmp != NULL) {
                    SelectObject(s_memDC, s_memOld);
                    DeleteObject(s_memBmp);
                    s_memBmp = NULL;
                }
                if (pw > 0 && ph > 0) {
                    s_memBmp = CreateCompatibleBitmap(pdc, pw, ph);
                }
                if (s_memBmp != NULL) {
                    s_memOld = SelectObject(s_memDC, s_memBmp);
                    s_memW = pw;
                    s_memH = ph;
                }
            }
            dc = (s_memBmp != NULL) ? s_memDC : pdc;
            {
                HBRUSH bg = CreateSolidBrush(LD_BG);
                FillRect(dc, &rc, bg);   /* 只填需要更新的一小块 */
                DeleteObject(bg);
            }

            /* 居中小电视（24bpp BGR 直投，GDI 转换可靠，不会偏色） */
            if (s_frameCount > 0 && g_frameData != NULL && s_frame < s_frameCount) {
                struct {
                    BITMAPINFOHEADER hdr;
                } bi;
                const unsigned char *fp = g_frameData +
                    (size_t)s_frame * g_frameW * g_frameH * 3;
                int x = (rc.right - g_frameW) / 2;
                int y = (rc.bottom - g_frameH) / 2;

                if (x < 0) {
                    x = 0;
                }
                if (y < 0) {
                    y = 0;
                }
                memset(&bi, 0, sizeof(bi));
                bi.hdr.biSize = sizeof(BITMAPINFOHEADER);
                bi.hdr.biWidth = g_frameW;
                bi.hdr.biHeight = -g_frameH;   /* 顶朝下 */
                bi.hdr.biPlanes = 1;
                bi.hdr.biBitCount = 24;
                bi.hdr.biCompression = BI_RGB;
                StretchDIBits(dc, x, y, g_frameW, g_frameH,
                              0, 0, g_frameW, g_frameH,
                              fp, (BITMAPINFO *)&bi, DIB_RGB_COLORS, SRCCOPY);
            } else {
                /* 兜底：素材没加载出来也画个电视轮廓 */
                HPEN pen = CreatePen(PS_SOLID, 3, RGB(40, 40, 40));
                HGDIOBJ op = SelectObject(dc, pen);
                HGDIOBJ ob = SelectObject(dc, GetStockObject(NULL_BRUSH));
                int l = (rc.right - 120) / 2;
                int t = (rc.bottom - 80) / 2;

                Rectangle(dc, l, t, l + 120, t + 80);
                SelectObject(dc, ob);
                SelectObject(dc, op);
                DeleteObject(pen);
            }

            /* 左上返回箭头（‹） */
            {
                HPEN pen = CreatePen(PS_SOLID, 3, LD_FG);
                HGDIOBJ op = SelectObject(dc, pen);
                POINT p[3];

                p[0].x = 26;
                p[0].y = 8;
                p[1].x = 12;
                p[1].y = 24;
                p[2].x = 26;
                p[2].y = 40;
                Polyline(dc, p, 3);
                SelectObject(dc, op);
                DeleteObject(pen);
            }

            /* 底部两行状态（上一行 → 当前行） */
            if (s_font != NULL) {
                HGDIOBJ of = SelectObject(dc, s_font);
                RECT tr;

                SetBkMode(dc, TRANSPARENT);
                SetTextColor(dc, LD_FG);
                tr.left = 10;
                tr.right = rc.right - 10;
                tr.top = rc.bottom - 72;
                tr.bottom = tr.top + 28;
                DrawTextW(dc, s_line1, -1, &tr,
                          DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX);
                tr.top = rc.bottom - 44;
                tr.bottom = tr.top + 28;
                DrawTextW(dc, s_line2, -1, &tr,
                          DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX);
                SelectObject(dc, of);
            }

            if (dc != pdc && s_memBmp != NULL) {
                BitBlt(pdc, 0, 0, pw, ph, s_memDC, 0, 0, SRCCOPY);
            }
            EndPaint(hWnd, &ps);
        }
        return 0;

    case WM_LBUTTONDOWN:
        {
            int x = LOWORD(lParam);
            int y = HIWORD(lParam);

            if (x >= 0 && x < 48 && y >= 0 && y < 48) {
                s_cancel = true;
                g_app.RequestCancel();   /* 通知后台任务放弃播放 */
                Hide();
            }
        }
        return 0;

    case WM_ERASEBKGND:
        return 1;
    }
    return DefWindowProc(hWnd, msg, wParam, lParam);
}
