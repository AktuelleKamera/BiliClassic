package tv.biliclassic.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 统一的图片加载器（封面/头像共用）。
 *
 * 修复“滚动时正在看的图像闪一下/被当成重新加载”的根因：
 * 原各适配器在 getView 每次绑定都把 ImageView 重置为占位图，且 applyCover 在滚动中
 * 把真实图延迟到滑动停止才贴上去，于是滑动过程中当前图先变占位图、停下才恢复。
 *
 * 本类改为：命中缓存的图【立即】应用（不受滚动延迟影响）；只有【未命中缓存】时才显示
 * 占位图并异步加载（滚动中新图按原逻辑延迟应用，避免整屏重绘）。位图复用
 * GlobalImageCache，不在网络层重复下载。
 *
 * 滚动标志、线程池、加载去重、延迟队列均为进程级共享（同一时刻通常只有一个列表在滚动）。
 */
public class ImageLoader {

    private static ExecutorService executor;
    private static final Map<String, Boolean> loadingMap = new HashMap<String, Boolean>();
    private static volatile boolean sScrolling = false;
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static final ArrayList<Runnable> sPending = new ArrayList<Runnable>();
    private static final Object sLock = new Object();

    // 进程级软引用本地缓存（进一步提升跨页面复用率；与 GlobalImageCache 互补）
    private static final Map<String, SoftReference<Bitmap>> sImageCache = new HashMap<String, SoftReference<Bitmap>>();

