package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import tv.biliclassic.model.PlayerData;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class PlayerApi {

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

        playerData.timeStamp = System.currentTimeMillis();
        android.util.Log.e("PlayerApi", "开始获取新地址，timeStamp 已更新");

        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";
        android.util.Log.e("PlayerApi", "danmakuUrl=" + playerData.danmakuUrl);

        boolean html5 = !download && "mtvPlayer".equals(SharedPreferencesUtil.getString("player", ""));
        android.util.Log.e("PlayerApi", "html5模式=" + html5);

        String url = "https://api.bilibili.com/x/player/wbi/playurl?"
                + "avid=" + playerData.aid
                + "&cid=" + playerData.cid
                + (html5 ? "&high_quality=1" : "")
                + "&qn=" + playerData.qn
                + (download ? "&fnval=1" : "&fnval=0")   // 下载用MP4(更多画质), 播放用FLV(IJK最佳)
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

        // ========== 尝试解析 durl（MP4 格式） ==========
        if (data.has("durl")) {
            JSONArray durl = data.getJSONArray("durl");
            android.util.Log.e("PlayerApi", "durl 数组长度: " + durl.length());
            if (durl.length() > 0) {
                JSONObject videoUrlObj = durl.getJSONObject(0);
                videoUrl = videoUrlObj.getString("url");
                android.util.Log.e("PlayerApi", "使用 durl 格式");
            }
        }

        // ========== 如果没有 durl，尝试解析 dash（DASH 格式） ==========
        if (videoUrl == null && data.has("dash")) {
            JSONObject dash = data.getJSONObject("dash");
            android.util.Log.e("PlayerApi", "使用 dash 格式");
            JSONArray video = dash.getJSONArray("video");
            if (video.length() > 0) {
                JSONObject firstVideo = video.getJSONObject(0);
                JSONArray backupUrl = firstVideo.optJSONArray("backupUrl");
                if (backupUrl != null && backupUrl.length() > 0) {
                    videoUrl = backupUrl.getString(0);
                } else {
                    videoUrl = firstVideo.optString("baseUrl", "");
                }
                android.util.Log.e("PlayerApi", "视频地址: " + videoUrl);
            }
        }

        if (videoUrl == null || videoUrl.length() == 0) {
            android.util.Log.e("PlayerApi", "无法获取视频地址");
            throw new JSONException("无法获取视频地址");
        }

        playerData.videoUrl = videoUrl;
        android.util.Log.e("PlayerApi", "videoUrl: " + playerData.videoUrl);

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

        android.util.Log.e("PlayerApi", "========== getVideo 结束，videoUrl=" + playerData.videoUrl + " ==========");
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