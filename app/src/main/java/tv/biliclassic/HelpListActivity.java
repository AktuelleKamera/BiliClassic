package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.HelpApi;
import tv.biliclassic.model.HelpItem;
import tv.biliclassic.util.UiSkin;
import tv.biliclassic.widget.LoadingBarView;

public class HelpListActivity extends BaseActivity {

    private final List<HelpItem> items = new ArrayList<HelpItem>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListView list;
    private TextView empty;
    private LoadingBarView loading;
    private Adapter adapter;
    private boolean loadingNow;

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_help, parent, false);
            }
            HelpItem item = items.get(position);
            TextView title = (TextView) convertView.findViewById(R.id.help_title);
            TextView summary = (TextView) convertView.findViewById(R.id.help_summary);
            boolean night = UiSkin.isNight();
            if (title != null) {
                title.setText(item.title);
                title.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);
            }
            if (summary != null) {
                if (item.summary == null || item.summary.length() == 0) {
                    summary.setVisibility(View.GONE);
                } else {
                    summary.setText(item.summary);
                    summary.setVisibility(View.VISIBLE);
                }
                summary.setTextColor(night ? 0xFFB0B0B0 : 0xFF999999);
            }
            UiSkin.setBgResourceKeepPadding(convertView, night
                    ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white);
            View divider = convertView.findViewById(R.id.help_divider);
            if (divider != null) {
                divider.setBackgroundColor(night ? 0xFF3A3A3A : 0xFFD9D9D9);
            }
            UiSkin.recolorItem(convertView);
            return convertView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_list);
        initRoundTitleBar();

        list = (ListView) findViewById(R.id.list_view);
        empty = (TextView) findViewById(R.id.empty_view);
        loading = (LoadingBarView) getLayoutInflater().inflate(R.layout.list_footer, list, false);
        loading.hide();
        list.addFooterView(loading);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= items.size()) return;
                HelpItem item = items.get(position);
                Intent intent = new Intent(HelpListActivity.this, ArticleActivity.class);
                intent.putExtra("help_url", HelpApi.resolveUrl(item));
                intent.putExtra("title", item.title);
                startActivity(intent);
            }
        });

        View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        if (empty != null) {
            empty.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadHelp();
                }
            });
        }
        applyColors();
        loadHelp();
    }

    private void applyColors() {
        boolean night = UiSkin.isNight();
        if (list != null) list.setBackgroundColor(night ? 0xFF171717 : 0xFFF5F5F5);
        if (empty != null) empty.setTextColor(night ? 0xFFB0B0B0 : 0xFF999999);
    }

    private void loadHelp() {
        if (loadingNow) return;
        loadingNow = true;
        if (empty != null) {
            empty.setText(R.string.help_loading);
            empty.setVisibility(View.VISIBLE);
        }
        loading.showLoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<HelpItem> result = null;
                String error = null;
                try {
                    result = HelpApi.getList();
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final List<HelpItem> finalResult = result;
                final String finalError = error;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        loadingNow = false;
                        loading.hide();
                        items.clear();
                        if (finalResult != null) items.addAll(finalResult);
                        adapter.notifyDataSetChanged();
                        if (finalError != null) {
                            if (empty != null) {
                                empty.setText(R.string.help_load_failed);
                                empty.setVisibility(View.VISIBLE);
                            }
                        } else if (items.size() == 0 && empty != null) {
                            empty.setText(R.string.help_empty);
                            empty.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }, "HelpList").start();
    }
    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }


}
