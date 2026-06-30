package tv.biliclassic.tv.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import tv.biliclassic.R;

public class TvGridAdapter extends BaseAdapter {

    private Context context;
    private String[] titles;
    private int[] icons;
    private OnTileClickListener listener;

    public interface OnTileClickListener {
        void onTileClick(String label);
    }

    public TvGridAdapter(Context context) {
        this.context = context;
        this.titles = new String[]{"登录", "推荐", "时间线", "分区", "搜索", "历史", "设置"};
        this.icons = new int[]{
                R.drawable.ic_action_rating_important,
                R.drawable.ic_action_content_select_all,
                R.drawable.bili_timeline_clock_1,
                R.drawable.ic_action_content_select_all,
                R.drawable.ic_action_search,
                R.drawable.ic_action_device_access_data_usage,
                R.drawable.ic_action_refresh
        };
    }

    public void setOnTileClickListener(OnTileClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return titles.length;
    }

    @Override
    public String getItem(int position) {
        return titles[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final View itemView;
        ViewHolder holder;
        if (convertView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.item_tv_tile, parent, false);
            holder = new ViewHolder();
            holder.icon = (ImageView) itemView.findViewById(R.id.tile_icon);
            holder.label = (TextView) itemView.findViewById(R.id.tile_label);
            itemView.setTag(holder);
        } else {
            itemView = convertView;
            holder = (ViewHolder) itemView.getTag();
        }

        holder.icon.setImageResource(icons[position]);
        holder.label.setText(titles[position]);

        // 每个 item 自己的 OnClickListener
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onTileClick(titles[position]);
                }
            }
        });

        // 强制正方形
        itemView.post(new Runnable() {
            @Override
            public void run() {
                int width = itemView.getWidth();
                if (width > 0) {
                    ViewGroup.LayoutParams params = itemView.getLayoutParams();
                    params.height = (int)(width * 0.7);
                    itemView.setLayoutParams(params);
                }
            }
        });

        // 焦点放大效果
        itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            }
        });

        return itemView;
    }

    static class ViewHolder {
        ImageView icon;
        TextView label;
    }
}