/* =====================================================================
 * qrcode.c - 极简 QR 编码器（移植 WP 版 QrEncoder.cs，固定 v7-L）
 * ===================================================================== */
#include "qrcode.h"
#include <string.h>

#define S              QR_SIZE
#define EC_PER_BLOCK   20
#define BLOCK_COUNT    2
#define DATA_PER_BLOCK 78
#define TOTAL_CW       196
#define ALIGN_N        3

static unsigned char g_m[S * S];    /* 模块：1=黑 */
static unsigned char g_res[S * S];  /* 功能图案保留位 */

static unsigned char g_exp[512];
static unsigned char g_log[256];

static const int g_align[ALIGN_N] = { 6, 22, 38 };

static void gf_init(void)
{
    int i;
    int x = 1;

    for (i = 0; i < 255; i++) {
        g_exp[i] = (unsigned char)x;
        g_log[x] = (unsigned char)i;
        x <<= 1;
        if ((x & 0x100) != 0) {
            x ^= 0x11D;
        }
    }
    for (i = 255; i < 512; i++) {
        g_exp[i] = g_exp[i - 255];
    }
}

static unsigned char gf_mul(unsigned char a, unsigned char b)
{
    if (a == 0 || b == 0) {
        return 0;
    }
    return g_exp[g_log[a] + g_log[b]];
}

/* ---- 位缓冲 ---- */
static unsigned char g_bits[(DATA_PER_BLOCK * BLOCK_COUNT) * 8 + 32];
static int g_bitn;

static void bit_put(int value, int length)
{
    int i;
    for (i = length - 1; i >= 0; i--) {
        g_bits[g_bitn++] = (unsigned char)((value >> i) & 1);
    }
}

static unsigned char g_cw[TOTAL_CW];

static void build_generator(int ec, unsigned char *gen, int *genlen)
{
    int len = 1;
    int i;
    int j;

    gen[0] = 1;
    *genlen = 1;
    for (i = 0; i < ec; i++) {
        unsigned char next[64];

        memset(next, 0, sizeof(next));
        for (j = 0; j < len; j++) {
            next[j] ^= gen[j];
            next[j + 1] ^= gf_mul(gen[j], g_exp[i]);
        }
        len++;
        memcpy(gen, next, (size_t)len);
    }
    *genlen = len;
}

static void rs_encode(const unsigned char *data, int dlen,
                      int ec, unsigned char *out)
{
    unsigned char gen[64];
    int genlen;
    unsigned char rem[64];
    int i;
    int j;

    build_generator(ec, gen, &genlen);
    memset(rem, 0, sizeof(rem));
    for (i = 0; i < dlen; i++) {
        int factor = (data[i] ^ rem[0]) & 0xFF;

        for (j = 0; j < ec - 1; j++) {
            rem[j] = rem[j + 1];
        }
        rem[ec - 1] = 0;
        for (j = 0; j < ec; j++) {
            rem[j] ^= gf_mul(gen[j + 1], (unsigned char)factor);
        }
    }
    memcpy(out, rem, (size_t)ec);
}

static int build_codewords(const unsigned char *data, int len)
{
    int totalDataBits = DATA_PER_BLOCK * BLOCK_COUNT * 8;
    unsigned char dcw[DATA_PER_BLOCK * BLOCK_COUNT];
    int n = 0;
    int i;
    unsigned char db[BLOCK_COUNT][DATA_PER_BLOCK];
    unsigned char eb[BLOCK_COUNT][EC_PER_BLOCK];
    unsigned char pad[2] = { 0xEC, 0x11 };
    int padIndex = 0;
    int idx = 0;
    int b;

    g_bitn = 0;
    bit_put(0x4, 4);
    bit_put(len, 8);
    for (i = 0; i < len; i++) {
        bit_put(data[i], 8);
    }
    {
        int padbits = 4;
        if (totalDataBits - g_bitn < padbits) {
            padbits = totalDataBits - g_bitn;
        }
        bit_put(0, padbits);
    }
    while ((g_bitn % 8) != 0) {
        g_bits[g_bitn++] = 0;
    }
    for (i = 0; i + 8 <= g_bitn; i += 8) {
        int v = 0;
        int j;
        for (j = 0; j < 8; j++) {
            v = (v << 1) | g_bits[i + j];
        }
        dcw[n++] = (unsigned char)v;
    }
    while (n < DATA_PER_BLOCK * BLOCK_COUNT) {
        dcw[n++] = pad[padIndex];
        padIndex ^= 1;
    }

    for (b = 0; b < BLOCK_COUNT; b++) {
        memcpy(db[b], dcw + b * DATA_PER_BLOCK, DATA_PER_BLOCK);
        rs_encode(db[b], DATA_PER_BLOCK, EC_PER_BLOCK, eb[b]);
    }
    for (i = 0; i < DATA_PER_BLOCK; i++) {
        for (b = 0; b < BLOCK_COUNT; b++) {
            g_cw[idx++] = db[b][i];
        }
    }
    for (i = 0; i < EC_PER_BLOCK; i++) {
        for (b = 0; b < BLOCK_COUNT; b++) {
            g_cw[idx++] = eb[b][i];
        }
    }
    return 1;
}

/* ---- 图案 ---- */

static void set_func(int x, int y, int dark)
{
    if (x < 0 || y < 0 || x >= S || y >= S) {
        return;
    }
    g_m[y * S + x] = (unsigned char)(dark ? 1 : 0);
    g_res[y * S + x] = 1;
}

static void reserve(int x, int y)
{
    if (x >= 0 && y >= 0 && x < S && y < S) {
        g_res[y * S + x] = 1;
    }
}

