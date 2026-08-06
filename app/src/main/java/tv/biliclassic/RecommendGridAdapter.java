package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

/**
 * 推荐/分区列表的行式适配器（配合 ListView 使用，实现虚拟化）。
 * 每行 = numColumns 个视频卡片；ListView 只构建可见行，滚动回收。
 */
public class RecommendGridAdapter extends BaseAdapter {

    private static final String TAG = "RecommendAdapter";
    private Context context;
    private List<VideoCard> list;
    private int numColumns = 2;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 滚动中暂缓应用新图，避免每张图到达都触发整屏软件重绘；
    // 仅在主线程访问（mainHandler.post 与 setScrolling 都在主线程）
    private volatile boolean mScrolling = false;
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

    // Android 2.x 上 setImageResource 每次可能重新解码资源图，这里缓存默认 Drawable 实例复用
    private static Drawable sDefaultCoverDrawable;

    public RecommendGridAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
        initExecutor();
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 24576;
    }

    private int getConfiguredThreadCount() {
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

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
        notifyDataSetChanged();
    }

    public int getNumColumns() {
        return numColumns;
    }

    @Override
    public int getCount() {
        if (list == null || list.size() == 0) return 0;
        return (list.size() + numColumns - 1) / numColumns;
    }

    @Override
    public Object getItem(int position) {
        int start = position * numColumns;
        if (list != null && start < list.size()) {
            return list.get(start);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        if (convertView instanceof LinearLayout) {
            row = (LinearLayout) convertView;
        } else {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        }

        // 列数变化时重建行内 cell
        if (row.getChildCount() != numColumns) {
            row.removeAllViews();
            for (int i = 0; i < numColumns; i++) {
                View cell = LayoutInflater.from(context).inflate(R.layout.item_recommend, row, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i < numColumns - 1) {
                    lp.rightMargin = dpToPx(8);
                }
                cell.setLayoutParams(lp);
                row.addView(cell);
            }
        }

        int cellWidth = computeCellWidth();
        int start = position * numColumns;
        for (int i = 0; i < numColumns; i++) {
            View cell = row.getChildAt(i);
            int index = start + i;
            if (index < list.size()) {
                cell.setVisibility(View.VISIBLE);
                bindCell(cell, list.get(index), cellWidth);
            } else {
                cell.setVisibility(View.INVISIBLE);
            }
        }
        return row;
    }

    private int computeCellWidth() {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int padding = dpToPx(4) * 2;
        int spacing = dpToPx(8);
        return (screenWidth - padding - (numColumns - 1) * spacing) / numColumns;
    }

    private void bindCell(final View cell, final VideoCard item, int cellWidth) {
        CellHolder h = (CellHolder) cell.getTag();
        if (h == null) {
            h = new CellHolder();
            h.coverContainer = (FrameLayout) cell.findViewById(R.id.cover_container);
            h.cover = (ImageView) cell.findViewById(R.id.cover);
            h.title = (TextView) cell.findViewById(R.id.title);
            h.view = (TextView) cell.findViewById(R.id.view);
            h.danmaku = (TextView) cell.findViewById(R.id.danmaku);
            cell.setTag(h);
        }

        int coverHeight = cellWidth * 9 / 16;
        if (coverHeight > 0) {
            ViewGroup.LayoutParams p = h.coverContainer.getLayoutParams();
            if (p.height != coverHeight) {
                p.height = coverHeight;
                h.coverContainer.setLayoutParams(p);
            }
        }

        h.title.setText(item.title != null ? item.title : "");
        h.view.setText(item.view != null ? item.view : "0");
        h.danmaku.setText(item.danmaku > 0 ? String.valueOf(item.danmaku) : "0");

        if (sDefaultCoverDrawable == null) {
            try {
                sDefaultCoverDrawable = context.getResources().getDrawable(R.drawable.bili_default_image_tv_with_bg);
            } catch (Throwable t) {
                sDefaultCoverDrawable = null;
            }
        }
        if (sDefaultCoverDrawable != null && h.cover.getDrawable() != sDefaultCoverDrawable) {
            h.cover.setImageDrawable(sDefaultCoverDrawable);
        }

        // 点击：直接绑定当前视频
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (item == null) return;
                Intent intent = new Intent(context, VideoDetailActivity.class);
                if (item.aid != 0) {
                    intent.putExtra("aid", item.aid);
                } else if (item.bvid != null && item.bvid.length() > 0) {
                    intent.putExtra("bvid", item.bvid);
                } else {
                    Toast.makeText(context, "无法获取视频信息", Toast.LENGTH_SHORT).show();
                    return;
                }
                context.startActivity(intent);
            }
        });

        // 释放上一个封面引用
        if (h.currentCoverUrl != null) {
            GlobalImageCache.getInstance().release(h.currentCoverUrl);
            h.currentCoverUrl = null;
        }

        if (item.cover != null && item.cover.length() > 0
                && !SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }
            final String finalUrl = coverUrl;
            final ImageView coverView = h.cover;
            coverView.setTag(finalUrl);

            Bitmap cached = GlobalImageCache.getInstance().getAndAcquire(finalUrl);
            if (cached != null && !cached.isRecycled()) {
                coverView.setImageBitmap(cached);
                h.currentCoverUrl = finalUrl;
                return;
            }

            final int targetW = cellWidth;
            final int targetH = coverHeight;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = downloadImage(finalUrl, targetW, targetH);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                        GlobalImageCache.getInstance().acquire(finalUrl);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mScrolling) {
                                    // 滚动中不立即应用：每张图到达都会触发整屏软件重绘
                                    pendingBitmapSets.add(this);
                                    return;
                                }
                                applyBitmap(cell, coverView, finalUrl, bitmap);
                            }
                        });
                    }
                }
            });
        }
    }

    private Bitmap downloadImage(String urlStr, int targetWidth, int targetHeight) {
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

            // 按实际显示尺寸解码：1:1 绘制无需软件缩放滤镜（更省且更清晰）
            int minScale = tv.biliclassic.util.SdkHelper.getSdkInt() >= 9 ? 2 : 4;
            return GlobalImageCache.decodeFileSafely(tempFile, targetWidth, targetHeight, minScale);
        } catch (Exception e) {
            Log.e(TAG, "下载失败: " + urlStr, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
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
        java.util.ArrayList<Runnable> pending = new java.util.ArrayList<Runnable>(pendingBitmapSets);
        pendingBitmapSets.clear();
        for (int i = 0; i < pending.size(); i++) {
            try {
                pending.get(i).run();
            } catch (Throwable t) {
            }
        }
    }

    private void applyBitmap(View cell, ImageView coverView, String finalUrl, Bitmap bitmap) {
        Object tag = coverView.getTag();
        if (tag != null && tag.equals(finalUrl)) {
            coverView.setImageBitmap(bitmap);
            CellHolder hh = (CellHolder) cell.getTag();
            if (hh != null) {
                hh.currentCoverUrl = finalUrl;
            }
        } else {
            GlobalImageCache.getInstance().release(finalUrl);
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
        pendingBitmapSets.clear();
        executor.shutdownNow();
        GlobalImageCache.getInstance().clear();
    }

    static class CellHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView title;
        TextView view;
        TextView danmaku;
        String currentCoverUrl;
    }
}
