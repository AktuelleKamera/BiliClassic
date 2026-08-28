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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 被 luern0313 创建于 2020/5/4.
 * #以下代码部分来源于腕上哔哩的开源项目，有修改。感谢开源者做出的贡献！
 * 移植到 BiliClassic
 */
public class SharedPreferencesUtil {
    // 常量定义
    public static final String LINK_ENABLE = "link_enable";
    public static final String RCMD_API_NEW_PARAM = "rcmd_api_new_param";
    public static final String MENU_SORT = "menu_sort";
    public static final String ASYNC_INFLATE_ENABLE = "async_inflate_enable";
    public static final String LOAD_TRANSITION = "load_transition";
    public static final String SNACKBAR_ENABLE = "snackbar_enable";
    public static final String STRICT_URL_MATCH = "strict_url_match";
    public static final String NO_VIP_COLOR = "no_vip_color";
    public static final String NO_MEDAL = "no_medal";
    public static final String REPLY_MARQUEE_NAME = "reply_marquee_name";
    public static final String NIGHT_MODE = "night_mode";

    public static final String cookies = "cookies";
    public static final String mid = "mid";
    public static final String csrf = "csrf";
    public static final String access_key = "access_key";
    public static final String refresh_token = "refresh_token";
    public static final String setup = "setup";
    public static final String last_version = "last_version";
    public static final String player = "player";
    public static final String padding_horizontal = "padding_horizontal";
    public static final String padding_vertical = "padding_vertical";
    public static final String cookie_refresh = "cookie_refresh";
    public static final String search_history = "search_history";
    public static final String cover_play_enabled = "cover_play_enabled";
    public static final String tutorial_version = "tutorial_version";
    public static final String IMAGE_LOAD_THREADS = "image_load_threads";
    public static final String PLAYER_PREFERENCE = "player_preference";
    public static final String KEEP_BACKGROUND = "keep_background";
    public static final String PLAYER_AUTO_ROTATION = "player_auto_rotation";
    public static final String PLAYER_PORTRAIT_ROTATION = "player_portrait_rotation";
    public static final String DANMAKU_ENGINE_MODE = "danmaku_engine_mode"; // 0=完整, 1=简易
    public static final String RENDERER_TYPE = "renderer_type"; // 0=SurfaceView, 1=TextureView
    public static final String COMPLETION_ACTION = "completion_action"; // 0=loop,1=next,2=next_loop,3=pause,4=exit
    public static final String NO_IMAGE_MODE = "no_image_mode";
    public static final String PRIVACY_MODE = "privacy_mode";
    public static final String INCOGNITO_MODE = "incognito_mode";
    public static final String ENABLE_GESTURE = "player_enable_gesture";
    public static final String COMMENT_SWIPE_ENABLE = "player_comment_swipe";
    public static final String DOWNLOAD_FORMAT = "download_format"; // "mp4" 或 "original"
    public static final String DIALOG_STYLE = "dialog_style";
    public static final String PLAY_STREAM_FORMAT = "play_stream_format"; // 1=MP4, 16=DASH
    public static final String ROUND_SCREEN_CENTER = "round_screen_center";

    // CookieGenerator 需要的 key
    public static final String BUVid3 = "buvid3";
    public static final String BUVid4 = "buvid4";
    public static final String BILI_TICKET = "bili_ticket";
    public static final String BILI_TICKET_EXPIRES = "bili_ticket_expires";
    public static final String UUID = "_uuid";
    public static final String B_LSID = "b_lsid";
    public static final String BUVid_FP = "buvid_fp";
    public static final String B_NUT = "b_nut";

    private static final String TAG = "SharedPreferencesUtil";
    private static final String PROBE_KEY = "__probe__";

    private static SharedPreferences sharedPreferences;
    private static Context sAppContext;

    public static void init(Context context) {
        sAppContext = context.getApplicationContext();
        if (sharedPreferences == null) {
            SharedPreferences internal = sAppContext.getSharedPreferences("biliclassic", Context.MODE_PRIVATE);
            if (probeWritable(internal)) {
                // 内部存储正常：清掉探测残留，用系统实现
                try {
                    internal.edit().remove(PROBE_KEY).commit();
                } catch (Throwable t) {
                }
                sharedPreferences = internal;
            } else {
                // 内部存储无法写入（如 /data 分区满），尝试退回 SD 卡
                Log.w(TAG, "内部存储不可写，尝试 SD 卡回退");
                SharedPreferences sd = createSdPreferences();
                if (sd != null && probeWritable(sd)) {
                    sharedPreferences = sd;
                } else {
                    // SD 也不可用：退回内部存储尽力而为（偏好设置可能无法持久化，但不再刷 SD 错误）
                    Log.w(TAG, "SD 卡回退不可用，继续使用内部存储（偏好设置可能无法持久化）");
                    sharedPreferences = internal;
                }
            }
        }
    }

