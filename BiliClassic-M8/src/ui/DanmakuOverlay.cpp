/* =====================================================================
 * DanmakuOverlay.cpp - 纯 GDI 弹幕层实现
 * ===================================================================== */
#include "DanmakuOverlay.h"
#include <stdlib.h>
#include <string.h>

#define DM_LATE_MS        1500
#define DM_MAX_CONCURRENT 12
#define DM_SCROLL_MS      7000
#define DM_TOPBOTTOM_MS   4500
#define DM_LINE_HEIGHT    30

static void dm_to_wide(const char *utf8, wchar_t *out, int cap)
{
    out[0] = L'\0';
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, out, cap);
}

DanmakuOverlay::DanmakuOverlay()
{
    m_items = NULL;
    m_count = 0;
    m_cursor = 0;
    m_active = true;
    m_liveCount = 0;
    m_lines = 0;
    m_lineHeight = DM_LINE_HEIGHT;
    m_lastNow = -1;
    {
        int i;
        for (i = 0; i < 16; i++) {
            m_lineFree[i] = -0x7FFFFFFF;
        }
    }
    m_font = NULL;
    m_fontOwned = false;
    m_fontDm = NULL;
    m_fontDmOwned = false;
    m_screenDC = NULL;
    m_memDC = NULL;
    memset(m_linePrev, 0, sizeof(m_linePrev));
    memset(m_rowCount, 0, sizeof(m_rowCount));
    memset(m_rowMoving, 0, sizeof(m_rowMoving));
    m_brush = NULL;
    m_rowBuf = NULL;
    m_rowBufCap = 0;
}

DanmakuOverlay::~DanmakuOverlay()
{
    Clear();
    if (m_fontOwned && m_font != NULL) {
        DeleteObject(m_font);
    }
    m_font = NULL;
    if (m_fontDmOwned && m_fontDm != NULL) {
        DeleteObject(m_fontDm);
    }
    m_fontDm = NULL;
    if (m_brush != NULL) {
        DeleteObject(m_brush);
        m_brush = NULL;
    }
    if (m_rowBuf != NULL) {
        free(m_rowBuf);
        m_rowBuf = NULL;
        m_rowBufCap = 0;
    }
    if (m_memDC != NULL) {
        DeleteDC(m_memDC);
        m_memDC = NULL;
    }
    if (m_screenDC != NULL) {
        ReleaseDC(NULL, m_screenDC);
        m_screenDC = NULL;
    }
}

