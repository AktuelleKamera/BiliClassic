package tv.biliclassic.api;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;

public class ConfInfoApi {

    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private static String sWbiMixinKey = "";
    private static int sLastWbiDate = 0;
    // nav 拉取失败后的重试冷却，避免每次搜索都打一次 nav
    private static long sWbiRetryAtMs = 0;
    private static final long WBI_RETRY_INTERVAL_MS = 60 * 1000L;
    // 仅在拉不到 nav 时使用，不持久化
    private static final String FALLBACK_WBI_KEY = "604f662d63f4ee19c94bd8ac0de3f84d";

    private static final String SP_KEY_WBI_MIXIN = "wbi_mixin_key";
    private static final String SP_KEY_WBI_DATE = "wbi_date";

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

    /**
     * 取当日 WBI key：成功才记日期，失败不当天锁死（60秒冷却后重试）
     */
    private static synchronized String ensureWbiMixinKey() {
        int curr = getDateCurr();

        // 从 SharedPreferences 恢复上次缓存的 WBI key（进程被杀后避免重新请求 nav）
        if (sWbiMixinKey == null || sWbiMixinKey.length() == 0) {
            int savedDate = SharedPreferencesUtil.getInt(SP_KEY_WBI_DATE, 0);
            String savedKey = SharedPreferencesUtil.getString(SP_KEY_WBI_MIXIN, "");
            if (savedDate == curr && savedKey != null && savedKey.length() > 0) {
                sWbiMixinKey = savedKey;
                sLastWbiDate = curr;
            }
        }

        boolean hasKey = sWbiMixinKey != null && sWbiMixinKey.length() > 0;
        if (hasKey && sLastWbiDate == curr) {
            return sWbiMixinKey;
        }
        if (hasKey && System.currentTimeMillis() < sWbiRetryAtMs) {
            // 刚失败过，冷却期内先用旧 key 签名
            return sWbiMixinKey;
        }

        try {
            android.util.Log.d("NetDiag", "signWBI: 请求nav接口获取WBI key");
            long t0 = System.currentTimeMillis();
            String rawKey = getWBIRawKey();
            android.util.Log.d("NetDiag", "signWBI: nav接口耗时=" + (System.currentTimeMillis() - t0) + "ms, rawKeyLen=" + (rawKey == null ? -1 : rawKey.length()));
            if (rawKey == null || rawKey.length() < 64) {
                // 被风控时 data 无 wbi_img，拿到的串不完整
                throw new IOException("nav 返回的 wbi_img 不完整");
            }
            sWbiMixinKey = getWBIMixinKey(rawKey);
            sLastWbiDate = curr;
            sWbiRetryAtMs = 0;
            SharedPreferencesUtil.putString(SP_KEY_WBI_MIXIN, sWbiMixinKey);
            SharedPreferencesUtil.putInt(SP_KEY_WBI_DATE, curr);
        } catch (Exception e) {
            android.util.Log.e("NetDiag", "signWBI: 获取WBI key失败 " + e.getClass().getName() + ": " + e.getMessage());
            // 失败不写日期，下次可重试；限频防止拖慢搜索
            sLastWbiDate = 0;
            sWbiRetryAtMs = System.currentTimeMillis() + WBI_RETRY_INTERVAL_MS;
            if (sWbiMixinKey == null || sWbiMixinKey.length() == 0) {
                sWbiMixinKey = FALLBACK_WBI_KEY;
            }
        }
        return sWbiMixinKey;
    }

    /**
     * WBI key 失效（签名校验类错误），下次调用重新拉 nav。
     * 不加锁：可能从主线程调用，而取 key 时会持锁做网络请求
     */
    public static void invalidateWbiKey() {
        sWbiMixinKey = "";
        sLastWbiDate = 0;
        sWbiRetryAtMs = 0;
        SharedPreferencesUtil.putString(SP_KEY_WBI_MIXIN, "");
        SharedPreferencesUtil.putInt(SP_KEY_WBI_DATE, 0);
    }

    public static String signWBI(String url_query) throws IOException, JSONException {
        String mixin_key = ensureWbiMixinKey();

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

        String sortedParams = sortUrlParams(filterWbiParam(paramStr));
        String calc_str = sortedParams + mixin_key;
        String w_rid = md5(calc_str);

        return baseUrl + sortedParams + "&w_rid=" + w_rid;
    }

    /**
     * WBI 签名要求参数值过滤 !'()* ，返回的 URL 与 w_rid 同源，天然一致
     */
    private static String filterWbiParam(String paramStr) {
        if (paramStr == null || paramStr.length() == 0 || !needWbiFilter(paramStr)) {
            return paramStr;
        }
        String[] params = paramStr.split("&");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            String param = params[i];
            int eq = param.indexOf('=');
            if (eq >= 0) {
                String name = param.substring(0, eq);
                String value = param.substring(eq + 1);
                try {
                    value = java.net.URLDecoder.decode(value, "UTF-8");
                    value = value.replace("!", "").replace("'", "")
                            .replace("(", "").replace(")", "").replace("*", "");
                    value = java.net.URLEncoder.encode(value, "UTF-8");
                } catch (Exception e) {
                    // 解码失败保持原样，不破坏请求
                }
                param = name + "=" + value;
            }
            if (i > 0) {
                sb.append("&");
            }
            sb.append(param);
        }
        return sb.toString();
    }

    // 是否含需过滤的字符（裸的或已百分号编码的）
    private static boolean needWbiFilter(String s) {
        if (s.indexOf('!') >= 0 || s.indexOf('\'') >= 0 || s.indexOf('(') >= 0
                || s.indexOf(')') >= 0 || s.indexOf('*') >= 0) {
            return true;
        }
        String u = s.toUpperCase();
        return u.indexOf("%21") >= 0 || u.indexOf("%27") >= 0 || u.indexOf("%28") >= 0
                || u.indexOf("%29") >= 0 || u.indexOf("%2A") >= 0;
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
        ArrayList headers = new ArrayList();
        headers.add("Accept-Language");
        headers.add(NetWorkUtil.getAcceptLanguage());
        // nav 缺 Referer/Origin 会被风控拦截，导致 WBI key 拉不到
        headers.add("Referer");
        headers.add("https://www.bilibili.com/");
        headers.add("Origin");
        headers.add("https://www.bilibili.com");
        headers.add("Accept-Encoding");
        headers.add("identity");
        return NetWorkUtil.get(urlStr, headers);
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