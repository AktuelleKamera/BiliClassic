package tv.biliclassic.tv.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import tv.biliclassic.R;

public class TvGridAdapter extends BaseAdapter {

    private Context context;
    private String[] titles;
    private int[] icons;

    public TvGridAdapter(Context context) {
        this.context = context;
        this.titles = new String[]{"追番", "推荐", "时间线", "分区", "搜索", "历史", "设置"};
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
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_tv_tile, parent, false);
            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.tile_icon);
            holder.label = (TextView) convertView.findViewById(R.id.tile_label);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.icon.setImageResource(icons[position]);
        holder.label.setText(titles[position]);

        // 焦点放大效果（使用 ScaleAnimation，兼容 Android 2.3）
        convertView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    ScaleAnimation scaleAnim = new ScaleAnimation(
                            1.0f, 1.1f,
                            1.0f, 1.1f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f
                    );
                    scaleAnim.setDuration(150);
                    scaleAnim.setFillAfter(true);
                    v.startAnimation(scaleAnim);
                } else {
                    ScaleAnimation scaleAnim = new ScaleAnimation(
                            1.1f, 1.0f,
                            1.1f, 1.0f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f
                    );
                    scaleAnim.setDuration(150);
                    scaleAnim.setFillAfter(true);
                    v.startAnimation(scaleAnim);
                }
            }
        });

        return convertView;
    }

    static class ViewHolder {
        ImageView icon;
        TextView label;
    }
}