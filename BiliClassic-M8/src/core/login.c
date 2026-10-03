/* =====================================================================
 * login.c - B 站登录实现（移植 WP 版 LoginService/SmsLoginService/AppSign）
 * ===================================================================== */
#include "login.h"
#include "bili.h"
#include "http.h"
#include "util.h"
#include "md5.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOGIN_REFERER "Referer: https://www.bilibili.com/\r\n"

/* 短信登录用的 App UA（WP/WM 版一致） */
#define LOGIN_APP_UA \
    "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android " \
    "model/android_hd mobi_app/android_hd build/2001100 channel/master " \
    "innerVer/2001100 osVer/15 network/2"

static void get_buvid3(char *out, int cap);

static void set_err(char *err, int errcap, const char *msg)
{
    if (err != NULL && errcap > 0) {
        str_copy(err, errcap, msg);
    }
}

static int json_int(const char *json, const char *key, int fb)
{
    char pat[64];
    const char *p;

    sprintf(pat, "\"%s\"", key);
    p = strstr(json, pat);
    if (p == NULL) {
        return fb;
    }
    p += strlen(pat);
    while (*p == ' ' || *p == '\t' || *p == ':') {
        p++;
    }
    if (*p == '-' || (*p >= '0' && *p <= '9')) {
        return atoi(p);
    }
    return fb;
}

static int json_str(const char *json, const char *key, char *out, int cap)
{
    return json_get_string(json, key, out, cap);
}

static int find_field(const char *buf, const char *key, char *out, int cap)
{
    char pat[64];
    const char *p;
    const char *end;
    int n;

    out[0] = '\0';
    sprintf(pat, "\"%s\"", key);
    p = strstr(buf, pat);
    if (p == NULL) {
        return 0;
    }
    p += strlen(pat);
    while (*p == ' ' || *p == '\t' || *p == ':') {
        p++;
    }
    if (*p != '"') {
        return 0;
    }
    p++;
    end = p;
    while (*end != '\0' && *end != '"') {
        end++;
    }
    n = (int)(end - p);
    if (n >= cap) {
        n = cap - 1;
    }
    memcpy(out, p, (size_t)n);
    out[n] = '\0';
    return 1;
}

static void form_encode(const char *in, char *out, int cap)
{
    int o = 0;
    const unsigned char *p = (const unsigned char *)in;

    if (in == NULL) {
        out[0] = '\0';
        return;
    }
    while (*p != '\0' && o < cap - 4) {
        unsigned char c = *p++;

        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' ||
            c == '*') {
            out[o++] = (char)c;
        } else if (c == ' ') {
            out[o++] = '+';
        } else {
            out[o++] = '%';
            out[o++] = "0123456789ABCDEF"[c >> 4];
            out[o++] = "0123456789ABCDEF"[c & 15];
        }
    }
    out[o] = '\0';
}

typedef struct {
    char name[32];
    char val[512];
} LoginParam;

static void param_add(LoginParam *ps, int *n, const char *name, const char *val)
{
    if (*n >= 32) {
        return;
    }
    str_copy(ps[*n].name, (int)sizeof(ps[*n].name), name);
    str_copy(ps[*n].val, (int)sizeof(ps[*n].val), (val != NULL) ? val : "");
    (*n)++;
}

static void param_set(LoginParam *ps, int n, const char *name, const char *val)
{
    int i;

    for (i = 0; i < n; i++) {
        if (strcmp(ps[i].name, name) == 0) {
            str_copy(ps[i].val, (int)sizeof(ps[i].val),
                     (val != NULL) ? val : "");
            return;
        }
    }
}

