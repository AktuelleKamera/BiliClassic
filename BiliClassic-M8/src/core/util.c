/* =====================================================================
 * util.c - 字符串/URL/JSON/编码/文件 小工具（C89，M8 / WinCE 版）
 * ---------------------------------------------------------------------
 * M8 差异：
 *   - coredll 不导出 ANSI(A) 版 API -> 文件/路径一律 GBK<->UTF-16 走 W 版
 *   - 新增 ansi_to_utf8 / dir_writable / get_data_dir（可写目录兜底）
 * ===================================================================== */
#include "util.h"
#include <windows.h>
#include <wincrypt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void str_copy(char *dst, int cap, const char *src)
{
    int i;
    if (cap <= 0) {
        return;
    }
    if (src == NULL) {
        dst[0] = '\0';
        return;
    }
    for (i = 0; i < cap - 1 && src[i] != '\0'; i++) {
        dst[i] = src[i];
    }
    dst[i] = '\0';
}

void str_append(char *dst, int cap, const char *src)
{
    int len, i;
    if (cap <= 0 || src == NULL) {
        return;
    }
    len = (int)strlen(dst);
    if (len >= cap - 1) {
        return;
    }
    for (i = 0; len + i < cap - 1 && src[i] != '\0'; i++) {
        dst[len + i] = src[i];
    }
    dst[len + i] = '\0';
}

static int is_unreserved(unsigned char c)
{
    if (c >= 'a' && c <= 'z') return 1;
    if (c >= 'A' && c <= 'Z') return 1;
    if (c >= '0' && c <= '9') return 1;
    if (c == '-' || c == '_' || c == '.' || c == '~') return 1;
    return 0;
}

void url_encode(const char *src, char *dst, int cap)
{
    static const char *hex = "0123456789ABCDEF";
    int di = 0;
    int i;

    if (cap <= 0) {
        return;
    }
    for (i = 0; src != NULL && src[i] != '\0' && di < cap - 4; i++) {
        unsigned char c = (unsigned char)src[i];
        if (is_unreserved(c)) {
            dst[di++] = (char)c;
        } else {
            dst[di++] = '%';
            dst[di++] = hex[(c >> 4) & 0x0F];
            dst[di++] = hex[c & 0x0F];
        }
    }
    dst[di] = '\0';
}

int url_downgrade_https(char *url)
{
    if (url == NULL || strncmp(url, "https://", 8) != 0) {
        return 0;
    }
    /* 只删掉 "https" 里的那个 's'，后面整体前移 1 字节 */
    memmove(url + 4, url + 5, strlen(url + 5) + 1);
    return 1;
}

/* ---- UTF-8 -> UTF-16 -> ANSI ---- */
static int utf8_next(const unsigned char *s, unsigned int *cp, int *used)
{
    unsigned char c = s[0];
    if (c < 0x80) {
        *cp = c;
        *used = 1;
        return 1;
    }
    if ((c & 0xE0) == 0xC0 && (s[1] & 0xC0) == 0x80) {
        *cp = ((unsigned int)(c & 0x1F) << 6) | (unsigned int)(s[1] & 0x3F);
        *used = 2;
        return 1;
    }
    if ((c & 0xF0) == 0xE0 && (s[1] & 0xC0) == 0x80 && (s[2] & 0xC0) == 0x80) {
        *cp = ((unsigned int)(c & 0x0F) << 12) |
              ((unsigned int)(s[1] & 0x3F) << 6) |
              (unsigned int)(s[2] & 0x3F);
        *used = 3;
        return 1;
    }
    if ((c & 0xF8) == 0xF0 && (s[1] & 0xC0) == 0x80 &&
        (s[2] & 0xC0) == 0x80 && (s[3] & 0xC0) == 0x80) {
        *cp = ((unsigned int)(c & 0x07) << 18) |
              ((unsigned int)(s[1] & 0x3F) << 12) |
              ((unsigned int)(s[2] & 0x3F) << 6) |
              (unsigned int)(s[3] & 0x3F);
        *used = 4;
        return 1;
    }
    *cp = (unsigned int)c;
    *used = 1;
    return 0;
}

