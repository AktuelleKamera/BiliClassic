package tv.biliclassic.player;

import android.content.Intent;

import java.util.Map;

public final class ExternalPlayerExtras {

    private ExternalPlayerExtras() {
    }

    public static void putForLocal(Intent intent, String title, long cid,
                                   int resumePosition, String danmakuPath) {
        if (resumePosition > 0) intent.putExtra("progress", resumePosition);
        if (title != null) intent.putExtra("name", title);
        if (danmakuPath != null && danmakuPath.length() > 0) {
            intent.putExtra("danmaku", danmakuPath);
        } else if (cid > 0) {
            intent.putExtra("danmaku", "https://comment.bilibili.com/" + cid + ".xml");
        }
        intent.putExtra("live_mode", false);
    }

    public static void putForOnline(Intent intent, String title, long cid,
                                    int resumePosition) {
        Map<String, String> headers = PlayerNetworkHeaders.build();
        String cookie = headers.get("Cookie");
        String referer = headers.get("Referer");
        if (cookie != null && cookie.length() > 0) {
            intent.putExtra("cookie", cookie);
            intent.putExtra("referer", referer);
        }
        intent.putExtra("agent", headers.get("User-Agent"));
        if (title != null) intent.putExtra("name", title);
        if (cid > 0) {
            intent.putExtra("danmaku", "https://comment.bilibili.com/" + cid + ".xml");
        }
        intent.putExtra("progress", resumePosition);
        intent.putExtra("live_mode", false);
    }
}
