package tv.biliclassic;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;

public class FavoriteFolderListActivity extends BaseActivity {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;

    private FavoriteFolderAdapter adapter;
    private List folderList = new ArrayList();

    private List cachedFolders = null;
    private long lastLoadTime = 0;
    private static final long CACHE_DURATION = 30 * 1000; // 30秒缓存
    private boolean isLoading = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_folder_list);

        listView = (ListView) findViewById(R.id.list_view);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        emptyView = (TextView) findViewById(R.id.empty_view);

        adapter = new FavoriteFolderAdapter(this, folderList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (position < 0 || position >= folderList.size()) {
                    return;
                }
                FavoriteFolder folder = (FavoriteFolder) folderList.get(position);
                if (folder == null) {
                    return;
                }
                Intent intent = new Intent(FavoriteFolderListActivity.this, FavoriteVideoListActivity.class);
                intent.putExtra("fid", folder.id);
                intent.putExtra("name", folder.name);
                startActivity(intent);
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        loadFolders();
    }

    private void loadFolders() {
        if (isLoading) {
            return;
        }

        long now = System.currentTimeMillis();
        if (cachedFolders != null && cachedFolders.size() > 0 && (now - lastLoadTime) < CACHE_DURATION) {
            folderList.clear();
            folderList.addAll(cachedFolders);
            adapter.notifyDataSetChanged();
            return;
        }

        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        if (mid == 0L) {
            progressBar.setVisibility(View.GONE);
            emptyView.setText("请先登录");
            emptyView.setVisibility(View.VISIBLE);
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null || cookies.length() == 0) {
            progressBar.setVisibility(View.GONE);
            emptyView.setText("请先登录");
            emptyView.setVisibility(View.VISIBLE);
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

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
                            progressBar.setVisibility(View.GONE);

                            if (result != null && result.size() > 0) {
                                cachedFolders = new ArrayList(result);
                                lastLoadTime = System.currentTimeMillis();

                                folderList.clear();
                                folderList.addAll(result);
                                adapter.notifyDataSetChanged();

                                // 后台加载封面
                                loadCoversInBackground(mid);
                            } else {
                                emptyView.setText("暂无收藏夹");
                                emptyView.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            progressBar.setVisibility(View.GONE);
                            emptyView.setText("加载失败: " + e.getMessage());
                            emptyView.setVisibility(View.VISIBLE);
                            Toast.makeText(FavoriteFolderListActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * 后台加载封面
     */
    private void loadCoversInBackground(final long mid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final HashMap coverMap = FavoriteApi.getCoverMap(mid);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            boolean updated = false;
                            for (int i = 0; i < folderList.size(); i++) {
                                FavoriteFolder folder = (FavoriteFolder) folderList.get(i);
                                String cover = (String) coverMap.get(new Long(folder.fid));
                                if (cover != null && cover.length() > 0 && !cover.equals(folder.cover)) {
                                    folder.cover = cover;
                                    updated = true;
                                }
                            }

                            if (updated) {
                                adapter.notifyDataSetChanged();
                                if (cachedFolders != null) {
                                    cachedFolders.clear();
                                    cachedFolders.addAll(folderList);
                                }
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
    protected void onResume() {
        super.onResume();
        loadFolders();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cachedFolders != null) {
            cachedFolders.clear();
            cachedFolders = null;
        }
    }
}