package tv.biliclassic;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.Locale;

import tv.biliclassic.api.BangumiApi;
import tv.biliclassic.util.NetWorkUtil;

/**
 * 外部链接分发页
 */
public class LinkActivity extends BaseActivity {

    private static final String TAG = "LinkActivity";

    private static final int MAX_DEPTH = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent() != null ? getIntent().getData() : null;
        if (uri == null) {
            goHome();
            return;
        }
        route(uri, 0);
    }

    private void route(Uri uri, int depth) {
        if (isFinishing()) {
            return;
        }
        if (depth > MAX_DEPTH) {
            goHome();
            return;
        }
        String scheme = lower(uri.getScheme());
        boolean custom = "bilibili".equals(scheme);
        if (!custom && !"http".equals(scheme) && !"https".equals(scheme)) {
            goHome();
            return;
        }

        long epid = epidOf(uri);
        if (epid > 0) {
            resolveEpid(uri, epid);
            return;
        }

        if (custom) {
            startTarget(fromCustom(uri));
            return;
        }

        Intent target = fromWeb(uri);
        if (target != null) {
            startTarget(target);
            return;
        }
        if (isShortHost(lower(uri.getHost()))) {
            resolveShort(uri, depth);
            return;
        }
        openInWebView(uri);
    }

    // 自定义协议分发

    private Intent fromCustom(Uri uri) {
        String host = lower(uri.getHost());
        if (host == null) {
            host = "";
        }
        List segs = uri.getPathSegments();
        String first = segs.size() > 0 ? (String) segs.get(0) : "";

        if ("video".equals(host)) {
            Intent i = videoIntent(first);
            if (i != null) {
                return i;
            }
            String bvid = query(uri, "bvid");
            if (bvid == null || bvid.length() == 0) {
                bvid = query(uri, "bvid_id");
            }
            if (bvid != null && bvid.length() > 0) {
                Intent r = new Intent(this, VideoDetailActivity.class);
                r.putExtra("bvid", bvid);
                return r;
            }
            long aid = parseLong(query(uri, "aid"));
            if (aid <= 0) {
                aid = parseLong(query(uri, "avid"));
            }
            if (aid > 0) {
                Intent r = new Intent(this, VideoDetailActivity.class);
                r.putExtra("aid", aid);
                return r;
            }
            return null;
        }
        if ("space".equals(host)) {
            long mid = parseLong(first);
            return mid > 0 ? userIntent(mid) : null;
        }
        if ("article".equals(host)) {
            long cv = parseId(first, "cv");
            return cv > 0 ? articleIntent(cv) : null;
        }
        if ("live".equals(host)) {
            long room = parseLong(first);
            return room > 0 ? liveIntent(room) : null;
        }
        if ("search".equals(host)) {
            String kw = query(uri, "keyword");
            if (kw == null || kw.length() == 0) {
                kw = first;
            }
            return kw != null && kw.length() > 0 ? searchIntent(kw) : null;
        }
        if ("bangumi".equals(host)) {
            String token = lastSeg(segs);
            return seasonFromToken(token);
        }
        if ("dynamic".equals(host) || "feed".equals(host)) {
            long id = parseLong(lastSeg(segs));
            return id > 0 ? dynamicIntent(id) : null;
        }
        return null;
    }

    // 网页链接分发

    private Intent fromWeb(Uri uri) {
        String host = lower(uri.getHost());
        String path = uri.getPath();
        if (path == null) {
            path = "";
        }
        List segs = uri.getPathSegments();
        String first = segs.size() > 0 ? (String) segs.get(0) : "";

        if (host != null && (host.equals("space.bilibili.com")
                || host.endsWith(".space.bilibili.com"))) {
            long mid = parseLong(first);
            return mid > 0 ? userIntent(mid) : null;
        }
        if (host != null && host.startsWith("live.bilibili.com")) {
            long room = parseLong(first);
            return room > 0 ? liveIntent(room) : null;
        }
        if (host != null && (host.equals("t.bilibili.com")
                || host.endsWith(".t.bilibili.com"))) {
            long id = parseLong(first);
            return id > 0 ? dynamicIntent(id) : null;
        }
        if (host != null && host.startsWith("search.bilibili.com")) {
            String kw = query(uri, "keyword");
            if (kw == null || kw.length() == 0) {
                kw = first;
            }
            return kw != null && kw.length() > 0 ? searchIntent(kw) : null;
        }
        if (path.startsWith("/video/") && segs.size() > 1) {
            return videoIntent((String) segs.get(1));
        }
        if (path.startsWith("/read/") && segs.size() > 1) {
            long cv = parseId((String) segs.get(1), "cv");
            return cv > 0 ? articleIntent(cv) : null;
        }
        if (path.startsWith("/bangumi/play/") && segs.size() > 1) {
            return seasonFromToken(lastSeg(segs));
        }
        if (path.startsWith("/dynamic/")) {
            long id = parseLong(lastSeg(segs));
            return id > 0 ? dynamicIntent(id) : null;
        }
        if (path.startsWith("/medialist/detail/")) {
            long id = parseLong(lastSeg(segs));
            return id > 0 ? dynamicIntent(id) : null;
        }
        return null;
    }

    // 短链与 ep 异步解析

    private void resolveShort(final Uri uri, final int depth) {
        new Thread(new Runnable() {
            public void run() {
                String result = null;
                try {
                    result = NetWorkUtil.resolveFinalUrl(uri.toString());
                } catch (Exception e) {
                    Log.w(TAG, "短链解析失败 " + e);
                }
                final String fin = result;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        if (fin == null || fin.length() == 0) {
                            openInWebView(uri);
                            return;
                        }
                        route(Uri.parse(fin), depth + 1);
                    }
                });
            }
        }).start();
    }

    private void resolveEpid(final Uri uri, final long epid) {
        new Thread(new Runnable() {
            public void run() {
                long sid = 0;
                try {
                    sid = BangumiApi.getSeasonIdFromEpid(epid);
                } catch (Exception e) {
                    Log.w(TAG, "番剧解析失败 " + e);
                }
                final long season = sid;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        if (season > 0) {
                            startTarget(seasonIntent(season));
                        } else {
                            openInWebView(uri);
                        }
                    }
                });
            }
        }).start();
    }

    // 目标页

    private Intent videoIntent(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        Intent i = new Intent(this, VideoDetailActivity.class);
        if (id.startsWith("BV") || id.startsWith("bv")) {
            i.putExtra("bvid", id);
            return i;
        }
        long aid = 0;
        if (id.length() > 2 && id.substring(0, 2).equalsIgnoreCase("av")) {
            aid = parseLong(id.substring(2));
        }
        if (aid <= 0) {
            aid = parseLong(id);
        }
        if (aid <= 0) {
            return null;
        }
        i.putExtra("aid", aid);
        return i;
    }

    private Intent userIntent(long mid) {
        Intent i = new Intent(this, UserProfileActivity.class);
        i.putExtra("mid", mid);
        return i;
    }

    private Intent articleIntent(long cvid) {
        Intent i = new Intent(this, ArticleActivity.class);
        i.putExtra("cvid", cvid);
        return i;
    }

    private Intent liveIntent(long roomId) {
        Intent i = new Intent(this, LiveInfoActivity.class);
        i.putExtra("room_id", roomId);
        return i;
    }

    private Intent dynamicIntent(long id) {
        Intent i = new Intent(this, DynamicDetailActivity.class);
        i.putExtra("id", id);
        return i;
    }

    private Intent searchIntent(String keyword) {
        Intent i = new Intent(this, SearchActivity.class);
        i.putExtra("keyword", keyword);
        return i;
    }

    private Intent seasonIntent(long seasonId) {
        Intent i = new Intent(this, VideoDetailActivity.class);
        i.putExtra("bangumi_season_id", seasonId);
        return i;
    }

    // 结果落地

    private void startTarget(Intent target) {
        if (target == null) {
            goHome();
            return;
        }
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(target);
        } catch (Exception e) {
            Log.w(TAG, "跳转失败 " + e);
            goHome();
            return;
        }
        finish();
    }

    private void openInWebView(Uri uri) {
        Intent i = new Intent(this, WebViewActivity.class);
        i.putExtra("url", uri.toString());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "网页打开失败 " + e);
            goHome();
            return;
        }
        finish();
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "回首页失败 " + e);
        }
        finish();
    }

    // 工具

    private String lower(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.US);
    }

    private String lastSeg(List segs) {
        if (segs == null || segs.size() == 0) {
            return "";
        }
        return (String) segs.get(segs.size() - 1);
    }

    private String query(Uri uri, String key) {
        try {
            return uri.getQueryParameter(key);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isShortHost(String host) {
        if (host == null || host.length() == 0) {
            return false;
        }
        return host.equals("b23.tv") || host.endsWith(".b23.tv")
                || host.equals("b23.com") || host.endsWith(".b23.com")
                || host.equals("bili2233.cn") || host.endsWith(".bili2233.cn");
    }

    private long epidOf(Uri uri) {
        List segs = uri.getPathSegments();
        if (segs.size() < 2) {
            return 0;
        }
        if (!"play".equalsIgnoreCase((String) segs.get(segs.size() - 2))) {
            return 0;
        }
        String token = (String) segs.get(segs.size() - 1);
        if (token.length() <= 2 || !token.substring(0, 2).equalsIgnoreCase("ep")) {
            return 0;
        }
        return parseLong(token.substring(2));
    }

    private Intent seasonFromToken(String token) {
        if (token == null || token.length() <= 2) {
            return null;
        }
        if (token.substring(0, 2).equalsIgnoreCase("ss")) {
            long sid = parseLong(token.substring(2));
            return sid > 0 ? seasonIntent(sid) : null;
        }
        return null;
    }

    private long parseLong(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return 0;
        }
        try {
            return Long.parseLong(s.substring(0, i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseId(String s, String prefix) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        if (s.length() > prefix.length()
                && s.substring(0, prefix.length()).equalsIgnoreCase(prefix)) {
            return parseLong(s.substring(prefix.length()));
        }
        return parseLong(s);
    }
}
