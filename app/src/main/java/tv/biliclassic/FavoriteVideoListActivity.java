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
import tv.biliclassic.util.SharedPreferencesUtil;

public class FavoriteVideoListActivity extends BaseActivity {

    private ListView listView;
    private TextView emptyView;
    private TextView titleText;
    private View footerView;
    private android.widget.ProgressBar footerProgressBar;

    private FavoriteVideoAdapter adapter;
    private List<VideoCard> videoList = new ArrayList<VideoCard>();

    private long fid;
    private String folderName;
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        listView.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (videoList == null || position >= videoList.size()) {
                    Toast.makeText(FavoriteVideoListActivity.this, "数据加载中，请稍后", Toast.LENGTH_SHORT).show();
                    return;
                }
                VideoCard video = videoList.get(position);
                if (video == null) {
                    return;
                }

                Intent intent = new Intent(FavoriteVideoListActivity.this, VideoDetailActivity.class);
                if (video.aid != 0L) {
                    intent.putExtra("aid", video.aid);
                } else if (video.bvid != null && video.bvid.length() > 0) {
                    intent.putExtra("bvid", video.bvid);
                } else {
                    Toast.makeText(FavoriteVideoListActivity.this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView parent, View view, int position, long id) {
                if (videoList == null || position >= videoList.size()) {
                    return true;
                }
                showDeleteConfirm(position);
                return true;
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0) {
                        loadMoreVideos();
                    }
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreVideos();
                    }
                }
            }
        });

        loadVideos();
    }

    private void showDeleteConfirm(final int position) {
        new AlertDialog.Builder(this)
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

    private void deleteVideo(final int position) {
        final VideoCard video = videoList.get(position);

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
                    final int result = FavoriteApi.deleteFavorite(video.aid, fid);
                    Log.e("FavoriteVideo", "删除结果: " + result);
                    mainHandler.post(new Runnable() {
                        public void run() {
                            if (result == 0) {
                                Toast.makeText(FavoriteVideoListActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                videoList.remove(position);
                                adapter.notifyDataSetChanged();
                                if (videoList.size() == 0) {
                                    emptyView.setText("暂无收藏视频");
                                    emptyView.setVisibility(View.VISIBLE);
                                    footerView.setVisibility(View.GONE);
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
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        if (mid == 0L) {
            emptyView.setText("请先登录");
            emptyView.setVisibility(View.VISIBLE);
            footerView.setVisibility(View.GONE);
            return;
        }

        NetWorkUtil.refreshHeaders();

        isLoading = true;
        emptyView.setVisibility(View.GONE);
        footerView.setVisibility(View.VISIBLE);
        footerProgressBar.setVisibility(View.VISIBLE);
        currentPage = 1;
        isEnd = false;
        videoList.clear();

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = FavoriteApi.getFolderVideos(mid, fid, currentPage, (ArrayList<VideoCard>) videoList);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            isLoading = false;
                            footerProgressBar.setVisibility(View.GONE);

                            if (videoList.size() == 0) {
                                emptyView.setText("暂无收藏视频");
                                emptyView.setVisibility(View.VISIBLE);
                                footerView.setVisibility(View.GONE);
                            } else {
                                adapter.notifyDataSetChanged();
                                if (result == 1) {
                                    isEnd = true;
                                    footerView.setVisibility(View.GONE);
                                } else {
                                    footerView.setVisibility(View.VISIBLE);
                                    currentPage++;
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        public void run() {
                            isLoading = false;
                            footerView.setVisibility(View.GONE);
                            emptyView.setText("加载失败: " + e.getMessage());
                            emptyView.setVisibility(View.VISIBLE);
                            Toast.makeText(FavoriteVideoListActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void loadMoreVideos() {
        if (isLoading || isEnd) return;
        isLoading = true;

        footerProgressBar.setVisibility(View.VISIBLE);
        footerView.setVisibility(View.VISIBLE);

        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = FavoriteApi.getFolderVideos(mid, fid, currentPage, (ArrayList<VideoCard>) videoList);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            footerProgressBar.setVisibility(View.GONE);
                            isLoading = false;

                            if (result == 1) {
                                isEnd = true;
                                footerView.setVisibility(View.GONE);
                                Toast.makeText(FavoriteVideoListActivity.this, "已经到底啦", Toast.LENGTH_SHORT).show();
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

    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.clearCache();
        }
    }
}