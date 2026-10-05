/* =====================================================================
 * BiliClassic - bc.h （魅族 M8 / Windows CE 6.0 版）
 * ---------------------------------------------------------------------
 * 设计约束：
 *   - 只依赖 coredll / ws2(Winsock 2) / Mzfc(仅 UI 层)
 *   - 不使用 C99 特性（声明在块首、/* *\/ 注释、无变长数组）
 *   - M8 coredll 不导出 ANSI(A) 版 API：文件/窗口一律 W 版（UTF-16），
 *     GBK <-> UTF-16 走 CP_ACP（M8 中文系统 ACP=936）转换（ut_a2w/ut_w2a/gbk2w）
 *   - UTF-8 显示文本先自行解码为 UTF-16，再 WideCharToMultiByte(CP_ACP)
 * ===================================================================== */
#ifndef BC_H
#define BC_H

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define BC_APP_NAME     "哔哩经典"
#define BC_APP_TITLE    "哔哩经典 for Mymobile"
/* 界面上显示的短版本（三段）。exe 的 VERSIONINFO 资源用四段 0.2.0.0 */
#define BC_APP_VER      "0.2.0"
#define BC_APP_VER_FULL 0,2,0,0
/* 更新接口 version.json 里的 version_code（0.2.0.0 -> 200） */
#define BC_APP_VER_CODE 200
#define BC_MAX_ITEMS    300
#define BC_TITLE_LEN    220
#define BC_AUTHOR_LEN   80
#define BC_BVID_LEN     16
#define BC_PIC_LEN      512
#define BC_URL_LEN      2048
#define BC_PATH_LEN     512
#define BC_LOG_LEN      1024

/* 转码服务器（腾讯云 SCF）：只用于 /health /register /transcode，
 * 不再做 API 代理（API 全走 wolfSSL 直连）。仍保留是因为转码在这里。 */
#define BC_RELAY_DEFAULT_HOST "1303002254-dja6s2xtn7.ap-hongkong.tencentscf.com"
#define BC_RELAY_DEFAULT_PORT 80
#define BC_REG_BUCKET   "video-storage-1303002254"
#define BC_REG_REGION   "ap-hongkong"
#define BC_AUTH_SECRET  "da2e0efd51c3d601f629e9e878aa5c3218419d5aeb459c6355c4f1674b3bb2de"

/* 转码格式：wmv / h264 */
#define BC_TRANSCODE_FMT_LEN 16
#define BC_DEFAULT_TRANSCODE_FMT "h264"

/* 一条视频结果 */
typedef struct {
    char bvid[BC_BVID_LEN];
    char title[BC_TITLE_LEN];
    char author[BC_AUTHOR_LEN];
    char pic[BC_PIC_LEN];      /* 封面 URL（//i0.hdslb.com/... 或 http(s)://） */
} BcItem;

/* 运行配置/会话状态 */
typedef struct {
    char exe_dir[260];              /* exe 所在目录（\ 结尾） */
    char data_dir[260];             /* 可写数据目录（日志/下载，\ 结尾） */
    char relay_host[128];           /* 转码服务器地址 */
    int  relay_port;
    char install_id[40];
    char token[80];                 /* /transcode 用的每设备 token */
    int  token_ok;
    int  transcode;                 /* 1 = 先把视频交给服务器转成老格式再下载 */
    char transcode_fmt[BC_TRANSCODE_FMT_LEN];   /* "wmv" / "h264" */
    int  conns;                     /* 下载并行连接数 1..8（1=单连接） */
    int  offline;                   /* 1 = 下载到本地离线播放；0 = 只在线流播不落盘 */
    int  danmaku;                   /* 1 = 显示弹幕；0 = 关闭弹幕 */
    int  report_history;            /* 1 = 播放时上报 B 站观看历史；0 = 不上报 */
    char wbi_mixin[40];             /* WBI 签名用的 mixin key（32 hex） */
    int  wbi_ok;                    /* mixin key 是否已取到 */
    long time_offset;               /* 服务器时间 - 本机时间（秒） */
    char cookie[1200];              /* 登录 Cookie（SESSDATA 等），登录后持久化 */
} BcConfig;

#endif /* BC_H */
