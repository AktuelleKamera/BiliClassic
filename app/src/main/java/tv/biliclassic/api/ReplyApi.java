package tv.biliclassic.api;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import tv.biliclassic.model.Reply;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class ReplyApi {

    public static final int REPLY_TYPE_VIDEO_CHILD = 0;
    public static final int REPLY_TYPE_VIDEO = 1;
    public static final int REPLY_TYPE_ARTICLE = 12;
    public static final int REPLY_TYPE_DYNAMIC_CHILD = 11;
    public static final int REPLY_TYPE_DYNAMIC = 17;
    public static final String TOP_TIP = "[置顶]";

    public static class ReplyResult {
        public int code;
        public Reply reply;
    }

    /**
     * 评论列表返回结果
     */
    public static class ReplyListResult {
        public int code;
        public String nextPagination;

        public ReplyListResult(int code, String nextPagination) {
            this.code = code;
            this.nextPagination = nextPagination;
        }
    }

    /**
     * 获取评论列表（使用 WBI 签名接口）
     * 获取评论的回复（楼中楼）
     */
    public static ReplyListResult getRepliesLazy(long oid, long rpid, String pagination,
                                                 int type, int sort, List<Reply> replyList)
            throws JSONException, IOException {

        // 获取子回复（楼中楼）
        if (rpid > 0) {
            int pageNumber = 1;
            if (pagination != null && pagination.length() > 0) {
                try {
                    pageNumber = Integer.parseInt(pagination);
                } catch (NumberFormatException e) {
                    pageNumber = 1;
                }
            }

            String url = "https://api.bilibili.com/x/v2/reply/reply?oid=" + oid
                    + "&type=" + type
                    + "&root=" + rpid
                    + "&pn=" + pageNumber;

            JSONObject all = NetWorkUtil.getJson(url);

            if (all.getInt("code") == 0 && !all.isNull("data")) {
                JSONObject data = all.getJSONObject("data");
                JSONObject page = data.optJSONObject("page");

                if (!data.isNull("replies") && data.getJSONArray("replies").length() > 0) {
                    JSONArray replies = data.getJSONArray("replies");
                    analyzeReplyArray(true, replies, replyList);

                    int totalCount = page != null ? page.optInt("count", 0) : 0;
                    int currentPage = page != null ? page.optInt("num", 1) : 1;
                    int size = page != null ? page.optInt("size", 0) : 0;

                    if (replyList.size() >= totalCount || size == 0) {
                        return new ReplyListResult(1, "");
                    } else {
                        return new ReplyListResult(0, String.valueOf(currentPage + 1));
                    }
                } else {
                    return new ReplyListResult(1, "");
                }
            } else {
                return new ReplyListResult(-1, "");
            }
        }

        // 获取根评论（视频评论区），使用 WBI 签名接口
        NetWorkUtil.FormData reqData = new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("type", type)
                .put("oid", oid)
                .put("plat", 1)
                .put("web_location", "1315875")
                .put("mode", sort);

        JSONObject paginationJson = new JSONObject();
        paginationJson.put("offset", (pagination == null || pagination.length() == 0) ? "" : pagination);
        reqData.put("pagination_str", paginationJson.toString());

        String baseUrl = "https://api.bilibili.com/x/v2/reply/wbi/main";
        String unsignedUrl = baseUrl + reqData.toString();
        String signedUrl = ConfInfoApi.signWBI(unsignedUrl);

        JSONObject all = NetWorkUtil.getJson(signedUrl);

        if (all.getInt("code") == 0 && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject cursor = data.getJSONObject("cursor");

            if (!data.isNull("replies") && data.getJSONArray("replies").length() > 0) {
                if (data.has("top_replies") && !data.isNull("top_replies")
                        && cursor.optBoolean("is_begin", false)) {
                    analyzeReplyArray(true, data.getJSONArray("top_replies"), replyList);
                }

                JSONArray replies = data.getJSONArray("replies");
                analyzeReplyArray(true, replies, replyList);

                JSONObject paginationReply = cursor.optJSONObject("pagination_reply");
                String nextOffset = paginationReply == null ? null : paginationReply.optString("next_offset");

                if (cursor.optBoolean("is_end", false) || nextOffset == null || nextOffset.length() == 0) {
                    return new ReplyListResult(1, "");
                } else {
                    return new ReplyListResult(0, nextOffset);
                }
            } else {
                return new ReplyListResult(1, "");
            }
        } else {
            return new ReplyListResult(-1, "");
        }
    }

    private static void analyzeReplyArray(boolean isRoot, JSONArray replies, List<Reply> replyList)
            throws JSONException {
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.getJSONObject(i);
            Reply replyReturn = new Reply(isRoot, reply);
            replyList.add(replyReturn);
        }
    }

    public static ReplyResult sendReplyResult(long oid, long root, long parent, String text, int type)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/add";
        String arg = "oid=" + oid + "&type=" + type
                + (root == 0 ? "" : ("&root=" + root + "&parent=" + parent))
                + "&message=" + text + "&jsonp=jsonp&csrf="
                + SharedPreferencesUtil.getString("csrf", "");

        String response = NetWorkUtil.post(url, arg, null);
        JSONObject result = new JSONObject(response);
        Log.d("ReplyApi", "发送回复: " + result.toString());

        ReplyResult replyResult = new ReplyResult();
        replyResult.code = result.getInt("code");
        replyResult.reply = null;

        if (result.has("data") && !result.isNull("data")
                && result.getJSONObject("data").has("reply")
                && !result.getJSONObject("data").isNull("reply")) {
            JSONObject reply = result.getJSONObject("data").getJSONObject("reply");
            replyResult.reply = new Reply(root != 0, reply);
        }
        return replyResult;
    }

    /**
     * 上传评论图片
     * @return JSON string: {"image_url":"http://...","image_width":...,"image_height":...,"img_size":...} or null on failure
     */
    public static String uploadReplyImage(long oid, byte[] imageData, String fileName) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs";
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        String cookies = SharedPreferencesUtil.getString("cookies", "");

        String boundary = "----WebKitFormBoundary" + Long.toHexString(System.currentTimeMillis());
        String lineEnd = "\r\n";

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            // file_up
            baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
            baos.write(("Content-Disposition: form-data; name=\"file_up\"; filename=\"" + fileName + "\"" + lineEnd).getBytes("UTF-8"));
            baos.write(("Content-Type: image/jpeg" + lineEnd + lineEnd).getBytes("UTF-8"));
            baos.write(imageData);
            baos.write(lineEnd.getBytes("UTF-8"));

            // category
            baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
            baos.write(("Content-Disposition: form-data; name=\"category\"" + lineEnd + lineEnd).getBytes("UTF-8"));
            baos.write(("daily" + lineEnd).getBytes("UTF-8"));

            // biz
            baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
            baos.write(("Content-Disposition: form-data; name=\"biz\"" + lineEnd + lineEnd).getBytes("UTF-8"));
            baos.write(("new_dyn" + lineEnd).getBytes("UTF-8"));

            // csrf
            baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
            baos.write(("Content-Disposition: form-data; name=\"csrf\"" + lineEnd + lineEnd).getBytes("UTF-8"));
            baos.write((csrf + lineEnd).getBytes("UTF-8"));

            // end
            baos.write(("--" + boundary + "--" + lineEnd).getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) { return null; }

        byte[] postData = baos.toByteArray();
        java.io.ByteArrayOutputStream baos2 = new java.io.ByteArrayOutputStream();
        java.io.InputStream is = null;

        try {
            java.net.URL uploadUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uploadUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Content-Length", String.valueOf(postData.length));
            conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("Origin", "https://www.bilibili.com");
            if (cookies != null && cookies.length() > 0) {
                conn.setRequestProperty("Cookie", cookies);
            }

            java.io.OutputStream os = conn.getOutputStream();
            os.write(postData);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                is = conn.getErrorStream();
            } else {
                is = conn.getInputStream();
            }
            if (is != null) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) {
                    baos2.write(buf, 0, len);
                }
            }
            conn.disconnect();

            String responseText = baos2.toString("UTF-8");
            JSONObject json = new JSONObject(responseText);
            if (json.getInt("code") == 0) {
                JSONObject data = json.getJSONObject("data");
                return data.toString();
            }
            return null;
        } catch (Exception e) {
            Log.e("ReplyApi", "上传图片失败: " + e.getMessage());
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) {}
            try { baos2.close(); } catch (Exception e) {}
        }
    }

    public static int likeComment(long oid, long rpid, int type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/action";
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        String arg = "type=" + type + "&oid=" + oid + "&rpid=" + rpid + "&action=1&csrf=" + csrf;
        String response = NetWorkUtil.post(url, arg, null);
        JSONObject json = new JSONObject(response);
        return json.getInt("code");
    }

    public static int unlikeComment(long oid, long rpid, int type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/action";
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        String arg = "type=" + type + "&oid=" + oid + "&rpid=" + rpid + "&action=0&csrf=" + csrf;
        String response = NetWorkUtil.post(url, arg, null);
        JSONObject json = new JSONObject(response);
        return json.getInt("code");
    }

    public static int deleteComment(long oid, long rpid, int type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/del";
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        String arg = "type=" + type + "&oid=" + oid + "&rpid=" + rpid + "&csrf=" + csrf;
        String response = NetWorkUtil.post(url, arg, null);
        JSONObject json = new JSONObject(response);
        return json.getInt("code");
    }
}