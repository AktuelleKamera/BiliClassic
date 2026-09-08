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