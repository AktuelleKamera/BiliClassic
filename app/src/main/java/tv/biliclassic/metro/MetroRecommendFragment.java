package tv.biliclassic.metro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.R;
import tv.biliclassic.VideoDetailActivity;
import tv.biliclassic.api.RecommendApi;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;

/**
 * Metro 主题下的"推荐视频"页（作为 MetroHome 内的 Fragment）。
 * 磁贴以 2 列布局用 ListView 虚拟化（convertView 复用），只构建/绑定可见行，
 * 比 ScrollView 一次性 inflate 全部磁贴更省；保留错峰入场/出场动画。
 */
public class MetroRecommendFragment extends Fragment implements MetroTurnPage {

    private ListView mTileList;
    private TextView mTvLoading;
    private SwipeRefreshLayout mSwipeRefresh;
    private List<VideoCard> mVideoList = new ArrayList<VideoCard>();
    private android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean mIsLoading = false;
    private boolean mIsEnd = false;
    private boolean mFirstLoad = true;
    private int mPage = 1;
    private ExecutorService mImageExecutor;
    private TileRowAdapter mTileAdapter;
    // 视图重建时恢复的滚动位置（避免再次进入"推荐/个人中心"时被顶回顶部）
    private int mSavedPosition = 0;
    // 本次"加载更多"新内容的起始行（用于滚动到新内容开头 + 为新行播放错峰滑入）
    private int mLoadMoreStartRow = -1;
    // 已触发过滑入动画的新行位置（避免重复播放）
    private final java.util.Set<Integer> mAnimatedLoadMoreRows = new java.util.HashSet<Integer>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.metro_recommend, container, false);

        // 封面加载线程池：按"图片加载线程数"设置创建（与其它页面一致）
        int loadThreads = tv.biliclassic.util.SdkHelper.getImageLoadThreads();
        if (loadThreads <= 1) {
            mImageExecutor = Executors.newSingleThreadExecutor();
        } else {
            mImageExecutor = Executors.newFixedThreadPool(loadThreads);
        }

        mTileList = (ListView) root.findViewById(R.id.tile_list);
        mTvLoading = (TextView) root.findViewById(R.id.tv_loading);
        // 夜间模式：灰色提示文字换白色
        mTvLoading.setTextColor(MetroTheme.grey());

        // API 3: 移除 SwipeRefreshLayout（引起 Layout.draw 递归），与推荐页处理一致
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
            SwipeRefreshLayout srl = (SwipeRefreshLayout) root.findViewById(R.id.swipe_refresh_metro);
            if (srl != null) {
                ListView list = (ListView) srl.findViewById(R.id.tile_list);
                ViewGroup parent = (ViewGroup) srl.getParent();
                if (parent != null && list != null) {
                    int idx = parent.indexOfChild(srl);
                    parent.removeView(srl);
                    parent.addView(list, idx, srl.getLayoutParams());
                }
            }
        }

        mTileAdapter = new TileRowAdapter();
        mTileList.setAdapter(mTileAdapter);

        // 下拉刷新（与正常版推荐一致）
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

        // 滚动接近底部自动加载下一页（虚拟化列表复用行）
        mTileList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    checkLoadMore();
                }
            }
        });

        mTileList.post(new Runnable() {
            @Override
            public void run() {
                mTileList.setSelection(mSavedPosition);
                loadRecommend();
            }
        });

        return root;
    }

    private void checkLoadMore() {
        if (mTileList == null || mIsLoading || mIsEnd) return;
        int last = mTileList.getLastVisiblePosition();
        if (last >= mTileAdapter.getCount() - 2) {
            loadMore();
        }
    }

    /** 已有数据被移走再挂回时：新视图树为空，不重复下载，按 mVideoList 重建。 */
    private void rebuildTiles() {
        if (mTileAdapter == null) return;
        mFirstLoad = false;
        mTileAdapter.notifyDataSetChanged();
        if (mTileList != null) {
            mTileList.setVisibility(View.VISIBLE);
            mTileList.setSelection(mSavedPosition);
            // 重挂载后也强制可见行重新绑定，避免残留旧内容
            mTileList.invalidateViews();
        }
        if (mTvLoading != null) mTvLoading.setVisibility(View.GONE);
    }

    private void loadRecommend() {
        // 已加载（预加载/重挂载）则不重复请求，直接按已有数据重建磁贴
        if (mVideoList.size() > 0) {
            stopRefreshing();
            rebuildTiles();
            return;
        }
        mIsLoading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    RecommendApi.getRecommend(items, 1, 0);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                mIsLoading = false;
                                stopRefreshing();
                                return;
                            }
                            if (items.size() == 0) {
                                mTvLoading.setText(getString(R.string.no_recommendations));
                                mIsLoading = false;
                                stopRefreshing();
                                return;
                            }
                            mVideoList.addAll(items);
                            mTvLoading.setVisibility(View.GONE);
                            mTileList.setVisibility(View.VISIBLE);
                            mTileAdapter.notifyDataSetChanged();
                            mFirstLoad = false;
                            mIsLoading = false;
                            mPage = 1;
                            stopRefreshing();
                            // 强制可见行立即重新绑定，否则刷新后需滑动一下才看到更新
                            mTileList.invalidateViews();
                            mTileList.post(new Runnable() {
                                @Override
                                public void run() {
                                    animateAllRows();
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                mIsLoading = false;
                                stopRefreshing();
                                return;
                            }
                            if (mVideoList.size() == 0) {
                                mTvLoading.setText(getString(R.string.load_failed));
                            }
                            mIsLoading = false;
                            stopRefreshing();
                        }
                    });
                }
            }
        }).start();
    }

    /** 下拉刷新：清空并重载第一页，回到顶部。 */
    private void refresh() {
        if (mIsLoading) {
            stopRefreshing();
            return;
        }
        mIsEnd = false;
        mPage = 1;
        mSavedPosition = 0;
        mVideoList.clear();
        if (mTileAdapter != null) mTileAdapter.notifyDataSetChanged();
        loadRecommend();
    }

    private void stopRefreshing() {
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    private void loadMore() {
        if (mIsLoading || mIsEnd) return;
        mIsLoading = true;
        mPage++;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    RecommendApi.getPopular(items, mPage);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                mIsLoading = false;
                                return;
                            }
                            if (items.size() == 0) {
                                mIsEnd = true;
                            } else {
                                int oldCount = mVideoList.size();
                                mVideoList.addAll(items);
                                // 新内容起始行 = 旧行数 / 2（每行两个磁贴）
                                mLoadMoreStartRow = oldCount / 2;
                                mTileAdapter.notifyDataSetChanged();
                            }
                            mIsLoading = false;
                            // 不自动滚动：用户停留原地，继续往下滑即可看到新内容；
                            // 每行在滚到该行时由 getView 触发一次错峰滑入。
                            mAnimatedLoadMoreRows.clear();
                        }
                    });
                } catch (final Exception e) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                mIsLoading = false;
                                return;
                            }
                            mIsEnd = true;
                            mIsLoading = false;
                        }
                    });
                }
            }
        }).start();
    }

    /** 清除可见行上残留的动画，避免"上一个滑入未播完"被 startAnimation 直接跳到末帧。 */
    private void clearRowAnimations() {
        if (mTileList == null) return;
        int count = mTileList.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = mTileList.getChildAt(i);
            if (row == null) continue;
            row.clearAnimation();
            row.setVisibility(View.VISIBLE);
        }
    }

    /** 首次加载：先清残留动画，再对可见行做错峰滑入（老机用适中位移）。 */
    private void animateAllRows() {
        if (mTileList == null) return;
        clearRowAnimations();
        int count = mTileList.getChildCount();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int travel = Math.max(screenWidth / 3, 80);

        for (int i = 0; i < count; i++) {
            final View row = mTileList.getChildAt(i);
            if (row == null) continue;
            row.setVisibility(View.VISIBLE);

            final Animation a;
            if (tv.biliclassic.util.SdkHelper.getSdkInt() < 16) {
                a = new TranslateAnimation(travel, 0, 0, 0);
                a.setDuration(350);
                a.setStartOffset(i * 120);
                a.setInterpolator(new DecelerateInterpolator());
            } else {
                a = new TranslateAnimation(screenWidth, 0, 0, 0);
                a.setDuration(350);
                a.setStartOffset(i * 120);
                a.setInterpolator(new DecelerateInterpolator());
            }
            row.startAnimation(a);
        }
    }

    private int tileSizePx() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int tileSizePx = (screenWidth - 48) / 2;
        if (tileSizePx <= 0) tileSizePx = screenWidth / 2;
        return tileSizePx;
    }

    /** MetroTurnPage：翻入时磁贴错峰滑入。 */
    public void animateTurnIn() {
        if (mTileList == null) return;
        clearRowAnimations();
        int count = mTileList.getChildCount();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int travel = Math.max(screenWidth / 3, 80);
        for (int i = 0; i < count; i++) {
            final View row = mTileList.getChildAt(i);
            if (row == null) continue;
            row.setVisibility(View.VISIBLE);
            final Animation a;
            if (tv.biliclassic.util.SdkHelper.getSdkInt() < 16) {
                a = new TranslateAnimation(travel, 0, 0, 0);
                a.setDuration(280);
                a.setStartOffset(i * 70);
                a.setInterpolator(new DecelerateInterpolator());
            } else {
                a = new TranslateAnimation(screenWidth, 0, 0, 0);
                a.setDuration(280);
                a.setStartOffset(i * 70);
                a.setInterpolator(new DecelerateInterpolator());
            }
            row.startAnimation(a);
        }
    }

    /** MetroTurnPage：翻出时磁贴错峰滑出。 */
    public void animateTurnOut() {
        if (mTileList == null) return;
        clearRowAnimations();
        int count = mTileList.getChildCount();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int travel = Math.max(screenWidth / 3, 80);
        for (int i = 0; i < count; i++) {
            final View row = mTileList.getChildAt(i);
            if (row == null) continue;
            row.setVisibility(View.VISIBLE);
            final Animation a;
            if (tv.biliclassic.util.SdkHelper.getSdkInt() < 16) {
                a = new TranslateAnimation(0, travel, 0, 0);
                a.setDuration(240);
                a.setStartOffset(i * 50);
                a.setInterpolator(new DecelerateInterpolator());
                a.setFillAfter(true);
            } else {
                a = new TranslateAnimation(0, screenWidth, 0, 0);
                a.setDuration(240);
                a.setStartOffset(i * 50);
                a.setInterpolator(new DecelerateInterpolator());
                a.setFillAfter(true);
            }
            row.startAnimation(a);
        }
    }

    /** 加载封面：先查 GlobalImageCache，未命中则抛到图片线程池下载并用缓存保存（不手动回收位图）。 */
    private void loadCover(final ImageView iv, final String url) {
        if (iv == null) return;
        if (url == null || url.length() == 0) {
            iv.setImageResource(R.drawable.bili_default_image_tv_with_bg_flat);
            return;
        }
        final String finalUrl = url;
        iv.setTag(finalUrl);

        Bitmap cached = GlobalImageCache.getInstance().get(finalUrl);
        if (cached != null && !cached.isRecycled()) {
            iv.setImageBitmap(cached);
            return;
        }
        // 无缓存：先重置为占位图，避免复用行残留上一张封面（旧图短暂闪现/重复覆盖）
        iv.setImageResource(R.drawable.bili_default_image_tv_with_bg_flat);
        if (mImageExecutor == null || mImageExecutor.isShutdown()) return;

        mImageExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = downloadCover(finalUrl);
                if (bmp == null || bmp.isRecycled()) return;
                GlobalImageCache.getInstance().put(finalUrl, bmp);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (iv.getTag() == null || !finalUrl.equals(iv.getTag())) return;
                        iv.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private Bitmap downloadCover(String urlStr) {
        android.content.Context ctx = getActivity();
        if (ctx == null) return null;
        return ImageLoader.fetchBitmap(ctx, urlStr, 160, 90);
    }

    @Override
    public void onPause() {
        super.onPause();
        tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
    }

    @Override
    public void onDestroyView() {
        // 视图即将销毁（切走/被替换）：保存当前滚动位置，重新进入时恢复
        if (mTileList != null && mTileAdapter != null) {
            mSavedPosition = mTileList.getFirstVisiblePosition();
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mImageExecutor != null) {
            mImageExecutor.shutdownNow();
            mImageExecutor = null;
        }
    }

    /** 每行两列磁贴，ListView 虚拟化复用行。 */
    private class TileRowAdapter extends BaseAdapter {
        private final int mColPx;
        private final LayoutInflater mInflater;

        TileRowAdapter() {
            mInflater = LayoutInflater.from(getActivity());
            mColPx = tileSizePx();
        }

        @Override
        public int getCount() {
            return (mVideoList.size() + 1) / 2;
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CellViews c;
            if (convertView == null) {
                convertView = buildRow();
            }
            c = (CellViews) convertView.getTag();
            if (convertView.getAnimation() != null) convertView.clearAnimation();
            convertView.setVisibility(View.VISIBLE);

            // 加载更多后的新行：滚到该行时做一次错峰滑入（行内位置作为延迟）
            if (mLoadMoreStartRow >= 0 && position >= mLoadMoreStartRow
                    && !mAnimatedLoadMoreRows.contains(Integer.valueOf(position))) {
                mAnimatedLoadMoreRows.add(Integer.valueOf(position));
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int travel = Math.max(screenWidth / 3, 80);
                int rowIndex = position - mLoadMoreStartRow;
                final Animation a;
                if (tv.biliclassic.util.SdkHelper.getSdkInt() < 16) {
                    a = new TranslateAnimation(travel, 0, 0, 0);
                    a.setDuration(300);
                    a.setStartOffset(rowIndex * 60);
                    a.setInterpolator(new DecelerateInterpolator());
                } else {
                    a = new TranslateAnimation(screenWidth, 0, 0, 0);
                    a.setDuration(300);
                    a.setStartOffset(rowIndex * 60);
                    a.setInterpolator(new DecelerateInterpolator());
                }
                convertView.startAnimation(a);
            }

            int left = position * 2;
            int right = left + 1;
            if (left < mVideoList.size()) {
                bindTile(c.left, mVideoList.get(left));
                c.left.setVisibility(View.VISIBLE);
            } else {
                c.left.setVisibility(View.GONE);
            }
            if (right < mVideoList.size()) {
                bindTile(c.right, mVideoList.get(right));
                c.right.setVisibility(View.VISIBLE);
            } else {
                c.right.setVisibility(View.GONE);
            }
            return convertView;
        }

        private View buildRow() {
            LinearLayout row = new LinearLayout(mTileList.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, 4, 0, 4);

            CellViews c = new CellViews();
            c.left = createTile(row);
            c.right = createTile(row);
            c.root = row;
            row.addView(c.left);
            row.addView(c.right);
            row.setTag(c);
            return row;
        }

        private View createTile(ViewGroup parent) {
            View cell = mInflater.inflate(R.layout.item_recommend, parent, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mColPx, mColPx);
            lp.setMargins(4, 4, 4, 4);
            cell.setLayoutParams(lp);
            cell.setClickable(true);
            cell.setFocusable(true);
            return cell;
        }

        private void bindTile(View cell, final VideoCard card) {
            ImageView cover = (ImageView) cell.findViewById(R.id.cover);
            TextView title = (TextView) cell.findViewById(R.id.title);
            TextView viewTv = (TextView) cell.findViewById(R.id.view);
            TextView danmakuTv = (TextView) cell.findViewById(R.id.danmaku);

            // 夜间模式：磁贴本体换成深灰色（与黑色背景形成对比），标题恢复白色文字
            if (MetroTheme.isNight()) {
                cell.setBackgroundResource(R.drawable.item_click_effect_grey);
                View coverContainer = cell.findViewById(R.id.cover_container);
                if (coverContainer != null) {
                    coverContainer.setBackgroundColor(0xFF484848);
                }
                title.setTextColor(0xFFF2F2F2);
            }

            title.setText(card.title != null ? card.title : "");
            viewTv.setText(card.view != null ? card.view : "0");
            danmakuTv.setText(card.danmaku > 0 ? String.valueOf(card.danmaku) : "0");
            loadCover(cover, card.cover);

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) return;
                    Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
                    if (card.aid != 0) {
                        intent.putExtra("aid", card.aid);
                    } else if (card.bvid != null && card.bvid.length() > 0) {
                        intent.putExtra("bvid", card.bvid);
                    }
                    startActivity(intent);
                }
            });
        }

        private class CellViews {
            View root;
            View left;
            View right;
        }
    }
}
