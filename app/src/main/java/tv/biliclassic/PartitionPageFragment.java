package tv.biliclassic;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.PartitionApi;
import tv.biliclassic.model.VideoCard;

public class PartitionPageFragment extends Fragment {

    private static final String ARG_TID = "tid";

    private ScrollView scrollView;
    private ExpandableGridView gridView;
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

        gridView = (ExpandableGridView) view.findViewById(R.id.recommend_grid);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);
        footerContainer = (LinearLayout) view.findViewById(R.id.footer_container);
        footerProgressBar = (ProgressBar) view.findViewById(R.id.footer_progress);
        scrollView = (ScrollView) view.findViewById(R.id.scroll_view);

        footerContainer.setVisibility(View.GONE);

        int numColumns = isTablet() ? (isLandscape() ? 4 : 3) : 2;
        gridView.setNumColumns(numColumns);
        gridView.setVerticalSpacing(dpToPx(8));
        gridView.setHorizontalSpacing(dpToPx(8));
        gridView.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

        adapter = new RecommendGridAdapter(getActivity(), videoList);
        adapter.setNumColumns(numColumns);
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= videoList.size()) return;
                VideoCard item = videoList.get(position);
                if (item == null || getActivity() == null) return;
                Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
                if (item.aid != 0) {
                    intent.putExtra("aid", item.aid);
                } else if (item.bvid != null && item.bvid.length() > 0) {
                    intent.putExtra("bvid", item.bvid);
                } else {
                    Toast.makeText(getActivity(), "无法获取视频信息", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(intent);
            }
        });

        gridView.setFocusable(false);
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);
        scrollView.requestFocus();

        scrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    checkScrollToBottom();
                }
                return false;
            }
        });

        scrollView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                checkScrollToBottom();
                            }
                        }, 150);
                    }
                }
                return false;
            }
        });

        if (isFirstLoad) {
            loadVideos();
        }

        return view;
    }

    private void checkScrollToBottom() {
        if (scrollView == null) return;
        View child = scrollView.getChildAt(0);
        if (child != null) {
            int scrollY = scrollView.getScrollY();
            int height = child.getHeight();
            int scrollViewHeight = scrollView.getHeight();
            if (scrollY + scrollViewHeight >= height - 30) {
                loadMoreVideos();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getActivity() == null) return;
        int numColumns = isTablet() ? (isLandscape() ? 4 : 3) : 2;
        gridView.setNumColumns(numColumns);
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
        footerContainer.setVisibility(View.VISIBLE);
        if (footerProgressBar != null) {
            footerProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideFooter() {
        footerContainer.setVisibility(View.GONE);
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
