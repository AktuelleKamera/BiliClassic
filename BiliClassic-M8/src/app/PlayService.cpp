/* =====================================================================
 * PlayService.cpp - 播放链路实现（从 BiliClassic_Native main.c 移植）
 * ---------------------------------------------------------------------
 * 流程：pagelist -> cid -> playurl -> 逐镜像探测 ->
 *       (转码 ? 请服务器转码 : 直下原片) -> 下载 -> 校验格式 -> 播放
 * 播放调用：ShellExecute 交给 M8 系统播放器（CE 无 MCI）
 * ===================================================================== */
#include "PlayService.h"
#include "AppContext.h"
#include "../core/bili.h"
#include "../core/http.h"
#include "../core/danmaku.h"
#include "../core/util.h"
#include <shellapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ---- 边下边播状态（下载线程与进度回调共用） ---- */
static int  s_stream_on = 0;          /* 本次下载是否边下边播 */
static int  s_stream_started = 0;     /* 是否已经启动过播放器 */
static char s_stream_path[260];
static char s_stream_url[512];        /* 明文 http 地址则优先交给播放器在线播 */
#define STREAM_THRESHOLD (1024L * 1024L)   /* 攒够 1MB 就起播放器 */

/* ---------------- 格式辅助 ---------------- */

/* 只转 H.264 Baseline：M8 能硬解、最省空间 */
const char *PlayService::FmtLabel(void)
{
    return "H.264 Baseline";
}

/* 服务器返回的地址自带扩展名（240p/x.mp4），比瞎猜准 */
const char *PlayService::UrlExt(const char *url)
{
    const char *q = strchr(url, '?');
    int len = (q != NULL) ? (int)(q - url) : (int)strlen(url);

    if (len >= 4) {
        if (strncmp(url + len - 4, ".mpg", 4) == 0) {
            return ".mpg";
        }
        if (strncmp(url + len - 4, ".wmv", 4) == 0) {
            return ".wmv";
        }
        if (strncmp(url + len - 4, ".mp4", 4) == 0) {
            return ".mp4";
        }
        if (strncmp(url + len - 4, ".avi", 4) == 0) {
            return ".avi";
        }
    }
    if (len >= 5 && strncmp(url + len - 5, ".mpeg", 5) == 0) {
        return ".mpg";
    }
    return NULL;
}

/* ---------------- 下载进度 ---------------- */

void PlayService::OnProgress(long done, long total)
{
    static long last_mb = -1;
    long mb = done / (1024L * 1024L);

    if (g_app.Sink() != NULL) {
        g_app.Sink()->OnDownloadProgress(done, total);
        g_app.Pump();
    }
    /* 边下边播：攒够阈值就先交给播放器（下载继续） */
    if (s_stream_on && !s_stream_started && done >= STREAM_THRESHOLD) {
        s_stream_started = 1;
        if (g_app.Sink() != NULL) {
            if (s_stream_url[0] != '\0') {
                /* 本地文件正被下载器占用，播放器打不开；
                 * 明文 http（转码产物）直接交给播放器在线播 */
                g_app.GetLogger().Log("边下边播：已缓存 %ld KB，改用在线流播放",
                                      done / 1024L);
                g_app.Sink()->OnPlayMedia(s_stream_url);
            } else {
                g_app.GetLogger().Log("边下边播：已缓存 %ld KB，启动播放器",
                                      done / 1024L);
                g_app.Sink()->OnPlayMedia(s_stream_path);
            }
        }
    }
    /* 日志按 MB 节流，避免刷屏 */
    if (mb != last_mb) {
        last_mb = mb;
        if (total > 0) {
            g_app.GetLogger().Log("下载中 %ld KB / %ld KB",
                                  done / 1024L, total / 1024L);
        } else {
            g_app.GetLogger().Log("下载中 %ld KB", done / 1024L);
        }
    }
}

/* ---------------- 播放调用（ShellExecute 交给系统播放器） ---------------- */

