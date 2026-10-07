package tv.biliclassic;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.AbsListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import tv.biliclassic.api.BangumiApi;
import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.api.WatchLaterApi;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.BroadcastConstants;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.widget.LoadingBarView;

public class FavoriteFolderListActivity extends BaseActivity {

    private static final int REQUEST_VIDEO_LIST = 1001;

    private ListView listView;
    private TextView emptyView;

    private FavoriteFolderAdapter adapter;
    private List<FavoriteFolder> folderList = new ArrayList<FavoriteFolder>();

    private List<FavoriteFolder> cachedFolders = null;
    private boolean isLoading = false;
    private boolean dataLoaded = false;

    private static final int MAX_RETRY = 1;
    private int retryCount = 0;

    // OK 键按住到该 repeat 次数视为长按 → 删除收藏夹（模仿 FollowingListActivity）
    private static final int OK_LONG_PRESS_REPEAT = 20;
    private boolean mOkLongPressed = false;

    // 0=收藏夹 1=稍后再看 2=追番
    private int mTab = 0;
    private RelatedVideosAdapter videoAdapter;   // 稍后再看
    private RelatedVideosAdapter bangumiAdapter; // 追番
    private RelatedVideosAdapter activeVideoAdapter;
    private final List<VideoCard> videoList = new ArrayList<VideoCard>();
    private boolean videoEnd = false;