void utf8_to_ansi(const char *src, char *dst, int cap)
{
    WCHAR wbuf[1024];
    static char abuf[4096];
    int wi = 0;
    int i = 0;
    int len;
    int n;

    if (cap <= 0) {
        return;
    }
    dst[0] = '\0';
    if (src == NULL) {
        return;
    }
    len = (int)strlen(src);

    while (i < len && wi < 1023) {
        unsigned int cp = 0;
        int used = 0;
        if (!utf8_next((const unsigned char *)src + i, &cp, &used)) {
            cp = '?';
        }
        i += used;
        if (cp >= 0x10000) {
            unsigned int v = cp - 0x10000;
            if (wi < 1022) {
                wbuf[wi++] = (WCHAR)(0xD800 + (v >> 10));
                wbuf[wi++] = (WCHAR)(0xDC00 + (v & 0x3FF));
            }
        } else {
            wbuf[wi++] = (WCHAR)cp;
        }
    }
    wbuf[wi] = 0;

    if (wi == 0) {
        return;
    }
    /* 先转到足够大的临时缓冲（WideCharToMultiByte 在放不下时会整体失败，
     * 直接写 dst 会导致长标题变成空串），再截断拷贝 */
    n = WideCharToMultiByte(CP_ACP, 0, wbuf, wi, abuf,
                            (int)sizeof(abuf) - 1, NULL, NULL);
    if (n <= 0) {
        return;
    }
    abuf[n] = '\0';
    str_copy(dst, cap, abuf);
}

