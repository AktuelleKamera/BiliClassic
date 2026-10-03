/* =====================================================================
 * md5.c - MD5（RFC 1321 的紧凑实现）
 * ---------------------------------------------------------------------
 * B 站 WBI 签名需要 md5(query + mixin_key)，就为这个写的。
 * 只按小端机算（Win95/98/NT4 的 x86 都是小端）。
 * ===================================================================== */
#include "md5.h"
#include <stdio.h>
#include <string.h>

typedef struct {
    unsigned long state[4];
    unsigned long count[2];
    unsigned char buf[64];
} MD5_CTX;

#define MD5_F(x, y, z) (((x) & (y)) | (~(x) & (z)))
#define MD5_G(x, y, z) (((x) & (z)) | ((y) & ~(z)))
#define MD5_H(x, y, z) ((x) ^ (y) ^ (z))
#define MD5_I(x, y, z) ((y) ^ ((x) | ~(z)))
#define MD5_ROTL(x, n) (((x) << (n)) | ((x) >> (32 - (n))))

static void md5_transform(unsigned long state[4], const unsigned char block[64])
{
    static const unsigned long K[64] = {
        0xd76aa478UL, 0xe8c7b756UL, 0x242070dbUL, 0xc1bdceeeUL,
        0xf57c0fafUL, 0x4787c62aUL, 0xa8304613UL, 0xfd469501UL,
        0x698098d8UL, 0x8b44f7afUL, 0xffff5bb1UL, 0x895cd7beUL,
        0x6b901122UL, 0xfd987193UL, 0xa679438eUL, 0x49b40821UL,
        0xf61e2562UL, 0xc040b340UL, 0x265e5a51UL, 0xe9b6c7aaUL,
        0xd62f105dUL, 0x02441453UL, 0xd8a1e681UL, 0xe7d3fbc8UL,
        0x21e1cde6UL, 0xc33707d6UL, 0xf4d50d87UL, 0x455a14edUL,
        0xa9e3e905UL, 0xfcefa3f8UL, 0x676f02d9UL, 0x8d2a4c8aUL,
        0xfffa3942UL, 0x8771f681UL, 0x6d9d6122UL, 0xfde5380cUL,
        0xa4beea44UL, 0x4bdecfa9UL, 0xf6bb4b60UL, 0xbebfbc70UL,
        0x289b7ec6UL, 0xeaa127faUL, 0xd4ef3085UL, 0x04881d05UL,
        0xd9d4d039UL, 0xe6db99e5UL, 0x1fa27cf8UL, 0xc4ac5665UL,
        0xf4292244UL, 0x432aff97UL, 0xab9423a7UL, 0xfc93a039UL,
        0x655b59c3UL, 0x8f0ccc92UL, 0xffeff47dUL, 0x85845dd1UL,
        0x6fa87e4fUL, 0xfe2ce6e0UL, 0xa3014314UL, 0x4e0811a1UL,
        0xf7537e82UL, 0xbd3af235UL, 0x2ad7d2bbUL, 0xeb86d391UL
    };
    static const int S[64] = {
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    };
    unsigned long x[16];
    unsigned long a = state[0];
    unsigned long b = state[1];
    unsigned long c = state[2];
    unsigned long d = state[3];
    int i;

    for (i = 0; i < 16; i++) {
        x[i] = (unsigned long)block[i * 4]
             | ((unsigned long)block[i * 4 + 1] << 8)
             | ((unsigned long)block[i * 4 + 2] << 16)
             | ((unsigned long)block[i * 4 + 3] << 24);
    }
    for (i = 0; i < 64; i++) {
        unsigned long f;
        unsigned long g;
        unsigned long tmp;

        if (i < 16) {
            f = MD5_F(b, c, d);
            g = (unsigned long)i;
        } else if (i < 32) {
            f = MD5_G(b, c, d);
            g = (unsigned long)((5 * i + 1) % 16);
        } else if (i < 48) {
            f = MD5_H(b, c, d);
            g = (unsigned long)((3 * i + 5) % 16);
        } else {
            f = MD5_I(b, c, d);
            g = (unsigned long)((7 * i) % 16);
        }
        tmp = d;
        d = c;
        c = b;
        b = b + MD5_ROTL(a + f + K[i] + x[g], S[i]);
        a = tmp;
    }
    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
}

static void md5_init(MD5_CTX *c)
{
    c->count[0] = 0;
    c->count[1] = 0;
    c->state[0] = 0x67452301UL;
    c->state[1] = 0xefcdab89UL;
    c->state[2] = 0x98badcfeUL;
    c->state[3] = 0x10325476UL;
}

static void md5_update(MD5_CTX *c, const unsigned char *in, unsigned int len)
{
    unsigned int i;
    unsigned int idx;
    unsigned int part;

    idx = (unsigned int)((c->count[0] >> 3) & 0x3F);
    c->count[0] += ((unsigned long)len << 3);
    if (c->count[0] < ((unsigned long)len << 3)) {
        c->count[1]++;
    }
    c->count[1] += ((unsigned long)len >> 29);

    part = 64 - idx;
    if (len >= part) {
        memcpy(&c->buf[idx], in, part);
        md5_transform(c->state, c->buf);
        for (i = part; i + 63 < len; i += 64) {
            md5_transform(c->state, &in[i]);
        }
        idx = 0;
    } else {
        i = 0;
    }
    memcpy(&c->buf[idx], &in[i], len - i);
}

static void md5_final(unsigned char digest[16], MD5_CTX *c)
{
    static const unsigned char pad[64] = { 0x80 };
    unsigned char bits[8];
    unsigned int idx;
    unsigned int padlen;
    int i;

    for (i = 0; i < 8; i++) {
        bits[i] = (unsigned char)((c->count[i >> 2] >> ((i & 3) * 8)) & 0xFF);
    }
    idx = (unsigned int)((c->count[0] >> 3) & 0x3F);
    padlen = (idx < 56) ? (56 - idx) : (120 - idx);
    md5_update(c, pad, padlen);
    md5_update(c, bits, 8);

    for (i = 0; i < 4; i++) {
        digest[i * 4] = (unsigned char)(c->state[i] & 0xFF);
        digest[i * 4 + 1] = (unsigned char)((c->state[i] >> 8) & 0xFF);
        digest[i * 4 + 2] = (unsigned char)((c->state[i] >> 16) & 0xFF);
        digest[i * 4 + 3] = (unsigned char)((c->state[i] >> 24) & 0xFF);
    }
}

void md5_hex(const char *data, int len, char *out)
{
    MD5_CTX c;
    unsigned char digest[16];
    int i;

    md5_init(&c);
    if (data != NULL && len > 0) {
        md5_update(&c, (const unsigned char *)data, (unsigned int)len);
    }
    md5_final(digest, &c);
    for (i = 0; i < 16; i++) {
        sprintf(out + i * 2, "%02x", (unsigned int)digest[i]);
    }
    out[32] = '\0';
}
