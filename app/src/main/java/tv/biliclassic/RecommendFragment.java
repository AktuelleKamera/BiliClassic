package tv.biliclassic;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.KeyEvent;
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
import tv.biliclassic.util.KeyBindingUtil;
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

    // 方向键选中的视频卡索引
    private int selectedPosition = 0;

    // 是否已用遥控器按键导航过（触屏用户未按键时不高亮第一张卡）
    private boolean mKeyNavActive = false;

    // 光标是否停留在顶部 PagerTabStrip 指示器层（推荐页默认位置）。
    // 在指示器层时左右键切换 Tab，下键/确认键进入网格后才左右移动卡片。
    private boolean mAtTabStrip = true;

    // 双击数字键 2 回到顶部：单击 2 延迟翻页等待可能的双击
    private static final long NUM2_DOUBLE_TAP_MS = 350;
    private long lastNum2PressTime = 0;
    private Runnable pendingPageUpRunnable = null;

    private static final String STATE_GRID_POS = "grid_pos";

    // 首次加载失败/返回空时自动重试
    private static final int MAX_LOAD_RETRY = 3;
    private static final long RETRY_DELAY_MS = 1500;
    private int loadRetryCount = 0;
    private Handler retryHandler = new Handler(Looper.getMainLooper());

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
                    // 滚动结束：保持高亮隐藏，等待再次按键恢复
                } else {
                    adapter.setScrolling(true);
                    // 开始触摸滚动/甩动：隐藏光标高亮
                    adapter.setHideHighlight(true);
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
    public void onResume() {
        super.onResume();
        // 切回该界面时，让方向键选中态与焦点恢复（仅遥控器用户，触屏用户不显示光标）
        if (mKeyNavActive && videoList != null && videoList.size() > 0 && gridView != null && adapter != null) {
            if (mAtTabStrip) {
                // 光标停在顶部指示器层：网格不高亮，等待下键进入
                adapter.setSelectedPosition(-1);
                return;
            }
            adapter.setSelectedPosition(selectedPosition);
            gridView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (gridView != null) {
                        // setSelection 为 API 1，兼容 Android 2.x；smoothScrollToPosition 需 API 8
                        gridView.setSelection(selectedPosition / (adapter != null ? adapter.getNumColumns() : 2));
                        gridView.requestFocus();
                    }
                }
            }, 100);
        }
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

    /** 手动/下拉刷新触发：重置重试计数后重新加载 */
    public void loadRecommend() {
        loadRetryCount = 0;
        doLoadRecommend();
    }

    private void doLoadRecommend() {
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
                    RecommendApi.getRecommend(items, currentPage, 0);
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
                            isLoading = false;

                            if (items == null || items.size() == 0) {
                                if (loadRetryCount < MAX_LOAD_RETRY) {
                                    loadRetryCount++;
                                    android.util.Log.e("RecommendDiag", "推荐结果为空, 第 "
                                            + loadRetryCount + "/" + MAX_LOAD_RETRY + " 次重试");
                                    retryLoad();
                                    return;
                                }
                                android.util.Log.e("RecommendDiag", "推荐结果为空, 重试次数用尽, 显示空视图");
                                hideAllLoading();
                                stopRefreshing();
                                if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                                if (gridView != null) gridView.setVisibility(View.GONE);
                                return;
                            }
                            hideAllLoading();
                            stopRefreshing();
                            videoList.clear();
                            videoList.addAll(items);
                            int cols = adapter.getNumColumns();
                            while (videoList.size() % cols != 0) {
                                videoList.remove(videoList.size() - 1);
                            }
                            selectedPosition = 0;
                            // 触屏用户未按键时不高亮第一张卡（adapter 默认 -1 = 无高亮）
                            adapter.setSelectedPosition(mKeyNavActive ? 0 : -1);
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
                                        // 仅遥控器用户抢占焦点（触屏用户 requestFocus 会显示原生选中框）
                                        if (mKeyNavActive) {
                                            gridView.requestFocus();
                                        }
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
                            if (loadRetryCount < MAX_LOAD_RETRY) {
                                loadRetryCount++;
                                android.util.Log.e("RecommendDiag", "推荐加载异常, 第 "
                                        + loadRetryCount + "/" + MAX_LOAD_RETRY + " 次重试: "
                                        + (e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
                                retryLoad();
                                return;
                            }
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
                    RecommendApi.getRecommend(newItems, page, videoList.size());

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
                                showToast(getString(R.string.emoticon__no_more_data));
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

    private void retryLoad() {
        if (getActivity() == null || getView() == null) return;
        retryHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null || getView() == null) return;
                doLoadRecommend();
            }
        }, RETRY_DELAY_MS);
    }

    /**
     * 供 MainActivity.dispatchKeyEvent 调用：处理遥控器方向键与确认键。
     * 方向键始终在卡片网格内移动光标（到达边界时停在原地），全部消费事件，
     * 绝不把 LEFT/RIGHT 让给 ViewPager 切 Tab、也不把 UP/DOWN 让给 ListView 滚动。
     * 返回 true 表示事件已被消费。
     */
    public boolean handleRemoteKey(android.view.KeyEvent event) {
        // 数字键 5：刷新并回到顶部（列表为空/加载失败时也可用）
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && KeyBindingUtil.classify(event.getKeyCode()) == KeyBindingUtil.ACTION_NUM_5) {
            mKeyNavActive = true;
            if (adapter != null) {
                adapter.setHideHighlight(false);
            }
            cancelPendingPageUp();
            scrollToTop();
            loadRecommend();
            return true;
        }
        if (videoList == null || videoList.size() == 0 || gridView == null) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        // 指示器层：光标停在顶部 PagerTabStrip，左右键让 MainActivity 切 Tab，
        // 下键/确认键进入网格（此时才启用卡片光标高亮）。
        if (mAtTabStrip) {
            int act = KeyBindingUtil.classify(event.getKeyCode());
            if (act == KeyBindingUtil.ACTION_LEFT
                    || act == KeyBindingUtil.ACTION_RIGHT) {
                // 不消费：返回 false，MainActivity 负责切换 Tab
                return false;
            }
            if (act == KeyBindingUtil.ACTION_DOWN
                    || act == KeyBindingUtil.ACTION_CONFIRM) {
                if (event.getRepeatCount() == 0) {
                    enterGrid();
                }
                return true;
            }
            // 指示器层其他键（如数字键 2/8）仍按原逻辑处理
        }
        // 真实遥控器按键：启用光标高亮（首次按键立即显示当前位置）
        if (!mKeyNavActive) {
            mKeyNavActive = true;
            if (adapter != null) {
                adapter.setSelectedPosition(selectedPosition);
            }
        }
        // 按键恢复：取消触摸滑动时的隐藏，重新显示光标
        if (adapter != null) {
            adapter.setHideHighlight(false);
        }
        int cols = adapter.getNumColumns();
        int count = videoList.size();
        int newPos = selectedPosition;
        int action = KeyBindingUtil.classify(event.getKeyCode());
        // 数字键 2/8：按一屏快速翻页；连按两下 2 回到顶部
        // （首次按下翻页；长按 repeat 消费但不连续翻页）
        if (action == KeyBindingUtil.ACTION_NUM_2 || action == KeyBindingUtil.ACTION_NUM_8) {
            if (event.getRepeatCount() == 0) {
                if (action == KeyBindingUtil.ACTION_NUM_2) {
                    // 单击 2 延迟翻页，双击（350ms 内再按一次）则回到顶部
                    long now = System.currentTimeMillis();
                    if (now - lastNum2PressTime <= NUM2_DOUBLE_TAP_MS) {
                        lastNum2PressTime = 0;
                        cancelPendingPageUp();
                        scrollToTop();
                    } else {
                        lastNum2PressTime = now;
                        schedulePageUp();
                    }
                } else {
                    // 数字键 8：向下翻一屏
                    int pagePos = pageMove(1);
                    if (pagePos != selectedPosition) {
                        setSelectedPosition(pagePos);
                        // 翻到接近底部时触发"加载更多"
                        if (pagePos >= count - 1) {
                            loadMoreRecommend();
                        }
                    }
                }
            }
            return true;
        }
        switch (action) {
            case KeyBindingUtil.ACTION_UP:
                if (selectedPosition < cols) {
                    // 已在第一行：回到顶部指示器层，取消网格光标
                    backToTabStrip();
                    return true;
                }
                newPos = selectedPosition - cols;
                break;
            case KeyBindingUtil.ACTION_DOWN:
                newPos = Math.min(count - 1, selectedPosition + cols);
                break;
            case KeyBindingUtil.ACTION_LEFT:
                if (selectedPosition % cols == 0) {
                    // 已是该行最左：停在原地（不回行首、不切 Tab）
                    newPos = selectedPosition;
                } else {
                    newPos = selectedPosition - 1;
                }
                break;
            case KeyBindingUtil.ACTION_RIGHT:
                if (selectedPosition % cols == cols - 1) {
                    // 已是该行最右：停在原地（不切 Tab）
                    newPos = selectedPosition;
                } else {
                    newPos = Math.min(count - 1, selectedPosition + 1);
                }
                break;
            case KeyBindingUtil.ACTION_CONFIRM:
                openVideo(selectedPosition);
                return true;
            default:
                return false;
        }
        if (newPos != selectedPosition) {
            setSelectedPosition(newPos);
            // 光标到达列表底部时，顺便触发"加载更多"
            if (action == KeyBindingUtil.ACTION_DOWN
                    && newPos >= count - 1) {
                loadMoreRecommend();
            }
        }
        // 边界（首行 UP / 末行 DOWN / 行首 LEFT / 行末 RIGHT）：
        // 选中不移动，仍消费事件，避免 ListView 滚动 / ViewPager 切 Tab。
        return true;
    }

    /**
     * 供 MainActivity 判断：光标是否停留在顶部指示器层（左右键应切换 Tab）。
     */
    public boolean isAtTabStrip() {
        return mAtTabStrip;
    }

    /**
     * 下键/确认键从指示器层进入网格：选中第一个卡片并显示高亮。
     */
    private void enterGrid() {
        mAtTabStrip = false;
        mKeyNavActive = true;
        selectedPosition = 0;
        if (adapter != null) {
            adapter.setSelectedPosition(0);
            adapter.setHideHighlight(false);
        }
        if (gridView != null) {
            gridView.setSelection(0);
        }
    }

    /**
     * 网格层第一行再按上键：回到顶部指示器层，取消网格光标。
     */
    private void backToTabStrip() {
        mAtTabStrip = true;
        if (adapter != null) {
            adapter.setSelectedPosition(-1);
        }
    }

    /**
     * 用遥控器方向键进入指定位置的视频详情。
     */
    private void openVideo(int position) {
        if (position < 0 || position >= videoList.size()) return;
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

    /**
     * 更新方向键选中的卡片位置，刷新高亮并让 ListView 滚动使其可见。
     */
    private void setSelectedPosition(int position) {
        if (position < 0 || position >= videoList.size()) return;
        selectedPosition = position;
        if (adapter != null) {
            adapter.setSelectedPosition(position);
        }
        ensureSelectedVisible();
    }

    /**
     * 数字键 2/8：按一屏（ListView 可视区能显示的行数 × 列数）快速翻页。
     * direction=-1 向上翻，+1 向下翻；返回新位置（已做边界钳制）。
     */
    private int pageMove(int direction) {
        int cols = adapter != null ? adapter.getNumColumns() : 2;
        int rowsPerScreen = getRowsPerScreen();
        int step = Math.max(1, rowsPerScreen * cols);
        int newPos = selectedPosition + direction * step;
        int count = videoList.size();
        if (newPos < 0) {
            newPos = 0;
        } else if (newPos >= count) {
            newPos = count - 1;
        }
        return newPos;
    }

    /** 单击数字键 2：延迟一屏向上翻页，等待可能的双击 */
    private void schedulePageUp() {
        if (pendingPageUpRunnable == null) {
            pendingPageUpRunnable = new Runnable() {
                @Override
                public void run() {
                    pendingPageUpRunnable = null;
                    if (getActivity() == null || getView() == null || gridView == null) {
                        return;
                    }
                    int pagePos = pageMove(-1);
                    if (pagePos != selectedPosition) {
                        setSelectedPosition(pagePos);
                    }
                }
            };
        }
        mainHandler.removeCallbacks(pendingPageUpRunnable);
        mainHandler.postDelayed(pendingPageUpRunnable, NUM2_DOUBLE_TAP_MS);
    }

    /** 取消待执行的单击翻页（双击触发时调用） */
    private void cancelPendingPageUp() {
        if (pendingPageUpRunnable != null) {
            mainHandler.removeCallbacks(pendingPageUpRunnable);
            pendingPageUpRunnable = null;
        }
    }

    /** 回到网格顶部 */
    private void scrollToTop() {
        if (selectedPosition != 0) {
            setSelectedPosition(0);
        } else if (gridView != null) {
            // setSelection 为 API 1，兼容 Android 2.x；smoothScrollToPosition 需 API 8
            gridView.setSelection(0);
        }
    }

    /** 估算一屏能显示多少行卡片（复用 ensureSelectedVisible 的行高估算）。 */
    private int getRowsPerScreen() {
        if (gridView == null) {
            return 3;
        }
        int first = gridView.getFirstVisiblePosition();
        int last = gridView.getLastVisiblePosition();
        int visible = last - first + 1;
        if (visible > 1) {
            return visible;
        }
        int scrollViewHeight = gridView.getHeight();
        if (scrollViewHeight <= 0) {
            return 3;
        }
        int cols = adapter != null ? adapter.getNumColumns() : 2;
        float density = getResources().getDisplayMetrics().density;
        int parentWidth = gridView.getWidth();
        int containerWidth = parentWidth > 0
                ? parentWidth / cols - dpToPx(6)
                : getResources().getDisplayMetrics().widthPixels / cols - dpToPx(6);
        int coverH = containerWidth > 0 ? containerWidth * 9 / 16 : (int) (100 * density);
        int titleH = (int) (46 * density);
        int rowH = coverH + titleH + dpToPx(12) + dpToPx(12);
        if (rowH <= 0) {
            return 3;
        }
        int rows = scrollViewHeight / rowH;
        return Math.max(1, rows);
    }

    /**
     * 让 ListView 定位到当前选中卡片所在行，保证其在可视区域内。
     * 用 setSelection 同步定位而非 smoothScrollToPosition：
     * 选中更新紧跟在 adapter.notifyDataSetChanged() 之后，平滑滚动动画会与
     * 数据变更触发的重排竞争，导致列表被拉回顶部。
     */
    private void ensureSelectedVisible() {
        if (gridView == null) return;
        int cols = adapter != null ? adapter.getNumColumns() : 2;
        int row = selectedPosition / cols;
        gridView.setSelection(row);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        retryHandler.removeCallbacksAndMessages(null);
        cancelPendingPageUp();
        if (adapter != null) {
            adapter.clearCache();
        }
    }
}
