/* =====================================================================
 * MainWindow.cpp - 主窗口实现
 * ---------------------------------------------------------------------
 * 职责边界：只做显示与转发；解析/下载/转码全在 src\app。
 * 关键词与日志用原生 Win32 EDIT（读取可靠、系统输入法友好），
 * 列表/按钮/标题/状态用 Mzfc 控件（保持 M8 触摸风格）。
 * ===================================================================== */
#include "MainWindow.h"
#include "App.h"
#include "TextUtil.h"
#include "VideoWindow.h"
#include "LoadingWnd.h"
#include "CoverCache.h"
#include "MyWindow.h"
#include "../../resource.h"
#include "../app/AppContext.h"
#include "../app/SearchService.h"
#include "../app/PlayService.h"
#include <Mzfc/ImagingHelper.h>
#include <Mzfc/FontHelper.h>
#include "../core/http.h"
#include <Mzfc/SipHelper.h>
#include <commctrl.h>
#include <shellsdk.h>
#include <stdio.h>
#include <stdlib.h>

#pragma warning(disable:4996)

MZ_IMPLEMENT_DYNAMIC(MainWindow)

/* =====================================================================
 * 检查更新：拉 version.json 和本机版本比（version_code 比大小）。
 *   {"version":"0.1.0.0","version_code":100,
 *    "download_url":"http://.../BiliClassic-0.1.0.0.cab",
 *    "force_update":false,"changelog":["...","..."]}
 * ===================================================================== */
static const char kUpdateUrl[] =
    "http://www.biliclassic.cn/mymobile/api/version.json";

static HWND s_updateWnd = NULL;
static char s_updateMsg[1024];
static std::wstring s_updateText;

/* 把 changelog 数组里的每一条追加到 out（UTF-8 -> GBK） */
static void append_changelog(const char *body, char *out, int cap)
{
    const char *p = strstr(body, "\"changelog\"");
    char tmp[512];
    char g[512];

    if (p == NULL) {
        return;
    }
    p = strchr(p, '[');
    if (p == NULL) {
        return;
    }
    p++;
    while (*p != '\0' && *p != ']') {
        int k = 0;

        while (*p != '\0' && *p != '"' && *p != ']') {
            p++;
        }
        if (*p != '"') {
            break;
        }
        p++;
        while (*p != '\0' && *p != '"' && k < (int)sizeof(tmp) - 1) {
            if (*p == '\\' && p[1] != '\0') {
                p++;                 /* 简单去转义 */
            }
            tmp[k++] = *p++;
        }
        tmp[k] = '\0';
        if (*p == '"') {
            p++;
        }
        if (tmp[0] != '\0') {
            utf8_to_ansi(tmp, g, (int)sizeof(g));
            str_append(out, cap, g);
            str_append(out, cap, "\n");
        }
    }
}

static void job_update(void *arg)
{
    char *body = NULL;
    int len = 0;
    int status = 0;
    char err[160];

    (void)arg;
    err[0] = '\0';
    s_updateMsg[0] = '\0';
    if (http_get_url(kUpdateUrl, "User-Agent: Mozilla/5.0\r\n",
                     &body, &len, &status, err, (int)sizeof(err)) != HTTP_OK ||
        status != 200 || body == NULL) {
        sprintf(s_updateMsg, "检查更新失败：%s",
                (err[0] != '\0') ? err : "网络错误");
    } else {
        char ver[64];
        char dl[256];
        long code = 0;

        ver[0] = '\0';
        dl[0] = '\0';
        json_get_string(body, "version", ver, (int)sizeof(ver));
        json_get_string(body, "download_url", dl, (int)sizeof(dl));
        json_get_int(body, "version_code", &code);
        if (code > BC_APP_VER_CODE) {
            sprintf(s_updateMsg, "发现新版本 %s（当前 %s）\n\n更新日志：\n",
                    (ver[0] != '\0') ? ver : "?", BC_APP_VER);
            append_changelog(body, s_updateMsg, (int)sizeof(s_updateMsg));
            str_append(s_updateMsg, (int)sizeof(s_updateMsg), "\n下载地址：\n");
            str_append(s_updateMsg, (int)sizeof(s_updateMsg), dl);
        } else if (code > 0) {
            sprintf(s_updateMsg, "已经是最新版本（%s）", BC_APP_VER);
        } else {
            str_copy(s_updateMsg, (int)sizeof(s_updateMsg),
                     "检查更新失败：版本信息解析不出来");
        }
        free(body);
    }
    /* 后台线程里先转成宽串，UI 线程直接拿去显示 */
    s_updateText = gbk2w(s_updateMsg);
    if (s_updateWnd != NULL) {
        PostMessage(s_updateWnd, WM_APP_UPDATE_DONE, 0, 0);
    }
}

/* =====================================================================
 * 回声洞（模仿 WP 版）：拉 echo.json，随机取一条匿名留言显示。
 *   [{"text":"...","author":"...","device":"...","time":"..."}, ...]
 * ===================================================================== */
static const char kEchoUrl[] =
    "http://www.biliclassic.cn/api/echo.json";
static const char kEchoUrl2[] =
    "http://www.biliclassic.cn/mymobile/api/echo.json";

static HWND s_echoWnd = NULL;
static std::wstring s_echoText;
static int s_echoLast = -1;

/* 取 JSON 对象里的字段（值为 UTF-8）转成宽串 */
static void echo_field_w(const char *obj, const char *key, wchar_t *out, int cap)
{
    char tmp[512];

    out[0] = L'\0';
    tmp[0] = '\0';
    if (json_get_string(obj, key, tmp, (int)sizeof(tmp)) && tmp[0] != '\0') {
        MultiByteToWideChar(CP_UTF8, 0, tmp, -1, out, cap);
    }
}

