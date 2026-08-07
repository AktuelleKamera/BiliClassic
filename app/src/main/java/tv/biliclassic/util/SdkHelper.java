package tv.biliclassic.util;

public class SdkHelper {
    private SdkHelper() {}

    public static int getSdkInt() {
        try {
            return android.os.Build.VERSION.class.getField("SDK_INT").getInt(null);
        } catch (Exception e) {
            try {
                return Integer.parseInt(android.os.Build.VERSION.SDK);
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    /** Java 堆上限（KB） */
    public static int getMaxMemoryKB() {
        return (int) (Runtime.getRuntime().maxMemory() / 1024);
    }

    /** 高内存设备：堆 >= 32MB（可启用绘制缓存等内存开销较大的优化） */
    public static boolean isHighMemoryDevice() {
        return getMaxMemoryKB() >= 32768;
    }

    /** 低内存设备：堆 < 24MB（强制单线程解码等保守策略） */
    public static boolean isLowMemoryDevice() {
        return getMaxMemoryKB() < 24576;
    }

    /**
     * 图片加载线程数：优先用户设置值（IMAGE_LOAD_THREADS，>0 时用设置）；
     * 未设置时按设备内存给默认值（低内存 1，否则 2）
     */
    public static int getImageLoadThreads() {
        int saved = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (saved > 0) {
            return saved;
        }
        return isLowMemoryDevice() ? 1 : 2;
    }

    /**
     * 关闭过度滚动（API 9+）。必须用反射：API<9 平台上没有 setOverScrollMode 方法，
     * 若在字节码里直接引用，verifier 会在类加载时因方法不存在而拒绝整个类（VerifyError）。
     * OVER_SCROLL_NEVER = 2。
     */
    public static void setOverScrollNever(android.view.View view) {
        try {
            java.lang.reflect.Method m = android.view.View.class.getMethod("setOverScrollMode", int.class);
            m.invoke(view, 2);
        } catch (Throwable t) {
        }
    }

    /**
     * 设备是否有物理按键（硬件键盘 / 方向键）。用于判断是否需要引导按键绑定：
     * 纯触屏机（无键盘、无方向键）直接跳过绑定流程。
     * Configuration.keyboard / navigation 均为 API 1，Android 1.5 可用。
     */
    public static boolean hasHardwareKeys(android.content.Context context) {
        try {
            android.content.res.Configuration cfg = context.getResources().getConfiguration();
            if (cfg.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS) {
                return true;
            }
            if (cfg.navigation == android.content.res.Configuration.NAVIGATION_DPAD
                    || cfg.navigation == android.content.res.Configuration.NAVIGATION_WHEEL) {
                return true;
            }
        } catch (Throwable t) {
        }
        return false;
    }
}
