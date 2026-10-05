/* =====================================================================
 * bili.c - B 站接口封装（Windows CE / 魅族 M8 版）
 * ===================================================================== */
#include "bili.h"
#include "http.h"
#include "util.h"
#include "sha1.h"
#include "md5.h"
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define BILI_API_HOST   "api.bilibili.com"
#define BILI_API_PORT   80

static void set_err(char *err, int errcap, const char *msg)
{
    if (err != NULL && errcap > 0) {
        str_copy(err, errcap, msg);
    }
}

/* 调试日志回调（app 层设置） */
static void (*s_log)(const char *) = NULL;
void bili_set_log(void (*fn)(const char *msg))
{
    s_log = fn;
}
#define BLOG(...) do { \
    if (s_log != NULL) { \
        char _lb[256]; \
        _snprintf(_lb, sizeof(_lb) - 1, __VA_ARGS__); \
        _lb[sizeof(_lb) - 1] = '\0'; \
        s_log(_lb); \
    } \
} while (0)

/* 设备的 buvid cookie（来自 /x/frontend/finger/spi）。风控主要看这个，
 * 假 buvid 会返回 v_voucher / -412。 */
static char s_cookie[400];
/* 登录 Cookie（SESSDATA 等），登录后由 login 模块设置 */
static char s_login_cookie[1200];

void bili_set_login_cookie(const char *c)
{
    str_copy(s_login_cookie, (int)sizeof(s_login_cookie), (c != NULL) ? c : "");
}

const char *bili_get_cookie(void)
{
    return s_cookie;
}

/* 直连 B 站时统一带的请求头：Referer/Origin/Accept-Language（+ 真实 buvid + 登录 Cookie） */
const char *bili_req_headers(void)
{
    static char hdr[1900];
    const char *lc = (s_login_cookie[0] != '\0') ? s_login_cookie : NULL;

    if (s_cookie[0] != '\0') {
        if (lc != NULL) {
            sprintf(hdr,
                    "Referer: https://www.bilibili.com/\r\n"
                    "Origin: https://www.bilibili.com\r\n"
                    "Accept-Language: zh-CN,zh;q=0.9\r\n"
                    "Cookie: %s; %s\r\n",
                    s_cookie, lc);
        } else {
            sprintf(hdr,
                    "Referer: https://www.bilibili.com/\r\n"
                    "Origin: https://www.bilibili.com\r\n"
                    "Accept-Language: zh-CN,zh;q=0.9\r\n"
                    "Cookie: %s\r\n",
                    s_cookie);
        }
    } else if (lc != NULL) {
        sprintf(hdr,
                "Referer: https://www.bilibili.com/\r\n"
                "Origin: https://www.bilibili.com\r\n"
                "Accept-Language: zh-CN,zh;q=0.9\r\n"
                "Cookie: %s\r\n",
                lc);
    } else {
        sprintf(hdr,
                "Referer: https://www.bilibili.com/\r\n"
                "Origin: https://www.bilibili.com\r\n"
                "Accept-Language: zh-CN,zh;q=0.9\r\n");
    }
    return hdr;
}

/* 搜索用的匿名头：未登录时不发任何 Cookie。
 * 对照实验（同一 UA/Referer/签名，只换 Cookie）：
 *   完全不带 Cookie            -> 28/28 全部正常
 *   带未登录的那套指纹 Cookie    -> 约 32% 被 v_voucher 拦下，重试 3 次
 *                                 耗尽就是「搜索繁忙」。
 * M8 的请求头是这里手拼的，没有网络层自动补 Cookie，所以少拼一行
 * Cookie 即可，不需要 Android 那种 X-Skip-Cookie 内部标记。 */
const char *bili_req_headers_anon(void)
{
    static char hdr[400];

    sprintf(hdr,
            "Referer: https://www.bilibili.com/\r\n"
            "Origin: https://www.bilibili.com\r\n"
            "Accept-Language: zh-CN,zh;q=0.9\r\n");
    return hdr;
}

/* Cookie 里有 SESSDATA 才算登录，才算「该发 Cookie」 */
static int bili_cookie_ok(void)
{
    return (s_login_cookie[0] != '\0' &&
            strstr(s_login_cookie, "SESSDATA=") != NULL) ? 1 : 0;
}

/* 搜索该用的请求头：已登录带登录 Cookie，未登录彻底不带 Cookie */
static const char *search_req_headers(void)
{
    return bili_cookie_ok() ? bili_req_headers() : bili_req_headers_anon();
}

static int direct_api_get(const char *path, char **out_body, int *out_len,
                          char *err, int errcap);

/* 取一次 buvid3/buvid4 并缓存到 s_cookie（匿名 finger/spi 接口） */
static int bili_ensure_buvid(BcConfig *cfg, char *err, int errcap)
{
    char *body = NULL;
    int len = 0;
    char b3[80];
    char b4[200];
    int rc;

    (void)cfg;
    if (s_cookie[0] != '\0') {
        return 0;
    }
    rc = direct_api_get("/x/frontend/finger/spi", &body, &len, err, errcap);
    BLOG("buvid: direct_api_get rc=%d len=%d", rc, len);
    if (rc != 0 || body == NULL) {
        if (body != NULL) {
            free(body);
        }
        set_err(err, errcap, "取 buvid 失败");
        return -1;
    }
    b3[0] = '\0';
    b4[0] = '\0';
    json_get_string(body, "b_3", b3, (int)sizeof(b3));
    json_get_string(body, "b_4", b4, (int)sizeof(b4));
    free(body);
    if (b3[0] == '\0') {
        set_err(err, errcap, "spi 无 b_3");
        return -1;
    }
    sprintf(s_cookie, "buvid3=%s; buvid4=%s; b_nut=%ld",
            b3, b4, (long)bc_unix_time());
    return 0;
}

