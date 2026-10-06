package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.ImageLoader;

public class RelatedVideosAdapter extends BaseObservableAdapter<VideoCard> {

    public interface OnVideoClickListener {
        void onVideoClick(VideoCard video, int position);
    }

    public interface OnVideoLongClickListener {
        void onVideoLongClick(VideoCard video, int position);
    }

    private OnVideoClickListener mClickListener;
    private OnVideoLongClickListener mLongClickListener;
    private int itemLayout = R.layout.item_history;

    public RelatedVideosAdapter(Context context, List<VideoCard> list) {
        super(context, list);
    }

    /** 指定 item 布局（需含 cover_container/cover/title/up_name/progress 这些 id）。 */
    public RelatedVideosAdapter(Context context, List<VideoCard> list, int itemLayout) {
        super(context, list);
        if (itemLayout != 0) {
            this.itemLayout = itemLayout;
        }
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.mClickListener = listener;
    }

    public void setOnVideoLongClickListener(OnVideoLongClickListener listener) {
        this.mLongClickListener = listener;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(itemLayout, parent, false);
            holder = new ViewHolder();
            holder.coverContainer = (FrameLayout) convertView.findViewById(R.id.cover_container);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.upName = (TextView) convertView.findViewById(R.id.up_name);
            holder.progress = (TextView) convertView.findViewById(R.id.progress);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // 键盘光标高亮（选中：半透明粉色；未选中：恢复原点击效果背景）
        // 触摸滑动时隐藏高亮（mHideHighlight），避免光标与手指位置混淆
        final boolean night = tv.biliclassic.metro.MetroTheme.isNight();
        if (position == selectedPosition && !mHideHighlight) {
            convertView.setBackgroundColor(0x66D86DA5);
        } else {
            try {
                convertView.setBackgroundDrawable(convertView.getResources().getDrawable(
                        night ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white));
            } catch (Exception e) {
                convertView.setBackgroundColor(night ? 0xFF222222 : 0xFFFFFFFF);
            }
        }
        // 夜间：封面框换深灰、文字调亮
        if (holder.coverContainer != null) {
            if (night) {
                holder.coverContainer.setBackgroundColor(0xFF484848);
            } else {
                holder.coverContainer.setBackgroundResource(R.drawable.bili_thumb_boarder);
            }
        }
        holder.title.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);
        holder.upName.setTextColor(night ? 0xFFB0B0B0 : 0xFF666666);
        holder.progress.setTextColor(night ? 0xFFB0B0B0 : 0xFF999999);

        final VideoCard item = list.get(position);

        holder.title.setText(item.title);
        holder.upName.setText(item.upName);
        holder.progress.setText(item.view);

        ImageLoader.bind(holder.cover, item.cover, R.drawable.bili_default_image_tv_with_bg, 76, 56);

        final VideoCard clickItem = item;
        final int pos = position;

        // 点击
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickListener != null) {
                    mClickListener.onVideoClick(clickItem, pos);
                }
            }
        });

        // 长按
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mLongClickListener != null) {
                    mLongClickListener.onVideoLongClick(clickItem, pos);
                    return true;
                }
                return false;
            }
        });

        return convertView;
    }

    public void clearCache() {
        ImageLoader.clearCache();
        notifyDataSetChanged();
    }

    static class ViewHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView title;
        TextView upName;
        TextView progress;
    }
}