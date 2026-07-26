package tv.biliclassic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.SharedPreferencesUtil;

public class HistoryAdapter extends BaseAdapter {

    private Context context;
    private List<VideoCard> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();

    private boolean isScrolling = false;
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private int getConfiguredThreadCount() {
        int savedThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (savedThreads > 0) {
            // 手动设置了线程数，直接使用喵
            return savedThreads;
        }
        // 自动检测：低内存用1线程，否则用3线程
        return isLowMemoryDevice() ? 1 : 3;
    }

    private void initExecutor() {
        // 关闭旧的 executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        int threadCount = getConfiguredThreadCount();
        if (threadCount <= 1) {
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
        }
    }

    public HistoryAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
        initExecutor();
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
    }

    // 当设置改变时，重新初始化线程池
    public void reloadExecutor() {
        initExecutor();
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
            convertView = LayoutInflater.from(context).inflate(R.layout.history_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.upName = (TextView) convertView.findViewById(R.id.up_name);
            holder.progress = (TextView) convertView.findViewById(R.id.progress);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final VideoCard item = list.get(position);
        final int currentPos = position;

        holder.title.setText(item.title);
        holder.upName.setText(item.upName);
        holder.progress.setText(item.view);

        if (item.cover != null && item.cover.length() > 0) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }

            final String finalCoverUrl = coverUrl;
            final ImageView coverView = holder.cover;

            coverView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            coverView.setTag(finalCoverUrl);

            SoftReference<Bitmap> softBitmap = imageCache.get(finalCoverUrl);
            if (softBitmap != null) {
                Bitmap cachedBitmap = softBitmap.get();
                if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                    coverView.setImageBitmap(cachedBitmap);
                    return convertView;
                } else {
                    imageCache.remove(finalCoverUrl);
                }
            }

            Boolean isLoading = loadingMap.get(currentPos);
            if (isLoading != null && isLoading) {
                return convertView;
            }

            loadingMap.put(currentPos, true);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = downloadImage(finalCoverUrl);
                    loadingMap.remove(currentPos);

                    if (bitmap != null && !bitmap.isRecycled()) {
                        imageCache.put(finalCoverUrl, new SoftReference<Bitmap>(bitmap));
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Object currentTag = coverView.getTag();
                                if (currentTag != null && currentTag.equals(finalCoverUrl)) {
                                    coverView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    }
                }
            });
        } else {
            holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
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

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            int targetWidth = (int) (160 * context.getResources().getDisplayMetrics().density);
            int scale = 1;
            if (options.outWidth > targetWidth) {
                scale = options.outWidth / targetWidth;
                if (scale < 1) scale = 1;
                if (scale > 4) scale = 4;
            }

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
        loadingMap.clear();
    }

    static class ViewHolder {
        TextView title;
        TextView upName;
        TextView progress;
        ImageView cover;
    }
}