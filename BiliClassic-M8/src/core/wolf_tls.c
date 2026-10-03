/* =====================================================================
 * wolf_tls.c - 用 wolfSSL 做 HTTPS GET（自带 TLS，绕过 M8 ROM 的 SChannel）
 * ---------------------------------------------------------------------
 *  - 自己 ws2 建 TCP，用 wolfSSL 在其中做 TLS1.2 握手（带 SNI）
 *  - 不做证书校验（SSL_VERIFY_NONE）：只为打通通道，不依赖系统根库/时间
 *  - 连接复用：同一 host:port 的多次请求共用一条 TLS 长连接（省握手）
 *  - 失败返回负值，上层（core/bili.c）自行处理
 * ===================================================================== */
#include <winsock2.h>
#include <windows.h>
#include <wincrypt.h>
#include "http.h"
#include "util.h"
#include "wolfssl/ssl.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define WT_MAX_RESP (2 * 1024 * 1024)
#define WT_MAX_URL  4600
#define WT_MAX_HDR  8192

static int s_wolf_inited = 0;

/* 复用的连接状态（同一时刻只有一个后台任务在跑，无需加锁） */
static WOLFSSL_CTX *s_ctx = NULL;
static WOLFSSL     *s_ssl = NULL;
static SOCKET       s_fd  = INVALID_SOCKET;
static char         s_peer[128];
static int          s_peerport = 0;
static long         s_server_time = 0;   /* 最近一次响应的 Date 头（unix 秒） */

long http_server_time(void)
{
    return s_server_time;
}

/* 把 "Thu, 01 Oct 2026 19:10:27 GMT" 转成 unix 秒；失败返回 0 */
static long wt_parse_date(const char *h)
{
    static const char *mon = "JanFebMarAprMayJunJulAugSepOctNovDec";
    static const int mdays[12] = {31,28,31,30,31,30,31,31,30,31,30,31};
    const char *p;
    int day = 0;
    int year = 0;
    int hh = 0;
    int mm = 0;
    int ss = 0;
    int mi = -1;
    int i;
    long days = 0;

    p = strstr(h, "Date:");
    if (p == NULL) {
        p = strstr(h, "date:");
    }
    if (p == NULL) {
        return 0;
    }
    p += 5;
    while (*p != '\0' && (*p < '0' || *p > '9')) {
        p++;
    }
    while (*p >= '0' && *p <= '9') { day = day * 10 + (*p - '0'); p++; }
    while (*p == ' ') p++;
    for (i = 0; i < 12; i++) {
        if (p[0] == mon[i * 3] && p[1] == mon[i * 3 + 1] &&
            p[2] == mon[i * 3 + 2]) {
            mi = i;
            break;
        }
    }
    if (mi < 0) {
        return 0;
    }
    p += 3;
    while (*p == ' ') p++;
    while (*p >= '0' && *p <= '9') { year = year * 10 + (*p - '0'); p++; }
    while (*p == ' ') p++;
    while (*p >= '0' && *p <= '9') { hh = hh * 10 + (*p - '0'); p++; }
    if (*p == ':') p++;
    while (*p >= '0' && *p <= '9') { mm = mm * 10 + (*p - '0'); p++; }
    if (*p == ':') p++;
    while (*p >= '0' && *p <= '9') { ss = ss * 10 + (*p - '0'); p++; }
    if (year < 1970 || day < 1 || day > 31) {
        return 0;
    }
    for (i = 1970; i < year; i++) {
        days += 365;
        if ((i % 4 == 0 && i % 100 != 0) || i % 400 == 0) days++;
    }
    for (i = 0; i < mi; i++) {
        days += mdays[i];
        if (i == 1 && ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0)) {
            days++;
        }
    }
    days += day - 1;
    return days * 86400L + (long)hh * 3600L + (long)mm * 60L + (long)ss;
}

/* wolfSSL 的熵源回调（见 user_settings.h 的 CUSTOM_RAND_GENERATE_SEED） */
int bc_rng_seed(unsigned char *output, unsigned int sz)
{
    HCRYPTPROV h;
    if (!CryptAcquireContext(&h, NULL, NULL, PROV_RSA_FULL,
                             CRYPT_VERIFYCONTEXT)) {
        return -1;
    }
    if (!CryptGenRandom(h, (DWORD)sz, (BYTE *)output)) {
        CryptReleaseContext(h, 0);
        return -1;
    }
    CryptReleaseContext(h, 0);
    return 0;
}

static void wt_err(char *err, int errcap, const char *msg)
{
    if (err != NULL && errcap > 0) {
        str_copy(err, errcap, msg);
    }
}

