package tv.biliclassic.util;

import android.util.Log;

import org.json.JSONObject;

/**
 * 转码播放取地址工具：把原始视频地址交给 SCF 转成 240P H.264 Baseline，
 * 供 VideoDetailFragment / Ostwind / BiliPlayer 共用（取地址阶段要显示在小电视加载动画里）。
 *
 * 注意：转码是同步长耗时（几百秒），必须在后台线程调用。
 */
public class ConvertPlayUtil {
    private static final String TAG = "ConvertPlay";

    private static final String KEY_INSTALL_ID = "convert_install_id";
    private static final String KEY_TOKEN = "convert_token";

    private static String sToken;
    private static boolean sTokenLoaded;
    private static boolean sTooLarge;

    /** AUTH_MASKED = "da2e0efd51c3d601f629e9e878aa5c3218419d5aeb459c6355c4f1674b3bb2de" 逐字符 XOR 0x5A，
        避免密钥明文直接出现在 dex 字符串里（仅防随手窃取，非强加密）。 */
    private static final int[] AUTH_MASKED = {
        62,59,104,63,106,63,60,62,111,107,57,105,62,108,106,107,60,108,104,99,63,99,63,98,
        109,98,59,59,111,57,105,104,107,98,110,107,99,62,111,59,63,56,110,111,99,57,108,105,
        111,111,57,110,60,107,108,109,110,56,105,56,56,104,62,63
    };

    private static String sAuthSecret;

    private static String getAuthSecret() {
        if (sAuthSecret == null) {
            char[] c = new char[AUTH_MASKED.length];
            for (int i = 0; i < AUTH_MASKED.length; i++) {
                c[i] = (char) (AUTH_MASKED[i] ^ 0x5A);
            }
            sAuthSecret = new String(c);
        }
        return sAuthSecret;
    }

