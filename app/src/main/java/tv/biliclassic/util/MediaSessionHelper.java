package tv.biliclassic.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.view.KeyEvent;

import tv.biliclassic.R;

public class MediaSessionHelper {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "media_playback";

    private Context context;
    private Class<?> activityClass;
    private MediaSession mediaSession;
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

        if (Build.VERSION.SDK_INT < 21) return;

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "媒体播放", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }

        defaultLargeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);

        mediaSession = new MediaSession(context, "BiliClassicPlayer");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            || event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
                        notifyListener();
                        return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

            @Override
            public void onPlay() {
                notifyListener();
            }

            @Override
            public void onPause() {
                notifyListener();
            }
        });
        mediaSession.setActive(true);

        playPauseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                notifyListener();
            }
        };
        context.registerReceiver(playPauseReceiver, new IntentFilter("tv.biliclassic.ACTION_MEDIA_PLAY_PAUSE"));
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
        if (Build.VERSION.SDK_INT >= 21 && mediaSession != null) {
            android.media.MediaMetadata.Builder builder = new android.media.MediaMetadata.Builder();
            builder.putString(android.media.MediaMetadata.METADATA_KEY_TITLE, this.title);
            builder.putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, this.artist);
            builder.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 0);
            if (coverBitmap != null && !coverBitmap.isRecycled()) {
                builder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, coverBitmap);
            }
            mediaSession.setMetadata(builder.build());
        }
        showNotification();
    }

    public void setCoverBitmap(Bitmap bitmap) {
        this.coverBitmap = bitmap;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
        if (Build.VERSION.SDK_INT >= 21 && mediaSession != null) {
            int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                    .setState(state, 0, 1.0f)
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE);
            mediaSession.setPlaybackState(stateBuilder.build());
        }
        showNotification();
    }

    public void updatePlaybackPosition(long position, long duration) {
        if (Build.VERSION.SDK_INT < 21 || mediaSession == null) return;
        int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setState(state, position, 1.0f)
                .setActions(PlaybackState.ACTION_PLAY_PAUSE);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void showNotification() {
        if (Build.VERSION.SDK_INT < 21 || notificationManager == null) return;

        Intent activityIntent = new Intent(context, activityClass);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent playPauseIntent = new Intent("tv.biliclassic.ACTION_MEDIA_PLAY_PAUSE");
        PendingIntent playPausePending = PendingIntent.getBroadcast(context, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int playIcon = isPlaying ? R.drawable.bili_player_play_can_pause : R.drawable.bili_player_play_can_play;

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying)
                .setShowWhen(false);

        Bitmap largeIcon = (coverBitmap != null && !coverBitmap.isRecycled())
                ? coverBitmap : defaultLargeIcon;
        if (largeIcon != null && !largeIcon.isRecycled()) {
            builder.setLargeIcon(largeIcon);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor(0xff212121);
        }

        Notification.MediaStyle mediaStyle = new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0);

        builder.setStyle(mediaStyle);
        builder.addAction(playIcon, isPlaying ? "暂停" : "播放", playPausePending);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void hideNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    public void release() {
        if (Build.VERSION.SDK_INT >= 21 && mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
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
