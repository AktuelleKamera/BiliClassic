package tv.biliclassic.download;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;

import java.io.File;

/**
 * 视频下载服务（Android Service）
 * 在后台执行视频下载，支持通知栏进度显示
 * 参考原版哔哩哔哩的 VideoDownloadService（大幅简化）
 *
 * 调用方通过 startService(Intent) 启动，Intent 中携带序列化的 VideoDownloadEntry 信息。
 * 服务启动后解析视频地址，提交到 VideoDownloadManager 队列中执行下载。
 */
public class VideoDownloadService extends Service {

    public static final String ACTION_PAUSE = "tv.biliclassic.action.PAUSE";
    public static final String ACTION_RESUME = "tv.biliclassic.action.RESUME";
    public static final String ACTION_CANCEL = "tv.biliclassic.action.CANCEL";

    private static final String EXTRA_ENTRY = "entry";
    private static final String EXTRA_VIDEO_URL = "video_url";

    private VideoDownloadManager mDownloadManager;
    private VideoDownloadNotificationHelper mNotifHelper;
    private File mDownloadDir;

    @Override
    public void onCreate() {
        super.onCreate();
        mDownloadDir = resolveDownloadDir();
        mNotifHelper = new VideoDownloadNotificationHelper(this);
        mDownloadManager = new VideoDownloadManager(mNotifHelper, mDownloadDir);
        mDownloadManager.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            if (mDownloadManager != null) mDownloadManager.pauseCurrent();
            return START_NOT_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            long key = intent.getLongExtra("key", 0);
            if (key != 0 && mDownloadManager != null) mDownloadManager.resumePaused(key);
            else if (mDownloadManager != null) mDownloadManager.resumeCurrent();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            if (mDownloadManager != null) mDownloadManager.cancelAll();
            if (mNotifHelper != null) mNotifHelper.cancelNotification();
            stopSelf();
            return START_NOT_STICKY;
        }

        long avid = intent.getLongExtra("avid", 0);
        String bvid = intent.getStringExtra("bvid");
        String title = intent.getStringExtra("title");
        String pageTitle = intent.getStringExtra("page_title");
        long cid = intent.getLongExtra("cid", 0);
        int page = intent.getIntExtra("page", 1);
        int quality = intent.getIntExtra("quality", 16);
        String qualityName = intent.getStringExtra("quality_name");
        String coverUrl = intent.getStringExtra("cover_url");
        String upName = intent.getStringExtra("up_name");
        String description = intent.getStringExtra("description");
        String tags = intent.getStringExtra("tags");
        String videoUrl = intent.getStringExtra("video_url");

        if (avid == 0 || cid == 0 || videoUrl == null || videoUrl.length() == 0) {
            return START_NOT_STICKY;
        }

        VideoDownloadEntry entry = new VideoDownloadEntry();
        entry.avid = avid;
        entry.bvid = bvid;
        entry.title = title;
        entry.pageTitle = pageTitle;
        entry.cid = cid;
        entry.page = page;
        entry.quality = quality;
        entry.qualityName = qualityName != null ? qualityName : VideoDownloadEnvironment.getQualityName(quality);
        entry.coverUrl = coverUrl;
        entry.upName = upName;
        entry.description = description;
        entry.tags = tags;
        entry.typeTag = VideoDownloadEnvironment.getTypeTagFromQuality(quality);
        entry.state = VideoDownloadEntry.STATE_IN_QUEUE;
        entry.timeStamp = System.currentTimeMillis();

        // 创建下载环境并保存初始 entry
        VideoDownloadEnvironment env = new VideoDownloadEnvironment(mDownloadDir,
                entry.avid, entry.page);
        entry.downloadEnv = env;
        env.saveEntry(entry);

        // 提交下载
        mDownloadManager.submit(entry, videoUrl);

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // 不需要绑定
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDownloadManager != null) {
            mDownloadManager.stop();
            mDownloadManager = null;
        }
        if (mNotifHelper != null) {
            mNotifHelper.cancelNotification();
            mNotifHelper = null;
        }
    }

    /**
     * 启动下载服务（静态便捷方法）
     */
    public static void startDownload(Context context,
                                     long avid, String bvid,
                                     String title, String pageTitle,
                                     long cid, int page,
                                     int quality, String qualityName,
                                     String coverUrl, String upName,
                                     String videoUrl, String description, String tags) {
        Intent intent = new Intent(context, VideoDownloadService.class);
        intent.putExtra("avid", avid);
        intent.putExtra("bvid", bvid);
        intent.putExtra("title", title);
        intent.putExtra("page_title", pageTitle);
        intent.putExtra("cid", cid);
        intent.putExtra("page", page);
        intent.putExtra("quality", quality);
        intent.putExtra("quality_name", qualityName);
        intent.putExtra("cover_url", coverUrl);
        intent.putExtra("up_name", upName);
        intent.putExtra("description", description);
        intent.putExtra("tags", tags);
        intent.putExtra("video_url", videoUrl);
        context.startService(intent);
    }

    private File resolveDownloadDir() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File extDir = new File(Environment.getExternalStorageDirectory(), "BiliClassic/Download");
            if (!extDir.exists()) {
                extDir.mkdirs();
            }
            if (extDir.isDirectory()) {
                return extDir;
            }
        }
        File internalDir = new File(getFilesDir(), "Download");
        if (!internalDir.exists()) {
            internalDir.mkdirs();
        }
        return internalDir;
    }
}
