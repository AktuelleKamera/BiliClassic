/* =====================================================================
 * AppContext.cpp - 全局应用上下文实现
 * ===================================================================== */
#include "AppContext.h"
#include "../core/bili.h"
#include "../core/http.h"
#include <stdlib.h>
#include <string.h>

AppContext g_app;

/* 后台任务线程的入口包装 */
struct AppJobCtx
{
    void (*fn)(void *);
    void *arg;
};

static DWORD WINAPI AppJobProc(LPVOID p)
{
    AppJobCtx *c = (AppJobCtx *)p;
    c->fn(c->arg);
    free(c);
    return 0;
}

/* core/http.c 的诊断日志转进日志文件，便于定位卡在哪一步 */
static void HttpLogCb(const char *msg)
{
    g_app.GetLogger().Log("[http] %s", msg);
}

/* core/bili.c 的诊断日志 */
static void BiliLogCb(const char *msg)
{
    g_app.GetLogger().Log("[bili] %s", msg);
}

AppContext::AppContext()
{
    m_sink = NULL;
    m_item_count = 0;
    m_ready = 0;
    m_busy = 0;
    m_video_opened = 0;
    m_cancel = 0;
    m_job = NULL;
    m_danmaku = NULL;
    m_danmaku_count = 0;
    m_last_file[0] = '\0';
    memset(m_items, 0, sizeof(m_items));
    memset(&m_cfg, 0, sizeof(m_cfg));
}

void AppContext::SetDanmaku(BcDanmaku *items, int count)
{
    if (m_danmaku != NULL) {
        free(m_danmaku);
    }
    m_danmaku = items;
    m_danmaku_count = (items != NULL) ? count : 0;
}

BcDanmaku *AppContext::TakeDanmaku(int *count)
{
    BcDanmaku *items = m_danmaku;

    m_danmaku = NULL;
    if (count != NULL) {
        *count = m_danmaku_count;
    }
    m_danmaku_count = 0;
    return items;
}

void AppContext::Init(IUiSink *sink)
{
    m_sink = sink;

    bili_config_init(&m_cfg);
    m_log.Init(m_cfg.data_dir, m_sink);
    m_log.Log("[bc] logger init ok");

    http_init();
    http_set_log(HttpLogCb);
    http_set_conns(m_cfg.conns);
    bili_set_log(BiliLogCb);
    m_log.Log("[bc] core init ok");

    m_log.Log("%s %s", BC_APP_TITLE, BC_APP_VER);
    m_log.Log("[bc] after title");
    m_log.Log("数据目录：%s", m_cfg.data_dir);
    m_log.Log("[bc] after datadir");
    m_log.Log("TLS：wolfSSL（API 全直连）");
    m_log.Log("转码：%s（H.264 Baseline，服务器 %s:%d）",
              m_cfg.transcode ? "开" : "关", m_cfg.relay_host, m_cfg.relay_port);
    m_log.Log("下载连接数：%d", m_cfg.conns);
    m_log.Log("[bc] before status");
    m_log.Status("就绪");
    m_ready = 1;
    m_log.Log("[bc] init done");
}

void AppContext::Shutdown()
{
    /* 等后台网络任务退出（最多 5s，超时就放着，网络超时后自然会结束） */
    if (m_job != NULL) {
        WaitForSingleObject(m_job, 5000);
        CloseHandle(m_job);
        m_job = NULL;
    }
    http_done();
}

void AppContext::RunAsync(void (*fn)(void *), void *arg)
{
    AppJobCtx *c;

    if (m_job != NULL) {
        CloseHandle(m_job);
        m_job = NULL;
    }
    c = (AppJobCtx *)malloc(sizeof(AppJobCtx));
    if (c == NULL) {
        fn(arg);            /* 起不了线程就同步跑，至少不丢任务 */
        return;
    }
    c->fn = fn;
    c->arg = arg;
    /* wolfSSL 握手很吃栈，显式给大栈（默认只有 exe 的 64KB，会溢出崩） */
    m_job = CreateThread(NULL, 1024 * 1024, AppJobProc, c, 0, NULL);
    if (m_job == NULL) {
        fn(arg);
        free(c);
    }
}

void AppContext::SetSink(IUiSink *sink)
{
    m_sink = sink;
    m_log.Init(m_cfg.data_dir, m_sink);
}

IUiSink *AppContext::Sink()
{
    return m_sink;
}

void AppContext::SetBusy(int busy)
{
    m_busy = busy ? 1 : 0;
    if (m_sink != NULL) {
        m_sink->OnBusyChanged(m_busy);
        m_sink->PumpMessages();
    }
}

void AppContext::Pump()
{
    if (m_sink != NULL) {
        m_sink->PumpMessages();
    }
}
