package tv.biliclassic;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

import tv.biliclassic.model.PrivateMessage;

/**
 * 私信聊天适配器
 */
public class PrivateMsgAdapter extends BaseAdapter {

    private final Context context;
    private final List<PrivateMessage> list;
    private final long myUid;

    public PrivateMsgAdapter(Context context, List<PrivateMessage> list, long myUid) {
        this.context = context;
        this.list = list;
        this.myUid = myUid;
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        if (list == null || position < 0 || position >= list.size()) return null;
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_private_msg, parent, false);
        }
        if (list == null || position < 0 || position >= list.size()) {
            return convertView;
        }
        PrivateMessage msg = list.get(position);
        if (msg == null) {
            return convertView;
        }

        LinearLayout root = (LinearLayout) convertView.findViewById(R.id.msg_root);
        TextView text = (TextView) convertView.findViewById(R.id.msg_text);

        if (msg.type == PrivateMessage.TYPE_RETRACT || msg.type == PrivateMessage.TYPE_SYSTEM) {
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            text.setBackgroundColor(0x00000000);
            text.setTextColor(0xFF999999);
            text.setTextSize(12);
        } else {
            text.setTextColor(0xFF333333);
            text.setTextSize(14);
            if (msg.uid == myUid) {
                root.setGravity(Gravity.RIGHT);
                text.setBackgroundResource(R.drawable.chat_bubble_right);
            } else {
                root.setGravity(Gravity.LEFT);
                text.setBackgroundResource(R.drawable.chat_bubble_left);
            }
        }

        text.setText(messageText(msg));
        return convertView;
    }

    private String messageText(PrivateMessage msg) {
        try {
            switch (msg.type) {
                case PrivateMessage.TYPE_TEXT:
                    return msg.content.optString("content", "");
                case PrivateMessage.TYPE_PIC:
                case PrivateMessage.TYPE_FACE:
                    return "[图片]";
                case PrivateMessage.TYPE_VIDEO:
                case PrivateMessage.TYPE_NOMAL_CARD:
                case PrivateMessage.TYPE_PIC_CARD:
                    return "[视频] " + msg.content.optString("title", "");
                case PrivateMessage.TYPE_TEXT_WITH_VIDEO:
                    return msg.content.optString("reply_content", "");
                case PrivateMessage.TYPE_RETRACT:
                    return msg.name + "撤回了一条消息";
                case PrivateMessage.TYPE_SYSTEM:
                    if (msg.content_array.length() > 0) {
                        JSONObject obj = msg.content_array.optJSONObject(0);
                        if (obj != null) return obj.optString("text", "");
                    }
                    return "";
                default:
                    return "[暂不支持的消息]";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
