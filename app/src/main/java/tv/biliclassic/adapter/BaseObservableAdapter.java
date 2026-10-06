package tv.biliclassic.adapter;

import android.content.Context;
import android.widget.BaseAdapter;

import java.util.List;

import tv.biliclassic.util.ImageLoader;

public abstract class BaseObservableAdapter<T> extends BaseAdapter {

    protected Context context;
    protected List<T> list;
    protected int selectedPosition = -1;
    protected boolean mHideHighlight = false;

    public BaseObservableAdapter(Context context, List<T> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        if (list == null || position < 0 || position >= list.size()) {
            return null;
        }
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setHideHighlight(boolean hide) {
        if (this.mHideHighlight == hide) {
            return;
        }
        this.mHideHighlight = hide;
        // 没有选中项时无需重绑（触摸滑动时 selectedPosition 通常为 -1）。
        // 有选中项才刷新一次高亮——避免一开滑就 notifyDataSetChanged 整屏重绑、
        // 把视野内已加载的图片清成占位图。
        if (selectedPosition >= 0) {
            notifyDataSetChanged();
        }
    }

    public void setScrolling(boolean scrolling) {
        ImageLoader.setScrolling(scrolling);
    }

    public void updateData(List<T> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public void clearCache() {
        ImageLoader.clearCache();
    }
}