void bili_config_init(BcConfig *cfg)
{
    char ini_path[BC_PATH_LEN];
    char ini[1024];
    char *line;
    char token_path[BC_PATH_LEN];
    char install_path[BC_PATH_LEN];

    memset(cfg, 0, sizeof(BcConfig));
    get_exe_dir(cfg->exe_dir, (int)sizeof(cfg->exe_dir));
    get_data_dir(cfg->data_dir, (int)sizeof(cfg->data_dir));
    str_copy(cfg->relay_host, (int)sizeof(cfg->relay_host), BC_RELAY_DEFAULT_HOST);
    cfg->relay_port = BC_RELAY_DEFAULT_PORT;
    cfg->transcode = 1;     /* 老播放器放不了 High Profile，默认让服务器转码 */
    str_copy(cfg->transcode_fmt, (int)sizeof(cfg->transcode_fmt),
             BC_DEFAULT_TRANSCODE_FMT);
    cfg->conns = 4;         /* 下载并行连接数（CDN 单连接常被限速） */
    cfg->offline = 0;       /* 默认只在线流播，不下载到本地 */
    cfg->danmaku = 1;       /* 默认开弹幕 */
    cfg->report_history = 1; /* 默认上报观看历史 */

    str_copy(ini_path, (int)sizeof(ini_path), cfg->data_dir);
    str_append(ini_path, (int)sizeof(ini_path), "biliclassic_m8.ini");
    if (file_read_all(ini_path, ini, (int)sizeof(ini)) > 0) {
        line = ini;
        while (line != NULL && *line != '\0') {
            char *nl = strchr(line, '\n');
            if (nl != NULL) {
                *nl = '\0';
            }
            /* file_write_all 用 \r\n 写行，剥离行尾 \r 防止污染取值 */
            {
                int L = (int)strlen(line);
                while (L > 0 && (line[L - 1] == '\r' || line[L - 1] == ' ')) {
                    line[--L] = '\0';
                }
            }
            if (strncmp(line, "relay_host=", 11) == 0) {
                str_copy(cfg->relay_host, (int)sizeof(cfg->relay_host), line + 11);
            } else if (strncmp(line, "relay_port=", 11) == 0) {
                int p = atoi(line + 11);
                if (p > 0 && p < 65536) {
                    cfg->relay_port = p;
                }
            } else if (strncmp(line, "transcode=", 10) == 0) {
                cfg->transcode = (atoi(line + 10) != 0);
            } else if (strncmp(line, "transcode_fmt=", 14) == 0) {
                str_copy(cfg->transcode_fmt, (int)sizeof(cfg->transcode_fmt),
                         line + 14);
            } else if (strncmp(line, "download_conns=", 15) == 0) {
                int p = atoi(line + 15);
                if (p >= 1 && p <= 8) {
                    cfg->conns = p;
                }
            } else if (strncmp(line, "offline=", 8) == 0) {
                cfg->offline = (atoi(line + 8) != 0);
            } else if (strncmp(line, "danmaku=", 8) == 0) {
                cfg->danmaku = (atoi(line + 8) != 0);
            } else if (strncmp(line, "report_history=", 15) == 0) {
                cfg->report_history = (atoi(line + 15) != 0);
            }
            if (nl == NULL) {
                break;
            }
            line = nl + 1;
        }
    }

    /* VCD(MPEG-1) 选项已取消：旧配置里的 mpeg1/vcd 回退到默认格式 */
    if (cfg->transcode_fmt[0] == '\0' ||
        strcmp(cfg->transcode_fmt, "mpeg1") == 0 ||
        strcmp(cfg->transcode_fmt, "vcd") == 0) {
        str_copy(cfg->transcode_fmt, (int)sizeof(cfg->transcode_fmt),
                 BC_DEFAULT_TRANSCODE_FMT);
    }

    /* install id（注册 token 用） */
    str_copy(install_path, (int)sizeof(install_path), cfg->data_dir);
    str_append(install_path, (int)sizeof(install_path), "biliclassic_m8_install.txt");
    if (file_read_all(install_path, cfg->install_id, (int)sizeof(cfg->install_id)) < 8) {
        make_random_hex(cfg->install_id, 32);
        file_write_all(install_path, cfg->install_id);
    }
    {
        int i;
        for (i = 0; cfg->install_id[i] != '\0'; i++) {
            if (cfg->install_id[i] == '\r' || cfg->install_id[i] == '\n' ||
                cfg->install_id[i] == ' ') {
                cfg->install_id[i] = '\0';
                break;
            }
        }
    }

    /* token 缓存 */
    str_copy(token_path, (int)sizeof(token_path), cfg->data_dir);
    str_append(token_path, (int)sizeof(token_path), "biliclassic_m8_token.txt");
    if (file_read_all(token_path, cfg->token, (int)sizeof(cfg->token)) > 0) {
        int i;
        for (i = 0; cfg->token[i] != '\0'; i++) {
            if (cfg->token[i] == '\r' || cfg->token[i] == '\n' ||
                cfg->token[i] == ' ') {
                cfg->token[i] = '\0';
                break;
            }
        }
        if (cfg->token[0] != '\0') {
            cfg->token_ok = 1;
        }
    }

    /* 登录 Cookie 缓存（单独文件，避免 ini 太小） */
    {
        char ck_path[BC_PATH_LEN];
        str_copy(ck_path, (int)sizeof(ck_path), cfg->data_dir);
        str_append(ck_path, (int)sizeof(ck_path), "biliclassic_m8_cookie.txt");
        cfg->cookie[0] = '\0';
        if (file_read_all(ck_path, cfg->cookie, (int)sizeof(cfg->cookie)) > 0) {
            int i;
            for (i = 0; cfg->cookie[i] != '\0'; i++) {
                if (cfg->cookie[i] == '\r' || cfg->cookie[i] == '\n') {
                    cfg->cookie[i] = '\0';
                    break;
                }
            }
        }
        bili_set_login_cookie(cfg->cookie);
    }
}

static void save_token(BcConfig *cfg)
{
    char token_path[BC_PATH_LEN];
    str_copy(token_path, (int)sizeof(token_path), cfg->data_dir);
    str_append(token_path, (int)sizeof(token_path), "biliclassic_m8_token.txt");
    file_write_all(token_path, cfg->token);
}

/* 保存登录 Cookie 到数据目录，并立即生效到请求头 */
void bili_save_cookie(BcConfig *cfg)
{
    char path[BC_PATH_LEN];

    str_copy(path, (int)sizeof(path), cfg->data_dir);
    str_append(path, (int)sizeof(path), "biliclassic_m8_cookie.txt");
    file_write_all(path, cfg->cookie);
    bili_set_login_cookie(cfg->cookie);
}

/* 把转码/连接数设置写回 ini */
void bili_save_config(BcConfig *cfg)
{
    char ini_path[BC_PATH_LEN];
    char ini[512];

    sprintf(ini,
            "relay_host=%s\r\nrelay_port=%d\r\ntranscode=%d\r\ntranscode_fmt=%s\r\n"
            "download_conns=%d\r\noffline=%d\r\ndanmaku=%d\r\nreport_history=%d\r\n",
            cfg->relay_host, cfg->relay_port,
            cfg->transcode ? 1 : 0, cfg->transcode_fmt, cfg->conns,
            cfg->offline ? 1 : 0, cfg->danmaku ? 1 : 0,
            cfg->report_history ? 1 : 0);
    str_copy(ini_path, (int)sizeof(ini_path), cfg->data_dir);
    str_append(ini_path, (int)sizeof(ini_path), "biliclassic_m8.ini");
    file_write_all(ini_path, ini);
}

