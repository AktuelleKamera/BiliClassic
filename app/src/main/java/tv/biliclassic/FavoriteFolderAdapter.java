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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.util.SharedPreferencesUtil;

public class FavoriteFolderAdapter extends BaseAdapter {

    private Context context;
    private List<FavoriteFolder> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private boolean isLowMemory = false;

    public FavoriteFolderAdapter(Context context, List<FavoriteFolder> list) {
        this.context = context;
        this.list = list;
        if (this.list == null) {
            this.list = new ArrayList<FavoriteFolder>();
        }
        this.isLowMemory = isLowMemoryDevice();
        initExecutor();
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private int getConfiguredThreadCount() {
        int savedThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (savedThreads > 0) {
            return savedThreads;
        }
        return isLowMemory ? 1 : 2;
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

    public void reloadExecutor() {
        initExecutor();
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        if (list == null || position < 0 || position >= list.size()) {
            return null;
        }
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (list == null || position < 0 || position >= list.size()) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_folder, parent, false);
            }
            return convertView;
        }

        final FavoriteFolder item = list.get(position);
        if (item == null) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_folder, parent, false);
            }
            return convertView;
        }

        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_folder, parent, false);
            holder = new ViewHolder();
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.count = (TextView) convertView.findViewById(R.id.count);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.name.setText(item.name != null ? item.name : "");
        holder.count.setText((item.videoCount >= 0 ? item.videoCount : 0) + "个视频");

        // 占位图
        holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

        // 加载封面
        if (item.cover != null && item.cover.length() > 0) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }

            final String finalCoverUrl = coverUrl;
            final ImageView coverView = holder.cover;
            final int currentPos = position;
            coverView.setTag(finalCoverUrl);

            // 检查缓存（加同步锁）
            Bitmap cachedBitmap = null;
            synchronized (imageCache) {
                SoftReference<Bitmap> softBitmap = imageCache.get(finalCoverUrl);
                if (softBitmap != null) {
                    cachedBitmap = softBitmap.get();
                    if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                        coverView.setImageBitmap(cachedBitmap);
                        return convertView;
                    } else {
                        imageCache.remove(finalCoverUrl);
                    }
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
                        synchronized (imageCache) {
                            imageCache.put(finalCoverUrl, new SoftReference<Bitmap>(bitmap));
                        }
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Object tag = coverView.getTag();
                                if (tag != null && tag.equals(finalCoverUrl)) {
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

            // 获取图片尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            // 重新连接
            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            // 计算采样率
            int targetWidth = (int) (120 * context.getResources().getDisplayMetrics().density);
            int scale = 1;
            if (options.outWidth > targetWidth) {
                scale = options.outWidth / targetWidth;
                if (scale < 1) scale = 1;
                if (scale > 4) scale = 4;
            }

            // 使用 RGB_565 格式节省清朝老设备的内存
            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inPurgeable = true;
            options.inInputShareable = true;

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            return bitmap;
        } catch (OutOfMemoryError e) {
            System.gc();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void updateData(List<FavoriteFolder> newList) {
        if (newList == null) {
            this.list = new ArrayList<FavoriteFolder>();
        } else {
            this.list = newList;
        }
        loadingMap.clear();
        notifyDataSetChanged();
    }

    public void clearCache() {
        // 加同步锁，防止 ConcurrentModificationException
        synchronized (imageCache) {
            for (SoftReference<Bitmap> ref : imageCache.values()) {
                Bitmap bmp = ref.get();
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
            }
            imageCache.clear();
        }
        loadingMap.clear();
    }

    public void clearCacheForActivity() {
        clearCache();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
    }

    static class ViewHolder {
        TextView name;
        TextView count;
        ImageView cover;
    }
}