/* ---- ANSI(CP_ACP) -> UTF-8（搜索关键词上行用；手工编码，不依赖 CP_UTF8） ---- */
void ansi_to_utf8(const char *src, char *dst, int cap)
{
    WCHAR wbuf[512];
    int n;
    int i;
    int di = 0;

    if (cap <= 0) {
        return;
    }
    dst[0] = '\0';
    if (src == NULL) {
        return;
    }

    n = MultiByteToWideChar(CP_ACP, 0, src, -1, wbuf, 511);
    if (n <= 0) {
        return;
    }
    wbuf[511] = L'\0';

    for (i = 0; wbuf[i] != L'\0' && di < cap - 4; i++) {
        unsigned int cp = (unsigned int)wbuf[i];

        if (cp >= 0xD800 && cp <= 0xDBFF &&
            wbuf[i + 1] >= 0xDC00 && wbuf[i + 1] <= 0xDFFF) {
            cp = 0x10000UL +
                 (((unsigned int)cp - 0xD800UL) << 10) +
                 ((unsigned int)wbuf[i + 1] - 0xDC00UL);
            i++;
        }
        if (cp < 0x80UL) {
            dst[di++] = (char)cp;
        } else if (cp < 0x800UL) {
            dst[di++] = (char)(0xC0 | (cp >> 6));
            dst[di++] = (char)(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000UL) {
            dst[di++] = (char)(0xE0 | (cp >> 12));
            dst[di++] = (char)(0x80 | ((cp >> 6) & 0x3F));
            dst[di++] = (char)(0x80 | (cp & 0x3F));
        } else {
            dst[di++] = (char)(0xF0 | (cp >> 18));
            dst[di++] = (char)(0x80 | ((cp >> 12) & 0x3F));
            dst[di++] = (char)(0x80 | ((cp >> 6) & 0x3F));
            dst[di++] = (char)(0x80 | (cp & 0x3F));
        }
    }
    dst[di] = '\0';
}

/* ---- JSON ---- */

const char *json_find(const char *json, const char *key)
{
    char pat[128];
    if (json == NULL || key == NULL) {
        return NULL;
    }
    str_copy(pat, (int)sizeof(pat), "\"");
    str_append(pat, (int)sizeof(pat), key);
    str_append(pat, (int)sizeof(pat), "\"");
    return strstr(json, pat);
}

/* 找到 "key" 后面的冒号（容忍空格），返回值起始位置；找不到返回 NULL */
static const char *json_value_start(const char *json, const char *key)
{
    char pat[128];
    const char *p;
    const char *q;

    if (json == NULL || key == NULL) {
        return NULL;
    }
    str_copy(pat, (int)sizeof(pat), "\"");
    str_append(pat, (int)sizeof(pat), key);
    str_append(pat, (int)sizeof(pat), "\"");
    p = json;
    for (;;) {
        p = strstr(p, pat);
        if (p == NULL) {
            return NULL;
        }
        q = p + (int)strlen(pat);
        while (*q == ' ' || *q == '\t') {
            q++;
        }
        if (*q == ':') {
            break;
        }
        p++;
    }
    q++;
    while (*q == ' ' || *q == '\t') {
        q++;
    }
    return q;
}

int json_get_string(const char *json, const char *key, char *out, int cap)
{
    const char *p;
    int i = 0;

    if (cap > 0) {
        out[0] = '\0';
    }
    if (json == NULL || key == NULL || cap <= 1) {
        return 0;
    }
    p = json_value_start(json, key);
    if (p == NULL || *p != '"') {
        return 0;
    }
    p++;
    while (*p != '\0' && *p != '"' && i < cap - 1) {
        if (*p == '\\' && p[1] != '\0') {
            if (p[1] == 'u' && p[2] != '\0' && p[3] != '\0' &&
                p[4] != '\0' && p[5] != '\0') {
                /* 只处理 \u00XX 形式的 ASCII 转义；中文一般是原样 UTF-8 */
                int v = 0;
                int k;
                int ok = 1;
                for (k = 0; k < 4; k++) {
                    char c = p[2 + k];
                    v <<= 4;
                    if (c >= '0' && c <= '9') {
                        v += c - '0';
                    } else if (c >= 'a' && c <= 'f') {
                        v += c - 'a' + 10;
                    } else if (c >= 'A' && c <= 'F') {
                        v += c - 'A' + 10;
                    } else {
                        ok = 0;
                        break;
                    }
                }
                if (ok && v > 0 && v < 0x80) {
                    out[i++] = (char)v;
                    p += 6;
                    continue;
                }
            }
            if (p[1] == 'n' || p[1] == 'r' || p[1] == 't') {
                out[i++] = ' ';
                p += 2;
                continue;
            }
            p++;
            if (*p == '\0') {
                break;
            }
        }
        out[i++] = *p++;
    }
    out[i] = '\0';
    return 1;
}

int json_get_int(const char *json, const char *key, long *out)
{
    const char *p;

    if (json == NULL || key == NULL || out == NULL) {
        return 0;
    }
    p = json_value_start(json, key);
    if (p == NULL) {
        return 0;
    }
    *out = atol(p);
    return 1;
}

void json_unescape(char *s)
{
    char *r = s;
    char *w = s;
    if (s == NULL) {
        return;
    }
    while (*r != '\0') {
        if (*r == '\\' && r[1] != '\0') {
            if (r[1] == '/' || r[1] == '\\' || r[1] == '"') {
                *w++ = r[1];
                r += 2;
                continue;
            }
            if (r[1] == 'n') { *w++ = ' '; r += 2; continue; }
            if (r[1] == 'r' || r[1] == 't') { r += 2; continue; }
        }
        *w++ = *r++;
    }
    *w = '\0';
}

/* ---- 文件 ---- */

int file_read_all(const char *path, char *buf, int cap)
{
    FILE *f;
    int n;

    if (cap <= 0) {
        return 0;
    }
    buf[0] = '\0';
    f = fopen(path, "rb");
    if (f == NULL) {
        return 0;
    }
    n = (int)fread(buf, 1, (size_t)(cap - 1), f);
    fclose(f);
    buf[n] = '\0';
    return n;
}

int file_write_all(const char *path, const char *text)
{
    FILE *f = fopen(path, "wb");
    if (f == NULL) {
        return 0;
    }
    fwrite(text, 1, strlen(text), f);
    fclose(f);
    return 1;
}

void make_random_hex(char *out, int hex_chars)
{
    static const char *hex = "0123456789abcdef";
    unsigned long seed;
    int i;

    seed = (unsigned long)GetTickCount();
    seed = seed * 1103515245UL + (unsigned long)GetCurrentProcessId() + 12345UL;
    srand((unsigned int)seed);

    if (hex_chars < 4) {
        hex_chars = 4;
    }
    for (i = 0; i < hex_chars; i++) {
        int v;
        if ((i % 4) == 0) {
            seed = seed * 1103515245UL + 12345UL;
        }
        v = (int)((seed >> ((i % 4) * 5)) & 0x0F);
        v = (v + rand() + i) & 0x0F;
        out[i] = hex[v];
    }
    out[hex_chars] = '\0';
}

/* M8 coredll has no ANSI(A) exports: GBK <-> UTF-16, use W APIs */
static void ut_a2w(const char *src, wchar_t *dst, int cap)
{
    MultiByteToWideChar(CP_ACP, 0, src, -1, dst, cap);
}

static void ut_w2a(const wchar_t *src, char *dst, int cap)
{
    WideCharToMultiByte(CP_ACP, 0, src, -1, dst, cap, NULL, NULL);
}

void get_exe_dir(char *out, int cap)
{
    char path[260];
    wchar_t wpath[260];
    int i;

    if (cap <= 0) {
        return;
    }
    out[0] = '\0';
    if (GetModuleFileNameW(NULL, wpath, 260) == 0) {
        return;
    }
    ut_w2a(wpath, path, (int)sizeof(path));
    for (i = (int)strlen(path) - 1; i >= 0; i--) {
        if (path[i] == '\\' || path[i] == '/') {
            path[i + 1] = '\0';
            break;
        }
    }
    str_copy(out, cap, path);
}

void get_temp_dir(char *out, int cap)
{
    char path[260];
    wchar_t wpath[260];
    DWORD n = GetTempPathW(260, wpath);
    if (n == 0) {
        CreateDirectoryW(L"\\Temp", NULL);
        str_copy(out, cap, "\\Temp\\");
        return;
    }
    ut_w2a(wpath, path, (int)sizeof(path));
    str_copy(out, cap, path);
}

/* 目录能不能直接写文件（写完即删） */
static int dir_writable(const char *dir)
{
    char probe[300];
    wchar_t wprobe[300];
    HANDLE h;

    str_copy(probe, (int)sizeof(probe), dir);
    str_append(probe, (int)sizeof(probe), "__bc_wtest.tmp");
    ut_a2w(probe, wprobe, 300);
    h = CreateFileW(wprobe, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                    FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) {
        return 0;
    }
    CloseHandle(h);
    DeleteFileW(wprobe);
    return 1;
}

void get_data_dir(char *out, int cap)
{
    char dir[260];

    if (cap <= 0) {
        return;
    }
    out[0] = '\0';

    /* 1) exe 所在目录（先试这个，可用就顺手） */
    get_exe_dir(dir, (int)sizeof(dir));
    if (dir_writable(dir)) {
        str_copy(out, cap, dir);
        return;
    }

    /* 2) M8 存储根 \Disk（M8SDK 默认 \Disk\MUSIC）；再退到 \Storage Card */
    CreateDirectoryW(L"\\Disk\\BiliClassic", NULL);
    if (dir_writable("\\Disk\\BiliClassic\\")) {
        str_copy(out, cap, "\\Disk\\BiliClassic\\");
        return;
    }
    CreateDirectoryW(L"\\Storage Card\\BiliClassic", NULL);
    if (dir_writable("\\Storage Card\\BiliClassic\\")) {
        str_copy(out, cap, "\\Storage Card\\BiliClassic\\");
        return;
    }

    /* 3) 兜底：\Temp\ */
    CreateDirectoryW(L"\\Temp", NULL);
    if (dir_writable("\\Temp\\")) {
        str_copy(out, cap, "\\Temp\\");
        return;
    }

    /* 实在不行就用原路径 */
    str_copy(out, cap, dir);
}

/* 看文件头判断实际容器格式（下载完核对服务器到底给了什么） */
const char *bc_sniff_media(const char *path){
    FILE *f;
    unsigned char b[16];
    int n;

    f = fopen(path, "rb");
    if (f == NULL) {
        return "读不出文件";
    }
    n = (int)fread(b, 1, sizeof(b), f);
    fclose(f);
    if (n < 4) {
        return "文件太小（下载不完整？）";
    }
    if (b[0] == 0x00 && b[1] == 0x00 && b[2] == 0x01 &&
        (b[3] == 0xBA || b[3] == 0xB9 || b[3] == 0xB3)) {
        return "MPEG-1/MPEG-PS";
    }
    if (n >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
        return "MP4/H.264";
    }
    if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F') {
        return "AVI";
    }
    if (b[0] == 0x30 && b[1] == 0x26 && b[2] == 0xB2 && b[3] == 0x75) {
        return "ASF/WMV";
    }
    if (b[0] == 'F' && b[1] == 'L' && b[2] == 'V') {
        return "FLV";
    }
    if (b[0] == 0x1A && b[1] == 0x45 && b[2] == 0xDF && b[3] == 0xA3) {
        return "MKV";
    }
    if (b[0] == 0x49 && b[1] == 0x44 && b[2] == 0x33) {
        return "MP3";
    }
    return "未知格式";
}

/* 当前 Unix 时间（秒，UTC）。minicrt 没有 time()，用 GetSystemTime 自己推。 */
long bc_unix_time(void)
{
    static const int mdays[12] = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
    SYSTEMTIME st;
    long days = 0;
    int y;
    int m;

    GetSystemTime(&st);
    for (y = 1970; y < (int)st.wYear; y++) {
        days += 365;
        if ((y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)) {
            days++;
        }
    }
    for (m = 0; m < (int)st.wMonth - 1 && m < 12; m++) {
        days += mdays[m];
        if (m == 1 &&
            ((st.wYear % 4 == 0 && st.wYear % 100 != 0) || st.wYear % 400 == 0)) {
            days++;
        }
    }
    days += (long)st.wDay - 1;
    return days * 86400L + (long)st.wHour * 3600L +
           (long)st.wMinute * 60L + (long)st.wSecond;
}