static void job_echo(void *arg)
{
    char *body = NULL;
    int status = 0;
    char err[160];
    int ok = 0;
    int u;

    (void)arg;
    err[0] = '\0';
    /* 先试 WP 版地址，失败再试 M8 命名空间，兼容两种部署 */
    for (u = 0; u < 2 && !ok; u++) {
        int len = 0;

        body = NULL;
        status = 0;
        err[0] = '\0';
        if (http_get_url((u == 0) ? kEchoUrl : kEchoUrl2,
                         "User-Agent: Mozilla/5.0\r\n",
                         &body, &len, &status, err, (int)sizeof(err)) == HTTP_OK &&
            status == 200 && body != NULL) {
            ok = 1;
        } else if (body != NULL) {
            free(body);
            body = NULL;
        }
    }
    if (!ok) {
        s_echoText = L"回声洞拉取失败：";
        s_echoText += gbk2w((err[0] != '\0') ? err : "网络错误");
        if (s_echoWnd != NULL) {
            PostMessage(s_echoWnd, WM_APP_ECHO_DONE, 0, 0);
        }
        return;
    }

    {
        int starts[256];
        int n = 0;
        const char *p = body;

        while (*p != '\0' && n < 256) {
            if (*p == '{') {
                starts[n++] = (int)(p - body);
            }
            p++;
        }
        if (n <= 0) {
            s_echoText = L"回声洞暂无内容";
        } else {
            int idx;
            char obj[1400];
            const char *st;
            const char *en;
            int L;
            wchar_t wt[512];
            wchar_t wa[256];
            wchar_t wd[128];
            wchar_t wm[64];
            wchar_t buf[1400];

            if (n <= 1) {
                idx = 0;
            } else {
                idx = (int)(GetTickCount() % (DWORD)n);
                if (idx == s_echoLast) {
                    idx = (idx + 1) % n;   /* 尽量不连着出同一条 */
                }
            }
            s_echoLast = idx;

            st = body + starts[idx];
            en = strchr(st, '}');
            L = (en != NULL) ? (int)(en - st + 1) : 0;
            if (L > (int)sizeof(obj) - 1) {
                L = (int)sizeof(obj) - 1;
            }
            if (L > 0) {
                memcpy(obj, st, (size_t)L);
            }
            obj[L] = '\0';

            echo_field_w(obj, "text", wt, 512);
            echo_field_w(obj, "author", wa, 256);
            echo_field_w(obj, "device", wd, 128);
            echo_field_w(obj, "time", wm, 64);

            if (wt[0] == L'\0') {
                s_echoText = L"回声洞暂无内容";
            } else {
                wsprintfW(buf, L"%s\r\n\r\n—— %s\r\n来自 %s\r\n%s",
                          wt,
                          (wa[0] != L'\0') ? wa : L"匿名",
                          (wd[0] != L'\0') ? wd : L"未知",
                          (wm[0] != L'\0') ? wm : L"未知");
                s_echoText = buf;
            }
        }
    }
    free(body);
    if (s_echoWnd != NULL) {
        PostMessage(s_echoWnd, WM_APP_ECHO_DONE, 0, 0);
    }
}


/* ===================== UiResultList ===================== */

UiResultList::UiResultList()
{
    m_owner = NULL;
    m_lastIdx = -1;
    m_lastTick = 0;
    m_downIdx = -1;
    m_downTick = 0;
    m_moved = false;
    m_bottomOk = false;
}

int UiResultList::OnLButtonDown(UINT fwKeys, int xPos, int yPos)
{
    m_moved = false;
    m_downIdx = CalcIndexOfPos(xPos, yPos);
    m_downTick = GetTickCount();
    return UiList::OnLButtonDown(fwKeys, xPos, yPos);
}

int UiResultList::OnMouseMove(UINT fwKeys, int xPos, int yPos)
{
    int r = UiList::OnMouseMove(fwKeys, xPos, yPos);

    m_moved = true;
    CheckBottom();
    return r;
}

int UiResultList::OnLButtonUp(UINT fwKeys, int xPos, int yPos)
{
    int r = UiList::OnLButtonUp(fwKeys, xPos, yPos);
    DWORD now = GetTickCount();

    if (m_owner != NULL && !m_moved && m_downIdx >= 0 &&
        m_downIdx < GetItemCount()) {
        /* 按住不放且没滚动 >= 600ms -> 长按详情（此时不算双击） */
        if ((now - m_downTick) >= 600) {
            m_lastIdx = -1;
            m_owner->OnListLongPress(m_downIdx);
        } else if (m_downIdx == m_lastIdx && (now - m_lastTick) <= 700) {
            m_lastIdx = -1;
            m_owner->OnListDoubleTap(m_downIdx);
        } else {
            m_lastIdx = m_downIdx;
            m_lastTick = now;
        }
    } else {
        m_lastIdx = -1;
    }
    if (m_moved) {
        CheckBottom();   /* 拖到底松手也要翻页 */
    }
    return r;
}

/* 惯性滚动（甩一下）走的是控件内部定时器，同样要检测底部 */
int UiResultList::OnTimer(UINT_PTR nIDEvent)
{
    int r = UiList::OnTimer(nIDEvent);

    CheckBottom();
    return r;
}

/* 最后一条已经进入可视区 -> 触发下一页（由 SearchService::More 去重） */
void UiResultList::CheckBottom()
{
    int first = 0;
    int count = 0;

    if (m_owner == NULL || GetItemCount() <= 0) {
        return;
    }
    if (!GetVisibleRange(&first, &count)) {
        return;
    }
    if (count <= 0) {
        return;
    }
    if (first + count >= GetItemCount()) {
        if (!m_bottomOk) {
            m_bottomOk = true;
            m_owner->OnListBottom();
        }
    } else {
        m_bottomOk = false;
    }
}

/* 自绘条目：左侧封面缩略图 + 标题 + UP主 */
/* 默认封面占位图（exe 内 RCDATA 2001），缩放到 144x81 并缓存 */
static HBITMAP cover_placeholder()
{
    static HBITMAP s_bmp = NULL;
    static int s_tried = 0;
    HDC screen;
    HDC mdc;
    HGDIOBJ old;
    RECT rc;
    struct {
        BITMAPINFOHEADER hdr;
    } bi;
    void *bits = NULL;

    if (s_tried) {
        return s_bmp;
    }
    s_tried = 1;
    screen = GetDC(NULL);
    memset(&bi, 0, sizeof(bi));
    bi.hdr.biSize = sizeof(BITMAPINFOHEADER);
    bi.hdr.biWidth = 144;
    bi.hdr.biHeight = -81;
    bi.hdr.biPlanes = 1;
    bi.hdr.biBitCount = 24;
    bi.hdr.biCompression = BI_RGB;
    s_bmp = CreateDIBSection(screen, (BITMAPINFO *)&bi, DIB_RGB_COLORS,
                             &bits, NULL, 0);
    mdc = CreateCompatibleDC(screen);
    if (s_bmp == NULL) {
        DeleteDC(mdc);
        ReleaseDC(NULL, screen);
        return NULL;
    }
    old = SelectObject(mdc, s_bmp);
    rc.left = 0;
    rc.top = 0;
    rc.right = 144;
    rc.bottom = 81;
    {
        HBRUSH b = CreateSolidBrush(RGB(214, 214, 214));
        FillRect(mdc, &rc, b);
        DeleteObject(b);
    }
    /* 占位图直接取 exe 里的 RCDATA（2001），不再依赖外部 png 文件 */
    ImagingHelper::DrawImage(mdc, &rc, GetModuleHandle(NULL), RT_RCDATA,
                             MAKEINTRESOURCE(2001), true);
    SelectObject(mdc, old);
    DeleteDC(mdc);
    ReleaseDC(NULL, screen);
    return s_bmp;
}

