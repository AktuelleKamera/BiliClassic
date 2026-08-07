package tv.biliclassic;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.HistoryApi;
import tv.biliclassic.model.ApiResult;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class HistoryActivity extends BaseActivity {

    private static final String TAG = "HistoryActivity";

    private ListView historyList;
    private ProgressBar progressBar;
    private TextView emptyView;
    private ImageView backBtn;
    private ProgressBar footerProgressBar;
    private View footerView;

    private HistoryAdapter adapter;
    private List<VideoCard> videoList = new ArrayList<VideoCard>();
    private ApiResult lastResult = new ApiResult();
    private boolean isLoading = false;
    private boolean isEnd = false;
    private boolean mHasError = false;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 用户是否已主动滚动过（首次加载后不自动触发加载更多，避免不足一屏时立即翻页导致风控）
    private boolean mUserScrolled = false;

    // 网络检查
    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history_activity);

        historyList = (ListView) findViewById(R.id.history_list);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        emptyView = (TextView) findViewById(R.id.empty_view);
        backBtn = (ImageView) findViewById(R.id.btn_back);

        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerProgressBar = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        historyList.addFooterView(footerView);
        footerView.setVisibility(View.GONE);

        adapter = new HistoryAdapter(this, videoList);
        historyList.setAdapter(adapter);

        // 隐藏原生 selector，避免覆盖自定义光标高亮（粉色）
        historyList.setSelector(android.R.color.transparent);
        historyList.setCacheColorHint(0x00000000);

        backBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        emptyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retryLoadHistory();
            }
        });

        historyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (position >= videoList.size()) {
                    return;
                }
                VideoCard item = (VideoCard) videoList.get(position);
                if (item == null) {
                    return;
                }

                Intent intent = new Intent(HistoryActivity.this, VideoDetailActivity.class);
                if (item.aid != 0) {
                    intent.putExtra("aid", item.aid);
                } else if (item.bvid != null && item.bvid.length() > 0) {
                    intent.putExtra("bvid", item.bvid);
                } else {
                    Toast.makeText(HistoryActivity.this, HistoryActivity.this.getString(R.string.historyactivity_toast_65e0), Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(intent);
            }
        });

        historyList.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    if (adapter != null) {
                        adapter.setScrolling(false);
                        // 滚动结束：保持高亮隐藏，等待再次按键恢复
                    }
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (mUserScrolled && lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0) {
                        loadMoreHistory();
                    }
                } else {
                    // 用户开始滚动：之后才允许滚动到底部时加载更多
                    mUserScrolled = true;
                    if (adapter != null) {
                        adapter.setScrolling(true);
                        // 开始触摸滚动/甩动：隐藏光标高亮
                        adapter.setHideHighlight(true);
                    }
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (mUserScrolled && !isLoading && !isEnd && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreHistory();
                    }
                }
            }
        });

        loadHistory();
    }

    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.clearCache();
        }
    }

    // ===== 遥控器按键导航（模仿 RelatedVideosFragment） =====
    private int selectedPosition = -1;

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (videoList == null || videoList.size() == 0 || historyList == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_2
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_8) {
            return super.dispatchKeyEvent(event);
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        // 按键恢复：取消触摸滑动时的隐藏，重新显示光标
        if (adapter != null) {
            adapter.setHideHighlight(false);
        }
        // 首次按下才移动光标；长按 repeat 只消费不移动
        if (event.getRepeatCount() == 0) {
            int count = videoList.size();
            if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_2) {
                selectedPosition = pageMove(-1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_8) {
                selectedPosition = pageMove(1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
                VideoCard item = videoList.get(selectedPosition);
                if (item != null) {
                    onHistoryClick(item, selectedPosition);
                }
                return true;
            }
            applySelection();
        }
        return true;
    }

    private int pageMove(int direction) {
        if (historyList == null) {
            return selectedPosition;
        }
        int first = historyList.getFirstVisiblePosition();
        int last = historyList.getLastVisiblePosition();
        int visibleCount = Math.max(1, last - first + 1);
        int newPos = selectedPosition + direction * visibleCount;
        int count = videoList.size();
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
        if (historyList != null) {
            // setSelection 为 API 1，兼容 Android 2.x；smoothScrollToPosition 需 API 8
            historyList.setSelection(selectedPosition);
        }
    }

    private void retryLoadHistory() {
        Log.d(TAG, "retryLoadHistory - 用户手动重试");
        mHasError = false;
        isEnd = false;
        isLoading = false;
        videoList.clear();
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        loadHistory();
    }

    private void loadHistory() {
        // 检查网络
        if (!isNetworkAvailable()) {
            mainHandler.post(new Runnable() {
                public void run() {
                    progressBar.setVisibility(View.GONE);
                    emptyView.setText(getString(R.string.emoticon__no_network));
                    emptyView.setVisibility(View.VISIBLE);
                    historyList.setVisibility(View.GONE);
                    mHasError = true;
                }
            });
            return;
        }

        if (mHasError) {
            Log.d(TAG, "loadHistory - 已有错误，显示空视图等待用户操作");
            if (emptyView.getVisibility() == View.VISIBLE) {
                return;
            }
            emptyView.setText(getString(R.string.historyactivity_settext_52a0));
            emptyView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            return;
        }

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        Log.d(TAG, "loadHistory - cookies length: " + (cookies == null ? "null" : String.valueOf(cookies.length())));

        if (cookies == null || cookies.length() == 0) {
            Log.e(TAG, "loadHistory - 未登录");
            mainHandler.post(new Runnable() {
                public void run() {
                    progressBar.setVisibility(View.GONE);
                    emptyView.setText(getString(R.string.historyactivity_settext_8fd8));
                    emptyView.setVisibility(View.VISIBLE);
                    historyList.setVisibility(View.GONE);
                }
            });
            return;
        }

        if (isLoading) return;
        isLoading = true;

        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        historyList.setVisibility(View.VISIBLE);
        videoList.clear();
        adapter.notifyDataSetChanged();

        lastResult = new ApiResult();
        isEnd = false;
        footerView.setVisibility(View.GONE);

        NetWorkUtil.refreshHeaders();

        new Thread(new Runnable() {
            public void run() {
                try {
                    Log.d(TAG, "loadHistory - 开始请求历史记录");
                    final HistoryApi.HistoryResult historyResult = HistoryApi.getHistory(lastResult);
                    final ApiResult result = historyResult.apiResult;
                    final List<VideoCard> newItems = historyResult.newItems;

                    Log.e(TAG, "========== 历史记录响应 ==========");
                    Log.e(TAG, "code: " + result.code);
                    Log.e(TAG, "message: " + result.message);
                    Log.e(TAG, "isBottom: " + result.isBottom);
                    Log.e(TAG, "videoList size: " + videoList.size());
                    if (videoList.size() > 0) {
                        Log.e(TAG, "第一个视频标题: " + ((VideoCard) videoList.get(0)).title);
                    }
                    Log.e(TAG, "=================================");

                    mainHandler.post(new Runnable() {
                        public void run() {
                            // 检查 Activity 是否存活
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            progressBar.setVisibility(View.GONE);
                            isLoading = false;

                            if (result.code == 0) {
                                lastResult = result;
                                videoList.addAll(newItems);
                                adapter.notifyDataSetChanged();
                                mHasError = false;

                                if (result.isBottom) {
                                    isEnd = true;
                                    footerView.setVisibility(View.GONE);
                                } else {
                                    footerView.setVisibility(View.VISIBLE);
                                }

                                if (videoList.size() == 0) {
                                    emptyView.setText(getString(R.string.historyactivity_settext_6682));
                                    emptyView.setVisibility(View.VISIBLE);
                                    historyList.setVisibility(View.GONE);
                                } else {
                                    emptyView.setVisibility(View.GONE);
                                    historyList.setVisibility(View.VISIBLE);
                                    // 首次加载完成：聚焦第一个视频，暂不自动加载更多（防止不足一屏立即翻页触发风控）
                                    mUserScrolled = false;
                                    historyList.setSelection(0);
                                }
                            } else {
                                String msg = result.message;
                                if (msg == null || msg.length() == 0) {
                                    msg = "加载失败";
                                }
                                if (msg.contains("banned") || msg.contains("request was banned")) {
                                    msg = "请求过于频繁，请稍后重试";
                                }
                                emptyView.setText(msg + "\n点击重试");
                                emptyView.setVisibility(View.VISIBLE);
                                historyList.setVisibility(View.GONE);
                                mHasError = true;
                                footerView.setVisibility(View.GONE);
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "loadHistory - 异常: ", e);
                    mainHandler.post(new Runnable() {
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            isLoading = false;
                            String errMsg = e.getMessage();
                            if (errMsg == null || errMsg.length() == 0) {
                                errMsg = "加载失败";
                            }
                            if (errMsg.contains("banned") || errMsg.contains("request was banned")) {
                                errMsg = "请求过于频繁，请稍后重试";
                            }
                            emptyView.setText(errMsg + "\n点击重试");
                            emptyView.setVisibility(View.VISIBLE);
                            historyList.setVisibility(View.GONE);
                            mHasError = true;
                            footerView.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }).start();
    }

    public void onHistoryClick(VideoCard video, int position) {
        if (video == null) return;
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra("aid", video.aid);
        intent.putExtra("bvid", video.bvid);
        startActivity(intent);
    }

    private void loadMoreHistory() {
        if (mHasError || isLoading || isEnd) return;

        if (!isNetworkAvailable()) {
            return;
        }

        isLoading = true;
        footerProgressBar.setVisibility(View.VISIBLE);
        footerView.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            public void run() {
                try {
                    Log.d(TAG, "loadMoreHistory - 加载更多");
                    final HistoryApi.HistoryResult historyResult = HistoryApi.getHistory(lastResult);

                    final ApiResult result = historyResult.apiResult;
                    final List<VideoCard> newItems = historyResult.newItems;

                    Log.d(TAG, "loadMoreHistory - code: " + result.code + ", isBottom: " + result.isBottom + ", newItems: " + newItems.size());

                    mainHandler.post(new Runnable() {
                        public void run() {
                            footerProgressBar.setVisibility(View.GONE);
                            isLoading = false;

                            if (result.code == 0) {
                                // 在 UI 线程合并数据
                                videoList.addAll(newItems);
                                adapter.notifyDataSetChanged();
                                lastResult = result;
                                mHasError = false;

                                if (result.isBottom) {
                                    isEnd = true;
                                    footerView.setVisibility(View.GONE);
                                    Toast.makeText(HistoryActivity.this, getString(R.string.emoticon__no_more_data), Toast.LENGTH_SHORT).show();
                                } else {
                                    footerView.setVisibility(View.VISIBLE);
                                }
                            } else {
                                footerView.setVisibility(View.GONE);
                                mHasError = true;
                                String msg = result.message;
                                if (msg == null || msg.length() == 0) {
                                    msg = "加载失败";
                                }
                                if (msg.contains("banned") || msg.contains("request was banned")) {
                                    msg = "请求过于频繁，请稍后重试";
                                }
                                emptyView.setText(msg + "\n点击重试");
                                emptyView.setVisibility(View.VISIBLE);
                                historyList.setVisibility(View.GONE);
                                Toast.makeText(HistoryActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "loadMoreHistory - 异常: ", e);
                    mainHandler.post(new Runnable() {
                        public void run() {
                            footerProgressBar.setVisibility(View.GONE);
                            isLoading = false;
                            footerView.setVisibility(View.GONE);
                            mHasError = true;
                            String errMsg = e.getMessage();
                            if (errMsg == null || errMsg.length() == 0) {
                                errMsg = "加载失败";
                            }
                            if (errMsg.contains("banned") || errMsg.contains("request was banned")) {
                                errMsg = "请求过于频繁，请稍后重试";
                            }
                            emptyView.setText(errMsg + "\n点击重试");
                            emptyView.setVisibility(View.VISIBLE);
                            historyList.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }).start();
    }
}