    /**
     * 探测内部存储是否真的可写：shared_prefs 目录建不了/空间不足时，
     * commit() 会返回 false。
     */
    private static boolean probeWritable(SharedPreferences prefs) {
        try {
            return prefs.edit().putBoolean(PROBE_KEY, true).commit();
        } catch (Throwable t) {
            return false;
        }
    }

    private static SharedPreferences createSdPreferences() {
        try {
            // 先确认 SD 卡确实可写，避免在不支持外部存储的设备上无谓地抛 FileNotFoundException
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
                Log.w(TAG, "SD 卡不可用（state=" + state + "），跳过 SD 偏好设置回退");
                return null;
            }
            File dir = new File(Environment.getExternalStorageDirectory(), "BiliClassic");
            File f = new File(dir, "prefs.properties");
            return new SdSharedPreferences(f);
        } catch (Throwable t) {
            Log.e(TAG, "SD 卡偏好设置创建失败", t);
            return null;
        }
    }

    public static SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public static Context getAppContext() {
        return sAppContext;
    }

    public static SharedPreferences getDefaultSharedPreferences() {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(sAppContext);
    }

    public static boolean contains(String key) {
        if (sharedPreferences == null) return false;
        return sharedPreferences.contains(key);
    }

    public static String getString(String key, String def) {
        if (sharedPreferences == null) return def;
        return sharedPreferences.getString(key, def);
    }

    public static void putString(String key, String value) {
        if (sharedPreferences == null) return;
        sharedPreferences.edit().putString(key, value).commit();
    }

    public static int getInt(String key, int def) {
        if (sharedPreferences == null) return def;
        return sharedPreferences.getInt(key, def);
    }

    public static void putInt(String key, int value) {
        if (sharedPreferences == null) return;
        sharedPreferences.edit().putInt(key, value).commit();
    }

    public static long getLong(String key, long def) {
        if (sharedPreferences == null) return def;
        return sharedPreferences.getLong(key, def);
    }

    public static void putLong(String key, long value) {
        if (sharedPreferences == null) return;
        sharedPreferences.edit().putLong(key, value).commit();
    }

    public static boolean getBoolean(String key, boolean def) {
        if (sharedPreferences == null) return def;
        return sharedPreferences.getBoolean(key, def);
    }

    public static void putBoolean(String key, boolean value) {
        if (sharedPreferences == null) return;
        sharedPreferences.edit().putBoolean(key, value).commit();
    }

    public static void putFloat(String key, float value) {
        if (sharedPreferences == null) return;
        sharedPreferences.edit().putFloat(key, value).commit();
    }

    public static float getFloat(String key, float def) {
        if (sharedPreferences == null) return def;
        return sharedPreferences.getFloat(key, def);
    }

    public static void removeValue(String key) {
        if (sharedPreferences == null) return;
        sharedPreferences.edit().remove(key).commit();
    }

    // ===== SD 卡文件背书的 SharedPreferences（内部存储满时的回退） =====

    /**
     * 用 Properties 文件持久化的极简 SharedPreferences。
     * 仅当 /data 分区无法写入（G1 这类小存储设备）时启用，
     * 保证设置/登录态/转码开关等在空间不足时仍能保存。
     */
    private static class SdSharedPreferences implements SharedPreferences {
        private final File mFile;
        private final Properties mProps = new Properties();
        private final Map<String, Object> mValues = new HashMap<String, Object>();
        private final Map<OnSharedPreferenceChangeListener, Boolean> mListeners =
                new HashMap<OnSharedPreferenceChangeListener, Boolean>();

        SdSharedPreferences(File file) {
            mFile = file;
            load();
        }

        private synchronized void load() {
            mValues.clear();
            try {
                if (mFile.exists()) {
                    FileInputStream fis = new FileInputStream(mFile);
                    mProps.load(fis);
                    fis.close();
                }
                Enumeration<?> en = mProps.propertyNames();
                while (en.hasMoreElements()) {
                    String key = (String) en.nextElement();
                    Object v = decode(key, mProps.getProperty(key));
                    if (v != null) mValues.put(key, v);
                }
            } catch (Throwable t) {
                Log.e(TAG, "SD prefs load error", t);
            }
        }

        private synchronized boolean persist() {
            try {
                File dir = mFile.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(mFile);
                mProps.store(fos, "biliclassic prefs (sd fallback)");
                fos.close();
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "SD prefs save error", t);
                return false;
            }
        }

        private String encode(String key, Object value) {
            if (value instanceof Integer) return "@I:" + value;
            if (value instanceof Long) return "@L:" + value;
            if (value instanceof Boolean) return "@B:" + value;
            if (value instanceof Float) return "@F:" + value;
            if (value instanceof Set) {
                StringBuilder sb = new StringBuilder("@SS:");
                for (Object o : (Set<?>) value) {
                    sb.append(o).append('\u0001');
                }
                return sb.toString();
            }
            return "@S:" + String.valueOf(value);
        }

        private Object decode(String key, String s) {
            if (s == null) return null;
            try {
                if (s.startsWith("@I:")) return Integer.valueOf(s.substring(3));
                if (s.startsWith("@L:")) return Long.valueOf(s.substring(3));
                if (s.startsWith("@B:")) return Boolean.valueOf(s.substring(3));
                if (s.startsWith("@F:")) return Float.valueOf(s.substring(3));
                if (s.startsWith("@SS:")) {
                    Set<String> set = new HashSet<String>();
                    String body = s.substring(4);
                    if (body.length() > 0) {
                        String[] parts = body.split("\u0001");
                        for (String p : parts) set.add(p);
                    }
                    return set;
                }
                if (s.startsWith("@S:")) return s.substring(3);
            } catch (Throwable t) {
                return s;
            }
            return s;
        }

        @Override
        public synchronized Map<String, ?> getAll() {
            return new HashMap<String, Object>(mValues);
        }

        @Override
        public synchronized String getString(String key, String defValue) {
            Object v = mValues.get(key);
            return (v instanceof String) ? (String) v : defValue;
        }

        @Override
        public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
            Object v = mValues.get(key);
            if (v instanceof Set) {
                return new HashSet<String>((Set<String>) v);
            }
            return defValues;
        }

        @Override
        public synchronized int getInt(String key, int defValue) {
            Object v = mValues.get(key);
            return (v instanceof Integer) ? ((Integer) v).intValue() : defValue;
        }

        @Override
        public synchronized long getLong(String key, long defValue) {
            Object v = mValues.get(key);
            return (v instanceof Long) ? ((Long) v).longValue() : defValue;
        }

        @Override
        public synchronized float getFloat(String key, float defValue) {
            Object v = mValues.get(key);
            return (v instanceof Float) ? ((Float) v).floatValue() : defValue;
        }

        @Override
        public synchronized boolean getBoolean(String key, boolean defValue) {
            Object v = mValues.get(key);
            return (v instanceof Boolean) ? ((Boolean) v).booleanValue() : defValue;
        }

        @Override
        public synchronized boolean contains(String key) {
            return mValues.containsKey(key);
        }

        @Override
        public synchronized Editor edit() {
            return new SdEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            synchronized (mListeners) {
                mListeners.put(listener, Boolean.TRUE);
            }
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            synchronized (mListeners) {
                mListeners.remove(listener);
            }
        }

        private void notifyChanged(String key) {
            synchronized (mListeners) {
                for (OnSharedPreferenceChangeListener l : mListeners.keySet()) {
                    if (l != null) {
                        try {
                            l.onSharedPreferenceChanged(this, key);
                        } catch (Throwable t) {
                        }
                    }
                }
            }
        }

        private class SdEditor implements Editor {
            private final Map<String, Object> mModified = new HashMap<String, Object>();
            private boolean mClear = false;

            @Override
            public Editor putString(String key, String value) {
                mModified.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                mModified.put(key, values);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                mModified.put(key, Integer.valueOf(value));
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                mModified.put(key, Long.valueOf(value));
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                mModified.put(key, Float.valueOf(value));
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                mModified.put(key, Boolean.valueOf(value));
                return this;
            }

            @Override
            public Editor remove(String key) {
                mModified.put(key, null);
                return this;
            }

            @Override
            public Editor clear() {
                mClear = true;
                return this;
            }

            @Override
            public boolean commit() {
                synchronized (SdSharedPreferences.this) {
                    if (mClear) {
                        mValues.clear();
                        mProps.clear();
                    }
                    for (Map.Entry<String, Object> e : mModified.entrySet()) {
                        String key = e.getKey();
                        Object value = e.getValue();
                        if (value == null) {
                            mValues.remove(key);
                            mProps.remove(key);
                        } else {
                            mValues.put(key, value);
                            mProps.setProperty(key, encode(key, value));
                        }
                    }
                    boolean ok = persist();
                    if (ok) {
                        for (String key : mModified.keySet()) {
                            notifyChanged(key);
                        }
                    }
                    mModified.clear();
                    mClear = false;
                    return ok;
                }
            }

            @Override
            public void apply() {
                commit();
            }
        }
    }
}