static void build_signed_body(LoginParam *ps, int *n,
                              const char *appKey, const char *appSec,
                              char *out, int cap)
{
    char enc[2600];
    char sign[40];
    int order[32];
    int used[32];
    int i;
    int j;

    param_add(ps, n, "appkey", appKey);
    {
        char ts[24];
        sprintf(ts, "%ld", (long)bc_unix_time());
        param_add(ps, n, "ts", ts);
    }
    for (i = 0; i < *n; i++) {
        used[i] = 0;
        order[i] = 0;
    }
    for (i = 0; i < *n; i++) {
        int best = -1;

        for (j = 0; j < *n; j++) {
            if (used[j]) {
                continue;
            }
            if (best < 0 || strcmp(ps[j].name, ps[best].name) < 0) {
                best = j;
            }
        }
        order[i] = best;
        used[best] = 1;
    }
    enc[0] = '\0';
    for (i = 0; i < *n; i++) {
        char en[1024];
        int k = order[i];

        if (i > 0) {
            str_append(enc, (int)sizeof(enc), "&");
        }
        form_encode(ps[k].name, en, (int)sizeof(en));
        str_append(enc, (int)sizeof(enc), en);
        str_append(enc, (int)sizeof(enc), "=");
        form_encode(ps[k].val, en, (int)sizeof(en));
        str_append(enc, (int)sizeof(enc), en);
    }
    {
        char full[2900];
        str_copy(full, (int)sizeof(full), enc);
        str_append(full, (int)sizeof(full), appSec);
        md5_hex(full, (int)strlen(full), sign);
    }
    param_add(ps, n, "sign", sign);
    out[0] = '\0';
    for (i = 0; i < *n; i++) {
        char en[1024];

        if (i > 0) {
            str_append(out, cap, "&");
        }
        form_encode(ps[i].name, en, (int)sizeof(en));
        str_append(out, cap, en);
        str_append(out, cap, "=");
        form_encode(ps[i].val, en, (int)sizeof(en));
        str_append(out, cap, en);
    }
}

/* ---- Cookie ---- */

int bc_login_is_logged_in(BcConfig *cfg)
{
    return (cfg != NULL && strstr(cfg->cookie, "SESSDATA=") != NULL) ? 1 : 0;
}

void bc_login_set_cookie(BcConfig *cfg, const char *cookie)
{
    if (cfg == NULL) {
        return;
    }
    str_copy(cfg->cookie, (int)sizeof(cfg->cookie),
             (cookie != NULL) ? cookie : "");
    bili_save_cookie(cfg);
}

void bc_login_logout(BcConfig *cfg)
{
    if (cfg == NULL) {
        return;
    }
    cfg->cookie[0] = '\0';
    bili_save_cookie(cfg);
}

static void cookie_append(BcConfig *cfg, const char *name, const char *val)
{
    if (cfg->cookie[0] != '\0') {
        str_append(cfg->cookie, (int)sizeof(cfg->cookie), "; ");
    }
    str_append(cfg->cookie, (int)sizeof(cfg->cookie), name);
    str_append(cfg->cookie, (int)sizeof(cfg->cookie), "=");
    str_append(cfg->cookie, (int)sizeof(cfg->cookie), val);
}

/* SMS 的 cookie_info.cookies[] -> Cookie 串 */
static void cookie_from_pairs(BcConfig *cfg, const char *body)
{
    const char *p = body;
    int n = 0;

    cfg->cookie[0] = '\0';
    while (p != NULL && *p != '\0') {
        const char *np = strstr(p, "\"name\"");
        const char *vp;
        char name[64];
        char val[512];

        if (np == NULL) {
            break;
        }
        vp = strstr(np, "\"value\"");
        if (vp == NULL) {
            break;
        }
        if (!find_field(np, "name", name, (int)sizeof(name))) {
            p = np + 6;
            continue;
        }
        if (!find_field(vp, "value", val, (int)sizeof(val))) {
            p = vp + 7;
            continue;
        }
        if (name[0] != '\0' && val[0] != '\0') {
            cookie_append(cfg, name, val);
            n++;
        }
        p = vp + 7;
    }
    if (n > 0) {
        bili_save_cookie(cfg);
    }
}

