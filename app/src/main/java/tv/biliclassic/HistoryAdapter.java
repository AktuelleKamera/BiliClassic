package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.ImageLoader;

public class HistoryAdapter extends BaseObservableAdapter<VideoCard> {

    public HistoryAdapter(Context context, List<VideoCard> list) {
        super(context, list);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.upName = (TextView) convertView.findViewById(R.id.up_name);
            holder.progress = (TextView) convertView.findViewById(R.id.progress);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.coverContainer = convertView.findViewById(R.id.cover_container);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final VideoCard item = list.get(position);

        // 遥控器光标高亮（选中：半透明粉色；未选中：恢复原点击效果背景）
        // 触摸滑动时隐藏高亮（mHideHighlight），避免光标与手指位置混淆
        final boolean night = tv.biliclassic.metro.MetroTheme.isNight();
        if (position == selectedPosition && !mHideHighlight) {
            tv.biliclassic.util.UiSkin.setBgColorKeepPadding(convertView, 0x66D86DA5);
        } else {
            try {
                convertView.setBackgroundDrawable(convertView.getResources().getDrawable(
                        night ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white));
            } catch (Exception e) {
                tv.biliclassic.util.UiSkin.setBgColorKeepPadding(convertView, night ? 0xFF222222 : 0xFFFFFFFF);
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

        holder.title.setText(item.title);
        holder.upName.setText(item.upName);
        holder.progress.setText(item.view);

        ImageLoader.bind(holder.cover, item.cover, R.drawable.bili_default_image_tv_with_bg, 76, 56);

        final int pos = position;
        final VideoCard clickItem = item;
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof HistoryActivity) {
                    ((HistoryActivity) context).onHistoryClick(clickItem, pos);
                }
            }
        });

        return convertView;
    }

    static class ViewHolder {
        TextView title;
        TextView upName;
        TextView progress;
        ImageView cover;
        View coverContainer;
    }
}