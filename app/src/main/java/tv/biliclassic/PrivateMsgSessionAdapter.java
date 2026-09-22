package tv.biliclassic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.api.PrivateMsgApi;
import tv.biliclassic.model.PrivateMessage;
import tv.biliclassic.model.PrivateMsgSession;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.util.ImageLoader;

/**
 * 私信会话列表适配器
 */
public class PrivateMsgSessionAdapter extends BaseAdapter {

    private final Context context;
    private final List<PrivateMsgSession> list;
    private final HashMap<Long, UserInfo> userMap;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HashSet<Long> requestedUids = new HashSet<Long>();

    private int selectedPosition = -1;
    private boolean mHideHighlight = false;

    public PrivateMsgSessionAdapter(Context context, List<PrivateMsgSession> list,
                                    HashMap<Long, UserInfo> userMap) {
        this.context = context;
        this.list = list;
        this.userMap = userMap;
    }

    public void release() {
        executor.shutdownNow();
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public void setHideHighlight(boolean hide) {
        if (this.mHideHighlight == hide) {
            return;
        }
        this.mHideHighlight = hide;
        notifyDataSetChanged();
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
            convertView = LayoutInflater.from(context).inflate(R.layout.item_private_msg_session, parent, false);
        }
        if (list == null || position < 0 || position >= list.size()) {
            return convertView;
        }
        PrivateMsgSession session = list.get(position);
        if (session == null) {
            return convertView;
        }

        ImageView avatar = (ImageView) convertView.findViewById(R.id.iv_avatar);
        TextView name = (TextView) convertView.findViewById(R.id.tv_name);
        TextView content = (TextView) convertView.findViewById(R.id.tv_content);
        TextView unread = (TextView) convertView.findViewById(R.id.tv_unread);

        UserInfo user = userMap != null ? userMap.get(session.talkerUid) : null;
        if (user == null && session.talkerUid > 0
                && (isBlank(session.talkerName) || isBlank(session.talkerFace))) {
            loadUser(session.talkerUid);
        }

        String displayName = null;
        String avatarUrl = null;
        if (user != null) {
            displayName = user.name;
            avatarUrl = user.avatar;
        }
        if (isBlank(displayName)) {
            displayName = session.talkerName;
        }
        if (isBlank(displayName)) {
            displayName = "用户_" + session.talkerUid;
        }
        if (isBlank(avatarUrl)) {
            avatarUrl = session.talkerFace;
        }
        avatarUrl = PrivateMsgApi.normalizeUrl(avatarUrl);

        name.setText(displayName);
        content.setText(previewText(session));
        if (session.unread > 0) {
            unread.setText(String.valueOf(session.unread));
            unread.setVisibility(View.VISIBLE);
        } else {
            unread.setVisibility(View.GONE);
        }

        if (position == selectedPosition && !mHideHighlight) {
            convertView.setBackgroundColor(0x66D86DA5);
        } else {
            try {
                convertView.setBackgroundDrawable(
                        convertView.getResources().getDrawable(R.drawable.item_click_effect_white));
            } catch (Exception e) {
                convertView.setBackgroundColor(0xFFF5F5F5);
            }
        }

        avatar.setImageResource(R.drawable.bili_default_avatar);
        ImageLoader.bind(avatar, avatarUrl, R.drawable.bili_default_avatar, 48, 48);
        return convertView;
    }

    // 缺昵称头像时补拉用户名片
    private void loadUser(final long uid) {
        synchronized (requestedUids) {
            if (requestedUids.contains(uid)) {
                return;
            }
            requestedUids.add(uid);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final UserInfo user = PrivateMsgApi.getUserCard(uid);
                    if (user != null && userMap != null) {
                        userMap.put(uid, user);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                notifyDataSetChanged();
                            }
                        });
                    }
                } catch (Exception ignore) {
                }
            }
        });
    }

    private boolean isBlank(String s) {
        return s == null || s.length() == 0;
    }

    private String previewText(PrivateMsgSession session) {
        if (session.content == null) return "";
        try {
            switch (session.contentType) {
                case PrivateMessage.TYPE_TEXT:
                    return session.content.optString("content", "");
                case PrivateMessage.TYPE_PIC:
                case PrivateMessage.TYPE_FACE:
                    return "[图片消息]";
                case PrivateMessage.TYPE_VIDEO:
                case PrivateMessage.TYPE_NOMAL_CARD:
                case PrivateMessage.TYPE_PIC_CARD:
                    return session.content.optString("title", "");
                case PrivateMessage.TYPE_TEXT_WITH_VIDEO:
                    return session.content.optString("reply_content", "");
                case PrivateMessage.TYPE_RETRACT:
                    return "[撤回消息]";
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