static void cookie_from_crossdomain(BcConfig *cfg, const char *url)
{
    static const char *keys[] = { "SESSDATA", "DedeUserID",
                                  "DedeUserID__ckMd5", "bili_jct" };
    char clean[1400];
    int i;

    /* 反转义（JSON 里可能是 \/ \u0026 等） */
    {
        int o = 0;
        const char *p = url;
        while (*p != '\0' && o < (int)sizeof(clean) - 1) {
            if (p[0] == '\\' && p[1] == '/') {
                clean[o++] = '/'; p += 2; continue;
            }
            if (p[0] == '\\' && p[1] == '"') {
                clean[o++] = '"'; p += 2; continue;
            }
            if (p[0] == '\\' && p[1] == 'u' && p[2] == '0' && p[3] == '0') {
                int hi = p[4];
                int lo = p[5];
                int v = 0;
                if (hi >= '0' && hi <= '9') v = (hi - '0') << 4;
                else if (hi >= 'a' && hi <= 'f') v = (hi - 'a' + 10) << 4;
                else if (hi >= 'A' && hi <= 'F') v = (hi - 'A' + 10) << 4;
                if (lo >= '0' && lo <= '9') v |= (lo - '0');
                else if (lo >= 'a' && lo <= 'f') v |= (lo - 'a' + 10);
                else if (lo >= 'A' && lo <= 'F') v |= (lo - 'A' + 10);
                clean[o++] = (char)v; p += 6; continue;
            }
            clean[o++] = *p++;
        }
        clean[o] = '\0';
    }

    cfg->cookie[0] = '\0';
    for (i = 0; i < 4; i++) {
        char pat[48];
        const char *p;
        const char *e;
        char val[512];
        int len;

        sprintf(pat, "%s=", keys[i]);
        p = strstr(clean, pat);
        if (p == NULL) {
            continue;
        }
        p += strlen(pat);
        e = p;
        while (*e != '\0' && *e != '&' && *e != '"') {
            e++;
        }
        len = (int)(e - p);
        if (len <= 0 || len >= (int)sizeof(val)) {
            continue;
        }
        memcpy(val, p, (size_t)len);
        val[len] = '\0';
        cookie_append(cfg, keys[i], val);
    }
}

/* 从响应头的 Set-Cookie 里补 cookie。
 * 新版 /qrcode/poll 登录成功后 data.url 可能是空的，cookie 只在
 * Set-Cookie 头里；这里把缺的那几个补上（已有的不动）。 */
static void cookie_from_setcookie(BcConfig *cfg, const char *headers)
{
    static const char *keys[] = { "SESSDATA", "DedeUserID",
                                  "DedeUserID__ckMd5", "bili_jct" };
    int i;

    if (headers == NULL) {
        return;
    }
    for (i = 0; i < 4; i++) {
        char have[64];
        char pat[96];
        const char *p;
        const char *e;
        char val[512];
        int len;

        sprintf(have, "%s=", keys[i]);
        if (strstr(cfg->cookie, have) != NULL) {
            continue;                 /* 已经有了 */
        }
        sprintf(pat, "Set-Cookie: %s=", keys[i]);
        p = strstr(headers, pat);
        if (p == NULL) {
            sprintf(pat, "set-cookie: %s=", keys[i]);
            p = strstr(headers, pat);
        }
        if (p == NULL) {
            continue;
        }
        p += strlen(pat);
        e = p;
        while (*e != '\0' && *e != ';' && *e != '\r' && *e != '\n') {
            e++;
        }
        len = (int)(e - p);
        if (len <= 0 || len >= (int)sizeof(val)) {
            continue;
        }
        memcpy(val, p, (size_t)len);
        val[len] = '\0';
        cookie_append(cfg, keys[i], val);
    }
}

