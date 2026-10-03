/* =====================================================================
 * sha1.c - SHA-1 + HMAC-SHA1（公开算法，自行实现，C89）
 * ===================================================================== */
#include "sha1.h"
#include <string.h>
#include <stdio.h>

#define ROL32(v, n) (((v) << (n)) | ((v) >> (32 - (n))))

static void sha1_transform(unsigned long state[5], const unsigned char block[64])
{
    unsigned long w[80];
    unsigned long a, b, c, d, e, t;
    int i;

    for (i = 0; i < 16; i++) {
        w[i] = ((unsigned long)block[i * 4] << 24) |
               ((unsigned long)block[i * 4 + 1] << 16) |
               ((unsigned long)block[i * 4 + 2] << 8) |
               ((unsigned long)block[i * 4 + 3]);
    }
    for (i = 16; i < 80; i++) {
        w[i] = ROL32(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
    }

    a = state[0]; b = state[1]; c = state[2]; d = state[3]; e = state[4];

    for (i = 0; i < 80; i++) {
        unsigned long f, k;
        if (i < 20) {
            f = (b & c) | ((~b) & d);
            k = 0x5A827999UL;
        } else if (i < 40) {
            f = b ^ c ^ d;
            k = 0x6ED9EBA1UL;
        } else if (i < 60) {
            f = (b & c) | (b & d) | (c & d);
            k = 0x8F1BBCDCUL;
        } else {
            f = b ^ c ^ d;
            k = 0xCA62C1D6UL;
        }
        t = ROL32(a, 5) + f + e + k + w[i];
        e = d;
        d = c;
        c = ROL32(b, 30);
        b = a;
        a = t;
    }

    state[0] += a; state[1] += b; state[2] += c;
    state[3] += d; state[4] += e;
}

void sha1_init(Sha1Ctx *ctx)
{
    ctx->state[0] = 0x67452301UL;
    ctx->state[1] = 0xEFCDAB89UL;
    ctx->state[2] = 0x98BADCFEUL;
    ctx->state[3] = 0x10325476UL;
    ctx->state[4] = 0xC3D2E1F0UL;
    ctx->count[0] = 0;
    ctx->count[1] = 0;
}

void sha1_update(Sha1Ctx *ctx, const unsigned char *data, unsigned int len)
{
    unsigned int i, j;

    j = (ctx->count[0] >> 3) & 63;
    ctx->count[0] += (unsigned long)len << 3;
    if (ctx->count[0] < ((unsigned long)len << 3)) {
        ctx->count[1]++;
    }
    ctx->count[1] += (unsigned long)len >> 29;

    if ((j + len) > 63) {
        memcpy(&ctx->buffer[j], data, 64 - j);
        sha1_transform(ctx->state, ctx->buffer);
        for (i = 64 - j; i + 63 < len; i += 64) {
            sha1_transform(ctx->state, &data[i]);
        }
        j = 0;
    } else {
        i = 0;
    }
    memcpy(&ctx->buffer[j], &data[i], len - i);
}

void sha1_final(Sha1Ctx *ctx, unsigned char digest[SHA1_DIGEST_SIZE])
{
    unsigned char finalcount[8];
    unsigned char c;
    int i;

    for (i = 0; i < 8; i++) {
        finalcount[i] = (unsigned char)((ctx->count[(i >= 4) ? 0 : 1] >>
                        ((3 - (i & 3)) * 8)) & 255);
    }
    c = 0200;
    sha1_update(ctx, &c, 1);
    while ((ctx->count[0] & 504) != 448) {
        c = 0000;
        sha1_update(ctx, &c, 1);
    }
    sha1_update(ctx, finalcount, 8);
    for (i = 0; i < SHA1_DIGEST_SIZE; i++) {
        digest[i] = (unsigned char)((ctx->state[i >> 2] >>
                     ((3 - (i & 3)) * 8)) & 255);
    }
}

static void sha1_bytes(const unsigned char *data, unsigned int len,
                       unsigned char digest[SHA1_DIGEST_SIZE])
{
    Sha1Ctx ctx;
    sha1_init(&ctx);
    sha1_update(&ctx, data, len);
    sha1_final(&ctx, digest);
}

void hmac_sha1_hex(const char *key, const char *msg, char *out, int cap)
{
    unsigned char k[SHA1_BLOCK_SIZE];
    unsigned char ipad[SHA1_BLOCK_SIZE];
    unsigned char opad[SHA1_BLOCK_SIZE];
    unsigned char inner[SHA1_DIGEST_SIZE];
    unsigned char outer[SHA1_DIGEST_SIZE];
    int i, klen;

    memset(k, 0, sizeof(k));
    klen = (int)strlen(key);
    if (klen > SHA1_BLOCK_SIZE) {
        sha1_bytes((const unsigned char *)key, (unsigned int)klen, k);
    } else {
        memcpy(k, key, (size_t)klen);
    }
    for (i = 0; i < SHA1_BLOCK_SIZE; i++) {
        ipad[i] = (unsigned char)(k[i] ^ 0x36);
        opad[i] = (unsigned char)(k[i] ^ 0x5C);
    }

    {
        Sha1Ctx ctx;
        sha1_init(&ctx);
        sha1_update(&ctx, ipad, SHA1_BLOCK_SIZE);
        sha1_update(&ctx, (const unsigned char *)msg, (unsigned int)strlen(msg));
        sha1_final(&ctx, inner);

        sha1_init(&ctx);
        sha1_update(&ctx, opad, SHA1_BLOCK_SIZE);
        sha1_update(&ctx, inner, SHA1_DIGEST_SIZE);
        sha1_final(&ctx, outer);
    }

    if (cap > SHA1_DIGEST_SIZE * 2 + 1) {
        cap = SHA1_DIGEST_SIZE * 2 + 1;
    }
    for (i = 0; i < SHA1_DIGEST_SIZE && (i * 2 + 1) < cap; i++) {
        sprintf(&out[i * 2], "%02x", outer[i]);
    }
    if (cap > 0) {
        out[cap - 1] = '\0';
    }
}
