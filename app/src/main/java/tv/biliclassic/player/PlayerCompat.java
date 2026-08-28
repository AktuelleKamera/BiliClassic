package tv.biliclassic.player;

import android.content.Context;
import android.os.Handler;
import android.view.Surface;
import android.view.View;
import android.view.Window;

/**
 * 播放器新 API 兼容层。
 *
 * Android 2.1 及以下的 Dalvik 校验器为硬失败模式：类中任何一条指令引用了
 * 本平台不存在的类/方法/字段，整个类会被拒载（VerifyError）；2.2+ 才是软失败
 * （replacing opcode，补丁后可照常运行）。
 * 因此 BiliPlayerActivity 的字节码里不允许出现任何高版本 API 的直接引用，
 * 相关调用统一收敛到本类。本类内部对新 API 一律走反射（找不到即抛
 * NoSuchMethodException，捕获后按"低版本无此能力"处理），保证本类自身
 * 在 API 3 上可通过校验。
 */
public final class PlayerCompat {

    /**
     * 自定义普通接口，替代 TextureView$SurfaceTextureListener：
     * 老平台没有该接口，直接匿名实现会导致内部类链接失败。
     * surfaceTexture 以 Object 传递。
     */
    public interface SurfaceTextureCallback {
        void onSurfaceTextureAvailable(Object surfaceTexture, int width, int height);
        void onSurfaceTextureSizeChanged(Object surfaceTexture, int width, int height);
        boolean onSurfaceTextureDestroyed(Object surfaceTexture);
        void onSurfaceTextureUpdated(Object surfaceTexture, int width, int height);
    }

    private PlayerCompat() {}

