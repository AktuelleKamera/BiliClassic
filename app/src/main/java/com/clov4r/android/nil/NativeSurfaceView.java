package com.clov4r.android.nil;

import android.view.Surface;

/**
 * 软解渲染绑定（对应 libcmplayer_*.so 导出的
 * Java_com_clov4r_android_nil_NativeSurfaceView_* 符号）。
 * native 端通过 Surface.lock/unlockAndPost 直接画帧，无需 Java onDraw。
 * 注意：必须与原始 MoboPlayer 的声明完全一致（无多余字段），
 * 否则 native 端 GetFieldID 语义会变化导致 surface 绑定异常。
 */
public class NativeSurfaceView {

    public static native int getVideoWidth();

    public static native int getVideoHeight();

    public static native void setSurfaceChanged(Surface surface, int width, int height);
}