void PlayService::PlayFile(const char *path)
{
    SHELLEXECUTEINFO sei;
    wchar_t wpath[BC_PATH_LEN + 4];
    BOOL ok;

    wpath[0] = L'\0';
    MultiByteToWideChar(CP_ACP, 0, path, -1, wpath,
                        (int)(sizeof(wpath) / sizeof(wpath[0])));

    /* 1) 交给系统关联的播放器（open 动词） */
    memset(&sei, 0, sizeof(sei));
    sei.cbSize = sizeof(sei);
    sei.fMask = SEE_MASK_FLAG_NO_UI;
    sei.hwnd = GetForegroundWindow();
    sei.lpVerb = L"open";
    sei.lpFile = wpath;
    sei.lpParameters = NULL;
    sei.lpDirectory = NULL;
    sei.nShow = SW_SHOWNORMAL;
    ok = ShellExecuteEx(&sei);

    if (!ok || (long)sei.hInstApp <= 32) {
        /* 有些 ROM 不认 "open" 动词，试默认动词 */
        sei.lpVerb = NULL;
        sei.hInstApp = 0;
        ok = ShellExecuteEx(&sei);
    }
    if (ok && (long)sei.hInstApp > 32) {
        g_app.GetLogger().Log("已交给系统播放器");
        g_app.GetLogger().Status("已交给系统播放器");
        g_app.SetVideoOpened(1);
        return;
    }
    g_app.GetLogger().Log("系统关联打开失败：hInstApp=%ld err=%lu，改直接调播放器",
                          (long)sei.hInstApp, (unsigned long)GetLastError());

    /* 2) 兜底：直接调常见播放器 exe */
    {
        static const wchar_t *kPlayers[] = {
            L"\\Windows\\wmplayer.exe",
            L"\\Windows\\Player.exe",
            L"\\Program Files\\Windows Media Player\\wmplayer.exe",
            L"\\Windows\\WMP\\wmplayer.exe",
            NULL
        };
        int i;
        for (i = 0; kPlayers[i] != NULL; i++) {
            wchar_t cmd[BC_PATH_LEN + 16];
            STARTUPINFOW si;
            PROCESS_INFORMATION pi;

            if (GetFileAttributesW(kPlayers[i]) == (DWORD)-1) {
                continue;   /* 该路径没有 */
            }
            wsprintfW(cmd, L"\"%s\"", wpath);
            memset(&si, 0, sizeof(si));
            si.cb = sizeof(si);
            if (CreateProcessW(kPlayers[i], cmd, NULL, NULL, FALSE, 0,
                               NULL, NULL, &si, &pi)) {
                CloseHandle(pi.hProcess);
                CloseHandle(pi.hThread);
                g_app.GetLogger().Log("已用 %ls 打开", kPlayers[i]);
                g_app.GetLogger().Status("已用系统播放器打开");
                g_app.SetVideoOpened(1);
                return;
            }
            g_app.GetLogger().Log("调用 %ls 失败 err=%lu",
                                  kPlayers[i], (unsigned long)GetLastError());
        }
    }

    /* 3) 最后兜底：扫目录找疑似播放器 exe，并把匹配项记进日志 */
    {
        static const wchar_t *kDirs[] = {
            L"\\Windows", L"\\Program Files", L"\\Program Files\\Multimedia",
            NULL
        };
        static const wchar_t *kKey[] = {
            L"play", L"media", L"video", L"movie", L"player",
            L"wmp", L"music", L"sound", L"mci", L"mz", NULL
        };
        int d;

        for (d = 0; kDirs[d] != NULL; d++) {
            WIN32_FIND_DATAW fd;
            HANDLE hFind;
            wchar_t pat[BC_PATH_LEN + 16];

            wsprintfW(pat, L"%s\\*.exe", kDirs[d]);
            hFind = FindFirstFileW(pat, &fd);
            if (hFind == INVALID_HANDLE_VALUE) {
                g_app.GetLogger().Log("扫描 %ls 无结果 err=%lu",
                                      kDirs[d], (unsigned long)GetLastError());
                continue;
            }
            do {
                wchar_t lower[BC_PATH_LEN];
                wchar_t full[BC_PATH_LEN + 16];
                wchar_t cmd[BC_PATH_LEN + 16];
                STARTUPINFOW si;
                PROCESS_INFORMATION pi;
                int k, n;
                bool hit = false;

                if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                    continue;
                }
                /* 文件名转小写，便于关键字匹配 */
                n = 0;
                while (n < (int)(sizeof(lower) / sizeof(lower[0])) - 1 &&
                       fd.cFileName[n] != L'\0') {
                    wchar_t c = fd.cFileName[n];
                    if (c >= L'A' && c <= L'Z') {
                        c = (wchar_t)(c - L'A' + L'a');
                    }
                    lower[n] = c;
                    n++;
                }
                lower[n] = L'\0';
                for (k = 0; kKey[k] != NULL; k++) {
                    if (wcsstr(lower, kKey[k]) != NULL) {
                        hit = true;
                        break;
                    }
                }
                if (!hit) {
                    continue;
                }
                wsprintfW(full, L"%s\\%s", kDirs[d], fd.cFileName);
                g_app.GetLogger().Log("疑似播放器：%ls", full);

                wsprintfW(cmd, L"\"%s\"", wpath);
                memset(&si, 0, sizeof(si));
                si.cb = sizeof(si);
                if (CreateProcessW(full, cmd, NULL, NULL, FALSE, 0,
                                   NULL, NULL, &si, &pi)) {
                    CloseHandle(pi.hProcess);
                    CloseHandle(pi.hThread);
                    g_app.GetLogger().Log("已用 %ls 打开", full);
                    g_app.GetLogger().Status("已用系统播放器打开");
                    g_app.SetVideoOpened(1);
                    FindClose(hFind);
                    return;
                }
            } while (FindNextFileW(hFind, &fd));
            FindClose(hFind);
        }
    }
    g_app.GetLogger().Status("打开失败，请手动打开：%s", path);
}

