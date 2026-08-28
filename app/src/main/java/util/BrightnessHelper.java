package util;

import android.app.Activity;
import android.content.ContentResolver;
import android.provider.Settings;
import android.view.WindowManager;

public class BrightnessHelper {
    public static float getScreenBrightness(Activity activity) {
        ContentResolver resolver = activity.getContentResolver();
        try {
            int nowBrightnessValue = Settings.System.getInt(resolver, "screen_brightness", -1);
            return nowBrightnessValue / 255.0f;
        } catch (Exception e) {
            return -1.0f;
        }
    }

    public static void setBrightness(Activity activity, float brightness) {
        // 1) 窗口级亮度（API 8+）
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = Math.max(brightness, 0.01f);
        activity.getWindow().setAttributes(lp);

        // 2) 部分老 ROM（Android 2.2/SDK 8）忽略窗口级覆盖，必须直接写系统
        //    亮度设置并切到手动模式才真正生效。SDK 9+ 实测窗口覆盖即可，
        //    保持无侵入；仅 SDK 8 及以下执行系统写入。
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 9) {
            try {
                ContentResolver resolver = activity.getContentResolver();
                Settings.System.putInt(resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                int v = Math.round(Math.max(brightness, 0.01f) * 255f);
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, v);
            } catch (Throwable t) {
            }
        }
    }
}
