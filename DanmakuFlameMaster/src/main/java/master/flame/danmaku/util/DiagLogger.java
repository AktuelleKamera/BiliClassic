package master.flame.danmaku.util;

/**
 * 弹幕引擎诊断日志：静态可配置，默认关闭（避免日志写入影响性能）。
 * 由 App 层注入日志写入实现（写文件），引擎代码不依赖 App 包，仅记录诊断信息。
 */
public final class DiagLogger {

    public interface LogSink {
        void log(String tag, String msg);
    }

    private static volatile boolean sEnabled = false;
    private static volatile LogSink sSink;

    private DiagLogger() {
    }

    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
    }

    public static void setSink(LogSink sink) {
        sSink = sink;
    }

    public static boolean enabled() {
        return sEnabled;
    }

    public static void diag(String tag, String msg) {
        if (sSink == null) {
            return;
        }
        try {
            sSink.log(tag, msg);
        } catch (Throwable t) {
            // 诊断日志写入失败不影响播放
        }
    }
}
