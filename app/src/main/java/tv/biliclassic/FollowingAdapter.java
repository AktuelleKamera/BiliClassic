package tv.biliclassic;

import tv.biliclassic.util.DeviceUtil;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.api.UserInfoApi;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.util.ImageLoader;

/**
 * 关注的人列表适配器（古早风格：分割线 + 左边头像 + 名字/签名 + 右边取消关注垃圾桶）。
 */
public class FollowingAdapter extends BaseObservableAdapter<UserInfo> {

    public interface OnUnfollowListener {
        void onUnfollowed(UserInfo user, int position);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private OnUnfollowListener unfollowListener;
    // 滚动中下载完成的图片 set 操作排队，停止后分批应用（避免滚动时频繁 setImageBitmap 卡顿）
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

    public FollowingAdapter(Context context, List<UserInfo> list) {
        super(context, list);
    }

    public void setOnUnfollowListener(OnUnfollowListener listener) {
        this.unfollowListener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (list == null || position < 0 || position >= list.size()) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_following, parent, false);
            }
            return convertView;
        }
        final UserInfo user = list.get(position);
        if (user == null) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_following, parent, false);
            }
            return convertView;
        }

        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_following, parent, false);
            holder = new ViewHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.iv_avatar);
            holder.name = (TextView) convertView.findViewById(R.id.tv_name);
            holder.sign = (TextView) convertView.findViewById(R.id.tv_sign);
            holder.btnUnfollow = (ImageView) convertView.findViewById(R.id.btn_unfollow);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.name.setText(user.name != null ? user.name : "");
        holder.sign.setText(user.sign != null && user.sign.length() > 0 ? user.sign : "这个人很懒，什么都没写");
        holder.avatar.setImageResource(R.drawable.bili_default_avatar);

        // 遥控器光标高亮（选中：半透明粉色；未选中：夜间灰 / 白天白底）
        boolean night = tv.biliclassic.metro.MetroTheme.isNight();
        if (position == selectedPosition && !mHideHighlight) {
            tv.biliclassic.util.UiSkin.setBgColorKeepPadding(convertView, 0x66D86DA5);
        } else {
            tv.biliclassic.util.UiSkin.setBgResourceKeepPadding(convertView, night
                    ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white);
        }
        holder.name.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);
        holder.sign.setTextColor(night ? 0xFFB0B0B0 : 0xFF999999);

        // 点击整行跳转由 ListView 的 OnItemClickListener 处理（滚动不会误触），
        // 这里不放 item 内 onClick，避免 convertView 复用时滚动误触发跳转

        // 点击垃圾桶：取消关注
        final int pos = position;
        holder.btnUnfollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmUnfollow(user, pos);
            }
        });

        loadAvatar(holder.avatar, user.avatar, position);
        tv.biliclassic.util.UiSkin.recolorItem(convertView);
        return convertView;
    }

    /** 确认取消关注（垃圾桶点击与遥控器长按 OK 共用），成功后经 OnUnfollowListener 回调移除 */
    public void confirmUnfollow(final UserInfo user, final int position) {
        new android.app.AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(context))
                .setTitle(context.getString(R.string.following_list_unfollow))
                .setMessage(context.getString(R.string.following_list_unfollow_msg, user.name != null ? user.name : ""))
                .setPositiveButton(context.getString(R.string.ostwind_no_decoder_ok),
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                doUnfollow(user, position);
                            }
                        })
                .setNegativeButton(context.getString(R.string.videodetail_cancel), null)
                .show();
    }

    private void doUnfollow(final UserInfo user, final int position) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = UserInfoApi.followUser(user.mid, false);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                Toast.makeText(context,
                                        context.getString(R.string.following_list_unfollow_done),
                                        Toast.LENGTH_SHORT).show();
                                if (unfollowListener != null) {
                                    unfollowListener.onUnfollowed(user, position);
                                }
                            } else {
                                Toast.makeText(context,
                                        context.getString(R.string.following_list_unfollow_fail),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context,
                                    context.getString(R.string.following_list_unfollow_fail),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void loadAvatar(final ImageView avatarView, final String url, final int position) {
        if (avatarView == null) return;
        // 通道头像边框
        addAvatarBorder(avatarView);
        ImageLoader.bind(avatarView, url, R.drawable.bili_default_avatar, 48, 48);
    }

    private void addAvatarBorder(ImageView imageView) {
        if (imageView == null) return;
        try {
            android.graphics.drawable.Drawable borderDrawable =
                    context.getResources().getDrawable(R.drawable.image_border_overlay);
            imageView.setBackgroundDrawable(borderDrawable);
            int paddingPx = DeviceUtil.dpToPx(2);
            imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } catch (Exception e) {
        }
    }



    static class ViewHolder {
        ImageView avatar;
        TextView name;
        TextView sign;
        ImageView btnUnfollow;
    }
}
