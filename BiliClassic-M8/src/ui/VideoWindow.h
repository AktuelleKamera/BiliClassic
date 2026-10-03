/* =====================================================================
 * VideoWindow.h - 内嵌视频播放窗口（模态，全屏黑底）
 * ---------------------------------------------------------------------
 * 用 MzPlayer 把系统 PlayerCore 嵌进来播放；带一个「返回」按钮，
 * 支持 M8 返回键（VK_ESCAPE / VK_BACK）退出。
 * ===================================================================== */
#ifndef UI_VIDEOWINDOW_H
#define UI_VIDEOWINDOW_H

#include <mzfc_inc.h>
#include "MzPlayer.h"
#include "DanmakuOverlay.h"
#include <mzfc/MzDDrawOverlay.h>
#include <string>

class VideoWindow : public CMzWndEx
{
    MZ_DECLARE_DYNAMIC(VideoWindow);
public:
    VideoWindow();
    virtual ~VideoWindow();

    /* 模态打开并播放；返回 true 表示内置播放器成功启动 */
    bool PlayModal(HWND hwndParent, const char *pathGbk);

protected:
    virtual BOOL OnInitDialog();
    virtual void OnSize(int nWidth, int nHeight);
    virtual void OnTimer(UINT_PTR nIDEvent);
    virtual void OnMzCommand(WPARAM wParam, LPARAM lParam);
    virtual void OnLButtonDown(UINT fwKeys, int xPos, int yPos);
    virtual LRESULT MzDefWndProc(UINT message, WPARAM wParam, LPARAM lParam);
private:
    void UpdateLayout();
    void OpenPlayer();
    void DrawDanmaku();
    bool InitOverlay();
    void ShowControls();
    void DrawControls(void *surface, int pitch, int w, int h);
    void FreeControls();
    int  HandleControlTap(int ox, int oy);

    MzPlayer    m_player;
    UiButton    m_btnBack;
    std::string m_path;
    bool        m_ok;
    bool        m_closing;
    int         m_rotated;   /* 是否把屏幕转成了横屏（退出要恢复） */
    DanmakuOverlay m_danmaku;
    MzDDrawOverlay m_overlay;
    int         m_overlayOn;
    int         m_ovW;
    int         m_ovH;
    DWORD       m_playStart;  /* 开播时刻，GetCurPos 不可用时兜底 */
    long        m_lastPos;    /* 最近一次弹幕位置（息屏恢复用） */
    UINT        m_msgLock;    /* shell 进入锁屏消息值 */
    UINT        m_msgUnlock;  /* shell 离开锁屏消息值 */
    int         m_lockRegistered;
    int         m_unrotated;  /* 锁屏时已转回竖屏，亮屏需转回横屏 */
    int         m_smoothSaved;
    UINT        m_oldSmooth;
    int         m_screenOn;    /* 是否已请求屏幕常亮（退出要恢复） */

    /* 播放器控制层（点一下出现，5 秒后自动隐藏） */
    int         m_ctrlShown;
    DWORD       m_ctrlHideAt;  /* 自动隐藏时刻（GetTickCount） */
    int         m_playing;
    int         m_volume;      /* 0..100，控制层音量调节 */
    HDC         m_ctrlDC;
    HBITMAP     m_ctrlBmp;
    HBITMAP     m_ctrlOldBmp;
    void       *m_ctrlBits;
    int         m_ctrlPitch;
    int         m_ctrlBw;
    int         m_ctrlBh;
    HFONT       m_ctrlFont;
};

#endif /* UI_VIDEOWINDOW_H */
