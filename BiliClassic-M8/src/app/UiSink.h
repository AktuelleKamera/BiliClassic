/* =====================================================================
 * UiSink.h - 业务层 -> 界面层 的回调接口（纯虚，业务层不依赖 Mzfc）
 * ---------------------------------------------------------------------
 * 所有文本参数都是 GBK(ACP) 窄字符串，由界面层负责转宽字符显示。
 * ===================================================================== */
#ifndef APP_UISINK_H
#define APP_UISINK_H

#include "../core/bc.h"

class IUiSink
{
public:
    virtual ~IUiSink() {}

    /* 追加一行日志（已带 [HH:MM:SS] 时间戳的完整行） */
    virtual void OnLog(const char *line) = 0;

    /* 底部状态行（一句话说明当前动作） */
    virtual void OnStatus(const char *text) = 0;

    /* 加载层状态文字（非空=显示并设置当前阶段；空=NULL=隐藏加载层） */
    virtual void OnLoading(const char *text) = 0;

    /* 一次搜索/热门拿到了新的结果集 */
    virtual void OnItems(const BcItem *items, int count, const char *what,
                         int append) = 0;

    /* 1=进入忙态（禁用按钮、等光标），0=恢复 */
    virtual void OnBusyChanged(int busy) = 0;

    /* 下载进度回调（在同步下载期间反复触发） */
    virtual void OnDownloadProgress(long done, long total) = 0;

    /* 业务层把「要播放的媒体」交给界面层（界面层决定内嵌/系统播放器） */
    virtual void OnPlayMedia(const char *path) = 0;

    /* 业务层在同步等待期间要求界面层泵消息，保持界面响应 */
    virtual void PumpMessages() = 0;
};

#endif /* APP_UISINK_H */
