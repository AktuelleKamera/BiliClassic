/* =====================================================================
 * DanmakuOverlay.h - 纯 GDI 弹幕层（滚动/顶部/底部）
 * ---------------------------------------------------------------------
 * 布局算法移植自 BiliClassic WP 版 Danmaku/DanmakuView.cs。
 * 直接画到播放器视频窗的 DC 上（M8 没有分层窗口，做不了色键透明）。
 * ===================================================================== */
#ifndef UI_DANMAKUOVERLAY_H
#define UI_DANMAKUOVERLAY_H

#include <windows.h>
#include "../core/danmaku.h"

/* overlay 色键：这个颜色在屏幕上透明。
 * 用黑色——字形边缘和它混合只会变暗，不会像洋红那样发紫。 */
#define DM_COLORKEY RGB(0, 0, 0)

class DanmakuOverlay
{
public:
    DanmakuOverlay();
    ~DanmakuOverlay();

    /* 接管 items 的所有权（会 free）。count<=0 视为未就绪。 */
    void Load(BcDanmaku *items, int count);
    void Clear();
    /* 回到开头（不清 items，只把游标/在场都复位，供重播用） */
    void Rewind();

    bool Ready() const { return m_items != NULL; }
    int  Count() const { return m_count; }
    int  LiveCount() const { return m_liveCount; }

    /* 弹幕总开关：关时 Update 只推进游标、清空在场，不生成/渲染任何弹幕 */
    void SetActive(bool on) { m_active = on; }
    bool IsActive() const { return m_active; }

    /* 更新（生成/淘汰，预渲染位图）——不接触 overlay 显存 */
    void Update(long nowMs, int w, int h);
    /* 只把位图贴到 overlay 显存（调用方已锁住显存并整屏清成色键）。
     * 注意：overlay 是翻转链，调用方每帧必须整帧重画，否则翻到另一块
     * 后备缓冲时会缺内容 → 闪。 */
    void Render(void *surface, int pitch, int w, int h);

private:
    struct Live {
        int  item;
        long startMs;
        long endMs;
        int  line;
        int  fromX;
        int  toX;
        int  width;
        HBITMAP bmp;    /* 预渲染的 16 位 DIB（黑底=色键） */
        void  *bits;    /* DIB 像素指针（top-down） */
        int   bw;
        int   bh;
        int   stride;   /* DIB 每行字节 */
    };

    void Reset();
    int  AllocateLine(int mode, long now);
    int  Measure(int idx, HDC dc);
    void EnsureFont();
    HBITMAP RenderText(const wchar_t *wbuf, COLORREF cr, void **pBits,
                       int *pStride, int *pBw, int *pBh, int *pTextW);

    BcDanmaku *m_items;
    int  m_count;
    int  m_cursor;
    bool m_active;   /* 弹幕开关（默认开） */

    Live m_live[32];
    int  m_liveCount;

    long m_lineFree[16];
    int  m_lines;
    int  m_lineHeight;

    long m_lastNow;
    HFONT m_font;       /* 正文：宋体（汉字全覆盖） */
    bool  m_fontOwned;
    HFONT m_fontDm;     /* 补充：danmaku.ttf（假名/符号/私用区特殊符） */
    bool  m_fontDmOwned;
    HDC   m_screenDC;   /* 用于创建兼容位图 */
    HDC   m_memDC;      /* 复用：渲染文字 / 贴图 */

    /* 上一帧用到的行，下一帧整行擦掉（擦得干净又不整屏重绘 → 不拖尾也不闪） */
    bool m_linePrev[16];
    int  m_rowCount[16];   /* 上一帧每行的条数 */
    bool m_rowMoving[16];  /* 上一帧每行是否有滚动条目 */
    HBRUSH m_brush;
    unsigned char *m_rowBuf;   /* 行缓冲：整行拼好再一次性写显存 */
    int  m_rowBufCap;
};

#endif /* UI_DANMAKUOVERLAY_H */