    // 广播接收器
    private BroadcastReceiver favoriteChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 收藏夹数据发生变化，刷新列表
            loadFolders(true);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_folder_list);
        initRoundTitleBar();

        listView = (ListView) findViewById(R.id.list_view);
        emptyView = (TextView) findViewById(R.id.empty_view);

        adapter = new FavoriteFolderAdapter(this, folderList);
        adapter.setOnFolderLongClickListener(new FavoriteFolderAdapter.OnFolderLongClickListener() {
            @Override
            public void onFolderLongClick(int position) {
                if (position >= 0 && position < folderList.size()) {
                    confirmDeleteFolder(folderList.get(position));
                }
            }
        });
        listView.setAdapter(adapter);

        // 隐藏原生 selector，避免覆盖自定义光标高亮（粉色）
        listView.setSelector(android.R.color.transparent);
        listView.setCacheColorHint(0x00000000);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    adapter.setScrolling(false);
                    if (videoAdapter != null) videoAdapter.setScrolling(false);
                    if (bangumiAdapter != null) bangumiAdapter.setScrolling(false);
                    // 滚动结束：保持高亮隐藏，等待再次按键恢复
                } else {
                    adapter.setScrolling(true);
                    adapter.setHideHighlight(true);
                    if (videoAdapter != null) {
                        videoAdapter.setScrolling(true);
                        videoAdapter.setHideHighlight(true);
                    }
                    if (bangumiAdapter != null) {
                        bangumiAdapter.setScrolling(true);
                        bangumiAdapter.setHideHighlight(true);
                    }
                    // 开始触摸滚动/甩动：隐藏光标高亮
                }
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 点击标题刷新
        TextView title = (TextView) findViewById(R.id.title_text);
        if (title != null) {
            title.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadFolders(true);
                }
            });
        }

        RelatedVideosAdapter.OnVideoClickListener click = new RelatedVideosAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(VideoCard video, int position) {
                openCard(video);
            }
        };
        videoAdapter = new RelatedVideosAdapter(this, videoList, R.layout.item_history);
        videoAdapter.setOnVideoClickListener(click);
        bangumiAdapter = new RelatedVideosAdapter(this, videoList, R.layout.item_bangumi_follow);
        bangumiAdapter.setOnVideoClickListener(click);

        selectTab(0);
    }

    /** 顶栏「更多」= 切换 收藏视频/稍后再看/我的追番 */
    @Override
    protected boolean includeDefaultOverflowMenu() {
        return false;
    }

    @Override
    protected int onAppendOverflowItems(int[] ids, String[] titles, int n) {
        if (n < ids.length) { ids[n] = R.id.menu_fav_create_folder; titles[n] = getString(R.string.fav_create_folder); n++; }
        if (n < ids.length) { ids[n] = R.id.menu_fav_video; titles[n] = getString(R.string.fav_tab_videos); n++; }
        if (n < ids.length) { ids[n] = R.id.menu_fav_later; titles[n] = getString(R.string.fav_tab_watch_later); n++; }
        if (n < ids.length) { ids[n] = R.id.menu_fav_bangumi; titles[n] = getString(R.string.fav_tab_bangumi); n++; }
        return n;
    }

    @Override
    protected void onMenuAction(int id) {
        if (id == R.id.menu_fav_create_folder) { showCreateFolderDialog(); return; }
        if (id == R.id.menu_fav_video) { selectTab(0); return; }
        if (id == R.id.menu_fav_later) { selectTab(1); return; }
        if (id == R.id.menu_fav_bangumi) { selectTab(2); return; }
        super.onMenuAction(id);
    }

    // ===== 长按删除收藏夹 =====
    private void confirmDeleteFolder(final FavoriteFolder folder) {
        if (folder == null) return;
        new android.app.AlertDialog.Builder(
                tv.biliclassic.util.SdkHelper.dialogContext(tv.biliclassic.util.DialogUtil.wrap(this)))
                .setTitle(getString(R.string.fav_delete_folder_title))
                .setMessage(getString(R.string.fav_delete_folder_confirm, folder.name))
                .setPositiveButton(getString(R.string.common_delete), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        doDeleteFolder(folder);
                    }
                })
                .setNegativeButton(getString(R.string.videodetail_cancel), null)
                .show();
    }

    private void doDeleteFolder(final FavoriteFolder folder) {
        // 删收藏夹用媒体 id（mlid）：短 fid 会被接口当成无效、返回 code:0 但什么都不删
        final long fid = folder.id != 0 ? folder.id : folder.fid;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int code;
                try {
                    code = FavoriteApi.deleteFolder(fid);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(FavoriteFolderListActivity.this,
                                    "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (code == 0) {
                            Toast.makeText(FavoriteFolderListActivity.this, getString(R.string.fav_folder_deleted), Toast.LENGTH_SHORT).show();
                            loadFolders(true);
                        } else {
                            Toast.makeText(FavoriteFolderListActivity.this,
                                    "删除失败，错误码: " + code, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    // ===== 创建收藏夹 =====
    private void showCreateFolderDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.fav_folder_name_hint));
        input.setSingleLine(true);
        new android.app.AlertDialog.Builder(
                tv.biliclassic.util.SdkHelper.dialogContext(tv.biliclassic.util.DialogUtil.wrap(this)))
                .setTitle(getString(R.string.fav_create_folder_title))
                .setView(input)
                .setPositiveButton(getString(R.string.common_create), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) return;
                        doCreateFolder(name);
                    }
                })
                .setNegativeButton(getString(R.string.videodetail_cancel), null)
                .show();
    }

    private void doCreateFolder(final String name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int code;
                try {
                    code = FavoriteApi.createFolder(name, 0);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(FavoriteFolderListActivity.this,
                                    "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (code == 0) {
                            Toast.makeText(FavoriteFolderListActivity.this, getString(R.string.fav_folder_created), Toast.LENGTH_SHORT).show();
                            loadFolders(true);
                        } else {
                            Toast.makeText(FavoriteFolderListActivity.this,
                                    "创建失败，错误码: " + code, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void selectTab(int idx) {
        mTab = idx;
        selectedPosition = -1;
        if (idx == 0) {
            activeVideoAdapter = null;
            listView.setAdapter(adapter);
            loadFolders(false);
        } else {
            activeVideoAdapter = (idx == 1) ? videoAdapter : bangumiAdapter;
            videoList.clear();
            activeVideoAdapter.notifyDataSetChanged();
            listView.setAdapter(activeVideoAdapter);
            loadVideoTab();
        }
    }

    private void loadVideoTab() {
        videoList.clear();
        if (activeVideoAdapter != null) activeVideoAdapter.notifyDataSetChanged();
        videoEnd = false;
        showLoading();
        final int tab = mTab;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<VideoCard> got = new ArrayList<VideoCard>();
                try {
                    if (tab == 1) {
                        WatchLaterApi.getWatchLaterList(got);
                    } else {
                        int rc = BangumiApi.getFollowingList(1, got);
                        if (rc == -1) throw new java.io.IOException("未登录");
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mTab != tab) return;
                            hideAllLoading();
                            emptyView.setText(getString(R.string.emoticon__failed_need_retry));
                            emptyView.setVisibility(View.VISIBLE);
                            listView.setVisibility(View.GONE);
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTab != tab) return;
                        hideAllLoading();
                        videoEnd = got.size() < 15;
                        if (tab == 2) {
                            for (VideoCard c : got) {
                                if (c != null) c.upName = "番剧";
                            }
                        }
                        videoList.addAll(got);
                        if (activeVideoAdapter != null) activeVideoAdapter.notifyDataSetChanged();
                        if (videoList.size() == 0) {
                            emptyView.setText(getString(R.string.no_data));
                            emptyView.setVisibility(View.VISIBLE);
                            listView.setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            listView.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }).start();
    }

    private void openCard(VideoCard card) {
        if (card == null) return;
        Intent intent = new Intent(this, VideoDetailActivity.class);
        if ("media_bangumi".equals(card.type)) {
            intent.putExtra("bangumi_media_id", card.aid);
        } else if (card.aid != 0) {
            intent.putExtra("aid", card.aid);
        } else if (card.bvid != null && card.bvid.length() > 0) {
            intent.putExtra("bvid", card.bvid);
        } else {
            return;
        }
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注册广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(BroadcastConstants.ACTION_FAVORITE_CHANGED);
        registerReceiver(favoriteChangeReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 注销广播
        try {
            unregisterReceiver(favoriteChangeReceiver);
        } catch (Exception e) {}
    }

    public void onFolderClick(FavoriteFolder folder, int position) {
        if (folder == null) return;
        Intent intent = new Intent(FavoriteFolderListActivity.this, FavoriteVideoListActivity.class);
        intent.putExtra("fid", folder.fid);
        intent.putExtra("name", folder.name);
        startActivityForResult(intent, REQUEST_VIDEO_LIST);
    }

    // ===== 遥控器按键导航（模仿 RelatedVideosFragment） =====
    private int selectedPosition = -1;

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
            mOkLongPressed = false;
        }
        int count = (mTab == 0) ? folderList.size() : videoList.size();
        if (count == 0 || listView == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_PAGE_UP
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_PAGE_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        // 按键恢复：取消触摸滑动时的隐藏，重新显示光标
        if (mTab == 0) {
            if (adapter != null) adapter.setHideHighlight(false);
        } else {
            if (activeVideoAdapter != null) activeVideoAdapter.setHideHighlight(false);
        }
        // OK 键长按（收藏夹 Tab）：删除选中收藏夹；只触发一次
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM
                && event.getRepeatCount() >= OK_LONG_PRESS_REPEAT) {
            if (!mOkLongPressed) {
                mOkLongPressed = true;
                if (mTab == 0 && selectedPosition >= 0 && selectedPosition < folderList.size()) {
                    FavoriteFolder folder = folderList.get(selectedPosition);
                    if (folder != null) {
                        confirmDeleteFolder(folder);
                    }
                }
            }
            return true;
        }
        // 首次按下才移动光标；长按 repeat 只消费不移动
        if (event.getRepeatCount() == 0) {
            if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_PAGE_UP) {
                selectedPosition = pageMove(-1, count);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_PAGE_DOWN) {
                selectedPosition = pageMove(1, count);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
                if (mTab == 0) {
                    FavoriteFolder folder = folderList.get(selectedPosition);
                    if (folder != null) {
                        onFolderClick(folder, selectedPosition);
                    }
                } else {
                    openCard(videoList.get(selectedPosition));
                }
                return true;
            }
            applySelection();
        }
        return true;
    }

    private int pageMove(int direction, int count) {
        if (listView == null) {
            return selectedPosition;
        }
        int first = listView.getFirstVisiblePosition();
        int last = listView.getLastVisiblePosition();
        int visibleCount = Math.max(1, last - first + 1);
        int newPos = selectedPosition + direction * visibleCount;
        if (newPos < 0) {
            newPos = 0;
        } else if (newPos >= count) {
            newPos = count - 1;
        }
        return newPos;
    }

    private void applySelection() {
        if (mTab == 0) {
            if (adapter != null) adapter.setSelectedPosition(selectedPosition);
        } else {
            if (activeVideoAdapter != null) activeVideoAdapter.setSelectedPosition(selectedPosition);
        }
        if (listView != null) {
            // setSelection 为 API 1，兼容 Android 2.x；smoothScrollToPosition 需 API 8
            listView.setSelection(selectedPosition);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_LIST && resultCode == RESULT_OK) {
            loadFolders(true);
        }
    }

    private void showLoading() {
        LoadingBarView bar = (LoadingBarView) findViewById(R.id.progress_bar);
        if (bar != null) {
            bar.bindBackground(listView);
            bar.showLoading();
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        // 列表显示，但可能为空
        listView.setVisibility(View.VISIBLE);
    }

    private void hideAllLoading() {
        LoadingBarView bar = (LoadingBarView) findViewById(R.id.progress_bar);
        if (bar != null) {
            bar.hide();
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

    private void loadFolders(final boolean forceRefresh) {
        retryCount = 0;
        doLoadFolders(forceRefresh);
    }

    private void doLoadFolders(final boolean forceRefresh) {
        if (isLoading) {
            return;
        }

        if (!forceRefresh && dataLoaded && cachedFolders != null && cachedFolders.size() > 0) {
            if (folderList.size() != cachedFolders.size()) {
                folderList.clear();
                folderList.addAll(cachedFolders);
                adapter.notifyDataSetChanged();
            }
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            return;
        }

        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        if (mid == 0L) {
            emptyView.setText(getString(R.string.favorites_need_login));
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            return;
        }

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null || cookies.length() == 0) {
            emptyView.setText(getString(R.string.please_login_first));
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            Toast.makeText(this, this.getString(R.string.please_login_first), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!NetWorkUtil.isNetworkAvailable(FavoriteFolderListActivity.this)) {
            showNoNetwork();
            return;
        }

        isLoading = true;
        showLoading();

        NetWorkUtil.refreshHeaders();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArrayList result = FavoriteApi.getFavoriteFoldersFast(mid);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            hideAllLoading();

                            if (result != null && result.size() > 0) {
                                cachedFolders = new ArrayList<FavoriteFolder>(result);
                                dataLoaded = true;
                                retryCount = 0;

                                folderList.clear();
                                folderList.addAll(cachedFolders);
                                adapter.notifyDataSetChanged();
                                loadCoversInBackground();
                                listView.setVisibility(View.VISIBLE);
                                emptyView.setVisibility(View.GONE);

                                if (forceRefresh) {
                                    Toast.makeText(FavoriteFolderListActivity.this, FavoriteFolderListActivity.this.getString(R.string.refresh_ok), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                emptyView.setText(getString(R.string.no_favorite_folders));
                                emptyView.setVisibility(View.VISIBLE);
                                listView.setVisibility(View.GONE);
                                folderList.clear();
                                adapter.notifyDataSetChanged();
                                if (forceRefresh) {
                                    Toast.makeText(FavoriteFolderListActivity.this, FavoriteFolderListActivity.this.getString(R.string.no_folders), Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            hideAllLoading();
                            if (retryCount < MAX_RETRY && NetWorkUtil.isNetworkAvailable(FavoriteFolderListActivity.this)) {
                                retryCount++;
                                doLoadFolders(forceRefresh);
                            } else {
                                showLoadError();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void loadCoversInBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);
                    if (mid == 0L || folderList.size() == 0) {
                        return;
                    }

                    final HashMap coverMap = FavoriteApi.getCoverMap(mid);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (folderList.size() == 0) {
                                return;
                            }

                            boolean updated = false;
                            for (int i = 0; i < folderList.size(); i++) {
                                FavoriteFolder folder = folderList.get(i);
                                String cover = (String) coverMap.get(new Long(folder.fid));
                                if (cover != null && cover.length() > 0 && !cover.equals(folder.cover)) {
                                    folder.cover = cover;
                                    updated = true;
                                }
                            }

                            if (updated) {
                                if (cachedFolders != null) {
                                    cachedFolders.clear();
                                    cachedFolders.addAll(folderList);
                                }
                                adapter.notifyDataSetChanged();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}