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
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.R;
import tv.biliclassic.VideoDetailActivity;
import tv.biliclassic.api.HistoryApi;
import tv.biliclassic.model.ApiResult;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * Metro 主题下的"历史记录"页（作为 MetroHome 内的 Fragment，与推荐/个人中心同构）。
 * 透明列表 + 共用 item_metro_video_row；翻入/翻出时条目错峰滑入/滑出。
 * 数据层与 Classic 版历史页一致：HistoryApi（Cookie 鉴权，cursor 分页）。
 */
public class MetroHistoryFragment extends Fragment implements MetroTurnPage {

    private ListView mHistoryList;
    private TextView mTvLoading;
    private SwipeRefreshLayout mSwipeRefresh;
    private List<VideoCard> mVideoList = new ArrayList<VideoCard>();
    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private MetroVideoRowAdapter mAdapter;
    private ApiResult mLastResult = new ApiResult();
    private boolean mIsLoading = false;
    private boolean mIsEnd = false;
    private boolean mHasError = false;
    private boolean mFirstLoad = true;

    // 视图重建时恢复的滚动位置
    private int mSavedPosition = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.metro_history, container, false);

        mHistoryList = (ListView) root.findViewById(R.id.history_list);
        mTvLoading = (TextView) root.findViewById(R.id.tv_loading);
        // 夜间模式：灰色提示文字换白色
        mTvLoading.setTextColor(MetroTheme.grey());

        // API 3: 移除 SwipeRefreshLayout（引起 Layout.draw 递归），与推荐页处理一致
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
            SwipeRefreshLayout srl = (SwipeRefreshLayout) root.findViewById(R.id.swipe_refresh_metro);
            if (srl != null) {
                ListView list = (ListView) srl.findViewById(R.id.history_list);
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

        mAdapter = new MetroVideoRowAdapter(getActivity(), mVideoList);
        // 点击直接回调到行 View 上（与推荐页磁贴/个人中心行一致）
        mAdapter.setOnVideoClickListener(new MetroVideoRowAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(VideoCard card, int position) {
                openVideo(card);
            }
        });
        mHistoryList.setAdapter(mAdapter);

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

        // 状态文本点击：失败/为空时重试
        mTvLoading.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHasError) {
                    refresh();
                }
            }
        });

        // 滚动接近底部自动加载下一页
        mHistoryList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    mAdapter.setScrolling(false);
                    checkLoadMore();
                } else {
                    mAdapter.setScrolling(true);
                    mAdapter.setHideHighlight(true);
                }
            }
        });

        mHistoryList.post(new Runnable() {
            @Override
            public void run() {
                mHistoryList.setSelection(mSavedPosition);
                loadHistory();
            }
        });

        return root;
    }

    private void checkLoadMore() {
        if (mHistoryList == null || mIsLoading || mIsEnd || mHasError) return;
        int last = mHistoryList.getLastVisiblePosition();
        if (last >= mAdapter.getCount() - 2) {
            loadMore();
        }
    }

    /** 已有数据被移走再挂回时：不重复请求，按 mVideoList 重建。 */
    private void rebuildList() {
        if (mAdapter == null) return;
        mAdapter.notifyDataSetChanged();
        if (mHistoryList != null) {
            if (mVideoList.size() > 0) {
                mHistoryList.setVisibility(View.VISIBLE);
                mHistoryList.setSelection(mSavedPosition);
                mHistoryList.invalidateViews();
            }
        }
        if (mTvLoading != null) {
            mTvLoading.setVisibility(mVideoList.size() > 0 ? View.GONE : View.VISIBLE);
        }
    }

    private void loadHistory() {
        // 已加载（重挂载）则不重复请求
        if (mVideoList.size() > 0) {
            stopRefreshing();
            rebuildList();
            return;
        }
        if (mIsLoading) return;

        // 未登录：历史记录需要 Cookie
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null || cookies.length() == 0) {
            mTvLoading.setText(getString(R.string.not_logged_in_yet));
            mFirstLoad = false;
            stopRefreshing();
            return;
        }

        mIsLoading = true;
        mHasError = false;
        mLastResult = new ApiResult();
        mIsEnd = false;

        NetWorkUtil.refreshHeaders();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final HistoryApi.HistoryResult historyResult = HistoryApi.getHistory(mLastResult);
                    final ApiResult result = historyResult.apiResult;
                    final List<VideoCard> newItems = historyResult.newItems;

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

                            if (result.code == 0) {
                                mLastResult = result;
                                mVideoList.addAll(newItems);
                                mHasError = false;

                                if (result.isBottom || mVideoList.size() == 0) {
                                    mIsEnd = true;
                                }

                                if (mVideoList.size() == 0) {
                                    mTvLoading.setText(getString(R.string.no_history_2));
                                    mTvLoading.setVisibility(View.VISIBLE);
                                    mHistoryList.setVisibility(View.GONE);
                                } else {
                                    mTvLoading.setVisibility(View.GONE);
                                    mHistoryList.setVisibility(View.VISIBLE);
                                    mAdapter.notifyDataSetChanged();
                                    mHistoryList.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            animateAllRows();
                                        }
                                    });
                                }
                            } else {
                                mHasError = true;
                                mTvLoading.setText(loadErrMsg(result.message) + "\n点击重试");
                            }
                            stopRefreshing();
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
                            mIsLoading = false;
                            mFirstLoad = false;
                            mHasError = true;
                            mTvLoading.setText(loadErrMsg(e.getMessage()) + "\n点击重试");
                            stopRefreshing();
                        }
                    });
                }
            }
        }).start();
    }

    /** 下拉刷新：清空重载第一页 */
    private void refresh() {
        if (mIsLoading) {
            stopRefreshing();
            return;
        }
        mIsEnd = false;
        mHasError = false;
        mSavedPosition = 0;
        mVideoList.clear();
        mAdapter.notifyDataSetChanged();
        mHistoryList.setVisibility(View.GONE);
        mTvLoading.setVisibility(View.VISIBLE);
        mTvLoading.setText("正在加载…");
        loadHistory();
    }

    private void loadMore() {
        if (mIsLoading || mIsEnd || mHasError) return;
        mIsLoading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final HistoryApi.HistoryResult historyResult = HistoryApi.getHistory(mLastResult);
                    final ApiResult result = historyResult.apiResult;
                    final List<VideoCard> newItems = historyResult.newItems;

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || getView() == null) {
                                mIsLoading = false;
                                return;
                            }
                            mIsLoading = false;
                            if (result.code == 0) {
                                mLastResult = result;
                                mVideoList.addAll(newItems);
                                if (result.isBottom) {
                                    mIsEnd = true;
                                }
                                mAdapter.notifyDataSetChanged();
                            } else {
                                mIsEnd = true;
                            }
                        }
                    });
                } catch (final Exception e) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mIsLoading = false;
                            mIsEnd = true;
                        }
                    });
                }
            }
        }).start();
    }

    /** 统一错误文案（含风控提示） */
    private String loadErrMsg(String msg) {
        if (msg == null || msg.length() == 0) {
            msg = getString(R.string.load_failed_tap_retry);
        }
        if (msg.contains("banned") || msg.contains("request was banned")) {
            msg = getString(R.string.load_failed_tap_retry);
        }
        return msg;
    }

    private void stopRefreshing() {
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    private void openVideo(VideoCard card) {
        if (getActivity() == null || card == null) return;
        Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
        if (card.aid != 0) {
            intent.putExtra("aid", card.aid);
        } else if (card.bvid != null && card.bvid.length() > 0) {
            intent.putExtra("bvid", card.bvid);
        } else {
            return;
        }
        startActivity(intent);
    }

    // ===== 动画 =====

    /** 清除可见行上残留的动画 */
    private void clearRowAnimations() {
        if (mHistoryList == null) return;
        int count = mHistoryList.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = mHistoryList.getChildAt(i);
            if (row == null) continue;
            row.clearAnimation();
            row.setVisibility(View.VISIBLE);
        }
    }

    /** 首次加载完成：可见行错峰滑入 */
    private void animateAllRows() {
        if (mHistoryList == null) return;
        clearRowAnimations();
        int count = mHistoryList.getChildCount();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int travel = Math.max(screenWidth / 3, 80);
        for (int i = 0; i < count; i++) {
            final View row = mHistoryList.getChildAt(i);
            if (row == null) continue;
            row.setVisibility(View.VISIBLE);
            Animation a = new TranslateAnimation(travel, 0, 0, 0);
            a.setDuration(350);
            a.setStartOffset(i * 120);
            a.setInterpolator(new DecelerateInterpolator());
            row.startAnimation(a);
        }
    }

    /** MetroTurnPage：翻入时条目错峰滑入 */
    public void animateTurnIn() {
        if (mHistoryList == null || mVideoList.size() == 0) return;
        clearRowAnimations();
        int count = mHistoryList.getChildCount();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int travel = Math.max(screenWidth / 3, 80);
        for (int i = 0; i < count; i++) {
            final View row = mHistoryList.getChildAt(i);
            if (row == null) continue;
            row.setVisibility(View.VISIBLE);
            Animation a = new TranslateAnimation(travel, 0, 0, 0);
            a.setDuration(280);
            a.setStartOffset(i * 70);
            a.setInterpolator(new DecelerateInterpolator());
            row.startAnimation(a);
        }
    }

    /** MetroTurnPage：翻出时条目错峰滑出 */
    public void animateTurnOut() {
        if (mHistoryList == null || mVideoList.size() == 0) return;
        clearRowAnimations();
        int count = mHistoryList.getChildCount();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int travel = Math.max(screenWidth / 3, 80);
        for (int i = 0; i < count; i++) {
            final View row = mHistoryList.getChildAt(i);
            if (row == null) continue;
            row.setVisibility(View.VISIBLE);
            Animation a = new TranslateAnimation(0, travel, 0, 0);
            a.setDuration(240);
            a.setStartOffset(i * 50);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            row.startAnimation(a);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
    }

    @Override
    public void onDestroyView() {
        // 保存滚动位置，重新进入时恢复
        if (mHistoryList != null && mAdapter != null) {
            mSavedPosition = mHistoryList.getFirstVisiblePosition();
        }
        super.onDestroyView();
    }
}
