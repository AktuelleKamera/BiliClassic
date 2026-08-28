package tv.biliclassic;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import tv.biliclassic.api.PlayerApi;
import tv.biliclassic.api.RecommendApi;
import tv.biliclassic.api.VideoInfoApi;
import tv.biliclassic.model.PlayerData;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.model.VideoInfo;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.ProxyStreamService;
import tv.biliclassic.util.CookieGenerator;
import tv.biliclassic.util.NetWorkUtil;

// 并没有什么用处的竖屏模式……才不是因为哔哩轻享才加入的呢~
public class RecommendVerticalFragment extends Fragment {

    private static final String TAG = "VertFeed";

    private FrameLayout mRootView;
    private ListView mListView;
    private ProgressBar mProgressBar;
    private TextView mEmptyView;
    private TextView mHintView;
    private VerticalAdapter mAdapter;
    private ArrayList<VideoCard> mItems = new ArrayList<VideoCard>();
    private Handler mHandler = new Handler();
    private int mActivePosition = -1;
    /** 原始播放地址（B 站 durl） */
    private SparseArray<String> mUrlCache = new SparseArray<String>();
    /** 最终可播地址（经转码后的 http 地址；未开转码时与原始地址相同） */
    private SparseArray<String> mPlayCache = new SparseArray<String>();
    private SparseArray<Long> mCidCache = new SparseArray<Long>();
    private boolean mLoaded = false;
    private View mActiveView = null;

