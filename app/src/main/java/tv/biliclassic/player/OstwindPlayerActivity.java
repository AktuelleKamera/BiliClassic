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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏隐藏状态栏 + 播放时保持屏幕常亮（API 1）
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_ostwind_player);

        mSurfaceView = (SurfaceView) findViewById(R.id.ostwind_surface);
        mHolder = mSurfaceView.getHolder();
        // msm7x30 视频播放必须用 push 模式表面（硬件 overlay 路径），默认 NORMAL 会在
        // 解码器端口重建时触发 QComHardwareOverlayRenderer 的 UAF 崩溃（系统播放器即 push 模式）
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
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
                if (mPlayer != null) {
                    try {
                        mPlayer.seekTo(seekBar.getProgress());
                        if (mDanmaku != null) {
                            mDanmaku.seekTo(seekBar.getProgress());
                            mLastDanmakuSeek = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "seekTo failed", e);
                    }
                }
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

        // 弹幕：有 cid 就加载（异步下载 XML，播放前准备好即同步）
        if (mCid > 0) {
            FrameLayout danmakuContainer = (FrameLayout) findViewById(R.id.danmaku_container);
            if (danmakuContainer != null) {
                mDanmaku = new DanmakuManager(this, danmakuContainer, mAid, mCid, null);
                mDanmaku.init();
            }
        }
        if (mDanmaku == null) {
            if (mToggleDanmaku != null) mToggleDanmaku.setVisibility(View.GONE);
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
        android.util.Log.d(TAG, "surfaceCreated");
        mSurfaceReady = true;
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
        android.util.Log.d(TAG, "surfaceChanged w=" + width + " h=" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        android.util.Log.d(TAG, "surfaceDestroyed");
        mSurfaceReady = false;
        if (mPlayer != null) {
            mPlayer.setDisplay(null);
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
        mPreparing = true;
        mErrorHandled = false;
        if (!mIsLocalFile) {
            showStatus(getString(R.string.ostwind_loading));
        }
        android.util.Log.d(TAG, "preparePlayer url=" + mVideoUrl
                + " hasCookie=" + (mCookie != null && mCookie.length() > 0)
                + " agentLen=" + (mAgent != null ? mAgent.length() : 0)
                + " sdk=" + android.os.Build.VERSION.SDK_INT);
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

    private void togglePlayPause() {
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

    // 按所选比例调整 SurfaceView 尺寸（容器内适配，居中）
    private void applyAspectRatio() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mSurfaceView.getLayoutParams();
        if (lp == null) return;

        View container = findViewById(R.id.ostwind_root);
        int cw = container != null ? container.getWidth() : 0;
        int ch = container != null ? container.getHeight() : 0;
        if (cw <= 0 || ch <= 0) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            cw = dm.widthPixels;
            ch = dm.heightPixels;
        }
        float containerRatio = (float) cw / ch;

        int vw = 0, vh = 0;
        if (mPlayer != null) {
            try {
                vw = mPlayer.getVideoWidth();
                vh = mPlayer.getVideoHeight();
            } catch (Exception e) {
            }
        }
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

        lp.width = tw;
        lp.height = th;
        lp.gravity = android.view.Gravity.CENTER;
        lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
        mSurfaceView.setLayoutParams(lp);
        mSurfaceView.requestLayout();
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
            if (mPlayer != null) {
                try {
                    if (mPlayer.isPlaying() && !mIsSeeking) {
                        int pos = mPlayer.getCurrentPosition();
                        int dur = mPlayer.getDuration();
                        if (dur > 0) mSeekBar.setMax(dur);
                        mSeekBar.setProgress(pos);
                        mTimeCurrent.setText(formatTime(pos));
                        if (dur > 0) mTimeTotal.setText(formatTime(dur));
                        if (pos > 0 && pos % 5000 < 250) {
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

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        int action = event.getAction();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            mTouchDownX = event.getX();
            mTouchDownY = event.getY();
            mGestureSeeking = false;
            mTouchDownPos = 0;
            if (mPlayer != null && mPrepared) {
                try {
                    mTouchDownPos = mPlayer.getCurrentPosition();
                } catch (Exception e) {
                    mTouchDownPos = 0;
                }
            }
            return true;
        } else if (action == android.view.MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - mTouchDownX;
            float dy = event.getY() - mTouchDownY;
            if (!mGestureSeeking && mPlayer != null && mPrepared) {
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
                long dur = 0;
                try {
                    dur = mPlayer.getDuration();
                } catch (Exception e) {
                }
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
                if (mPlayer != null) {
                    try {
                        mPlayer.seekTo(mGestureTargetPos);
                        if (mDanmaku != null) {
                            mDanmaku.seekTo(mGestureTargetPos);
                            mLastDanmakuSeek = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "gesture seekTo failed", e);
                    }
                }
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
        if (mPlayer != null && mPlayer.isPlaying()) {
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
        if (mPlayer != null && mPrepared && mSurfaceReady) {
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
