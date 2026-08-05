package tv.biliclassic.util;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能日志：记录整帧 / 每次 measure / layout / draw 的耗时，定位主线程卡顿。
 * 与 BuildType 无关，Release 下默认开启（日常即 Release）。
 * 开关：连点主界面 logo 3 次切换，状态持久化到 SharedPreferences(key=perf_log)。
 * Logcat tag = PerfLog。
 * 带阈值与限流：视图级耗时 >=5ms、整帧 >=50ms 才打印，同一 key 最多 1 条 / 800ms。
 *
 * 注意：ViewTreeObserver 是整窗共享一个，OnPreDraw 每帧全局触发一次，
 * 因此整帧耗时只能用"一个全局监听器"测，不能每个页面各挂（会重复且 tag 失真）。
 */
public class PerfLog {

    private static final String TAG = "PerfLog";
    private static final String KEY_ENABLED = "perf_log";
    private static final long RATE_LIMIT_MS = 800;
    private static final long VIEW_THRESHOLD_NS = 5 * 1000000L;
    private static final long FRAME_THRESHOLD_NS = 50 * 1000000L;

    private static boolean sEnabled = true;
    private static boolean sGlobalWatcherAttached = false;
    private static String sPage = "";
    private static Map<String, Long> sLastLog = new HashMap<String, Long>();

    private PerfLog() {
    }

    /** 在 MainActivity.onCreate 调用：读取持久化开关（默认开） */
    public static void init() {
        sEnabled = SharedPreferencesUtil.getBoolean(KEY_ENABLED, true);
        Log.i(TAG, sEnabled ? "PerfLog 已启用" : "PerfLog 未启用");
    }

    public static boolean enabled() {
        return sEnabled;
    }

    /** 切换开关并持久化，返回新状态 */
    public static boolean toggle() {
        sEnabled = !sEnabled;
        SharedPreferencesUtil.putBoolean(KEY_ENABLED, sEnabled);
        Log.i(TAG, sEnabled ? "PerfLog 已开启" : "PerfLog 已关闭");
        return sEnabled;
    }

    /** 标记当前可见页面（ViewPager setPrimaryItem 时调用），帧日志带上页签 */
    public static void setPage(String page) {
        sPage = (page == null) ? "" : page;
    }

    /** 记录一次普通耗时（measure/layout/draw 等），超过 5ms 且限流内打印 */
    public static void record(String tag, String label, long durNanos) {
        if (!sEnabled) return;
        if (durNanos < VIEW_THRESHOLD_NS) return;
        if (rateLimited(tag + "|" + label)) return;
        Log.w(TAG, tag + " " + label + "=" + (durNanos / 1000000) + "ms");
    }

    /** 记录一次整帧耗时（超过 50ms 视为掉帧） */
    public static void recordFrame(String tag, String label, long durNanos) {
        if (!sEnabled) return;
        if (durNanos < FRAME_THRESHOLD_NS) return;
        if (rateLimited(tag + "|" + label)) return;
        Log.w(TAG, tag + " " + label + "=" + (durNanos / 1000000) + "ms");
    }

    private static boolean rateLimited(String key) {
        long now = System.currentTimeMillis();
        synchronized (sLastLog) {
            Long last = sLastLog.get(key);
            if (last != null && now - last < RATE_LIMIT_MS) {
                return true;
            }
            sLastLog.put(key, now);
            return false;
        }
    }

    /**
     * 全局整帧监听（整个窗口一个）：在 MainActivity setContentView 后调用一次。
     * 通过 OnPreDraw 测上一帧整体耗时（上一帧 draw + 本帧 measure/layout）。
     * 必须在 view 挂到窗口后再 add（onCreateView 里 view 尚未 attach，
     * 此时拿到的 ViewTreeObserver 会在 attach 时被替换）。
     */
    public static void attachGlobalFrameWatcher(final View root) {
        if (root == null || sGlobalWatcherAttached) return;
        sGlobalWatcherAttached = true;
        root.post(new Runnable() {
            @Override
            public void run() {
                ViewTreeObserver obs = root.getViewTreeObserver();
                if (obs == null || !obs.isAlive()) return;
                obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    private long last = System.nanoTime();

                    @Override
                    public boolean onPreDraw() {
                        if (!sEnabled) {
                            last = System.nanoTime();
                            return true;
                        }
                        long now = System.nanoTime();
                        recordFrame(sPage.length() == 0 ? "Window" : sPage, "帧耗时", now - last);
                        last = now;
                        return true;
                    }
                });
            }
        });
    }
}
