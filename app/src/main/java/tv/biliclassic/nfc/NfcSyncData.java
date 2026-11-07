package tv.biliclassic.nfc;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

import tv.biliclassic.SettingsActivity;
import tv.biliclassic.util.LocalFavoriteStore;
import tv.biliclassic.util.LocalHistoryStore;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public final class NfcSyncData {

    public static final int FAVORITES = 1;
    public static final int HISTORY = 2;
    public static final int DANMAKU = 4;
    public static final int QUALITY = 8;
    public static final int LANGUAGE = 16;
    public static final int DIALOG_STYLE = 32;
    public static final int MODERN_MODE = 64;
    public static final int IMAGE_THREADS = 128;
    public static final int LOGIN = 256;
    public static final int ALL = FAVORITES | HISTORY | DANMAKU | QUALITY
            | LANGUAGE | DIALOG_STYLE | MODERN_MODE | IMAGE_THREADS | LOGIN;

    private static final String QUALITY_DASH = "video_quality_dash";
    private static final String LANGUAGE_KEY = "app_locale";
    private static final String MODERN_MODE_KEY = "modern_mode";

    private NfcSyncData() {
    }

    public static String build(int mask) {
        JSONObject root = new JSONObject();
        try {
            if ((mask & FAVORITES) != 0) {
                String favorites = LocalFavoriteStore.exportData();
                if (favorites != null && favorites.length() > 0) {
                    root.put("favorites", new JSONObject(favorites));
                }
            }
            if ((mask & HISTORY) != 0) {
                String history = LocalHistoryStore.exportData();
                if (history != null && history.length() > 0) {
                    root.put("history", new JSONArray(history));
                }
            }
            if ((mask & LOGIN) != 0) {
                String cookies = SharedPreferencesUtil.getString(
                        SharedPreferencesUtil.cookies, "");
                if (cookies != null && cookies.length() > 0) {
                    JSONObject login = new JSONObject();
                    login.put("cookies", cookies);
                    login.put("refresh_token", SharedPreferencesUtil.getString(
                            SharedPreferencesUtil.refresh_token, ""));
                    root.put("login", login);
                }
            }
            JSONObject prefs = exportPrefs(mask);
            if (prefs.length() > 0) {
                root.put("prefs", prefs);
            }
        } catch (Throwable ignored) {
            return null;
        }
        return root.toString();
    }

    public static void apply(String json) {
        apply(json, ALL);
    }

    public static void apply(String json, int mask) {
        if (json == null || json.length() == 0) return;
        try {
            JSONObject root = new JSONObject(json);
            if ((mask & FAVORITES) != 0) {
                JSONObject favorites = root.optJSONObject("favorites");
                if (favorites != null) {
                    LocalFavoriteStore.importData(favorites.toString());
                }
            }
            if ((mask & HISTORY) != 0) {
                JSONArray history = root.optJSONArray("history");
                if (history != null) {
                    LocalHistoryStore.importData(history.toString());
                }
            }
            if ((mask & LOGIN) != 0) {
                JSONObject login = root.optJSONObject("login");
                if (login != null) {
                    applyLogin(login.optString("cookies", ""),
                            login.optString("refresh_token", ""));
                }
            }
            if ((mask & (DANMAKU | QUALITY | LANGUAGE | DIALOG_STYLE
                    | MODERN_MODE | IMAGE_THREADS)) != 0) {
                JSONObject prefs = root.optJSONObject("prefs");
                if (prefs != null) {
                    applyPrefs(prefs, mask);
                }
            }
            if ((mask & LANGUAGE) != 0) {
                tv.biliclassic.util.LocaleHelper.setCurrentLocale(
                        SharedPreferencesUtil.getString(LANGUAGE_KEY, "zh_CN"));
            }
            if ((mask & IMAGE_THREADS) != 0) {
                tv.biliclassic.util.ImageLoader.reloadExecutor();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void applyLogin(String cookies, String refreshToken) {
        String normalized = NetWorkUtil.boundCookie(cookies);
        if (normalized == null || normalized.length() == 0) return;
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, normalized);
        String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", normalized);
        if (mid != null && mid.length() > 0) {
            try {
                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,
                        Long.parseLong(mid));
            } catch (Throwable ignored) {
            }
        }
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", normalized);
        if (csrf != null && csrf.length() > 0) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);
        }
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token,
                refreshToken == null ? "" : refreshToken);
        NetWorkUtil.setCookieString(normalized);
        NetWorkUtil.refreshHeaders();
        NetWorkUtil.syncLoginState();
    }

    private static JSONObject exportPrefs(int mask) {
        JSONObject out = new JSONObject();
        SharedPreferences prefs = SharedPreferencesUtil.getSharedPreferences();
        if (prefs == null) return out;
        Map<String, ?> all = prefs.getAll();
        if (all == null) return out;
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            boolean danmaku = key.startsWith("danmaku_");
            boolean quality = SettingsActivity.KEY_VIDEO_QUALITY.equals(key)
                    || QUALITY_DASH.equals(key);
            boolean language = LANGUAGE_KEY.equals(key);
            boolean dialogStyle = SharedPreferencesUtil.DIALOG_STYLE.equals(key);
            boolean modern = MODERN_MODE_KEY.equals(key);
            boolean imageThreads = SharedPreferencesUtil.IMAGE_LOAD_THREADS.equals(key);
            if ((!danmaku || (mask & DANMAKU) == 0)
                    && (!quality || (mask & QUALITY) == 0)
                    && (!language || (mask & LANGUAGE) == 0)
                    && (!dialogStyle || (mask & DIALOG_STYLE) == 0)
                    && (!modern || (mask & MODERN_MODE) == 0)
                    && (!imageThreads || (mask & IMAGE_THREADS) == 0)) {
                continue;
            }
            try {
                out.put(key, entry.getValue());
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static void applyPrefs(JSONObject prefs, int mask) {
        Iterator<String> keys = prefs.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            boolean danmaku = key.startsWith("danmaku_");
            boolean quality = SettingsActivity.KEY_VIDEO_QUALITY.equals(key)
                    || QUALITY_DASH.equals(key);
            boolean language = LANGUAGE_KEY.equals(key);
            boolean dialogStyle = SharedPreferencesUtil.DIALOG_STYLE.equals(key);
            boolean modern = MODERN_MODE_KEY.equals(key);
            boolean imageThreads = SharedPreferencesUtil.IMAGE_LOAD_THREADS.equals(key);
            if ((danmaku && (mask & DANMAKU) == 0)
                    || (quality && (mask & QUALITY) == 0)
                    || (language && (mask & LANGUAGE) == 0)
                    || (dialogStyle && (mask & DIALOG_STYLE) == 0)
                    || (modern && (mask & MODERN_MODE) == 0)
                    || (imageThreads && (mask & IMAGE_THREADS) == 0)
                    || (!danmaku && !quality && !language && !dialogStyle
                            && !modern && !imageThreads)) {
                continue;
            }
            Object value = prefs.opt(key);
            if (value == null) continue;
            if (value instanceof Boolean) {
                SharedPreferencesUtil.putBoolean(key, ((Boolean) value).booleanValue());
            } else if (value instanceof Integer) {
                SharedPreferencesUtil.putInt(key, ((Integer) value).intValue());
            } else if (value instanceof Long) {
                SharedPreferencesUtil.putLong(key, ((Long) value).longValue());
            } else if (value instanceof Float || value instanceof Double) {
                SharedPreferencesUtil.putFloat(key, ((Number) value).floatValue());
            } else {
                SharedPreferencesUtil.putString(key, String.valueOf(value));
            }
        }
    }
}
