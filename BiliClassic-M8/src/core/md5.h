/* =====================================================================
 * md5.h - MD5（WBI 签名用，C89 / 无外部依赖）
 * ===================================================================== */
#ifndef BC_MD5_H
#define BC_MD5_H

#ifdef __cplusplus
extern "C" {
#endif

/* 计算 data[0..len) 的 MD5，输出 32 位小写 hex 到 out（需 33 字节） */
void md5_hex(const char *data, int len, char *out);

#ifdef __cplusplus
}
#endif

#endif /* BC_MD5_H */
