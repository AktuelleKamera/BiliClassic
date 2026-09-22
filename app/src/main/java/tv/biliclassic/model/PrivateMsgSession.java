package tv.biliclassic.model;

import org.json.JSONObject;

/**
 * 私信会话
 */
public class PrivateMsgSession {
    public long talkerUid = 0;
    public int unread = 0;
    public int contentType = 0;
    public JSONObject content;
    public String talkerName = "";
    public String talkerFace = "";
    public long maxSeqno = 0;
    public long ackSeqno = 0;
    public long lastMsgTimestamp = 0;

    public PrivateMsgSession() {
    }
}
