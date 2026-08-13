
package master.flame.danmaku.danmaku.model.android;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import tv.cjump.jni.NativeBitmapFactory;

public class DrawingCacheHolder {

    private static int sSdkInt = -1;

    private static int getSdkInt() {
        if (sSdkInt < 0) {
            try {
                sSdkInt = android.os.Build.VERSION.class.getField("SDK_INT").getInt(null);
            } catch (Exception e) {
                try {
                    sSdkInt = Integer.parseInt(android.os.Build.VERSION.SDK);
                } catch (Exception e2) {
                    sSdkInt = 0;
                }
            }
        }
        return sSdkInt;
    }

    public Canvas canvas;

    public Bitmap bitmap;
    
    public Bitmap[][] bitmapArray;

    public Object extra;

    public int width;

    public int height;

    public boolean drawn;

    private int mDensity;

    public DrawingCacheHolder() {

    }

    public DrawingCacheHolder(int w, int h) {
        buildCache(w, h, 0, true);
    }
    
    public DrawingCacheHolder(int w, int h, int density) {
        mDensity = density;
        buildCache(w, h, density, true);
    }

    public void buildCache(int w, int h, int density, boolean checkSizeEquals) {
        boolean reuse = checkSizeEquals ? (w == width && h == height) : (w <= width && h <= height);
        if (reuse && bitmap != null) {
//            canvas.drawColor(Color.TRANSPARENT);
            canvas.setBitmap(null);
            bitmap.eraseColor(Color.TRANSPARENT);
            canvas.setBitmap(bitmap);
            recycleBitmapArray();
            return;
        }
        if (bitmap != null) {
            recycle();
        }
        width = w;
        height = h;
        bitmap = NativeBitmapFactory.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        if (density > 0) {
            mDensity = density;
            // Bitmap.setDensity(int) 是 API 4+（部分老 ROM 缺失）。封装进 DanmakuCompat.V4
            // 并用 SDK 判断：VFY 安全 + 直接调用（无反射，不卡）。
            if (getSdkInt() >= 4) {
                master.flame.danmaku.util.DanmakuCompat.V4.setBitmapDensity(bitmap, density);
            }
        }
        if (canvas == null){
            canvas = new Canvas(bitmap);
            if (getSdkInt() >= 4) {
                master.flame.danmaku.util.DanmakuCompat.V4.setCanvasDensity(canvas, density);
            }
        }else
            canvas.setBitmap(bitmap);
    }

    public void erase() {
        eraseBitmap(bitmap);
        eraseBitmapArray();
    }

    public synchronized void recycle() {
        width = height = 0;
//        if (canvas != null) {
//            canvas = null;
//        }
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        recycleBitmapArray();
        extra = null;
    }

    @SuppressLint("NewApi")
    public void splitWith(int dispWidth, int dispHeight, int maximumCacheWidth, int maximumCacheHeight) {
        recycleBitmapArray();
        if (width <= 0 || height <= 0 || bitmap == null) {
            return;
        }
        if (width <= maximumCacheWidth && height <= maximumCacheHeight) {
            return;
        }
        maximumCacheWidth = Math.min(maximumCacheWidth, dispWidth);
        maximumCacheHeight = Math.min(maximumCacheHeight, dispHeight);
        int xCount = width / maximumCacheWidth + (width % maximumCacheWidth == 0 ? 0 : 1);
        int yCount = height / maximumCacheHeight + (height % maximumCacheHeight == 0 ? 0 : 1);
        int averageWidth = width / xCount;
        int averageHeight = height / yCount;
        final Bitmap[][] bmpArray = new Bitmap[yCount][xCount];
        if (canvas == null){
            canvas = new Canvas();
            if (mDensity > 0 && getSdkInt() >= 4) {
                master.flame.danmaku.util.DanmakuCompat.V4.setCanvasDensity(canvas, mDensity);
            }
        }
        Rect rectSrc = new Rect();
        Rect rectDst = new Rect();
        for (int yIndex = 0; yIndex < yCount; yIndex++) {
            for (int xIndex = 0; xIndex < xCount; xIndex++) {
                Bitmap bmp = bmpArray[yIndex][xIndex] = NativeBitmapFactory.createBitmap(
                        averageWidth, averageHeight, Bitmap.Config.ARGB_8888);
                if (mDensity > 0 && getSdkInt() >= 4) {
                    master.flame.danmaku.util.DanmakuCompat.V4.setBitmapDensity(bmp, mDensity);
                }
                canvas.setBitmap(bmp);
                int left = xIndex * averageWidth, top = yIndex * averageHeight;
                rectSrc.set(left, top, left + averageWidth, top + averageHeight);
                rectDst.set(0, 0, bmp.getWidth(), bmp.getHeight());
                canvas.drawBitmap(bitmap, rectSrc, rectDst, null);
            }
        }
        canvas.setBitmap(bitmap);
        bitmapArray = bmpArray;
    }

    private void eraseBitmap(Bitmap bmp) {
        if (bmp != null) {
            bmp.eraseColor(Color.TRANSPARENT);
        }
    }

    private void eraseBitmapArray() {
        if (bitmapArray != null) {
            for (int i = 0; i < bitmapArray.length; i++) {
                for (int j = 0; j < bitmapArray[i].length; j++) {
                    eraseBitmap(bitmapArray[i][j]);
                }
            }
        }
    }

    private void recycleBitmapArray() {
        if (bitmapArray != null) {
            for (int i = 0; i < bitmapArray.length; i++) {
                for (int j = 0; j < bitmapArray[i].length; j++) {
                    if (bitmapArray[i][j] != null) {
                        bitmapArray[i][j].recycle();
                        bitmapArray[i][j] = null;
                    }
                }
            }
            bitmapArray = null;
        }
    }

    public final synchronized boolean draw(Canvas canvas, float left, float top, Paint paint) {
        if (bitmapArray != null) {
            for (int i = 0; i < bitmapArray.length; i++) {
                for (int j = 0; j < bitmapArray[i].length; j++) {
                    Bitmap bmp = bitmapArray[i][j];
                    if (bmp != null) {
                        float dleft = left + j * bmp.getWidth();
                        if (dleft > canvas.getWidth() || dleft + bmp.getWidth() < 0) {
                            continue;
                        }
                        float dtop = top + i * bmp.getHeight();
                        if (dtop > canvas.getHeight() || dtop + bmp.getHeight() < 0) {
                            continue;
                        }
                        drawBitmapExact(canvas, bmp, dleft, dtop, paint);
                    }
                }
            }
            return true;
        } else if (bitmap != null) {
            drawBitmapExact(canvas, bitmap, left, top, paint);
            return true;
        }
        return false;
    }

    /**
     * 1:1 像素绘制缓存 bitmap，不依赖 density 自动缩放。
     * drawBitmap(bitmap, x, y) 会按 bitmap.density 与 canvas.density 的比值缩放，
     * 若两者不一致（如软件 canvas density=160、缓存 bitmap density=屏幕 480）会被整体放大，
     * 表现为弹幕变宽/变大。用 src/dst 显式绘制可避免该缩放。
     */
    private static void drawBitmapExact(Canvas canvas, Bitmap bmp, float x, float y, Paint paint) {
        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        Rect dst = sDstRect;
        dst.set((int) x, (int) y, (int) x + bw, (int) y + bh);
        Rect src = sSrcRect;
        src.set(0, 0, bw, bh);
        canvas.drawBitmap(bmp, src, dst, paint);
    }

    private static final Rect sSrcRect = new Rect();
    private static final Rect sDstRect = new Rect();

}
