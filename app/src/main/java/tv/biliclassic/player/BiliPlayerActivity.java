package tv.biliclassic.player;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.api.PlayerApi;
import tv.biliclassic.model.PlayerData;
import tv.biliclassic.player.danmaku.DanmakuManager;
import tv.biliclassic.subsettings.DecoderSettingsActivity;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.widget.RadioGridGroup;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.biliclassic.util.DeviceInfoUtil;
import util.LocalStreamProxy;

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
    private static final int DECODER_IJK_HARD = 1;
    private static final int DECODER_IJK_SOFT = 2;

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

    private static final int RENDERER_SURFACEVIEW = 0;
    private static final int RENDERER_TEXTUREVIEW = 1;

    private View videoView;
    private SurfaceHolder surfaceHolder;
    private Surface mVideoSurface;
    private int mRendererType = RENDERER_SURFACEVIEW;
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
    private boolean mPlaybackCompleted;
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
    private FileInputStream mFileInputStream;

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

    private PopupWindow mPlayerOptionsPannel;

    private int mHardwareDecodeRetryCount = 0;
    private static final int MAX_HARDWARE_RETRY = 5;
    private boolean mIsDragging;

    private PlayerQualityManager mQualityManager;
    private String[] mQualityNames;
    private int[] mQualityValues;
    private int mCurrentQn;
    private boolean mOfflineMode;
    private int mQualitySwitchSeekPos = 0;
    private boolean mErrorToastShown;

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

    private GestureController mGestureController;

    // 小电视加载动画
    private View mLoadingOverlay;
    private ImageView mLoadingIcon;
    private Handler mAnimHandler = new Handler();
    private int mAnimIndex;
    private int[] mAnimDrawables = {
            R.drawable.bili_anim_tv_chan_1,
            R.drawable.bili_anim_tv_chan_3,
            R.drawable.bili_anim_tv_chan_5,
            R.drawable.bili_anim_tv_chan_7,
            R.drawable.bili_anim_tv_chan_9
    };
    private Runnable mAnimRunnable = new Runnable() {
        public void run() {
            if (mLoadingOverlay != null && mLoadingOverlay.getVisibility() == View.VISIBLE) {
                mLoadingIcon.setImageResource(mAnimDrawables[mAnimIndex]);
                mAnimIndex = (mAnimIndex + 1) % mAnimDrawables.length;
                mAnimHandler.postDelayed(this, 200);
            }
        }
    };

    private Object createSurfaceTextureListener() {
        return new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int width, int height) {
                mVideoSurface = new Surface(st);
                surfaceReady = true;
                if (videoWidth > 0 && videoHeight > 0) {
                    st.setDefaultBufferSize(videoWidth, videoHeight);
                }
                if (pendingPrepare) {
                    pendingPrepare = false;
                    preparePlayer();
                } else if (mediaPlayer != null) {
                    if (isPrepared && videoWidth > 0 && videoHeight > 0) {
                        st.setDefaultBufferSize(videoWidth, videoHeight);
                    }
                    try {
                        mediaPlayer.setSurface(mVideoSurface);
                        if (isPrepared && isPlaying) {
                            mediaPlayer.start();
                        }
                    } catch (Exception e) {}
                }
            }

            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int width, int height) {
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.setSurface(mVideoSurface);
                    } catch (Exception e) {}
                }
            }

            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) {
                surfaceReady = false;
                if (mediaPlayer != null && mRendererType != RENDERER_SURFACEVIEW) {
                    if (isPrepared) {
                        try {
                            mSeekWhenPrepared = (int) mediaPlayer.getCurrentPosition();
                        } catch (Exception e) {}
                    }
                }
                if (mVideoSurface != null) {
                    mVideoSurface.release();
                    mVideoSurface = null;
                }
                return true;
            }

            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {}
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFFFFFFFF));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.bili_app_player_view_new);
        if (!getIntent().getBooleanExtra("offline_mode", false)) {
            initLoadingOverlay();
        }

        videoUrl = getIntent().getStringExtra("video_url");
        videoTitle = getIntent().getStringExtra("video_title");
        cachePath = getIntent().getStringExtra("cache_path");
        isLiveStream = getIntent().getBooleanExtra("live", false);
        boolean onlineMode = getIntent().getBooleanExtra("online_mode", false);
        decoderType = SettingsActivity.getDecoderType();
        mRendererType = SettingsActivity.getRendererType();
        if (mRendererType == RENDERER_TEXTUREVIEW && android.os.Build.VERSION.SDK_INT < 14) {
            mRendererType = RENDERER_SURFACEVIEW;
        }

        mAid = getIntent().getLongExtra("aid", 0);
        mCid = getIntent().getLongExtra("cid", 0);

        mQualityNames = getIntent().getStringArrayExtra("qn_str_array");
        mQualityValues = getIntent().getIntArrayExtra("qn_value_array");
        mCurrentQn = getIntent().getIntExtra("current_qn", 0);
        mOfflineMode = getIntent().getBooleanExtra("offline_mode", false);

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
                }
            }
        }

        mSeekWhenPrepared = sPendingSeekPosition;
        sPendingSeekPosition = 0;
        keepBackground = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.KEEP_BACKGROUND, true);

        if (DeviceInfoUtil.isUnsupportedCpu()) {
            new AlertDialog.Builder(this)
                    .setTitle("设备不支持")
                    .setMessage("ARMv5TE 或无 VFP 的 ARMv6 设备无法使用内置播放器，请关闭\"在线播放\"后下载视频，使用第三方播放器播放。")
                    .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }

        initViews();
        initPlayer();
        initGestureController();
    }

    private void initLoadingOverlay() {
        FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        if (root == null) return;

        mLoadingOverlay = LayoutInflater.from(this).inflate(
                R.layout.activity_player_anim, root, false);
        mLoadingOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mLoadingIcon = (ImageView) mLoadingOverlay.findViewById(R.id.iv_tv_anim);
        View progressGroup = mLoadingOverlay.findViewById(R.id.linearLayout);
        if (progressGroup != null) progressGroup.setVisibility(View.INVISIBLE);
        root.addView(mLoadingOverlay);
        mAnimHandler.post(mAnimRunnable);
    }

    private void hideLoadingOverlay() {
        if (mLoadingOverlay != null && mLoadingOverlay.getVisibility() == View.VISIBLE) {
            mAnimHandler.removeCallbacks(mAnimRunnable);
            mLoadingOverlay.setVisibility(View.GONE);
        }
    }

    private void initGestureController() {
        View rootView = findViewById(android.R.id.content);
        mGestureController = new GestureController(this, handler, rootView,
                new GestureController.OnGestureActionListener() {
                    public void onToggleControls() {
                        toggleControls();
                    }

                    public void onTogglePlayPause() {
                        togglePlayPause();
                    }

                    public void onSeekTo(long position) {
                        if (mediaPlayer != null && isPrepared && mDuration > 0) {
                            mediaPlayer.seekTo(position);
                            if (mDanmakuManager != null) mDanmakuManager.seekTo(position);
                            if (tvCurrentTime != null) {
                                tvCurrentTime.setText(formatTime((int) position));
                            }
                        }
                    }

                    public void onShowToast(String text) {
                        Toast.makeText(BiliPlayerActivity.this, text, Toast.LENGTH_SHORT).show();
                    }

                    public void onHideToast() {
                        // 不需要处理
                    }
                });
        mGestureController.setLiveStream(isLiveStream);
    }

    private void createVideoView() {
        FrameLayout container = (FrameLayout) findViewById(R.id.video_container);
        if (container == null) return;

        container.removeAllViews();

        if (mRendererType == RENDERER_TEXTUREVIEW && android.os.Build.VERSION.SDK_INT >= 14) {
            TextureView tv = new TextureView(this);
            tv.setSurfaceTextureListener(
                    (TextureView.SurfaceTextureListener) createSurfaceTextureListener());
            videoView = tv;
            surfaceHolder = null;
            mVideoSurface = null;
        } else {
            mRendererType = RENDERER_SURFACEVIEW;
            SurfaceView sv = new SurfaceView(this);
            videoView = sv;
            mVideoSurface = null;
            surfaceHolder = sv.getHolder();
            if (decoderType == DECODER_SYSTEM) {
                if (android.os.Build.VERSION.SDK_INT >= 5) {
                    sv.setZOrderMediaOverlay(true);
                }
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            }
            surfaceHolder.addCallback(this);
        }

        videoView.setKeepScreenOn(true);
        container.addView(videoView, 0);
    }

    private void setDisplayOnPlayer() {
        if (mediaPlayer == null) return;
        try {
            if (mRendererType == RENDERER_TEXTUREVIEW && mVideoSurface != null) {
                mediaPlayer.setSurface(mVideoSurface);
            } else if (surfaceHolder != null) {
                mediaPlayer.setDisplay(surfaceHolder);
            }
        } catch (Exception e) {}
    }

    private void initViews() {
        createVideoView();

        lockOverlayStub = (ViewStub) findViewById(R.id.lock_view);
        danmakuInputStub = (ViewStub) findViewById(R.id.danmaku_sender_viewstub);

        bufferingGroup = (LinearLayout) findViewById(R.id.buffering_group);
        bufferingView = (ProgressBar) findViewById(R.id.buffering_view);

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

        if (videoTitle != null && tvTitle != null) {
            tvTitle.setText(videoTitle);
        }

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

        // 初始化弹幕管理器
        FrameLayout danmakuContainer = (FrameLayout) findViewById(R.id.danmaku_view);
        if (danmakuContainer != null) {
            mDanmakuManager = new DanmakuManager(this, danmakuContainer, mAid, mCid,
                    danmakuInputStub);

            String danmakuCachePath = getIntent().getStringExtra("danmaku_cache_path");
            if (danmakuCachePath != null && danmakuCachePath.length() > 0) {
                File danmakuFile = new File(danmakuCachePath);
                if (danmakuFile.exists() && danmakuFile.length() > 0) {
                    mDanmakuManager.setOfflineDanmakuFile(danmakuFile);
                }
            }

            mDanmakuManager.init();
        }

        showControlsWithAutoHide();
        initQualityManager();
    }

    private void initQualityManager() {
        mQualityManager = new PlayerQualityManager(this);
        boolean allowSwitch = !mOfflineMode && !isLiveStream && mAid > 0 && mCid > 0
                && mQualityNames != null && mQualityNames.length > 1;
        mQualityManager.init(mQualityNames, mQualityValues, mCurrentQn, allowSwitch);
        mQualityManager.setOnQualityChangeListener(new PlayerQualityManager.OnQualityChangeListener() {
            public void onQualityChange(int newQn) {
                switchQuality(newQn);
            }
        });
    }

    private void switchQuality(final int newQn) {
        if (mediaPlayer != null && isPrepared) {
            try {
                mQualitySwitchSeekPos = (int) mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                mQualitySwitchSeekPos = 0;
            }
        }

        showBuffering(true);

        new Thread(new Runnable() {
            public void run() {
                try {
                    PlayerData playerData = new PlayerData();
                    playerData.aid = mAid;
                    playerData.cid = mCid;
                    playerData.qn = newQn;
                    playerData.timeStamp = 0;

                    PlayerApi.getVideo(playerData, false);
                    final String newUrl = playerData.videoUrl;

                    if (newUrl != null && newUrl.length() > 0) {
                        final String[] newQnStrs = playerData.qnStrList;
                        final int[] newQnVals = playerData.qnValueList;

                        runOnUiThread(new Runnable() {
                            public void run() {
                                videoUrl = newUrl;
                                mCurrentQn = newQn;
                                if (newQnStrs != null && newQnVals != null) {
                                    mQualityNames = newQnStrs;
                                    mQualityValues = newQnVals;
                                }
                                if (mQualityManager != null) {
                                    mQualityManager.updateCurrentQuality(newQn);
                                }
                                if (decoderType == DECODER_SYSTEM) {
                                    releasePlayer();
                                    sPendingSeekPosition = mQualitySwitchSeekPos;
                                    mQualitySwitchSeekPos = 0;
                                    Intent intent = getIntent();
                                    intent.putExtra("video_url", newUrl);
                                    intent.putExtra("current_qn", newQn);
                                    if (newQnStrs != null) {
                                        intent.putExtra("qn_str_array", newQnStrs);
                                    }
                                    if (newQnVals != null) {
                                        intent.putExtra("qn_value_array", newQnVals);
                                    }
                                    overridePendingTransition(0, 0);
                                    finish();
                                    startActivity(intent);
                                    overridePendingTransition(0, 0);
                                } else {
                                    cleanupAndRestartWithQuality();
                                }
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                showBuffering(false);
                                Toast.makeText(BiliPlayerActivity.this,
                                        "切换画质失败，请重试", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            showBuffering(false);
                            Toast.makeText(BiliPlayerActivity.this,
                                    "切换画质失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void recreateVideoView() {
        FrameLayout container = (FrameLayout) findViewById(R.id.video_container);
        if (container == null) {
            container = (FrameLayout) videoView.getParent();
        }
        if (container == null) return;

        if (videoView != null) {
            container.removeView(videoView);
        }

        if (mRendererType == RENDERER_TEXTUREVIEW && android.os.Build.VERSION.SDK_INT >= 14) {
            TextureView tv = new TextureView(this);
            tv.setSurfaceTextureListener(
                    (TextureView.SurfaceTextureListener) createSurfaceTextureListener());
            videoView = tv;
            surfaceHolder = null;
            mVideoSurface = null;
        } else {
            mRendererType = RENDERER_SURFACEVIEW;
            SurfaceView sv = new SurfaceView(this);
            videoView = sv;
            mVideoSurface = null;
            surfaceHolder = sv.getHolder();
            if (decoderType == DECODER_SYSTEM) {
                if (android.os.Build.VERSION.SDK_INT >= 5) {
                    sv.setZOrderMediaOverlay(true);
                }
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            }
            surfaceHolder.addCallback(this);
        }

        videoView.setKeepScreenOn(true);
        container.addView(videoView, 0);
    }

    private void cleanupAndRestartWithQuality() {
        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }

        if (mDanmakuManager != null) {
            mDanmakuManager.pause();
            mDanmakuManager.release();
        }

        releasePlayer();

        recreateVideoView();

        surfaceReady = false;
        pendingPrepare = false;
        isPrepared = false;
        isPlaying = false;
        updatePlayPauseButton();
        mSeekWhenPrepared = mQualitySwitchSeekPos;
        mQualitySwitchSeekPos = 0;

        handler.postDelayed(new Runnable() {
            public void run() {
                if (mDanmakuManager != null) {
                    mDanmakuManager.init();
                }

                if (surfaceReady) {
                    preparePlayer();
                } else {
                    pendingPrepare = true;
                }
            }
        }, 300);
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
        mErrorToastShown = false;

        mHardwareDecodeRetryCount = 0;
        mAllowDecoderFallback = true;

        boolean isNetworkUrl = videoUrl != null && videoUrl.startsWith("http");

        String actualUrl = videoUrl;
        if (isNetworkUrl) {
            Map<String, String> proxyHeaders = getProxyHeaders();
            localProxy = new LocalStreamProxy(videoUrl, proxyHeaders);
            try {
                actualUrl = localProxy.start();
            } catch (IOException e) {
                actualUrl = videoUrl;
            }
        }

        if (decoderType == DECODER_SYSTEM) {
            // 系统解码器
            AndroidMediaPlayer androidPlayer = new AndroidMediaPlayer();
            mediaPlayer = androidPlayer;

            try {
                if (isNetworkUrl) {
                    androidPlayer.setDataSource(this, Uri.parse(actualUrl));
                } else {
                    String localPath = null;
                    if (cachePath != null && new File(cachePath).exists()) {
                        localPath = cachePath;
                    } else if (videoUrl != null && new File(videoUrl).exists()) {
                        localPath = videoUrl;
                    }

                    if (localPath != null) {
                        try {
                            mFileInputStream = new FileInputStream(localPath);
                            androidPlayer.setDataSource(mFileInputStream.getFD());
                        } catch (Exception e) {
                            try {
                                Uri localUri = Uri.fromFile(new File(localPath));
                                androidPlayer.setDataSource(this, localUri);
                            } catch (Exception e2) {
                                androidPlayer.setDataSource(localPath);
                            }
                        }
                    } else {
                        Toast.makeText(this, "无视频源", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "设置数据源失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnSeekCompleteListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setOnInfoListener(this);
            mediaPlayer.setOnBufferingUpdateListener(this);

            setDisplayOnPlayer();

            // 创建播放器后设置到 GestureController
            if (mGestureController != null) {
                mGestureController.setMediaPlayer(mediaPlayer);
                mGestureController.setDecoderType(decoderType);
            }

            try {
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                Toast.makeText(this, "准备播放失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }

        // IJK 解码器（硬解或软解）
        IjkMediaPlayer ijkPlayer = new IjkMediaPlayer();
        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT);
        mediaPlayer = ijkPlayer;

        boolean enableHardware = (decoderType == DECODER_IJK_HARD);
        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", enableHardware ? 1L : 0L);

        if (enableHardware) {
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L);
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-timeout", 10000L);
        }

        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
                DecoderSettingsActivity.isOpenSLESEnabled() ? 1L : 0L);
        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1L);
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
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 512 * 1024L);
        }

        try {
            if (isNetworkUrl) {
                Map<String, String> headers = new HashMap<String, String>();
                headers.put("Referer", "https://www.bilibili.com/");
                String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
                if (cookie != null && cookie.length() > 0) {
                    headers.put("Cookie", cookie);
                }
                ijkPlayer.setDataSource(actualUrl, headers);
            } else {
                if (cachePath != null && new File(cachePath).exists()) {
                    ijkPlayer.setDataSource(cachePath);
                } else if (videoUrl != null && new File(videoUrl).exists()) {
                    ijkPlayer.setDataSource(videoUrl);
                } else {
                    Toast.makeText(this, "无视频源", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            }
        } catch (IOException e) {
            Toast.makeText(this, "设置数据源失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);

        setDisplayOnPlayer();

        // 设置到 GestureController
        if (mGestureController != null && mediaPlayer != null) {
            mGestureController.setMediaPlayer(mediaPlayer);
            mGestureController.setDecoderType(decoderType);
        }

        try {
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "准备播放失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mRendererType == RENDERER_TEXTUREVIEW) return;
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
            setDisplayOnPlayer();
            if (isPrepared && isPlaying) {
                mediaPlayer.start();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mRendererType == RENDERER_TEXTUREVIEW) return;
        setDisplayOnPlayer();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mRendererType == RENDERER_TEXTUREVIEW) return;
        surfaceReady = false;
        if (mediaPlayer != null && !(decoderType == DECODER_SYSTEM && android.os.Build.VERSION.SDK_INT < 14)) {
            if (isPrepared) {
                try {
                    mSeekWhenPrepared = (int) mediaPlayer.getCurrentPosition();
                } catch (Exception e) {}
            }
        }
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        isPrepared = true;
        showBuffering(false);
        hideLoadingOverlay();

        setDisplayOnPlayer();

        // Set default buffer size for TextureView
        if (mRendererType == RENDERER_TEXTUREVIEW && videoView instanceof TextureView) {
            TextureView tv = (TextureView) videoView;
            if (tv.getSurfaceTexture() != null) {
                try {
                    tv.getSurfaceTexture().setDefaultBufferSize(
                            mp.getVideoWidth(), mp.getVideoHeight());
                } catch (Exception e) {}
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

        if (mGestureController != null) {
            mGestureController.setDuration(mDuration);
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
        mPlaybackCompleted = true;
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

        int sdkInt = android.os.Build.VERSION.SDK_INT;

        // 系统解码器失败
        if (decoderType == DECODER_SYSTEM && mAllowDecoderFallback) {
            if (!isPrepared) {
                mAllowDecoderFallback = false;
                // 4.1以下直接软解，4.1以上先硬解
                if (sdkInt < 16) {
                    decoderType = DECODER_IJK_SOFT;
                    Toast.makeText(this, "系统解码器失败，切换到IJK软解", Toast.LENGTH_LONG).show();
                } else {
                    decoderType = DECODER_IJK_HARD;
                    Toast.makeText(this, "系统解码器失败，切换到IJK硬解", Toast.LENGTH_LONG).show();
                }
                mHardwareDecodeRetryCount = 0;
                cleanupAndRestart();
                return true;
            }

            // 已成功播放过，偶发错误 → 重试 5 次
            if (mHardwareDecodeRetryCount < MAX_HARDWARE_RETRY) {
                mHardwareDecodeRetryCount++;
                handler.postDelayed(new Runnable() {
                    public void run() {
                        cleanupAndRestart();
                    }
                }, 500);
                return true;
            }

            // 重试耗尽 → 降级
            mAllowDecoderFallback = false;
            if (sdkInt < 16) {
                decoderType = DECODER_IJK_SOFT;
                Toast.makeText(this, "系统解码器失败，切换到IJK软解", Toast.LENGTH_LONG).show();
            } else {
                decoderType = DECODER_IJK_HARD;
                Toast.makeText(this, "系统解码器失败，切换到IJK硬解", Toast.LENGTH_LONG).show();
            }
            mHardwareDecodeRetryCount = 0;
            cleanupAndRestart();
            return true;
        }

        // IJK 硬解失败
        if (decoderType == DECODER_IJK_HARD && mAllowDecoderFallback) {
            if (mHardwareDecodeRetryCount < MAX_HARDWARE_RETRY) {
                mHardwareDecodeRetryCount++;
                handler.postDelayed(new Runnable() {
                    public void run() {
                        cleanupAndRestart();
                    }
                }, 500);
                return true;
            }

            // 硬解重试耗尽 → 降级软解
            mAllowDecoderFallback = false;
            decoderType = DECODER_IJK_SOFT;
            mHardwareDecodeRetryCount = 0;
            Toast.makeText(this, "IJK硬解失败，切换到IJK软解", Toast.LENGTH_LONG).show();
            cleanupAndRestart();
            return true;
        }

        // IJK 软解失败 或 不允许降级
        if (decoderType == DECODER_IJK_SOFT || !mAllowDecoderFallback) {
            if (!mErrorToastShown) {
                mErrorToastShown = true;
                Toast.makeText(this, "播放失败，请检查网络或重试", Toast.LENGTH_LONG).show();
            }
            finish();
            return true;
        }

        if (!mErrorToastShown) {
            mErrorToastShown = true;
            Toast.makeText(this, "播放出错: what=" + what + ", extra=" + extra, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void cleanupAndRestart() {
        mHardwareDecodeRetryCount = 0;

        // 保存当前播放位置
        if (mediaPlayer != null && isPrepared) {
            try { mSeekWhenPrepared = (int) mediaPlayer.getCurrentPosition(); } catch (Exception e) {}
        }

        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }

        if (mDanmakuManager != null) {
            mDanmakuManager.pause();
            mDanmakuManager.release();
        }

        releasePlayer();

        recreateVideoView();

        surfaceReady = false;
        pendingPrepare = false;

        handler.postDelayed(new Runnable() {
            public void run() {
                if (mDanmakuManager != null) {
                    mDanmakuManager.init();
                }

                if (surfaceReady) {
                    preparePlayer();
                } else {
                    pendingPrepare = true;
                }
            }
        }, 300);
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                showBuffering(true);
                if (mDanmakuManager != null && isPlaying) mDanmakuManager.pause();
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                showBuffering(false);
                if (mDanmakuManager != null && isPlaying) mDanmakuManager.resume();
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

        if (mRendererType == RENDERER_SURFACEVIEW && surfaceHolder != null) {
            surfaceHolder.setFixedSize(videoWidth, videoHeight);
        } else if (mRendererType == RENDERER_TEXTUREVIEW && videoView instanceof TextureView) {
            TextureView tv = (TextureView) videoView;
            if (tv.getSurfaceTexture() != null) {
                try {
                    tv.getSurfaceTexture().setDefaultBufferSize(videoWidth, videoHeight);
                } catch (Exception e) {}
            }
        }

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

    // ---- Player Options Pannel ----

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
                    if (mGestureController != null) {
                        mGestureController.setEnableGesture(isChecked);
                    }
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
        String decoder;
        if (decoderType == DECODER_SYSTEM) {
            decoder = "系统硬解";
        } else if (decoderType == DECODER_IJK_HARD) {
            decoder = "IJK 硬解";
        } else {
            decoder = "IJK 软解";
        }
        String renderer = (mRendererType == RENDERER_TEXTUREVIEW) ? "TextureView" : "SurfaceView";
        String resolution = videoWidth + " x " + videoHeight;
        String duration = "";
        if (mediaPlayer != null && isPrepared) {
            long dur = mediaPlayer.getDuration();
            if (dur > 0) {
                duration = formatTime((int) dur);
            }
        }
        String fps = "N/A";
        if (isPrepared && mediaPlayer instanceof IjkMediaPlayer) {
            try {
                float f = ((IjkMediaPlayer) mediaPlayer).getVideoOutputFramesPerSecond();
                if (f > 0) {
                    fps = String.format("%.1f", f);
                }
            } catch (Exception e) {}
        }

        StringBuilder msg = new StringBuilder();
        msg.append("分辨率: ").append(resolution).append("\n");
        msg.append("渲染方式: ").append(renderer).append("\n");
        if (decoderType != DECODER_SYSTEM) {
            msg.append("帧数: ").append(fps).append("\n");
        }
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
                } catch (Exception e) {}
            } else if (mPlaybackCompleted) {
                mPlaybackCompleted = false;
                try {
                    mediaPlayer.seekTo(0);
                } catch (Exception e) {}
                if (mDanmakuManager != null) mDanmakuManager.seekTo(0L);
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
        if (mAid == 0 || mCid == 0) return;
        if (mLastReportProgress == progress) return;
        mLastReportProgress = progress;

        final int progressSec = progress / 1000;

        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/v2/history/report";
                    String cookie = SharedPreferencesUtil.getString("cookies", "");

                    String csrf = null;
                    if (cookie != null && cookie.length() > 0) {
                        java.util.regex.Pattern p = java.util.regex.Pattern.compile("bili_jct=([a-f0-9]+)");
                        java.util.regex.Matcher m = p.matcher(cookie);
                        if (m.find()) {
                            csrf = m.group(1);
                        }
                    }

                    if (csrf == null || csrf.length() == 0) {
                        return;
                    }

                    java.util.ArrayList headers = new java.util.ArrayList();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");
                    headers.add("Cookie");
                    headers.add(cookie);
                    headers.add("Content-Type");
                    headers.add("application/x-www-form-urlencoded");

                    String arg = "aid=" + mAid + "&cid=" + mCid + "&progress=" + progressSec + "&csrf=" + csrf;
                    String result = NetWorkUtil.post(url, arg, headers);
                } catch (Exception e) {}
            }
        }).start();
    }

    private void updateTimeDisplay() {
        if (mediaPlayer == null || !isPrepared) return;

        if (mIsDragging || (mGestureController != null && mGestureController.isGestureSeeking())) return;

        try {
            long current = mediaPlayer.getCurrentPosition();
            if (seekBar != null && mDuration > 0) {
                seekBar.setProgress((int) (1000L * current / mDuration));
            }
            if (tvCurrentTime != null) {
                tvCurrentTime.setText(formatTime((int) current));
            }

            int progress = (int) current;
            if (progress > 0 && progress % 5000 < 250) {
                reportHistory(progress);
            }
        } catch (Exception e) {}
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
        if (mQualityManager != null) mQualityManager.hideQualityList();
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
        if (mGestureController != null) {
            mGestureController.onTouchEvent(ev);
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (mQualityManager != null && mQualityManager.isQualityListVisible()) {
                mQualityManager.hideQualityList();
                return true;
            }
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
        mAnimHandler.removeCallbacks(mAnimRunnable);
        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }
        dismissAllPanels();
        if (mDanmakuManager != null) {
            mDanmakuManager.release();
            mDanmakuManager = null;
        }
        if (mGestureController != null) {
            mGestureController.release();
            mGestureController = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (mQualityManager != null) {
            mQualityManager.release();
            mQualityManager = null;
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
        if (mFileInputStream != null) {
            try {
                mFileInputStream.close();
            } catch (Exception e) {}
            mFileInputStream = null;
        }

        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }
        handler.removeMessages(MSG_UPDATE_PROGRESS);
        handler.removeMessages(MSG_HIDE_CONTROLS);
        handler.removeMessages(MSG_UPDATE_TIME);

        if (mediaPlayer != null) {
            try {
                if (mRendererType == RENDERER_TEXTUREVIEW && mVideoSurface != null) {
                    mediaPlayer.setSurface(null);
                } else {
                    mediaPlayer.setDisplay(null);
                }
            } catch (Exception e) {}
            try {
                mediaPlayer.reset();
            } catch (Exception e) {}
            try {
                mediaPlayer.release();
            } catch (Exception e) {}
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