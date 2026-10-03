/* =====================================================================
 * inflate.h - 纯 C 的 DEFLATE 解压（原始 / zlib / gzip）
 * ---------------------------------------------------------------------
 * 移植自 BiliClassic WP 版 Danmaku/Inflate.cs。B 站弹幕 XML 是 deflate 压缩的。
 * ===================================================================== */
#ifndef BC_INFLATE_H
#define BC_INFLATE_H

#ifdef __cplusplus
extern "C" {
#endif

/* 成功返回 malloc 出来的缓冲区并写 *outlen；失败返回 NULL。 */
unsigned char *bc_inflate(const unsigned char *in, int inlen, int *outlen);

#ifdef __cplusplus
}
#endif

#endif /* BC_INFLATE_H */
