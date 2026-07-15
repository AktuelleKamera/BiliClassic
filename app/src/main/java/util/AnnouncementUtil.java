package tv.biliclassic.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tv.biliclassic.BuildConfig;

/**
 * 公告工具类
 * 兼容 Android 1.6 (API 4) 及以上
 */
public class AnnouncementUtil {

    private static final String SP_NAME = "announcement";
    private static final String KEY_SHOWN_PREFIX = "shown_";
    private static final String KEY_DISMISSED_PREFIX = "dismissed_";

    private static Handler sHandler = new Handler(Looper.getMainLooper());

    public interface AnnouncementCallback {
        void onSuccess(Announcement announcement);
        void onFailed(String error);
    }

    /**
     * 多公告回调接口
     */
    public interface MultipleAnnouncementCallback {
        void onSuccess(List<Announcement> announcements);
        void onFailed(String error);
    }

    public static class Announcement {
        public String id;
        public String title;
        public String content;
        public String version;
        public int minSdk;
        public String url;
        public String buttonText;
        public boolean showOnce;
        public boolean forceShow;
        public String imageUrl;
        public int priority;  // 优先级，数字越小越先显示

        public boolean isExpired(Context context) {
            if (version == null || version.length() == 0) {
                return false;
            }
            return compareVersion(getVersionName(context), version) < 0;
        }

        public boolean isSdkSupported() {
            return android.os.Build.VERSION.SDK_INT >= minSdk;
        }

        public String getDisplayContent() {
            if (url == null || url.length() == 0) {
                return content;
            }
            return content + "\n\n" + url;
        }

        public boolean shouldShow(Context context) {
            if (!isSdkSupported() || isExpired(context)) {
                return false;
            }
            if (showOnce && isAnnouncementShown(context, id)) {
                return false;
            }
            if (isAnnouncementDismissed(context, id)) {
                return false;
            }
            return true;
        }
    }

    /**
     * 检查并显示单个公告（兼容旧接口）
     */
    public static void checkAnnouncement(final Context context, final AnnouncementCallback callback) {
        checkMultipleAnnouncements(context, new MultipleAnnouncementCallback() {
            @Override
            public void onSuccess(List<Announcement> announcements) {
                if (announcements != null && announcements.size() > 0) {
                    // 只返回第一个公告
                    if (callback != null) {
                        callback.onSuccess(announcements.get(0));
                    }
                } else {
                    if (callback != null) {
                        callback.onFailed("没有可显示的公告");
                    }
                }
            }

            @Override
            public void onFailed(String error) {
                if (callback != null) {
                    callback.onFailed(error);
                }
            }
        });
    }

