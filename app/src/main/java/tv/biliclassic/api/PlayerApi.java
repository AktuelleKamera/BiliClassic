package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import tv.biliclassic.model.PlayerData;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class PlayerApi {

    // 播放地址缓存：同一 aid+cid+qn 10 分钟内复用，避免每次点播放都重新请求
    private static final long URL_CACHE_TTL = 600000; // 10 分钟
    private static final Map<String, CachedUrl> sUrlCache = new HashMap<String, CachedUrl>();

    private static class CachedUrl {
        final String videoUrl;
        final String audioUrl;
        final int actualQn;
        final long timestamp;
        final String[] qnStrList;
        final int[] qnValueList;
        CachedUrl(String videoUrl, String audioUrl, int actualQn, long timestamp, String[] qnStrList, int[] qnValueList) {
            this.videoUrl = videoUrl;
            this.audioUrl = audioUrl;
            this.actualQn = actualQn;
            this.timestamp = timestamp;
            this.qnStrList = qnStrList;
            this.qnValueList = qnValueList;
        }
    }

    private static String buildCacheKey(PlayerData playerData) {
        int streamFormat = playerData.type == PlayerData.TYPE_VIDEO
                ? tv.biliclassic.SettingsActivity.getPlayStreamFormat() : 1;
        return playerData.aid + "_" + playerData.cid + "_" + playerData.qn + "_" + playerData.type + "_" + streamFormat;
    }

    /**
     * 从 dash.video[] 中选出目标画质的码流。
     * DASH 响应会一次性返回所有可用画质（qn 参数对 DASH 无效），必须客户端按 id 选择：
     *  1. 目标画质存在 → 选它；不存在 → 降级到最接近且低于目标的画质；再没有 → 最低画质
     *  2. 同画质组内编码优先级 AVC > HEVC > 其他（本 app 目标设备靠软解，
     *     AVC 性能最好；AV1 老设备只能赤石英雄了）
     */
    private static JSONObject selectDashVideoEntry(JSONArray video, int targetQn) throws JSONException {
        int n = video.length();
        int bestId = -1;
        for (int i = 0; i < n; i++) {
            int id = video.getJSONObject(i).optInt("id", -1);
            if (id == targetQn) { bestId = targetQn; break; }
            if (id < targetQn && id > bestId) bestId = id;
        }
        if (bestId < 0) {
            // 所有可用画质都高于目标（目标过低），取最低档
            for (int i = 0; i < n; i++) {
                int id = video.getJSONObject(i).optInt("id", -1);
                if (bestId < 0 || id < bestId) bestId = id;
            }
        }
        JSONObject fallback = null;
        JSONObject hevc = null;
        for (int i = 0; i < n; i++) {
            JSONObject v = video.getJSONObject(i);
            if (v.optInt("id", -1) != bestId) continue;
            if (fallback == null) fallback = v;
            String codecs = v.optString("codecs", "");
            if (codecs.startsWith("avc1") || codecs.startsWith("avc3")) return v;
            if ((codecs.startsWith("hev1") || codecs.startsWith("hvc1")) && hevc == null) hevc = v;
        }
        if (hevc != null) return hevc;
        return fallback;
    }

/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年6月19日
 *
 * 安卓2也要看B站！
 */

    /**
     * 获取视频播放地址
     * @param playerData 传入 aid、cid、qn 等必要数据
     * @param download 是否下载模式（影响画质参数）
     */
    public static void getVideo(PlayerData playerData, boolean download) throws JSONException, IOException {
        android.util.Log.e("PlayerApi", "========== getVideo 开始 ==========");
        android.util.Log.e("PlayerApi", "aid=" + playerData.aid + ", cid=" + playerData.cid + ", qn=" + playerData.qn);
        android.util.Log.e("PlayerApi", "timeStamp=" + playerData.timeStamp + ", currentTime=" + System.currentTimeMillis());

        // if上一次获取在十分钟内就无需再次获取
        if (System.currentTimeMillis() - playerData.timeStamp < 600000) {
            android.util.Log.e("PlayerApi", "使用缓存的播放地址，跳过获取");
            return;
        }

        // 静态缓存：同一 aid+cid+qn 十分钟内复用，避免每次点播放都重新请求
        String cacheKey = buildCacheKey(playerData);
        if (!download) {
            CachedUrl cached;
            synchronized (sUrlCache) {
                cached = sUrlCache.get(cacheKey);
            }
            if (cached != null && cached.videoUrl != null && cached.videoUrl.length() > 0
                    && System.currentTimeMillis() - cached.timestamp < URL_CACHE_TTL) {
                android.util.Log.e("PlayerApi", "命中静态缓存: " + cached.videoUrl);
                playerData.videoUrl = cached.videoUrl;
                playerData.audioUrl = cached.audioUrl;
                // DASH 下实际画质可能与请求不同（被降级），恢复真实值
                if (cached.actualQn > 0) {
                    playerData.qn = cached.actualQn;
                }
                playerData.qnStrList = cached.qnStrList;
                playerData.qnValueList = cached.qnValueList;
                playerData.timeStamp = System.currentTimeMillis();
                return;
            }
        }

        playerData.timeStamp = System.currentTimeMillis();
        android.util.Log.e("PlayerApi", "开始获取新地址，timeStamp 已更新");

        android.util.Log.e("PlayerApi", "getPlayStreamFormat=" + tv.biliclassic.SettingsActivity.getPlayStreamFormat()
                + " (1=MP4 durl, 16=DASH)");

        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";
        android.util.Log.e("PlayerApi", "danmakuUrl=" + playerData.danmakuUrl);

        boolean html5 = !download && "mtvPlayer".equals(SharedPreferencesUtil.getString("player", ""));
        android.util.Log.e("PlayerApi", "html5模式=" + html5);

        // bvid 优先（推荐流卡片没有 aid，avid=0 会被 API 拒绝 -400）
        String idParam = (playerData.bvid != null && playerData.bvid.length() > 0)
                ? "&bvid=" + playerData.bvid
                : "&avid=" + playerData.aid;
        String url = "https://api.bilibili.com/x/player/wbi/playurl?"
                + idParam
                + "&cid=" + playerData.cid
                + (html5 ? "&high_quality=1" : "")
                + "&qn=" + playerData.qn
                + "&fnval=" + (download ? 1 : tv.biliclassic.SettingsActivity.getPlayStreamFormat()) // 1=MP4, 16=DASH
                + "&fnver=0"
                + "&platform=" + (html5 ? "html5" : "pc")
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";

        android.util.Log.e("PlayerApi", "原始URL: " + url);

        url = ConfInfoApi.signWBI(url);
        android.util.Log.e("PlayerApi", "签名后URL: " + url);

        JSONObject body = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        int code = body.optInt("code", -1);
        android.util.Log.e("PlayerApi", "API响应码: " + code);
        android.util.Log.e("PlayerApi", "API响应消息: " + body.optString("message", ""));

        if (code != 0) {
            android.util.Log.e("PlayerApi", "API返回错误，code=" + code);
            throw new JSONException("API错误: " + body.optString("message", "未知错误"));
        }

        JSONObject data = body.getJSONObject("data");
        android.util.Log.e("PlayerApi", "data 对象存在");

        String videoUrl = null;
        String audioUrl = "";
        boolean dashRequested = !download && tv.biliclassic.SettingsActivity.getPlayStreamFormat() == 16;

        // ========== 优先解析 dash（DASH 音视频分离流，仅非下载模式） ==========
        if (dashRequested && data.has("dash")) {
            JSONObject dash = data.getJSONObject("dash");
            android.util.Log.e("PlayerApi", "使用 dash 格式");
            JSONArray video = dash.optJSONArray("video");
            JSONArray audio = dash.optJSONArray("audio");
            JSONObject videoEntry = null;
            if (video != null && video.length() > 0) {
                // DASH 响应会返回所有可用画质（qn 参数对 DASH 无效），
                // 必须客户端按 id 选择目标画质，否则切画质永远无效
                videoEntry = selectDashVideoEntry(video, playerData.qn);
            }
            if (videoEntry != null) {
                android.util.Log.e("PlayerApi", "dash video codecs=" + videoEntry.optString("codecs", "?")
                        + " id=" + videoEntry.optString("id", "?"));
                videoUrl = videoEntry.optString("baseUrl", "");
                JSONArray backupUrl = videoEntry.optJSONArray("backupUrl");
                if ((videoUrl == null || videoUrl.length() == 0) && backupUrl != null && backupUrl.length() > 0) {
                    videoUrl = backupUrl.getString(0);
                }
                // 实际画质以选中流为准（目标画质不可用时会被降级），回写保证 UI/续播一致
                playerData.qn = videoEntry.optInt("id", playerData.qn);
                android.util.Log.e("PlayerApi", "视频地址: " + videoUrl);
            }
            if (audio != null && audio.length() > 0) {
                JSONObject firstAudio = audio.getJSONObject(0);
                android.util.Log.e("PlayerApi", "dash audio codecs=" + firstAudio.optString("codecs", "?"));
                audioUrl = firstAudio.optString("baseUrl", "");
                JSONArray backupUrl = firstAudio.optJSONArray("backupUrl");
                if ((audioUrl == null || audioUrl.length() == 0) && backupUrl != null && backupUrl.length() > 0) {
                    audioUrl = backupUrl.getString(0);
                }
                android.util.Log.e("PlayerApi", "音频地址: " + audioUrl);
            }
        }

        // ========== 尝试解析 durl（MP4 格式，作为回退） ==========
        if ((videoUrl == null || videoUrl.length() == 0) && data.has("durl")) {
            JSONArray durl = data.getJSONArray("durl");
            android.util.Log.e("PlayerApi", "durl 数组长度: " + durl.length());
            if (durl.length() > 0) {
                JSONObject videoUrlObj = durl.getJSONObject(0);
                videoUrl = videoUrlObj.getString("url");
                android.util.Log.e("PlayerApi", "使用 durl 格式, codec=" + videoUrlObj.optString("codecs", "?"));
            }
        }

        if (videoUrl == null || videoUrl.length() == 0) {
            android.util.Log.e("PlayerApi", "无法获取视频地址");
            if (download) {
                throw new JSONException("该视频仅提供 DASH 格式，不支持下载为 MP4");
            }
            throw new JSONException("无法获取视频地址");
        }

        playerData.videoUrl = videoUrl;
        playerData.audioUrl = audioUrl;
        playerData.durationMs = data.optLong("timelength", 0);
        android.util.Log.e("PlayerApi", "videoUrl: " + playerData.videoUrl
                + ", durationMs=" + playerData.durationMs);

        // B 站 playurl 接口返回的 last_play_time 单位是「毫秒」，last_play_cid 是上次播放分P。
        // progress<0 表示已看完（从 0 开始），=0 表示无历史（从 0 开始）。
        playerData.cidHistory = data.optLong("last_play_cid", 0);
        playerData.progress = data.optInt("last_play_time", 0);
        android.util.Log.e("PlayerApi", "cidHistory=" + playerData.cidHistory + ", progress=" + playerData.progress);

        if (playerData.cidHistory == 0) {
            playerData.cidHistory = playerData.cid;
            playerData.progress = 0;
            android.util.Log.e("PlayerApi", "重置 cidHistory 为 " + playerData.cidHistory);
        }

        // 解析可用画质列表
        JSONArray acceptDescription = data.getJSONArray("accept_description");
        JSONArray acceptQuality = data.getJSONArray("accept_quality");
        int len = acceptDescription.length();
        android.util.Log.e("PlayerApi", "可用画质数量: " + len);

        String[] qnStrList = new String[len];
        int[] qnValueList = new int[len];
        for (int i = 0; i < len; i++) {
            qnStrList[i] = acceptDescription.optString(i);
            qnValueList[i] = acceptQuality.optInt(i);
            android.util.Log.e("PlayerApi", "画质[" + i + "]: " + qnStrList[i] + " (" + qnValueList[i] + ")");
        }
        playerData.qnStrList = qnStrList;
        playerData.qnValueList = qnValueList;

        // 写入静态缓存
        if (!download && videoUrl.length() > 0) {
            synchronized (sUrlCache) {
                sUrlCache.put(cacheKey, new CachedUrl(videoUrl, audioUrl, playerData.qn,
                        System.currentTimeMillis(), qnStrList, qnValueList));
            }
        }

        android.util.Log.e("PlayerApi", "========== getVideo 结束，videoUrl=" + playerData.videoUrl + " ==========");
    }

    /**
     * 获取视频播放地址（强制 MP4 / fnval=1，便于内置 MediaPlayer 直接播放）。
     * 解析 durl[0].url 填入 playerData.videoUrl。
     */
    public static void getVideoMp4(PlayerData playerData) throws JSONException, IOException {
        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";
        // bvid 优先（推荐流卡片没有 aid，avid=0 会被 API 拒绝 -400）
        String idParam = (playerData.bvid != null && playerData.bvid.length() > 0)
                ? "&bvid=" + playerData.bvid
                : "&avid=" + playerData.aid;
        String url = "https://api.bilibili.com/x/player/wbi/playurl?"
                + idParam
                + "&cid=" + playerData.cid
                + "&qn=" + playerData.qn
                + "&fnval=1"
                + "&fnver=0"
                + "&platform=pc"
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";
        url = ConfInfoApi.signWBI(url);
        JSONObject body = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        int code = body.optInt("code", -1);
        if (code != 0) {
            throw new JSONException("API错误: " + body.optString("message", "未知错误"));
        }
        JSONObject data = body.getJSONObject("data");
        String videoUrl = null;
        if (data.has("durl")) {
            JSONArray durl = data.getJSONArray("durl");
            if (durl.length() > 0) {
                JSONObject videoUrlObj = durl.getJSONObject(0);
                videoUrl = videoUrlObj.getString("url");
            }
        }
        if (videoUrl == null || videoUrl.length() == 0) {
            throw new JSONException("无法获取 MP4 视频地址");
        }
        playerData.videoUrl = videoUrl;
    }

    /**
     * 获取番剧播放地址（与普通视频 API 不同）
     */
    public static void getBangumi(PlayerData playerData) throws JSONException, IOException {
        android.util.Log.e("PlayerApi", "========== getBangumi 开始 ==========");
        android.util.Log.e("PlayerApi", "aid=" + playerData.aid + ", cid=" + playerData.cid + ", qn=" + playerData.qn);

        NetWorkUtil.FormData reqData = new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("aid", playerData.aid)
                .put("cid", playerData.cid)
                .put("fnval", 4048)
                .put("fnvar", 0)
                .put("qn", playerData.qn)
                .put("season_type", 1)
                .put("platform", "pc");

        String url = "https://api.bilibili.com/pgc/player/web/playurl" + reqData.toString();
        android.util.Log.e("PlayerApi", "请求URL: " + url);

        JSONObject body = NetWorkUtil.getJson(url);
        int code = body.optInt("code", -1);
        android.util.Log.e("PlayerApi", "API响应码: " + code);

        if (code != 0) {
            android.util.Log.e("PlayerApi", "API返回错误");
            return;
        }

        JSONObject data = body.getJSONObject("result");
        JSONArray durl = data.getJSONArray("durl");
        JSONObject videoUrlObj = durl.getJSONObject(0);
        String videoUrl = videoUrlObj.getString("url");

        playerData.videoUrl = videoUrl;
        android.util.Log.e("PlayerApi", "videoUrl=" + playerData.videoUrl);

        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";

        JSONArray acceptDescription = data.getJSONArray("accept_description");
        JSONArray acceptQuality = data.getJSONArray("accept_quality");
        int len = acceptDescription.length();
        String[] qnStrList = new String[len];
        int[] qnValueList = new int[len];
        for (int i = 0; i < len; i++) {
            qnStrList[i] = acceptDescription.optString(i);
            qnValueList[i] = acceptQuality.optInt(i);
        }
        playerData.qnStrList = qnStrList;
        playerData.qnValueList = qnValueList;

        android.util.Log.e("PlayerApi", "========== getBangumi 结束 ==========");
    }
}