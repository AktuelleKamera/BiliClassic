package tv.biliclassic.util;

import android.content.Context;
import android.media.AudioManager;

public class AudioManagerHelper {
    public static int getStreamMaxVolume(Context context, int streamType) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return 0;
        }
        return audioManager.getStreamMaxVolume(streamType);
    }

    public static int getStreamVolume(Context context, int streamType) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return 0;
        }
        return audioManager.getStreamVolume(streamType);
    }

    public static void setStreamVolume(Context context, int streamType, int volume, int flag) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setStreamVolume(streamType, volume, flag);
        }
    }

    public static float getPercentageStreamVolumeValue(Context context, int streamType) {
        int max = getStreamMaxVolume(context, streamType);
        if (max <= 0) return 0f;
        float value = (float) getStreamVolume(context, streamType) / (float) max;
        return Math.min(Math.max(value, 0.0f), 1.0f);
    }
}
