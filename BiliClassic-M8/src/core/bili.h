/* =====================================================================
 * bili.h - B 站接口封装（Win95 测试版）
 * ---------------------------------------------------------------------
 * 走法：
 *   search / popular / nav / pagelist / playurl
 *                      -> wolfSSL HTTPS 直连 api.bilibili.com（带 SNI）
 *   transcode          -> POST 到转码服务器（SCF），把 High Profile 转成
 *                         M8 能放的老格式；此时也会 /register 换 token
 *   media              -> 直连 CDN 下载
 * ===================================================================== */
#ifndef BC_BILI_H
#define BC_BILI_H

#include "bc.h"

#ifdef __cplusplus
extern "C" {
#endif

void bili_config_init(BcConfig *cfg);
void bili_save_config(BcConfig *cfg);

/* 调试用：core 层日志回调（app 层设成 Logger，便于定位崩溃点） */
void bili_set_log(void (*fn)(const char *msg));

/* 设置/清除登录 Cookie（内存，影响之后所有 API 请求头） */
void bili_set_login_cookie(const char *c);
/* 把 cfg->cookie 写到数据目录（登录/退出后调用） */
void bili_save_cookie(BcConfig *cfg);

/* 统一请求头（含 Referer/Cookie）与设备 buvid cookie（login 模块也用） */
const char *bili_req_headers(void);
const char *bili_get_cookie(void);

/* 取转码服务器时间并缓存；转码/token 需要窗口内的时间戳 */
int  bili_sync_time(BcConfig *cfg, char *err, int errcap);

/* 让服务器把 src_url 转成老格式（cfg->transcode_fmt），结果地址写进 out_url。
 * 同步长耗时（可能几百秒），成功返回 0。 */
int  bili_transcode(BcConfig *cfg, const char *src_url, const char *name,
                    char *out_url, int out_cap, char *err, int errcap);

int  bili_search(BcConfig *cfg, const char *keyword, int page,
                 char **out_body, int *out_len, char *err, int errcap);
int  bili_popular(BcConfig *cfg, int page,
                  char **out_body, int *out_len, char *err, int errcap);
int  bili_pagelist(BcConfig *cfg, const char *bvid,
                   char **out_body, int *out_len, char *err, int errcap);
int  bili_playurl(BcConfig *cfg, const char *bvid, const char *cid,
                  char **out_body, int *out_len, char *err, int errcap);

/* 从 JSON 里抠字段的便捷包装（顺带 UTF-8 -> ANSI） */
int  bili_pick_string(const char *json, const char *key, char *out, int cap);
long bili_pick_int(const char *json, const char *key, long fallback);

/* 取一个数值字段的原始数字串（cid 等 64 位值，long 放不下），成功返回 1 */
int  bili_pick_number(const char *json, const char *key, char *out, int cap);

/* 把 search/popular 的 result[] 解成 BcItem 数组，返回条数 */
/* pic_field：封面字段名（搜索/热门用 "pic"，观看历史用 "cover"，NULL 视为 "pic"） */
int  bili_parse_items(const char *json, BcItem *items, int max,
                      const char *pic_field);

/* 收藏夹文件夹列表 -> BcItem：bvid=media_id，title=名字，author=视频数 */
int  bili_parse_fav_folders(const char *json, BcItem *items, int max);

/* 从 playurl 响应取候选直链（主地址 + backup_url 镜像），返回条数 */
#define BC_MAX_MEDIA_URLS 6
int  bili_parse_media_urls(const char *json, char (*urls)[BC_URL_LEN], int max);

/* ---- WBI 签名（B 站接口的 w_rid，防 -412） ---- */
/* 取 wbi_img 算出 mixin_key（结果缓存在 cfg 里），成功返回 0 */
int  bili_wbi_ensure(BcConfig *cfg, char *err, int errcap);
/* 把 "a=1&b=2" 按 WBI 规则排序+过滤+加 wts，输出 "排序后的query&w_rid=..." */
int  bili_wbi_sign(BcConfig *cfg, const char *query, char *out, int outcap);

/* 下载媒体到本地文件（明文 http；不需要 Referer，但带上更稳） */
int  bili_download(BcConfig *cfg, const char *url, const char *dst_path,
                   void (*progress)(long done, long total), long *out_total,
                   char *err, int errcap);

#ifdef __cplusplus
}
#endif

#endif /* BC_BILI_H */
