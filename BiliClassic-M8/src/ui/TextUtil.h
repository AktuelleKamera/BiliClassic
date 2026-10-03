/* =====================================================================
 * TextUtil.h - GBK(ACP) <-> 宽字符 辅助（界面层专用）
 * ===================================================================== */
#ifndef UI_TEXTUTIL_H
#define UI_TEXTUTIL_H

#include <string>
#include <windows.h>

/* GBK 窄串 -> wstring（界面显示用） */
inline std::wstring gbk2w(const char *s)
{
    std::wstring out;
    int n;

    if (s == NULL || *s == '\0') {
        return out;
    }
    n = MultiByteToWideChar(CP_ACP, 0, s, -1, NULL, 0);
    if (n <= 0) {
        return out;
    }
    wchar_t *buf = new wchar_t[n];
    if (MultiByteToWideChar(CP_ACP, 0, s, -1, buf, n) > 0) {
        out = buf;
    }
    delete[] buf;
    return out;
}

#endif /* UI_TEXTUTIL_H */
