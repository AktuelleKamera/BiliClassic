package tv.biliclassic.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 背景图裁剪控件：拖动/双指缩放定位，整屏可视区域即裁剪结果
 */
public class CropImageView extends View {

    private Bitmap bmp;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint();
    private float baseScale = 1f;
    private boolean ready = false;
    private int mode = 0;
    private float lastX;
    private float lastY;
    private float baseSpacing = 0f;

    public CropImageView(Context context) {
        super(context);
        init();
    }

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        paint.setFilterBitmap(true);
        paint.setAntiAlias(true);
    }

    public void setBitmap(Bitmap b) {
        bmp = b;
        ready = false;
        setup(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setup(w, h);
    }

    private void setup(int w, int h) {
        if (bmp == null || bmp.isRecycled() || w <= 0 || h <= 0) {
            return;
        }
        float sx = w / (float) bmp.getWidth();
        float sy = h / (float) bmp.getHeight();
        baseScale = sx > sy ? sx : sy;
        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        float dx = (w - bmp.getWidth() * baseScale) / 2f;
        float dy = (h - bmp.getHeight() * baseScale) / 2f;
        matrix.postTranslate(dx, dy);
        ready = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        if (ready && bmp != null && !bmp.isRecycled()) {
            canvas.drawBitmap(bmp, matrix, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!ready) {
            return true;
        }
        int action = e.getAction() & MotionEvent.ACTION_MASK;
        if (action == MotionEvent.ACTION_DOWN) {
            mode = 1;
            lastX = e.getX();
            lastY = e.getY();
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (e.getPointerCount() >= 2) {
                mode = 2;
                baseSpacing = spacing(e);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mode == 2 && e.getPointerCount() >= 2) {
                float sp = spacing(e);
                if (baseSpacing > 1f && sp > 1f) {
                    float f = sp / baseSpacing;
                    float mx = (e.getX(0) + e.getX(1)) / 2f;
                    float my = (e.getY(0) + e.getY(1)) / 2f;
                    matrix.postScale(f, f, mx, my);
                    baseSpacing = sp;
                    clamp();
                    invalidate();
                }
            } else {
                float x = e.getX();
                float y = e.getY();
                matrix.postTranslate(x - lastX, y - lastY);
                lastX = x;
                lastY = y;
                clamp();
                invalidate();
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            mode = 1;
            lastX = e.getX(0);
            lastY = e.getY(0);
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            mode = 0;
        }
        return true;
    }

    private float spacing(MotionEvent e) {
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void clamp() {
        int w = getWidth();
        int h = getHeight();
        if (bmp == null || w <= 0 || h <= 0) {
            return;
        }
        float[] v = new float[9];
        matrix.getValues(v);
        float scale = v[Matrix.MSCALE_X];
        if (scale < baseScale) {
            float f = baseScale / scale;
            matrix.postScale(f, f, w / 2f, h / 2f);
        }
        RectF r = new RectF(0, 0, bmp.getWidth(), bmp.getHeight());
        matrix.mapRect(r);
        float dx = 0;
        float dy = 0;
        if (r.left > 0) {
            dx = -r.left;
        }
        if (r.right < w) {
            dx = w - r.right;
        }
        if (r.top > 0) {
            dy = -r.top;
        }
        if (r.bottom < h) {
            dy = h - r.bottom;
        }
        if (dx != 0 || dy != 0) {
            matrix.postTranslate(dx, dy);
        }
    }

    /** 按当前可视区域生成裁剪位图 */
    public Bitmap crop() {
        int w = getWidth();
        int h = getHeight();
        if (bmp == null || w <= 0 || h <= 0) {
            return null;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF000000);
        c.drawBitmap(bmp, matrix, paint);
        return out;
    }
}