/* 单行测量：返回 s 的前几个字符能在 maxW 宽内放下（二分） */
static int title_split(HDC hdc, const wchar_t *s, int n, int maxW)
{
    int lo = 0;
    int hi = n;
    wchar_t buf[512];

    while (lo < hi) {
        int mid = (lo + hi + 1) / 2;
        int k = (mid < 511) ? mid : 511;
        RECT r;

        memcpy(buf, s, (size_t)k * sizeof(wchar_t));
        buf[k] = L'\0';
        r.left = 0;
        r.top = 0;
        r.right = 0;
        r.bottom = 0;
        DrawTextW(hdc, buf, -1, &r,
                  DT_LEFT | DT_SINGLELINE | DT_NOPREFIX | DT_CALCRECT);
        if ((r.right - r.left) <= maxW) {
            lo = mid;
        } else {
            hi = mid - 1;
        }
    }
    return lo;
}

void UiResultList::DrawItem(HDC hdc, int nIndex, RECT *prcItem,
                            RECT *prcWin, RECT *prcUpdate)
{
    const BcItem *it;
    RECT rcCover;
    RECT rcText;

    /* 背景/选中态由基类画（条目文字已置空，避免和自绘文字重叠） */
    UiList::DrawItem(hdc, nIndex, prcItem, prcWin, prcUpdate);

    if (nIndex < 0 || nIndex >= g_app.ItemCount()) {
        return;
    }
    it = &g_app.Items()[nIndex];

    /* 封面：左侧 16:9 缩略图（144x81），用缓存位图避免每次重绘都解码 */
    rcCover.left = prcItem->left + 8;
    rcCover.top = prcItem->top +
                  ((prcItem->bottom - prcItem->top) - 81) / 2;   /* 竖着居中 */
    rcCover.right = rcCover.left + 144;
    rcCover.bottom = rcCover.top + 81;

    /* 拟物化相框：右下投影 + 深色外框 + 亮边 + 白色卡纸（先画框再贴图） */
    {
        RECT fr;
        RECT sh;
        RECT t;
        HBRUSH b;

        fr.left = rcCover.left - 5;
        fr.top = rcCover.top - 5;
        fr.right = rcCover.right + 5;
        fr.bottom = rcCover.bottom + 5;

        sh = fr;
        OffsetRect(&sh, 2, 2);
        b = CreateSolidBrush(RGB(168, 168, 168));
        FillRect(hdc, &sh, b);
        DeleteObject(b);

        b = CreateSolidBrush(RGB(104, 104, 104));
        FillRect(hdc, &fr, b);
        DeleteObject(b);

        t = fr;
        InflateRect(&t, -1, -1);
        b = CreateSolidBrush(RGB(232, 232, 232));
        FillRect(hdc, &t, b);
        DeleteObject(b);

        t = fr;
        InflateRect(&t, -2, -2);
        b = CreateSolidBrush(RGB(255, 255, 255));
        FillRect(hdc, &t, b);
        DeleteObject(b);
    }

    /* 贴封面（画在卡纸上面） */
    {
        static HDC s_mdc = NULL;
        HBITMAP cb = CoverCache::GetBitmap(it->bvid);

        if (cb != NULL) {
            if (s_mdc == NULL) {
                s_mdc = CreateCompatibleDC(hdc);
            }
            if (s_mdc != NULL) {
                HGDIOBJ old = SelectObject(s_mdc, cb);

                BitBlt(hdc, rcCover.left, rcCover.top, 144, 81, s_mdc, 0, 0,
                       SRCCOPY);
                SelectObject(s_mdc, old);
            }
        } else {
            HBITMAP ph = cover_placeholder();

            /* 位图被 Trim 掉了就重新排队补一张（文件已在本地，只会解码
             * 不会重下；下载失败不会 Post 完成消息，不会重绘死循环） */
            if (m_owner != NULL && it->bvid[0] != '\0' &&
                it->pic[0] != '\0') {
                CoverCache::Request(m_owner->m_hWnd, nIndex, it->pic,
                                    it->bvid);
            }
            if (ph != NULL) {
                if (s_mdc == NULL) {
                    s_mdc = CreateCompatibleDC(hdc);
                }
                if (s_mdc != NULL) {
                    HGDIOBJ old = SelectObject(s_mdc, ph);

                    BitBlt(hdc, rcCover.left, rcCover.top, 144, 81, s_mdc,
                           0, 0, SRCCOPY);
                    SelectObject(s_mdc, old);
                }
            } else {
                HBRUSH b = CreateSolidBrush(RGB(214, 214, 214));
                FillRect(hdc, &rcCover, b);
                DeleteObject(b);
            }
        }
    }

    /* 标题：手动断成最多两行，每行用 DT_SINGLELINE 画。
     * CE 的 DrawText 不按矩形高裁剪，DT_WORDBREAK 会一路画到第三行，
     * 所以这里完全不依赖它折行。 */
    SetBkMode(hdc, TRANSPARENT);
    {
        TEXTMETRICW tm;
        int lh;
        int maxW;
        std::wstring t;

        memset(&tm, 0, sizeof(tm));
        GetTextMetricsW(hdc, &tm);
        lh = tm.tmHeight + tm.tmExternalLeading;
        if (lh <= 0) {
            lh = 22;
        }

        rcText.left = rcCover.right + 12;
        rcText.right = prcItem->right - 8;
        rcText.top = prcItem->top + 10;
        rcText.bottom = rcText.top + lh;
        maxW = rcText.right - rcText.left;

        if (maxW > 0 && prcItem->bottom - rcText.top >= lh) {
            RECT mr;
            int fullW;
            int n;
            int split;

            t = gbk2w(it->title);
            n = (int)t.size();
            mr.left = 0;
            mr.top = 0;
            mr.right = 0;
            mr.bottom = 0;
            DrawTextW(hdc, t.c_str(), -1, &mr,
                      DT_LEFT | DT_SINGLELINE | DT_NOPREFIX | DT_CALCRECT);
            fullW = mr.right - mr.left;

            ::SetTextColor(hdc, RGB(20, 20, 20));

            if (n <= 0) {
                /* 空标题 */
            } else if (fullW <= maxW) {
                DrawTextW(hdc, t.c_str(), -1, &rcText,
                          DT_LEFT | DT_TOP | DT_SINGLELINE |
                          DT_END_ELLIPSIS | DT_NOPREFIX);
            } else {
                split = title_split(hdc, t.c_str(), n, maxW);
                if (split < 1) {
                    split = 1;
                }
                DrawTextW(hdc, t.substr(0, (size_t)split).c_str(), -1,
                          &rcText,
                          DT_LEFT | DT_TOP | DT_SINGLELINE |
                          DT_END_ELLIPSIS | DT_NOPREFIX);
                if (split < n) {
                    rcText.top += lh;
                    rcText.bottom = rcText.top + lh;
                    DrawTextW(hdc, t.substr((size_t)split).c_str(), -1,
                              &rcText,
                              DT_LEFT | DT_TOP | DT_SINGLELINE |
                              DT_END_ELLIPSIS | DT_NOPREFIX);
                }
            }
        }
    }

    /* UP主 */
    rcText.top = prcItem->bottom - 36;
    rcText.bottom = prcItem->bottom - 8;
    if (rcText.right > rcText.left) {
        std::wstring a = gbk2w(it->author);

        ::SetTextColor(hdc, RGB(120, 120, 120));
        DrawTextW(hdc, a.c_str(), -1, &rcText,
                  DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS |
                  DT_NOPREFIX);
    }
}

