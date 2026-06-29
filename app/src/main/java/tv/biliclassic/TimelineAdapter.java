package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class TimelineAdapter extends BaseAdapter {

    private Context context;
    private List<TimelineFragment.timelineDay> list;

    // B站 day_index -> 图标资源
    // 6=周四, 7=周五, 1=周六, 2=周日, 3=周一, 4=周二, 5=周三
    private static int getDayIcon(int biliDay) {
        switch (biliDay) {
            case 6: return R.drawable.bili_timeline_clock_6;  // 周四
            case 7: return R.drawable.bili_timeline_clock_7;  // 周五
            case 1: return R.drawable.bili_timeline_clock_1;  // 周六
            case 2: return R.drawable.bili_timeline_clock_2;  // 周日
            case 3: return R.drawable.bili_timeline_clock_3;  // 周一
            case 4: return R.drawable.bili_timeline_clock_4;  // 周二
            case 5: return R.drawable.bili_timeline_clock_5;  // 周三
            default: return R.drawable.bili_timeline_clock_6; // 默认周四
        }
    }

    public TimelineAdapter(Context context, List<TimelineFragment.timelineDay> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        return list == null ? null : list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_timeline_day, parent, false);
            holder = new ViewHolder();
            holder.clockIcon = (ImageView) convertView.findViewById(R.id.clock_icon);
            holder.dayTitle = (TextView) convertView.findViewById(R.id.day_title);
            holder.dayDate = (TextView) convertView.findViewById(R.id.day_date);
            holder.itemsContainer = (LinearLayout) convertView.findViewById(R.id.items_container);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TimelineFragment.timelineDay day = list.get(position);
        if (day == null) {
            return convertView;
        }

        // 动态设置时钟图标
        int dayIndex = day.dayIndex;
        holder.clockIcon.setImageResource(getDayIcon(dayIndex));

        // 设置标题和日期
        holder.dayTitle.setText(day.day);
        holder.dayDate.setText(day.date);

        // 清空并重新填充番剧列表
        holder.itemsContainer.removeAllViews();
        if (day.items != null) {
            for (TimelineFragment.timelineItem item : day.items) {
                TextView tv = new TextView(context);
                tv.setText("• " + item.title);
                tv.setTextSize(13);
                tv.setTextColor(0xFF333333);
                tv.setPadding(0, 2, 0, 2);
                holder.itemsContainer.addView(tv);
            }
        }

        return convertView;
    }

    public void updateData(List<TimelineFragment.timelineDay> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder {
        ImageView clockIcon;
        TextView dayTitle;
        TextView dayDate;
        LinearLayout itemsContainer;
    }
}