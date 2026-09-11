package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.biliclassic.api.BilibiliIDConverter;
import tv.biliclassic.api.SearchApi;
import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.MsgUtil;
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

    // 搜索历史
    private static final String KEY_SEARCH_HISTORY = "search_history";

    // 排序/筛选下拉（ActionBar 式）
    private View sortButton;
    private TextView sortButtonText;
    // 日期筛选范围（秒），0 表示不限
    private long filterBeginS = 0;
    private long filterEndS = 0;
    private static final String[] MENU_NAMES = {"综合", "相关度", "发布日期", "起始日期", "评论", "弹幕", "收藏", "UP主"};
    // 每项对应动作：0-5=排序索引；-1=起始日期；-2=UP主
    private static final int[] MENU_ACTIONS = {0, 1, 2, -1, 3, 4, 5, -2};
    // 结果态/搜索态切换
    private View titleSearchBox;
    private View titleSpacer;
    private int currentSortIndex = 0;
    private static final String[] SORT_NAMES = {"综合", "相关度", "发布日期", "评论", "弹幕", "收藏"};
    private static final String[] SORT_ORDERS = {"", "ranklevel", "pubdate", "scores", "dm", "stow"};
    private static final int[] SORT_STAT = {
            SearchResultAdapter.STAT_PLAY,
            SearchResultAdapter.STAT_PLAY,
            SearchResultAdapter.STAT_PUBDATE,
            SearchResultAdapter.STAT_REVIEW,
            SearchResultAdapter.STAT_DANMAKU,
            SearchResultAdapter.STAT_FAVORITE
    };

    private SearchResultAdapter adapter;
    private List<SearchResultItem> resultListData = new ArrayList<SearchResultItem>();

    // 键盘光标选中的项，-1 表示无选中
    private int selectedPosition = -1;

    private String currentKeyword = "";
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;
    private boolean hasSearched = false;

    private Handler retryHandler = new Handler();
    private int searchSeq = 0;

    private static final Pattern AV_PATTERN = Pattern.compile("av(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BV_PATTERN = Pattern.compile("bv([a-zA-Z0-9]{10})", Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_search);
        } catch (Exception e) {
            android.util.Log.w("SearchActivity", "Could not inflate search layout", e);
            setContentView(new TextView(this));
            return;
        }

        searchEdit = (EditText) findViewById(R.id.search_edit);
        backBtn = (ImageView) findViewById(R.id.back);
        searchAction = (ImageView) findViewById(R.id.search_action);
        resultList = (ListView) findViewById(R.id.result_list);
        emptyView = (TextView) findViewById(R.id.empty_view);
        topLoading = (LinearLayout) findViewById(R.id.top_loading);
        topProgress = (ProgressBar) findViewById(R.id.top_progress);

        // 排序下拉 + 结果态搜索按钮 + 搜索态搜索框
        sortButton = findViewById(R.id.sort_button);
        sortButtonText = (TextView) findViewById(R.id.sort_button_text);
        titleSearchBox = findViewById(R.id.title_search_box);
        titleSpacer = findViewById(R.id.title_spacer);
        setupSortButton();
        View btnSearchTitle = findViewById(R.id.btn_search_title);
        if (btnSearchTitle != null) {
            btnSearchTitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSearchBox();
                }
            });
        }

        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerProgressBar = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        footerView.setVisibility(View.GONE);

        resultList.addFooterView(footerView, null, false);

        // 背景是平铺纹理位图：必须显式透明 cacheColorHint，否则 2.x 滚动/加载更多时
        // 绘制缓存用默认颜色填充，透明的 footer 区域露出纯色底 → footer 背景闪烁
        resultList.setCacheColorHint(0x00000000);

        adapter = new SearchResultAdapter(this, resultListData);
        resultList.setAdapter(adapter);

        // TV 模式适配：让 ListView 可聚焦
        resultList.setFocusable(true);
        resultList.setFocusableInTouchMode(true);

        resultList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    adapter.setScrolling(false);
                    // 滚动结束：仍保持高亮隐藏，等待再次按键恢复（避免光标跳到触摸滚动到的位置）
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (hasSearched && lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0) {
                        loadMoreResults();
                    }
                } else {
                    adapter.setScrolling(true);
                    // 开始触摸滚动/甩动：隐藏光标高亮
                    adapter.setHideHighlight(true);
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
                    Toast.makeText(SearchActivity.this, SearchActivity.this.getString(R.string.load_video_info_failed_4), Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(intent);
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (titleSearchBox != null && titleSearchBox.getVisibility() == View.VISIBLE && hasSearched) {
                    hideSearchBox();
                } else {
                    finish();
                }
            }
        });

        // bilibili 图标：点击返回（带粉色点击特效）
        View logoContainer = findViewById(R.id.logo_container);
        if (logoContainer != null) {
            logoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        searchAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        // Holo 搜索框的清除按钮：有文字清空，无文字收起
        ImageView searchClear = (ImageView) findViewById(R.id.search_clear);
        if (searchClear != null) {
            searchClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (searchEdit != null && searchEdit.getText().length() > 0) {
                        searchEdit.setText("");
                    } else {
                        hideSearchBox();
                    }
                }
            });
        }

        tv.biliclassic.util.SdkHelper.setOnEditorActionListener(searchEdit, new tv.biliclassic.util.SdkHelper.EditorActionHandler() {
            @Override
            public boolean onEditorAction(int actionId, KeyEvent event) {
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

        // TV 模式：搜索框也要能聚焦
        searchEdit.setFocusable(true);
        searchEdit.setFocusableInTouchMode(true);
        try {
            java.lang.reflect.Method m = searchEdit.getClass().getMethod("setImeOptions", int.class);
            m.invoke(searchEdit, EditorInfo.IME_ACTION_SEARCH);
        } catch (Throwable t) {
        }
        searchEdit.setSingleLine(true);

        String keyword = getIntent().getStringExtra("keyword");
        if (keyword != null && keyword.length() > 0) {
            searchEdit.setText(keyword);
            performSearch();
        } else {
            showSearchBox();
        }

        // 让搜索框获得焦点（TV 模式）
        searchEdit.post(new Runnable() {
            @Override
            public void run() {
                searchEdit.requestFocus();
            }
        });
    }

    // 保存搜索历史
    private void saveSearchHistory(String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return;
        }

        String historyJson = SharedPreferencesUtil.getString(KEY_SEARCH_HISTORY, "");
        List<String> historyList = new ArrayList<String>();

        try {
            if (historyJson != null && historyJson.length() > 0) {
                JSONArray arr = new JSONArray(historyJson);
                for (int i = 0; i < arr.length(); i++) {
                    String item = arr.getString(i);
                    if (!item.equals(keyword)) {
                        historyList.add(item);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        historyList.add(0, keyword);
        if (historyList.size() > 10) {
            historyList = historyList.subList(0, 10);
        }

        try {
            JSONArray arr = new JSONArray();
            for (String item : historyList) {
                arr.put(item);
            }
            SharedPreferencesUtil.putString(KEY_SEARCH_HISTORY, arr.toString());
        } catch (Exception e) {
            e.printStackTrace();
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
                Toast.makeText(this, this.getString(R.string.opening_video), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, this.getString(R.string.opening_video), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(SearchActivity.this, VideoDetailActivity.class);
            intent.putExtra("bvid", bvid);
            startActivity(intent);
            return true;
        }

        return false;
    }

    private boolean checkAndTriggerCheatCode(String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return false;
        }

        String lowerKeyword = keyword.toLowerCase();

        // GTA 作弊码 → SettingsActivity
        if (lowerKeyword.equals("nuttertools") ||
                lowerKeyword.equals("professionaltools") ||
                lowerKeyword.equals("thugstools")) {

            try {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(200);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Toast.makeText(this, this.getString(R.string.cheat_code_enabled), Toast.LENGTH_LONG).show();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(SearchActivity.this, SettingsActivity.class));
                }
            }, 800);
            return true;
        }

        // giveusatank → 打开神秘页面
        // 来自 GTA 3 的神秘作弊码……
        if (lowerKeyword.equals("giveusatank")) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(300);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Toast.makeText(this, this.getString(R.string.cheat_code_enabled), Toast.LENGTH_LONG).show();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(SearchActivity.this, WebViewActivity.class);
                    intent.putExtra("url", "http://www.biliclassic.cn/buy");
                    intent.putExtra("title", "不必追求2.3版本");
                    startActivity(intent);
                }
            }, 800);
            return true;
        }

        // GETTHEREQUICKLY → 生放送（直播）
        if (lowerKeyword.equals("gettherequickly")) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(200);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Toast.makeText(this, this.getString(R.string.cheat_code_enabled), Toast.LENGTH_LONG).show();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(SearchActivity.this, LiveRoomListActivity.class));
                }
            }, 800);
            return true;
        }

        return false;
    }

    /** 搜索态：显示搜索框，隐藏排序 + 搜索按钮（模仿 1.8.4 点搜索按钮展开） */
    private void showSearchBox() {
        if (sortButton != null) {
            sortButton.setVisibility(View.GONE);
        }
        if (titleSpacer != null) {
            titleSpacer.setVisibility(View.GONE);
        }
        View btn = findViewById(R.id.btn_search_title);
        if (btn != null) {
            btn.setVisibility(View.GONE);
        }
        if (titleSearchBox != null) {
            titleSearchBox.setVisibility(View.VISIBLE);
        }
        if (searchEdit != null) {
            searchEdit.requestFocus();
            searchEdit.postDelayed(new Runnable() {
                @Override
                public void run() {
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null && searchEdit != null) {
                        imm.showSoftInput(searchEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }, 100);
        }
    }

    /** 结果态：隐藏搜索框，显示排序 + 搜索按钮 */
    private void hideSearchBox() {
        if (titleSearchBox != null) {
            titleSearchBox.setVisibility(View.GONE);
        }
        if (sortButton != null) {
            sortButton.setVisibility(View.VISIBLE);
        }
        if (titleSpacer != null) {
            titleSpacer.setVisibility(View.VISIBLE);
        }
        View btn = findViewById(R.id.btn_search_title);
        if (btn != null) {
            btn.setVisibility(View.VISIBLE);
        }
        View focus = getCurrentFocus();
        if (focus != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    /** 初始化排序/筛选下拉按钮（点开是 ActionBar 式菜单） */
    private void setupSortButton() {
        if (sortButton == null) {
            return;
        }
        if (sortButtonText != null) {
            sortButtonText.setText(SORT_NAMES[currentSortIndex]);
        }
        sortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterMenu();
            }
        });
    }

    /** 排序/筛选菜单：综合/相关度/发布日期/评论/弹幕/收藏/UP主/日期 */
    private void showFilterMenu() {
        android.content.Context ctx = tv.biliclassic.util.DialogUtil.wrap(this);
        new android.app.AlertDialog.Builder(ctx)
                .setTitle("排序 / 筛选")
                .setItems(MENU_NAMES, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which < 0 || which >= MENU_ACTIONS.length) {
                            return;
                        }
                        int action = MENU_ACTIONS[which];
                        if (action >= 0) {
                            selectSort(action);
                        } else if (action == -1) {
                            showDateRangeDialog();
                        } else if (action == -2) {
                            String kw = searchEdit != null ? searchEdit.getText().toString().trim() : "";
                            if (kw.length() == 0) {
                                kw = currentKeyword != null ? currentKeyword : "";
                            }
                            if (kw.length() == 0) {
                                Toast.makeText(SearchActivity.this, "请先输入搜索词", Toast.LENGTH_SHORT).show();
                            } else {
                                searchUp(kw);
                            }
                        }
                    }
                })
                .show();
    }

    /** 选择排序方式并重新搜索 */
    private void selectSort(int index) {
        currentSortIndex = index;
        if (sortButtonText != null) {
            sortButtonText.setText(SORT_NAMES[index]);
        }
        if (adapter != null) {
            adapter.setStatMode(SORT_STAT[index]);
        }
        researchWithFilter();
    }

    /** 搜UP主：走用户搜索，结果直接显示在当前列表（复用 item_following 布局） */
    private void searchUp(final String name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final org.json.JSONObject json = SearchApi.searchUser(name, 1);
                    final java.util.List<SearchResultItem> users = new java.util.ArrayList<SearchResultItem>();
                    if (json != null && json.optInt("code", -1) == 0) {
                        org.json.JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            org.json.JSONArray arr = data.optJSONArray("result");
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    org.json.JSONObject u = arr.optJSONObject(i);
                                    if (u == null) {
                                        continue;
                                    }
                                    String uname = u.optString("uname", "");
                                    if (uname.length() == 0) {
                                        continue;
                                    }
                                    SearchResultItem it = new SearchResultItem();
                                    it.isUser = true;
                                    it.userMid = u.optLong("mid", 0);
                                    it.userName = uname;
                                    it.userSign = u.optString("usign", u.optString("sign", ""));
                                    String upic = u.optString("upic", "");
                                    if (upic.startsWith("//")) {
                                        upic = "https:" + upic;
                                    } else if (upic.startsWith("http://")) {
                                        upic = "https://" + upic.substring(7);
                                    }
                                    it.userAvatar = upic;
                                    users.add(it);
                                }
                            }
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hideSearchBox();
                            hasSearched = true;
                            isLoading = false;
                            isEnd = true;
                            resultListData.clear();
                            resultListData.addAll(users);
                            adapter.notifyDataSetChanged();
                            if (users.size() == 0) {
                                showEmptyResult();
                                emptyView.setText("没有找到UP主");
                            } else {
                                hideFirstLoadingAndShowList();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(SearchActivity.this, "搜索UP主失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 日期范围：三级菜单，编辑起始日期和截止日期 */
    private void showDateRangeDialog() {
        android.content.Context ctx = tv.biliclassic.util.DialogUtil.wrap(this);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        final android.widget.DatePicker startPicker = new android.widget.DatePicker(ctx);
        final android.widget.DatePicker endPicker = new android.widget.DatePicker(ctx);
        // 2009-06-26（B站建站）之前不可选；截止不超过今天
        final long minMs = 1245945600L * 1000L;
        final long maxMs = System.currentTimeMillis();
        setPickerRange(startPicker, minMs, maxMs);
        setPickerRange(endPicker, minMs, maxMs);
        // 初始：起始=2009-06-26（B站建站），截止=今天
        startPicker.updateDate(2009, 5, 26);
        endPicker.updateDate(cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH));
        if (filterBeginS > 0) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(filterBeginS * 1000L);
            startPicker.updateDate(c.get(java.util.Calendar.YEAR),
                    c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH));
        }
        if (filterEndS > 0) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(filterEndS * 1000L);
            endPicker.updateDate(c.get(java.util.Calendar.YEAR),
                    c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH));
        }
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 12);
        ll.setPadding(pad, pad, pad, pad);
        TextView st = new TextView(ctx);
        st.setText("起始日期");
        TextView et = new TextView(ctx);
        et.setText("截止日期");
        ll.addView(st);
        ll.addView(startPicker);
        ll.addView(et);
        ll.addView(endPicker);
        new android.app.AlertDialog.Builder(ctx)
                .setTitle("日期范围")
                .setView(ll)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        filterBeginS = pickerToSeconds(startPicker);
                        filterEndS = pickerToSeconds(endPicker) + 86399; // 当天结束
                        if (filterBeginS > filterEndS) {
                            long t = filterBeginS;
                            filterBeginS = filterEndS - 86399;
                            filterEndS = t + 86399;
                        }
                        researchWithFilter();
                    }
                })
                .setNeutralButton("清除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        filterBeginS = 0;
                        filterEndS = 0;
                        researchWithFilter();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 排序/日期变化后从第一页重新搜索 */
    private void researchWithFilter() {
        if (hasSearched && currentKeyword != null && currentKeyword.length() > 0) {
            ++searchSeq;
            isLoading = true;
            isEnd = false;
            currentPage = 1;
            selectedPosition = -1;
            showFirstLoading();
            doSearchRequest(currentKeyword, 1, 3);
        }
    }

    /** DatePicker.setMinDate/setMaxDate 是 API 11+，用反射兼容老设备（否则验证器拒绝整个类） */
    private void setPickerRange(android.widget.DatePicker picker, long minMs, long maxMs) {
        if (picker == null) {
            return;
        }
        try {
            java.lang.reflect.Method setMin = android.widget.DatePicker.class.getMethod("setMinDate", long.class);
            setMin.invoke(picker, minMs);
        } catch (Throwable t) {
        }
        try {
            java.lang.reflect.Method setMax = android.widget.DatePicker.class.getMethod("setMaxDate", long.class);
            setMax.invoke(picker, maxMs);
        } catch (Throwable t) {
        }
    }

    private long pickerToSeconds(android.widget.DatePicker p) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(p.getYear(), p.getMonth(), p.getDayOfMonth(), 0, 0, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 1000L;
    }

    /** 当前排序对应的接口 order 值（空串=综合） */
    private String currentOrder() {
        if (currentSortIndex < 0 || currentSortIndex >= SORT_ORDERS.length) {
            return "";
        }
        return SORT_ORDERS[currentSortIndex];
    }

    private void performSearch() {
        final String keyword = searchEdit.getText().toString().trim();
        if (keyword == null || keyword.length() == 0) {
            Toast.makeText(this, this.getString(R.string.search_input_required), Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkAndTriggerCheatCode(keyword)) {
            return;
        }

        if (checkAndJumpToVideo(keyword)) {
            return;
        }

        ++searchSeq;

        saveSearchHistory(keyword);

        hideSearchBox();

        hasSearched = true;
        isLoading = true;
        isEnd = false;
        currentKeyword = keyword;
        currentPage = 1;

        if (getCurrentFocus() != null) {
            tv.biliclassic.util.SdkHelper.hideSoftInputFromWindow(this, getCurrentFocus().getWindowToken(), 0);
        }

        showFirstLoading();
        doSearchRequest(keyword, 1, 3);
    }

    public void onSearchResultClick(SearchActivity.SearchResultItem item, int position) {
        if (item == null) return;
        if (item.isUser) {
            Intent userIntent = new Intent(this, UserProfileActivity.class);
            userIntent.putExtra("mid", item.userMid);
            startActivity(userIntent);
            return;
        }
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra("aid", item.aid);
        startActivity(intent);
    }

    private void doSearchRequest(final String keyword, final int page, final int retryLeft) {
        final int curSeq = searchSeq;
        final String order = currentOrder();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject json = SearchApi.search(keyword, page, order, filterBeginS, filterEndS);
                    final int code = json.optInt("code", -1);
                    final String message = json.optString("message", "");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (searchSeq != curSeq) return;
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
                            if (searchSeq != curSeq) return;
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
                    item.review = obj.optInt("review");
                    item.favorites = obj.optInt("favorites");
                    item.pubdate = obj.optLong("pubdate", 0);

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
            MsgUtil.showMsg(this, "签名验证失败，正在重试...");
            retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doSearchRequest(keyword, 1, retryLeft - 1);
                }
            }, 1000);
        } else if (code == -111) {
            // B 站对搜索敏感词/被屏蔽内容统一返回 -111 "csrf 校验失败"，并非登录或 csrf 问题。
            showEmptyResult();
            emptyView.setText("该关键词暂时无法搜索");
            MsgUtil.showMsg(this, "该关键词暂时无法搜索");
        } else {
            showEmptyResult();
            emptyView.setText("API错误(" + code + "): " + message);
            MsgUtil.showMsg(this, "搜索失败: " + message);
        }
    }

    private void handleNetworkError(String errMsg, final String keyword, final int retryLeft) {
        isLoading = false;

        boolean isNetworkError = errMsg != null && (errMsg.contains("Transport endpoint") || errMsg.contains("No route") || errMsg.contains("timeout"));

        if (retryLeft > 0 && isNetworkError) {
            MsgUtil.showMsg(this, "网络异常，正在重试...(" + retryLeft + ")");
            retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doSearchRequest(keyword, 1, retryLeft - 1);
                }
            }, 1500);
        } else {
            showEmptyResult();
            emptyView.setText("请求失败: " + (errMsg != null ? errMsg : "请检查网络"));
            MsgUtil.showMsg(this, "请求失败: " + (errMsg != null ? errMsg : "请检查网络"));
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
        final int curSeq = searchSeq;
        final String order = currentOrder();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject json = SearchApi.search(keyword, page, order, filterBeginS, filterEndS);
                    final int code = json.optInt("code", -1);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (searchSeq != curSeq) return;
                            handleLoadMoreResponse(json, code, keyword, page, retryLeft);
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (searchSeq != curSeq) return;
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
                    item.review = obj.optInt("review");
                    item.favorites = obj.optInt("favorites");
                    item.pubdate = obj.optLong("pubdate", 0);

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
        Toast.makeText(this, getString(R.string.emoticon__no_more_data), Toast.LENGTH_SHORT).show();
    }


    /**
     * 遥控器方向键在搜索结果列表内移动光标（选中高亮），确认键打开视频。
     * 返回 true 表示事件已被消费。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // 搜索框展开时，返回键先收起搜索框
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && titleSearchBox != null && titleSearchBox.getVisibility() == View.VISIBLE && hasSearched) {
            hideSearchBox();
            return true;
        }
        if (handleRemoteKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 方向键上下移动光标、数字键 2/8 翻页、确认键打开视频。
     * 仅在已有搜索结果且焦点不在输入框时生效。
     */
    public boolean handleRemoteKey(KeyEvent event) {
        if (resultList == null || adapter == null || resultListData.size() == 0) {
            return false;
        }
        // 排序下拉获得焦点时，方向键交给它处理
        if (sortButton != null && sortButton.hasFocus()) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        // 数字键在输入框聚焦时保留给输入法（避免输入数字被拦截），
        // 方向键/OK 始终用于列表导航
        int action = KeyBindingUtil.classify(event.getKeyCode());
        if (searchEdit != null && searchEdit.hasFocus()) {
            if (action == KeyBindingUtil.ACTION_UP
                    || action == KeyBindingUtil.ACTION_DOWN
                    || action == KeyBindingUtil.ACTION_CONFIRM) {
                // 放行：在输入框聚焦时也能用方向键/OK 操作列表
            } else {
                return false;
            }
        }
        if (action != KeyBindingUtil.ACTION_UP
                && action != KeyBindingUtil.ACTION_DOWN
                && action != KeyBindingUtil.ACTION_CONFIRM
                && action != KeyBindingUtil.ACTION_NUM_2
                && action != KeyBindingUtil.ACTION_NUM_8) {
            return false;
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        // 按键恢复：先取消触摸滑动时的隐藏，重新显示光标并滚回选中项，
        // 让"焦点"回到遥控器接管的位置
        if (adapter != null) {
            adapter.setHideHighlight(false);
        }
        // 首次按下才移动光标；长按 repeat 只消费不移动，防止 ListView 内置滚动干扰
        if (event.getRepeatCount() == 0) {
            int count = resultListData.size();
            if (action == KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == KeyBindingUtil.ACTION_NUM_2) {
                selectedPosition = pageMove(-1);
            } else if (action == KeyBindingUtil.ACTION_NUM_8) {
                selectedPosition = pageMove(1);
                if (selectedPosition >= count - 1) {
                    loadMoreResults();
                }
            } else if (action == KeyBindingUtil.ACTION_CONFIRM) {
                openResult(selectedPosition);
                return true;
            }
            applySelection();
        }
        // 所有 DOWN 事件都消费（含长按 repeat），避免列表自身滚动导致"回顶"
        return true;
    }

    /** 数字键 2/8：按一屏（当前可见项数）快速翻页。 */
    private int pageMove(int direction) {
        if (resultList == null) {
            return selectedPosition;
        }
        int first = resultList.getFirstVisiblePosition();
        int last = resultList.getLastVisiblePosition();
        int visibleCount = Math.max(1, last - first + 1);
        int newPos = selectedPosition + direction * visibleCount;
        int count = resultListData.size();
        if (newPos < 0) {
            newPos = 0;
        } else if (newPos >= count) {
            newPos = count - 1;
        }
        return newPos;
    }

    private void applySelection() {
        if (adapter != null) {
            adapter.setSelectedPosition(selectedPosition);
        }
        if (resultList != null) {
            resultList.setSelection(selectedPosition);
        }
    }

    /** 打开搜索结果列表第 position 项的视频详情。 */
    private void openResult(int position) {
        if (position < 0 || position >= resultListData.size()) {
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
            Toast.makeText(SearchActivity.this,
                    getString(R.string.load_video_info_failed_4), Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
    }

    public static class SearchResultItem {
        public String title;
        public String cover;
        public String author;
        public int play;
        public int danmaku;
        public int review;
        public int favorites;
        public long pubdate;
        public long aid;
        public String bvid;
        // UP主结果（isUser=true 时用下面字段）
        public boolean isUser;
        public long userMid;
        public String userName;
        public String userSign;
        public String userAvatar;
    }
}