static void wt_err_num(char *err, int errcap, const char *msg, int code)
{
    char tmp[128];
    sprintf(tmp, "%s err=%d", msg, code);
    wt_err(err, errcap, tmp);
}

/* ---- IO 回调：绑定到我们的 socket ---- */
static int wt_recv(WOLFSSL *ssl, char *buf, int sz, void *ctx)
{
    SOCKET fd = (SOCKET)(int)ctx;
    int n;
    (void)ssl;
    n = recv(fd, buf, sz, 0);
    if (n > 0) {
        return n;
    }
    if (n == 0) {
        return WOLFSSL_CBIO_ERR_CONN_CLOSE;
    }
    {
        int e = WSAGetLastError();
        if (e == WSAEWOULDBLOCK) {
            return WOLFSSL_CBIO_ERR_WANT_READ;
        }
        return WOLFSSL_CBIO_ERR_GENERAL;
    }
}

static int wt_send(WOLFSSL *ssl, char *buf, int sz, void *ctx)
{
    SOCKET fd = (SOCKET)(int)ctx;
    int n;
    (void)ssl;
    n = send(fd, buf, sz, 0);
    if (n > 0) {
        return n;
    }
    {
        int e = WSAGetLastError();
        if (e == WSAEWOULDBLOCK) {
            return WOLFSSL_CBIO_ERR_WANT_WRITE;
        }
        return WOLFSSL_CBIO_ERR_GENERAL;
    }
}

static void wt_close(void)
{
    if (s_ssl != NULL) {
        wolfSSL_shutdown(s_ssl);
        wolfSSL_free(s_ssl);
        s_ssl = NULL;
    }
    if (s_fd != INVALID_SOCKET) {
        closesocket(s_fd);
        s_fd = INVALID_SOCKET;
    }
    s_peer[0] = '\0';
    s_peerport = 0;
}

/* 确保有一条到 host:port 的可用 TLS 连接（复用或新建） */
static int wt_ensure_conn(const char *host, int port, char *err, int errcap)
{
    struct hostent *he;
    struct sockaddr_in sa;
    SOCKET fd;
    int tv = 20000;
    int ret;

    if (s_ssl != NULL && strcmp(s_peer, host) == 0 && s_peerport == port) {
        return 0;       /* 复用 */
    }
    wt_close();

    if (!s_wolf_inited) {
        wolfSSL_Init();
        s_wolf_inited = 1;
    }
    if (s_ctx == NULL) {
        s_ctx = wolfSSL_CTX_new(wolfTLSv1_2_client_method());
        if (s_ctx == NULL) {
            wt_err(err, errcap, "wolfSSL_CTX_new 失败");
            return -1;
        }
        wolfSSL_CTX_SetIORecv(s_ctx, wt_recv);
        wolfSSL_CTX_SetIOSend(s_ctx, wt_send);
        wolfSSL_CTX_set_verify(s_ctx, SSL_VERIFY_NONE, NULL);
    }

    he = gethostbyname(host);
    if (he == NULL || he->h_addr_list == NULL || he->h_addr_list[0] == NULL) {
        wt_err(err, errcap, "DNS 解析失败");
        return -1;
    }
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons((unsigned short)port);
    memcpy(&sa.sin_addr, he->h_addr_list[0], 4);

    fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (fd == INVALID_SOCKET) {
        wt_err(err, errcap, "socket 创建失败");
        return -1;
    }
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, (const char *)&tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, (const char *)&tv, sizeof(tv));
    if (connect(fd, (struct sockaddr *)&sa, sizeof(sa)) != 0) {
        closesocket(fd);
        wt_err(err, errcap, "TCP 连接失败");
        return -1;
    }

    s_ssl = wolfSSL_new(s_ctx);
    if (s_ssl == NULL) {
        closesocket(fd);
        wt_err(err, errcap, "wolfSSL_new 失败");
        return -1;
    }
    s_fd = fd;
    str_copy(s_peer, (int)sizeof(s_peer), host);
    s_peerport = port;
    wolfSSL_SetIOReadCtx(s_ssl, (void *)(int)fd);
    wolfSSL_SetIOWriteCtx(s_ssl, (void *)(int)fd);
    wolfSSL_UseSNI(s_ssl, WOLFSSL_SNI_HOST_NAME, host, (unsigned short)strlen(host));

    ret = wolfSSL_connect(s_ssl);
    if (ret != WOLFSSL_SUCCESS) {
        int e = wolfSSL_get_error(s_ssl, ret);
        wt_err_num(err, errcap, "TLS 握手失败", e);
        wt_close();
        return -1;
    }
    return 0;
}

