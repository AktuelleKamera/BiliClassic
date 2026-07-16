package tv.biliclassic.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.ImageView;

public class PhotoView extends ImageView {

    private Matrix mMatrix = new Matrix();
    private float mScale = 1.0f;
    private float mMaxScale = 3.0f;
    private float mMinScale = 0.5f;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mPosX;
    private float mPosY;
    private boolean mIsDragging = false;
    private int mViewWidth;
    private int mViewHeight;
    private int mImageWidth;
    private int mImageHeight;

    // 缩放手势检测器
    private Object mScaleDetector = null;
    private float mScaleFocusX = 0;
    private float mScaleFocusY = 0;
    private float mScaleStartDistance = 0;
    private float mScaleStartScale = 1.0f;
    private float mScaleStartPosX = 0;
    private float mScaleStartPosY = 0;

    // 反射缓存
    private static java.lang.reflect.Method sGetPointerCountMethod = null;
    private static java.lang.reflect.Method sGetXMethod = null;
    private static java.lang.reflect.Method sGetYMethod = null;
    private static boolean sReflectionInit = false;

    // 按键缩放步进值（每次按键缩放 0.1 倍）
    private static final float KEY_SCALE_STEP = 0.1f;

    private static void initReflection() {
        if (sReflectionInit) return;
        try {
            Class<?> motionEventClass = Class.forName("android.view.MotionEvent");
            try {
                sGetPointerCountMethod = motionEventClass.getMethod("getPointerCount");
            } catch (Exception e) {}
            try {
                sGetXMethod = motionEventClass.getMethod("getX");
            } catch (Exception e) {}
            try {
                sGetYMethod = motionEventClass.getMethod("getY");
            } catch (Exception e) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
        sReflectionInit = true;
    }

    public PhotoView(Context context) {
        super(context);
        init();
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public PhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public PhotoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
        setClickable(true);
        initReflection();
        // 尝试初始化 ScaleGestureDetector（Android 2.2+）
        try {
            Class.forName("android.view.ScaleGestureDetector");
            Class<?> detectorClass = Class.forName("android.view.ScaleGestureDetector");
            java.lang.reflect.Constructor<?> constructor = detectorClass.getConstructor(
                    Context.class,
                    Class.forName("android.view.ScaleGestureDetector$OnScaleGestureListener")
            );
            Object listener = createScaleListener();
            mScaleDetector = constructor.newInstance(getContext(), listener);
        } catch (Exception e) {
            mScaleDetector = null;
        }
    }

    // 通过反射创建 OnScaleGestureListener
    private Object createScaleListener() {
        try {
            Class<?> listenerClass = Class.forName("android.view.ScaleGestureDetector$OnScaleGestureListener");
            return java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            String name = method.getName();
                            try {
                                if ("onScale".equals(name)) {
                                    Object detector = args[0];
                                    float scaleFactor = (Float) detector.getClass().getMethod("getScaleFactor").invoke(detector);
                                    float focusX = (Float) detector.getClass().getMethod("getFocusX").invoke(detector);
                                    float focusY = (Float) detector.getClass().getMethod("getFocusY").invoke(detector);
                                    onScale(scaleFactor, focusX, focusY);
                                    return true;
                                } else if ("onScaleBegin".equals(name)) {
                                    Object detector = args[0];
                                    float focusX = (Float) detector.getClass().getMethod("getFocusX").invoke(detector);
                                    float focusY = (Float) detector.getClass().getMethod("getFocusY").invoke(detector);
                                    onScaleBegin(focusX, focusY);
                                    return true;
                                } else if ("onScaleEnd".equals(name)) {
                                    onScaleEnd();
                                    return null;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                    }
            );
        } catch (Exception e) {
            return null;
        }
    }

    // 兼容的 MotionEvent 方法

    private int getPointerCount(MotionEvent event) {
        if (sGetPointerCountMethod != null) {
            try {
                return (Integer) sGetPointerCountMethod.invoke(event);
            } catch (Exception e) {}
        }
        return 1;
    }

    private float getX(MotionEvent event) {
        if (sGetXMethod != null) {
            try {
                return (Float) sGetXMethod.invoke(event);
            } catch (Exception e) {}
        }
        return event.getX();
    }

    private float getY(MotionEvent event) {
        if (sGetYMethod != null) {
            try {
                return (Float) sGetYMethod.invoke(event);
            } catch (Exception e) {}
        }
        return event.getY();
    }

    private float getPointerX(MotionEvent event, int index) {
        if (index == 0) {
            return getX(event);
        }
        try {
            java.lang.reflect.Method method = MotionEvent.class.getMethod("getX", int.class);
            return (Float) method.invoke(event, index);
        } catch (Exception e) {
            return getX(event);
        }
    }

    private float getPointerY(MotionEvent event, int index) {
        if (index == 0) {
            return getY(event);
        }
        try {
            java.lang.reflect.Method method = MotionEvent.class.getMethod("getY", int.class);
            return (Float) method.invoke(event, index);
        } catch (Exception e) {
            return getY(event);
        }
    }

    private float getFingerDistance(MotionEvent event) {
        int count = getPointerCount(event);
        if (count < 2) return 0;
        float x1 = getPointerX(event, 0);
        float y1 = getPointerY(event, 0);
        float x2 = getPointerX(event, 1);
        float y2 = getPointerY(event, 1);
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void getFingerCenter(MotionEvent event, float[] center) {
        int count = getPointerCount(event);
        if (count < 2) {
            center[0] = getX(event);
            center[1] = getY(event);
            return;
        }
        center[0] = (getPointerX(event, 0) + getPointerX(event, 1)) / 2;
        center[1] = (getPointerY(event, 0) + getPointerY(event, 1)) / 2;
    }

    private void onScale(float scaleFactor, float focusX, float focusY) {
        if (mImageWidth == 0 || mImageHeight == 0) return;

        float newScale = mScale * scaleFactor;
        if (newScale < mMinScale) {
            newScale = mMinScale;
        }
        if (newScale > mMaxScale) {
            newScale = mMaxScale;
        }

        if (newScale != mScale) {
            float imageX = (focusX - mPosX) / mScale;
            float imageY = (focusY - mPosY) / mScale;
            mScale = newScale;
            mPosX = focusX - imageX * mScale;
            mPosY = focusY - imageY * mScale;
            boundPosition();
            updateMatrix();
        }
    }

    private void onScaleBegin(float focusX, float focusY) {
        mScaleStartPosX = mPosX;
        mScaleStartPosY = mPosY;
        mScaleStartScale = mScale;
        mScaleFocusX = focusX;
        mScaleFocusY = focusY;
    }

    private void onScaleEnd() {
        // 缩放结束
    }

    // 手动处理双指缩放
    private boolean handlePinch(MotionEvent event) {
        int count = getPointerCount(event);
        if (count < 2) {
            return false;
        }

        int action = event.getAction() & MotionEvent.ACTION_MASK;

        if (mScaleDetector == null) {
            float[] center = new float[2];
            getFingerCenter(event, center);
            float focusX = center[0];
            float focusY = center[1];

            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    mScaleStartDistance = getFingerDistance(event);
                    mScaleStartScale = mScale;
                    mScaleStartPosX = mPosX;
                    mScaleStartPosY = mPosY;
                    mScaleFocusX = focusX;
                    mScaleFocusY = focusY;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float currentDistance = getFingerDistance(event);
                    if (currentDistance > 0 && mScaleStartDistance > 0) {
                        float scaleFactor = currentDistance / mScaleStartDistance;
                        float newScale = mScaleStartScale * scaleFactor;
                        if (newScale < mMinScale) newScale = mMinScale;
                        if (newScale > mMaxScale) newScale = mMaxScale;
                        if (newScale != mScale) {
                            float imageX = (mScaleFocusX - mPosX) / mScale;
                            float imageY = (mScaleFocusY - mPosY) / mScale;
                            mScale = newScale;
                            mPosX = mScaleFocusX - imageX * mScale;
                            mPosY = mScaleFocusY - imageY * mScale;
                            boundPosition();
                            updateMatrix();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mScaleStartDistance = 0;
                    return true;
            }
            return false;
        }

        try {
            java.lang.reflect.Method method = mScaleDetector.getClass().getMethod("onTouchEvent", MotionEvent.class);
            return (Boolean) method.invoke(mScaleDetector, event);
        } catch (Exception e) {
            return false;
        }
    }

    // 按键缩放（方向键上下）

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 只有图片加载完成后才响应按键
        if (mImageWidth == 0 || mImageHeight == 0) {
            return super.onKeyDown(keyCode, event);
        }

        boolean isZoomIn = false;
        boolean isZoomOut = false;

        // DPAD_UP 放大，DPAD_DOWN 缩小
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            isZoomIn = true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            isZoomOut = true;
        } else {
            return super.onKeyDown(keyCode, event);
        }

        // 计算缩放中心点（屏幕中心）
        float focusX = mViewWidth / 2.0f;
        float focusY = mViewHeight / 2.0f;

        float newScale = mScale;
        if (isZoomIn) {
            newScale = mScale + KEY_SCALE_STEP;
        } else if (isZoomOut) {
            newScale = mScale - KEY_SCALE_STEP;
        }

        // 限制缩放范围
        if (newScale < mMinScale) {
            newScale = mMinScale;
        }
        if (newScale > mMaxScale) {
            newScale = mMaxScale;
        }

        if (newScale != mScale) {
            // 以屏幕中心为缩放点
            float imageX = (focusX - mPosX) / mScale;
            float imageY = (focusY - mPosY) / mScale;
            mScale = newScale;
            mPosX = focusX - imageX * mScale;
            mPosY = focusY - imageY * mScale;
            boundPosition();
            updateMatrix();
            return true;
        }

        return true;
    }

    public void setMaximumScale(float maxScale) {
        mMaxScale = maxScale;
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        if (bm != null) {
            mImageWidth = bm.getWidth();
            mImageHeight = bm.getHeight();
            fitCenter();
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null) {
            mImageWidth = drawable.getIntrinsicWidth();
            mImageHeight = drawable.getIntrinsicHeight();
            fitCenter();
        }
    }

    private void fitCenter() {
        if (mImageWidth == 0 || mImageHeight == 0 || mViewWidth == 0 || mViewHeight == 0) {
            return;
        }
        float scaleX = (float) mViewWidth / mImageWidth;
        float scaleY = (float) mViewHeight / mImageHeight;
        mScale = Math.min(scaleX, scaleY);
        mMinScale = mScale;
        mPosX = (mViewWidth - mImageWidth * mScale) / 2;
        mPosY = (mViewHeight - mImageHeight * mScale) / 2;
        updateMatrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mViewWidth = w;
        mViewHeight = h;
        if (mImageWidth > 0 && mImageHeight > 0) {
            fitCenter();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int count = getPointerCount(event);
        if (count >= 2) {
            if (handlePinch(event)) {
                return true;
            }
        }

        if (count == 1) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            float x = getX(event);
            float y = getY(event);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mLastTouchX = x;
                    mLastTouchY = y;
                    mIsDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!mIsDragging && (Math.abs(x - mLastTouchX) > 10 || Math.abs(y - mLastTouchY) > 10)) {
                        mIsDragging = true;
                    }
                    if (mIsDragging) {
                        float dx = x - mLastTouchX;
                        float dy = y - mLastTouchY;
                        mPosX += dx;
                        mPosY += dy;
                        boundPosition();
                        mLastTouchX = x;
                        mLastTouchY = y;
                        updateMatrix();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mIsDragging = false;
                    break;
            }
        }
        return super.onTouchEvent(event);
    }

    private void boundPosition() {
        if (mImageWidth == 0 || mImageHeight == 0) return;
        float displayWidth = mImageWidth * mScale;
        float displayHeight = mImageHeight * mScale;
        if (displayWidth <= mViewWidth) {
            mPosX = (mViewWidth - displayWidth) / 2;
        } else {
            if (mPosX > 0) mPosX = 0;
            if (mPosX < mViewWidth - displayWidth) mPosX = mViewWidth - displayWidth;
        }
        if (displayHeight <= mViewHeight) {
            mPosY = (mViewHeight - displayHeight) / 2;
        } else {
            if (mPosY > 0) mPosY = 0;
            if (mPosY < mViewHeight - displayHeight) mPosY = mViewHeight - displayHeight;
        }
    }

    private void updateMatrix() {
        mMatrix.reset();
        mMatrix.postScale(mScale, mScale);
        mMatrix.postTranslate(mPosX, mPosY);
        setImageMatrix(mMatrix);
    }
}