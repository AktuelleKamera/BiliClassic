package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.api.BangumiApi;
import tv.biliclassic.model.Bangumi;

public class BangumiDetailFragment extends Fragment {

    private static final String ARG_MEDIA_ID = "media_id";

    private long mediaId;
    private Bangumi bangumi;

    private TextView tvTitle, tvArea, tvScore, tvIndexShow, tvSeason, tvDesc, tvDescExpand, tvEpisodeCount;
    private ImageView ivCover;
    private ListView lvEpisodes;
    private Button btnSubscribe;

    private EpisodeAdapter adapter;
    private List<Bangumi.Episode> episodeList = new ArrayList<Bangumi.Episode>();
    private Handler mHandler = new Handler();
    private View mRootView;

    private boolean mIsFollowing = false;
    private boolean mIsFollowLoading = false;

    public static BangumiDetailFragment newInstance(long mediaId) {
        Bundle args = new Bundle();
        args.putLong(ARG_MEDIA_ID, mediaId);
        BangumiDetailFragment fragment = new BangumiDetailFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mediaId = getArguments().getLong(ARG_MEDIA_ID, 0);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_bangumi_detail, container, false);
        mRootView = view;

        tvTitle = (TextView) view.findViewById(R.id.tv_title);
        tvArea = (TextView) view.findViewById(R.id.tv_area);
        tvScore = (TextView) view.findViewById(R.id.tv_score);
        tvIndexShow = (TextView) view.findViewById(R.id.tv_index_show);
        tvSeason = (TextView) view.findViewById(R.id.tv_season);
        tvDesc = (TextView) view.findViewById(R.id.tv_desc);
        tvDescExpand = (TextView) view.findViewById(R.id.tv_desc_expand);
        tvEpisodeCount = (TextView) view.findViewById(R.id.tv_episode_count);
        ivCover = (ImageView) view.findViewById(R.id.iv_cover);
        lvEpisodes = (tv.biliclassic.ObservableListView) view.findViewById(R.id.lv_episodes);

        adapter = new EpisodeAdapter(getActivity(), episodeList);
        adapter.setOnEpisodeClickListener(new EpisodeAdapter.OnEpisodeClickListener() {
            @Override
            public void onEpisodeClick(Bangumi.Episode episode, int position) {
                playEpisode(episode);
            }
        });
        lvEpisodes.setAdapter(adapter);

        // 在 View 还未显示时，先让 tvDesc 不限制行数，测完再恢复
        tvDesc.setMaxLines(Integer.MAX_VALUE);
        tvDesc.setEllipsize(null);

        if (mediaId != 0) {
            loadBangumiDetail();
        }

        // 防止加载完成后滚动到底部
        view.post(new Runnable() {
            @Override
            public void run() {
                if (ivCover != null) {
                    ivCover.setFocusable(true);
                    ivCover.setFocusableInTouchMode(true);
                    ivCover.requestFocus();
                }
                ScrollView scrollView = (ScrollView) view;
                if (scrollView != null) {
                    scrollView.smoothScrollTo(0, 0);
                }
            }
        });

