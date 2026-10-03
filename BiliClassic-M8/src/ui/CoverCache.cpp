/* =====================================================================
 * CoverCache.cpp - 封面缩略图缓存实现
 * ===================================================================== */
#include "CoverCache.h"
#include "../app/AppContext.h"
#include "../core/http.h"
#include "../core/bc.h"
#include <Mzfc/ImagingHelper.h>
#include <objbase.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef WM_APP_COVER_DONE
#define WM_APP_COVER_DONE (WM_APP + 2)
#endif

#define COVER_W 144
#define COVER_H 81

struct CoverJob
{
    int  index;
    char bvid[BC_BVID_LEN];
    char url[BC_PIC_LEN];
};

/* 已解码封面位图缓存（bvid -> HBITMAP），避免重绘时反复解码 */
struct CoverBmp
{
    char    bvid[BC_BVID_LEN];
    HBITMAP bmp;
};

/* 磁盘上的一个 cover_<bvid>.jpg，用来排序删旧的 */
struct CoverFile
{
    FILETIME t;
    wchar_t  name[160];
};

static CRITICAL_SECTION s_lock;
static int     s_lockInit = 0;
static CoverJob s_jobs[BC_MAX_ITEMS];
static int     s_jobCount = 0;
static HWND    s_wnd = NULL;
static volatile int s_running = 0;
static CoverBmp s_bmps[BC_MAX_ITEMS];
static int     s_bmpCount = 0;

/* 把 //host/... 或 https://host/... 变成明文 http://host/...
 * 并追加 B 站图片处理参数，取小图（144x81），下载/解码都快很多 */
static void to_http_url(const char *in, char *out, int cap)
{
    char tmp[BC_PIC_LEN];
    char *q;

    if (in == NULL) {
        out[0] = '\0';
        return;
    }
    if (strncmp(in, "//", 2) == 0) {
        str_copy(tmp, (int)sizeof(tmp), "http:");
        str_append(tmp, (int)sizeof(tmp), in);
    } else if (strncmp(in, "https://", 8) == 0) {
        str_copy(tmp, (int)sizeof(tmp), "http://");
        str_append(tmp, (int)sizeof(tmp), in + 8);
    } else {
        str_copy(tmp, (int)sizeof(tmp), in);
    }
    /* 在查询串之前插入 @144w_81h.jpg（B 站图片云支持裁剪/缩放） */
    q = strchr(tmp, '?');
    if (q != NULL) {
        *q = '\0';
    }
    str_copy(out, cap, tmp);
    if (strstr(tmp, "@") == NULL) {
        str_append(out, cap, "@144w_81h.jpg");
    }
    if (q != NULL) {
        str_append(out, cap, "?");
        str_append(out, cap, q + 1);
    }
}

/* ---- 磁盘封面清理 ----
 * 每张图 3~8KB，一直翻页能堆出上千个 cover_<bvid>.jpg，从不删除，
 * M8 的存储很快就满。保留最近 COVER_DISK_KEEP 个，其余按修改时间删掉。
 * 只在封面下载线程里调，启动时下载还没开始，没有并发写文件的问题。 */
/* 保留最近 200 张（约 700KB，够往回翻 10 页不用重新下） */
#define COVER_DISK_KEEP 200

static int cover_ft_newest_first(const void *a, const void *b)
{
    return CompareFileTime(&((const CoverFile *)b)->t,
                           &((const CoverFile *)a)->t);
}

