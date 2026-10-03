/* =====================================================================
 * http.c - 极简 HTTP 客户端（Winsock 2，Windows CE 6.0 / 魅族 M8）
 * ---------------------------------------------------------------------
 *  - 只用 ws2.dll（WinCE 自带 Winsock 2），不依赖 IE/TLS
 *  - HTTP/1.1 + Connection: close
 *  - Content-Length 或 chunked 都能处理
 *  - 3xx 自动跟随（https 目标降级成明文 http，走中继）
 *  - 不发送 Accept-Encoding（避免 gzip）
 *  - 大缓冲一律 static/heap
 * ===================================================================== */
#include <winsock2.h>
#include "http.h"
#include "util.h"
#include <windows.h>
#include <sslsock.h>       /* 保留 CE Winsock SSL 代码备查（本 ROM 不可用） */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* M8 coredll has no ANSI(A) exports: GBK path -> UTF-16, use W APIs */
static void bc_a2w(const char *src, wchar_t *dst, int cap)
{
    MultiByteToWideChar(CP_ACP, 0, src, -1, dst, cap);
}

static HANDLE bc_open_dst(const char *path, DWORD disp)
{
    wchar_t w[640];
    bc_a2w(path, w, 640);
    return CreateFileW(w, GENERIC_WRITE,
                       FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                       disp, FILE_ATTRIBUTE_NORMAL, NULL);
}

/* 写文件（边下边播时文件同时被播放器读，必须用共享句柄而不是 fopen） */
static int bc_write_all(HANDLE h, const void *p, int n)
{
    const char *q = (const char *)p;
    while (n > 0) {
        DWORD wrote = 0;
        if (!WriteFile(h, q, (DWORD)n, &wrote, NULL) || wrote == 0) {
            return 0;
        }
        q += wrote;
        n -= (int)wrote;
    }
    return 1;
}

static void bc_del(const char *path)
{
    wchar_t w[640];
    bc_a2w(path, w, 640);
    DeleteFileW(w);
}

static void (*s_log)(const char *msg) = NULL;
static volatile DWORD s_quiet_tid = 0;   /* 静音这个线程的 http 日志 */
static int s_wsa_ok = 0;
static int s_timeout_ms = 30000;
static int s_conns = 4;     /* 下载并行连接数（1=关） */

#define MAX_RESP        (2 * 1024 * 1024)
#define MAX_HDR_BUF     8192
#define MAX_URL_LEN     4600
#define MAX_REDIRECTS   4

void http_set_log(void (*fn)(const char *msg))
{
    s_log = fn;
}

void http_set_quiet(int on)
{
    s_quiet_tid = on ? GetCurrentThreadId() : 0;
}

void http_set_timeout(int ms)
{
    s_timeout_ms = ms;
}

void http_set_conns(int n)
{
    if (n < 1) {
        n = 1;
    }
    if (n > 8) {
        n = 8;
    }
    s_conns = n;
}

static void hlog(const char *msg)
{
    if (s_quiet_tid != 0 && s_quiet_tid == GetCurrentThreadId()) {
        return;
    }
    if (s_log != NULL) {
        s_log(msg);
    }
}

void http_init(void)
{
    WSADATA wsa;
    if (s_wsa_ok) {
        return;
    }
    if (WSAStartup(MAKEWORD(2, 2), &wsa) == 0) {
        s_wsa_ok = 1;
    }
}

void http_done(void)
{
    if (s_wsa_ok) {
        WSACleanup();
        s_wsa_ok = 0;
    }
}

static void set_err(char *err, int errcap, const char *msg)
{
    if (err != NULL && errcap > 0) {
        str_copy(err, errcap, msg);
    }
}

/* ---------------- URL 处理 ---------------- */

static int bc_split_url(const char *url, char *host, int hostcap,
                        int *port, char *path, int pathcap)
{
    const char *p;
    const char *slash;
    const char *colon;
    int hlen;

    host[0] = '\0';
    path[0] = '\0';
    *port = 80;

    if (strncmp(url, "http://", 7) == 0) {
        p = url + 7;
    } else if (strncmp(url, "https://", 8) == 0) {
        /* Win95 没有 TLS：一律按明文 http 访问（CDN 两种都支持） */
        p = url + 8;
    } else {
        return 0;
    }

    slash = strchr(p, '/');
    if (slash == NULL) {
        if (pathcap >= 2) {
            path[0] = '/';
            path[1] = '\0';
        }
        slash = p + strlen(p);
    } else {
        str_copy(path, pathcap, slash);
    }

    colon = strchr(p, ':');
    if (colon != NULL && colon < slash) {
        hlen = (int)(colon - p);
        *port = atoi(colon + 1);
        if (*port == 0 || *port == 443) {
            *port = 80;     /* https 默认端口也按明文 80 走 */
        }
    } else {
        hlen = (int)(slash - p);
    }
    if (hlen <= 0 || hlen >= hostcap) {
        return 0;
    }
    memcpy(host, p, (size_t)hlen);
    host[hlen] = '\0';
    return 1;
}

static void bc_build_url(const char *host, int port, const char *path,
                         char *out, int outcap)
{
    out[0] = '\0';
    str_append(out, outcap, "http://");
    str_append(out, outcap, host);
    if (port != 80) {
        char pb[16];
        sprintf(pb, ":%d", port);
        str_append(out, outcap, pb);
    }
    str_append(out, outcap, path);
}

