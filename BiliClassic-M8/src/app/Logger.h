/* =====================================================================
 * Logger.h - 日志：写文件（GBK）+ 转发给界面层
 * ===================================================================== */
#ifndef APP_LOGGER_H
#define APP_LOGGER_H

#include "UiSink.h"

class Logger
{
public:
    Logger();

    /* dataDir 为可写数据目录（\ 结尾）；日志文件 biliclassic_m8_log.txt */
    void Init(const char *dataDir, IUiSink *sink);

    /* 带时间戳写一行（文件 + 界面），fmt 用 printf 风格（GBK） */
    void Log(const char *fmt, ...);

    /* 只更新界面显示的提示行，不写文件（少用） */
    void Status(const char *fmt, ...);

private:
    void WriteLine(const char *line);

    char     m_path[320];
    IUiSink *m_sink;
};

#endif /* APP_LOGGER_H */
