package tv.biliclassic;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.NetWorkUtil;

public class TimelineFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;

    private TimelineAdapter adapter;
    private List<timelineDay> timelineList = new ArrayList<timelineDay>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timeline, container, false);

        listView = (ListView) view.findViewById(R.id.timeline_list);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(android.R.color.transparent);

        listView.setFocusable(true);
        listView.setFocusableInTouchMode(true);
        listView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        adapter = new TimelineAdapter(getActivity(), timelineList);
        listView.setAdapter(adapter);

        loadtimeline();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.getVisibility() == View.VISIBLE) {
            listView.requestFocus();
        }
    }

    private void loadtimeline() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = SettingsActivity.getTimelineApiUrl();

                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
                    conn.connect();

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        showError("数据加载失败 (HTTP " + responseCode + ")");
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    conn.disconnect();

                    String jsonStr = sb.toString();
                    if (jsonStr == null || jsonStr.length() == 0) {
                        showError("数据为空");
                        return;
                    }

                    final List<timelineDay> items = parsetimeline(jsonStr);

                    if (getActivity() == null) return;

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            if (items == null || items.size() == 0) {
                                emptyView.setText("暂无放送时间表数据");
                                emptyView.setVisibility(View.VISIBLE);
                                listView.setVisibility(View.GONE);
                                return;
                            }
                            timelineList.clear();
                            timelineList.addAll(items);
                            adapter.notifyDataSetChanged();
                            listView.setVisibility(View.VISIBLE);
                            listView.requestFocus();
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    showError("加载失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private List<timelineDay> parsetimeline(String jsonStr) throws Exception {
        List<timelineDay> result = new ArrayList<timelineDay>();

        JSONObject root = new JSONObject(jsonStr);
        String season = root.optString("season", "");
        String updateTime = root.optString("update_time", "");

        JSONArray weekdays = root.optJSONArray("weekdays");
        if (weekdays == null || weekdays.length() == 0) {
            return result;
        }

        for (int i = 0; i < weekdays.length(); i++) {
            JSONObject dayObj = weekdays.getJSONObject(i);
            timelineDay day = new timelineDay();

            day.day = dayObj.optString("day", "");
            day.dayEn = dayObj.optString("day_en", "");
            day.date = dayObj.optString("date", "");
            day.dayIndex = getDayIndexFromEn(day.dayEn);

            JSONArray items = dayObj.optJSONArray("items");
            if (items != null) {
                for (int j = 0; j < items.length(); j++) {
                    JSONObject itemObj = items.getJSONObject(j);
                    timelineItem item = new timelineItem();
                    item.title = itemObj.optString("title", "");
                    day.items.add(item);
                }
            }

            result.add(day);
        }

        return result;
    }

    /**
     * 根据英文星期名获取 B站 day_index
     * 6=周四, 7=周五, 1=周六, 2=周日, 3=周一, 4=周二, 5=周三
     */
    private int getDayIndexFromEn(String dayEn) {
        if (dayEn == null) return 6;
        if (dayEn.equalsIgnoreCase("Monday")) return 3;
        if (dayEn.equalsIgnoreCase("Tuesday")) return 4;
        if (dayEn.equalsIgnoreCase("Wednesday")) return 5;
        if (dayEn.equalsIgnoreCase("Thursday")) return 6;
        if (dayEn.equalsIgnoreCase("Friday")) return 7;
        if (dayEn.equalsIgnoreCase("Saturday")) return 1;
        if (dayEn.equalsIgnoreCase("Sunday")) return 2;
        return 6;
    }

    private void showError(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                emptyView.setText(msg);
                emptyView.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static class timelineDay {
        public String day;
        public String dayEn;
        public String date;
        public int dayIndex;
        public List<timelineItem> items = new ArrayList<timelineItem>();
    }

    public static class timelineItem {
        public String title;
    }
}