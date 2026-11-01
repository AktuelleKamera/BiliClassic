package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.NetWorkUtil;

public final class AgreementApi {

    private static final String[] URLS = {
            "http://www.biliclassic.cn/api/agreement.json"
    };

    private AgreementApi() {
    }

    public static List<String> fetch() {
        for (int i = 0; i < URLS.length; i++) {
            List<String> lines = fetch(URLS[i]);
            if (lines != null && lines.size() > 0) {
                return lines;
            }
        }
        return null;
    }

    private static List<String> fetch(String url) {
        try {
            ArrayList<String> headers = new ArrayList<String>();
            headers.add("User-Agent");
            headers.add("BiliClassic");
            JSONObject json = NetWorkUtil.getJson(url, headers);
            if (json == null) return null;

            JSONArray lines = json.optJSONArray("agreement");
            if (lines == null) lines = json.optJSONArray("content");
            if (lines == null) lines = json.optJSONArray("lines");
            if (lines != null) {
                List<String> out = new ArrayList<String>();
                for (int i = 0; i < lines.length(); i++) {
                    String line = lines.optString(i, "");
                    if (line != null) out.add(line);
                }
                return out;
            }

            String content = json.optString("content", "");
            JSONObject data = json.optJSONObject("data");
            if (content.length() == 0 && data != null) {
                content = data.optString("content", "");
            }
            if (content.length() == 0) return null;

            String[] split = content.split("\\r?\\n");
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < split.length; i++) {
                out.add(split[i]);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}
