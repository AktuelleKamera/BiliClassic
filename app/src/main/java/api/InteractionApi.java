package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class InteractionApi {

    public static int triple(long aid) throws IOException, JSONException {
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        String arg = "aid=" + aid + "&csrf=" + csrf;
        String response = NetWorkUtil.post("https://api.bilibili.com/x/web-interface/archive/like/triple", arg, NetWorkUtil.webHeaders);
        JSONObject result = new JSONObject(response);
        Log.d("InteractionApi", "三连: " + result.toString());
        return result.optInt("code", -1);
    }

    public static int like(long aid, int likeState) throws IOException, JSONException {
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        String arg = "aid=" + aid + "&like=" + likeState + "&csrf=" + csrf;
        String response = NetWorkUtil.post("https://api.bilibili.com/x/web-interface/archive/like", arg, NetWorkUtil.webHeaders);
        JSONObject result = new JSONObject(response);
        Log.d("InteractionApi", "点赞: " + result.toString());
        return result.optInt("code", -1);
    }

    public static int coin(long aid, int multiply) throws IOException, JSONException {
        String csrf = SharedPreferencesUtil.getString("csrf", "");
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
        ArrayList coinHeaders = new ArrayList();
        coinHeaders.add("Referer");
        coinHeaders.add("https://www.bilibili.com/video/av" + aid);
        String response = NetWorkUtil.post("https://api.bilibili.com/x/web-interface/coin/add", arg, coinHeaders);
        JSONObject result = new JSONObject(response);
        Log.d("InteractionApi", "投币: " + result.toString());
        return result.optInt("code", -1);
    }

    public static int favorite(long aid, long fid) throws IOException, JSONException {
        String strMid = String.valueOf(SharedPreferencesUtil.getLong("mid", 0));
        String addFid = fid + strMid.substring(strMid.length() - 2);
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        String arg = "rid=" + aid + "&type=2&add_media_ids=" + addFid + "&del_media_ids=&csrf=" + csrf;
        String response = NetWorkUtil.post("https://api.bilibili.com/medialist/gateway/coll/resource/deal", arg, NetWorkUtil.webHeaders);
        JSONObject result = new JSONObject(response);
        Log.d("InteractionApi", "收藏: " + result.toString());
        return result.optInt("code", -1);
    }

    public static int getVideoRelation(long aid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/archive/relation?aid=" + aid;
        ArrayList headers = new ArrayList();
        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        headers.add("Referer");
        headers.add("https://www.bilibili.com/");
        headers.add("Cookie");
        headers.add(SharedPreferencesUtil.getString("cookies", ""));
        String response = NetWorkUtil.get(url, headers);
        JSONObject result = new JSONObject(response);
        return result.optInt("code", -1);
    }
}
