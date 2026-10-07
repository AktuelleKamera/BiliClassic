package tv.biliclassic.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Window;

import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;

/**
 * 自定义图片背景：经典/Metro 主题共用的窗口背景加载。
 * 背景图会先模糊并叠加遮罩，避免过于花哨影响阅读。
 */
public final class BgUtil {

    private static Bitmap sCache;
    private static String sCachePath;
    private static boolean sCacheNight;

    private BgUtil() {
    }

    public static String getPath() {
        return SettingsActivity.getMetroBgPath();
    }

    public static boolean hasImage() {
        String p = getPath();
        return p != null && p.length() > 0;
    }

    /** 更换背景后清掉解码缓存（同一路径内容会变） */
    public static void clearCache() {
        sCache = null;
        sCachePath = null;
    }

    public static boolean isNight() {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NIGHT_MODE, false);
    }

    /** 应用窗口背景：有自定义图用图（模糊+遮罩），否则用纹理（夜间深色） */
    public static void applyWindowBackground(final Activity a) {
        if (a == null) {
            return;
        }
        final Window w = a.getWindow();
        if (w == null) {
            return;
        }
        final boolean night = isNight();
        final int texture = night
                ? R.drawable.bili_texture_background_night
                : R.drawable.bili_texture_background;
        if (!hasImage()) {
            w.setBackgroundDrawableResource(texture);
            return;
        }
        final String path = getPath();
        if (sCache != null && !sCache.isRecycled()
                && path.equals(sCachePath) && sCacheNight == night) {
            w.setBackgroundDrawable(new BgDrawable(sCache));
            return;
        }
        // 先铺纹理，解码完成再替换，避免白屏
        w.setBackgroundDrawableResource(texture);
        final DisplayMetrics dm = a.getResources().getDisplayMetrics();
        final int sw = dm.widthPixels;
        final int sh = dm.heightPixels;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap raw = decode(path, sw, sh);
                if (raw == null) {
                    return;
                }
                final Bitmap bmp = prepare(raw, sw, sh, night);
                if (bmp != raw) {
                    raw.recycle();
                }
                sCache = bmp;
                sCachePath = path;
                sCacheNight = night;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!a.isFinishing() && !bmp.isRecycled()) {
                                a.getWindow().setBackgroundDrawable(new BgDrawable(bmp));
                            }
                        } catch (Throwable t) {
                        }
                    }
                });
            }
        }).start();
    }

    private static Bitmap decode(String path, int sw, int sh) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int outW = o.outWidth;
            int outH = o.outHeight;
            if (outW <= 0 || outH <= 0) {
                return null;
            }
            int sample = 1;
            while (outW / sample > sw || outH / sample > sh) {
                sample <<= 1;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 缩小后做盒式模糊，再放大并叠遮罩，得到柔和的背景。public：Metro 主页复用同一套毛玻璃 */
    public static Bitmap prepare(Bitmap src, int sw, int sh, boolean night) {
        try {
            int bw = Math.max(1, sw / 2);
            int bh = Math.max(1, sh / 2);
            Bitmap small = Bitmap.createScaledBitmap(src, bw, bh, true);
            int[] pix = new int[bw * bh];
            small.getPixels(pix, 0, bw, 0, 0, bw, bh);
            boxBlur(pix, bw, bh, 2);
            small.setPixels(pix, 0, bw, 0, 0, bw, bh);

            Bitmap out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint();
            p.setFilterBitmap(true);
            c.drawBitmap(small, null, new Rect(0, 0, sw, sh), p);
            if (small != src) {
                small.recycle();
            }
            p.setFilterBitmap(false);
            p.setColor(night ? 0x73000000 : 0x4DFFFFFF);
            c.drawRect(0, 0, sw, sh, p);
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    /** 简单位图 Drawable，避开旧 ROM 缺失的 BitmapDrawable(Resources,Bitmap) 构造器 */
    public static class BgDrawable extends android.graphics.drawable.Drawable {
        private final Bitmap bmp;
        private final Paint paint = new Paint();

        public BgDrawable(Bitmap b) {
            bmp = b;
            paint.setFilterBitmap(true);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect r = getBounds();
            canvas.drawBitmap(bmp, null, r, paint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    private static void boxBlur(int[] pix, int w, int h, int r) {
        int[] tmp = new int[pix.length];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int ra = 0;
                int ga = 0;
                int ba = 0;
                int n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = x + k;
                    if (xx < 0) xx = 0;
                    if (xx >= w) xx = w - 1;
                    int col = pix[row + xx];
                    ra += (col >>> 16) & 0xFF;
                    ga += (col >>> 8) & 0xFF;
                    ba += col & 0xFF;
                    n++;
                }
                tmp[row + x] = 0xFF000000 | ((ra / n) << 16) | ((ga / n) << 8) | (ba / n);
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int ra = 0;
                int ga = 0;
                int ba = 0;
                int n = 0;
                for (int k = -r; k <= r; k++) {
                    int yy = y + k;
                    if (yy < 0) yy = 0;
                    if (yy >= h) yy = h - 1;
                    int col = tmp[yy * w + x];
                    ra += (col >>> 16) & 0xFF;
                    ga += (col >>> 8) & 0xFF;
                    ba += col & 0xFF;
                    n++;
                }
                pix[y * w + x] = 0xFF000000 | ((ra / n) << 16) | ((ga / n) << 8) | (ba / n);
            }
        }
    }
}
