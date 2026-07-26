package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.biliclassic.api.ConfInfoApi;
import tv.biliclassic.api.BilibiliIDConverter;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class SearchActivity extends BaseActivity {

    private EditText searchEdit;
    private ImageView backBtn;
    private ImageView searchAction;
    private ListView resultList;
    private TextView emptyView;
    private LinearLayout topLoading;
    private ProgressBar topProgress;
    private View footerView;
    private ProgressBar footerProgressBar;

    private SearchResultAdapter adapter;
    private List<SearchResultItem> resultListData = new ArrayList<SearchResultItem>();

    private String currentKeyword = "";
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;
    private boolean hasSearched = false;

    private Handler retryHandler = new Handler();

    private static final Pattern AV_PATTERN = Pattern.compile("av(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BV_PATTERN = Pattern.compile("bv([a-zA-Z0-9]{10})", Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        searchEdit = (EditText) findViewById(R.id.search_edit);
        backBtn = (ImageView) findViewById(R.id.back);
        searchAction = (ImageView) findViewById(R.id.search_action);
        resultList = (ListView) findViewById(R.id.result_list);
        emptyView = (TextView) findViewById(R.id.empty_view);
        topLoading = (LinearLayout) findViewById(R.id.top_loading);
        topProgress = (ProgressBar) findViewById(R.id.top_progress);

        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerProgressBar = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        footerView.setVisibility(View.GONE);

        resultList.addFooterView(footerView, null, false);

        adapter = new SearchResultAdapter(this, resultListData);
        resultList.setAdapter(adapter);

        resultList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (hasSearched && lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0) {
                        loadMoreResults();
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (hasSearched && !isLoading && !isEnd && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
                        loadMoreResults();
                    }
                }
            }
        });

        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (view == footerView) {
                    return;
                }
                if (position >= resultListData.size()) {
                    return;
                }
                SearchResultItem item = resultListData.get(position);
                if (item == null) {
                    return;
                }

                Intent intent = new Intent(SearchActivity.this, VideoDetailActivity.class);
                if (item.aid != 0) {
                    intent.putExtra("aid", item.aid);
                } else if (item.bvid != null && item.bvid.length() > 0) {
                    intent.putExtra("bvid", item.bvid);
                } else {
                    Toast.makeText(SearchActivity.this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(intent);
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        searchAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        searchEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();
                    return true;
                }
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event.getRepeatCount() == 0) {
                        performSearch();
                        return true;
                    }
                }
                return false;
            }
        });

        searchEdit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEdit.setSingleLine(true);

        String keyword = getIntent().getStringExtra("keyword");
        if (keyword != null && keyword.length() > 0) {
            searchEdit.setText(keyword);
            performSearch();
        }
    }

    private void showFirstLoading() {
        resultListData.clear();
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(View.GONE);
        resultList.setVisibility(View.GONE);
        topLoading.setVisibility(View.VISIBLE);
        if (topProgress != null) {
            topProgress.setVisibility(View.VISIBLE);
        }
        footerView.setVisibility(View.GONE);
    }

    private void hideFirstLoadingAndShowList() {
        topLoading.setVisibility(View.GONE);
        resultList.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showEmptyResult() {
        topLoading.setVisibility(View.GONE);
        footerView.setVisibility(View.GONE);
        resultList.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    private boolean checkAndJumpToVideo(String input) {
        if (input == null || input.length() == 0) {
            return false;
        }

        Matcher avMatcher = AV_PATTERN.matcher(input);
        if (avMatcher.find()) {
            String aidStr = avMatcher.group(1);
            try {
                final long aid = Long.parseLong(aidStr);
                Toast.makeText(this, "正在打开视频...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(SearchActivity.this, VideoDetailActivity.class);
                intent.putExtra("aid", aid);
                startActivity(intent);
                return true;
            } catch (NumberFormatException e) {
            }
        }

        Matcher bvMatcher = BV_PATTERN.matcher(input);
        if (bvMatcher.find()) {
            String bvid = bvMatcher.group(1);
            if (!bvid.startsWith("BV") && !bvid.startsWith("bv")) {
                bvid = "BV" + bvid;
            }
            Toast.makeText(this, "正在打开视频...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(SearchActivity.this, VideoDetailActivity.class);
            intent.putExtra("bvid", bvid);
            startActivity(intent);
            return true;
        }

        return false;
    }

    /**
     * 是否为 GTA 仿古作弊码彩蛋
     */
    private boolean checkAndTriggerCheatCode(String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return false;
        }

        String lowerKeyword = keyword.toLowerCase();
        if (lowerKeyword.equals("nuttertools") ||
                lowerKeyword.equals("professionaltools") ||
                lowerKeyword.equals("thugstools")) {

            // 震动效果
            try {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(200);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Toast.makeText(this, "作弊码已启用！正在跳转设置...", Toast.LENGTH_LONG).show();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(SearchActivity.this, SettingsActivity.class));
                }
            }, 800);
            return true;
        }
        return false;
    }

    private void performSearch() {
        final String keyword = searchEdit.getText().toString().trim();
        if (keyword == null || keyword.length() == 0) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查 GTA 作弊码彩蛋
        if (checkAndTriggerCheatCode(keyword)) {
            return;
        }

        if (checkAndJumpToVideo(keyword)) {
            return;
        }

        if (isLoading) return;

        hasSearched = true;
        isLoading = true;
        isEnd = false;
        currentKeyword = keyword;
        currentPage = 1;

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        showFirstLoading();
        doSearchRequest(keyword, 1, 3);
    }

    private void doSearchRequest(final String keyword, final int page, final int retryLeft) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/web-interface/wbi/search/type?";
                    url += "search_type=video";
                    url += "&keyword=" + URLEncoder.encode(keyword, "UTF-8");
                    url += "&page=" + page;
                    url += "&pagesize=20";

                    url = ConfInfoApi.signWBI(url);
                    ArrayList<String> headers = buildHeaders();
                    String response = NetWorkUtil.get(url, headers);

                    final JSONObject json = new JSONObject(response);
                    final int code = json.optInt("code", -1);
                    final String message = json.optString("message", "");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                handleSearchResponse(json);
                            } else {
                                handleSearchError(code, message, keyword, retryLeft);
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    final String errMsg = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleNetworkError(errMsg, keyword, retryLeft);
                        }
                    });
                }
            }
        }).start();
    }

    private void handleSearchResponse(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                showEmptyResult();
                isLoading = false;
                return;
            }

            JSONArray result = data.optJSONArray("result");
            if (result == null || result.length() == 0) {
                showEmptyResult();
                isLoading = false;
                return;
            }

            List<SearchResultItem> items = new ArrayList<SearchResultItem>();
            for (int i = 0; i < result.length(); i++) {
                JSONObject obj = result.getJSONObject(i);
                if ("video".equals(obj.optString("type"))) {
                    SearchResultItem item = new SearchResultItem();
                    String title = obj.optString("title");
                    if (title != null) {
                        title = title.replaceAll("<em class=\"keyword\">", "");
                        title = title.replaceAll("</em>", "");
                        item.title = StringUtil.htmlToString(title);
                    } else {
                        item.title = "";
                    }

                    String pic = obj.optString("pic");
                    if (pic != null && pic.length() > 0) {
                        if (pic.startsWith("//")) {
                            pic = "https:" + pic;
                        } else if (pic.startsWith("http://")) {
                            pic = "https://" + pic.substring(7);
                        } else if (!pic.startsWith("https://")) {
                            pic = "https://" + pic;
                        }
                        item.cover = pic;
                    }
                    item.author = obj.optString("author");
                    item.play = obj.optInt("play");
                    item.danmaku = obj.optInt("danmaku");

                    long aid = obj.optLong("aid", 0);
                    String bvid = obj.optString("bvid");

                    if (aid == 0 && bvid != null && bvid.length() > 0) {
                        try {
                            aid = BilibiliIDConverter.bvtoaid(bvid);
                        } catch (Exception e) {
                            aid = 0;
                        }
                    }

                    item.aid = aid;
                    item.bvid = bvid;
                    items.add(item);
                }
            }

            if (items.size() == 0) {
                showEmptyResult();
                isLoading = false;
                return;
            }

            resultListData.clear();
            resultListData.addAll(items);
            adapter.notifyDataSetChanged();

            hideFirstLoadingAndShowList();
            isLoading = false;

            if (result.length() >= 20) {
                currentPage = 2;
                footerView.setVisibility(View.VISIBLE);
            } else {
                isEnd = true;
                footerView.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showEmptyResult();
            isLoading = false;
        }
    }

    private void handleSearchError(int code, String message, final String keyword, final int retryLeft) {
        isLoading = false;

        if (retryLeft > 0 && (code == -400 || message.contains("sign") || message.contains("wbi"))) {
            Toast.makeText(this, "签名验证失败，正在重试...", Toast.LENGTH_SHORT).show();
            retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doSearchRequest(keyword, 1, retryLeft - 1);
                }
            }, 1000);
        } else {
            showEmptyResult();
            emptyView.setText("API错误(" + code + "): " + message);
            Toast.makeText(this, "搜索失败: " + message, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleNetworkError(String errMsg, final String keyword, final int retryLeft) {
        isLoading = false;

        boolean isNetworkError = errMsg != null && (errMsg.contains("Transport endpoint") || errMsg.contains("No route") || errMsg.contains("timeout"));

        if (retryLeft > 0 && isNetworkError) {
            Toast.makeText(this, "网络异常，正在重试...(" + retryLeft + ")", Toast.LENGTH_SHORT).show();
            retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doSearchRequest(keyword, 1, retryLeft - 1);
                }
            }, 1500);
        } else {
            showEmptyResult();
            emptyView.setText("请求失败: " + (errMsg != null ? errMsg : "请检查网络"));
            Toast.makeText(this, "请求失败: " + (errMsg != null ? errMsg : "请检查网络"), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMoreResults() {
        if (!hasSearched || isLoading || isEnd) return;
        if (resultListData.size() == 0) return;

        final int nextPage = currentPage;

        isLoading = true;
        footerView.setVisibility(View.VISIBLE);
        if (footerProgressBar != null) {
            footerProgressBar.setVisibility(View.VISIBLE);
        }

        doLoadMoreRequest(currentKeyword, nextPage, 2);
    }

    private void doLoadMoreRequest(final String keyword, final int page, final int retryLeft) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/web-interface/wbi/search/type?";
                    url += "search_type=video";
                    url += "&keyword=" + URLEncoder.encode(keyword, "UTF-8");
                    url += "&page=" + page;
                    url += "&pagesize=20";

                    url = ConfInfoApi.signWBI(url);
                    ArrayList<String> headers = buildHeaders();
                    String response = NetWorkUtil.get(url, headers);

                    final JSONObject json = new JSONObject(response);
                    final int code = json.optInt("code", -1);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleLoadMoreResponse(json, code, keyword, page, retryLeft);
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleLoadMoreError(e.getMessage(), keyword, page, retryLeft);
                        }
                    });
                }
            }
        }).start();
    }

    private void handleLoadMoreResponse(JSONObject json, int code, final String keyword, final int page, final int retryLeft) {
        try {
            if (code != 0) {
                if (retryLeft > 0) {
                    retryHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            doLoadMoreRequest(keyword, page, retryLeft - 1);
                        }
                    }, 1000);
                    return;
                }
                footerView.setVisibility(View.GONE);
                isLoading = false;
                Toast.makeText(this, "加载更多失败: " + json.optString("message"), Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                showNoMore();
                return;
            }

            JSONArray result = data.optJSONArray("result");
            if (result == null || result.length() == 0) {
                showNoMore();
                return;
            }

            int added = 0;
            for (int i = 0; i < result.length(); i++) {
                JSONObject obj = result.getJSONObject(i);
                if ("video".equals(obj.optString("type"))) {
                    SearchResultItem item = new SearchResultItem();
                    String title = obj.optString("title");
                    if (title != null) {
                        title = title.replaceAll("<em class=\"keyword\">", "");
                        title = title.replaceAll("</em>", "");
                        item.title = StringUtil.htmlToString(title);
                    } else {
                        item.title = "";
                    }

                    String pic = obj.optString("pic");
                    if (pic != null && pic.length() > 0) {
                        if (pic.startsWith("//")) {
                            pic = "https:" + pic;
                        } else if (pic.startsWith("http://")) {
                            pic = "https://" + pic.substring(7);
                        } else if (!pic.startsWith("https://")) {
                            pic = "https://" + pic;
                        }
                        item.cover = pic;
                    }
                    item.author = obj.optString("author");
                    item.play = obj.optInt("play");
                    item.danmaku = obj.optInt("danmaku");

                    long aid = obj.optLong("aid", 0);
                    String bvid = obj.optString("bvid");

                    if (aid == 0 && bvid != null && bvid.length() > 0) {
                        try {
                            aid = BilibiliIDConverter.bvtoaid(bvid);
                        } catch (Exception e) {
                            aid = 0;
                        }
                    }

                    item.aid = aid;
                    item.bvid = bvid;
                    resultListData.add(item);
                    added++;
                }
            }

            adapter.notifyDataSetChanged();
            footerView.setVisibility(View.GONE);
            isLoading = false;

            if (added == 0 || result.length() < 20) {
                showNoMore();
            } else {
                currentPage = page + 1;
                footerView.setVisibility(View.VISIBLE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            footerView.setVisibility(View.GONE);
            isLoading = false;
            Toast.makeText(this, "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLoadMoreError(String errMsg, final String keyword, final int page, final int retryLeft) {
        footerView.setVisibility(View.GONE);

        boolean isNetworkError = errMsg != null && (errMsg.contains("Transport endpoint") || errMsg.contains("No route") || errMsg.contains("timeout"));

        if (retryLeft > 0 && isNetworkError) {
            retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doLoadMoreRequest(keyword, page, retryLeft - 1);
                }
            }, 1500);
        } else {
            isLoading = false;
            Toast.makeText(this, "加载更多失败: " + (errMsg != null ? errMsg : "请检查网络"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showNoMore() {
        isEnd = true;
        footerView.setVisibility(View.GONE);
        Toast.makeText(this, "已经到底了", Toast.LENGTH_SHORT).show();
    }

    private ArrayList<String> buildHeaders() {
        ArrayList<String> headers = new ArrayList<String>();

        headers.add("User-Agent");
        headers.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        headers.add("Accept");
        headers.add("application/json, text/plain, */*");

        headers.add("Accept-Language");
        headers.add("zh-CN,zh;q=0.9,en;q=0.8");

        headers.add("Accept-Encoding");
        headers.add("identity");

        headers.add("Referer");
        headers.add("https://www.bilibili.com/");

        headers.add("Origin");
        headers.add("https://www.bilibili.com");

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies != null && cookies.length() > 0) {
            headers.add("Cookie");
            headers.add(cookies);
        }

        return headers;
    }

    public static class SearchResultItem {
        public String title;
        public String cover;
        public String author;
        public int play;
        public int danmaku;
        public long aid;
        public String bvid;
    }
}