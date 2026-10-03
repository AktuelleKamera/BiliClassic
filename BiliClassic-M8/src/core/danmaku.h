/* =====================================================================
 * danmaku.h - B 站弹幕：拉取 comment.bilibili.com/<cid>.xml 并解析
 * ---------------------------------------------------------------------
 * 数据模型/解析规则移植自 BiliClassic WP 版 Danmaku/DanmakuParser.cs。
 * ===================================================================== */
#ifndef BC_DANMAKU_H
#define BC_DANMAKU_H

#ifdef __cplusplus
extern "C" {
#endif

#define BC_DM_TEXT_MAX 128

typedef struct {
    int          time_ms;   /* 出现时间（毫秒） */
    int          mode;      /* 1 滚动 4 底部 5 顶部 6 逆向 */
    int          size;      /* 字号（B 站原始，一般 18/25） */
    unsigned int color;     /* RGB（0xRRGGBB） */
    char         text[BC_DM_TEXT_MAX];
} BcDanmaku;

/* 拉取并解析 cid 的弹幕。成功返回条数(>0)，*out 由调用者 free()；
 * 失败返回 -1 并填 err。 */
int bc_danmaku_load(const char *cid, BcDanmaku **out, char *err, int errcap);

#ifdef __cplusplus
}
#endif

#endif /* BC_DANMAKU_H */
