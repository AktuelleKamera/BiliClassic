package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
import tv.biliclassic.model.VideoPart;

public class VideoPartAdapter extends BaseAdapter {

    private Context context;
    private List<VideoPart> list;
    private int selectedPosition = -1;

    public VideoPartAdapter(Context context, List<VideoPart> list) {
        this.context = context;
        this.list = list;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return list.size();
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
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_video_part, parent, false);
        }

        VideoPart item = list.get(position);

        TextView tvIndex = (TextView) convertView.findViewById(R.id.tv_part_index);
        TextView tvTitle = (TextView) convertView.findViewById(R.id.tv_part_title);

        tvIndex.setText(item.index + "");
        tvTitle.setText(item.title);

        // 高亮选中的项
        if (position == selectedPosition) {
            convertView.setBackgroundColor(0x33FF6699);
        } else {
            convertView.setBackgroundColor(0x00000000);
        }

        return convertView;
    }
}