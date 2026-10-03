/* =====================================================================
 * MzPlayer.h - 魅族 M8 系统播放器 PlayerCore(COM) 的薄封装
 * ---------------------------------------------------------------------
 * 用官方 COM（CLSID_PlayerCore / IID_PlayerCore_Play）在宿主窗口里内嵌播放。
 * 注意：IPlayerCore_Play::OpenFile() 没有文件名参数，按 MZ COM 惯例，
 *       媒体源用同一对象上的 IMzSysFile::SetName(路径) 指定。
 * ===================================================================== */
#ifndef UI_MZPLAYER_H
#define UI_MZPLAYER_H

#include <windows.h>

class MzPlayer
{
public:
    MzPlayer();
    ~MzPlayer();

    /* host: 播放宿主窗口；rc: 视频区域（客户区坐标）；
     * pathGbk: 本地文件路径，或 http 流地址（均为 GBK / CP_ACP） */
    bool Open(HWND host, const RECT &rc, const char *pathGbk);

    /* 分两步：Prepare 打开媒体并取到原始宽高（此时还没建视频窗），
     * Start 再按算好的矩形初始化视频窗并开播。 */
    bool Prepare(HWND host, const char *pathGbk);
    bool Start(HWND host, const RECT &rc);

    void Close();

    bool IsOpen() const { return m_play != NULL; }
    long LastError() const { return m_lastHr; }

    /* Prepare 之后可用：媒体原始宽高（0,0 表示未知） */
    long VideoW() const { return m_vw; }
    long VideoH() const { return m_vh; }

    /* 当前播放位置（毫秒），未播放返回 0 */
    long CurPosMs() const;

    /* 媒体总时长（毫秒），取不到返回 0 */
    long LengthMs() const;

    /* 跳转到指定位置（毫秒） */
    void SeekTo(long ms);

    /* 是否正在播放 */
    bool IsPlaying() const;

    /* 设置播放音量（0..100） */
    void SetVolume(int v);

    /* 暂停 / 继续（锁屏时暂停） */
    void Pause();
    void Resume();

private:
    void *m_play;   /* IPlayerCore_Play* */
    void *m_sys;    /* IMzSysFile*      */
    long  m_lastHr;
    long  m_vw;
    long  m_vh;
};

#endif /* UI_MZPLAYER_H */
