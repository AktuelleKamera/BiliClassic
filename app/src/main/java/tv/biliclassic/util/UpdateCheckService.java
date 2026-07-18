package tv.biliclassic.util;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Method;

import tv.biliclassic.R;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;

public class UpdateCheckService extends Service {

    private static final String CHANNEL_ID_FG = "update_check_fg";
    private static final String CHANNEL_ID_RESULT = "update_check_result";
    private static final int NOTIFY_FG = 1;
    private static final int NOTIFY_UPDATE = 2;
    private static final long CHECK_INTERVAL = 24 * 60 * 60 * 1000; // 24h

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!SharedPreferencesUtil.getBoolean("auto_check_update", true)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Notification n = buildForegroundNotification();
                Service.class.getMethod("startForeground", int.class, Notification.class)
                        .invoke(this, NOTIFY_FG, n);
            } catch (Exception e) {
            }
        }

        checkUpdate();
        scheduleNext();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Service.class.getMethod("stopForeground", boolean.class).invoke(this, true);
            } catch (Exception e) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannels() {
        if (SdkHelper.getSdkInt() < 26) return;
        try {
            Object manager = getSystemService("notification");
            Class<?> nmClass = manager.getClass();

            Object fgChannel = createChannel(manager, CHANNEL_ID_FG, "更新检查运行中", 1);
            nmClass.getMethod("createNotificationChannel", getChannelClass())
                    .invoke(manager, fgChannel);

            Object resultChannel = createChannel(manager, CHANNEL_ID_RESULT, "更新检查结果", 4);
            nmClass.getMethod("createNotificationChannel", getChannelClass())
                    .invoke(manager, resultChannel);
        } catch (Exception e) {
        }
    }

    private Object createChannel(Object manager, String id, String name, int importance) throws Exception {
        Class<?> channelClass = Class.forName("android.app.NotificationChannel");
        Object channel = channelClass.getConstructor(String.class, CharSequence.class, int.class)
                .newInstance(id, name, importance);
        channelClass.getMethod("setShowBadge", boolean.class).invoke(channel, false);
        return channel;
    }

    private Class<?> getChannelClass() throws ClassNotFoundException {
        return Class.forName("android.app.NotificationChannel");
    }

    private Notification buildForegroundNotification() {
        try {
            Class<?> builderClass = Class.forName("android.app.Notification$Builder");
            Object builder = builderClass.getConstructor(Context.class, String.class)
                    .newInstance(this, CHANNEL_ID_FG);
            builderClass.getMethod("setContentTitle", CharSequence.class)
                    .invoke(builder, "检查更新");
            builderClass.getMethod("setContentText", CharSequence.class)
                    .invoke(builder, "正在检查新版本...");
            builderClass.getMethod("setSmallIcon", int.class)
                    .invoke(builder, android.R.drawable.ic_menu_rotate);
            builderClass.getMethod("setOngoing", boolean.class).invoke(builder, true);
            builderClass.getMethod("setPriority", int.class).invoke(builder, -2);
            return (Notification) builderClass.getMethod("build").invoke(builder);
        } catch (Exception e) {
            return null;
        }
    }

    private void checkUpdate() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    int curCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    String curName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

                    UpdateUtil.checkUpdate(UpdateCheckService.this, curCode, curName,
                            new UpdateUtil.UpdateCallback() {
                                public void onCheckStart() {}

                                public void onCheckComplete(boolean hasUpdate, String message) {
                                    if (hasUpdate) {
                                        showUpdateNotification(message);
                                    }
                                    stopSelf();
                                }

                                public void onCheckFailed(String error) {
                                    stopSelf();
                                }
                            });
                } catch (Exception e) {
                    stopSelf();
                }
            }
        }).start();
    }

    private void showUpdateNotification(String message) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (SdkHelper.getSdkInt() >= 33) {
                try {
                    Method checkMethod = Context.class.getMethod("checkSelfPermission", String.class);
                    String permission = "android.permission.POST_NOTIFICATIONS";
                    int result = (Integer) checkMethod.invoke(this, permission);
                    if (result != 0) return;
                } catch (Exception e) {
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("http://www.biliclassic.cn"));
            PendingIntent pi;
            if (SdkHelper.getSdkInt() >= 23) {
                pi = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pi = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
            }

            Notification notif;
            if (SdkHelper.getSdkInt() >= 26) {
                Class<?> builderClass = Class.forName("android.app.Notification$Builder");
                Object builder = builderClass.getConstructor(Context.class, String.class)
                        .newInstance(this, CHANNEL_ID_RESULT);
                builderClass.getMethod("setContentTitle", CharSequence.class)
                        .invoke(builder, "发现新版本");
                builderClass.getMethod("setContentText", CharSequence.class)
                        .invoke(builder, message);
                builderClass.getMethod("setSmallIcon", int.class)
                        .invoke(builder, android.R.drawable.ic_menu_info_details);
                builderClass.getMethod("setAutoCancel", boolean.class).invoke(builder, true);
                builderClass.getMethod("setContentIntent", PendingIntent.class)
                        .invoke(builder, pi);
                builderClass.getMethod("setPriority", int.class).invoke(builder, 1);
                notif = (Notification) builderClass.getMethod("build").invoke(builder);
            } else {
                notif = new Notification(android.R.drawable.ic_menu_info_details,
                        "发现新版本", System.currentTimeMillis());
                try {
                    Class<?> builderClass = Class.forName("android.app.Notification$Builder");
                    Object builder = builderClass.getConstructor(Context.class)
                            .newInstance(this);
                    builderClass.getMethod("setContentTitle", CharSequence.class)
                            .invoke(builder, "发现新版本");
                    builderClass.getMethod("setContentText", CharSequence.class)
                            .invoke(builder, message);
                    builderClass.getMethod("setSmallIcon", int.class)
                            .invoke(builder, android.R.drawable.ic_menu_info_details);
                    builderClass.getMethod("setAutoCancel", boolean.class).invoke(builder, true);
                    builderClass.getMethod("setContentIntent", PendingIntent.class)
                            .invoke(builder, pi);
                    notif = (Notification) builderClass.getMethod("build").invoke(builder);
                } catch (Exception e) {
                }
            }

            nm.notify(NOTIFY_UPDATE, notif);
        } catch (Exception e) {
        }
    }

    private void scheduleNext() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(this, UpdateCheckService.class);
        PendingIntent pi;
        if (SdkHelper.getSdkInt() >= 23) {
            pi = PendingIntent.getService(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pi = PendingIntent.getService(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        long next = System.currentTimeMillis() + CHECK_INTERVAL;
        if (SdkHelper.getSdkInt() >= 19) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, CHECK_INTERVAL, pi);
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, next, CHECK_INTERVAL, pi);
        }
    }

    public static void schedule(Context context) {
        Intent intent = new Intent(context, UpdateCheckService.class);
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
}
