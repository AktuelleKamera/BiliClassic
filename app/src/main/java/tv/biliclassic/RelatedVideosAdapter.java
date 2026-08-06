package tv.biliclassic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
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
import tv.biliclassic.util.GlobalImageCache;
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

    // 滚动中暂缓应用新图，避免每张图到达都触发整屏软件重绘（仅主线程访问）
    private volatile boolean mScrolling = false;
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 24576;
    }

    private int getConfiguredThreadCount() {
        // 统一走 SdkHelper：优先用户设置，未设置再按设备内存给默认值，不写死
        return tv.biliclassic.util.SdkHelper.getImageLoadThreads();
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

    /** 滚动状态变化时由 ListView 的 OnScrollListener 调用 */
    public void setScrolling(boolean scrolling) {
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
                if (executor == null || executor.isShutdown()) {
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

    private void applyCover(final ImageView coverView, final String url, final Bitmap bitmap) {
        if (mScrolling) {
            // 滚动中暂缓应用，停下后统一补显示
            pendingBitmapSets.add(new Runnable() {
                @Override
                public void run() {
                    applyCover(coverView, url, bitmap);
                }
            });
            return;
        }
        Object tag = coverView.getTag();
        if (tag == null || !tag.equals(url)) {
            return;
        }
        android.graphics.drawable.Drawable cur = coverView.getDrawable();
        if (cur instanceof android.graphics.drawable.BitmapDrawable
                && ((android.graphics.drawable.BitmapDrawable) cur).getBitmap() == bitmap) {
            return;
        }
        coverView.setImageBitmap(bitmap);
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

            boolean alreadySet = false;
            SoftReference<Bitmap> softBitmap;
            synchronized (imageCache) {
                softBitmap = imageCache.get(finalCoverUrl);
            }
            if (softBitmap != null) {
                Bitmap cachedBitmap = softBitmap.get();
                if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                    alreadySet = true;
                    applyCover(coverView, finalCoverUrl, cachedBitmap);
                } else {
                    synchronized (imageCache) {
                        imageCache.remove(finalCoverUrl);
                    }
                }
            }

            // 命中缓存不再重新下载（原逻辑每 bind 都会重新下载）
            if (!alreadySet) {
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
                                        applyCover(coverView, finalCoverUrl, bitmap);
                                    }
                                });
                            }
                        }
                    });
                }
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

            if (!tempFile.exists() || tempFile.length() == 0) return null;

            // 按实际显示尺寸解码（封面 76x56dp），1:1 绘制无需软件缩放滤镜
            float density = context.getResources().getDisplayMetrics().density;
            int minScale = tv.biliclassic.util.SdkHelper.getSdkInt() >= 9 ? 2 : 4;
            return GlobalImageCache.decodeFileSafely(tempFile,
                    (int) (76 * density + 0.5f), (int) (56 * density + 0.5f), minScale);
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
        pendingBitmapSets.clear();
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