/* 取转码服务器时间（老设备 RTC 可能不准，token 签名要窗口内 */
int bili_sync_time(BcConfig *cfg, char *err, int errcap)
{
    char *body = NULL;
    int len = 0;
    int status = 0;
    long ts = 0;
    int rc;

    http_set_timeout(5000);     /* 同步时间失败也不能拖太久 */
    rc = http_request(cfg->relay_host, cfg->relay_port, "GET", "/health", NULL,
                      NULL, 0, &body, &len, &status);
    http_set_timeout(30000);
    if (rc != 0 || status != 200) {
        set_err(err, errcap, "转码服务器 /health 失败");
        if (body != NULL) {
            free(body);
        }
        return -1;
    }
    if (!json_get_int(body, "ts", &ts)) {
        free(body);
        set_err(err, errcap, "/health 无 ts");
        return -1;
    }
    free(body);
    cfg->time_offset = ts - bc_unix_time();
    return 0;
}

/* 用 HMAC-SHA1 签名换每设备 token（转码用） */
static int bili_ensure_token(BcConfig *cfg, char *err, int errcap)
{
    char *body = NULL;
    int len = 0;
    int status = 0;
    long ts = 0;
    char tsbuf[32];
    char msg[256];
    char sig[64];
    char reqbody[512];
    int rc;

    if (cfg->token_ok) {
        return 0;
    }
    if (bili_sync_time(cfg, err, errcap) != 0) {
        return -1;
    }
    ts = bc_unix_time() + cfg->time_offset;

    sprintf(tsbuf, "%ld", ts);
    str_copy(msg, (int)sizeof(msg), tsbuf);
    str_append(msg, (int)sizeof(msg), "|");
    str_append(msg, (int)sizeof(msg), cfg->install_id);
    hmac_sha1_hex(BC_AUTH_SECRET, msg, sig, (int)sizeof(sig));

    sprintf(reqbody,
            "{\"bucket\":\"%s\",\"region\":\"%s\",\"install_id\":\"%s\","
            "\"ts\":\"%s\",\"sig\":\"%s\"}",
            BC_REG_BUCKET, BC_REG_REGION, cfg->install_id, tsbuf, sig);

    body = NULL;
    rc = http_request(cfg->relay_host, cfg->relay_port, "POST", "/register",
                      "Content-Type: application/json; charset=utf-8\r\n",
                      reqbody, (int)strlen(reqbody), &body, &len, &status);
    if (rc != 0 || body == NULL) {
        set_err(err, errcap, "转码服务器 /register 失败");
        if (body != NULL) {
            free(body);
        }
        return -1;
    }
    if (!json_get_string(body, "token", cfg->token, (int)sizeof(cfg->token)) ||
        cfg->token[0] == '\0') {
        str_copy(err, errcap, "register 无 token: ");
        str_append(err, errcap, body);
        free(body);
        return -1;
    }
    free(body);
    cfg->token_ok = 1;
    save_token(cfg);
    return 0;
}

/* 直连 api.bilibili.com：先试 HTTPS（ws2 SSL），失败退回明文 http。
 * 失败返回 -1，调用方（api_get_with_fallback）会再回退中继。 */
static int direct_api_get_h(const char *path, const char *hdr,
                            char **out_body, int *out_len,
                            char *err, int errcap)
{
    static char url[BC_URL_LEN + 64];
    int status = 0;

    str_copy(url, (int)sizeof(url), "https://");
    str_append(url, (int)sizeof(url), BILI_API_HOST);
    str_append(url, (int)sizeof(url), path);
    BLOG("https: enter %.80s", url);
    if (http_get_https(url, hdr,
                       out_body, out_len, &status, err, errcap) == HTTP_OK &&
        status == 200) {
        BLOG("https: ok status=%d len=%d", status, (out_len != NULL) ? *out_len : -1);
        return 0;
    }
    BLOG("https: fail status=%d, fallback http", status);
    if (*out_body != NULL) {
        free(*out_body);
        *out_body = NULL;
        *out_len = 0;
    }

    /* HTTPS 走不通时退回明文 http（搜索/热门接口允许） */
    status = 0;
    if (http_request(BILI_API_HOST, BILI_API_PORT, "GET", path,
                     hdr,
                     NULL, 0, out_body, out_len, &status) != 0) {
        set_err(err, errcap, "直连 api.bilibili.com 失败");
        return -1;
    }
    if (status != 200) {
        char tmp[64];
        sprintf(tmp, "直连 HTTP %d", status);
        set_err(err, errcap, tmp);
        if (*out_body != NULL) {
            free(*out_body);
            *out_body = NULL;
            *out_len = 0;
        }
        return -1;
    }
    return 0;
}

static int direct_api_get(const char *path, char **out_body, int *out_len,
                          char *err, int errcap)
{
    return direct_api_get_h(path, bili_req_headers(),
                            out_body, out_len, err, errcap);
}


/* 把 "https://api.bilibili.com/xxx?y=z" 走 wolfSSL HTTPS 直连，失败返回 -1。 */
static int direct_url_get(const char *url, char **out_body, int *out_len,
                          char *err, int errcap)
{
    int status = 0;
    int last_status = 0;
    int attempt;

    if (strncmp(url, "https://", 8) != 0) {
        set_err(err, errcap, "直连只支持 https 地址");
        return -1;
    }
    /* 偶发 TLS 握手/读超时（进入历史某条时常见），多试几次 */
    for (attempt = 0; attempt < 3; attempt++) {
        status = 0;
        if (http_get_https(url, bili_req_headers(),
                           out_body, out_len, &status, err, errcap) != HTTP_OK) {
            status = 0;
        }
        if (status != 0) {
            last_status = status;
        }
        BLOG("直连: 第 %d 次 %.120s -> status=%d",
             attempt + 1, url, status);
        if (status == 200) {
            return 0;
        }
        if (*out_body != NULL) {
            free(*out_body);
            *out_body = NULL;
        }
        if (out_len != NULL) {
            *out_len = 0;
        }
        if (attempt + 1 < 3) {
            Sleep(400);
        }
    }
    if (last_status != 0) {
        char tmp[220];

        sprintf(tmp, "直连 HTTP %d", last_status);
        set_err(err, errcap, tmp);
        return -1;
    }
    set_err(err, errcap, "直连 HTTPS 失败");
    return -1;
}

