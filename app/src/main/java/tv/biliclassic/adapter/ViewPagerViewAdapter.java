package tv.biliclassic.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.support.v4.view.PagerAdapter;

import java.util.List;

public class ViewPagerViewAdapter extends PagerAdapter {

    private List<View> mViewList;

    public ViewPagerViewAdapter(List<View> viewList) {
        this.mViewList = viewList;
    }

    @Override
    public int getCount() {
        return mViewList.size();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        View view = mViewList.get(position);
        container.addView(view);
        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }
}