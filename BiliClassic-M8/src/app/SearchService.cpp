/* =====================================================================
 * SearchService.cpp - 搜索 / 热门 / 观看历史 / 收藏夹
 * ===================================================================== */
#include "SearchService.h"
#include "AppContext.h"
#include "../core/bili.h"
#include "../core/login.h"
#include "../core/util.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int         SearchService::s_kind      = QK_NONE;
int         SearchService::s_page      = 1;
int         SearchService::s_ps        = 20;
int         SearchService::s_hasMore   = 0;
int         SearchService::s_append    = 0;
char        SearchService::s_keyword[600] = { 0 };
long        SearchService::s_fid       = 0;
long        SearchService::s_curMax    = 0;
long        SearchService::s_curViewAt = 0;

static const char *kind_name(int kind)
{
    switch (kind) {
    case QK_SEARCH:     return "搜索";
    case QK_POPULAR:    return "热门";
    case QK_HISTORY:    return "观看历史";
    case QK_FAV_FOLDER: return "收藏夹";
    case QK_FAV_VIDEOS: return "收藏视频";
    default:            return "列表";
    }
}

void SearchService::ResetQuery(int kind, int ps)
{
    s_kind    = kind;
    s_page    = 1;
    s_ps      = ps;
    s_hasMore = 1;
    s_append  = 0;
    s_curMax    = 0;
    s_curViewAt = 0;
}

/* 只把本页解析出的 n 条交给 UI；append=1 表示接在现有行后面 */
void SearchService::ShowItems(const char *json, const char *what,
                              const char *pic_field, int append)
{
    int base = 0;
    int n = 0;

    if (append) {
        base = g_app.ItemCount();
    } else {
        g_app.ItemCount() = 0;
    }
    if (base < BC_MAX_ITEMS) {
        n = bili_parse_items(json, g_app.Items() + base,
                             BC_MAX_ITEMS - base, pic_field);
    }
    g_app.ItemCount() = base + n;

    /* 没拉到新的 / 不满一页 / 到达容量 -> 以后不再翻页 */
    if (n <= 0 || n < s_ps || g_app.ItemCount() >= BC_MAX_ITEMS) {
        s_hasMore = 0;
    }

    if (n == 0 && !append) {
        g_app.GetLogger().Log("%s：解析到 0 条（响应 %d 字节，前 160 字：%.160s）",
                              what, (int)strlen(json), json);
        g_app.GetLogger().Status("%s：没有结果", what);
    } else {
        g_app.GetLogger().Log("%s：本页 %d 条，累计 %d%s", what, n,
                              g_app.ItemCount(), append ? "（翻页）" : "");
        g_app.GetLogger().Status("%s：%d 条", what, g_app.ItemCount());
    }

    /* 重复行自检：封面按 bvid 缓存，bvid 撞了就会两张行显示同一张图。
     * 报前 3 组出来，一次日志就能判断是解析串行还是翻页重拉。 */
    {
        int i;
        int dup = 0;
        int total = g_app.ItemCount();

        for (i = 0; i < total - 1 && dup < 3; i++) {
            int j;

            for (j = i + 1; j < total; j++) {
                int sameBv = (g_app.Items()[i].bvid[0] != '\0' &&
                              strcmp(g_app.Items()[i].bvid,
                                     g_app.Items()[j].bvid) == 0);
                int samePic = (g_app.Items()[i].pic[0] != '\0' &&
                               strcmp(g_app.Items()[i].pic,
                                      g_app.Items()[j].pic) == 0);

                if (sameBv || samePic) {
                    g_app.GetLogger().Log(
                        "%s 重复 %d/%d bvid=%s pic=%s title=%.36s|%.36s",
                        what, i, j,
                        sameBv ? g_app.Items()[i].bvid : "-",
                        samePic ? "同封面" : "-",
                        g_app.Items()[i].title, g_app.Items()[j].title);
                    dup++;
                    break;
                }
            }
        }
        if (dup > 0) {
            g_app.GetLogger().Log("%s：共 %d 组重复（累计 %d 行）",
                                  what, dup, total);
        }
    }

    if (g_app.Sink() != NULL && (n > 0 || !append)) {
        g_app.Sink()->OnItems(g_app.Items() + base, n, what, append);
    }
}

/* ---------------------------- 后台任务 ---------------------------- */

void SearchService::SearchJob(void *arg)
{
    char *keyword = (char *)arg;
    char *body = NULL;
    int len = 0;
    char err[256];
    int append = s_append;

    err[0] = '\0';
    if (bili_search(&g_app.Cfg(), keyword, s_page, &body, &len,
                    err, (int)sizeof(err)) != 0) {
        g_app.GetLogger().Log("搜索失败：%s", err);
        g_app.GetLogger().Status("搜索失败：%s", err);
    } else {
        g_app.GetLogger().Log("搜索响应 %d 字节", len);
        ShowItems(body, "搜索结果", "pic", append);
        free(body);
    }

    free(keyword);
    g_app.SetBusy(0);
}