    /** HmacSHA1(secret, message) 的十六进制串；失败返回 null（上层回退原地址）。 */
    private static String hmacSha1Hex(String message) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    getAuthSecret().getBytes("UTF-8"), "HmacSHA1"));
            byte[] raw = mac.doFinal(message.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                int v = b & 0xff;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 转码播放是否开启且设备符合条件（安卓 5.0 以下且无 NEON：NEON 能硬解/软解原画质，不需要转码） */
    public static boolean isConvertEnabled() {
        return tv.biliclassic.SettingsActivity.isConvertPlayEnabled()
                && tv.biliclassic.util.SdkHelper.getSdkInt() < 21
                && !tv.biliclassic.util.DeviceInfoUtil.hasNeon();
    }

    /**
     * 转码播放：开启且系统<5.0 时把地址交给 SCF 转成 240P；失败则回退原地址。
     * 必须在后台线程调用。
     */
    public static String convertPlayUrl(String rawUrl, String name) {
        if (rawUrl == null || rawUrl.length() == 0) return rawUrl;
        if (!isConvertEnabled()) return rawUrl;
        String conv = fetchTranscodedUrl(rawUrl, name);
        return (conv != null && conv.length() > 0) ? conv : rawUrl;
    }

    /**
     * 调 SCF /transcode 转码，返回可播放的 http 地址；失败返回 null。
     * 第一次会先 /register 换每设备 token（缓存在 SharedPreferences）。
     * 必须在后台线程调用。
     */
    public static String fetchTranscodedUrl(String srcUrl, String name) {
        sTooLarge = false;
        String token = getCachedToken();
        if (token == null) {
            token = registerToken();
        }
        if (token == null) return null;
        String api = tv.biliclassic.SettingsActivity.getConvertPlayApiUrl();
        String url = doTranscode(api, srcUrl, token);
        if (url != null) return url;
        // 源视频过大已被服务端拒收：直接回退原画质，不再重试
        if (sTooLarge) return null;
        // token 可能已被服务端作废：清掉重新注册再试一次
        sToken = null;
        sTokenLoaded = false;
        SharedPreferencesUtil.putString(KEY_TOKEN, "");
        String token2 = registerToken();
        if (token2 == null) return null;
        return doTranscode(api, srcUrl, token2);
    }

    private static String getCachedToken() {
        if (!sTokenLoaded) {
            sTokenLoaded = true;
            sToken = SharedPreferencesUtil.getString(KEY_TOKEN, "");
        }
        return (sToken != null && sToken.length() > 0) ? sToken : null;
    }

    private static String doTranscode(String api, String srcUrl, String token) {
        try {
            JSONObject body = new JSONObject();
            body.put("bucket", tv.biliclassic.SettingsActivity.getConvertPlayBucket());
            body.put("region", tv.biliclassic.SettingsActivity.getConvertPlayRegion());
            body.put("src_url", srcUrl);
            body.put("token", token);
            // 不传 name：让服务端按 src_url 文件名(含 cid)生成唯一 out_key，
            // 避免不同视频都叫 video.mp4 互相覆盖
            String[] resp = httpPostJson(api, body);
            int code = Integer.parseInt(resp[0]);
            if (code >= 200 && code < 300) {
                JSONObject res = new JSONObject(resp[1]);
                String url = res.optString("url", "");
                if (url.length() > 0) {
                    // G1 等老设备无法校验 HTTPS 证书，COS 输出地址强制转成 http 才能在线流播
                    if (url.startsWith("https://")) {
                        url = "http://" + url.substring("https://".length());
                    }
                    Log.e(TAG, "转码成功: " + url);
                    return url;
                }
            } else {
                Log.e(TAG, "转码失败 code=" + code + " body=" + resp[1]);
                // 源视频超过 100MB：服务端拒收，提示用户并直接回退原画质
                if (resp[1] != null && resp[1].contains("过长")) {
                    sTooLarge = true;
                    String hint = resp[1];
                    try {
                        hint = new JSONObject(resp[1]).optString("error", resp[1]);
                    } catch (Exception e) {
                    }
                    showToast(hint);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "转码异常", e);
        }
        return null;
    }

    /** /register 用全局密钥签名换每设备 token；同一设备返回同一 token。失败返回 null。 */
    private static String registerToken() {
        String installId = getInstallId();
        // 老设备系统时钟可能严重错误（如 G1 电池断电导致时钟回到 1970/1980），
        // 直接用本机时间签名会被服务端判为“签名过期”。先取服务器时钟算 ts。
        String ts = getServerTs();
        if (ts == null) {
            ts = Long.toString(System.currentTimeMillis() / 1000L);
        }
        String sig = hmacSha1Hex(ts + "|" + installId);
        try {
            JSONObject body = new JSONObject();
            body.put("bucket", tv.biliclassic.SettingsActivity.getConvertPlayBucket());
            body.put("region", tv.biliclassic.SettingsActivity.getConvertPlayRegion());
            body.put("install_id", installId);
            body.put("ts", ts);
            if (sig != null) body.put("sig", sig);
            String[] resp = httpPostJson(
                    tv.biliclassic.SettingsActivity.getConvertPlayApiBase() + "/register", body);
            int code = Integer.parseInt(resp[0]);
            if (code >= 200 && code < 300) {
                JSONObject res = new JSONObject(resp[1]);
                String token = res.optString("token", "");
                if (token.length() > 0) {
                    SharedPreferencesUtil.putString(KEY_TOKEN, token);
                    sToken = token;
                    sTokenLoaded = true;
                    Log.e(TAG, "注册成功 install=" + installId);
                    return token;
                }
            } else {
                Log.e(TAG, "注册失败 code=" + code + " body=" + resp[1]);
            }
        } catch (Exception e) {
            Log.e(TAG, "注册异常", e);
        }
        return null;
    }

    /** GET /health 取服务器秒级时间戳，用于老设备时钟错误时签名。失败返回 null。 */
    private static String getServerTs() {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL u = new java.net.URL(
                    tv.biliclassic.SettingsActivity.getConvertPlayApiBase() + "/health");
            conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            if (code == 200) {
                java.io.InputStream is = conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject o = new JSONObject(sb.toString());
                long ts = o.optLong("ts", 0);
                if (ts > 0) return Long.toString(ts);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取服务器时间失败", e);
        } finally {
            try {
                if (conn != null) conn.disconnect();
            } catch (Throwable t) {
            }
        }
        return null;
    }

    /** POST JSON，返回 [状态码, 响应体]。转码读超时按函数最长耗时放宽。 */
    private static String[] httpPostJson(String url, JSONObject body) throws Exception {
        java.net.URL u = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(900000);
        java.io.OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        int code = conn.getResponseCode();
        java.io.InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        conn.disconnect();
        return new String[]{Integer.toString(code), sb.toString()};
    }

    /** 主线程弹提示（供后台线程调用）。 */
    private static void showToast(final String msg) {
        try {
            final android.content.Context ctx = tv.biliclassic.util.SharedPreferencesUtil.getAppContext();
            if (ctx == null) return;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    try {
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show();
                    } catch (Throwable t) {
                    }
                }
            });
        } catch (Throwable t) {
        }
    }

    private static String getInstallId() {
        String id = SharedPreferencesUtil.getString(KEY_INSTALL_ID, "");
        if (id.length() == 0) {
            id = randomHex(16);
            SharedPreferencesUtil.putString(KEY_INSTALL_ID, id);
        }
        return id;
    }

    private static String randomHex(int bytes) {
        try {
            java.security.SecureRandom r = java.security.SecureRandom.getInstance("SHA1PRNG");
            byte[] b = new byte[bytes];
            r.nextBytes(b);
            StringBuilder sb = new StringBuilder(bytes * 2);
            for (byte x : b) {
                int v = x & 0xff;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return Long.toHexString(System.currentTimeMillis()) + Long.toHexString(System.nanoTime());
        }
    }
}