    /**
     * 创建 TextureView 并用动态代理接线回调（API 14+）。
     * 低版本或创建失败时返回 null，调用方应回退 SurfaceView。
     */
    public static View createTextureView(Context ctx, final SurfaceTextureCallback cb) {
        try {
            final Class<?> tvClass = Class.forName("android.view.TextureView");
            final Object tv = tvClass.getConstructor(Context.class).newInstance(ctx);
            final Class<?> listenerClass =
                    Class.forName("android.view.TextureView$SurfaceTextureListener");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new java.lang.reflect.InvocationHandler() {
                        public Object invoke(Object proxy, java.lang.reflect.Method method,
                                             Object[] args) throws Throwable {
                            String name = method.getName();
                            // 四个回调签名均为 (SurfaceTexture, int, int)
                            if (args == null || args.length < 3) {
                                return Boolean.FALSE;
                            }
                            Object st = args[0];
                            int w = ((Integer) args[1]).intValue();
                            int h = ((Integer) args[2]).intValue();
                            if ("onSurfaceTextureAvailable".equals(name)) {
                                cb.onSurfaceTextureAvailable(st, w, h);
                            } else if ("onSurfaceTextureSizeChanged".equals(name)) {
                                cb.onSurfaceTextureSizeChanged(st, w, h);
                            } else if ("onSurfaceTextureDestroyed".equals(name)) {
                                return Boolean.valueOf(cb.onSurfaceTextureDestroyed(st));
                            } else if ("onSurfaceTextureUpdated".equals(name)) {
                                cb.onSurfaceTextureUpdated(st, w, h);
                            }
                            return null;
                        }
                    });
            tvClass.getMethod("setSurfaceTextureListener", listenerClass).invoke(tv, proxy);
            return (View) tv;
        } catch (Throwable t) {
            return null;
        }
    }

    /** TextureView.getSurfaceTexture()；低版本或尚无 surface 时返回 null */
    public static Object getSurfaceTexture(View view) {
        try {
            return view.getClass().getMethod("getSurfaceTexture").invoke(view);
        } catch (Throwable t) {
            return null;
        }
    }

    /** new Surface(SurfaceTexture)；API 11 以下返回 null */
    public static Surface createSurfaceFromTexture(Object surfaceTexture) {
        try {
            Class<?> stClass = Class.forName("android.graphics.SurfaceTexture");
            return (Surface) Surface.class.getConstructor(stClass).newInstance(surfaceTexture);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 视图变换：setPivotX/Y、setScaleX/Y、setTranslationX/Y、setRotation(0)，
     * 全部是 API 11+ 方法，反射调用；老平台抛 NoSuchMethodException 后整体跳过
     * （SurfaceView 缩放路径不依赖变换，行为正确）。
     */
    public static void setViewTransform(View v, float pivotX, float pivotY,
                                        float scaleX, float scaleY,
                                        float translationX, float translationY) {
        try {
            invokeF(v, "setPivotX", pivotX);
            invokeF(v, "setPivotY", pivotY);
            invokeF(v, "setScaleX", scaleX);
            invokeF(v, "setScaleY", scaleY);
            invokeF(v, "setTranslationX", translationX);
            invokeF(v, "setTranslationY", translationY);
            invokeF(v, "setRotation", 0f);
        } catch (Throwable t) {
        }
    }

    private static void invokeF(Object target, String method, float value) throws Exception {
        java.lang.reflect.Method m = target.getClass().getMethod(method, float.class);
        m.invoke(target, Float.valueOf(value));
    }

    /** View.setAlpha(float)（API 11+），入参为 0~255 透明度；低版本 no-op */
    public static void setAlpha(View v, int alpha255) {
        try {
            v.getClass().getMethod("setAlpha", float.class)
                    .invoke(v, Float.valueOf(alpha255 / 255f));
        } catch (Throwable t) {
        }
    }

    /** View.setSystemUiVisibility(int)（API 11+）；低版本 no-op。flags 为编译期内联常量 */
    public static void setSystemUiVisibility(View decorView, int visibility) {
        try {
            View.class.getMethod("setSystemUiVisibility", int.class)
                    .invoke(decorView, Integer.valueOf(visibility));
        } catch (Throwable t) {
        }
    }

    /**
     * 注册 OnSystemUiVisibilityChangeListener（API 11+ 接口，Proxy 动态实现）：
     * 系统栏重新可见时延迟 3 秒自动再次隐藏。低版本 Class.forName 失败即跳过。
     */
    public static void setAutoRehideSystemUiListener(final View decorView,
                                                     final Handler handler,
                                                     final Runnable rehideAction) {
        try {
            final int hideNavigationFlag = 0x2; // View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            final Class<?> listenerClass =
                    Class.forName("android.view.View$OnSystemUiVisibilityChangeListener");
            final java.lang.reflect.Method setter = View.class.getMethod(
                    "setOnSystemUiVisibilityChangeListener", listenerClass);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new java.lang.reflect.InvocationHandler() {
                        public Object invoke(Object proxy, java.lang.reflect.Method method,
                                             Object[] args) throws Throwable {
                            if ("onSystemUiVisibilityChange".equals(method.getName())
                                    && args != null && args.length > 0) {
                                int visibility = ((Integer) args[0]).intValue();
                                if ((visibility & hideNavigationFlag) == 0) {
                                    handler.removeCallbacks(rehideAction);
                                    handler.postDelayed(rehideAction, 3000);
                                }
                            }
                            return null;
                        }
                    });
            setter.invoke(decorView, proxy);
        } catch (Throwable t) {
        }
    }

    /**
     * API 21+ 消费系统窗口插入，防止旋转后安全区/挖孔推回布局。
     * 低版本 Class.forName(WindowInsets) 失败即跳过。
     */
    public static void consumeSystemWindowInsets(final Window window) {
        try {
            final View decor = window.getDecorView();
            final Class<?> listenerClass =
                    Class.forName("android.view.View$OnApplyWindowInsetsListener");
            final Class<?> insetsClass = Class.forName("android.view.WindowInsets");
            final java.lang.reflect.Method consume =
                    insetsClass.getMethod("consumeSystemWindowInsets");
            final java.lang.reflect.Method setter = View.class.getMethod(
                    "setOnApplyWindowInsetsListener", listenerClass);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new java.lang.reflect.InvocationHandler() {
                        public Object invoke(Object p, java.lang.reflect.Method method,
                                             Object[] args) throws Throwable {
                            if ("onApplyWindowInsets".equals(method.getName())
                                    && args != null && args.length > 1) {
                                return consume.invoke(args[1]);
                            }
                            return null;
                        }
                    });
            setter.invoke(decor, proxy);
        } catch (Throwable t) {
        }
    }

    /** View.isAttachedToWindow()（API 19+）；低版本返回 false */
    public static boolean isAttachedToWindow(View v) {
        try {
            Object r = View.class.getMethod("isAttachedToWindow").invoke(v);
            return ((Boolean) r).booleanValue();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * SurfaceView.setZOrderMediaOverlay(boolean)（API 5+）；
     * 低版本抛 NoSuchMethodException 后跳过（等价于不置顶媒体层）。
     */
    public static void setZOrderMediaOverlay(android.view.SurfaceView sv, boolean on) {
        try {
            android.view.SurfaceView.class
                    .getMethod("setZOrderMediaOverlay", boolean.class)
                    .invoke(sv, Boolean.valueOf(on));
        } catch (Throwable t) {
        }
    }

    /**
     * MotionEvent.getPointerCount()（API 5+）；低版本返回 1（单点触摸语义）。
     */
    public static int getPointerCount(android.view.MotionEvent ev) {
        try {
            Object r = android.view.MotionEvent.class
                    .getMethod("getPointerCount").invoke(ev);
            return ((Integer) r).intValue();
        } catch (Throwable t) {
            return 1;
        }
    }

    /** MotionEvent.getRawX()（API 5+）；低版本退化为视图内坐标 getX() */
    public static float getRawX(android.view.MotionEvent ev) {
        try {
            Object r = android.view.MotionEvent.class.getMethod("getRawX").invoke(ev);
            return ((Float) r).floatValue();
        } catch (Throwable t) {
            return ev.getX();
        }
    }

    /** MotionEvent.getRawY()（API 5+）；低版本退化为视图内坐标 getY() */
    public static float getRawY(android.view.MotionEvent ev) {
        try {
            Object r = android.view.MotionEvent.class.getMethod("getRawY").invoke(ev);
            return ((Float) r).floatValue();
        } catch (Throwable t) {
            return ev.getY();
        }
    }

    /** Activity.overridePendingTransition(int,int)（API 5+）；低版本 no-op */
    public static void overridePendingTransition(android.app.Activity activity,
                                                 int enterAnim, int exitAnim) {
        try {
            android.app.Activity.class
                    .getMethod("overridePendingTransition", int.class, int.class)
                    .invoke(activity, Integer.valueOf(enterAnim), Integer.valueOf(exitAnim));
        } catch (Throwable t) {
        }
    }
}