/* 调试开关：=1 时不做「未签名」回退，用来验证 wbi 签名是否真被服务器接受 */
int g_bili_no_fallback = 0;

/* 依次尝试：直连(带签名) -> 直连(未签名)。
 * path_signed 可以传 NULL（说明这次没有签名）。body 里的 code 非 0 也算失败。
 * hdr 由调用方决定（搜索未登录时传匿名头）。 */
static int api_get_with_fallback(BcConfig *cfg, const char *hdr,
                                 const char *path_signed, const char *path_plain,
                                 char **out_body, int *out_len,
                                 char *err, int errcap)
{
    long code = 0;
    int rc;
    int parsed;
    int has_vv;

    (void)cfg;
    if (path_signed != NULL) {
        rc = direct_api_get_h(path_signed, hdr, out_body, out_len, err, errcap);
        code = 0;
        parsed = (rc == 0 && out_body != NULL && *out_body != NULL) ?
                 json_get_int(*out_body, "code", &code) : 0;
        has_vv = (*out_body != NULL && strstr(*out_body, "v_voucher") != NULL) ? 1 : 0;
        BLOG("api: signed rc=%d code=%ld vch=%d", rc, code, has_vv);
        if (rc == 0 && (!parsed || code == 0) && !has_vv) {
            return 0;
        }
        if (*out_body != NULL) {
            free(*out_body);
            *out_body = NULL;
            *out_len = 0;
        }
        if (g_bili_no_fallback) {
            set_err(err, errcap, "带签名的请求失败（回退已禁用）");
            return -1;
        }
    }

    rc = direct_api_get_h(path_plain, hdr, out_body, out_len, err, errcap);
    code = 0;
    parsed = (rc == 0 && out_body != NULL && *out_body != NULL) ?
             json_get_int(*out_body, "code", &code) : 0;
    has_vv = (*out_body != NULL && strstr(*out_body, "v_voucher") != NULL) ? 1 : 0;
    BLOG("api: plain rc=%d code=%ld vch=%d", rc, code, has_vv);
    if (rc == 0 && (!parsed || code == 0) && !has_vv) {
        return 0;
    }
    if (*out_body != NULL) {
        free(*out_body);
        *out_body = NULL;
        *out_len = 0;
    }
    return -1;
}


int bili_search(BcConfig *cfg, const char *keyword, int page,
                char **out_body, int *out_len, char *err, int errcap)
{
    static char kw8[1024];
    static char enc[1024];
    static char q[1400];
    static char sq[4600];
    static char path[BC_URL_LEN];
    static char path_plain[BC_URL_LEN];

    /* 本地关键词是 GBK(ACP) 字节，B 站接口期望 UTF-8 —— 先转码再编码 */
    ansi_to_utf8(keyword, kw8, (int)sizeof(kw8));
    url_encode(kw8, enc, (int)sizeof(enc));

    /* 参数（未签名形式） */
    q[0] = '\0';
    str_append(q, (int)sizeof(q), "keyword=");
    str_append(q, (int)sizeof(q), enc);
    {
        char pb[32];
        sprintf(pb, "&page=%d", page);
        str_append(q, (int)sizeof(q), pb);
    }
    str_append(q, (int)sizeof(q), "&search_type=video");

    /* 回退：免签名的老搜索接口 */
    path_plain[0] = '\0';
    str_append(path_plain, (int)sizeof(path_plain),
               "/x/web-interface/search/type?");
    str_append(path_plain, (int)sizeof(path_plain), q);

    /* 正式接口带 WBI 签名（无 w_rid 易被风控 v_voucher/412）。
     * 头走 search_req_headers()：未登录时不带任何 Cookie。 */
    path[0] = '\0';
    if (bili_wbi_ensure(cfg, err, errcap) == 0 &&
        bili_wbi_sign(cfg, q, sq, (int)sizeof(sq)) == 0) {
        str_append(path, (int)sizeof(path),
                   "/x/web-interface/wbi/search/type?");
        str_append(path, (int)sizeof(path), sq);
        BLOG("search: wbi page=%d cookie=%d", page, bili_cookie_ok());
        return api_get_with_fallback(cfg, search_req_headers(), path, path_plain,
                                     out_body, out_len, err, errcap);
    }
    BLOG("search: no-wbi page=%d cookie=%d", page, bili_cookie_ok());
    return api_get_with_fallback(cfg, search_req_headers(), NULL, path_plain,
                                 out_body, out_len, err, errcap);
}

int bili_popular(BcConfig *cfg, int page,
                 char **out_body, int *out_len, char *err, int errcap)
{
    char path[256];
    int r;

    BLOG("popular: ensure buvid");
    bili_ensure_buvid(cfg, err, errcap);
    BLOG("popular: buvid ok");
    sprintf(path, "/x/web-interface/popular?ps=20&pn=%d", page);
    BLOG("popular: GET %s", path);
    r = api_get_with_fallback(cfg, bili_req_headers(), NULL, path,
                              out_body, out_len, err, errcap);
    BLOG("popular: ret=%d len=%d", r, (out_len != NULL) ? *out_len : -1);
    return r;
}

int bili_pagelist(BcConfig *cfg, const char *bvid,
                  char **out_body, int *out_len, char *err, int errcap)
{
    char target[BC_URL_LEN];
    long code = -1;

    bili_ensure_buvid(cfg, err, errcap);
    str_copy(target, (int)sizeof(target),
             "https://api.bilibili.com/x/player/pagelist?bvid=");
    str_append(target, (int)sizeof(target), bvid);
    if (direct_url_get(target, out_body, out_len, err, errcap) == 0) {
        if (!json_get_int(*out_body, "code", &code) || code == 0) {
            return 0;
        }
        {
            char tmp[256];
            sprintf(tmp, "直连 pagelist code=%ld: %.150s", code,
                    (*out_body != NULL) ? *out_body : "");
            set_err(err, errcap, tmp);
        }
        free(*out_body);
        *out_body = NULL;
        *out_len = 0;
    }
    return -1;
}

/* 调试开关：=1 时 playurl 不回退老接口（验证 wbi/playurl 签名是否被接受） */
extern int g_bili_no_fallback;

