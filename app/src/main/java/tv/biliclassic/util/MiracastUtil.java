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
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.util;

import android.content.Context;
import android.content.Intent;

/**
 * 投屏（Miracast）辅助：
 * Miracast 是系统级无线显示功能，App 不自行推流，只在播放前引导用户
 * 打开系统的"无线显示"设置完成连接；连接后系统会镜像整个屏幕，
 * 播放器画面自动出现在电视/显示器上。
 *
 * 注意：DisplayManager 相关 API 为 Android 4.2+（API 17），
 * 为避免老平台上类校验拒载，全部走反射访问。
 */
public class MiracastUtil {
    private static final String KEY_CAST_ENABLED = "miracast_enabled";
    private static final String KEY_CAST_PROMPTED = "miracast_prompted";

    /** 投屏引导开关（实验室） */
    public static boolean isCastEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_CAST_ENABLED, false);
    }

    public static void setCastEnabled(boolean enabled) {
        SharedPreferencesUtil.putBoolean(KEY_CAST_ENABLED, enabled);
        if (!enabled) {
            // 关闭时重置引导标记，下次开启会重新引导
            SharedPreferencesUtil.putBoolean(KEY_CAST_PROMPTED, false);
        }
    }

    /**
     * 远程显示设备（Miracast/HDMI 拓展屏）是否已连接。
     * Android 4.2+ 才有 DisplayManager，低版本一律返回 false。
     */
    public static boolean isRemoteDisplayConnected(Context context) {
        if (SdkHelper.getSdkInt() < 17) return false;
        try {
            Object displayManager = context.getSystemService("display");
            if (displayManager == null) return false;
            // DisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            java.lang.reflect.Method getDisplays = displayManager.getClass()
                    .getMethod("getDisplays", String.class);
            Object[] displays = (Object[]) getDisplays.invoke(
                    displayManager, "android.presentation");
            return displays != null && displays.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 播放前是否需要先引导系统投屏：
     * 开关开启 && 本次开启后尚未引导过 && 远程显示器未连接。
     * 引导过一次后（无论是否连接成功）播放不再跳转，避免循环。
     */
    public static boolean shouldPromptBeforePlay(Context context) {
        if (!isCastEnabled()) return false;
        if (SharedPreferencesUtil.getBoolean(KEY_CAST_PROMPTED, false)) return false;
        return !isRemoteDisplayConnected(context);
    }

    /** 标记已引导过：之后的播放直接走正常流程 */
    public static void markPrompted() {
        SharedPreferencesUtil.putBoolean(KEY_CAST_PROMPTED, true);
    }

    /**
     * 跳转系统投屏（无线显示）设置。
     * 各 ROM 支持的 action 不同，依次尝试，全部失败返回 false。
     */
    public static boolean openCastSettings(Context context) {
        // 4.2+ AOSP 的无线显示设置
        if (tryStart(context, "android.settings.WIRELESS_DISPLAY_SETTINGS")) return true;
        // 5.0+ 的投屏设置
        if (tryStart(context, "android.settings.CAST_SETTINGS")) return true;
        // 回退：WiFi 设置 / 显示设置
        if (tryStart(context, "android.settings.WIFI_SETTINGS")) return true;
        return tryStart(context, "android.settings.DISPLAY_SETTINGS");
    }

    private static boolean tryStart(Context context, String action) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
