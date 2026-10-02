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

    public RelatedVideosAdapter(Context context, List<VideoCard> list) {
        super(context, list);
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
            convertView = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
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
        if (position == selectedPosition && !mHideHighlight) {
            convertView.setBackgroundColor(0x66D86DA5);
        } else {
            try {
                convertView.setBackgroundDrawable(
                        convertView.getResources().getDrawable(R.drawable.item_click_effect_white));
            } catch (Exception e) {
                convertView.setBackgroundColor(0xFFFFFFFF);
            }
        }

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