int bili_playurl(BcConfig *cfg, const char *bvid, const char *cid,
                 char **out_body, int *out_len, char *err, int errcap)
{
    static char q[1024];
    static char sq[4600];
    static char target[BC_URL_LEN];
    long code = -1;

    /* html5 平台：返回 durl 的 muxed mp4，老播放器友好 */
    sprintf(q, "bvid=%s&cid=%s&platform=html5&high_quality=1", bvid, cid);

    /* 先试带 WBI 签名的 wbi/playurl；拿不到签名就退回老 playurl */
    if (bili_wbi_ensure(cfg, err, errcap) == 0 &&
        bili_wbi_sign(cfg, q, sq, (int)sizeof(sq)) == 0) {
        str_copy(target, (int)sizeof(target),
                 "https://api.bilibili.com/x/player/wbi/playurl?");
        str_append(target, (int)sizeof(target), sq);
        code = -1;
        if (direct_url_get(target, out_body, out_len, err, errcap) == 0 &&
            (!json_get_int(*out_body, "code", &code) || code == 0)) {
            return 0;
        }
        if (*out_body != NULL) {
            free(*out_body);
            *out_body = NULL;
            *out_len = 0;
        }
        if (g_bili_no_fallback) {
            set_err(err, errcap, "带签名的 playurl 失败（回退已禁用）");
            return -1;
        }
    }

    str_copy(target, (int)sizeof(target),
             "https://api.bilibili.com/x/player/playurl?");
    str_append(target, (int)sizeof(target), q);
    code = -1;
    if (direct_url_get(target, out_body, out_len, err, errcap) == 0 &&
        (!json_get_int(*out_body, "code", &code) || code == 0)) {
        return 0;
    }
    if (*out_body != NULL) {
        free(*out_body);
        *out_body = NULL;
        *out_len = 0;
    }
    return -1;
}

/* ---- WBI 签名 ---------------------------------------------------------
 * B 站 2023 起给一批接口加了 w_rid 签名：把参数按键排序、值里去掉
 * !'()* 这几个字符，拼成 query，再拼上 mixin_key 做 md5。
 * mixin_key 由 nav 接口给的 img_url/sub_url 里的两个 key 按规定表重排得到。
 * ------------------------------------------------------------------- */

/* 参数名排序用的比较（minicrt 没有 strcmp） */
static int wbi_cmp(const char *a, const char *b)
{
    while (*a != '\0' && *a == *b) {
        a++;
        b++;
    }
    return (int)(unsigned char)*a - (int)(unsigned char)*b;
}

/* 从 "https://i0.hdslb.com/bfs/wbi/abc123.png" 里取 "abc123" */
static void wbi_take_key(const char *url, char *out, int cap)
{
    const char *p = url;
    const char *last = url;

    while ((p = strchr(last, '/')) != NULL) {
        last = p + 1;
    }
    p = last;
    while (*p != '\0' && *p != '.' && *p != '?' && *p != '&') {
        p++;
    }
    if (p == last) {
        str_copy(out, cap, "");
        return;
    }
    {
        int n = (int)(p - last);
        if (n > cap - 1) {
            n = cap - 1;
        }
        memcpy(out, last, (size_t)n);
        out[n] = '\0';
    }
}

int bili_wbi_ensure(BcConfig *cfg, char *err, int errcap)
{
    static const unsigned char tab[64] = {
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };
    char *body = NULL;
    int len = 0;
    char img[256];
    char sub[256];
    char imgk[64];
    char subk[64];
    char raw[128];
    int i;

    if (cfg->wbi_ok) {
        return 0;
    }
    bili_ensure_buvid(cfg, err, errcap);    /* 风控要真实 buvid，失败也继续 */
    /* 优先用 B 站响应的 Date 头校正本机时钟（M8 RTC 常不准，wts 超窗会 412） */
    if (http_server_time() > 0) {
        cfg->time_offset = http_server_time() - bc_unix_time();
    }
    if (cfg->time_offset == 0) {
        bili_sync_time(cfg, err, errcap);   /* 退而求其次用转码服务器时间 */
    }

    /* nav 接口匿名调用也会带 wbi_img */
    if (direct_api_get("/x/web-interface/nav", &body, &len, err, errcap) != 0) {
        if (body != NULL) {
            free(body);
            body = NULL;
        }
        return -1;
    }

    img[0] = '\0';
    sub[0] = '\0';
    json_get_string(body, "img_url", img, (int)sizeof(img));
    json_get_string(body, "sub_url", sub, (int)sizeof(sub));
    free(body);
    body = NULL;

    if (img[0] == '\0' || sub[0] == '\0') {
        set_err(err, errcap, "nav 里没有 wbi_img");
        return -1;
    }
    wbi_take_key(img, imgk, (int)sizeof(imgk));
    wbi_take_key(sub, subk, (int)sizeof(subk));
    raw[0] = '\0';
    str_append(raw, (int)sizeof(raw), imgk);
    str_append(raw, (int)sizeof(raw), subk);
    if (strlen(raw) < 64) {
        set_err(err, errcap, "wbi key 长度不对");
        return -1;
    }
    for (i = 0; i < 32; i++) {
        cfg->wbi_mixin[i] = raw[tab[i]];
    }
    cfg->wbi_mixin[32] = '\0';
    cfg->wbi_ok = 1;
    return 0;
}

int bili_wbi_sign(BcConfig *cfg, const char *query, char *out, int outcap)
{
    static char keys[24][48];
    static char vals[24][768];
    static char sorted[4600];
    static char full[4700];
    char md[40];
    char wts[32];
    const char *p;
    int n = 0;
    int i;
    int j;

    if (!cfg->wbi_ok) {
        return -1;
    }

    p = query;
    while (*p != '\0' && n < 23) {
        j = 0;
        while (*p != '\0' && *p != '=' && *p != '&' && j < 47) {
            keys[n][j++] = *p++;
        }
        keys[n][j] = '\0';
        j = 0;
        if (*p == '=') {
            p++;
            while (*p != '\0' && *p != '&' && j < 767) {
                char c = *p++;
                if (c == '!' || c == '\'' || c == '(' || c == ')' || c == '*') {
                    continue;       /* WBI 规则：值里这几个字符要滤掉 */
                }
                vals[n][j++] = c;
            }
        }
        vals[n][j] = '\0';
        if (*p == '&') {
            p++;
        }
        n++;
    }
    sprintf(wts, "%ld", bc_unix_time() + cfg->time_offset);
    str_copy(keys[n], (int)sizeof(keys[n]), "wts");
    str_copy(vals[n], (int)sizeof(vals[n]), wts);
    n++;

    for (i = 1; i < n; i++) {
        char tk[48];
        char tv[768];
        str_copy(tk, (int)sizeof(tk), keys[i]);
        str_copy(tv, (int)sizeof(tv), vals[i]);
        j = i - 1;
        while (j >= 0 && wbi_cmp(keys[j], tk) > 0) {
            str_copy(keys[j + 1], (int)sizeof(keys[j + 1]), keys[j]);
            str_copy(vals[j + 1], (int)sizeof(vals[j + 1]), vals[j]);
            j--;
        }
        str_copy(keys[j + 1], (int)sizeof(keys[j + 1]), tk);
        str_copy(vals[j + 1], (int)sizeof(vals[j + 1]), tv);
    }

    sorted[0] = '\0';
    for (i = 0; i < n; i++) {
        if (i > 0) {
            str_append(sorted, (int)sizeof(sorted), "&");
        }
        str_append(sorted, (int)sizeof(sorted), keys[i]);
        str_append(sorted, (int)sizeof(sorted), "=");
        str_append(sorted, (int)sizeof(sorted), vals[i]);
    }

    str_copy(full, (int)sizeof(full), sorted);
    str_append(full, (int)sizeof(full), cfg->wbi_mixin);
    md5_hex(full, (int)strlen(full), md);

    str_copy(out, outcap, sorted);
    str_append(out, outcap, "&w_rid=");
    str_append(out, outcap, md);
    return 0;
}

