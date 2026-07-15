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
import android.util.Log;
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
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tv.biliclassic.CommentAdapter;
import tv.biliclassic.CommentFragment;
import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.UserProfileActivity;
import tv.biliclassic.api.PlayerApi;
import tv.biliclassic.model.PlayerData;
import tv.biliclassic.player.danmaku.DanmakuManager;
import tv.biliclassic.subsettings.DecoderSettingsActivity;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.widget.MarqueeTextView;
import tv.biliclassic.widget.RadioGridGroup;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.biliclassic.widget.BatteryView2;
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
    private static final int CONTROL_HIDE_DELAY = 3000;
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
    private static final int ASPECT_RATIO_9_16_INSIDE = 4;
    private static final int ASPECT_RATIO_COUNT = 5;

    private static final int RENDERER_SURFACEVIEW = 0;
    private static final int RENDERER_TEXTUREVIEW = 1;

    private static final long BACK_PRESS_INTERVAL = 2000; // 2秒内按两次退出

    private static final int SWIPE_THRESHOLD = 200;

    private View videoView;
    private SurfaceHolder surfaceHolder;
    private FrameLayout mDanmakuContainer;
    private FrameLayout mResetScaleContainer;
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
    private View mBatteryView;
    private TextView btnResetScale;
    private LinearLayout bufferingGroup;
    private ProgressBar bufferingView;
    private TextView btnAspectRatio;
    private TextView btnDanmaku;
    private TextView btnLock;
    private TextView btnSendDanmaku;
    private TextView btnMediaInfo;
    private BatteryView2 batteryView;

    private String videoUrl;
    private String videoTitle;
    private String cachePath;
    private boolean isLiveStream;
    private int decoderType;
    private LocalStreamProxy localProxy;

    private boolean isPlaying = false;
    private boolean isPrepared = false;
    private boolean mIsFirstInit = true;
    private boolean mPlaybackCompleted;
    private boolean controlsVisible = true;
    private boolean surfaceReady = false;
    private boolean pendingPrepare = false;
    private int mSeekWhenPrepared = 0;
    private boolean mTextureViewConfigured = false;
    private static int sPendingSeekPosition = 0;
    private boolean playerLocked = false;
    private DanmakuManager mDanmakuManager;
    private long mAid;
    private long mCid;
    private long mLastBackPressTime = 0;
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
    private long mLastApplyTime = 0;

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
    private View commentOverlay;
    private View commentScrim;
    private View commentClose;
    private ListView commentList;
    private TextView commentEmpty;
    private List<CommentFragment.CommentItem> commentItems;
    private CommentAdapter commentAdapter;
    private boolean commentLoaded;
    private float touchStartX;
    private float touchStartY;
    private Set<Long> commentIdSet = new HashSet<Long>();
    private String commentNextCursor = "";
    private boolean commentIsLoading = false;
    private boolean commentIsEnd = false;
    private boolean commentIsLoadingMore = false;
    private View commentFooterView;
    private ProgressBar commentFooterProgress;
    private float commentTouchStartX = 0;
    private float commentTouchStartY = 0;
    private boolean commentIsSwiping = false;

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

    private long[] mCids;
    private String[] mPartNames;
    private int mCurrentPartIndex;

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

        mCids = getIntent().hasExtra("cids") ? getIntent().getLongArrayExtra("cids") : null;
        mPartNames = getIntent().hasExtra("pagenames") ? getIntent().getStringArrayExtra("pagenames") : null;
        mCurrentPartIndex = getIntent().getIntExtra("part_index", 0);

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
        completionAction = SharedPreferencesUtil.getInt(SharedPreferencesUtil.COMPLETION_ACTION, COMPLETION_ACTION_PAUSE);

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
                            if (!isPlaying && mDanmakuManager != null) mDanmakuManager.pause();
                            if (tvCurrentTime != null) {
                                tvCurrentTime.setText(formatTime((int) position));
                            }
                        }
                    }
                });
        mGestureController.setLiveStream(isLiveStream);

        mGestureController.setOnScaleChangeListener(new GestureController.OnScaleChangeListener() {
            @Override
            public void onScaleChange(float scale, float translateX, float translateY) {
                applyVideoScale(scale, translateX, translateY);
                updateResetScaleButtonVisibility(scale);
            }

            @Override
            public void onScaleReset() {
                applyVideoScale(1.0f, 0, 0);
                updateResetScaleButtonVisibility(1.0f);
            }
        });
    }

    /**
     * 应用视频缩放
     * @param scale 缩放倍数 (1.0 = 原始大小)
     */
    private void applyVideoScale(float scale, float translateX, float translateY) {
        if (videoView == null) return;
        if (videoWidth == 0 || videoHeight == 0) {
            if (mediaPlayer != null) {
                videoWidth = mediaPlayer.getVideoWidth();
                videoHeight = mediaPlayer.getVideoHeight();
            }
            if (videoWidth == 0 || videoHeight == 0) return;
        }

        FrameLayout container = (FrameLayout) findViewById(R.id.video_container);
        if (container == null) return;

        int containerWidth = container.getWidth();
        int containerHeight = container.getHeight();

        if (containerWidth == 0 || containerHeight == 0) {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            containerWidth = dm.widthPixels;
            containerHeight = dm.heightPixels;
        }

        // 计算目标比例
        float containerRatio = (float) containerWidth / containerHeight;
        float videoRatio = (float) videoWidth / videoHeight;

        float targetRatio;
        switch (currentAspectRatio) {
            case ASPECT_RATIO_ADJUST_CONTENT:
                targetRatio = videoRatio;
                break;
            case ASPECT_RATIO_ADJUST_SCREEN:
                targetRatio = containerRatio;
                break;
            case ASPECT_RATIO_4_3_INSIDE:
                targetRatio = 4f / 3f;
                break;
            case ASPECT_RATIO_16_9_INSIDE:
                targetRatio = 16f / 9f;
                break;
            case ASPECT_RATIO_9_16_INSIDE:
                targetRatio = 9f / 16f;
                break;
            default:
                targetRatio = videoRatio;
                break;
        }

        float baseScaleX = (float) containerWidth / videoWidth;
        float baseScaleY = (float) containerHeight / videoHeight;

        float adjustedScale;
        if (targetRatio > containerRatio) {
            adjustedScale = (float) containerWidth / videoWidth;
        } else {
            adjustedScale = (float) containerHeight / videoHeight;
        }

        float finalScale = adjustedScale * scale;

        int scaledWidth = (int) (videoWidth * finalScale);
        int scaledHeight = (int) (videoHeight * finalScale);

        float maxTranslateX = Math.max(0, (scaledWidth - containerWidth) / 2.0f);
        float maxTranslateY = Math.max(0, (scaledHeight - containerHeight) / 2.0f);

        float finalTranslateX = translateX * maxTranslateX;
        float finalTranslateY = translateY * maxTranslateY;

        if (finalTranslateX > maxTranslateX) finalTranslateX = maxTranslateX;
        if (finalTranslateX < -maxTranslateX) finalTranslateX = -maxTranslateX;
        if (finalTranslateY > maxTranslateY) finalTranslateY = maxTranslateY;
        if (finalTranslateY < -maxTranslateY) finalTranslateY = -maxTranslateY;

        if (scale <= 1.0f) {
            finalTranslateX = 0;
            finalTranslateY = 0;
        }
       // TextureView
        if (mRendererType == RENDERER_TEXTUREVIEW) {
            final TextureView tv = (TextureView) videoView;
            if (tv.getSurfaceTexture() == null) return;

            final FrameLayout tvContainer = (FrameLayout) findViewById(R.id.video_container);
            if (tvContainer == null) return;

            final float fScale = scale;
            final float fTranslateX = translateX;
            final float fTranslateY = translateY;

            tv.post(new Runnable() {
                @Override
                public void run() {
                    int containerWidth = tvContainer.getWidth();
                    int containerHeight = tvContainer.getHeight();
                    if (containerWidth == 0 || containerHeight == 0) return;
                    if (videoWidth == 0 || videoHeight == 0) return;

                    boolean portrait = getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

                    // 竖屏 setRotation
                    if (portrait) {
                        tv.setPivotX(containerWidth / 2f);
                        tv.setPivotY(containerHeight / 2f);
                        tv.setScaleX(1f);
                        tv.setScaleY(1f);
                        tv.setTranslationX(0);
                        tv.setTranslationY(0);
                        return;
                    }

                    // 横屏：重置旋转
                    tv.setRotation(0);
                    tv.setPivotX(0);
                    tv.setPivotY(0);

                    float containerRatio = (float) containerWidth / containerHeight;
                    float videoRatio = (float) videoWidth / videoHeight;

                    float targetRatio;
                    switch (currentAspectRatio) {
                        case ASPECT_RATIO_ADJUST_CONTENT:
                            targetRatio = videoRatio;
                            break;
                        case ASPECT_RATIO_ADJUST_SCREEN:
                            targetRatio = containerRatio;
                            break;
                        case ASPECT_RATIO_4_3_INSIDE:
                            targetRatio = 4f / 3f;
                            break;
                        case ASPECT_RATIO_16_9_INSIDE:
                            targetRatio = 16f / 9f;
                            break;
                        case ASPECT_RATIO_9_16_INSIDE:
                            targetRatio = 9f / 16f;
                            break;
                        default:
                            targetRatio = videoRatio;
                            break;
                    }

                    int targetWidth, targetHeight;
                    if (targetRatio > containerRatio) {
                        targetWidth = containerWidth;
                        targetHeight = (int) (containerWidth / targetRatio);
                    } else {
                        targetHeight = containerHeight;
                        targetWidth = (int) (containerHeight * targetRatio);
                    }

                    if (targetWidth < 1) targetWidth = 1;
                    if (targetHeight < 1) targetHeight = 1;

                    float userScale = fScale;
                    if (userScale < 1.0f) userScale = 1.0f;
                    targetWidth = (int) (targetWidth * userScale);
                    targetHeight = (int) (targetHeight * userScale);

                    float maxTranslateX = Math.max(0, (targetWidth - containerWidth) / 2.0f);
                    float maxTranslateY = Math.max(0, (targetHeight - containerHeight) / 2.0f);
                    float finalTranslateX = fTranslateX * maxTranslateX;
                    float finalTranslateY = fTranslateY * maxTranslateY;

                    if (finalTranslateX > maxTranslateX) finalTranslateX = maxTranslateX;
                    if (finalTranslateX < -maxTranslateX) finalTranslateX = -maxTranslateX;
                    if (finalTranslateY > maxTranslateY) finalTranslateY = maxTranslateY;
                    if (finalTranslateY < -maxTranslateY) finalTranslateY = -maxTranslateY;

                    if (fScale <= 1.0f) {
                        finalTranslateX = 0;
                        finalTranslateY = 0;
                    }

                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tv.getLayoutParams();
                    if (lp == null) {
                        lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
                        lp.gravity = android.view.Gravity.LEFT | android.view.Gravity.TOP;
                    } else {
                        lp.width = targetWidth;
                        lp.height = targetHeight;
                        lp.gravity = android.view.Gravity.LEFT | android.view.Gravity.TOP;
                    }

                    lp.leftMargin = (containerWidth - targetWidth) / 2 + (int) finalTranslateX;
                    lp.topMargin = (containerHeight - targetHeight) / 2 + (int) finalTranslateY;
                    lp.rightMargin = 0;
                    lp.bottomMargin = 0;

                    tv.setLayoutParams(lp);
                    tv.requestLayout();
                }
            });
            return;
        }
        // IJK 硬解 + SurfaceView：直接改尺寸切换比例
        if (decoderType == DECODER_IJK_HARD && mRendererType == RENDERER_SURFACEVIEW) {
            // 确保容器尺寸有效
            if (containerWidth == 0 || containerHeight == 0) {
                final float fScale = scale;
                final float fTranslateX = translateX;
                final float fTranslateY = translateY;
                videoView.post(new Runnable() {
                    @Override
                    public void run() {
                        applyVideoScale(fScale, fTranslateX, fTranslateY);
                    }
                });
                return;
            }

            // 计算目标尺寸
            float ijkContainerRatio = (float) containerWidth / containerHeight;
            float ijkVideoRatio = (float) videoWidth / videoHeight;

            float ijkTargetRatio;
            switch (currentAspectRatio) {
                case ASPECT_RATIO_ADJUST_CONTENT:
                    ijkTargetRatio = ijkVideoRatio;
                    break;
                case ASPECT_RATIO_ADJUST_SCREEN:
                    ijkTargetRatio = ijkContainerRatio;
                    break;
                case ASPECT_RATIO_4_3_INSIDE:
                    ijkTargetRatio = 4f / 3f;
                    break;
                case ASPECT_RATIO_16_9_INSIDE:
                    ijkTargetRatio = 16f / 9f;
                    break;
                case ASPECT_RATIO_9_16_INSIDE:
                    ijkTargetRatio = 9f / 16f;
                    break;
                default:
                    ijkTargetRatio = ijkVideoRatio;
                    break;
            }

            int ijkTargetWidth, ijkTargetHeight;
            if (ijkTargetRatio > ijkContainerRatio) {
                ijkTargetWidth = containerWidth;
                ijkTargetHeight = (int) (containerWidth / ijkTargetRatio);
            } else {
                ijkTargetHeight = containerHeight;
                ijkTargetWidth = (int) (containerHeight * ijkTargetRatio);
            }

            if (ijkTargetWidth < 1) ijkTargetWidth = 1;
            if (ijkTargetHeight < 1) ijkTargetHeight = 1;

            // 如果尺寸没有变化，不操作
            FrameLayout.LayoutParams currentParams = (FrameLayout.LayoutParams) videoView.getLayoutParams();
            if (currentParams != null && currentParams.width == ijkTargetWidth && currentParams.height == ijkTargetHeight) {
                return;
            }

            // 判断是否是真正的第一次初始化
            boolean isInit = mIsFirstInit && scale == 1.0f && translateX == 0 && translateY == 0;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ijkTargetWidth, ijkTargetHeight);
            params.gravity = android.view.Gravity.CENTER;
            videoView.setLayoutParams(params);
            videoView.requestLayout();

            if (isInit) {
                mIsFirstInit = false;
                return;
            }

            final long currentPos = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
            final boolean wasPlaying = (mediaPlayer != null && isPlaying);

            if (wasPlaying) {
                mediaPlayer.pause();
            }

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null) {
                        mediaPlayer.seekTo(currentPos);
                        if (wasPlaying) {
                            mediaPlayer.start();
                            isPlaying = true;
                            updatePlayPauseButton();
                        }
                    }
                }
            }, 300);
            return;
        }

        // IJK 软解 + SurfaceView：用 LayoutParams 改尺寸
        if (decoderType == DECODER_IJK_SOFT && mRendererType == RENDERER_SURFACEVIEW) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoView.getLayoutParams();
            if (params == null) {
                params = new FrameLayout.LayoutParams(scaledWidth, scaledHeight);
            }
            params.width = scaledWidth;
            params.height = scaledHeight;
            params.leftMargin = (containerWidth - scaledWidth) / 2 + (int) finalTranslateX;
            params.topMargin = (containerHeight - scaledHeight) / 2 + (int) finalTranslateY;
            params.rightMargin = 0;
            params.bottomMargin = 0;
            params.gravity = android.view.Gravity.LEFT | android.view.Gravity.TOP;
            videoView.setLayoutParams(params);
            videoView.requestLayout();
            return;
        }

        // 系统解码器 + SurfaceView
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoView.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(scaledWidth, scaledHeight);
        }

        params.width = scaledWidth;
        params.height = scaledHeight;
        params.leftMargin = (containerWidth - scaledWidth) / 2 + (int) finalTranslateX;
        params.topMargin = (containerHeight - scaledHeight) / 2 + (int) finalTranslateY;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        params.gravity = android.view.Gravity.LEFT | android.view.Gravity.TOP;

        videoView.setLayoutParams(params);
        videoView.requestLayout();

        if (surfaceHolder != null) {
            try {
                surfaceHolder.setFixedSize(videoWidth, videoHeight);
            } catch (Exception e) {}
        }
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
            mBatteryView = controllerView.findViewById(R.id.battery_view);

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

        // 选集按钮
        View pageListSelector = controllerView != null ? controllerView.findViewById(R.id.page_list_selector) : null;
        if (pageListSelector != null) {
            if (mCids != null && mCids.length > 1) {
                pageListSelector.setVisibility(View.VISIBLE);
                pageListSelector.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showPartSelector();
                    }
                });
            } else {
                pageListSelector.setVisibility(View.GONE);
            }
        }

        // 设置标题
        if (videoTitle != null && tvTitle != null) {
            tvTitle.setText(videoTitle);

            if (tvTitle instanceof MarqueeTextView) {
                final MarqueeTextView marqueeTv = (MarqueeTextView) tvTitle;
                marqueeTv.setAutoStartMarquee(false);
                marqueeTv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        boolean needScroll = false;
                        try {
                            float textWidth = marqueeTv.getPaint().measureText(videoTitle);
                            int viewWidth = marqueeTv.getWidth() - marqueeTv.getPaddingLeft() - marqueeTv.getPaddingRight();
                            needScroll = textWidth > viewWidth;
                        } catch (Exception e) {
                            needScroll = videoTitle.length() > 18;
                        }
                        if (needScroll) {
                            marqueeTv.initMarquee();
                        } else {
                            marqueeTv.stopMarquee();
                        }
                    }
                }, 300);
            } else {
                tvTitle.setSelected(false);
                tvTitle.setFocusable(false);
                tvTitle.setFocusableInTouchMode(false);
                tvTitle.setSingleLine(true);
                tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tvTitle.setMarqueeRepeatLimit(0);
            }
        }

        // 评论覆盖层
        commentOverlay = findViewById(R.id.comment_overlay);
        if (commentOverlay != null) {
            commentScrim = commentOverlay.findViewById(R.id.comment_scrim);
            commentClose = commentOverlay.findViewById(R.id.comment_close);
            commentList = (ListView) commentOverlay.findViewById(R.id.comment_overlay_list);
            commentEmpty = (TextView) commentOverlay.findViewById(R.id.comment_overlay_empty);

            // 添加底部加载Footer
            commentFooterView = LayoutInflater.from(this).inflate(R.layout.list_footer, null);
            commentFooterProgress = (ProgressBar) commentFooterView.findViewById(R.id.footer_progress);
            if (commentFooterProgress != null) {
                commentFooterProgress.setVisibility(View.GONE);
            }
            commentFooterView.setVisibility(View.GONE);
            commentList.addFooterView(commentFooterView);

            commentItems = new ArrayList<CommentFragment.CommentItem>();
            commentAdapter = new CommentAdapter(this, commentItems, mAid, null);
            commentList.setAdapter(commentAdapter);

            // 设置点击用户跳转
            commentAdapter.setOnUserClickListener(new CommentAdapter.OnUserClickListener() {
                @Override
                public void onUserClick(long mid, String userName) {
                    if (mid != 0) {
                        Intent intent = new Intent(BiliPlayerActivity.this, UserProfileActivity.class);
                        intent.putExtra("mid", mid);
                        startActivity(intent);
                    } else {
                        Toast.makeText(BiliPlayerActivity.this, "无法获取用户信息", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            if (commentClose != null) {
                commentClose.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        hideCommentOverlay();
                    }
                });
            }
            if (commentScrim != null) {
                commentScrim.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        hideCommentOverlay();
                    }
                });
            }

            // 分页加载
            commentList.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (scrollState == SCROLL_STATE_IDLE) {
                        int lastVisible = view.getLastVisiblePosition();
                        int totalCount = commentAdapter.getCount();
                        if (lastVisible >= totalCount - 1 && !commentIsLoadingMore && !commentIsEnd && totalCount > 0) {
                            loadMoreCommentsForOverlay();
                        }
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (!commentIsLoadingMore && !commentIsEnd && totalItemCount > 0) {
                        if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                            loadMoreCommentsForOverlay();
                        }
                    }
                }
            });

            // 设置触摸事件实现右滑关闭
            commentOverlay.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return handleCommentTouch(event);
                }
            });
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
            // 竖屏时隐藏锁屏按钮
            if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                btnLock.setVisibility(View.GONE);
            }
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
            seekBar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // 先标记按下位置，不立即跳转
                            return false; // 让 SeekBar 自己处理

                        case MotionEvent.ACTION_UP:
                            // 检查是否是点击（没有移动）
                            SeekBar sb = (SeekBar) v;
                            float touchX = event.getX();
                            float width = sb.getWidth();
                            if (width > 0 && touchX >= 0) {
                                int newProgress = (int) ((touchX / width) * sb.getMax());
                                // 限制范围
                                if (newProgress < 0) newProgress = 0;
                                if (newProgress > sb.getMax()) newProgress = sb.getMax();
                                sb.setProgress(newProgress);
                                // 执行跳转
                                if (mediaPlayer != null && isPrepared && mDuration > 0) {
                                    long position = ((long) newProgress) * mDuration / 1000;
                                    mediaPlayer.seekTo(position);
                                    if (mDanmakuManager != null) {
                                        mDanmakuManager.seekTo(position);
                                        if (isPlaying) {
                                            mDanmakuManager.resume();
                                        } else {
                                            mDanmakuManager.pause();
                                        }
                                    }
                                    if (tvCurrentTime != null) {
                                        tvCurrentTime.setText(formatTime((int) position));
                                    }
                                }
                            }
                            return false;

                        default:
                            return false;
                    }
                }
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null && isPrepared && mDuration > 0) {
                        long newPosition = ((long) progress) * mDuration / 1000;
                        Log.d("SeekBarDebug", "onProgressChanged: progress=" + progress + ", position=" + newPosition + "ms, duration=" + mDuration);
                        if (tvCurrentTime != null) {
                            tvCurrentTime.setText(formatTime((int) newPosition));
                        }
                    }
                }
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mIsDragging = true;
                    handler.removeMessages(MSG_HIDE_CONTROLS);
                    Log.d("SeekBarDebug", "onStartTrackingTouch: max=" + seekBar.getMax() + ", progress=" + seekBar.getProgress());
                }
                public void onStopTrackingTouch(SeekBar seekBar) {
                    mIsDragging = false;
                    int progress = seekBar.getProgress();
                    Log.d("SeekBarDebug", "onStopTrackingTouch: progress=" + progress + ", max=" + seekBar.getMax());
                    if (mediaPlayer != null && isPrepared && mDuration > 0) {
                        long position = ((long) progress) * mDuration / 1000;
                        Log.d("SeekBarDebug", "跳转到: " + position + "ms");
                        mediaPlayer.seekTo(position);
                        if (mDanmakuManager != null) mDanmakuManager.seekTo(position);
                        if (!isPlaying && mDanmakuManager != null) mDanmakuManager.pause();
                    }
                    showControlsWithAutoHide();
                }
            });
        }

        // 初始化弹幕管理器
        mDanmakuContainer = (FrameLayout) findViewById(R.id.danmaku_view);
        if (mDanmakuContainer != null) {
            mDanmakuManager = new DanmakuManager(this, mDanmakuContainer, mAid, mCid,
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
        createResetScaleButton();
    }

    private boolean handleCommentTouch(MotionEvent event) {
        float x = event.getRawX();
        float y = event.getRawY();
        final View panel = commentOverlay.findViewById(R.id.comment_panel);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                commentTouchStartX = x;
                commentTouchStartY = y;
                commentIsSwiping = false;
                return false;

            case MotionEvent.ACTION_MOVE:
                float dx = x - commentTouchStartX;
                float dy = y - commentTouchStartY;

                // 判断是否为水平滑动（水平距离大于垂直距离）
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 50) {
                    commentIsSwiping = true;
                    if (panel != null) {
                        // 使用 layout 实现滑动，限制最大滑动距离
                        int offset = (int) Math.min(dx, panel.getWidth());
                        if (offset < 0) offset = 0;
                        panel.layout(offset, panel.getTop(), offset + panel.getWidth(), panel.getBottom());

                        // 通过透明度变化实现淡出效果（使用 setAlpha 的兼容方式）
                        float progress = offset / (float) panel.getWidth();
                        int alpha = (int) (255 * (1.0f - progress * 0.6f));
                        if (commentScrim != null) {
                            commentScrim.setAlpha(alpha);
                        }
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float dx2 = x - commentTouchStartX;

                if (commentIsSwiping && panel != null) {
                    if (dx2 > SWIPE_THRESHOLD) {
                        // 右滑超过阈值，关闭评论
                        final int targetX = panel.getWidth();
                        android.view.animation.TranslateAnimation anim = new android.view.animation.TranslateAnimation(
                                0, targetX, 0, 0);
                        anim.setDuration(200);
                        anim.setFillAfter(true);
                        anim.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(android.view.animation.Animation animation) {}
                            @Override
                            public void onAnimationEnd(android.view.animation.Animation animation) {
                                panel.clearAnimation();
                                hideCommentOverlay();
                            }
                            @Override
                            public void onAnimationRepeat(android.view.animation.Animation animation) {}
                        });
                        panel.startAnimation(anim);
                    } else {
                        // 回弹
                        android.view.animation.TranslateAnimation anim = new android.view.animation.TranslateAnimation(
                                panel.getLeft(), 0, 0, 0);
                        anim.setDuration(200);
                        anim.setFillAfter(true);
                        anim.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(android.view.animation.Animation animation) {}
                            @Override
                            public void onAnimationEnd(android.view.animation.Animation animation) {
                                panel.clearAnimation();
                                panel.layout(0, panel.getTop(), panel.getWidth(), panel.getBottom());
                                if (commentScrim != null) {
                                    commentScrim.setAlpha(255);
                                }
                            }
                            @Override
                            public void onAnimationRepeat(android.view.animation.Animation animation) {}
                        });
                        panel.startAnimation(anim);
                    }
                    commentIsSwiping = false;
                    return true;
                }
                break;
        }
        return false;
    }

    private void createResetScaleButton() {
        FrameLayout parent = (FrameLayout) findViewById(android.R.id.content);
        if (parent == null) return;

        // 容器 - 和2.0x一样
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        containerParams.bottomMargin = dpToPx(80);
        container.setLayoutParams(containerParams);
        container.setVisibility(View.GONE);

        btnResetScale = new TextView(this);
        btnResetScale.setText("还原屏幕");
        btnResetScale.setTextSize(16);
        btnResetScale.setTextColor(0xFFD86DA5);
        btnResetScale.setGravity(Gravity.CENTER);
        btnResetScale.setBackgroundColor(0x88000000);
        btnResetScale.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12));
        btnResetScale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGestureController != null) {
                    mGestureController.resetScale();
                }
            }
        });

        container.addView(btnResetScale);
        parent.addView(container);
        mResetScaleContainer = container;

        container.bringToFront();
    }

    private void updateResetScaleButtonVisibility(float scale) {
        if (mResetScaleContainer == null) return;
        if (scale > 1.0f) {
            mResetScaleContainer.setVisibility(View.VISIBLE);
        } else {
            mResetScaleContainer.setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    //评论加载
    private void loadCommentsForOverlay() {
        if (commentLoaded || mAid == 0) return;
        if (commentIsLoading) return;

        commentIsLoading = true;
        commentNextCursor = "";
        commentIsEnd = false;
        commentIsLoadingMore = false;

        commentItems.clear();
        commentIdSet.clear();

        if (commentEmpty != null) {
            commentEmpty.setVisibility(View.VISIBLE);
            commentEmpty.setText("嘿咻…嘿咻…");
        }

        commentFooterView.setVisibility(View.GONE);
        if (commentFooterProgress != null) {
            commentFooterProgress.setVisibility(View.GONE);
        }

        final String oidParam = String.valueOf(mAid);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/v2/reply/main?type=1&oid=" + oidParam;
                    java.util.ArrayList<String> headers = new java.util.ArrayList<String>();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");
                    String cookies = SharedPreferencesUtil.getString("cookies", "");
                    if (cookies != null && cookies.length() > 0) {
                        headers.add("Cookie");
                        headers.add(cookies);
                    }
                    String response = NetWorkUtil.get(url, headers);
                    if (response == null || response.length() == 0) {
                        showCommentError("网络返回为空");
                        return;
                    }

                    org.json.JSONObject json = new org.json.JSONObject(response);
                    if (json.optInt("code", -1) == 0) {
                        org.json.JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            org.json.JSONObject cursor = data.optJSONObject("cursor");
                            if (cursor != null) {
                                commentNextCursor = cursor.optString("next", "");
                                commentIsEnd = cursor.optBoolean("is_end", true);
                            }

                            org.json.JSONArray replies = data.optJSONArray("replies");
                            if (replies != null && replies.length() > 0) {
                                parseCommentReplies(replies, false);
                                commentLoaded = true;
                                return;
                            }
                        }
                    }
                    showCommentEmpty("暂无评论");
                } catch (Exception e) {
                    showCommentError("加载失败: " + e.getMessage());
                } finally {
                    commentIsLoading = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (commentEmpty != null) {
                                commentEmpty.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void loadMoreCommentsForOverlay() {
        if (commentIsLoadingMore || commentIsEnd || commentIsLoading) return;
        if (commentNextCursor == null || commentNextCursor.length() == 0) {
            commentIsEnd = true;
            commentFooterView.setVisibility(View.GONE);
            return;
        }

        commentIsLoadingMore = true;
        commentFooterView.setVisibility(View.VISIBLE);
        if (commentFooterProgress != null) {
            commentFooterProgress.setVisibility(View.VISIBLE);
        }

        final String oidParam = String.valueOf(mAid);
        final String cursor = commentNextCursor;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/v2/reply/main?type=1&oid=" + oidParam + "&next=" + cursor;
                    java.util.ArrayList<String> headers = new java.util.ArrayList<String>();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");
                    String cookies = SharedPreferencesUtil.getString("cookies", "");
                    if (cookies != null && cookies.length() > 0) {
                        headers.add("Cookie");
                        headers.add(cookies);
                    }
                    String response = NetWorkUtil.get(url, headers);

                    if (response == null || response.length() == 0) {
                        showLoadMoreError("网络返回为空");
                        return;
                    }

                    org.json.JSONObject json = new org.json.JSONObject(response);
                    if (json.optInt("code", -1) == 0) {
                        org.json.JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            org.json.JSONObject cursor = data.optJSONObject("cursor");
                            if (cursor != null) {
                                commentNextCursor = cursor.optString("next", "");
                                commentIsEnd = cursor.optBoolean("is_end", true);
                            }

                            org.json.JSONArray replies = data.optJSONArray("replies");
                            if (replies != null && replies.length() > 0) {
                                parseCommentReplies(replies, true);
                                return;
                            }
                        }
                    }
                    showLoadMoreError("没有更多评论");
                } catch (final Exception e) {
                    showLoadMoreError("加载失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void parseCommentReplies(final org.json.JSONArray replies, final boolean isMore) {
        final java.util.List<CommentFragment.CommentItem> newItems =
                new java.util.ArrayList<CommentFragment.CommentItem>();

        for (int i = 0; i < replies.length(); i++) {
            try {
                org.json.JSONObject reply = replies.getJSONObject(i);
                if (reply == null) continue;

                long replyId = reply.optLong("rpid", 0);
                if (replyId == 0) continue;

                if (commentIdSet.contains(replyId)) {
                    continue;
                }
                commentIdSet.add(replyId);

                CommentFragment.CommentItem item = new CommentFragment.CommentItem();
                item.rpid = replyId;

                org.json.JSONObject member = reply.optJSONObject("member");
                if (member != null) {
                    item.userName = member.optString("uname", "匿名用户");
                    item.mid = member.optLong("mid", 0);
                    String avatar = member.optString("avatar", "");
                    if (avatar != null && avatar.length() > 0) {
                        avatar = avatar.replace("/64", "/48");
                        if (avatar.startsWith("https://")) {
                            avatar = "http://" + avatar.substring(8);
                        }
                    }
                    item.userAvatar = avatar;
                } else {
                    item.userName = "匿名用户";
                    item.userAvatar = null;
                    item.mid = 0;
                }

                org.json.JSONObject content = reply.optJSONObject("content");
                item.message = content != null ? content.optString("message", "") : "";
                item.likeCount = reply.optInt("like", 0);
                item.time = reply.optLong("ctime", 0);

                org.json.JSONArray replyReplies = reply.optJSONArray("replies");
                if (replyReplies != null && replyReplies.length() > 0) {
                    item.replies = new java.util.ArrayList<CommentFragment.ReplyItem>();
                    for (int j = 0; j < replyReplies.length(); j++) {
                        try {
                            org.json.JSONObject rr = replyReplies.getJSONObject(j);
                            CommentFragment.ReplyItem ri = new CommentFragment.ReplyItem();
                            org.json.JSONObject rmember = rr.optJSONObject("member");
                            if (rmember != null) {
                                ri.userName = rmember.optString("uname", "");
                                ri.mid = rmember.optLong("mid", 0);
                            }
                            org.json.JSONObject rcontent = rr.optJSONObject("content");
                            ri.message = rcontent != null ? rcontent.optString("message", "") : "";
                            item.replies.add(ri);
                        } catch (Exception e) { }
                    }
                }
                newItems.add(item);
            } catch (Exception e) { }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isMore) {
                    commentItems.addAll(newItems);
                    commentAdapter.updateData(commentItems);

                    commentFooterView.setVisibility(View.GONE);
                    if (commentFooterProgress != null) {
                        commentFooterProgress.setVisibility(View.GONE);
                    }
                    commentIsLoadingMore = false;

                    if (commentIsEnd) {
                        Toast.makeText(BiliPlayerActivity.this, getString(R.string.emoticon__no_more_data), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    commentItems.clear();
                    commentItems.addAll(newItems);
                    commentAdapter.updateData(commentItems);

                    if (commentItems.size() == 0) {
                        commentEmpty.setText("暂无评论");
                        commentEmpty.setVisibility(View.VISIBLE);
                    } else {
                        commentEmpty.setVisibility(View.GONE);
                        if (!commentIsEnd && commentNextCursor != null && commentNextCursor.length() > 0) {
                            commentFooterView.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
        });
    }

    private void showCommentError(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentIsLoading = false;
                commentEmpty.setText(msg);
                commentEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(BiliPlayerActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCommentEmpty(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentIsLoading = false;
                commentEmpty.setText(msg);
                commentEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showLoadMoreError(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentFooterView.setVisibility(View.GONE);
                if (commentFooterProgress != null) {
                    commentFooterProgress.setVisibility(View.GONE);
                }
                commentIsLoadingMore = false;
                Toast.makeText(BiliPlayerActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCommentOverlay() {
        if (commentOverlay == null || commentOverlay.getVisibility() == View.VISIBLE) return;

        // 显示评论前完全禁用所有手势（包括长按）
        if (mGestureController != null) {
            mGestureController.setEnableGesture(false);
            // 强制取消长按计时
            mGestureController.cancelLongPressForComment();
        }

        // 重置滑动状态
        touchStartX = 0;
        touchStartY = 0;

        commentOverlay.setVisibility(View.VISIBLE);
        View panel = commentOverlay.findViewById(R.id.comment_panel);
        if (panel != null) {
            panel.layout(0, panel.getTop(), panel.getWidth(), panel.getBottom());
            android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this,
                    R.anim.options_pannel_in);
            panel.startAnimation(anim);
        }
        if (commentScrim != null) {
            commentScrim.setVisibility(View.VISIBLE);
        }
        commentOverlay.requestFocus();
        if (!commentLoaded) {
            loadCommentsForOverlay();
        }
    }

    private void hideCommentOverlay() {
        if (commentOverlay == null || commentOverlay.getVisibility() != View.VISIBLE) return;
        View panel = commentOverlay.findViewById(R.id.comment_panel);
        if (panel != null) {
            android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this,
                    R.anim.options_pannel_out);
            anim.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                @Override
                public void onAnimationStart(android.view.animation.Animation animation) {}
                @Override
                public void onAnimationEnd(android.view.animation.Animation animation) {
                    commentOverlay.setVisibility(View.GONE);
                    // 评论关闭后恢复手势
                    if (mGestureController != null) {
                        mGestureController.setEnableGesture(true);
                        // 重置长按状态
                        mGestureController.cancelLongPressForComment();
                    }
                }
                @Override
                public void onAnimationRepeat(android.view.animation.Animation animation) {}
            });
            panel.startAnimation(anim);
        } else {
            commentOverlay.setVisibility(View.GONE);
            if (mGestureController != null) {
                mGestureController.setEnableGesture(true);
                mGestureController.cancelLongPressForComment();
            }
        }
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

    private void switchToPart(final int newPartIndex) {
        if (mCids == null || newPartIndex < 0 || newPartIndex >= mCids.length) return;

        if (mediaPlayer != null && isPrepared) {
            try { mQualitySwitchSeekPos = (int) mediaPlayer.getCurrentPosition(); } catch (Exception e) {}
        }

        showBuffering(true);

        final long newCid = mCids[newPartIndex];
        final String newTitle = (mPartNames != null && newPartIndex < mPartNames.length)
                ? mPartNames[newPartIndex] : videoTitle;

        new Thread(new Runnable() {
            public void run() {
                try {
                    PlayerData playerData = new PlayerData();
                    playerData.aid = mAid;
                    playerData.cid = newCid;
                    playerData.qn = mCurrentQn;
                    playerData.timeStamp = 0;

                    PlayerApi.getVideo(playerData, false);
                    final String newUrl = playerData.videoUrl;

                    if (newUrl != null && newUrl.length() > 0) {
                        final String[] newQnStrs = playerData.qnStrList;
                        final int[] newQnVals = playerData.qnValueList;

                        runOnUiThread(new Runnable() {
                            public void run() {
                                videoUrl = newUrl;
                                videoTitle = newTitle;
                                mCid = newCid;
                                mCurrentPartIndex = newPartIndex;
                                if (newQnStrs != null && newQnVals != null) {
                                    mQualityNames = newQnStrs;
                                    mQualityValues = newQnVals;
                                }
                                if (mQualityManager != null) {
                                    mQualityManager.updateCurrentQuality(mCurrentQn);
                                }
                                if (mDanmakuManager != null) {
                                    mDanmakuManager.pause();
                                    mDanmakuManager.release();
                                    mDanmakuManager = null;
                                }
                                if (tvTitle != null) tvTitle.setText(videoTitle);
                                if (decoderType == DECODER_SYSTEM) {
                                    releasePlayer();
                                    sPendingSeekPosition = 0;
                                    Intent intent = getIntent();
                                    intent.putExtra("video_url", newUrl);
                                    intent.putExtra("video_title", newTitle);
                                    intent.putExtra("cid", newCid);
                                    intent.putExtra("part_index", newPartIndex);
                                    if (newQnStrs != null) intent.putExtra("qn_str_array", newQnStrs);
                                    if (newQnVals != null) intent.putExtra("qn_value_array", newQnVals);
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
                                Toast.makeText(BiliPlayerActivity.this, "换P失败，获取播放地址失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            showBuffering(false);
                            Toast.makeText(BiliPlayerActivity.this, "换P失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showPartSelector() {
        if (mCids == null || mCids.length <= 1) return;
        String[] items = new String[mCids.length];
        for (int i = 0; i < mCids.length; i++) {
            items[i] = (i + 1) + ". " + (mPartNames != null && i < mPartNames.length ? mPartNames[i] : "P" + (i + 1));
        }
        new AlertDialog.Builder(this)
                .setTitle("选集")
                .setSingleChoiceItems(items, mCurrentPartIndex, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (which != mCurrentPartIndex) {
                            switchToPart(which);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
            mDanmakuManager = null;
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
                if (mDanmakuManager == null && mDanmakuContainer != null) {
                    mDanmakuManager = new DanmakuManager(BiliPlayerActivity.this,
                            mDanmakuContainer, mAid, mCid, danmakuInputStub);
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

        // 获取视频尺寸
        videoWidth = mp.getVideoWidth();
        videoHeight = mp.getVideoHeight();

        // 竖屏视频切竖屏
        if (VideoAspectRatioHelper.isPortraitVideo(videoWidth, videoHeight)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        // TextureView 不需要 adjustVideoSize
        if (!(mRendererType == RENDERER_TEXTUREVIEW && mRendererType == RENDERER_TEXTUREVIEW)) {
            adjustVideoSize();
        }
        updateTopBarForOrientation();

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

        if (mGestureController != null) {
            mGestureController.setMaxScale(3.0f);
        }

        mTextureViewConfigured = false;

        // 应用缩放
        applyVideoScale(1.0f, 0, 0);

        if (!isLiveStream) {
            handler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
        }
        handler.sendEmptyMessage(MSG_UPDATE_TIME);
        updateNetworkStatus();

        if (mDanmakuManager != null) {
            mDanmakuManager.onVideoPrepared(mp);
        }

        if (btnAspectRatio != null) {
            if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                btnAspectRatio.setVisibility(View.GONE);
            } else {
                btnAspectRatio.setVisibility(View.VISIBLE);
            }
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
        showControlsWithAutoHide();
    }

    @Override
    public void onSeekComplete(IMediaPlayer mp) {
        if (!mIsDragging) {
            updateTimeDisplay();
            // 只有正在播放时才恢复弹幕
            if (mDanmakuManager != null && isPlaying) {
                mDanmakuManager.resume();
            } else if (mDanmakuManager != null && !isPlaying) {
                mDanmakuManager.pause();
            }
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
                if (mCids == null || mCids.length <= 1) {
                    if (mDanmakuManager != null) mDanmakuManager.pause();
                } else if (mCurrentPartIndex < mCids.length - 1) {
                    switchToPart(mCurrentPartIndex + 1);
                } else if (completionAction == COMPLETION_ACTION_NEXT_LOOP) {
                    switchToPart(0);
                } else {
                    if (mDanmakuManager != null) mDanmakuManager.pause();
                }
                break;
            case COMPLETION_ACTION_PAUSE:
            default:
                if (mDanmakuManager != null) mDanmakuManager.pause();
                break;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        showBuffering(false);

        int sdkInt = android.os.Build.VERSION.SDK_INT;

        if (decoderType == DECODER_SYSTEM && mAllowDecoderFallback) {
            if (!isPrepared) {
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

            if (mHardwareDecodeRetryCount < MAX_HARDWARE_RETRY) {
                mHardwareDecodeRetryCount++;
                handler.postDelayed(new Runnable() {
                    public void run() {
                        cleanupAndRestart();
                    }
                }, 500);
                return true;
            }

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

            mAllowDecoderFallback = false;
            decoderType = DECODER_IJK_SOFT;
            mHardwareDecodeRetryCount = 0;
            Toast.makeText(this, "IJK硬解失败，切换到IJK软解", Toast.LENGTH_LONG).show();
            cleanupAndRestart();
            return true;
        }

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
            mDanmakuManager = null;
        }

        releasePlayer();

        recreateVideoView();

        surfaceReady = false;
        pendingPrepare = false;

        handler.postDelayed(new Runnable() {
            public void run() {
                if (mDanmakuManager == null && mDanmakuContainer != null) {
                    mDanmakuManager = new DanmakuManager(BiliPlayerActivity.this,
                            mDanmakuContainer, mAid, mCid, danmakuInputStub);
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
                }
                break;
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
    }

    private void adjustVideoSize() {
        // TextureView 不在这里处理，由 applyVideoScale 的 Matrix 控制
        if (mRendererType == RENDERER_TEXTUREVIEW && mRendererType == RENDERER_TEXTUREVIEW) {
            return;
        }

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

        VideoAspectRatioHelper.autoRotateIfPortrait(this, videoWidth, videoHeight,
                !aspectRatioFixed, btnAspectRatio, mGestureController);

        applyAspectRatio(currentAspectRatio);
    }

    private void applyAspectRatio(int mode) {
        if (videoWidth == 0 || videoHeight == 0) return;

        FrameLayout container = (FrameLayout) findViewById(R.id.video_container);
        if (container == null) return;

        int containerWidth = container.getWidth();
        int containerHeight = container.getHeight();

        if (containerWidth == 0 || containerHeight == 0) {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            containerWidth = dm.widthPixels;
            containerHeight = dm.heightPixels;
        }

        currentAspectRatio = mode;

        if (decoderType == DECODER_IJK_HARD && mRendererType == RENDERER_SURFACEVIEW) {
            applyVideoScale(1.0f, 0, 0);
            return;
        }

        // TextureView 通过 Matrix 控制
        if (mRendererType == RENDERER_TEXTUREVIEW && mRendererType == RENDERER_TEXTUREVIEW) {
            // 重新应用缩放
            if (mGestureController != null) {
                applyVideoScale(mGestureController.getCurrentScale(),
                        mGestureController.getTranslateX(),
                        mGestureController.getTranslateY());
            } else {
                applyVideoScale(1.0f, 0, 0);
            }
            return;
        }

        // SurfaceView 用 LayoutParams
        float containerRatio = (float) containerWidth / containerHeight;
        float videoRatio = (float) videoWidth / videoHeight;

        float targetRatio;
        switch (mode) {
            case ASPECT_RATIO_ADJUST_CONTENT:
                targetRatio = videoRatio;
                break;
            case ASPECT_RATIO_ADJUST_SCREEN:
                targetRatio = containerRatio;
                break;
            case ASPECT_RATIO_4_3_INSIDE:
                targetRatio = 4f / 3f;
                break;
            case ASPECT_RATIO_16_9_INSIDE:
                targetRatio = 16f / 9f;
                break;
            case ASPECT_RATIO_9_16_INSIDE:
                targetRatio = 9f / 16f;
                break;
            default:
                targetRatio = videoRatio;
                break;
        }

        int targetWidth, targetHeight;
        if (targetRatio > containerRatio) {
            targetWidth = containerWidth;
            targetHeight = (int) (containerWidth / targetRatio);
        } else {
            targetHeight = containerHeight;
            targetWidth = (int) (containerHeight * targetRatio);
        }

        if (targetWidth < 1) targetWidth = 1;
        if (targetHeight < 1) targetHeight = 1;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoView.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(targetWidth, targetHeight);
            params.gravity = android.view.Gravity.CENTER;
        } else {
            params.width = targetWidth;
            params.height = targetHeight;
            params.gravity = android.view.Gravity.CENTER;
            params.leftMargin = 0;
            params.topMargin = 0;
            params.rightMargin = 0;
            params.bottomMargin = 0;
        }
        videoView.setLayoutParams(params);
        videoView.requestLayout();

        if (mRendererType == RENDERER_SURFACEVIEW && surfaceHolder != null) {
            try {
                surfaceHolder.setFixedSize(videoWidth, videoHeight);
            } catch (Exception e) {}
        }
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
                    SharedPreferencesUtil.putInt(SharedPreferencesUtil.COMPLETION_ACTION, completionAction);
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
            long duration = mediaPlayer.getDuration();
            if (duration <= 0) {
                duration = mDuration;
            }
            if (seekBar != null && duration > 0) {
                int progress = (int) (1000L * current / duration);
                if (progress < 0) progress = 0;
                if (progress > 1000) progress = 1000;
                seekBar.setProgress(progress);
            }
            if (tvCurrentTime != null) {
                tvCurrentTime.setText(formatTime((int) current));
            }

            int progressMs = (int) current;
            if (progressMs > 0 && progressMs % 5000 < 250) {
                reportHistory(progressMs);
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
        if (commentOverlay != null && commentOverlay.getVisibility() != View.VISIBLE) {
            int edgeThreshold = (int) (getResources().getDisplayMetrics().density * 30);
            int screenWidth = getWindow().getWindowManager().getDefaultDisplay().getWidth();

            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (ev.getX() >= screenWidth - edgeThreshold) {
                        touchStartX = ev.getX();
                        touchStartY = ev.getY();
                        if (mGestureController != null) {
                            mGestureController.setEnableGesture(false);
                        }
                        return super.dispatchTouchEvent(ev);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (touchStartX > 0) {
                        float dx = touchStartX - ev.getX();
                        float dy = Math.abs(touchStartY - ev.getY());

                        // 水平滑动超过阈值，打开评论
                        if (dx > getResources().getDisplayMetrics().density * 40 && dy < getResources().getDisplayMetrics().density * 100) {
                            touchStartX = 0;
                            touchStartY = 0;
                            showCommentOverlay();
                            return true;
                        }

                        // 如果垂直偏移太大，取消评论滑动，恢复手势
                        if (dy > getResources().getDisplayMetrics().density * 50) {
                            touchStartX = 0;
                            touchStartY = 0;
                            if (mGestureController != null) {
                                mGestureController.setEnableGesture(true);
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (touchStartX > 0) {
                        touchStartX = 0;
                        touchStartY = 0;
                        if (mGestureController != null) {
                            mGestureController.setEnableGesture(true);
                        }
                    }
                    break;
            }
        }

        // 评论覆盖层可见时，所有事件交给评论处理，完全禁用手势
        if (commentOverlay != null && commentOverlay.getVisibility() == View.VISIBLE) {
            if (mGestureController != null) {
                mGestureController.setEnableGesture(false);
            }
            return super.dispatchTouchEvent(ev);
        }

        if (isPrepared && !isLiveStream && mGestureController != null) {
            if (ev.getPointerCount() >= 2) {
                int[] location = new int[2];
                videoView.getLocationOnScreen(location);
                float touchX = ev.getRawX();
                float touchY = ev.getRawY();
                if (touchX >= location[0] && touchX <= location[0] + videoView.getWidth() &&
                        touchY >= location[1] && touchY <= location[1] + videoView.getHeight()) {
                    mGestureController.onTouchEvent(ev);
                    return super.dispatchTouchEvent(ev);
                }
            }
        }

        // 单指手势交给 GestureController（只在非评论滑动时）
        if (mGestureController != null && ev.getPointerCount() == 1 && touchStartX == 0) {
            // 确保手势已启用，但不要重复启用
            if (!mGestureController.isGestureEnabled()) {
                mGestureController.setEnableGesture(true);
            }
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
            // 评论覆盖层可见时，先关闭评论
            if (commentOverlay != null && commentOverlay.getVisibility() == View.VISIBLE) {
                hideCommentOverlay();
                return true;
            }
            // 画质列表可见时，先关闭画质列表
            if (mQualityManager != null && mQualityManager.isQualityListVisible()) {
                mQualityManager.hideQualityList();
                return true;
            }
            // 弹幕输入框可见时，先关闭弹幕输入
            if (mDanmakuManager != null && mDanmakuManager.isInputVisible()) {
                mDanmakuManager.hideInputPanel(mPlayControl);
                return true;
            }
            // 弹窗面板可见时，先关闭弹窗
            if ((mPlayerOptionsPannel != null && mPlayerOptionsPannel.isShowing())
                    || (mDanmakuManager != null && mDanmakuManager.isOptionsPanelShowing())) {
                dismissAllPanels();
                return true;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - mLastBackPressTime < BACK_PRESS_INTERVAL) {
                mLastBackPressTime = 0;
                finish();
                return true;
            } else {
                mLastBackPressTime = currentTime;
                Toast.makeText(this, "再次按后退可以结束播放", Toast.LENGTH_SHORT).show();
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
        if (batteryView != null) {
            batteryView.release();
            batteryView = null;
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

    private void updateTopBarForOrientation() {
        boolean portrait = getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (tvDateTime != null) tvDateTime.setVisibility(portrait ? View.GONE : View.VISIBLE);
        if (tvNetworkStatus != null) tvNetworkStatus.setVisibility(portrait ? View.GONE : View.VISIBLE);
        if (mBatteryView != null) mBatteryView.setVisibility(portrait ? View.GONE : View.VISIBLE);
        if (btnLock != null) {btnLock.setVisibility(portrait ? View.GONE : View.VISIBLE);
        }

        // 竖屏时自动解锁
        if (portrait && playerLocked) {
            unlock();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isPrepared) {
            if (mGestureController != null) {
                mGestureController.onOrientationChanged();
            }
            if (mGestureController != null) {
                updateResetScaleButtonVisibility(mGestureController.getCurrentScale());
            }
            if (btnAspectRatio != null) {
                if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    btnAspectRatio.setVisibility(View.GONE);
                } else {
                    btnAspectRatio.setVisibility(View.VISIBLE);
                }
            }
            updateTopBarForOrientation();
            if (isPlaying && !isLiveStream) {
                handler.removeMessages(MSG_UPDATE_PROGRESS);
                handler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
            }
            videoView.postDelayed(new Runnable() {
                public void run() {
                    applyAspectRatio(currentAspectRatio);
                    // 屏幕旋转后重新应用缩放
                    if (mGestureController != null) {
                        applyVideoScale(mGestureController.getCurrentScale(),
                                mGestureController.getTranslateX(),
                                mGestureController.getTranslateY());
                    }
                }
            }, 100);
        }
    }
}