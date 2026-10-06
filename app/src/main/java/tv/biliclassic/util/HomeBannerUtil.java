package tv.biliclassic.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页横幅：默认用本地图，若服务端提供则改用网络图（可多张，自动轮播）。
 * 列表接口返回形如 {"images":["bili_main_banner1.jpg","bili_main_banner2.jpg"]}，
 * 元素可以是文件名（拼到 base）或完整 URL。
 */
public class HomeBannerUtil {

    private static final String LIST_URL = "http://www.biliclassic.cn/api/images/list.json";
    private static final String BASE = "http://www.biliclassic.cn/api/images/";

    public static List<String> fetchBanners() {
        List<String> out = new ArrayList<String>();
        try {
            JSONObject json = NetWorkUtil.getJson(LIST_URL);
            if (json == null) return out;
            JSONArray arr = json.optJSONArray("images");
            if (arr == null) arr = json.optJSONArray("banners");
            if (arr == null) arr = json.optJSONArray("list");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (s == null || s.length() == 0) continue;
                out.add(s.startsWith("http") ? s : BASE + s);
            }
        } catch (Throwable t) {
        }
        return out;
    }
}
