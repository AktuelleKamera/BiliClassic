package tv.biliclassic.tv.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import java.lang.reflect.Field;

public class TvUtil {

    private static final String TAG = "TvUtil";

    // 开发调试开关
    private static final boolean FORCE_TV_MODE = false;

    public static boolean isTv(Context context) {
        if (FORCE_TV_MODE) {
            Log.d(TAG, "isTv: 强制 TV 模式开启");
            return true;
        }

        PackageManager pm = context.getPackageManager();

        // 1. 检测 FEATURE_TELEVISION
        boolean hasTV = hasFeature(pm, "FEATURE_TELEVISION");
        Log.d(TAG, "FEATURE_TELEVISION: " + hasTV);
        if (hasTV) {
            Log.d(TAG, "isTv: 检测到 FEATURE_TELEVISION，返回 true");
            return true;
        }

        // 2. 检测 FEATURE_LEANBACK
        boolean hasLeanback = hasFeature(pm, "FEATURE_LEANBACK");
        Log.d(TAG, "FEATURE_LEANBACK: " + hasLeanback);
        if (hasLeanback) {
            Log.d(TAG, "isTv: 检测到 FEATURE_LEANBACK，返回 true");
            return true;
        }

        // 3. 没有触摸屏 + 有键盘 = 可能是电视
        boolean hasTouch = hasFeature(pm, "FEATURE_TOUCHSCREEN");
        boolean hasKeyboard = hasFeature(pm, "FEATURE_KEYBOARD");
        Log.d(TAG, "FEATURE_TOUCHSCREEN: " + hasTouch + ", FEATURE_KEYBOARD: " + hasKeyboard);

        if (!hasTouch && hasKeyboard) {
            Log.d(TAG, "isTv: 无触摸屏且有键盘，判定为电视");
            return true;
        }

        Log.d(TAG, "isTv: 判定为非电视设备");
        return false;
    }

    // 反射检测系统特征
    private static boolean hasFeature(PackageManager pm, String featureName) {
        try {
            Field field = PackageManager.class.getField(featureName);
            String feature = (String) field.get(null);
            boolean result = pm.hasSystemFeature(feature);
            Log.d(TAG, "hasFeature: " + featureName + " = " + result);
            return result;
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "hasFeature: " + featureName + " 字段不存在");
            return false;
        } catch (Exception e) {
            Log.d(TAG, "hasFeature: " + featureName + " 检测异常 - " + e.getMessage());
            return false;
        }
    }

    public static boolean isLeanbackSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        return hasFeature(pm, "FEATURE_LEANBACK");
    }

    public static boolean isTouchScreen(Context context) {
        PackageManager pm = context.getPackageManager();
        return hasFeature(pm, "FEATURE_TOUCHSCREEN");
    }
}