char g_login_qr_cross[1400];char g_login_sms_raw[400];
char g_login_sms_req[900];

/* ---- 资料 ---- */

int bc_login_nav(BcConfig *cfg, char **out_body, int *out_len,
                 char *err, int errcap)
{
    int status = 0;
    (void)cfg;
    if (http_get_https("https://api.bilibili.com/x/web-interface/nav",
                       bili_req_headers(), out_body, out_len, &status,
                       err, errcap) == HTTP_OK && status == 200) {
        return 0;
    }
    set_err(err, errcap, "取资料失败");
    return -1;
}

/* ---- 二维码 ---- */

int bc_login_qr_generate(char *qrcode_key, int kcap,
                         char *url, int ucap, char *err, int errcap)
{
    char *body = NULL;
    int len = 0;
    int status = 0;

    qrcode_key[0] = '\0';
    url[0] = '\0';
    if (http_get_https(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
            "?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F",
            LOGIN_REFERER, &body, &len, &status, err, errcap) != HTTP_OK ||
        status != 200 || body == NULL) {
        set_err(err, errcap, "获取二维码失败");
        if (body != NULL) {
            free(body);
        }
        return -1;
    }
    if (!json_str(body, "qrcode_key", qrcode_key, kcap) ||
        !json_str(body, "url", url, ucap)) {
        set_err(err, errcap, "二维码响应无法解析");
        free(body);
        return -1;
    }
    free(body);
    return 0;
}

int bc_login_qr_poll(BcConfig *cfg, const char *qrcode_key, int *state,
                     char *err, int errcap)
{
    char url[600];
    char *body = NULL;
    int len = 0;
    int status = 0;
    int outer;
    int inner;
    const char *data;

    *state = -1;
    sprintf(url,
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
            "?source=main-fe-header&qrcode_key=%s", qrcode_key);
    if (http_get_https(url, LOGIN_REFERER, &body, &len, &status,
                       err, errcap) != HTTP_OK || status != 200 ||
        body == NULL) {
        set_err(err, errcap, "轮询失败");
        if (body != NULL) {
            free(body);
        }
        return -1;
    }
    outer = json_int(body, "code", -1);
    if (outer != 0) {
        set_err(err, errcap, "接口返回 code!=0");
        free(body);
        return -1;
    }
    data = strstr(body, "\"data\"");
    if (data == NULL) {
        data = body;
    }
    inner = json_int(data, "code", -1);
    if (inner == 0) {
        char cross[1024];

        cross[0] = '\0';
        json_str(data, "url", cross, (int)sizeof(cross));
        str_copy(g_login_qr_cross, (int)sizeof(g_login_qr_cross), cross);
        cookie_from_crossdomain(cfg, cross);
        if (!bc_login_is_logged_in(cfg)) {
            /* 兜底 1：有些返回把 cookie 放在整段 body 里 */
            cookie_from_crossdomain(cfg, body);
        }
        if (!bc_login_is_logged_in(cfg)) {
            /* 兜底 2：新版把 cookie 放在响应头的 Set-Cookie 里 */
            cookie_from_setcookie(cfg, http_last_headers());
        }
        if (bc_login_is_logged_in(cfg)) {
            bili_save_cookie(cfg);
            *state = 0;
        } else {
            char eb[96];

            sprintf(eb, "登录成功但没解析到 SESSDATA（url %d 字节）",
                    (int)strlen(cross));
            set_err(err, errcap, eb);
            *state = -1;
        }
    } else if (inner == 86101 || inner == 86090 || inner == 86038) {
        *state = inner;
    } else {
        set_err(err, errcap, "未知扫码状态");
    }
    free(body);
    return 0;
}

/* ---- 短信 ---- */

static void get_buvid3(char *out, int cap)
{
    const char *p = strstr(bili_get_cookie(), "buvid3=");
    int k = 0;

    out[0] = '\0';
    if (p == NULL) {
        return;
    }
    p += 7;
    while (*p != '\0' && *p != ';' && k < cap - 1) {
        out[k++] = *p++;
    }
    out[k] = '\0';
}

