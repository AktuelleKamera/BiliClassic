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

    private static SharedPreferences sharedPreferences;

    // 初始化方法，在 Application 中调用
    public static void init(Context context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences("biliclassic", Context.MODE_PRIVATE);
        }
    }

    public static SharedPreferences getSharedPreferences() {
        return sharedPreferences;
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
}