/* chunked 解码：in/inlen -> 新 malloc 的 out；失败返回 NULL */
static char *wt_dechunk(const char *in, int inlen, int *outlen)
{
    char *dst = (char *)malloc((size_t)inlen + 1);
    int i = 0;
    int di = 0;

    if (dst == NULL) {
        return NULL;
    }
    while (i < inlen) {
        int chunk = 0;
        int digits = 0;
        while (i < inlen) {
            char c = in[i];
            int v = -1;
            if (c >= '0' && c <= '9') v = c - '0';
            else if (c >= 'a' && c <= 'f') v = c - 'a' + 10;
            else if (c >= 'A' && c <= 'F') v = c - 'A' + 10;
            if (v < 0) break;
            chunk = chunk * 16 + v;
            digits++;
            i++;
        }
        if (digits == 0) break;
        while (i < inlen && in[i] != '\n') i++;
        i++;
        if (chunk <= 0) break;
        if (i + chunk > inlen) chunk = inlen - i;
        memcpy(dst + di, in + i, (size_t)chunk);
        di += chunk;
        i += chunk;
        while (i < inlen && (in[i] == '\r' || in[i] == '\n')) i++;
    }
    dst[di] = '\0';
    *outlen = di;
    return dst;
}

/* 最近一次响应的原始头。扫码登录成功后 B 站把 cookie 放在 Set-Cookie
 * 里（data.url 可能为空），所以把头留出来给上层兜底解析。 */
static char s_last_hdrs[WT_MAX_HDR];

const char *http_last_headers(void)
{
    return s_last_hdrs;
}

/* 读取一个 HTTP 响应（复用连接时按 Content-Length/chunked 定界）。
 * 成功返回 0，body 为 malloc 的响应体；失败返回 -1。 */