/* 把 Location 变成绝对 URL；只支持绝对地址和 "/xxx" */
static int bc_resolve_location(const char *base, const char *loc,
                               char *out, int outcap)
{
    char host[128];
    static char path[MAX_URL_LEN];
    int port = 80;

    if (loc == NULL || loc[0] == '\0') {
        return 0;
    }
    if (strncmp(loc, "http://", 7) == 0 || strncmp(loc, "https://", 8) == 0) {
        if (!bc_split_url(loc, host, (int)sizeof(host), &port, path, (int)sizeof(path))) {
            return 0;
        }
        bc_build_url(host, port, path, out, outcap);
        return 1;
    }
    if (loc[0] == '/') {
        if (!bc_split_url(base, host, (int)sizeof(host), &port, path, (int)sizeof(path))) {
            return 0;
        }
        bc_build_url(host, port, loc, out, outcap);
        return 1;
    }
    return 0;
}

/* ---------------- TCP ---------------- */

static int tcp_connect(const char *host, int port, SOCKET *out_sock)
{
    struct hostent *he;
    struct sockaddr_in sa;
    SOCKET s;

    he = gethostbyname(host);
    if (he == NULL || he->h_addr_list == NULL || he->h_addr_list[0] == NULL) {
        hlog("dns failed");
        return HTTP_ERR_DNS;
    }
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons((unsigned short)port);
    memcpy(&sa.sin_addr, he->h_addr_list[0], 4);

    s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (s == INVALID_SOCKET) {
        hlog("socket failed");
        return HTTP_ERR_SOCKET;
    }
    if (s_timeout_ms > 0) {
        int tv = s_timeout_ms;
        setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, (const char *)&tv, sizeof(tv));
        setsockopt(s, SOL_SOCKET, SO_SNDTIMEO, (const char *)&tv, sizeof(tv));
    }
    {
        /* Win95 默认接收窗口很小（几 KB），高延迟下一连接只能跑一两百 KB/s。
         * 设大一点（无窗口缩放时上限 64KB）能明显提速。必须在 connect 前设。 */
        int rb = 65536;
        int sb = 32768;
        setsockopt(s, SOL_SOCKET, SO_RCVBUF, (const char *)&rb, sizeof(rb));
        setsockopt(s, SOL_SOCKET, SO_SNDBUF, (const char *)&sb, sizeof(sb));
    }
    if (connect(s, (struct sockaddr *)&sa, sizeof(sa)) != 0) {
        closesocket(s);
        hlog("connect failed");
        return HTTP_ERR_CONNECT;
    }
    *out_sock = s;
    return HTTP_OK;
}

/* 一律接受服务器证书：CE6 根证书库旧，B 站证书链未必对得上 */
static int CALLBACK bc_cert_ok(DWORD dwType, LPVOID pvArg, DWORD dwChainLen,
                               LPBLOB pCertChain, DWORD dwFlags)
{
    (void)dwType; (void)pvArg; (void)dwChainLen;
    (void)pCertChain; (void)dwFlags;
    return SSL_ERR_OKAY;
}

/* TCP + SSL（CE Winsock SSL：SO_SEC_SSL）。SSL 不支持时返回错误，调用方回退中继。 */
static int tcp_connect_ssl(const char *host, int port, SOCKET *out_sock)
{
    struct hostent *he;
    struct sockaddr_in sa;
    SOCKET s;
    DWORD dwSecure = SO_SEC_SSL;
    DWORD cb = 0;
    SSLVALIDATECERTHOOK hook;
    SSLPROTOCOLS protos;

    he = gethostbyname(host);
    if (he == NULL || he->h_addr_list == NULL || he->h_addr_list[0] == NULL) {
        hlog("ssl: dns failed");
        return HTTP_ERR_DNS;
    }
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons((unsigned short)port);
    memcpy(&sa.sin_addr, he->h_addr_list[0], 4);

    s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (s == INVALID_SOCKET) {
        hlog("ssl: socket failed");
        return HTTP_ERR_SOCKET;
    }
    if (s_timeout_ms > 0) {
        int tv = s_timeout_ms;
        setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, (const char *)&tv, sizeof(tv));
        setsockopt(s, SOL_SOCKET, SO_SNDTIMEO, (const char *)&tv, sizeof(tv));
    }
    {
        int rb = 65536;
        int sb = 32768;
        setsockopt(s, SOL_SOCKET, SO_RCVBUF, (const char *)&rb, sizeof(rb));
        setsockopt(s, SOL_SOCKET, SO_SNDBUF, (const char *)&sb, sizeof(sb));
    }

    hlog("ssl: setsockopt SO_SECURE");
    if (setsockopt(s, SOL_SOCKET, SO_SECURE, (const char *)&dwSecure,
                   sizeof(dwSecure)) != 0) {
        closesocket(s);
        hlog("ssl: SO_SECURE unsupported");
        return HTTP_ERR_SOCKET;
    }

    /* 关键：延迟握手。connect 只做 TCP（SSL socket 直接阻塞 connect 会
     * WSAEINVAL），TLS 握手留给后面的 SO_SSL_PERFORM_HANDSHAKE 显式做 */
    {
        DWORD sslFlags = SSL_FLAG_DEFER_HANDSHAKE;
        if (WSAIoctl(s, SO_SSL_SET_FLAGS, &sslFlags, sizeof(sslFlags),
                     NULL, 0, &cb, NULL, NULL) == SOCKET_ERROR) {
            hlog("ssl: set flags failed");
        }
    }

    protos.dwCount = 1;
    protos.ProtocolList[0].dwProtocol = SSL_PROTOCOL_TLS1;
    protos.ProtocolList[0].dwVersion = 0;
    protos.ProtocolList[0].dwFlags = 0;
    if (WSAIoctl(s, SO_SSL_SET_PROTOCOLS, &protos, sizeof(protos),
                 NULL, 0, &cb, NULL, NULL) == SOCKET_ERROR) {
        hlog("ssl: set protocols failed");
    }

    hook.HookFunc = bc_cert_ok;
    hook.pvArg = NULL;
    if (WSAIoctl(s, SO_SSL_SET_VALIDATE_CERT_HOOK, &hook, sizeof(hook),
                 NULL, 0, &cb, NULL, NULL) == SOCKET_ERROR) {
        hlog("ssl: set cert hook failed");
    }

    hlog("ssl: connect");
    if (connect(s, (struct sockaddr *)&sa, sizeof(sa)) != 0) {
        char em[48];
        sprintf(em, "ssl: connect failed wsa=%d", WSAGetLastError());
        hlog(em);
        closesocket(s);
        return HTTP_ERR_CONNECT;
    }

    hlog("ssl: perform handshake");
    if (WSAIoctl(s, SO_SSL_PERFORM_HANDSHAKE, NULL, 0, NULL, 0,
                 &cb, NULL, NULL) == SOCKET_ERROR) {
        char em[48];
        sprintf(em, "ssl: handshake failed wsa=%d", WSAGetLastError());
        hlog(em);
        closesocket(s);
        return HTTP_ERR_CONNECT;
    }

    hlog("ssl: connected");
    *out_sock = s;
    return HTTP_OK;
}

