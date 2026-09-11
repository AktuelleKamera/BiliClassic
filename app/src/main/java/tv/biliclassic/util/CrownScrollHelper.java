package tv.biliclassic.util;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import java.util.ArrayList;

/**
 * 表冠旋钮滚动支持。
 * 表冠旋转会以泛动作事件（ACTION_SCROLL，携带 AXIS_VSCROLL / AXIS_SCROLL 轴值）送达窗口，
 * 系统默认不做任何处理；本类把旋转量换算成像素并滚动当前页面的
 * ListView / ScrollView / HorizontalScrollView。
 *
 * 用法：
 * 1. Activity 层：BaseActivity 已统一在 DecorView 挂监听（onContentChanged 里调用），
 *    BiliPlayerActivity / OstwindPlayerActivity 直接继承 Activity，在 setContentView 后单独挂；
 * 2. 弹窗等独立窗口：调用 {@link #attach(View)} 给滚动容器挂监听。
 *
 * 兼容性：泛动作分发为 API 12+ 能力。旧 Dalvik（2.x）校验器遇到对不存在 API 的
 * invoke-super/接口引用会硬拒绝整个类，所以监听器创建全部隔离在 {@link Api12Impl}，
 * 且入口用 SdkHelper.getSdkInt() 门控——旧系统上不加载、零引用、无异常。
 */
public final class CrownScrollHelper {

    private CrownScrollHelper() {
    }

    /**
     * 给 Activity 窗口的 DecorView 挂表冠滚动监听（onContentChanged / setContentView 后调用）。
     * 未被子视图消费的泛动作事件最终会落到 DecorView 监听器上，
     * 焦点列表被系统原生消费时监听器不触发，两条路径互不冲突。
     */
    public static void attachToWindow(Activity activity) {
        if (activity == null || SdkHelper.getSdkInt() < 12) {
            return;
        }
        try {
            Api12Impl.attachToWindow(activity);
        } catch (Throwable t) {
        }
    }

    /**
     * 给定滚动容器（弹窗里的 ListView/ScrollView 等）挂上表冠滚动监听。
     * 泛动作监听为 API 12+ 能力，旧系统上门控跳过。
     */
    public static void attach(View target) {
        if (target == null || SdkHelper.getSdkInt() < 12) {
            return;
        }
        try {
            Api12Impl.attach(target);
        } catch (Throwable t) {
        }
    }

    /**
     * 播放器音量模式：表冠滚动不滚动列表，而是回调音量步进。
     * steps 正值 = 表冠向下转（向内容尾部方向）= 增大音量，与修正后的滚动方向一致。
     * 泛动作监听为 API 12+ 能力，旧系统上门控跳过。
     */
    public static void attachVolume(Activity activity, CrownVolumeListener listener) {
        if (activity == null || listener == null || SdkHelper.getSdkInt() < 12) {
            return;
        }
        try {
            Api12Impl.attachVolume(activity, listener);
        } catch (Throwable t) {
        }
    }

    public interface CrownVolumeListener {
        /** steps：正值增大音量，负值减小 */
        void onCrownVolume(int steps);
    }

    /**
     * 仅在 API 12+ 上才会被加载的隔离层：
     * OnGenericMotionListener 接口与 setOnGenericMotionListener 都是 12 才有，
     * 放这里保证工具类本身在旧 Dalvik 上校验通过。
     */
    private static class Api12Impl {
        static void attachToWindow(final Activity activity) {
            activity.getWindow().getDecorView().setOnGenericMotionListener(
                    new View.OnGenericMotionListener() {
                        @Override
                        public boolean onGenericMotion(View v, MotionEvent event) {
                            return handleCrownScroll(activity, event);
                        }
                    });
        }

        static void attach(View target) {
            target.setOnGenericMotionListener(new View.OnGenericMotionListener() {
                @Override
                public boolean onGenericMotion(View v, MotionEvent event) {
                    return handleCrownScrollOfView(v, event);
                }
            });
        }

