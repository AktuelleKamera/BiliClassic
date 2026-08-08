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

    public interface OnPartClickListener {
        void onPartClick(VideoPart part, int position);
    }

    private Context context;
    private List<VideoPart> list;
    private int selectedPosition = -1;
    private OnPartClickListener mListener;

    public VideoPartAdapter(Context context, List<VideoPart> list) {
        this.context = context;
        this.list = list;
    }

    public void setOnPartClickListener(OnPartClickListener listener) {
        this.mListener = listener;
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

        final VideoPart item = list.get(position);
        final int pos = position;

        TextView tvIndex = (TextView) convertView.findViewById(R.id.tv_part_index);
        TextView tvTitle = (TextView) convertView.findViewById(R.id.tv_part_title);

        tvIndex.setText(item.index + "");
        tvTitle.setText(item.title);

        // 高亮选中的项；未选中用布局标准底色（浅灰 #F5F5F5，避免全透明露出页面背景看起来像纯白）
        if (position == selectedPosition) {
            convertView.setBackgroundColor(0x33FF6699);
        } else {
            convertView.setBackgroundResource(R.drawable.item_click_effect_white);
        }

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onPartClick(item, pos);
                }
            }
        });

        return convertView;
    }
}