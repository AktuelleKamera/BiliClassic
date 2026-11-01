package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONObject;

import tv.biliclassic.util.NetWorkUtil;

public class SubtitleApi {
    public static JSONArray getSubtitleList(long aid, long cid) {
        try {
            String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid + "&cid=" + cid;
            url = ConfInfoApi.signWBI(url);
            JSONObject root = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
            if (root == null || root.optInt("code", -1) != 0) {
                return null;
            }
            JSONObject data = root.optJSONObject("data");
            JSONObject subtitle = data == null ? null : data.optJSONObject("subtitle");
            JSONArray subtitles = subtitle == null ? null : subtitle.optJSONArray("subtitles");
            return subtitles == null || subtitles.length() == 0 ? null : subtitles;
        } catch (Throwable t) {
            return null;
        }
    }

    public static JSONArray getSubtitleBody(String subtitleUrl) {
        if (subtitleUrl == null || subtitleUrl.length() == 0) {
            return null;
        }
        try {
            if (subtitleUrl.startsWith("//")) {
                subtitleUrl = "https:" + subtitleUrl;
            } else if (!subtitleUrl.startsWith("http://") && !subtitleUrl.startsWith("https://")) {
                subtitleUrl = "https://" + subtitleUrl;
            }
            String raw = NetWorkUtil.get(subtitleUrl);
            if (raw == null || raw.length() == 0) {
                return null;
            }
            try {
                return new JSONArray(raw);
            } catch (Throwable ignored) {
            }
            JSONObject root = new JSONObject(raw);
            JSONArray body = root.optJSONArray("body");
            if (body != null) {
                return body;
            }
            JSONObject data = root.optJSONObject("data");
            return data == null ? null : data.optJSONArray("body");
        } catch (Throwable t) {
            return null;
        }
    }
}
