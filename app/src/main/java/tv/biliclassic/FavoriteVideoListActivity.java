package tv.biliclassic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class FavoriteVideoListActivity extends BaseActivity {

    private ListView listView;
    private TextView emptyView;
    private TextView titleText;
    private View footerView;
    private android.widget.ProgressBar footerProgressBar;

    private FavoriteVideoAdapter adapter;
    private ArrayList<VideoCard> videoList = new ArrayList<VideoCard>();

    private long fid;
    private String folderName;
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;

    private Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;
    private int longPressPosition = -1;
    private boolean isLongPressTriggered = false;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int MAX_RETRY = 1;
    private int retryCount = 0;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_video_list);

        Intent intent = getIntent();
        fid = intent.getLongExtra("fid", 0L);
        folderName = intent.getStringExtra("name");

        titleText = (TextView) findViewById(R.id.title_text);
        if (folderName != null) {
            titleText.setText(folderName);
        }

        listView = (ListView) findViewById(R.id.list_view);
        emptyView = (TextView) findViewById(R.id.empty_view);

        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerProgressBar = (android.widget.ProgressBar) footerView.findViewById(R.id.footer_progress);
        listView.addFooterView(footerView);
        footerView.setVisibility(View.GONE);

        adapter = new FavoriteVideoAdapter(this, videoList);
        adapter.setOnDeleteClickListener(new FavoriteVideoAdapter.OnDeleteClickListener() {
            @Override
            public void onDeleteClick(int position) {
                showDeleteConfirm(position);
            }
        });
        listView.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0 && totalCount >= 30) {
                        loadMoreVideos();
                    }
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && totalItemCount > 0 && totalItemCount >= 30) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreVideos();
                    }
                }
            }
        });

        loadVideos();
    }

    public void onVideoClick(VideoCard video, int position) {
        if (video == null) return;
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra("aid", video.aid);
        intent.putExtra("bvid", video.bvid);
        startActivity(intent);
    }

    private void showDeleteConfirm(final int position) {
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle("提示")
                .setMessage("确定要从收藏夹中删除该视频吗？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteVideo(position);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLoading() {
        View headerContainer = findViewById(R.id.header_container);
        if (headerContainer != null) {
            headerContainer.setVisibility(View.VISIBLE);
            headerContainer.requestLayout();
            headerContainer.invalidate();
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (footerView != null) {
            footerView.setVisibility(View.GONE);
        }
        listView.setVisibility(View.VISIBLE);
    }

    private void hideAllLoading() {
        View headerContainer = findViewById(R.id.header_container);
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
    }

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

    private void showNoNetwork() {
        hideAllLoading();
        emptyView.setText(getString(R.string.emoticon__no_network));
        emptyView.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
    }

    private void showLoadError() {
        hideAllLoading();
        emptyView.setText(getString(R.string.emoticon__failed_need_retry));
        emptyView.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
    }

    private void deleteVideo(final int position) {
        if (fid == 0) {
            Toast.makeText(this, "收藏夹ID无效，无法删除", Toast.LENGTH_SHORT).show();
            return;
        }

        final VideoCard video = videoList.get(position);
        if (video == null) {
            Toast.makeText(this, "视频信息无效", Toast.LENGTH_SHORT).show();
            return;
        }

        NetWorkUtil.refreshHeaders();

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        String savedCsrf = SharedPreferencesUtil.getString("csrf", "");
        Log.e("FavoriteVideo", "===== 删除调试 =====");
        Log.e("FavoriteVideo", "Cookie: " + (cookies == null ? "null" : cookies));
        Log.e("FavoriteVideo", "保存的 csrf: " + savedCsrf);
        Log.e("FavoriteVideo", "aid: " + video.aid + ", fid: " + fid);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = FavoriteApi.deleteFavorite(video.aid, null, fid);
                    Log.e("FavoriteVideo", "删除结果: " + result);
                    mainHandler.post(new Runnable() {
                        public void run() {
                            if (result == 0) {
                                Toast.makeText(FavoriteVideoListActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                videoList.remove(position);
                                adapter.notifyDataSetChanged();

                                Intent broadcastIntent = new Intent();
                                broadcastIntent.setAction("tv.biliclassic.FAVORITE_CHANGED");
                                sendBroadcast(broadcastIntent);

                                if (videoList.size() == 0) {
                                    emptyView.setText("暂无收藏视频");
                                    emptyView.setVisibility(View.VISIBLE);
                                    footerView.setVisibility(View.GONE);
                                    setResult(RESULT_OK);
                                    finish();
                                } else {
                                    setResult(RESULT_OK);
                                }
                            } else if (result == -401) {
                                Toast.makeText(FavoriteVideoListActivity.this, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(FavoriteVideoListActivity.this, "删除失败，错误码: " + result, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e("FavoriteVideo", "删除异常: ", e);
                    mainHandler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(FavoriteVideoListActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void loadVideos() {
        retryCount = 0;
        doLoadVideos();
    }

    private void doLoadVideos() {
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        if (mid == 0L) {
            emptyView.setText("请先登录的说~");
            emptyView.setVisibility(View.VISIBLE);
            footerView.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
            return;
        }

        NetWorkUtil.refreshHeaders();

        if (!isNetworkAvailable()) {
            showNoNetwork();
            return;
        }

        isLoading = true;
        showLoading();
        emptyView.setVisibility(View.GONE);
        footerView.setVisibility(View.GONE);
        footerProgressBar.setVisibility(View.GONE);
        currentPage = 1;
        isEnd = false;
        videoList.clear();

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = FavoriteApi.getFolderVideos(mid, fid, currentPage, videoList);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            isLoading = false;
                            hideAllLoading();
                            footerProgressBar.setVisibility(View.GONE);

                            if (videoList.size() == 0) {
                                emptyView.setText("暂无收藏视频");
                                emptyView.setVisibility(View.VISIBLE);
                                footerView.setVisibility(View.GONE);
                                isEnd = true;
                                listView.setVisibility(View.GONE);
                            } else {
                                adapter.notifyDataSetChanged();
                                retryCount = 0;
                                listView.setVisibility(View.VISIBLE);
                                emptyView.setVisibility(View.GONE);

                                if (videoList.size() < 30) {
                                    isEnd = true;
                                    footerView.setVisibility(View.GONE);
                                } else {
                                    if (result == 1) {
                                        isEnd = true;
                                        footerView.setVisibility(View.GONE);
                                    } else {
                                        footerView.setVisibility(View.VISIBLE);
                                        currentPage++;
                                    }
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        public void run() {
                            isLoading = false;
                            hideAllLoading();
                            footerView.setVisibility(View.GONE);
                            if (retryCount < MAX_RETRY && isNetworkAvailable()) {
                                retryCount++;
                                doLoadVideos();
                            } else {
                                showLoadError();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void loadMoreVideos() {
        if (isLoading || isEnd) return;
        if (videoList.size() == 0) return;

        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.emoticon__no_network), Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        footerProgressBar.setVisibility(View.VISIBLE);
        footerView.setVisibility(View.VISIBLE);

        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = FavoriteApi.getFolderVideos(mid, fid, currentPage, videoList);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            footerProgressBar.setVisibility(View.GONE);
                            isLoading = false;

                            if (result == 1) {
                                isEnd = true;
                                footerView.setVisibility(View.GONE);
                                if (videoList.size() > 0) {
                                    Toast.makeText(FavoriteVideoListActivity.this, getString(R.string.emoticon__no_more_data), Toast.LENGTH_SHORT).show();
                                }
                            } else if (result == 0) {
                                adapter.notifyDataSetChanged();
                                currentPage++;
                                footerView.setVisibility(View.VISIBLE);
                            } else {
                                footerView.setVisibility(View.GONE);
                                Toast.makeText(FavoriteVideoListActivity.this, "加载更多失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        public void run() {
                            footerProgressBar.setVisibility(View.GONE);
                            isLoading = false;
                            footerView.setVisibility(View.GONE);
                            Toast.makeText(FavoriteVideoListActivity.this, "加载更多失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        longPressHandler.removeCallbacks(longPressRunnable);
        if (adapter != null) {
            adapter.clearCache();
        }
    }
}