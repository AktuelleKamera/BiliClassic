package tv.biliclassic.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.KeyEvent;

/**
 * 媒体会话 + 媒体样式通知（API 21+ 能力）。
 *
 * Android 2.1 及以下 Dalvik 校验器为硬失败模式：本类会被 BiliPlayerActivity
 * 直接引用，若字节码里出现 MediaSession / NotificationChannel / Notification.Builder /
 * PlaybackState / MediaMetadata 等高版本 API 的直接引用，整个类（连带引用方）都会被拒载。
 * 因此所有高版本 API 一律通过反射访问：低版本上构造器提前返回、各方法内部 no-op。
 * MediaSession.Callback 是抽象类无法代理，拆分到独立文件 MediaSessionCallbackImpl，
 * 仅在 API 21+ 上通过 Class.forName 按需加载。
 */
public class MediaSessionHelper {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "media_playback";

    // MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
    private static final int SESSION_FLAGS = 0x1 | 0x2;
    // PlaybackState.STATE_PLAYING / STATE_PAUSED
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 2;
    // PlaybackState.ACTION_PLAY_PAUSE
    private static final long ACTION_PLAY_PAUSE = 1L << 9;
    // NotificationManager.IMPORTANCE_DEFAULT
    private static final int IMPORTANCE_DEFAULT = 3;

    private Context context;
    private Class<?> activityClass;
    private Object mediaSession; // android.media.session.MediaSession
    private NotificationManager notificationManager;
    private String title = "";
    private String artist = "";
    private Bitmap coverBitmap;
    private Bitmap defaultLargeIcon;
    private boolean isPlaying;
    private PlayPauseListener listener;
    private BroadcastReceiver playPauseReceiver;

    public interface PlayPauseListener {
        void onPlayPause();
    }

