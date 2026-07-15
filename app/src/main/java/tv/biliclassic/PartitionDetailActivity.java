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
import android.widget.TabHost;
import android.widget.TextView;

import java.util.HashMap;

public class PartitionDetailActivity extends BaseActivity implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {

    private TabHost mTabHost;
    private ViewPager mPager;
    private HashMap<String, Integer> mTabIndexMap = new HashMap<String, Integer>();
    private int[] mTidArray;
    private int mMajorTid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_partition_detail);

        mMajorTid = getIntent().getIntExtra("rid", 1);
        String partitionName = getIntent().getStringExtra("name");
        if (partitionName == null) partitionName = "分区";

        mTidArray = TidData.getTidGroup(mMajorTid);

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

        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.setOnTabChangedListener(this);
        mTabHost.clearAllTabs();

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(new CategoryPagerAdapter(getSupportFragmentManager()));
        mPager.setOnPageChangeListener(this);

        for (int i = 0; i < mTidArray.length; i++) {
            addTab(i, mTidArray[i]);
        }
    }

    private void addTab(int index, int tid) {
        String tag = String.valueOf(tid);
        TextView tabLabel = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.bili_category_tab_label, (ViewGroup) null, false);
        tabLabel.setText(TidData.getNameByTid(tid));
        tabLabel.setTextSize(12);

        TabHost.TabSpec tabSpec = mTabHost.newTabSpec(tag).setIndicator(tabLabel);
        tabSpec.setContent(new DummyTabFactory(this));
        mTabIndexMap.put(tag, Integer.valueOf(index));
        mTabHost.addTab(tabSpec);
    }

    @Override
    public void onTabChanged(String tabId) {
        Integer index = mTabIndexMap.get(tabId);
        if (index != null) {
            mPager.setCurrentItem(index.intValue(), false);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        mTabHost.setCurrentTab(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    private static class DummyTabFactory implements TabHost.TabContentFactory {
        private final Context mContext;

        public DummyTabFactory(Context context) {
            this.mContext = context;
        }

        @Override
        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }
    }

    private class CategoryPagerAdapter extends FragmentStatePagerAdapter {

        public CategoryPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            int tid = mTidArray[position];
            return PartitionPageFragment.newInstance(tid);
        }

        @Override
        public int getCount() {
            return mTidArray.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return TidData.getNameByTid(mTidArray[position]);
        }
    }

    public static Intent createIntent(Context context, int tid) {
        Intent intent = new Intent(context, PartitionDetailActivity.class);
        intent.putExtra("rid", tid);
        intent.putExtra("name", TidData.getNameByTid(tid));
        return intent;
    }
}
