package tv.biliclassic.player;

import android.app.Activity;
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

public class GestureController {

    private static final String TAG = "GestureController";

    private Activity mActivity;
    private Handler mHandler;
    private View mGestureView;
    private View mTouchingView;
    private ViewGroup mBrightnessBar;
    private ViewGroup mVolumeBar;
    private ProgressBar mBrightnessLevel;
    private ProgressBar mVolumeLevel;

    private GestureDetector mGestureScanner;

    private int mGestureWidth;
    private int mGestureHeight;
    private int mBrightnessLevelStart;
    private int mLastBrightnessLevel = -1;
    private int mVolumeStart;

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

    private Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsLongPressed) {
                // API 23 以下的系统解码器不支持变速
                if (mDecoderType == 0 && Build.VERSION.SDK_INT < 23) return;
                mIsLongPressed = true;
                Log.d(TAG, "长按触发，设置 2.0x 加速");
                setPlaybackSpeed(2.0f);
                showSpeedTip(2.0f);
            }
        }
    };

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

        mGestureView = rootView.findViewById(R.id.controller_underlay);
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
        // 如果速度不是 1.0，恢复
        if (mCurrentSpeed != 1.0f) {
            setPlaybackSpeed(1.0f);
            hideSpeedTip();
        }
    }

    // 重新开始长按计时（手势结束时调用）
    private void restartLongPressTimer() {
        mLongPressHandler.removeCallbacks(mLongPressRunnable);
        mIsLongPressed = false;
        mLongPressHandler.postDelayed(mLongPressRunnable, LONG_PRESS_TIMEOUT);
    }

    public void setEnableGesture(boolean enable) {
        this.enableGesture = enable;
    }

    public void setLiveStream(boolean live) {
        this.isLiveStream = live;
    }

    public void setDuration(int duration) {
        this.mDuration = duration;
        Log.d(TAG, "setDuration: " + duration + "ms");
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
            case 2: typeName = "IJK软解"; break;
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
            viewWidth = Math.max(dm.widthPixels, dm.heightPixels);
            viewHeight = Math.min(dm.widthPixels, dm.heightPixels);
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
                if (mGestureScanner != null) {
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
                    if (mGestureScanner != null) {
                        return mGestureScanner.onTouchEvent(event);
                    }
                    return false;
                }
            });
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            Log.d(TAG, "onTouchEvent: ACTION_UP/CANCEL, mIsLongPressed=" + mIsLongPressed);
            if (mIsLongPressed) {
                Log.d(TAG, "恢复 1.0x 正常播放");
                setPlaybackSpeed(1.0f);
                mIsLongPressed = false;
                showSpeedTip(1.0f);
                // 延迟隐藏
                mHandler.postDelayed(mHideSpeedTipRunnable, 1000);
            } else {
                // 没有触发长按，取消计时
                mLongPressHandler.removeCallbacks(mLongPressRunnable);
                // 如果有速度提示显示，隐藏
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

        // IJK 直接支持
        if (mMediaPlayer instanceof IjkMediaPlayer) {
            try {
                ((IjkMediaPlayer) mMediaPlayer).setSpeed(speed);
                return;
            } catch (Exception e) {
                Log.e(TAG, "IJK setSpeed 失败: " + e.getMessage());
            }
        }

        // 系统解码器 (AndroidMediaPlayer)
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Class<?> cls = mMediaPlayer.getClass();
                // 遍历所有字段，找到 MediaPlayer 类型的
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

                // 对内部 MediaPlayer 调用 setPlaybackParams
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

    // GestureController 方法

    private int getMaxSeekableValue() {
        if (mDuration <= 0) return 0;
        if (mMaxSeekableValue != -1) return mMaxSeekableValue;
        float p = 90000.0f / mDuration;
        if (p > 1.0f) p = 1.0f;
        mMaxSeekableValue = (int) (1000.0f * p);
        Log.d(TAG, "getMaxSeekableValue: " + mMaxSeekableValue + " (duration=" + mDuration + ")");
        return mMaxSeekableValue;
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
            updateCurrentPositionForGesture();
            hideBarControllers(0);
            startBrightnessChange();
            startVolumeChange();
            // 启动长按计时
            mLongPressHandler.removeCallbacks(mLongPressRunnable);
            mIsLongPressed = false;
            mLongPressHandler.postDelayed(mLongPressRunnable, LONG_PRESS_TIMEOUT);
            return true;
        }

        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.d(TAG, "onSingleTapConfirmed");
            // 取消长按计时，防止干扰
            mLongPressHandler.removeCallbacks(mLongPressRunnable);
            if (mInGestureSeekingMode || mInHorizontalMoving || mInVerticalMoving) {
                return false;
            }
            if (mListener != null) {
                mListener.onToggleControls();
            }
            return true;
        }

        public boolean onDoubleTap(MotionEvent e) {
            Log.d(TAG, "onDoubleTap");
            // 取消长按计时
            mLongPressHandler.removeCallbacks(mLongPressRunnable);
            if (mListener != null) {
                mListener.onTogglePlayPause();
            }
            return true;
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e1 == null || e2 == null) return false;
            if (!enableGesture) return false;

            float startX = e1.getX();
            if (startX < mGestureWidth * 0.01f || startX > mGestureWidth * 0.95f) return true;
            float startY = e1.getY();
            if (startY < mGestureHeight * 0.1f || startY > mGestureHeight * 0.95f) return true;

            float moveDelta = Math.abs(distanceY) - Math.abs(distanceX);

            // 检测到垂直移动（亮度/音量调节）
            if (moveDelta > 0f) {
                // 取消长按计时
                mLongPressHandler.removeCallbacks(mLongPressRunnable);
                onVerticalMove(e1, e2, distanceX, distanceY);
            } else if (moveDelta < 0f && !isLiveStream) {
                // 水平移动（快进快退），取消长按计时
                mLongPressHandler.removeCallbacks(mLongPressRunnable);
                onHorizontalMove(e1, e2, distanceX, distanceY);
            }
            return true;
        }

        private void onHorizontalMove(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mInVerticalMoving || mSeekBar == null) return;
            float deltaFactorX = (e1.getX() - e2.getX()) / (float) mGestureWidth;
            if (Math.abs(deltaFactorX) >= 0.02f || mInGestureSeekingMode) {
                if (!mInGestureSeekingMode) {
                    mInGestureSeekingMode = true;
                    mSeekBarStartProgress = mSeekBar.getProgress();
                    Log.d(TAG, "开始手势快进，起始进度: " + mSeekBarStartProgress);
                }
                int maxSeekable = getMaxSeekableValue();
                mSeekbarProgress = (int) (mSeekBarStartProgress - (maxSeekable * deltaFactorX));
                mSeekbarProgress = Math.min(Math.max(mSeekbarProgress, 0), mSeekBar.getMax());
                mSeekBar.setProgress(mSeekbarProgress);
                if (mDuration > 0 && mTvCurrentTime != null) {
                    long newPosition = ((long) mSeekbarProgress) * mDuration / 1000;
                    mTvCurrentTime.setText(formatTime((int) newPosition));
                }
                showSeekProgressHint(mSeekbarProgress);
                if (!mInHorizontalMoving) mInHorizontalMoving = true;
            }
        }

        private void onVerticalMove(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mInHorizontalMoving) return;
            float startX1 = e1.getX();
            float startX2 = e2.getX();
            float left = mGestureWidth / 3f;
            float right = left * 2f;
            float deltaFactorY = (e1.getY() - e2.getY()) / (float) mGestureHeight;

            if (startX1 < left && startX2 < left) {
                changeBrightness(deltaFactorY);
                if (!mInVerticalMoving) mInVerticalMoving = true;
            } else if (startX1 > right && startX2 > right) {
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
        newLevel = Math.min(Math.max(newLevel, 0), max);
        float brightness = newLevel / (float) max;
        BrightnessHelper.setBrightness(mActivity, brightness);
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

    private void showSeekProgressHint(int progress) {
        if (mToastViewHolder == null) return;
        android.widget.FrameLayout rootView = (android.widget.FrameLayout)
                mActivity.findViewById(android.R.id.content);
        if (rootView == null) return;
        mToastViewHolder.initView(mActivity, rootView);

        int progressMs = (int) (((long) progress) * mDuration / 1000);
        int beginMs = (int) (((long) mSeekBeginPosition) * mDuration / 1000);
        String timeText = formatTime(progressMs);
        String durationText = formatTime(mDuration);

        long diff = (progressMs - beginMs) / 1000;
        String diffTime = (diff >= 0 ? "+" : "") + diff;

        String text = String.format(mProgreesFmt, timeText, durationText, diffTime);
        mToastViewHolder.show(text, 500000, false);
    }

    private void handleGestureUp() {
        Log.d(TAG, "handleGestureUp");
        if ((mBrightnessBar != null && mBrightnessBar.isShown()) ||
                (mVolumeBar != null && mVolumeBar.isShown())) {
            mHandler.removeCallbacks(mHideBarsRunnable);
            mHandler.postDelayed(mHideBarsRunnable, 1000);
        }

        if (mInGestureSeekingMode) {
            mInGestureSeekingMode = false;
            if (mDuration > 0 && mListener != null) {
                long finalPosition = ((long) mSeekbarProgress) * mDuration / 1000;
                Log.d(TAG, "手势快进结束，跳转到: " + finalPosition + "ms");
                mListener.onSeekTo(finalPosition);
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