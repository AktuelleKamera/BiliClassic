/* =====================================================================
 * MzPlayer.cpp - M8 PlayerCore(COM) 内嵌播放实现
 * ---------------------------------------------------------------------
 * 流程：CoCreateInstance(CLSID_PlayerCore, IID_PlayerCore_Play)
 *       -> QI(IMzSysFile).SetParentWnd/SetName(媒体源)
 *       -> InitVideoWnd(宿主, 视频区)
 *       -> OpenFile() -> Play()
 * 每一步的 HRESULT 都写日志，方便真机定位（CLSID 是否注册、媒体源是否被接受）。
 * ===================================================================== */
#include <windows.h>
#include <InitGuid.h>
#include <objbase.h>

#include "MzPlayer.h"

#include <IMzUnknown.h>
#include <IMzUnknown_IID.h>
#include <IPlayerCore.h>
#include <IPlayerCore_IID.h>
#include <PlayerCore_GUID.h>
#include <IMzSysFile.h>

#include "../app/AppContext.h"
#include "../core/bc.h"

#define MPLOG(...) do { g_app.GetLogger().Log(__VA_ARGS__); } while (0)

MzPlayer::MzPlayer()
{
    m_play = NULL;
    m_sys = NULL;
    m_lastHr = 0;
    m_vw = 0;
    m_vh = 0;
}

MzPlayer::~MzPlayer()
{
    Close();
}

/* 第一步：打开媒体、取原始宽高、设通知（此时先不建视频窗） */
bool MzPlayer::Prepare(HWND host, const char *pathGbk)
{
    IPlayerCore_Play *play = NULL;
    IMzSysFile *sys = NULL;
    wchar_t wpath[BC_PATH_LEN + 8];
    HRESULT hr;

    Close();

    m_vw = 0;
    m_vh = 0;
    if (host == NULL || pathGbk == NULL || pathGbk[0] == '\0') {
        return false;
    }
    wpath[0] = L'\0';
    MultiByteToWideChar(CP_ACP, 0, pathGbk, -1, wpath,
                        (int)(sizeof(wpath) / sizeof(wpath[0])));

    hr = CoCreateInstance(CLSID_PlayerCore, NULL, CLSCTX_INPROC_SERVER,
                          IID_PlayerCore_Play, (void **)&play);
    m_lastHr = (long)hr;
    MPLOG("PlayerCore: CoCreateInstance hr=0x%08lX", (unsigned long)hr);
    if (FAILED(hr) || play == NULL) {
        MPLOG("PlayerCore 不可用（CLSID 未注册 / 无 COM 支持）");
        return false;
    }

    /* 媒体源：按 MZ COM 惯例用 IMzSysFile 设置 */
    hr = play->QueryInterface(IID_MZ_SysFile, (void **)&sys);
    MPLOG("PlayerCore: QI(IMzSysFile) hr=0x%08lX", (unsigned long)hr);
    if (SUCCEEDED(hr) && sys != NULL) {
        sys->SetParentWnd(host);
        sys->SetName(wpath);
        MPLOG("PlayerCore: 媒体源 = %s", pathGbk);
    } else {
        sys = NULL;
        MPLOG("PlayerCore: 该对象不支持 IMzSysFile，可能无法指定媒体源");
    }

    /* OpenFile = 第一步：建 DirectShow 播放图（必须在 InitVideoWnd 之前） */
    hr = play->OpenFile(false, false);
    MPLOG("PlayerCore: OpenFile hr=0x%08lX", (unsigned long)hr);
    if (FAILED(hr)) {
        if (sys != NULL) {
            sys->Release();
        }
        play->Release();
        m_lastHr = (long)hr;
        return false;
    }

    play->GetVideoSize(m_vw, m_vh);
    MPLOG("PlayerCore: 原始尺寸 %ldx%ld", m_vw, m_vh);

    hr = play->SetNotify(host, (UINT)(WM_APP + 0x520));
    MPLOG("PlayerCore: SetNotify hr=0x%08lX", (unsigned long)hr);

    m_play = play;
    m_sys = sys;
    return true;
}

long MzPlayer::CurPosMs() const
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

    if (play == NULL) {
        return 0;
    }
    return (long)play->GetCurPos();
}

long MzPlayer::LengthMs() const
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

    if (play == NULL) {
        return 0;
    }
    return (long)play->GetLength();
}

void MzPlayer::SeekTo(long ms)
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

    if (play == NULL) {
        return;
    }
    if (ms < 0) {
        ms = 0;
    }
    play->SeekTo((LONGLONG)ms);
}

bool MzPlayer::IsPlaying() const
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

    if (play == NULL) {
        return false;
    }
    return play->GetCurState() == psRUNNING;
}

void MzPlayer::SetVolume(int v)
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

    if (play == NULL) {
        return;
    }
    if (v < 0) {
        v = 0;
    }
    if (v > 100) {
        v = 100;
    }
    play->SetVolume((UINT)v);
}

void MzPlayer::Pause()
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

    if (play != NULL) {
        play->Pause();
    }
}

void MzPlayer::Resume()
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

    if (play != NULL) {
        play->Play();
    }
}

/* 第二步：按给定矩形建视频窗并开播 */
bool MzPlayer::Start(HWND host, const RECT &rc)
{
    IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;
    HRESULT hr;

    if (play == NULL) {
        return false;
    }
    MPLOG("PlayerCore: InitVideoWnd host=%p rc=%d,%d,%d,%d",
          (void *)host, (int)rc.left, (int)rc.top,
          (int)rc.right, (int)rc.bottom);
    hr = play->InitVideoWnd(host, rc);
    MPLOG("PlayerCore: InitVideoWnd hr=0x%08lX", (unsigned long)hr);

    hr = play->Play();
    MPLOG("PlayerCore: Play hr=0x%08lX（屏幕 %dx%d）",
          (unsigned long)hr,
          GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN));

    /* 默认音量 60/100，避免一开就是最大声 */
    play->SetVolume(60);

    m_lastHr = (long)hr;
    return SUCCEEDED(hr);
}

bool MzPlayer::Open(HWND host, const RECT &rc, const char *pathGbk)
{
    if (!Prepare(host, pathGbk)) {
        return false;
    }
    return Start(host, rc);
}

void MzPlayer::Close()
{
    if (m_play != NULL) {
        IPlayerCore_Play *play = (IPlayerCore_Play *)m_play;

        play->Stop();
        play->Close();
        play->Release();
        m_play = NULL;
    }
    if (m_sys != NULL) {
        ((IMzSysFile *)m_sys)->Release();
        m_sys = NULL;
    }
}
