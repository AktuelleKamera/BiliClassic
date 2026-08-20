package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class PartitionDetailActivity extends BaseActivity implements ViewPager.OnPageChangeListener, TabHost.OnTabChangeListener {

    private ViewPager mPager;
    private TabHost mTabHost;
    private LinearLayout mTabContainer;
    private List<View> mTabViews = new ArrayList<View>();
    private boolean mIsUpdating = false;

    private int[] mTidArray;
    private int mMajorTid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMajorTid = getIntent().getIntExtra("rid", 1);
        String partitionName = getIntent().getStringExtra("name");
        if (partitionName == null) partitionName = "分区";
        mTidArray = TidData.getTidGroup(mMajorTid);

        boolean useTabHost = tv.biliclassic.util.SdkHelper.getSdkInt() >= 4;
        if (useTabHost) {
            setContentView(R.layout.activity_partition_detail);
        } else {
            setContentView(R.layout.activity_partition_detail_v3);
        }

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(new CategoryPagerAdapter(getSupportFragmentManager()));
        mPager.setOnPageChangeListener(this);

        if (useTabHost) {
            initTabHost();
        } else {
            initSimpleTabs();
        }

        if (mTabHost != null) {
            mTabHost.setCurrentTab(0);
        } else if (mTabViews.size() > 0) {
            selectTab(0);
        }

        TextView titleText = (TextView) findViewById(R.id.title_text);
        if (titleText != null) {
            titleText.setText(partitionName + "分区");
        }

        ImageView btnBack = (ImageView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    private void initTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.setOnTabChangedListener(this);
        mTabHost.clearAllTabs();
        for (int i = 0; i < mTidArray.length; i++) {
            int tid = mTidArray[i];
            String tag = String.valueOf(tid);
            View tabView = LayoutInflater.from(this).inflate(R.layout.bili_category_tab_label, null);
            ((TextView) tabView).setText(TidData.getNameByTid(tid));
            TabHost.TabSpec spec = mTabHost.newTabSpec(tag);
            try {
                TabHost.TabSpec.class.getMethod("setIndicator", View.class).invoke(spec, tabView);
            } catch (Exception e) {
                spec.setIndicator(((TextView) tabView).getText());
            }
            mTabHost.addTab(spec.setContent(new DummyTabFactory(this)));
        }
    }

    private void initSimpleTabs() {
        mTabContainer = (LinearLayout) findViewById(R.id.tab_container);
        for (int i = 0; i < mTidArray.length; i++) {
            final int idx = i;
            TextView tab = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.bili_category_tab_label, mTabContainer, false);
            tab.setText(TidData.getNameByTid(mTidArray[i]));
            tab.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            tab.setFocusable(true);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(idx);
                    mPager.setCurrentItem(idx, false);
                }
            });
            mTabContainer.addView(tab);
            mTabViews.add(tab);
        }
    }

    private void selectTab(int position) {
        for (int i = 0; i < mTabViews.size(); i++) {
            mTabViews.get(i).setSelected(i == position);
        }
    }

    @Override
    public void onTabChanged(String tabId) {
        if (mIsUpdating) return;
        mIsUpdating = true;
        try {
            int index = Integer.parseInt(tabId);
            for (int i = 0; i < mTidArray.length; i++) {
                if (mTidArray[i] == index) {
                    mPager.setCurrentItem(i, false);
                    return;
                }
            }
        } catch (NumberFormatException ignored) {
        } finally {
            mIsUpdating = false;
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        if (mIsUpdating) return;
        mIsUpdating = true;
        try {
            if (mTabHost != null) {
                mTabHost.setCurrentTab(position);
            } else {
                selectTab(position);
            }
        } finally {
            mIsUpdating = false;
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    private static class DummyTabFactory implements TabHost.TabContentFactory {
        private final Context mContext;
        public DummyTabFactory(Context context) { this.mContext = context; }
        @Override
        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }
    }

    private class CategoryPagerAdapter extends FragmentStatePagerAdapter {
        public CategoryPagerAdapter(FragmentManager fm) { super(fm); }
        @Override public Fragment getItem(int position) { return PartitionPageFragment.newInstance(mTidArray[position]); }
        @Override public int getCount() { return mTidArray.length; }
        @Override public CharSequence getPageTitle(int position) { return TidData.getNameByTid(mTidArray[position]); }
    }

    public static Intent createIntent(Context context, int tid) {
        Intent intent = new Intent(context, PartitionDetailActivity.class);
        intent.putExtra("rid", tid);
        intent.putExtra("name", TidData.getNameByTid(tid));
        return intent;
    }
}
