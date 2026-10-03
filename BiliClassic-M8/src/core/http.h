/* =====================================================================
 * http.h - 极简 HTTP 客户端（Windows CE / 魅族 M8）
 * ---------------------------------------------------------------------
 *  - 明文 http：只走 ws2.dll（WinCE 自带），HTTP/1.1 + Connection: close，
 *    按 Content-Length 或读到断开，支持 chunked 解码，自动跟随 3xx
 *  - https：http_get_https 走 WinInet（与 M8 浏览器同栈）。B 站证书链的根
 *    GlobalSign Root CA - R3 需设备信任，否则会卡在证书校验；失败返回负值，
 *    上层回退中继
 *  - 不发 Accept-Encoding（避免 gzip）
 * ===================================================================== */
#ifndef BC_HTTP_H
#define BC_HTTP_H

#ifdef __cplusplus
extern "C" {
#endif

#define HTTP_OK           0
#define HTTP_ERR_DNS     -1
#define HTTP_ERR_SOCKET  -2
#define HTTP_ERR_CONNECT -3
#define HTTP_ERR_SEND    -4
#define HTTP_ERR_RECV    -5
#define HTTP_ERR_MEM     -6
#define HTTP_ERR_FORMAT  -7
#define HTTP_ERR_FILE    -8
#define HTTP_ERR_REDIR   -9
#define HTTP_ERR_ARG    -10

void http_init(void);
void http_done(void);
void http_set_log(void (*fn)(const char *msg));

/* 静音本线程的 http 日志（封面下载用，避免几十张图把日志刷爆）。
 * on=1 记下调用线程 id，on=0 解除；只影响本线程，别的线程照常输出。 */
void http_set_quiet(int on);

/* 收发超时（毫秒，0=不限）。默认 30000；转码这种同步长耗时先调大再调回 */
void http_set_timeout(int ms);

/* 下载并行连接数（1..8，1=单连接）。默认 4；服务器不支持 Range 时自动退回单连接 */
void http_set_conns(int n);

/* 直连 host:port 请求（不跟随跳转，底层接口） */
int http_request(const char *host, int port,
                 const char *method, const char *path,
                 const char *extra_headers,
                 const char *body, int body_len,
                 char **out_body, int *out_len, int *out_status);

/* GET 整个 URL（跟随跳转），拿到响应体；只要拿到 HTTP 响应就返回 HTTP_OK，
 * 具体状态码在 *out_status（可能是 4xx/5xx） */
int http_get_url(const char *url, const char *extra_headers,
                 char **out_body, int *out_len, int *out_status,
                 char *err, int errcap);

/* 经 WinInet 发 HTTPS GET（底层 Schannel，跟随跳转，忽略证书 CN/日期）。
 * M8 的 WinInet 一般只到 TLS 1.0；wininet.dll 缺失时返回负错误码，调用方可回退中继。
 * 只要拿到 HTTP 响应就返回 HTTP_OK，状态码在 *out_status。 */
int http_get_https(const char *url, const char *extra_headers,
                   char **out_body, int *out_len, int *out_status,
                   char *err, int errcap);

/* 经 wolfSSL 发 HTTPS POST（application/x-www-form-urlencoded） */
int http_post_https(const char *url, const char *extra_headers,
                    const char *body, int body_len,
                    char **out_body, int *out_len, int *out_status,
                    char *err, int errcap);

/* 最近一次 HTTPS 响应 Date 头的 unix 秒（0=未知）。用于校正本机时钟做 WBI 签名 */
long http_server_time(void);

/* 最近一次响应的原始头（含 Set-Cookie）。扫码登录要用它兜底取 cookie */
const char *http_last_headers(void);

/* GET 整个 URL 并流式写入文件（跟随跳转） */
int http_get_url_to_file(const char *url, const char *extra_headers,
                         const char *dst_path,
                         void (*progress)(long done, long total),
                         long *out_total, char *err, int errcap);

#ifdef __cplusplus
}
#endif

#endif /* BC_HTTP_H */