    /**
     * 检查并显示多个公告
     */
    public static void checkMultipleAnnouncements(final Context context, final MultipleAnnouncementCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String json = fetchAnnouncement(context);
                    if (json == null || json.length() == 0) {
                        if (callback != null) {
                            sHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFailed("获取公告失败");
                                }
                            });
                        }
                        return;
                    }

                    final List<Announcement> validAnnouncements = parseAnnouncementList(json, context);
                    if (validAnnouncements == null || validAnnouncements.size() == 0) {
                        if (callback != null) {
                            sHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFailed("没有可显示的公告");
                                }
                            });
                        }
                        return;
                    }

                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // 按优先级排序（优先级小的先显示）
                            java.util.Collections.sort(validAnnouncements,
                                    new java.util.Comparator<Announcement>() {
                                        @Override
                                        public int compare(Announcement a, Announcement b) {
                                            return a.priority - b.priority;
                                        }
                                    });
                            // 显示公告队列
                            showAnnouncementQueue(context, validAnnouncements, 0);
                            if (callback != null) {
                                callback.onSuccess(validAnnouncements);
                            }
                        }
                    });

                } catch (final Exception e) {
                    if (callback != null) {
                        sHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFailed(e.getMessage());
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * 递归显示公告队列
     */
    private static void showAnnouncementQueue(final Context context,
                                              final List<Announcement> announcements, final int index) {
        if (index >= announcements.size()) {
            return;
        }

        final Announcement announcement = announcements.get(index);

        // 检查是否应该显示
        if (!announcement.shouldShow(context)) {
            // 跳过这个公告，显示下一个
            showAnnouncementQueue(context, announcements, index + 1);
            return;
        }

        // 显示公告对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(announcement.title);
        builder.setMessage(announcement.getDisplayContent());

        if (announcement.url != null && announcement.url.length() > 0) {
            builder.setNeutralButton("查看详情", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(announcement.url)));
                }
            });
        }

        builder.setPositiveButton(announcement.buttonText, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (announcement.showOnce) {
                    markAnnouncementShown(context, announcement.id);
                }
                // 显示下一个公告
                showAnnouncementQueue(context, announcements, index + 1);
            }
        });

        if (!announcement.forceShow) {
            builder.setNegativeButton("不再显示", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    markAnnouncementDismissed(context, announcement.id);
                    // 显示下一个公告
                    showAnnouncementQueue(context, announcements, index + 1);
                }
            });
        }

        builder.setCancelable(announcement.forceShow);
        builder.show();
    }

    private static String fetchAnnouncement(Context context) throws Exception {
        String url = "http://www.biliclassic.cn/api/announcement.json";
        HttpURLConnection conn = null;
        try {
            HttpURLConnection.setFollowRedirects(true);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "BiliClassic/" + getVersionName(context));

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析公告列表（兼容单个公告和公告数组）
     */
    private static List<Announcement> parseAnnouncementList(String json, Context context) throws Exception {
        List<Announcement> result = new ArrayList<Announcement>();

        JSONObject obj = new JSONObject(json);

        // 检查是否是单个公告对象（兼容旧格式）
        if (obj.has("title") || obj.has("content")) {
            if (obj.optBoolean("enabled", true)) {
                Announcement announcement = parseSingleAnnouncement(obj);
                if (announcement != null && announcement.shouldShow(context)) {
                    result.add(announcement);
                }
            }
            return result;
        }

        // 检查是否是数组
        if (obj.has("announcements")) {
            JSONArray arr = obj.getJSONArray("announcements");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                if (item.optBoolean("enabled", true)) {
                    Announcement announcement = parseSingleAnnouncement(item);
                    if (announcement != null && announcement.shouldShow(context)) {
                        result.add(announcement);
                    }
                }
            }
        }

        return result;
    }

    private static Announcement parseSingleAnnouncement(JSONObject obj) throws Exception {
        // 日期范围检查
        String startDate = obj.optString("start_date", "");
        String endDate = obj.optString("end_date", "");
        if (startDate != null && startDate.length() > 0 && endDate != null && endDate.length() > 0) {
            long current = System.currentTimeMillis();
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                long start = sdf.parse(startDate).getTime();
                long end = sdf.parse(endDate).getTime();
                if (current < start || current > end) {
                    return null;
                }
            } catch (Exception e) {
                // 日期解析失败，跳过
            }
        }

        int minVerCode = obj.optInt("min_version_code", -1);
        if (minVerCode >= 0) {
            if (BuildConfig.VERSION_CODE < minVerCode) return null;
        } else {
            String minVersion = obj.optString("min_version", "");
            if (minVersion != null && minVersion.length() > 0) {
                if (compareVersion(getVersionName(null), minVersion) < 0) return null;
            }
        }
        int maxVerCode = obj.optInt("max_version_code", -1);
        if (maxVerCode >= 0) {
            if (BuildConfig.VERSION_CODE > maxVerCode) return null;
        } else {
            String maxVersion = obj.optString("max_version", "");
            if (maxVersion != null && maxVersion.length() > 0) {
                if (compareVersion(getVersionName(null), maxVersion) > 0) return null;
            }
        }

        Announcement announcement = new Announcement();
        announcement.id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
        announcement.title = obj.optString("title", "公告");
        announcement.content = obj.optString("content", "");
        announcement.version = obj.optString("version", "");
        announcement.minSdk = obj.optInt("min_sdk", 4);
        announcement.url = obj.optString("url", "");
        announcement.buttonText = obj.optString("button_text", "知道了");
        announcement.showOnce = obj.optBoolean("show_once", true);
        announcement.forceShow = obj.optBoolean("force_show", false);
        announcement.imageUrl = obj.optString("image_url", "");
        announcement.priority = obj.optInt("priority", 100);

        if (announcement.id == null || announcement.id.length() == 0) {
            announcement.id = "announcement_" + System.currentTimeMillis();
        }

        return announcement;
    }

    private static void markAnnouncementShown(Context context, String id) {
        getPrefs(context).edit().putBoolean(KEY_SHOWN_PREFIX + id, true).commit();
    }

    private static boolean isAnnouncementShown(Context context, String id) {
        return getPrefs(context).getBoolean(KEY_SHOWN_PREFIX + id, false);
    }

    private static void markAnnouncementDismissed(Context context, String id) {
        getPrefs(context).edit().putBoolean(KEY_DISMISSED_PREFIX + id, true).commit();
    }

    private static boolean isAnnouncementDismissed(Context context, String id) {
        return getPrefs(context).getBoolean(KEY_DISMISSED_PREFIX + id, false);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    private static String getVersionName(Context context) {
        return BuildConfig.VERSION_NAME;
    }

    private static int compareVersion(String v1, String v2) {
        if (v1 == null || v2 == null) return 0;
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = parts1.length;
        if (parts2.length > len) {
            len = parts2.length;
        }
        for (int i = 0; i < len; i++) {
            int n1 = 0;
            int n2 = 0;
            try {
                if (i < parts1.length) {
                    n1 = Integer.parseInt(parts1[i]);
                }
            } catch (NumberFormatException e) {
                // 忽略
            }
            try {
                if (i < parts2.length) {
                    n2 = Integer.parseInt(parts2[i]);
                }
            } catch (NumberFormatException e) {
                // 忽略
            }
            if (n1 != n2) {
                return n1 - n2;
            }
        }
        return 0;
    }
}