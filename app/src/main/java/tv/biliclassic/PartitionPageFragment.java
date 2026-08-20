package tv.biliclassic;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.PartitionApi;
import tv.biliclassic.model.VideoCard;

public class PartitionPageFragment extends Fragment {

    private static final String ARG_TID = "tid";

    private ListView gridView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private LinearLayout footerContainer;
    private ProgressBar footerProgressBar;

    private RecommendGridAdapter adapter;
    private List<VideoCard> videoList = new ArrayList<VideoCard>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private int tid;
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;
    private boolean isFirstLoad = true;

    public static PartitionPageFragment newInstance(int tid) {
        PartitionPageFragment fragment = new PartitionPageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TID, tid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tid = getArguments().getInt(ARG_TID, 1);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recommend, container, false);

        // 移除 SwipeRefreshLayout（分区不需要下拉刷新，留著會因 mListener==null 閃退）
        SwipeRefreshLayout srl = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh);
        ViewGroup parent = (ViewGroup) view.findViewById(R.id.recommend_content);
        ListView grid = (ListView) view.findViewById(R.id.recommend_grid);
        if (srl != null && parent != null && grid != null) {
            ViewGroup gParent = (ViewGroup) grid.getParent();
            if (gParent != null) gParent.removeView(grid);
            int idx = parent.indexOfChild(srl);
            parent.removeView(srl);
            parent.addView(grid, idx, srl.getLayoutParams());
        }

        gridView = (ListView) view.findViewById(R.id.recommend_grid);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        hideFooter();

        int numColumns = isTablet() ? (isLandscape() ? 4 : 3) : 2;
        gridView.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        gridView.setClipToPadding(false);
        gridView.setVerticalFadingEdgeEnabled(false);
        gridView.setHorizontalFadingEdgeEnabled(false);
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 9) {
            tv.biliclassic.util.SdkHelper.setOverScrollNever(gridView);
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

        // 滚动停止时若接近底部则自动加载更多（ListView 自带虚拟化，只构建可见项）
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

        if (isFirstLoad) {
            loadVideos();
        }

        return view;
    }

    private void checkScrollToBottom() {
        if (gridView == null) return;
        int total = gridView.getCount();
        if (total <= 0) return;
        int last = gridView.getLastVisiblePosition();
        if (last >= total - 2) {
            loadMoreVideos();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getActivity() == null) return;
        int numColumns = isTablet() ? (isLandscape() ? 4 : 3) : 2;
        adapter.setNumColumns(numColumns);
    }

    private boolean isTablet() {
        return getResources().getBoolean(R.bool.is_tablet);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
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

    private void loadVideos() {
        if (!isFirstLoad && videoList.size() > 0) return;
        isFirstLoad = false;

        currentPage = 1;
        isEnd = false;

        if (getActivity() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        gridView.setVisibility(View.GONE);
        hideFooter();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    PartitionApi.getRegionVideos(items, tid, currentPage);

                    if (getActivity() == null) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null) return;
                            progressBar.setVisibility(View.GONE);
                            if (items == null || items.size() == 0) {
                                emptyView.setVisibility(View.VISIBLE);
                                gridView.setVisibility(View.GONE);
                                hideFooter();
                                return;
                            }
                            videoList.clear();
                            videoList.addAll(items);
                            adapter.notifyDataSetChanged();
                            gridView.setVisibility(View.VISIBLE);
                            currentPage = 2;
                            hideFooter();
                            if (items.size() < 20) isEnd = true;

                            // 内容不足一屏时自动补页
                            gridView.post(new Runnable() {
                                @Override
                                public void run() {
                                    checkScrollToBottom();
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    if (getActivity() == null) return;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null) return;
                            progressBar.setVisibility(View.GONE);
                            emptyView.setText("加载失败: " + e.getMessage());
                            emptyView.setVisibility(View.VISIBLE);
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void loadMoreVideos() {
        if (isLoading || isEnd || videoList.size() == 0) return;
        isLoading = true;
        showFooter();

        final int page = currentPage;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> newItems = new ArrayList<VideoCard>();
                    PartitionApi.getRegionVideos(newItems, tid, page);

                    if (getActivity() == null) {
                        hideFooter();
                        isLoading = false;
                        return;
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null) {
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
                            adapter.notifyDataSetChanged();
                            currentPage = page + 1;
                            if (newItems.size() < 20) isEnd = true;

                            gridView.post(new Runnable() {
                                @Override
                                public void run() {
                                    checkScrollToBottom();
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideFooter();
                            isLoading = false;
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
