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

    private Handler mainHandler = new Handler(Looper.getMainLooper());

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

        backBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
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
                    Toast.makeText(HistoryActivity.this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
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
                    }
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0) {
                        loadMoreHistory();
                    }
                } else {
                    if (adapter != null) {
                        adapter.setScrolling(true);
                    }
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && totalItemCount > 0) {
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

    private void loadHistory() {
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        Log.d(TAG, "loadHistory - cookies length: " + (cookies == null ? "null" : String.valueOf(cookies.length())));

        if (cookies == null || cookies.length() == 0) {
            Log.e(TAG, "loadHistory - 未登录");
            mainHandler.post(new Runnable() {
                public void run() {
                    progressBar.setVisibility(View.GONE);
                    emptyView.setText("请先登录");
                    emptyView.setVisibility(View.VISIBLE);
                }
            });
            return;
        }

        if (isLoading) return;
        isLoading = true;

        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
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
                    final ApiResult result = HistoryApi.getHistory(lastResult, videoList);

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
                            progressBar.setVisibility(View.GONE);
                            isLoading = false;

                            if (result.code == 0) {
                                lastResult = result;
                                adapter.notifyDataSetChanged();

                                if (result.isBottom) {
                                    isEnd = true;
                                    footerView.setVisibility(View.GONE);
                                    if (videoList.size() == 0) {
                                        emptyView.setText("暂无历史记录");
                                        emptyView.setVisibility(View.VISIBLE);
                                    }
                                } else {
                                    footerView.setVisibility(View.VISIBLE);
                                }

                                if (videoList.size() == 0) {
                                    emptyView.setText("暂无历史记录");
                                    emptyView.setVisibility(View.VISIBLE);
                                } else {
                                    emptyView.setVisibility(View.GONE);
                                }
                            } else {
                                String msg = result.message;
                                if (msg == null || msg.length() == 0) {
                                    msg = "错误码: " + result.code;
                                }
                                emptyView.setText(msg);
                                emptyView.setVisibility(View.VISIBLE);
                                Toast.makeText(HistoryActivity.this, msg, Toast.LENGTH_SHORT).show();
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
                            emptyView.setText(errMsg);
                            emptyView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }).start();
    }

    private void loadMoreHistory() {
        if (isLoading || isEnd) return;
        isLoading = true;

        footerProgressBar.setVisibility(View.VISIBLE);
        footerView.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            public void run() {
                try {
                    Log.d(TAG, "loadMoreHistory - 加载更多");
                    final ApiResult result = HistoryApi.getHistory(lastResult, videoList);

                    Log.d(TAG, "loadMoreHistory - code: " + result.code + ", isBottom: " + result.isBottom);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            footerProgressBar.setVisibility(View.GONE);
                            isLoading = false;

                            if (result.code == 0) {
                                lastResult = result;
                                adapter.notifyDataSetChanged();

                                if (result.isBottom) {
                                    isEnd = true;
                                    footerView.setVisibility(View.GONE);
                                    Toast.makeText(HistoryActivity.this, "已经到底啦", Toast.LENGTH_SHORT).show();
                                } else {
                                    footerView.setVisibility(View.VISIBLE);
                                }
                            } else {
                                footerView.setVisibility(View.GONE);
                                String msg = result.message;
                                if (msg == null || msg.length() == 0) {
                                    msg = "加载更多失败";
                                }
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
                            String errMsg = e.getMessage();
                            if (errMsg == null || errMsg.length() == 0) {
                                errMsg = "加载更多失败";
                            }
                            Toast.makeText(HistoryActivity.this, errMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}