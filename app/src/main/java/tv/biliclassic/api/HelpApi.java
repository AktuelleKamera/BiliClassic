package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.model.HelpItem;
import tv.biliclassic.util.NetWorkUtil;

public final class HelpApi {

    private static final String LIST_URL = "http://www.biliclassic.cn/api/help/list.json";

    private HelpApi() {
    }

    public static List<HelpItem> getList() throws IOException, Exception {
        JSONObject json = NetWorkUtil.getJson(LIST_URL);
        List<HelpItem> out = new ArrayList<HelpItem>();
        if (json == null) return out;
        JSONArray items = json.optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            HelpItem help = new HelpItem();
            help.id = item.optString("id", "");
            help.title = item.optString("title", "");
            help.summary = item.optString("summary", "");
            help.url = item.optString("url", "");
            if (help.title.length() > 0) out.add(help);
        }
        return out;
    }

    public static String resolveUrl(HelpItem item) {
        if (item == null) return "";
        String url = item.url == null ? "" : item.url.trim();
        if (url.length() == 0) {
            return "http://www.biliclassic.cn/api/help/" + item.id + ".html";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "http:" + url;
        while (url.startsWith("/")) url = url.substring(1);
        return "http://www.biliclassic.cn/api/help/" + url;
    }
}
