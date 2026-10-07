package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.SharedPreferencesUtil;

public class FavoriteVideoAdapter extends BaseObservableAdapter<VideoCard> {

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    // Metro 主题标记
    private final boolean mMetro;

    // 长按检测
    private Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;
    private int longPressPosition = -1;
    private boolean isLongPressTriggered = false;

    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    private OnDeleteClickListener deleteClickListener;

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteClickListener = listener;
    }

    public FavoriteVideoAdapter(Context context, List<VideoCard> list) {
        super(context, list);
        if (this.list == null) {
            this.list = new ArrayList<VideoCard>();
        }
        // Metro 主题：使用透明的共用视频 item（与历史记录页共用）
        this.mMetro = SettingsActivity.getUiTheme() == SettingsActivity.THEME_METRO;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    mMetro ? R.layout.item_metro_video_row : R.layout.item_favorite_video, parent, false);
            holder = new ViewHolder();
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.author = (TextView) convertView.findViewById(R.id.up_name);   // 改为 up_name
            holder.play = (TextView) convertView.findViewById(R.id.view);        // 改为 view
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (list == null || position < 0 || position >= list.size()) {
            holder.title.setText("嘿咻…嘿咻…");
            return convertView;
        }

        final VideoCard item = list.get(position);
        if (item == null) {
            holder.title.setText("视频信息错误");
            return convertView;
        }

        // 遥控器光标高亮（选中：半透明粉色；未选中：夜间灰 / 白天白底；Metro 保持透明）
        boolean night = tv.biliclassic.metro.MetroTheme.isNight();
        if (position == selectedPosition && !mHideHighlight) {
            tv.biliclassic.util.UiSkin.setBgColorKeepPadding(convertView, 0x66D86DA5);
        } else if (mMetro) {
            convertView.setBackgroundDrawable(null);
        } else {
            tv.biliclassic.util.UiSkin.setBgResourceKeepPadding(convertView, night
                    ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white);
        }
        holder.title.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);
        holder.author.setTextColor(night ? 0xFFB0B0B0 : 0xFF666666);
        holder.play.setTextColor(night ? 0xFFB0B0B0 : 0xFF999999);
        if (!mMetro) {
            View coverBox = (View) holder.cover.getParent();
            if (coverBox != null) {
                if (night) {
                    coverBox.setBackgroundColor(0xFF484848);
                } else {
                    coverBox.setBackgroundResource(R.drawable.bili_thumb_boarder);
                }
            }
        }

        holder.title.setText(item.title != null ? item.title : "无标题");
        holder.author.setText(item.upName != null ? item.upName : "未知UP主");
        holder.play.setText(item.view != null ? item.view : "0观看");

        ImageLoader.bind(holder.cover, item.cover, R.drawable.bili_default_image_tv_with_bg, 96, 61);

        final int pos = position;
        final VideoCard clickItem = item;

        // 直接设置点击，不拦截长按
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof FavoriteVideoListActivity) {
                    ((FavoriteVideoListActivity) context).onVideoClick(clickItem, pos);
                }
            }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (deleteClickListener != null) {
                    deleteClickListener.onDeleteClick(pos);
                    return true;
                }
                return false;
            }
        });

        tv.biliclassic.util.UiSkin.recolorItem(convertView);
        return convertView;
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView author;
        TextView play;
    }
}