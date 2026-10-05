/* =====================================================================
 * AppContext.h - 全局应用上下文（配置/日志/结果集/忙态/网络生命周期）
 * ---------------------------------------------------------------------
 * 唯一的全局状态持有者；业务服务（Search/Play）通过它访问 core 层。
 * ===================================================================== */
#ifndef APP_APPCONTEXT_H
#define APP_APPCONTEXT_H

#include "../core/bc.h"
#include "../core/util.h"
#include "../core/danmaku.h"
#include "UiSink.h"
#include "Logger.h"

class AppContext
{
public:
    AppContext();

    /* 初始化配置、日志、网络；sink 可先为空，稍后 SetSink */
    void Init(IUiSink *sink);
    void Shutdown();

    BcConfig &Cfg()      { return m_cfg; }
    Logger   &GetLogger() { return m_log; }

    void SetSink(IUiSink *sink);
    IUiSink *Sink();

    /* 最近一次搜索/热门的结果集 */
    BcItem *Items()      { return m_items; }
    int     &ItemCount() { return m_item_count; }

    /* 最近一次下载成功的文件（用于「播放上次」） */
    const char *LastFile()          { return m_last_file; }
    void SetLastFile(const char *p) { str_copy(m_last_file, (int)sizeof(m_last_file), p); }

    /* 忙态：禁用控件 + 保持泵消息（防重入） */
    void SetBusy(int busy);
    int  IsBusy() { return m_busy; }

    /* Init 是否已经跑完。启动阶段的嵌套泵消息可能在初始化中途
     * 触发界面动作，用它挡住，避免半初始化状态下重入。 */
    int  IsReady() { return m_ready; }

    /* 取消：加载页点返回时置位，后台任务在步骤边界检查后放弃 */
    void ClearCancel() { m_cancel = 0; }
    void RequestCancel() { m_cancel = 1; }
    int  IsCancelled() { return m_cancel; }

    /* 同步等待期间泵消息（转发给界面层实现） */
    void Pump();

    /* 在后台线程跑一个任务（网络请求专用）。同一时刻只跑一个，
     * 由 IsBusy 保护；arg 由任务自己释放。 */
    void RunAsync(void (*fn)(void *), void *arg);

    /* 内置播放器是否真的打开了。UI 线程写、下载线程读，
     * 用来判断边下边播有没有成功（没成功就等下完再放）。 */
    void SetVideoOpened(int v) { m_video_opened = v; }
    int  VideoOpened()         { return m_video_opened; }

    /* 当前在播的视频（供观看历史上报用）。UI 线程写，上报线程读。 */
    void SetPlayingInfo(const char *bvid, const char *cid);
    const char *PlayingBvid() { return m_play_bvid; }
    const char *PlayingCid()  { return m_play_cid; }

    /* 弹幕：后台线程 Set（接管所有权），UI 线程 Take 走 */
    void SetDanmaku(BcDanmaku *items, int count);
    BcDanmaku *TakeDanmaku(int *count);

private:
    BcConfig m_cfg;
    Logger   m_log;
    IUiSink *m_sink;
    BcItem   m_items[BC_MAX_ITEMS];
    int      m_item_count;
    int      m_ready;
    char     m_last_file[260];
    int      m_busy;
    volatile int m_cancel;
    volatile int m_video_opened;
    char     m_play_bvid[BC_BVID_LEN + 4];
    char     m_play_cid[32];
    BcDanmaku *m_danmaku;
    int        m_danmaku_count;
    HANDLE   m_job;         /* 当前后台任务线程（可为 NULL） */
};

extern AppContext g_app;

#endif /* APP_APPCONTEXT_H */