/* 在 buf[0..buflen) 里找 "key":"value"，last=1 取最后一次出现，
 * last=0 取第一次出现。保留反斜杠交给 json_unescape 处理 */
static int json_scan_raw(const char *buf, int buflen, const char *key,
                         int last, char *out, int cap)
{
    char pat[96];
    int i;
    int found = -1;
    int j = 0;
    const char *p;

    if (cap <= 1) {
        return 0;
    }
    out[0] = '\0';
    if (buf == NULL || buflen <= 0) {
        return 0;
    }
    str_copy(pat, (int)sizeof(pat), "\"");
    str_append(pat, (int)sizeof(pat), key);
    str_append(pat, (int)sizeof(pat), "\"");

    for (i = 0; i + (int)strlen(pat) < buflen; i++) {
        if (strncmp(buf + i, pat, (int)strlen(pat)) != 0) {
            continue;
        }
        /* 容忍 "key" : "value" 里的空格 */
        p = buf + i + (int)strlen(pat);
        while (*p == ' ' || *p == '\t') {
            p++;
        }
        if (*p != ':') {
            continue;
        }
        p++;
        while (*p == ' ' || *p == '\t') {
            p++;
        }
        if (*p != '"') {
            continue;
        }
        found = (int)(p - buf) + 1;
        if (!last) {
            break;
        }
    }
    if (found < 0) {
        return 0;
    }
    p = buf + found;
    while (*p != '\0' && j < cap - 1) {
        if (*p == '\\' && p[1] != '\0') {
            /* search 的标题里有 \u003c 之类的 ASCII 转义，必须先解开，
             * 否则后面的 strip_tags 认不出 <em> 标签 */
            if (p[1] == 'u' && p[2] != '\0' && p[3] != '\0' &&
                p[4] != '\0' && p[5] != '\0') {
                int v = 0;
                int ok = 1;
                int k;

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
                    out[j++] = (char)v;
                    p += 6;
                    continue;
                }
            }
            out[j++] = *p++;
            if (j < cap - 1) {
                out[j++] = *p++;
            }
            continue;
        }
        if (*p == '"') {
            break;
        }
        out[j++] = *p++;
    }
    out[j] = '\0';
    return 1;
}

static int json_get_string_last_raw(const char *buf, int buflen,
                                    const char *key, char *out, int cap)
{
    return json_scan_raw(buf, buflen, key, 1, out, cap);
}

static int json_get_string_first_raw(const char *buf, int buflen,
                                     const char *key, char *out, int cap)
{
    return json_scan_raw(buf, buflen, key, 0, out, cap);
}

/* 这个字段排在 bvid 后面还是前面？
 *   1 = 在 bvid 后面（search 的 title/pic 是这样）
 *   0 = 在 bvid 前面（popular 的 pic/title、history 的 title/cover 是这样）
 *  -1 = 找不到
 * 只比「第一次出现」会被第一条数据缺字段坑：历史第一页常常第一条是直播/
 * 专栏，没有 cover，第一次 "cover": 就跑到第二条里去了，比下来会误判成
 * 「在 bvid 后面」，结果每行都吃到下一行的封面（封面重复就是这么来的）。
 * 所以要两条证据一起看：
 *   第一条 bvid 前面就出现过 -> 一定在前面；
 *   否则只有最后一条 bvid 后面还出现过才敢说在后面，其余情况一律按前面算。 */
static int key_follows_bvid(const char *json, const char *key)
{
    char pat[96];
    const char *bpat; /* "bvid": 或 "bv_id": */
    const char *fk;   /* key 第一次出现 */
    const char *lk;   /* key 最后一次出现 */
    const char *fb;   /* bvid 第一次出现 */
    const char *lb;   /* bvid 最后一次出现 */
    const char *q;

    if (json == NULL || key == NULL) {
        return -1;
    }
    str_copy(pat, (int)sizeof(pat), "\"");
    str_append(pat, (int)sizeof(pat), key);
    str_append(pat, (int)sizeof(pat), "\":");
    bpat = "\"bvid\":\"";
    fb = strstr(json, bpat);
    if (fb == NULL) {
        bpat = "\"bv_id\":\"";
        fb = strstr(json, bpat);
    }
    fk = strstr(json, pat);
    if (fk == NULL || fb == NULL) {
        return -1;
    }
    lb = fb;
    q = fb;
    while ((q = strstr(q, bpat)) != NULL) {
        lb = q;
        q++;
    }
    lk = fk;
    q = fk;
    while ((q = strstr(q, pat)) != NULL) {
        lk = q;
        q++;
    }
    if (fk < fb) {
        return 0;
    }
    return (lk > lb) ? 1 : 0;
}

/* 去掉 B 站搜索标题里的 <em class="keyword"> 高亮标签 */
static void strip_tags(const char *src, char *dst, int cap)
{
    int di = 0;
    int in_tag = 0;

    while (*src != '\0' && di < cap - 1) {
        if (*src == '<') {
            in_tag = 1;
            src++;
            continue;
        }
        if (in_tag) {
            if (*src == '>') {
                in_tag = 0;
            }
            src++;
            continue;
        }
        dst[di++] = *src++;
    }
    dst[di] = '\0';
}

/* 在本条 bvid 的前后两段里挑一个字段的原始值。
 * dir=1 -> 字段排在 bvid 后面，取 bvid 后面那段的第一次出现；
 * dir!=1 -> 字段排在 bvid 前面，取 bvid 前面那段的最后一次出现。
 * 只查本条所属的那一段，绝不跨到隔壁条去兜底：跨过去取到的一定是
 * 下一条（或上一条）的值，一屏里就会出现两条同一张封面。 */
