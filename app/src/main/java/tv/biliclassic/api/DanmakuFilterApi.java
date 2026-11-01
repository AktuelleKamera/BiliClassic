package tv.biliclassic.api;

import org.json.JSONObject;

import java.net.URLEncoder;

import tv.biliclassic.util.NetWorkUtil;

public final class DanmakuFilterApi {
    private static final String ADD_URL = "https://api.bilibili.com/x/dm/filter/user/add";

    private DanmakuFilterApi() {
    }

    public static int addUserFilter(long uid) {
        try {
            String csrf = NetWorkUtil.getCsrf();
            String body = "type=2&filter=" + uid
                    + "&csrf=" + URLEncoder.encode(csrf == null ? "" : csrf, "UTF-8");
            String response = NetWorkUtil.post(ADD_URL, body,
                    NetWorkUtil.webHeaders, "application/x-www-form-urlencoded");
            JSONObject json = new JSONObject(response);
            return json.optInt("code", -1);
        } catch (Throwable t) {
            return -1;
        }
    }
}
