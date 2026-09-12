package tv.biliclassic.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import javax.crypto.Cipher;

/**
 * app 端短信验证码登录（实现参考 PiliPlus LoginHttp/AppSign/LoginUtils）。
 * <p>
 * 为什么要走 app 端：web 端短信接口返回 -105 时给的人机验证是 web 场景 gt（文字点选），
 * 而 app 端（-105 的 data.recaptcha_url 或 /x/safecenter/captcha/pre）给的是 app 场景 gt（滑动验证）。
 * <p>
 * 发送：POST /x/passport-login/sms/send（AppSign 签名）
 * 登录：POST /x/passport-login/login/sms（AppSign + dt=RSA(随机16位)，凭证在 data.cookie_info.cookies）
 * <p>
 * 移植到 BiliClassic：minSdk 1，不用 android.util.Base64（API 8 才有），改用纯 Java Base64。
 */
public class BiliAppLogin {

    public static final String APP_KEY = "dfca71928277209b";
    public static final String APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e";
    public static final String APP_UA =
            "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd"
                    + " mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2";
    public static final String STATISTICS = "{\"appId\":5,\"platform\":3,\"version\":\"2.0.1\",\"abtest\":\"\"}";

    private static final String URL_SMS_SEND = "https://passport.bilibili.com/x/passport-login/sms/send";
    private static final String URL_SMS_LOGIN = "https://passport.bilibili.com/x/passport-login/login/sms";
    private static final String URL_PRE_CAPTCHA = "https://passport.bilibili.com/x/safecenter/captcha/pre";
    private static final String URL_WEB_KEY = "https://passport.bilibili.com/x/passport-login/web/key";

    // ===== 对外结果 =====

    /** 发送验证码结果 */
    public static class SendResult {
        public boolean ok = false;
        public int code = -1;
        public String message = "";
        public String captchaKey = "";
        /** -105 时返回的人机验证地址（含 gee_gt / gee_challenge / recaptcha_token） */
        public String recaptchaUrl = "";
        public String geeGt = "";
        public String geeChallenge = "";
        public String recaptchaToken = "";

        public boolean hasGee() {
            return geeGt != null && geeGt.length() > 0
                    && geeChallenge != null && geeChallenge.length() > 0
                    && recaptchaToken != null && recaptchaToken.length() > 0;
        }
    }

    /** 验证码登录结果 */
    public static class LoginResult {
        public boolean ok = false;
        public int code = -1;
        public String message = "";
        public String cookieString = "";
    }

    // ===== 发送短信验证码 =====

