package tv.biliclassic.metro;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

import tv.biliclassic.FavoriteVideoListActivity;
import tv.biliclassic.R;
import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * Metro 主题下的"我的收藏"页（作为 MetroHome 内的 Fragment，与推荐/历史同构）。
 * 列出收藏夹（透明行），点击进入 Classic 收藏视频列表页（按 fid）。
 * 翻入/翻出时条目错峰滑入/滑出；夜间模式灰字换白。
 */
public class MetroFavoriteFragment extends Fragment implements MetroTurnPage {

    private ListView mFolderList;
    private TextView mTvLoading;
    private SwipeRefreshLayout mSwipeRefresh;
    private ArrayList<FavoriteFolder> mFolders = new ArrayList<FavoriteFolder>();
    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private FolderAdapter mAdapter;
    private boolean mIsLoading = false;
    private boolean mFirstLoad = true;
    private boolean mHasError = false;

    // 视图重建时恢复的滚动位置
    private int mSavedPosition = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.metro_favorite, container, false);

        mFolderList = (ListView) root.findViewById(R.id.folder_list);
        mTvLoading = (TextView) root.findViewById(R.id.tv_loading);
        // 夜间模式：灰色提示文字换白色
        mTvLoading.setTextColor(MetroTheme.grey());

        // API 3: 移除 SwipeRefreshLayout（引起 Layout.draw 递归），与推荐页处理一致
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
            SwipeRefreshLayout srl = (SwipeRefreshLayout) root.findViewById(R.id.swipe_refresh_metro);
            if (srl != null) {
                ListView list = (ListView) srl.findViewById(R.id.folder_list);
                ViewGroup parent = (ViewGroup) srl.getParent();
                if (parent != null && list != null) {
                    int idx = parent.indexOfChild(srl);
                    // list 目前还是 srl 的子 View，必须先摘掉才能挂到 parent 上，
                    // 否则 addView 抛 "child already has a parent"
                    ViewGroup listParent = (ViewGroup) list.getParent();
                    if (listParent != null) listParent.removeView(list);
                    parent.removeView(srl);
                    parent.addView(list, idx, srl.getLayoutParams());
                }
            }
        }

        mAdapter = new FolderAdapter();
        mFolderList.setAdapter(mAdapter);

        // 下拉刷新
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 4) {
            mSwipeRefresh = (SwipeRefreshLayout) root.findViewById(R.id.swipe_refresh_metro);
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        refresh();
                    }
                });
            }
        }

        // 状态文本点击：失败时重试
        mTvLoading.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHasError) {
                    refresh();
                }
            }
        });

        mFolderList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }
        });

        mFolderList.post(new Runnable() {
            @Override
            public void run() {
                mFolderList.setSelection(mSavedPosition);
                loadFolders();
            }
        });

        return root;
    }

    private void loadFolders() {
        // 已加载（重挂载）则不重复请求
        if (mFolders.size() > 0) {
            stopRefreshing();
            rebuildList();
            return;
        }
        if (mIsLoading) return;

        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid <= 0) {
            mTvLoading.setText(getString(R.string.not_logged_in_yet));
            mFirstLoad = false;
            stopRefreshing();
            return;
        }

        mIsLoading = true;
        mHasError = false;

        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<FavoriteFolder> folders = null;
                try {
                    // getFavoriteFoldersFast 返回 ArrayList<FavoriteFolder>（原始类型，这里统一转换）
                    ArrayList raw = FavoriteApi.getFavoriteFoldersFast(
                            SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
                    folders = new ArrayList<FavoriteFolder>();
                    if (raw != null) {
                        for (int i = 0; i < raw.size(); i++) {
                            Object o = raw.get(i);
                            if (o instanceof FavoriteFolder) {
                                folders.add((FavoriteFolder) o);
                            }
                        }
                    }
                } catch (final Exception e) {
                    folders = null;
                }
                final ArrayList<FavoriteFolder> result = folders;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (getActivity() == null || getView() == null) {
                            mIsLoading = false;
                            stopRefreshing();
                            return;
                        }
                        mIsLoading = false;
                        mFirstLoad = false;

                        if (result == null) {
                            mHasError = true;
                            mTvLoading.setText(getString(R.string.load_failed_tap_retry) + "\n点击重试");
                            stopRefreshing();
                            return;
                        }

                        mFolders.clear();
                        mFolders.addAll(result);

                        if (mFolders.size() == 0) {
                            mTvLoading.setText(getString(R.string.no_favorite_folders_2));
                            mTvLoading.setVisibility(View.VISIBLE);
                            mFolderList.setVisibility(View.GONE);
                        } else {
                            mTvLoading.setVisibility(View.GONE);
                            mFolderList.setVisibility(View.VISIBLE);
                            mAdapter.notifyDataSetChanged();
                            loadCoversInBackground();
                        }
                        stopRefreshing();
                    }
                });
            }
        }).start();
    }

    /** 后台拉取收藏夹封面（fid → 封面地址），完成后刷新列表 */
    private void loadCoversInBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);
                    if (mid == 0L || mFolders.size() == 0) return;

                    final HashMap coverMap = FavoriteApi.getCoverMap(mid);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null || mFolders.size() == 0) return;
                            for (int i = 0; i < mFolders.size(); i++) {
                                FavoriteFolder folder = mFolders.get(i);
                                String cover = (String) coverMap.get(new Long(folder.fid));
                                if (cover != null && cover.length() > 0 && !cover.equals(folder.cover)) {
                                    folder.cover = cover;
                                }
                            }
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (Exception e) {
                    // 封面加载失败不影响列表
                }
            }
        }).start();
    }

    /** 下拉刷新：清空重载 */
    private void refresh() {        if (mIsLoading) {
            stopRefreshing();
            return;
        }
        mHasError = false;
        mSavedPosition = 0;
        mFolders.clear();
        mAdapter.notifyDataSetChanged();
        mFolderList.setVisibility(View.GONE);
        mTvLoading.setVisibility(View.VISIBLE);
        mTvLoading.setText("正在加载…");
        loadFolders();
    }

    private void stopRefreshing() {
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    /** 已有数据被移走再挂回时：不重复请求，按 mFolders 重建。 */
    private void rebuildList() {
        if (mAdapter == null) return;
        mAdapter.notifyDataSetChanged();
        if (mFolderList != null) {
            if (mFolders.size() > 0) {
                mFolderList.setVisibility(View.VISIBLE);
                mFolderList.setSelection(mSavedPosition);
                mFolderList.invalidateViews();
            }
        }
        if (mTvLoading != null) {
            mTvLoading.setVisibility(mFolders.size() > 0 ? View.GONE : View.VISIBLE);
        }
    }

    // ===== 动画 =====
    // 收藏两级页面不做任何条目动画（翻入/翻出均无），只有整页转门

    /** MetroTurnPage：翻入（无条目动画） */
    public void animateTurnIn() {
    }

    /** MetroTurnPage：翻出（无条目动画） */
    public void animateTurnOut() {
    }

    @Override
    public void onPause() {
        super.onPause();
        tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
    }

    @Override
    public void onDestroyView() {
        if (mFolderList != null && mAdapter != null) {
            mSavedPosition = mFolderList.getFirstVisiblePosition();
        }
        super.onDestroyView();
    }

    // ===== 收藏夹列表适配器（透明行，点击进 Classic 收藏视频列表） =====

    private class FolderAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mFolders.size();
        }

        @Override
        public Object getItem(int position) {
            return mFolders.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.item_metro_folder, parent, false);
                holder = new ViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.folder_name);
                holder.count = (TextView) convertView.findViewById(R.id.folder_count);
                holder.cover = (ImageView) convertView.findViewById(R.id.cover);
                holder.badgePrivate = (TextView) convertView.findViewById(R.id.badge_private);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final FavoriteFolder folder = mFolders.get(position);
            if (folder == null) {
                return convertView;
            }

            // 夜间模式：灰/深色文字换白色系
            holder.name.setTextColor(MetroTheme.dark());
            holder.count.setTextColor(MetroTheme.grey());

            holder.name.setText(folder.name != null && folder.name.length() > 0
                    ? folder.name : "未命名收藏夹");
            holder.count.setText(folder.videoCount + " 个内容");

            // 私密标记：封面右上角灰色方块
            holder.badgePrivate.setVisibility(folder.isPrivate ? View.VISIBLE : View.GONE);

            tv.biliclassic.util.ImageLoader.bind(holder.cover, folder.cover,
                    R.drawable.bili_default_image_tv_with_bg, 112, 66);

            // 点击直接挂在行 View 上：转门翻入收藏夹视频页（标题=收藏夹名）
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() instanceof MetroHomeActivity) {
                        ((MetroHomeActivity) getActivity()).openFavoriteFolder(folder.fid, folder.name);
                    } else if (getActivity() != null) {
                        // 非 MetroHome 容器（理论不会发生）跳 Classic 收藏视频页
                        Intent intent = new Intent(getActivity(), FavoriteVideoListActivity.class);
                        intent.putExtra("fid", folder.fid);
                        intent.putExtra("name", folder.name);
                        startActivity(intent);
                    }
                }
            });

            return convertView;
        }

        private class ViewHolder {
            TextView name;
            TextView count;
            ImageView cover;
            TextView badgePrivate;
        }
    }
}