/* ===================== MainSink ===================== */

MainSink::MainSink()
{
    m_w = NULL;
    m_hwnd = NULL;
    m_uiThread = 0;
    m_alive = 1;
    m_head = NULL;
    m_tail = NULL;
    m_lastProgKB = -1;
    InitializeCriticalSection(&m_lock);
}

MainSink::~MainSink()
{
    UiEvent *e;

    m_alive = 0;
    e = DequeueAll();
    while (e != NULL) {
        UiEvent *n = e->next;
        free(e);
        e = n;
    }
    DeleteCriticalSection(&m_lock);
}

void MainSink::AttachThread(HWND hwnd)
{
    m_hwnd = hwnd;
    m_uiThread = GetCurrentThreadId();
}

void MainSink::Stop()
{
    m_alive = 0;
}

bool MainSink::OnUiThread()
{
    return GetCurrentThreadId() == m_uiThread;
}

void MainSink::Enqueue(UiEvent *e)
{
    e->next = NULL;
    EnterCriticalSection(&m_lock);
    if (m_tail != NULL) {
        m_tail->next = e;
    } else {
        m_head = e;
    }
    m_tail = e;
    LeaveCriticalSection(&m_lock);
    if (m_hwnd != NULL) {
        PostMessage(m_hwnd, WM_APP_UI_EVENT, 0, 0);
    }
}

MainSink::UiEvent *MainSink::DequeueAll()
{
    UiEvent *h;

    EnterCriticalSection(&m_lock);
    h = m_head;
    m_head = NULL;
    m_tail = NULL;
    LeaveCriticalSection(&m_lock);
    return h;
}

void MainSink::Post(int type, const char *text, const BcItem *items,
                    int count, int busy, long done, long total, int append)
{
    UiEvent *e;

    if (!m_alive) {
        return;
    }
    e = (UiEvent *)malloc(sizeof(UiEvent));
    if (e == NULL) {
        return;
    }
    memset(e, 0, sizeof(UiEvent));
    e->type = type;
    e->append = append;
    if (text != NULL) {
        str_copy(e->text, (int)sizeof(e->text), text);
    }
    if (items != NULL && count > 0) {
        if (count > BC_MAX_ITEMS) {
            count = BC_MAX_ITEMS;
        }
        e->items = (BcItem *)malloc(sizeof(BcItem) * (size_t)count);
        if (e->items != NULL) {
            memcpy(e->items, items, sizeof(BcItem) * (size_t)count);
            e->count = count;
        }
    }
    e->busy = busy;
    e->done = done;
    e->total = total;
    Enqueue(e);
}

void MainSink::OnLog(const char *line)
{
    if (m_w == NULL) {
        return;
    }
    if (OnUiThread()) {
        m_w->AppendLog(line);
        return;
    }
    Post(UI_EVT_LOG, line, NULL, 0, 0, 0, 0, 0);
}

void MainSink::OnStatus(const char *text)
{
    if (m_w == NULL) {
        return;
    }
    if (OnUiThread()) {
        m_w->SetStatus(text);
        return;
    }
    Post(UI_EVT_STATUS, text, NULL, 0, 0, 0, 0, 0);
}

void MainSink::OnLoading(const char *text)
{
    if (m_w == NULL) {
        return;
    }
    if (text == NULL || text[0] == '\0') {
        g_app.GetLogger().Log("MainSink: OnLoading 空 -> 隐藏加载层 (线程%s)",
                              OnUiThread() ? "UI" : "后台");
    }
    if (OnUiThread()) {
        m_w->SetLoading(text);
        return;
    }
    Post(UI_EVT_LOADING, text, NULL, 0, 0, 0, 0, 0);
}

void MainSink::OnItems(const BcItem *items, int count, const char *what,
                       int append)
{
    (void)what;
    if (m_w == NULL) {
        return;
    }
    if (OnUiThread()) {
        m_w->FillList(items, count, append);
        return;
    }
    Post(UI_EVT_ITEMS, NULL, items, count, 0, 0, 0, append);
}

void MainSink::OnBusyChanged(int busy)
{
    if (m_w == NULL) {
        return;
    }
    if (busy) {
        m_lastProgKB = -1;
    }
    if (OnUiThread()) {
        m_w->SetBusyUI(busy);
        return;
    }
    Post(UI_EVT_BUSY, NULL, NULL, 0, busy, 0, 0, 0);
}

void MainSink::OnDownloadProgress(long done, long total)
{
    char buf[96];
    int kb;

    if (m_w == NULL) {
        return;
    }
    kb = (int)(done / 1024L);
    if (kb == (int)m_lastProgKB) {
        return;
    }
    m_lastProgKB = kb;
    if (total > 0) {
        sprintf(buf, "下载中 %ld KB / %ld KB", done / 1024L, total / 1024L);
    } else {
        sprintf(buf, "下载中 %ld KB", done / 1024L);
    }
    if (OnUiThread()) {
        m_w->SetStatus(buf);
        return;
    }
    Post(UI_EVT_STATUS, buf, NULL, 0, 0, 0, 0, 0);
}

void MainSink::OnPlayMedia(const char *path)
{
    if (m_w == NULL) {
        return;
    }
    if (OnUiThread()) {
        m_w->PlayMedia(path);
        return;
    }
    Post(UI_EVT_PLAY, path, NULL, 0, 0, 0, 0, 0);
}

void MainSink::PumpMessages()
{
    if (m_w == NULL) {
        return;
    }
    /* 后台线程不能泵 UI 消息；UI 线程本就在跑消息循环，无需泵 */
    if (!OnUiThread()) {
        return;
    }
    m_w->PumpNow();
}

void MainSink::Drain()
{
    UiEvent *e = DequeueAll();

    while (e != NULL) {
        UiEvent *n = e->next;
        if (m_w != NULL) {
            switch (e->type) {
            case UI_EVT_LOG:    m_w->AppendLog(e->text); break;
            case UI_EVT_STATUS: m_w->SetStatus(e->text); break;
            case UI_EVT_LOADING: m_w->SetLoading(e->text); break;
            case UI_EVT_ITEMS:  m_w->FillList(e->items, e->count,
                                              e->append); break;
            case UI_EVT_BUSY:   /* 事件可能排队排了很久，按「当前」忙态刷，
                                 * 否则会把后开的新任务的忙态覆盖掉 */
                                m_w->SetBusyUI(g_app.IsBusy()); break;
            case UI_EVT_PLAY:   m_w->PlayMedia(e->text); break;
            default: break;
            }
        }
        if (e->items != NULL) {
            free(e->items);
        }
        free(e);
        e = n;
    }
}