    /** 发送短信验证码；gee 参数为空则先直发，返回 -105 时需要人机验证 */
    public static SendResult sendSms(String tel, String geeChallenge, String geeValidate,
                                     String geeSeccode, String recaptchaToken) {
        SendResult r = new SendResult();
        try {
            long tsMs = System.currentTimeMillis();
            LinkedHashMap<String, String> p = new LinkedHashMap<String, String>();
            p.put("build", "2001100");
            p.put("buvid", buvid());
            p.put("c_locale", "zh_CN");
            p.put("channel", "master");
            p.put("cid", "86");
            p.put("disable_rcmd", "0");
            if (geeChallenge != null && geeChallenge.length() > 0) {
                p.put("gee_challenge", geeChallenge);
            }
            if (geeSeccode != null && geeSeccode.length() > 0) {
                p.put("gee_seccode", geeSeccode);
            }
            if (geeValidate != null && geeValidate.length() > 0) {
                p.put("gee_validate", geeValidate);
            }
            if (recaptchaToken != null && recaptchaToken.length() > 0) {
                p.put("recaptcha_token", recaptchaToken);
            }
            p.put("local_id", buvid());
            p.put("login_session_id", md5Hex(buvid() + tsMs));
            p.put("mobi_app", "android_hd");
            p.put("platform", "android");
            p.put("s_locale", "zh_CN");
            p.put("statistics", STATISTICS);
            p.put("tel", tel);
            p.put("ts", String.valueOf(tsMs / 1000));

            JSONObject j = postForm(URL_SMS_SEND, appSign(p), appHeaders());
            r.code = j.optInt("code", -1);
            r.message = j.optString("message", "");
            if (r.code == 0) {
                r.ok = true;
                JSONObject d = j.optJSONObject("data");
                if (d != null) {
                    r.captchaKey = d.optString("captcha_key", "");
                    r.recaptchaUrl = d.optString("recaptcha_url", "");
                }
                return r;
            }
            // -105：需要人机验证（app 场景 gt → 滑动验证）
            JSONObject d = j.optJSONObject("data");
            if (d != null) {
                r.recaptchaUrl = d.optString("recaptcha_url", "");
            }
            if (r.recaptchaUrl.length() > 0) {
                fillGeeFromUrl(r, r.recaptchaUrl);
            }
        } catch (Throwable e) {
            r.code = -1;
            r.message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return r;
    }

    /** 从 recaptcha_url 解析 gee_gt / gee_challenge / recaptcha_token */
    private static void fillGeeFromUrl(SendResult r, String url) {
        try {
            int q = url.indexOf('?');
            if (q < 0) {
                return;
            }
            String query = url.substring(q + 1);
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = pair.substring(0, eq);
                String v = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                if ("gee_gt".equals(k)) {
                    r.geeGt = v;
                } else if ("gee_challenge".equals(k)) {
                    r.geeChallenge = v;
                } else if ("recaptcha_token".equals(k)) {
                    r.recaptchaToken = v;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 兜底：POST /x/safecenter/captcha/pre 取极验参数（app 场景，滑动验证） */
    public static SendResult preCapture() {
        SendResult r = new SendResult();
        try {
            LinkedHashMap<String, String> p = new LinkedHashMap<String, String>();
            p.put("disable_rcmd", "0");
            JSONObject j = postForm(URL_PRE_CAPTCHA, appSign(p), appHeaders());
            r.code = j.optInt("code", -1);
            r.message = j.optString("message", "");
            if (r.code == 0) {
                JSONObject d = j.optJSONObject("data");
                if (d != null) {
                    r.geeGt = d.optString("gee_gt", "");
                    r.geeChallenge = d.optString("gee_challenge", "");
                    r.recaptchaToken = d.optString("recaptcha_token", "");
                }
            }
        } catch (Throwable e) {
            r.code = -1;
            r.message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return r;
    }

    // ===== 验证码登录 =====

    public static LoginResult loginBySms(String tel, String code, String captchaKey) {
        LoginResult r = new LoginResult();
        try {
            String key = null;
            try {
                JSONObject wk = get(URL_WEB_KEY, appHeaders());
                if (wk.optInt("code", -1) == 0) {
                    JSONObject d = wk.optJSONObject("data");
                    if (d != null) {
                        key = d.optString("key", "");
                    }
                }
            } catch (Throwable ignored) {
            }
            String dt = "";
            if (key != null && key.length() > 0) {
                dt = rsaEncrypt(key, randomString(16));
            }
            LinkedHashMap<String, String> p = new LinkedHashMap<String, String>();
            String deviceId = deviceId();
            p.put("bili_local_id", deviceId);
            p.put("build", "2001100");
            p.put("buvid", buvid());
            p.put("c_locale", "zh_CN");
            p.put("captcha_key", captchaKey == null ? "" : captchaKey);
            p.put("channel", "master");
            p.put("cid", "86");
            p.put("code", code);
            p.put("device", "phone");
            p.put("device_id", deviceId);
            p.put("device_name", "vivo");
            p.put("device_platform", "Android14vivo");
            p.put("disable_rcmd", "0");
            if (dt.length() > 0) {
                p.put("dt", dt);
            }
            p.put("from_pv", "main.my-information.my-login.0.click");
            p.put("from_url", "bilibili://user_center/mine");
            p.put("local_id", buvid());
            p.put("mobi_app", "android_hd");
            p.put("platform", "android");
            p.put("s_locale", "zh_CN");
            p.put("statistics", STATISTICS);
            p.put("tel", tel);

            JSONObject j = postForm(URL_SMS_LOGIN, appSign(p), appHeaders());
            r.code = j.optInt("code", -1);
            r.message = j.optString("message", "");
            if (r.code != 0) {
                return r;
            }
            r.ok = true;
            // 凭证：data.cookie_info.cookies
            StringBuilder sb = new StringBuilder();
            JSONObject d = j.optJSONObject("data");
            if (d != null) {
                JSONObject ci = d.optJSONObject("cookie_info");
                JSONArray arr = ci == null ? null : ci.optJSONArray("cookies");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.optJSONObject(i);
                        if (c == null) {
                            continue;
                        }
                        String n = c.optString("name", "");
                        String v = c.optString("value", "");
                        if (n.length() == 0) {
                            continue;
                        }
                        if (sb.length() > 0) {
                            sb.append("; ");
                        }
                        sb.append(n).append('=').append(v);
                    }
                }
            }
            r.cookieString = sb.toString();
            // 顺便保存 access_token（部分接口需要）
            try {
                JSONObject ti = d == null ? null : d.optJSONObject("token_info");
                if (ti != null) {
                    String at = ti.optString("access_token", "");
                    String rt = ti.optString("refresh_token", "");
                    if (at.length() > 0) {
                        SharedPreferencesUtil.putString("access_key", at);
                    }
                    if (rt.length() > 0) {
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, rt);
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable e) {
            r.code = -1;
            r.message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return r;
    }

    // ===== 设备标识 =====

    /** buvid：优先用本机已有 buvid3，没有则按 PiliPlus 规则生成并持久化 */
    public static String buvid() {
        try {
            String b = SharedPreferencesUtil.getString("buvid3", "");
            if (b != null && b.length() > 0) {
                return b;
            }
            String md5 = md5Hex(randomBytes(16));
            String gen = "XY" + md5.charAt(2) + md5.charAt(12) + md5.charAt(22) + md5;
            SharedPreferencesUtil.putString("buvid3", gen);
            return gen;
        } catch (Throwable e) {
            return "XY" + md5Hex(String.valueOf(System.currentTimeMillis()));
        }
    }

    /** 设备 id（PiliPlus LoginUtils.genDeviceId 同款算法） */
    public static String deviceId() {
        try {
            String saved = SharedPreferencesUtil.getString("app_device_id", "");
            if (saved != null && saved.length() > 0) {
                return saved;
            }
            byte[] bytes = new byte[32];
            Random rnd = new Random();
            for (int i = 0; i < 16; i++) {
                bytes[i] = (byte) rnd.nextInt(256);
            }
            java.util.Calendar c = java.util.Calendar.getInstance();
            bytes[16] = (byte) bcd(c.get(java.util.Calendar.YEAR) / 100);
            bytes[17] = (byte) bcd(c.get(java.util.Calendar.YEAR) % 100);
            bytes[18] = (byte) bcd(c.get(java.util.Calendar.MONTH) + 1);
            bytes[19] = (byte) bcd(c.get(java.util.Calendar.DAY_OF_MONTH));
            bytes[20] = (byte) bcd(c.get(java.util.Calendar.HOUR_OF_DAY));
            bytes[21] = (byte) bcd(c.get(java.util.Calendar.MINUTE));
            bytes[22] = (byte) bcd(c.get(java.util.Calendar.SECOND));
            for (int i = 23; i < 31; i++) {
                bytes[i] = (byte) rnd.nextInt(256);
            }
            int sum = 0;
            for (int i = 0; i < 31; i++) {
                sum += bytes[i] & 0xFF;
            }
            String check = String.format("%02x", sum & 0xFF);
            String id = md5Hex(bytes) + check;
            SharedPreferencesUtil.putString("app_device_id", id);
            return id;
        } catch (Throwable e) {
            return md5Hex(String.valueOf(System.nanoTime()));
        }
    }

    private static int bcd(int dec) {
        return ((dec / 10) << 4) | (dec % 10);
    }

    // ===== 签名 / 加密 / 网络 =====

    /** AppSign：加 appkey/ts → 按 key 字典序排序 → k=v&... + appsec → md5 */
    public static LinkedHashMap<String, String> appSign(LinkedHashMap<String, String> params) {
        params.put("appkey", APP_KEY);
        params.put("ts", String.valueOf(System.currentTimeMillis() / 1000));
        TreeMap<String, String> sorted = new TreeMap<String, String>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey()));
            sb.append('=');
            sb.append(urlEncode(e.getValue()));
        }
        params.put("sign", md5Hex(sb.toString() + APP_SEC));
        return params;
    }

    public static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return toHex(md.digest(s.getBytes("UTF-8")));
        } catch (Throwable e) {
            return "";
        }
    }

    private static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return toHex(md.digest(data));
        } catch (Throwable e) {
            return "";
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            String h = Integer.toHexString(x & 0xFF);
            if (h.length() == 1) {
                sb.append('0');
            }
            sb.append(h);
        }
        return sb.toString();
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new Random().nextBytes(b);
        return b;
    }

    private static String randomString(int n) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** RSA(PKCS1) 加密并 base64（dt 参数）。Base64 用纯 Java 实现，兼容 API < 8。 */
    private static String rsaEncrypt(String pemKey, String plain) {
        try {
            String body = pemKey.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = base64Decode(body);
            PublicKey pub = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.ENCRYPT_MODE, pub);
            return base64Encode(c.doFinal(plain.getBytes("UTF-8")));
        } catch (Throwable e) {
            android.util.Log.w("BiliSms", "rsaEncrypt 失败: " + e.getMessage());
            return "";
        }
    }

    /** app 端请求头 */
    private static List<String> appHeaders() {
        List<String> h = new ArrayList<String>();
        h.add("User-Agent");
        h.add(APP_UA);
        h.add("buvid");
        h.add(buvid());
        h.add("env");
        h.add("prod");
        h.add("app-key");
        h.add("android_hd");
        h.add("x-bili-trace-id");
        h.add("11111111111111111111111111111111:1111111111111111:0:0");
        h.add("Referer");
        h.add("https://www.bilibili.com/");
        return h;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Throwable e) {
            return s == null ? "" : s;
        }
    }

    /** 表单 POST（app 端接口），并把响应 Set-Cookie 合并进本地 Cookie */
    private static JSONObject postForm(String url, LinkedHashMap<String, String> params, List<String> headers)
            throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        NetWorkUtil.applySSLCompat(conn, url);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        if (headers != null) {
            for (int i = 0; i + 1 < headers.size(); i += 2) {
                conn.setRequestProperty(headers.get(i), headers.get(i + 1));
            }
        }
        String ck = NetWorkUtil.getCookieString();
        if (ck != null && ck.length() > 0) {
            conn.setRequestProperty("Cookie", ck);
        }
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();
        InputStream is;
        try {
            is = conn.getInputStream();
        } catch (Throwable e) {
            is = conn.getErrorStream();
        }
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            is.close();
        }
        storeSetCookies(conn);
        String text = sb.toString();
        return new JSONObject(text.length() == 0 ? "{}" : text);
    }

    private static JSONObject get(String url, List<String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        NetWorkUtil.applySSLCompat(conn, url);
        conn.setRequestMethod("GET");
        if (headers != null) {
            for (int i = 0; i + 1 < headers.size(); i += 2) {
                conn.setRequestProperty(headers.get(i), headers.get(i + 1));
            }
        }
        String ck = NetWorkUtil.getCookieString();
        if (ck != null && ck.length() > 0) {
            conn.setRequestProperty("Cookie", ck);
        }
        InputStream is = conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[1024];
        int n;
        while ((n = is.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, "UTF-8"));
        }
        is.close();
        return new JSONObject(sb.toString());
    }

    /** 收集 Set-Cookie 并合并落库（登录凭证可能直接来自 Set-Cookie） */
    private static void storeSetCookies(HttpURLConnection conn) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                String k = e.getKey();
                if (k != null && "Set-Cookie".equalsIgnoreCase(k)) {
                    for (String val : e.getValue()) {
                        if (val == null) {
                            continue;
                        }
                        String part = val.split(";")[0].trim();
                        if (part.indexOf('=') > 0) {
                            if (sb.length() > 0) {
                                sb.append("; ");
                            }
                            sb.append(part);
                        }
                    }
                }
            }
            String sc = sb.toString();
            if (sc.length() > 0) {
                NetWorkUtil.setCookieString(sc);
                SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, NetWorkUtil.getCookieString());
                NetWorkUtil.refreshHeaders();
                NetWorkUtil.syncLoginState();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 把 cookie_info 拼出的登录 Cookie 写入本地存储 */
    public static void storeLoginCookies(String cookieString) {
        if (cookieString == null || cookieString.length() == 0) {
            return;
        }
        NetWorkUtil.setCookieString(cookieString);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, NetWorkUtil.getCookieString());
        NetWorkUtil.refreshHeaders();
        NetWorkUtil.syncLoginState();
    }

    // ===== Base64（纯 Java，兼容 API < 8，避免引用 android.util.Base64 触发 VerifyError） =====

    private static final char[] BASE64_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private static String base64Encode(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = i < data.length ? data[i++] & 0xFF : -1;
            int b2 = i < data.length ? data[i++] & 0xFF : -1;
            sb.append(BASE64_CHARS[b0 >> 2]);
            sb.append(BASE64_CHARS[((b0 & 0x03) << 4) | (b1 == -1 ? 0 : (b1 >> 4))]);
            sb.append(b1 == -1 ? '=' : BASE64_CHARS[((b1 & 0x0F) << 2) | (b2 == -1 ? 0 : (b2 >> 6))]);
            sb.append(b2 == -1 ? '=' : BASE64_CHARS[b2 & 0x3F]);
        }
        return sb.toString();
    }

    private static byte[] base64Decode(String s) {
        if (s == null) return new byte[0];
        s = s.replaceAll("[^A-Za-z0-9+/]", "");
        int len = s.length();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(len * 3 / 4 + 3);
        int buf = 0, bits = 0;
        for (int i = 0; i < len; i++) {
            int v = indexOfChar(s.charAt(i));
            if (v < 0) continue;
            buf = (buf << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buf >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    private static int indexOfChar(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }
}
