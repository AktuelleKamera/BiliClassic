package tv.biliclassic;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    private LinearLayout partitionsContainer;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Android 2.x 堆很小，串行加载避免并发解码耗尽外部堆
        imageExecutor = Executors.newSingleThreadExecutor();

        // 进入分区页前先释放全局图片缓存引用，为 inflate 布局腾出空间
        // （Android 2.x 上 bitmap 常驻外部堆，满时 inflate 任何 ImageView 都会 OOM）
        try {
            GlobalImageCache.getInstance().releaseMemory();
            System.gc();
        } catch (Throwable t) {
        }

        View view = null;
        for (int attempt = 0; attempt < 2 && view == null; attempt++) {
            try {
                view = inflater.inflate(R.layout.content_home, container, false);
            } catch (OutOfMemoryError e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (android.view.InflateException e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (Throwable e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            }
        }
        // 重试仍失败：返回空布局而非崩溃，分区内容留空
        if (view == null) {
            view = new LinearLayout(getActivity());
            view.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        partitionsContainer = (LinearLayout) view.findViewById(R.id.partitions_container);
        if (partitionsContainer == null) {
            partitionsContainer = new LinearLayout(getActivity());
            partitionsContainer.setOrientation(LinearLayout.VERTICAL);
            ((LinearLayout) view).addView(partitionsContainer);
        }

        mainCategories = TidData.getMainCategories();

        for (int i = 0; i < mainCategories.length; i++) {
            final int tid = mainCategories[i];
            final String name = TidData.getNameByTid(tid);
            addPartitionSection(name, tid, i);
        }
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (imageExecutor != null) {
            imageExecutor.shutdownNow();
            imageExecutor = null;
        }
    }

    private void addPartitionSection(final String partitionName, final int tid, final int index) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        // Android 2.x 外部堆很小，inflate 布局加载资源图片可能抛 InflateException（cause 为 OOM）。
        // 先释放全局图片缓存，重试一次仍失败则跳过该分区，避免整页崩溃
        View section = null;
        for (int attempt = 0; attempt < 2 && section == null; attempt++) {
            try {
                section = inflater.inflate(R.layout.item_partition_section, partitionsContainer, false);
            } catch (OutOfMemoryError e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (android.view.InflateException e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (Throwable e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            }
        }
        if (section == null) {
            return;
        }
        final View view = section;

        TextView partitionNameView = (TextView) view.findViewById(R.id.partition_name);
        partitionNameView.setText(partitionName);

        ImageView cardBg = (ImageView) view.findViewById(R.id.card_background);
        try {
            cardBg.setImageResource(CARD_BACKGROUNDS[index % 2]);
        } catch (OutOfMemoryError e) {
            GlobalImageCache.getInstance().freeAllUnreferenced();
            System.gc();
            cardBg.setImageDrawable(null);
        }

        ImageView cardFg = (ImageView) view.findViewById(R.id.card_foreground);
        try {
            cardFg.setImageResource(CARD_FOREGROUNDS[index]);
        } catch (OutOfMemoryError e) {
            GlobalImageCache.getInstance().freeAllUnreferenced();
            System.gc();
            cardFg.setImageDrawable(null);
        }

        final View partitionCard = view.findViewById(R.id.partition_card);
        partitionCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() == null) return;
                Intent intent = PartitionDetailActivity.createIntent(getActivity(), tid);
                startActivity(intent);
            }
        });

        final ImageView video1 = (ImageView) view.findViewById(R.id.video1_cover);
        final ImageView video2 = (ImageView) view.findViewById(R.id.video2_cover);
        final ImageView video3 = (ImageView) view.findViewById(R.id.video3_cover);
        final ImageView video4 = (ImageView) view.findViewById(R.id.video4_cover);
        video1.setScaleType(ImageView.ScaleType.CENTER_CROP);
        video2.setScaleType(ImageView.ScaleType.CENTER_CROP);
        video3.setScaleType(ImageView.ScaleType.CENTER_CROP);
        video4.setScaleType(ImageView.ScaleType.CENTER_CROP);

        final LinearLayout videoArea = (LinearLayout) view.findViewById(R.id.video_area);
        final LinearLayout root = (LinearLayout) videoArea.getParent();
        final View v3c = view.findViewById(R.id.video3_container);
        final View v4c = view.findViewById(R.id.video4_container);
        final LinearLayout.LayoutParams vp = (LinearLayout.LayoutParams) videoArea.getLayoutParams();
        final LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) partitionCard.getLayoutParams();

        loadThumbnails(tid, new ImageView[]{video1, video2, video3, video4});
        partitionsContainer.addView(view);

        view.post(new Runnable() {
            public void run() {
                if (!isAdded()) return;
                applyTabletSection(view, videoArea, root, partitionCard,
                        video1, video2, video3, video4, v3c, v4c, vp, cp);
            }
        });
    }

    private void loadThumbnails(final int tid, final ImageView[] previews) {
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
                    for (int i = 0; i < previews.length && i < cards.size(); i++) {
                        loadCover(previews[i], cards.get(i));
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
                            for (int i = 0; i < previews.length && i < cards.size(); i++) {
                                loadCover(previews[i], cards.get(i));
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
        if (partitionsContainer == null) return;
        if (!getResources().getBoolean(R.bool.is_tablet)) return;
        for (int i = 0; i < partitionsContainer.getChildCount(); i++) {
            View section = partitionsContainer.getChildAt(i);
            LinearLayout videoArea = (LinearLayout) section.findViewById(R.id.video_area);
            if (videoArea == null) continue;
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

    private void loadCover(final ImageView imageView, final VideoCard card) {
        String coverUrl = card.cover;
        if (coverUrl == null || coverUrl.length() == 0) return;
        if (coverUrl.startsWith("https://")) {
            coverUrl = "http://" + coverUrl.substring(8);
        }
        final String url = coverUrl;
        imageView.setTag(url);

        imageView.post(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams params = imageView.getLayoutParams();
                if (params != null) {
                    View parent = (View) imageView.getParent();
                    if (parent != null) {
                        int parentWidth = parent.getWidth();
                        if (parentWidth > 0) {
                            params.width = parentWidth;
                            params.height = parentWidth * 3 / 4;
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
            imageView.setImageBitmap(cached);
            setupClickListener(imageView, card);
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
                                if (getActivity() != null
                                        && imageView.getTag() != null
                                        && imageView.getTag().equals(url)) {
                                    imageView.setImageBitmap(bitmap);
                                    setupClickListener(imageView, card);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                targetWidth = (int)(targetWidth * 1.25f);
                targetHeight = (int)(targetHeight * 1.25f);
            }
            int minScale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD ? 2 : 4;
            return GlobalImageCache.decodeFileSafely(tempFile, targetWidth, targetHeight, minScale);
        } finally {
            if (conn != null) conn.disconnect();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }
}