/* ===================== MainWindow ===================== */

MainWindow::MainWindow()
{
    m_sink.m_w = this;
    m_hEdit = NULL;
}

/* ---------- 布局 ---------- */

void MainWindow::Layout()
{
    int w = GetWidth();
    int h = GetHeight();
    int y;
    int rh = MZM_HEIGHT_SINGLELINE_EDIT;
    int gap = 6;
    int bwSearch = 120;
    int editW;
    int hBar = MZM_HEIGHT_TEXT_TOOLBAR;
    int yBar, yList, hList;

    m_caption.SetPos(0, 0, w, MZM_HEIGHT_CAPTION);

    /* 搜索行：EDIT + 搜索按钮 */
    y = MZM_HEIGHT_CAPTION + 6;
    editW = w - 20 - gap - bwSearch;
    MoveWindow(m_hEdit, 10, y, editW, rh - 12, TRUE);
    m_btnSearch.SetPos(10 + editW + gap, y, bwSearch, rh - 12);
    y += rh + 8;
    yList = y;

    yBar = h - hBar;
    hList = yBar - 6 - yList;
    if (hList < 80) {
        hList = 80;
    }

    m_list.SetPos(0, yList, w, hList);

    m_toolbar.SetTextBarType((w >= 640) ?
                             TEXT_TOOLBAR_TYPE_720 : TEXT_TOOLBAR_TYPE_480);
    m_toolbar.SetPos(0, yBar, w, hBar);
}

/* ---------- 初始化 ---------- */

BOOL MainWindow::OnInitDialog()
{
    HFONT hFont;

    if (!CMzWndEx::OnInitDialog()) {
        return FALSE;
    }

    /* 记录 UI 线程与窗口句柄：后台任务的回调靠它 PostMessage 回主线程 */
    m_sink.AttachThread(m_hWnd);
    g_app.GetLogger().Log("UI: OnInitDialog 开始");

    SetBgColor(RGB(236, 236, 236));

    m_caption.SetPos(0, 0, GetWidth(), MZM_HEIGHT_CAPTION);
    m_caption.SetText(L"哔哩经典 for Mymobile  v" L"0.2.0");
    AddUiWin(&m_caption);

    /* 关键词输入（原生单行 EDIT：GetWindowTextW 读取 100% 可靠；
     * M8 coredll 无 ANSI 版 API，一律走 W 版） */
    m_hEdit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
        WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL,
        10, 48, 200, 48, m_hWnd, (HMENU)(INT_PTR)MZ_IDC_EDIT_KEYWORD,
        NULL, NULL);

    hFont = (HFONT)GetStockObject(SYSTEM_FONT);
    if (hFont != NULL) {
        ::SendMessageW(m_hEdit, WM_SETFONT, (WPARAM)hFont, TRUE);
    }
    ::SendMessageW(m_hEdit, EM_SETLIMITTEXT, 64, 0);

    m_btnSearch.SetButtonType(MZC_BUTTON_GREEN);
    m_btnSearch.SetID(MZ_IDC_BTN_SEARCH);
    m_btnSearch.SetText(L"搜索");
    AddUiWin(&m_btnSearch);

    m_list.SetOwner(this);
    m_list.SetID(109);
    m_list.SetItemHeight(120);      /* 带封面缩略图的行高（要放得下两行标题） */
    AddUiWin(&m_list);

    /* MZ 原生底部文字工具栏：菜单 / 我的 / 设置（返回在「菜单」里） */
    m_toolbar.SetPos(0, GetHeight() - MZM_HEIGHT_TEXT_TOOLBAR,
                     GetWidth(), MZM_HEIGHT_TEXT_TOOLBAR);
    m_toolbar.SetButton(0, true, true, L"菜单");
    m_toolbar.SetButton(1, true, true, L"我的");
    m_toolbar.SetButton(2, true, true, L"设置");
    m_toolbar.SetID(MZ_IDC_TOOLBAR);
    AddUiWin(&m_toolbar);

    Layout();
    g_app.GetLogger().Log("UI: Layout 完成");
    g_app.GetLogger().Log("UI: OnInitDialog 完成");
    return TRUE;
}

/* ---------- 对外：配置变化后刷新显示 ---------- */

void MainWindow::RefreshUI()
{
    /* 转码开关现在在设置菜单里，界面无需刷新 */
}

/* ---------- 事件路由 ---------- */

void MainWindow::OnMzCommand(WPARAM wParam, LPARAM lParam)
{
    UINT_PTR id = LOWORD(wParam);

    switch (id) {
    case MZ_IDC_BTN_SEARCH:
        DoSearch(0);
        break;
    case MZ_IDC_TOOLBAR: {
        int idx = (int)lParam;   /* 0=菜单 1=我的 2=设置 */
        ShowMenu(idx);
        break;
    }
    default:
        break;
    }
}

LRESULT MainWindow::MzDefWndProc(UINT message, WPARAM wParam, LPARAM lParam)
{
    static int s_paint = 0;
    if (message == WM_PAINT && s_paint < 4) {
        g_app.GetLogger().Log("UI: WM_PAINT #%d", s_paint);
        s_paint++;
    }
    if (message == WM_APP_UI_EVENT) {
        m_sink.Drain();
        return 0;
    }
    if (message == WM_APP_COVER_DONE) {
        int idx = (int)wParam;

        /* 只重画到货的这一条。整列表 Invalidate() 在 M8 上等于把 20 条
         * 全画一遍，封面陆续到货时会把 UI 线程占满，界面卡死。 */
        if (idx >= 0 && idx < m_list.GetItemCount()) {
            m_list.InvalidateItem(idx);
        }
        return 0;
    }
    if (message == WM_APP_UPDATE_DONE) {
        g_app.SetBusy(0);
        ShowUpdateResult();
        return 0;
    }
    if (message == WM_APP_ECHO_DONE) {
        g_app.SetBusy(0);
        ShowEchoResult();
        return 0;
    }
    if (message == WM_APP_STARTUP) {
        g_app.GetLogger().Log("UI: WM_APP_STARTUP -> 拉推荐");
        DoSearch(1);    /* 启动自动拉推荐 */
        g_app.GetLogger().Log("UI: WM_APP_STARTUP 返回");
        return 0;
    }
    if (message == WM_COMMAND) {
        UINT id = LOWORD(wParam);
        UINT code = HIWORD(wParam);

        /* 系统底部菜单栏 */
        switch (id) {
        case MZ_MENU_RECOMMEND: DoSearch(1); return 0;
        case MZ_MENU_ECHO:      EchoHole(); return 0;
        case MZ_MENU_BACK:      DoBack(); return 0;
        case MZ_MENU_REPLAY:    if (!g_app.IsBusy()) PlayService::PlayLast(); return 0;
        case MZ_MENU_LOGIN:
        case MZ_MENU_FAVS:
        case MZ_MENU_PROFILE: {
            MyWindow w;
            w.DoModal();
            return 0;
        }
        case MZ_MENU_CONNS_1: PlayService::SetConns(1); return 0;
        case MZ_MENU_CONNS_2: PlayService::SetConns(2); return 0;
        case MZ_MENU_CONNS_4: PlayService::SetConns(4); return 0;
        case MZ_MENU_TRANS:
            PlayService::SetTranscode(g_app.Cfg().transcode ? 0 : 1);
            return 0;
        case MZ_MENU_OFFLINE:
            PlayService::SetOffline(g_app.Cfg().offline ? 0 : 1);
            return 0;
        case MZ_MENU_DANMAKU:
            PlayService::SetDanmaku(g_app.Cfg().danmaku ? 0 : 1);
            return 0;
        case MZ_MENU_REPORTHIST:
            PlayService::SetReportHistory(g_app.Cfg().report_history ? 0 : 1);
            return 0;
        case MZ_MENU_EXIT: ::PostMessage(m_hWnd, WM_CLOSE, 0, 0); return 0;
        default: break;
        }

        if (id == MZ_IDC_EDIT_KEYWORD) {
            if (code == EN_SETFOCUS) {
                MzOpenSip();
            } else if (code == EN_KILLFOCUS) {
                MzCloseSip();
            }
            return 0;
        }
    } else if (message == WM_DESTROY) {
        m_sink.Stop();      /* 先停收后台事件，再等线程退出 */
        g_app.Shutdown();
    }
    return CMzWndEx::MzDefWndProc(message, wParam, lParam);
}

