package tv.biliclassic.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
import tv.biliclassic.ProxyStreamService;
import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.UserProfileActivity;
import tv.biliclassic.api.PlayerApi;
import tv.biliclassic.model.PlayerData;
import tv.biliclassic.player.danmaku.DanmakuManager;
import tv.biliclassic.subsettings.DecoderSettingsActivity;
import tv.biliclassic.util.CookieGenerator;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.widget.MarqueeTextView;
import tv.biliclassic.widget.RadioGridGroup;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.biliclassic.widget.BatteryView2;
import tv.biliclassic.util.DeviceInfoUtil;
import tv.biliclassic.util.FileProviderCompat;
import tv.biliclassic.util.MediaSessionHelper;
import util.LocalStreamProxy;

import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.SdkHelper;
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
    // 凉腕播放器返回播放进度（测试用）
    private static final int REQ_LIANGWAN_PROGRESS = 0x5A11;

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
    private String audioUrl;
    private long mProxyDurationMs;
    private String videoTitle;
    private String cachePath;
    private boolean isLiveStream;
    private int decoderType;
    private LocalStreamProxy localProxy;

    private boolean isPlaying = false;
    private boolean isPrepared = false;
    private MediaSessionHelper mediaSessionHelper;
    private Runnable mRehideNavRunnable = new Runnable() {
        public void run() { hideSystemUI(); }
    };
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
    private static final long OK_DOUBLE_CLICK_INTERVAL = 300;
    private Handler mOkHandler = new Handler();
    private Runnable mOkSingleClick = new Runnable() {
        public void run() {
            if (!isPrepared) return;
            showControlsWithAutoHide();
        }
    };
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
            if (mediaPlayer != null && isPrepared) { mediaPlayer.start(); isPlaying = true; updatePlayPauseButton(); if (!isLiveStream) handler.sendEmptyMessage(MSG_UPDATE_PROGRESS); }
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
    private boolean autoRotation;
    private boolean portraitRotation;

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

    // 加载动画（preloading 布局，动画由 AnimationDrawable 自动播放）
    private View mLoadingOverlay;
    private ImageView mLoadingIcon;
    // 两阶段加载状态：都在左下角状态栏上，第一行（获取播放地址）显示在第二行（正在加载视频）上方
    private TextView mLoadingStep1;
    private String mLoadStep1Text;
    private String mLoadStep2Text;
    private boolean mUrlResolved = false;

    private Object createSurfaceTextureListener() {
        return new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int width, int height) {
                mVideoSurface = new Surface(st);
                surfaceReady = true;
                if (pendingPrepare) {
                    pendingPrepare = false;
                    resolveAndPrepare();
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

        // 全面屏挖孔适配：API 28+ 延伸到挖孔区域
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (SdkHelper.getSdkInt() >= 28) {
            try {
                android.view.WindowManager.LayoutParams attrs = getWindow().getAttributes();
                java.lang.reflect.Field f = android.view.WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode");
                f.setInt(attrs, 1);
                getWindow().setAttributes(attrs);
            } catch (Exception e) {
            }
        }
        if (SdkHelper.getSdkInt() >= 19) {
            hideSystemUI();
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                new android.view.View.OnSystemUiVisibilityChangeListener() {
                    public void onSystemUiVisibilityChange(int visibility) {
                        if ((visibility & android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                            handler.removeCallbacks(mRehideNavRunnable);
                            handler.postDelayed(mRehideNavRunnable, 3000);
                        }
                    }
                });
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF000000));
        // API 21+ 消费所有系统窗口插入，防止旋转后安全区/挖孔推回布局
        if (SdkHelper.getSdkInt() >= 21) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener(
                new android.view.View.OnApplyWindowInsetsListener() {
                    public android.view.WindowInsets onApplyWindowInsets(
                            android.view.View v, android.view.WindowInsets insets) {
                        return insets.consumeSystemWindowInsets();
                    }
                });
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.bili_app_player_view_new);

        if (!getIntent().getBooleanExtra("offline_mode", false)) {
            initLoadingOverlay();
        }

        videoUrl = getIntent().getStringExtra("video_url");
        audioUrl = getIntent().getStringExtra("audio_url");
        mProxyDurationMs = getIntent().getLongExtra("duration_ms", 0);
        videoTitle = getIntent().getStringExtra("video_title");
        cachePath = getIntent().getStringExtra("cache_path");
        final String coverUrl = getIntent().getStringExtra("cover_url");

        mediaSessionHelper = new MediaSessionHelper(this, BiliPlayerActivity.class);
        mediaSessionHelper.setPlayPauseListener(new MediaSessionHelper.PlayPauseListener() {
            @Override
            public void onPlayPause() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        togglePlayPause();
                    }
                });
            }
        });
        if (coverUrl != null && coverUrl.length() > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = loadCoverBitmap(coverUrl);
                    if (bitmap != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mediaSessionHelper != null) {
                                    mediaSessionHelper.setCoverBitmap(bitmap);
                                    mediaSessionHelper.setMetadata(videoTitle, "");
                                }
                            }
                        });
                    }
                }
            }).start();
        }
        isLiveStream = getIntent().getBooleanExtra("live", false);
        boolean onlineMode = getIntent().getBooleanExtra("online_mode", false);
        decoderType = SettingsActivity.getDecoderType();
        // 系统 MediaPlayer 无法解析 MPEG-DASH（音视频分离流）。
        // 在线 DASH 时即使解码器偏好选了"系统"也强制走内置 IJK 路径：
        // Android 4.1+（MediaCodec 可用）优先硬解，硬解失败会自动回退软解；
        // 更老设备直接用软解。
        if (onlineMode && SettingsActivity.getPlayStreamFormat() == 16
                && decoderType == DECODER_SYSTEM) {
            decoderType = SdkHelper.getSdkInt() >= 16 ? DECODER_IJK_HARD : DECODER_IJK_SOFT;
        }
        mRendererType = SettingsActivity.getRendererType();
        if (mRendererType == RENDERER_TEXTUREVIEW && SdkHelper.getSdkInt() < 14) {
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

        // 非内置播放器且在线模式 → 自动关闭在线播放，交给外部播放器
        if (!mOfflineMode && !getIntent().getBooleanExtra("_from_external_redirect", false)) {
            int pref = SettingsActivity.getPlayerPreference();
            if (pref != 8) {
                String playerPkg = SettingsActivity.getPlayerPackageName();
                // DASH（音视频分离）只有内置 IJK 能播，不转交 Ostwind/外部播放器
                if (videoUrl != null && videoUrl.length() > 0 && (audioUrl == null || audioUrl.length() == 0)) {
                    if ("tv.biliclassic.ostwind".equals(playerPkg)) {
                        // Ostwind 简易播放器：本 App 内 Activity，MediaPlayer + 自定义请求头
                        Intent wIntent = new Intent(this, OstwindPlayerActivity.class);
                        wIntent.putExtra("video_url", videoUrl);
                        String cookie = CookieGenerator.getCookieString(true);
                        if (cookie != null && cookie.length() > 0) {
                            wIntent.putExtra("cookie", cookie);
                        }
                        wIntent.putExtra("agent", NetWorkUtil.USER_AGENT_WEB);
                        wIntent.putExtra("_from_external_redirect", true);
                        try {
                            startActivity(wIntent);
                            releaseBatteryReceiver();
                            finish();
                            return;
                        } catch (Exception e) {
                        }
                    }
                    Intent extIntent;
                    // 在线播放：给所有外部播放器传本地代理地址（代理带 Referer/Cookie/UA 请求头
                    // 转发，外部播放器无法携带请求头，B 站 CDN 会 403 拒绝直连）
                    boolean isNet = videoUrl.startsWith("http://") || videoUrl.startsWith("https://");
                    String playUrl = videoUrl;
                    if (isNet) {
                        try {
                            String proxied = ProxyStreamService.startProxyForExternal(this,
                                    videoUrl, getProxyHeaders());
                            if (proxied != null && proxied.length() > 0) {
                                playUrl = proxied;
                            }
                        } catch (Throwable t) {
                        }
                    }
                    if ("com.aliangmaker.media".equals(playerPkg)) {
                        // 凉腕播放器：直接跳转其 PlayVideoActivity，并按凉腕约定附加在线播放信息。
                        // 用 startActivityForResult 接收凉腕返回的播放进度（setResult putExtra("progress")），
                        // 便于上报 B 站历史记录续播位置。
                        extIntent = new Intent();
                        extIntent.setClassName("com.aliangmaker.media",
                                "com.aliangmaker.media.PlayVideoActivity");
                        extIntent.setAction(Intent.ACTION_VIEW);
                        extIntent.setData(Uri.parse(playUrl));
                        putLiangwanExtras(extIntent);
                        extIntent.putExtra("_from_external_redirect", true);
                        try {
                            startActivityForResult(extIntent, REQ_LIANGWAN_PROGRESS);
                            releaseBatteryReceiver();
                            return;
                        } catch (Exception e) {
                        }
                    } else {
                        extIntent = new Intent(Intent.ACTION_VIEW);
                        extIntent.setDataAndType(Uri.parse(playUrl), "video/mp4");
                        if (playerPkg != null) {
                            try { Intent.class.getMethod("setPackage", String.class).invoke(extIntent, new Object[]{playerPkg}); } catch (Exception ignored) {};
                        }
                        putExternalPlayerExtras(extIntent);
                    }
                    extIntent.putExtra("_from_external_redirect", true);
                    try {
                        startActivity(extIntent);
                        // 跳转前注销电池广播，避免 Activity finish 时 view 未 detach 导致 receiver 泄漏
                        releaseBatteryReceiver();
                        finish();
                        return;
                    } catch (Exception e) {
                    }
                }
            }
        }

        if (onlineMode) {
            if (videoUrl == null || videoUrl.length() == 0) {
                Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_5728), Toast.LENGTH_SHORT).show();
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
        // 断点续播：从 B 站播放地址接口带回的上次进度（毫秒）作为默认 seek 位置。
        // 仅在没有任何其他待跳转位置（如画质切换/后台恢复）时生效；直播不续播。
        if (mSeekWhenPrepared <= 0 && !isLiveStream) {
            mSeekWhenPrepared = getIntent().getIntExtra("resume_position", 0);
        }
        enableGesture = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.ENABLE_GESTURE, true);
        keepBackground = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.KEEP_BACKGROUND, true);
        completionAction = SharedPreferencesUtil.getInt(SharedPreferencesUtil.COMPLETION_ACTION, COMPLETION_ACTION_PAUSE);
        autoRotation = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.PLAYER_AUTO_ROTATION, false);
        portraitRotation = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.PLAYER_PORTRAIT_ROTATION, false);
        applyAutoRotation();

        if (DeviceInfoUtil.isUnsupportedCpu()) {
            if (!DeviceInfoUtil.isLegacy) {
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.biliplayeractivity_settitle_8bbe))
                .setMessage("ARMv5TE 或无 VFP 的 ARMv6 设备无法使用内置播放器，请关闭\"在线播放\"后下载视频，使用第三方播放器播放。")
                .setPositiveButton("继续尝试", null)
                .setNegativeButton("确定", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
                return;
            }
        }

        initViews();
        initPlayer();
        initGestureController();
        if (mGestureController != null) {
            mGestureController.setEnableGesture(enableGesture);
        }
    }

    /**
     * 给外部播放器 intent 附加 B 站在线播放所需信息：
     * cookie（含 Cookie + Referer 头）、agent（网页版 UA）、progress（进度）、
     * name（标题）、danmaku（弹幕 XML 地址）、live_mode（直播标记）。
     * 支持这些 extras 的播放器（如凉腕）可据此直接在线播放。
     */
    private void putExternalPlayerExtras(Intent extIntent) {
        try {
            String cookie = CookieGenerator.getCookieString(true);
            String referer = "https://www.bilibili.com/";
            if (cookie != null && cookie.length() > 0) {
                extIntent.putExtra("cookie", cookie);
                extIntent.putExtra("referer", referer);
            }
            extIntent.putExtra("agent", NetWorkUtil.USER_AGENT_WEB);
            if (videoTitle != null) {
                extIntent.putExtra("name", videoTitle);
            }
            if (mCid > 0) {
                extIntent.putExtra("danmaku", "https://comment.bilibili.com/" + mCid + ".xml");
            }
            // 断点续播进度（毫秒）：从 B 站获取的上次观看位置
            extIntent.putExtra("progress", getIntent().getIntExtra("resume_position", 0));
            extIntent.putExtra("live_mode", false);
        } catch (Throwable t) {
        }
    }

    /**
     * 凉腕播放器（com.aliangmaker.media）在线播放附加信息（凉腕端约定）：
     * cookie 必须是可序列化的 HashMap<String,String>（Cookie + Referer 头），
     * agent 为网页 UA，progress 为毫秒，name/danmaku/live_mode 见文档。
     */
    private void putLiangwanExtras(Intent extIntent) {
        try {
            if (videoTitle != null) {
                extIntent.putExtra("name", videoTitle);
            }
            if (mCid > 0) {
                extIntent.putExtra("danmaku", "https://comment.bilibili.com/" + mCid + ".xml");
            }
            extIntent.putExtra("live_mode", false);

            // 在线视频必须带请求头，绕过 B 站防盗链
            java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
            String cookie = CookieGenerator.getCookieString(true);
            if (cookie != null && cookie.length() > 0) {
                headers.put("Cookie", cookie);
            }
            headers.put("Referer", "https://www.bilibili.com/");
            extIntent.putExtra("cookie", (java.io.Serializable) headers);

            extIntent.putExtra("agent", NetWorkUtil.USER_AGENT_WEB);
            // 续播进度（毫秒）：从 B 站获取的上次观看位置
            extIntent.putExtra("progress", (long) getIntent().getIntExtra("resume_position", 0));
        } catch (Throwable t) {
        }
    }

    /**
     * 注销电池广播 receiver，防止外部播放器跳转/退出时
     * BatteryView2 未 detach 导致 IntentReceiver 泄漏。
     */
    private void releaseBatteryReceiver() {
        try {
            if (batteryView != null) {
                batteryView.release();
            } else if (mBatteryView instanceof BatteryView2) {
                ((BatteryView2) mBatteryView).release();
            } else {
                View bv = findViewById(R.id.battery_view);
                if (bv instanceof BatteryView2) {
                    ((BatteryView2) bv).release();
                }
            }
        } catch (Throwable t) {
        }
    }

    private void initLoadingOverlay() {
        FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        if (root == null) return;

        mLoadingOverlay = LayoutInflater.from(this).inflate(
                R.layout.bili_app_player_preloading, root, false);
        mLoadingOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mLoadingIcon = (ImageView) mLoadingOverlay.findViewById(R.id.tv_chan_animation);
        // 两阶段加载状态：都放在左下角状态栏（样式与「正在加载…」一致），
        // 第一行"获取播放地址"显示在第二行"正在加载视频"上方。
        mLoadingStep1 = (TextView) mLoadingOverlay.findViewById(R.id.video_preloading_status_bar);
        // preloading 布局里用不到的杂项元素（重试/返回/随机提示等）一律隐藏，
        // 加载失败由本 Activity 自己的逻辑处理
        hidePreloadingExtraViews(mLoadingOverlay);
        // 底部状态栏文字：默认"正在加载视频……"，转码/准备阶段再逐行覆盖
        setLoadingStep2(getString(R.string.player_loading_step_loading_video));
        root.addView(mLoadingOverlay);
        startLoadingAnimation();
    }

    private void startLoadingAnimation() {
        try {
            if (mLoadingIcon != null) {
                mLoadingIcon.setImageResource(R.anim.bili_loading_tv_chan);
                android.graphics.drawable.AnimationDrawable ad =
                        (android.graphics.drawable.AnimationDrawable) mLoadingIcon.getDrawable();
                if (ad != null) {
                    ad.stop();
                    ad.start();
                }
            }
        } catch (Throwable t) {
        }
    }

    private void stopLoadingAnimation() {
        try {
            if (mLoadingIcon != null) {
                android.graphics.drawable.Drawable d = mLoadingIcon.getDrawable();
                if (d instanceof android.graphics.drawable.AnimationDrawable) {
                    ((android.graphics.drawable.AnimationDrawable) d).stop();
                }
            }
        } catch (Throwable t) {
        }
    }

    private void hidePreloadingExtraViews(View overlay) {
        int[] extraIds = {
                R.id.press_back_to_exit,
                R.id.random_tips,
                R.id.preloading_overlay,
                R.id.refresh,
                R.id.retry_tips,
                R.id.refresh_tips
        };
        for (int id : extraIds) {
            try {
                View v = overlay.findViewById(id);
                if (v != null) v.setVisibility(View.GONE);
            } catch (Throwable t) {
            }
        }
        // 保留返回按钮：点击退出播放器
        try {
            View backBtn = overlay.findViewById(R.id.back);
            if (backBtn != null) {
                backBtn.setVisibility(View.VISIBLE);
                backBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
        } catch (Throwable t) {
        }
    }

    private void hideLoadingOverlay() {
        if (mLoadingOverlay != null && mLoadingOverlay.getVisibility() == View.VISIBLE) {
            stopLoadingAnimation();
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

                    int baseWidth, baseHeight;
                    if (targetRatio > containerRatio) {
                        baseWidth = containerWidth;
                        baseHeight = (int) (containerWidth / targetRatio);
                    } else {
                        baseHeight = containerHeight;
                        baseWidth = (int) (containerHeight * targetRatio);
                    }

                    if (baseWidth < 1) baseWidth = 1;
                    if (baseHeight < 1) baseHeight = 1;

                    // 布局尺寸固定为基础尺寸（容器适配），避免尺寸变化触发
                    // onSurfaceTextureSizeChanged → Surface 重建 → 放大抖动
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tv.getLayoutParams();
                    if (lp == null) {
                        lp = new FrameLayout.LayoutParams(baseWidth, baseHeight);
                    }
                    lp.width = baseWidth;
                    lp.height = baseHeight;
                    lp.gravity = android.view.Gravity.CENTER;
                    lp.leftMargin = 0;
                    lp.topMargin = 0;
                    lp.rightMargin = 0;
                    lp.bottomMargin = 0;
                    tv.setLayoutParams(lp);

                    // 缩放/平移走 View 变换层（不改变布局尺寸、不触发 surface 重建）
                    float userScale = fScale;
                    if (userScale < 1.0f) userScale = 1.0f;

                    float maxTranslateX = Math.max(0, (baseWidth * userScale - containerWidth) / 2.0f);
                    float maxTranslateY = Math.max(0, (baseHeight * userScale - containerHeight) / 2.0f);
                    float finalTranslateX = fTranslateX * maxTranslateX;
                    float finalTranslateY = fTranslateY * maxTranslateY;

                    if (finalTranslateX > maxTranslateX) finalTranslateX = maxTranslateX;
                    if (finalTranslateX < -maxTranslateX) finalTranslateX = -maxTranslateX;
                    if (finalTranslateY > maxTranslateY) finalTranslateY = maxTranslateY;
                    if (finalTranslateY < -maxTranslateY) finalTranslateY = -maxTranslateY;

                    if (userScale <= 1.0f) {
                        finalTranslateX = 0;
                        finalTranslateY = 0;
                    }

                    // pivot 设在视图中心：缩放围绕中心，不产生位置偏移
                    tv.setPivotX(baseWidth / 2.0f);
                    tv.setPivotY(baseHeight / 2.0f);
                    tv.setScaleX(userScale);
                    tv.setScaleY(userScale);
                    tv.setTranslationX(finalTranslateX);
                    tv.setTranslationY(finalTranslateY);
                    tv.setRotation(0);
                    tv.requestLayout();
                }
            });
            return;
        }
        // ===== SurfaceView（IJK硬解/软解/系统解码器）：恢复正常版本的"改 LayoutParams 尺寸"缩放路径。
        // 旧版安卓上 SurfaceView 的 View 变换（setScaleX/setScaleY）不缩放 Surface 内容，
        // 只能通过改变 SurfaceView 的 LayoutParams 尺寸 + setFixedSize 真实缩放。
        if (mRendererType == RENDERER_SURFACEVIEW) {
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

            float svContainerRatio = (float) containerWidth / containerHeight;
            float svVideoRatio = (float) videoWidth / videoHeight;

            float svTargetRatio;
            switch (currentAspectRatio) {
                case ASPECT_RATIO_ADJUST_CONTENT:
                    svTargetRatio = svVideoRatio;
                    break;
                case ASPECT_RATIO_ADJUST_SCREEN:
                    svTargetRatio = svContainerRatio;
                    break;
                case ASPECT_RATIO_4_3_INSIDE:
                    svTargetRatio = 4f / 3f;
                    break;
                case ASPECT_RATIO_16_9_INSIDE:
                    svTargetRatio = 16f / 9f;
                    break;
                case ASPECT_RATIO_9_16_INSIDE:
                    svTargetRatio = 9f / 16f;
                    break;
                default:
                    svTargetRatio = svVideoRatio;
                    break;
            }

            int svTargetWidth, svTargetHeight;
            if (svTargetRatio > svContainerRatio) {
                svTargetWidth = containerWidth;
                svTargetHeight = (int) (containerWidth / svTargetRatio);
            } else {
                svTargetHeight = containerHeight;
                svTargetWidth = (int) (containerHeight * svTargetRatio);
            }
            if (svTargetWidth < 1) svTargetWidth = 1;
            if (svTargetHeight < 1) svTargetHeight = 1;

            // 按用户缩放倍率放大目标尺寸
            float svUserScale = scale;
            if (svUserScale < 1.0f) svUserScale = 1.0f;
            int svFinalW = (int) (svTargetWidth * svUserScale);
            int svFinalH = (int) (svTargetHeight * svUserScale);
            if (svFinalW < 1) svFinalW = 1;
            if (svFinalH < 1) svFinalH = 1;

            float svMaxTranslateX = Math.max(0, (svFinalW - containerWidth) / 2.0f);
            float svMaxTranslateY = Math.max(0, (svFinalH - containerHeight) / 2.0f);
            float svFinalTranslateX = translateX * svMaxTranslateX;
            float svFinalTranslateY = translateY * svMaxTranslateY;

            if (svFinalTranslateX > svMaxTranslateX) svFinalTranslateX = svMaxTranslateX;
            if (svFinalTranslateX < -svMaxTranslateX) svFinalTranslateX = -svMaxTranslateX;
            if (svFinalTranslateY > svMaxTranslateY) svFinalTranslateY = svMaxTranslateY;
            if (svFinalTranslateY < -svMaxTranslateY) svFinalTranslateY = -svMaxTranslateY;

            if (scale <= 1.0f) {
                svFinalTranslateX = 0;
                svFinalTranslateY = 0;
            }

            FrameLayout.LayoutParams svLp = (FrameLayout.LayoutParams) videoView.getLayoutParams();
            if (svLp == null) {
                svLp = new FrameLayout.LayoutParams(svFinalW, svFinalH);
            }
            svLp.width = svFinalW;
            svLp.height = svFinalH;
            svLp.leftMargin = (containerWidth - svFinalW) / 2 + (int) svFinalTranslateX;
            svLp.topMargin = (containerHeight - svFinalH) / 2 + (int) svFinalTranslateY;
            svLp.rightMargin = 0;
            svLp.bottomMargin = 0;
            svLp.gravity = android.view.Gravity.LEFT | android.view.Gravity.TOP;
            videoView.setLayoutParams(svLp);
            videoView.requestLayout();

            // SurfaceView 内容随 View 尺寸变化而拉伸/放大（改尺寸缩放路径）
            if (surfaceHolder != null && videoWidth > 0 && videoHeight > 0) {
                try {
                    int bufferW = (int) (videoWidth * svUserScale);
                    int bufferH = (int) (videoHeight * svUserScale);
                    if (bufferW < 1) bufferW = 1;
                    if (bufferH < 1) bufferH = 1;
                    surfaceHolder.setFixedSize(bufferW, bufferH);
                } catch (Exception e) {
                }
            }
            return;
        }
    }

    private void createVideoView() {
        FrameLayout container = (FrameLayout) findViewById(R.id.video_container);
        if (container == null) return;

        container.removeAllViews();

        if (mRendererType == RENDERER_TEXTUREVIEW && SdkHelper.getSdkInt() >= 14) {
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
                if (SdkHelper.getSdkInt() >= 5) {
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
                        Toast.makeText(BiliPlayerActivity.this, BiliPlayerActivity.this.getString(R.string.biliplayeractivity_toast_65e0), Toast.LENGTH_SHORT).show();
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
            if (isPortraitLayout()) {
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
                    Toast.makeText(BiliPlayerActivity.this, BiliPlayerActivity.this.getString(R.string.biliplayeractivity_toast_5df2),
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
        btnResetScale.setText(getString(R.string.biliplayeractivity_settext_8fd8));
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
            commentEmpty.setText(getString(R.string.biliplayeractivity_settext_563f));
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
                            org.json.JSONArray topReplies = data.optJSONArray("top_replies");
                            boolean isBegin = cursor != null && cursor.optBoolean("is_begin", false);
                            if (topReplies != null && topReplies.length() > 0 && isBegin) {
                                if (replies != null && replies.length() > 0) {
                                    org.json.JSONArray merged = new org.json.JSONArray();
                                    for (int t = 0; t < topReplies.length(); t++) {
                                        merged.put(topReplies.get(t));
                                    }
                                    for (int r = 0; r < replies.length(); r++) {
                                        merged.put(replies.get(r));
                                    }
                                    replies = merged;
                                } else {
                                    replies = topReplies;
                                }
                            }
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
                item.replyCount = reply.optInt("rcount", 0);
                org.json.JSONObject replyCtrl = reply.optJSONObject("reply_control");
                item.isTop = replyCtrl != null && replyCtrl.optBoolean("is_up_top", false);
                if (content != null) {
                    org.json.JSONArray pictures = content.optJSONArray("pictures");
                    if (pictures != null && pictures.length() > 0) {
                        item.pictureList = new java.util.ArrayList<String>();
                        for (int p = 0; p < pictures.length(); p++) {
                            try {
                                org.json.JSONObject pic = pictures.getJSONObject(p);
                                String imgSrc = pic.optString("img_src", "");
                                if (imgSrc != null && imgSrc.length() > 0) {
                                    if (imgSrc.startsWith("https://")) {
                                        imgSrc = "http://" + imgSrc.substring(8);
                                    }
                                    item.pictureList.add(imgSrc);
                                }
                            } catch (Exception e) { }
                        }
                    }
                }

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
                        commentFooterView.setVisibility(View.VISIBLE);
                        if (commentFooterProgress != null) {
                            commentFooterProgress.setVisibility(View.GONE);
                        }
                        TextView ft = (TextView) commentFooterView.findViewById(R.id.footer_text);
                        if (ft != null) {
                            ft.setText(getString(R.string.emoticon__no_more_data));
                        }
                    }
                } else {
                    commentItems.clear();
                    commentItems.addAll(newItems);
                    commentAdapter.updateData(commentItems);

                    if (commentItems.size() == 0) {
                        commentEmpty.setText(getString(R.string.biliplayeractivity_settext_6682));
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
                if (commentFooterProgress != null) {
                    commentFooterProgress.setVisibility(View.GONE);
                }
                commentIsLoadingMore = false;
                commentFooterView.setVisibility(View.VISIBLE);
                TextView ft = (TextView) commentFooterView.findViewById(R.id.footer_text);
                if (ft != null) {
                    if ("没有更多评论".equals(msg)) {
                        ft.setText(getString(R.string.emoticon__no_more_data));
                    } else {
                        ft.setText(msg);
                    }
                }
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
        // 转码播放时强制 360P：不允许切到更高画质，避免白白增加转码耗时与流量
        final int effectiveQn = tv.biliclassic.util.ConvertPlayUtil.isConvertEnabled() ? 16 : newQn;
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
                    playerData.qn = effectiveQn;
                    playerData.timeStamp = 0;

                    PlayerApi.getVideo(playerData, false);
                    final String newUrl = playerData.videoUrl;
                    final String newAudioUrl = playerData.audioUrl;
                    final long newDurationMs = playerData.durationMs;
                    // DASH 下实际画质可能与请求不同（目标画质不可用时被降级）
                    final int actualQn = playerData.qn;

                    if (newUrl != null && newUrl.length() > 0) {
                        final String[] newQnStrs = playerData.qnStrList;
                        final int[] newQnVals = playerData.qnValueList;

                        runOnUiThread(new Runnable() {
                            public void run() {
                                videoUrl = newUrl;
                                audioUrl = newAudioUrl;
                                mProxyDurationMs = newDurationMs;
                                mCurrentQn = actualQn;
                                if (newQnStrs != null && newQnVals != null) {
                                    mQualityNames = newQnStrs;
                                    mQualityValues = newQnVals;
                                }
                                if (mQualityManager != null) {
                                    mQualityManager.updateCurrentQuality(actualQn);
                                }
                                if (decoderType == DECODER_SYSTEM) {
                                    releasePlayer();
                                    sPendingSeekPosition = mQualitySwitchSeekPos;
                                    mQualitySwitchSeekPos = 0;
                                    Intent intent = getIntent();
                                    intent.putExtra("video_url", newUrl);
                                    if (newAudioUrl != null && newAudioUrl.length() > 0) {
                                        intent.putExtra("audio_url", newAudioUrl);
                                    }
                                    if (newDurationMs > 0) {
                                        intent.putExtra("duration_ms", newDurationMs);
                                    }
                                    intent.putExtra("current_qn", actualQn);
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
                                Toast.makeText(BiliPlayerActivity.this, BiliPlayerActivity.this.getString(R.string.biliplayeractivity_toast_5207), Toast.LENGTH_SHORT).show();
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
                    final String newAudioUrl = playerData.audioUrl;
                    final long newDurationMs = playerData.durationMs;
                    final int actualQn = playerData.qn;

                    if (newUrl != null && newUrl.length() > 0) {
                        final String[] newQnStrs = playerData.qnStrList;
                        final int[] newQnVals = playerData.qnValueList;

                        runOnUiThread(new Runnable() {
                            public void run() {
                                videoUrl = newUrl;
                                audioUrl = newAudioUrl;
                                mProxyDurationMs = newDurationMs;
                                mCurrentQn = actualQn;
                                videoTitle = newTitle;
                                if (mediaSessionHelper != null) {
                                    mediaSessionHelper.setMetadata(videoTitle, "");
                                }
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
                                    if (newAudioUrl != null && newAudioUrl.length() > 0) {
                                        intent.putExtra("audio_url", newAudioUrl);
                                    }
                                    if (newDurationMs > 0) {
                                        intent.putExtra("duration_ms", newDurationMs);
                                    }
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
                                Toast.makeText(BiliPlayerActivity.this, BiliPlayerActivity.this.getString(R.string.biliplayeractivity_toast_6362), Toast.LENGTH_SHORT).show();
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
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.biliplayeractivity_settitle_9009))
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

        if (mRendererType == RENDERER_TEXTUREVIEW && SdkHelper.getSdkInt() >= 14) {
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
                if (SdkHelper.getSdkInt() >= 5) {
                    sv.setZOrderMediaOverlay(true);
                }
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            }
            surfaceHolder.addCallback(this);
        }

        videoView.setKeepScreenOn(true);
        container.addView(videoView, 0);
    }

    private boolean tryLoadIjkLibrary() {
        try {
            System.loadLibrary("ijkffmpeg");
            System.loadLibrary("ijksdl");
            return true;
        } catch (Throwable t) {
            return false;
        }
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
                    resolveAndPrepare();
                } else {
                    pendingPrepare = true;
                }
            }
        }, 300);
    }

    private void initPlayer() {
        showBuffering(true);

        if (surfaceReady) {
            resolveAndPrepare();
        } else {
            pendingPrepare = true;
        }
    }

    /**
     * 取地址 + 加载序列：需要转码时先在小电视动画里显示「获取播放地址……」，
     * 后台取到 240P 地址后标【完成】并显示「正在加载视频……」，再走 preparePlayer。
     */
    private void resolveAndPrepare() {
        if (mediaPlayer != null) {
            preparePlayer();
            return;
        }
        if (videoUrl == null || videoUrl.length() == 0) {
            preparePlayer();
            return;
        }
        if (!mUrlResolved && videoUrl.startsWith("http")
                && tv.biliclassic.util.ConvertPlayUtil.isConvertEnabled()) {
            mUrlResolved = true;
            showBuffering(true);
            // 转码期间只显示「获取播放地址……」，成功拿到地址后再追加第二行「正在加载视频……」
            setLoadingStep1(getString(R.string.player_loading_step_get_url));
            setLoadingStep2(null);
            final String rawUrl = videoUrl;
            new Thread(new Runnable() {
                public void run() {
                    final String resolved = tv.biliclassic.util.ConvertPlayUtil.fetchTranscodedUrl(rawUrl, null);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (resolved != null && resolved.length() > 0) {
                                videoUrl = resolved;
                            }
                            setLoadingStep1(getString(R.string.player_loading_step_get_url)
                                    + getString(R.string.player_loading_step_done));
                            setLoadingStep2(getString(R.string.player_loading_step_loading_video));
                            preparePlayer();
                        }
                    });
                }
            }).start();
            return;
        }
        if (videoUrl.startsWith("http")) {
            showBuffering(true);
            setLoadingStep1(getString(R.string.player_loading_step_get_url)
                    + getString(R.string.player_loading_step_done));
            setLoadingStep2(getString(R.string.player_loading_step_loading_video));
        }
        preparePlayer();
    }

    private void setLoadingStep1(String text) {
        mLoadStep1Text = text;
        renderLoadingStatus();
    }

    private void setLoadingStep2(String text) {
        mLoadStep2Text = text;
        renderLoadingStatus();
    }

    private void markLoadingStep2Done() {
        mLoadStep2Text = getString(R.string.player_loading_step_loading_video)
                + getString(R.string.player_loading_step_done);
        renderLoadingStatus();
    }

    /** 加载失败：状态栏显示「正在加载视频……【失败】」，小电视动画停下。 */
    private void showLoadingFailed() {
        String step2 = mLoadStep2Text;
        if (step2 == null || step2.length() == 0) {
            step2 = getString(R.string.player_loading_step_loading_video);
        }
        // 若已拼过【完成】则先去掉，避免出现「【完成】【失败】」
        String done = getString(R.string.player_loading_step_done);
        if (step2.endsWith(done)) {
            step2 = step2.substring(0, step2.length() - done.length());
        }
        mLoadStep2Text = step2 + getString(R.string.player_loading_step_fail);
        mLoadStep1Text = null;
        renderLoadingStatus();
        stopLoadingAnimation();
    }

    /** 把两行状态合并写进左下角状态栏：第一行（获取播放地址）在第二行（正在加载视频）上方。 */
    private void renderLoadingStatus() {
        if (mLoadingStep1 == null) return;
        StringBuilder sb = new StringBuilder();
        if (mLoadStep1Text != null && mLoadStep1Text.length() > 0) {
            sb.append(mLoadStep1Text);
        }
        if (mLoadStep2Text != null && mLoadStep2Text.length() > 0) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(mLoadStep2Text);
        }
        mLoadingStep1.setText(sb.toString());
        mLoadingStep1.setVisibility(View.VISIBLE);
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
            if (audioUrl != null && audioUrl.length() > 0) {
                localProxy = new LocalStreamProxy(videoUrl, audioUrl, mProxyDurationMs, proxyHeaders);
            } else {
                localProxy = new LocalStreamProxy(videoUrl, proxyHeaders);
            }
            try {
                actualUrl = localProxy.start();
            } catch (IOException e) {
                actualUrl = videoUrl;
            }
        }

        if (decoderType != DECODER_SYSTEM) {
            // IJK 需要加载 native 库；部分 ROM 上 libijkffmpeg.so 加载失败（如缺少 libOpenSLES 符号）
            boolean ijkLoaded = tryLoadIjkLibrary();
            if (!ijkLoaded && decoderType == DECODER_IJK_HARD) {
                // 硬解失败，无感切换软解
                decoderType = DECODER_IJK_SOFT;
                ijkLoaded = tryLoadIjkLibrary();
                if (ijkLoaded) {
                    Toast.makeText(this, "硬件解码失败，已切换软件解码", Toast.LENGTH_LONG).show();
                }
            }
            if (!ijkLoaded) {
                android.util.Log.w("BiliPlayer", "IJK native 库加载失败，回退到系统播放器");
                decoderType = DECODER_SYSTEM;
                Toast.makeText(this, "内置播放器加载失败，使用系统播放器", Toast.LENGTH_LONG).show();
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
                                if (SdkHelper.getSdkInt() >= 24) {
                                    Uri localUri = FileProviderCompat.getUriForFile(BiliPlayerActivity.this, new File(localPath));
                                    androidPlayer.setDataSource(this, localUri);
                                } else {
                                    androidPlayer.setDataSource(localPath);
                                }
                            } catch (Exception e2) {
                                androidPlayer.setDataSource(localPath);
                            }
                        }
                    } else {
                        Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_65e0_1), Toast.LENGTH_SHORT).show();
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
                showLoadingFailed();
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
        int framedrop = DecoderSettingsActivity.getFramedrop();
        // DASH 音视频分离流：seek 后视频从关键帧（最多落后一个 GOP）追赶音频，
        // 不丢帧的话慢设备上永远追不上，表现为持续音画不同步
        if (audioUrl != null && audioUrl.length() > 0 && framedrop < 1) {
            framedrop = 1;
        }
        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", (long) framedrop);
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
                String cookie = CookieGenerator.getCookieString(true);
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
                    Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_65e0_1), Toast.LENGTH_SHORT).show();
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
            showLoadingFailed();
            finish();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mRendererType == RENDERER_TEXTUREVIEW) return;
        surfaceReady = true;
        if (decoderType == DECODER_SYSTEM && SdkHelper.getSdkInt() < 14) {
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        if (pendingPrepare) {
            pendingPrepare = false;
            resolveAndPrepare();
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
        if (mediaPlayer != null && !(decoderType == DECODER_SYSTEM && SdkHelper.getSdkInt() < 14)) {
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
        markLoadingStep2Done();
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
            // 防御：断点位置超出视频总长（如接口返回异常值）时，从 0 开始，避免被夹到结尾
            if (mSeekWhenPrepared <= mDuration) {
                mp.seekTo(mSeekWhenPrepared);
                if (mDanmakuManager != null) mDanmakuManager.seekTo(mSeekWhenPrepared);
            } else {
                mp.seekTo(0);
                if (mDanmakuManager != null) mDanmakuManager.seekTo(0L);
            }
        }
        mp.start();
        isPlaying = true;
        if (mediaSessionHelper != null) {
            mediaSessionHelper.setMetadata(videoTitle, "");
        }
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
            if (isPortraitLayout()) {
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

        int sdkInt = SdkHelper.getSdkInt();

        if (decoderType == DECODER_SYSTEM && mAllowDecoderFallback) {
            if (!isPrepared) {
                mAllowDecoderFallback = false;
                if (sdkInt < 16) {
                    decoderType = DECODER_IJK_SOFT;
                    Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_7cfb), Toast.LENGTH_LONG).show();
                } else {
                    decoderType = DECODER_IJK_HARD;
                    Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_7cfb), Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_7cfb), Toast.LENGTH_LONG).show();
            } else {
                decoderType = DECODER_IJK_HARD;
                Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_7cfb), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_786c), Toast.LENGTH_LONG).show();
            cleanupAndRestart();
            return true;
        }

        if (decoderType == DECODER_IJK_SOFT || !mAllowDecoderFallback) {
            if (!mErrorToastShown) {
                mErrorToastShown = true;
                Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_64ad), Toast.LENGTH_LONG).show();
            }
            // 加载失败：显示「正在加载视频……【失败】」并停下小电视
            showLoadingFailed();
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
                    resolveAndPrepare();
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
                !aspectRatioFixed, btnAspectRatio, mGestureController, enableGesture);

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
                        Toast.makeText(BiliPlayerActivity.this, BiliPlayerActivity.this.getString(R.string.biliplayeractivity_toast_7528), Toast.LENGTH_SHORT).show();
                        showControlsWithAutoHide();
                    }
                });
            }
            if (optionsMenuItemOrientation != null) {
                optionsMenuItemOrientation.setVisibility(autoRotation ? View.GONE : View.VISIBLE);
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
        final View panel = inflater.inflate(R.layout.bili_app_player_options_pannel, null);

        // 用 FrameLayout 包裹并右对齐，满宽 PopupWindow 时安全区挤压左侧空白区域
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(this);
        wrapper.setBackgroundDrawable(null);
        wrapper.addView(panel, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.Gravity.RIGHT));

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
        final CheckBox autoRotationCb = (CheckBox) panel.findViewById(
                R.id.player_options_auto_rotation);
        final CheckBox portraitRotationCb = (CheckBox) panel.findViewById(
                R.id.player_options_portrait_rotation);
        View screenOrientation = panel.findViewById(R.id.player_options_screen_orientation);

        if (portraitRotationCb != null) {
            portraitRotationCb.setVisibility(autoRotation ? View.VISIBLE : View.GONE);
            portraitRotationCb.setChecked(portraitRotation);
        }
        if (screenOrientation != null) {
            screenOrientation.setVisibility(autoRotation ? View.GONE : View.VISIBLE);
        }

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
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.ENABLE_GESTURE, isChecked);
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

        if (autoRotationCb != null) {
            autoRotationCb.setChecked(autoRotation);
            autoRotationCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    autoRotation = isChecked;
                    SharedPreferencesUtil.putBoolean(
                            SharedPreferencesUtil.PLAYER_AUTO_ROTATION, isChecked);
                    applyAutoRotation();
                    if (optionsMenuItemOrientation != null) {
                        optionsMenuItemOrientation.setVisibility(
                                isChecked ? View.GONE : View.VISIBLE);
                    }
                    if (portraitRotationCb != null) {
                        portraitRotationCb.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                    updateTopBarForOrientation();
                    View orientationItem = panel.findViewById(
                            R.id.player_options_screen_orientation);
                    if (orientationItem != null) {
                        orientationItem.setVisibility(isChecked ? View.GONE : View.VISIBLE);
                    }
                }
            });
        }

        if (portraitRotationCb != null) {
            portraitRotationCb.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            portraitRotation = isChecked;
                            SharedPreferencesUtil.putBoolean(
                                    SharedPreferencesUtil.PLAYER_PORTRAIT_ROTATION, isChecked);
                            applyAutoRotation();
                            updateTopBarForOrientation();
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

        mPlayerOptionsPannel = new PopupWindow(wrapper,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        mPlayerOptionsPannel.setAnimationStyle(R.style.Animation_SidePannel);
        mPlayerOptionsPannel.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                android.graphics.Color.TRANSPARENT));
        mPlayerOptionsPannel.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() {
                mPlayerOptionsPannel = null;
            }
        });

        View root = getWindow().getDecorView();
        mPlayerOptionsPannel.showAtLocation(root, Gravity.RIGHT, 0, 0);
        if (SdkHelper.getSdkInt() >= 28) {
            tv.biliclassic.util.SdkHelper.onViewAttached(wrapper, new Runnable() {
                public void run() {
                    applyPopupCutout(mPlayerOptionsPannel);
                }
            });
        }
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

    // 为 PopupWindow 的独立窗口设置挖孔模式
    public static void applyPopupCutout(PopupWindow popup) {
        if (SdkHelper.getSdkInt() < 28 || popup == null || !popup.isShowing()) return;
        try {
            // 直接修改 PopupWindow 内部的 mWindowLayoutParams（原始 LayoutParams，非副本）
            java.lang.reflect.Field lpField = PopupWindow.class.getDeclaredField("mWindowLayoutParams");
            lpField.setAccessible(true);
            android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) lpField.get(popup);
            if (wlp == null) return;
            java.lang.reflect.Field cutoutField = android.view.WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode");
            if (cutoutField.getInt(wlp) == 1) return;
            cutoutField.setInt(wlp, 1);
            // 用修改后的原始 LayoutParams 更新窗口
            java.lang.reflect.Field decorField = PopupWindow.class.getDeclaredField("mDecorView");
            decorField.setAccessible(true);
            android.view.View decor = (android.view.View) decorField.get(popup);
            if (decor != null && decor.isAttachedToWindow()) {
                android.view.WindowManager wm = (android.view.WindowManager) decor.getContext()
                        .getSystemService(android.content.Context.WINDOW_SERVICE);
                wm.updateViewLayout(decor, wlp);
            }
        } catch (Exception e) {
            android.util.Log.e("BiliPlayer", "applyPopupCutout failed", e);
        }
    }

    private void toggleScreenOrientation() {
        int current = getRequestedOrientation();
        if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void applyAutoRotation() {
        if (autoRotation) {
            if (isPrepared && VideoAspectRatioHelper.isPortraitVideo(videoWidth, videoHeight)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if (portraitRotation) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            } else if (SdkHelper.getSdkInt() >= Build.VERSION_CODES.GINGERBREAD) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } else {
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
    }

    private void showMediaInfoDialog() {
        String decoder;
        if (decoderType == DECODER_SYSTEM) {
            decoder = "系统硬解";
        } else if (decoderType == DECODER_IJK_HARD) {
            decoder = "IJK 硬解";
        } else {
            decoder = "软件解码器";
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
        msg.append("弹幕引擎: ")
                .append(DanmakuManager.isSimpleEngineEnabled() ? "BT-5" : "烈焰弹幕使")
                .append("\n");
        if (duration.length() > 0) {
            msg.append("时长: ").append(duration);
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.biliplayeractivity_settitle_89c6))
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

    private void hideSystemUI() {
        if (SdkHelper.getSdkInt() >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE);
        }
    }

    private void updatePlayPauseButton() {
        if (btnPlayPause != null) {
            btnPlayPause.setImageLevel(isPlaying ? 1 : 0);
        }
        if (mediaSessionHelper != null) {
            mediaSessionHelper.setPlaying(isPlaying);
        }
    }

    private void updateProgress() {
        if (mediaPlayer != null && isPrepared && isPlaying && !isLiveStream) {
            updateTimeDisplay();
            handler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, PROGRESS_UPDATE_INTERVAL);
        }
    }

    private void reportHistory(final int progress) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false)) return;
        if (mAid == 0 || mCid == 0) return;
        if (mLastReportProgress == progress) return;
        mLastReportProgress = progress;
        reportHistoryStatic(this, mAid, mCid, progress);
    }

    public static void reportHistoryStatic(final Context context, final long aid, final long cid, final int progressMs) {
        tv.biliclassic.api.HistoryApi.report(aid, cid, progressMs);
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

            if (mediaSessionHelper != null) {
                mediaSessionHelper.updatePlaybackPosition(current, duration);
            }

            int progressMs = (int) current;
            if (progressMs >= 0 && progressMs % 5000 < 250) {
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
        hideSystemUI();
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

    private Bitmap loadCoverBitmap(String urlStr) {
        if (urlStr == null || urlStr.length() == 0) return null;
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            NetWorkUtil.applySSLCompat(conn, urlStr);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
            conn.connect();
            is = conn.getInputStream();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) { try { is.close(); } catch (Exception ignored) {} }
            if (conn != null) conn.disconnect();
        }
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
        // 未准备完成（加载动画/缓冲中）禁用手势，避免小电视动画期间左右滑动进退。
        if (mGestureController != null && isPrepared && ev.getPointerCount() == 1 && touchStartX == 0) {
            // 如果用户启用手势，恢复因边缘滑动暂时禁用的状态
            if (enableGesture && !mGestureController.isGestureEnabled()) {
                mGestureController.setEnableGesture(true);
            }
            mGestureController.onTouchEvent(ev);
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_LIANGWAN_PROGRESS) {
            // 凉腕返回播放进度：读取 progress（毫秒）并上报 B 站历史记录
            int progressMs = 0;
            if (data != null) {
                progressMs = data.getIntExtra("progress", 0);
            }
            if (progressMs < 0) progressMs = 0;
            if (mAid > 0 && mCid > 0) {
                reportHistory(progressMs);
            }
            finish();
        }
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
            int maxMemoryMB = (int)(Runtime.getRuntime().maxMemory() / (1024 * 1024));
            if (maxMemoryMB >= 48) {
                if (mDanmakuManager != null) {
                    mDanmakuManager.pause();
                    mDanmakuManager.release();
                    mDanmakuManager = null;
                }
                mSeekWhenPrepared = sPendingSeekPosition;
                sPendingSeekPosition = 0;
                initPlayer();
                if (mDanmakuContainer != null) {
                    mDanmakuManager = new DanmakuManager(BiliPlayerActivity.this,
                            mDanmakuContainer, mAid, mCid, danmakuInputStub);
                    mDanmakuManager.init();
                }
            } else {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
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
                Toast.makeText(this, this.getString(R.string.biliplayeractivity_toast_518d), Toast.LENGTH_SHORT).show();
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

        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!isPrepared) return true;
            // 控制栏有聚焦按钮时，确认键触发该按钮（高亮项点击有效），否则切换播放/暂停
            View focused = getCurrentFocus();
            if (focused != null && focused.isFocusable()
                    && focused.isShown() && focused.hasOnClickListeners()) {
                focused.performClick();
                return true;
            }
            if (mOkHandler.hasMessages(0)) {
                mOkHandler.removeMessages(0);
                togglePlayPause();
            } else {
                mOkHandler.postDelayed(mOkSingleClick, OK_DOUBLE_CLICK_INTERVAL);
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLoadingAnimation();
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
        if (mediaSessionHelper != null) {
            mediaSessionHelper.release();
            mediaSessionHelper = null;
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
        String cookie = CookieGenerator.getCookieString(true);
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
            // 先 stop 再 reset/release：崩溃/出错后原生解码线程可能还在向 OpenSLES/AudioTrack
            // 喂音频，只调 reset/release（且抛异常被吞掉时）音频会一直放不出去。
            try {
                mediaPlayer.pause();
            } catch (Exception e) {}
            try {
                mediaPlayer.stop();
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
        boolean portrait = isPortraitLayout();
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

    private boolean isPortraitLayout() {
        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            return true;
        }
        return autoRotation && portraitRotation
                && getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 旋转后延迟重新应用挖孔模式（等系统完成旋转再设置）
        if (SdkHelper.getSdkInt() >= 28) {
            new android.os.Handler().post(new Runnable() {
                public void run() {
                    try {
                        android.view.WindowManager.LayoutParams attrs = getWindow().getAttributes();
                        java.lang.reflect.Field f = android.view.WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode");
                        f.setInt(attrs, 1);
                        getWindow().setAttributes(attrs);
                    } catch (Exception e) {
                    }
                }
            });
        }
        if (isPrepared) {
            if (mGestureController != null) {
                mGestureController.onOrientationChanged();
            }
            if (mGestureController != null) {
                updateResetScaleButtonVisibility(mGestureController.getCurrentScale());
            }
            if (btnAspectRatio != null) {
                if (isPortraitLayout()) {
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