void SearchService::PopularJob(void *arg)
{
    char *body = NULL;
    int len = 0;
    char err[256];
    int append = s_append;

    (void)arg;
    err[0] = '\0';
    if (bili_popular(&g_app.Cfg(), s_page, &body, &len,
                     err, (int)sizeof(err)) != 0) {
        g_app.GetLogger().Log("热门失败：%s", err);
        g_app.GetLogger().Status("热门获取失败：%s", err);
    } else {
        g_app.GetLogger().Log("热门响应 %d 字节", len);
        ShowItems(body, "热门", "pic", append);
        free(body);
    }

    g_app.SetBusy(0);
}

void SearchService::HistoryJob(void *arg)
{
    char *body = NULL;
    int len = 0;
    char err[256];
    int append = s_append;
    long oldMax = s_curMax;
    long oldView = s_curViewAt;

    (void)arg;
    err[0] = '\0';
    if (bc_login_history(&g_app.Cfg(), s_curMax, s_curViewAt,
                         &body, &len, err, (int)sizeof(err)) != 0) {
        g_app.GetLogger().Log("历史失败：%s", err);
        g_app.GetLogger().Status("取观看历史失败：%s", err);
    } else {
        g_app.GetLogger().Log("历史响应 %d 字节", len);
        ShowItems(body, "观看历史", "cover", append);
        /* 下一页游标 */
        {
            long v = 0;

            if (json_get_int(body, "max", &v)) {
                s_curMax = v;
            }
            if (json_get_int(body, "view_at", &v)) {
                s_curViewAt = v;
            }
        }
        if (s_curMax == oldMax && s_curViewAt == oldView) {
            s_hasMore = 0;   /* 游标没往前动 -> 没有下一页 */
        }
        free(body);
    }

    g_app.SetBusy(0);
}

void SearchService::FavFolderJob(void *arg)
{
    char *body = NULL;
    int len = 0;
    char err[256];
    int n = 0;

    (void)arg;
    err[0] = '\0';
    if (bc_login_favs(&g_app.Cfg(), &body, &len, err, (int)sizeof(err)) != 0) {
        g_app.GetLogger().Log("收藏夹失败：%s", err);
        g_app.GetLogger().Status("取收藏夹失败：%s", err);
    } else {
        g_app.GetLogger().Log("收藏夹响应 %d 字节", len);
        g_app.ItemCount() = 0;
        n = bili_parse_fav_folders(body, g_app.Items(), BC_MAX_ITEMS);
        g_app.ItemCount() = n;
        s_hasMore = 0;   /* 文件夹就这一屏 */

        /* 列表接口不带封面 -> 先走旧接口 getBoxList 一把抓（和安卓版一致），
         * 拿不到的再逐个 /x/space/fav/arc 补。都在后台线程里跑。 */
        if (n > 0) {
            int i;
            int got = 0;
            char *box = NULL;
            int blen = 0;
            char be[128];

            be[0] = '\0';
            g_app.GetLogger().Status("正在取收藏夹封面…");
            if (bc_login_fav_boxlist(&g_app.Cfg(), &box, &blen, be,
                                     (int)sizeof(be)) == 0 && box != NULL) {
                g_app.GetLogger().Log("收藏夹封面：getBoxList %d 字节", blen);
                for (i = 0; i < n; i++) {
                    char pic[BC_PIC_LEN];

                    pic[0] = '\0';
                    if (bc_fav_cover_boxlist(box, g_app.Items()[i].bvid,
                                             pic, (int)sizeof(pic)) == 0 &&
                        pic[0] != '\0') {
                        str_copy(g_app.Items()[i].pic, BC_PIC_LEN, pic);
                        got++;
                    }
                }
                if (got == 0) {
                    g_app.GetLogger().Log("收藏夹封面：getBoxList 没解析出封面 %.300s",
                                          box);
                }
                free(box);
            } else {
                g_app.GetLogger().Log("收藏夹封面：getBoxList 失败 %s", be);
            }

            /* 还缺的逐个补，最多 10 个，免得慢 */
            if (got < n) {
                int tried = 0;

                for (i = 0; i < n && tried < 10; i++) {
                    char pic[BC_PIC_LEN];
                    char ce[128];

                    if (g_app.Items()[i].pic[0] != '\0') {
                        continue;
                    }
                    tried++;
                    pic[0] = '\0';
                    ce[0] = '\0';
                    if (bc_login_fav_cover(&g_app.Cfg(), g_app.Items()[i].bvid,
                                           pic, (int)sizeof(pic), ce,
                                           (int)sizeof(ce)) == 0 &&
                        pic[0] != '\0') {
                        str_copy(g_app.Items()[i].pic, BC_PIC_LEN, pic);
                        got++;
                    } else {
                        g_app.GetLogger().Log("收藏夹封面：%s 失败 %s",
                                              g_app.Items()[i].bvid, ce);
                    }
                }
            }
            g_app.GetLogger().Log("收藏夹封面：取到 %d/%d", got, n);
        }

        g_app.GetLogger().Log("收藏夹：%d 个", n);
        if (n > 0) {
            g_app.GetLogger().Status("收藏夹：%d 个", n);
        } else {
            g_app.GetLogger().Status("没有收藏夹");
        }
        if (g_app.Sink() != NULL) {
            g_app.Sink()->OnItems(g_app.Items(), n, "收藏夹", 0);
        }
        free(body);
    }

    g_app.SetBusy(0);
}

