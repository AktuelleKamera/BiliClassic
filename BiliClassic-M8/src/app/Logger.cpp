/* =====================================================================
 * Logger.cpp - 日志实现
 * ===================================================================== */
#include "Logger.h"
#include "../core/bc.h"
#include "../core/util.h"
#include <stdio.h>
#include <stdarg.h>
#include <string.h>

Logger::Logger()
{
    m_path[0] = '\0';
    m_sink = NULL;
    /* Init 之前的启动阶段（OnInitDialog 等）也要能落盘：
     * 先默认写到 exe 目录，Init 之后再用数据目录覆盖。 */
    get_exe_dir(m_path, (int)sizeof(m_path));
    str_append(m_path, (int)sizeof(m_path), "biliclassic_m8_log.txt");
}

void Logger::Init(const char *dataDir, IUiSink *sink)
{
    str_copy(m_path, (int)sizeof(m_path), dataDir);
    str_append(m_path, (int)sizeof(m_path), "biliclassic_m8_log.txt");
    m_sink = sink;
}

/* 日志超过这个大小就清空重写：M8 存储小，不能让它无限涨。
 * 用 CREATE_ALWAYS 就地截断，不做改名（改名在 CE 上容易出岔子）。 */
#define LOG_MAX_BYTES (256 * 1024)

void Logger::WriteLine(const char *line)
{
    wchar_t wpath[360];
    HANDLE h;

    /* 直接走 Win32 并显式 FlushFileBuffers：崩溃时也能把已写的行留住 */
    wpath[0] = L'\0';
    MultiByteToWideChar(CP_ACP, 0, m_path, -1, wpath, 360);
    h = CreateFileW(wpath, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE,
                    NULL, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h != INVALID_HANDLE_VALUE) {
        DWORD size = GetFileSize(h, NULL);

        if (size != INVALID_FILE_SIZE && size > LOG_MAX_BYTES) {
            CloseHandle(h);
            h = CreateFileW(wpath, GENERIC_WRITE,
                            FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                            CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        }
    }
    if (h != INVALID_HANDLE_VALUE) {
        DWORD n = 0;

        SetFilePointer(h, 0, NULL, FILE_END);
        WriteFile(h, line, (DWORD)strlen(line), &n, NULL);
        WriteFile(h, "\r\n", 2, &n, NULL);
        FlushFileBuffers(h);
        CloseHandle(h);
    }
    if (m_sink != NULL) {
        m_sink->OnLog(line);
    }
}

void Logger::Log(const char *fmt, ...)
{
    char buf[BC_LOG_LEN];
    char line[BC_LOG_LEN];
    SYSTEMTIME st;
    va_list ap;

    va_start(ap, fmt);
    _vsnprintf(buf, sizeof(buf) - 1, fmt, ap);
    va_end(ap);
    buf[sizeof(buf) - 1] = '\0';

    GetLocalTime(&st);
    _snprintf(line, sizeof(line) - 1, "[%02d:%02d:%02d] %s",
              st.wHour, st.wMinute, st.wSecond, buf);
    line[sizeof(line) - 1] = '\0';

    WriteLine(line);
}

void Logger::Status(const char *fmt, ...)
{
    char buf[256];
    va_list ap;

    va_start(ap, fmt);
    _vsnprintf(buf, sizeof(buf) - 1, fmt, ap);
    va_end(ap);
    buf[sizeof(buf) - 1] = '\0';

    if (m_sink != NULL) {
        m_sink->OnStatus(buf);
    }
}
