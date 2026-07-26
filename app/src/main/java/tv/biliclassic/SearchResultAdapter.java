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

import tv.biliclassic.util.SharedPreferencesUtil;

public class SearchResultAdapter extends BaseAdapter {

    private Context context;
    private List<SearchActivity.SearchResultItem> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();

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
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
        }
    }

    public SearchResultAdapter(Context context, List<SearchActivity.SearchResultItem> list) {
        this.context = context;
        this.list = list;
        initExecutor();
    }

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
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.search_result_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.author = (TextView) convertView.findViewById(R.id.author);
            holder.play = (TextView) convertView.findViewById(R.id.play);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final SearchActivity.SearchResultItem item = list.get(position);
        final int currentPos = position;

        holder.title.setText(item.title);
        holder.author.setText(item.author);
        holder.play.setText(item.play + "播放");

        // 先设置占位图
        holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

        if (item.cover != null && item.cover.length() > 0) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }

            final String finalCoverUrl = coverUrl;
            final ImageView coverView = holder.cover;
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

            Boolean isLoading = loadingMap.get(Integer.valueOf(currentPos));
            if (isLoading != null && isLoading.booleanValue()) {
                return convertView;
            }

            loadingMap.put(Integer.valueOf(currentPos), Boolean.TRUE);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = downloadImage(finalCoverUrl);
                    loadingMap.remove(Integer.valueOf(currentPos));

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

    public void updateData(List<SearchActivity.SearchResultItem> newList) {
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
        TextView author;
        TextView play;
        ImageView cover;
    }
}