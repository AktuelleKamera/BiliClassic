package tv.biliclassic;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.List;

import tv.biliclassic.api.PartitionApi;
import tv.biliclassic.model.VideoCard;

public class HomeFragment extends Fragment {

    private LinearLayout partitionsContainer;
    private int[] mainCategories;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        View view = inflater.inflate(R.layout.content_home, container, false);
        partitionsContainer = (LinearLayout) view.findViewById(R.id.partitions_container);

        mainCategories = TidData.getMainCategories();

        for (int i = 0; i < mainCategories.length; i++) {
            final int tid = mainCategories[i];
            final String name = TidData.getNameByTid(tid);
            addPartitionSection(name, tid, i);
        }
        return view;
    }

    private void addPartitionSection(final String partitionName, final int tid, final int index) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View section = inflater.inflate(R.layout.item_partition_section, partitionsContainer, false);

        TextView partitionNameView = (TextView) section.findViewById(R.id.partition_name);
        partitionNameView.setText(partitionName);

        ImageView cardBg = (ImageView) section.findViewById(R.id.card_background);
        cardBg.setImageResource(CARD_BACKGROUNDS[index % 2]);

        ImageView cardFg = (ImageView) section.findViewById(R.id.card_foreground);
        cardFg.setImageResource(CARD_FOREGROUNDS[index]);

        View partitionCard = section.findViewById(R.id.partition_card);
        partitionCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() == null) return;
                Intent intent = PartitionDetailActivity.createIntent(getActivity(), tid);
                startActivity(intent);
            }
        });

        final ImageView video1 = (ImageView) section.findViewById(R.id.video1_cover);
        final ImageView video2 = (ImageView) section.findViewById(R.id.video2_cover);
        video1.setScaleType(ImageView.ScaleType.CENTER_CROP);
        video2.setScaleType(ImageView.ScaleType.CENTER_CROP);

        loadThumbnails(tid, video1, video2);

        partitionsContainer.addView(section);
    }

    private void loadThumbnails(final int tid, final ImageView video1, final ImageView video2) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> cards = new ArrayList<VideoCard>();
                    PartitionApi.getRegionVideos(cards, tid, 1);

                    if (getActivity() == null) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() == null) return;
                            if (cards.size() > 0) loadCover(video1, cards.get(0));
                            if (cards.size() > 1) loadCover(video2, cards.get(1));
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void loadCover(final ImageView imageView, final VideoCard card) {
        String coverUrl = card.cover;
        if (coverUrl == null || coverUrl.length() == 0) return;
        if (coverUrl.startsWith("https://")) {
            coverUrl = "http://" + coverUrl.substring(8);
        }
        final String url = coverUrl;
        imageView.setTag(url);

        // 固定尺寸：宽度用屏幕宽度的 2/7（左右各占 weight=2），高度按 4:3 计算
        imageView.post(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams params = imageView.getLayoutParams();
                if (params != null) {
                    // 获取父容器宽度
                    View parent = (View) imageView.getParent();
                    if (parent != null) {
                        int parentWidth = parent.getWidth();
                        if (parentWidth > 0) {
                            // 宽度 = 父容器宽度（weight=2，但实际宽度由 LinearLayout 分配）
                            // 这里用 parentWidth 作为实际宽度
                            params.width = parentWidth;
                            // 4:3 比例，高度 = 宽度 * 3 / 4
                            params.height = parentWidth * 3 / 4;
                            imageView.setLayoutParams(params);
                        }
                    }
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = downloadImage(url);
                    if (bitmap != null && getActivity() != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (getActivity() != null
                                        && imageView.getTag() != null
                                        && imageView.getTag().equals(url)) {
                                    imageView.setImageBitmap(bitmap);
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
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Bitmap downloadImage(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.connect();

        InputStream is = conn.getInputStream();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        is.close();
        conn.disconnect();

        int targetWidth = 160;
        int targetHeight = 90;
        int scale = 1;
        if (options.outWidth > targetWidth || options.outHeight > targetHeight) {
            scale = Math.max(options.outWidth / targetWidth, options.outHeight / targetHeight);
            if (scale < 1) scale = 1;
            if (scale > 8) scale = 8;
        }

        conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.connect();
        is = conn.getInputStream();

        options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
        is.close();
        conn.disconnect();
        return bitmap;
    }
}