/* 短信接口要求的 App 头（buvid/env/app-key/x-bili-trace-id/UA/Referer） */
static const char *app_headers(void)
{
    static char h[900];
    char buvid[400];

    get_buvid3(buvid, (int)sizeof(buvid));
    sprintf(h,
            "User-Agent: %s\r\n"
            "Referer: https://www.bilibili.com/\r\n"
            "Origin: https://www.bilibili.com\r\n"
            "buvid: %s\r\n"
            "env: prod\r\n"
            "app-key: android_hd\r\n"
            "x-bili-trace-id: "
            "11111111111111111111111111111111:1111111111111111:0:0\r\n",
            LOGIN_APP_UA, buvid);
    return h;
}

int bc_login_send_sms(const char *tel, char *captcha_key, int ckcap,
                      char *msg, int msgcap)
{
    LoginParam ps[32];
    int n = 0;
    char body[4096];
    char *resp = NULL;
    int len = 0;
    int status = 0;
    char idbuf[400];
    char ses[80];
    char mix[512];
    char err[160];
    long tsMs = bc_unix_time() * 1000L;

    if (captcha_key != NULL && ckcap > 0) {
        captcha_key[0] = '\0';
    }
    if (msg != NULL && msgcap > 0) {
        msg[0] = '\0';
    }
    get_buvid3(idbuf, (int)sizeof(idbuf));
    sprintf(mix, "%s%ld", idbuf, tsMs);
    md5_hex(mix, (int)strlen(mix), ses);

    param_add(ps, &n, "build", "2001100");
    param_add(ps, &n, "buvid", idbuf);
    param_add(ps, &n, "c_locale", "zh_CN");
    param_add(ps, &n, "channel", "master");
    param_add(ps, &n, "cid", "86");
    param_add(ps, &n, "disable_rcmd", "0");
    param_add(ps, &n, "local_id", idbuf);
    param_add(ps, &n, "login_session_id", ses);
    param_add(ps, &n, "mobi_app", "android_hd");
    param_add(ps, &n, "platform", "android");
    param_add(ps, &n, "s_locale", "zh_CN");
    param_add(ps, &n, "statistics",
              "{\"appId\":5,\"platform\":3,\"version\":\"2.0.1\",\"abtest\":\"\"}");
    param_add(ps, &n, "tel", tel);
    build_signed_body(ps, &n, "dfca71928277209b",
                      "b5475a8825547a4fc26c7d518eaaa02e",
                      body, (int)sizeof(body));
    str_copy(g_login_sms_req, (int)sizeof(g_login_sms_req), body);

    err[0] = '\0';
    if (http_post_https(
            "https://passport.bilibili.com/x/passport-login/sms/send",
            app_headers(), body, (int)strlen(body),
            &resp, &len, &status, err, (int)sizeof(err)) != HTTP_OK ||
        status != 200 || resp == NULL) {
        if (msg != NULL && msgcap > 0) {
            str_copy(msg, msgcap, (err[0] != '\0') ? err : "网络失败");
        }
        if (resp != NULL) {
            free(resp);
        }
        return -1;
    }
    {
        int code = json_int(resp, "code", -1);

        str_copy(g_login_sms_raw, (int)sizeof(g_login_sms_raw), resp);
        if (code == 0) {
            if (captcha_key != NULL && ckcap > 0) {
                json_str(resp, "captcha_key", captcha_key, ckcap);
            }
            if (msg != NULL && msgcap > 0) {
                str_copy(msg, msgcap, "验证码已发送");
            }
            free(resp);
            return 0;
        }
        if (msg != NULL && msgcap > 0) {
            json_str(resp, "message", msg, msgcap);
            if (msg[0] == '\0') {
                sprintf(msg, "发送失败 code=%d", code);
            }
        }
    }
    free(resp);
    return -1;
}

