package tv.biliclassic;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.RecommendApi;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.SharedPreferencesUtil;

public class RecommendFragment extends Fragment {

    private ListView gridView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private LinearLayout footerContainer;
    private ProgressBar footerProgressBar;
    private View headerContainer;

    private RecommendGridAdapter adapter;
    private List<VideoCard> videoList = new ArrayList<VideoCard>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private SwipeRefreshLayout swipeRefreshLayout;

    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;
    private int savedGridPos = -1;

    private static final String STATE_GRID_POS = "grid_pos";

    private void showToast(String msg) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recommend, container, false);

        // API 3: 移除 SwipeRefreshLayout（引起 Layout.draw 递归），直接使用 ListView
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
            android.support.v4.widget.SwipeRefreshLayout srl = (android.support.v4.widget.SwipeRefreshLayout)
                    view.findViewById(R.id.swipe_refresh);
            ViewGroup parent = (ViewGroup) view.findViewById(R.id.recommend_content);
            ListView grid = (ListView) view.findViewById(R.id.recommend_grid);
            if (srl != null && parent != null && grid != null) {
                int idx = parent.indexOfChild(srl);
                ViewGroup gParent = (ViewGroup) grid.getParent();
                if (gParent != null) gParent.removeView(grid);
                parent.removeView(srl);
                parent.addView(grid, idx, srl.getLayoutParams());
            }
        }

        gridView = (ListView) view.findViewById(R.id.recommend_grid);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);
        headerContainer = view.findViewById(R.id.header_container);

        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
        hideFooter();

        int numColumns = isTablet() ? (isLandscape() ? 4 : 3) : 2;
        gridView.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        gridView.setClipToPadding(false);
        gridView.setVerticalFadingEdgeEnabled(false);
        gridView.setHorizontalFadingEdgeEnabled(false);
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 9) {
            tv.biliclassic.util.SdkHelper.setOverScrollNever(gridView);
        }
        // 绘制缓存（仅 32MB+ 堆设备）：滑页转场命中缓存，避免每帧重绘全部行
        if (tv.biliclassic.util.SdkHelper.isHighMemoryDevice()) {
            gridView.setDrawingCacheEnabled(true);
            gridView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);
        }

        // footer 直接加在列表内容之后（addFooterView，随列表滚动）
        View footer = inflater.inflate(R.layout.item_recommend_footer, gridView, false);
        footerContainer = (LinearLayout) footer;
        footerProgressBar = (ProgressBar) footer.findViewById(R.id.footer_progress);
        gridView.addFooterView(footer);

        adapter = new RecommendGridAdapter(getActivity(), videoList);
        adapter.setNumColumns(numColumns);
        gridView.setAdapter(adapter);

        gridView.setFocusable(true);
        gridView.setFocusableInTouchMode(true);

        // 滚动停止时若接近底部则自动加载更多（GridView 自带虚拟化，只构建可见项）
        gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    adapter.setScrolling(false);
                    checkScrollToBottom();
                } else {
                    adapter.setScrolling(true);
                }
            }
        });

        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 4) {
            swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh);
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        loadRecommend();
                    }
                });
            }
        }

        // 恢复滚动位置
        if (savedInstanceState != null) {
            savedGridPos = savedInstanceState.getInt(STATE_GRID_POS, -1);
        }

        loadRecommend();

        return view;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getActivity() == null) return;
        int numColumns = isTablet() ? (isLandscape() ? 4 : 3) : 2;
        adapter.setNumColumns(numColumns);
        int remainder = videoList.size() % numColumns;
        if (remainder > 0) {
            videoList.subList(videoList.size() - remainder, videoList.size()).clear();
        }
        adapter.notifyDataSetChanged();
        gridView.post(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (gridView != null) {
            outState.putInt(STATE_GRID_POS, gridView.getFirstVisiblePosition());
        }
    }

    private void checkScrollToBottom() {
        if (gridView == null) return;
        int total = gridView.getCount();
        if (total <= 0) return;
        int last = gridView.getLastVisiblePosition();
        if (last >= total - 2) {
            loadMoreRecommend();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private boolean isTablet() {
        return getResources().getBoolean(R.bool.is_tablet);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void showLoading() {
        if (headerContainer != null) {
            headerContainer.setVisibility(View.VISIBLE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (gridView != null) {
            gridView.setVisibility(View.GONE);
        }
        hideFooter();
    }

    private void hideAllLoading() {
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
    }

    private void stopRefreshing() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void showFooter() {
        if (footerContainer != null) {
            footerContainer.setVisibility(View.VISIBLE);
            if (footerProgressBar != null) {
                footerProgressBar.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideFooter() {
        if (footerContainer != null) {
            footerContainer.setVisibility(View.GONE);
        }
    }

    private void loadRecommend() {
        if (isLoading) return;
        isLoading = true;

        android.util.Log.d("RecommendDiag", "loadRecommend 开始, sdk=" + tv.biliclassic.util.SdkHelper.getSdkInt()
                + ", networkAvailable=" + isNetworkAvailable()
                + ", cookieLen=" + (SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "") == null ? -1 : SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "").length()));

        if (!isNetworkAvailable()) {
            android.util.Log.e("RecommendDiag", "loadRecommend: isNetworkAvailable()==false, 显示无网络");
            isLoading = false;
            hideAllLoading();
            emptyView.setText(getString(R.string.emoticon__no_network));
            emptyView.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
            return;
        }

        showLoading();
        currentPage = 1;
        isEnd = false;
        videoList.clear();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    android.util.Log.d("RecommendDiag", "开始调用 RecommendApi.getRecommend");
                    long t0 = System.currentTimeMillis();
                    RecommendApi.getRecommend(items);
                    android.util.Log.d("RecommendDiag", "RecommendApi.getRecommend 返回, 耗时=" + (System.currentTimeMillis() - t0) + "ms, items=" + (items == null ? -1 : items.size()));

                    if (getActivity() == null) {
                        isLoading = false;
                        return;
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                isLoading = false;
                                return;
                            }
                            hideAllLoading();
                            stopRefreshing();
                            isLoading = false;

                            if (items == null || items.size() == 0) {
                                android.util.Log.e("RecommendDiag", "推荐结果为空, 显示空视图");
                                if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                                if (gridView != null) gridView.setVisibility(View.GONE);
                                return;
                            }
                            videoList.clear();
                            videoList.addAll(items);
                            int cols = adapter.getNumColumns();
                            while (videoList.size() % cols != 0) {
                                videoList.remove(videoList.size() - 1);
                            }
                            adapter.notifyDataSetChanged();
                            gridView.setVisibility(View.VISIBLE);
                            currentPage = 2;

                            if (items.size() < 20) {
                                isEnd = true;
                            }

                            if (savedGridPos >= 0) {
                                final int restorePos = savedGridPos;
                                savedGridPos = -1;
                                gridView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        gridView.setSelection(restorePos);
                                    }
                                });
                            } else {
                                gridView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        gridView.setSelection(0);
                                        gridView.requestFocus();
                                    }
                                });
                            }

                            // 首页内容不足一屏时自动补页
                            gridView.post(new Runnable() {
                                @Override
                                public void run() {
                                    checkScrollToBottom();
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    android.util.Log.e("RecommendDiag", "推荐加载异常, 类型=" + e.getClass().getName()
                            + ", msg=" + e.getMessage()
                            + ", sdk=" + tv.biliclassic.util.SdkHelper.getSdkInt()
                            + ", networkAvailable=" + isNetworkAvailable(), e);
                    if (getActivity() == null) {
                        isLoading = false;
                        return;
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                isLoading = false;
                                return;
                            }
                            isLoading = false;
                            hideAllLoading();
                            stopRefreshing();
                            String msg = e.getMessage();
                            if (msg != null && (msg.contains("Unable to resolve host") || msg.contains("ConnectException") || msg.contains("SocketException") || msg.contains("timeout") || msg.contains("timed out"))) {
                                android.util.Log.e("RecommendDiag", "异常被判定为无网络: " + msg);
                                if (emptyView != null) emptyView.setText(getString(R.string.emoticon__no_network));
                            } else {
                                android.util.Log.e("RecommendDiag", "异常未判定为无网络: " + msg);
                                if (emptyView != null) emptyView.setText("加载失败: " + msg);
                            }
                            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getActivity().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isAvailable() && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    public void loadMoreRecommend() {
        if (isLoading || isEnd || videoList.size() == 0) return;

        isLoading = true;
        showFooter();

        final int page = currentPage;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> newItems = new ArrayList<VideoCard>();
                    RecommendApi.getRecommend(newItems);

                    if (getActivity() == null) {
                        hideFooter();
                        isLoading = false;
                        return;
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                hideFooter();
                                isLoading = false;
                                return;
                            }
                            hideFooter();
                            isLoading = false;

                            if (newItems == null || newItems.size() == 0) {
                                isEnd = true;
                                return;
                            }

                            videoList.addAll(newItems);
                            int cols = adapter.getNumColumns();
                            while (videoList.size() % cols != 0) {
                                videoList.remove(videoList.size() - 1);
                            }
                            adapter.notifyDataSetChanged();
                            currentPage = page + 1;

                            if (newItems.size() < 20) {
                                isEnd = true;
                                showToast("已经到底啦");
                            }

                            // 追加后仍不满一屏则继续补页
                            gridView.post(new Runnable() {
                                @Override
                                public void run() {
                                    checkScrollToBottom();
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    if (getActivity() == null) {
                        hideFooter();
                        isLoading = false;
                        return;
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                hideFooter();
                                isLoading = false;
                                return;
                            }
                            hideFooter();
                            isLoading = false;
                            showToast("加载更多失败: " + e.getMessage());
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.clearCache();
        }
    }
}