/* 正文用系统宋体（汉字全覆盖），danmaku.ttf 作为补充字体（假名/符号/私用区）。 */
void DanmakuOverlay::EnsureFont()
{
    LOGFONTW lf;
    wchar_t path[MAX_PATH];
    wchar_t *p;

    if (m_font != NULL) {
        return;
    }

    /* 正文：取系统默认字体的字面名，用规范 TrueType 设置建大号字体
     * （OUT_TT_PRECIS 避免被替换成光栅/合成斜体；不加粗避免合成粗体）。 */
    memset(&lf, 0, sizeof(lf));
    {
        HDC dc = GetDC(NULL);
        wchar_t face[LF_FACESIZE];

        face[0] = L'\0';
        if (dc != NULL) {
            HGDIOBJ old = SelectObject(dc, GetStockObject(SYSTEM_FONT));
            GetTextFaceW(dc, LF_FACESIZE, face);
            SelectObject(dc, old);
            ReleaseDC(NULL, dc);
        }
        if (face[0] == L'\0') {
            wcscpy(face, L"宋体");
        }

        lf.lfHeight = -22;
        lf.lfWeight = FW_NORMAL;
        lf.lfItalic = 0;
        lf.lfUnderline = 0;
        lf.lfStrikeOut = 0;
        lf.lfCharSet = DEFAULT_CHARSET;
        lf.lfOutPrecision = OUT_DEFAULT_PRECIS;
        lf.lfClipPrecision = CLIP_DEFAULT_PRECIS;
        lf.lfQuality = NONANTIALIASED_QUALITY;
        lf.lfPitchAndFamily = DEFAULT_PITCH | FF_DONTCARE;
        wcscpy(lf.lfFaceName, face);
        m_font = CreateFontIndirectW(&lf);
    }
    if (m_font != NULL) {
        m_fontOwned = true;
    } else {
        m_font = (HFONT)GetStockObject(SYSTEM_FONT);
        m_fontOwned = false;
    }

    /* 补充字体 danmaku.ttf（family=SimHei） */
    path[0] = L'\0';
    if (GetModuleFileNameW(NULL, path, MAX_PATH) > 0) {
        p = wcsrchr(path, L'\\');
        if (p != NULL) {
            *(p + 1) = L'\0';
            wcscat(path, L"danmaku.ttf");
            if (AddFontResourceW(path)) {
                /* 该 ttf 的 family 名是「黑体」（不是英文 SimHei）。写错名字
                 * GDI 会替换成别的字体（斜的/缺字形），表现为个别弹幕整条歪。 */
                wcscpy(lf.lfFaceName, L"黑体");
                m_fontDm = CreateFontIndirectW(&lf);
                if (m_fontDm != NULL) {
                    m_fontDmOwned = true;
                }
            }
        }
    }

    if (m_screenDC == NULL) {
        m_screenDC = GetDC(NULL);
        if (m_screenDC != NULL) {
            m_memDC = CreateCompatibleDC(m_screenDC);
            SelectObject(m_screenDC, m_font);   /* 供 Measure 用 */
        }
    }
}

/* 该码位用 danmaku.ttf（补充）还是宋体（正文）。 */
static int dm_use_dm_font(unsigned int c)
{
    if (c >= 0x2E80 && c <= 0x33FF) {
        return 1;   /* CJK 部首 / 符号 / 假名 / 兼容 */
    }
    if (c >= 0xE000 && c <= 0xF8FF) {
        return 1;   /* 私用区（B 站特殊符号） */
    }
    if (c >= 0xF900 && c <= 0xFAFF) {
        return 1;   /* CJK 兼容表意文字 */
    }
    if (c >= 0xFE30 && c <= 0xFE6F) {
        return 1;   /* CJK 兼容形式 */
    }
    if (c >= 0xFF00 && c <= 0xFFEF) {
        return 1;   /* 全角形式 */
    }
    return 0;
}

static HFONT dm_pick_font(HDC dc, HFONT main, HFONT supp, wchar_t c)
{
    (void)dc;
    if (supp == NULL) {
        return main;
    }
    return dm_use_dm_font((unsigned int)c) ? supp : main;
}

