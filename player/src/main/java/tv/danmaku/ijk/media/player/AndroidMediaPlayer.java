/*
 * Copyright (C) 2006 Bilibili
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2013 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;

import tv.danmaku.ijk.media.player.misc.AndroidTrackInfo;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.pragma.DebugLog;

public class AndroidMediaPlayer extends AbstractMediaPlayer {
    private final MediaPlayer mInternalMediaPlayer;
    private String mDataSource;

    private final Object mInitLock = new Object();
    private boolean mIsReleased;

    private static MediaInfo sMediaInfo;

    public AndroidMediaPlayer() {
        synchronized (mInitLock) {
            mInternalMediaPlayer = new MediaPlayer();
        }
        mInternalMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        attachInternalListeners();
    }

    public MediaPlayer getInternalMediaPlayer() {
        return mInternalMediaPlayer;
    }

    @Override
    public void setDisplay(SurfaceHolder sh) {
        synchronized (mInitLock) {
            if (!mIsReleased) {
                mInternalMediaPlayer.setDisplay(sh);
            }
        }
    }

    private static int getSdkInt() {
        try { return Build.VERSION.class.getField("SDK_INT").getInt(null); }
        catch (Exception e) { return Integer.parseInt(Build.VERSION.SDK); }
    }

    @Override
    public void setSurface(Surface surface) {
        // 实测部分老平台（API<=4）没有 MediaPlayer.setSurface(Surface)，
        // 硬失败校验下直接引用会拒载整类；反射调用，方法不存在时静默跳过。
        // 该路径仅服务 TextureView 渲染（API 14+），老平台本就走 SurfaceHolder
        try {
            mInternalMediaPlayer.getClass()
                    .getMethod("setSurface", Surface.class)
                    .invoke(mInternalMediaPlayer, surface);
        } catch (Throwable t) {
        }
    }

    @Override
    public void setDataSource(Context context, Uri uri)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSourceCompat(context, uri, null);
    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSourceCompat(context, uri, headers);
    }

    /**
     * MediaPlayer.setDataSource(Context,Uri[,Map]) 分别是 API 14/8 才加入的方法。
     * Android 2.1 及以下 Dalvik 校验器为硬失败模式：字节码里直接引用会让本类
     * 在安装期 dexopt 就被标记拒绝、实例化时抛 VerifyError，运行时守卫无效，
     * 因此必须走反射；老平台无该重载时退化为 setDataSource(String)——
     * http 渐进式流播放自 API 1 即支持。
     */
    @SuppressWarnings("unchecked")
    private void setDataSourceCompat(Context context, Uri uri, Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        java.lang.reflect.Method m = null;
        if (headers != null) {
            try {
                m = MediaPlayer.class.getMethod("setDataSource",
                        Context.class, Uri.class, Map.class);
            } catch (NoSuchMethodException e) {
                m = null;
            }
        }
        if (m == null) {
            try {
                m = MediaPlayer.class.getMethod("setDataSource", Context.class, Uri.class);
            } catch (NoSuchMethodException e) {
                m = null;
            }
        }
        if (m == null) {
            // 最老平台：直接按字符串路径设置（本地代理 URL / 直链均可）
            mInternalMediaPlayer.setDataSource(uri.toString());
            return;
        }
        try {
            if (headers != null && m.getParameterTypes().length == 3) {
                m.invoke(mInternalMediaPlayer, context, uri, headers);
            } else {
                m.invoke(mInternalMediaPlayer, context, uri);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof IllegalArgumentException) throw (IllegalArgumentException) cause;
            if (cause instanceof SecurityException) throw (SecurityException) cause;
            if (cause instanceof IllegalStateException) throw (IllegalStateException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            // 注意：IOException(Throwable)/IllegalStateException(Throwable) 构造器
            // 是 API 9+ 才有的，老平台上字节码直接引用会再次导致整类被拒，
            // 这里只能用 String 版构造器（API 1 即有）
            throw new IOException(String.valueOf(cause));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e.toString());
        }
    }

    @Override
    public void setDataSource(FileDescriptor fd)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mInternalMediaPlayer.setDataSource(fd);
    }

    @Override
    public void setDataSource(String path) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException {
        mDataSource = path;

        Uri uri = Uri.parse(path);
        String scheme = uri.getScheme();
        if (scheme != null && scheme.length() > 0 && scheme.equalsIgnoreCase("file")) {
            mInternalMediaPlayer.setDataSource(uri.getPath());
        } else {
            mInternalMediaPlayer.setDataSource(path);
        }
    }

    @Override
    public void setDataSource(IMediaDataSource mediaDataSource) {
    }

    @Override
    public String getDataSource() {
        return mDataSource;
    }

    private void releaseMediaDataSource() {
    }

    @Override
    public void prepareAsync() throws IllegalStateException {
        mInternalMediaPlayer.prepareAsync();
    }

    @Override
    public void start() throws IllegalStateException {
        mInternalMediaPlayer.start();
    }

    @Override
    public void stop() throws IllegalStateException {
        mInternalMediaPlayer.stop();
    }

    @Override
    public void pause() throws IllegalStateException {
        mInternalMediaPlayer.pause();
    }

    @Override
    public void setScreenOnWhilePlaying(boolean screenOn) {
        mInternalMediaPlayer.setScreenOnWhilePlaying(screenOn);
    }

    @Override
    public ITrackInfo[] getTrackInfo() {
        return AndroidTrackInfo.fromMediaPlayer(mInternalMediaPlayer);
    }

    @Override
    public int getVideoWidth() {
        return mInternalMediaPlayer.getVideoWidth();
    }

    @Override
    public int getVideoHeight() {
        return mInternalMediaPlayer.getVideoHeight();
    }

    @Override
    public int getVideoSarNum() {
        return 1;
    }

    @Override
    public int getVideoSarDen() {
        return 1;
    }

    @Override
    public boolean isPlaying() {
        try {
            return mInternalMediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            DebugLog.printStackTrace(e);
            return false;
        }
    }

    @Override
    public void seekTo(long msec) throws IllegalStateException {
        if (getSdkInt() >= 26) {
            try {
                MediaPlayer.class.getMethod("seekTo", long.class, int.class)
                        .invoke(mInternalMediaPlayer, msec, 3);
                return;
            } catch (Exception ignored) {
            }
        }
        mInternalMediaPlayer.seekTo((int) msec);
    }

    @Override
    public long getCurrentPosition() {
        try {
            return mInternalMediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            DebugLog.printStackTrace(e);
            return 0;
        }
    }

    @Override
    public long getDuration() {
        try {
            return mInternalMediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            DebugLog.printStackTrace(e);
            return 0;
        }
    }

    @Override
    public void release() {
        mIsReleased = true;
        mInternalMediaPlayer.release();
        releaseMediaDataSource();
        resetListeners();
        attachInternalListeners();
    }

    @Override
    public void reset() {
        try {
            mInternalMediaPlayer.reset();
        } catch (IllegalStateException e) {
            DebugLog.printStackTrace(e);
        }
        releaseMediaDataSource();
        resetListeners();
        attachInternalListeners();
    }

    @Override
    public void setLooping(boolean looping) {
        try {
            MediaPlayer.class.getMethod("setLooping", boolean.class)
                    .invoke(mInternalMediaPlayer, Boolean.valueOf(looping));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isLooping() {
        try {
            Object value = MediaPlayer.class.getMethod("isLooping")
                    .invoke(mInternalMediaPlayer);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        mInternalMediaPlayer.setVolume(leftVolume, rightVolume);
    }

    @Override
    public int getAudioSessionId() {
        // getAudioSessionId 是 API 9+ 方法：硬失败校验平台（2.1 及以下）上
        // 字节码直接引用会导致整类被 dexopt 拒载、实例化时抛 VerifyError，
        // 运行时守卫无效，必须反射；低版本返回 0（无音频会话语义）
        try {
            Object r = MediaPlayer.class.getMethod("getAudioSessionId")
                    .invoke(mInternalMediaPlayer);
            return ((Integer) r).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public MediaInfo getMediaInfo() {
        if (sMediaInfo == null) {
            MediaInfo module = new MediaInfo();

            module.mVideoDecoder = "android";
            module.mVideoDecoderImpl = "HW";

            module.mAudioDecoder = "android";
            module.mAudioDecoderImpl = "HW";

            sMediaInfo = module;
        }

        return sMediaInfo;
    }

    @Override
    public void setLogEnabled(boolean enable) {
    }

    @Override
    public boolean isPlayable() {
        return true;
    }

    /*--------------------
     * misc
     */
    @Override
    public void setWakeMode(Context context, int mode) {
        mInternalMediaPlayer.setWakeMode(context, mode);
    }

    @Override
    public void setAudioStreamType(int streamtype) {
        mInternalMediaPlayer.setAudioStreamType(streamtype);
    }

    @Override
    public void setKeepInBackground(boolean keepInBackground) {
    }

    /*--------------------
     * Listeners adapter
     */
    private void attachInternalListeners() {
        java.lang.reflect.InvocationHandler handler = new java.lang.reflect.InvocationHandler() {
            public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                String name = method.getName();
                try {
                    if ("onPrepared".equals(name)) {
                        notifyOnPrepared();
                        return null;
                    }
                    if ("onCompletion".equals(name)) {
                        notifyOnCompletion();
                        return null;
                    }
                    if ("onBufferingUpdate".equals(name)) {
                        if (args != null && args.length >= 2) {
                            notifyOnBufferingUpdate(((Integer) args[1]).intValue());
                        }
                        return null;
                    }
                    if ("onSeekComplete".equals(name)) {
                        notifyOnSeekComplete();
                        return null;
                    }
                    if ("onVideoSizeChanged".equals(name)) {
                        if (args != null && args.length >= 3) {
                            notifyOnVideoSizeChanged(((Integer) args[1]).intValue(),
                                    ((Integer) args[2]).intValue(), 1, 1);
                        }
                        return null;
                    }
                    if ("onError".equals(name) && args != null && args.length >= 3) {
                        return Boolean.valueOf(notifyOnError(((Integer) args[1]).intValue(),
                                ((Integer) args[2]).intValue()));
                    }
                    if ("onInfo".equals(name) && args != null && args.length >= 3) {
                        return Boolean.valueOf(notifyOnInfo(((Integer) args[1]).intValue(),
                                ((Integer) args[2]).intValue()));
                    }
                } catch (Throwable ignored) {
                }
                return null;
            }
        };
        setListener("android.media.MediaPlayer$OnPreparedListener", "setOnPreparedListener", handler);
        setListener("android.media.MediaPlayer$OnBufferingUpdateListener", "setOnBufferingUpdateListener", handler);
        setListener("android.media.MediaPlayer$OnCompletionListener", "setOnCompletionListener", handler);
        setListener("android.media.MediaPlayer$OnSeekCompleteListener", "setOnSeekCompleteListener", handler);
        setListener("android.media.MediaPlayer$OnVideoSizeChangedListener", "setOnVideoSizeChangedListener", handler);
        setListener("android.media.MediaPlayer$OnErrorListener", "setOnErrorListener", handler);
        setListener("android.media.MediaPlayer$OnInfoListener", "setOnInfoListener", handler);
    }

    private void setListener(String interfaceName, String methodName,
                             java.lang.reflect.InvocationHandler handler) {
        try {
            Class<?> cls = Class.forName(interfaceName);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    cls.getClassLoader(), new Class[]{cls}, handler);
            MediaPlayer.class.getMethod(methodName, cls).invoke(mInternalMediaPlayer, proxy);
        } catch (Throwable ignored) {
        }
    }
}
