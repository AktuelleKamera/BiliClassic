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
}
