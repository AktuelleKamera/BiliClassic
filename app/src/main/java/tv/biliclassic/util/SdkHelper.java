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
     * 设置输入类型（API 3+ 才有 TextView.setInputType）。API<3 平台上没有该方法，
     * 若在字节码里直接引用，verifier 会在类加载时因方法不存在而拒绝整个类（VerifyError）。
     * 这里用反射，低版本直接忽略（输入类型不可编程设置）。
     */
    public static void setInputType(android.widget.TextView view, int type) {
        if (view == null) return;
        try {
            java.lang.reflect.Method m = android.widget.TextView.class.getMethod("setInputType", int.class);
            m.invoke(view, type);
        } catch (Throwable t) {
            // API < 3 无 setInputType，忽略
        }
    }

    /**
     * 获取 AlertDialog 的按钮（API 3+ 才有 AlertDialog.getButton）。API<3 平台上没有该方法，
     * 若在字节码里直接引用，verifier 会在类加载时因方法不存在而拒绝整个类（VerifyError）。
     * 这里用反射，低版本返回 null（调用方需判空，退化为默认行为）。
     */
    public static android.widget.Button getDialogButton(android.app.AlertDialog dialog, int which) {
        if (dialog == null) return null;
        try {
            java.lang.reflect.Method m = android.app.AlertDialog.class.getMethod("getButton", int.class);
            return (android.widget.Button) m.invoke(dialog, which);
        } catch (Throwable t) {
            return null;
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

    /**
     * 创建 GestureDetector。API 3+ 才有 GestureDetector(Context, OnGestureListener) 构造；
     * API 1 只有 GestureDetector(OnGestureListener)（单参，已 deprecated）。
     * 若在字节码里直接引用 2 参构造，verifier 会在 API<3 平台拒绝整个类（VerifyError）。
     * 这里用反射：优先 2 参构造，失败退回 1 参构造，再失败返回 null。
     */
    public static android.view.GestureDetector newGestureDetector(android.content.Context context,
            android.view.GestureDetector.OnGestureListener listener) {
        if (listener == null) return null;
        try {
            java.lang.reflect.Constructor<?> c = android.view.GestureDetector.class
                    .getConstructor(android.content.Context.class, android.view.GestureDetector.OnGestureListener.class);
            return (android.view.GestureDetector) c.newInstance(context, listener);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Constructor<?> c = android.view.GestureDetector.class
                        .getConstructor(android.view.GestureDetector.OnGestureListener.class);
                return (android.view.GestureDetector) c.newInstance(listener);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /**
     * 给 PopupWindow 设置 OnDismissListener。PopupWindow.OnDismissListener 接口与
     * PopupWindow.setOnDismissListener 方法都是 API 3+ 才有的；若直接在字节码里实现该接口，
     * 内部匿名类在 API<3 平台会因接口不存在而无法链接（VerifyError）。
     * 这里用 Proxy 动态实现接口 + 反射调用 setOnDismissListener；低版本无此接口则直接忽略。
     */
    public static void setOnDismissListener(android.widget.PopupWindow pw, final Runnable onDismiss) {
        if (pw == null || onDismiss == null) return;
        try {
            final Class<?> iface = Class.forName("android.widget.PopupWindow$OnDismissListener");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(iface.getClassLoader(),
                    new Class<?>[]{iface}, new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            if ("onDismiss".equals(method.getName())) {
                                try { onDismiss.run(); } catch (Throwable t) {}
                            }
                            return null;
                        }
                    });
            java.lang.reflect.Method set = android.widget.PopupWindow.class.getMethod("setOnDismissListener", iface);
            set.invoke(pw, proxy);
        } catch (Throwable t) {
            // API < 3 无 OnDismissListener 支持，忽略
        }
    }

    /**
     * 显示软键盘。InputMethodManager 在部分 API 1 框架上不存在（直接 check-cast 会被 verifier 拒绝），
     * 这里用反射拿系统服务并调用 showSoftInput，避免直接引用 InputMethodManager 类型。SHOW_IMPLICIT = 1。
     */
    public static void showSoftInput(android.view.View view, int flags) {
        if (view == null) return;
        try {
            android.content.Context ctx = view.getContext();
            java.lang.Object imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            java.lang.reflect.Method m = imm.getClass().getMethod("showSoftInput", android.view.View.class, int.class);
            m.invoke(imm, view, flags);
        } catch (Throwable t) {
        }
    }

    /**
     * 隐藏软键盘。同样避免直接引用 InputMethodManager 类型（API 1 上 check-cast 会被 verifier 拒绝）。
     * 需要 Context 来取系统服务，故多传一个 ctx 参数。
     */
    public static void hideSoftInputFromWindow(android.content.Context ctx, android.os.IBinder token, int flags) {
        if (ctx == null || token == null) return;
        try {
            java.lang.Object imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            java.lang.reflect.Method m = imm.getClass().getMethod("hideSoftInputFromWindow", android.os.IBinder.class, int.class);
            m.invoke(imm, token, flags);
        } catch (Throwable t) {
        }
    }

    /**
     * Activity.overridePendingTransition(int,int)（API 5+）；低版本 no-op。
     * 直接调用会让 API&lt;5 的 verifier 拒绝整个类，故用反射。
     */
    public static void overridePendingTransition(android.app.Activity activity, int enterAnim, int exitAnim) {
        if (activity == null) return;
        try {
            android.app.Activity.class
                    .getMethod("overridePendingTransition", int.class, int.class)
                    .invoke(activity, Integer.valueOf(enterAnim), Integer.valueOf(exitAnim));
        } catch (Throwable t) {
        }
    }

    public interface EditorActionHandler {
        boolean onEditorAction(int actionId, android.view.KeyEvent event);
    }

    /**
     * 给 TextView 设置 OnEditorActionListener。TextView.OnEditorActionListener 接口是 API 3+ 才有的，
     * 直接在字节码里 new 实现类，内部匿名类在 API<3 平台会因接口不存在而无法链接（VerifyError）。
     * 用 Proxy 动态实现接口 + 反射调用 setOnEditorActionListener；低版本无此接口则直接忽略。
     */
    public static void setOnEditorActionListener(android.widget.TextView tv, final EditorActionHandler handler) {
        if (tv == null || handler == null) return;
        try {
            final Class<?> iface = Class.forName("android.widget.TextView$OnEditorActionListener");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(iface.getClassLoader(),
                    new Class<?>[]{iface}, new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object p, java.lang.reflect.Method m, Object[] a) {
                            if ("onEditorAction".equals(m.getName()) && a != null && a.length >= 3) {
                                try {
                                    return handler.onEditorAction((Integer) a[1], (android.view.KeyEvent) a[2]);
                                } catch (Throwable t) {
                                }
                            }
                            return false;
                        }
                    });
            java.lang.reflect.Method set = android.widget.TextView.class.getMethod("setOnEditorActionListener", iface);
            set.invoke(tv, proxy);
        } catch (Throwable t) {
            // API < 3 无 OnEditorActionListener 接口（如 Android 1.0）：退化为监听物理回车键。
            // 否则在搜索框里按回车没有任何反应（软键盘也是 API 3+，低版本根本弹不出来）。
            tv.setOnKeyListener(new android.view.View.OnKeyListener() {
                @Override
                public boolean onKey(android.view.View v, int keyCode, android.view.KeyEvent event) {
                    if (event != null
                            && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                            && event.getRepeatCount() == 0
                            && keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                        handler.onEditorAction(0, event);
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    /**
     * 弹窗 context 包裹：API 11+ 直接返回原 context（沿用 Activity 主题里的 alertDialogTheme，行为不变）；
     * API 1-10 则套上自包含的 AppDialog 主题，避免 Activity 的深色主题（白字）渗进对话框造成白底白字。
     * alertDialogTheme 这类主题属性在 API 11 才加入，必须在代码里用 ContextThemeWrapper 强制套主题。
     */
    public static android.content.Context dialogContext(android.content.Context context) {
        if (context == null) return null;
        if (getSdkInt() >= 11) return context;
        return new android.view.ContextThemeWrapper(context, tv.biliclassic.R.style.AppDialog);
    }
}
