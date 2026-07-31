package tv.biliclassic.api;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

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
 * 修改时间：2026年6月15日
 *
 * 安卓2也要看B站！
 */

//RobinNotBad: 搜索API 自己写的
//逐渐感觉拆json是个很爽的事（
//2023-07-14
//移植到 BiliClassic

public class SearchApi {

    public static String seid = "";
    public static String searchKeyword = "";

    /**
     * 搜索视频
     * @param keyword 关键词
     * @param page 页码
     * @return 搜索结果 JSONArray
     */
    public static JSONArray search(String keyword, int page) throws IOException, JSONException {
        if (!searchKeyword.equals(keyword)) {
            searchKeyword = keyword;
            seid = "";
        }

        String url = "https://api.bilibili.com/x/web-interface/search/type?";
        url += "page=" + page +
                "&pagesize=20" +
                "&search_type=video" +
                "&keyword=" + URLEncoder.encode(searchKeyword, "UTF-8");

        // 使用 NetWorkUtil 发送请求
        String response = NetWorkUtil.get(url);
        if (response == null || response.length() == 0) {
            return null;
        }

        JSONObject all = new JSONObject(response);
        if (all.getInt("code") != 0) {
            return null;
        }

        JSONObject data = all.getJSONObject("data");
        if (data.has("result") && !data.isNull("result")) {
            return data.getJSONArray("result");
        }
        return null;
    }

    /**
     * 获取视频列表
     */
    public static void getVideosFromSearchResult(JSONArray input, ArrayList<VideoCard> videoCardList) throws JSONException {
        for (int i = 0; i < input.length(); i++) {
            JSONObject card = input.getJSONObject(i);
            String type = card.getString("type");

            if (!"video".equals(type)) {
                continue;
            }

            String title = card.getString("title");
            title = title.replace("<em class=\"keyword\">", "").replace("</em>", "");
            title = StringUtil.htmlToString(title);

            String bvid = card.getString("bvid");
            long aid = card.getLong("aid");
            String cover = card.getString("pic");
            if (!cover.startsWith("http")) {
                cover = "http:" + cover;
            }
            String upName = card.getString("author");
            long play = card.getLong("play");
            String playTimesStr = StringUtil.toWan(play) + "播放";

            videoCardList.add(new VideoCard(title, upName, playTimesStr, cover, aid, bvid, type));
        }
    }

    /**
     * 搜索用户
     */
    public static JSONArray searchUser(String keyword, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/search/type?";
        url += "page=" + page +
                "&pagesize=20" +
                "&search_type=bili_user" +
                "&keyword=" + URLEncoder.encode(keyword, "UTF-8");

        String response = NetWorkUtil.get(url);
        if (response == null || response.length() == 0) {
            return null;
        }

        JSONObject all = new JSONObject(response);
        if (all.getInt("code") != 0) {
            return null;
        }

        JSONObject data = all.getJSONObject("data");
        if (data.has("result") && !data.isNull("result")) {
            return data.getJSONArray("result");
        }
        return null;
    }
}