package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.model.LiveRoom;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.StringUtil;

/**
 * 生放送列表适配器（古早风格：封面 + 标题 + 主播 + 人气/分区）。
 * 支持遥控器方向键光标高亮，与 SearchResultAdapter 一致。
 */
public class LiveRoomAdapter extends BaseObservableAdapter<LiveRoom> {

    public LiveRoomAdapter(Context context, List<LiveRoom> list) {
        super(context, list);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_live_room, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.uname = (TextView) convertView.findViewById(R.id.uname);
            holder.info = (TextView) convertView.findViewById(R.id.info);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final LiveRoom room = list.get(position);
        if (room == null) return convertView;

        // 键盘光标高亮（选中：半透明粉色；未选中：恢复原点击效果背景）
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

        holder.title.setText(room.title != null ? room.title : "");
        holder.uname.setText(room.uname != null && room.uname.length() > 0 ? room.uname : "未知主播");

        StringBuilder info = new StringBuilder();
        if (room.online > 0) {
            info.append(StringUtil.toWan(room.online)).append("人气");
        }
        if (room.area_name != null && room.area_name.length() > 0) {
            if (info.length() > 0) info.append(" · ");
            info.append(room.area_name);
        }
        if (room.live_status == 1) {
            if (info.length() > 0) info.append(" · ");
            info.append("直播中");
        }
        holder.info.setText(info.toString());

        ImageLoader.bind(holder.cover, room.pickCover(), R.drawable.bili_default_image_tv_with_bg, 96, 66);
        return convertView;
    }

    static class ViewHolder {
        TextView title;
        TextView uname;
        TextView info;
        ImageView cover;
    }
}
