package tv.biliclassic.util;

import android.graphics.Bitmap;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局图片缓存管理器（单例）
 * 所有 Activity 共享，避免重复下载和回收冲突
 */
public class GlobalImageCache {

    private static GlobalImageCache instance;
    private Map<String, SoftReference<Bitmap>> cache;

    private GlobalImageCache() {
        cache = new HashMap<String, SoftReference<Bitmap>>();
    }

    public static synchronized GlobalImageCache getInstance() {
        if (instance == null) {
            instance = new GlobalImageCache();
        }
        return instance;
    }

    public synchronized Bitmap get(String key) {
        SoftReference<Bitmap> ref = cache.get(key);
        if (ref != null) {
            Bitmap bmp = ref.get();
            if (bmp != null && !bmp.isRecycled()) {
                return bmp;
            } else {
                cache.remove(key);
            }
        }
        return null;
    }

    public synchronized void put(String key, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        // 限制缓存大小，防止 OOM
        if (cache.size() > 40) {
            String oldestKey = cache.keySet().iterator().next();
            SoftReference<Bitmap> oldRef = cache.remove(oldestKey);
            if (oldRef != null) {
                Bitmap oldBmp = oldRef.get();
                if (oldBmp != null && !oldBmp.isRecycled()) {
                    oldBmp.recycle();
                }
            }
        }
        cache.put(key, new SoftReference<Bitmap>(bitmap));
    }

    public synchronized void clear() {
        // 不手动回收 Bitmap，让 SoftReference 自动处理
        cache.clear();
    }

    public synchronized void remove(String key) {
        cache.remove(key);
    }
}