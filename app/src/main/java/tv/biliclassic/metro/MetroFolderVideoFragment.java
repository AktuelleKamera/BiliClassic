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
import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * Metro 风格"收藏夹视频"页（从"我的收藏"点收藏夹转门翻入）。
 * 页面标题 = 收藏夹名称；透明列表复用共用 item_metro_video_row；
 * 翻入/翻出条目错峰滑入/滑出；下拉刷新 + 滚动到底分页。
 */
public class MetroFolderVideoFragment extends Fragment implements MetroTurnPage {

    private ListView mVideoList;
    private TextView mTvLoading;
    private SwipeRefreshLayout mSwipeRefresh;
    private List<VideoCard> mVideos = new ArrayList<VideoCard>();
    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private MetroVideoRowAdapter mAdapter;
    private boolean mIsLoading = false;
    private boolean mIsEnd = false;
    private boolean mHasError = false;
    private int mPage = 1;

    // 视图重建时恢复的滚动位置
    private int mSavedPosition = 0;

    // 当前展示的收藏夹（fid 存字段；arguments 仅用于首次创建）
    private long mCurrentFid = -1;
    // 换夹代数：旧请求的回调按代数丢弃，避免串台
    private int mGen = 0;

    /** 已挂载实例换收藏夹：更新大标题并重载数据（宿主复用 Fragment 时调用） */
    public void updateFolder(long fid, String name) {
        View v = getView();
        if (v != null) {
            TextView t = (TextView) v.findViewById(R.id.page_title);
            if (t != null) {
                t.setText(name != null && name.length() > 0 ? name : "收藏夹");
            }
        }
        // 换夹重载（fid 存字段，不从 arguments 读——已添加的 Fragment 无法更新 arguments）
        if (fid != mCurrentFid) {
            mCurrentFid = fid;
            mGen++;
            mSavedPosition = 0;
            mVideos.clear();
            mIsEnd = false;
            mHasError = false;
            mIsLoading = false; // 旧请求结果会被代数校验丢弃
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            if (mVideoList != null && mTvLoading != null && getView() != null) {
                mVideoList.setVisibility(View.GONE);
                mTvLoading.setVisibility(View.VISIBLE);
                mTvLoading.setText("正在加载…");
                mVideoList.post(new Runnable() {
                    @Override
                    public void run() {
                        loadFirstPage();
                    }
                });
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.metro_history, container, false);

        mVideoList = (ListView) root.findViewById(R.id.history_list);
        mTvLoading = (TextView) root.findViewById(R.id.tv_loading);
        // 夜间模式：灰色提示文字换白色
        mTvLoading.setTextColor(MetroTheme.grey());

        // 页面标题 = 收藏夹名称；记录当前 fid（后续换夹走 updateFolder）
        TextView pageTitle = (TextView) root.findViewById(R.id.page_title);
        Bundle args = getArguments();
        String folderName = args != null ? args.getString("name", "") : "";
        mCurrentFid = args != null ? args.getLong("fid", 0) : 0;
        if (pageTitle != null) {
            pageTitle.setText(folderName != null && folderName.length() > 0
                    ? folderName : "收藏夹");
        }

        // API 3: 移除 SwipeRefreshLayout（引起 Layout.draw 递归），与推荐页处理一致
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
            SwipeRefreshLayout srl = (SwipeRefreshLayout) root.findViewById(R.id.swipe_refresh_metro);
            if (srl != null) {
                ListView list = (ListView) srl.findViewById(R.id.history_list);
                ViewGroup parent = (ViewGroup) srl.getParent();
                if (parent != null && list != null) {
                    int idx = parent.indexOfChild(srl);
                    parent.removeView(srl);
                    parent.addView(list, idx, srl.getLayoutParams());
                }
            }
        }

        mAdapter = new MetroVideoRowAdapter(getActivity(), mVideos);
        // 点击直接回调到行 View 上（与推荐/历史一致）
        mAdapter.setOnVideoClickListener(new MetroVideoRowAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(VideoCard card, int position) {
                openVideo(card);
            }
        });
        mVideoList.setAdapter(mAdapter);

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
        mVideoList.setOnScrollListener(new AbsListView.OnScrollListener() {
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

        mVideoList.post(new Runnable() {
            @Override
            public void run() {
                mVideoList.setSelection(mSavedPosition);
                loadFirstPage();
            }
        });

        return root;
    }

    private long folderFid() {
        // fid 存字段（updateFolder 换夹时更新）；arguments 仅在首次创建时初始化该字段
        return mCurrentFid;
    }

    private void checkLoadMore() {
        if (mVideoList == null || mIsLoading || mIsEnd || mHasError) return;
        int last = mVideoList.getLastVisiblePosition();
        if (last >= mAdapter.getCount() - 2) {
            loadMore();
        }
    }

    /** 已有数据被移走再挂回时：不重复请求，按 mVideos 重建。 */
    private void rebuildList() {
        if (mAdapter == null) return;
        mAdapter.notifyDataSetChanged();
        if (mVideoList != null && mVideos.size() > 0) {
            mVideoList.setVisibility(View.VISIBLE);
            mVideoList.setSelection(mSavedPosition);
            mVideoList.invalidateViews();
        }
        if (mTvLoading != null) {
            mTvLoading.setVisibility(mVideos.size() > 0 ? View.GONE : View.VISIBLE);
        }
    }

    private void loadFirstPage() {
        if (mVideos.size() > 0) {
            stopRefreshing();
            rebuildList();
            return;
        }
        mPage = 1;
        mIsEnd = false;
        loadPage(1);
    }

    private void loadPage(final int page) {
        if (mIsLoading) return;
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        final long fid = folderFid();
        if (mid <= 0 || fid <= 0) {
            mTvLoading.setText(getString(R.string.not_logged_in_yet));
            stopRefreshing();
            return;
        }

        mIsLoading = true;
        mHasError = false;
        final int gen = mGen; // 换夹后旧回调按代数丢弃
        if (page == 1) {
            mTvLoading.setVisibility(View.VISIBLE);
            mTvLoading.setText("正在加载…");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                int code = -1;
                final List<VideoCard> items = new ArrayList<VideoCard>();
                try {
                    // getFolderVideos 返回：0=有数据 1=到底 -1=失败（page==1 时内部会清空传入列表）
                    code = FavoriteApi.getFolderVideos(mid, fid, page, (ArrayList<VideoCard>) items);
                } catch (Exception e) {
                    code = -1;
                }
                final int resultCode = code;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != mGen) {
                            // 已切换到其他收藏夹：丢弃旧结果（新请求自行管理状态）
                            return;
                        }
                        if (getActivity() == null || getView() == null) {
                            mIsLoading = false;
                            stopRefreshing();
                            return;
                        }
                        mIsLoading = false;

                        if (resultCode == -1) {
                            if (page == 1 && mVideos.size() == 0) {
                                mHasError = true;
                                mTvLoading.setText(getString(R.string.load_failed_tap_retry) + "\n点击重试");
                                mTvLoading.setVisibility(View.VISIBLE);
                            }
                            stopRefreshing();
                            return;
                        }

                        if (page == 1) {
                            mVideos.clear();
                        }
                        mVideos.addAll(items);
                        mPage = page;
                        mAdapter.notifyDataSetChanged();

                        if (resultCode == 1 || items.size() == 0) {
                            mIsEnd = true;
                        }

                        if (mVideos.size() == 0) {
                            mTvLoading.setText(getString(R.string.no_favorite_folders_2));
                            mTvLoading.setVisibility(View.VISIBLE);
                            mVideoList.setVisibility(View.GONE);
                        } else {
                            mTvLoading.setVisibility(View.GONE);
                            mVideoList.setVisibility(View.VISIBLE);
                            if (page == 1) {
                                mVideoList.setSelection(0);
                            }
                        }
                        stopRefreshing();
                    }
                });
            }
        }).start();
    }

    private void loadMore() {
        if (mIsLoading || mIsEnd || mHasError) return;
        loadPage(mPage + 1);
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
        mVideos.clear();
        mAdapter.notifyDataSetChanged();
        mVideoList.setVisibility(View.GONE);
        mTvLoading.setVisibility(View.VISIBLE);
        mTvLoading.setText("正在加载…");
        loadPage(1);
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
        if (mVideoList != null && mAdapter != null) {
            mSavedPosition = mVideoList.getFirstVisiblePosition();
        }
        super.onDestroyView();
    }
}
