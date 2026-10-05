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
    void UpdateControlBar();
    void RenderControlBar();
    void DrawControls(void *surface, int pitch, int w, int h);
    void FreeControls();
    int  HandleControlTap(int ox, int oy);
    static DWORD WINAPI HistThread(void *arg);   /* 观看历史上报线程 */

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
    DWORD       m_ctrlShownAt; /* 刚亮出控制条的时刻（防触摸重复 DOWN 立刻收回） */
    int         m_playing;
    int         m_volume;      /* 0..100，控制层音量调节 */
    int         m_danmakuOn;   /* 弹幕开关（控制层按钮切换，持久化到 ini） */
    HDC         m_ctrlDC;
    HBITMAP     m_ctrlBmp;
    HBITMAP     m_ctrlOldBmp;
    void       *m_ctrlBits;
    int         m_ctrlPitch;
    int         m_ctrlBw;
    int         m_ctrlBh;
    HFONT       m_ctrlFont;

    /* 播放环境效率：画面没变就不碰 overlay 显存（不 Lock/不 memset/不 flip）。
     * 视频大部分时间屏幕上是空的（弹幕间隙）或暂停中，这时零开销。 */
    long        m_prevPos;    /* 上一帧用的播放位置 */
    int         m_endSeeked;  /* 播到结尾已自动 seek 回 0 */
    int         m_prevLive;   /* 上一帧屏上的弹幕条数 */
    int         m_prevCtrl;   /* 上一帧控制条是否在屏上 */
    int         m_ctrlBmpValid;   /* 控制条 DIB 内容是否仍符合缓存键 */
    long        m_ctrlSec;        /* 控制条缓存键：进度秒 */
    int         m_ctrlVolume;     /* 控制条缓存键：音量 */
    int         m_ctrlPlaying;    /* 控制条缓存键：播放/暂停 */
    int         m_ctrlDirty;      /* 用户操作过控制条：下一帧强制重画 */
    HANDLE      m_histThread;     /* 观看历史上报线程 */
    volatile long m_histStop;     /* 置 1 让上报线程退出 */
};

#endif /* UI_VIDEOWINDOW_H */
