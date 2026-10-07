package tv.biliclassic.metro;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import tv.biliclassic.R;
import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.ImageLoader;

/**
 * Metro 风格视频行适配器：绑定共用 item_metro_video_row
 * （历史记录 / 收藏视频共用），透明背景 + 粉色光标高亮。
 * 点击直接设置在行 View 上（与 MetroHome 内其他页面一致，
 * 不走 ListView 的 OnItemClickListener 管道）。
 * 数据源为 VideoCard（历史记录：view 字段为观看进度文案）。
 */
public class MetroVideoRowAdapter extends BaseObservableAdapter<VideoCard> {

    public interface OnVideoClickListener {
        void onVideoClick(VideoCard card, int position);
    }

    private OnVideoClickListener clickListener;

    public MetroVideoRowAdapter(Context context, List<VideoCard> list) {
        super(context, list);
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_metro_video_row, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.upName = (TextView) convertView.findViewById(R.id.up_name);
            holder.progress = (TextView) convertView.findViewById(R.id.progress);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final VideoCard item = list.get(position);
        if (item == null) {
            return convertView;
        }

        // 文字颜色：夜间模式灰/深色文字换白色系
        holder.title.setTextColor(MetroTheme.dark());
        holder.upName.setTextColor(MetroTheme.grey());

        // 光标高亮：选中（遥控器）常驻粉色；未选中用按压反馈 selector
        if (position == selectedPosition && !mHideHighlight) {
            tv.biliclassic.util.UiSkin.setBgColorKeepPadding(convertView, 0x66D86DA5);
        } else {
            tv.biliclassic.util.UiSkin.setBgResourceKeepPadding(convertView, R.drawable.metro_row_bg);
        }

        holder.title.setText(item.title != null ? item.title : "");
        holder.upName.setText(item.upName != null && item.upName.length() > 0 ? item.upName : "");
        holder.progress.setText(item.view != null ? item.view : "");

        ImageLoader.bind(holder.cover, item.cover, R.drawable.bili_default_image_tv_with_bg, 112, 66);

        // 点击直接挂在行 View 上（与推荐页磁贴/个人中心行一致）
        final int pos = position;
        final VideoCard clickItem = item;
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) {
                    clickListener.onVideoClick(clickItem, pos);
                }
            }
        });

        tv.biliclassic.util.UiSkin.recolorItem(convertView);
        return convertView;
    }

    static class ViewHolder {
        TextView title;
        TextView upName;
        TextView progress;
        ImageView cover;
    }
}
