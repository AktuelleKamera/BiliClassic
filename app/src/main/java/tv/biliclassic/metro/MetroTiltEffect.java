package tv.biliclassic.metro;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import tv.biliclassic.util.SdkHelper;

/**
 * Metro 主题通用"重力/加速仪"微动效果。
 * 全部通过反射访问 android.hardware（Sensor/传感器的注册与回调），类中无任何对
 * android.hardware.* 的静态类型引用，故严格 dalvik / 老设备不会因校验而崩溃。
 * 优先陀螺仪（TYPE_GYROSCOPE，反射读取常量，失败回退 4），不可用则回退加速度计；
 * 无传感器则自动不启动。
 * 平移：API 11+ 用 setTranslationX/Y；API 8（Android 2.x）用 offsetLeftAndRight/offsetTopAndBottom。
 */
public class MetroTiltEffect {

    private final View mTarget;
    private final float mMaxShift;
    private final Context mContext;

    private Object mSensorManager;
    private Object mSensor;
    private Object mListener;
    private Method mUnregister;
    private java.lang.reflect.Field mEventValues;

    private boolean mStarted;
    // API<11 用 offset，记录当前已偏移量
    private int mAppliedX = 0;
    private int mAppliedY = 0;
    // 节流：记录上次实际采用的偏移与时间，避免高频 redraw（旧卡/残留）
    private int mLastX = 0;
    private int mLastY = 0;
    private long mLastTime = 0;
    // 加速度计（重力）优先，可保持稳定偏移；陀螺仪仅作为无加速计时的回退（仅运动时动）
    private boolean mUsingAccel;
    private boolean mHasBase;
    private float mBaseX, mBaseY;      // 静止基准（采集到时的重力分量）
    private float mFilteredX, mFilteredY; // 低通滤波后的偏移（-1..1）
    private static final float ALPHA = 0.5f; // 越接近 1 越跟手、越平滑；越小越迟钝
    private static final float SENSITIVITY = 4f; // 越大越"迟钝"，越小倾斜一点点就满幅
    private static final float CLAMP = 1.0f; // 位移上限（超过即饱和）
    private long mMinInterval = 16L; // 现代机 60fps；老机（软件渲染）33ms 以免卡
    private static final int MIN_DELTA_PX = 1; // 偏移变化小于该值时不刷
    private static final String TAG = "MetroTiltEffect";

    public MetroTiltEffect(Context context, View target, float maxShiftPx) {
        mContext = context;
        mTarget = target;
        mMaxShift = maxShiftPx;
        // API 11+ 硬件加速可高频刷新；API< 11（软件渲染）降频防卡
        if (SdkHelper.getSdkInt() < 11) {
            mMinInterval = 33L;
        } else {
            mMinInterval = 16L;
        }
    }