static int send_all(SOCKET s, const char *buf, int len)
{
    int sent = 0;
    while (sent < len) {
        int n = send(s, buf + sent, len - sent, 0);
        if (n == SOCKET_ERROR || n <= 0) {
            char em[48];
            sprintf(em, "send_all failed wsa=%d", WSAGetLastError());
            hlog(em);
            return HTTP_ERR_SEND;
        }
        sent += n;
    }
    return HTTP_OK;
}

/* 读响应到 buf；*io_len 为已读长度（可续读）。stop_at_headers=1 时找到
 * \r\n\r\n 立即返回（返回 header 结束偏移），否则读到断开/满。 */
static int fill_buffer(SOCKET s, char *buf, int cap, int *io_len, int stop_at_headers)
{
    int len = *io_len;
    int header_end = -1;

    while (len < cap - 1) {
        int n = recv(s, buf + len, cap - 1 - len, 0);
        if (n <= 0) {
            if (n < 0) {
                char em[48];
                sprintf(em, "recv failed wsa=%d", WSAGetLastError());
                hlog(em);
            }
            break;
        }
        len += n;
        buf[len] = '\0';
        if (stop_at_headers) {
            char *p = strstr(buf, "\r\n\r\n");
            if (p != NULL) {
                header_end = (int)(p - buf) + 4;
                break;
            }
        }
    }
    *io_len = len;
    return header_end;
}