        return view;
    }

    private void playEpisode(final Bangumi.Episode episode) {
        if (episode == null || episode.aid == 0) {
            Toast.makeText(getActivity(), "分集信息无效", Toast.LENGTH_SHORT).show();
            return;
        }

        String bangumiTitle = bangumi != null && bangumi.info != null
                ? bangumi.info.title
                : "番剧";

        Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
        intent.putExtra("aid", episode.aid);
        intent.putExtra("bangumi_media_id", mediaId);
        intent.putExtra("bangumi_title", bangumiTitle);
        startActivity(intent);
    }

    private void loadBangumiDetail() {
        showLoading(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bangumi result = BangumiApi.getBangumi(mediaId);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (result != null && result.info != null) {
                                    bangumi = result;
                                    displayBangumi();
                                } else {
                                    Toast.makeText(getActivity(), "获取番剧信息失败", Toast.LENGTH_SHORT).show();
                                }
                                showLoading(false);
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showLoading(false);
                                String msg = e.getMessage();
                                if (msg != null && msg.contains("404")) {
                                    Toast.makeText(getActivity(), "番剧不存在或已下架", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getActivity(), "加载失败: " + msg, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void displayBangumi() {
        if (bangumi == null || bangumi.info == null) {
            return;
        }

        Bangumi.Info info = bangumi.info;

        tvTitle.setText(info.title != null ? info.title : "未知");
        tvArea.setText(info.area_name != null ? info.area_name : "未知");
        tvScore.setText(info.score > 0 ? String.valueOf(info.score) : "暂无评分");
        tvIndexShow.setText(info.indexShow != null ? info.indexShow : "敬请期待");
        tvSeason.setText(info.season != null ? info.season : "");

        final String descText = info.evaluate != null && info.evaluate.length() > 0
                ? info.evaluate
                : "暂无简介";

        // 找到简介的父容器，整体隐藏
        final View descContainer = (View) tvDesc.getParent();
        if (descContainer != null) {
            descContainer.setVisibility(View.INVISIBLE);
        }

        tvDesc.setText(descText);

        // 临时取消 maxLines 限制
        tvDesc.setMaxLines(Integer.MAX_VALUE);
        tvDesc.setEllipsize(null);
        tvDesc.requestLayout();

        tvDesc.post(new Runnable() {
            @Override
            public void run() {
                int fullLineCount = tvDesc.getLineCount();

                // 恢复 maxLines 限制
                tvDesc.setMaxLines(3);
                tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);

                // 显示整个容器
                if (descContainer != null) {
                    descContainer.setVisibility(View.VISIBLE);
                }

                if (fullLineCount > 3) {
                    tvDescExpand.setVisibility(View.VISIBLE);
                    tvDescExpand.setOnClickListener(new View.OnClickListener() {
                        boolean isExpanded = false;

                        @Override
                        public void onClick(View v) {
                            isExpanded = !isExpanded;
                            if (isExpanded) {
                                tvDesc.setMaxLines(Integer.MAX_VALUE);
                                tvDesc.setEllipsize(null);
                                tvDescExpand.setText("收起");
                            } else {
                                tvDesc.setMaxLines(3);
                                tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
                                tvDescExpand.setText("展开");
                            }
                        }
                    });
                } else {
                    tvDescExpand.setVisibility(View.GONE);
                }
            }
        });

        loadCover(info.cover);

        // 分集列表...
        episodeList.clear();
        int totalEpisodes = 0;

        if (bangumi.sectionList != null && !bangumi.sectionList.isEmpty()) {
            for (int i = 0; i < bangumi.sectionList.size(); i++) {
                Bangumi.Section section = bangumi.sectionList.get(i);
                if (section.episodeList != null && !section.episodeList.isEmpty()) {
                    boolean showDivider = bangumi.sectionList.size() > 1
                            || (section.title != null && !"正片".equals(section.title));
                    if (showDivider) {
                        Bangumi.Episode divider = new Bangumi.Episode();
                        divider.isDivider = true;
                        divider.title = section.title != null ? section.title : "其他";
                        episodeList.add(divider);
                    }
                    episodeList.addAll(section.episodeList);
                    totalEpisodes += section.episodeList.size();
                }
            }
        }

        tvEpisodeCount.setText("共 " + totalEpisodes + " 集");
        adapter.notifyDataSetChanged();

        // 滚动到顶部
        if (mRootView != null) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ScrollView scrollView = (ScrollView) mRootView;
                    if (scrollView != null) {
                        scrollView.smoothScrollTo(0, 0);
                    }
                    if (ivCover != null) {
                        ivCover.requestFocus();
                    }
                }
            }, 200);
        }
    }

    private void loadCover(String url) {
        if (getActivity() == null) return;
        if (url == null || url.length() == 0) return;

        if (url.startsWith("https://")) {
            url = "http://" + url.substring(8);
        }

        final String finalUrl = url;
        final GlobalImageCache cache = GlobalImageCache.getInstance();

        Bitmap cached = cache.get(finalUrl);
        if (cached != null && !cached.isRecycled()) {
            ivCover.setImageBitmap(cached);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                java.net.HttpURLConnection conn = null;
                try {
                    java.net.URL urlObj = new java.net.URL(finalUrl);
                    conn = (java.net.HttpURLConnection) urlObj.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();
                    java.io.InputStream is = conn.getInputStream();
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inSampleSize = 2;
                    options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
                    final Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is, null, options);
                    is.close();
                    if (bitmap != null && !bitmap.isRecycled()) {
                        cache.put(finalUrl, bitmap);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ivCover.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }).start();
    }

    private void showLoading(boolean show) {
        if (getView() != null) {
            View loading = getView().findViewById(R.id.progress_bar);
            if (loading != null) {
                loading.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
    }

    // Adapter

    private static class EpisodeAdapter extends BaseAdapter {

        private Context context;
        private List<Bangumi.Episode> list;
        private OnEpisodeClickListener mClickListener;

        public interface OnEpisodeClickListener {
            void onEpisodeClick(Bangumi.Episode episode, int position);
        }

        public EpisodeAdapter(Context context, List<Bangumi.Episode> list) {
            this.context = context;
            this.list = list;
        }

        public void setOnEpisodeClickListener(OnEpisodeClickListener listener) {
            this.mClickListener = listener;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Bangumi.Episode episode = list.get(position);
            final int pos = position;

            // 分割标题
            if (episode.isDivider) {
                if (convertView == null || !(convertView instanceof TextView)) {
                    convertView = new TextView(context);
                    convertView.setLayoutParams(new ListView.LayoutParams(
                            ListView.LayoutParams.MATCH_PARENT,
                            ListView.LayoutParams.WRAP_CONTENT));
                }
                TextView tv = (TextView) convertView;
                tv.setText(episode.title);
                tv.setTextSize(14);
                tv.setTextColor(0xFFD86DA5);
                tv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(4));
                tv.setBackgroundColor(0xFFF0F0F0);
                tv.setClickable(false);
                tv.setFocusable(false);
                return convertView;
            }

            // 普通集数
            ViewHolder holder;
            if (convertView == null || convertView instanceof TextView) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_video_part, parent, false);
                holder = new ViewHolder();
                holder.tvIndex = (TextView) convertView.findViewById(R.id.tv_part_index);
                holder.tvTitle = (TextView) convertView.findViewById(R.id.tv_part_title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            String title = episode.titleLong != null && episode.titleLong.length() > 0
                    ? episode.titleLong
                    : (episode.title != null ? episode.title : "第" + (position + 1) + "话");

            holder.tvIndex.setText(String.valueOf(position + 1));
            holder.tvTitle.setText(title);

            // 设置点击事件
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mClickListener != null && !episode.isDivider) {
                        mClickListener.onEpisodeClick(episode, pos);
                    }
                }
            });

            return convertView;
        }

        private int dpToPx(int dp) {
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (dp * density + 0.5f);
        }

        private static class ViewHolder {
            TextView tvIndex;
            TextView tvTitle;
        }
    }
}