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
 * 安卓1也要看B站！
 */
package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.StringUtil;

/**
 * 稍后再看
 */
public class WatchLaterApi {

    /** 获取稍后再看列表（一次性返回全部） */
    public static void getWatchLaterList(List<VideoCard> out) throws IOException, JSONException {
        JSONObject resp = NetWorkUtil.getJson("https://api.bilibili.com/x/v2/history/toview/web");
        if (resp.optInt("code", -1) != 0) {
            throw new JSONException(resp.optString("message", "获取稍后再看失败"));
        }
        JSONObject data = resp.optJSONObject("data");
        JSONArray list = data != null ? data.optJSONArray("list") : null;
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            JSONObject owner = item.optJSONObject("owner");
            JSONObject stat = item.optJSONObject("stat");
            long view = stat != null ? stat.optLong("view", 0) : 0;
            out.add(new VideoCard(
                    item.optString("title", ""),
                    owner != null ? owner.optString("name", "") : "",
                    StringUtil.toWan(view) + "观看",
                    item.optString("pic", ""),
                    item.optLong("aid", 0),
                    item.optString("bvid", "")));
        }
    }

    public static int delete(long aid) throws IOException, JSONException {
        String arg = "aid=" + aid + "&csrf=" + NetWorkUtil.getCsrf();
        return new JSONObject(NetWorkUtil.post("https://api.bilibili.com/x/v2/history/toview/del", arg,
                NetWorkUtil.webHeaders)).optInt("code", -1);
    }

    /** 加入稍后再看 */
    public static int add(long aid) throws IOException, JSONException {
        String arg = "aid=" + aid + "&csrf=" + NetWorkUtil.getCsrf();
        return new JSONObject(NetWorkUtil.post("https://api.bilibili.com/x/v2/history/toview/add", arg,
                NetWorkUtil.webHeaders)).optInt("code", -1);
    }
}