/* ---------------- 主流程 ---------------- */

/* 后台任务参数 */
struct PlayJobArg
{
    int index;
    int forceTranscode;
};

/* UI 线程入口：校验后把全部网络/转码工作丢给后台线程 */
void PlayService::PlaySelected(int index, int forceTranscode)
{
    PlayJobArg *a;

    if (g_app.IsBusy()) {
        return;
    }
    if (index < 0 || index >= g_app.ItemCount()) {
        g_app.GetLogger().Log("请先在列表里选中一条");
        return;
    }
    a = (PlayJobArg *)malloc(sizeof(PlayJobArg));
    if (a == NULL) {
        return;
    }
    a->index = index;
    a->forceTranscode = forceTranscode;
    g_app.ClearCancel();
    g_app.SetBusy(1);
    g_app.RunAsync(PlayJob, a);
}

/* 用户点了加载页返回：放弃本次播放。返回 1 表示已取消（调用方应 return）。 */
static int play_cancelled(void)
{
    if (!g_app.IsCancelled()) {
        return 0;
    }
    g_app.GetLogger().Log("用户取消播放");
    if (g_app.Sink() != NULL) {
        g_app.Sink()->OnLoading(NULL);
    }
    g_app.SetBusy(0);
    return 1;
}

/* 出错收尾：撤掉加载层（会同时转回竖屏）并解除忙态 */
static void play_fail(void)
{
    if (g_app.Sink() != NULL) {
        g_app.Sink()->OnLoading(NULL);
    }
    g_app.SetBusy(0);
}

/* 播放前装载弹幕（把加载层切到「装填弹幕中…」） */
static void play_load_danmaku(const char *cid)
{
    BcDanmaku *dms = NULL;
    char dmerr[160];
    int dmn;

    if (g_app.Sink() != NULL) {
        g_app.Sink()->OnLoading("装填弹幕中…");
    }
    dmerr[0] = '\0';
    dmn = bc_danmaku_load(cid, &dms, dmerr, (int)sizeof(dmerr));
    if (dmn > 0) {
        g_app.GetLogger().Log("弹幕：共 %d 条", dmn);
        g_app.SetDanmaku(dms, dmn);
    } else {
        g_app.GetLogger().Log("弹幕拉取失败：%s", dmerr);
    }
}

