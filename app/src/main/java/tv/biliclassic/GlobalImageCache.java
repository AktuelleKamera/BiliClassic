package tv.biliclassic.util;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

public class GlobalImageCache {

    private static GlobalImageCache instance;
    private LruCache<String, Bitmap> cache;

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
        cache.put(key, bitmap);
    }

    public synchronized void clear() {
        cache.evictAll();
    }

    public synchronized void remove(String key) {
        if (key != null) {
            cache.remove(key);
        }
    }
}
