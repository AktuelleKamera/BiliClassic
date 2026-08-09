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
     * 读取布尔资源。API 1 的 Resources 只有 getBoolean(int, boolean)（带默认值），
     * 单参 getBoolean(int) 是 API 4+ 才有的；若在字节码里直接引用单参版本，
     * verifier 会在 API<4 平台上因方法不存在而拒绝整个类（VerifyError）。
     * 这里用反射，先试单参，失败再退回带默认值的双参版本。
     */
    public static boolean getBooleanResource(android.content.res.Resources res, int resId) {
        if (res == null) {
            return false;
        }
        try {
            java.lang.reflect.Method m = android.content.res.Resources.class.getMethod("getBoolean", int.class);
            return ((Boolean) m.invoke(res, resId)).booleanValue();
        } catch (Throwable t) {
            try {
                java.lang.reflect.Method m2 = android.content.res.Resources.class.getMethod("getBoolean", int.class, boolean.class);
                return ((Boolean) m2.invoke(res, resId, Boolean.FALSE)).booleanValue();
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    /**
     * 读取 DisplayMetrics.densityDpi。该字段是 API 4+ 才加入的，API 3 上不存在；
     * 若在字节码里直接引用该字段，verifier 会在类加载时因字段不存在而拒绝整个类（VerifyError）。
     * 这里用反射读取，失败时退回 160（mdpi 默认值）。
     */
    public static int getDensityDpi(android.util.DisplayMetrics dm) {
        if (dm == null) {
            return 160;
        }
        try {
            java.lang.reflect.Field f = android.util.DisplayMetrics.class.getField("densityDpi");
            return f.getInt(dm);
        } catch (Throwable t) {
            return 160;
        }
    }

    /**
     * 在 View 上注册 onAttachToWindow 回调（仅首次触发后自动移除）。
     * View.OnAttachStateChangeListener 是 API 12+ 的接口，若在字节码里直接 new 实现类，
     * verifier 会在 API<12 平台上因接口不存在而拒绝整个类（VerifyError）。
     * 这里用 Proxy + 反射动态创建监听器，API 3 也安全。
     */
    public static void onViewAttached(android.view.View view, final Runnable onAttached) {
        if (view == null || onAttached == null) return;
        try {
            final Class<?> listenerClass = Class.forName("android.view.View$OnAttachStateChangeListener");
            final Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            try {
                                String name = method.getName();
                                if ("onViewAttachedToWindow".equals(name)) {
                                    onAttached.run();
                                    // 已触发，移除监听（反射调用）
                                    if (args != null && args.length > 0) {
                                        try {
                                            java.lang.reflect.Method remove =
                                                    android.view.View.class.getMethod(
                                                            "removeOnAttachStateChangeListener", listenerClass);
                                            remove.invoke(args[0], proxy);
                                        } catch (Throwable t) {
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                            }
                            return null;
                        }
                    });
            java.lang.reflect.Method add =
                    android.view.View.class.getMethod("addOnAttachStateChangeListener", listenerClass);
            add.invoke(view, listener);
        } catch (Throwable t) {
        }
    }
}
