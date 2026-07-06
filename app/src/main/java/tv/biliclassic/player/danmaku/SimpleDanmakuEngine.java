package tv.biliclassic.player.danmaku;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class SimpleDanmakuEngine extends View {

    private static final String TAG = "BT-5";
    private static final int ROWS_R2L = 8;
    private static final int RENDER_FPS = 20;
    private static final int FRAME_MS = 1000 / RENDER_FPS;

    private List<DanmakuItem> mItems;
    private int mItemIndex;
    private boolean mLoaded;
    private boolean mVisible = true;
    private boolean mPaused;

    private long mTimeOrigin;
    private long mTimePaused;
    private long mSeekOffset;

    private Paint mPaint;
    private Paint mBitmapPaint;
    private float mTextSize;
    private float mSpeed = 1.0f;
    private float mOpacity = 0.6f; // 0=全透明, 1=不透明
    private int mMaxOnScreen = 50;
    private boolean mBlockTop;
    private boolean mBlockScroll;
    private boolean mBlockBottom;
    private boolean mBlockColorful;

    private float[] mRowBusyUntil = new float[ROWS_R2L + 4 + 8]; // scroll 8 + top 4 + bottom 8

    private Thread mRenderThread;
    private volatile boolean mRunning;
    private volatile boolean mStopped;
    private int mFrameCount;
    private long mVideoPosition; // 由外部同步的视频播放位置

    public interface VideoTimeProvider {
        long getCurrentPosition();
    }
    private VideoTimeProvider mTimeProvider;

    private Bitmap mOffscreen;
    private Canvas mOffscreenCanvas;

    // bitmap 缓存：同文案复用，限制总内存
    private Map<String, WeakReference<Bitmap>> mBitmapPool = new HashMap<String, WeakReference<Bitmap>>();
    private int mBitmapMemory;
    private static final int MAX_BITMAP_MEMORY = 2 * 1024 * 1024;
    private final Object mPoolLock = new Object();

    private static class DanmakuItem {
        float time;
        int type;
        int color;
        String text;
        float row = -1;
        boolean placed;
        boolean hasBitmap;
        Bitmap bitmap; // 直接引用，避免 HashMap 查找
    }

    public SimpleDanmakuEngine(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        mTextSize = 14f * density;
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBitmapPaint.setFilterBitmap(true);
    }

    public void init(ViewGroup container) {
        Log.e(TAG, "init()");
        container.addView(this,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private boolean mDataReady; // 数据已加载，等待 provider 后启动

    public void setDanmakuData(File xmlFile) {
        try {
            FileInputStream fis = new FileInputStream(xmlFile);
            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
            mItems = parseXml(reader);
            reader.close();
            fis.close();
            Collections.sort(mItems, new Comparator<DanmakuItem>() {
                public int compare(DanmakuItem a, DanmakuItem b) {
                    return Float.compare(a.time, b.time);
                }
            });
            mDataReady = true;
            Log.e(TAG, "parsed " + mItems.size() + " items, dataReady=" + mDataReady
                    + " providerSet=" + (mTimeProvider != null));
            tryStartIfReady();
        } catch (Exception e) {
            Log.e(TAG, "parse error: " + e.getMessage());
            mItems = new ArrayList<DanmakuItem>();
        }
    }

    public void start() {
        if (mItems == null || mItems.isEmpty() || mLoaded) return;
        mLoaded = true;
        mVisible = true;
        mRunning = true;
        mStopped = false;
        mPaused = false;
        mItemIndex = 0;
        mFrameCount = 0;
        mSeekOffset = 0;
        mVideoPosition = 0;
        // 初始化时拿到首帧位置，跳过已错过的弹幕
        if (mTimeProvider != null) {
            mVideoPosition = Math.max(0, mTimeProvider.getCurrentPosition());
        }
        float startT = mVideoPosition / 1000f;
        while (mItemIndex < mItems.size() && mItems.get(mItemIndex).time < startT) {
            mItemIndex++;
        }
        resetRows();
        mTimeOrigin = System.currentTimeMillis();

        mRenderThread = new Thread(new Runnable() {
            public void run() {
                Log.e(TAG, "render thread started");
                while (!mStopped) {
                    if (mRunning && mVisible) {
                        postInvalidate();
                        if (!mPaused) {
                            prepareBitmaps();
                        }
                    }
                    try { Thread.sleep(FRAME_MS); } catch (InterruptedException e) { break; }
                }
                Log.e(TAG, "render thread stopped, frames=" + mFrameCount);
            }
        }, "DanmakuRender");
        mRenderThread.setPriority(Thread.MIN_PRIORITY);
        mRenderThread.start();
    }

    public void pauseDanmaku() {
        mPaused = true;
        mTimePaused = System.currentTimeMillis();
        preloadBitmaps(mVideoPosition);
    }

    private void preloadBitmaps(long positionMs) {
        if (mItems == null || mItems.isEmpty()) return;
        final float t = positionMs / 1000f;
        int start = mItemIndex;
        while (start > 0 && start <= mItems.size() && mItems.get(start - 1).time > t - 1f) start--;
        final int startIdx = start;
        new Thread(new Runnable() {
            public void run() {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                int end = Math.min(startIdx + 200, mItems.size());
                float limit = t + 15f;
                for (int i = startIdx; i < end; i++) {
                    if (i >= mItems.size()) break;
                    DanmakuItem item = mItems.get(i);
                    if (item.time > limit) break;
                    ensureBitmap(item);
                }
            }
        }).start();
    }

    public void resumeDanmaku() {
        if (!mPaused) return;
        mTimeOrigin += System.currentTimeMillis() - mTimePaused;
        mPaused = false;
        mRunning = true;
    }

    public void seekTo(long positionMs) {
        mSeekOffset = -positionMs;
        if (mLoaded) {
            mTimeOrigin = System.currentTimeMillis() + mSeekOffset;
            mItemIndex = 0;
            resetRows();
            clearBitmaps();
            postInvalidate();
        }
    }

    public void toggleVisibility() {
        mVisible = !mVisible;
        postInvalidate();
    }

    public boolean isEnabled() { return mVisible; }
    public boolean isLoaded() { return mLoaded; }

    public void setScaleTextSize(float scale) {
        float density = getContext().getResources().getDisplayMetrics().density;
        mTextSize = 14f * density * scale;
        clearBitmaps();
    }

    public void setScrollSpeedFactor(float factor) {
        mSpeed = factor;
    }

    public void setDanmakuOpacity(float opacity) {
        mOpacity = opacity;
    }

    public void setMaximumVisibleSizeInScreen(int max) {
        mMaxOnScreen = max;
    }

    public void setTimeProvider(VideoTimeProvider provider) {
        mTimeProvider = provider;
        Log.e(TAG, "timeProvider set, dataReady=" + mDataReady);
        tryStartIfReady();
    }

    private void tryStartIfReady() {
        if (mDataReady && mTimeProvider != null && !mLoaded) {
            warmUp();
            start();
        }
    }

    private void warmUp() {
        if (mItems == null || mItems.isEmpty() || mTimeProvider == null) return;
        mVideoPosition = Math.max(0, mTimeProvider.getCurrentPosition());
        float t = mVideoPosition / 1000f;
        // 预创建当前位置附近前 100 条弹幕的 Bitmap
        int wi = mItemIndex;
        while (wi > 0 && wi <= mItems.size() && mItems.get(wi - 1).time > t - 1f) wi--;
        int end = Math.min(wi + 100, mItems.size());
        for (int i = wi; i < end; i++) {
            if (i >= mItems.size()) break;
            DanmakuItem item = mItems.get(i);
            if (item.time > t + 10f) break;
            if (item.bitmap == null) ensureBitmap(item);
        }
    }

    public void updateVideoPosition(long positionMs) {
        mVideoPosition = positionMs;
    }

    public void setBlockTop(boolean block) { mBlockTop = block; }
    public void setBlockScroll(boolean block) { mBlockScroll = block; }
    public void setBlockBottom(boolean block) { mBlockBottom = block; }
    public void setBlockColorful(boolean block) { mBlockColorful = block; }

    public void releaseDanmaku() {
        mStopped = true;
        mRunning = false;
        if (mRenderThread != null) {
            mRenderThread.interrupt();
            try { mRenderThread.join(500); } catch (Exception e) {}
            mRenderThread = null;
        }
        clearBitmaps();
        if (mItems != null) mItems.clear();
        if (mOffscreen != null) { mOffscreen.recycle(); mOffscreen = null; }
        mLoaded = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mVisible || mItems == null || mItems.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 离屏缓冲，消除闪烁
        if (mOffscreen == null || mOffscreen.getWidth() != w || mOffscreen.getHeight() != h) {
            if (mOffscreen != null) mOffscreen.recycle();
            mOffscreen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mOffscreenCanvas = new Canvas(mOffscreen);
        }

        if (mTimeProvider != null) {
            mVideoPosition = mTimeProvider.getCurrentPosition();
        }
        float t = mVideoPosition / 1000f;
        float screenSeconds = 6f / mSpeed + 1f;

        while (mItemIndex < mItems.size()
                && mItems.get(mItemIndex).time < t - screenSeconds - 0.5f) {
            mItemIndex++;
        }

        mOffscreenCanvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);

        int rowH = (int) (mTextSize * 1.6f);
        int rowBaseY = h / 12;
        int count = 0;
        mBitmapPaint.setAlpha((int) (255 * mOpacity));

        for (int i = mItemIndex; i < mItems.size(); i++) {
            DanmakuItem item = mItems.get(i);
            if (item.time > t + 0.3f) break;
            if (count >= mMaxOnScreen) break;
            count++;
            if (mBlockScroll && item.type == 1) continue;
            if (mBlockTop && item.type == 5) continue;
            if (mBlockBottom && item.type == 4) continue;
            if (mBlockColorful && (item.color & 0xFFFFFF) != 0xFFFFFF) {
                item.color = Color.WHITE;
            }

            float dt = t - item.time;

            if (item.type == 4) {
                if (!item.placed) { item.placed = true; item.row = findTopRow(ROWS_R2L + 4, 8, t); }
                if (item.row < 0 || dt > 5f) continue;
                drawDanmaku(mOffscreenCanvas, item, w / 2f, h - rowBaseY - item.row * rowH);
            } else if (item.type == 5) {
                if (!item.placed) { item.placed = true; item.row = findTopRow(ROWS_R2L, 4, t); }
                if (item.row < 0 || dt > 5f) continue;
                drawDanmaku(mOffscreenCanvas, item, w / 2f, rowBaseY + item.row * rowH + mTextSize);
            } else {
                if (!item.placed) { item.placed = true; item.row = findScrollRow(t); }
                if (item.row < 0) continue;
                float speedPx = (w + 200) / 6f * mSpeed;
                float x = w - dt * speedPx;
                if (x < -600) continue;
                drawDanmaku(mOffscreenCanvas, item, x, rowBaseY + item.row * rowH + mTextSize);
            }
        }

        canvas.drawBitmap(mOffscreen, 0, 0, null);

        mFrameCount++;
        if (mFrameCount % (RENDER_FPS * 5) == 0) {
            Log.e(TAG, "frame " + mFrameCount + " pos=" + mVideoPosition
                    + "ms t=" + String.format("%.1f", t)
                    + " vis=" + count + " idx=" + mItemIndex);
        }
    }

    private void drawDanmaku(Canvas canvas, DanmakuItem item, float x, float y) {
        Bitmap bmp = getTextBitmap(item);
        if (bmp == null) return;
        mBitmapPaint.setAlpha((int) (255 * mOpacity));
        canvas.drawBitmap(bmp, x - bmp.getWidth() / 2f,
                y - bmp.getHeight(), mBitmapPaint);
    }

    private void prepareBitmaps() {
        if (mItems == null || mItems.isEmpty()) return;
        float t = mVideoPosition / 1000f;
        int start = mItemIndex;
        while (start > 0 && start <= mItems.size() && mItems.get(start - 1).time > t - 1f) start--;
        int end = Math.min(start + 80, mItems.size());
        float limit = t + 8f;
        int prepared = 0;
        for (int i = start; i < end && prepared < 20; i++) {
            if (i >= mItems.size()) break;
            DanmakuItem item = mItems.get(i);
            if (item.time > limit) break;
            if (item.bitmap == null) {
                ensureBitmap(item);
                prepared++;
            }
        }
    }

    private Bitmap getTextBitmap(DanmakuItem item) {
        if (item.bitmap != null) return item.bitmap;
        synchronized (mPoolLock) {
            WeakReference<Bitmap> ref = mBitmapPool.get(item.text);
            if (ref != null) {
                Bitmap bmp = ref.get();
                if (bmp != null) {
                    item.bitmap = bmp;
                    return bmp;
                }
            }
        }
        return null;
    }

    private void ensureBitmap(DanmakuItem item) {
        if (item.bitmap != null) return;
        synchronized (mPoolLock) {
            WeakReference<Bitmap> ref = mBitmapPool.get(item.text);
            if (ref != null && ref.get() != null) {
                item.bitmap = ref.get();
                return;
            }
        }
        if (mBitmapMemory >= MAX_BITMAP_MEMORY) {
            evictOldBitmaps();
        }
        synchronized (mPoolLock) {
            if (mBitmapMemory >= MAX_BITMAP_MEMORY) return;
        }
        Bitmap bmp = createTextBitmap(item);
        if (bmp != null) {
            item.bitmap = bmp;
            synchronized (mPoolLock) {
                mBitmapPool.put(item.text, new WeakReference<Bitmap>(bmp));
                mBitmapMemory += bmp.getRowBytes() * bmp.getHeight();
            }
        }
    }

    private void evictOldBitmaps() {
        List<String> toRemove = new ArrayList<String>();
        synchronized (mPoolLock) {
            for (Map.Entry<String, WeakReference<Bitmap>> e : mBitmapPool.entrySet()) {
                Bitmap bmp = e.getValue().get();
                if (bmp == null) {
                    toRemove.add(e.getKey());
                }
            }
            for (String key : toRemove) {
                mBitmapPool.remove(key);
            }
            if (mBitmapMemory >= MAX_BITMAP_MEMORY) {
                Log.e(TAG, "evicting all bitmaps, memory=" + mBitmapMemory);
                mBitmapPool.clear();
                mBitmapMemory = 0;
            }
        }
        if (mBitmapMemory == 0 && mItems != null) {
            for (DanmakuItem item : mItems) {
                item.bitmap = null;
                item.hasBitmap = false;
            }
        }
    }

    private Bitmap createTextBitmap(DanmakuItem item) {
        try {
            mPaint.setTextSize(mTextSize);
            float w = mPaint.measureText(item.text) + 12;
            float h = mTextSize * 1.4f + 6;
            int iw = (int) Math.ceil(w);
            int ih = (int) Math.ceil(h);
            if (iw <= 0 || ih <= 0) return null;

            Bitmap bmp;
            try {
                bmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_4444);
            } catch (Exception e) {
                bmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
            }
            Canvas bc = new Canvas(bmp);
            // shadow
            mPaint.setColor(0x80000000);
            bc.drawText(item.text, 6, mTextSize + 3, mPaint);
            bc.drawText(item.text, 8, mTextSize + 3, mPaint);
            bc.drawText(item.text, 7, mTextSize + 2, mPaint);
            bc.drawText(item.text, 7, mTextSize + 4, mPaint);
            // main
            mPaint.setColor(item.color);
            bc.drawText(item.text, 7, mTextSize + 3, mPaint);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private float findScrollRow(float t) {
        for (int i = 0; i < ROWS_R2L; i++) {
            if (mRowBusyUntil[i] < t) {
                mRowBusyUntil[i] = t + 6f;
                return i;
            }
        }
        int best = 0;
        for (int i = 1; i < ROWS_R2L; i++) {
            if (mRowBusyUntil[i] < mRowBusyUntil[best]) best = i;
        }
        mRowBusyUntil[best] = Math.max(mRowBusyUntil[best], t) + 6f;
        return best;
    }

    private float findTopRow(int start, int count, float t) {
        for (int i = start; i < start + count; i++) {
            if (mRowBusyUntil[i] < t) {
                mRowBusyUntil[i] = t + 5f;
                return i - start;
            }
        }
        int best = start;
        for (int i = start + 1; i < start + count; i++) {
            if (mRowBusyUntil[i] < mRowBusyUntil[best]) best = i;
        }
        mRowBusyUntil[best] = Math.max(mRowBusyUntil[best], t) + 5f;
        return best - start;
    }

    private void resetRows() {
        for (int i = 0; i < mRowBusyUntil.length; i++) mRowBusyUntil[i] = 0;
    }

    private void clearBitmaps() {
        synchronized (mPoolLock) {
            mBitmapPool.clear();
            mBitmapMemory = 0;
        }
        if (mItems != null) {
            for (DanmakuItem item : mItems) {
                item.placed = false;
                item.row = -1;
                item.bitmap = null;
                item.hasBitmap = false;
            }
        }
    }

    private List<DanmakuItem> parseXml(java.io.Reader input) {
        List<DanmakuItem> list = new ArrayList<DanmakuItem>();
        try {
            XmlPullParserFactory f = XmlPullParserFactory.newInstance();
            XmlPullParser p = f.newPullParser();
            p.setInput(input);
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "d".equals(p.getName())) {
                    try {
                        String a = p.getAttributeValue(null, "p");
                        if (a == null) { ev = p.next(); continue; }
                        String[] parts = a.split(",");
                        if (parts.length < 5) { ev = p.next(); continue; }
                        DanmakuItem item = new DanmakuItem();
                        item.time = safeFloat(parts[0], -1f);
                        if (item.time < 0f) { ev = p.next(); continue; }
                        item.type = safeInt(parts[1], 1);
                        item.color = safeColor(parts[3]);
                        item.text = p.nextText();
                        if (item.text != null && item.text.length() > 0) list.add(item);
                    } catch (Exception e) {
                        // 单条弹幕解析失败，跳过继续
                    }
                }
                ev = p.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "XML error: " + e.getMessage());
        }
        return list;
    }

    private static float safeFloat(String s, float def) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return def; }
    }

    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static int safeColor(String s) {
        try {
            int c = Integer.parseInt(s.trim());
            if ((c & 0xFF000000) == 0) c |= 0xFF000000;
            return c;
        } catch (Exception e) {
            return Color.WHITE;
        }
    }
}
