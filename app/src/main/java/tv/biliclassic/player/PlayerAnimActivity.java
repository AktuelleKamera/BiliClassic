package tv.biliclassic.player;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;

public class PlayerAnimActivity extends Activity {

    private ImageView ivTvAnim;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private TextView tvStatus;

    private Handler handler = new Handler();
    private Handler animHandler = new Handler();
    private int animIndex = 0;
    private int[] animDrawables = {
            R.drawable.bili_anim_tv_chan_1,
            R.drawable.bili_anim_tv_chan_3,
            R.drawable.bili_anim_tv_chan_5,
            R.drawable.bili_anim_tv_chan_7,
            R.drawable.bili_anim_tv_chan_9
    };

    private String videoUrl;
    private String videoTitle;
    private long aid;
    private long cid;
    private File cacheFile;

    private boolean hasShownPreferenceToast = false;
    private boolean isOnlineMode = false;

    private String[] mQualityNames;
    private int[] mQualityValues;
    private int mCurrentQn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_anim);

        ivTvAnim = (ImageView) findViewById(R.id.iv_tv_anim);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        tvProgress = (TextView) findViewById(R.id.tv_progress);
        tvStatus = (TextView) findViewById(R.id.tv_status);

        videoUrl = getIntent().getStringExtra("video_url");
        videoTitle = getIntent().getStringExtra("video_title");
        aid = getIntent().getLongExtra("aid", 0);
        cid = getIntent().getLongExtra("cid", 0);

        // 检测是否在线模式
        isOnlineMode = SettingsActivity.isOnlinePlayEnabled();

        mQualityNames = getIntent().getStringArrayExtra("qn_str_array");
        mQualityValues = getIntent().getIntArrayExtra("qn_value_array");
        mCurrentQn = getIntent().getIntExtra("current_qn", 0);

        // 打印日志确认 videoUrl
        android.util.Log.e("PlayerAnim", "videoUrl: " + videoUrl);
        android.util.Log.e("PlayerAnim", "videoTitle: " + videoTitle);
        android.util.Log.e("PlayerAnim", "aid: " + aid + ", cid: " + cid);
        android.util.Log.e("PlayerAnim", "isOnlineMode: " + isOnlineMode);

        if (videoUrl == null || videoUrl.length() == 0) {
            Toast.makeText(this, "视频地址无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startTvAnimation();

        if (isOnlineMode) {
            // 在线模式：直接播放，不下载
            tvStatus.setText("在线播放模式...");
            progressBar.setVisibility(ProgressBar.GONE);
            tvProgress.setVisibility(TextView.GONE);

            // 延迟一下让动画先显示
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopTvAnimation();
                    // 直接传入 videoUrl
                    playWithBuiltinPlayer(videoUrl);
                }
            }, 500);
        } else {
            // 非在线模式：走下载缓存流程
            File cacheDir = getCacheDir();

            if (isSDCardAvailable()) {
                try {
                    File baseDir = Environment.getExternalStorageDirectory();
                    File sdCacheDir = new File(baseDir, "BiliClassic/cache");
                    if (!sdCacheDir.exists()) {
                        sdCacheDir.mkdirs();
                    }
                    if (sdCacheDir.exists() && sdCacheDir.canWrite()) {
                        cacheDir = sdCacheDir;
                        android.util.Log.e("PlayerAnim", "使用 SD 卡缓存: " + cacheDir.getAbsolutePath());
                    } else {
                        android.util.Log.e("PlayerAnim", "SD 卡不可写，使用内部缓存");
                    }
                } catch (Exception e) {
                    android.util.Log.e("PlayerAnim", "SD 卡访问异常: " + e.getMessage() + "，使用内部缓存");
                }
            } else {
                android.util.Log.e("PlayerAnim", "SD 卡不可用，使用内部缓存");
            }

            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            String cacheFileName = aid + "_" + cid + ".mp4";
            cacheFile = new File(cacheDir, cacheFileName);
            android.util.Log.e("PlayerAnim", "缓存路径: " + cacheFile.getAbsolutePath());

            if (cacheFile.exists()) {
                // 缓存存在，用文件路径播放
                playWithPlayer();
            } else {
                startDownload();
            }
        }
    }

    /**
     * 使用内置播放器直接播放视频（在线模式）
     */
    private void playWithBuiltinPlayer(String url) {
        android.util.Log.e("PlayerAnim", "playWithBuiltinPlayer, url: " + url);
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "视频地址为空", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent intent = new Intent(this, BiliPlayerActivity.class);
        intent.putExtra("video_title", videoTitle);
        intent.putExtra("video_url", url);
        intent.putExtra("cache_path", (String) null);
        intent.putExtra("aid", aid);
        intent.putExtra("cid", cid);
        // 用 extra 标记在线模式，让 BiliPlayerActivity 知道不要依赖缓存
        intent.putExtra("online_mode", true);
        putQualityExtras(intent);
        startActivity(intent);
        finish();
    }

    private void startTvAnimation() {
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

    private void startDownload() {
        tvStatus.setText("正在缓冲...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadVideo();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopTvAnimation();
                            playWithPlayer();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopTvAnimation();
                            Toast.makeText(PlayerAnimActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void downloadVideo() throws Exception {
        File parentDir = cacheFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (cacheFile.exists()) {
            return;
        }

        String url = videoUrl;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        if (url.startsWith("https") && conn instanceof javax.net.ssl.HttpsURLConnection) {
            javax.net.ssl.SSLSocketFactory sslFactory = tv.biliclassic.util.NetWorkUtil.getTrustAllSSLSocketFactory();
            if (sslFactory != null) {
                ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
            }
            ((javax.net.ssl.HttpsURLConnection) conn).setHostnameVerifier(
                    tv.biliclassic.util.NetWorkUtil.TRUST_ALL_HOSTNAMES
            );
        }

        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.connect();

        int contentLength = conn.getContentLength();
        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(cacheFile);
        byte[] buffer = new byte[32768];
        int len;
        long total = 0;
        int lastProgress = 0;

        while ((len = is.read(buffer)) != -1) {
            fos.write(buffer, 0, len);
            total += len;

            if (contentLength > 0) {
                final int percent = (int) (total * 100 / contentLength);
                if (percent - lastProgress >= 2) {
                    lastProgress = percent;
                    final int p = percent;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(p);
                            tvProgress.setText(p + "%");
                        }
                    });
                }
            }
        }
        fos.close();
        is.close();
        conn.disconnect();
    }

    private void playWithPlayer() {
        if (!cacheFile.exists() || cacheFile.length() == 0) {
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show();
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

    private boolean tryPlayWithPackage(String packageName, String playerName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");
            intent.setPackage(packageName);

            PackageManager pm = getPackageManager();
            if (pm.queryIntentActivities(intent, 0).size() > 0) {
                startActivity(intent);
                finish();
                return true;
            } else {
                if (!hasShownPreferenceToast) {
                    hasShownPreferenceToast = true;
                    Toast.makeText(this, playerName + " 未安装，正在尝试其他播放器...", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean trySystemPlayer() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");

            PackageManager pm = getPackageManager();
            if (pm.queryIntentActivities(intent, 0).size() > 0) {
                startActivity(intent);
                finish();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 使用缓存文件播放（非在线模式）
     */
    private void tryBuiltinPlayerWithCache() {
        Intent intent = new Intent(this, BiliPlayerActivity.class);
        intent.putExtra("video_title", videoTitle);
        intent.putExtra("video_url", "");  // 传空，播放器会使用缓存
        intent.putExtra("cache_path", cacheFile.getAbsolutePath());
        intent.putExtra("aid", aid);
        intent.putExtra("cid", cid);
        intent.putExtra("online_mode", false);
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
        if (trySystemPlayer()) {
            return;
        }
        Toast.makeText(this, "未找到可用的视频播放器，请安装设置里的任意播放器", Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean tryPlayWithPackageSilent(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");
            intent.setPackage(packageName);

            PackageManager pm = getPackageManager();
            if (pm.queryIntentActivities(intent, 0).size() > 0) {
                startActivity(intent);
                finish();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTvAnimation();
        handler.removeCallbacksAndMessages(null);
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