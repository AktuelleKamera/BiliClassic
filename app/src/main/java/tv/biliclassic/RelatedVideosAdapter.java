package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.ImageLoader;

public class RelatedVideosAdapter extends BaseAdapter {

    public interface OnVideoClickListener {
        void onVideoClick(VideoCard video, int position);
    }

    public interface OnVideoLongClickListener {
        void onVideoLongClick(VideoCard video, int position);
    }

    private Context context;
    private List<VideoCard> list;
    private OnVideoClickListener mClickListener;
    private OnVideoLongClickListener mLongClickListener;

    // 键盘光标选中的项，-1 表示无选中
    private int selectedPosition = -1;

    // 触摸滑动中是否隐藏光标高亮（滑动时隐藏，再次按键时恢复）
    private boolean mHideHighlight = false;

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    /**
     * 触摸滑动时隐藏/显示光标高亮（值不变时跳过重绘）。
     */
    public void setHideHighlight(boolean hide) {
        if (this.mHideHighlight == hide) {
            return;
        }
        this.mHideHighlight = hide;
        notifyDataSetChanged();
    }

    public RelatedVideosAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.mClickListener = listener;
    }

    public void setOnVideoLongClickListener(OnVideoLongClickListener listener) {
        this.mLongClickListener = listener;
    }

    /** 滚动状态变化时由 ListView 的 OnScrollListener 调用 */
    public void setScrolling(boolean scrolling) {
        ImageLoader.setScrolling(scrolling);
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
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
        final int currentPos = position;

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

    public void updateData(List<VideoCard> newList) {
        this.list = newList;
        notifyDataSetChanged();
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