/* =====================================================================
 * SearchService.h - 搜索 / 热门 / 历史 / 收藏（网络跑后台线程，回调到 UI）
 * ===================================================================== */
#ifndef APP_SEARCHSERVICE_H
#define APP_SEARCHSERVICE_H

/* 当前列表是哪种查询：UI 据此决定双击行为（收藏夹文件夹=进文件夹） */
enum QueryKind
{
    QK_NONE = 0,
    QK_SEARCH,
    QK_POPULAR,
    QK_HISTORY,
    QK_FAV_FOLDER,   /* 列表里是收藏夹文件夹，双击进该文件夹 */
    QK_FAV_VIDEOS
};

class SearchService
{
public:
    /* keyword 为 GBK 窄字符串（界面编辑框原文） */
    static void Search(const char *keyword);
    static void Popular();
    static void History();
    static void FavFolders();
    static void OpenFolder(int index);   /* 双击某个收藏夹文件夹 */
    static void More();                  /* 滑到底部 -> 下一页 */

    static int Kind() { return s_kind; }

private:
    static void ResetQuery(int kind, int ps);
    static void ShowItems(const char *json, const char *what,
                          const char *pic_field, int append);
    static void SearchJob(void *arg);
    static void PopularJob(void *arg);
    static void HistoryJob(void *arg);
    static void FavFolderJob(void *arg);
    static void FavVideosJob(void *arg);

    static int  s_kind;
    static int  s_page;
    static int  s_ps;        /* 每页条数：返回不足此数即没有下一页 */
    static int  s_hasMore;
    static int  s_append;    /* 当前这一页是翻页追加还是重新开始 */
    static char s_keyword[600];
    static long s_fid;       /* 收藏夹 media_id */
    static long s_curMax;    /* 历史游标 max */
    static long s_curViewAt; /* 历史游标 view_at */
};

#endif /* APP_SEARCHSERVICE_H */