    private static void ensureExecutor() {
        if (executor == null || executor.isShutdown()) {
            int threadCount = SdkHelper.getImageLoadThreads();
            if (threadCount <= 1) {
                executor = Executors.newSingleThreadExecutor();
            } else {
                executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>());
            }
        }
    }

    /** 滚动状态变化时由 ListView 的 OnScrollListener 调用（全应用共享一个滚动标志） */
    public static void setScrolling(boolean scrolling) {
        sScrolling = scrolling;
        if (!scrolling) {
            flushPending();
        }
    }

    private static void flushPending() {
        synchronized (sLock) {
            if (sPending.isEmpty()) return;
            final ArrayList<Runnable> pending = new ArrayList<Runnable>(sPending);
            sPending.clear();
            final int[] idx = {0};
            final Runnable drain = new Runnable() {
                @Override
                public void run() {
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
                        sMain.postDelayed(this, 16);
                    }
                }
            };
            drain.run();
        }
    }

    // 命中缓存：立即应用，不受滚动延迟影响
    private static void applyImmediate(ImageView view, String url, Bitmap bitmap) {
        Object tag = view.getTag();
        if (tag == null || !tag.equals(url)) return;
        Drawable cur = view.getDrawable();
        if (cur instanceof BitmapDrawable && ((BitmapDrawable) cur).getBitmap() == bitmap) return;
        view.setImageBitmap(bitmap);
    }

    // 异步加载完成：滚动中延迟，停下统一补（带 tag 校验，避免复用错位）
    private static void applyDeferred(final ImageView view, final String url, final Bitmap bitmap) {
        if (sScrolling) {
            synchronized (sLock) {
                sPending.add(new Runnable() {
                    @Override
                    public void run() {
                        applyDeferred(view, url, bitmap);
                    }
                });
            }
            return;
        }
        Object tag = view.getTag();
        if (tag == null || !tag.equals(url)) return;
        Drawable cur = view.getDrawable();
        if (cur instanceof BitmapDrawable && ((BitmapDrawable) cur).getBitmap() == bitmap) return;
        view.setImageBitmap(bitmap);
    }

    /**
     * 绑定图片到 ImageView。
     *
     * @param view            目标 ImageView
     * @param url             图片地址（http/https 均可）
     * @param placeholderResId 占位图资源 id（0 表示不设置占位）
     * @param dpW/dpH         目标解码尺寸（dp），按显示尺寸解码以省内存
     */
    public static void bind(ImageView view, String url, int placeholderResId, int dpW, int dpH) {
        if (view == null) return;
        final Context ctx = view.getContext();
        if (url == null || url.length() == 0) {
            if (placeholderResId != 0) view.setImageResource(placeholderResId);
            return;
        }
        String coverUrl = url;
        if (coverUrl.startsWith("https://")) {
            coverUrl = "http://" + coverUrl.substring(8);
        }
        final String finalUrl = coverUrl;
        view.setTag(finalUrl);

        // 1) 全局缓存（跨页面共享）
        Bitmap gCached = GlobalImageCache.getInstance().get(finalUrl);
        if (gCached != null && !gCached.isRecycled()) {
            applyImmediate(view, finalUrl, gCached);
            return;
        }
        // 2) 本地软引用缓存
        SoftReference<Bitmap> soft = softCacheGet(finalUrl);
        if (soft != null) {
            Bitmap b = soft.get();
            if (b != null && !b.isRecycled()) {
                applyImmediate(view, finalUrl, b);
                return;
            } else {
                softCacheRemove(finalUrl);
            }
        }
        // 3) 未命中：仅此时显示占位图并异步加载（命中缓存不闪烁、不重新下载）
        final ImageView fView = view;
        final int fDpW = dpW;
        final int fDpH = dpH;
        if (placeholderResId != 0) view.setImageResource(placeholderResId);
        Boolean isLoading = loadingMapGet(finalUrl);
        if (isLoading == null || !isLoading) {
            loadingMapPut(finalUrl, true);
            ensureExecutor();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (ctx == null) {
                        loadingMapRemove(finalUrl);
                        return;
                    }
                    final Bitmap bitmap;
                    if (finalUrl.startsWith("content://")) {
                        bitmap = decodeContent(ctx, finalUrl, fDpW, fDpH);
                    } else {
                        bitmap = downloadImage(ctx, finalUrl, fDpW, fDpH);
                    }
                    loadingMapRemove(finalUrl);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                        softCachePut(finalUrl, new SoftReference<Bitmap>(bitmap));
                        sMain.post(new Runnable() {
                            @Override
                            public void run() {
                                applyDeferred(fView, finalUrl, bitmap);
                            }
                        });
                    }
                }
            });
        }
    }

    private static synchronized SoftReference<Bitmap> softCacheGet(String k) {
        return sImageCache.get(k);
    }

    private static synchronized void softCachePut(String k, SoftReference<Bitmap> v) {
        sImageCache.put(k, v);
    }

    private static synchronized void softCacheRemove(String k) {
        sImageCache.remove(k);
    }

    private static synchronized Boolean loadingMapGet(String k) {
        return loadingMap.get(k);
    }

    private static synchronized void loadingMapPut(String k, Boolean v) {
        loadingMap.put(k, v);
    }

    private static synchronized void loadingMapRemove(String k) {
        loadingMap.remove(k);
    }

    /**
     * 鍚屾鍥炬煍鍥剧墖浣嶅浘锛氬懡涓叏灞€缂撳瓨鐩存帴杩斿洖锛屽惁鍒欎笅杞斤紙鏃犲浘妯″紡杩斿洖 null锛夊苟鍐欏叆缂撳瓨銆
     * 蹇呴』鍦ㄥ悗鍙扮嚎绋嬭皟鐢ㄣ€俉/H 鍗曚綅涓?dp銆倅
     */
    public static Bitmap fetchBitmap(android.content.Context context, String url, int dpW, int dpH) {
        if (url == null || url.length() == 0) return null;
        Bitmap cached = GlobalImageCache.getInstance().get(url);
        if (cached != null && !cached.isRecycled()) return cached;
        Bitmap bitmap = downloadImage(context, url, dpW, dpH);
        if (bitmap != null && !bitmap.isRecycled()) {
            GlobalImageCache.getInstance().put(url, bitmap);
        }
        return bitmap;
    }

    private static Bitmap downloadImage(Context context, String urlStr, int dpW, int dpH) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;
        // 本地文件（离线封面等）：直接解码，不联网
        if (urlStr.startsWith("file://")) {
            String path = urlStr.substring(7);
            java.io.File localFile = new java.io.File(path);
            if (!localFile.exists()) return null;
            float density = context.getResources().getDisplayMetrics().density;
            int decodeW = (int) (dpW * density + 0.5f);
            int decodeH = (int) (dpH * density + 0.5f);
            int minScale = SdkHelper.getSdkInt() >= 9 ? 2 : 4;
            return GlobalImageCache.decodeFileSafely(localFile, decodeW, decodeH, minScale);
        }
        HttpURLConnection conn = null;
        java.io.File tempFile = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
            conn.connect();

            tempFile = new java.io.File(context.getCacheDir(), "img_" + urlStr.hashCode() + ".tmp");
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

            float density = context.getResources().getDisplayMetrics().density;
            int decodeW = (int) (dpW * density + 0.5f);
            int decodeH = (int) (dpH * density + 0.5f);
            int minScale = SdkHelper.getSdkInt() >= 9 ? 2 : 4;
            return GlobalImageCache.decodeFileSafely(tempFile, decodeW, decodeH, minScale);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    /** 重新按当前线程数配置创建线程池（用户更改图片加载线程数后调用） */
    public static synchronized void reloadExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        executor = null;
        ensureExecutor();
    }

    /** 释放全部本地缓存引用（不 recycle，避免正在绘制的位图崩溃）；可选在页面销毁时调用 */
    public static synchronized void clearCache() {
        synchronized (sLock) {
            for (Map.Entry<String, SoftReference<Bitmap>> e : sImageCache.entrySet()) {
                Bitmap bmp = e.getValue().get();
                if (bmp != null) {
                    Bitmap inCache = GlobalImageCache.getInstance().get(e.getKey());
                    if (inCache == bmp) GlobalImageCache.getInstance().remove(e.getKey());
                }
            }
            sImageCache.clear();
        }
        loadingMap.clear();
    }

    /**
     * 加载 content:// / file:// Uri 指向的本地文件。
     * Android 7+ 推荐用 FileProviderCompat 生成的 content://（替代受限的 file://）。
     */
    public static void bind(ImageView view, android.net.Uri uri, int placeholderResId, int dpW, int dpH) {
        bind(view, uri != null ? uri.toString() : null, placeholderResId, dpW, dpH);
    }

    /** 直接加载本地文件（应用内读取自身文件，Android 7+ 仍可用，无需 FileProvider） */
    public static void bind(ImageView view, java.io.File file, int placeholderResId, int dpW, int dpH) {
        bind(view, file != null ? "file://" + file.getAbsolutePath() : null, placeholderResId, dpW, dpH);
    }

    /** 通过 ContentResolver 解码 content:// Uri（Android 7+ 下替代 file:// 的合规方式） */
    private static Bitmap decodeContent(Context context, String urlStr, int dpW, int dpH) {
        if (context == null) return null;
        android.net.Uri uri = android.net.Uri.parse(urlStr);
        android.content.ContentResolver cr = context.getContentResolver();
        try {
            float density = context.getResources().getDisplayMetrics().density;
            int targetW = (int) (dpW * density + 0.5f);
            int targetH = (int) (dpH * density + 0.5f);
            int minScale = SdkHelper.getSdkInt() >= 9 ? 2 : 4;

            android.os.ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
            if (pfd == null) return null;
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, bounds);
            pfd.close();
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int scale = 1;
            if (bounds.outWidth > targetW || bounds.outHeight > targetH) {
                int widthRatio = bounds.outWidth / targetW;
                int heightRatio = bounds.outHeight / targetH;
                scale = Math.max(widthRatio, heightRatio);
                if (scale < 1) scale = 1;
                if (scale > 8) scale = 8;
                if (scale < minScale) scale = minScale;
            }

            android.graphics.Bitmap bitmap = null;
            int curScale = scale;
            while (bitmap == null && curScale <= 16) {
                try {
                    android.os.ParcelFileDescriptor pfd2 = cr.openFileDescriptor(uri, "r");
                    if (pfd2 == null) return null;
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    opts.inSampleSize = curScale;
                    opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
                    bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd2.getFileDescriptor(), null, opts);
                    pfd2.close();
                } catch (OutOfMemoryError e) {
                    curScale *= 2;
                    if (curScale > 16) break;
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
