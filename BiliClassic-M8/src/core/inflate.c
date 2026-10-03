/* =====================================================================
 * inflate.c - 纯 C DEFLATE 解压（移植自 WP 版 Danmaku/Inflate.cs）
 * ===================================================================== */
#include <stdlib.h>
#include <string.h>

#include "inflate.h"

#define IN_MAX_BITS 15
#define IN_MAX_OUT  (8 * 1024 * 1024)

static const short kLenBase[29] = {
    3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
    35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
};
static const short kLenExtra[29] = {
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
    3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
};
static const short kDistBase[30] = {
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
    257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
    8193, 12289, 16385, 24577
};
static const short kDistExtra[30] = {
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
    7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
};
static const short kLenOrder[19] = {
    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
};

typedef struct {
    const unsigned char *in;
    int inEnd;
    int inPos;
    int bitBuf;
    int bitCnt;
    unsigned char *out;
    int outPos;
    int outCap;
    int error;
} Inflate;

typedef struct {
    short count[IN_MAX_BITS + 1];
    short symbol[320];
} InHuff;

static int in_read_bits(Inflate *s, int need)
{
    int value = s->bitBuf;

    while (s->bitCnt < need) {
        if (s->inPos >= s->inEnd) {
            s->error = 1;
            return 0;
        }
        value |= s->in[s->inPos++] << s->bitCnt;
        s->bitCnt += 8;
    }
    s->bitBuf = value >> need;
    s->bitCnt -= need;
    return value & ((1 << need) - 1);
}

static void in_align(Inflate *s)
{
    s->bitBuf = 0;
    s->bitCnt = 0;
}

static int in_put(Inflate *s, unsigned char v)
{
    if (s->outPos >= s->outCap) {
        int size;
        unsigned char *bigger;

        if (s->outCap >= IN_MAX_OUT) {
            s->error = 1;
            return -1;
        }
        size = s->outCap * 2;
        if (size > IN_MAX_OUT) {
            size = IN_MAX_OUT;
        }
        bigger = (unsigned char *)realloc(s->out, (size_t)size);
        if (bigger == NULL) {
            s->error = 1;
            return -1;
        }
        s->out = bigger;
        s->outCap = size;
    }
    s->out[s->outPos++] = v;
    return 0;
}

static void in_construct(Inflate *s, InHuff *h, const unsigned char *lengths,
                         int offset, int n)
{
    short offs[IN_MAX_BITS + 2];
    int left = 1;
    int len;
    int kk;

    for (len = 0; len <= IN_MAX_BITS; len++) {
        h->count[len] = 0;
    }
    for (kk = 0; kk < n; kk++) {
        int L = lengths[offset + kk];
        if (L > IN_MAX_BITS) {
            s->error = 1;
            return;
        }
        h->count[L]++;
    }
    if (h->count[0] == n) {
        return;
    }
    for (len = 1; len <= IN_MAX_BITS; len++) {
        left <<= 1;
        left -= h->count[len];
        if (left < 0) {
            s->error = 1;
            return;
        }
    }
    offs[1] = 0;
    for (len = 1; len < IN_MAX_BITS; len++) {
        offs[len + 1] = (short)(offs[len] + h->count[len]);
    }
    for (kk = 0; kk < n; kk++) {
        int L = lengths[offset + kk];
        if (L != 0) {
            h->symbol[offs[L]++] = (short)kk;
        }
    }
}

static int in_decode(Inflate *s, InHuff *h)
{
    int code = 0;
    int first = 0;
    int index = 0;
    int len;

    for (len = 1; len <= IN_MAX_BITS; len++) {
        int count;
        code |= in_read_bits(s, 1);
        if (s->error) {
            return -1;
        }
        count = h->count[len];
        if (code - first < count) {
            return h->symbol[index + (code - first)];
        }
        index += count;
        first += count;
        first <<= 1;
        code <<= 1;
    }
    return -10;
}

static void in_stored(Inflate *s)
{
    int len;
    int nlen;
    int i;

    in_align(s);
    if (s->inPos + 4 > s->inEnd) {
        s->error = 1;
        return;
    }
    len = s->in[s->inPos] | (s->in[s->inPos + 1] << 8);
    nlen = s->in[s->inPos + 2] | (s->in[s->inPos + 3] << 8);
    s->inPos += 4;
    if ((len ^ 0xFFFF) != nlen) {
        s->error = 1;
        return;
    }
    if (s->inPos + len > s->inEnd) {
        s->error = 1;
        return;
    }
    for (i = 0; i < len; i++) {
        if (in_put(s, s->in[s->inPos++]) != 0) {
            return;
        }
    }
}

static void in_fixed(Inflate *s, InHuff *lenCode, InHuff *distCode)
{
    unsigned char lengths[288];
    unsigned char distLengths[30];
    int i;

    for (i = 0; i < 144; i++) lengths[i] = 8;
    for (i = 144; i < 256; i++) lengths[i] = 9;
    for (i = 256; i < 280; i++) lengths[i] = 7;
    for (i = 280; i < 288; i++) lengths[i] = 8;
    in_construct(s, lenCode, lengths, 0, 288);

    for (i = 0; i < 30; i++) distLengths[i] = 5;
    in_construct(s, distCode, distLengths, 0, 30);
}