/* 后台线程：pagelist -> playurl -> 探测 -> 下载 -> 交给播放器 */
void PlayService::PlayJob(void *arg)
{
    PlayJobArg *a = (PlayJobArg *)arg;
    const BcItem *items = g_app.Items();
    const char *bvid;
    BcConfig *cfg = &g_app.Cfg();
    char *body = NULL;
    int len = 0;
    char err[256];
    char cid[32];
    char dst[260];
    char dl_url[BC_URL_LEN];
    static char media_urls[BC_MAX_MEDIA_URLS][BC_URL_LEN];
    int media_count;
    long total = 0;
    int i;
    int chosen;
    int rc;
    const char *ext;
    int use_trans;
    int index = a->index;
    int forceTranscode = a->forceTranscode;

    free(a);
    bvid = items[index].bvid;
    use_trans = (cfg->transcode || forceTranscode) ? 1 : 0;
    g_app.GetLogger().Log("播放：%s（%s）%s", items[index].title, bvid,
                          use_trans ? "[转码 H.264 Baseline]" : "[原片]");
    g_app.GetLogger().Status("正在解析 %s 的播放地址…", bvid);
    if (g_app.Sink() != NULL) {
        g_app.Sink()->OnLoading("获取播放地址…");
    }

    err[0] = '\0';

    /* 1) pagelist -> cid */
    cid[0] = '\0';
    if (bili_pagelist(cfg, bvid, &body, &len, err, (int)sizeof(err)) != 0) {
        g_app.GetLogger().Log("pagelist 失败：%s", err);
        g_app.GetLogger().Status("取分P信息失败：%s", err);
        play_fail();
        return;
    }
    if (!bili_pick_number(body, "cid", cid, (int)sizeof(cid))) {
        g_app.GetLogger().Log("pagelist 无 cid：%.160s", body);
        g_app.GetLogger().Status("取分P信息失败（响应里没有 cid）");
        free(body);
        play_fail();
        return;
    }
    free(body);
    body = NULL;
    g_app.GetLogger().Log("cid=%s", cid);
    /* 记下在播视频（供观看历史上报用：上报线程靠它取 bvid/cid） */
    g_app.SetPlayingInfo(bvid, cid);

    if (play_cancelled()) {
        return;
    }

    /* 2) playurl -> 候选直链（主地址 + backup_url 镜像） */
    if (bili_playurl(cfg, bvid, cid, &body, &len, err, (int)sizeof(err)) != 0) {
        g_app.GetLogger().Log("playurl 失败：%s", err);
        g_app.GetLogger().Status("取播放地址失败：%s", err);
        play_fail();
        return;
    }
    media_count = bili_parse_media_urls(body, media_urls, BC_MAX_MEDIA_URLS);
    free(body);
    body = NULL;
    if (media_count <= 0) {
        g_app.GetLogger().Log("playurl 里没有可用直链");
        g_app.GetLogger().Status("没有拿到可用的播放地址");
        play_fail();
        return;
    }
    for (i = 0; i < media_count; i++) {
        g_app.GetLogger().Log("候选 %d/%d：%.100s",
                              i + 1, media_count, media_urls[i]);
    }

    /* 3) 逐个探测（只取头 1KB，便宜），挑第一个能用的镜像。
     *    探测用明文 http 的副本，原 https 地址留着给服务器转码用。 */
    chosen = -1;
    g_app.GetLogger().Status("正在测试 %d 个 CDN 镜像…", media_count);
    for (i = 0; i < media_count; i++) {
        static char probe_url[BC_URL_LEN];
        char *pbody = NULL;
        int plen = 0;
        int st = 0;

        str_copy(probe_url, (int)sizeof(probe_url), media_urls[i]);
        url_downgrade_https(probe_url);
        err[0] = '\0';
        rc = http_get_url(probe_url,
                          "Range: bytes=0-1023\r\nReferer: https://www.bilibili.com/\r\n",
                          &pbody, &plen, &st, err, (int)sizeof(err));
        if (pbody != NULL) {
            free(pbody);
        }
        if (rc == HTTP_OK && (st == 200 || st == 206)) {
            g_app.GetLogger().Log("候选 %d 直连可用：HTTP %d，%d 字节",
                                  i + 1, st, plen);
            chosen = i;
            break;
        }
        if (rc == HTTP_OK) {
            g_app.GetLogger().Log("候选 %d 直连不可用：HTTP %d", i + 1, st);
        } else {
            g_app.GetLogger().Log("候选 %d 直连不可用：%s", i + 1, err);
        }
    }

    /* 4) 要转码就请服务器转成 H.264 Baseline，否则直下原片 */
    dl_url[0] = '\0';
    ext = ".mp4";
    if (use_trans) {
        char name[64];
        sprintf(name, "%s.mp4", bvid);
        g_app.GetLogger().Log("请服务器转码成 %s（可能要等几分钟）…", FmtLabel());
        g_app.GetLogger().Status("服务器转码中…（最长几分钟，请勿退出）");
        for (i = 0; i < media_count; i++) {
            g_app.GetLogger().Log("  提交候选 %d/%d 转码…", i + 1, media_count);
            err[0] = '\0';
            if (bili_transcode(cfg, media_urls[i], name,
                               dl_url, (int)sizeof(dl_url),
                               err, (int)sizeof(err)) == 0) {
                const char *ue = UrlExt(dl_url);
                if (ue != NULL) {
                    ext = ue;
                }
                g_app.GetLogger().Log("  转码成功：%.120s", dl_url);
                break;
            }
            g_app.GetLogger().Log("  候选 %d 转码失败：%s", i + 1, err);
            dl_url[0] = '\0';
        }
        if (dl_url[0] == '\0') {
            g_app.GetLogger().Log("转码都没成功，改成直接下原片");
        }
    }
    if (dl_url[0] == '\0') {
        if (chosen < 0) {
            g_app.GetLogger().Log("所有镜像都不能直连，也没转码成功");
            g_app.GetLogger().Status("失败了：没有可用的下载地址");
            play_fail();
            return;
        }
        g_app.GetLogger().Log("改成直接下原片（High Profile 可能打不开）");
        str_copy(dl_url, (int)sizeof(dl_url), media_urls[chosen]);
        ext = ".mp4";
    }

    /* 4.5) 没开离线：转码产物是明文 http，直接在线流播，不落盘 */
    if (!cfg->offline && strncmp(dl_url, "http://", 7) == 0) {
        g_app.GetLogger().Log("在线播放（不下载）：%.120s", dl_url);
        g_app.GetLogger().Status("在线播放中…");
        /* 加载层文字：正在加载视频… -> 装填弹幕中…（顺序别反） */
        if (g_app.Sink() != NULL) {
            g_app.Sink()->OnLoading("正在加载视频…");
        }
        play_load_danmaku(cid);
        if (play_cancelled()) {
            return;
        }
        g_app.SetBusy(0);
        if (g_app.Sink() != NULL) {
            g_app.Sink()->OnPlayMedia(dl_url);
        } else {
            PlayFile(dl_url);
        }
        return;
    }

    /* 5) 下载到数据目录 */
    str_copy(dst, (int)sizeof(dst), cfg->data_dir);
    str_append(dst, (int)sizeof(dst), "biliclassic_m8_");
    str_append(dst, (int)sizeof(dst), bvid);
    str_append(dst, (int)sizeof(dst), ext);

    g_app.GetLogger().Log("开始下载（边下边播）：%s", dst);
    g_app.GetLogger().Status("准备下载…");
    /* 加载层文字：正在加载视频… -> 装填弹幕中… */
    if (g_app.Sink() != NULL) {
        g_app.Sink()->OnLoading("正在加载视频…");
    }
    play_load_danmaku(cid);
    if (play_cancelled()) {
        return;
    }
    /* 边下边播必须顺序单连接，否则文件有空洞没法放 */
    str_copy(s_stream_path, (int)sizeof(s_stream_path), dst);
    /* 明文 http（转码产物）可以直接交给播放器在线播，绕开文件占用问题 */
    if (strncmp(dl_url, "http://", 7) == 0) {
        str_copy(s_stream_url, (int)sizeof(s_stream_url), dl_url);
    } else {
        s_stream_url[0] = '\0';
    }
    s_stream_started = 0;
    s_stream_on = 1;
    g_app.SetVideoOpened(0);
    http_set_conns(1);
    err[0] = '\0';
    if (bili_download(cfg, dl_url, dst, OnProgress, &total,
                      err, (int)sizeof(err)) != 0) {
        http_set_conns(cfg->conns);
        s_stream_on = 0;
        g_app.GetLogger().Log("下载失败：%s", err);
        g_app.GetLogger().Status("下载失败：%s", err);
        play_fail();
        return;
    }
    http_set_conns(cfg->conns);
    s_stream_on = 0;
    g_app.GetLogger().Log("下载完成：%s（%ld KB）", dst, total / 1024L);
    g_app.GetLogger().Log("文件实际格式：%s", bc_sniff_media(dst));
    g_app.SetLastFile(dst);
    g_app.SetBusy(0);

    g_app.GetLogger().Log("下载完成，准备播放（边下边播=%d 已打开=%d）",
                          s_stream_started, g_app.VideoOpened());
    if (s_stream_started && g_app.VideoOpened()) {
        g_app.GetLogger().Status("边下边播：后台已下载完");
    } else if (g_app.Sink() != NULL) {
        g_app.Sink()->OnPlayMedia(dst);
    } else {
        PlayFile(dst);
    }
}

