package tv.biliclassic.tv.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import tv.biliclassic.util.SharedPreferencesUtil;

public class TvUtil {

    private static final String TAG = "TvUtil";

    private static final String KEY_FORCE_TV_MODE = "force_tv_mode";

    public static boolean isForceTvModeEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_FORCE_TV_MODE, false);
    }

    public static boolean isTv(Context context) {
        if (isForceTvModeEnabled()) {
            Log.d(TAG, "isTv: 强制 TV 模式开启");
            return true;
        }

        // 检测 Android 版本
        int sdkInt = getSdkInt();
        Log.d(TAG, "SDK_INT: " + sdkInt);

        // Android 4.0 (API 14) 以下不支持 TV 模式
        if (sdkInt < 14) {
            Log.d(TAG, "isTv: Android 4.0 以下，不支持 TV 模式");
            return false;
        }

        PackageManager pm = context.getPackageManager();

        // 1. 检测 FEATURE_TELEVISION (API 11+)
        boolean hasTV = hasSystemFeature(pm, "FEATURE_TELEVISION");
        Log.d(TAG, "FEATURE_TELEVISION: " + hasTV);
        if (hasTV) {
            Log.d(TAG, "isTv: 检测到 FEATURE_TELEVISION，返回 true");
            return true;
        }

        // 2. 检测 FEATURE_LEANBACK (API 14+)
        boolean hasLeanback = hasSystemFeature(pm, "FEATURE_LEANBACK");
        Log.d(TAG, "FEATURE_LEANBACK: " + hasLeanback);
        if (hasLeanback) {
            Log.d(TAG, "isTv: 检测到 FEATURE_LEANBACK，返回 true");
            return true;
        }

        // 3. 没有触摸屏 + 有键盘 = 可能是电视
        boolean hasTouch = hasSystemFeature(pm, "FEATURE_TOUCHSCREEN");
        boolean hasKeyboard = hasSystemFeature(pm, "FEATURE_KEYBOARD");
        Log.d(TAG, "FEATURE_TOUCHSCREEN: " + hasTouch + ", FEATURE_KEYBOARD: " + hasKeyboard);

        if (!hasTouch && hasKeyboard) {
            Log.d(TAG, "isTv: 无触摸屏且有键盘，判定为电视");
            return true;
        }

        Log.d(TAG, "isTv: 判定为非电视设备");
        return false;
    }

    // 获取 SDK_INT，兼容 Android 2.2
    private static int getSdkInt() {
        try {
            Field field = Build.VERSION.class.getField("SDK_INT");
            return field.getInt(null);
        } catch (Exception e) {
            // Android 2.2 及以下没有 SDK_INT，使用 VERSION.SDK
            try {
                Field field = Build.VERSION.class.getField("SDK");
                return Integer.parseInt(field.get(null).toString());
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    // 反射检测系统特征（兼容 Android 2.2）
    private static boolean hasSystemFeature(PackageManager pm, String featureName) {
        try {
            Field field = PackageManager.class.getField(featureName);
            String feature = (String) field.get(null);

            Method method = PackageManager.class.getMethod("hasSystemFeature", String.class);
            Boolean result = (Boolean) method.invoke(pm, feature);
            return result != null && result.booleanValue();

        } catch (NoSuchFieldException e) {
            Log.d(TAG, "hasSystemFeature: " + featureName + " 字段不存在");
            return false;
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "hasSystemFeature: hasSystemFeature 方法不存在 (Android 2.2 及以下)");
            return false;
        } catch (Exception e) {
            Log.d(TAG, "hasSystemFeature: " + featureName + " 检测异常 - " + e.getMessage());
            return false;
        }
    }

    // 显示 Toast
    private static void showToast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "showToast 失败: " + e.getMessage());
        }
    }

    public static boolean isLeanbackSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        return hasSystemFeature(pm, "FEATURE_LEANBACK");
    }

    public static boolean isTouchScreen(Context context) {
        PackageManager pm = context.getPackageManager();
        return hasSystemFeature(pm, "FEATURE_TOUCHSCREEN");
    }
}