/* ---------- 动作 ---------- */

void MainWindow::DoSearch(int popular)
{
    wchar_t wkw[64];
    char kw[128];

    g_app.GetLogger().Log("UI: DoSearch 进入 popular=%d", popular);
    if (!g_app.IsReady()) {
        g_app.GetLogger().Log("UI: DoSearch 初始化未完成，忽略");
        return;
    }
    if (g_app.IsBusy()) {
        g_app.GetLogger().Log("UI: DoSearch busy 直接返回");
        return;
    }
    if (popular) {
        g_app.GetLogger().Log("UI: DoSearch 调 Popular()");
        SearchService::Popular();
        g_app.GetLogger().Log("UI: DoSearch Popular() 返回");
        return;
    }
    wkw[0] = L'\0';
    ::GetWindowTextW(m_hEdit, wkw, 64);
    kw[0] = '\0';
    WideCharToMultiByte(CP_ACP, 0, wkw, -1, kw, (int)sizeof(kw), NULL, NULL);
    SearchService::Search(kw);
}

void MainWindow::DoPlay(int forceTranscode)
{
    int idx = SelectedIndex();

    if (g_app.IsBusy()) {
        return;
    }
    if (idx < 0) {
        SetStatus("请先在列表里点选一条");
        return;
    }
    LoadingWnd::Show();
    LoadingWnd::SetStatus(L"获取播放地址…");
    PlayService::PlaySelected(idx, forceTranscode);
}

/* 忙态时用 150ms 定时器周期性排空后台事件队列。
 * 这样即使 WM_APP_UI_EVENT 因框架路由没到，也能把结果/解除忙态送达 UI。 */
void MainWindow::OnTimer(UINT_PTR nIDEvent)
{
    if (nIDEvent == 1) {
        m_sink.Drain();
        return;
    }
    if (nIDEvent == 11) {
        /* 首屏内容迟迟没到：到点也必须把启动图收掉 */
        ::KillTimer(m_hWnd, 11);
        SplashHide();
        return;
    }
    if (nIDEvent == 9) {
        /* 启动自动推荐（延后一拍，避开启动早期崩溃） */
        ::KillTimer(m_hWnd, 9);
        g_app.GetLogger().Log("UI: 定时器触发 -> 自动拉推荐");
        DoSearch(1);
        return;
    }
    CMzWndEx::OnTimer(nIDEvent);
}

void MainWindow::OnListDoubleTap(int index)
{
    if (g_app.IsBusy()) {
        return;
    }
    if (SearchService::Kind() == QK_FAV_FOLDER) {
        SearchService::OpenFolder(index);
        return;
    }
    LoadingWnd::Show();
    LoadingWnd::SetStatus(L"获取播放地址…");
    PlayService::PlaySelected(index, 0);
}

/* 长按一个条目 -> 弹视频详情（原底栏「当前视频详情」挪到这里） */
void MainWindow::OnListLongPress(int index)
{
    if (g_app.IsBusy()) {
        return;
    }
    ShowDetail(index);
}

/* 底栏「返回」：收藏夹内容 -> 收藏夹列表；搜索/历史/收藏夹列表 -> 推荐 */
void MainWindow::DoBack()
{
    int kind = SearchService::Kind();

    if (g_app.IsBusy()) {
        return;
    }
    if (kind == QK_FAV_VIDEOS) {
        SearchService::FavFolders();
    } else if (kind == QK_SEARCH || kind == QK_HISTORY ||
               kind == QK_FAV_FOLDER) {
        SearchService::Popular();
    }
}

/* 滑到底部 -> 自动翻下一页（SearchService 内部按当前查询类型分派） */
void MainWindow::OnListBottom()
{
    SearchService::More();
}

void MainWindow::PlayMedia(const char *pathGbk)
{
    VideoWindow vw;

    if (pathGbk == NULL || pathGbk[0] == '\0') {
        return;
    }
    g_app.GetLogger().Log("MainWindow: PlayMedia 收到 %s", pathGbk);
    /* 加载期间用户点了返回：不要开播 */
    if (g_app.IsCancelled()) {
        g_app.GetLogger().Log("MainWindow: 已取消，不开播");
        LoadingWnd::Hide(true);
        return;
    }
    /* 加载层文字由 PlayService 设好（正在加载视频… / 装填弹幕中…），
     * 这里只保证窗口显示，别再重复设置，否则会多出一遍 */
    LoadingWnd::Show();
    if (vw.PlayModal(m_hWnd, pathGbk)) {
        return;
    }
    /* 内置 PlayerCore 不可用：回退系统播放器 */
    LoadingWnd::Hide();
    AppendLog("内置播放器不可用，改用系统播放器打开");
    PlayService::PlayFile(pathGbk);
}

int MainWindow::SelectedIndex()
{
    return m_list.GetSelectedIndex();
}

void MainWindow::ToggleTranscode()
{
    if (g_app.IsBusy()) {
        return;
    }
    PlayService::SetTranscode(g_app.Cfg().transcode ? 0 : 1);
    RefreshUI();
}

void MainWindow::ShowDetail(int idx)
{
    const BcItem *it;
    char buf[800];
    std::wstring w;

    if (idx < 0 || idx >= g_app.ItemCount()) {
        return;
    }
    it = &g_app.Items()[idx];
    sprintf(buf, "标题：\r\n%s\r\n\r\nUP主：%s\r\nBV号：%s",
            it->title, it->author, it->bvid);
    w = gbk2w(buf);
    MzMessageBoxEx(m_hWnd, w.c_str(), L"视频详情", MB_OK, false);
}

