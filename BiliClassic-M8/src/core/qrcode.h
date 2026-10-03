/* =====================================================================
 * qrcode.h - 极简 QR 编码器（Version 7, EC 级别 L, 45x45）
 * ---------------------------------------------------------------------
 * 移植自 WP 版 Api/QrEncoder.cs。容量 154 字节（UTF-8）。
 * ===================================================================== */
#ifndef BC_QRCODE_H
#define BC_QRCODE_H

#define QR_SIZE 45
#define QR_CAP  154

#ifdef __cplusplus
extern "C" {
#endif

/* 把 content（UTF-8）编码成 QR 矩阵。成功返回 1。
 * out 至少 QR_SIZE*QR_SIZE 字节，out[y*QR_SIZE+x] = 1 黑 / 0 白。 */
int qr_encode(const char *content, unsigned char *out);

#ifdef __cplusplus
}
#endif

#endif /* BC_QRCODE_H */
