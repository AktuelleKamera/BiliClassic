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

    // 用户是否已主动滚动过（首次加载后不自动触发加载更多，防止不足一屏时立即翻页触发风控）
    private boolean mUserScrolled = false;

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

        // 隐藏原生 selector，避免覆盖自定义光标高亮（粉色）
        listView.setSelector(android.R.color.transparent);
        listView.setCacheColorHint(0x00000000);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    adapter.setScrolling(false);
                    // 滚动结束：保持高亮隐藏，等待再次按键恢复
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (mUserScrolled && lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0 && totalCount >= 30) {
                        loadMoreVideos();
                    }
                } else {
                    // 用户开始滚动：之后才允许滚动到底部时加载更多
                    mUserScrolled = true;
                    adapter.setScrolling(true);
                    // 开始触摸滚动/甩动：隐藏光标高亮
                    adapter.setHideHighlight(true);
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (mUserScrolled && !isLoading && !isEnd && totalItemCount > 0 && totalItemCount >= 30) {
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

    // ===== 遥控器按键导航（模仿 RelatedVideosFragment） =====
    private int selectedPosition = -1;

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (videoList == null || videoList.size() == 0 || listView == null) {
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
                    onVideoClick(item, selectedPosition);
                }
                return true;
            }
            applySelection();
        }
        return true;
    }

    private int pageMove(int direction) {
        if (listView == null) {
            return selectedPosition;
        }
        int first = listView.getFirstVisiblePosition();
        int last = listView.getLastVisiblePosition();
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
        if (listView != null) {
            // setSelection 为 API 1，兼容 Android 2.x；smoothScrollToPosition 需 API 8
            listView.setSelection(selectedPosition);
        }
    }

    private void showDeleteConfirm(final int position) {
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.favoritevideolistactivity_settitle_63d0))
                .setMessage(getString(R.string.favoritevideolistactivity_setmessage_786e))
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
            Toast.makeText(this, this.getString(R.string.favoritevideolistactivity_toast_6536), Toast.LENGTH_SHORT).show();
            return;
        }

        final VideoCard video = videoList.get(position);
        if (video == null) {
            Toast.makeText(this, this.getString(R.string.favoritevideolistactivity_toast_89c6), Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(FavoriteVideoListActivity.this, FavoriteVideoListActivity.this.getString(R.string.favoritevideolistactivity_toast_5220), Toast.LENGTH_SHORT).show();
                                videoList.remove(position);
                                adapter.notifyDataSetChanged();

                                Intent broadcastIntent = new Intent();
                                broadcastIntent.setAction("tv.biliclassic.FAVORITE_CHANGED");
                                sendBroadcast(broadcastIntent);

                                if (videoList.size() == 0) {
                                    emptyView.setText(getString(R.string.favoritevideolistactivity_settext_6682));
                                    emptyView.setVisibility(View.VISIBLE);
                                    footerView.setVisibility(View.GONE);
                                    setResult(RESULT_OK);
                                    finish();
                                } else {
                                    setResult(RESULT_OK);
                                }
                            } else if (result == -401) {
                                Toast.makeText(FavoriteVideoListActivity.this, FavoriteVideoListActivity.this.getString(R.string.favoritevideolistactivity_toast_767b), Toast.LENGTH_SHORT).show();
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
            emptyView.setText(getString(R.string.favoritevideolistactivity_settext_8bf7));
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
                                emptyView.setText(getString(R.string.favoritevideolistactivity_settext_6682));
                                emptyView.setVisibility(View.VISIBLE);
                                footerView.setVisibility(View.GONE);
                                isEnd = true;
                                listView.setVisibility(View.GONE);
                            } else {
                                adapter.notifyDataSetChanged();
                                retryCount = 0;
                                listView.setVisibility(View.VISIBLE);
                                emptyView.setVisibility(View.GONE);
                                // 首次加载完成：聚焦第一个视频，暂不自动加载更多（防止不足一屏立即翻页触发风控）
                                mUserScrolled = false;
                                listView.setSelection(0);

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
                                Toast.makeText(FavoriteVideoListActivity.this, FavoriteVideoListActivity.this.getString(R.string.favoritevideolistactivity_toast_52a0), Toast.LENGTH_SHORT).show();
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