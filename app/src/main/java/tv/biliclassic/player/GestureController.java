package tv.biliclassic.player;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import tv.biliclassic.R;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import util.AudioManagerHelper;
import util.BrightnessHelper;
import util.PlayerToastMessageViewHolder;
import android.view.ScaleGestureDetector;
import tv.biliclassic.util.SdkHelper;

public class GestureController {

    private static final String TAG = "GestureController";

    private Activity mActivity;
    private Handler mHandler;
    private View mGestureView;
    private View mTouchingView;
    private View mBrightnessDimView;
    private ViewGroup mBrightnessBar;
    private ViewGroup mVolumeBar;
    private ProgressBar mBrightnessLevel;
    private ProgressBar mVolumeLevel;

    private GestureDetector mGestureScanner;
    private ScaleGestureDetector mScaleDetector;

    private int mGestureWidth;
    private int mGestureHeight;
    private int mBrightnessLevelStart;
    private int mLastBrightnessLevel = -1;
    private int mVolumeStart;
    private int mTouchSlop;

    // 方向锁定：一旦某次手势确定是水平/垂直，本次手势内不再翻转
    private boolean mDirectionLocked;
    private boolean mDirectionHorizontal;

    private boolean mInGestureSeekingMode;
    private boolean mInHorizontalMoving;
    private boolean mInVerticalMoving;
    private boolean enableGesture = true;
    private boolean isLiveStream;
    private int mSeekBarStartProgress;
    private int mSeekbarProgress;
    private int mSeekBeginPosition;
    private int mMaxSeekableValue = -1;
    private int mDuration = 0;

    private SeekBar mSeekBar;
    private TextView mTvCurrentTime;
    // true = 注入的 SeekBar 进度单位是毫秒（如 Ostwind 的 setMax(dur)）；
    // false = 千分制 0~1000（完整版 BiliPlayer）
    private boolean mSeekBarUsesMillis = false;

    private PlayerToastMessageViewHolder mToastViewHolder;
    private String mProgreesFmt;

    private GestureListener mGestureListener;

    // 长按加速相关
    private static final int LONG_PRESS_TIMEOUT = 500;
    private Handler mLongPressHandler = new Handler();
    private boolean mIsLongPressed = false;
    private float mCurrentSpeed = 1.0f;
    private Object mMediaPlayer;
    private int mDecoderType;

    // 速度提示 View
    private FrameLayout mSpeedTipContainer;
    private TextView mSpeedTipText;
    private boolean mSpeedTipShowing = false;

    // 缩放相关
    private float mCurrentScale = 1.0f;
    private float mMinScale = 1.0f;
    private float mMaxScale = 3.0f;
    private boolean mIsPinching = false;
    private OnScaleChangeListener mScaleChangeListener;

    // 平移相关
    private float mTranslateX = 0;
    private float mTranslateY = 0;
    private float mLastTouchX = 0;
    private float mLastTouchY = 0;
    private float mDragStartX = 0;
    private float mDragStartY = 0;
    private boolean mIsDragging = false;

    // 手势互斥状态
    private boolean mIsScaling = false;
    private boolean mIsSeeking = false;
    private boolean mIsAdjustingBrightness = false;
    private boolean mIsAdjustingVolume = false;
    private boolean mIsLongPressing = false;

