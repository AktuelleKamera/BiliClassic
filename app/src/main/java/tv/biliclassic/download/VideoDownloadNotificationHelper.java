package tv.biliclassic.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import tv.biliclassic.OfflineActivity;
import tv.biliclassic.R;

/**
 * 下载通知栏辅助类，管理下载进度通知
 * 参考原版哔哩哔哩的 VideoDownloadNotificationHelper
 */
public class VideoDownloadNotificationHelper {

    public static final int ID_NOTIFICATION_DOWNLOAD = 69632;

    private final Context mContext;
    private final NotificationManager mNotifManager;
    private NotificationCompat.Builder mBuilder;

    public VideoDownloadNotificationHelper(Context context) {
        mContext = context.getApplicationContext();
        mNotifManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.ic_launcher)
                .setWhen(System.currentTimeMillis());
    }

    /**
     * 显示下载进度通知
     */
    public void notifyDownloadProgress(VideoDownloadEntry entry) {
        String title = entry.pageTitle != null && entry.pageTitle.length() > 0
                ? entry.pageTitle : entry.title;

        int progress = entry.getProgressPercentage();

        Intent intent = new Intent(mContext, OfflineActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentTitle("正在下载: " + title)
                .setContentText(progress + "%  |  av" + entry.avid)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setProgress(100, progress, false);

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            mBuilder.setPriority(Notification.PRIORITY_LOW);
        }

        Notification notif = mBuilder.build();
        mNotifManager.notify(ID_NOTIFICATION_DOWNLOAD, notif);
    }

    /**
     * 下载完成通知
     */
    public void notifyDownloadComplete(VideoDownloadEntry entry) {
        String title = entry.pageTitle != null && entry.pageTitle.length() > 0
                ? entry.pageTitle : entry.title;

        Intent intent = new Intent(mContext, OfflineActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentTitle("下载完成: " + title)
                .setContentText("av" + entry.avid + " | " + entry.qualityName)
                .setContentIntent(pendingIntent)
                .setOngoing(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false);

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            mBuilder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        Notification notif = mBuilder.build();
        mNotifManager.notify(ID_NOTIFICATION_DOWNLOAD, notif);
    }

    /**
     * 下载失败通知
     */
    public void notifyDownloadFailed(VideoDownloadEntry entry, String errorMsg) {
        String title = entry.pageTitle != null && entry.pageTitle.length() > 0
                ? entry.pageTitle : entry.title;

        Intent intent = new Intent(mContext, OfflineActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentTitle("下载失败: " + title)
                .setContentText(errorMsg != null ? errorMsg : "未知错误")
                .setContentIntent(pendingIntent)
                .setOngoing(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false);

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            mBuilder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        Notification notif = mBuilder.build();
        mNotifManager.notify(ID_NOTIFICATION_DOWNLOAD, notif);
    }

    /**
     * 取消通知
     */
    public void cancelNotification() {
        mNotifManager.cancel(ID_NOTIFICATION_DOWNLOAD);
    }
}
