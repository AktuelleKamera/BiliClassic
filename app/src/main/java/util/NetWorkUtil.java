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
 * 修改时间：2026年6月19日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.util;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class NetWorkUtil {

    public static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";

    // 默认请求头，用于各API调用
    public static ArrayList webHeaders = new ArrayList();

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;
    private static final int MAX_REDIRECT_COUNT = 5;

    // 线程安全的 Cookie 存储
    private static String sCookieString = "";

    // 强制携带登录 Cookie 标志（用于 无痕模式 下仍需登录才能使用的功能，如播放历史）
    private static boolean sForceLogin = false;

    public static void setForceLogin(boolean force) {
        sForceLogin = force;
    }

    public static synchronized String getCookieString() {
        return sCookieString;
    }

    public static synchronized void setCookieString(String cookie) {
        if (cookie == null) cookie = "";
        sCookieString = mergeCookies(sCookieString, cookie);
    }

    public static synchronized void ensureBrowserCookies() {
        Map cookieMap = parseCookieMap(SharedPreferencesUtil.getString("cookies", ""));

        // b_nut
        if (!cookieMap.containsKey("b_nut")) {
            cookieMap.put("b_nut", String.valueOf(System.currentTimeMillis() / 1000));
        }

        // b_lsid: 8位大写hex + "_" + 当前时间hex
        if (!cookieMap.containsKey("b_lsid")) {
            java.util.Random rnd = new java.util.Random();
            String hex = "0123456789ABCDEF";
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 8; i++) sb.append(hex.charAt(rnd.nextInt(16)));
            sb.append("_");
            sb.append(Long.toHexString(System.currentTimeMillis()).toUpperCase());
            cookieMap.put("b_lsid", sb.toString());
        }

        // _uuid: 8-4-4-4-12 + 5位time%100000 + infoc
        if (!cookieMap.containsKey("_uuid")) {
            java.util.Random rnd = new java.util.Random();
            String hex = "0123456789ABCDEF";
            StringBuffer sb = new StringBuffer();
            int[] groups = {8, 4, 4, 4, 12};
            for (int g = 0; g < groups.length; g++) {
                for (int i = 0; i < groups[g]; i++) sb.append(hex.charAt(rnd.nextInt(16)));
                if (g < groups.length - 1) sb.append("-");
            }
            sb.append(String.format("%05d", System.currentTimeMillis() % 100000));
            sb.append("infoc");
            cookieMap.put("_uuid", sb.toString());
        }

        // LIVE_BUVID
        if (!cookieMap.containsKey("LIVE_BUVID")) {
            long min = 1000000000000000L;
            long max = 9999999999999999L;
            cookieMap.put("LIVE_BUVID", "AUTO" + (min + (long)(new java.util.Random().nextDouble() * (max - min))));
        }

        // browser_resolution (默认1280x720)
        if (!cookieMap.containsKey("browser_resolution")) {
            cookieMap.put("browser_resolution", "1280-720");
        }

        // buvid_fp (随机32位hex)
        if (!cookieMap.containsKey("buvid_fp")) {
            java.util.Random rnd = new java.util.Random();
            String hex = "0123456789abcdef";
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 32; i++) sb.append(hex.charAt(rnd.nextInt(16)));
            cookieMap.put("buvid_fp", sb.toString());
        }

        // 其他静态cookie
        if (!cookieMap.containsKey("enable_web_push")) cookieMap.put("enable_web_push", "DISABLE");
        if (!cookieMap.containsKey("home_feed_column")) cookieMap.put("home_feed_column", "4");
        if (!cookieMap.containsKey("PVID")) cookieMap.put("PVID", "1");

        String newCookie = mapToCookieString(cookieMap);
        SharedPreferencesUtil.putString("cookies", newCookie);
        setCookieString(newCookie);
    }

    public static synchronized void refreshHeaders() {
        ensureBrowserCookies();
        String cookie = cleanCookieString(SharedPreferencesUtil.getString("cookies", ""));
        if (cookie == null) cookie = "";
        SharedPreferencesUtil.putString("cookies", cookie);
        sCookieString = mergeCookies(sCookieString, cookie);
    }

    // Cookie 工具方法

    /**
     * 合并 Cookie：按名称去重，后面的覆盖前面的
     */
    private static String mergeCookies(String existing, String newCookie) {
        if (newCookie == null || newCookie.length() == 0) {
            return existing == null ? "" : existing;
        }
        if (existing == null || existing.length() == 0) {
            return newCookie;
        }

        Map cookieMap = parseCookieMap(existing);
        Map newMap = parseCookieMap(newCookie);

        for (Iterator it = newMap.keySet().iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            cookieMap.put(key, newMap.get(key));
        }

        return mapToCookieString(cookieMap);
    }

    /**
     * 解析 Cookie 字符串为 Map
     */
    private static Map parseCookieMap(String cookie) {
        Map map = new HashMap();
        if (cookie == null || cookie.length() == 0) {
            return map;
        }
        String[] pairs = cookie.split("; ");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            int eq = pair.indexOf("=");
            if (eq > 0) {
                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Map 转 Cookie 字符串
     */
    private static String mapToCookieString(Map map) {
        StringBuffer sb = new StringBuffer();
        for (Iterator it = map.keySet().iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            String value = (String) map.get(key);
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(key).append("=").append(value);
        }
        return sb.toString();
    }

    /**
     * 从 Cookie 字符串中获取指定名称的值（自动 URL 解码）
     * 修复：使用正则提取，避免 JSON 污染
     */
    public static synchronized String getInfoFromCookie(String name, String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return "";
        }

        // 直接用正则提取，避免 parseCookieMap 解析 JSON 污染
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(name + "=([^;\\s]+)");
        java.util.regex.Matcher m = p.matcher(cookie);
        if (m.find()) {
            String value = m.group(1);
            // 如果提取的值包含引号或逗号，说明被污染了，尝试用 URL 解码
            if (value != null && value.length() > 0) {
                try {
                    return URLDecoder.decode(value, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return value;
                }
            }
        }
        return "";
    }

    /**
     * 从 Cookie 提取 bili_jct（专门方法，用正则）
     */
    public static synchronized String getCsrfFromCookie(String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return null;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("bili_jct=([a-f0-9]+)");
        java.util.regex.Matcher m = p.matcher(cookie);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    // SSL 相关

    private static final X509TrustManager TRUST_ALL_CERTS = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    public static final HostnameVerifier TRUST_ALL_HOSTNAMES = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) { return true; }
    };

    public static SSLSocketFactory getTrustAllSSLSocketFactory() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new X509TrustManager[]{TRUST_ALL_CERTS}, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            Log.e("NetWorkUtil", "创建 TrustAll SSLSocketFactory 失败: " + e.getMessage());
            return null;
        }
    }

    // JSON 请求

    public static JSONObject getJson(String url) throws IOException, JSONException {
        String response = get(url);
        if (response == null || response.length() == 0) {
            throw new JSONException("在访问 " + url + " 时返回数据为空");
        }
        return new JSONObject(response);
    }

    public static JSONObject getJson(String url, ArrayList headers) throws IOException, JSONException {
        String response = get(url, headers);
        if (response == null || response.length() == 0) {
            throw new JSONException("在访问 " + url + " 时返回数据为空");
        }
        return new JSONObject(response);
    }

    // GET 请求

    public static String get(String url) throws IOException {
        return get(url, null);
    }

    public static String get(String url, ArrayList headers) throws IOException {
        return getInternal(url, headers, 0);
    }

    private static String getInternal(String url, ArrayList headers, int retryCount) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        java.io.CharArrayWriter caw = null;
        try {
            conn = createConnection(url, "GET", headers);
            conn.connect();

            int responseCode = conn.getResponseCode();

            if (responseCode == 301 || responseCode == 302 || responseCode == 307) {
                return handleRedirect(conn, url, headers, "GET", null, retryCount + 1);
            }

            return readResponse(conn, responseCode);

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("请求异常: " + e.toString());
        } finally {
            closeQuietly(reader);
            closeQuietly(caw);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // POST 请求

    public static String post(String url, String data, List headers) throws IOException {
        return post(url, data, headers, "application/x-www-form-urlencoded");
    }

    public static String postJson(String url, String data, List headers) throws IOException {
        return post(url, data, headers, "application/json");
    }

    public static String post(String url, String data) throws IOException {
        return post(url, data, null);
    }

    public static String post(String url, String data, List headers, String contentType) throws IOException {
        return postInternal(url, data, headers, contentType, 0);
    }

    private static String postInternal(String url, String data, List headers, String contentType, int retryCount) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        java.io.CharArrayWriter caw = null;
        try {
            conn = createConnection(url, "POST", headers);
            conn.setRequestProperty("Content-Type", contentType + "; charset=utf-8");

            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();

            if (responseCode == 301 || responseCode == 302 || responseCode == 307) {
                return handleRedirect(conn, url, headers, "POST", data, retryCount + 1);
            }

            return readResponse(conn, responseCode);

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("请求异常: " + e.toString());
        } finally {
            closeQuietly(reader);
            closeQuietly(caw);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // 核心方法

    private static HttpURLConnection createConnection(String url, String method, List headers) throws IOException {
        URL requestUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) requestUrl.openConnection();

        if (url.startsWith("https") && conn instanceof HttpsURLConnection) {
            SSLSocketFactory sslFactory = getTrustAllSSLSocketFactory();
            if (sslFactory != null) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
                ((HttpsURLConnection) conn).setHostnameVerifier(TRUST_ALL_HOSTNAMES);
            }
        }

        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput("POST".equals(method) || "PUT".equals(method));
        conn.setInstanceFollowRedirects(false);

        conn.setRequestProperty("Connection", "keep-alive");

        conn.setRequestProperty("User-Agent", USER_AGENT_WEB);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("Origin", "https://www.bilibili.com");

        // 应用传入的 headers
        boolean hasCookieInHeaders = false;
        if (headers != null) {
            Map headerMap = listToMap(headers);
            for (Iterator it = headerMap.keySet().iterator(); it.hasNext(); ) {
                String key = (String) it.next();
                String value = (String) headerMap.get(key);
                if (key != null && value != null) {
                    conn.setRequestProperty(key, value);
                    if ("Cookie".equalsIgnoreCase(key)) {
                        hasCookieInHeaders = true;
                    }
                }
            }
        }

        // Cookie 处理 - 如果调用方没有自带 Cookie，再自动合并
        if (!hasCookieInHeaders) {
            CookieGenerator.ensureCookies();
            boolean incognitoMode = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.INCOGNITO_MODE, false);
            boolean forceLogin = sForceLogin;
            sForceLogin = false;
            if (forceLogin) {
                incognitoMode = false;
            }
            String cookie = CookieGenerator.getCookieString(!incognitoMode);
            if (!incognitoMode) {
                String loggedCookie = getCookieString();
                if (loggedCookie == null || loggedCookie.length() == 0) {
                    loggedCookie = SharedPreferencesUtil.getString("cookies", "");
                    if (loggedCookie != null && loggedCookie.length() > 0) {
                        setCookieString(loggedCookie);
                    }
                }
                if (loggedCookie != null && loggedCookie.length() > 0) {
                    cookie = mergeCookies(cookie, loggedCookie);
                }
            }
            if (cookie != null && cookie.length() > 0) {
                conn.setRequestProperty("Cookie", cookie);
            }
        }

        return conn;
    }

    /**
     * ArrayList 转 Map（解决 ArrayList 当 Map 用的问题）
     */
    private static Map listToMap(List list) {
        Map map = new HashMap();
        if (list == null) {
            return map;
        }
        for (int i = 0; i < list.size() - 1; i += 2) {
            Object key = list.get(i);
            Object value = list.get(i + 1);
            if (key != null && value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * 处理重定向，支持最大重试次数限制，防止无限递归
     */
    private static String handleRedirect(HttpURLConnection conn, String originalUrl, List headers, String method, String postData, int retryCount) throws IOException {
        // 检查重试次数是否超过上限
        if (retryCount > MAX_REDIRECT_COUNT) {
            throw new IOException("重定向次数超过上限 (" + MAX_REDIRECT_COUNT + " 次)，可能陷入循环重定向。URL: " + originalUrl);
        }

        String location = conn.getHeaderField("Location");
        String setCookie = conn.getHeaderField("Set-Cookie");

        if (setCookie != null && setCookie.length() > 0) {
            saveCookieFromHeader(setCookie);
        }

        conn.disconnect();

        if (location == null || location.length() == 0) {
            throw new IOException("重定向响应缺少 Location 头");
        }

        // 处理相对路径
        if (!location.startsWith("http")) {
            int slashIndex = originalUrl.indexOf("/", 8);
            if (slashIndex > 0) {
                location = originalUrl.substring(0, slashIndex) + "/" + location;
            } else {
                location = originalUrl + "/" + location;
            }
        }

        Log.d("NetWorkUtil", "重定向到: " + location + " (第 " + retryCount + " 次)");

        if ("POST".equals(method) && postData != null) {
            return postInternal(location, postData, headers, "application/x-www-form-urlencoded", retryCount + 1);
        } else {
            return getInternal(location, (ArrayList) headers, retryCount + 1);
        }
    }

    private static String readResponse(HttpURLConnection conn, int responseCode) throws IOException {
        InputStream is;
        if (responseCode >= 400) {
            is = conn.getErrorStream();
            Log.e("NetWorkUtil", "HTTP错误: " + responseCode);
        } else {
            is = conn.getInputStream();
        }

        if (is == null) {
            return "";
        }

        // 定长块列表，避免 ByteArrayOutputStream 翻倍分配的堆碎片 OOM
        java.util.ArrayList chunks = new java.util.ArrayList();
        byte[] buffer = new byte[4096];
        int total = 0;
        int maxBytes = 1024 * 1024;
        int len;
        try {
            while ((len = is.read(buffer, 0, buffer.length)) != -1) {
                total += len;
                if (total > maxBytes) {
                    throw new java.io.IOException("响应数据过大 (" + total + " 字节)");
                }
                byte[] chunk = new byte[len];
                System.arraycopy(buffer, 0, chunk, 0, len);
                chunks.add(chunk);
            }
        } finally {
            is.close();
        }

        String result;
        try {
            byte[] allBytes = new byte[total];
            int offset = 0;
            for (int i = 0; i < chunks.size(); i++) {
                byte[] chunk = (byte[]) chunks.get(i);
                System.arraycopy(chunk, 0, allBytes, offset, chunk.length);
                offset += chunk.length;
            }
            chunks.clear();
            result = new String(allBytes, "UTF-8");
        } catch (OutOfMemoryError e) {
            chunks.clear();
            throw new java.io.IOException("响应数据过大，内存不足");
        }

        String setCookie = conn.getHeaderField("Set-Cookie");
        if (setCookie != null && setCookie.length() > 0) {
            saveCookieFromHeader(setCookie);
        }

        return result;
    }

    // 流式解析 JSON，避免大响应构造 String（byte[]+char[] 双倍内存）
    public static JSONObject getJsonStream(String url, ArrayList headers) throws IOException, JSONException {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            conn = createConnection(url, "GET", headers);
            conn.connect();
            int responseCode = conn.getResponseCode();
            is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                throw new JSONException("在访问 " + url + " 时返回数据为空");
            }

            // 反射调用 JSONTokener(Reader)（API 10+），无 String 分配
            java.io.Reader reader = new java.io.InputStreamReader(is, "UTF-8");
            try {
                java.lang.reflect.Constructor c = JSONTokener.class.getConstructor(java.io.Reader.class);
                JSONTokener tokener = (JSONTokener) c.newInstance(reader);
                reader.close(); is.close(); is = null;
                String setCookie = conn.getHeaderField("Set-Cookie");
                if (setCookie != null && setCookie.length() > 0) saveCookieFromHeader(setCookie);
                return new JSONObject(tokener);
            } catch (Exception ignored) {
                // API < 10：JSONTokener(Reader) 不存在，走老方式
            }
            // 原始连接已消耗，断开后重新请求
            try { reader.close(); } catch (Exception ignored) {}
            try { is.close(); } catch (Exception ignored) {}
            conn.disconnect();
            conn = null;
            is = null;

            String text = get(url, headers);
            return new JSONObject(text);
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static synchronized void saveCookieFromHeader(String setCookie) {
        String cookiePure = extractCookiePairs(setCookie);
        if (cookiePure == null || cookiePure.length() == 0) {
            return;
        }

        String merged = mergeCookies(sCookieString, cookiePure);
        sCookieString = merged;
        SharedPreferencesUtil.putString("cookies", merged);

        String mid = getInfoFromCookie("DedeUserID", merged);
        if (mid != null && mid.length() > 0) {
            try {
                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
            } catch (NumberFormatException e) {}
        }
    }

    private static String extractCookiePairs(String setCookie) {
        if (setCookie == null) return "";
        StringBuffer sb = new StringBuffer();
        String[] parts = setCookie.split(";");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.indexOf("=") != -1 &&
                    !part.startsWith("Path") &&
                    !part.startsWith("Domain") &&
                    !part.startsWith("Expires") &&
                    !part.startsWith("Secure") &&
                    !part.startsWith("HttpOnly") &&
                    !part.startsWith("SameSite")) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private static String cleanCookieString(String cookie) {
        if (cookie == null || cookie.length() == 0) return "";
        StringBuffer sb = new StringBuffer();
        String[] pairs = cookie.split("; ");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i].trim();
            if (pair.indexOf("=") != -1 && !isSetCookieAttribute(pair)) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(pair);
            }
        }
        return sb.toString();
    }

    private static boolean isSetCookieAttribute(String part) {
        if (part == null) return false;
        String lower = part.toLowerCase();
        return lower.startsWith("path") ||
                lower.startsWith("domain") ||
                lower.startsWith("expires") ||
                lower.startsWith("secure") ||
                lower.startsWith("httponly") ||
                lower.startsWith("samesite") ||
                lower.startsWith("max-age") ||
                lower.startsWith("comment") ||
                lower.startsWith("discard");
    }

    /**
     * 获取 buvid3（设备标识）
     * 需要先请求 B站 首页，从 Set-Cookie 中提取
     */
    private static String randomHex(int len) {
        java.util.Random rnd = new java.util.Random();
        String hex = "0123456789abcdef";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < len; i++) {
            sb.append(hex.charAt(rnd.nextInt(hex.length())));
        }
        return sb.toString();
    }

    public static synchronized String fetchBuvid3() {
        try {
            String url = "https://www.bilibili.com";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT_WEB);
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(false);
            conn.connect();

            Map<String, List<String>> headerFields = conn.getHeaderFields();
            List<String> setCookies = headerFields.get("Set-Cookie");
            conn.disconnect();

            if (setCookies != null) {
                StringBuffer allCookies = new StringBuffer();
                for (int i = 0; i < setCookies.size(); i++) {
                    String sc = (String) setCookies.get(i);
                    if (sc != null) {
                        String clean = extractCookiePairs(sc);
                        if (clean != null && clean.length() > 0) {
                            if (allCookies.length() > 0) allCookies.append("; ");
                            allCookies.append(clean);
                        }
                        if (sc.contains("bili_ticket") || sc.contains("sid=") || sc.contains("DedeUserID__ckMd5")) {
                            Log.d("NetWorkUtil", "首页Set-Cookie关键: " + extractCookiePairs(sc));
                        }
                    }
                }
                String merged = allCookies.toString();
                if (merged.length() > 0) {
                    Log.d("NetWorkUtil", "首页Set-Cookie全部: " + merged);
                    String existing = SharedPreferencesUtil.getString("cookies", "");
                    String newCookie = mergeCookies(existing, merged);
                    SharedPreferencesUtil.putString("cookies", newCookie);
                    setCookieString(newCookie);
                    // 补充浏览器必备 cookie
                    ensureBrowserCookies();
                    String buvid3 = getInfoFromCookie("buvid3", merged);
                    if (buvid3 != null && buvid3.length() > 0) {
                        Log.d("NetWorkUtil", "获取到 buvid3: " + buvid3);
                        return buvid3;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("NetWorkUtil", "获取 buvid3 失败: " + e.getMessage());
        }
        return null;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {}
        }
    }

    // 工具方法

    public static byte[] readStream(InputStream inStream) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        outStream.close();
        inStream.close();
        return outStream.toByteArray();
    }

    public static class FormData {
        private final Map data;
        private boolean isUrlParam;

        public FormData() {
            data = new HashMap();
        }

        public FormData remove(String key) {
            data.remove(key);
            return this;
        }

        public FormData put(String key, Object value) {
            data.put(key, String.valueOf(value));
            return this;
        }

        public FormData setUrlParam(boolean isUrlParam) {
            this.isUrlParam = isUrlParam;
            return this;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            if (isUrlParam) {
                sb.append("?");
            }
            try {
                for (Object o : data.keySet()) {
                    String key = (String) o;
                    if (sb.length() > (isUrlParam ? 1 : 0)) {
                        sb.append("&");
                    }
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode((String) data.get(key), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e.getMessage());
            }
            return sb.toString();
        }
    }
}