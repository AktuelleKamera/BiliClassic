package tv.biliclassic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;

public class PhotoViewPager extends FrameLayout {

    private ViewPager mViewPager;

    public PhotoViewPager(Context context) {
        super(context);
        init(context);
    }

    public PhotoViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mViewPager = new ViewPager(context);
        mViewPager.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mViewPager);
    }

    public void setAdapter(PagerAdapter adapter) {
        mViewPager.setAdapter(adapter);
    }

    public int getCurrentItem() {
        return mViewPager.getCurrentItem();
    }

    public void setCurrentItem(int item) {
        mViewPager.setCurrentItem(item);
    }

    public void setOnPageChangeListener(ViewPager.OnPageChangeListener listener) {
        mViewPager.setOnPageChangeListener(listener);
    }
}
