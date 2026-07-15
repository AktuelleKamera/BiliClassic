package tv.biliclassic;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
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

        listView = (ListView) findViewById(R.id.list_view);
        emptyView = (TextView) findViewById(R.id.empty_view);

        adapter = new FavoriteFolderAdapter(this, folderList);
        listView.setAdapter(adapter);

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
            emptyView.setText("登录以后才能访问收藏列表哦");
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            return;
        }

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null || cookies.length() == 0) {
            emptyView.setText("请先登录的说~");
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            Toast.makeText(this, "请先登录的说~", Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(FavoriteFolderListActivity.this, "刷新成功", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                emptyView.setText("暂无收藏夹");
                                emptyView.setVisibility(View.VISIBLE);
                                listView.setVisibility(View.GONE);
                                folderList.clear();
                                adapter.notifyDataSetChanged();
                                if (forceRefresh) {
                                    Toast.makeText(FavoriteFolderListActivity.this, "没有收藏夹", Toast.LENGTH_SHORT).show();
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