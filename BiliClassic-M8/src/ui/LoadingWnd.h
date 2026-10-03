/* =====================================================================
 * LoadingWnd.h - 全屏加载层（仿安卓/WM 版「小电视」preloading 界面）
 * ---------------------------------------------------------------------
 * 灰底 #BABABA + 居中 120x120 小电视帧动画（5 帧 / 100ms）+
 * 左上返回按钮 + 底部左对齐状态文字（两行，滚动显示上一阶段）。
 * 纯 Win32（非 Mzfc）顶层窗口，置顶，覆盖在播放器之上，直到视频起来。
 * ===================================================================== */
#ifndef UI_LOADINGWND_H
#define UI_LOADINGWND_H

#include <windows.h>

class LoadingWnd
{
public:
    /* 显示加载层（已显示则置顶）并开始动画，同时把屏幕转横屏 */
    static void Show();
    /* 设置状态文字：新文字成为当前行，上一行上移（两行）；重复文字忽略 */
    static void SetStatus(const wchar_t *text);
    /* 隐藏并停止动画。restorePortrait=true 时同时转回竖屏（取消/失败用）；
     * 开播时传 false，屏幕保持横屏交给播放器。 */
    static void Hide(bool restorePortrait = true);
    static bool IsShowing();

    /* 用户是否点了左上返回（取走标记，取走后清零） */
    static bool TakeCancel();

private:
    static LRESULT CALLBACK WndProc(HWND hWnd, UINT msg,
                                    WPARAM wParam, LPARAM lParam);
    static void EnsureClass();
    static void LoadFrames();
    static void FreeRes();

    static HWND      s_hwnd;
    static HBITMAP   s_frames[5];
    static int       s_frameCount;
    static int       s_frame;
    static bool      s_show;
    static bool      s_cancel;
    static wchar_t   s_line1[128];
    static wchar_t   s_line2[128];
    static HFONT     s_font;
    static int       s_w;
    static int       s_h;
};

#endif /* UI_LOADINGWND_H */
