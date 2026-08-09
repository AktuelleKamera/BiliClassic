package com.clov4r.android.nil;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.lang.reflect.Method;

/**
 * 软解播放器 JNI 绑定（对应 libcmplayer_*.so 导出的
 * Java_com_clov4r_android_nil_CMPlayer_* 符号）。
 * 包名与类名必须与符号一致，否则 System.loadLibrary 后方法绑定失败。
 */
public class CMPlayer {

    // 保存 native 端正在使用的 AudioTrack 强引用，防止被 GC 回收导致
    // native 音频线程写入已释放对象而 SIGSEGV（libmedia.so 崩溃）。
    public AudioTrack mAudioTrack;

    public native int nativeOpen(String filePath, int sdkVersion, String params);

    public native void nativePlay();

    public native void nativePause();

    public native void nativeSeek(long positionMs);

    public native long nativeGetCurrTime();

    public native long nativeGetDurationTime();

    public native void nativeClose();

    public native int nativeGetFPS();

    public native String nativeGetFileInfo();

    public native int nativeGetSubtileCount();

    public native byte[] nativeGetSubtitle();

    public native int nativeGetVideoDecAvgTime();

    // 供 native 回调创建音频输出（对应 C++ 端 GetMethodID("newAudioTrack")）。
    // native 传入的语义（对应原始 MoboPlayer MoboVideoView.newAudioTrack(int rate,int channels,int bufSize)）：
    //   rate     = 采样率
    //   channels = 声道数（1/2）
    //   bufSize  = 未使用的第三个 int（原始实现直接忽略，音频格式固定 PCM16）
    // 音频数据由 native 直接 write 到返回的 AudioTrack。
    public AudioTrack newAudioTrack(int rate, int channels, int ignored) {
        android.util.Log.d("CMPlayer", "newAudioTrack rate=" + rate + " channels=" + channels
                + " ignored=" + ignored);
        int maxSampleRate;
        try {
            Method m = AudioTrack.class.getMethod(
                    "getNativeOutputSampleRate", int.class);
            maxSampleRate = (Integer) m.invoke(null, AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            maxSampleRate = 44100;
        }
        android.util.Log.d("CMPlayer", "newAudioTrack nativeSampleRate=" + maxSampleRate
                + " bufferSize=" + ignored);
        // native 端会按传入的 rate 写入 PCM；直接用该 rate 创建 AudioTrack，
        // 不做 clamp + setPlaybackRate 的二次调整（速率不一致会造成爆音/变调）。
        int sampleRate = rate;
        if (sampleRate <= 0) {
            sampleRate = maxSampleRate;
        }
        int config = (channels == 1) ? AudioFormat.CHANNEL_CONFIGURATION_MONO
                : AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        // native 传的 ignored 即期望的音频缓冲字节数（= rate*channels*2 的秒缓冲），
        // 用它做 AudioTrack buffer，避免 native 大块写入 + getMinBufferSize 小 buffer
        // 导致溢出/underrun 爆音。无效时回退到 getMinBufferSize。
        int bufferSize = ignored;
        if (bufferSize <= 0) {
            bufferSize = AudioTrack.getMinBufferSize(sampleRate, config,
                    AudioFormat.ENCODING_PCM_16BIT);
        }
        AudioTrack track = null;
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, config,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize,
                    AudioTrack.MODE_STREAM);
        } catch (Exception e) {
            int b = AudioTrack.getMinBufferSize(maxSampleRate, config,
                    AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, maxSampleRate, config,
                    AudioFormat.ENCODING_PCM_16BIT, b,
                    AudioTrack.MODE_STREAM);
        }
        track.play();
        mAudioTrack = track;
        return track;
    }
}
