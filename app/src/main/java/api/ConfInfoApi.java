package tv.biliclassic.api;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ConfInfoApi {

    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private static String sWbiMixinKey = "";
    private static int sLastWbiDate = 0;

    public static String getWBIRawKey() throws IOException, JSONException {
        String response = httpGet("https://api.bilibili.com/x/web-interface/nav");
        JSONObject getJson = new JSONObject(response);
        JSONObject wbi_img = getJson.getJSONObject("data").getJSONObject("wbi_img");
        String img_key = getFileFirstName(getFileNameFromLink(wbi_img.getString("img_url")));
        String sub_key = getFileFirstName(getFileNameFromLink(wbi_img.getString("sub_url")));
        return img_key + sub_key;
    }

    public static String getWBIMixinKey(String raw_key) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            key.append(raw_key.charAt(MIXIN_KEY_ENC_TAB[i]));
        }
        return key.toString();
    }

    public static String signWBI(String url_query) throws IOException, JSONException {
        String mixin_key;
        int curr = getDateCurr();
        if (sLastWbiDate < curr) {
            sLastWbiDate = curr;
            try {
                String rawKey = getWBIRawKey();
                mixin_key = getWBIMixinKey(rawKey);
                sWbiMixinKey = mixin_key;
            } catch (Exception e) {
                if (sWbiMixinKey == null || sWbiMixinKey.length() == 0) {
                    sWbiMixinKey = "604f662d63f4ee19c94bd8ac0de3f84d";
                }
                mixin_key = sWbiMixinKey;
            }
        } else {
            if (sWbiMixinKey == null || sWbiMixinKey.length() == 0) {
                try {
                    String rawKey = getWBIRawKey();
                    sWbiMixinKey = getWBIMixinKey(rawKey);
                } catch (Exception e) {
                    sWbiMixinKey = "604f662d63f4ee19c94bd8ac0de3f84d";
                }
            }
            mixin_key = sWbiMixinKey;
        }

        String wts = String.valueOf(System.currentTimeMillis() / 1000);

        // 提取 query 字符串
        String query = url_query;
        String baseUrl = "";
        int queryIndex = url_query.indexOf('?');
        if (queryIndex >= 0) {
            baseUrl = url_query.substring(0, queryIndex + 1);
            query = url_query.substring(queryIndex + 1);
        } else {
            baseUrl = url_query + "?";
            query = "";
        }

        // 构建参数字符串
        String paramStr = query;
        if (paramStr.length() > 0) {
            paramStr += "&wts=" + wts;
        } else {
            paramStr = "wts=" + wts;
        }

        String sortedParams = sortUrlParams(paramStr);
        String calc_str = sortedParams + mixin_key;
        String w_rid = md5(calc_str);

        return baseUrl + sortedParams + "&w_rid=" + w_rid;
    }

    public static String sortUrlParams(String urlQuery) {
        if (urlQuery == null || urlQuery.length() == 0) {
            return "";
        }
        String[] params = urlQuery.split("&");
        Map<String, String> paramMap = new HashMap<String, String>();

        for (String param : params) {
            if (param == null || param.length() == 0) continue;
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2) {
                paramMap.put(keyValue[0], keyValue[1]);
            } else if (keyValue.length == 1) {
                paramMap.put(keyValue[0], "");
            }
        }

        Map<String, String> sortedMap = new TreeMap<String, String>(paramMap);
        StringBuilder sortedUrl = new StringBuilder();
        boolean isFirst = true;
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            if (!isFirst) {
                sortedUrl.append("&");
            } else {
                isFirst = false;
            }
            sortedUrl.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sortedUrl.toString();
    }

    public static int getDateCurr() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) * 10000 + (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DATE);
    }

    // 工具方法

    private static String getFileNameFromLink(String link) {
        int length = link.length();
        for (int i = length - 1; i > 0; i--) {
            if (link.charAt(i) == '/') {
                return link.substring(i + 1);
            }
        }
        return link;
    }

    private static String getFileFirstName(String file) {
        for (int i = 0; i < file.length(); i++) {
            if (file.charAt(i) == '.') {
                return file.substring(0, i);
            }
        }
        return file;
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}