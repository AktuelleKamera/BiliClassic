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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.SharedPreferencesUtil;

public class FavoriteVideoAdapter extends BaseAdapter {

    private Context context;
    private List<VideoCard> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private boolean isLowMemory = false;
    private volatile boolean mScrolling = false;
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

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
                                        if (mScrolling) {
                                            // 滚动中暂缓应用，避免每张图到达都整屏软件重绘
                                            pendingBitmapSets.add(this);
                                            return;
                                        }
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

            tempFile = new java.io.File(context.getCacheDir(), "fav_" + urlStr.hashCode() + ".tmp");
            InputStream is = conn.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            is.close();
            fos.close();

            if (!tempFile.exists() || tempFile.length() == 0) return null;

            // 按实际显示尺寸解码（封面 96x61dp），1:1 绘制无需软件缩放滤镜
            float density = context.getResources().getDisplayMetrics().density;
            return GlobalImageCache.decodeFileSafely(tempFile,
                    (int) (96 * density + 0.5f), (int) (61 * density + 0.5f), 2);
        } catch (OutOfMemoryError e) {
            // 不显式 System.gc()
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception e) {}
            }
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete(); } catch (Exception e) {}
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
        pendingBitmapSets.clear();
        loadingMap.clear();
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView author;
        TextView play;
    }
}