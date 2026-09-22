package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import tv.biliclassic.model.PrivateMessage;
import tv.biliclassic.model.PrivateMsgSession;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 私信相关接口
 */
public class PrivateMsgApi {

    // 获取聊天记录
    public static JSONObject getPrivateMsg(long talkerId, int size, long beginSeqno, long endSeqno)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs"
                + "?session_type=1&talker_id=" + talkerId
                + "&size=" + size
                + "&begin_seqno=" + beginSeqno
                + "&end_seqno=" + endSeqno;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all != null && all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return new JSONObject();
    }

    // 解析聊天记录
    public static ArrayList<PrivateMessage> getPrivateMsgList(JSONObject allMsgJson,
                                                             long myUid, String myName,
                                                             long talkerUid, String talkerName)
            throws JSONException {
        ArrayList<PrivateMessage> list = new ArrayList<PrivateMessage>();
        if (allMsgJson == null || allMsgJson.isNull("messages")) {
            return list;
        }
        JSONArray messages = allMsgJson.getJSONArray("messages");
        for (int i = 0; i < messages.length(); i++) {
            PrivateMessage msg = new PrivateMessage();
            JSONObject msgJson = messages.getJSONObject(i);
            msg.uid = msgJson.optLong("sender_uid", 0);
            msg.type = msgJson.optInt("msg_type", 1);
            String senderName = msg.uid == myUid ? myName : talkerName;
            msg.name = senderName != null ? senderName : "";
            msg.timestamp = msgJson.optLong("timestamp", 0);
            msg.msg_source = msgJson.optInt("msg_source", 0);
            msg.msgId = msgJson.optLong("msg_key", 0);
            msg.msgSeqno = msgJson.optLong("msg_seqno", 0);
            String content = msgJson.optString("content", "");
            if (content.length() > 1 && content.startsWith("{") && content.endsWith("}")) {
                try {
                    msg.content = new JSONObject(content);
                } catch (Exception ignore) {
                }
                try {
                    msg.content_array = new JSONArray(content);
                } catch (Exception ignore) {
                }
            }
            list.add(msg);
        }
        return list;
    }

    // 会话列表
    public static ArrayList<PrivateMsgSession> getSessionsList(int size)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions"
                + "?session_type=1&size=" + size;
        JSONObject root = NetWorkUtil.getJson(url);
        return parseSessionRoot(root);
    }

    // 新会话列表
    public static ArrayList<PrivateMsgSession> getNewSessionsList(long beginTs, int size)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/new_sessions"
                + "?size=" + size + "&mobi_app=web";
        if (beginTs > 0) {
            url = url + "&begin_ts=" + beginTs;
        }
        JSONObject root = NetWorkUtil.getJson(url);
        return parseSessionRoot(root);
    }

    private static ArrayList<PrivateMsgSession> parseSessionRoot(JSONObject root) throws JSONException {
        ArrayList<PrivateMsgSession> sessionList = new ArrayList<PrivateMsgSession>();
        if (root == null || !root.has("data") || root.isNull("data")) {
            return sessionList;
        }
        JSONObject data = root.getJSONObject("data");
        if (!data.has("session_list") || data.isNull("session_list")) {
            return sessionList;
        }
        JSONArray sessions = data.getJSONArray("session_list");
        for (int i = 0; i < sessions.length(); i++) {
            sessionList.add(parseSession(sessions.getJSONObject(i)));
        }
        return sessionList;
    }

    private static PrivateMsgSession parseSession(JSONObject sessionJson) throws JSONException {
        PrivateMsgSession session = new PrivateMsgSession();
        session.talkerUid = sessionJson.optLong("talker_id", 0);
        session.unread = sessionJson.optInt("unread_count", 0);
        session.maxSeqno = sessionJson.optLong("max_seqno", 0);
        session.ackSeqno = sessionJson.optLong("ack_seqno", 0);

        if (sessionJson.has("account_info") && !sessionJson.isNull("account_info")) {
            JSONObject accInfo = sessionJson.getJSONObject("account_info");
            session.talkerName = accInfo.optString("name", "");
            session.talkerFace = normalizeUrl(accInfo.optString("face", ""));
        }

        if (sessionJson.has("last_msg") && !sessionJson.isNull("last_msg")) {
            JSONObject lastMsg = sessionJson.getJSONObject("last_msg");
            session.contentType = lastMsg.optInt("msg_type", 1);
            session.lastMsgTimestamp = lastMsg.optLong("timestamp", 0);
            String content = lastMsg.optString("content", "");
            if (content.length() > 1 && content.startsWith("{") && content.endsWith("}")) {
                try {
                    session.content = new JSONObject(content);
                } catch (JSONException ignored) {
                }
            }
        }
        return session;
    }

    // 批量获取用户信息
    public static HashMap<Long, UserInfo> getUsersInfo(List<Long> uidList) throws IOException, JSONException {
        HashMap<Long, UserInfo> userMap = new HashMap<Long, UserInfo>();
        if (uidList == null || uidList.size() == 0) {
            return userMap;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < uidList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(uidList.get(i));
        }
        String url = "https://api.vc.bilibili.com/account/v1/user/cards?uids=" + sb.toString();
        JSONObject root = NetWorkUtil.getJson(url);
        if (root == null || !root.has("data") || root.isNull("data")) {
            return userMap;
        }
        JSONArray data = root.getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONObject userJson = data.getJSONObject(i);
            UserInfo user = new UserInfo();
            user.mid = userJson.optLong("mid", 0);
            user.name = userJson.optString("name", "");
            user.avatar = normalizeUrl(userJson.optString("face", ""));
            userMap.put(user.mid, user);
        }
        return userMap;
    }

    // 单个用户名片，用于补全昵称头像
    public static UserInfo getUserCard(long uid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/card?mid=" + uid;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all == null || all.optInt("code", -1) != 0) {
            return null;
        }
        JSONObject data = all.optJSONObject("data");
        JSONObject card = data != null ? data.optJSONObject("card") : null;
        if (card == null) {
            return null;
        }
        UserInfo user = new UserInfo();
        user.mid = uid;
        user.name = card.optString("name", "");
        user.avatar = normalizeUrl(card.optString("face", ""));
        user.sign = card.optString("sign", "");
        return user;
    }

    // 补全协议相对地址
    public static String normalizeUrl(String url) {
        if (url == null || url.length() == 0) {
            return "";
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        return url;
    }

    // 发送私信
    public static JSONObject sendMsg(long senderUid, long receiverUid, int msgType, long timestamp, String content)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg?";
        String csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        String per = "msg[dev_id]=" + getDevId()
                + "&msg[msg_type]=" + msgType
                + "&msg[content]=" + content
                + "&msg[receiver_type]=1"
                + "&msg[sender_uid]=" + senderUid
                + "&msg[receiver_id]=" + receiverUid
                + "&msg[timestamp]=" + timestamp
                + "&csrf=" + csrf;
        String response = NetWorkUtil.post(url, per, null);
        return new JSONObject(response);
    }

    // 标记已读
    public static JSONObject updateAck(long talkerId, int sessionType, long ackSeqno) throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack";
        String csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        String per = "talker_id=" + talkerId
                + "&session_type=" + sessionType
                + (ackSeqno > 0 ? ("&ack_seqno=" + ackSeqno) : "")
                + "&csrf_token=" + csrf
                + "&csrf=" + csrf
                + "&build=0&mobi_app=web";
        String response = NetWorkUtil.post(url, per, null);
        return new JSONObject(response);
    }

    // 生成设备标识
    private static String getDevId() {
        char[] b = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] s = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".toCharArray();
        for (int i = 0; i < s.length; i++) {
            if ('-' == s[i] || '4' == s[i]) continue;
            int randomInt = (int) (16 * Math.random());
            if ('x' == s[i]) {
                s[i] = b[randomInt];
            } else {
                s[i] = b[3 & randomInt | 8];
            }
        }
        return new String(s);
    }
}
