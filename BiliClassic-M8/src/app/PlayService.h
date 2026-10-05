/* =====================================================================
 * PlayService.h - 播放链路：详情解析 / 镜像探测 / 下载 / 调播放器
 * ===================================================================== */
#ifndef APP_PLAYSERVICE_H
#define APP_PLAYSERVICE_H

class PlayService
{
public:
    /* 播放当前选中项；forceTranscode=1 时无视开关强制转码 */
    static void PlaySelected(int index, int forceTranscode);

    /* 播放最近一次下载成功的文件 */
    static void PlayLast();

    /* 设置变更（写回 ini） */
    static void SetTranscode(int on);
    static void SetConns(int n);
    static void SetOffline(int on);
    static void SetDanmaku(int on);
    static void SetReportHistory(int on);

    /* 转码目标格式的中文说明（固定 H.264 Baseline） */
    static const char *FmtLabel(void);

    /* 用系统播放器打开一个本地文件（界面层在内嵌播放器失败时回退调用） */
    static void PlayFile(const char *path);

private:
    static void OnProgress(long done, long total);
    static const char *UrlExt(const char *url);
    static void PlayJob(void *arg);   /* 后台线程：arg 是 PlayJobArg，自行释放 */
};

#endif /* APP_PLAYSERVICE_H */
