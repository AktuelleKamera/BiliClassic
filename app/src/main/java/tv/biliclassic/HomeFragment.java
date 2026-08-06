package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.support.v4.app.Fragment;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.api.PartitionApi;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.SharedPreferencesUtil;

public class HomeFragment extends Fragment {

    // 分区视频数据缓存：划走再划回时不重新请求网络
    private static final Map<Integer, List<VideoCard>> sCachedCards = new HashMap<Integer, List<VideoCard>>();

    private ListView homeList;
    private HomeSectionAdapter adapter;
    private int[] mainCategories;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService imageExecutor;

    private static final int[] CARD_BACKGROUNDS = {
        R.drawable.bili_intent_light,
        R.drawable.bili_intent_dark
    };

    private static final int[] CARD_FOREGROUNDS = {
        R.drawable.bili_intent_to_bangumi,   // 13 番剧
        R.drawable.bili_intent_to_part,      // 11 连载
        R.drawable.bili_intent_to_douga,     // 1 动画
        R.drawable.bili_intent_to_ent,       // 5 娱乐
        R.drawable.bili_intent_to_music,     // 3 音乐
        R.drawable.bili_intent_to_game,      // 4 游戏
        R.drawable.bili_intent_to_ent        // 36 科技
    };

    // 分区卡背景/前景 Drawable 静态缓存复用（2.x 上 setImageResource 每次同步解码大 PNG）
    private static final Drawable[] sCardBackgrounds =
            new Drawable[CARD_BACKGROUNDS.length];
    private static final Drawable[] sCardForegrounds =
            new Drawable[CARD_FOREGROUNDS.length];

    // Banner 是固定图：一次性缩放到屏幕宽后静态复用，绘制 1:1，
    // 避免每帧把 224x150 源图软件上采样到全宽
    private static android.graphics.Bitmap sBannerBitmap;
    private static int sBannerBitmapWidth = 0;

    private Drawable getCachedCardDrawable(int resId, int slot, Drawable[] arr) {
        if (slot < 0 || slot >= arr.length) return null;
        if (arr[slot] == null) {
            try {
                arr[slot] = getResources().getDrawable(resId);
            } catch (Throwable t) {
                arr[slot] = null;
            }
        }
        return arr[slot];
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 统一走 SdkHelper：优先用户设置，未设置再按设备内存给默认值，不写死
        int loadThreads = tv.biliclassic.util.SdkHelper.getImageLoadThreads();
        if (loadThreads <= 1) {
            imageExecutor = Executors.newSingleThreadExecutor();
        } else {
            imageExecutor = Executors.newFixedThreadPool(loadThreads);
        }

        // 进入分区页前先释放全局图片缓存引用，为 inflate 布局腾出空间
        // （Android 2.x 上 bitmap 常驻外部堆，满时 inflate 任何 ImageView 都会 OOM）
        // 不显式 System.gc()
        try {
            GlobalImageCache.getInstance().releaseMemory();
        } catch (Throwable t) {
        }

        View view = null;
        for (int attempt = 0; attempt < 2 && view == null; attempt++) {
            try {
                view = inflater.inflate(R.layout.content_home, container, false);
            } catch (OutOfMemoryError e) {
                GlobalImageCache.getInstance().releaseMemory();
            } catch (android.view.InflateException e) {
                GlobalImageCache.getInstance().releaseMemory();
            } catch (Throwable e) {
                GlobalImageCache.getInstance().releaseMemory();
            }
        }
        // 重试仍失败：返回空布局而非崩溃
        if (view == null) {
            view = new LinearLayout(getActivity());
            view.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        homeList = (ListView) view.findViewById(R.id.home_list);
        if (homeList == null) {
            return view;
        }

        homeList.setDivider(null);
        homeList.setDividerHeight(0);
        homeList.setVerticalFadingEdgeEnabled(false);
        homeList.setHorizontalFadingEdgeEnabled(false);
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 9) {
            tv.biliclassic.util.SdkHelper.setOverScrollNever(homeList);
        }
        homeList.setCacheColorHint(0x00000000);
        homeList.setClipToPadding(false);
        homeList.setFocusable(true);
        homeList.setFocusableInTouchMode(true);

        // 绘制缓存（仅 32MB+ 堆设备）：ViewPager 滑页转场时本页没有被重排，
        // 命中缓存可避免每帧软件重绘全部行；低内存设备开不起每页 ~1.5MB 缓存
        if (tv.biliclassic.util.SdkHelper.isHighMemoryDevice()) {
            homeList.setDrawingCacheEnabled(true);
            homeList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);
        }

