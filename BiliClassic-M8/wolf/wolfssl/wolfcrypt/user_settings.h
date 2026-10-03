/* =====================================================================
 * user_settings.h - wolfSSL 精简配置（魅族 M8 / Windows CE 6.0 / ARMv4I）
 * ---------------------------------------------------------------------
 * 目标：只做 TLS 客户端，支持 TLS1.2/1.3、RSA/ECDHE、AES-GCM/CBC、
 *       ChaCha20-Poly1305、SHA-256/384。单线程；用自定义 IO 绑定我们的 socket。
 * ===================================================================== */
#ifndef WOLFSSL_USER_SETTINGS_H
#define WOLFSSL_USER_SETTINGS_H

/* ---- 平台 ---- */
#define SINGLE_THREADED
#define WOLFSSL_SMALL_STACK
#define USE_WINDOWS_API
#define WOLFSSL_NO_ATOMICS
#define WC_NO_ASYNC_THREADING
#define NO_WOLFSSL_DIR
#define WOLFSSL_GENERAL_ALIGNMENT 4
#define NOMINMAX                /* 别让 windows.h 把 min/max 变宏，和 wolfSSL 冲突 */
#define WOLFSSL_IGNORE_FILE_WARN /* MSVC 不认 #warning：跳过那些提示 */
#define WOLFSSL_USER_IO         /* 用自己的 socket IO（避免 wolfio 的 FormatMessageA） */

/* ---- 去掉文件系统/杂项（我们用自定义 IO 和内存中的证书） ---- */
#define NO_FILESYSTEM
#define NO_WRITEV
#define NO_DEV_RANDOM
#define NO_MAIN_DRIVER

/* ---- 数学（fastmath，无汇编） ---- */
#define USE_FAST_MATH
#define TFM_TIMING_RESISTANT
#define TFM_NO_ASM
#define ALT_ECC_SIZE
#define FP_MAX_BITS 8192
#define ECC_SHAMIR
#define ECC_TIMING_RESISTANT
#define TFM_ECC256

/* ---- 证书/ASN ---- */
#define WOLFSSL_ASN_TEMPLATE
#define NO_ASN_TIME          /* 不校验证书有效期：CE 无可靠时间；连接层不依赖它 */

/* ---- TLS 特性 ---- */
#define HAVE_TLS_EXTENSIONS
#define HAVE_SUPPORTED_CURVES
#define HAVE_EXTENDED_MASTER
#define HAVE_SNI
#define HAVE_ENCRYPT_THEN_MAC
/* 先只用 TLS1.2（TLS1.3 需要 FFDHE 表，体积大；B 站支持 1.2） */
#define HAVE_HKDF
#define WC_RSA_PSS
#define WOLFSSL_KEY_GEN
#define NO_OLD_TLS           /* 只要 TLS1.2/1.3 */

/* ---- 加密算法 ---- */
#define HAVE_ECC
#define HAVE_AESGCM
#define HAVE_AES_CBC
#define HAVE_AES_ECB
#define WOLFSSL_AES_DIRECT
#define WOLFSSL_AES_COUNTER
#define GCM_SMALL            /* ARM11 上体积优先；需要更快可换 GCM_TABLE_4BIT */
#define HAVE_CHACHA
#define HAVE_POLY1305
#define HAVE_ONE_TIME_AUTH

/* ---- 熵源：用 crypt32 的 CryptGenRandom（CE 有） ---- */
extern int bc_rng_seed(unsigned char *output, unsigned int sz);
#define CUSTOM_RAND_GENERATE_SEED bc_rng_seed

/* ---- 关闭不需要的 ---- */
#define NO_WOLFSSL_SERVER
#define NO_DES3
#define NO_RC4
#define NO_DSA
#define NO_PSK
#define NO_MD4
#define NO_MD5
#define NO_PWDBASED
#define NO_SESSION_CACHE
#define NO_OLD_TLS

#endif /* WOLFSSL_USER_SETTINGS_H */
