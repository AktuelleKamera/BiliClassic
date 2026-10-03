/* =====================================================================
 * danmaku.c - 弹幕拉取 + 解析（移植自 WP 版 DanmakuLoader/Parser）
 * ===================================================================== */
#include <windows.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#include "danmaku.h"
#include "inflate.h"
#include "http.h"

static void dm_unescape(const char *src, char *dst, int cap)
{
    int o = 0;

    while (*src != '\0' && o < cap - 1) {
        if (*src == '&') {
            if (strncmp(src, "&lt;", 4) == 0) {
                dst[o++] = '<';
                src += 4;
                continue;
            }
            if (strncmp(src, "&gt;", 4) == 0) {
                dst[o++] = '>';
                src += 4;
                continue;
            }
            if (strncmp(src, "&quot;", 6) == 0) {
                dst[o++] = '"';
                src += 6;
                continue;
            }
            if (strncmp(src, "&apos;", 6) == 0) {
                dst[o++] = '\'';
                src += 6;
                continue;
            }
            if (strncmp(src, "&#39;", 5) == 0) {
                dst[o++] = '\'';
                src += 5;
                continue;
            }
            if (strncmp(src, "&amp;", 5) == 0) {
                dst[o++] = '&';
                src += 5;
                continue;
            }
        }
        dst[o++] = *src++;
    }
    dst[o] = '\0';
}

static int dm_cmp_time(const void *a, const void *b)
{
    const BcDanmaku *x = (const BcDanmaku *)a;
    const BcDanmaku *y = (const BcDanmaku *)b;
    return x->time_ms - y->time_ms;
}

static int dm_parse(const char *xml, BcDanmaku **out, int *count)
{
    const char *p = xml;
    BcDanmaku *arr = NULL;
    int n = 0;
    int cap = 0;

    while ((p = strstr(p, "<d p=\"")) != NULL) {
        const char *ps;
        const char *pe;
        const char *ts;
        const char *te;
        char pbuf[160];
        char tbuf[BC_DM_TEXT_MAX + 8];
        int plen;
        char *c1;
        char *c2;
        char *c3;
        char *c4;
        int mode;

        ps = p + 6;                     /* 跳过 <d p=" */
        pe = strchr(ps, '"');
        if (pe == NULL) {
            break;
        }
        ts = pe + 1;
        if (*ts == '>') {
            ts++;
        }
        te = strchr(ts, '<');
        if (te == NULL) {
            break;
        }

        plen = (int)(pe - ps);
        if (plen > (int)sizeof(pbuf) - 1) {
            plen = (int)sizeof(pbuf) - 1;
        }
        memcpy(pbuf, ps, plen);
        pbuf[plen] = '\0';

        c1 = pbuf;
        c2 = strchr(c1, ',');
        if (c2 == NULL) { p = te + 1; continue; }
        *c2++ = '\0';
        c3 = strchr(c2, ',');
        if (c3 == NULL) { p = te + 1; continue; }
        *c3++ = '\0';
        c4 = strchr(c3, ',');
        if (c4 == NULL) { p = te + 1; continue; }
        *c4++ = '\0';
        {
            char *c5 = strchr(c4, ',');
            if (c5 != NULL) {
                *c5 = '\0';
            }
        }

        mode = atoi(c2);
        if (mode == 2 || mode == 3) {
            mode = 1;
        }
        if (mode == 1 || mode == 4 || mode == 5 || mode == 6) {
            int tlen = (int)(te - ts);
            if (tlen > (int)sizeof(tbuf) - 1) {
                tlen = (int)sizeof(tbuf) - 1;
            }
            memcpy(tbuf, ts, tlen);
            tbuf[tlen] = '\0';

            if (n >= cap) {
                int nc = (cap == 0) ? 256 : cap * 2;
                BcDanmaku *na = (BcDanmaku *)realloc(arr,
                                        (size_t)nc * sizeof(BcDanmaku));
                if (na == NULL) {
                    break;
                }
                arr = na;
                cap = nc;
            }

            arr[n].time_ms = (int)(atof(c1) * 1000.0);
            arr[n].mode = mode;
            arr[n].size = atoi(c3);
            arr[n].color = (unsigned int)strtoul(c4, NULL, 10);
            dm_unescape(tbuf, arr[n].text, BC_DM_TEXT_MAX);
            if (arr[n].text[0] != '\0') {
                n++;
            }
        }
        p = te + 1;
    }

    if (arr != NULL && n > 1) {
        qsort(arr, (size_t)n, sizeof(BcDanmaku), dm_cmp_time);
    }
    *out = arr;
    *count = n;
    return n;
}

int bc_danmaku_load(const char *cid, BcDanmaku **out, char *err, int errcap)
{
    char url[128];
    char *body = NULL;
    int len = 0;
    int st = 0;
    int rc;
    int n = 0;
    char err2[160];

    if (out != NULL) {
        *out = NULL;
    }
    if (cid == NULL || cid[0] == '\0') {
        if (err != NULL) {
            sprintf(err, "缺少 cid");
        }
        return -1;
    }

    url[0] = '\0';
    /* 用和 API 同源的 api.bilibili.com（comment.bilibili.com 在部分设备/网络下解析不到），
     * 返回的是同一份 XML。 */
    sprintf(url, "https://api.bilibili.com/x/v1/dm/list.so?oid=%.64s", cid);
    err2[0] = '\0';
    rc = http_get_https(url, "Referer: https://www.bilibili.com/\r\n",
                        &body, &len, &st, err2, (int)sizeof(err2));
    if (rc != HTTP_OK || body == NULL) {
        if (err != NULL) {
            sprintf(err, "弹幕请求失败：%.100s", err2);
        }
        return -1;
    }
    if (st != 200) {
        if (err != NULL) {
            sprintf(err, "弹幕 HTTP %d", st);
        }
        free(body);
        return -1;
    }

    /* 明文 XML 就直接解析；否则多为 deflate 压缩，先解压 */
    if (strstr(body, "<chatserver>") != NULL) {
        dm_parse(body, out, &n);
    } else {
        int rl = 0;
        unsigned char *raw = bc_inflate((const unsigned char *)body, len, &rl);
        if (raw != NULL && rl > 0) {
            char *z = (char *)malloc((size_t)rl + 1);
            if (z != NULL) {
                memcpy(z, raw, (size_t)rl);
                z[rl] = '\0';
                dm_parse(z, out, &n);
                free(z);
            }
        }
        if (raw != NULL) {
            free(raw);
        }
    }
    free(body);

    if (n <= 0) {
        if (err != NULL) {
            sprintf(err, "未解析到弹幕（%d 字节）", len);
        }
        return -1;
    }
    return n;
}
