package tv.biliclassic.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class Reply implements Serializable {

    public long rpid;
    public long oid;
    public long root;
    public long parent;
    public boolean forceDelete;
    public String ofBvid = "";
    public String pubTime;
    public long ctime;
    public UserInfo sender;
    public String message;
    public ArrayList<String> pictureList = new ArrayList<String>();
    public int likeCount;
    public boolean upLiked;
    public boolean upReplied;
    public boolean liked;
    public int childCount;
    public boolean isDynamic;
    public ArrayList<Reply> childMsgList = new ArrayList<Reply>();
    public boolean isTop;
    public int replyCount;

    public Reply() {
    }

    public Reply(boolean isRoot, JSONObject replyJson) throws JSONException {
        this.rpid = replyJson.getLong("rpid");
        this.oid = replyJson.getLong("oid");
        this.root = replyJson.getLong("root");
        this.parent = replyJson.getLong("parent");

        if (replyJson.has("member") && !replyJson.isNull("member")) {
            this.sender = new UserInfo(replyJson.getJSONObject("member"));
        }

        JSONObject content = replyJson.getJSONObject("content");
        JSONObject replyCtrl = replyJson.optJSONObject("reply_control");
        long rawCtime = replyJson.getLong("ctime");
        this.ctime = rawCtime;
        long ctimeMs = rawCtime * 1000;

        String time;
        if (replyCtrl != null && System.currentTimeMillis() - ctimeMs < 3 * 24 * 60 * 60 * 1000 && replyCtrl.has("time_desc")) {
            time = replyCtrl.getString("time_desc");
        } else {
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(ctimeMs);
        }

        if (replyCtrl != null && replyCtrl.has("location")) {
            String location = replyCtrl.getString("location");
            if (location != null && location.length() > 5) {
                time = time + " | IP:" + location.substring(5);
            }
        }
        this.pubTime = time;

        if (replyCtrl != null && replyCtrl.has("is_up_top") && replyCtrl.getBoolean("is_up_top")) {
            this.isTop = true;
        }

        String rawMessage = content.getString("message");
        this.message = stripHtml(rawMessage);
        if (this.isTop) {
            this.message = "[置顶]" + this.message;
        }

        this.likeCount = replyJson.getInt("like");
        this.liked = replyJson.getInt("action") == 1;

        if (replyJson.has("up_action") && !replyJson.isNull("up_action")) {
            JSONObject upAction = replyJson.getJSONObject("up_action");
            this.upLiked = upAction.optBoolean("like", false);
            this.upReplied = upAction.optBoolean("reply", false);
        }

        if (isRoot) {
            if (content.has("pictures") && !content.isNull("pictures")) {
                JSONArray pictures = content.getJSONArray("pictures");
                for (int j = 0; j < pictures.length(); j++) {
                    JSONObject picture = pictures.getJSONObject(j);
                    String imgSrc = picture.optString("img_src", "");
                    if (imgSrc != null && imgSrc.length() > 0) {
                        this.pictureList.add(imgSrc);
                    }
                }
            }

            this.childCount = replyJson.getInt("rcount");

            if (replyJson.has("replies") && !replyJson.isNull("replies")) {
                JSONArray childReplies = replyJson.getJSONArray("replies");
                for (int j = 0; j < childReplies.length(); j++) {
                    Reply childReply = new Reply(false, childReplies.getJSONObject(j));
                    this.childMsgList.add(childReply);
                }
            }
        }
    }

    private String stripHtml(String html) {
        if (html == null || html.length() == 0) {
            return "";
        }
        String result = html.replaceAll("<br\\s*/?>", "\n");
        result = result.replaceAll("<[^>]+>", "");
        return result;
    }

    public String getFormattedTime() {
        return pubTime != null ? pubTime : "";
    }

    public boolean isRoot() {
        return root == 0;
    }

    public boolean isTop() {
        return isTop;
    }

    public String getContent() {
        return message != null ? message : "";
    }
}