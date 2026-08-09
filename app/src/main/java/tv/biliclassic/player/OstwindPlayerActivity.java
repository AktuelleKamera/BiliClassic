package tv.biliclassic.player;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.api.HistoryApi;
import tv.biliclassic.player.danmaku.DanmakuManager;
import tv.biliclassic.util.NetWorkUtil;
import util.LocalStreamProxy;

/**
 * Ostwind 播放器（兼容 Android 2.2 及以下）。
 *
 * 用系统 MediaPlayer + setDataSource(Context, Uri, headers) 自定义请求头
 * （User-Agent / Referer / Cookie），绕过 B 站防盗链在线播放。
 * 全部使用 API 1 方法，无需 ffmpeg / IJK。
 *
 * 接收 Intent extra：
 *  - video_url : 视频直链（必填）
 *  - cookie    : B 站登录 Cookie（可选，缺省则不带）
 *  - agent     : User-Agent（可选，缺省用网页版 UA）
 */
public class OstwindPlayerActivity extends Activity
        implements SurfaceHolder.Callback,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    private static final String TAG = "Ostwind";

    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private MediaPlayer mPlayer;

    // B站播放器风格底部控制栏
    private View mBottomBar;
    private SeekBar mSeekBar;
    private TextView mTimeCurrent;
    private TextView mTimeTotal;
    private ImageButton mPlayPause;
    private boolean mIsSeeking = false;
    private static final long CONTROLLER_HIDE_DELAY = 4000;

    // 顶部控制栏（返回 + 标题）
    private View mTopBar;
    private TextView mTitle;

    // 弹幕开关/设置
    private Button mToggleDanmaku;
    private Button mSendDanmaku;
    private ImageButton mDanmakuOptions;

    // 画面比例
    private Button mAspectRatio;
    private int mCurrentAspectRatio;
    private static final int AR_ADJUST_CONTENT = 0;
    private static final int AR_ADJUST_SCREEN = 1;
    private static final int AR_4_3 = 2;
    private static final int AR_16_9 = 3;
    private static final int AR_9_16 = 4;
    private static final int ASPECT_RATIO_COUNT = 5;

    // 小电视加载动画
    private View mLoadingOverlay;
    private ImageView mLoadingIcon;
    private Handler mAnimHandler = new Handler();
    private Handler mUiHandler = new Handler();
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

    private String mVideoUrl;
    private String mCookie;
    private String mAgent;

    // 弹幕
    private DanmakuManager mDanmaku;
    private long mAid;
    private long mCid;

    // 弹幕输入面板用：暂停/恢复播放器
    private final DanmakuManager.PlayControl mPlayControl = new DanmakuManager.PlayControl() {
        public boolean isPlaying() {
            if (mUseSoftDecode) {
                return mSoftPlaying;
            }
            if (mPlayer != null) {
                try {
                    return mPlayer.isPlaying();
                } catch (Throwable t) {
                    return false;
                }
            }
            return false;
        }

        public boolean isPrepared() {
            return mPrepared;
        }

        public void pausePlayer() {
            if (mUseSoftDecode) {
                if (mSoftPlayer != null && mSoftPlaying) {
                    try {
                        mSoftPlayer.nativePause();
                        mSoftPlaying = false;
                    } catch (Throwable t) {
                    }
                }
            } else if (mPlayer != null) {
                try {
                    mPlayer.pause();
                } catch (Throwable t) {
                }
            }
            if (mDanmaku != null) mDanmaku.pause();
            updatePlayPauseIcon();
        }

        public void resumePlayer() {
            if (mUseSoftDecode) {
                if (mSoftPlayer != null && mPrepared && !mSoftPlaying) {
                    try {
                        mSoftPlayer.nativePlay();
                        mSoftPlaying = true;
                    } catch (Throwable t) {
                    }
                }
            } else if (mPlayer != null && mPrepared) {
                try {
                    mPlayer.start();
                } catch (Throwable t) {
                }
            }
            if (mDanmaku != null) mDanmaku.resume();
            updatePlayPauseIcon();
        }
    };

    // 播放历史上报（每 5s 一次，去重）
    private int mLastReportProgress = -1;

    // seek 手势（水平滑动快进快退）
    private float mTouchDownX;
    private float mTouchDownY;
    private int mTouchDownPos;
    private boolean mGestureSeeking;
    private int mGestureTargetPos;
    private static final int GESTURE_SEEK_RANGE_MS = 600000; // 全屏滑动 = 10 分钟

    // 弹幕时钟纠偏
    private long mLastDanmakuSeek;
    private long mLastDanmakuCorrection;

    // 双击暂停 / 按返回两次退出
    private long mLastTapTime;
    private float mLastTapX;
    private float mLastTapY;
    private long mLastBackTime;
    private static final long DOUBLE_TAP_TIME = 300;
    private static final int DOUBLE_TAP_SLOP = 50;
    private static final long BACK_EXIT_TIME = 2000;

    private LocalStreamProxy mLocalProxy;
    private boolean mPrepared = false;
    private boolean mPreparing = false;
    private boolean mFailed = false;
    private boolean mSurfaceReady = false;
    private boolean mErrorHandled = false;
    private boolean mIsLocalFile = false;

    // ---- 软解内核（MoboPlayer cmplayer + ffmpeg，MediaPlayer 失败时自动降级） ----
    // 需要与 SettingsActivity.DECODER_IJK_SOFT (=2) 保持一致
    private static final int DECODER_IJK_SOFT = 2;
    private com.clov4r.android.nil.CMPlayer mSoftPlayer;
    private boolean mUseSoftDecode = false;
    private boolean mSoftFallbackTried = false;
    private boolean mSoftPlaying = false;
    // nativeOpen 成功后才为 true；失败时 native 内部状态未正确建立，
    // 后续调 nativeClose/nativePause 会访问 null 状态崩溃（cmplayer 0x58f8）。
    private boolean mSoftOpenOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏隐藏状态栏 + 播放时保持屏幕常亮（API 1）
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_ostwind_player);

        mSurfaceView = (SurfaceView) findViewById(R.id.ostwind_surface);
        mHolder = mSurfaceView.getHolder();
        // 表面类型按解码模式动态决定：
        //  - 硬解(msm7x30 overlay)：必须 push 模式，避免 QComHardwareOverlayRenderer UAF 崩溃
        //  - 软解(ffmpeg 直写)：必须 NORMAL，native 端用 Surface.lock/unlockAndPost 画帧，
        //    PUSH_BUFFERS 下 lock 会失败（requestBuffer null handle / w:0,h:0）
        mUseSoftDecode = SettingsActivity.getDecoderType() == DECODER_IJK_SOFT;
        mHolder.setType(mUseSoftDecode
                ? SurfaceHolder.SURFACE_TYPE_NORMAL
                : SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.addCallback(this);

        mBottomBar = findViewById(R.id.ostwind_bottom_bar);
        mSeekBar = (SeekBar) findViewById(R.id.ostwind_seek);
        mTimeCurrent = (TextView) findViewById(R.id.ostwind_time_current);
        mTimeTotal = (TextView) findViewById(R.id.ostwind_time_total);
        mPlayPause = (ImageButton) findViewById(R.id.ostwind_play_pause);

        mTopBar = findViewById(R.id.ostwind_top_bar);
        mTitle = (TextView) findViewById(R.id.ostwind_title);
        ImageButton btnBack = (ImageButton) findViewById(R.id.ostwind_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finishPlayer();
            }
        });

        mToggleDanmaku = (Button) findViewById(R.id.ostwind_toggle_danmaku);
        mSendDanmaku = (Button) findViewById(R.id.ostwind_send_danmaku);
        mDanmakuOptions = (ImageButton) findViewById(R.id.ostwind_options);
        mAspectRatio = (Button) findViewById(R.id.ostwind_aspect_ratio);
        mAspectRatio.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCurrentAspectRatio = (mCurrentAspectRatio + 1) % ASPECT_RATIO_COUNT;
                if (mAspectRatio.getCompoundDrawables() != null
                        && mAspectRatio.getCompoundDrawables().length > 1
                        && mAspectRatio.getCompoundDrawables()[1] != null) {
                    mAspectRatio.getCompoundDrawables()[1].setLevel(mCurrentAspectRatio);
                }
                applyAspectRatio();
            }
        });

        mPlayPause.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                togglePlayPause();
            }
        });
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mIsSeeking = true;
                    mTimeCurrent.setText(formatTime(progress));
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsSeeking = true;
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                doSeek(seekBar.getProgress());
                mIsSeeking = false;
            }
        });

        // 小电视加载动画
        mLoadingOverlay = findViewById(R.id.ostwind_loading);
        if (mLoadingOverlay != null) {
            mLoadingIcon = (ImageView) mLoadingOverlay.findViewById(R.id.iv_tv_anim);
            View progressGroup = mLoadingOverlay.findViewById(R.id.linearLayout);
            if (progressGroup != null) progressGroup.setVisibility(View.INVISIBLE);
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        Intent intent = getIntent();
        mVideoUrl = intent.getStringExtra("video_url");
        mCookie = intent.getStringExtra("cookie");
        mAgent = intent.getStringExtra("agent");
        mAid = intent.getLongExtra("aid", 0);
        mCid = intent.getLongExtra("cid", 0);
        String title = intent.getStringExtra("video_title");
        if (title != null && title.length() > 0) {
            mTitle.setText(title);
            mTitle.setSelected(true); // 触发标题跑马灯
        }

        if (mVideoUrl == null || mVideoUrl.length() == 0) {
            Toast.makeText(this, getString(R.string.ostwind_error), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (mAgent == null || mAgent.length() == 0) {
            mAgent = NetWorkUtil.USER_AGENT_WEB;
        }
        // 本地文件（非 http/https）直接播，无需代理，也不显示网络加载动画
        mIsLocalFile = !(mVideoUrl.startsWith("http://") || mVideoUrl.startsWith("https://"));

        // 解码方式：设置里选了"软解"则直接软解；否则 MediaPlayer 硬解，失败自动降级软解
        mUseSoftDecode = SettingsActivity.getDecoderType() == DECODER_IJK_SOFT;
        android.util.Log.d(TAG, "decoder=" + SettingsActivity.getDecoderType()
                + " useSoftDecode=" + mUseSoftDecode);

        // 弹幕：有 cid 就加载（异步下载 XML，播放前准备好即同步）
        if (mCid > 0) {
            FrameLayout danmakuContainer = (FrameLayout) findViewById(R.id.danmaku_container);
            ViewStub inputStub = (ViewStub) findViewById(R.id.danmaku_sender_viewstub);
            if (danmakuContainer != null) {
                mDanmaku = new DanmakuManager(this, danmakuContainer, mAid, mCid, inputStub);
                mDanmaku.init();
            }
        }
        if (mDanmaku == null) {
            if (mToggleDanmaku != null) mToggleDanmaku.setVisibility(View.GONE);
            if (mSendDanmaku != null) mSendDanmaku.setVisibility(View.GONE);
            if (mDanmakuOptions != null) mDanmakuOptions.setVisibility(View.GONE);
        } else {
            mToggleDanmaku.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mDanmaku.toggleVisibility();
                    int level = mDanmaku.isEnabled() ? 0 : 1;
                    android.graphics.drawable.Drawable[] cds = mToggleDanmaku.getCompoundDrawables();
                    if (cds != null && cds.length > 1 && cds[1] != null) {
                        cds[1].setLevel(level);
                    }
                }
            });
            if (mSendDanmaku != null) {
                mSendDanmaku.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mDanmaku.showInputPanel(mPlayControl);
                    }
                });
            }
            mDanmakuOptions.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mDanmaku.showOptionsPanel();
                }
            });
        }
    }

    // ===== SurfaceHolder.Callback =====
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        android.util.Log.d(TAG, "surfaceCreated thread=" + Thread.currentThread().getName()
                + " mPrepared=" + mPrepared + " mSoftPlayer=" + (mSoftPlayer != null));
        mSurfaceReady = true;
        if (mUseSoftDecode) {
            // 软解：把新 Surface 交给 native 绑定显示
            if (mPrepared && mSoftPlayer != null) {
                try {
                    com.clov4r.android.nil.NativeSurfaceView.setSurfaceChanged(holder.getSurface(),
                            holder.getSurfaceFrame().width(), holder.getSurfaceFrame().height());
                    android.util.Log.d(TAG, "setSurfaceChanged in surfaceCreated done "
                            + holder.getSurfaceFrame().width() + "x"
                            + holder.getSurfaceFrame().height());
                } catch (Throwable t) {
                    android.util.Log.e(TAG, "setSurfaceChanged in surfaceCreated failed", t);
                }
            } else {
                preparePlayer();
            }
            return;
        }
        if (mPlayer != null) {
            // 表面可能因布局/加载层重建而再次创建：只需重新绑定显示，
            // 绝不能在这里新建播放器，否则 msm7x30 上会出现第二个解码器
            //（OMX-VDEC-720P: Reject Second instance of Decoder）导致 overlay 冲突崩溃。
            if (mPrepared) {
                try {
                    mPlayer.setDisplay(mHolder);
                    // 息屏恢复后仅 setDisplay 有时不刷新画面（老 ROM overlay bug），
                    // seek 到当前进度强制解码器输出一帧（同位置 seek，无感）。
                    try {
                        int pos = mPlayer.getCurrentPosition();
                        if (pos > 0) mPlayer.seekTo(pos);
                    } catch (Exception ignored) {
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "setDisplay in surfaceCreated failed", e);
                }
            }
        } else {
            preparePlayer();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        android.util.Log.d(TAG, "surfaceChanged w=" + width + " h=" + height
                + " thread=" + Thread.currentThread().getName());
        if (mUseSoftDecode && mSoftPlayer != null) {
            try {
                // 与原始 MoboPlayer 一致：传 surface 实际尺寸（setFixedSize 后 = 视频尺寸）
                com.clov4r.android.nil.NativeSurfaceView.setSurfaceChanged(
                        holder.getSurface(), width, height);
                android.util.Log.d(TAG, "setSurfaceChanged in surfaceChanged done");
            } catch (Throwable t) {
                android.util.Log.e(TAG, "setSurfaceChanged failed", t);
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        android.util.Log.d(TAG, "surfaceDestroyed thread=" + Thread.currentThread().getName());
        mSurfaceReady = false;
        // API 1 的 MediaPlayer.setDisplay(null) 在播放器已 release 或 surface 为 null 时
        // 会在 native 层 NPE（AOSP 1.5 MediaPlayer.java:481），这里必须 try-catch 保护
        if (!mUseSoftDecode && mPlayer != null && mPrepared) {
            try {
                mPlayer.setDisplay(null);
            } catch (Throwable t) {
                android.util.Log.w(TAG, "setDisplay(null) in surfaceDestroyed failed", t);
            }
        }
    }

    private void preparePlayer() {
        if (mFailed || mPreparing) {
            android.util.Log.d(TAG, "preparePlayer skip, mFailed=" + mFailed
                    + " mPreparing=" + mPreparing);
            return;
        }
        if (mPlayer != null || !mSurfaceReady) {
            android.util.Log.d(TAG, "preparePlayer skip, mPlayer=" + (mPlayer != null)
                    + " mSurfaceReady=" + mSurfaceReady);
            return;
        }
        if (mUseSoftDecode) {
            // 软解：native 用 Surface.lock/unlockAndPost 画帧，必须 NORMAL 类型
            try {
                mHolder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
            } catch (Throwable t) {
            }
            prepareSoftPlayer();
            return;
        }
        // 硬解(msm7x30 overlay)：push 模式表面，避免解码器端口重建时崩溃
        try {
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        } catch (Throwable t) {
        }
        mPreparing = true;
        mErrorHandled = false;
        if (!mIsLocalFile) {
            showStatus(getString(R.string.ostwind_loading));
        }
        android.util.Log.d(TAG, "preparePlayer url=" + mVideoUrl
                + " hasCookie=" + (mCookie != null && mCookie.length() > 0)
                + " agentLen=" + (mAgent != null ? mAgent.length() : 0)
                + " sdk=" + tv.biliclassic.util.SdkHelper.getSdkInt());
        try {
            mPlayer = new MediaPlayer();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setOnPreparedListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.setOnCompletionListener(this);

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", mAgent);
            headers.put("Referer", "https://www.bilibili.com/");
            if (mCookie != null && mCookie.length() > 0) {
                headers.put("Cookie", mCookie);
            }

            String playUrl = mVideoUrl;
            if (mVideoUrl.startsWith("http://") || mVideoUrl.startsWith("https://")) {
                // B 站 CDN 防盗链需要 Referer/Cookie 等请求头，MediaPlayer 在线播放无法传 headers
                //（(Context,Uri,Map) 对 https 抛 No content provider，(String) 无 headers 参数），
                // 所以用本地 HTTP 代理带请求头转发，MediaPlayer 连 127.0.0.1。
                mLocalProxy = new LocalStreamProxy(mVideoUrl, headers);
                try {
                    playUrl = mLocalProxy.start();
                    android.util.Log.d(TAG, "proxy started: " + playUrl);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "proxy start failed, fallback to direct url", e);
                    mLocalProxy = null;
                }
            }

            mPlayer.setDataSource(playUrl);
            mPlayer.setDisplay(mHolder);
            android.util.Log.d(TAG, "setDataSource OK, prepareAsync...");
            mPlayer.prepareAsync();
        } catch (Exception e) {
            android.util.Log.e(TAG, "preparePlayer failed", e);
            mPreparing = false;
            mFailed = true;
            showStatus(getString(R.string.ostwind_error));
            releasePlayer();
        }
    }

    // ===== 软解内核（MoboPlayer cmplayer + ffmpeg，MediaPlayer 失败时自动降级） =====

    /**
     * 软解准备：后台线程 nativeOpen + 轮询取视频尺寸，成功后 UI 线程绑定 Surface 并播放。
     */
    private void prepareSoftPlayer() {
        if (mSoftPlayer != null) {
            return;
        }
        mPreparing = true;
        mErrorHandled = false;
        if (!mIsLocalFile) {
            showStatus(getString(R.string.ostwind_loading));
        }
        if (!com.clov4r.android.nil.library.NativeLibrary.load()) {
            android.util.Log.e(TAG, "soft lib load failed");
            mPreparing = false;
            mFailed = true;
            hideStatus();
            Toast.makeText(this, getString(R.string.ostwind_error), Toast.LENGTH_SHORT).show();
            finishPlayer();
            return;
        }
        // 本地代理复用同一逻辑：软解 native 端 ffmpeg 需要走 http/file 本地路径
        final String playUrl;
        String resolvedUrl = mVideoUrl;
        if (mVideoUrl.startsWith("http://") || mVideoUrl.startsWith("https://")) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", mAgent);
            headers.put("Referer", "https://www.bilibili.com/");
            if (mCookie != null && mCookie.length() > 0) {
                headers.put("Cookie", mCookie);
            }
            mLocalProxy = new LocalStreamProxy(mVideoUrl, headers);
            try {
                resolvedUrl = mLocalProxy.start();
                android.util.Log.d(TAG, "soft proxy started: " + resolvedUrl);
            } catch (Exception e) {
                android.util.Log.e(TAG, "soft proxy start failed, fallback to direct url", e);
                mLocalProxy = null;
            }
        }
        playUrl = resolvedUrl;

        Thread t = new Thread(new Runnable() {
            public void run() {
                android.util.Log.d(TAG, "soft thread start, thread=" + Thread.currentThread().getName());
                mSoftPlayer = new com.clov4r.android.nil.CMPlayer();
                int ret;
                try {
                    android.util.Log.d(TAG, "nativeOpen begin url=" + playUrl
                            + " sdk=" + com.clov4r.android.nil.library.NativeLibrary.sdkVersion());
                    long t0 = System.currentTimeMillis();
                    ret = mSoftPlayer.nativeOpen(playUrl,
                            com.clov4r.android.nil.library.NativeLibrary.sdkVersion(), "");
                    android.util.Log.d(TAG, "nativeOpen done ret=" + ret
                            + " 耗时=" + (System.currentTimeMillis() - t0) + "ms");
                } catch (Throwable e) {
                    android.util.Log.e(TAG, "nativeOpen crashed", e);
                    ret = -1;
                }
                if (ret < 0) {
                    android.util.Log.e(TAG, "nativeOpen ret<0 -> onSoftOpenFailed");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            onSoftOpenFailed();
                        }
                    });
                    return;
                }
                mSoftOpenOk = true;
                android.util.Log.d(TAG, "nativeOpen success, mSoftOpenOk=true");
                int w = 0, h = 0;
                // ARMv6/vfp 慢设备上 nativeOpen 后视频流可能尚未就绪（曾出现 6s+），
                // 轮询放宽到 ~12s 避免尺寸未就绪就绑定错误尺寸导致渲染 lock 失败。
                for (int i = 0; i < 240; i++) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                    try {
                        h = com.clov4r.android.nil.NativeSurfaceView.getVideoHeight();
                        w = com.clov4r.android.nil.NativeSurfaceView.getVideoWidth();
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "getVideoSize failed", e);
                    }
                    if (h > 0 && w > 0) {
                        break;
                    }
                }
                android.util.Log.d(TAG, "poll video size done w=" + w + " h=" + h);
                final int fw = w;
                final int fh = h;
                runOnUiThread(new Runnable() {
                    public void run() {
                        onSoftOpenSuccess(fw, fh);
                    }
                });
            }
        });
        t.start();
    }

    private void onSoftOpenFailed() {
        android.util.Log.e(TAG, "onSoftOpenFailed thread=" + Thread.currentThread().getName());
        mPreparing = false;
        mFailed = true;
        hideStatus();
        releasePlayer();
        Toast.makeText(this, getString(R.string.ostwind_error), Toast.LENGTH_SHORT).show();
        finishPlayer();
    }

    private void onSoftOpenSuccess(int videoW, int videoH) {
        if (mFailed) {
            return;
        }
        mPreparing = false;
        mPrepared = true;
        mSoftPlaying = true;
        hideStatus();
        // 与原始 MoboPlayer 一致：
        //  - setFixedSize = 视频原始尺寸 → native 画满整个 buffer
        //  - setSurfaceChanged = 视频尺寸 → native 渲染目标与 buffer 一致
        //  - View layoutParams 由 applyAspectRatio 设为适配尺寸（如 720x480）
        //    SurfaceView 会把 buffer 拉伸到 View 区域 → 全屏且保持比例
        bindSoftVideoSize(videoW, videoH);
        try {
            long dur = mSoftPlayer.nativeGetDurationTime();
            if (dur > 0) {
                mSeekBar.setMax((int) dur);
                mTimeTotal.setText(formatTime(dur));
            }
        } catch (Throwable t) {
        }
        mPlayPause.getDrawable().setLevel(1); // 播放中 -> 暂停图标
        try {
            android.util.Log.d(TAG, "onSoftOpenSuccess nativePlay begin");
            mSoftPlayer.nativePlay();
            android.util.Log.d(TAG, "onSoftOpenSuccess nativePlay done");
        } catch (Throwable t) {
            android.util.Log.e(TAG, "nativePlay failed", t);
        }
        mUiHandler.post(mTimeRunnable);
        applyAspectRatio();
        if (videoW <= 0 || videoH <= 0) {
            // 视频尺寸尚未就绪（慢设备）：后台持续补取，就绪后重新绑定 surface，
            // 否则 native 渲染线程会用错误的 surface 尺寸 lock buffer 而失败。
            Thread t = new Thread(new Runnable() {
                public void run() {
                    int w = 0, h = 0;
                    for (int i = 0; i < 240; i++) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            break;
                        }
                        try {
                            h = com.clov4r.android.nil.NativeSurfaceView.getVideoHeight();
                            w = com.clov4r.android.nil.NativeSurfaceView.getVideoWidth();
                        } catch (Throwable e) {
                        }
                        if (h > 0 && w > 0) {
                            break;
                        }
                    }
                    if (w > 0 && h > 0 && !mFailed) {
                        final int fw = w;
                        final int fh = h;
                        android.util.Log.d(TAG, "re-bind video size after play w=" + fw + " h=" + fh);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (mFailed) return;
                                bindSoftVideoSize(fw, fh);
                                applyAspectRatio();
                            }
                        });
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }
        if (mDanmaku != null) {
            mDanmaku.setPositionProvider(new DanmakuManager.PositionProvider() {
                public long getCurrentPosition() {
                    if (mSoftPlayer == null) return 0;
                    try {
                        long pos = mSoftPlayer.nativeGetCurrTime();
                        return pos >= 0 ? pos : 0;
                    } catch (Throwable t) {
                        return 0;
                    }
                }
            });
        }
    }

    // 软解：把视频尺寸绑定到 Surface（setFixedSize + native setSurfaceChanged）
    private void bindSoftVideoSize(int videoW, int videoH) {
        if (videoW > 0 && videoH > 0 && mHolder != null) {
            try {
                mHolder.setFixedSize(videoW, videoH);
                if (mSurfaceReady) {
                    com.clov4r.android.nil.NativeSurfaceView.setSurfaceChanged(
                            mHolder.getSurface(), videoW, videoH);
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "setFixedSize/setSurfaceChanged(video size) failed", t);
            }
        } else if (mSurfaceReady && mHolder != null) {
            try {
                com.clov4r.android.nil.NativeSurfaceView.setSurfaceChanged(mHolder.getSurface(),
                        mHolder.getSurfaceFrame().width(), mHolder.getSurfaceFrame().height());
            } catch (Throwable t) {
                android.util.Log.e(TAG, "setSurfaceChanged(frame size) failed", t);
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        android.util.Log.d(TAG, "onPrepared, starting");
        mPrepared = true;
        mPreparing = false;
        // 确保视频画面正确显示
        try {
            mp.setDisplay(mHolder);
        } catch (Exception e) {
            android.util.Log.e(TAG, "setDisplay in onPrepared failed", e);
        }
        try {
            int dur = mp.getDuration();
            if (dur > 0) {
                mSeekBar.setMax(dur);
                mTimeTotal.setText(formatTime(dur));
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "getDuration failed", e);
        }
        mPlayPause.getDrawable().setLevel(1); // 播放中 -> 暂停图标
        mUiHandler.post(mTimeRunnable);
        // 适应视频本身比例
        applyAspectRatio();
        if (mDanmaku != null) {
            mDanmaku.setPositionProvider(new DanmakuManager.PositionProvider() {
                public long getCurrentPosition() {
                    if (mPlayer == null) return 0;
                    try {
                        long pos = mPlayer.getCurrentPosition();
                        return pos >= 0 ? pos : 0;
                    } catch (Exception e) {
                        return 0;
                    }
                }
            });
        }
        hideStatus();
        mp.start();
    }

    // 同步播放/暂停图标状态
    private void updatePlayPauseIcon() {
        if (mPlayPause == null) return;
        boolean playing;
        if (mUseSoftDecode) {
            playing = mSoftPlaying;
        } else {
            try {
                playing = mPlayer != null && mPlayer.isPlaying();
            } catch (Throwable t) {
                playing = false;
            }
        }
        mPlayPause.getDrawable().setLevel(playing ? 1 : 0);
    }

    private void togglePlayPause() {
        if (mUseSoftDecode) {
            if (mSoftPlayer == null || !mPrepared) return;
            try {
                if (mSoftPlaying) {
                    android.util.Log.d(TAG, "togglePlayPause: nativePause");
                    mSoftPlayer.nativePause();
                    mSoftPlaying = false;
                    mPlayPause.getDrawable().setLevel(0); // 已暂停 -> 播放图标
                    if (mDanmaku != null) mDanmaku.pause();
                } else {
                    android.util.Log.d(TAG, "togglePlayPause: nativePlay");
                    mSoftPlayer.nativePlay();
                    mSoftPlaying = true;
                    mPlayPause.getDrawable().setLevel(1); // 播放中 -> 暂停图标
                    if (mDanmaku != null) mDanmaku.resume();
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "soft togglePlayPause failed", t);
            }
            return;
        }
        if (mPlayer == null) return;
        try {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                mPlayPause.getDrawable().setLevel(0); // 已暂停 -> 播放图标
                if (mDanmaku != null) mDanmaku.pause();
            } else {
                mPlayer.start();
                mPlayPause.getDrawable().setLevel(1); // 播放中 -> 暂停图标
                if (mDanmaku != null) mDanmaku.resume();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "togglePlayPause failed", e);
        }
    }

    // 计算目标显示尺寸 {w, h}：容器内按所选比例适配（与 applyAspectRatio 同一套逻辑）
    private int[] computeDisplaySize(int vw, int vh) {
        View container = findViewById(R.id.ostwind_root);
        int cw = container != null ? container.getWidth() : 0;
        int ch = container != null ? container.getHeight() : 0;
        if (cw <= 0 || ch <= 0) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            cw = dm.widthPixels;
            ch = dm.heightPixels;
        }
        float containerRatio = (float) cw / ch;

        float videoRatio = (vw > 0 && vh > 0) ? (float) vw / vh : containerRatio;
        float targetRatio;
        switch (mCurrentAspectRatio) {
            case AR_ADJUST_SCREEN:
                targetRatio = containerRatio;
                break;
            case AR_4_3:
                targetRatio = 4f / 3f;
                break;
            case AR_16_9:
                targetRatio = 16f / 9f;
                break;
            case AR_9_16:
                targetRatio = 9f / 16f;
                break;
            case AR_ADJUST_CONTENT:
            default:
                targetRatio = videoRatio;
                break;
        }

        int tw, th;
        if (targetRatio > containerRatio) {
            tw = cw;
            th = (int) (cw / targetRatio);
        } else {
            th = ch;
            tw = (int) (ch * targetRatio);
        }
        if (tw < 1) tw = 1;
        if (th < 1) th = 1;
        return new int[]{tw, th};
    }

    // 按所选比例调整 SurfaceView 尺寸（容器内适配，居中）
    private void applyAspectRatio() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mSurfaceView.getLayoutParams();
        if (lp == null) return;

        int vw = 0, vh = 0;
        if (mUseSoftDecode) {
            try {
                vw = com.clov4r.android.nil.NativeSurfaceView.getVideoWidth();
                vh = com.clov4r.android.nil.NativeSurfaceView.getVideoHeight();
            } catch (Throwable t) {
            }
        } else if (mPlayer != null) {
            try {
                vw = mPlayer.getVideoWidth();
                vh = mPlayer.getVideoHeight();
            } catch (Exception e) {
            }
        }
        int[] size = computeDisplaySize(vw, vh);
        int tw = size[0], th = size[1];

        lp.width = tw;
        lp.height = th;
        lp.gravity = android.view.Gravity.CENTER;
        lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
        mSurfaceView.setLayoutParams(lp);
        mSurfaceView.requestLayout();
        android.util.Log.d(TAG, "applyAspectRatio video=" + vw + "x" + vh
                + " view=" + tw + "x" + th
                + " surface=" + (mHolder != null ? mHolder.getSurfaceFrame().width() + "x"
                        + mHolder.getSurfaceFrame().height() : "null"));
    }

    private void toggleController() {
        boolean show = mBottomBar.getVisibility() != View.VISIBLE;
        mBottomBar.setVisibility(show ? View.VISIBLE : View.GONE);
        mTopBar.setVisibility(show ? View.VISIBLE : View.GONE);
        mUiHandler.removeCallbacks(mHideControllerRunnable);
        if (show) {
            mUiHandler.postDelayed(mHideControllerRunnable, CONTROLLER_HIDE_DELAY);
        }
    }

    private final Runnable mHideControllerRunnable = new Runnable() {
        public void run() {
            mBottomBar.setVisibility(View.GONE);
            mTopBar.setVisibility(View.GONE);
        }
    };

    // 定时刷新进度与时间
    private final Runnable mTimeRunnable = new Runnable() {
        public void run() {
            if (mUseSoftDecode) {
                tickSoft();
            } else if (mPlayer != null) {
                try {
                    if (mPlayer.isPlaying() && !mIsSeeking) {
                        int pos = mPlayer.getCurrentPosition();
                        int dur = mPlayer.getDuration();
                        if (dur > 0) mSeekBar.setMax(dur);
                        mSeekBar.setProgress(pos);
                        mTimeCurrent.setText(formatTime(pos));
                        if (dur > 0) mTimeTotal.setText(formatTime(dur));
                        if (pos >= 0 && pos % 5000 < 250) {
                            reportHistory(pos);
                        }
                        // 弹幕时钟纠偏：漂移超 2s 才 seekTo 对齐，且每次纠偏至少间隔 5s
                        //（避免 Ace 上视频丢帧/缓冲导致弹幕时钟持续超前时，频繁 seekTo 重置渲染造成闪烁/消失）
                        if (mDanmaku != null
                                && System.currentTimeMillis() - mLastDanmakuSeek > 2000
                                && System.currentTimeMillis() - mLastDanmakuCorrection > 5000) {
                            try {
                                long dTime = mDanmaku.getCurrentTime();
                                if (dTime > 0 && Math.abs(dTime - pos) > 2000) {
                                    mLastDanmakuCorrection = System.currentTimeMillis();
                                    mDanmaku.seekTo(pos);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
            mUiHandler.postDelayed(this, 500);
        }
    };

    // 软解进度刷新（native 无回调，播放完成在此检测）
    private void tickSoft() {
        if (mSoftPlayer == null || !mPrepared) {
            return;
        }
        try {
            if (mSoftPlaying && !mIsSeeking) {
                long pos = mSoftPlayer.nativeGetCurrTime();
                long dur = mSoftPlayer.nativeGetDurationTime();
                if (dur > 0) mSeekBar.setMax((int) dur);
                mSeekBar.setProgress((int) pos);
                mTimeCurrent.setText(formatTime(pos));
                if (dur > 0) mTimeTotal.setText(formatTime(dur));
                if (pos >= 0 && pos % 5000 < 250) {
                    reportHistory((int) pos);
                }
                if (mDanmaku != null
                        && System.currentTimeMillis() - mLastDanmakuSeek > 2000
                        && System.currentTimeMillis() - mLastDanmakuCorrection > 5000) {
                    try {
                        long dTime = mDanmaku.getCurrentTime();
                        if (dTime > 0 && Math.abs(dTime - pos) > 2000) {
                            mLastDanmakuCorrection = System.currentTimeMillis();
                            mDanmaku.seekTo(pos);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (dur > 0 && pos >= dur - 500 && dur > 5000) {
                    finishPlayer();
                }
            }
        } catch (Throwable t) {
        }
    }

    // 上报播放进度到 B 站观看历史（去重，异步）
    private void reportHistory(int progressMs) {
        if (mLastReportProgress == progressMs) return;
        mLastReportProgress = progressMs;
        HistoryApi.report(mAid, mCid, progressMs);
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        android.util.Log.e(TAG, "onError what=" + what + " extra=" + extra);
        if (mErrorHandled) {
            return true;
        }
        mErrorHandled = true;
        mPreparing = false;
        // 自动降级：MediaPlayer 硬解失败 → 切换软解重试一次
        if (!mSoftFallbackTried) {
            android.util.Log.e(TAG, "hard decode failed, falling back to soft decode");
            mSoftFallbackTried = true;
            releaseHardPlayer();
            mUseSoftDecode = true;
            mFailed = false;
            mErrorHandled = false;
            if (mSurfaceReady) {
                preparePlayer();
            } else {
                hideStatus();
            }
            return true;
        }
        mFailed = true;
        // 停止加载动画并释放播放器/代理，避免 MediaPlayer 在 error 状态反复回调导致动画一直转
        hideStatus();
        releasePlayer();
        Toast.makeText(this, getString(R.string.ostwind_error), Toast.LENGTH_SHORT).show();
        finishPlayer();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        finishPlayer();
    }

    // 当前播放位置（软硬解分派）
    private int getEnginePosition() {
        if (mUseSoftDecode) {
            if (mSoftPlayer != null) {
                try {
                    return (int) mSoftPlayer.nativeGetCurrTime();
                } catch (Throwable t) {
                }
            }
            return 0;
        }
        if (mPlayer != null) {
            try {
                return mPlayer.getCurrentPosition();
            } catch (Exception e) {
            }
        }
        return 0;
    }

    // 总时长（软硬解分派）
    private int getEngineDuration() {
        if (mUseSoftDecode) {
            if (mSoftPlayer != null) {
                try {
                    return (int) mSoftPlayer.nativeGetDurationTime();
                } catch (Throwable t) {
                }
            }
            return 0;
        }
        if (mPlayer != null) {
            try {
                return mPlayer.getDuration();
            } catch (Exception e) {
            }
        }
        return 0;
    }

    // 统一的 seek（软硬解分派），并同步弹幕时钟
    private void doSeek(int positionMs) {        if (mUseSoftDecode) {
            if (mSoftPlayer != null) {
                try {
                    mSoftPlayer.nativeSeek(positionMs);
                    if (mDanmaku != null) {
                        mDanmaku.seekTo(positionMs);
                        mLastDanmakuSeek = System.currentTimeMillis();
                    }
                } catch (Throwable t) {
                    android.util.Log.e(TAG, "soft seek failed", t);
                }
            }
            return;
        }
        if (mPlayer != null) {
            try {
                mPlayer.seekTo(positionMs);
                if (mDanmaku != null) {
                    mDanmaku.seekTo(positionMs);
                    mLastDanmakuSeek = System.currentTimeMillis();
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "seekTo failed", e);
            }
        }
    }

    // 仅释放硬解 MediaPlayer/代理，保留软解尝试状态（自动降级用）
    private void releaseHardPlayer() {        mUiHandler.removeCallbacks(mTimeRunnable);
        if (mPlayer != null) {
            try {
                mPlayer.release();
            } catch (Exception e) {
            }
            mPlayer = null;
        }
        if (mLocalProxy != null) {
            try {
                mLocalProxy.stop();
            } catch (Exception e) {
            }
            mLocalProxy = null;
        }
        mPrepared = false;
        if (mDanmaku != null) {
            try {
                mDanmaku.release();
            } catch (Exception e) {
            }
            mDanmaku = null;
            FrameLayout danmakuContainer = (FrameLayout) findViewById(R.id.danmaku_container);
            if (danmakuContainer != null && mCid > 0) {
                mDanmaku = new DanmakuManager(this, danmakuContainer, mAid, mCid, null);
                mDanmaku.init();
            }
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        int action = event.getAction();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            mTouchDownX = event.getX();
            mTouchDownY = event.getY();
            mGestureSeeking = false;
            mTouchDownPos = 0;
            if (mPrepared) {
                mTouchDownPos = getEnginePosition();
            }
            return true;
        } else if (action == android.view.MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - mTouchDownX;
            float dy = event.getY() - mTouchDownY;
            if (!mGestureSeeking && mPrepared) {
                int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    mGestureSeeking = true;
                    mIsSeeking = true;
                    if (mBottomBar.getVisibility() != View.VISIBLE) {
                        mBottomBar.setVisibility(View.VISIBLE);
                        mTopBar.setVisibility(View.VISIBLE);
                    }
                    mUiHandler.removeCallbacks(mHideControllerRunnable);
                }
            }
            if (mGestureSeeking) {
                int w = mSurfaceView.getWidth();
                if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
                long dur = getEngineDuration();
                long target = mTouchDownPos + (long) (dx / w * GESTURE_SEEK_RANGE_MS);
                if (dur > 0) {
                    target = Math.min(Math.max(target, 0), dur);
                }
                mGestureTargetPos = (int) target;
                mSeekBar.setProgress(mGestureTargetPos);
                mTimeCurrent.setText(formatTime(mGestureTargetPos));
            }
            return true;
        } else if (action == android.view.MotionEvent.ACTION_UP) {
            if (mGestureSeeking) {
                doSeek(mGestureTargetPos);
                mGestureSeeking = false;
                mIsSeeking = false;
                mUiHandler.postDelayed(mHideControllerRunnable, CONTROLLER_HIDE_DELAY);
                return true;
            }
            // 双击：切换播放/暂停
            long now = System.currentTimeMillis();
            float tapX = event.getX();
            float tapY = event.getY();
            if (mLastTapTime > 0
                    && now - mLastTapTime <= DOUBLE_TAP_TIME
                    && Math.abs(tapX - mLastTapX) <= DOUBLE_TAP_SLOP
                    && Math.abs(tapY - mLastTapY) <= DOUBLE_TAP_SLOP) {
                mLastTapTime = 0;
                togglePlayPause();
                return true;
            }
            mLastTapTime = now;
            mLastTapX = tapX;
            mLastTapY = tapY;
            if (mLoadingOverlay == null || mLoadingOverlay.getVisibility() != View.VISIBLE) {
                toggleController();
            }
            return true;
        } else if (action == android.view.MotionEvent.ACTION_CANCEL) {
            mGestureSeeking = false;
            mIsSeeking = false;
            mLastTapTime = 0;
            return true;
        }
        return true;
    }

    // 返回键：第一次 toast"再按一次退出"，2s 内再按退出；弹幕设置面板开着则先关面板
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (mDanmaku != null && mDanmaku.isOptionsPanelShowing()) {
                mDanmaku.dismissAllPanels();
                return true;
            }
            long now = System.currentTimeMillis();
            if (mLastBackTime > 0 && now - mLastBackTime <= BACK_EXIT_TIME) {
                finishPlayer();
                return true;
            }
            mLastBackTime = now;
            Toast.makeText(this, getString(R.string.biliplayeractivity_toast_518d), Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void finishPlayer() {
        finish();
    }

    private void showStatus(String msg) {
        if (mLoadingOverlay != null) {
            mLoadingOverlay.setVisibility(View.VISIBLE);
            mAnimHandler.post(mAnimRunnable);
        }
    }

    private void hideStatus() {
        if (mLoadingOverlay != null) {
            mAnimHandler.removeCallbacks(mAnimRunnable);
            mLoadingOverlay.setVisibility(View.GONE);
        }
    }

    private void releasePlayer() {
        mAnimHandler.removeCallbacks(mAnimRunnable);
        mUiHandler.removeCallbacks(mTimeRunnable);
        mUiHandler.removeCallbacks(mHideControllerRunnable);
        mPreparing = false;
        if (mSoftPlayer != null) {
            android.util.Log.d(TAG, "releasePlayer: closing soft player (openOk=" + mSoftOpenOk + ")");
            // nativeOpen 失败时 native 内部状态未建立，调 nativeClose/nativePause
            // 会访问 null 状态崩溃，因此只在打开成功后才释放
            if (mSoftOpenOk) {
                try {
                    mSoftPlayer.nativePause();
                } catch (Throwable t) {
                    android.util.Log.w(TAG, "nativePause failed", t);
                }
                try {
                    mSoftPlayer.nativeClose();
                } catch (Throwable t) {
                    android.util.Log.w(TAG, "nativeClose failed", t);
                }
                // native 已释放音频输出，清掉强引用让 GC 回收 AudioTrack
                try {
                    mSoftPlayer.mAudioTrack = null;
                } catch (Throwable t) {
                }
            }
            mSoftPlayer = null;
            mSoftPlaying = false;
            mSoftOpenOk = false;
        }
        if (mPlayer != null) {
            try {
                mPlayer.release();
            } catch (Exception e) {
            }
            mPlayer = null;
        }
        if (mLocalProxy != null) {
            try {
                mLocalProxy.stop();
            } catch (Exception e) {
            }
            mLocalProxy = null;
        }
        mPrepared = false;
        if (mDanmaku != null) {
            try {
                mDanmaku.release();
            } catch (Exception e) {
            }
            mDanmaku = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mUseSoftDecode) {
            if (mSoftPlayer != null && mSoftPlaying) {
                try {
                    mSoftPlayer.nativePause();
                    mSoftPlaying = false;
                } catch (Throwable t) {
                }
            }
        } else if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
        if (mDanmaku != null) {
            mDanmaku.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 息屏/切后台返回后：确保画面重新绑定（surfaceCreated 可能未触发），
        // 与 surfaceCreated 相同的 seek 强制刷帧，避免黑屏
        if (mUseSoftDecode) {
            // 软解暂停中，等待用户点播放恢复（与硬解行为一致）
        } else if (mPlayer != null && mPrepared && mSurfaceReady) {
            try {
                mPlayer.setDisplay(mHolder);
                try {
                    int pos = mPlayer.getCurrentPosition();
                    if (pos > 0) mPlayer.seekTo(pos);
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "setDisplay in onResume failed", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
