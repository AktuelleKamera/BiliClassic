package tv.biliclassic.player;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.WindowManager;
import android.app.AlertDialog;
import java.io.IOException;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import tv.biliclassic.widget.RadioGridGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.player.danmaku.DanmakuManager;
import tv.biliclassic.subsettings.DecoderSettingsActivity;
import tv.biliclassic.util.NetWorkUtil;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import util.AudioManagerHelper;
import util.BrightnessHelper;
import util.LocalStreamProxy;
import util.PlayerToastMessageViewHolder;
import tv.biliclassic.util.SharedPreferencesUtil;

/************
 * 巨大屎山 *
 ************/
public class BiliPlayerActivity extends Activity implements
        SurfaceHolder.Callback,
        IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnInfoListener,
        IMediaPlayer.OnBufferingUpdateListener,
        IMediaPlayer.OnSeekCompleteListener {

    private static final int MSG_HIDE_CONTROLS = 1;
    private static final int MSG_UPDATE_PROGRESS = 2;
    private static final int MSG_UPDATE_TIME = 3;
    private static final int CONTROL_HIDE_DELAY = 2000;
    private static final int PROGRESS_UPDATE_INTERVAL = 500;
    private static final int TIME_UPDATE_INTERVAL = 30000;

    private static final int DECODER_SYSTEM = 0;
    private static final int DECODER_IJK = 1;

    // Completion actions
    private static final int COMPLETION_ACTION_LOOP = 0;
    private static final int COMPLETION_ACTION_NEXT = 1;
    private static final int COMPLETION_ACTION_NEXT_LOOP = 2;
    private static final int COMPLETION_ACTION_PAUSE = 3;
    private static final int COMPLETION_ACTION_EXIT = 4;

    private static final int ASPECT_RATIO_ADJUST_CONTENT = 0;
    private static final int ASPECT_RATIO_ADJUST_SCREEN = 1;
    private static final int ASPECT_RATIO_4_3_INSIDE = 2;
    private static final int ASPECT_RATIO_16_9_INSIDE = 3;
    private static final int ASPECT_RATIO_COUNT = 4;

    private static final int[] ASPECT_RATIO_TOAST_IDS = {
            R.string.PlayerController_toast_message_aspect_ratio_adjust_content,
            R.string.PlayerController_toast_message_aspect_ratio_adjust_screen,
            R.string.PlayerController_toast_message_aspect_ratio_4_3_inside,
            R.string.PlayerController_toast_message_aspect_ratio_16_9_inside
    };

    private SurfaceView videoView;
    private SurfaceHolder surfaceHolder;
    private IMediaPlayer mediaPlayer;
    private View topBar;
    private View bottomBar;
    private ImageView btnBack;
    private ImageView btnPlayPause;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvTitle;
    private TextView tvDateTime;
    private TextView tvNetworkStatus;
    private LinearLayout bufferingGroup;
    private ProgressBar bufferingView;
    private TextView btnAspectRatio;
    private TextView btnDanmaku;
    private TextView btnLock;
    private TextView btnSendDanmaku;
    private TextView btnMediaInfo;

    private String videoUrl;
    private String videoTitle;
    private String cachePath;
    private boolean isLiveStream;
    private int decoderType;
    private LocalStreamProxy localProxy;

    private boolean isPlaying = false;
    private boolean isPrepared = false;
    private boolean controlsVisible = true;
    private boolean surfaceReady = false;
    private boolean pendingPrepare = false;
    private int mSeekWhenPrepared = 0;
    private static int sPendingSeekPosition = 0;
    private boolean playerLocked = false;
    private DanmakuManager mDanmakuManager;
    private long mAid;
    private long mCid;
    private boolean mAllowDecoderFallback = true;
    private int mLastReportProgress = -1;

    private final DanmakuManager.PlayControl mPlayControl = new DanmakuManager.PlayControl() {
        public boolean isPlaying() { return isPlaying; }
        public boolean isPrepared() { return isPrepared; }
        public void pausePlayer() {
            if (mediaPlayer != null) { mediaPlayer.pause(); isPlaying = false; updatePlayPauseButton(); }
        }
        public void resumePlayer() {
            if (mediaPlayer != null && isPrepared) { mediaPlayer.start(); isPlaying = true; updatePlayPauseButton(); }
        }
    };
    private boolean optionsMenuInflated = false;
    private boolean aspectRatioFixed = false;

    private int mDuration = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private int currentAspectRatio = ASPECT_RATIO_ADJUST_CONTENT;

    // Completion action
    private int completionAction = COMPLETION_ACTION_PAUSE;
    private boolean enableGesture = true;
    private boolean keepBackground;

    private View optionsMenuBtn;
    private ViewStub optionsMenuStub;
    private ViewGroup optionsMenuItems;
    private View optionsMenuItemPlayer;
    private View optionsMenuItemDanmaku;
    private View optionsMenuItemBlock;
    private View optionsMenuItemOrientation;
    private View optionsMenuItemInfo;
    private ViewStub lockOverlayStub;
    private View lockOverlay;
    private View lockUnlockLeft;
    private View lockUnlockRight;
    private boolean lockIconsVisible = false;
    private Runnable lockIconsHideRunnable;

    private ViewStub danmakuInputStub;

    // Panels (PopupWindow)
    private PopupWindow mPlayerOptionsPannel;

    // Gesture
    private GestureDetector mGestureScanner;
    private View mGestureView;
    private View mTouchingView;
    private ViewGroup mBrightnessBar;
    private ViewGroup mVolumeBar;
    private ProgressBar mBrightnessLevel;
    private ProgressBar mVolumeLevel;
    private int mBrightnessLevelStart;
    private int mLastBrightnessLevel = -1;
    private int mVolumeStart;
    private boolean mInGestureSeekingMode;
    private boolean mInHorizontalMoving;
    private boolean mInVerticalMoving;
    private int mSeekBarStartProgress;
    private int mSeekbarProgress;
    private int mSeekBeginPosition;
    private int mMaxSeekableValue = -1;
    private int mGestureWidth;
    private int mGestureHeight;
    private boolean mIsDragging;

    private PlayerToastMessageViewHolder mToastViewHolder;
    private String mProgreesFmt;

    private Runnable mHideBarsRunnable = new Runnable() {
        public void run() {
            if (mBrightnessBar != null) mBrightnessBar.setVisibility(View.GONE);
            if (mVolumeBar != null) mVolumeBar.setVisibility(View.GONE);
        }
    };

    private Handler handler = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_HIDE_CONTROLS:
                    hideControls();
                    return true;
                case MSG_UPDATE_PROGRESS:
                    updateProgress();
                    return true;
                case MSG_UPDATE_TIME:
                    updateDateTime();
                    return true;
            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.bili_app_player_view_new);

        videoUrl = getIntent().getStringExtra("video_url");
        videoTitle = getIntent().getStringExtra("video_title");
        cachePath = getIntent().getStringExtra("cache_path");
        isLiveStream = getIntent().getBooleanExtra("live", false);
        boolean onlineMode = getIntent().getBooleanExtra("online_mode", false);
        decoderType = SettingsActivity.getDecoderType();

        android.util.Log.e("BiliPlayer", "videoUrl: " + videoUrl);
        android.util.Log.e("BiliPlayer", "videoTitle: " + videoTitle);
        android.util.Log.e("BiliPlayer", "cachePath: " + cachePath);
        android.util.Log.e("BiliPlayer", "onlineMode: " + onlineMode);

        mAid = getIntent().getLongExtra("aid", 0);
        mCid = getIntent().getLongExtra("cid", 0);
        android.util.Log.e("BiliPlayer", "aid: " + mAid + ", cid: " + mCid);

        if (onlineMode) {
            if (videoUrl == null || videoUrl.length() == 0) {
                Toast.makeText(this, "在线模式：视频地址为空", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            if ((videoUrl == null || videoUrl.length() == 0) && cachePath != null && cachePath.length() > 0) {
                File cacheFile = new File(cachePath);
                if (cacheFile.exists()) {
                    videoUrl = cachePath;
                    android.util.Log.e("BiliPlayer", "使用缓存文件: " + videoUrl);
                }
            }
        }

        mSeekWhenPrepared = sPendingSeekPosition;
        sPendingSeekPosition = 0;
        keepBackground = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.KEEP_BACKGROUND, true);

        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT);

        initViews();
        initPlayer();
        initToastView();
    }

    private void initViews() {
        videoView = (SurfaceView) findViewById(R.id.video_view);
        videoView.setKeepScreenOn(true);

        lockOverlayStub = (ViewStub) findViewById(R.id.lock_view);
        danmakuInputStub = (ViewStub) findViewById(R.id.danmaku_sender_viewstub);

        bufferingGroup = (LinearLayout) findViewById(R.id.buffering_group);
        bufferingView = (ProgressBar) findViewById(R.id.buffering_view);

        // Gesture view
        mGestureView = findViewById(R.id.controller_underlay);

        // Brightness/Volume bars
        View barsGroup = findViewById(R.id.vertically_bars_group);
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

        View controllerView = findViewById(R.id.controller_view);
        if (controllerView != null) {
            topBar = controllerView.findViewById(R.id.top);
            bottomBar = controllerView.findViewById(R.id.bottom);
            btnBack = (ImageView) controllerView.findViewById(R.id.back);
            btnPlayPause = (ImageView) controllerView.findViewById(R.id.play_pause);
            seekBar = (SeekBar) controllerView.findViewById(R.id.seekbar);
            tvCurrentTime = (TextView) controllerView.findViewById(R.id.time_current);
            tvTotalTime = (TextView) controllerView.findViewById(R.id.time_total);
            tvTitle = (TextView) controllerView.findViewById(R.id.title);

            tvDateTime = (TextView) controllerView.findViewById(R.id.date_time);
            tvNetworkStatus = (TextView) controllerView.findViewById(R.id.network_status);

            View toggleAspect = controllerView.findViewById(R.id.toggle_aspect_ratio_button);
            View toggleDanmaku = controllerView.findViewById(R.id.toggle_danmaku_button);
            View lockPlayer = controllerView.findViewById(R.id.lock_player);
            View sendDanmaku = controllerView.findViewById(R.id.send_danmaku);
            View mediaInfo = controllerView.findViewById(R.id.media_info);

            if (toggleAspect instanceof TextView) btnAspectRatio = (TextView) toggleAspect;
            if (toggleDanmaku instanceof TextView) btnDanmaku = (TextView) toggleDanmaku;
            if (lockPlayer instanceof TextView) btnLock = (TextView) lockPlayer;
            if (sendDanmaku instanceof TextView) btnSendDanmaku = (TextView) sendDanmaku;
            if (mediaInfo instanceof TextView) btnMediaInfo = (TextView) mediaInfo;

            optionsMenuBtn = controllerView.findViewById(R.id.options_menu);
            optionsMenuStub = (ViewStub) controllerView.findViewById(R.id.options_menu_items_stub);
        }

        bufferingGroup = (LinearLayout) findViewById(R.id.buffering_group);
        bufferingView = (ProgressBar) findViewById(R.id.buffering_view);

        if (videoTitle != null && tvTitle != null) {
            tvTitle.setText(videoTitle);
        }

        surfaceHolder = videoView.getHolder();
        if (decoderType == DECODER_SYSTEM) {
            if (android.os.Build.VERSION.SDK_INT >= 5) {
                videoView.setZOrderMediaOverlay(true);
            }
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        surfaceHolder.addCallback(this);

        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    togglePlayPause();
                }
            });
        }

        if (btnAspectRatio != null) {
            btnAspectRatio.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    currentAspectRatio = (currentAspectRatio + 1) % ASPECT_RATIO_COUNT;
                    applyAspectRatio(currentAspectRatio);
                    if (btnAspectRatio.getCompoundDrawables()[1] != null) {
                        btnAspectRatio.getCompoundDrawables()[1].setLevel(currentAspectRatio);
                    }
                    showControlsWithAutoHide();
                }
            });
        }

        if (optionsMenuBtn != null) {
            optionsMenuBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    toggleOptionsMenu();
                    showControlsWithAutoHide();
                }
            });
        }

        if (btnDanmaku != null) {
            btnDanmaku.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mDanmakuManager != null) {
                        mDanmakuManager.toggleVisibility();
                        if (btnDanmaku.getCompoundDrawables()[1] != null) {
                            btnDanmaku.getCompoundDrawables()[1].setLevel(
                                    mDanmakuManager.isEnabled() ? 0 : 1);
                        }
                        int toastId = mDanmakuManager.isEnabled()
                                ? R.string.PlayerController_toast_message_danmaku_state_visible
                                : R.string.PlayerController_toast_message_danmaku_state_hidden;
                        Toast.makeText(BiliPlayerActivity.this, getString(toastId),
                                Toast.LENGTH_SHORT).show();
                    }
                    showControlsWithAutoHide();
                }
            });
        }

        if (btnSendDanmaku != null) {
            btnSendDanmaku.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mDanmakuManager != null) {
                        mDanmakuManager.showInputPanel(mPlayControl);
                    }
                }
            });
        }

        if (btnLock != null) {
            btnLock.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    playerLocked = true;
                    hideControls();
                    ensureLockOverlay();
                    if (lockOverlay != null) {
                        lockOverlay.setVisibility(View.VISIBLE);
                    }
                    hideLockIcons();
                    Toast.makeText(BiliPlayerActivity.this, "已锁定",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null && isPrepared && mDuration > 0) {
                        long newPosition = ((long) progress) * mDuration / 1000;
                        if (tvCurrentTime != null) {
                            tvCurrentTime.setText(formatTime((int) newPosition));
                        }
                    }
                }
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mIsDragging = true;
                    handler.removeMessages(MSG_HIDE_CONTROLS);
                }
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (mediaPlayer != null && isPrepared && mDuration > 0) {
                        long finalPosition = ((long) seekBar.getProgress()) * mDuration / 1000;
                        mediaPlayer.seekTo(finalPosition);
                        if (mDanmakuManager != null) mDanmakuManager.seekTo(finalPosition);
                        if (tvCurrentTime != null) {
                            tvCurrentTime.setText(formatTime((int) finalPosition));
                        }
                    }
                    mIsDragging = false;
                    showControlsWithAutoHide();
                }
            });
        }

        // Gesture setup
        if (mGestureView != null) {
            mGestureView.postDelayed(new Runnable() {
                public void run() {
                    setupGestureDetector();
                }
            }, 300L);
        }

        // 初始化弹幕管理器
        FrameLayout danmakuContainer = (FrameLayout) findViewById(R.id.danmaku_view);
        if (danmakuContainer != null) {
            mDanmakuManager = new DanmakuManager(this, danmakuContainer, mAid, mCid,
                    danmakuInputStub);

            // 离线弹幕支持：如果提供了离线弹幕缓存路径，优先使用
            String danmakuCachePath = getIntent().getStringExtra("danmaku_cache_path");
            if (danmakuCachePath != null && danmakuCachePath.length() > 0) {
                File danmakuFile = new File(danmakuCachePath);
                if (danmakuFile.exists() && danmakuFile.length() > 0) {
                    mDanmakuManager.setOfflineDanmakuFile(danmakuFile);
                    android.util.Log.e("BiliPlayer", "使用离线弹幕文件: " + danmakuCachePath);
                }
            }

            mDanmakuManager.init();
            android.util.Log.e("BiliPlayer", "DanmakuManager 已初始化");
        }

        showControlsWithAutoHide();
    }

    private void initToastView() {
        mToastViewHolder = new PlayerToastMessageViewHolder();
        mProgreesFmt = getString(R.string.PlayerController_toast_message_play_progress_fmt);
    }

    private void setupGestureDetector() {
        if (mGestureView == null) return;
        int viewWidth = mGestureView.getWidth();
        int viewHeight = mGestureView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            if (videoView != null) {
                viewWidth = videoView.getWidth();
                viewHeight = videoView.getHeight();
            }
        }
        if (viewWidth <= 0 || viewHeight <= 0) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            viewWidth = Math.max(dm.widthPixels, dm.heightPixels);
            viewHeight = Math.min(dm.widthPixels, dm.heightPixels);
        }
        mGestureWidth = viewWidth;
        mGestureHeight = viewHeight;

        mBrightnessLevelStart = 0;
        if (mBrightnessLevel != null) {
            mBrightnessLevelStart = 15;
        }

        mGestureScanner = new GestureDetector(getApplicationContext(), mGestureListener);
        mGestureView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                mTouchingView = v;
                if (mGestureScanner != null) {
                    return mGestureScanner.onTouchEvent(event);
                }
                return false;
            }
        });

        View preloadingView = findViewById(R.id.preloading_view);
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

    // ---- GestureListener ----

    private GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        private Runnable mHideUIRunnable = new Runnable() {
            public void run() {
                if (mBrightnessBar != null) mBrightnessBar.setVisibility(View.GONE);
                if (mVolumeBar != null) mVolumeBar.setVisibility(View.GONE);
            }
        };

        public boolean onDown(MotionEvent e) {
            updateCurrentPositionForGesture();
            hideBarControllers(0);
            startBrightnessChange();
            startVolumeChange();
            return true;
        }

        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mInGestureSeekingMode || mInHorizontalMoving || mInVerticalMoving) {
                return false;
            }
            toggleControls();
            return true;
        }

        public boolean onDoubleTap(MotionEvent e) {
            togglePlayPause();
            if (isPlaying) {
                showToastMessage(getString(R.string.PlayerController_toast_message_play));
            } else {
                showToastMessage(getString(R.string.PlayerController_toast_message_pause));
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
            if (moveDelta > 0f) {
                onVerticalMove(e1, e2, distanceX, distanceY);
            } else if (moveDelta < 0f && !isLiveStream) {
                onHorizontalMove(e1, e2, distanceX, distanceY);
            }
            return true;
        }

        private void onHorizontalMove(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mInVerticalMoving || seekBar == null) return;
            float deltaFactorX = (e1.getX() - e2.getX()) / (float) mGestureWidth;
            if (Math.abs(deltaFactorX) >= 0.02f || mInGestureSeekingMode) {
                if (!mInGestureSeekingMode) {
                    mInGestureSeekingMode = true;
                    mSeekBarStartProgress = seekBar.getProgress();
                }
                int maxSeekable = getMaxSeekableValue();
                mSeekbarProgress = (int) (mSeekBarStartProgress - (maxSeekable * deltaFactorX));
                mSeekbarProgress = Math.min(Math.max(mSeekbarProgress, 0), seekBar.getMax());
                seekBar.setProgress(mSeekbarProgress);
                if (mediaPlayer != null && isPrepared && mDuration > 0) {
                    long newPosition = ((long) mSeekbarProgress) * mDuration / 1000;
                    if (tvCurrentTime != null) {
                        tvCurrentTime.setText(formatTime((int) newPosition));
                    }
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
            handler.removeCallbacks(mHideUIRunnable);
            handler.postDelayed(mHideUIRunnable, delay);
        }
    };

    private void handleGestureUp() {
        if ((mBrightnessBar != null && mBrightnessBar.isShown()) ||
                (mVolumeBar != null && mVolumeBar.isShown())) {
            handler.removeCallbacks(mHideBarsRunnable);
            handler.postDelayed(mHideBarsRunnable, 1000);
        }

        if (mInGestureSeekingMode) {
            mInGestureSeekingMode = false;
            if (mediaPlayer != null && isPrepared && mDuration > 0) {
                long finalPosition = ((long) mSeekbarProgress) * mDuration / 1000;
                mediaPlayer.seekTo(finalPosition);
                if (mDanmakuManager != null) mDanmakuManager.seekTo(finalPosition);
                if (tvCurrentTime != null) {
                    tvCurrentTime.setText(formatTime((int) finalPosition));
                }
            }
        }
        mInHorizontalMoving = false;
        mInVerticalMoving = false;
        hideToastHint();
    }

    private void updateCurrentPositionForGesture() {
        if (mediaPlayer != null && isPrepared) {
            updateTimeDisplay();
            try {
                if (mDuration > 0) {
                    mSeekBeginPosition = (int) (1000L * mediaPlayer.getCurrentPosition() / mDuration);
                } else {
                    mSeekBeginPosition = seekBar != null ? seekBar.getProgress() : 0;
                }
            } catch (Exception e) {
                mSeekBeginPosition = seekBar != null ? seekBar.getProgress() : 0;
            }
        }
    }

    private int getMaxSeekableValue() {
        if (mDuration <= 0) return 0;
        if (mMaxSeekableValue != -1) return mMaxSeekableValue;
        float p = 90000.0f / mDuration;
        if (p > 1.0f) p = 1.0f;
        mMaxSeekableValue = (int) (1000.0f * p);
        return mMaxSeekableValue;
    }

    private void startBrightnessChange() {
        if (mLastBrightnessLevel >= 0) {
            mBrightnessLevelStart = mLastBrightnessLevel;
        } else {
            mBrightnessLevelStart = 0;
            try {
                float b = BrightnessHelper.getScreenBrightness(this);
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
        mVolumeStart = AudioManagerHelper.getStreamVolume(this, AudioManager.STREAM_MUSIC);
    }

    private void changeBrightness(float deltaFactorY) {
        int max = 15;
        int newLevel = (int) Math.floor(mBrightnessLevelStart + (0.8f * deltaFactorY * max));
        newLevel = Math.min(Math.max(newLevel, 0), max);
        float brightness = newLevel / (float) max;
        BrightnessHelper.setBrightness(this, brightness);
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
        int max = AudioManagerHelper.getStreamMaxVolume(this, AudioManager.STREAM_MUSIC);
        int newVol = (int) Math.floor(mVolumeStart + (1.5f * deltaFactorY * max));
        newVol = Math.min(Math.max(newVol, 0), max);
        AudioManagerHelper.setStreamVolume(this, AudioManager.STREAM_MUSIC, newVol, 0);
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
        FrameLayout rootView = (FrameLayout) findViewById(android.R.id.content);
        if (rootView == null) return;
        mToastViewHolder.initView(this, rootView);

        if (mProgreesFmt == null) {
            mProgreesFmt = getString(R.string.PlayerController_toast_message_play_progress_fmt);
        }

        int progressMs = (int)(((long) progress) * mDuration / 1000);
        int beginMs = (int)(((long) mSeekBeginPosition) * mDuration / 1000);
        String timeText = formatTime(progressMs);
        String durationText = formatTime(mDuration);

        long diff = (progressMs - beginMs) / 1000;
        String diffTime = (diff >= 0 ? "+" : "") + diff;

        String text = String.format(mProgreesFmt, timeText, durationText, diffTime);
        mToastViewHolder.show(text, 500000, false);
    }

    private void showToastMessage(String text) {
        if (mToastViewHolder == null) return;
        FrameLayout rootView = (FrameLayout) findViewById(android.R.id.content);
        if (rootView == null) return;
        mToastViewHolder.initView(this, rootView);
        mToastViewHolder.show(text, 1000, false);
    }

    private void hideToastHint() {
        if (mToastViewHolder != null) {
            mToastViewHolder.dismiss();
        }
    }

    private void initPlayer() {
        showBuffering(true);

        if (surfaceReady) {
            preparePlayer();
        } else {
            pendingPrepare = true;
        }
    }

    private void preparePlayer() {
        releasePlayer();

        mAllowDecoderFallback = true;

        boolean isNetworkUrl = videoUrl != null && videoUrl.startsWith("http");
        android.util.Log.e("BiliPlayer", "preparePlayer: decoderType=" + decoderType + ", isNetworkUrl=" + isNetworkUrl);
        android.util.Log.e("BiliPlayer", "videoUrl=" + videoUrl);

        String actualUrl = videoUrl;
        if (isNetworkUrl) {
            Map<String, String> proxyHeaders = getProxyHeaders();
            localProxy = new LocalStreamProxy(videoUrl, proxyHeaders);
            try {
                actualUrl = localProxy.start();
                android.util.Log.e("Skyler1nADD", "本地中转代理URL: " + actualUrl);
            } catch (IOException e) {
                android.util.Log.e("Skyler1nADD", "代理启动失败: " + e.getMessage());
                actualUrl = videoUrl;
            }
        }

        if (decoderType == DECODER_IJK) {
            android.util.Log.e("BiliPlayer", "使用 IjkMediaPlayer");
            IjkMediaPlayer ijkPlayer = new IjkMediaPlayer();
            mediaPlayer = ijkPlayer;

            // IJK 选项
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
                    DecoderSettingsActivity.isOpenSLESEnabled() ? 1L : 0L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop",
                    (long) DecoderSettingsActivity.getFramedrop());
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps",
                    (long) DecoderSettingsActivity.getMaxFps());
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-pictq-size",
                    (long) DecoderSettingsActivity.getVideoPictqSize());
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn",
                    DecoderSettingsActivity.isVideoDisabled() ? 1L : 0L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "an",
                    DecoderSettingsActivity.isAudioDisabled() ? 1L : 0L);

            int skipLoopFilter = DecoderSettingsActivity.getSkipLoopFilter();
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", (long) skipLoopFilter);
            int skipFrame = DecoderSettingsActivity.getSkipFrame();
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", (long) skipFrame);

            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "flush_packets");
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB);

            if (isNetworkUrl) {
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L);
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 15 * 1000 * 1000L);
            }

            try {
                if (isNetworkUrl) {
                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("Referer", "https://www.bilibili.com/");
                    String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
                    if (cookie != null && cookie.length() > 0) {
                        headers.put("Cookie", cookie);
                        android.util.Log.e("BiliPlayer", "Cookie: " + cookie);
                    } else {
                        android.util.Log.e("BiliPlayer", "Cookie 为空");
                    }
                    android.util.Log.e("BiliPlayer", "IJK 网络播放，setDataSource 带 headers");
                    ijkPlayer.setDataSource(actualUrl, headers);
                } else {
                    if (cachePath != null && new File(cachePath).exists()) {
                        android.util.Log.e("BiliPlayer", "使用缓存文件: " + cachePath);
                        ijkPlayer.setDataSource(cachePath);
                    } else if (videoUrl != null && new File(videoUrl).exists()) {
                        android.util.Log.e("BiliPlayer", "使用本地文件: " + videoUrl);
                        ijkPlayer.setDataSource(videoUrl);
                    } else {
                        android.util.Log.e("BiliPlayer", "无效的本地源");
                        Toast.makeText(this, "无视频源", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                }
            } catch (IOException e) {
                android.util.Log.e("BiliPlayer", "setDataSource IOException: " + e.getMessage());
                e.printStackTrace();
                Toast.makeText(this, "设置数据源失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

        } else {
            // ===== 系统解码器 (AndroidMediaPlayer) =====
            android.util.Log.e("BiliPlayer", "使用 AndroidMediaPlayer");
            AndroidMediaPlayer androidPlayer = new AndroidMediaPlayer();
            mediaPlayer = androidPlayer;

            try {
                if (isNetworkUrl) {
                    android.util.Log.e("BiliPlayer", "系统播放器网络播放: " + actualUrl);
                    androidPlayer.setDataSource(this, Uri.parse(actualUrl));
                } else {
                    if (cachePath != null && new File(cachePath).exists()) {
                        android.util.Log.e("BiliPlayer", "系统播放器使用缓存文件: " + cachePath);
                        androidPlayer.setDataSource(cachePath);
                    } else if (videoUrl != null && new File(videoUrl).exists()) {
                        android.util.Log.e("BiliPlayer", "系统播放器使用本地文件: " + videoUrl);
                        androidPlayer.setDataSource(videoUrl);
                    } else {
                        android.util.Log.e("BiliPlayer", "系统播放器无效的本地源");
                        Toast.makeText(this, "无视频源", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("BiliPlayer", "系统播放器 setDataSource 异常: " + e.getMessage());
                e.printStackTrace();
                Toast.makeText(this, "设置数据源失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }

        // 公共设置
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);

        if (surfaceHolder != null) {
            try {
                mediaPlayer.setDisplay(surfaceHolder);
                android.util.Log.e("BiliPlayer", "setDisplay 成功");
            } catch (Exception e) {
                android.util.Log.e("BiliPlayer", "setDisplay 失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            android.util.Log.e("BiliPlayer", "surfaceHolder 为空");
        }

        try {
            mediaPlayer.prepareAsync();
            android.util.Log.e("BiliPlayer", "prepareAsync 已调用");
        } catch (Exception e) {
            android.util.Log.e("BiliPlayer", "prepareAsync 失败: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "准备播放失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (decoderType == DECODER_SYSTEM && android.os.Build.VERSION.SDK_INT < 14) {
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        if (pendingPrepare) {
            pendingPrepare = false;
            preparePlayer();
        } else if (mediaPlayer != null) {
            if (isPrepared && videoWidth > 0 && videoHeight > 0) {
                holder.setFixedSize(videoWidth, videoHeight);
            }
            // ========== 强制重新设置 Display ==========
            try {
                mediaPlayer.setDisplay(holder);
                android.util.Log.e("BiliPlayer", "surfaceCreated: 重新设置 Display");
                if (isPrepared && isPlaying) {
                    mediaPlayer.start();
                }
            } catch (Exception e) {
                android.util.Log.e("BiliPlayer", "surfaceCreated setDisplay 失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setDisplay(holder);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        if (mediaPlayer != null && !(decoderType == DECODER_SYSTEM && android.os.Build.VERSION.SDK_INT < 14)) {
            if (isPrepared) {
                try {
                    mSeekWhenPrepared = (int) mediaPlayer.getCurrentPosition();
                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        isPrepared = true;
        showBuffering(false);

        if (surfaceHolder != null) {
            try {
                mediaPlayer.setDisplay(surfaceHolder);
                android.util.Log.e("BiliPlayer", "onPrepared: 重新绑定 Display");
            } catch (Exception e) {
                android.util.Log.e("BiliPlayer", "onPrepared setDisplay 失败: " + e.getMessage());
            }
        }

        mDuration = (int) mp.getDuration();
        if (seekBar != null && !isLiveStream) {
            seekBar.setMax(1000);
            seekBar.setEnabled(mDuration > 0);
        }
        if (tvTotalTime != null) {
            tvTotalTime.setText(formatTime(mDuration));
        }

        adjustVideoSize();

        if (mSeekWhenPrepared > 0 && mDuration > 0) {
            mp.seekTo(mSeekWhenPrepared);
        }
        mp.start();
        isPlaying = true;
        updatePlayPauseButton();
        if (mSeekWhenPrepared > 0) {
            updateTimeDisplay();
            mSeekWhenPrepared = 0;
        }
        aspectRatioFixed = false;

        if (!isLiveStream) {
            handler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
        }
        handler.sendEmptyMessage(MSG_UPDATE_TIME);
        updateNetworkStatus();

        if (mDanmakuManager != null) {
            mDanmakuManager.onVideoPrepared(mp);
        }

        if (btnAspectRatio != null) {
            btnAspectRatio.setVisibility(View.VISIBLE);
            if (btnAspectRatio.getCompoundDrawables()[1] != null) {
                btnAspectRatio.getCompoundDrawables()[1].setLevel(currentAspectRatio);
            }
        }
        if (btnDanmaku != null) {
            btnDanmaku.setVisibility(View.VISIBLE);
            if (btnDanmaku.getCompoundDrawables()[1] != null) {
                btnDanmaku.getCompoundDrawables()[1].setLevel(0);
            }
        }
        if (btnSendDanmaku != null) {
            btnSendDanmaku.setVisibility(View.VISIBLE);
        }
        if (btnLock != null) {
            btnLock.setVisibility(View.VISIBLE);
            if (btnLock.getCompoundDrawables()[1] != null) {
                btnLock.getCompoundDrawables()[1].setLevel(0);
            }
        }
        if (btnMediaInfo != null) btnMediaInfo.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSeekComplete(IMediaPlayer mp) {
        if (!mIsDragging) {
            updateTimeDisplay();
        }
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        isPlaying = false;
        updatePlayPauseButton();
        if (seekBar != null) {
            seekBar.setProgress(0);
        }
        if (tvCurrentTime != null) {
            tvCurrentTime.setText("00:00");
        }
        showControls();
        showBuffering(false);

        switch (completionAction) {
            case COMPLETION_ACTION_LOOP:
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(0L);
                    mediaPlayer.start();
                    isPlaying = true;
                    updatePlayPauseButton();
                    if (mDanmakuManager != null) mDanmakuManager.seekTo(0L);
                    if (mDanmakuManager != null) mDanmakuManager.resume();
                }
                break;
            case COMPLETION_ACTION_EXIT:
                finish();
                break;
            case COMPLETION_ACTION_NEXT:
            case COMPLETION_ACTION_NEXT_LOOP:
                Toast.makeText(this, "换P功能暂未实现", Toast.LENGTH_SHORT).show();
                break;
            case COMPLETION_ACTION_PAUSE:
            default:
                break;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        showBuffering(false);
        android.util.Log.e("BiliPlayer", "onError: what=" + what + ", extra=" + extra + ", decoder=" + decoderType);

        boolean isMSM7x27 = isMSM7x27Device();

        if (isMSM7x27 && decoderType == DECODER_SYSTEM && mAllowDecoderFallback) {
            mAllowDecoderFallback = false;
            decoderType = DECODER_IJK;
            android.util.Log.e("Skyler1nADD", "MSM7x27 设备，自动切换到软解");
            Toast.makeText(this, "该设备不支持硬解，已自动切换到软解模式", Toast.LENGTH_LONG).show();
            cleanupAndRestart();
            return true;
        }

        if (decoderType == DECODER_SYSTEM && mAllowDecoderFallback) {
            mAllowDecoderFallback = false;
            decoderType = DECODER_IJK;
            android.util.Log.e("Skyler1nADD", "硬解失败，降级到 IJK 软解");
            Toast.makeText(this, "硬解失败，已自动切换到软解模式", Toast.LENGTH_LONG).show();
            cleanupAndRestart();
            return true;
        }

        if (decoderType == DECODER_IJK || !mAllowDecoderFallback) {
            android.util.Log.e("Skyler1nADD", "播放失败");
            Toast.makeText(this, "播放失败，请检查网络或重试", Toast.LENGTH_LONG).show();
            finish();
            return true;
        }

        Toast.makeText(this, "播放出错: what=" + what + ", extra=" + extra, Toast.LENGTH_LONG).show();
        return true;
    }

    // 检测 MSM7x27 系列设备
    private boolean isMSM7x27Device() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            new java.io.FileInputStream("/proc/cpuinfo")
                    )
            );
            String line;
            boolean isARMv6 = false;
            boolean hasNeon = false;
            String hardware = "";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("CPU architecture")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String arch = parts[1].trim();
                        if (arch.startsWith("6")) {
                            isARMv6 = true;
                        }
                    }
                }
                if (line.startsWith("Features")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String features = parts[1].trim();
                        if (features.indexOf("neon") != -1) {
                            hasNeon = true;
                        }
                    }
                }
                if (line.startsWith("Hardware")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        hardware = parts[1].trim();
                    }
                }
            }
            reader.close();

            if (hardware != null && hardware.length() > 0) {
                if (hardware.indexOf("7x27") != -1 || hardware.indexOf("7227") != -1 ||
                        hardware.indexOf("7627") != -1 || hardware.indexOf("MSM7x27") != -1) {
                    android.util.Log.e("BiliPlayer", "检测到 MSM7x27 设备: " + hardware);
                    return true;
                }
            }
            if (isARMv6) {
                android.util.Log.e("BiliPlayer", "检测到 ARMv6 设备");
                return true;
            }
            if (!hasNeon) {
                android.util.Log.e("BiliPlayer", "设备不支持 NEON");
                return true;
            }
        } catch (Exception e) {
            android.util.Log.e("BiliPlayer", "检测 CPU 失败: " + e.getMessage());
        }
        return false;
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                showBuffering(true);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                showBuffering(false);
                break;
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                showBuffering(false);
                if (!aspectRatioFixed) {
                    aspectRatioFixed = true;
                    videoView.post(new Runnable() {
                        public void run() {
                            if (isPrepared && mediaPlayer != null) {
                                applyAspectRatio(currentAspectRatio);
                            }
                        }
                    });
                }
                break;
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
    }

    private void adjustVideoSize() {
        videoWidth = mediaPlayer.getVideoWidth();
        videoHeight = mediaPlayer.getVideoHeight();

        if (videoWidth == 0 || videoHeight == 0) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (mediaPlayer != null) {
                        adjustVideoSize();
                    }
                }
            }, 200);
            return;
        }

        applyAspectRatio(currentAspectRatio);
    }

    private void applyAspectRatio(int mode) {
        if (videoWidth == 0 || videoHeight == 0) return;

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int containerWidth = dm.widthPixels;
        int containerHeight = dm.heightPixels;

        float targetRatio;
        switch (mode) {
            case ASPECT_RATIO_ADJUST_CONTENT:
                targetRatio = (float) videoWidth / videoHeight;
                break;
            case ASPECT_RATIO_ADJUST_SCREEN:
                targetRatio = (float) containerWidth / containerHeight;
                break;
            case ASPECT_RATIO_4_3_INSIDE:
                targetRatio = 4f / 3f;
                break;
            case ASPECT_RATIO_16_9_INSIDE:
                targetRatio = 16f / 9f;
                break;
            default:
                targetRatio = (float) videoWidth / videoHeight;
                break;
        }

        float containerRatio = (float) containerWidth / containerHeight;

        int targetWidth, targetHeight;
        if (targetRatio > containerRatio) {
            targetWidth = containerWidth;
            targetHeight = (int) (containerWidth / targetRatio);
        } else {
            targetHeight = containerHeight;
            targetWidth = (int) (containerHeight * targetRatio);
        }

        SurfaceHolder holder = videoView.getHolder();
        holder.setFixedSize(videoWidth, videoHeight);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoView.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(targetWidth, targetHeight);
            params.gravity = android.view.Gravity.CENTER;
        } else {
            params.width = targetWidth;
            params.height = targetHeight;
            params.gravity = android.view.Gravity.CENTER;
        }
        videoView.setLayoutParams(params);
        videoView.requestLayout();
    }

    // ---- Options Menu ----

    private void toggleOptionsMenu() {
        if (!optionsMenuInflated) {
            inflateOptionsMenu();
        }
        if (optionsMenuItems != null) {
            if (optionsMenuItems.getVisibility() == View.VISIBLE) {
                hideOptionsMenu();
            } else {
                optionsMenuItems.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideOptionsMenu() {
        if (optionsMenuItems != null) {
            optionsMenuItems.setVisibility(View.INVISIBLE);
        }
    }

    private void inflateOptionsMenu() {
        if (optionsMenuStub == null) return;
        View inflated = optionsMenuStub.inflate();
        if (inflated instanceof ViewGroup) {
            optionsMenuItems = (ViewGroup) inflated;
            optionsMenuItemPlayer = optionsMenuItems.findViewById(R.id.options_menu_item_player);
            optionsMenuItemDanmaku = optionsMenuItems.findViewById(R.id.options_menu_item_danmaku);
            optionsMenuItemBlock = optionsMenuItems.findViewById(R.id.options_menu_item_block);
            optionsMenuItemOrientation = optionsMenuItems.findViewById(R.id.options_menu_item_orientation);
            optionsMenuItemInfo = optionsMenuItems.findViewById(R.id.options_menu_item_info);

            if (optionsMenuItemPlayer != null) {
                optionsMenuItemPlayer.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        hideOptionsMenu();
                        showPlayerOptionsPannel();
                    }
                });
            }
            if (optionsMenuItemDanmaku != null) {
                optionsMenuItemDanmaku.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        hideOptionsMenu();
                        if (mDanmakuManager != null) mDanmakuManager.showOptionsPanel();
                    }
                });
            }
            if (optionsMenuItemBlock != null) {
                optionsMenuItemBlock.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        hideOptionsMenu();
                        Toast.makeText(BiliPlayerActivity.this,
                                "用户屏蔽 (暂未实现)", Toast.LENGTH_SHORT).show();
                        showControlsWithAutoHide();
                    }
                });
            }
            if (optionsMenuItemOrientation != null) {
                optionsMenuItemOrientation.setVisibility(View.VISIBLE);
                optionsMenuItemOrientation.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        toggleScreenOrientation();
                        hideOptionsMenu();
                        showControlsWithAutoHide();
                    }
                });
            }
            if (optionsMenuItemInfo != null) {
                optionsMenuItemInfo.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showMediaInfoDialog();
                        hideOptionsMenu();
                        showControlsWithAutoHide();
                    }
                });
            }
        }
        optionsMenuItems.setVisibility(View.GONE);
        optionsMenuInflated = true;
    }

    // ---- Player Options Pannel (播放设置) ----

    private void showPlayerOptionsPannel() {
        if (mPlayerOptionsPannel != null && mPlayerOptionsPannel.isShowing()) {
            mPlayerOptionsPannel.dismiss();
            return;
        }
        dismissAllPanels();

        LayoutInflater inflater = LayoutInflater.from(this);
        View panel = inflater.inflate(R.layout.bili_app_player_options_pannel, null);

        TextView titleView = (TextView) panel.findViewById(R.id.title);
        if (titleView != null) {
            titleView.setText(R.string.Player_playback_options_pannel_title);
        }

        View closeBtn = panel.findViewById(R.id.close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dismissAllPanels();
                }
            });
        }

        final CheckBox enableGestureCb = (CheckBox) panel.findViewById(
                R.id.player_options_enable_gesture);
        final CheckBox keepBackgroundCb = (CheckBox) panel.findViewById(
                R.id.player_options_keep_background);
        View screenOrientation = panel.findViewById(R.id.player_options_screen_orientation);

        // Set current values
        RadioGridGroup completionGroup = (RadioGridGroup) panel.findViewById(
                R.id.player_options_completion_actions);
        if (completionGroup != null) {
            int checkedId;
            switch (completionAction) {
                case COMPLETION_ACTION_LOOP: checkedId = R.id.completion_actions_loop; break;
                case COMPLETION_ACTION_NEXT: checkedId = R.id.completion_actions_switch_part; break;
                case COMPLETION_ACTION_NEXT_LOOP: checkedId = R.id.completion_actions_switch_part_loop; break;
                case COMPLETION_ACTION_EXIT: checkedId = R.id.completion_actions_exit; break;
                default: checkedId = R.id.completion_actions_pause; break;
            }
            completionGroup.check(checkedId);
            completionGroup.setOnCheckedChangeListener(new RadioGridGroup.OnCheckedChangeListener() {
                public void onCheckedChanged(RadioGridGroup group, int checkedId) {
                    if (checkedId == R.id.completion_actions_loop) {
                        completionAction = COMPLETION_ACTION_LOOP;
                    } else if (checkedId == R.id.completion_actions_switch_part) {
                        completionAction = COMPLETION_ACTION_NEXT;
                    } else if (checkedId == R.id.completion_actions_switch_part_loop) {
                        completionAction = COMPLETION_ACTION_NEXT_LOOP;
                    } else if (checkedId == R.id.completion_actions_exit) {
                        completionAction = COMPLETION_ACTION_EXIT;
                    } else {
                        completionAction = COMPLETION_ACTION_PAUSE;
                    }
                }
            });
        }

        if (enableGestureCb != null) {
            enableGestureCb.setChecked(enableGesture);
            enableGestureCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    enableGesture = isChecked;
                }
            });
        }

        if (keepBackgroundCb != null) {
            keepBackgroundCb.setChecked(keepBackground);
            keepBackgroundCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    keepBackground = isChecked;
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.KEEP_BACKGROUND, isChecked);
                }
            });
        }

        if (screenOrientation != null) {
            screenOrientation.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dismissAllPanels();
                    toggleScreenOrientation();
                    showControlsWithAutoHide();
                }
            });
        }

        mPlayerOptionsPannel = new PopupWindow(panel,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        mPlayerOptionsPannel.setAnimationStyle(R.style.Animation_SidePannel);
        mPlayerOptionsPannel.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                android.graphics.Color.TRANSPARENT));
        mPlayerOptionsPannel.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() {
                mPlayerOptionsPannel = null;
            }
        });

        View root = findViewById(android.R.id.content);
        mPlayerOptionsPannel.showAtLocation(root, Gravity.RIGHT, 0, 0);
        showControlsWithAutoHide();
    }

    private void dismissAllPanels() {
        if (mPlayerOptionsPannel != null && mPlayerOptionsPannel.isShowing()) {
            mPlayerOptionsPannel.dismiss();
            mPlayerOptionsPannel = null;
        }
        if (mDanmakuManager != null) mDanmakuManager.dismissAllPanels();
        hideOptionsMenu();
    }

    private void toggleScreenOrientation() {
        int current = getRequestedOrientation();
        if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void showMediaInfoDialog() {
        String decoder = (decoderType == DECODER_IJK) ? "IJK V3软解" : "系统硬解";
        String resolution = videoWidth + " x " + videoHeight;
        String duration = "";
        if (mediaPlayer != null && isPrepared) {
            long dur = mediaPlayer.getDuration();
            if (dur > 0) {
                duration = formatTime((int) dur);
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("分辨率: ").append(resolution).append("\n");
        msg.append("解码器: ").append(decoder).append("\n");
        if (duration.length() > 0) {
            msg.append("时长: ").append(duration);
        }

        new AlertDialog.Builder(this)
                .setTitle("视频信息")
                .setMessage(msg.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    private void ensureLockOverlay() {
        if (lockOverlay == null && lockOverlayStub != null) {
            lockOverlay = lockOverlayStub.inflate();
            if (lockOverlay != null) {
                lockUnlockLeft = lockOverlay.findViewById(R.id.unlock_left);
                lockUnlockRight = lockOverlay.findViewById(R.id.unlock_right);
                lockOverlay.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (lockIconsVisible) {
                            hideLockIcons();
                        } else {
                            showLockIcons();
                        }
                    }
                });
                View.OnClickListener unlockListener = new View.OnClickListener() {
                    public void onClick(View v) {
                        unlock();
                    }
                };
                if (lockUnlockLeft != null) {
                    lockUnlockLeft.setOnClickListener(unlockListener);
                }
                if (lockUnlockRight != null) {
                    lockUnlockRight.setOnClickListener(unlockListener);
                }
            }
        }
    }

    private void showLockIcons() {
        lockIconsVisible = true;
        if (lockUnlockLeft != null) lockUnlockLeft.setVisibility(View.VISIBLE);
        if (lockUnlockRight != null) lockUnlockRight.setVisibility(View.VISIBLE);
        if (lockIconsHideRunnable != null) {
            handler.removeCallbacks(lockIconsHideRunnable);
        }
        lockIconsHideRunnable = new Runnable() {
            public void run() {
                hideLockIcons();
            }
        };
        handler.postDelayed(lockIconsHideRunnable, 5000);
    }

    private void hideLockIcons() {
        lockIconsVisible = false;
        if (lockIconsHideRunnable != null) {
            handler.removeCallbacks(lockIconsHideRunnable);
        }
        if (lockUnlockLeft != null) lockUnlockLeft.setVisibility(View.GONE);
        if (lockUnlockRight != null) lockUnlockRight.setVisibility(View.GONE);
    }

    private void unlock() {
        playerLocked = false;
        hideLockIcons();
        if (lockOverlay != null) {
            lockOverlay.setVisibility(View.GONE);
        }
        showControlsWithAutoHide();
    }

    private void showNoFade() {
        showControls();
        handler.removeMessages(MSG_HIDE_CONTROLS);
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;

        if (isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            if (mDanmakuManager != null) mDanmakuManager.pause();
        } else {
            if (!isPrepared) {
                try {
                    mediaPlayer.seekTo(0);
                } catch (Exception e) {
                }
            }
            isPrepared = true;
            mediaPlayer.start();
            isPlaying = true;
            if (mDanmakuManager != null) mDanmakuManager.resume();
        }
        updatePlayPauseButton();

        if (isPlaying && !isLiveStream) {
            handler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
        }
    }

    private void updatePlayPauseButton() {
        if (btnPlayPause != null) {
            btnPlayPause.setImageLevel(isPlaying ? 1 : 0);
        }
    }

    private void updateProgress() {
        if (mediaPlayer != null && isPrepared && isPlaying && !isLiveStream) {
            updateTimeDisplay();
            handler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, PROGRESS_UPDATE_INTERVAL);
        }
    }

    // 上报播放进度
    private void reportHistory(final int progress) {
        if (mAid == 0 || mCid == 0) {
            android.util.Log.e("BiliPlayer", "aid或cid为0，跳过上报");
            return;
        }
        if (mLastReportProgress == progress) return;
        mLastReportProgress = progress;

        final int progressSec = progress / 1000;  // 毫秒 → 秒
        android.util.Log.e("BiliPlayer", "准备上报进度: " + progress + "ms = " + progressSec + "s, aid=" + mAid + ", cid=" + mCid);

        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/v2/history/report";
                    String cookie = SharedPreferencesUtil.getString("cookies", "");
                    String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookie);

                    if (csrf == null || csrf.length() == 0) {
                        android.util.Log.e("BiliPlayer", "csrf 为空，跳过上报");
                        return;
                    }

                    String arg = "aid=" + mAid + "&cid=" + mCid + "&progress=" + progressSec + "&csrf=" + csrf;  // ← 这里用 progressSec
                    NetWorkUtil.setCookieString(cookie);
                    String result = NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders);
                    android.util.Log.e("BiliPlayer", "上报结果: " + result);
                } catch (Exception e) {
                    android.util.Log.e("BiliPlayer", "上报进度失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void updateTimeDisplay() {
        if (mediaPlayer == null || !isPrepared) return;

        if (mIsDragging || mInGestureSeekingMode) return;

        try {
            long current = mediaPlayer.getCurrentPosition();
            if (seekBar != null && mDuration > 0) {
                seekBar.setProgress((int) (1000L * current / mDuration));
            }
            if (tvCurrentTime != null) {
                tvCurrentTime.setText(formatTime((int) current));
            }

            // ========== 每 5 秒上报一次进度 ==========
            int progress = (int) current;
            if (progress > 0 && progress % 5000 < 250) {
                reportHistory(progress);
            }
        } catch (Exception e) {
        }
    }

    private void updateDateTime() {
        if (tvDateTime != null) {
            GregorianCalendar calendar = new GregorianCalendar();
            String dateString = String.format(Locale.US, "%02d:%02d",
                    calendar.get(GregorianCalendar.HOUR_OF_DAY),
                    calendar.get(GregorianCalendar.MINUTE));
            tvDateTime.setText(dateString);
        }
        handler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, TIME_UPDATE_INTERVAL);
    }

    private void updateNetworkStatus() {
        if (tvNetworkStatus == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) {
                tvNetworkStatus.setVisibility(View.GONE);
                return;
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) {
                tvNetworkStatus.setVisibility(View.GONE);
                return;
            }
            String name;
            String typeName = info.getTypeName();
            if ("WIFI".equalsIgnoreCase(typeName)) {
                name = "WIFI";
            } else {
                name = info.getExtraInfo();
                if (name == null || name.length() == 0) {
                    name = typeName;
                }
            }
            if (name == null || name.length() == 0) {
                tvNetworkStatus.setVisibility(View.GONE);
                return;
            }
            tvNetworkStatus.setText(name.toUpperCase(Locale.US));
            tvNetworkStatus.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            tvNetworkStatus.setVisibility(View.GONE);
        }
    }

    private void showBuffering(boolean show) {
        if (bufferingGroup != null) {
            bufferingGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (bufferingView != null) {
            bufferingView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showControls() {
        if (topBar != null) topBar.setVisibility(View.VISIBLE);
        if (bottomBar != null) bottomBar.setVisibility(View.VISIBLE);
        if (btnBack != null) btnBack.setVisibility(View.VISIBLE);
        updateNetworkStatus();
        controlsVisible = true;
        handler.removeMessages(MSG_HIDE_CONTROLS);
        handler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLS, CONTROL_HIDE_DELAY);
    }

    private void hideControls() {
        if (topBar != null) topBar.setVisibility(View.GONE);
        if (bottomBar != null) bottomBar.setVisibility(View.GONE);
        if (btnBack != null) btnBack.setVisibility(View.GONE);
        hideOptionsMenu();
        controlsVisible = false;
        handler.removeMessages(MSG_HIDE_CONTROLS);
    }

    private void showControlsWithAutoHide() {
        showControls();
    }

    private void toggleControls() {
        if (playerLocked) return;
        if (controlsVisible) {
            hideControls();
        } else {
            showControlsWithAutoHide();
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            handleGestureUp();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            updatePlayPauseButton();
            if (mDanmakuManager != null) mDanmakuManager.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing() && keepBackground && mediaPlayer != null && isPrepared) {
            try {
                sPendingSeekPosition = (int) mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                sPendingSeekPosition = 0;
            }
        }
        releasePlayer();
        if (!keepBackground && !isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (keepBackground) {
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }
    }

    private void cleanupAndRestart() {
        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }

        if (mDanmakuManager != null) {
            mDanmakuManager.pause();
            mDanmakuManager.release();
        }

        releasePlayer();

        // 强制重新创建 SurfaceView
        final FrameLayout parent = (FrameLayout) videoView.getParent();
        if (parent != null) {
            // 移除旧的 SurfaceView
            parent.removeView(videoView);
            // 创建新的 SurfaceView（旧的 Surface 已经失效）
            videoView = new SurfaceView(this);
            videoView.setKeepScreenOn(true);
            // 重新获取 SurfaceHolder
            surfaceHolder = videoView.getHolder();
            surfaceHolder.addCallback(BiliPlayerActivity.this);
            // 加回布局
            parent.addView(videoView, 0);
            android.util.Log.e("BiliPlayer", "SurfaceView 已重新创建");
        }

        surfaceReady = false;
        pendingPrepare = false;

        handler.postDelayed(new Runnable() {
            public void run() {
                if (mDanmakuManager != null) {
                    mDanmakuManager.init();
                    android.util.Log.e("BiliPlayer", "DanmakuManager 已重新初始化");
                }

                if (surfaceHolder != null) {
                    surfaceReady = true;
                    preparePlayer();
                } else {
                    pendingPrepare = true;
                }
            }
        }, 300);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (mDanmakuManager != null && mDanmakuManager.isInputVisible()) {
                mDanmakuManager.hideInputPanel(mPlayControl);
                return true;
            }
            if ((mPlayerOptionsPannel != null && mPlayerOptionsPannel.isShowing())
                    || (mDanmakuManager != null && mDanmakuManager.isOptionsPanelShowing())) {
                dismissAllPanels();
                return true;
            }
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!isPrepared) {
                return true;
            }
            if (!controlsVisible) {
                showControlsWithAutoHide();
            }
            if (optionsMenuBtn != null) {
                optionsMenuBtn.performClick();
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }
        dismissAllPanels();
        if (mDanmakuManager != null) {
            mDanmakuManager.release();
            mDanmakuManager = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (mToastViewHolder != null) {
            mToastViewHolder.release();
            mToastViewHolder = null;
        }
        releasePlayer();
    }

    private Map<String, String> getProxyHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Referer", "https://www.bilibili.com/");
        headers.put("User-Agent", NetWorkUtil.USER_AGENT_WEB);
        String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (cookie != null && cookie.length() > 0) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    private void releasePlayer() {
        releasePlayer(true);
    }

    private void releasePlayer(boolean clearState) {
        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }
        handler.removeMessages(MSG_UPDATE_PROGRESS);
        handler.removeMessages(MSG_HIDE_CONTROLS);
        handler.removeMessages(MSG_UPDATE_TIME);

        if (mediaPlayer != null) {
            try {
                mediaPlayer.setDisplay(null);
            } catch (Exception e) {
            }
            try {
                mediaPlayer.reset();
            } catch (Exception e) {
            }
            try {
                mediaPlayer.release();
            } catch (Exception e) {
            }
            mediaPlayer = null;
        }
        if (clearState) {
            isPrepared = false;
            isPlaying = false;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isPrepared) {
            videoView.postDelayed(new Runnable() {
                public void run() {
                    applyAspectRatio(currentAspectRatio);
                }
            }, 100);
        }
    }
}