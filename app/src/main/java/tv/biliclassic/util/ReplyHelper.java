package tv.biliclassic.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;

import tv.biliclassic.api.ReplyApi;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class ReplyHelper {

    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ReplyCallback {
        void onSuccess(String responseJson);
        void onFailed(String error);
    }

    /**
     * 发送回复
     */
    public static void sendReply(final Context context, final long aid, final long root,
                                 final long parent, final String text, final ReplyCallback callback) {
        // 1000字限制
        if (text == null || text.length() == 0) {
            showToast(context, "回复内容不能为空哦");
            if (callback != null) {
                callback.onFailed("回复内容不能为空哦");
            }
            return;
        }
        if (text.length() > 1000) {
            showToast(context, "回复内容不能超过1000字哦");
            if (callback != null) {
                callback.onFailed("回复内容不能超过1000字哦");
            }
            return;
        }

        long mid = SharedPreferencesUtil.getLong("mid", 0);
        final String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (mid == 0 || cookies == null || cookies.length() == 0) {
            showToast(context, "请先登录后再回复的说~");
            if (callback != null) {
                callback.onFailed("未登录");
            }
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String csrf = extractCsrfFromCookie(cookies);
                    if (csrf != null && csrf.length() > 0) {
                        SharedPreferencesUtil.putString("csrf", csrf);
                    }

                    final String encodedMessage = java.net.URLEncoder.encode(text, "UTF-8");
                    final String url = "https://api.bilibili.com/x/v2/reply/add";
                    final String arg = "oid=" + aid + "&type=1&root=" + root + "&parent=" + parent
                            + "&message=" + encodedMessage + "&jsonp=jsonp&csrf=" + csrf;

                    ArrayList<String> headers = new ArrayList<String>();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");
                    headers.add("Content-Type");
                    headers.add("application/x-www-form-urlencoded");
                    headers.add("Cookie");
                    headers.add(cookies);

                    final String response = NetWorkUtil.post(url, arg, headers);
                    final JSONObject result = new JSONObject(response);
                    final int code = result.optInt("code", -1);
                    final String message = result.optString("message", "");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                showToast(context, "回复发送成功");
                                if (callback != null) {
                                    callback.onSuccess(response);
                                }
                            } else {
                                showToast(context, "发送失败: " + message);
                                if (callback != null) {
                                    callback.onFailed(message);
                                }
                            }
                        }
                    });

                } catch (final Exception e) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showToast(context, "发送失败: " + errorMsg);
                            if (callback != null) {
                                callback.onFailed(errorMsg);
                            }
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 发送回复（带图片）
     * @param picturesJson JSON array string: [{"img_src":"http://...","img_width":W,"img_height":H}]
     */
    public static void sendReplyWithPictures(final Context context, final long aid, final long root,
                                             final long parent, final String text,
                                             final String picturesJson, final ReplyCallback callback) {
        if (text == null || text.length() == 0) {
            showToast(context, "回复内容不能为空哦");
            if (callback != null) callback.onFailed("回复内容不能为空哦");
            return;
        }
        if (text.length() > 1000) {
            showToast(context, "回复内容不能超过1000字哦");
            if (callback != null) callback.onFailed("回复内容不能超过1000字哦");
            return;
        }

        long mid = SharedPreferencesUtil.getLong("mid", 0);
        final String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (mid == 0 || cookies == null || cookies.length() == 0) {
            showToast(context, "请先登录后再回复的说~");
            if (callback != null) callback.onFailed("未登录");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String csrf = extractCsrfFromCookie(cookies);
                    if (csrf != null && csrf.length() > 0) {
                        SharedPreferencesUtil.putString("csrf", csrf);
                    }

                    final String encodedMessage = java.net.URLEncoder.encode(text, "UTF-8");
                    String pictureParam = "";
                    if (picturesJson != null && picturesJson.length() > 0) {
                        pictureParam = "&pictures=" + java.net.URLEncoder.encode(picturesJson, "UTF-8");
                    }
                    final String url = "https://api.bilibili.com/x/v2/reply/add";
                    final String arg = "oid=" + aid + "&type=1&root=" + root + "&parent=" + parent
                            + "&message=" + encodedMessage + pictureParam + "&jsonp=jsonp&csrf=" + csrf;

                    ArrayList<String> headers = new ArrayList<String>();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");
                    headers.add("Content-Type");
                    headers.add("application/x-www-form-urlencoded");
                    headers.add("Cookie");
                    headers.add(cookies);

                    final String response = NetWorkUtil.post(url, arg, headers);
                    final JSONObject result = new JSONObject(response);
                    final int code = result.optInt("code", -1);
                    final String message = result.optString("message", "");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                showToast(context, "回复发送成功");
                                if (callback != null) callback.onSuccess(response);
                            } else {
                                showToast(context, "发送失败: " + message);
                                if (callback != null) callback.onFailed(message);
                            }
                        }
                    });

                } catch (final Exception e) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showToast(context, "发送失败: " + errorMsg);
                            if (callback != null) callback.onFailed(errorMsg);
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static String extractCsrfFromCookie(String cookie) {
        if (cookie == null || cookie.length() == 0) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("bili_jct=([a-f0-9]+)");
        java.util.regex.Matcher m = p.matcher(cookie);
        if (m.find()) return m.group(1);
        return null;
    }

    private static void showToast(final Context context, final String msg) {
        if (context != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}