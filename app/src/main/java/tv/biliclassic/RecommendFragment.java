package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
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

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.RecommendApi;
import tv.biliclassic.model.VideoCard;

public class RecommendFragment extends Fragment {

    private ExpandableGridView gridView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private LinearLayout footerContainer;
    private ProgressBar footerProgressBar;
    private ScrollView scrollView;
    private View headerContainer;

    private RecommendGridAdapter adapter;
    private List<VideoCard> videoList = new ArrayList<VideoCard>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;

    private void showToast(String msg) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recommend, container, false);

        gridView = (ExpandableGridView) view.findViewById(R.id.recommend_grid);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);
        footerContainer = (LinearLayout) view.findViewById(R.id.footer_container);
        footerProgressBar = (ProgressBar) view.findViewById(R.id.footer_progress);
        scrollView = (ScrollView) view.findViewById(R.id.scroll_view);
        headerContainer = view.findViewById(R.id.header_container);

        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
        footerContainer.setVisibility(View.GONE);

        gridView.setNumColumns(2);
        gridView.setVerticalSpacing(dpToPx(8));
        gridView.setHorizontalSpacing(dpToPx(8));
        gridView.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

        adapter = new RecommendGridAdapter(getActivity(), videoList);
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                VideoCard item = videoList.get(position);
                if (item == null || getActivity() == null) return;
                Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
                if (item.aid != 0) {
                    intent.putExtra("aid", item.aid);
                } else if (item.bvid != null && item.bvid.length() > 0) {
                    intent.putExtra("bvid", item.bvid);
                } else {
                    showToast("无法获取视频信息");
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
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                            keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
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

        loadRecommend();

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
                loadMoreRecommend();
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
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
        showLoading();
        currentPage = 1;
        isEnd = false;
        videoList.clear();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    RecommendApi.getRecommend(items);

                    if (getActivity() == null) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null) return;
                            hideAllLoading();

                            if (items == null || items.size() == 0) {
                                emptyView.setVisibility(View.VISIBLE);
                                gridView.setVisibility(View.GONE);
                                return;
                            }
                            videoList.clear();
                            videoList.addAll(items);
                            adapter.notifyDataSetChanged();
                            gridView.setVisibility(View.VISIBLE);
                            currentPage = 2;

                            if (items.size() < 20) {
                                isEnd = true;
                            }

                            scrollView.smoothScrollTo(0, 0);
                            scrollView.requestFocus();
                        }
                    });
                } catch (final Exception e) {
                    if (getActivity() == null) return;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null) return;
                            hideAllLoading();
                            emptyView.setText("加载失败: " + e.getMessage());
                            emptyView.setVisibility(View.VISIBLE);
                            showToast("加载失败: " + e.getMessage());
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
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

                            if (newItems.size() < 20) {
                                isEnd = true;
                                showToast("已经到底啦");
                            }
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
                            if (getActivity() == null) {
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