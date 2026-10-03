/* =====================================================================
 * CoverCache.h - 封面缩略图缓存
 * ---------------------------------------------------------------------
 * 后台线程把 B 站封面（明文 http）下到数据目录 cover_<bvid>.jpg，
 * 完成后向窗口 PostMessage(WM_APP_COVER_DONE, index, 0) 让列表重画。
 * ===================================================================== */
#ifndef UI_COVERCACHE_H
#define UI_COVERCACHE_H

#include <windows.h>

class CoverCache
{
public:
    /* 换了一批结果时清空待下载队列 */
    static void Reset();

    /* 清空封面缓存：内存里的解码位图 + 数据目录里所有 cover_*.jpg。
     * 必须在 UI 线程调用。 */
    static void ClearAll();

    /* 只留最近 keep 张解码位图，删掉更早的（必须在 UI 线程调用）。
     * WinCE 的 GDI/DIB 内存很金贵，翻页累加下去会把整机拖死。 */
    static void Trim(int keep);

    /* 请求某条结果的封面（已缓存则直接跳过）。index 用于完成通知 */
    static void Request(HWND wnd, int index, const char *picUrl, const char *bvid);

    /* 取缓存文件路径(GBK)写到 out；*exists=1 表示文件已存在 */
    static void PathFor(const char *bvid, char *out, int cap, int *exists);

    /* 取已解码的封面 HBITMAP（144x81）；无缓存返回 NULL。
     * 首次调用会把文件解码成位图并缓存，避免每次重绘都解码。 */
    static HBITMAP GetBitmap(const char *bvid);

private:
    static DWORD WINAPI ThreadProc(LPVOID param);
};

#endif /* UI_COVERCACHE_H */