HBITMAP DanmakuOverlay::RenderText(const wchar_t *wbuf, COLORREF cr,
                                   void **pBits, int *pStride,
                                   int *pBw, int *pBh, int *pTextW)
{
    int i;
    int len;
    int tw;
    int th;
    int dw;
    int x;
    HBITMAP bmp;
    HBITMAP old;
    HBRUSH br;
    RECT r;
    void *bits = NULL;
    struct {
        BITMAPINFOHEADER hdr;
        DWORD masks[3];
    } bi;

    *pBits = NULL;
    *pStride = 0;
    *pBw = 0;
    *pBh = 0;
    *pTextW = 0;
    if (m_memDC == NULL || m_screenDC == NULL || wbuf == NULL) {
        return NULL;
    }
    len = (int)wcslen(wbuf);
    if (len <= 0) {
        return NULL;
    }

    /* 逐字量宽（按每个字实际用的字体：宋体 / danmaku.ttf 补充） */
    tw = 0;
    for (i = 0; i < len; i++) {
        HFONT f = dm_pick_font(m_memDC, m_font, m_fontDm, wbuf[i]);
        SIZE sz;

        SelectObject(m_memDC, f);
        if (GetTextExtentPoint32W(m_memDC, &wbuf[i], 1, &sz)) {
            tw += sz.cx;
        }
    }
    if (tw <= 0) {
        return NULL;
    }
    th = m_lineHeight + 4;
    /* DIB 每行字节数必须 4 字节对齐：宽度取偶，dw*2 才是 4 的倍数。
     * 之前 dw 可能是奇数，按 dw*2 逐行拷贝会错位，弹幕看着就是斜的。 */
    dw = (tw + 4 + 1) & ~1;

    /* 16bpp RGB565 DIB（顶朝下），和 overlay 面同格式，方便逐行 memcpy */
    memset(&bi, 0, sizeof(bi));
    bi.hdr.biSize = sizeof(BITMAPINFOHEADER);
    bi.hdr.biWidth = dw;
    bi.hdr.biHeight = -th;
    bi.hdr.biPlanes = 1;
    bi.hdr.biBitCount = 16;
    bi.hdr.biCompression = BI_BITFIELDS;
    bi.masks[0] = 0xF800;
    bi.masks[1] = 0x07E0;
    bi.masks[2] = 0x001F;

    bmp = CreateDIBSection(m_screenDC, (BITMAPINFO *)&bi, DIB_RGB_COLORS,
                           &bits, NULL, 0);
    if (bmp == NULL || bits == NULL) {
        return NULL;
    }
    old = (HBITMAP)SelectObject(m_memDC, bmp);
    r.left = 0;
    r.top = 0;
    r.right = dw;
    r.bottom = th;
    br = CreateSolidBrush(DM_COLORKEY);
    FillRect(m_memDC, &r, br);
    DeleteObject(br);
    SetBkMode(m_memDC, TRANSPARENT);

    /* 逐字画（4 向描边 + 本色），每个字用各自字体 */
    x = 2;
    for (i = 0; i < len; i++) {
        static const int ox[4] = { -1, 1, 0, 0 };
        static const int oy[4] = { 0, 0, -1, 1 };
        HFONT f = dm_pick_font(m_memDC, m_font, m_fontDm, wbuf[i]);
        int o;
        SIZE sz;

        SelectObject(m_memDC, f);
        SetTextColor(m_memDC, RGB(20, 20, 20));
        for (o = 0; o < 4; o++) {
            ExtTextOutW(m_memDC, x + ox[o], 2 + oy[o], 0, NULL, &wbuf[i], 1, NULL);
        }
        SetTextColor(m_memDC, cr);
        ExtTextOutW(m_memDC, x, 2, 0, NULL, &wbuf[i], 1, NULL);
        if (GetTextExtentPoint32W(m_memDC, &wbuf[i], 1, &sz)) {
            x += sz.cx;
        }
    }
    SelectObject(m_memDC, old);

    *pBits = bits;
    *pStride = dw * 2;
    *pBw = dw;
    *pBh = th;
    *pTextW = tw;
    return bmp;
}

/* 把 16bpp DIB 逐行贴到 overlay 显存（带裁剪；黑底=色键自动透明） */
static void dm_blit(void *surf, int pitch, int sw, int sh,
                    const void *bits, int stride, int bw, int bh, int dx, int dy)
{
    const unsigned char *src = (const unsigned char *)bits;
    int j;

    if (surf == NULL || bits == NULL) {
        return;
    }
    if (dx >= sw || dx + bw <= 0 || dy >= sh || dy + bh <= 0) {
        return;
    }
    for (j = 0; j < bh; j++) {
        int yy = dy + j;
        int x0 = dx;
        int x1 = dx + bw;
        int ww;

        if (yy < 0 || yy >= sh) {
            continue;
        }
        if (x0 < 0) {
            x0 = 0;
        }
        if (x1 > sw) {
            x1 = sw;
        }
        ww = x1 - x0;
        if (ww <= 0) {
            continue;
        }
        memcpy((unsigned char *)surf + (size_t)yy * pitch + (size_t)x0 * 2,
               src + (size_t)j * stride + (size_t)(x0 - dx) * 2,
               (size_t)ww * 2);
    }
}