static void draw_finder(int left, int top)
{
    int dy;
    int dx;

    for (dy = -1; dy <= 7; dy++) {
        for (dx = -1; dx <= 7; dx++) {
            int dark = ((dx >= 0 && dx <= 6 && (dy == 0 || dy == 6)) ||
                        (dy >= 0 && dy <= 6 && (dx == 0 || dx == 6)) ||
                        (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
            set_func(left + dx, top + dy, dark);
        }
    }
}

static void draw_alignment(int cx, int cy)
{
    int dy;
    int dx;

    for (dy = -2; dy <= 2; dy++) {
        for (dx = -2; dx <= 2; dx++) {
            int ax = dx < 0 ? -dx : dx;
            int ay = dy < 0 ? -dy : dy;
            int dark = ((ax > ay ? ax : ay) != 1);
            set_func(cx + dx, cy + dy, dark);
        }
    }
}

static void draw_function_patterns(void)
{
    int i;
    int j;

    memset(g_m, 0, sizeof(g_m));
    memset(g_res, 0, sizeof(g_res));

    draw_finder(0, 0);
    draw_finder(S - 7, 0);
    draw_finder(0, S - 7);

    for (i = 8; i < S - 8; i++) {
        set_func(i, 6, (i % 2) == 0);
        set_func(6, i, (i % 2) == 0);
    }
    for (i = 0; i < ALIGN_N; i++) {
        for (j = 0; j < ALIGN_N; j++) {
            int cx = g_align[i];
            int cy = g_align[j];
            int overlaps = (cx == 6 && cy == 6) ||
                           (cx == S - 7 && cy == 6) ||
                           (cx == 6 && cy == S - 7);
            if (overlaps) {
                continue;
            }
            draw_alignment(cx, cy);
        }
    }
    set_func(8, S - 8, 1);
    for (i = 0; i <= 8; i++) {
        reserve(8, i);
        reserve(i, 8);
    }
    for (i = 0; i < 8; i++) {
        reserve(S - 1 - i, 8);
        reserve(8, S - 1 - i);
    }
    for (i = 0; i < 18; i++) {
        int a = S - 11 + i % 3;
        int b = i / 3;
        reserve(a, b);
        reserve(b, a);
    }
}

static void place_data(void)
{
    int totalBits = TOTAL_CW * 8;
    int bitIndex = 0;
    int right;
    int vert;
    int j;

    for (right = S - 1; right >= 1; right -= 2) {
        if (right == 6) {
            right = 5;
        }
        for (vert = 0; vert < S; vert++) {
            for (j = 0; j < 2; j++) {
                int x = right - j;
                int upward = ((right + 1) & 2) == 0;
                int y = upward ? S - 1 - vert : vert;
                unsigned char bit = 0;

                if (g_res[y * S + x]) {
                    continue;
                }
                if (bitIndex < totalBits) {
                    bit = (unsigned char)((g_cw[bitIndex >> 3] >>
                                           (7 - (bitIndex & 7))) & 1);
                }
                bitIndex++;
                if (((x + y) & 1) == 0) {
                    bit ^= 1;
                }
                g_m[y * S + x] = bit;
            }
        }
    }
}

static int get_bit(int value, int index)
{
    return (value >> index) & 1;
}

static int format_bits(int ecLevelBits, int mask)
{
    int data = (ecLevelBits << 3) | mask;
    int value = data << 10;
    int i;

    for (i = 14; i >= 10; i--) {
        if (((value >> i) & 1) != 0) {
            value ^= 0x537 << (i - 10);
        }
    }
    return value ^ 0x5412;
}

static int version_bits(int version)
{
    int value = version << 12;
    int i;

    for (i = 17; i >= 12; i--) {
        if (((value >> i) & 1) != 0) {
            value ^= 0x1F25 << (i - 12);
        }
    }
    return value;
}

static void draw_format_info(void)
{
    int bits = format_bits(1, 0);
    int i;

    for (i = 0; i <= 5; i++) {
        g_m[i * S + 8] = (unsigned char)get_bit(bits, i);
    }
    g_m[7 * S + 8] = (unsigned char)get_bit(bits, 6);
    g_m[8 * S + 8] = (unsigned char)get_bit(bits, 7);
    g_m[8 * S + 7] = (unsigned char)get_bit(bits, 8);
    for (i = 9; i < 15; i++) {
        g_m[8 * S + (14 - i)] = (unsigned char)get_bit(bits, i);
    }
    for (i = 0; i < 8; i++) {
        g_m[8 * S + (S - 1 - i)] = (unsigned char)get_bit(bits, i);
    }
    for (i = 8; i < 15; i++) {
        g_m[(S - 15 + i) * S + 8] = (unsigned char)get_bit(bits, i);
    }
    g_m[(S - 8) * S + 8] = 1;
}

static void draw_version_info(int version)
{
    int bits = version_bits(version);
    int i;

    for (i = 0; i < 18; i++) {
        int bit = get_bit(bits, i);
        int a = S - 11 + i % 3;
        int b = i / 3;
        g_m[b * S + a] = (unsigned char)bit;
        g_m[a * S + b] = (unsigned char)bit;
    }
}

int qr_encode(const char *content, unsigned char *out)
{
    int len = (int)strlen(content);
    int i;

    if (len > QR_CAP) {
        return 0;
    }
    gf_init();
    build_codewords((const unsigned char *)content, len);
    draw_function_patterns();
    place_data();
    draw_format_info();
    draw_version_info(7);
    for (i = 0; i < S * S; i++) {
        out[i] = g_m[i];
    }
    return 1;
}