int bc_login_by_sms(BcConfig *cfg, const char *tel, const char *code,
                    const char *captcha_key, char *msg, int msgcap)
{
    LoginParam ps[32];
    int n = 0;
    char body[4096];
    char *resp = NULL;
    int len = 0;
    int status = 0;
    char idbuf[400];
    char device[64];
    char err[160];

    if (msg != NULL && msgcap > 0) {
        msg[0] = '\0';
    }
    get_buvid3(idbuf, (int)sizeof(idbuf));
    str_copy(device, (int)sizeof(device), idbuf);
    if (device[0] == '\0' && cfg != NULL) {
        str_copy(device, (int)sizeof(device), cfg->install_id);
    }

    param_add(ps, &n, "bili_local_id", device);
    param_add(ps, &n, "build", "2001100");
    param_add(ps, &n, "buvid", idbuf);
    param_add(ps, &n, "c_locale", "zh_CN");
    param_add(ps, &n, "captcha_key",
              (captcha_key != NULL) ? captcha_key : "");
    param_add(ps, &n, "channel", "master");
    param_add(ps, &n, "cid", "86");
    param_add(ps, &n, "code", code);
    param_add(ps, &n, "device", "phone");
    param_add(ps, &n, "device_id", device);
    param_add(ps, &n, "device_name", "vivo");
    param_add(ps, &n, "device_platform", "Android14vivo");
    param_add(ps, &n, "disable_rcmd", "0");
    param_add(ps, &n, "from_pv", "main.my-information.my-login.0.click");
    param_add(ps, &n, "from_url", "bilibili://user_center/mine");
    param_add(ps, &n, "local_id", idbuf);
    param_add(ps, &n, "mobi_app", "android_hd");
    param_add(ps, &n, "platform", "android");
    param_add(ps, &n, "s_locale", "zh_CN");
    param_add(ps, &n, "statistics",
              "{\"appId\":5,\"platform\":3,\"version\":\"2.0.1\",\"abtest\":\"\"}");
    param_add(ps, &n, "tel", tel);
    build_signed_body(ps, &n, "dfca71928277209b",
                      "b5475a8825547a4fc26c7d518eaaa02e",
                      body, (int)sizeof(body));

    err[0] = '\0';
    if (http_post_https(
            "https://passport.bilibili.com/x/passport-login/login/sms",
            app_headers(), body, (int)strlen(body),
            &resp, &len, &status, err, (int)sizeof(err)) != HTTP_OK ||
        status != 200 || resp == NULL) {
        if (msg != NULL && msgcap > 0) {
            str_copy(msg, msgcap, (err[0] != '\0') ? err : "网络失败");
        }
        if (resp != NULL) {
            free(resp);
        }
        return -1;
    }
    {
        int rc = json_int(resp, "code", -1);

        if (rc == 0) {
            cookie_from_pairs(cfg, resp);
            if (bc_login_is_logged_in(cfg)) {
                if (msg != NULL && msgcap > 0) {
                    str_copy(msg, msgcap, "登录成功");
                }
                free(resp);
                return 0;
            }
            if (msg != NULL && msgcap > 0) {
                str_copy(msg, msgcap, "登录成功但没拿到 Cookie");
            }
        } else if (msg != NULL && msgcap > 0) {
            json_str(resp, "message", msg, msgcap);
            if (msg[0] == '\0') {
                sprintf(msg, "登录失败 code=%d", rc);
            }
        }
    }
    free(resp);
    return -1;
}

/* ---- 收藏 ---- */

