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

    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private boolean isScrolling = false;

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private int getConfiguredThreadCount() {
        int savedThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (savedThreads > 0) {
            return savedThreads;
        }
        return isLowMemoryDevice() ? 1 : 3;
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

    public HistoryAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
        initExecutor();
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
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
                    // 继续执行点击监听
                } else {
                    imageCache.remove(finalCoverUrl);
                }
            }

            Boolean isLoading = loadingMap.get(currentPos);
            if (isLoading == null || !isLoading) {
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
            }
        } else {
            holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
        }

        final int pos = position;
        final VideoCard clickItem = item;
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof HistoryActivity) {
                    ((HistoryActivity) context).onHistoryClick(clickItem, pos);
                }
            }
        });

        return convertView;
    }

    private Bitmap downloadImage(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            // Android 2.3 兼容：https 转 http
            if (urlStr != null && urlStr.startsWith("https://")) {
                urlStr = "http://" + urlStr.substring(8);
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            is = conn.getInputStream();

            // 第一步：只读取图片尺寸，不加载像素
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            // 第二步：根据图片尺寸计算采样率
            int sampleSize = 1;
            int targetSize = 200;  // 历史记录缩略图目标尺寸

            while (opts.outWidth / sampleSize > targetSize
                    || opts.outHeight / sampleSize > targetSize) {
                sampleSize *= 2;
                if (sampleSize > 16) break;  // 限制最大采样
            }

            // 重新连接，获取输入流
            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            // 第三步：用采样率解码
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;  // 节省内存
            return BitmapFactory.decodeStream(is, null, opts);

        } catch (OutOfMemoryError e) {
            System.gc();
            // OOM 时固定用 8 倍采样重试
            try {
                if (conn != null) conn.disconnect();
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();
                is = conn.getInputStream();
                BitmapFactory.Options optsRetry = new BitmapFactory.Options();
                optsRetry.inSampleSize = 8;
                optsRetry.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(is, null, optsRetry);
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception e) {}
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