static int wt_read_response(char **out_body, int *out_len, int *out_status,
                            char *err, int errcap)
{
    char *raw = (char *)malloc(WT_MAX_RESP);
    int total = 0;
    int hdr_end = -1;
    int body_off = 0;
    long clen = -1;
    int chunked = 0;
    int keep = 1;
    char *body = NULL;
    int blen = 0;
    int ret;

    *out_body = NULL;
    *out_len = 0;
    *out_status = 0;
    if (raw == NULL) {
        wt_err(err, errcap, "内存不足");
        return -1;
    }

    /* 1) 读响应头 */
    for (;;) {
        if (total >= WT_MAX_HDR) {
            break;
        }
        ret = wolfSSL_read(s_ssl, raw + total, WT_MAX_HDR - 1 - total);
        if (ret > 0) {
            total += ret;
            raw[total] = '\0';
            hdr_end = (int)(strstr(raw, "\r\n\r\n") - raw);
            if (strstr(raw, "\r\n\r\n") != NULL) {
                hdr_end += 4;
                break;
            }
            continue;
        }
        if (ret == 0) {
            break;
        }
        if (wolfSSL_get_error(s_ssl, ret) == WOLFSSL_ERROR_WANT_READ) {
            continue;
        }
        free(raw);
        wt_err(err, errcap, "读响应头失败");
        return -1;
    }
    if (hdr_end <= 0) {
        free(raw);
        wt_err(err, errcap, "响应头不完整");
        return -1;
    }

    {
        int n = hdr_end;

        if (n > (int)sizeof(s_last_hdrs) - 1) {
            n = (int)sizeof(s_last_hdrs) - 1;
        }
        memcpy(s_last_hdrs, raw, (size_t)n);
        s_last_hdrs[n] = '\0';
    }


    {
        char *sp = strchr(raw, ' ');
        *out_status = (sp != NULL) ? atoi(sp + 1) : 0;
    }
    {
        long dt = wt_parse_date(raw);
        if (dt > 0) {
            s_server_time = dt;
        }
    }
    if (strstr(raw, "Transfer-Encoding: chunked") != NULL ||
        strstr(raw, "transfer-encoding: chunked") != NULL) {
        chunked = 1;
    } else {
        const char *cl = strstr(raw, "Content-Length:");
        if (cl == NULL) {
            cl = strstr(raw, "content-length:");
        }
        if (cl != NULL) {
            clen = atol(cl + 15);
        }
    }
    if (strstr(raw, "Connection: close") != NULL ||
        strstr(raw, "connection: close") != NULL) {
        keep = 0;
    }
    body_off = hdr_end;

    /* 2) 读正文到（近乎）完整 */
    if (chunked) {
        /* 按 chunk 帧正确解析并累积正文。旧的 strstr("\r\n0") 判断会在
         * keep-alive 连接上误判/空转到超时（弹幕接口实测等 60 秒）。 */
        int pos = body_off;
        int done = 0;
        int stall = 0;

        body = (char *)malloc((size_t)(total - body_off) + 64);
        blen = 0;
        if (body == NULL) {
            free(raw);
            wt_err(err, errcap, "内存不足");
            return -1;
        }
        while (!done) {
            int nl = -1;
            int i;
            long csize = 0;

            for (i = pos; i + 1 < total; i++) {
                if (raw[i] == '\r' && raw[i + 1] == '\n') {
                    nl = i;
                    break;
                }
            }
            if (nl < 0) {
                if (total >= WT_MAX_RESP - 1) { done = 1; break; }
                ret = wolfSSL_read(s_ssl, raw + total, WT_MAX_RESP - 1 - total);
                if (ret > 0) { total += ret; raw[total] = '\0'; stall = 0; continue; }
                if (ret == 0) { done = 1; break; }
                if (wolfSSL_get_error(s_ssl, ret) == WOLFSSL_ERROR_WANT_READ) {
                    if (stall++ >= 1) { done = 1; break; }
                    continue;
                }
                done = 1;
                break;
            }
            for (i = pos; i < nl; i++) {
                int c = (unsigned char)raw[i];
                int v = -1;
                if (c >= '0' && c <= '9') v = c - '0';
                else if (c >= 'a' && c <= 'f') v = c - 'a' + 10;
                else if (c >= 'A' && c <= 'F') v = c - 'A' + 10;
                if (v < 0) break;
                csize = csize * 16 + v;
            }
            pos = nl + 2;
            if (csize <= 0) { done = 1; break; }
            while ((long)(total - pos) < csize + 2) {
                if (total >= WT_MAX_RESP - 1) { done = 1; break; }
                ret = wolfSSL_read(s_ssl, raw + total, WT_MAX_RESP - 1 - total);
                if (ret > 0) { total += ret; raw[total] = '\0'; stall = 0; continue; }
                if (ret == 0) { done = 1; break; }
                if (wolfSSL_get_error(s_ssl, ret) == WOLFSSL_ERROR_WANT_READ) {
                    if (stall++ >= 1) { done = 1; break; }
                    continue;
                }
                done = 1;
                break;
            }
            if (done) break;
            {
                char *nb = (char *)realloc(body,
                                           (size_t)blen + (size_t)csize + 1);
                if (nb == NULL) { free(body); body = NULL; done = 1; break; }
                body = nb;
            }
            memcpy(body + blen, raw + pos, (size_t)csize);
            blen += (int)csize;
            pos += csize + 2;
        }
        free(raw);
        if (body == NULL) {
            wt_err(err, errcap, "内存不足");
            return -1;
        }
        body[blen] = '\0';
        if (!keep) {
            wt_close();
        }
        *out_body = body;
        *out_len = blen;
        return 0;
    } else if (clen >= 0) {
        while ((long)(total - body_off) < clen && total < WT_MAX_RESP - 1) {
            ret = wolfSSL_read(s_ssl, raw + total, WT_MAX_RESP - 1 - total);
            if (ret > 0) {
                total += ret;
                continue;
            }
            if (ret == 0) {
                break;
            }
            if (wolfSSL_get_error(s_ssl, ret) == WOLFSSL_ERROR_WANT_READ) {
                continue;
            }
            break;
        }
        if ((long)(total - body_off) > clen) {
            total = body_off + (int)clen;
        }
    } else {
        /* 无长度：读到关闭（不能复用） */
        int stall = 0;
        while (total < WT_MAX_RESP - 1) {
            ret = wolfSSL_read(s_ssl, raw + total, WT_MAX_RESP - 1 - total);
            if (ret > 0) {
                total += ret;
                stall = 0;
                continue;
            }
            if (ret == 0) {
                break;
            }
            if (wolfSSL_get_error(s_ssl, ret) == WOLFSSL_ERROR_WANT_READ) {
                if (stall++ >= 1) {
                    break;
                }
                continue;
            }
            break;
        }
        keep = 0;
    }
    raw[total] = '\0';

    /* 3) 取正文 */
    if (chunked) {
        body = wt_dechunk(raw + body_off, total - body_off, &blen);
        if (body == NULL) {
            free(raw);
            wt_err(err, errcap, "内存不足");
            return -1;
        }
    } else {
        blen = total - body_off;
        if (blen < 0) {
            blen = 0;
        }
        body = (char *)malloc((size_t)blen + 1);
        if (body == NULL) {
            free(raw);
            wt_err(err, errcap, "内存不足");
            return -1;
        }
        memcpy(body, raw + body_off, (size_t)blen);
        body[blen] = '\0';
    }
    free(raw);

    if (!keep) {
        wt_close();
    }
    *out_body = body;
    *out_len = blen;
    return 0;
}