    public MediaSessionHelper(Context context, Class<?> activityClass) {
        this.context = context;
        this.activityClass = activityClass;

        if (SdkHelper.getSdkInt() < 21) return;

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (SdkHelper.getSdkInt() >= 26) {
            createChannelCompat();
        }

        defaultLargeIcon = BitmapFactory.decodeResource(context.getResources(), tv.biliclassic.R.drawable.ic_launcher);

        try {
            Class<?> msClass = Class.forName("android.media.session.MediaSession");
            mediaSession = msClass.getConstructor(Context.class, String.class)
                    .newInstance(context, "BiliClassicPlayer");
            msClass.getMethod("setFlags", int.class).invoke(mediaSession, Integer.valueOf(SESSION_FLAGS));
            try {
                Class<?> cbClass = Class.forName("android.media.session.MediaSession$Callback");
                Object callback = Class.forName("tv.biliclassic.util.MediaSessionCallbackImpl")
                        .getConstructor(MediaSessionHelper.class).newInstance(this);
                msClass.getMethod("setCallback", cbClass).invoke(mediaSession, callback);
            } catch (Throwable t) {
            }
            msClass.getMethod("setActive", boolean.class).invoke(mediaSession, Boolean.TRUE);
        } catch (Throwable t) {
            mediaSession = null;
        }

        playPauseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                notifyListener();
            }
        };
        context.registerReceiver(playPauseReceiver, new IntentFilter("tv.biliclassic.ACTION_MEDIA_PLAY_PAUSE"));
    }

    /** API 26+ 通知渠道，反射创建 */
    private void createChannelCompat() {
        try {
            Class<?> chClass = Class.forName("android.app.NotificationChannel");
            Object channel = chClass.getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(CHANNEL_ID, "媒体播放", Integer.valueOf(IMPORTANCE_DEFAULT));
            chClass.getMethod("setShowBadge", boolean.class).invoke(channel, Boolean.FALSE);
            chClass.getMethod("setLockscreenVisibility", int.class)
                    .invoke(channel, Integer.valueOf(Notification.VISIBILITY_PUBLIC));
            notificationManager.getClass()
                    .getMethod("createNotificationChannel", chClass).invoke(notificationManager, channel);
        } catch (Throwable t) {
        }
    }

    /** 由 MediaSessionCallbackImpl 回调：媒体键处理，返回是否已消费 */
    public boolean handleMediaButton(Intent mediaButtonIntent) {
        KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
                notifyListener();
                return true;
            }
        }
        return false;
    }

    /** 由 MediaSessionCallbackImpl 回调 */
    void firePlayPause() {
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onPlayPause();
        }
    }

    public void setPlayPauseListener(PlayPauseListener listener) {
        this.listener = listener;
    }

    public void setMetadata(String title, String artist) {
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        if (SdkHelper.getSdkInt() >= 21 && mediaSession != null) {
            applyMetadataCompat();
        }
        showNotification();
    }

    /** MediaMetadata.Builder 反射构建；键名为编译期内联字符串常量 */
    private void applyMetadataCompat() {
        try {
            Class<?> mmClass = Class.forName("android.media.MediaMetadata");
            Class<?> bClass = Class.forName("android.media.MediaMetadata$Builder");
            Object builder = bClass.newInstance();
            bClass.getMethod("putString", String.class, String.class)
                    .invoke(builder, "android.media.metadata.TITLE", this.title);
            bClass.getMethod("putString", String.class, String.class)
                    .invoke(builder, "android.media.metadata.ARTIST", this.artist);
            bClass.getMethod("putLong", String.class, long.class)
                    .invoke(builder, "android.media.metadata.DURATION", Long.valueOf(0));
            if (coverBitmap != null && !coverBitmap.isRecycled()) {
                bClass.getMethod("putBitmap", String.class, Bitmap.class)
                        .invoke(builder, "android.media.metadata.ALBUM_ART", coverBitmap);
            }
            Object metadata = bClass.getMethod("build").invoke(builder);
            mediaSession.getClass().getMethod("setMetadata", mmClass).invoke(mediaSession, metadata);
        } catch (Throwable t) {
        }
    }

    public void setCoverBitmap(Bitmap bitmap) {
        this.coverBitmap = bitmap;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
        if (SdkHelper.getSdkInt() >= 21 && mediaSession != null) {
            applyPlaybackStateCompat(0);
        }
        showNotification();
    }

    public void updatePlaybackPosition(long position, long duration) {
        if (SdkHelper.getSdkInt() < 21 || mediaSession == null) return;
        applyPlaybackStateCompat(position);
    }

    /** PlaybackState.Builder 反射构建 */
    private void applyPlaybackStateCompat(long position) {
        try {
            Class<?> psClass = Class.forName("android.media.session.PlaybackState");
            Class<?> bClass = Class.forName("android.media.session.PlaybackState$Builder");
            Object builder = bClass.newInstance();
            bClass.getMethod("setState", int.class, long.class, float.class)
                    .invoke(builder, Integer.valueOf(isPlaying ? STATE_PLAYING : STATE_PAUSED),
                            Long.valueOf(position), Float.valueOf(1.0f));
            bClass.getMethod("setActions", long.class)
                    .invoke(builder, Long.valueOf(ACTION_PLAY_PAUSE));
            Object state = bClass.getMethod("build").invoke(builder);
            mediaSession.getClass().getMethod("setPlaybackState", psClass)
                    .invoke(mediaSession, state);
        } catch (Throwable t) {
        }
    }

    private void showNotification() {
        if (SdkHelper.getSdkInt() < 21 || notificationManager == null) return;

        try {
            Intent activityIntent = new Intent(context, activityClass);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent playPauseIntent = new Intent("tv.biliclassic.ACTION_MEDIA_PLAY_PAUSE");
            PendingIntent playPausePending = PendingIntent.getBroadcast(context, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            int playIcon = isPlaying
                    ? tv.biliclassic.R.drawable.bili_player_play_can_pause
                    : tv.biliclassic.R.drawable.bili_player_play_can_play;

            Class<?> builderClass = Class.forName("android.app.Notification$Builder");
            Object builder;
            if (SdkHelper.getSdkInt() >= 26) {
                builder = builderClass.getConstructor(Context.class, String.class)
                        .newInstance(context, CHANNEL_ID);
            } else {
                builder = builderClass.getConstructor(Context.class).newInstance(context);
            }

            builderClass.getMethod("setSmallIcon", int.class)
                    .invoke(builder, Integer.valueOf(tv.biliclassic.R.drawable.ic_launcher));
            builderClass.getMethod("setContentTitle", CharSequence.class)
                    .invoke(builder, this.title);
            builderClass.getMethod("setContentText", CharSequence.class)
                    .invoke(builder, this.artist);
            builderClass.getMethod("setContentIntent", PendingIntent.class)
                    .invoke(builder, contentIntent);
            builderClass.getMethod("setOngoing", boolean.class)
                    .invoke(builder, Boolean.valueOf(isPlaying));
            builderClass.getMethod("setShowWhen", boolean.class)
                    .invoke(builder, Boolean.FALSE);

            Bitmap largeIcon = (coverBitmap != null && !coverBitmap.isRecycled())
                    ? coverBitmap : defaultLargeIcon;
            if (largeIcon != null && !largeIcon.isRecycled()) {
                builderClass.getMethod("setLargeIcon", Bitmap.class).invoke(builder, largeIcon);
            }

            builderClass.getMethod("setColor", int.class).invoke(builder, Integer.valueOf(0xff212121));

            // Notification.MediaStyle（API 21+）：setMediaSession(token).setShowActionsInCompactView(0)
            try {
                Class<?> styleClass = Class.forName("android.app.Notification$MediaStyle");
                Object style = styleClass.newInstance();
                Object token = mediaSession != null
                        ? mediaSession.getClass().getMethod("getSessionToken").invoke(mediaSession)
                        : null;
                if (token != null) {
                    styleClass.getMethod("setMediaSession",
                            Class.forName("android.media.session.MediaSession$Token"))
                            .invoke(style, token);
                }
                styleClass.getMethod("setShowActionsInCompactView", int[].class)
                        .invoke(style, new Object[]{new int[]{0}});
                builderClass.getMethod("setStyle",
                        Class.forName("android.app.Notification$Style")).invoke(builder, style);
            } catch (Throwable t) {
            }

            builderClass.getMethod("addAction", int.class, CharSequence.class, PendingIntent.class)
                    .invoke(builder, Integer.valueOf(playIcon),
                            isPlaying ? "暂停" : "播放", playPausePending);

            Object notification = builderClass.getMethod("build").invoke(builder);
            notificationManager.notify(NOTIFICATION_ID, (Notification) notification);
        } catch (Throwable t) {
        }
    }

    public void hideNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    public void release() {
        if (SdkHelper.getSdkInt() >= 21 && mediaSession != null) {
            try {
                mediaSession.getClass().getMethod("setActive", boolean.class)
                        .invoke(mediaSession, Boolean.FALSE);
                mediaSession.getClass().getMethod("release").invoke(mediaSession);
            } catch (Throwable t) {
            }
            mediaSession = null;
        }
        hideNotification();
        if (playPauseReceiver != null) {
            try {
                context.unregisterReceiver(playPauseReceiver);
            } catch (Exception ignored) {
            }
            playPauseReceiver = null;
        }
        if (defaultLargeIcon != null && !defaultLargeIcon.isRecycled()) {
            defaultLargeIcon.recycle();
            defaultLargeIcon = null;
        }
    }
}