static void cover_prune(void)
{
    const char *dir = g_app.Cfg().data_dir;
    wchar_t wdir[300];
    wchar_t pattern[320];
    wchar_t full[500];
    WIN32_FIND_DATAW fd;
    HANDLE h;
    CoverFile *arr = NULL;
    int cap = 0;
    int n = 0;
    int len;
    int i;

    wdir[0] = L'\0';
    MultiByteToWideChar(CP_ACP, 0,
                        (dir != NULL && dir[0] != '\0') ? dir : "", -1,
                        wdir, (int)(sizeof(wdir) / sizeof(wdir[0])));
    len = (int)wcslen(wdir);
    if (len == 0) {
        return;
    }
    if (wdir[len - 1] != L'\\' && wdir[len - 1] != L'/') {
        wdir[len++] = L'\\';
        wdir[len] = L'\0';
    }
    /* wdir <= 299 字符 + "cover_*.jpg" 11 字符 < 320，不会溢出 */
    wcsncpy(pattern, wdir, sizeof(pattern) / sizeof(pattern[0]) - 1);
    pattern[sizeof(pattern) / sizeof(pattern[0]) - 1] = L'\0';
    wcscat(pattern, L"cover_*.jpg");

    h = FindFirstFileW(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) {
        return;
    }
    do {
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            continue;
        }
        if (n >= cap) {
            int ncap = (cap > 0) ? cap * 2 : 64;
            CoverFile *na = (CoverFile *)realloc(arr,
                    sizeof(CoverFile) * (size_t)ncap);
            if (na == NULL) {
                break;
            }
            arr = na;
            cap = ncap;
        }
        arr[n].t = fd.ftLastWriteTime;
        wcsncpy(arr[n].name, fd.cFileName,
                sizeof(arr[n].name) / sizeof(arr[n].name[0]) - 1);
        arr[n].name[sizeof(arr[n].name) / sizeof(arr[n].name[0]) - 1] = L'\0';
        n++;
    } while (FindNextFileW(h, &fd));
    FindClose(h);

    if (n > COVER_DISK_KEEP && arr != NULL) {
        qsort(arr, (size_t)n, sizeof(CoverFile), cover_ft_newest_first);
        for (i = COVER_DISK_KEEP; i < n; i++) {
            wcsncpy(full, wdir, sizeof(full) / sizeof(full[0]) - 1);
            full[sizeof(full) / sizeof(full[0]) - 1] = L'\0';
            wcscat(full, arr[i].name);   /* wdir + name < 500 */
            DeleteFileW(full);
        }
    }
    free(arr);
}

void CoverCache::PathFor(const char *bvid, char *out, int cap, int *exists)
{
    const char *dir = g_app.Cfg().data_dir;
    char path[300];

    path[0] = '\0';
    str_copy(path, (int)sizeof(path), (dir != NULL && dir[0] != '\0') ?
             dir : "");
    str_append(path, (int)sizeof(path), "cover_");
    str_append(path, (int)sizeof(path), bvid);
    str_append(path, (int)sizeof(path), ".jpg");
    str_copy(out, cap, path);

    if (exists != NULL) {
        wchar_t wpath[300];
        wpath[0] = L'\0';
        MultiByteToWideChar(CP_ACP, 0, path, -1, wpath,
                            (int)(sizeof(wpath) / sizeof(wpath[0])));
        *exists = (GetFileAttributesW(wpath) != (DWORD)-1) ? 1 : 0;
    }
}

void CoverCache::Reset()
{
    Trim(0);
    if (s_lockInit) {
        EnterCriticalSection(&s_lock);
        s_jobCount = 0;
        LeaveCriticalSection(&s_lock);
    }
}

void CoverCache::ClearAll()
{
    const char *dir = g_app.Cfg().data_dir;
    wchar_t wdir[300];
    wchar_t pattern[320];
    wchar_t full[520];
    WIN32_FIND_DATAW fd;
    HANDLE h;
    int len;

    /* 先清内存里的解码位图（UI 线程） */
    Trim(0);
    if (s_lockInit) {
        EnterCriticalSection(&s_lock);
        s_jobCount = 0;
        LeaveCriticalSection(&s_lock);
    }

    wdir[0] = L'\0';
    MultiByteToWideChar(CP_ACP, 0,
                        (dir != NULL && dir[0] != '\0') ? dir : "", -1,
                        wdir, (int)(sizeof(wdir) / sizeof(wdir[0])));
    len = (int)wcslen(wdir);
    if (len == 0) {
        return;
    }
    if (wdir[len - 1] != L'\\' && wdir[len - 1] != L'/') {
        wdir[len++] = L'\\';
        wdir[len] = L'\0';
    }
    wcsncpy(pattern, wdir, sizeof(pattern) / sizeof(pattern[0]) - 1);
    pattern[sizeof(pattern) / sizeof(pattern[0]) - 1] = L'\0';
    wcscat(pattern, L"cover_*.jpg");

    h = FindFirstFileW(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) {
        return;
    }
    do {
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            continue;
        }
        wcsncpy(full, wdir, sizeof(full) / sizeof(full[0]) - 1);
        full[sizeof(full) / sizeof(full[0]) - 1] = L'\0';
        wcscat(full, fd.cFileName);
        DeleteFileW(full);
    } while (FindNextFileW(h, &fd));
    FindClose(h);
}

/* 只保留最近 keep 张位图，丢掉更早的。必须在 UI 线程调用：
 * 删除时不可能正被 DrawItem 用着，也不会和下载线程的 put 抢锁。 */
