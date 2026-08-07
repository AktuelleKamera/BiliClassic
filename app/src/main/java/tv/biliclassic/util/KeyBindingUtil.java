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

import android.view.KeyEvent;

/**
 * 按键绑定表工具类。
 *
 * 集中管理"逻辑动作 → keycode"的绑定读写，作为按键判定的唯一事实来源。
 * 未绑定的动作回退到系统默认 keycode（DPAD_* / SOFT_* / 数字 / STAR / POUND），
 * 保证老用户、跳过绑定、清数据等场景行为不变。
 */
public final class KeyBindingUtil {

    // ===== 逻辑动作常量 =====
    public static final int ACTION_SOFT_LEFT = 0;
    public static final int ACTION_SOFT_RIGHT = 1;
    public static final int ACTION_UP = 2;
    public static final int ACTION_DOWN = 3;
    public static final int ACTION_LEFT = 4;
    public static final int ACTION_RIGHT = 5;
    public static final int ACTION_CONFIRM = 6;
    public static final int ACTION_NUM_0 = 7;
    public static final int ACTION_NUM_1 = 8;
    public static final int ACTION_NUM_2 = 9;
    public static final int ACTION_NUM_3 = 10;
    public static final int ACTION_NUM_4 = 11;
    public static final int ACTION_NUM_5 = 12;
    public static final int ACTION_NUM_6 = 13;
    public static final int ACTION_NUM_7 = 14;
    public static final int ACTION_NUM_8 = 15;
    public static final int ACTION_NUM_9 = 16;
    public static final int ACTION_STAR = 17;
    public static final int ACTION_POUND = 18;

    public static final int ACTION_COUNT = 19;

    // ===== SharedPreferences key 常量（前缀 key_） =====
    private static final String[] PREFS_KEYS = {
        "key_soft_left",     // 0
        "key_soft_right",    // 1
        "key_up",            // 2
        "key_down",          // 3
        "key_left",          // 4
        "key_right",         // 5
        "key_confirm",       // 6
        "key_num_0",         // 7
        "key_num_1",         // 8
        "key_num_2",         // 9
        "key_num_3",         // 10
        "key_num_4",         // 11
        "key_num_5",         // 12
        "key_num_6",         // 13
        "key_num_7",         // 14
        "key_num_8",         // 15
        "key_num_9",         // 16
        "key_star",          // 17
        "key_pound"          // 18
    };

    private KeyBindingUtil() {
    }

    /**
     * 保存某个逻辑动作对应的 keycode。
     */
    public static void saveKey(int action, int keycode) {
        if (action < 0 || action >= ACTION_COUNT) {
            return;
        }
        SharedPreferencesUtil.putInt(PREFS_KEYS[action], keycode);
    }

    /**
     * 获取某个逻辑动作的 keycode；未绑定时返回系统默认值。
     */
    public static int getKey(int action) {
        if (action < 0 || action >= ACTION_COUNT) {
            return -1;
        }
        if (isBound(action)) {
            return SharedPreferencesUtil.getInt(PREFS_KEYS[action], -1);
        }
        return getDefaultKey(action);
    }

    /**
     * 返回某逻辑动作的系统默认 keycode。
     */
    public static int getDefaultKey(int action) {
        switch (action) {
            case ACTION_SOFT_LEFT: return KeyEvent.KEYCODE_SOFT_LEFT;
            case ACTION_SOFT_RIGHT: return KeyEvent.KEYCODE_SOFT_RIGHT;
            case ACTION_UP: return KeyEvent.KEYCODE_DPAD_UP;
            case ACTION_DOWN: return KeyEvent.KEYCODE_DPAD_DOWN;
            case ACTION_LEFT: return KeyEvent.KEYCODE_DPAD_LEFT;
            case ACTION_RIGHT: return KeyEvent.KEYCODE_DPAD_RIGHT;
            case ACTION_CONFIRM: return KeyEvent.KEYCODE_DPAD_CENTER;
            case ACTION_NUM_0: return KeyEvent.KEYCODE_0;
            case ACTION_NUM_1: return KeyEvent.KEYCODE_1;
            case ACTION_NUM_2: return KeyEvent.KEYCODE_2;
            case ACTION_NUM_3: return KeyEvent.KEYCODE_3;
            case ACTION_NUM_4: return KeyEvent.KEYCODE_4;
            case ACTION_NUM_5: return KeyEvent.KEYCODE_5;
            case ACTION_NUM_6: return KeyEvent.KEYCODE_6;
            case ACTION_NUM_7: return KeyEvent.KEYCODE_7;
            case ACTION_NUM_8: return KeyEvent.KEYCODE_8;
            case ACTION_NUM_9: return KeyEvent.KEYCODE_9;
            case ACTION_STAR: return KeyEvent.KEYCODE_STAR;
            case ACTION_POUND: return KeyEvent.KEYCODE_POUND;
            default: return -1;
        }
    }

    /**
     * 判断某逻辑动作是否已被用户绑定。
     */
    public static boolean isBound(int action) {
        if (action < 0 || action >= ACTION_COUNT) {
            return false;
        }
        return SharedPreferencesUtil.contains(PREFS_KEYS[action]);
    }

    /**
     * 判断是否已有任意按键被绑定（用于判断是否走完首次绑定流程）。
     */
    public static boolean anyBound() {
        for (int i = 0; i < ACTION_COUNT; i++) {
            if (isBound(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据 keycode 反查逻辑动作；未匹配返回 -1。
     *
     * 优先匹配用户绑定值，其次匹配系统默认值。
     */
    public static int classify(int keycode) {
        // 先匹配用户绑定值
        for (int i = 0; i < ACTION_COUNT; i++) {
            if (isBound(i) && SharedPreferencesUtil.getInt(PREFS_KEYS[i], -1) == keycode) {
                return i;
            }
        }
        // 再匹配系统默认值
        for (int i = 0; i < ACTION_COUNT; i++) {
            if (getDefaultKey(i) == keycode) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 清除所有按键绑定（重新绑定时先清空，避免旧值残留误判）。
     */
    public static void clearAll() {
        for (int i = 0; i < ACTION_COUNT; i++) {
            if (SharedPreferencesUtil.contains(PREFS_KEYS[i])) {
                SharedPreferencesUtil.removeValue(PREFS_KEYS[i]);
            }
        }
    }
}
