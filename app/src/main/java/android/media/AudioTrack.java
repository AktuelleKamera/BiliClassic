package android.media;

/**
 * Minimal API-1 compatibility shell for IJK's J4A class loader.
 * On Android 1.5+ the framework class wins; on Android 1.0 this class only
 * satisfies class/method lookup. IJK audio is disabled on this path and the
 * system MediaPlayer is used for audio instead.
 */
public class AudioTrack {
    public AudioTrack(int streamType, int sampleRateInHz, int channelConfig,
                      int audioFormat, int bufferSizeInBytes, int mode) {
    }

    public static int getMinBufferSize(int sampleRateInHz, int channelConfig,
                                       int audioFormat) {
        return 4096;
    }

    public static float getMaxVolume() {
        return 1.0f;
    }

    public static float getMinVolume() {
        return 0.0f;
    }

    public static int getNativeOutputSampleRate(int streamType) {
        return 44100;
    }

    public void play() {
    }

    public void pause() {
    }

    public void stop() {
    }

    public void flush() {
    }

    public void release() {
    }

    public int write(byte[] audioData, int offsetInBytes, int sizeInBytes) {
        return sizeInBytes;
    }

    public int setStereoVolume(float leftGain, float rightGain) {
        return 0;
    }

    public int getAudioSessionId() {
        return 0;
    }

    public PlaybackParams getPlaybackParams() {
        return null;
    }

    public void setPlaybackParams(PlaybackParams params) {
    }

    public int getStreamType() {
        return 0;
    }

    public int getSampleRate() {
        return 44100;
    }

    public int getPlaybackRate() {
        return 1000;
    }

    public int setPlaybackRate(int sampleRateInHz) {
        return 0;
    }
}
