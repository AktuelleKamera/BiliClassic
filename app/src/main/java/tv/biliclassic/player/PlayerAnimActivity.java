package tv.biliclassic.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import tv.biliclassic.ProxyStreamService;
import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.download.DownloadPathUtil;
import tv.biliclassic.download.VideoDownloadEntry;
import tv.biliclassic.download.VideoDownloadEnvironment;
import tv.biliclassic.download.VideoDownloadService;
import tv.biliclassic.util.CookieGenerator;
import tv.biliclassic.util.FileProviderCompat;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SdkHelper;
public class PlayerAnimActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        // 先包存储回退，再套 Locale（播放器不走 BaseActivity）
        android.content.Context wrapped = new tv.biliclassic.util.StorageFallbackContext(newBase);
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 17) {
            super.attachBaseContext(tv.biliclassic.util.LocaleHelper.wrapContext(wrapped));
        } else {
            super.attachBaseContext(wrapped);
        }
    }

    @Override
    public android.content.res.Resources getResources() {
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 17) {
            return super.getResources();
        }
        android.content.res.Resources res = super.getResources();
        android.content.res.Configuration config = res.getConfiguration();
        config.locale = tv.biliclassic.util.LocaleHelper.getLocale();
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
    }

    private ImageView ivTvAnim;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private TextView tvStatus;

    private Handler handler = new Handler();
    private Handler animHandler = new Handler();
    private int animIndex = 0;
    private int[] animDrawables = LoadingFrames.TV_CHAN;

    private String videoUrl;
    private String audioUrl;
    private long mDurationMs;
    private String videoTitle;
    private String coverUrl;
    private long aid;
    private long cid;
    private File cacheFile;
    private String danmakuCachePath;
    private String upName;

    private boolean hasShownPreferenceToast = false;
    private boolean isOnlineMode = false;

    private String[] mQualityNames;
    private int[] mQualityValues;
    private int mCurrentQn;

    // 断点续播：从 B 站拿到的上次播放进度（毫秒），透传给各播放器
    private int resumePosition = 0;

    // 下载控制
    private volatile boolean isDownloadCancelled = false;
    private VideoDownloadEnvironment downloadEnv;
    private int downloadPage = 1;
    private Runnable downloadPollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // sensorLandscape 是 API 9+ 的清单值，Android 2.2 及以下不生效（按 unspecified 处理），
        // 动态降级为固定横屏
        if (SdkHelper.getSdkInt() < 9) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        setContentView(R.layout.activity_player_anim);
        if (SdkHelper.getSdkInt() < 4) {
            SdkHelper.disableSoundEffects(getWindow().getDecorView());
        }
        if (SdkHelper.getSdkInt() >= 11
                && SdkHelper.getSdkInt() < 21) {
            View content = findViewById(android.R.id.content);
            if (content != null) {
                PlayerCompat.setLayerType(content, View.LAYER_TYPE_SOFTWARE);
            }
        }

        ivTvAnim = (ImageView) findViewById(R.id.iv_tv_anim);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        tvProgress = (TextView) findViewById(R.id.tv_progress);
        tvStatus = (TextView) findViewById(R.id.tv_status);

        // 左上角返回按钮：退出加载页
        try {
            View animBack = findViewById(R.id.anim_back);
            if (animBack != null) {
                animBack.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        isDownloadCancelled = true;
                        finish();
                    }
                });
            }
        } catch (Throwable t) {
        }

        videoUrl = getIntent().getStringExtra("video_url");
        audioUrl = getIntent().getStringExtra("audio_url");
        mDurationMs = getIntent().getLongExtra("duration_ms", 0);
        videoTitle = getIntent().getStringExtra("video_title");
        coverUrl = getIntent().getStringExtra("cover_url");
        aid = getIntent().getLongExtra("aid", 0);
        cid = getIntent().getLongExtra("cid", 0);


        // 检测是否在线模式
        isOnlineMode = SettingsActivity.isOnlinePlayEnabled();

        mQualityNames = getIntent().getStringArrayExtra("qn_str_array");
        mQualityValues = getIntent().getIntArrayExtra("qn_value_array");
        mCurrentQn = getIntent().getIntExtra("current_qn", 0);
        resumePosition = getIntent().getIntExtra("resume_position", 0);
        if (resumePosition <= 0 && aid > 0 && cid > 0) {
            resumePosition = tv.biliclassic.util.LocalHistoryStore.getProgress(aid, cid);
        }

        // 打印日志确认 videoUrl
        android.util.Log.e("PlayerAnim", "videoUrl: " + videoUrl);
        android.util.Log.e("PlayerAnim", "videoTitle: " + videoTitle);
        android.util.Log.e("PlayerAnim", "aid: " + aid + ", cid: " + cid);
        android.util.Log.e("PlayerAnim", "isOnlineMode: " + isOnlineMode);

        if (videoUrl == null || videoUrl.length() == 0) {
            Toast.makeText(this, this.getString(R.string.video_url_invalid), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startTvAnimation();

        if (isOnlineMode) {
            tvStatus.setText(getString(R.string.online_play_mode));
            progressBar.setVisibility(ProgressBar.GONE);
            tvProgress.setVisibility(TextView.GONE);
            handler.post(new Runnable() {
                public void run() {
                    stopTvAnimation();
                    int pref = SettingsActivity.getPlayerPreference();
                    if (audioUrl != null && audioUrl.length() > 0) {
                        // DASH 音视频分离流：系统 MediaPlayer/外部播放器无法解析，
                        // 强制走内置 IJK 播放路径
                        playWithBuiltinPlayer(videoUrl);
                        return;
                    }
                    if (pref == 8) {
                        // 内置播放器：BiliPlayerActivity（在线直接播放，自带代理）
                        playWithBuiltinPlayer(videoUrl);
                        return;
                    }
                    // 外部/系统/自动：在线播放走本地代理（带 Referer/Cookie/UA 请求头）再跳外部播放器
                    playOnlineWithExternal(pref);
                }
            });
        } else {
            if (aid <= 0 || cid <= 0) {
                playWithBuiltinPlayer(videoUrl);
                return;
            }
            downloadPage = getIntent().getIntExtra("part_index", 0) + 1;
            if (downloadPage < 1) downloadPage = 1;
            downloadEnv = new VideoDownloadEnvironment(
                    DownloadPathUtil.getDownloadDir(this), aid, downloadPage);
            cacheFile = downloadEnv.getVideoFile();
            File danmakuFile = downloadEnv.getDanmakuFile(false);
            if (danmakuFile != null && danmakuFile.exists()) {
                danmakuCachePath = danmakuFile.getAbsolutePath();
            }
            android.util.Log.e("PlayerAnim", "cache path: " + cacheFile.getAbsolutePath());
            if (cacheFile.exists() && cacheFile.length() > 0) {
                playWithPlayer();
            } else {
                startDownloadFromService();
            }
        }
    }

    private int getDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        try {
            java.lang.reflect.Method method = Display.class.getMethod("getRotation");
            return ((Integer) method.invoke(display)).intValue();
        } catch (Exception e) {
            // Android 2.1 及以下用 getOrientation
            try {
                java.lang.reflect.Method method = Display.class.getMethod("getOrientation");
                return ((Integer) method.invoke(display)).intValue();
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    /**
     * 判断是否竖屏（兼容 Android 2.3+）
     */
    private boolean isPortrait() {
        int rotation = getDisplayRotation();
        return (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180);
    }

    /**
     * 使用内置播放器直接播放视频（在线模式）
     */
    private void playWithBuiltinPlayer(String url) {
        if (url == null || url.length() == 0) {
            Toast.makeText(this, this.getString(R.string.video_file_missing), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        int partIndex = getIntent().getIntExtra("part_index", 0);
        long[] cids = getIntent().hasExtra("cids")
                ? getIntent().getLongArrayExtra("cids") : null;
        String[] pagenames = getIntent().hasExtra("pagenames")
                ? getIntent().getStringArrayExtra("pagenames") : null;
        Intent intent = PlayerIntentFactory.create(this, BiliPlayerActivity.class,
                url, audioUrl, mDurationMs, videoTitle, aid, cid, partIndex,
                resumePosition, cids, pagenames, coverUrl, upName, true);
        if (danmakuCachePath != null && danmakuCachePath.length() > 0) {
            intent.putExtra("danmaku_cache_path", danmakuCachePath);
        }
        if (isPortrait()) intent.putExtra("is_portrait_loading", true);
        putQualityExtras(intent);
        startActivity(intent);
        finish();
    }

    private void playOnlineWithExternal(int pref) {
        if (videoUrl == null || videoUrl.length() == 0) {
            Toast.makeText(this, this.getString(R.string.video_file_missing), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        boolean isNet = videoUrl.startsWith("http://") || videoUrl.startsWith("https://");
        String playUrl = videoUrl;
        if (isNet) {
            String proxied = PlayerNetworkHeaders.startExternalProxy(this, videoUrl);
            if (proxied != null && proxied.length() > 0) {
                playUrl = proxied;
            }
        }
        Intent extIntent = new Intent(Intent.ACTION_VIEW);
        extIntent.setDataAndType(Uri.parse(playUrl), "video/mp4");
        String pkg = SettingsActivity.getPlayerPackageName();
        if (pkg != null) {
            try { Intent.class.getMethod("setPackage", String.class).invoke(extIntent, new Object[]{pkg}); } catch (Exception ignored) {};
        }
        putOnlineExternalExtras(extIntent);
        try {
            startActivity(extIntent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, this.getString(R.string.no_player_found), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private Map<String, String> buildOnlineHeaders() {
        return PlayerNetworkHeaders.build();
    }

    private void putOnlineExternalExtras(Intent extIntent) {
        ExternalPlayerExtras.putForOnline(extIntent, videoTitle, cid, resumePosition);
    }

    private void startTvAnimation() {
        if (ivTvAnim != null) {
            ivTvAnim.setImageResource(animDrawables[0]);
            if (SdkHelper.getSdkInt() >= 11
                    && SdkHelper.getSdkInt() < 21) {
                PlayerCompat.setLayerType(ivTvAnim, View.LAYER_TYPE_SOFTWARE);
            }
        }
        animHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ivTvAnim.setImageResource(animDrawables[animIndex]);
                animIndex = (animIndex + 1) % animDrawables.length;
                animHandler.postDelayed(this, 200);
            }
        }, 200);
    }

    private void stopTvAnimation() {
        animHandler.removeCallbacksAndMessages(null);
    }

    private boolean isSDCardAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void startDownloadFromService() {
        tvStatus.setText(getString(R.string.buffering));
        handler.removeCallbacks(downloadPollRunnable);
        isDownloadCancelled = false;
        String bvid = getIntent().getStringExtra("bvid");
        String description = getIntent().getStringExtra("desc");
        String tags = getIntent().getStringExtra("tags");
        VideoDownloadService.startDownload(this, aid, bvid, videoTitle, videoTitle,
                cid, downloadPage, mCurrentQn,
                VideoDownloadEnvironment.getQualityName(mCurrentQn),
                coverUrl, upName, videoUrl, description, tags);
        pollDownloadProgress();
    }

    private void pollDownloadProgress() {
        if (isDownloadCancelled) return;
        if (downloadPollRunnable != null) handler.removeCallbacks(downloadPollRunnable);
        downloadPollRunnable = new Runnable() {
            public void run() {
                if (isDownloadCancelled || downloadEnv == null) return;
                VideoDownloadEntry entry = downloadEnv.loadEntry();
                if (entry != null && entry.totalBytes > 0) {
                    int percent = entry.getProgressPercentage();
                    if (progressBar != null) progressBar.setProgress(percent);
                    if (tvProgress != null) tvProgress.setText(percent + "%");
                }
                File videoFile = downloadEnv.getVideoFile();
                if (videoFile != null && videoFile.exists() && videoFile.length() > 0) {
                    cacheFile = videoFile;
                    File danmakuFile = downloadEnv.getDanmakuFile(false);
                    if (danmakuFile != null && danmakuFile.exists()) {
                        danmakuCachePath = danmakuFile.getAbsolutePath();
                    }
                    downloadPollRunnable = null;
                    stopTvAnimation();
                    playWithPlayer();
                    return;
                }
                handler.postDelayed(this, 1000L);
            }
        };
        handler.postDelayed(downloadPollRunnable, 1000L);
    }

    private void playWithPlayer() {
        if (!cacheFile.exists() || cacheFile.length() == 0) {
            Toast.makeText(this, this.getString(R.string.video_file_missing), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int preference = SettingsActivity.getPlayerPreference();

        hasShownPreferenceToast = false;

        if (preference == -1) {
            autoSelectPlayer();
            return;
        }

        switch (preference) {
            case 0:
                if (tryPlayWithPackage("com.mxtech.videoplayer.ad", "MX Player")) {
                    return;
                }
                break;
            case 1:
                if (tryPlayWithPackage("com.mxtech.videoplayer.pro", "MX Player专业版")) {
                    return;
                }
                break;
            case 2:
                if (tryPlayWithPackage("com.clov4r.android.nil", "MoboPlayer")) {
                    return;
                }
                break;
            case 11:
                if (tryPlayWithPackage("com.clov4r.android.nil.noad", "MoboPlayer Pro")) {
                    return;
                }
                break;
            case 3:
                if (tryPlayWithPackage("org.videolan.vlc", "VLC")) {
                    return;
                }
                break;
            case 4:
                if (tryPlayWithPackage("me.abitno.vplayer.t", "VPlayer")) {
                    return;
                }
                break;
            case 5:
                if (tryPlayWithPackage("com.redirectin.rockplayer.android.unified.lite", "RockPlayer Lite")) {
                    return;
                }
                break;
            case 6:
                if (tryPlayWithPackage("com.tencent.research.drop", "QQ影音")) {
                    return;
                }
                break;
            case 9:
                if (tryPlayWithPackage("com.aliangmaker.media", "凉腕播放器")) {
                    return;
                }
                break;
            case 7:
                if (trySystemPlayer()) {
                    return;
                }
                break;
            case 8:
                tryBuiltinPlayerWithCache();
                return;
            default:
                autoSelectPlayer();
                return;
        }

        autoSelectPlayer();
    }

    private Uri getExposedUri(File file) {
        if (SdkHelper.getSdkInt() >= 24) {
            return FileProviderCompat.getUriForFile(this, file);
        }
        return Uri.fromFile(file);
    }

    private boolean tryPlayWithPackage(String packageName, String playerName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(getExposedUri(cacheFile), "video/mp4");
            if (SdkHelper.getSdkInt() >= 24) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            try { Intent.class.getMethod("setPackage", String.class).invoke(intent, new Object[]{packageName}); } catch (Exception ignored) {};
            // 直接尝试启动；queryIntentActivities 对 FileProvider content URI 会因权限过滤误判 0，
            // 导致装了播放器也被降级。改为 try-catch，装了就播，没装/启动失败才降级。
            putLocalExternalExtras(intent);
            startActivity(intent);
            finish();
            return true;
        } catch (Exception e) {
            if (!hasShownPreferenceToast) {
                hasShownPreferenceToast = true;
                Toast.makeText(this, playerName + " 未安装或无法启动，正在尝试其他播放器...", Toast.LENGTH_SHORT).show();
            }
        }
        return false;
    }

    private boolean trySystemPlayer() {
        Uri contentUri = getExposedUri(cacheFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(contentUri, "video/mp4");
        if (SdkHelper.getSdkInt() >= 24) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        try {
            startActivity(intent);
            finish();
            return true;
        } catch (Exception e) {
            tryBuiltinPlayerWithCache();
            return true;
        }
    }

    /**
     * 使用缓存文件播放（非在线模式）
     */
    private void tryBuiltinPlayerWithCache() {
        int partIndex = getIntent().getIntExtra("part_index", 0);
        long[] cids = getIntent().hasExtra("cids")
                ? getIntent().getLongArrayExtra("cids") : null;
        String[] pagenames = getIntent().hasExtra("pagenames")
                ? getIntent().getStringArrayExtra("pagenames") : null;
        Intent intent = PlayerIntentFactory.create(this, BiliPlayerActivity.class,
                "", null, mDurationMs, videoTitle, aid, cid, partIndex,
                resumePosition, cids, pagenames, coverUrl, upName, false);
        intent.putExtra("cache_path", cacheFile.getAbsolutePath());
        intent.putExtra("offline_mode", true);
        if (danmakuCachePath != null && danmakuCachePath.length() > 0) {
            intent.putExtra("danmaku_cache_path", danmakuCachePath);
        }
        if (isPortrait()) intent.putExtra("is_portrait_loading", true);
        putQualityExtras(intent);
        startActivity(intent);
        finish();
    }

    private void autoSelectPlayer() {
        hasShownPreferenceToast = false;

        if (tryPlayWithPackageSilent("com.mxtech.videoplayer.ad")) {
            return;
        }
        if (tryPlayWithPackageSilent("com.mxtech.videoplayer.pro")) {
            return;
        }
        if (tryPlayWithPackageSilent("com.clov4r.android.nil")) {
            return;
        }
        if (tryPlayWithPackageSilent("com.clov4r.android.nil.noad")) {
            return;
        }
        if (tryPlayWithPackageSilent("org.videolan.vlc")) {
            return;
        }
        if (tryPlayWithPackageSilent("me.abitno.vplayer.t")) {
            return;
        }
        if (tryPlayWithPackageSilent("com.redirectin.rockplayer.android.unified.lite")) {
            return;
        }
        if (tryPlayWithPackageSilent("com.tencent.research.drop")) {
            return;
        }
        if (tryPlayWithPackageSilent("com.aliangmaker.media")) {
            return;
        }
        // 2.1+ 优先使用内置东风播放器，而不是跳系统播放器
        // 默认回退到内置系统 MediaPlayer 播放
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            tryBuiltinPlayerWithCache();
        } else {
            playWithBuiltinPlayer(videoUrl);
        }
        return;
    }

    private boolean tryPlayWithPackageSilent(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(getExposedUri(cacheFile), "video/mp4");
            if (SdkHelper.getSdkInt() >= 24) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            try { Intent.class.getMethod("setPackage", String.class).invoke(intent, new Object[]{packageName}); } catch (Exception ignored) {};
            if (getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                putLocalExternalExtras(intent);
            startActivity(intent);
                finish();
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消下载
        isDownloadCancelled = true;
        if (downloadPollRunnable != null) {
            handler.removeCallbacks(downloadPollRunnable);
            downloadPollRunnable = null;
        }
        stopTvAnimation();
        handler.removeCallbacksAndMessages(null);
    }

    private void putLocalExternalExtras(Intent intent) {
        ExternalPlayerExtras.putForLocal(intent, videoTitle, cid,
                resumePosition, danmakuCachePath);
    }

    private void putQualityExtras(Intent intent) {
        if (mQualityNames != null && mQualityNames.length > 0) {
            intent.putExtra("qn_str_array", mQualityNames);
        }
        if (mQualityValues != null && mQualityValues.length > 0) {
            intent.putExtra("qn_value_array", mQualityValues);
        }
        intent.putExtra("current_qn", mCurrentQn);
    }
}
