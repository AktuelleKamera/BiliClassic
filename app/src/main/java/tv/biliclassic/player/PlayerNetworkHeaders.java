package tv.biliclassic.player;

import android.content.Context;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import tv.biliclassic.ProxyStreamService;
import tv.biliclassic.util.CookieGenerator;
import tv.biliclassic.util.NetWorkUtil;

public final class PlayerNetworkHeaders {

    private PlayerNetworkHeaders() {
    }

    public static Map<String, String> build() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Referer", "https://www.bilibili.com/");
        headers.put("User-Agent", NetWorkUtil.USER_AGENT_WEB);
        String cookie = CookieGenerator.getCookieString(true);
        if (cookie != null && cookie.length() > 0) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    public static void apply(HttpURLConnection conn) {
        if (conn == null) return;
        for (Map.Entry<String, String> entry : build().entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        if (conn instanceof HttpsURLConnection) {
            SSLSocketFactory sslFactory = NetWorkUtil.getTrustAllSSLSocketFactory();
            if (sslFactory != null) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
            }
            ((HttpsURLConnection) conn).setHostnameVerifier(
                    NetWorkUtil.TRUST_ALL_HOSTNAMES);
        }
    }

    public static String startExternalProxy(Context context, String url) {
        try {
            return ProxyStreamService.startProxyForExternal(context, url, build());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
