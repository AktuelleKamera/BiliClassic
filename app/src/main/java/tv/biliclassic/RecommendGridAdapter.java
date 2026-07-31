package tv.biliclassic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.SharedPreferencesUtil;

public class RecommendGridAdapter extends BaseAdapter {

    private static final String TAG = "RecommendAdapter";
    private Context context;
    private List<VideoCard> list;
    private int numColumns = 2;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public RecommendGridAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
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

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
    }

    public int getNumColumns() {
        return numColumns;
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
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(
                    R.layout.item_recommend_simple, parent, false);
                holder = new ViewHolder();
                holder.title = (TextView) convertView.findViewById(R.id.simple_title);
                holder.view = (TextView) convertView.findViewById(R.id.simple_views);
                holder.danmaku = (TextView) convertView.findViewById(R.id.simple_danmaku);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            VideoCard item = list.get(position);
            if (item != null) {
                holder.title.setText(item.title);
                holder.view.setText("\u25B6 " + (item.view != null ? item.view : "0"));
                holder.danmaku.setText("\u2726 " + (item.danmaku > 0 ? String.valueOf(item.danmaku) : "0"));
            }
            return convertView;
        }
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_recommend, parent, false);
            holder = new ViewHolder();
            holder.coverContainer = (FrameLayout) convertView.findViewById(R.id.cover_container);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
                holder.title.setMaxLines(Integer.MAX_VALUE);
                holder.title.setEllipsize(null);
            }
            holder.view = (TextView) convertView.findViewById(R.id.view);
            holder.danmaku = (TextView) convertView.findViewById(R.id.danmaku);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (holder.currentCoverUrl != null) {
            GlobalImageCache.getInstance().release(holder.currentCoverUrl);
            holder.currentCoverUrl = null;
        }

        int parentWidth = parent.getWidth();
        int containerWidth;
        if (parentWidth > 0) {
            containerWidth = parentWidth / numColumns - dpToPx(6);
        } else {
            containerWidth = context.getResources().getDisplayMetrics().widthPixels / numColumns - dpToPx(6);
        }
        if (containerWidth > 0) {
            int containerHeight = containerWidth * 9 / 16;
            ViewGroup.LayoutParams params = holder.coverContainer.getLayoutParams();
            params.height = containerHeight;
            holder.coverContainer.setLayoutParams(params);
        }

        VideoCard item = list.get(position);
        if (item != null) {
            holder.title.setText(item.title);
            holder.view.setText(item.view != null ? item.view : "0");
            holder.danmaku.setText(item.danmaku > 0 ? String.valueOf(item.danmaku) : "0");
        }

        final ViewHolder fHolder = holder;
        holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

        if (item != null && item.cover != null && item.cover.length() > 0
                && !SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }
            final String finalUrl = coverUrl;
            final ImageView coverView = holder.cover;
            final int pos = position;

            coverView.setTag(pos);

            Bitmap cached = GlobalImageCache.getInstance().getAndAcquire(finalUrl);
            if (cached != null && !cached.isRecycled()) {
                coverView.setImageBitmap(cached);
                fHolder.currentCoverUrl = finalUrl;
                return convertView;
            }

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = downloadImage(finalUrl);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                        GlobalImageCache.getInstance().acquire(finalUrl);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Object tag = coverView.getTag();
                                if (tag != null && tag instanceof Integer && ((Integer) tag) == pos) {
                                    coverView.setImageBitmap(bitmap);
                                    fHolder.currentCoverUrl = finalUrl;
                                } else {
                                    GlobalImageCache.getInstance().release(finalUrl);
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
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;
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

            tempFile = new java.io.File(context.getCacheDir(), "rec_" + urlStr.hashCode() + ".tmp");
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

            byte[] imageData = new byte[(int) tempFile.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
            int offset = 0;
            while (offset < imageData.length) {
                int read = fis.read(imageData, offset, imageData.length - offset);
                if (read < 0) break;
                offset += read;
            }
            fis.close();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);

            int targetWidth = 160;
            int targetHeight = 90;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                targetWidth = (int)(targetWidth * 1.25f);
                targetHeight = (int)(targetHeight * 1.25f);
            }
            int minScale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD ? 2 : 4;
            int scale = 1;
            if (options.outWidth > targetWidth || options.outHeight > targetHeight) {
                int widthRatio = options.outWidth / targetWidth;
                int heightRatio = options.outHeight / targetHeight;
                scale = Math.max(widthRatio, heightRatio);
                if (scale < 1) scale = 1;
                if (scale > 8) scale = 8;
            }
            if (scale < minScale) scale = minScale;

            Bitmap bitmap = null;
            while (scale <= 16 && bitmap == null) {
                try {
                    System.gc();
                    options = new BitmapFactory.Options();
                    options.inSampleSize = scale;
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    options.inPurgeable = true;
                    options.inInputShareable = true;
                    bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
                } catch (OutOfMemoryError e) {
                    System.gc();
                    scale *= 2;
                }
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "下载失败: " + urlStr, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    public void updateData(List<VideoCard> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public void clearCache() {
        executor.shutdownNow();
        GlobalImageCache.getInstance().clear();
    }

    static class ViewHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView title;
        TextView view;
        TextView danmaku;
        String currentCoverUrl;
    }
}