/* chunked 解码（原地 -> 新缓冲） */
static int dechunk(const char *in, int in_len, char **out, int *out_len)
{
    char *dst = (char *)malloc((size_t)in_len + 1);
    int i = 0;
    int di = 0;

    if (dst == NULL) {
        return HTTP_ERR_MEM;
    }
    while (i < in_len) {
        int chunk = 0;
        int digits = 0;
        while (i < in_len) {
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
        if (digits == 0) {
            break;
        }
        while (i < in_len && in[i] != '\n') i++;
        i++;
        if (chunk <= 0) {
            break;
        }
        if (i + chunk > in_len) {
            chunk = in_len - i;
        }
        memcpy(dst + di, in + i, (size_t)chunk);
        di += chunk;
        i += chunk;
        while (i < in_len && (in[i] == '\r' || in[i] == '\n')) i++;
    }
    dst[di] = '\0';
    *out = dst;
    *out_len = di;
    return HTTP_OK;
}

static int parse_status(const char *headers, int *status)
{
    const char *p = strchr(headers, ' ');
    if (p == NULL) {
        return HTTP_ERR_FORMAT;
    }
    *status = atoi(p + 1);
    return HTTP_OK;
}

static int get_header_int(const char *headers, const char *name, long *out)
{
    char pat[64];
    const char *p;
    str_copy(pat, (int)sizeof(pat), name);
    str_append(pat, (int)sizeof(pat), ":");
    p = headers;
    for (;;) {
        const char *q = strstr(p, pat);
        if (q == NULL) {
            return 0;
        }
        if (q == headers || (q > headers && q[-1] == '\n')) {
            *out = atol(q + (int)strlen(pat));
            return 1;
        }
        p = q + 1;
    }
}

static int chr_lower(int c)
{
    if (c >= 'A' && c <= 'Z') {
        return c - 'A' + 'a';
    }
    return c;
}

/* 取头字段字符串值（大小写不敏感的名字） */
static int get_header_string(const char *headers, const char *name,
                             char *out, int cap)
{
    int nl = (int)strlen(name);
    const char *p = headers;

    if (cap > 0) {
        out[0] = '\0';
    }
    while (*p != '\0') {
        const char *eol = strchr(p, '\n');
        int i;
        int match = 1;
        for (i = 0; i < nl; i++) {
            if (chr_lower((unsigned char)p[i]) != chr_lower((unsigned char)name[i])) {
                match = 0;
                break;
            }
        }
        if (match && p[nl] == ':') {
            const char *v = p + nl + 1;
            int j = 0;
            while (*v == ' ' || *v == '\t') {
                v++;
            }
            while (*v != '\0' && *v != '\r' && *v != '\n' && j < cap - 1) {
                out[j++] = *v++;
            }
            out[j] = '\0';
            return 1;
        }
        if (eol == NULL) {
            break;
        }
        p = eol + 1;
    }
    return 0;
}

static int header_contains(const char *headers, const char *needle)
{
    return strstr(headers, needle) != NULL;
}

/* ---------------- 底层请求 ---------------- */

static int http_exchange(const char *host, int port,
                         const char *method, const char *path,
                         const char *extra_headers,
                         const char *body, int body_len,
                         char **out_body, int *out_len, int *out_status,
                         char *out_headers, int hdr_cap, int secure)
{
    SOCKET s = INVALID_SOCKET;
    char *raw = NULL;
    static char req[4096];
    int rc;
    int raw_len = 0;
    int header_end = -1;
    long content_len = -1;

    *out_body = NULL;
    *out_len = 0;
    *out_status = 0;
    if (out_headers != NULL && hdr_cap > 0) {
        out_headers[0] = '\0';
    }

    if (!s_wsa_ok) {
        http_init();
    }

    rc = secure ? tcp_connect_ssl(host, port, &s)
                : tcp_connect(host, port, &s);
    if (rc != HTTP_OK) {
        return rc;
    }

    req[0] = '\0';
    str_append(req, (int)sizeof(req), method);
    str_append(req, (int)sizeof(req), " ");
    str_append(req, (int)sizeof(req), path);
    str_append(req, (int)sizeof(req), " HTTP/1.1\r\nHost: ");
    str_append(req, (int)sizeof(req), host);
    if (port != 80 && port != 443) {
        char pb[16];
        sprintf(pb, ":%d", port);
        str_append(req, (int)sizeof(req), pb);
    }
    str_append(req, (int)sizeof(req), "\r\n");
    str_append(req, (int)sizeof(req), "User-Agent: Mozilla/5.0 (Windows CE 6.0; ARM) BiliClassicM8/0.1\r\n");
    str_append(req, (int)sizeof(req), "Accept: application/json, text/plain, */*\r\n");
    if (extra_headers != NULL) {
        str_append(req, (int)sizeof(req), extra_headers);
    }
    if (body != NULL && body_len > 0) {
        char lb[32];
        sprintf(lb, "Content-Length: %d\r\n", body_len);
        str_append(req, (int)sizeof(req), lb);
    }
    str_append(req, (int)sizeof(req), "Connection: close\r\n\r\n");

    hlog(secure ? "http: send request (ssl)" : "http: send request");
    rc = send_all(s, req, (int)strlen(req));
    if (rc == HTTP_OK && body != NULL && body_len > 0) {
        rc = send_all(s, body, body_len);
    }
    if (rc != HTTP_OK) {
        hlog("http: send failed");
        closesocket(s);
        return rc;
    }

    raw = (char *)malloc((size_t)MAX_RESP);
    if (raw == NULL) {
        closesocket(s);
        return HTTP_ERR_MEM;
    }
    hlog("http: read headers");
    header_end = fill_buffer(s, raw, MAX_RESP, &raw_len, 1);
    if (header_end < 0) {
        header_end = fill_buffer(s, raw, MAX_RESP, &raw_len, 0);
    }
    if (header_end < 0) {
        char em[48];
        sprintf(em, "http: no header (rx %d)", raw_len);
        hlog(em);
        free(raw);
        closesocket(s);
        return HTTP_ERR_FORMAT;
    }
    {
        char sm[48];
        int st = 0;
        parse_status(raw, &st);
        sprintf(sm, "http: status %d", st);
        hlog(sm);
    }

    if (out_headers != NULL && hdr_cap > 0) {
        int n = header_end;
        if (n > hdr_cap - 1) {
            n = hdr_cap - 1;
        }
        memcpy(out_headers, raw, (size_t)n);
        out_headers[n] = '\0';
    }

    parse_status(raw, out_status);

    if (header_contains(raw, "Transfer-Encoding: chunked") ||
        header_contains(raw, "transfer-encoding: chunked")) {
        int total = raw_len;
        while (total < MAX_RESP - 1) {
            int n = recv(s, raw + total, MAX_RESP - 1 - total, 0);
            if (n <= 0) {
                break;
            }
            total += n;
            raw[total] = '\0';
        }
        rc = dechunk(raw + header_end, total - header_end, out_body, out_len);
        free(raw);
        closesocket(s);
        return rc;
    }

    if (get_header_int(raw, "Content-Length", &content_len) && content_len >= 0) {
        int body_have = raw_len - header_end;
        while (body_have < (int)content_len && raw_len < MAX_RESP - 1) {
            int n = recv(s, raw + raw_len, MAX_RESP - 1 - raw_len, 0);
            if (n <= 0) {
                break;
            }
            raw_len += n;
            body_have += n;
            raw[raw_len] = '\0';
        }
        if (body_have > (int)content_len) {
            body_have = (int)content_len;
        }
        *out_body = (char *)malloc((size_t)body_have + 1);
        if (*out_body == NULL) {
            free(raw);
            closesocket(s);
            return HTTP_ERR_MEM;
        }
        memcpy(*out_body, raw + header_end, (size_t)body_have);
        (*out_body)[body_have] = '\0';
        *out_len = body_have;
    } else {
        int total = raw_len;
        int body_have;
        while (total < MAX_RESP - 1) {
            int n = recv(s, raw + total, MAX_RESP - 1 - total, 0);
            if (n <= 0) {
                break;
            }
            total += n;
            raw[total] = '\0';
        }
        body_have = total - header_end;
        *out_body = (char *)malloc((size_t)body_have + 1);
        if (*out_body == NULL) {
            free(raw);
            closesocket(s);
            return HTTP_ERR_MEM;
        }
        memcpy(*out_body, raw + header_end, (size_t)body_have);
        (*out_body)[body_have] = '\0';
        *out_len = body_have;
    }

    free(raw);
    closesocket(s);
    return HTTP_OK;
}

int http_request(const char *host, int port,
                 const char *method, const char *path,
                 const char *extra_headers,
                 const char *body, int body_len,
                 char **out_body, int *out_len, int *out_status)
{
    return http_exchange(host, port, method, path, extra_headers,
                         body, body_len, out_body, out_len, out_status,
                         NULL, 0, 0);
}

/* ---------------- 跟随跳转的 GET ---------------- */

int http_get_url(const char *url, const char *extra_headers,
                 char **out_body, int *out_len, int *out_status,
                 char *err, int errcap)
{
    static char cur[MAX_URL_LEN];
    static char hdr[MAX_HDR_BUF];
    static char path[MAX_URL_LEN];
    int hops;

    *out_body = NULL;
    *out_len = 0;
    *out_status = 0;
    str_copy(cur, (int)sizeof(cur), url);

    for (hops = 0; hops <= MAX_REDIRECTS; hops++) {
        char host[128];
        int port = 80;
        char *body = NULL;
        int len = 0;
        int status = 0;
        int rc;

        if (!bc_split_url(cur, host, (int)sizeof(host), &port, path, (int)sizeof(path))) {
            set_err(err, errcap, "地址格式不支持");
            return HTTP_ERR_ARG;
        }
        rc = http_exchange(host, port, "GET", path, extra_headers, NULL, 0,
                           &body, &len, &status, hdr, (int)sizeof(hdr), 0);
        if (rc != HTTP_OK) {
            set_err(err, errcap, "连接/接收失败");
            return rc;
        }
        if (status >= 300 && status < 400) {
            static char loc[MAX_URL_LEN];
            if (!get_header_string(hdr, "Location", loc, (int)sizeof(loc))) {
                set_err(err, errcap, "跳转但缺少 Location");
                if (body != NULL) free(body);
                return HTTP_ERR_REDIR;
            }
            if (!bc_resolve_location(cur, loc, cur, (int)sizeof(cur))) {
                set_err(err, errcap, "跳转地址无法处理");
                if (body != NULL) free(body);
                return HTTP_ERR_REDIR;
            }
            if (body != NULL) {
                free(body);
            }
            continue;
        }
        *out_body = body;
        *out_len = len;
        *out_status = status;
        return HTTP_OK;
    }
    set_err(err, errcap, "跳转次数过多");
    return HTTP_ERR_REDIR;
}

/* ---------------- 并行分段下载 ---------------- */

#define BC_MULTI_MIN   (512 * 1024L)   /* 小于 512KB 不值得并行 */
#define BC_MULTI_BUF   (64 * 1024)

typedef struct {
    char host[128];
    int  port;
    const char *path;
    const char *extra;
    const char *dst;
    long start;
    long end;              /* 含 */
    HANDLE hf;
    volatile long got;
    volatile int  done;
    int  ok;
    int  status;           /* 诊断用：本段的 HTTP 状态码 */
    int  wsaerr;           /* 诊断用：最后一次 recv 的错误码 */
} BcPart;

static void bc_part_run(BcPart *p)
{
    SOCKET s = INVALID_SOCKET;
    char *buf = NULL;
    char *req = NULL;
    char head[2048];
    char rng[64];
    long want = p->end - p->start + 1;
    long got = 0;
    int reqcap;
    int head_len = 0;
    int he;
    int st = 0;

    p->ok = 0;
    p->got = 0;
    p->done = 0;

    buf = (char *)malloc(BC_MULTI_BUF);
    if (buf == NULL) {
        goto done;
    }
    reqcap = (int)strlen(p->path) + (int)strlen(p->host) + 512;
    req = (char *)malloc((size_t)reqcap);
    if (req == NULL) {
        goto done;
    }
    if (tcp_connect(p->host, p->port, &s) != HTTP_OK) {
        goto done;
    }

    req[0] = '\0';
    str_append(req, reqcap, "GET ");
    str_append(req, reqcap, p->path);
    str_append(req, reqcap, " HTTP/1.1\r\nHost: ");
    str_append(req, reqcap, p->host);
    if (p->port != 80) {
        char pb[16];
        sprintf(pb, ":%d", p->port);
        str_append(req, reqcap, pb);
    }
    str_append(req, reqcap, "\r\nUser-Agent: Mozilla/5.0 (Windows CE 6.0; ARM) BiliClassicM8/0.1\r\n");
    str_append(req, reqcap, "Accept: */*\r\n");
    if (p->extra != NULL) {
        str_append(req, reqcap, p->extra);
    }
    sprintf(rng, "Range: bytes=%ld-%ld\r\n", p->start, p->end);
    str_append(req, reqcap, rng);
    str_append(req, reqcap, "Connection: close\r\n\r\n");

    if (send_all(s, req, (int)strlen(req)) != HTTP_OK) {
        goto done;
    }
    he = fill_buffer(s, head, (int)sizeof(head), &head_len, 1);
    if (he < 0) {
        goto done;
    }
    if (header_contains(head, "chunked") || header_contains(head, "Chunked")) {
        goto done;          /* 分段响应还分块传输，太麻烦，回退单连接 */
    }
    parse_status(head, &st);
    p->status = st;
    if (st != 206) {
        goto done;          /* 服务器没按 Range 给，回退（否则数据会错位） */
    }

    p->hf = bc_open_dst(p->dst, OPEN_ALWAYS);
    if (p->hf == INVALID_HANDLE_VALUE) {
        p->hf = NULL;
        goto done;
    }
    SetFilePointer(p->hf, p->start, NULL, FILE_BEGIN);

    if (head_len > he) {
        /* 响应头里已经夹带了一截正文 */
        long n = (long)(head_len - he);
        DWORD wr = 0;
        if (n > want) {
            n = want;
        }
        WriteFile(p->hf, head + he, (DWORD)n, &wr, NULL);
        got += (long)wr;
        p->got = got;
    }

    while (got < want) {
        int want_rd = (int)((want - got > BC_MULTI_BUF) ? BC_MULTI_BUF : (want - got));
        int n = recv(s, buf, want_rd, 0);
        DWORD wr = 0;
        if (n <= 0) {
            p->wsaerr = WSAGetLastError();
            break;
        }
        if (!WriteFile(p->hf, buf, (DWORD)n, &wr, NULL) || (int)wr != n) {
            p->wsaerr = -1000 - (int)GetLastError();
            break;
        }
        got += (long)wr;
        p->got = got;
    }
    p->ok = (got == want) ? 1 : 0;

done:
    if (p->hf != NULL && p->hf != INVALID_HANDLE_VALUE) {
        CloseHandle(p->hf);
        p->hf = NULL;
    }
    if (s != INVALID_SOCKET) {
        closesocket(s);
    }
    if (buf != NULL) {
        free(buf);
    }
    if (req != NULL) {
        free(req);
    }
    p->done = 1;
}

static DWORD WINAPI bc_part_thread(LPVOID arg)
{
    bc_part_run((BcPart *)arg);
    return 0;
}

/* 探测总大小：Range 探测拿到 Content-Range 里的总长度 */
static int bc_probe_total(const char *host, int port, const char *path,
                          const char *extra, long *out_total)
{
    SOCKET s = INVALID_SOCKET;
    static char req[4096];
    static char head[MAX_HDR_BUF];
    char val[128];
    int head_len = 0;
    int he;
    int st = 0;
    long total = -1;

    *out_total = -1;
    if (!s_wsa_ok) {
        http_init();
    }
    if (tcp_connect(host, port, &s) != HTTP_OK) {
        return HTTP_ERR_CONNECT;
    }

    req[0] = '\0';
    str_append(req, (int)sizeof(req), "GET ");
    str_append(req, (int)sizeof(req), path);
    str_append(req, (int)sizeof(req), " HTTP/1.1\r\nHost: ");
    str_append(req, (int)sizeof(req), host);
    if (port != 80) {
        char pb[16];
        sprintf(pb, ":%d", port);
        str_append(req, (int)sizeof(req), pb);
    }
    str_append(req, (int)sizeof(req), "\r\nUser-Agent: Mozilla/5.0 (Windows CE 6.0; ARM) BiliClassicM8/0.1\r\n");
    str_append(req, (int)sizeof(req), "Accept: */*\r\n");
    if (extra != NULL) {
        str_append(req, (int)sizeof(req), extra);
    }
    str_append(req, (int)sizeof(req), "Range: bytes=0-0\r\nConnection: close\r\n\r\n");

    if (send_all(s, req, (int)strlen(req)) != HTTP_OK) {
        closesocket(s);
        return HTTP_ERR_SEND;
    }
    he = fill_buffer(s, head, (int)sizeof(head), &head_len, 1);
    closesocket(s);
    if (he < 0) {
        return HTTP_ERR_FORMAT;
    }
    parse_status(head, &st);
    if (st == 206 && get_header_string(head, "Content-Range", val, (int)sizeof(val))) {
        char *p = strchr(val, '/');
        if (p != NULL) {
            total = atol(p + 1);
        }
    } else if (st == 200) {
        long cl = -1;
        if (get_header_int(head, "Content-Length", &cl)) {
            total = cl;
        }
    }
    *out_total = total;
    return (total > 0) ? HTTP_OK : HTTP_ERR_ARG;
}

/* 多连接分段下载：HTTP_OK=成功；HTTP_ERR_ARG=不适用（请走单连接） */
static int bc_multi_to_file(const char *host, int port, const char *path,
                            const char *extra, const char *dst_path,
                            void (*progress)(long done, long total),
                            long *out_total, char *err, int errcap)
{
    long total = -1;
    long last_report = -1;
    int nconn;
    int i;
    int attempt;
    BcPart *parts;
    HANDLE th[8];

    if (s_conns < 2) {
        return HTTP_ERR_ARG;
    }
    if (bc_probe_total(host, port, path, extra, &total) != HTTP_OK) {
        return HTTP_ERR_ARG;
    }
    if (total < BC_MULTI_MIN) {
        return HTTP_ERR_ARG;
    }
    nconn = (s_conns > 8) ? 8 : s_conns;
    while (nconn > 2 && total / nconn < 65536L) {
        nconn--;
    }
    if (nconn < 2) {
        return HTTP_ERR_ARG;
    }

    parts = (BcPart *)malloc(sizeof(BcPart) * (size_t)nconn);
    if (parts == NULL) {
        return HTTP_ERR_ARG;
    }
    memset(parts, 0, sizeof(BcPart) * (size_t)nconn);
    memset(th, 0, sizeof(th));
    for (i = 0; i < nconn; i++) {
        str_copy(parts[i].host, (int)sizeof(parts[i].host), host);
        parts[i].port = port;
        parts[i].path = path;
        parts[i].extra = extra;
        parts[i].dst = dst_path;
        parts[i].start = total * i / nconn;
        parts[i].end = (i == nconn - 1) ? (total - 1)
                                        : (total * (i + 1) / nconn - 1);
        parts[i].hf = NULL;
    }

    for (attempt = 0; attempt < 2; attempt++) {
        int started = 0;
        int allok = 1;

        for (i = 0; i < nconn; i++) {
            DWORD tid = 0;
            if (parts[i].ok) {
                continue;               /* 上一轮已经拿全的段不用再动 */
            }
            parts[i].got = 0;
            parts[i].done = 0;
            parts[i].hf = NULL;
            parts[i].wsaerr = 0;
            th[i] = CreateThread(NULL, 0, bc_part_thread,
                                            &parts[i], 0, &tid);
            if (th[i] == NULL) {
                parts[i].done = 1;      /* 起不来就标记失败，别让主循环干等 */
            } else {
                started++;
            }
        }
        if (started == 0) {
            break;
        }

        for (;;) {
            long sum = 0;
            int alldone = 1;
            for (i = 0; i < nconn; i++) {
                sum += parts[i].got;
                if (!parts[i].done) {
                    alldone = 0;
                }
            }
            if (progress != NULL && sum != last_report) {
                last_report = sum;
                progress(sum, total);   /* 只在主线程回调，别在线程里碰界面 */
            }
            if (alldone) {
                break;
            }
            Sleep(200);
        }
        for (i = 0; i < nconn; i++) {
            if (th[i] != NULL) {
                WaitForSingleObject(th[i], 30000);
                CloseHandle(th[i]);
                th[i] = NULL;
            }
        }
        for (i = 0; i < nconn; i++) {
            if (!parts[i].ok) {
                allok = 0;
            }
        }
        if (allok) {
            free(parts);
            if (out_total != NULL) {
                *out_total = total;
            }
            if (progress != NULL) {
                progress(total, total);
            }
            return HTTP_OK;
        }
        if (attempt == 0) {
            hlog("multi: retry failed parts");
        }
    }

    {
        int nok = 0;
        int bad = -1;
        char msg[240];
        for (i = 0; i < nconn; i++) {
            if (parts[i].ok) {
                nok++;
            } else if (bad < 0) {
                bad = i;
            }
        }
        sprintf(msg, "multi fail: %d/%d ok; part %d st=%d got=%ld/%ld wsa=%d",
                nok, nconn, bad + 1, (bad >= 0) ? parts[bad].status : 0,
                (bad >= 0) ? parts[bad].got : 0L,
                (bad >= 0) ? (parts[bad].end - parts[bad].start + 1) : 0L,
                (bad >= 0) ? parts[bad].wsaerr : 0);
        hlog(msg);
        free(parts);
        set_err(err, errcap, "并行分段下载失败");
        return HTTP_ERR_RECV;
    }
}

/* ---------------- 下载到文件 ---------------- */

/* 单次请求：把响应体流式写入文件（不含跳转） */
static int http_path_to_file(const char *host, int port, const char *path,
                             const char *extra_headers, const char *dst_path,
                             void (*progress)(long done, long total),
                             long *out_total,
                             char *out_headers, int hdr_cap,
                             char *err, int errcap)
{
    SOCKET s = INVALID_SOCKET;
    static char req[4096];
    static char head[MAX_HDR_BUF];
    char *buf = NULL;
    int head_len = 0;
    int header_end = -1;
    int status = 0;
    long content_len = -1;
    long done = 0;
    HANDLE fh = INVALID_HANDLE_VALUE;
    int rc;
    int chunked = 0;

    if (out_total != NULL) {
        *out_total = 0;
    }
    if (!s_wsa_ok) {
        http_init();
    }
    /* 先试并行分段：服务器不支持 Range / 文件太小 会自动回退到单连接 */
    if (s_conns > 1 && dst_path != NULL) {
        int m = bc_multi_to_file(host, port, path, extra_headers, dst_path,
                                 progress, out_total, err, errcap);
        if (m == HTTP_OK) {
            return HTTP_OK;
        }
        if (m != HTTP_ERR_ARG) {
            hlog("multi download failed, fallback to single");
            bc_del(dst_path);
            if (err != NULL) {
                err[0] = '\0';
            }
            if (out_total != NULL) {
                *out_total = 0;
            }
        }
    }
    rc = tcp_connect(host, port, &s);
    if (rc != HTTP_OK) {
        set_err(err, errcap, "连接服务器失败");
        return rc;
    }

    req[0] = '\0';
    str_append(req, (int)sizeof(req), "GET ");
    str_append(req, (int)sizeof(req), path);
    str_append(req, (int)sizeof(req), " HTTP/1.1\r\nHost: ");
    str_append(req, (int)sizeof(req), host);
    if (port != 80) {
        char pb[16];
        sprintf(pb, ":%d", port);
        str_append(req, (int)sizeof(req), pb);
    }
    str_append(req, (int)sizeof(req), "\r\n");
    str_append(req, (int)sizeof(req), "User-Agent: Mozilla/5.0 (Windows CE 6.0; ARM) BiliClassicM8/0.1\r\n");
    str_append(req, (int)sizeof(req), "Accept: */*\r\n");
    if (extra_headers != NULL) {
        str_append(req, (int)sizeof(req), extra_headers);
    }
    str_append(req, (int)sizeof(req), "Connection: close\r\n\r\n");

    rc = send_all(s, req, (int)strlen(req));
    if (rc != HTTP_OK) {
        set_err(err, errcap, "发送请求失败");
        closesocket(s);
        return rc;
    }

    header_end = fill_buffer(s, head, (int)sizeof(head), &head_len, 1);
    if (header_end < 0) {
        set_err(err, errcap, "响应头不完整");
        closesocket(s);
        return HTTP_ERR_FORMAT;
    }
    if (out_headers != NULL && hdr_cap > 0) {
        int n = header_end;
        if (n > hdr_cap - 1) {
            n = hdr_cap - 1;
        }
        memcpy(out_headers, head, (size_t)n);
        out_headers[n] = '\0';
    }
    parse_status(head, &status);
    if (status < 200 || status >= 300) {
        char tmp[64];
        sprintf(tmp, "服务器返回 HTTP %d", status);
        set_err(err, errcap, tmp);
        closesocket(s);
        return HTTP_ERR_FORMAT;
    }
    if (header_contains(head, "Transfer-Encoding: chunked") ||
        header_contains(head, "transfer-encoding: chunked")) {
        chunked = 1;
    }
    get_header_int(head, "Content-Length", &content_len);
    if (content_len > 0 && out_total != NULL) {
        *out_total = content_len;
    }

    fh = bc_open_dst(dst_path, CREATE_ALWAYS);
    if (fh == INVALID_HANDLE_VALUE) {
        set_err(err, errcap, "无法写入文件");
        closesocket(s);
        return HTTP_ERR_FILE;
    }

    if (head_len > header_end) {
        if (chunked) {
            /* 头部里已经带了一部分 chunked 数据，简化处理：先缓存再解 */
            /* 这种情况极少见（响应头恰好和第一块数据一起到达），
             * 直接把剩余部分解出来 */
            char *body = NULL;
            int blen = 0;
            int total = head_len;
            while (total < MAX_HDR_BUF - 1) {
                int n = recv(s, head + total, MAX_HDR_BUF - 1 - total, 0);
                if (n <= 0) break;
                total += n;
                head[total] = '\0';
            }
            if (dechunk(head + header_end, total - header_end, &body, &blen) == HTTP_OK) {
                bc_write_all(fh, body, blen);
                done += blen;
                if (progress != NULL) progress(done, content_len);
                free(body);
            }
        } else {
            int n = head_len - header_end;
            bc_write_all(fh, head + header_end, n);
            done += n;
            if (progress != NULL) {
                progress(done, content_len);
            }
        }
    }

    buf = (char *)malloc(65536);
    if (buf == NULL) {
        CloseHandle(fh);
        closesocket(s);
        return HTTP_ERR_MEM;
    }

    if (chunked) {
        /* 增量解 chunked：读大小行 → 读 chunk → 写文件 */
        for (;;) {
            char line[64];
            int li = 0;
            int chunk = 0;
            int digits = 0;
            for (;;) {
                char c;
                int n = recv(s, &c, 1, 0);
                if (n <= 0) {
                    goto stream_done;
                }
                if (c == '\n') {
                    break;
                }
                if (c != '\r' && li < (int)sizeof(line) - 1) {
                    line[li++] = c;
                }
            }
            line[li] = '\0';
            {
                int i = 0;
                while (i < li) {
                    char c = line[i];
                    int v = -1;
                    if (c >= '0' && c <= '9') v = c - '0';
                    else if (c >= 'a' && c <= 'f') v = c - 'a' + 10;
                    else if (c >= 'A' && c <= 'F') v = c - 'A' + 10;
                    if (v < 0) break;
                    chunk = chunk * 16 + v;
                    digits++;
                    i++;
                }
            }
            if (digits == 0 || chunk <= 0) {
                break;
            }
            while (chunk > 0) {
                int want = (chunk > 65536) ? 65536 : chunk;
                int n = recv(s, buf, want, 0);
                if (n <= 0) {
                    goto stream_done;
                }
                bc_write_all(fh, buf, n);
                done += n;
                chunk -= n;
                if (progress != NULL) {
                    progress(done, -1);
                }
            }
            {   /* 吃掉 chunk 结尾的 CRLF */
                char crlf[2];
                recv(s, crlf, 2, 0);
            }
        }
    } else {
        for (;;) {
            int n = recv(s, buf, 65536, 0);
            if (n <= 0) {
                break;
            }
            bc_write_all(fh, buf, n);
            done += n;
            if (progress != NULL) {
                progress(done, content_len);
            }
        }
    }

stream_done:
    free(buf);
    CloseHandle(fh);
    closesocket(s);

    if (progress != NULL) {
        progress(done, content_len);
    }
    if (done <= 0) {
        set_err(err, errcap, "没有收到数据");
        bc_del(dst_path);
        return HTTP_ERR_RECV;
    }
    if (content_len > 0 && !chunked && done < content_len) {
        char tmp[96];
        sprintf(tmp, "数据不完整（%ld/%ld）", done, content_len);
        set_err(err, errcap, tmp);
        bc_del(dst_path);
        return HTTP_ERR_RECV;
    }
    return HTTP_OK;
}

int http_get_url_to_file(const char *url, const char *extra_headers,
                         const char *dst_path,
                         void (*progress)(long done, long total),
                         long *out_total, char *err, int errcap)
{
    static char cur[MAX_URL_LEN];
    static char hdr[MAX_HDR_BUF];
    static char path[MAX_URL_LEN];
    int hops;

    if (out_total != NULL) {
        *out_total = 0;
    }
    str_copy(cur, (int)sizeof(cur), url);

    for (hops = 0; hops <= MAX_REDIRECTS; hops++) {
        char host[128];
        int port = 80;
        int rc;

        if (!bc_split_url(cur, host, (int)sizeof(host), &port, path, (int)sizeof(path))) {
            set_err(err, errcap, "地址格式不支持");
            return HTTP_ERR_ARG;
        }
        rc = http_path_to_file(host, port, path, extra_headers, dst_path,
                               progress, out_total, hdr, (int)sizeof(hdr),
                               err, errcap);
        if (rc == HTTP_ERR_FORMAT) {
            int status = 0;
            parse_status(hdr, &status);
            if (status >= 300 && status < 400) {
                static char loc[MAX_URL_LEN];
                if (get_header_string(hdr, "Location", loc, (int)sizeof(loc)) &&
                    bc_resolve_location(cur, loc, cur, (int)sizeof(cur))) {
                    continue;   /* 跟随跳转 */
                }
                set_err(err, errcap, "跳转地址无法处理");
                return HTTP_ERR_REDIR;
            }
        }
        return rc;
    }
    set_err(err, errcap, "跳转次数过多");
    return HTTP_ERR_REDIR;
}

