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
package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class RecommendApi {
    private static final long UNIQ_ID = (long) (new Random().nextDouble() * (1500000000000L - 1300000000000L));

    /**
     * 获取推荐视频列表（携带Cookie，个性化推荐）
     * 直接使用 API 返回的 aid，不进行 BV 转换
     */
    public static void getRecommend(List<VideoCard> videoCardList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd";
        url += new NetWorkUtil.FormData().setUrlParam(true)
                .put("web_location", 1430650)
                .put("feed_version", "V8")
                .put("homepage_ver", 1)
                .put("uniq_id", UNIQ_ID)
                .put("screen", "1100-2056");

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        ArrayList<String> headers = buildHeaders(cookies);

        String signedUrl = ConfInfoApi.signWBI(url);
        JSONObject result = NetWorkUtil.getJson(signedUrl, headers);

        JSONObject data = result.getJSONObject("data");
        JSONArray item = data.getJSONArray("item");

        for (int i = 0; i < item.length(); i++) {
            JSONObject card = item.getJSONObject(i);
            String bvid = card.getString("bvid");
            if (bvid == null || bvid.length() == 0) {
                Log.d("BiliClient", "RecommendApi getRecommend: isAd");
                continue;
            }
            String cover = card.getString("pic");
            String title = card.getString("title");
            String upName = card.getJSONObject("owner").getString("name");
            String view = StringUtil.toWan(card.getJSONObject("stat").getInt("view"));
            int danmaku = card.getJSONObject("stat").getInt("danmaku");

            // 直接使用 API 返回的 aid（不要用 BV 转换）
            long aid = card.optLong("aid", 0);

            android.util.Log.e("RecommendApi", "视频: " + title + ", aid=" + aid + ", bvid=" + bvid);

            videoCardList.add(new VideoCard(title, upName, view, cover, aid, bvid, danmaku));
        }
    }

    /**
     * 构建请求头
     */
    private static ArrayList<String> buildHeaders(String cookies) {
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("User-Agent");
        headers.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.add("Accept");
        headers.add("application/json, text/plain, */*");
        headers.add("Accept-Language");
        headers.add("zh-CN,zh;q=0.9,en;q=0.8");
        headers.add("Referer");
        headers.add("https://www.bilibili.com/");
        headers.add("Origin");
        headers.add("https://www.bilibili.com");
        headers.add("Cookie");
        headers.add(cookies);
        return headers;
    }

    /**
     * 获取相关视频推荐
     */
    public static ArrayList<VideoCard> getRelated(long aid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/web-interface/archive/related?aid=" + aid;

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        ArrayList<String> headers = buildHeaders(cookies);
        JSONObject result = NetWorkUtil.getJson(url, headers);

        ArrayList<VideoCard> videoList = new ArrayList<VideoCard>();
        if (result.has("data") && !result.isNull("data")) {
            JSONArray data = result.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject card = data.getJSONObject(i);
                VideoCard videoCard = new VideoCard();
                videoCard.aid = card.getLong("aid");
                videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view"));
                videoCard.cover = card.getString("pic");
                videoCard.title = card.getString("title");
                videoCard.upName = card.getJSONObject("owner").getString("name");
                int danmaku = card.getJSONObject("stat").getInt("danmaku");
                videoCard.danmaku = danmaku;
                videoList.add(videoCard);
            }
        }

        return videoList;
    }

    /**
     * 获取热门视频
     */
    public static void getPopular(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/web-interface/popular?pn=" + page + "&ps=10";

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        ArrayList<String> headers = buildHeaders(cookies);
        JSONObject result = NetWorkUtil.getJson(url, headers);

        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("list")) {
                JSONArray list = data.getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view"));
                    int danmaku = card.getJSONObject("stat").getInt("danmaku");
                    videoCard.danmaku = danmaku;
                    videoCardList.add(videoCard);
                }
            }
        }
    }

    /**
     * 获取入站必刷（珍贵视频）
     */
    public static void getPrecious(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/web-interface/popular/precious?page=" + page + "&page_size=10";

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        ArrayList<String> headers = buildHeaders(cookies);
        JSONObject result = NetWorkUtil.getJson(url, headers);

        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("list")) {
                JSONArray list = data.getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view"));
                    int danmaku = card.getJSONObject("stat").getInt("danmaku");
                    videoCard.danmaku = danmaku;
                    videoCardList.add(videoCard);
                }
            }
        }
    }
}