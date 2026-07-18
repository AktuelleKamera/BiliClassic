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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

public class HistoryAdapter extends BaseAdapter {

    private Context context;
    private List<VideoCard> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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

            Bitmap cachedBitmap = GlobalImageCache.getInstance().get(finalCoverUrl);
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                coverView.setImageBitmap(cachedBitmap);
            } else {
                Boolean isLoading = loadingMap.get(currentPos);
                if (isLoading == null || !isLoading) {
                    loadingMap.put(currentPos, true);
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            final Bitmap bitmap = downloadImage(finalCoverUrl);
                            loadingMap.remove(currentPos);

                            if (bitmap != null && !bitmap.isRecycled()) {
                                GlobalImageCache.getInstance().put(finalCoverUrl, bitmap);
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
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;
        HttpURLConnection conn = null;
        try {
            if (urlStr != null && urlStr.startsWith("https://")) {
                urlStr = "http://" + urlStr.substring(8);
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.connect();

            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();
            byte[] imageData = baos.toByteArray();

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageData, 0, imageData.length, opts);

            int sampleSize = 1;
            int targetSize = 200;

            while (opts.outWidth / sampleSize > targetSize
                    || opts.outHeight / sampleSize > targetSize) {
                sampleSize *= 2;
                if (sampleSize > 16) break;
            }

            Bitmap bitmap = null;
            while (sampleSize <= 16 && bitmap == null) {
                try {
                    opts = new BitmapFactory.Options();
                    opts.inSampleSize = sampleSize;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length, opts);
                } catch (OutOfMemoryError e) {
                    sampleSize *= 2;
                }
            }
            imageData = null;
            return bitmap;

        } catch (OutOfMemoryError e) {
            System.gc();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception e) {}
            }
        }
    }

    public void updateData(List<VideoCard> newList) {
        this.list = newList;
        loadingMap.clear();
        notifyDataSetChanged();
    }

    public void clearCache() {
        loadingMap.clear();
    }

    static class ViewHolder {
        TextView title;
        TextView upName;
        TextView progress;
        ImageView cover;
    }
}