void DanmakuOverlay::Clear()
{
    if (m_items != NULL) {
        free(m_items);
        m_items = NULL;
    }
    m_count = 0;
    m_cursor = 0;
    {
        int i;
        for (i = 0; i < m_liveCount; i++) {
            if (m_live[i].bmp != NULL) {
                DeleteObject(m_live[i].bmp);
                m_live[i].bmp = NULL;
            }
        }
    }
    m_liveCount = 0;
    m_lines = 0;
    {
        int i;
        for (i = 0; i < 16; i++) {
            m_lineFree[i] = -0x7FFFFFFF;
        }
    }
}

void DanmakuOverlay::Load(BcDanmaku *items, int count)
{
    Clear();
    m_items = items;
    m_count = count;
    m_cursor = 0;
}

/* 回到开头：清掉在场的，游标归零（items 保留）。播完 seek 回 0 时用。 */
void DanmakuOverlay::Rewind()
{
    int i;

    for (i = 0; i < m_liveCount; i++) {
        if (m_live[i].bmp != NULL) {
            DeleteObject(m_live[i].bmp);
            m_live[i].bmp = NULL;
        }
    }
    m_liveCount = 0;
    m_cursor = 0;
    for (i = 0; i < 16; i++) {
        m_lineFree[i] = -0x7FFFFFFF;
    }
    m_lastNow = -1;
}

int DanmakuOverlay::Measure(int idx, HDC dc)
{
    wchar_t wbuf[BC_DM_TEXT_MAX * 2];
    SIZE sz;

    dm_to_wide(m_items[idx].text, wbuf, BC_DM_TEXT_MAX * 2);
    if (GetTextExtentPoint32W(dc, wbuf, (int)wcslen(wbuf), &sz)) {
        return sz.cx;
    }
    return 60;
}

int DanmakuOverlay::AllocateLine(int mode, long now)
{
    int i;

    if (m_lines <= 0) {
        return -1;
    }
    for (i = 0; i < m_lines; i++) {
        int line = (mode == 4) ? (m_lines - 1 - i) : i;
        if (m_lineFree[line] <= now) {
            return line;
        }
    }
    return -1;
}

/* 更新：生成/淘汰并预渲染位图。刻意不接触 overlay 显存，
 * 避免"锁住显存期间再做 CreateDIBSection/GDI"造成显示驱动锁序死锁。 */