        static void attachVolume(final Activity activity, final CrownVolumeListener listener) {
            // 表冠固件可能每次事件送小数增量，累积取整后才回调，避免小数被吞
            final float[] pending = new float[1];
            activity.getWindow().getDecorView().setOnGenericMotionListener(
                    new View.OnGenericMotionListener() {
                        @Override
                        public boolean onGenericMotion(View v, MotionEvent event) {
                            if (event == null || event.getAction() != MotionEvent.ACTION_SCROLL) {
                                return false;
                            }
                            float delta = readCrownDelta(event);
                            if (delta == 0f) {
                                return false;
                            }
                            // 取反：delta 为负（向内容尾部方向滚动）= 增大音量
                            pending[0] -= delta;
                            int steps = (int) pending[0];
                            if (steps != 0) {
                                pending[0] -= steps;
                                listener.onCrownVolume(steps);
                            }
                            return true;
                        }
                    });
        }
    }

    /**
     * 处理表冠滚动：先沿焦点视图向上找最近的可滚动容器，
     * 找不到再滚动页面里最大可见的可滚动容器。返回 true 表示已消费。
     */
    public static boolean handleCrownScroll(Activity activity, MotionEvent event) {
        if (activity == null || event == null
                || event.getAction() != MotionEvent.ACTION_SCROLL) {
            return false;
        }
        try {
            float delta = readCrownDelta(event);
            if (delta == 0f) {
                return false;
            }
            View target = findTargetFromFocus(activity, delta);
            if (target == null) {
                target = findLargestScrollable(activity.findViewById(android.R.id.content), delta);
            }
            if (target == null) {
                return false;
            }
            scrollBy(target, delta);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** attach 用：只滚动挂了监听的这个容器本身 */
    private static boolean handleCrownScrollOfView(View v, MotionEvent event) {
        if (v == null || event == null
                || event.getAction() != MotionEvent.ACTION_SCROLL) {
            return false;
        }
        try {
            float delta = readCrownDelta(event);
            if (delta == 0f || !canScroll(v, delta)) {
                return false;
            }
            scrollBy(v, delta);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 读取表冠旋转量：不同固件把表冠映射到不同轴，
     * 依次尝试垂直滚轮 / 表冠专用轴 / 滚轮，返回原始轴向值。
     * 注意：实测表冠固件方向与滚轮约定相反，滚动/音量映射统一按"负值=向内容尾部"处理。
     */
    public static float readCrownDelta(MotionEvent event) {
        if (event == null) {
            return 0f;
        }
        float delta = getAxisValueCompat(event, MotionEvent.AXIS_VSCROLL);
        if (delta == 0f) {
            delta = getAxisValueCompat(event, MotionEvent.AXIS_SCROLL);
        }
        if (delta == 0f) {
            delta = getAxisValueCompat(event, MotionEvent.AXIS_WHEEL);
        }
        return delta;
    }

    /**
     * MotionEvent.getAxisValue 是 API 12+ 的方法。直接调用会让 dalvik 验证器在
     * 老设备（< API 12）上拒绝整个 CrownScrollHelper 类，导致 VerifyError。
     * 这里用反射调用，兼容老设备。
     */
    private static float getAxisValueCompat(MotionEvent event, int axis) {
        try {
            java.lang.reflect.Method m = MotionEvent.class.getMethod("getAxisValue", int.class);
            Object r = m.invoke(event, axis);
            if (r instanceof Float) {
                return (Float) r;
            }
        } catch (Throwable t) {
            // 老设备没有该方法，返回 0
        }
        return 0f;
    }

    /** 从焦点视图向上找最近一个能朝该方向继续滚动的容器 */
    private static View findTargetFromFocus(Activity activity, float delta) {
        try {
            View v = activity.getCurrentFocus();
            while (v != null) {
                if (canScroll(v, delta)) {
                    return v;
                }
                ViewGroup parent = v.getParent() instanceof ViewGroup
                        ? (ViewGroup) v.getParent() : null;
                v = parent;
            }
        } catch (Throwable t) {
        }
        return null;
    }

    /** 深度优先收集内容里可见的可滚动容器，返回朝该方向还能滚的最大者 */
    private static View findLargestScrollable(View root, float delta) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        try {
            ArrayList<View> found = new ArrayList<View>();
            collectScrollables((ViewGroup) root, found);
            View best = null;
            int bestSize = -1;
            for (int i = 0; i < found.size(); i++) {
                View v = found.get(i);
                if (!canScroll(v, delta)) {
                    continue;
                }
                int size = (v instanceof HorizontalScrollView)
                        ? v.getWidth() : v.getHeight();
                if (size > bestSize) {
                    bestSize = size;
                    best = v;
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 收集可见的可滚动容器；已进入滚动容器就不再深入其内部 */
    private static void collectScrollables(ViewGroup parent, ArrayList<View> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (c instanceof AbsListView || c instanceof ScrollView
                    || c instanceof HorizontalScrollView) {
                out.add(c);
            } else if (c instanceof ViewGroup) {
                collectScrollables((ViewGroup) c, out);
            }
        }
    }

    /** 该容器是否还能朝 delta 指示的方向滚动 */
    private static boolean canScroll(View v, float delta) {
        if (v == null || v.getVisibility() != View.VISIBLE) {
            return false;
        }
        boolean forward = delta < 0f;
        if (v instanceof AbsListView) {
            return listCanScroll((AbsListView) v, forward);
        }
        if (v instanceof ScrollView) {
            return scrollVerticalCan(v, forward);
        }
        if (v instanceof HorizontalScrollView) {
            return scrollHorizontalCan(v, forward);
        }
        return false;
    }

    private static boolean listCanScroll(AbsListView list, boolean forward) {
        try {
            int count = list.getCount();
            if (count <= 0) {
                return false;
            }
            if (forward) {
                if (list.getLastVisiblePosition() < count - 1) {
                    return true;
                }
                int childIndex = list.getChildCount() - 1;
                if (childIndex < 0) {
                    return false;
                }
                View c = list.getChildAt(childIndex);
                return c.getBottom() > list.getHeight() - list.getPaddingBottom();
            } else {
                if (list.getFirstVisiblePosition() > 0) {
                    return true;
                }
                if (list.getChildCount() == 0) {
                    return false;
                }
                View c = list.getChildAt(0);
                return c.getTop() < list.getPaddingTop();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean scrollVerticalCan(View v, boolean forward) {
        if (!(v instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) v;
        try {
            if (group.getChildCount() == 0) {
                return false;
            }
            if (forward) {
                View last = group.getChildAt(group.getChildCount() - 1);
                return last.getBottom() + group.getPaddingBottom()
                        > group.getScrollY() + group.getHeight();
            } else {
                return group.getScrollY() > group.getPaddingTop();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean scrollHorizontalCan(View v, boolean forward) {
        if (!(v instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) v;
        try {
            if (group.getChildCount() == 0) {
                return false;
            }
            if (forward) {
                View last = group.getChildAt(group.getChildCount() - 1);
                return last.getRight() + group.getPaddingRight()
                        > group.getScrollX() + group.getWidth();
            } else {
                return group.getScrollX() > group.getPaddingLeft();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** 执行平滑滚动；极旧系统无 smoothScrollBy 时退化为逐项跳选 */
    private static void scrollBy(View v, float delta) {
        int px = scrollAmount(v, delta);
        if (px == 0) {
            return;
        }
        // smoothScrollBy 是 API 8+，用反射调用以兼容更老设备（否则 dalvik 验证器会拒绝整个类）
        if (v instanceof AbsListView) {
            if (smoothScrollByCompat(v, px, 180)) {
                return;
            }
            try {
                AbsListView list = (AbsListView) v;
                int step = delta > 0f ? -1 : 1;
                list.setSelection(list.getFirstVisiblePosition() + step);
            } catch (Throwable t) {
            }
        } else if (v instanceof HorizontalScrollView) {
            if (smoothScrollByCompat(v, px, 0)) {
                return;
            }
            try {
                v.scrollBy(px, 0);
            } catch (Throwable t) {
            }
        } else if (v instanceof ScrollView) {
            if (smoothScrollByCompat(v, 0, px)) {
                return;
            }
            try {
                v.scrollBy(0, px);
            } catch (Throwable t) {
            }
        }
    }

    /** 反射调用 View.smoothScrollBy(int,int)（API 8+），成功返回 true */
    private static boolean smoothScrollByCompat(View v, int dx, int dy) {
        try {
            java.lang.reflect.Method m = View.class.getMethod("smoothScrollBy", int.class, int.class);
            m.invoke(v, dx, dy);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 每格表冠滚动约容器高的 1/3（最小 48px）；表冠向下转（delta 为负）= 向内容尾部滚 */
    private static int scrollAmount(View v, float delta) {
        int base = (v instanceof HorizontalScrollView) ? v.getWidth() : v.getHeight();
        int amt = Math.round(Math.abs(delta) * Math.max(base, 0) / 3f);
        if (amt < 48) {
            amt = 48;
        }
        return delta > 0f ? -amt : amt;
    }
}
