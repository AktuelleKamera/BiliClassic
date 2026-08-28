package tv.biliclassic;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.api.UserInfoApi;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.util.GlobalImageCache;

/**
 * 关注的人列表适配器（古早风格：分割线 + 左边头像 + 名字/签名 + 右边取消关注垃圾桶）。
 */
public class FollowingAdapter extends BaseAdapter {

    public interface OnUnfollowListener {
        void onUnfollowed(UserInfo user, int position);
    }

    private final Context context;
    private final List<UserInfo> list;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private OnUnfollowListener unfollowListener;
    private volatile boolean mScrolling = false;
    // 滚动中下载完成的图片 set 操作排队，停止后分批应用（避免滚动时频繁 setImageBitmap 卡顿）
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

    public FollowingAdapter(Context context, List<UserInfo> list) {
        this.context = context;
        this.list = list;
    }

    public void setOnUnfollowListener(OnUnfollowListener listener) {
        this.unfollowListener = listener;
    }

    // ===== 遥控器方向键选中的条目（-1 = 未选中），用于整行高亮 =====
    private int selectedPosition = -1;
    private boolean mHideHighlight = false;

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setHideHighlight(boolean hide) {
        if (this.mHideHighlight == hide) {
            return;
        }
        this.mHideHighlight = hide;
        notifyDataSetChanged();
    }

    /** 滚动状态：滚动中下载完成的图片暂不 set，停止后分批应用，避免列表滚动卡顿 */
    public void setScrolling(boolean scrolling) {
        this.mScrolling = scrolling;
        if (!scrolling) {
            flushPendingBitmapSets();
        }
    }

    private void flushPendingBitmapSets() {
        if (pendingBitmapSets.isEmpty()) return;
        final java.util.ArrayList<Runnable> pending =
                new java.util.ArrayList<Runnable>(pendingBitmapSets);
        pendingBitmapSets.clear();
        // 分批应用（每帧最多 2 张），避免停下瞬间一次性 setImageBitmap 全部头像造成整帧卡顿
        final int[] idx = {0};
        final Runnable apply = new Runnable() {
            @Override
            public void run() {
                int count = Math.min(2, pending.size() - idx[0]);
                for (int i = 0; i < count; i++) {
                    try {
                        pending.get(idx[0]).run();
                    } catch (Throwable t) {
                    }
                    idx[0]++;
                }
                if (idx[0] < pending.size()) {
                    mainHandler.post(this);
                }
            }
        };
        mainHandler.post(apply);
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

        // 遥控器光标高亮（选中：半透明粉色；未选中：恢复默认浅灰背景）
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
        return convertView;
    }

    /** 确认取消关注（垃圾桶点击与遥控器长按 OK 共用），成功后经 OnUnfollowListener 回调移除 */
    public void confirmUnfollow(final UserInfo user, final int position) {
        new android.app.AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(context))
                .setTitle(context.getString(R.string.following_list_unfollow_title))
                .setMessage(context.getString(R.string.following_list_unfollow_msg, user.name != null ? user.name : ""))
                .setPositiveButton(context.getString(R.string.following_list_unfollow_confirm),
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                doUnfollow(user, position);
                            }
                        })
                .setNegativeButton(context.getString(R.string.following_list_unfollow_cancel), null)
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
        // 通用头像边框
        addAvatarBorder(avatarView);
        if (url == null || url.length() == 0) {
            avatarView.setImageResource(R.drawable.bili_default_avatar);
            return;
        }
        final String finalUrl = url;
        avatarView.setTag(finalUrl);

        Bitmap cached = GlobalImageCache.getInstance().getAndAcquire(finalUrl);
        if (cached != null && !cached.isRecycled()) {
            avatarView.setImageBitmap(cached);
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = downloadAvatar(finalUrl);
                if (bitmap != null && !bitmap.isRecycled()) {
                    GlobalImageCache.getInstance().put(finalUrl, bitmap);
                    final Runnable setBitmap = new Runnable() {
                        @Override
                        public void run() {
                            Object tag = avatarView.getTag();
                            if (tag != null && tag.equals(finalUrl)) {
                                avatarView.setImageBitmap(bitmap);
                            }
                        }
                    };
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mScrolling) {
                                // 滚动中：排队，等停止后分批应用，避免 setImageBitmap 拖慢滚动
                                pendingBitmapSets.add(setBitmap);
                            } else {
                                setBitmap.run();
                            }
                        }
                    });
                }
            }
        });
    }

    private void addAvatarBorder(ImageView imageView) {
        if (imageView == null) return;
        try {
            android.graphics.drawable.Drawable borderDrawable =
                    context.getResources().getDrawable(R.drawable.image_border_overlay);
            imageView.setBackgroundDrawable(borderDrawable);
            int paddingPx = dpToPx(2);
            imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } catch (Exception e) {
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private Bitmap downloadAvatar(String urlStr) {
        if (tv.biliclassic.util.SharedPreferencesUtil.getBoolean(
                tv.biliclassic.util.SharedPreferencesUtil.NO_IMAGE_MODE, false)) {
            return null;
        }
        java.net.HttpURLConnection conn = null;
        java.io.File tempFile = null;
        try {
            java.net.URL url = new java.net.URL(urlStr);
            conn = (java.net.HttpURLConnection) url.openConnection();
            if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                javax.net.ssl.SSLSocketFactory sslFactory =
                        tv.biliclassic.util.NetWorkUtil.getTrustAllSSLSocketFactory();
                if (sslFactory != null) {
                    ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
                }
                ((javax.net.ssl.HttpsURLConnection) conn).setHostnameVerifier(
                        tv.biliclassic.util.NetWorkUtil.TRUST_ALL_HOSTNAMES);
            }
            conn.setRequestProperty("User-Agent", tv.biliclassic.util.NetWorkUtil.USER_AGENT_WEB);
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            tempFile = new java.io.File(context.getCacheDir(),
                    "follow_face_" + urlStr.hashCode() + ".tmp");
            java.io.InputStream is = conn.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
            is.close();
            fos.close();
            if (!tempFile.exists() || tempFile.length() == 0) return null;
            int targetSize = dpToPx(48);
            return GlobalImageCache.decodeFileSafely(tempFile, targetSize, targetSize, 2);
        } catch (OutOfMemoryError e) {
            GlobalImageCache.getInstance().freeAllUnreferenced();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception e) {}
            }
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete(); } catch (Exception e) {}
            }
        }
    }

    static class ViewHolder {
        ImageView avatar;
        TextView name;
        TextView sign;
        ImageView btnUnfollow;
    }
}