/* 从 Cookie 里取自己的 mid（DedeUserID），取不到就给空串 */
static void cookie_mid(BcConfig *cfg, char *out, int cap)
{
    const char *p;
    int k = 0;

    if (cap <= 0) {
        return;
    }
    out[0] = '\0';
    if (cfg == NULL) {
        return;
    }
    p = strstr(cfg->cookie, "DedeUserID=");
    if (p == NULL) {
        return;
    }
    p += 11;
    while (*p != '\0' && *p != ';' && k < cap - 1) {
        out[k++] = *p++;
    }
    out[k] = '\0';
}

int bc_login_favs(BcConfig *cfg, char **out_body, int *out_len,
                  char *err, int errcap)
{
    char mid[32];
    char url[300];
    int status = 0;

    if (!bc_login_is_logged_in(cfg)) {
        set_err(err, errcap, "未登录");
        return -1;
    }
    cookie_mid(cfg, mid, (int)sizeof(mid));
    sprintf(url,
            "https://api.bilibili.com/x/v3/fav/folder/created/list-all"
            "?up_mid=%s", mid);
    if (http_get_https(url, bili_req_headers(), out_body, out_len, &status,
                       err, errcap) == HTTP_OK && status == 200) {
        return 0;
    }
    set_err(err, errcap, "取收藏失败");
    return -1;
}

/* 收藏夹列表接口不给封面，取夹里第一个视频的图当封面（和安卓版一致）：
 *   /x/space/fav/arc?vmid=..&ps=1&fid=..&pn=1&order=fav_time
 * 成功返回 0，pic 里是封面 URL。 */
int bc_login_fav_cover(BcConfig *cfg, const char *fid, char *pic, int cap,
                       char *err, int errcap)
{
    char mid[32];
    char url[340];
    char *body = NULL;
    int len = 0;
    int status = 0;
    const char *arch;
    int rc = -1;

    if (cap > 0) {
        pic[0] = '\0';
    }
    if (!bc_login_is_logged_in(cfg)) {
        set_err(err, errcap, "未登录");
        return -1;
    }
    cookie_mid(cfg, mid, (int)sizeof(mid));
    if (mid[0] == '\0' || fid == NULL || fid[0] == '\0') {
        set_err(err, errcap, "拿不到 mid 或收藏夹 id");
        return -1;
    }
    sprintf(url,
            "https://api.bilibili.com/x/space/fav/arc"
            "?vmid=%s&ps=1&fid=%s&tid=0&keyword=&pn=1&order=fav_time",
            mid, fid);
    if (http_get_https(url, bili_req_headers(), &body, &len, &status,
                       err, errcap) == HTTP_OK && status == 200 &&
        body != NULL) {
        /* data.archives[0].pic；直接从 "archives" 往后找，避免吃到别的 pic */
        arch = strstr(body, "\"archives\"");
        if (arch == NULL) {
            arch = strstr(body, "\"medias\"");
        }
        if (arch == NULL) {
            arch = strstr(body, "\"list\"");
        }
        if (arch == NULL) {
            arch = body;
        }
        if (json_get_string(arch, "pic", pic, cap) ||
            json_get_string(arch, "cover", pic, cap)) {
            rc = 0;
        } else {
            long code = -1;
            char eb[48];

            json_get_int(body, "code", &code);
            sprintf(eb, "封面字段缺失 code=%ld", code);
            set_err(err, errcap, eb);
        }
        if (rc != 0 && cap > 0) {
            pic[0] = '\0';
        }
        free(body);
    } else if (err != NULL && errcap > 0 && err[0] == '\0') {
        set_err(err, errcap, "取封面失败");
    }
    return rc;
}

/* 收藏夹封面「一把抓」：space.bilibili.com/ajax/fav/getBoxList?mid=<mid>
 * 旧接口，一次返回所有夹 + 每个夹第一个视频（含 pic）。 */
