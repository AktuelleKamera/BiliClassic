package tv.biliclassic.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import tv.biliclassic.OfflineActivity;
import tv.biliclassic.util.SdkHelper;

public class VideoDownloadNotificationHelper {

    public static final int ID_NOTIFICATION_DOWNLOAD = 69632;

    private final Context mContext;
    private final NotificationManager mNotifManager;
    private final String mChannelId;

    private static Class<?> sBuilderClass;
    private static java.lang.reflect.Constructor<?> sBuilderCtor;

    static {
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                sBuilderClass = Class.forName("android.app.Notification$Builder");
                sBuilderCtor = sBuilderClass.getConstructor(Context.class, String.class);
            } catch (Exception e) {
            }
        }
    }

    public VideoDownloadNotificationHelper(Context context) {
        this(context, null);
    }

    public VideoDownloadNotificationHelper(Context context, String channelId) {
        mContext = context.getApplicationContext();
        mChannelId = channelId;
        mNotifManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private Object newBuilder() throws Exception {
        if (sBuilderCtor != null && mChannelId != null) {
            return sBuilderCtor.newInstance(mContext, mChannelId);
        }
        return null;
    }

    private void setStr(Object builder, String method, String value) throws Exception {
        if (builder == null) return;
        sBuilderClass.getMethod(method, CharSequence.class).invoke(builder, value);
    }

    private void setInt(Object builder, String method, int value) throws Exception {
        if (builder == null) return;
        sBuilderClass.getMethod(method, int.class).invoke(builder, value);
    }

    private void setBool(Object builder, String method, boolean value) throws Exception {
        if (builder == null) return;
        sBuilderClass.getMethod(method, boolean.class).invoke(builder, value);
    }

    private void setLong(Object builder, String method, long value) throws Exception {
        if (builder == null) return;
        sBuilderClass.getMethod(method, long.class).invoke(builder, value);
    }

    private Notification makeNotification(String title, String text,
                                          boolean ongoing, int progress, int max) {
        try {
            Object builder = newBuilder();
            if (builder == null) {
                Notification n = new Notification(android.R.drawable.ic_menu_rotate,
                        title, System.currentTimeMillis());
                try {
                    n.getClass().getMethod("setLatestEventInfo", Context.class,
                            CharSequence.class, CharSequence.class, PendingIntent.class)
                            .invoke(n, mContext, title, text, null);
                } catch (Exception e) {
                }
                return n;
            }
            setStr(builder, "setContentTitle", title);
            setStr(builder, "setContentText", text);
            setInt(builder, "setSmallIcon", android.R.drawable.ic_menu_rotate);
            setBool(builder, "setOngoing", ongoing);
            setBool(builder, "setAutoCancel", !ongoing);
            setLong(builder, "setWhen", System.currentTimeMillis());

            if (SdkHelper.getSdkInt() >= 11) {
                try {
                    sBuilderClass.getMethod("setPriority", int.class).invoke(builder, ongoing ? -2 : 0);
                } catch (Exception e) {
                }
            }

            if (progress > 0 && max > 0) {
                try {
                    sBuilderClass.getMethod("setProgress", int.class, int.class, boolean.class)
                            .invoke(builder, max, progress, false);
                } catch (Exception e) {
                }
            }

            Intent intent = new Intent(mContext, OfflineActivity.class);
            PendingIntent pi;
            if (SdkHelper.getSdkInt() >= 23) {
                pi = PendingIntent.getActivity(mContext, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pi = PendingIntent.getActivity(mContext, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
            }

            try {
                sBuilderClass.getMethod("setContentIntent", PendingIntent.class).invoke(builder, pi);
            } catch (Exception e) {
            }

            return (Notification) sBuilderClass.getMethod("build").invoke(builder);
        } catch (Exception e) {
            return null;
        }
    }

    public void notifyDownloadProgress(VideoDownloadEntry entry) {
        String title = entry.pageTitle != null && entry.pageTitle.length() > 0
                ? entry.pageTitle : entry.title;
        int progress = entry.getProgressPercentage();
        Notification n = makeNotification("正在下载: " + title,
                progress + "%  |  av" + entry.avid, true, progress, 100);
        if (n != null) mNotifManager.notify(ID_NOTIFICATION_DOWNLOAD, n);
    }

    public void notifyDownloadComplete(VideoDownloadEntry entry) {
        String title = entry.pageTitle != null && entry.pageTitle.length() > 0
                ? entry.pageTitle : entry.title;
        Notification n = makeNotification("下载完成: " + title,
                "av" + entry.avid + " | " + entry.qualityName, false, 0, 0);
        if (n != null) mNotifManager.notify(ID_NOTIFICATION_DOWNLOAD, n);
    }

    public void notifyDownloadFailed(VideoDownloadEntry entry, String errorMsg) {
        String title = entry.pageTitle != null && entry.pageTitle.length() > 0
                ? entry.pageTitle : entry.title;
        Notification n = makeNotification("下载失败: " + title,
                errorMsg != null ? errorMsg : "未知错误", false, 0, 0);
        if (n != null) mNotifManager.notify(ID_NOTIFICATION_DOWNLOAD, n);
    }

    public void cancelNotification() {
        mNotifManager.cancel(ID_NOTIFICATION_DOWNLOAD);
    }
}
