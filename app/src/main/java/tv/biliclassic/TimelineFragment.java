package tv.biliclassic;

import android.app.Activity;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import tv.biliclassic.util.NetWorkUtil;

public class TimelineFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private View headerContainer;

    private TimelineAdapter adapter;
    private List<timelineDay> timelineList = new ArrayList<timelineDay>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private File cacheDir;

    private static final int MAX_RETRY = 1;
    private int retryCount = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timeline, container, false);

        listView = (ListView) view.findViewById(R.id.timeline_list);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);
        headerContainer = view.findViewById(R.id.header_container);

        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }

        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(android.R.color.transparent);

        listView.setFocusable(false);
        listView.setFocusableInTouchMode(false);
        listView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        adapter = new TimelineAdapter(getActivity(), timelineList);
        listView.setAdapter(adapter);

        if (getActivity() != null) {
            cacheDir = new File(getActivity().getCacheDir(), "timeline_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
        }

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

    private void showLoading() {
        if (headerContainer != null) {
            headerContainer.setVisibility(View.VISIBLE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (listView != null) {
            listView.setVisibility(View.GONE);
        }
    }

    private void hideAllLoading() {
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
    }

    private void loadtimeline() {
        retryCount = 0;
        doLoadtimeline();
    }

    private void doLoadtimeline() {
        showLoading();

        // 先尝试加载缓存
        List<timelineDay> cachedItems = loadLocalCache();
        if (cachedItems != null && cachedItems.size() > 0) {
            hideAllLoading();
            timelineList.clear();
            timelineList.addAll(cachedItems);
            adapter.notifyDataSetChanged();
            listView.setVisibility(View.VISIBLE);
            listView.requestFocus();

            if (isNetworkAvailable()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        fetchTimelineFromNetwork();
                    }
                }).start();
            }
            return;
        }

        // 无缓存，检查网络
        if (!isNetworkAvailable()) {
            showNoNetwork();
            return;
        }

        // 无缓存有网络，请求数据
        new Thread(new Runnable() {
            @Override
            public void run() {
                fetchTimelineFromNetwork();
            }
        }).start();
    }

    private void fetchTimelineFromNetwork() {
        try {
            String url = SettingsActivity.getTimelineApiUrl();

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);

            File jsonFile = cacheDir != null ? new File(cacheDir, "data.json") : null;
            long ifModifiedSince = 0;
            if (jsonFile != null && jsonFile.exists()) {
                ifModifiedSince = jsonFile.lastModified();
                conn.setRequestProperty("If-Modified-Since", formatHttpDate(ifModifiedSince));
            }

            conn.connect();

            final int responseCode = conn.getResponseCode();

            if (ifModifiedSince > 0 && responseCode == 304) {
                if (jsonFile != null && jsonFile.exists()) {
                    jsonFile.setLastModified(System.currentTimeMillis());
                }
                conn.disconnect();
                return;
            }

            if (responseCode != 200) {
                conn.disconnect();
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showError("数据加载失败 (HTTP " + responseCode + ")");
                    }
                });
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
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showError("数据为空");
                    }
                });
                return;
            }

            saveToCache(jsonStr);

            final List<timelineDay> items = parsetimeline(jsonStr);

            if (getActivity() == null) return;

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideAllLoading();
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
                    retryCount = 0;
                }
            });

        } catch (final Exception e) {
            e.printStackTrace();
            if (getActivity() == null) return;
            if (!isNetworkAvailable()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showNoNetwork();
                    }
                });
            } else if (retryCount < MAX_RETRY) {
                retryCount++;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        doLoadtimeline();
                    }
                });
            } else {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showError("加载失败: " + e.getMessage());
                    }
                });
            }
        }
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getActivity().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private List<timelineDay> loadLocalCache() {
        if (cacheDir == null) return null;
        try {
            File jsonFile = new File(cacheDir, "data.json");
            if (!jsonFile.exists()) return null;

            long lastModified = jsonFile.lastModified();
            long now = System.currentTimeMillis();
            if (now - lastModified > 60 * 60 * 1000) {
                jsonFile.delete();
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(jsonFile), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            return parsetimeline(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveToCache(String jsonStr) {
        if (cacheDir == null || jsonStr == null) return;
        try {
            File jsonFile = new File(cacheDir, "data.json");
            FileOutputStream fos = new FileOutputStream(jsonFile);
            fos.write(jsonStr.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatHttpDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date(millis));
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

    private void showNoNetwork() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideAllLoading();
                emptyView.setText(getString(R.string.emoticon__no_network));
                emptyView.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
            }
        });
    }

    private void showError(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideAllLoading();
                emptyView.setText(getString(R.string.emoticon__failed_need_retry));
                emptyView.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
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