    /** 启动（反射；无传感器/无框架则不做任何事）。返回是否成功启动。 */
    public boolean start() {
        if (mStarted) return true;
        if (mTarget == null || mContext == null) return false;
        try {
            Class<?> clsSensor = Class.forName("android.hardware.Sensor");
            Class<?> clsSM = Class.forName("android.hardware.SensorManager");
            Class<?> clsSEL = Class.forName("android.hardware.SensorEventListener");
            Class<?> clsEvent = Class.forName("android.hardware.SensorEvent");

            int gyroType = readInt(clsSensor, "TYPE_GYROSCOPE", 4);
            int accelType = readInt(clsSensor, "TYPE_ACCELEROMETER", 1);
            int delayUi = readInt(clsSM, "SENSOR_DELAY_UI", 0);

            mSensorManager = mContext.getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager == null) return false;

            Method getDefault = clsSM.getMethod("getDefaultSensor", Integer.TYPE);
            Method register = clsSM.getMethod("registerListener", clsSEL, clsSensor, Integer.TYPE);
            mUnregister = clsSM.getMethod("unregisterListener", clsSEL);

            mSensor = getDefault.invoke(mSensorManager, accelType);
            mUsingAccel = mSensor != null;
            if (mSensor == null) {
                mSensor = getDefault.invoke(mSensorManager, gyroType);
                mUsingAccel = false;
            }
            if (mSensor == null) {
                Log.w(TAG, "no sensor (accel/gyro) on this device");
                return false;
            }

            mEventValues = clsEvent.getField("values");

            mListener = Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{clsSEL},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("onSensorChanged".equals(name) && args != null && args.length > 0 && args[0] != null) {
                                handleEvent(args[0]);
                                return null;
                            }
                            if ("hashCode".equals(name)) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(name)) {
                                return proxy == args[0];
                            }
                            if ("toString".equals(name)) {
                                return "MetroTiltSensor@" + Integer.toHexString(System.identityHashCode(proxy));
                            }
                            return null;
                        }
                    });

            register.invoke(mSensorManager, mListener, mSensor, delayUi);
            mStarted = true;
            mAppliedX = 0;
            mAppliedY = 0;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "failed to start sensor", t);
            return false;
        }
    }

    public void stop() {
        if (mStarted) {
            try {
                if (mUnregister != null && mListener != null) {
                    mUnregister.invoke(mSensorManager, mListener);
                }
            } catch (Throwable t) {
                // ignore
            }
        }
        mStarted = false;
    }

    private int readInt(Class<?> cls, String field, int fallback) {
        try {
            return cls.getField(field).getInt(null);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 来自 SensorEvent 的回调（values 通过反射读取），转成微移。 */
    private void handleEvent(Object event) {
        if (mTarget == null || mEventValues == null || event == null) return;
        final float[] values;
        try {
            values = (float[]) mEventValues.get(event);
        } catch (Throwable t) {
            return;
        }
        if (values == null || values.length < 2) return;

        final float vx = values[0];
        final float vy = values[1];
        final float nx;
        final float ny;
        if (mUsingAccel) {
            // 加速度计：以采集到的静止姿态为基准，倾斜产生稳定偏移
            if (!mHasBase) {
                mBaseX = vx;
                mBaseY = vy;
                mHasBase = true;
            }
            nx = clamp((vx - mBaseX) / SENSITIVITY);
            ny = clamp((vy - mBaseY) / SENSITIVITY);
        } else {
            // 陀螺仪：仅运动时产生偏移（回退方案）
            nx = clamp(vx / SENSITIVITY);
            ny = clamp(vy / SENSITIVITY);
        }
        mFilteredX += (nx - mFilteredX) * ALPHA;
        mFilteredY += (ny - mFilteredY) * ALPHA;

        final int dxPx = (int) (mFilteredX * mMaxShift);
        final int dyPx = (int) (mFilteredY * mMaxShift);

        // 节流：明显变化 + 距离上次足够久，才真正移动（否则高频 redraw → 卡/残留）
        long now = SystemClock.uptimeMillis();
        if (Math.abs(dxPx - mLastX) < MIN_DELTA_PX && Math.abs(dyPx - mLastY) < MIN_DELTA_PX) {
            return;
        }
        if (mLastTime != 0 && now - mLastTime < mMinInterval) {
            return;
        }
        mLastTime = now;
        mLastX = dxPx;
        mLastY = dyPx;

        final View t = mTarget;
        t.post(new Runnable() {
            @Override
            public void run() {
                if (t == null) return;
                if (SdkHelper.getSdkInt() >= 11) {
                    t.setTranslationX(dxPx);
                    t.setTranslationY(dyPx);
                } else {
                    // 用绝对 layout 定位（基准 = 当前位置 - 上次已偏移），避免翻页等重排冲掉偏移
                    applyOffset(t, dxPx, dyPx, mAppliedX, mAppliedY);
                }
            }
        });
    }

    /** API<11：以"当前布局位置 - 上次已偏移"为基准，把目标绝对放到 base+shift。 */
    private void applyOffset(View v, int dx, int dy, int prevX, int prevY) {
        if (v == null) return;
        int baseLeft = v.getLeft() - prevX;
        int baseTop = v.getTop() - prevY;
        int w = v.getWidth();
        int h = v.getHeight();
        v.layout(baseLeft + dx, baseTop + dy, baseLeft + w + dx, baseTop + h + dy);
        mAppliedX = dx;
        mAppliedY = dy;
        v.invalidate();
    }

    /** 翻页等引起重排后，把上次微动偏移重新施加到目标，保证翻页时也用"微动位置"。 */
    public void reapply() {
        if (mTarget == null || !mStarted) return;
        int x = mLastX;
        int y = mLastY;
        if (SdkHelper.getSdkInt() >= 11) {
            mTarget.setTranslationX(x);
            mTarget.setTranslationY(y);
        } else {
            applyOffset(mTarget, x, y, mAppliedX, mAppliedY);
        }
    }

    private float clamp(float v) {
        if (v > CLAMP) v = CLAMP;
        if (v < -CLAMP) v = -CLAMP;
        return v;
    }
}
