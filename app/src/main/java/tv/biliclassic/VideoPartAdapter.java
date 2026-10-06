package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.model.VideoPart;

public class VideoPartAdapter extends BaseObservableAdapter<VideoPart> {

    public interface OnPartClickListener {
        void onPartClick(VideoPart part, int position);
    }

    private OnPartClickListener mListener;

    public VideoPartAdapter(Context context, List<VideoPart> list) {
        super(context, list);
    }

    public void setOnPartClickListener(OnPartClickListener listener) {
        this.mListener = listener;
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
        boolean night = tv.biliclassic.metro.MetroTheme.isNight();
        if (position == selectedPosition) {
            convertView.setBackgroundColor(0x33FF6699);
        } else {
            convertView.setBackgroundResource(night
                    ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white);
        }
        tvIndex.setTextColor(0xFFD86DA5);
        tvTitle.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onPartClick(item, pos);
                }
            }
        });

        tv.biliclassic.util.UiSkin.recolorItem(convertView);
        return convertView;
    }
}