/* =====================================================================
 * 关于：自带折行的自绘窗口（MessageBox 碰到长行会跑出屏幕）
 * 文字想改就改下面 kAboutText，行太长也没关系，会自动折。
 * ===================================================================== */
static const wchar_t kAboutText[] =
    L"哔哩经典 for Mymobile\n"
    L"一个为 Mymobile 打造的哔哩哔哩第三方客户端\n"
    L"\n"
    L"项目地址：\n"
    L"https://github.com/AktuelleKamera/BiliClassic\n"
    L"\n"
    L"参考项目：\n"
    L"https://gitee.com/RobinNotBad/BiliClient\n"
    L"https://github.com/wolfSSL/wolfssl\n"
    L"https://github.com/bilibili/DanmakuFlameMaster\n"
    L"https://github.com/bilibili-plugins/bilibili-api-collect\n"
    L"\n"
    L"感谢 @K老于 提供的魅族M8";

/* 自绘正文：按控件宽度自动折行（MessageBox 遇到长行会跑出屏幕） */
class UiTextBody : public UiWin
{
public:
    UiTextBody() { m_text = NULL; }
    const wchar_t *m_text;

    virtual void OnPaint(HDC hdcDst, RECT *prcWin, RECT *prcUpdate)
    {
        RECT rc = *prcWin;
        HBRUSH b = CreateSolidBrush(RGB(255, 255, 255));
        HFONT f = FontHelper::GetFont(MZFS_TINY);
        HGDIOBJ old = NULL;

        FillRect(hdcDst, &rc, b);
        DeleteObject(b);

        rc.left += 12;
        rc.right -= 12;
        rc.top += 10;
        rc.bottom -= 6;

        if (f != NULL) {
            old = SelectObject(hdcDst, f);
        }
        SetBkMode(hdcDst, TRANSPARENT);
        ::SetTextColor(hdcDst, RGB(40, 40, 40));
        DrawTextW(hdcDst, (m_text != NULL) ? m_text : L"", -1, &rc,
                  DT_LEFT | DT_TOP | DT_WORDBREAK | DT_NOPREFIX);
        if (old != NULL) {
            SelectObject(hdcDst, old);
        }
        (void)prcUpdate;
    }
};

/* 通用「一大段文字」窗口：关于 / 检查更新都用它 */
class TextWindow : public CMzWndEx
{
    MZ_DECLARE_DYNAMIC(TextWindow);
public:
    TextWindow() { m_title = NULL; m_body = NULL; }

    void SetContent(const wchar_t *title, const wchar_t *body)
    {
        m_title = title;
        m_body = body;
    }

    virtual int DoModal()
    {
        int sw = GetSystemMetrics(SM_CXSCREEN);
        int sh = GetSystemMetrics(SM_CYSCREEN);

        if (sw <= 0) {
            sw = 480;
        }
        if (sh <= 0) {
            sh = 720;
        }
        if (!Create(0, 0, sw, sh, GetForegroundWindow(), 0, WS_POPUP)) {
            Create(0, 0, sw, sh, NULL, 0, WS_POPUP);
        }
        return CMzWndEx::DoModal();
    }

protected:
    virtual BOOL OnInitDialog()
    {
        if (!CMzWndEx::OnInitDialog()) {
            return FALSE;
        }
        SetBgColor(RGB(236, 236, 236));

        m_cap.SetPos(0, 0, GetWidth(), MZM_HEIGHT_CAPTION);
        m_cap.SetText((m_title != NULL) ? m_title : L"");
        AddUiWin(&m_cap);

        m_text.m_text = m_body;
        m_text.SetPos(10, MZM_HEIGHT_CAPTION + 10, GetWidth() - 20,
                      GetHeight() - MZM_HEIGHT_CAPTION - 90);
        AddUiWin(&m_text);

        m_ok.SetButtonType(MZC_BUTTON_PELLUCID);
        m_ok.SetID(1);
        m_ok.SetText(L"关闭");
        m_ok.SetPos(20, GetHeight() - 70, GetWidth() - 40, 54);
        AddUiWin(&m_ok);
        return TRUE;
    }

    virtual void OnMzCommand(WPARAM wParam, LPARAM lParam)
    {
        if (LOWORD(wParam) == 1) {
            EndModal(1);
        }
        (void)lParam;
    }

private:
    const wchar_t *m_title;
    const wchar_t *m_body;
    UiCaption   m_cap;
    UiTextBody  m_text;
    UiButton    m_ok;
};

MZ_IMPLEMENT_DYNAMIC(TextWindow)

void MainWindow::CheckUpdate()
{
    if (g_app.IsBusy()) {
        return;
    }
    s_updateWnd = m_hWnd;
    str_copy(s_updateMsg, (int)sizeof(s_updateMsg), "正在检查更新…");
    g_app.SetBusy(1);
    g_app.RunAsync(job_update, NULL);
}

void MainWindow::ShowAbout()
{
    TextWindow w;

    w.SetContent(L"关于", kAboutText);
    w.DoModal();
}

void MainWindow::ShowUpdateResult()
{
    TextWindow w;

    w.SetContent(L"检查更新", s_updateText.c_str());
    w.DoModal();
}

/* 菜单 -> 回声洞：后台拉一条随机留言，回来再弹窗 */
void MainWindow::EchoHole()
{
    if (g_app.IsBusy()) {
        return;
    }
    s_echoWnd = m_hWnd;
    g_app.GetLogger().Log("回声洞：拉取中");
    g_app.SetBusy(1);
    g_app.RunAsync(job_echo, NULL);
}

void MainWindow::ShowEchoResult()
{
    TextWindow w;

    w.SetContent(L"回声洞", s_echoText.c_str());
    w.DoModal();
}

void MainWindow::ClearCache()
{
    CoverCache::ClearAll();
    MzMessageBoxEx(m_hWnd, L"封面缓存已清除。", L"清除缓存", MB_OK, false);
}

/* 底栏弹出菜单：0=菜单 1=我的 2=设置 3=播放设置（设置的二级菜单）
 * 注意每项高 95px（Mzfc），一项一屏放不下多少，所以设置要分层。 */