static int wt_https_req(const char *method, const char *url,
                        const char *extra_headers,
                        const char *body, int body_len,
                        char **out_body, int *out_len, int *out_status,
                        char *err, int errcap)
{
    static char path[WT_MAX_URL];
    static char req[4096];
    char host[256];
    const char *p;
    const char *slash;
    const char *colon;
    int hlen;
    int port = 443;
    int attempt;
    int ret;

    *out_body = NULL;
    *out_len = 0;
    *out_status = 0;

    if (strncmp(url, "https://", 8) != 0) {
        wt_err(err, errcap, "不是 https 地址");
        return HTTP_ERR_ARG;
    }
    p = url + 8;
    slash = strchr(p, '/');
    if (slash == NULL) {
        str_copy(path, (int)sizeof(path), "/");
        slash = p + strlen(p);
    } else {
        str_copy(path, (int)sizeof(path), slash);
    }
    colon = strchr(p, ':');
    if (colon != NULL && colon < slash) {
        hlen = (int)(colon - p);
        port = atoi(colon + 1);
        if (port <= 0 || port > 65535) {
            port = 443;
        }
    } else {
        hlen = (int)(slash - p);
    }
    if (hlen <= 0 || hlen >= (int)sizeof(host)) {
        wt_err(err, errcap, "https 地址格式不支持");
        return HTTP_ERR_ARG;
    }
    memcpy(host, p, (size_t)hlen);
    host[hlen] = '\0';

    for (attempt = 0; attempt < 2; attempt++) {
        if (wt_ensure_conn(host, port, err, errcap) != 0) {
            return HTTP_ERR_CONNECT;
        }

        req[0] = '\0';
        str_append(req, (int)sizeof(req), method);
        str_append(req, (int)sizeof(req), " ");
        str_append(req, (int)sizeof(req), path);
        str_append(req, (int)sizeof(req), " HTTP/1.1\r\nHost: ");
        str_append(req, (int)sizeof(req), host);
        if (extra_headers == NULL ||
            strstr(extra_headers, "User-Agent:") == NULL) {
            str_append(req, (int)sizeof(req),
                       "\r\nUser-Agent: Mozilla/5.0 (Windows CE 6.0; ARM) BiliClassicM8/0.1");
        }
        str_append(req, (int)sizeof(req),
                   "\r\nAccept: application/json, text/plain, */*"
                   "\r\nAccept-Encoding: identity\r\n");
        if (extra_headers != NULL) {
            str_append(req, (int)sizeof(req), extra_headers);
        }
        if (body != NULL && body_len > 0) {
            char cl[48];

            sprintf(cl, "Content-Type: application/x-www-form-urlencoded; charset=utf-8\r\n"
                        "Content-Length: %d\r\n", body_len);
            str_append(req, (int)sizeof(req), cl);
        }
        str_append(req, (int)sizeof(req), "Connection: keep-alive\r\n\r\n");
        if (body != NULL && body_len > 0) {
            str_append(req, (int)sizeof(req), body);
        }

        ret = wolfSSL_write(s_ssl, req, (int)strlen(req));
        if (ret <= 0) {
            /* 复用连接可能已被服务器关掉：重连一次 */
            wt_close();
            continue;
        }
        if (wt_read_response(out_body, out_len, out_status,
                             err, errcap) == 0) {
            return HTTP_OK;
        }
        /* 读失败（多半是复用连接坏了）：重连一次 */
        wt_close();
    }
    if (err != NULL && err[0] == '\0') {
        wt_err(err, errcap, "HTTPS 请求失败");
    }
    return HTTP_ERR_CONNECT;
}


int http_get_https(const char *url, const char *extra_headers,
                   char **out_body, int *out_len, int *out_status,
                   char *err, int errcap)
{
    return wt_https_req("GET", url, extra_headers, NULL, 0,
                        out_body, out_len, out_status, err, errcap);
}

int http_post_https(const char *url, const char *extra_headers,
                    const char *body, int body_len,
                    char **out_body, int *out_len, int *out_status,
                    char *err, int errcap)
{
    return wt_https_req("POST", url, extra_headers, body, body_len,
                        out_body, out_len, out_status, err, errcap);
}
