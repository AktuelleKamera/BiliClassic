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
 * 修改时间：2026年10月5日
 *
 * 安卓1也要看B站！
 */
package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import tv.biliclassic.util.NetWorkUtil;

public class InteractionApi {

    public static int triple(long aid) throws IOException, JSONException {
        String csrf = NetWorkUtil.getCsrf();
        String arg = "aid=" + aid + "&csrf=" + csrf;
        String response = NetWorkUtil.post("https://api.bilibili.com/x/web-interface/archive/like/triple", arg, NetWorkUtil.webHeaders);
        JSONObject result = new JSONObject(response);
        Log.d("InteractionApi", "三连: " + result.toString());
        return result.optInt("code", -1);
    }

    public static int like(long aid, int likeState) throws IOException, JSONException {
        String csrf = NetWorkUtil.getCsrf();
        String arg = "aid=" + aid + "&like=" + likeState + "&csrf=" + csrf;
        String response = NetWorkUtil.post("https://api.bilibili.com/x/web-interface/archive/like", arg, NetWorkUtil.webHeaders);
        JSONObject result = new JSONObject(response);
        Log.d("InteractionApi", "点赞: " + result.toString());
        return result.optInt("code", -1);
    }

    public static int coin(long aid, int multiply) throws IOException, JSONException {
        String csrf = NetWorkUtil.getCsrf();
        NetWorkUtil.fetchBuvid3();
        String arg = "aid=" + aid
            + "&multiply=" + multiply
            + "&select_like=0"
            + "&cross_domain=true"
            + "&from_spmid=333.788.0.0"
            + "&spmid=333.788.0.0"
            + "&statistics=%7B%22appId%22%3A100%2C%22platform%22%3A5%7D"
            + "&eab_x=2"
            + "&ramval=0"
            + "&source=web_normal"
            + "&csrf=" + csrf;
        // 投币必须带视频页 Referer（其余头仍由 NetWorkUtil 统一处理）
        ArrayList<String> coinHeaders = new ArrayList<String>();
        coinHeaders.add("Referer");
        coinHeaders.add("https://www.bilibili.com/video/av" + aid);
        String response = NetWorkUtil.post("https://api.bilibili.com/x/web-interface/coin/add", arg, coinHeaders);
        JSONObject result = new JSONObject(response);
        Log.d("InteractionApi", "投币: " + result.toString());
        return result.optInt("code", -1);
    }
}