void MainWindow::ShowMenu(int which)
{
    CPopupMenu ppm;
    PopupMenuItemProp pmip;
    std::wstring s;
    RECT rc;
    int nID;

    if (g_app.IsBusy()) {
        return;
    }
    pmip.itemCr = MZC_BUTTON_PELLUCID;

    if (which == 0) {
        s = L"返回";         pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_BACK;      ppm.AddItem(pmip);
        s = L"回声洞";       pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_ECHO;      ppm.AddItem(pmip);
        s = L"推荐";         pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_RECOMMEND; ppm.AddItem(pmip);
        s = L"重播上次";     pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_REPLAY;    ppm.AddItem(pmip);
    } else if (which == 1) {
        s = L"我的账号";     pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_LOGIN;   ppm.AddItem(pmip);
        s = L"我的收藏";     pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_FAVS;    ppm.AddItem(pmip);
        s = L"观看历史";     pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_HISTORY; ppm.AddItem(pmip);
        s = L"我的资料";     pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_PROFILE; ppm.AddItem(pmip);
    } else if (which == 2) {
        /* 设置（5 项，放得下）：播放相关全收进「播放设置」，连接数一项循环 */
        s = L"播放设置";     pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_PLAYCFG; ppm.AddItem(pmip);
        s = g_app.Cfg().danmaku ? L"弹幕：开" : L"弹幕：关";
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_DANMAKU; ppm.AddItem(pmip);
        s = L"清除缓存";
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_CLEARCACHE; ppm.AddItem(pmip);
        s = L"检查更新";
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_UPDATE; ppm.AddItem(pmip);
        s = L"关于";
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_ABOUT; ppm.AddItem(pmip);
    } else {
        /* 播放设置（二级）：连接数一项循环 1 -> 2 -> 4 -> 1 */
        int c = g_app.Cfg().conns;

        s = L"连接数：";
        s += (c == 1) ? L"1" : ((c == 2) ? L"2" : L"4");
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_CONNS_CYCLE; ppm.AddItem(pmip);
        s = g_app.Cfg().transcode ? L"转码：开" : L"转码：关";
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_TRANS; ppm.AddItem(pmip);
        s = g_app.Cfg().offline ? L"离线播放：开" : L"离线播放：关";
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_OFFLINE; ppm.AddItem(pmip);
        s = g_app.Cfg().report_history ? L"上报历史：开" : L"上报历史：关";
        pmip.str = s.c_str(); pmip.itemRetID = MZ_MENU_REPORTHIST; ppm.AddItem(pmip);
    }

    rc = MzGetWorkArea();
    rc.top = rc.bottom - ppm.GetHeight();
    ppm.Create(rc.left, rc.top, RECT_WIDTH(rc), RECT_HEIGHT(rc), m_hWnd, 0, WS_POPUP);
    nID = ppm.DoModal();

    switch (nID) {
    case MZ_MENU_BACK:      DoBack(); break;
    case MZ_MENU_RECOMMEND: DoSearch(1); break;
    case MZ_MENU_ECHO:      EchoHole(); break;
    case MZ_MENU_REPLAY:    if (!g_app.IsBusy()) PlayService::PlayLast(); break;
    case MZ_MENU_LOGIN:
    case MZ_MENU_PROFILE: {
        MyWindow w;
        w.DoModal();
        break;
    }
    case MZ_MENU_FAVS:      SearchService::FavFolders(); break;
    case MZ_MENU_HISTORY:   SearchService::History(); break;
    case MZ_MENU_PLAYCFG:   ShowMenu(3); break;
    case MZ_MENU_CONNS_CYCLE: {
        int c = g_app.Cfg().conns;
        int n = (c == 1) ? 2 : ((c == 2) ? 4 : 1);
        PlayService::SetConns(n);
        break;
    }
    case MZ_MENU_CONNS_1:   PlayService::SetConns(1); break;
    case MZ_MENU_CONNS_2:   PlayService::SetConns(2); break;
    case MZ_MENU_CONNS_4:   PlayService::SetConns(4); break;
    case MZ_MENU_TRANS:     PlayService::SetTranscode(g_app.Cfg().transcode ? 0 : 1); break;
    case MZ_MENU_OFFLINE:   PlayService::SetOffline(g_app.Cfg().offline ? 0 : 1); break;
    case MZ_MENU_DANMAKU:   PlayService::SetDanmaku(g_app.Cfg().danmaku ? 0 : 1); break;
    case MZ_MENU_REPORTHIST: PlayService::SetReportHistory(g_app.Cfg().report_history ? 0 : 1); break;
    case MZ_MENU_ABOUT:     ShowAbout(); break;
    case MZ_MENU_UPDATE:    CheckUpdate(); break;
    case MZ_MENU_CLEARCACHE: ClearCache(); break;
    default: break;
    }
}
void MainWindow::AppendLog(const char *line)
{
    /* 日志框已移除：完整日志由 Logger 写入数据目录 biliclassic_m8_log.txt */
    (void)line;
}

void MainWindow::SetStatus(const char *text)
{
    /* 底部提示条已删除：状态文字不再显示（完整日志见数据目录日志文件） */
    (void)text;
}

/* 加载层：text 非空 -> 显示并设置当前阶段；NULL/空 -> 隐藏 */
void MainWindow::SetLoading(const char *text)
{
    if (text == NULL || text[0] == '\0') {
        LoadingWnd::Hide();
        return;
    }
    LoadingWnd::Show();
    LoadingWnd::SetStatus(gbk2w(text).c_str());
}

void MainWindow::FillList(const BcItem *items, int count, int append)
{
    int i;
    int base = 0;

    if (append) {
        base = m_list.GetItemCount();   /* 追加：接在已有的之后 */
    } else {
        /* 首屏内容到了：把启动图收掉 */
        ::KillTimer(m_hWnd, 11);
        SplashHide();
        m_list.RemoveAll();
        m_list.ResetBottom();           /* 新结果要重新检测触底 */
    }
    for (i = 0; i < count; i++) {
        ListItem li;

        li.Text = L"";      /* 由 UiResultList::DrawItem 自绘 */
        li.Data = (void *)(INT_PTR)(base + i);
        m_list.AddItem(li);
    }
    if (!append) {
        m_list.SetTopPos(0);
    }
    m_list.Invalidate();
    m_list.Update();

    /* 后台拉封面缩略图 */
    if (!append) {
        CoverCache::Reset();
    }
    for (i = 0; i < count; i++) {
        CoverCache::Request(m_hWnd, base + i, items[i].pic, items[i].bvid);
    }
    /* 位图缓存不设上限会把 WinCE 的 GDI/DIB 内存吃光，整机卡死 */
    CoverCache::Trim(48);
    if (append) {
        m_list.CheckBottom();   /* 还卡在底部就继续拉下一页 */
    }
}

void MainWindow::SetBusyUI(int busy)
{
    bool on = (busy == 0);

    m_btnSearch.SetEnable(on);
    EnableWindow(m_hEdit, on ? TRUE : FALSE);

    /* 忙态开定时器排空事件，闲时关掉省电 */
    if (busy) {
        SetTimer(m_hWnd, 1, 150, NULL);
    } else {
        KillTimer(m_hWnd, 1);
    }

    m_btnSearch.Invalidate();
    m_btnSearch.Update();
}

void MainWindow::PumpNow()
{
    MSG msg;

    while (PeekMessage(&msg, NULL, 0, 0, PM_REMOVE)) {
        if (msg.message == WM_QUIT) {
            PostQuitMessage((int)msg.wParam);
            break;
        }
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
}
