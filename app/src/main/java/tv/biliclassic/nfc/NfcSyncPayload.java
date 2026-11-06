package tv.biliclassic.nfc;

import android.net.Uri;
import android.util.Base64;

public final class NfcSyncPayload {

    public static final String SCHEME = "biliclassic";
    public static final String HOST = "sync";

    private NfcSyncPayload() {
    }

    public static String detail(long aid, int part) {
        return build("detail", aid, 0, part, 0);
    }

    public static String player(long aid, long cid, int part, long positionMs) {
        return build("player", aid, cid, part, positionMs);
    }

    public static String login(String cookies, String refreshToken) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(SCHEME).authority(HOST);
        builder.appendQueryParameter("from", "login");
        try {
            builder.appendQueryParameter("cookie", Base64.encodeToString(
                    cookies.getBytes("UTF-8"), Base64.NO_WRAP));
            if (refreshToken != null && refreshToken.length() > 0) {
                builder.appendQueryParameter("rt", Base64.encodeToString(
                        refreshToken.getBytes("UTF-8"), Base64.NO_WRAP));
            }
        } catch (Throwable ignored) {
            return null;
        }
        return builder.build().toString();
    }

    public static String decodeBase64(String value) {
        try {
            return new String(Base64.decode(value, Base64.NO_WRAP), "UTF-8");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String build(String from, long aid, long cid, int part, long positionMs) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(SCHEME).authority(HOST);
        builder.appendQueryParameter("from", from);
        builder.appendQueryParameter("aid", String.valueOf(aid));
        if (cid > 0) {
            builder.appendQueryParameter("cid", String.valueOf(cid));
        }
        if (part > 0) {
            builder.appendQueryParameter("p", String.valueOf(part));
        }
        if (positionMs > 0) {
            builder.appendQueryParameter("t", String.valueOf(positionMs));
        }
        return builder.build().toString();
    }

    public static boolean isSyncUri(Uri uri) {
        return uri != null && SCHEME.equals(uri.getScheme()) && HOST.equals(uri.getHost());
    }

    public static String getString(Uri uri, String key, String fallback) {
        String value = getParameter(uri, key);
        return value == null || value.length() == 0 ? fallback : value;
    }

    public static long getLong(Uri uri, String key, long fallback) {
        try {
            String value = getParameter(uri, key);
            return value == null || value.length() == 0 ? fallback : Long.parseLong(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static int getInt(Uri uri, String key, int fallback) {
        try {
            String value = getParameter(uri, key);
            return value == null || value.length() == 0 ? fallback : Integer.parseInt(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String getParameter(Uri uri, String key) {
        String query = uri == null ? null : uri.getQuery();
        if (query == null || query.length() == 0) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            if (!key.equals(Uri.decode(name))) continue;
            return eq >= 0 ? Uri.decode(pair.substring(eq + 1)) : "";
        }
        return null;
    }
}
