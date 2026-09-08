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
 * 修改时间：2026年9月7日
 *
 * 安卓2也要看B站！
 */

//RobinNotBad: 搜索API 自己写的
//逐渐感觉拆json是个很爽的事（
//2023-07-14

//移植到 BiliClassic
//2026-06-15

//一只毛子球：终于把这坨史山升级WBI签名了（
//2026-09-07

/**
 * 搜索统一入口：WBI 签名 + NetWorkUtil 自动请求头（UA/Referer/Cookie）。
 */
public class SearchApi {

    /**
     * 搜索视频（WBI 签名接口）
     * @param keyword 关键词
     * @param page 页码
     * @return 接口完整 JSON 响应（code==0 时 data.result 为结果数组）
     */
    public static JSONObject search(String keyword, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/wbi/search/type?";
        url += "search_type=video";
        url += "&keyword=" + URLEncoder.encode(keyword, "UTF-8");
        url += "&page=" + page;
        url += "&pagesize=20";

        url = ConfInfoApi.signWBI(url);
        String response = NetWorkUtil.get(url);
        if (response == null || response.length() == 0) {
            throw new IOException("empty search response");
        }
        return new JSONObject(response);
    }

    /**
     * 搜索用户（WBI 签名接口）
     * @return 接口完整 JSON 响应（code==0 时 data.result 为结果数组）
     */
    public static JSONObject searchUser(String keyword, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/wbi/search/type?";
        url += "search_type=bili_user";
        url += "&keyword=" + URLEncoder.encode(keyword, "UTF-8");
        url += "&page=" + page;
        url += "&pagesize=20";

        url = ConfInfoApi.signWBI(url);
        String response = NetWorkUtil.get(url);
        if (response == null || response.length() == 0) {
            throw new IOException("empty search response");
        }
        return new JSONObject(response);
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
                cover = "https:" + cover;
            }
            String upName = card.getString("author");
            long play = card.getLong("play");
            String playTimesStr = StringUtil.toWan(play) + "播放";

            videoCardList.add(new VideoCard(title, upName, playTimesStr, cover, aid, bvid, type));
        }
    }

    /**
     * 从结果数组中提取所有视频卡片（包含格式化标题/封面等信息）。
     */
    public static List<VideoCard> parseVideoCards(JSONArray result) throws JSONException {
        ArrayList<VideoCard> list = new ArrayList<VideoCard>();
        getVideosFromSearchResult(result, list);
        return list;
    }
}
