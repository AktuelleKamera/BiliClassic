package tv.biliclassic;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

import tv.biliclassic.util.LocalStreamProxy;

/**
 * 外部播放器（系统播放器/第三方 App）在线播放用本地代理持有者。
 *
 * B 站 CDN 防盗链需要 Referer/Cookie/UA 请求头，但通过 Intent 跳转的外部播放器
 * 无法携带请求头。因此在跳转前启动 LocalStreamProxy（带请求头转发），把代理地址
 * 传给外部播放器。BiliPlayerActivity 跳转后会 finish，代理必须由本 Service 持有，
 * 否则会随 Activity 销毁。空闲（无活动连接）超时后自动停止，避免端口泄漏。
 */
public class ProxyStreamService extends Service {

    private static final String ACTION_START = "tv.biliclassic.action.START_PROXY";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_AUDIO_URL = "audio_url";
    private static final String EXTRA_DURATION_MS = "duration_ms";
    private static final String EXTRA_HEADERS = "headers";

    private static final long IDLE_TIMEOUT_MS = 15 * 60 * 1000; // 15 分钟无连接自动停止

    private static LocalStreamProxy sProxy;
    private static String sProxyKey;
    private static Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sIdleRunnable;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            Map<String, String> headers = new HashMap<String, String>();
            try {
                java.io.Serializable ser = intent.getSerializableExtra(EXTRA_HEADERS);
                if (ser instanceof Map) {
                    Map<?, ?> raw = (Map<?, ?>) ser;
                    for (Map.Entry<?, ?> e : raw.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null) {
                            headers.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                        }
                    }
                }
            } catch (Throwable t) {
            }
            // 代理已在 startProxyForExternal 中启动，此处仅重置空闲计时
            scheduleIdleStop();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopProxy();
        super.onDestroy();
    }

    /**
     * 启动代理并返回可传给外部播放器的本地 URL；失败返回原始 URL。
     */
    public static String startProxyForExternal(Context context, String remoteUrl,
                                               Map<String, String> headers) {
        return startProxyForExternal(context, remoteUrl, null, 0, headers);
    }

    public static String startProxyForExternal(Context context, String remoteUrl,
                                               String audioUrl, long durationMs,
                                               Map<String, String> headers) {
        if (remoteUrl == null) {
            return null;
        }
        String key = remoteUrl + "\n" + (audioUrl == null ? "" : audioUrl)
                + "\n" + durationMs;
        if (sProxy != null) {
            if (key.equals(sProxyKey)) {
                return sProxy.getLocalUrl();
            }
            stopProxy();
        }
        LocalStreamProxy proxy = new LocalStreamProxy(remoteUrl, audioUrl, durationMs, headers);
        try {
            String localUrl = proxy.start();
            sProxy = proxy;
            sProxyKey = key;
            Intent svc = new Intent(context, ProxyStreamService.class);
            svc.setAction(ACTION_START);
            svc.putExtra(EXTRA_URL, remoteUrl);
            svc.putExtra(EXTRA_AUDIO_URL, audioUrl);
            svc.putExtra(EXTRA_DURATION_MS, durationMs);
            HashMap<String, String> headerCopy = new HashMap<String, String>();
            if (headers != null) {
                headerCopy.putAll(headers);
            }
            svc.putExtra(EXTRA_HEADERS, headerCopy);
            try {
                context.startService(svc);
            } catch (Exception e) {
            }
            scheduleIdleStop();
            return localUrl;
        } catch (Exception e) {
            try { proxy.stop(); } catch (Throwable t) {}
            return remoteUrl;
        }
    }

    /**
     * 主动停止（可被其他页面调用清理）。
     */
    public static void stopProxyForExternal(Context context) {
        stopProxy();
        if (context != null) {
            try {
                context.stopService(new Intent(context, ProxyStreamService.class));
            } catch (Exception e) {
            }
        }
    }

    private static void stopProxy() {
        if (sHandler != null) {
            sHandler.removeCallbacks(sIdleRunnable);
        }
        if (sProxy != null) {
            try { sProxy.stop(); } catch (Throwable t) {}
            sProxy = null;
        }
        sProxyKey = null;
    }

    private static void scheduleIdleStop() {
        if (sHandler == null) return;
        sHandler.removeCallbacks(sIdleRunnable);
        sIdleRunnable = new Runnable() {
            public void run() {
                // 空闲超时：停止代理并结束 Service
                stopProxy();
            }
        };
        sHandler.postDelayed(sIdleRunnable, IDLE_TIMEOUT_MS);
    }

    public static boolean isProxyActive() {
        return sProxy != null;
    }
}
