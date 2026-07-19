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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

public class RelatedVideosAdapter extends BaseAdapter {

    public interface OnVideoClickListener {
        void onVideoClick(VideoCard video, int position);
    }

    public interface OnVideoLongClickListener {
        void onVideoLongClick(VideoCard video, int position);
    }

    private Context context;
    private List<VideoCard> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private OnVideoClickListener mClickListener;
    private OnVideoLongClickListener mLongClickListener;

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

    public RelatedVideosAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
        initExecutor();
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.mClickListener = listener;
    }

    public void setOnVideoLongClickListener(OnVideoLongClickListener listener) {
        this.mLongClickListener = listener;
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
            convertView = LayoutInflater.from(context).inflate(R.layout.history_item, parent, false);
            holder = new ViewHolder();
            holder.coverContainer = (FrameLayout) convertView.findViewById(R.id.cover_container);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.upName = (TextView) convertView.findViewById(R.id.up_name);
            holder.progress = (TextView) convertView.findViewById(R.id.progress);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final VideoCard item = list.get(position);
        final int currentPos = position;

        holder.title.setText(item.title);
        holder.upName.setText(item.upName);
        holder.progress.setText(item.view);

        holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

        if (item.cover != null && item.cover.length() > 0) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }

            final String finalCoverUrl = coverUrl;
            final ImageView coverView = holder.cover;
            coverView.setTag(finalCoverUrl);

            SoftReference<Bitmap> softBitmap;
            synchronized (imageCache) {
                softBitmap = imageCache.get(finalCoverUrl);
            }
            if (softBitmap != null) {
                Bitmap cachedBitmap = softBitmap.get();
                if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                    coverView.setImageBitmap(cachedBitmap);
                } else {
                    synchronized (imageCache) {
                        imageCache.remove(finalCoverUrl);
                    }
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
                            synchronized (imageCache) {
                                imageCache.put(finalCoverUrl, new SoftReference<Bitmap>(bitmap));
                            }
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
        }

        final VideoCard clickItem = item;
        final int pos = position;

        // 点击
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickListener != null) {
                    mClickListener.onVideoClick(clickItem, pos);
                }
            }
        });

        // 长按
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mLongClickListener != null) {
                    mLongClickListener.onVideoLongClick(clickItem, pos);
                    return true;
                }
                return false;
            }
        });

        return convertView;
    }

    private Bitmap downloadImage(String urlStr) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;
        HttpURLConnection conn = null;
        java.io.File tempFile = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            // 下载到临时文件
            tempFile = new java.io.File(context.getCacheDir(), "rel_" + urlStr.hashCode() + ".tmp");
            InputStream is = conn.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            is.close();
            fos.close();
            conn.disconnect();
            conn = null;

            if (!tempFile.exists() || tempFile.length() == 0) return null;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);

            int targetWidth = (int) (160 * context.getResources().getDisplayMetrics().density);
            int scale = 1;
            if (options.outWidth > targetWidth && options.outWidth > 0) {
                scale = options.outWidth / targetWidth;
                if (scale < 1) scale = 1;
                if (scale > 4) scale = 4;
            }

            Bitmap bitmap = null;
            while (scale <= 16 && bitmap == null) {
                try {
                    options = new BitmapFactory.Options();
                    options.inSampleSize = scale;
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    bitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);
                } catch (OutOfMemoryError e) {
                    scale *= 2;
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    public void updateData(List<VideoCard> newList) {
        this.list = newList;
        loadingMap.clear();
        notifyDataSetChanged();
    }

    public void clearCache() {
        if (imageCache != null) {
            synchronized (imageCache) {
                for (SoftReference<Bitmap> ref : imageCache.values()) {
                    Bitmap bmp = ref.get();
                    if (bmp != null && !bmp.isRecycled()) {
                        bmp.recycle();
                    }
                }
                imageCache.clear();
            }
        }
        if (loadingMap != null) {
            loadingMap.clear();
        }
        notifyDataSetChanged();
    }

    static class ViewHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView title;
        TextView upName;
        TextView progress;
    }
}