package tv.biliclassic.util;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

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

    public static synchronized String getCookieString() {
        return sCookieString;
    }

    public static synchronized void setCookieString(String cookie) {
        if (cookie == null) cookie = "";
        sCookieString = mergeCookies(sCookieString, cookie);
    }

    public static synchronized void refreshHeaders() {
        String cookie = SharedPreferencesUtil.getString("cookies", "");
        if (cookie == null) cookie = "";
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
     */
    public static synchronized String getInfoFromCookie(String name, String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return "";
        }
        Map map = parseCookieMap(cookie);
        String value = (String) map.get(name);
        if (value == null || value.length() == 0) {
            return "";
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
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
        if (headers != null) {
            Map headerMap = listToMap(headers);
            for (Iterator it = headerMap.keySet().iterator(); it.hasNext(); ) {
                String key = (String) it.next();
                String value = (String) headerMap.get(key);
                if (key != null && value != null) {
                    conn.setRequestProperty(key, value);
                }
            }
        }

        // Cookie 处理
        String cookie = getCookieString();
        if (cookie == null || cookie.length() == 0) {
            cookie = SharedPreferencesUtil.getString("cookies", "");
            if (cookie != null && cookie.length() > 0) {
                setCookieString(cookie);
            }
        }
        if (cookie != null && cookie.length() > 0) {
            conn.setRequestProperty("Cookie", cookie);
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

        java.io.CharArrayWriter caw = new java.io.CharArrayWriter();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        char[] buffer = new char[4096];
        int len;
        while ((len = reader.read(buffer, 0, buffer.length)) != -1) {
            caw.write(buffer, 0, len);
        }
        reader.close();
        is.close();

        String result = caw.toString();
        caw.close();

        String setCookie = conn.getHeaderField("Set-Cookie");
        if (setCookie != null && setCookie.length() > 0) {
            saveCookieFromHeader(setCookie);
        }

        return result;
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