        // Banner 作为列表 header；显式按"全宽 × 图片比例"设高，避免 ListView header 里 adjustViewBounds 测量异常
        View banner = inflater.inflate(R.layout.home_banner, homeList, false);
        ImageView bannerImage = (ImageView) banner.findViewById(R.id.banner_image);
        if (bannerImage != null) {
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.bili_main_banner, o);
                if (o.outWidth > 0 && o.outHeight > 0) {
                    int w = getResources().getDisplayMetrics().widthPixels;
                    int h = (int) (w * (float) o.outHeight / o.outWidth);
                    ViewGroup.LayoutParams lp = bannerImage.getLayoutParams();
                    lp.height = h;
                    bannerImage.setLayoutParams(lp);
                }
            } catch (Throwable t) {
            }
            applyBannerBitmap(bannerImage);
        }
        homeList.addHeaderView(banner);

        mainCategories = TidData.getMainCategories();
        String[] names = new String[mainCategories.length];
        for (int i = 0; i < mainCategories.length; i++) {
            names[i] = TidData.getNameByTid(mainCategories[i]);
        }

        adapter = new HomeSectionAdapter(getActivity(), this);
        adapter.setData(mainCategories, names);
        homeList.setAdapter(adapter);

        // 滚动中暂缓封面应用：滑到新分区时封面一张张到达，避免每次触发整屏软件重绘
        homeList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    setScrolling(false);
                } else {
                    setScrolling(true);
                }
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pendingBitmapSets.clear();
        if (imageExecutor != null) {
            imageExecutor.shutdownNow();
            imageExecutor = null;
        }
    }

    private void applyBannerBitmap(final ImageView bannerImage) {
        final int screenW = getResources().getDisplayMetrics().widthPixels;
        if (sBannerBitmap != null && !sBannerBitmap.isRecycled() && sBannerBitmapWidth == screenW) {
            bannerImage.setImageBitmap(sBannerBitmap);
            return;
        }
        if (imageExecutor == null) return;
        imageExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeResource(
                            getResources(), R.drawable.bili_main_banner);
                    if (src == null) return;
                    int h = src.getHeight() * screenW / src.getWidth();
                    if (h < 1) h = 1;
                    android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(src, screenW, h, true);
                    if (scaled == src) {
                        scaled = src;
                    } else {
                        src.recycle();
                    }
                    final android.graphics.Bitmap fScaled = scaled;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null || fScaled.isRecycled()) return;
                            sBannerBitmap = fScaled;
                            sBannerBitmapWidth = screenW;
                            if (bannerImage != null && !fScaled.isRecycled()) {
                                bannerImage.setImageBitmap(fScaled);
                            }
                        }
                    });
                } catch (Throwable t) {
                }
            }
        });
    }

    private void loadThumbnails(final View section, final int tid, final ImageView[] previews) {
        // 命中缓存：直接用缓存数据填充封面，不重新请求网络
        final List<VideoCard> cached;
        synchronized (sCachedCards) {
            cached = sCachedCards.get(tid);
        }
        if (cached != null && cached.size() > 0) {
            final List<VideoCard> cards = cached;
            if (getActivity() == null) return;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() == null) return;
                    if (section.getTag() != null && ((Integer) section.getTag()) == tid) {
                        for (int i = 0; i < previews.length && i < cards.size(); i++) {
                            loadCover(previews[i], cards.get(i));
                        }
                    }
                }
            });
            return;
        }

        imageExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> cards = new ArrayList<VideoCard>();
                    PartitionApi.getRegionVideos(cards, tid, 1);

                    if (cards.size() > 0) {
                        synchronized (sCachedCards) {
                            sCachedCards.put(tid, cards);
                        }
                    }

                    if (getActivity() == null) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null) return;
                            if (section.getTag() != null && ((Integer) section.getTag()) == tid) {
                                for (int i = 0; i < previews.length && i < cards.size(); i++) {
                                    loadCover(previews[i], cards.get(i));
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (homeList == null) return;
        if (!getResources().getBoolean(R.bool.is_tablet)) return;
        for (int i = 0; i < homeList.getChildCount(); i++) {
            View section = homeList.getChildAt(i);
            if (section != null && section.findViewById(R.id.video_area) != null) {
                applyTabletLayout(section);
            }
        }
    }

    private void applyTabletLayout(final View section) {
        if (!isAdded()) return;
        if (!getResources().getBoolean(R.bool.is_tablet)) return;
        LinearLayout videoArea = (LinearLayout) section.findViewById(R.id.video_area);
        if (videoArea == null) return;
        LinearLayout root = (LinearLayout) videoArea.getParent();
        View partitionCard = section.findViewById(R.id.partition_card);
        View v3c = section.findViewById(R.id.video3_container);
        View v4c = section.findViewById(R.id.video4_container);
        ImageView video1 = (ImageView) section.findViewById(R.id.video1_cover);
        ImageView video2 = (ImageView) section.findViewById(R.id.video2_cover);
        ImageView video3 = (ImageView) section.findViewById(R.id.video3_cover);
        ImageView video4 = (ImageView) section.findViewById(R.id.video4_cover);
        LinearLayout.LayoutParams vp = (LinearLayout.LayoutParams) videoArea.getLayoutParams();
        LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) partitionCard.getLayoutParams();
        applyTabletSection(section, videoArea, root, partitionCard,
                video1, video2, video3, video4, v3c, v4c, vp, cp);
    }

    private void applyTabletSection(final View section, final LinearLayout videoArea,
                                    final LinearLayout root, final View partitionCard,
                                    final ImageView video1, final ImageView video2,
                                    final ImageView video3, final ImageView video4,
                                    final View v3c, final View v4c,
                                    final LinearLayout.LayoutParams vp,
                                    final LinearLayout.LayoutParams cp) {
        if (!isAdded()) return;
        if (!getResources().getBoolean(R.bool.is_tablet)) return;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        v3c.setVisibility(View.VISIBLE);
        if (landscape) {
            v4c.setVisibility(View.VISIBLE);
            video4.setScaleType(ImageView.ScaleType.CENTER_CROP);
            vp.weight = 4;
            cp.weight = 3;
            root.setWeightSum(7);
        } else {
            v4c.setVisibility(View.GONE);
            vp.weight = 3;
            cp.weight = 3;
            root.setWeightSum(6);
        }
        videoArea.requestLayout();
        // 权重变化后 ImageView 宽改变，重新应用 4:3 高
        videoArea.post(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                setCoverRatio(video1);
                setCoverRatio(video2);
                setCoverRatio(video3);
                setCoverRatio(video4);
            }
        });
    }

    private void setCoverRatio(ImageView iv) {
        ViewGroup.LayoutParams p = iv.getLayoutParams();
        if (p == null) return;
        View parent = (View) iv.getParent();
        if (parent == null) return;
        int pw = parent.getWidth();
        if (pw > 0) {
            p.width = pw;
            p.height = pw * 3 / 4;
            iv.setLayoutParams(p);
        }
    }

    // 滚动中暂缓应用新图，避免滑到时封面一张张到达触发整屏软件重绘（仅主线程访问）
    private volatile boolean mScrolling = false;
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

    private void setScrolling(boolean scrolling) {
        this.mScrolling = scrolling;
        if (!scrolling) {
            flushPendingBitmapSets();
        }
    }

    private void flushPendingBitmapSets() {
        if (pendingBitmapSets.isEmpty()) return;
        final java.util.ArrayList<Runnable> pending = new java.util.ArrayList<Runnable>(pendingBitmapSets);
        pendingBitmapSets.clear();
        // 分批应用（每帧最多 2 张），避免停下瞬间一次性 setImageBitmap 全部封面造成整帧卡顿
        final int[] idx = {0};
        final Runnable drain = new Runnable() {
            @Override
            public void run() {
                if (imageExecutor == null || imageExecutor.isShutdown()) {
                    return;
                }
                int applied = 0;
                while (idx[0] < pending.size() && applied < 2) {
                    try {
                        pending.get(idx[0]).run();
                    } catch (Throwable t) {
                    }
                    idx[0]++;
                    applied++;
                }
                if (idx[0] < pending.size()) {
                    mainHandler.postDelayed(this, 16);
                }
            }
        };
        drain.run();
    }

    private void setCoverBitmap(final ImageView imageView, final String url, final Bitmap bitmap) {
        if (mScrolling) {
            // 滚动中暂缓应用，停下后统一补显示
            pendingBitmapSets.add(new Runnable() {
                @Override
                public void run() {
                    setCoverBitmap(imageView, url, bitmap);
                }
            });
            return;
        }
        if (imageView.getTag() == null || !imageView.getTag().equals(url)) {
            return;
        }
        android.graphics.drawable.Drawable cur = imageView.getDrawable();
        if (cur instanceof android.graphics.drawable.BitmapDrawable
                && ((android.graphics.drawable.BitmapDrawable) cur).getBitmap() == bitmap) {
            return;
        }
        imageView.setImageBitmap(bitmap);
    }

    private void loadCover(final ImageView imageView, final VideoCard card) {
        String coverUrl = card.cover;
        if (coverUrl == null || coverUrl.length() == 0) return;
        if (coverUrl.startsWith("https://")) {
            coverUrl = "http://" + coverUrl.substring(8);
        }
        final String url = coverUrl;
        imageView.setTag(url);
        // 点击监听前置设置：即使封面未加载，点击预览区也能进详情
        setupClickListener(imageView, card);

        // 布局参数只在值变化时才设置：滚动中重复 setLayoutParams 会触发整表重排（requestLayout），
        // 是首页滚动卡顿的主因之一
        imageView.post(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams params = imageView.getLayoutParams();
                if (params == null) return;
                View parent = (View) imageView.getParent();
                if (parent != null) {
                    int parentWidth = parent.getWidth();
                    if (parentWidth > 0) {
                        int h = parentWidth * 3 / 4;
                        if (params.width != parentWidth || params.height != h) {
                            params.width = parentWidth;
                            params.height = h;
                            imageView.setLayoutParams(params);
                        }
                    }
                }
            }
        });

        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) {
            return;
        }

        Bitmap cached = GlobalImageCache.getInstance().get(url);
        if (cached != null && !cached.isRecycled()) {
            setCoverBitmap(imageView, url, cached);
            return;
        }

        imageExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = downloadImage(url);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        GlobalImageCache.getInstance().put(url, bitmap);
                    }
                    if (bitmap != null && getActivity() != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (getActivity() != null) {
                                    setCoverBitmap(imageView, url, bitmap);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setupClickListener(ImageView imageView, final VideoCard card) {
        final long aid = card.aid;
        final String bvid = card.bvid;
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() == null) return;
                Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
                if (aid != 0) {
                    intent.putExtra("aid", aid);
                } else if (bvid != null && bvid.length() > 0) {
                    intent.putExtra("bvid", bvid);
                }
                startActivity(intent);
            }
        });
    }

    private Bitmap downloadImage(String urlStr) throws Exception {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;

        android.content.Context ctx = tv.biliclassic.BaseActivity.getAppContext();
        if (ctx == null) return null;

        HttpURLConnection conn = null;
        java.io.File tempFile = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.connect();

            tempFile = new java.io.File(ctx.getCacheDir(), "hom_" + urlStr.hashCode() + ".tmp");
            InputStream is = conn.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            is.close();
            fos.close();

            if (!tempFile.exists() || tempFile.length() == 0) return null;

            int targetWidth = 160;
            int targetHeight = 90;
            if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 23) {
                targetWidth = (int)(targetWidth * 1.25f);
                targetHeight = (int)(targetHeight * 1.25f);
            }
            int minScale = tv.biliclassic.util.SdkHelper.getSdkInt() >= 9 ? 2 : 4;
            return GlobalImageCache.decodeFileSafely(tempFile, targetWidth, targetHeight, minScale);
        } finally {
            if (conn != null) conn.disconnect();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    /**
     * 虚拟化行适配器：ListView 每行一个分区卡，只构建可见行。
     */
    static class HomeSectionAdapter extends BaseAdapter {

        private Context context;
        private HomeFragment fragment;
        private int[] tids;
        private String[] names;

        HomeSectionAdapter(Context context, HomeFragment fragment) {
            this.context = context;
            this.fragment = fragment;
        }

        void setData(int[] tids, String[] names) {
            this.tids = tids;
            this.names = names;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return tids == null ? 0 : tids.length;
        }

        @Override
        public Object getItem(int position) {
            return tids == null ? null : tids[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View section = convertView;
            if (section == null) {
                try {
                    section = LayoutInflater.from(context).inflate(R.layout.item_partition_section, parent, false);
                } catch (Throwable t) {
                    section = new LinearLayout(context);
                    section.setLayoutParams(new AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 80));
                }
            }
            bindSection(section, position);
            return section;
        }

        private void bindSection(final View section, final int position) {
            final int tid = tids[position];
            final String name = names[position];
            // 用 tid 作行标签，网络回调回来时校验，避免回收错位
            section.setTag(tid);

            TextView nameView = (TextView) section.findViewById(R.id.partition_name);
            if (nameView != null) {
                nameView.setText(name);
            }

            ImageView cardBg = (ImageView) section.findViewById(R.id.card_background);
            if (cardBg != null) {
                Drawable d = fragment.getCachedCardDrawable(
                        fragment.CARD_BACKGROUNDS[position % 2], position % 2, fragment.sCardBackgrounds);
                if (d != null && cardBg.getDrawable() != d) cardBg.setImageDrawable(d);
            }

            ImageView cardFg = (ImageView) section.findViewById(R.id.card_foreground);
            if (cardFg != null) {
                Drawable d = fragment.getCachedCardDrawable(
                        fragment.CARD_FOREGROUNDS[position], position, fragment.sCardForegrounds);
                if (d != null && cardFg.getDrawable() != d) cardFg.setImageDrawable(d);
            }

            final View partitionCard = section.findViewById(R.id.partition_card);
            partitionCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (fragment.getActivity() == null) return;
                    Intent intent = PartitionDetailActivity.createIntent(fragment.getActivity(), tid);
                    fragment.startActivity(intent);
                }
            });

            ImageView[] previews = new ImageView[4];
            previews[0] = (ImageView) section.findViewById(R.id.video1_cover);
            previews[1] = (ImageView) section.findViewById(R.id.video2_cover);
            previews[2] = (ImageView) section.findViewById(R.id.video3_cover);
            previews[3] = (ImageView) section.findViewById(R.id.video4_cover);
            for (ImageView iv : previews) {
                if (iv != null) iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }

            fragment.loadThumbnails(section, tid, previews);
            fragment.applyTabletLayout(section);
        }
    }
}