void SearchService::FavVideosJob(void *arg)
{
    char *body = NULL;
    int len = 0;
    char err[256];
    int append = s_append;

    (void)arg;
    err[0] = '\0';
    if (bc_login_fav_videos(&g_app.Cfg(), s_fid, s_page,
                            &body, &len, err, (int)sizeof(err)) != 0) {
        g_app.GetLogger().Log("收藏视频失败：%s", err);
        g_app.GetLogger().Status("取收藏视频失败：%s", err);
    } else {
        g_app.GetLogger().Log("收藏视频响应 %d 字节", len);
        ShowItems(body, "收藏视频", "cover", append);
        free(body);
    }

    g_app.SetBusy(0);
}

/* ---------------------------- 入口 ---------------------------- */

void SearchService::Search(const char *keyword)
{
    char *copy;

    if (g_app.IsBusy()) {
        return;
    }
    if (keyword == NULL || keyword[0] == '\0') {
        g_app.GetLogger().Log("请输入关键词");
        g_app.GetLogger().Status("请先输入搜索关键词");
        return;
    }

    ResetQuery(QK_SEARCH, 20);
    str_copy(s_keyword, (int)sizeof(s_keyword), keyword);

    g_app.GetLogger().Log("搜索：%s", keyword);
    g_app.GetLogger().Status("正在搜索「%s」…", keyword);
    g_app.SetBusy(1);

    copy = (char *)malloc(strlen(keyword) + 1);
    if (copy == NULL) {
        g_app.SetBusy(0);
        return;
    }
    strcpy(copy, keyword);
    g_app.RunAsync(SearchJob, copy);
}

void SearchService::Popular()
{
    if (g_app.IsBusy()) {
        g_app.GetLogger().Log("SearchService::Popular busy 返回");
        return;
    }
    ResetQuery(QK_POPULAR, 20);

    g_app.GetLogger().Log("获取热门（popular）");
    g_app.GetLogger().Status("正在获取热门…");
    g_app.SetBusy(1);
    g_app.RunAsync(PopularJob, NULL);
}

void SearchService::History()
{
    if (g_app.IsBusy()) {
        return;
    }
    if (!bc_login_is_logged_in(&g_app.Cfg())) {
        g_app.GetLogger().Status("请先登录账号");
        return;
    }
    ResetQuery(QK_HISTORY, 30);

    g_app.GetLogger().Log("获取观看历史");
    g_app.GetLogger().Status("正在获取观看历史…");
    g_app.SetBusy(1);
    g_app.RunAsync(HistoryJob, NULL);
}

void SearchService::FavFolders()
{
    if (g_app.IsBusy()) {
        return;
    }
    if (!bc_login_is_logged_in(&g_app.Cfg())) {
        g_app.GetLogger().Status("请先登录账号");
        return;
    }
    ResetQuery(QK_FAV_FOLDER, 30);
    s_hasMore = 0;

    g_app.GetLogger().Log("获取收藏夹");
    g_app.GetLogger().Status("正在获取收藏夹…");
    g_app.SetBusy(1);
    g_app.RunAsync(FavFolderJob, NULL);
}

/* 双击收藏夹文件夹：bvid 里存的就是 media_id */
void SearchService::OpenFolder(int index)
{
    long fid;

    if (g_app.IsBusy()) {
        return;
    }
    if (index < 0 || index >= g_app.ItemCount()) {
        return;
    }
    fid = atol(g_app.Items()[index].bvid);
    if (fid <= 0) {
        g_app.GetLogger().Status("收藏夹 id 无效");
        return;
    }

    ResetQuery(QK_FAV_VIDEOS, 30);
    s_fid = fid;

    g_app.GetLogger().Log("打开收藏夹 media_id=%ld", fid);
    g_app.GetLogger().Status("正在打开收藏夹…");
    g_app.SetBusy(1);
    g_app.RunAsync(FavVideosJob, NULL);
}

/* 滑到底部触发：按当前查询类型取下一页（结果追加） */
void SearchService::More()
{
    void (*fn)(void *) = NULL;

    if (g_app.IsBusy() || !s_hasMore || s_kind == QK_NONE ||
        s_kind == QK_FAV_FOLDER) {
        return;
    }
    if (g_app.ItemCount() >= BC_MAX_ITEMS) {
        s_hasMore = 0;
        return;
    }
    switch (s_kind) {
    case QK_SEARCH:     s_page++; fn = SearchJob;    break;
    case QK_POPULAR:    s_page++; fn = PopularJob;   break;
    case QK_HISTORY:              fn = HistoryJob;   break;  /* 游标翻页 */
    case QK_FAV_VIDEOS: s_page++; fn = FavVideosJob; break;
    default:            return;
    }

    g_app.GetLogger().Log("翻页：%s 第 %d 页（已有 %d 条）",
                          kind_name(s_kind), s_page, g_app.ItemCount());
    g_app.SetBusy(1);
    s_append = 1;
    g_app.RunAsync(fn, NULL);
}
