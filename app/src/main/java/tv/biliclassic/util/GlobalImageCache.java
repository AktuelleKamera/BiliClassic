package tv.biliclassic.util;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import java.util.HashMap;
import java.util.Map;

public class GlobalImageCache {

    private static GlobalImageCache instance;
    private LruCache<String, Bitmap> cache;
    private Map<String, Integer> refCounts = new HashMap<String, Integer>();
    private Map<String, Bitmap> pendingRecycle = new HashMap<String, Bitmap>();

    private GlobalImageCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // Android 2.x 位图存放在独立的外部堆（约 13MB），且不支持 largeHeap。
        // 按 Java 堆 1/8 设置缓存会把外部堆撑爆：32MB 堆以下用更保守的 1/16，
        // 只有 32MB 堆以上的设备才用 1/8 的大缓存。
        int cacheSize;
        if (maxMemory < 32768) {
            cacheSize = maxMemory / 16;
        } else {
            cacheSize = maxMemory / 8;
        }
        if (cacheSize < 512) {
            cacheSize = 512;
        }
        cache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (oldValue != null && !oldValue.isRecycled()) {
                    synchronized (GlobalImageCache.this) {
                        Integer ref = refCounts.get(key);
                        if (ref != null && ref > 0) {
                            // 有引用（getAndAcquire/acquire 过）：交给 release 归零时回收
                            pendingRecycle.put(key, oldValue);
                        }
                        // 无引用：不主动 recycle，避免位图仍被 ImageView 绘制时崩溃。
                        // Android 2.x 上这些位图靠 inPurgeable + GC 由系统回收。
                    }
                }
            }
        };
    }

    public static synchronized GlobalImageCache getInstance() {
        if (instance == null) {
            instance = new GlobalImageCache();
        }
        return instance;
    }

    public synchronized Bitmap get(String key) {
        if (key == null) return null;
        Bitmap bmp = cache.get(key);
        if (bmp != null && !bmp.isRecycled()) {
            return bmp;
        }
        return null;
    }

    public synchronized void put(String key, Bitmap bitmap) {
        if (key == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        pendingRecycle.remove(key);
        cache.put(key, bitmap);
    }

    public synchronized Bitmap getAndAcquire(String key) {
        if (key == null) return null;
        Bitmap bmp = cache.get(key);
        if (bmp != null && !bmp.isRecycled()) {
            Integer count = refCounts.get(key);
            refCounts.put(key, (count == null ? 0 : count) + 1);
            return bmp;
        }
        return null;
    }

    public synchronized void acquire(String key) {
        if (key == null) return;
        Integer count = refCounts.get(key);
        refCounts.put(key, (count == null ? 0 : count) + 1);
    }

    public synchronized void release(String key) {
        if (key == null) return;
        Integer count = refCounts.get(key);
        if (count != null && count > 0) {
            if (count == 1) {
                refCounts.remove(key);
                Bitmap pending = pendingRecycle.remove(key);
                if (pending != null && !pending.isRecycled()) {
                    pending.recycle();
                }
            } else {
                refCounts.put(key, count - 1);
            }
        }
    }

    public synchronized void clear() {
        cache.evictAll();
        for (Bitmap bmp : pendingRecycle.values()) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        pendingRecycle.clear();
        refCounts.clear();
    }

    /**
     * 释放引用但不 recycle（避免 ImageView 正在绘制时崩溃），仅触发自然回收。
     * Android 2.x 位图为 inPurgeable，像素由系统 GC 自动回收；
     * 不显式 System.gc()——2.x 的 GC 是 stop-the-world，显式调用会冻结主线程数百毫秒。
     */
    public synchronized void releaseMemory() {
        cache.evictAll();
        refCounts.clear();
        pendingRecycle.clear();
    }

    public synchronized void remove(String key) {
        if (key != null) {
            cache.remove(key);
            Bitmap pending = pendingRecycle.remove(key);
            if (pending != null && !pending.isRecycled()) {
                pending.recycle();
            }
            refCounts.remove(key);
        }
    }

    /**
     * 内存不足时快速释放全部缓存引用（不 recycle，避免正在绘制的位图崩溃）。
     * 清空缓存引用即可；像素由 inPurgeable + 系统 GC 回收，不显式 System.gc()（见 releaseMemory 注释）。
     */
    public synchronized void freeAllUnreferenced() {
        cache.evictAll();
        pendingRecycle.clear();
        refCounts.clear();
    }

    /**
     * 释放缓存引用但不 recycle（避免回收仍被 ImageView 绘制的位图导致崩溃）。
     * 与 releaseMemory 等价，供 inflate 布局前腾出引用空间使用。
     */
    public synchronized void forceClear() {
        cache.evictAll();
        pendingRecycle.clear();
        refCounts.clear();
    }

    /** 是否处于内存紧张状态（剩余可用堆很小） */
    public static boolean isMemoryLow() {
        long maxMem = Runtime.getRuntime().maxMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long used = totalMem - freeMem;
        long freeHeap = maxMem - used;
        return freeHeap < maxMem / 5;
    }

    /**
     * 安全解码本地图片文件为位图。
     * 与 decodeByteArray 不同，这里直接流式解码文件，避免先读整张图片到内存造成双份内存峰值。
     * 内存不足时会先清空全局缓存再以更大采样率重试。
     *
     * @param file         已下载到本地的图片文件
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @param minScale     最小采样率（Android 2.x 上更保守）
     */
    public static android.graphics.Bitmap decodeFileSafely(java.io.File file, int targetWidth, int targetHeight, int minScale) {
        if (file == null || !file.exists() || file.length() == 0) return null;

        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try {
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        } catch (Throwable t) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int scale = 1;
        if (bounds.outWidth > targetWidth || bounds.outHeight > targetHeight) {
            int widthRatio = bounds.outWidth / targetWidth;
            int heightRatio = bounds.outHeight / targetHeight;
            scale = Math.max(widthRatio, heightRatio);
            if (scale < 1) scale = 1;
            if (scale > 8) scale = 8;
            // 仅当确实在降采样时才强制最小采样率；
            // 源图已 ≤ 目标尺寸时保持 scale=1，否则小图再按 minScale 降采样会糊
            if (scale < minScale) scale = minScale;
        }

        android.graphics.Bitmap bitmap = null;
        while (bitmap == null && scale <= 16) {
            try {
                if (isMemoryLow()) {
                    getInstance().freeAllUnreferenced();
                }
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inSampleSize = scale;
                opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
                // 不用 inPurgeable/inInputShareable：2.x 内存吃紧时（如启动详情页）系统会 purge
                // 可回收位图像素，过渡动画重绘时封面闪一下/变空白（新番页点击时"图片销毁"的根因）。
                // 封面现已按 1:1 小尺寸解码，常驻像素的内存开销可接受。
                bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            } catch (OutOfMemoryError e) {
                getInstance().freeAllUnreferenced();
                scale *= 2;
            }
        }
        return bitmap;
    }
}