int bc_login_fav_boxlist(BcConfig *cfg, char **out_body, int *out_len,
                         char *err, int errcap)
{
    char mid[32];
    char url[220];
    int status = 0;

    if (!bc_login_is_logged_in(cfg)) {
        set_err(err, errcap, "未登录");
        return -1;
    }
    cookie_mid(cfg, mid, (int)sizeof(mid));
    if (mid[0] == '\0') {
        set_err(err, errcap, "取不到 mid");
        return -1;
    }
    sprintf(url, "https://space.bilibili.com/ajax/fav/getBoxList?mid=%s", mid);
    if (http_get_https(url, bili_req_headers(), out_body, out_len, &status,
                       err, errcap) == HTTP_OK && status == 200) {
        return 0;
    }
    set_err(err, errcap, "取收藏夹封面失败");
    return -1;
}

/* 从 getBoxList 响应里按 fid 取封面。
 * 结构：{"status":true,"data":{"list":[{"fav_box":<fid>,"videos":[{"pic":".."}]}]}}
 * 用「第 k 个 fav_box 配第 k 个 videos」按序配对，不依赖对象内部键顺序。 */
int bc_fav_cover_boxlist(const char *body, const char *fid,
                         char *pic, int cap)
{
    const char *fb;
    const char *vd;
    size_t flen;

    if (cap > 0) {
        pic[0] = '\0';
    }
    if (body == NULL || fid == NULL || fid[0] == '\0' || cap <= 1) {
        return -1;
    }
    flen = strlen(fid);
    fb = body;
    vd = body;
    for (;;) {
        const char *f = strstr(fb, "\"fav_box\"");
        const char *v = strstr(vd, "\"videos\"");
        const char *s;
        const char *e;

        if (f == NULL || v == NULL) {
            return -1;
        }
        s = f + 9;
        while (*s != '\0' && *s != ':') {
            s++;
        }
        if (*s == ':') {
            s++;
        }
        while (*s == ' ' || *s == '\t') {
            s++;
        }
        if (*s == '"') {
            s++;
            e = strchr(s, '"');
        } else {
            e = s;
            while (*e >= '0' && *e <= '9') {
                e++;
            }
        }
        if (e == NULL || e == s) {
            return -1;
        }
        if ((size_t)(e - s) == flen && strncmp(s, fid, flen) == 0) {
            const char *nextv = strstr(v + 8, "\"videos\"");
            const char *pp = strstr(v + 8, "\"pic\"");

            if (pp != NULL && (nextv == NULL || pp < nextv)) {
                return json_get_string(pp, "pic", pic, cap) ? 0 : -1;
            }
            return -1;
        }
        fb = f + 9;
        vd = v + 8;
    }
}

int bc_login_fav_videos(BcConfig *cfg, long fid, int page,
                        char **out_body, int *out_len,
                        char *err, int errcap)
{
    char url[320];
    int status = 0;

    if (!bc_login_is_logged_in(cfg)) {
        set_err(err, errcap, "未登录");
        return -1;
    }
    sprintf(url,
            "https://api.bilibili.com/x/v3/fav/resource/list"
            "?media_id=%ld&pn=%d&ps=30&platform=web",
            fid, page);
    if (http_get_https(url, bili_req_headers(), out_body, out_len, &status,
                       err, errcap) == HTTP_OK && status == 200) {
        return 0;
    }
    set_err(err, errcap, "取收藏视频失败");
    return -1;
}

int bc_login_history(BcConfig *cfg, long max, long view_at,
                     char **out_body, int *out_len,
                     char *err, int errcap)
{
    char url[300];
    int status = 0;

    if (!bc_login_is_logged_in(cfg)) {
        set_err(err, errcap, "未登录");
        return -1;
    }
    sprintf(url,
            "https://api.bilibili.com/x/web-interface/history/cursor"
            "?ps=30&max=%ld&view_at=%ld&business=",
            max, view_at);
    if (http_get_https(url, bili_req_headers(), out_body, out_len, &status,
                       err, errcap) == HTTP_OK && status == 200) {
        return 0;
    }
    set_err(err, errcap, "取历史失败");
    return -1;
}
