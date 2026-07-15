package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class PartitionApi {

    private static final String TAG = "PartitionApi";

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
        if (cookies != null && cookies.length() > 0) {
            headers.add("Cookie");
            headers.add(cookies);
        }
        return headers;
    }

    public static void getRegionVideos(List<VideoCard> videoCardList, int rid, int page) throws IOException, JSONException {

        // 所有分区统一使用 tag 接口 + WBI 签名
        // 番剧（rid=13）使用专门的 tag_id=30294
        String url;
        if (rid == 13) {
            String unsignedUrl = "https://api.bilibili.com/x/tag/ranking/archives?tag_id=30294&rid=" + rid + "&type=0&pn=" + page + "&ps=20&gaia_source=main_web";
            url = ConfInfoApi.signWBI(unsignedUrl);
        } else {
            String unsignedUrl = "https://api.bilibili.com/x/tag/ranking/archives?tag_id=" + rid + "&type=0&pn=" + page + "&ps=20&gaia_source=main_web";
            url = ConfInfoApi.signWBI(unsignedUrl);
        }

        android.util.Log.d(TAG, "请求URL: " + url);

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null) {
            cookies = "";
        }
        ArrayList<String> headers = buildHeaders(cookies);

        JSONObject result = NetWorkUtil.getJson(url, headers);

        if (result == null) {
            android.util.Log.e(TAG, "result 为 null");
            return;
        }

        android.util.Log.d(TAG, "响应: " + result.toString());

        int code = result.optInt("code", -1);
        if (code != 0) {
            String message = result.optString("message", "未知错误");
            android.util.Log.e(TAG, "API返回错误: code=" + code + ", message=" + message);
            return;
        }

        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("archives") && !data.isNull("archives")) {
                JSONArray archives = data.getJSONArray("archives");
                android.util.Log.d(TAG, "获取到 " + archives.length() + " 个视频");
                for (int i = 0; i < archives.length(); i++) {
                    JSONObject card = archives.getJSONObject(i);

                    String bvid = card.optString("bvid", "");
                    if (bvid.length() == 0) {
                        continue;
                    }

                    String cover = card.optString("pic", "");
                    String title = card.optString("title", "无标题");
                    String upName = "";
                    if (card.has("owner") && !card.isNull("owner")) {
                        upName = card.getJSONObject("owner").optString("name", "");
                    }
                    int view = 0;
                    int danmaku = 0;
                    if (card.has("stat") && !card.isNull("stat")) {
                        view = card.getJSONObject("stat").optInt("view", 0);
                        danmaku = card.getJSONObject("stat").optInt("danmaku", 0);
                    }
                    long aid = card.optLong("aid", 0);

                    videoCardList.add(new VideoCard(
                            title,
                            upName,
                            StringUtil.toWan(view),
                            cover,
                            aid,
                            bvid,
                            danmaku
                    ));
                }
            } else {
                android.util.Log.e(TAG, "data 中没有 archives 字段");
            }
        } else {
            android.util.Log.e(TAG, "result 中没有 data 字段");
        }
    }
}