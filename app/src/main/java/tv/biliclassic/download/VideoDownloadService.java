package tv.biliclassic.download;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import java.io.File;
import java.util.ArrayList;

import tv.biliclassic.R;
import tv.biliclassic.util.PermissionUtil;

import tv.biliclassic.util.SdkHelper;
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
    private static final String CHANNEL_ID = "download_progress";
    private static final int NOTIFY_FG = 1;

    private VideoDownloadManager mDownloadManager;
    private VideoDownloadNotificationHelper mNotifHelper;
    private File mDownloadDir;
    private boolean mStopping;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Notification n = buildFgNotification();
                Service.class.getMethod("startForeground", int.class, Notification.class)
                        .invoke(this, NOTIFY_FG, n);
            } catch (Exception e) {
            }
        }
        mStopping = false;
        mDownloadDir = resolveDownloadDir();
        mNotifHelper = new VideoDownloadNotificationHelper(this, CHANNEL_ID);
        mDownloadManager = new VideoDownloadManager(mNotifHelper, mDownloadDir);
        mDownloadManager.setQueueListener(new VideoDownloadManager.QueueListener() {
            public void onQueueEmpty() {
                if (mStopping) return;
                mStopping = true;
                if (SdkHelper.getSdkInt() >= 26) {
                    try {
                        Service.class.getMethod("stopForeground", boolean.class).invoke(VideoDownloadService.this, true);
                    } catch (Exception e) {
                    }
                }
                stopSelf();
            }
        });
        mDownloadManager.start();
    }

    private void createChannel() {
        if (SdkHelper.getSdkInt() < 26) return;
        try {
            Object nm = getSystemService("notification");
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Object channel = channelClass.getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(CHANNEL_ID, "下载进度", 1);
            channelClass.getMethod("setShowBadge", boolean.class).invoke(channel, false);
            nm.getClass().getMethod("createNotificationChannel", channelClass).invoke(nm, channel);
        } catch (Exception e) {
        }
    }

    private Notification buildFgNotification() {
        try {
            Class<?> builderClass = Class.forName("android.app.Notification$Builder");
            Object builder = builderClass.getConstructor(Context.class, String.class)
                    .newInstance(this, CHANNEL_ID);
            builderClass.getMethod("setContentTitle", CharSequence.class).invoke(builder, "下载中");
            builderClass.getMethod("setContentText", CharSequence.class).invoke(builder, "正在下载视频...");
            builderClass.getMethod("setSmallIcon", int.class).invoke(builder, R.drawable.ic_launcher);
            builderClass.getMethod("setOngoing", boolean.class).invoke(builder, true);
            builderClass.getMethod("setPriority", int.class).invoke(builder, -2);
            return (Notification) builderClass.getMethod("build").invoke(builder);
        } catch (Exception e) {
            return new Notification();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            long key = intent.getLongExtra("key", 0);
            if (mDownloadManager != null) {
                if (key != 0) mDownloadManager.pauseByKey(key);
                else mDownloadManager.pauseCurrent();
            }
            return START_NOT_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            long key = intent.getLongExtra("key", 0);
            if (key != 0 && mDownloadManager != null) {
                boolean found = mDownloadManager.resumePaused(key);
                if (!found) {
                    // 重启后内存任务丢失，从 entry.json 恢复
                    resumeFromDisk(key);
                }
            } else if (mDownloadManager != null) {
                mDownloadManager.resumeCurrent();
            }
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            mStopping = true;
            if (mDownloadManager != null) mDownloadManager.cancelAll();
            if (mNotifHelper != null) mNotifHelper.cancelNotification();
            if (SdkHelper.getSdkInt() >= 26) {
                try {
                    Service.class.getMethod("stopForeground", boolean.class).invoke(this, true);
                } catch (Exception e) {
                }
            }
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
        entry.videoUrl = videoUrl;
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
        mStopping = true;
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Service.class.getMethod("stopForeground", boolean.class).invoke(this, true);
            } catch (Exception e) {
            }
        }
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
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Context.class.getMethod("startForegroundService", Intent.class).invoke(context, intent);
            } catch (Exception e) {
                context.startService(intent);
            }
        } else {
            context.startService(intent);
        }
    }

    private void resumeFromDisk(long key) {
        ArrayList<VideoDownloadEntry> allEntries = VideoDownloadEnvironment.loadAllEntries(mDownloadDir);
        if (allEntries == null) return;
        for (VideoDownloadEntry entry : allEntries) {
            if (entry.getKey() == key && !entry.isCompleted
                    && entry.videoUrl != null && entry.videoUrl.length() > 0) {
                // 防止重复提交已在队列或下载中的任务
                if (mDownloadManager.hasTask(key)) return;
                entry.state = VideoDownloadEntry.STATE_IN_QUEUE;
                entry.isPaused = false;
                mDownloadManager.submit(entry, entry.videoUrl);
                return;
            }
        }
    }

    private File resolveDownloadDir() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) && PermissionUtil.hasWriteStorage(this)) {
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
