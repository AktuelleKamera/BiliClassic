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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.model.ApiResult;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class HistoryApi {

    /**
     * 获取视频历史记录
     */
    public static ApiResult getHistory(ApiResult lastResult, List<VideoCard> videoList)
            throws IOException, JSONException {

        String url = "https://api.bilibili.com/x/web-interface/history/cursor?type=archive"
                + "&view_at=" + lastResult.timestamp
                + "&business=" + lastResult.business
                + "&max=" + lastResult.offset;

        // 构建带 Cookie 的请求头
        ArrayList<String> headers = new ArrayList<String>();
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        headers.add("Cookie");
        headers.add(cookies);
        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        headers.add("Referer");
        headers.add("https://www.bilibili.com/");
        headers.add("Accept");
        headers.add("application/json, text/plain, */*");

        JSONObject result = NetWorkUtil.getJson(url, headers);
        ApiResult apiResult = new ApiResult(result);

        if (result.getInt("code") != 0) {
            apiResult.code = result.getInt("code");
            apiResult.message = result.optString("message", "请求失败");
            return apiResult;
        }

        if (!result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            JSONArray list = data.optJSONArray("list");

            if (list != null && list.length() > 0) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject videoCard = list.getJSONObject(i);
                    String title = videoCard.optString("title", "未知标题");
                    String cover = videoCard.optString("cover", "");
                    String upName = videoCard.optString("author_name", "未知UP主");
                    int progress = videoCard.optInt("progress", 0);

                    JSONObject history = videoCard.optJSONObject("history");
                    if (history == null) continue;

                    long aid = history.optLong("oid", 0);
                    String bvid = history.optString("bvid", "");

                    if (aid == 0 && bvid.length() == 0) continue;

                    String viewStr;
                    if (progress == 0) {
                        viewStr = "还没看过";
                    } else {
                        viewStr = "看到" + StringUtil.toTime(progress);
                    }

                    videoList.add(new VideoCard(title, upName, viewStr, cover, aid, bvid));
                }
            }

            if (list == null || list.length() == 0) {
                apiResult.isBottom = true;
            }

            JSONObject cursor = data.optJSONObject("cursor");
            if (cursor != null) {
                apiResult.business = cursor.optString("business", "");
                apiResult.offset = cursor.optLong("max", 0);
                apiResult.timestamp = cursor.optLong("view_at", 0);
            }
        }

        return apiResult;
    }
}