void CoverCache::Trim(int keep)
{
    int i;
    int drop;

    if (keep < 0) {
        keep = 0;
    }
    if (!s_lockInit) {
        s_bmpCount = 0;
        return;
    }
    EnterCriticalSection(&s_lock);
    drop = s_bmpCount - keep;
    if (drop > s_bmpCount) {
        drop = s_bmpCount;
    }
    for (i = 0; i < drop; i++) {
        if (s_bmps[i].bmp != NULL) {
            DeleteObject(s_bmps[i].bmp);
            s_bmps[i].bmp = NULL;
        }
    }
    if (drop > 0) {
        memmove(&s_bmps[0], &s_bmps[drop],
                sizeof(CoverBmp) * (size_t)(s_bmpCount - drop));
        s_bmpCount -= drop;
    }
    LeaveCriticalSection(&s_lock);
}

/* 解码封面文件 -> 144x81 位图并缓存。只在主线程调用。 */
static HBITMAP cover_decode(const char *bvid, const char *pathGbk)
{
    wchar_t wpath[300];
    HDC screen;
    HDC mdc;
    HBITMAP bmp;
    HGDIOBJ old;
    RECT rc;
    struct {
        BITMAPINFOHEADER hdr;
    } bi;
    void *bits = NULL;

    wpath[0] = L'\0';
    MultiByteToWideChar(CP_ACP, 0, pathGbk, -1, wpath,
                        (int)(sizeof(wpath) / sizeof(wpath[0])));
    screen = GetDC(NULL);
    memset(&bi, 0, sizeof(bi));
    bi.hdr.biSize = sizeof(BITMAPINFOHEADER);
    bi.hdr.biWidth = COVER_W;
    bi.hdr.biHeight = -COVER_H;      /* 顶朝下 */
    bi.hdr.biPlanes = 1;
    bi.hdr.biBitCount = 24;
    bi.hdr.biCompression = BI_RGB;
    bmp = CreateDIBSection(screen, (BITMAPINFO *)&bi, DIB_RGB_COLORS,
                           &bits, NULL, 0);
    mdc = CreateCompatibleDC(screen);
    if (bmp == NULL) {
        DeleteDC(mdc);
        ReleaseDC(NULL, screen);
        return NULL;
    }
    old = SelectObject(mdc, bmp);
    rc.left = 0;
    rc.top = 0;
    rc.right = COVER_W;
    rc.bottom = COVER_H;
    {
        HBRUSH b = CreateSolidBrush(RGB(214, 214, 214));
        FillRect(mdc, &rc, b);
        DeleteObject(b);
    }
    ImagingHelper::DrawImage(mdc, &rc, wpath, true, false);
    SelectObject(mdc, old);
    DeleteDC(mdc);
    ReleaseDC(NULL, screen);
    (void)bvid;
    return bmp;
}

/* 位图缓存读/写（加锁；下载线程写，UI 线程读） */
static HBITMAP cover_cache_get(const char *bvid)
{
    int i;
    HBITMAP r = NULL;

    if (!s_lockInit) {
        return NULL;
    }
    EnterCriticalSection(&s_lock);
    for (i = 0; i < s_bmpCount; i++) {
        if (strcmp(s_bmps[i].bvid, bvid) == 0) {
            r = s_bmps[i].bmp;
            break;
        }
    }
    LeaveCriticalSection(&s_lock);
    return r;
}

static void cover_cache_put(const char *bvid, HBITMAP bmp)
{
    int i;

    if (bmp == NULL) {
        return;
    }
    if (!s_lockInit) {
        DeleteObject(bmp);
        return;
    }
    EnterCriticalSection(&s_lock);
    for (i = 0; i < s_bmpCount; i++) {
        if (strcmp(s_bmps[i].bvid, bvid) == 0) {
            LeaveCriticalSection(&s_lock);
            DeleteObject(bmp);
            return;
        }
    }
    if (s_bmpCount < BC_MAX_ITEMS) {
        str_copy(s_bmps[s_bmpCount].bvid, BC_BVID_LEN, bvid);
        s_bmps[s_bmpCount].bmp = bmp;
        s_bmpCount++;
        bmp = NULL;
    }
    LeaveCriticalSection(&s_lock);
    if (bmp != NULL) {
        DeleteObject(bmp);
    }
}

HBITMAP CoverCache::GetBitmap(const char *bvid)
{
    /* 只查缓存，绝不在 UI 线程解码（解码在下载线程做） */
    return cover_cache_get(bvid);
}