static void in_dynamic(Inflate *s, InHuff *lenCode, InHuff *distCode)
{
    unsigned char lengths[320];
    InHuff lenLenCode;
    int hlit;
    int hdist;
    int hclen;
    int i;
    int index;
    int total;

    memset(lengths, 0, sizeof(lengths));
    hlit = in_read_bits(s, 5) + 257;
    hdist = in_read_bits(s, 5) + 1;
    hclen = in_read_bits(s, 4) + 4;
    if (s->error || hlit > 286 || hdist > 30) {
        s->error = 1;
        return;
    }
    for (i = 0; i < hclen; i++) {
        lengths[kLenOrder[i]] = (unsigned char)in_read_bits(s, 3);
    }
    for (i = hclen; i < 19; i++) {
        lengths[kLenOrder[i]] = 0;
    }
    in_construct(s, &lenLenCode, lengths, 0, 19);

    index = 0;
    total = hlit + hdist;
    while (index < total) {
        int symbol = in_decode(s, &lenLenCode);
        int repeat;
        int value = 0;

        if (s->error) {
            return;
        }
        if (symbol < 0) {
            s->error = 1;
            return;
        }
        if (symbol < 16) {
            lengths[index++] = (unsigned char)symbol;
            continue;
        }
        if (symbol == 16) {
            if (index == 0) {
                s->error = 1;
                return;
            }
            value = lengths[index - 1];
            repeat = 3 + in_read_bits(s, 2);
        } else if (symbol == 17) {
            repeat = 3 + in_read_bits(s, 3);
        } else {
            repeat = 11 + in_read_bits(s, 7);
        }
        if (s->error || index + repeat > total) {
            s->error = 1;
            return;
        }
        while (repeat-- > 0) {
            lengths[index++] = (unsigned char)value;
        }
    }
    in_construct(s, lenCode, lengths, 0, hlit);
    in_construct(s, distCode, lengths, hlit, hdist);
}

static void in_compressed(Inflate *s, InHuff *lenCode, InHuff *distCode)
{
    for (;;) {
        int symbol = in_decode(s, lenCode);
        int length;
        int distSymbol;
        int distance;
        int i;

        if (s->error) {
            return;
        }
        if (symbol < 0) {
            s->error = 1;
            return;
        }
        if (symbol < 256) {
            if (in_put(s, (unsigned char)symbol) != 0) {
                return;
            }
            continue;
        }
        if (symbol == 256) {
            return;
        }
        symbol -= 257;
        if (symbol >= 29) {
            s->error = 1;
            return;
        }
        length = kLenBase[symbol] + in_read_bits(s, kLenExtra[symbol]);
        distSymbol = in_decode(s, distCode);
        if (s->error || distSymbol < 0 || distSymbol >= 30) {
            s->error = 1;
            return;
        }
        distance = kDistBase[distSymbol] + in_read_bits(s, kDistExtra[distSymbol]);
        if (s->error || distance > s->outPos) {
            s->error = 1;
            return;
        }
        for (i = 0; i < length; i++) {
            if (in_put(s, s->out[s->outPos - distance]) != 0) {
                return;
            }
        }
    }
}

unsigned char *bc_inflate(const unsigned char *in, int inlen, int *outlen)
{
    Inflate s;
    int last;

    if (outlen != NULL) {
        *outlen = 0;
    }
    if (in == NULL || inlen <= 0) {
        return NULL;
    }

    memset(&s, 0, sizeof(s));
    s.in = in;
    s.inPos = 0;
    s.inEnd = inlen;
    s.outCap = 8192;
    s.out = (unsigned char *)malloc((size_t)s.outCap);
    if (s.out == NULL) {
        return NULL;
    }

    /* 跳过 gzip / zlib 头 */
    if (inlen > 2 && in[0] == 0x1F && in[1] == 0x8B) {
        s.inPos = 10;
    } else if (inlen > 2 && in[0] == 0x78 &&
               (((in[0] << 8) | in[1]) % 31) == 0) {
        s.inPos = 2;
    }

    do {
        int type;

        last = in_read_bits(&s, 1);
        type = in_read_bits(&s, 2);
        if (s.error) {
            break;
        }
        if (type == 0) {
            in_stored(&s);
        } else if (type == 1 || type == 2) {
            InHuff lenCode;
            InHuff distCode;

            memset(&lenCode, 0, sizeof(lenCode));
            memset(&distCode, 0, sizeof(distCode));
            if (type == 1) {
                in_fixed(&s, &lenCode, &distCode);
            } else {
                in_dynamic(&s, &lenCode, &distCode);
            }
            if (!s.error) {
                in_compressed(&s, &lenCode, &distCode);
            }
        } else {
            s.error = 1;
        }
    } while (!last && !s.error);

    if (s.error) {
        free(s.out);
        return NULL;
    }
    if (outlen != NULL) {
        *outlen = s.outPos;
    }
    return s.out;
}
