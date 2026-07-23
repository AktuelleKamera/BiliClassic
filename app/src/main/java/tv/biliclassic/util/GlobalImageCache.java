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
        int cacheSize = maxMemory / 8;
        if (cacheSize < 1024) {
            cacheSize = 1024;
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
                            pendingRecycle.put(key, oldValue);
                        }
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
        System.gc();
    }

    /** 释放引用但不 recycle（避免 ImageView 正在绘制时崩溃），仅触发 GC */
    public synchronized void releaseMemory() {
        cache.evictAll();
        refCounts.clear();
        pendingRecycle.clear();
        System.gc();
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
}
