package tv.biliclassic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.SharedPreferencesUtil;

public class RecommendGridAdapter extends BaseAdapter {

    private Context context;
    private List<VideoCard> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private List<String> cacheKeys = new ArrayList<String>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();

    private boolean isScrolling = false;
    private static final int MAX_CACHE_SIZE = 12;

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private int getConfiguredThreadCount() {
        int savedThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (savedThreads > 0) {
            return savedThreads;
        }
        return isLowMemoryDevice() ? 1 : 2;
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        int threadCount = getConfiguredThreadCount();
        if (threadCount <= 1) {
            executor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
        } else {
            executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
        }
    }

    public RecommendGridAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
        initExecutor();
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
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
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_recommend, parent, false);
            holder = new ViewHolder();
            holder.coverContainer = (FrameLayout) convertView.findViewById(R.id.cover_container);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.view = (TextView) convertView.findViewById(R.id.view);
            holder.danmaku = (TextView) convertView.findViewById(R.id.danmaku);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        int containerWidth = parent.getWidth() / 2 - dpToPx(6);
        if (containerWidth > 0) {
            int containerHeight = containerWidth * 9 / 16;
            ViewGroup.LayoutParams params = holder.coverContainer.getLayoutParams();
            params.height = containerHeight;
            holder.coverContainer.setLayoutParams(params);
        }

        VideoCard item = list.get(position);
        if (item != null) {
            holder.title.setText(item.title);

            String viewText = (item.view != null && item.view.length() > 0) ? item.view : "0";
            holder.view.setText(viewText);

            String danmakuText = (item.danmaku > 0) ? String.valueOf(item.danmaku) : "0";
            holder.danmaku.setText(danmakuText);
        }

        // 先设置默认占位图，并清除之前的图片引用
        holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

        if (isScrolling) {
            return convertView;
        }

        if (item != null && item.cover != null && item.cover.length() > 0) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }
            final String finalUrl = coverUrl;
            final ImageView coverView = holder.cover;
            final int currentPos = position;
            coverView.setTag(finalUrl);

            // 检查缓存
            SoftReference<Bitmap> softBitmap = imageCache.get(finalUrl);
            if (softBitmap != null) {
                Bitmap cached = softBitmap.get();
                if (cached != null && !cached.isRecycled()) {
                    coverView.setImageBitmap(cached);
                    return convertView;
                } else {
                    imageCache.remove(finalUrl);
                    cacheKeys.remove(finalUrl);
                }
            }

            // 避免重复加载
            Boolean isLoading = loadingMap.get(currentPos);
            if (isLoading != null && isLoading) {
                return convertView;
            }

            loadingMap.put(currentPos, true);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = downloadImage(finalUrl);
                    loadingMap.remove(currentPos);

                    if (bitmap != null && !bitmap.isRecycled()) {
                        // 存入缓存
                        imageCache.put(finalUrl, new SoftReference<Bitmap>(bitmap));
                        // 管理缓存大小（不主动回收，让 SoftReference 自动管理）
                        if (!cacheKeys.contains(finalUrl)) {
                            if (cacheKeys.size() >= MAX_CACHE_SIZE) {
                                cacheKeys.remove(0);
                            }
                            cacheKeys.add(finalUrl);
                        }
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Object tag = coverView.getTag();
                                if (tag != null && tag.equals(finalUrl)) {
                                    coverView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    }
                }
            });
        }

        return convertView;
    }

    private Bitmap downloadImage(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            InputStream is = conn.getInputStream();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            int targetWidth = 160;
            int targetHeight = 90;
            int scale = 1;

            int outWidth = options.outWidth;
            int outHeight = options.outHeight;

            if (outWidth > targetWidth || outHeight > targetHeight) {
                int widthRatio = outWidth / targetWidth;
                int heightRatio = outHeight / targetHeight;
                scale = Math.max(widthRatio, heightRatio);
                if (scale < 1) scale = 1;
                if (scale > 8) scale = 8;
            }

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inPurgeable = true;
            options.inInputShareable = true;

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();

            return bitmap;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    public void updateData(List<VideoCard> newList) {
        this.list = newList;
        loadingMap.clear();
        notifyDataSetChanged();
    }

    public void clearCache() {
        for (SoftReference<Bitmap> ref : imageCache.values()) {
            Bitmap bmp = ref.get();
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        imageCache.clear();
        cacheKeys.clear();
        loadingMap.clear();
    }

    static class ViewHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView title;
        TextView view;
        TextView danmaku;
    }
}