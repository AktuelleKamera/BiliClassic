/* =====================================================================
 * login.h - B 站登录（二维码 / 短信验证码 / 手动 Cookie）+ 资料 + 收藏
 * ---------------------------------------------------------------------
 * 移植自 WP 版 Api/LoginService.cs / SmsLoginService.cs / AppSign.cs。
 * 全部走 wolfSSL HTTPS 直连 passport.bilibili.com。
 * ===================================================================== */
#ifndef BC_LOGIN_H
#define BC_LOGIN_H

#include "bc.h"

#ifdef __cplusplus
extern "C" {
#endif

/* 是否已登录（Cookie 里含 SESSDATA） */
int  bc_login_is_logged_in(BcConfig *cfg);

/* 手动：整串 Cookie（"k=v; k=v"）或只贴 SESSDATA 都会尽量规整；会保存 */
void bc_login_set_cookie(BcConfig *cfg, const char *cookie);
void bc_login_logout(BcConfig *cfg);

/* 个人资料（/x/web-interface/nav），成功返回 0；*out_body 需 free */
int  bc_login_nav(BcConfig *cfg, char **out_body, int *out_len,
                  char *err, int errcap);

/* ---- 二维码登录 ---- */
/* 取二维码内容（url）与 qrcode_key */
int  bc_login_qr_generate(char *qrcode_key, int kcap,
                          char *url, int ucap, char *err, int errcap);
/* 轮询。成功返回 0；*state: 0 成功 / 86101 未扫 / 86090 已扫 / 86038 过期 / -1 失败。
 * 成功时把 Cookie 写进 cfg 并保存。 */
int  bc_login_qr_poll(BcConfig *cfg, const char *qrcode_key, int *state,
                      char *err, int errcap);

/* ---- 短信验证码登录 ---- */
/* 发送验证码。成功返回 0；captcha_key/message 可能非空 */
int  bc_login_send_sms(const char *tel, char *captcha_key, int ckcap,
                       char *msg, int msgcap);
/* 用验证码登录。成功返回 0，并把 Cookie 写进 cfg 保存 */
int  bc_login_by_sms(BcConfig *cfg, const char *tel, const char *code,
                     const char *captcha_key, char *msg, int msgcap);

/* ---- 收藏夹（第一页，登录后可用） ---- */
int  bc_login_favs(BcConfig *cfg, char **out_body, int *out_len,
                   char *err, int errcap);
/* 收藏夹封面：取夹里第一个视频的图（fid 传 items[].bvid，也就是 media_id）。
 * 成功返回 0，pic 填 URL。 */
int  bc_login_fav_cover(BcConfig *cfg, const char *fid, char *pic, int cap,
                        char *err, int errcap);

/* 收藏夹封面「一把抓」：旧接口 space.bilibili.com/ajax/fav/getBoxList?mid=..
 * 一次拿到所有夹第一张图，成功返回 0（*out_body 需 free）。 */
int  bc_login_fav_boxlist(BcConfig *cfg, char **out_body, int *out_len,
                          char *err, int errcap);

/* 从 getBoxList 响应里按 fid(media_id) 取封面 URL，成功返回 0 */
int  bc_fav_cover_boxlist(const char *body, const char *fid,
                          char *pic, int cap);

/* ---- 收藏夹里的视频（page 从 1 开始，登录后可用） ---- */
int  bc_login_fav_videos(BcConfig *cfg, long fid, int page,
                         char **out_body, int *out_len,
                         char *err, int errcap);

/* ---- 观看历史（游标翻页，首屏 max=0/view_at=0） ---- */
int  bc_login_history(BcConfig *cfg, long max, long view_at,
                      char **out_body, int *out_len,
                      char *err, int errcap);

/* 诊断用：最近一次扫码 crossDomain URL / 短信原始响应 */
extern char g_login_qr_cross[1400];
extern char g_login_sms_raw[400];
extern char g_login_sms_req[900];

#ifdef __cplusplus
}
#endif

#endif /* BC_LOGIN_H */
