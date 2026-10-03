/* =====================================================================
 * util.h - 字符串/URL/JSON/编码/文件 小工具（C89）
 * ===================================================================== */
#ifndef BC_UTIL_H
#define BC_UTIL_H

#ifdef __cplusplus
extern "C" {
#endif

/* 有界字符串操作（cap 含结尾 0） */
void str_copy(char *dst, int cap, const char *src);
void str_append(char *dst, int cap, const char *src);

/* UTF-8 字节百分号编码（保留 RFC3986 unreserved） */
void url_encode(const char *src, char *dst, int cap);

/* 把开头的 https:// 就地改成 http://（Win95 没有 TLS）；
 * 改成功返回 1，本来就是 http:// 返回 0 */
int  url_downgrade_https(char *url);

/* UTF-8 -> 系统 ANSI(CP_ACP/936)，用于把 B 站 JSON 里的文本显示出来 */
void utf8_to_ansi(const char *src, char *dst, int cap);

/* 系统 ANSI(CP_ACP/936) -> UTF-8，用于把本地输入的关键词按 UTF-8 上线 */
void ansi_to_utf8(const char *src, char *dst, int cap);

/* JSON 极简取值（够用即可） */
const char *json_find(const char *json, const char *key);
int json_get_string(const char *json, const char *key, char *out, int cap);
int json_get_int(const char *json, const char *key, long *out);
void json_unescape(char *s);

/* 文件 */
int  file_read_all(const char *path, char *buf, int cap);
int  file_write_all(const char *path, const char *text);

/* 生成 32 位随机 hex（install_id） */
void make_random_hex(char *out, int hex_chars);

/* 取 exe 所在目录（含结尾 '\'） */
void get_exe_dir(char *out, int cap);

/* 取数据目录（ini/token/日志/下载都写这里，含结尾 '\'）。
 * 优先 exe 目录（可写时），否则回退 \Storage Card\BiliClassic\，再退 \Temp\ */
void get_data_dir(char *out, int cap);

/* 取临时目录（含结尾 '\'） */
void get_temp_dir(char *out, int cap);

/* 按文件头判断实际格式，返回一句中文（静态串） */
const char *bc_sniff_media(const char *path);

/* 当前 Unix 时间（秒，UTC，用 GetSystemTime 自己算，minicrt 没有 time()） */
long bc_unix_time(void);

#ifdef __cplusplus
}
#endif

#endif /* BC_UTIL_H */