void CoverCache::Request(HWND wnd, int index, const char *picUrl,
                         const char *bvid)
{
    if (picUrl == NULL || picUrl[0] == '\0' ||
        bvid == NULL || bvid[0] == '\0') {
        return;
    }
    /* 已解码在缓存里就跳过 */
    if (cover_cache_get(bvid) != NULL) {
        return;
    }
    if (!s_lockInit) {
        InitializeCriticalSection(&s_lock);
        s_lockInit = 1;
    }
    EnterCriticalSection(&s_lock);
    s_wnd = wnd;
    if (s_jobCount >= BC_MAX_ITEMS) {
        /* 队列满了 */
    } else {
        int k;

        /* 同一个封面别排两次队，否则一次重绘能把队列刷爆 */
        for (k = 0; k < s_jobCount; k++) {
            if (strcmp(s_jobs[k].bvid, bvid) == 0) {
                break;
            }
        }
        if (k >= s_jobCount) {
            CoverJob *j = &s_jobs[s_jobCount++];

            j->index = index;
            str_copy(j->bvid, (int)sizeof(j->bvid), bvid);
            to_http_url(picUrl, j->url, (int)sizeof(j->url));
        }
    }
    if (!s_running) {
        DWORD tid;

        s_running = 1;
        if (CreateThread(NULL, 512 * 1024, CoverCache::ThreadProc, NULL, 0, &tid)
            == NULL) {
            s_running = 0;
        }
    }
    LeaveCriticalSection(&s_lock);
}

DWORD WINAPI CoverCache::ThreadProc(LPVOID param)
{
    int newCount = 0;
    long newBytes = 0;
    int failCount = 0;
    char lastErr[128];

    (void)param;
    lastErr[0] = '\0';
    CoInitializeEx(NULL, COINIT_MULTITHREADED);
    /* 只静音本线程的 http 日志：每张图都会打 send/read headers 两三行，
     * 一页 20 张就是 60 行，日志文件很快被刷爆 */
    http_set_quiet(1);
    /* 下载开始前先把过期的 cover_*.jpg 清一批（此时还没人写文件） */
    cover_prune();
    for (;;) {
        CoverJob job;
        HWND wnd;
        int have = 0;

        EnterCriticalSection(&s_lock);
        if (s_jobCount > 0) {
            job = s_jobs[0];
            memmove(&s_jobs[0], &s_jobs[1],
                    sizeof(CoverJob) * (size_t)(s_jobCount - 1));
            s_jobCount--;
            have = 1;
        } else {
            s_running = 0;
        }
        wnd = s_wnd;
        LeaveCriticalSection(&s_lock);
        if (!have) {
            break;
        }

        {
            char path[300];
            char err[128];
            long total = 0;
            int exists = 0;
            int rc = HTTP_OK;

            PathFor(job.bvid, path, (int)sizeof(path), &exists);
            if (!exists) {
                err[0] = '\0';
                rc = http_get_url_to_file(job.url,
                        "Referer: https://www.bilibili.com/\r\n",
                        path, NULL, &total, err, (int)sizeof(err));
                if (rc != HTTP_OK || total <= 0) {
                    wchar_t wpath[300];

                    wpath[0] = L'\0';
                    MultiByteToWideChar(CP_ACP, 0, path, -1, wpath,
                                        (int)(sizeof(wpath) / sizeof(wpath[0])));
                    DeleteFileW(wpath);
                    failCount++;
                    str_copy(lastErr, (int)sizeof(lastErr), err);
                    continue;
                }
                newCount++;
                newBytes += total;
            }
            /* 在下载线程解码成位图缓存，UI 线程只取用（避免滚动时解码卡顿） */
            {
                HBITMAP b = cover_decode(job.bvid, path);

                if (b != NULL) {
                    cover_cache_put(job.bvid, b);
                    if (wnd != NULL) {
                        PostMessage(wnd, WM_APP_COVER_DONE,
                                    (WPARAM)job.index, 0);
                    }
                }
            }
        }
    }
    /* 一批下完只汇报一次，不逐张刷屏 */
    if (newCount > 0) {
        g_app.GetLogger().Log("封面缓存：本次新增 %d 个（%ld KB）",
                              newCount, newBytes / 1024L);
    }
    if (failCount > 0) {
        g_app.GetLogger().Log("封面缓存：本次失败 %d 个（%s）",
                              failCount, lastErr);
    }
    http_set_quiet(0);
    CoUninitialize();
    return 0;
}