void PlayService::PlayLast()
{
    const char *last = g_app.LastFile();

    if (last[0] == '\0') {
        g_app.GetLogger().Log("还没有下载过视频");
        g_app.GetLogger().Status("还没有下载过视频");
        return;
    }
    g_app.GetLogger().Log("播放上次下载的：%s", last);
    if (g_app.Sink() != NULL) {
        g_app.Sink()->OnLoading("正在加载视频…");
        g_app.Sink()->OnPlayMedia(last);
    } else {
        PlayFile(last);
    }
}

/* ---------------- 设置变更 ---------------- */

void PlayService::SetTranscode(int on)
{
    BcConfig *cfg = &g_app.Cfg();

    cfg->transcode = on ? 1 : 0;
    bili_save_config(cfg);
    g_app.GetLogger().Log("转码：%s（%s）",
                          cfg->transcode ? "开" : "关", FmtLabel());
    g_app.GetLogger().Status("转码：%s", cfg->transcode ? "开" : "关");
}

void PlayService::SetConns(int n)
{
    BcConfig *cfg = &g_app.Cfg();

    if (n < 1) {
        n = 1;
    }
    if (n > 8) {
        n = 8;
    }
    cfg->conns = n;
    http_set_conns(n);
    bili_save_config(cfg);
    g_app.GetLogger().Log("下载连接数：%d", n);
    g_app.GetLogger().Status("下载连接数：%d", n);
}

void PlayService::SetOffline(int on)
{
    BcConfig *cfg = &g_app.Cfg();

    cfg->offline = on ? 1 : 0;
    bili_save_config(cfg);
    g_app.GetLogger().Log("离线播放（下载到本地）：%s", cfg->offline ? "开" : "关");
    g_app.GetLogger().Status("离线播放（下载到本地）：%s",
                             cfg->offline ? "开" : "关");
}

void PlayService::SetDanmaku(int on)
{
    BcConfig *cfg = &g_app.Cfg();

    cfg->danmaku = on ? 1 : 0;
    bili_save_config(cfg);
    g_app.GetLogger().Log("弹幕：%s", cfg->danmaku ? "开" : "关");
    g_app.GetLogger().Status("弹幕：%s", cfg->danmaku ? "开" : "关");
}

void PlayService::SetReportHistory(int on)
{
    BcConfig *cfg = &g_app.Cfg();

    cfg->report_history = on ? 1 : 0;
    bili_save_config(cfg);
    g_app.GetLogger().Log("上报观看历史：%s", cfg->report_history ? "开" : "关");
    g_app.GetLogger().Status("上报观看历史：%s", cfg->report_history ? "开" : "关");
}