static int item_pick_raw(const char *ws, int wlen, const char *vend,
                         int awlen, const char *key, int dir,
                         char *out, int cap)
{
    if (dir == 1) {
        return json_get_string_first_raw(vend, awlen, key, out, cap);
    }
    return json_get_string_last_raw(ws, wlen, key, out, cap);
}

/* 同 item_pick_raw，但取完直接 json_unescape + UTF8->ANSI（author 用） */
static int item_pick_str(const char *ws, int wlen, const char *vend,
                         int awlen, const char *key, int dir,
                         char *out, int cap)
{
    char tmp[512];

    if (cap <= 1) {
        return 0;
    }
    out[0] = '\0';
    if (!item_pick_raw(ws, wlen, vend, awlen, key, dir, tmp,
                       (int)sizeof(tmp))) {
        return 0;
    }
    json_unescape(tmp);
    utf8_to_ansi(tmp, out, cap);
    return 1;
}

int bili_parse_items(const char *json, BcItem *items, int max,
                     const char *pic_field)
{
    const char *p = json;
    int n = 0;
    int titleDir;
    int authorDir;
    int anameDir;
    int nameDir;
    int picDir;

    if (json == NULL || items == NULL || max <= 0) {
        return 0;
    }
    if (pic_field == NULL || pic_field[0] == '\0') {
        pic_field = "pic";
    }
    /* 各接口里字段相对 bvid 的位置不一样：popular 的 pic/title 在 bvid 前，
     * search 的 title/pic 在 bvid 后，history 的 author_name 在 bvid 后。
     * 每个字段先判一次方位，之后按方位取值。 */
    titleDir  = key_follows_bvid(json, "title");
    authorDir = key_follows_bvid(json, "author");
    anameDir  = key_follows_bvid(json, "author_name");
    nameDir   = key_follows_bvid(json, "name");
    picDir    = key_follows_bvid(json, pic_field);

    while (n < max) {
        const char *ws = p;
        const char *b = strstr(p, "\"bvid\":\"");
        int bkey = 8;             /* "bvid":" 的长度 */
        int wlen;
        int awlen;
        const char *vend;
        const char *wend;
        char raw[1536];
        char tmp[BC_TITLE_LEN];
        int i;

        if (b == NULL) {
            /* 收藏夹接口的字段叫 bv_id */
            b = strstr(p, "\"bv_id\":\"");
            bkey = 9;
        }
        if (b == NULL) {
            break;
        }
        /* bvid 前面那一段：[上一条 bvid 之后, 本条 bvid) */
        wlen = (int)(b - ws);
        if (wlen < 0) {
            wlen = 0;
        }
        b += bkey;
        for (i = 0; i < BC_BVID_LEN - 1 && b[i] != '\0' && b[i] != '"'; i++) {
            items[n].bvid[i] = b[i];
        }
        items[n].bvid[i] = '\0';
        if (i == 0) {
            p = b;
            continue;
        }
        /* bvid 后面那一段：[本条 bvid 之后, 下一条 bvid) */
        vend = b + strlen(items[n].bvid);
        wend = strstr(vend, "\"bvid\":\"");
        if (wend == NULL) {
            wend = strstr(vend, "\"bv_id\":\"");
        }
        if (wend == NULL) {
            wend = json + strlen(json);
        }
        awlen = (int)(wend - vend);
        if (awlen < 0) {
            awlen = 0;
        }

        /* title（search 的标题里带 <em> 高亮标签要剥掉） */
        items[n].title[0] = '\0';
        if (item_pick_raw(ws, wlen, vend, awlen, "title", titleDir,
                          raw, (int)sizeof(raw))) {
            char stripped[sizeof(raw)];
            json_unescape(raw);
            strip_tags(raw, stripped, (int)sizeof(stripped));
            utf8_to_ansi(stripped, tmp, (int)sizeof(tmp));
            str_copy(items[n].title, BC_TITLE_LEN, tmp);
        }

        /* author：按 author / author_name / name(owner、upper) 的顺序试 */
        items[n].author[0] = '\0';
        item_pick_str(ws, wlen, vend, awlen, "author", authorDir,
                      items[n].author, BC_AUTHOR_LEN);
        if (items[n].author[0] == '\0') {
            item_pick_str(ws, wlen, vend, awlen, "author_name", anameDir,
                          items[n].author, BC_AUTHOR_LEN);
        }
        if (items[n].author[0] == '\0') {
            item_pick_str(ws, wlen, vend, awlen, "name", nameDir,
                          items[n].author, BC_AUTHOR_LEN);
        }

        /* pic：封面 URL */
        items[n].pic[0] = '\0';
        if (item_pick_raw(ws, wlen, vend, awlen, pic_field, picDir,
                          raw, (int)sizeof(raw))) {
            json_unescape(raw);
            str_copy(items[n].pic, BC_PIC_LEN, raw);
        }

        n++;
        p = vend;
    }
    return n;
}

/* 收藏夹文件夹列表 -> BcItem
 *   bvid   = media_id（双击时用它去取夹里的视频）
 *   title  = 夹子名字
 *   author = 视频数
 * 靠 media_count 且中间不能跨对象来确认这是文件夹对象，避免误吃到 tab 项 */
int bili_parse_fav_folders(const char *json, BcItem *items, int max)
{
    const char *p = json;
    int n = 0;

    if (json == NULL || items == NULL || max <= 0) {
        return 0;
    }
    while (n < max) {
        const char *idp = strstr(p, "\"id\":");
        const char *tp;
        const char *mp;
        const char *s;
        char idbuf[32];
        int k = 0;
        long cnt = 0;

        if (idp == NULL) {
            break;
        }
        idp += 5;
        while (*idp >= '0' && *idp <= '9' && k < (int)sizeof(idbuf) - 1) {
            idbuf[k++] = *idp++;
        }
        idbuf[k] = '\0';
        if (k == 0) {
            p = idp;
            continue;
        }
        tp = strstr(idp, "\"title\":");
        mp = strstr(idp, "\"media_count\":");
        if (tp == NULL || mp == NULL || tp - idp > 400 ||
            mp - idp > 400 || mp < tp) {
            p = idp;
            continue;
        }
        for (s = idp; s < mp; s++) {
            if (*s == '{') {
                break;   /* 中间开新对象了，id 不是这个文件夹的 */
            }
        }
        if (s < mp) {
            p = idp;
            continue;
        }

        items[n].title[0] = '\0';
        items[n].author[0] = '\0';
        items[n].pic[0] = '\0';
        if (!bili_pick_string(idp, "title", items[n].title, BC_TITLE_LEN)) {
            p = idp;
            continue;
        }
        cnt = bili_pick_int(idp, "media_count", 0);
        sprintf(items[n].author, "%ld 个视频", cnt);
        str_copy(items[n].bvid, BC_BVID_LEN, idbuf);

        n++;
        p = mp + 14;
    }
    return n;
}

