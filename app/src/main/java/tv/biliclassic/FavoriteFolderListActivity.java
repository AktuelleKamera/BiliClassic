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

import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.util.BroadcastConstants;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;

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
                    // 滚动结束：保持高亮隐藏，等待再次按键恢复
                } else {
                    adapter.setScrolling(true);
                    // 开始触摸滚动/甩动：隐藏光标高亮
                    adapter.setHideHighlight(true);
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

        loadFolders(false);
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
        if (folderList == null || folderList.size() == 0 || listView == null) {
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
            int count = folderList.size();
            if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_2) {
                selectedPosition = pageMove(-1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_8) {
                selectedPosition = pageMove(1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
                FavoriteFolder folder = folderList.get(selectedPosition);
                if (folder != null) {
                    onFolderClick(folder, selectedPosition);
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
        int count = folderList.size();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_LIST && resultCode == RESULT_OK) {
            loadFolders(true);
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
        // 列表显示，但可能为空
        listView.setVisibility(View.VISIBLE);
    }

    private void hideAllLoading() {
        View headerContainer = findViewById(R.id.header_container);
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
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
            emptyView.setText(getString(R.string.favoritefolderlistactivity_settext_767b));
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            return;
        }

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null || cookies.length() == 0) {
            emptyView.setText(getString(R.string.favoritefolderlistactivity_settext_8bf7));
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            Toast.makeText(this, this.getString(R.string.favoritefolderlistactivity_toast_8bf7), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
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
                                    Toast.makeText(FavoriteFolderListActivity.this, FavoriteFolderListActivity.this.getString(R.string.favoritefolderlistactivity_toast_5237), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                emptyView.setText(getString(R.string.favoritefolderlistactivity_settext_6682));
                                emptyView.setVisibility(View.VISIBLE);
                                listView.setVisibility(View.GONE);
                                folderList.clear();
                                adapter.notifyDataSetChanged();
                                if (forceRefresh) {
                                    Toast.makeText(FavoriteFolderListActivity.this, FavoriteFolderListActivity.this.getString(R.string.favoritefolderlistactivity_toast_6ca1), Toast.LENGTH_SHORT).show();
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
                            if (retryCount < MAX_RETRY && isNetworkAvailable()) {
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