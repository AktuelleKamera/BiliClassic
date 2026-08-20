-keepattributes *Annotation*

# Player (IjkPlayer): JNI native methods
-keep class tv.danmaku.ijk.media.player.IjkMediaPlayer {
    native <methods>;
}
-keepclassmembers class tv.danmaku.ijk.media.player.IjkMediaPlayer {
    @tv.danmaku.ijk.media.player.annotations.AccessedByNative <fields>;
    @tv.danmaku.ijk.media.player.annotations.CalledByNative <methods>;
}
-keep class tv.danmaku.ijk.media.player.ffmpeg.FFmpegApi {
    native <methods>;
}
-keep @interface tv.danmaku.ijk.media.player.annotations.*

# Player: public API
-keep class tv.danmaku.ijk.media.player.** { public protected *; }

# Danmaku: JNI utilities
-keep class tv.cjump.jni.** { *; }

# Danmaku: public API
-keep class master.flame.danmaku.** { public protected *; }