int bili_pick_string(const char *json, const char *key, char *out, int cap)
{
    char tmp[BC_TITLE_LEN * 4];
    if (!json_get_string(json, key, tmp, (int)sizeof(tmp))) {
        if (cap > 0) {
            out[0] = '\0';
        }
        return 0;
    }
    json_unescape(tmp);
    utf8_to_ansi(tmp, out, cap);
    return 1;
}

long bili_pick_int(const char *json, const char *key, long fallback)
{
    long v = 0;
    if (json_get_int(json, key, &v)) {
        return v;
    }
    return fallback;
}

/* 取 "key":<数字> 的原始数字串（cid 是 64 位，long 放不下）。成功返回 1。 */
int bili_pick_number(const char *json, const char *key, char *out, int cap)
{
    char pat[128];
    const char *p;
    int i = 0;

    if (cap > 0) {
        out[0] = '\0';
    }
    if (json == NULL || key == NULL || out == NULL || cap <= 0) {
        return 0;
    }
    str_copy(pat, (int)sizeof(pat), "\"");
    str_append(pat, (int)sizeof(pat), key);
    str_append(pat, (int)sizeof(pat), "\"");
    p = strstr(json, pat);
    if (p == NULL) {
        return 0;
    }
    p += strlen(pat);
    while (*p == ' ' || *p == '\t') {
        p++;
    }
    if (*p != ':') {
        return 0;
    }
    p++;
    while (*p == ' ' || *p == '\t') {
        p++;
    }
    if (*p == '"') {
        p++;
    }
    while (*p >= '0' && *p <= '9' && i < cap - 1) {
        out[i++] = *p++;
    }
    out[i] = '\0';
    return i > 0;
}

/* ---- 转码：POST /transcode，让服务器把视频转成老格式（M8 能放） ---- */

int bili_transcode(BcConfig *cfg, const char *src_url, const char *name,
                   char *out_url, int out_cap, char *err, int errcap)
{
    static char reqbody[4600];
    char *body = NULL;
    int len = 0;
    int status = 0;
    int rc;

    if (out_url != NULL && out_cap > 0) {
        out_url[0] = '\0';
    }
    if (cfg->transcode_fmt[0] == '\0') {
        str_copy(cfg->transcode_fmt, (int)sizeof(cfg->transcode_fmt),
                 BC_DEFAULT_TRANSCODE_FMT);
    }
    if (bili_ensure_token(cfg, err, errcap) != 0) {
        return -1;
    }

    sprintf(reqbody,
            "{\"bucket\":\"%s\",\"region\":\"%s\",\"token\":\"%s\","
            "\"src_url\":\"%s\",\"format\":\"%s\",\"name\":\"%s\"}",
            BC_REG_BUCKET, BC_REG_REGION, cfg->token, src_url,
            cfg->transcode_fmt, (name != NULL) ? name : "video.mp4");

    /* 转码是同步长耗时（几百秒），收发超时放宽到 15 分钟 */
    http_set_timeout(900000);
    rc = http_request(cfg->relay_host, cfg->relay_port, "POST", "/transcode",
                      "Content-Type: application/json\r\n",
                      reqbody, (int)strlen(reqbody),
                      &body, &len, &status);
    http_set_timeout(30000);
    if (rc != 0) {
        set_err(err, errcap, "转码请求失败（连不上服务器）");
        if (body != NULL) {
            free(body);
        }
        return -1;
    }
    if (status != 200) {
        char tmp[64];
        if (!bili_pick_string(body, "error", err, errcap)) {
            sprintf(tmp, "转码服务器 HTTP %d", status);
            set_err(err, errcap, tmp);
        }
        if (body != NULL) {
            free(body);
        }
        return -1;
    }
    if (body == NULL || !json_get_string(body, "url", out_url, out_cap)) {
        set_err(err, errcap, "转码响应里没有 url");
        if (body != NULL) {
            free(body);
        }
        return -1;
    }
    json_unescape(out_url);
    free(body);
    /* COS 支持明文 http：老设备没有 TLS，直接降级 */
    url_downgrade_https(out_url);
    return 0;
}

/* ---- 下载媒体（URL 解析/跳转/流式写盘都在 http.c 里） ---- */

int bili_download(BcConfig *cfg, const char *url, const char *dst_path,
                  void (*progress)(long done, long total), long *out_total,
                  char *err, int errcap)
{
    (void)cfg;
    if (http_get_url_to_file(url,
                             "Referer: https://www.bilibili.com/\r\n",
                             dst_path, progress, out_total,
                             err, errcap) != HTTP_OK) {
        if (err != NULL && err[0] == '\0') {
            set_err(err, errcap, "下载失败");
        }
        return -1;
    }
    return 0;
}

/* 从 playurl 响应里取出所有候选直链：durl[0].url + durl[0].backup_url[]
 * （B 站会给多个 CDN 镜像，某一个不通时可以用别的） */
int bili_parse_media_urls(const char *json, char (*urls)[BC_URL_LEN], int max)
{
    int n = 0;
    const char *p;
    char tmp[BC_URL_LEN];

    if (json == NULL || urls == NULL || max <= 0) {
        return 0;
    }
    tmp[0] = '\0';
    if (json_get_string(json, "url", tmp, (int)sizeof(tmp)) && tmp[0] != '\0') {
        json_unescape(tmp);
        str_copy(urls[n], BC_URL_LEN, tmp);
        n++;
    }

    p = strstr(json, "\"backup_url\"");
    if (p != NULL) {
        p = strchr(p, '[');
        if (p != NULL) {
            p++;
            while (n < max) {
                const char *q;
                int j = 0;
                while (*p == ' ' || *p == '\t' || *p == '\r' || *p == '\n' || *p == ',') {
                    p++;
                }
                if (*p != '"') {
                    break;
                }
                p++;
                q = p;
                while (*q != '\0' && *q != '"' && j < BC_URL_LEN - 1) {
                    tmp[j++] = *q++;
                }
                tmp[j] = '\0';
                if (*q != '"') {
                    break;
                }
                p = q + 1;
                if (j > 0) {
                    str_copy(urls[n], BC_URL_LEN, tmp);
                    n++;
                }
            }
        }
    }
    return n;
}