void DanmakuOverlay::Update(long nowMs, int w, int h)
{
    int i;

    if (m_items == NULL || m_count <= 0) {
        return;
    }
    if (!m_active) {
        /* 弹幕关：只推进游标丢掉过期项、清空在场（顺便释放位图），
         * 不做任何 CreateDIBSection/文字渲染；控制条不受影响。 */
        while (m_cursor < m_count && m_items[m_cursor].time_ms <= nowMs) {
            m_cursor++;
        }
        for (i = 0; i < m_liveCount; i++) {
            if (m_live[i].bmp != NULL) {
                DeleteObject(m_live[i].bmp);
                m_live[i].bmp = NULL;
            }
        }
        m_liveCount = 0;
        for (i = 0; i < 16; i++) {
            m_lineFree[i] = -0x7FFFFFFF;
        }
        m_lastNow = nowMs;
        return;
    }
    EnsureFont();

    /* 行数随窗口高度变化 */
    {
        int lines = h / m_lineHeight;
        if (lines > 16) {
            lines = 16;
        }
        if (lines < 1) {
            lines = 1;
        }
        if (lines != m_lines) {
            m_lines = lines;
            for (i = 0; i < 16; i++) {
                m_lineFree[i] = -0x7FFFFFFF;
            }
        }
    }

    /* 生成新的弹幕 */
    while (m_cursor < m_count && m_items[m_cursor].time_ms <= nowMs) {
        int idx = m_cursor++;
        int mode = m_items[idx].mode;
        int line;
        int tw;
        long dur;
        int fromX;
        int toX;
        Live *lv;

        if (nowMs - m_items[idx].time_ms > DM_LATE_MS) {
            continue;
        }
        if (m_liveCount >= DM_MAX_CONCURRENT) {
            continue;
        }
        line = AllocateLine(mode, nowMs);
        if (line < 0) {
            continue;
        }

        /* 先渲染成位图（顺带得到按实际字体算出的文字宽度 tw） */
        lv = &m_live[m_liveCount];
        lv->item = idx;
        lv->bmp = NULL;
        lv->bits = NULL;
        lv->bw = 0;
        lv->bh = 0;
        lv->stride = 0;
        lv->width = 0;
        {
            wchar_t wbuf[BC_DM_TEXT_MAX * 2];
            COLORREF cr;

            dm_to_wide(m_items[idx].text, wbuf, BC_DM_TEXT_MAX * 2);
            cr = RGB((m_items[idx].color >> 16) & 0xFF,
                     (m_items[idx].color >> 8) & 0xFF,
                     m_items[idx].color & 0xFF);
            lv->bmp = RenderText(wbuf, cr, &lv->bits, &lv->stride,
                                 &lv->bw, &lv->bh, &tw);
        }
        if (lv->bmp == NULL || tw <= 0) {
            if (lv->bmp != NULL) {
                DeleteObject(lv->bmp);
                lv->bmp = NULL;
            }
            continue;
        }

        dur = (mode == 4 || mode == 5) ? DM_TOPBOTTOM_MS : DM_SCROLL_MS;
        if (mode == 1) {
            fromX = w;
            toX = -tw;
        } else if (mode == 6) {
            fromX = -tw;
            toX = w;
        } else {
            fromX = (w - tw) / 2;
            toX = fromX;
        }

        lv->startMs = nowMs;
        lv->endMs = nowMs + dur;
        lv->line = line;
        lv->fromX = fromX;
        lv->toX = toX;
        lv->width = tw;
        m_liveCount++;

        if (mode == 1 || mode == 6) {
            long span = (toX > fromX) ? (toX - fromX) : (fromX - toX);
            long occ = dur;
            if (span > 0) {
                occ = (long)((double)dur * tw / (double)span);
            }
            m_lineFree[line] = nowMs + occ;
        } else {
            m_lineFree[line] = nowMs + dur;
        }
    }

    /* 淘汰结束的 */
    for (i = m_liveCount - 1; i >= 0; i--) {
        if (nowMs >= m_live[i].endMs) {
            if (m_live[i].bmp != NULL) {
                DeleteObject(m_live[i].bmp);
                m_live[i].bmp = NULL;
            }
            m_live[i] = m_live[m_liveCount - 1];
            m_liveCount--;
        }
    }

    m_lastNow = nowMs;
}

/* 只贴图到 overlay 显存（调用方已锁住并整屏清成色键） */
void DanmakuOverlay::Render(void *surface, int pitch, int w, int h)
{
    int i;

    if (surface == NULL || w <= 0 || h <= 0) {
        return;
    }
    for (i = 0; i < m_liveCount; i++) {
        Live *lv = &m_live[i];
        long t;
        long dur;
        int x;
        int y;

        if (lv->bits == NULL) {
            continue;
        }
        t = m_lastNow - lv->startMs;
        dur = lv->endMs - lv->startMs;
        if (t < 0) {
            t = 0;
        }
        if (t > dur) {
            t = dur;
        }
        if (dur <= 0) {
            dur = 1;
        }
        x = (int)(lv->fromX +
                  (long)((double)(lv->toX - lv->fromX) * (double)t / (double)dur));
        y = lv->line * m_lineHeight;
        dm_blit(surface, pitch, w, h, lv->bits, lv->stride,
                lv->bw, lv->bh, x - 2, y - 2);
    }
}
