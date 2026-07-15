package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.SharedPreferencesUtil;

public class FavoriteVideoAdapter extends BaseAdapter {

    private Context context;
    private List<VideoCard> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private boolean isLowMemory = false;

    // 长按检测
    private Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;
    private int longPressPosition = -1;
    private boolean isLongPressTriggered = false;

    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    private OnDeleteClickListener deleteClickListener;

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteClickListener = listener;
    }

    public FavoriteVideoAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
        if (this.list == null) {
            this.list = new ArrayList<VideoCard>();
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
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_video, parent, false);
            holder = new ViewHolder();
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.author = (TextView) convertView.findViewById(R.id.up_name);   // 改为 up_name
            holder.play = (TextView) convertView.findViewById(R.id.view);        // 改为 view
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (list == null || position < 0 || position >= list.size()) {
            holder.title.setText("嘿咻…嘿咻…");
            return convertView;
        }

        final VideoCard item = list.get(position);
        if (item == null) {
            holder.title.setText("视频信息错误");
            return convertView;
        }

        holder.title.setText(item.title != null ? item.title : "无标题");
        holder.author.setText(item.upName != null ? item.upName : "未知UP主");
        holder.play.setText(item.view != null ? item.view : "0观看");

        holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

        if (item.cover != null && item.cover.length() > 0) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }

            final String finalCoverUrl = coverUrl;
            final ImageView coverView = holder.cover;
            final int currentPos = position;
            coverView.setTag(finalCoverUrl);

            Bitmap cachedBitmap = null;
            synchronized (imageCache) {
                SoftReference<Bitmap> softBitmap = imageCache.get(finalCoverUrl);
                if (softBitmap != null) {
                    cachedBitmap = softBitmap.get();
                    if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                        coverView.setImageBitmap(cachedBitmap);
                    } else {
                        imageCache.remove(finalCoverUrl);
                    }
                }
            }

            if (cachedBitmap == null || cachedBitmap.isRecycled()) {
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
            }
        }

        final int pos = position;
        final VideoCard clickItem = item;

        // 直接设置点击，不拦截长按
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof FavoriteVideoListActivity) {
                    ((FavoriteVideoListActivity) context).onVideoClick(clickItem, pos);
                }
            }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (deleteClickListener != null) {
                    deleteClickListener.onDeleteClick(pos);
                    return true;
                }
                return false;
            }
        });

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

            int targetWidth = (int) (120 * context.getResources().getDisplayMetrics().density);
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

    public void updateData(List<VideoCard> newList) {
        if (newList == null) {
            this.list.clear();
            loadingMap.clear();
            notifyDataSetChanged();
            return;
        }
        this.list.clear();
        this.list.addAll(newList);
        loadingMap.clear();
        notifyDataSetChanged();
    }

    public void clearCache() {
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

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView author;
        TextView play;
    }
}