    private Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!enableGesture || mIsScaling || mIsPinching || mIsSeeking ||
                    mIsAdjustingBrightness || mIsAdjustingVolume ||
                    mInHorizontalMoving || mInVerticalMoving) {
                return;
            }
            if (!mIsLongPressed) {
                if (mDecoderType == 0 && SdkHelper.getSdkInt() < 23) return;
                mIsLongPressed = true;
                mIsLongPressing = true;
                Log.d(TAG, "长按触发，设置 2.0x 加速");
                setPlaybackSpeed(2.0f);
                showSpeedTip(2.0f);
            }
        }
    };

    // 缩放回调接口
    public interface OnScaleChangeListener {
        void onScaleChange(float scale, float translateX, float translateY);
        void onScaleReset();
    }

    public interface OnGestureActionListener {
        void onToggleControls();
        void onTogglePlayPause();
        void onSeekTo(long position);
    }

    private OnGestureActionListener mListener;

    private Runnable mHideBarsRunnable = new Runnable() {
        public void run() {
            if (mBrightnessBar != null) mBrightnessBar.setVisibility(View.GONE);
            if (mVolumeBar != null) mVolumeBar.setVisibility(View.GONE);
        }
    };

    private Runnable mHideSpeedTipRunnable = new Runnable() {
        @Override
        public void run() {
            hideSpeedTip();
        }
    };

    public GestureController(Activity activity, Handler handler, View rootView, OnGestureActionListener listener) {
        mActivity = activity;
        mHandler = handler;
        mListener = listener;
        mProgreesFmt = activity.getString(R.string.PlayerController_toast_message_play_progress_fmt);
        mTouchSlop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();

        mGestureView = rootView.findViewById(R.id.controller_underlay);
        // SurfaceView 硬解走硬件 overlay 时 window.screenBrightness 不生效，
        // 若布局提供了黑色遮罩层则用 alpha 模拟亮度
        mBrightnessDimView = rootView.findViewById(R.id.brightness_dim_layer);
        if (mBrightnessDimView != null) {
            // 注意：API<9（Ostwind 目标设备）没有 View.setAlpha，XML 的 android:alpha
            // 会被忽略导致遮罩全黑。默认必须隐藏遮罩（不调亮度=不遮挡），
            // 并保证背景 alpha 透明。
            try {
                mBrightnessDimView.setVisibility(View.GONE);
                android.graphics.drawable.Drawable bg = mBrightnessDimView.getBackground();
                if (bg != null) {
                    bg.setAlpha(0);
                }
            } catch (Throwable t) {
            }
        }
        View barsGroup = rootView.findViewById(R.id.vertically_bars_group);
        if (barsGroup != null) {
            mBrightnessBar = (ViewGroup) barsGroup.findViewById(R.id.brightness_bar);
            mVolumeBar = (ViewGroup) barsGroup.findViewById(R.id.volume_bar);
            if (mBrightnessBar != null) {
                mBrightnessLevel = (ProgressBar) mBrightnessBar.findViewById(R.id.brightness_level);
            }
            if (mVolumeBar != null) {
                mVolumeLevel = (ProgressBar) mVolumeBar.findViewById(R.id.volume_level);
            }
        }

        mSeekBar = (SeekBar) rootView.findViewById(R.id.seekbar);
        mTvCurrentTime = (TextView) rootView.findViewById(R.id.time_current);
        mToastViewHolder = new PlayerToastMessageViewHolder();

        initSpeedTipView(rootView);

        mScaleDetector = new android.view.ScaleGestureDetector(mActivity,
                new android.view.ScaleGestureDetector.OnScaleGestureListener() {
                    public boolean onScale(android.view.ScaleGestureDetector detector) {
                        float scaleFactor = detector.getScaleFactor();
                        float newScale = mCurrentScale * scaleFactor;

                        if (newScale < mMinScale) newScale = mMinScale;
                        if (newScale > mMaxScale) newScale = mMaxScale;

                        if (newScale != mCurrentScale) {
                            mCurrentScale = newScale;
                            mIsScaling = true;
                            // 缩放时清空平移：放大应居中，避免叠加之前单指拖动残留的
                            // translate 导致画面偏移（"放大往左上角移出屏幕"）。
                            mTranslateX = 0;
                            mTranslateY = 0;
                            if (mScaleChangeListener != null) {
                                mScaleChangeListener.onScaleChange(mCurrentScale, mTranslateX, mTranslateY);
                            }
                        }
                        return true;
                    }

                    public boolean onScaleBegin(android.view.ScaleGestureDetector detector) {
                        mIsPinching = true;
                        mIsScaling = true;
                        mLongPressHandler.removeCallbacks(mLongPressRunnable);
                        return true;
                    }

                    public void onScaleEnd(android.view.ScaleGestureDetector detector) {
                        mIsPinching = false;
                        mIsScaling = false;
                    }
                }
        );

        setupGestureDetector();
    }

    // 初始化速度提示 View
    private void initSpeedTipView(View rootView) {
        FrameLayout parent = (FrameLayout) rootView.findViewById(android.R.id.content);
        if (parent == null) {
            parent = (FrameLayout) mActivity.findViewById(android.R.id.content);
        }
        if (parent == null) return;

        mSpeedTipContainer = new FrameLayout(mActivity);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        containerParams.topMargin = dpToPx(80);
        mSpeedTipContainer.setLayoutParams(containerParams);
        mSpeedTipContainer.setVisibility(View.GONE);

        mSpeedTipText = new TextView(mActivity);
        mSpeedTipText.setTextColor(0xFFFFFFFF);
        mSpeedTipText.setTextSize(24);
        mSpeedTipText.setGravity(Gravity.CENTER);
        mSpeedTipText.setBackgroundColor(0x88000000);
        int paddingH = dpToPx(24);
        int paddingV = dpToPx(8);
        mSpeedTipText.setPadding(paddingH, paddingV, paddingH, paddingV);
        mSpeedTipContainer.addView(mSpeedTipText);

        parent.addView(mSpeedTipContainer);
    }

    private int dpToPx(int dp) {
        float density = mActivity.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    // 显示速度提示
    private void showSpeedTip(float speed) {
        if (mSpeedTipContainer == null || mSpeedTipText == null) return;
        String text = String.format("%.1fx", speed);
        mSpeedTipText.setText(text);
        mSpeedTipContainer.setVisibility(View.VISIBLE);
        mSpeedTipShowing = true;
        mHandler.removeCallbacks(mHideSpeedTipRunnable);
    }

    // 隐藏速度提示
    private void hideSpeedTip() {
        if (mSpeedTipContainer != null) {
            mSpeedTipContainer.setVisibility(View.GONE);
        }
        mSpeedTipShowing = false;
        mHandler.removeCallbacks(mHideSpeedTipRunnable);
    }

    // 取消长按计时（手势开始时调用）
    private void cancelLongPress() {
        mLongPressHandler.removeCallbacks(mLongPressRunnable);
        mIsLongPressed = false;
        if (mCurrentSpeed != 1.0f) {
            setPlaybackSpeed(1.0f);
            hideSpeedTip();
        }
    }

    public void setEnableGesture(boolean enable) {
        this.enableGesture = enable;
        if (!enable) {
            cancelLongPress();
            mIsDragging = false;
            mIsPinching = false;
            mIsScaling = false;
        }
    }

    public void setLiveStream(boolean live) {
        this.isLiveStream = live;
    }

    public void setDuration(int duration) {
        this.mDuration = duration;
        Log.d(TAG, "setDuration: " + duration + "ms");
    }

    /** 注入 SeekBar / 时间 TextView（供复用布局 id 不一致的播放器，如 Ostwind） */
    public void setSeekBar(SeekBar seekBar) {
        this.mSeekBar = seekBar;
    }

    /** 声明注入的 SeekBar 是否以毫秒为单位（Ostwind 传 true，完整版默认 false=千分制） */
    public void setSeekBarUsesMillis(boolean usesMillis) {
        this.mSeekBarUsesMillis = usesMillis;
    }

    public void setCurrentTimeView(TextView tv) {
        this.mTvCurrentTime = tv;
    }

    public void setSeekBeginPosition(int position) {
        this.mSeekBeginPosition = position;
    }

    public void setMediaPlayer(Object mediaPlayer) {
        this.mMediaPlayer = mediaPlayer;
        Log.d(TAG, "setMediaPlayer: " + (mediaPlayer != null ? mediaPlayer.getClass().getSimpleName() : "null"));
    }

    public void setDecoderType(int decoderType) {
        this.mDecoderType = decoderType;
        String typeName;
        switch (decoderType) {
            case 0: typeName = "系统解码器"; break;
            case 1: typeName = "IJK硬解"; break;
            case 2: typeName = "软件解码器"; break;
            default: typeName = "未知";
        }
        Log.d(TAG, "setDecoderType: " + typeName);
    }

    public View getGestureView() {
        return mGestureView;
    }

    public boolean isGestureSeeking() {
        return mInGestureSeekingMode || mInHorizontalMoving || mInVerticalMoving;
    }

    public boolean isGestureEnabled() {
        return enableGesture;
    }

    public void release() {
        Log.d(TAG, "release");
        mGestureListener = null;
        mListener = null;
        if (mToastViewHolder != null) {
            mToastViewHolder.release();
            mToastViewHolder = null;
        }
        mLongPressHandler.removeCallbacksAndMessages(null);
        mHandler.removeCallbacks(mHideSpeedTipRunnable);
        hideSpeedTip();
        if (mSpeedTipContainer != null) {
            ViewGroup parent = (ViewGroup) mSpeedTipContainer.getParent();
            if (parent != null) {
                parent.removeView(mSpeedTipContainer);
            }
            mSpeedTipContainer = null;
            mSpeedTipText = null;
        }
    }

    private void setupGestureDetector() {
        if (mGestureView == null) {
            Log.w(TAG, "mGestureView is null");
            return;
        }

        int viewWidth = mGestureView.getWidth();
        int viewHeight = mGestureView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();
            viewWidth = dm.widthPixels;
            viewHeight = dm.heightPixels;
            Log.d(TAG, "使用 DisplayMetrics: " + viewWidth + "x" + viewHeight);
        }
        mGestureWidth = viewWidth;
        mGestureHeight = viewHeight;
        Log.d(TAG, "手势区域大小: " + mGestureWidth + "x" + mGestureHeight);

        mBrightnessLevelStart = 0;
        if (mBrightnessLevel != null) {
            mBrightnessLevelStart = 15;
        }

        mGestureListener = new GestureListener();
        mGestureScanner = new GestureDetector(mActivity, mGestureListener);

        mGestureView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                mTouchingView = v;
                // 先让 ScaleDetector 处理缩放
                if (mScaleDetector != null && event.getPointerCount() >= 2) {
                    mScaleDetector.onTouchEvent(event);
                    return true;
                }
                // 单指交给 GestureDetector
                if (mGestureScanner != null && event.getPointerCount() == 1) {
                    return mGestureScanner.onTouchEvent(event);
                }
                return false;
            }
        });

        View preloadingView = mGestureView.getRootView().findViewById(R.id.preloading_view);
        if (preloadingView != null) {
            preloadingView.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    mTouchingView = v;
                    if (mScaleDetector != null && event.getPointerCount() >= 2) {
                        mScaleDetector.onTouchEvent(event);
                        return true;
                    }
                    if (mGestureScanner != null && event.getPointerCount() == 1) {
                        return mGestureScanner.onTouchEvent(event);
                    }
                    return false;
                }
            });
        }
    }

    public void onOrientationChanged() {
        DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();
        boolean portrait = mActivity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (portrait) {
            mGestureWidth = Math.min(dm.widthPixels, dm.heightPixels);
            mGestureHeight = Math.max(dm.widthPixels, dm.heightPixels);
        } else {
            mGestureWidth = dm.widthPixels;
            mGestureHeight = dm.heightPixels;
        }
        Log.d(TAG, "方向改变，手势区域更新: " + mGestureWidth + "x" + mGestureHeight);
    }

    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            Log.d(TAG, "onTouchEvent: ACTION_UP/CANCEL, mIsLongPressed=" + mIsLongPressed);
            mIsSeeking = false;
            mIsAdjustingBrightness = false;
            mIsAdjustingVolume = false;
            mIsScaling = false;
            mIsPinching = false;

            if (mIsLongPressed) {
                Log.d(TAG, "恢复 1.0x 正常播放");
                setPlaybackSpeed(1.0f);
                mIsLongPressed = false;
                mIsLongPressing = false;
                showSpeedTip(1.0f);
                mHandler.postDelayed(mHideSpeedTipRunnable, 1000);
            } else {
                mLongPressHandler.removeCallbacks(mLongPressRunnable);
                if (mSpeedTipShowing) {
                    hideSpeedTip();
                }
            }
            handleGestureUp();
        }
        return false;
    }

    // 长按 2x 加速

    private void setPlaybackSpeed(float speed) {
        if (mMediaPlayer == null) return;
        if (mCurrentSpeed == speed) return;
        mCurrentSpeed = speed;

        if (mMediaPlayer instanceof IjkMediaPlayer) {
            try {
                ((IjkMediaPlayer) mMediaPlayer).setSpeed(speed);
                return;
            } catch (Exception e) {
                Log.e(TAG, "IJK setSpeed 失败: " + e.getMessage());
            }
        }

        if (SdkHelper.getSdkInt() >= 23) {
            try {
                Class<?> cls = mMediaPlayer.getClass();
                java.lang.reflect.Field[] fields = cls.getDeclaredFields();
                Object internalMediaPlayer = null;
                for (java.lang.reflect.Field f : fields) {
                    f.setAccessible(true);
                    Object value = f.get(mMediaPlayer);
                    if (value != null && value.getClass().getName().equals("android.media.MediaPlayer")) {
                        internalMediaPlayer = value;
                        Log.d(TAG, "找到内部 MediaPlayer 字段: " + f.getName());
                        break;
                    }
                }

                if (internalMediaPlayer == null) {
                    Log.w(TAG, "未找到内部 MediaPlayer");
                    return;
                }

                Class<?> mediaPlayerClass = Class.forName("android.media.MediaPlayer");
                Class<?> playbackParamsClass = Class.forName("android.media.PlaybackParams");

                java.lang.reflect.Constructor<?> constructor = playbackParamsClass.getConstructor();
                Object params = constructor.newInstance();

                java.lang.reflect.Method setSpeed = playbackParamsClass.getMethod("setSpeed", float.class);
                Object newParams = setSpeed.invoke(params, speed);

                java.lang.reflect.Method setPlaybackParams = mediaPlayerClass.getMethod("setPlaybackParams", playbackParamsClass);
                setPlaybackParams.invoke(internalMediaPlayer, newParams);

                Log.d(TAG, "系统解码器 setPlaybackParams(" + speed + ") 成功");
            } catch (Exception e) {
                Log.e(TAG, "系统解码器 setPlaybackParams 失败: " + e.getMessage());
            }
        }
    }

    private int getMaxSeekableValue() {
        if (mDuration <= 0) return 0;
        if (mMaxSeekableValue != -1) return mMaxSeekableValue;
        mMaxSeekableValue = 1000;
        Log.d(TAG, "getMaxSeekableValue: " + mMaxSeekableValue + " (duration=" + mDuration + ")");
        return mMaxSeekableValue;
    }

    // 缩放相关
    public void setOnScaleChangeListener(OnScaleChangeListener listener) {
        mScaleChangeListener = listener;
    }

    public void setMaxScale(float maxScale) {
        mMaxScale = maxScale;
    }

    public float getCurrentScale() {
        return mCurrentScale;
    }

    public float getTranslateX() {
        return mTranslateX;
    }

    public float getTranslateY() {
        return mTranslateY;
    }

    // 重置缩放
    public void resetScale() {
        if (mScaleChangeListener != null) {
            mCurrentScale = 1.0f;
            mTranslateX = 0;
            mTranslateY = 0;
            mScaleChangeListener.onScaleReset();
        }
    }

    // 拖拽平移处理
    private void handleDrag(MotionEvent event) {
        if (mCurrentScale <= 1.0f) {
            mIsDragging = false;
            return;
        }

        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchX = x;
                mLastTouchY = y;
                mDragStartX = mTranslateX;
                mDragStartY = mTranslateY;
                mIsDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) return;

                float dx = (x - mLastTouchX) / mGestureWidth;
                float dy = (y - mLastTouchY) / mGestureHeight;

                if (Math.abs(dx) > 0.001f || Math.abs(dy) > 0.001f) {
                    mIsDragging = true;
                }

                float newTranslateX = mDragStartX + dx;
                float newTranslateY = mDragStartY + dy;

                mTranslateX = newTranslateX;
                mTranslateY = newTranslateY;

                if (mScaleChangeListener != null) {
                    mScaleChangeListener.onScaleChange(mCurrentScale, mTranslateX, mTranslateY);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                break;
        }
    }

    /**
     * 取消长按计时（用于评论滑动时强制取消）
     */
    public void cancelLongPressForComment() {
        mLongPressHandler.removeCallbacks(mLongPressRunnable);
        mIsLongPressed = false;
        if (mCurrentSpeed != 1.0f) {
            setPlaybackSpeed(1.0f);
            hideSpeedTip();
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private Runnable mHideUIRunnable = new Runnable() {
            public void run() {
                if (mBrightnessBar != null) mBrightnessBar.setVisibility(View.GONE);
                if (mVolumeBar != null) mVolumeBar.setVisibility(View.GONE);
            }
        };

        public boolean onDown(MotionEvent e) {
            Log.d(TAG, "onDown");
            mIsPinching = false;
            // 新一次触摸：重置方向锁定，避免上次手势残留
            mDirectionLocked = false;
            mDirectionHorizontal = false;
            mInHorizontalMoving = false;
            mInVerticalMoving = false;
            mInGestureSeekingMode = false;
            mIsSeeking = false;
            if (!enableGesture) {
                return true;
            }
            updateCurrentPositionForGesture();
            hideBarControllers(0);
            startBrightnessChange();
            startVolumeChange();
            mLongPressHandler.removeCallbacks(mLongPressRunnable);
            mIsLongPressed = false;
            mLongPressHandler.postDelayed(mLongPressRunnable, LONG_PRESS_TIMEOUT);
            return true;
        }

        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.d(TAG, "onSingleTapConfirmed");
            mLongPressHandler.removeCallbacks(mLongPressRunnable);
            if (!enableGesture) {
                return true;
            }
            if (mInGestureSeekingMode || mInHorizontalMoving || mInVerticalMoving ||
                    mIsScaling || mIsPinching || mIsDragging) {
                return false;
            }
            if (mListener != null) {
                mListener.onToggleControls();
            }
            return true;
        }

        public boolean onDoubleTap(MotionEvent e) {
            Log.d(TAG, "onDoubleTap");
            mLongPressHandler.removeCallbacks(mLongPressRunnable);
            // 双击：清除一切手势 seek/方向状态，避免第二次点击的轻微位移被当作 seek 提交
            mInGestureSeekingMode = false;
            mIsSeeking = false;
            mInHorizontalMoving = false;
            mInVerticalMoving = false;
            mDirectionLocked = false;
            mDirectionHorizontal = false;
            if (!enableGesture) {
                return true;
            }
            if (mListener != null) {
                mListener.onTogglePlayPause();
            }
            return true;
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e1 == null || e2 == null) return false;
            if (!enableGesture) return false;

            if (mIsScaling || mIsPinching) {
                return false;
            }

            // 拖拽模式（缩放 > 1.0 时）
            if (mCurrentScale > 1.0f && e2.getPointerCount() == 1) {
                float dx = Math.abs(e2.getX() - e1.getX());
                float dy = Math.abs(e2.getY() - e1.getY());
                if (dx > 15 || dy > 15) {
                    mLongPressHandler.removeCallbacks(mLongPressRunnable);
                    mIsLongPressed = false;

                    float moveX = -distanceX / mGestureWidth;
                    float moveY = -distanceY / mGestureHeight;

                    mTranslateX += moveX;
                    mTranslateY += moveY;
                    mIsDragging = true;

                    if (mScaleChangeListener != null) {
                        mScaleChangeListener.onScaleChange(mCurrentScale, mTranslateX, mTranslateY);
                    }
                    return true;
                }
            }

            // 缩放为 1.0 时处理快进快退 / 亮度音量
            if (mCurrentScale <= 1.0f) {
                float startX = e1.getX();
                if (startX < mGestureWidth * 0.01f || startX > mGestureWidth * 0.95f) return true;
                float startY = e1.getY();
                if (startY < mGestureHeight * 0.1f || startY > mGestureHeight * 0.95f) return true;

                float totalDx = e2.getX() - e1.getX();
                float totalDy = e2.getY() - e1.getY();

                // 未锁定方向时：位移必须超过触摸 slop 才判定方向，
                // 否则（单击/双击抖动）一律不处理，避免误触发 seek
                if (!mDirectionLocked) {
                    float adx = Math.abs(totalDx);
                    float ady = Math.abs(totalDy);
                    if (adx < mTouchSlop && ady < mTouchSlop) {
                        return true;
                    }
                    // 需要明显优势（1.2 倍）才锁定方向：既避免垂直滑动起始抖动被误判成水平，
                    // 又不会因 1.5 倍门槛过严导致明显的水平滑动（尤其带轻微斜向时）永不锁定。
                    if (adx > ady * 1.2f) {
                        mDirectionLocked = true;
                        mDirectionHorizontal = true;
                        mInHorizontalMoving = true;
                        mInVerticalMoving = false;
                    } else if (ady > adx * 1.2f) {
                        mDirectionLocked = true;
                        mDirectionHorizontal = false;
                        mInHorizontalMoving = false;
                        mInVerticalMoving = true;
                    } else {
                        // 方向不明（接近对角）：暂不处理，等位移更大再判
                        return true;
                    }
                }

                if (mDirectionHorizontal) {
                    // 水平手势：快进快退（仅当不是直播）
                    if (isLiveStream) {
                        return true;
                    }
                    if (mIsAdjustingBrightness || mIsAdjustingVolume) {
                        return true;
                    }
                    mLongPressHandler.removeCallbacks(mLongPressRunnable);
                    onHorizontalMove(e1, e2, distanceX, distanceY);
                } else {
                    // 垂直手势：亮度/音量
                    mLongPressHandler.removeCallbacks(mLongPressRunnable);
                    onVerticalMove(e1, e2, distanceX, distanceY);
                }
            }
            return true;
        }

        // 处理水平滑动（快进快退）
        private void onHorizontalMove(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mInVerticalMoving || mIsAdjustingBrightness || mIsAdjustingVolume || mSeekBar == null) return;
            float deltaFactorX = (e1.getX() - e2.getX()) / (float) mGestureWidth;
            // 需要明确的水平拖动（>= 5% 屏宽）才开始进退，避免点击/双击抖动误触发
            if (Math.abs(deltaFactorX) >= 0.05f || mInGestureSeekingMode) {
                if (!mInGestureSeekingMode) {
                    mInGestureSeekingMode = true;
                    mIsSeeking = true;
                    mSeekBarStartProgress = mSeekBar.getProgress();
                    Log.d(TAG, "开始手势快进，起始进度: " + mSeekBarStartProgress);
                }
                int seekBarMax = mSeekBar.getMax();
                // 统一换算成毫秒基准
                long startMs = mSeekBarUsesMillis
                        ? mSeekBarStartProgress
                        : ((long) mSeekBarStartProgress) * mDuration / seekBarMax;
                // 快退快进手感：全屏滑动按 10 分钟换算（与 Ostwind 原版 GESTURE_SEEK_RANGE_MS 一致），
                // 避免全屏=整段视频导致轻轻一滑就跳到结尾。
                // 注意：千分制（BiliPlayer seekbar max=1000）这里必须换算成毫秒范围再乘以 deltaFactorX，
                // 不能直接用 1000（那是刻度范围，对长视频会算成只退 1 秒，导致"进退+0秒"）。
                long seekRangeMs = Math.min(mDuration > 0 ? mDuration : 600000L, 600000L);
                long targetMs = startMs - (long) (seekRangeMs * deltaFactorX);
                if (targetMs < 0) targetMs = 0;
                if (mDuration > 0 && targetMs > mDuration) targetMs = mDuration;
                // 写回 SeekBar 显示
                int progress = mSeekBarUsesMillis
                        ? (int) targetMs
                        : (int) (seekBarMax * targetMs / mDuration);
                mSeekbarProgress = progress;
                mSeekBar.setProgress(progress);
                if (mDuration > 0 && mTvCurrentTime != null) {
                    mTvCurrentTime.setText(formatTime((int) targetMs));
                }
                showSeekProgressHint((int) targetMs);
                if (!mInHorizontalMoving) mInHorizontalMoving = true;
            }
        }

        // 处理垂直滑动（亮度/音量调节）
        private void onVerticalMove(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mInHorizontalMoving || mIsSeeking) return;
            float startX1 = e1.getX();
            float startX2 = e2.getX();
            float left = mGestureWidth / 3f;
            float right = left * 2f;
            float deltaFactorY = (e1.getY() - e2.getY()) / (float) mGestureHeight;

            if (startX1 < left && startX2 < left) {
                mIsAdjustingBrightness = true;
                changeBrightness(deltaFactorY);
                if (!mInVerticalMoving) mInVerticalMoving = true;
            } else if (startX1 > right && startX2 > right) {
                mIsAdjustingVolume = true;
                changeVolume(deltaFactorY);
                if (!mInVerticalMoving) mInVerticalMoving = true;
            }
        }

        private void hideBarControllers(int delay) {
            mHandler.removeCallbacks(mHideUIRunnable);
            mHandler.postDelayed(mHideUIRunnable, delay);
        }
    }

    private void updateCurrentPositionForGesture() {
        if (mSeekBar != null) {
            mSeekBeginPosition = mSeekBar.getProgress();
        } else {
            mSeekBeginPosition = 0;
        }
    }

    private void startBrightnessChange() {
        if (mLastBrightnessLevel >= 0) {
            mBrightnessLevelStart = mLastBrightnessLevel;
        } else {
            mBrightnessLevelStart = 0;
            try {
                float b = BrightnessHelper.getScreenBrightness(mActivity);
                if (b >= 0) mBrightnessLevelStart = (int) Math.floor(b * 15f);
                if (mBrightnessLevelStart < 1) {
                    mBrightnessLevelStart = 1;
                }
            } catch (Exception e) {
                mBrightnessLevelStart = 15;
            }
        }
    }

    private void startVolumeChange() {
        mVolumeStart = AudioManagerHelper.getStreamVolume(mActivity, android.media.AudioManager.STREAM_MUSIC);
    }

    private void changeBrightness(float deltaFactorY) {
        int max = 15;
        int newLevel = (int) Math.floor(mBrightnessLevelStart + (0.8f * deltaFactorY * max));
        // 最低 1 级：只能调到接近 0（很暗），不能全黑
        newLevel = Math.min(Math.max(newLevel, 1), max);
        float brightness = newLevel / (float) max;
        if (mBrightnessDimView != null) {
            // 硬解 overlay：用黑色遮罩模拟亮度（1-亮度 为遮罩不透明度）。
            // 注意 API<9（Ostwind 目标设备）没有 View.setAlpha，必须用背景 Drawable.setAlpha（API 1）
            float dimAlpha = 1.0f - brightness;
            try {
                mBrightnessDimView.setVisibility(View.VISIBLE);
                android.graphics.drawable.Drawable bg = mBrightnessDimView.getBackground();
                if (bg != null) {
                    bg.setAlpha((int) (dimAlpha * 255));
                }
            } catch (Throwable t) {
            }
        } else {
            BrightnessHelper.setBrightness(mActivity, brightness);
        }
        mLastBrightnessLevel = newLevel;
        if (mBrightnessBar != null) {
            mBrightnessBar.setVisibility(View.VISIBLE);
        }
        if (mBrightnessLevel != null) {
            mBrightnessLevel.setMax(max);
            mBrightnessLevel.setProgress(newLevel);
        }
    }

    private void changeVolume(float deltaFactorY) {
        int max = AudioManagerHelper.getStreamMaxVolume(mActivity, android.media.AudioManager.STREAM_MUSIC);
        int newVol = (int) Math.floor(mVolumeStart + (1.5f * deltaFactorY * max));
        newVol = Math.min(Math.max(newVol, 0), max);
        AudioManagerHelper.setStreamVolume(mActivity, android.media.AudioManager.STREAM_MUSIC, newVol, 0);
        if (mVolumeBar != null) {
            mVolumeBar.setVisibility(View.VISIBLE);
        }
        if (mVolumeLevel != null) {
            mVolumeLevel.setMax(max);
            mVolumeLevel.setProgress(newVol);
        }
    }

    private void showSeekProgressHint(int progressMs) {
        if (mToastViewHolder == null) return;
        android.widget.FrameLayout rootView = (android.widget.FrameLayout)
                mActivity.findViewById(android.R.id.content);
        if (rootView == null) return;
        mToastViewHolder.initView(mActivity, rootView);

        int beginMs = mSeekBarUsesMillis
                ? mSeekBeginPosition
                : (int) (((long) mSeekBeginPosition) * mDuration / (mSeekBar != null ? mSeekBar.getMax() : 1000));
        String timeText = formatTime(progressMs);
        String durationText = formatTime(mDuration);

        long diff = (progressMs - beginMs) / 1000;
        String diffTime = (diff >= 0 ? "+" : "") + diff;

        String text = String.format(mProgreesFmt, timeText, durationText, diffTime);
        mToastViewHolder.show(text, 500000, false);
    }

    // 处理手势结束，重置所有状态
    private void handleGestureUp() {
        Log.d(TAG, "handleGestureUp");

        mIsSeeking = false;
        mIsAdjustingBrightness = false;
        mIsAdjustingVolume = false;
        mIsScaling = false;
        mIsPinching = false;
        mIsLongPressing = false;
        mIsDragging = false;
        mDirectionLocked = false;
        mDirectionHorizontal = false;
        mInHorizontalMoving = false;
        mInVerticalMoving = false;

        if ((mBrightnessBar != null && mBrightnessBar.isShown()) ||
                (mVolumeBar != null && mVolumeBar.isShown())) {
            mHandler.removeCallbacks(mHideBarsRunnable);
            mHandler.postDelayed(mHideBarsRunnable, 1000);
        }

        if (mInGestureSeekingMode) {
            mInGestureSeekingMode = false;
            if (mDuration > 0 && mListener != null) {
                long finalMs = mSeekBarUsesMillis
                        ? mSeekbarProgress
                        : ((long) mSeekbarProgress) * mDuration / (mSeekBar != null ? mSeekBar.getMax() : 1000);
                Log.d(TAG, "手势快进结束，跳转到: " + finalMs + "ms");
                mListener.onSeekTo(finalMs);
            }
        }
        mInHorizontalMoving = false;
        mInVerticalMoving = false;
        hideToastHint();
    }

    private void hideToastHint() {
        if (mToastViewHolder != null) {
            mToastViewHolder.dismiss();
        }
    }

    private String formatTime(int ms) {
        if (ms < 0) ms = 0;
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        int hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}