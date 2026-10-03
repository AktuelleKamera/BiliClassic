/* =====================================================================
 * sha1.h - SHA-1 与 HMAC-SHA1（C89，仅用于 /register 签名）
 * ===================================================================== */
#ifndef BC_SHA1_H
#define BC_SHA1_H

#ifdef __cplusplus
extern "C" {
#endif

#define SHA1_DIGEST_SIZE 20
#define SHA1_BLOCK_SIZE  64

typedef struct {
    unsigned long state[5];
    unsigned long count[2];
    unsigned char buffer[SHA1_BLOCK_SIZE];
} Sha1Ctx;

void sha1_init(Sha1Ctx *ctx);
void sha1_update(Sha1Ctx *ctx, const unsigned char *data, unsigned int len);
void sha1_final(Sha1Ctx *ctx, unsigned char digest[SHA1_DIGEST_SIZE]);

/* hex(hmac_sha1(key, msg)) -> out(41 字节，含结尾 0) */
void hmac_sha1_hex(const char *key, const char *msg, char *out, int cap);

#ifdef __cplusplus
}
#endif

#endif /* BC_SHA1_H */