    public static RecommendVerticalFragment newInstance() {
        return new RecommendVerticalFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = (FrameLayout) inflater.inflate(R.layout.fragment_recommend_vertical, container, false);
        mListView = (ListView) mRootView.findViewById(R.id.vertical_list);
        mProgressBar = (ProgressBar) mRootView.findViewById(R.id.vertical_progress);
        mEmptyView = (TextView) mRootView.findViewById(R.id.vertical_empty);
        mHintView = (TextView) mRootView.findViewById(R.id.vertical_hint);

        mListView.setDivider(null);
        mListView.setDividerHeight(0);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setSelector(android.R.color.transparent);

        mAdapter = new VerticalAdapter();
        mListView.setAdapter(mAdapter);

        // 竖屏翻页
        mListView.setOnTouchListener(new View.OnTouchListener() {
            float downY = 0;
            long downTime = 0;
            boolean paging = false;

            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getY();
                        downTime = System.currentTimeMillis();
                        paging = false;
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        if (paging) {
                            break;
                        }
                        float h = v.getHeight() > 0 ? v.getHeight() : 480f;
                        float dist = Math.max(h / 10f, 50f);
                        float dy = downY - e.getY();
                        long dt = System.currentTimeMillis() - downTime;
                        if (dy > dist || (dy > 35 && dt < 250)) {
                            paging = true;
                            stepPage(1);
                        } else if (-dy > dist || (-dy > 35 && dt < 250)) {
                            paging = true;
                            stepPage(-1);
                        }
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!paging) {
                            long tapDt = System.currentTimeMillis() - downTime;
                            float tapDy = Math.abs(downY - e.getY());
                            if (tapDt < 300 && tapDy < 30) {
                                togglePlay();
                            }
                        }
                        paging = false;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        paging = false;
                        break;
                }
                return true;
            }
        });

        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    int pos = mListView.getFirstVisiblePosition();
                    if (pos != mActivePosition) {
                        updateActivePage(pos);
                    }
                    snapToPosition(pos);
                }
            }

            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
            }
        });

        loadData();
        return mRootView;
    }

    private void togglePlay() {
        if (mActiveView == null) {
            return;
        }
        VideoView vv = (VideoView) mActiveView.findViewById(R.id.video_view);
        if (vv != null) {
            if (vv.isPlaying()) {
                vv.pause();
            } else {
                try {
                    vv.start();
                } catch (Throwable t) {
                }
            }
        }
    }

    private void stepPage(int dir) {
        if (mItems.size() == 0 || mActivePosition < 0) {
            return;
        }
        final int target = mActivePosition + dir;
        if (target < 0 || target >= mItems.size()) {
            return;
        }
        animateToPage(target);
    }

    /** 逐帧动画翻页 */
    private void animateToPage(final int target) {
        final int h = mListView.getHeight();
        if (mAnimating || h <= 0) {
            // 动画中忽略新翻页
            if (!mAnimating && h <= 0) {
                jumpToPage(target);
            }
            return;
        }
        mAnimating = true;
        // 目标页起始偏移：向下翻目标在屏幕下方(+h)，向上翻在上方(-h)
        final float startOffset = target > mActivePosition ? h : -h;
        final long start = android.os.SystemClock.uptimeMillis();
        final long duration = 220;
        mAnimRunnable = new Runnable() {
            public void run() {
                if (!mAnimating) {
                    return;
                }
                float t = (android.os.SystemClock.uptimeMillis() - start) / (float) duration;
                if (t >= 1f) {
                    mAnimating = false;
                    jumpToPage(target); // 归位并激活
                    return;
                }
                float ease = 1f - (1f - t) * (1f - t) * (1f - t); // 三次缓出
                mListView.setSelectionFromTop(target, (int) (startOffset * (1f - ease)));
                mHandler.post(mAnimRunnable);
            }
        };
        mHandler.post(mAnimRunnable);
    }

    private void jumpToPage(final int target) {
        mListView.setSelectionFromTop(target, 0);
        if (mPendingActivate != null) {
            mHandler.removeCallbacks(mPendingActivate);
        }
        mPendingActivate = new Runnable() {
            public void run() {
                updateActivePage(target);
            }
        };
        mHandler.post(mPendingActivate);
    }

    private boolean mAnimating = false;
    private Runnable mAnimRunnable;
    private Runnable mPendingActivate;

    private void snapToPosition(int pos) {
        View child = mListView.getChildAt(pos - mListView.getFirstVisiblePosition());
        if (child == null) {
            return;
        }
        int top = child.getTop();
        if (Math.abs(top) > 1) {
            scrollToPosition(pos);
        }
    }

    private void scrollToPosition(int pos) {
        if (pos < 0 || pos >= mItems.size()) {
            return;
        }
        mListView.setSelectionFromTop(pos, 0);
    }

    private void stopViewPlayback(View item) {
        if (item == null) {
            return;
        }
        VideoView pv = (VideoView) item.findViewById(R.id.video_view);
        if (pv != null) {
            try {
                pv.stopPlayback();
            } catch (Throwable t) {
            }
        }
        ImageView cover = (ImageView) item.findViewById(R.id.cover);
        if (cover != null) {
            cover.setVisibility(View.VISIBLE);
        }
        ProgressBar pb = (ProgressBar) item.findViewById(R.id.item_progress);
        if (pb != null) {
            pb.setVisibility(View.GONE);
        }
    }

    private void updateActivePage(int pos) {
        // 同一页重复激活时直接跳过，避免把正在播的视频停掉重放
        if (pos == mActivePosition && mActiveView != null) {
            return;
        }
        stopViewPlayback(mActiveView);

        mActivePosition = pos;
        if (pos < 0 || pos >= mItems.size()) {
            mActiveView = null;
            return;
        }
        hideHint();

        View child = mListView.getChildAt(pos - mListView.getFirstVisiblePosition());
        mActiveView = child;
        android.util.Log.e(TAG, "激活第 " + pos + " 页");
        if (child == null) {
            return;
        }

        String play = mPlayCache.get(pos);
        if (play != null && play.length() > 0) {
            playViaProxy(pos, play);
        } else {
            String raw = mUrlCache.get(pos);
            if (raw != null && raw.length() > 0) {
                finishResolve(pos, raw);
            } else {
                ProgressBar pb = (ProgressBar) child.findViewById(R.id.item_progress);
                if (pb != null) {
                    pb.setVisibility(View.VISIBLE);
                }
                resolveChain(pos, mItems.get(pos));
            }
        }

        // 预取相邻视频的原始地址（不含转码——转码耗时长，只在真正翻到该页时做）
        final int cur = pos;
        new Thread(new Runnable() {
            public void run() {
                if (cur + 1 < mItems.size()) {
                    prefetchRaw(cur + 1, mItems.get(cur + 1));
                }
                if (cur - 1 >= 0) {
                    prefetchRaw(cur - 1, mItems.get(cur - 1));
                }
            }
        }).start();
    }

    /**
     * 取址链路（全 bvid，不用 aid）：
     * bvid → cid → MP4 durl → 本地代理。
     */
    private void resolveChain(final int pos, final VideoCard card) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    long cid = mCidCache.get(pos, 0L);
                    if (cid == 0) {
                        VideoInfo info = resolveInfo(card);
                        if (info != null) {
                            cid = info.cids.get(0);
                            mCidCache.put(pos, cid);
                        }
                    }
                    if (cid == 0) {
                        notifyFail(pos, "获取视频信息失败 bvid=" + card.bvid);
                        return;
                    }
                    android.util.Log.e(TAG, pos + " cid=" + cid);

                    PlayerData pd = new PlayerData();
                    pd.bvid = card.bvid;
                    pd.cid = cid;
                    pd.qn = SettingsActivity.getVideoQuality();
                    try {
                        PlayerApi.getVideoMp4(pd);
                    } catch (Exception mp4Err) {
                        android.util.Log.e(TAG, pos + " getVideoMp4 失败: " + mp4Err);
                        PlayerData pd2 = new PlayerData();
                        pd2.bvid = card.bvid;
                        pd2.cid = cid;
                        pd2.qn = pd.qn;
                        pd2.timeStamp = 0;
                        PlayerApi.getVideo(pd2, false);
                        if (pd2.audioUrl != null && pd2.audioUrl.length() > 0) {
                            throw new Exception("该视频仅 DASH 格式，竖屏模式不支持");
                        }
                        pd.videoUrl = pd2.videoUrl;
                    }
                    final String raw = pd.videoUrl;
                    if (raw == null || raw.length() == 0) {
                        notifyFail(pos, "未取得播放地址");
                        return;
                    }
                    android.util.Log.e(TAG, pos + " 原始地址: " + raw);
                    mUrlCache.put(pos, raw);
                    finishResolve(pos, raw);
                } catch (final Exception e) {
                    android.util.Log.e(TAG, pos + " 取址异常", e);
                    notifyFail(pos, e.getMessage());
                }
            }
        }).start();
    }

    /** 拿到原始地址后：缓存最终地址 → 走本地代理播放（与主播放器直连路径一致，不转码） */
    private void finishResolve(final int pos, final String rawUrl) {
        mPlayCache.put(pos, rawUrl);
        mHandler.post(new Runnable() {
            public void run() {
                if (pos != mActivePosition || mActiveView == null) {
                    return;
                }
                String p = mPlayCache.get(pos);
                if (p != null && p.length() > 0) {
                    playViaProxy(pos, p);
                }
            }
        });
    }

    private void playViaProxy(final int pos, final String remoteUrl) {
        try {
            String local = ProxyStreamService.startProxyForExternal(
                    getActivity(), remoteUrl, getProxyHeaders());
            if (local == null || local.length() == 0) {
                local = remoteUrl;
            }
            android.util.Log.e(TAG, pos + " 播放: " + local);
            View child = mActiveView;
            if (child == null || pos != mActivePosition) {
                return;
            }
            VideoView vv = (VideoView) child.findViewById(R.id.video_view);
            ImageView cover = (ImageView) child.findViewById(R.id.cover);
            ProgressBar pb = (ProgressBar) child.findViewById(R.id.item_progress);
            if (vv != null) {
                playVideo(vv, cover, pb, local);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, pos + " 代理启动失败", e);
            notifyFail(pos, e.getMessage());
        }
    }

    private void playVideo(final VideoView vv, final ImageView cover, final ProgressBar pb, final String url) {
        vv.setVideoURI(Uri.parse(url));
        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                android.util.Log.e(TAG, "onPrepared");
                hideHint();
                if (pb != null) {
                    pb.setVisibility(View.GONE);
                }
                if (cover != null) {
                    cover.setVisibility(View.GONE);
                }
                vv.start();
            }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                android.util.Log.e(TAG, "播放错误 what=" + what + " extra=" + extra);
                notifyFail(mActivePosition, "播放失败(" + what + "/" + extra + ")");
                return true;
            }
        });
        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                vv.start(); // 循环播放
            }
        });
    }

    private Map<String, String> getProxyHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Referer", "https://www.bilibili.com/");
        headers.put("User-Agent", NetWorkUtil.USER_AGENT_WEB);
        String cookie = CookieGenerator.getCookieString(true);
        if (cookie != null && cookie.length() > 0) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    /**
     * 解析视频信息：只用 bvid（推荐流卡片 aid=0 无效）。
     */
    private VideoInfo resolveInfo(VideoCard card) {
        if (card.bvid == null || card.bvid.length() == 0) {
            return null;
        }
        try {
            VideoInfo info = VideoInfoApi.getVideoInfo(card.bvid);
            if (info != null && info.cids != null && info.cids.size() > 0) {
                return info;
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "getVideoInfo(bvid) 失败: " + e);
        }
        return null;
    }

    /** 仅预取原始地址（cid + durl），不启动代理 */
    private void prefetchRaw(final int pos, final VideoCard card) {
        if (mUrlCache.get(pos) != null) {
            return;
        }
        try {
            long cid = mCidCache.get(pos, 0L);
            if (cid == 0) {
                VideoInfo info = resolveInfo(card);
                if (info != null) {
                    cid = info.cids.get(0);
                    mCidCache.put(pos, cid);
                }
            }
            if (cid == 0) {
                return;
            }
            PlayerData pd = new PlayerData();
            pd.bvid = card.bvid;
            pd.cid = cid;
            pd.qn = SettingsActivity.getVideoQuality();
            PlayerApi.getVideoMp4(pd);
            if (pd.videoUrl != null && pd.videoUrl.length() > 0) {
                mUrlCache.put(pos, pd.videoUrl);
                android.util.Log.e(TAG, pos + " 预取完成");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, pos + " 预取失败: " + e);
        }
    }

    private void notifyFail(final int pos, final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                if (pos >= 0 && pos != mActivePosition) {
                    return; // 已翻走的不打扰
                }
                if (mActiveView != null) {
                    ProgressBar pb = (ProgressBar) mActiveView.findViewById(R.id.item_progress);
                    if (pb != null) {
                        pb.setVisibility(View.GONE);
                    }
                }
                // 常驻显示在屏幕上，便于直接读出失败环节
                if (mHintView != null) {
                    mHintView.setVisibility(View.VISIBLE);
                    mHintView.setText("第" + (pos + 1) + "个 加载失败: " + msg
                            + "\n(滑走再滑回可重试)");
                }
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), "加载失败: " + msg, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** 成功起播后清掉错误提示 */
    private void hideHint() {
        if (mHintView != null) {
            mHintView.setVisibility(View.GONE);
        }
    }

    private void loadData() {
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.GONE);
        new Thread(new Runnable() {
            public void run() {
                try {
                    mItems.clear();
                    RecommendApi.getRecommend(mItems, 1, 0);
                    mLoaded = true;
                } catch (Exception e) {
                    android.util.Log.e(TAG, "推荐列表加载失败", e);
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        mProgressBar.setVisibility(View.GONE);
                        if (mItems.size() == 0) {
                            mEmptyView.setVisibility(View.VISIBLE);
                        } else {
                            mEmptyView.setVisibility(View.GONE);
                            mAdapter.notifyDataSetChanged();
                            mListView.post(new Runnable() {
                                public void run() {
                                    mActivePosition = -1;
                                    updateActivePage(0);
                                }
                            });
                        }
                    }
                });
            }
        }).start();
    }

    private class VerticalAdapter extends BaseAdapter {
        public int getCount() {
            return mItems.size();
        }

        public Object getItem(int position) {
            return mItems.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.item_vertical_video, parent, false);
                holder = new ViewHolder();
                holder.videoView = (VideoView) convertView.findViewById(R.id.video_view);
                holder.cover = (ImageView) convertView.findViewById(R.id.cover);
                holder.progress = (ProgressBar) convertView.findViewById(R.id.item_progress);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                holder.up = (TextView) convertView.findViewById(R.id.up);
                holder.stats = (TextView) convertView.findViewById(R.id.stats);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            int h = parent.getHeight();
            if (h <= 0) {
                android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
                h = dm.heightPixels;
            }
            convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, h));

            VideoCard card = mItems.get(position);
            holder.title.setText(card.title != null ? card.title : "");
            holder.up.setText(card.upName != null ? card.upName : "");
            StringBuilder sb = new StringBuilder();
            if (card.view != null) {
                sb.append(card.view).append("播放  ");
            }
            if (card.danmaku > 0) {
                sb.append(card.danmaku).append("弹幕");
            }
            holder.stats.setText(sb.toString());

            ImageLoader.bind(holder.cover, card.cover, R.drawable.bili_default_image_tv_with_bg, 0, 0);
            if (holder.progress != null) {
                holder.progress.setVisibility(position == mActivePosition && mPlayCache.get(position) == null
                        ? View.VISIBLE : View.GONE);
            }
            if (holder.cover != null) {
                holder.cover.setVisibility(View.VISIBLE);
            }

            if (position != mActivePosition && holder.videoView != null) {
                try {
                    holder.videoView.stopPlayback();
                } catch (Throwable t) {
                }
            }

            return convertView;
        }
    }

    static class ViewHolder {
        VideoView videoView;
        ImageView cover;
        ProgressBar progress;
        TextView title;
        TextView up;
        TextView stats;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        // ViewPager 内相邻页不会回调 onPause/onResume，切走时必须在这里暂停
        if (!isVisibleToUser) {
            pauseActiveVideo();
        } else if (isResumed()) {
            resumeActiveVideo();
        }
    }

    private void pauseActiveVideo() {
        if (mAnimating) {
            mHandler.removeCallbacks(mAnimRunnable);
            mAnimating = false;
        }
        if (mActiveView != null) {
            VideoView pv = (VideoView) mActiveView.findViewById(R.id.video_view);
            if (pv != null) {
                try {
                    pv.pause();
                } catch (Throwable t) {
                }
            }
        }
    }

    private void resumeActiveVideo() {
        if (mLoaded && mItems.size() > 0 && mActivePosition >= 0 && mActiveView != null) {
            VideoView vv = (VideoView) mActiveView.findViewById(R.id.video_view);
            if (vv != null && !vv.isPlaying()) {
                try {
                    vv.start();
                } catch (Throwable t) {
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        resumeActiveVideo();
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseActiveVideo();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopViewPlayback(mActiveView);
        try {
            ProxyStreamService.stopProxyForExternal(getActivity());
        } catch (Throwable t) {
        }
    }
}
