package tv.biliclassic.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import java.util.Random;

/**
 * 星球大战式 3D 字幕 + 星空背景：文字沿倾斜平面由近（屏幕底部）滚向远（上方）。
 * 透视用 Camera（API1 可用）实现；逐帧用 Handler 驱动，避开属性动画的版本限制。
 *
 * 老设备性能：星空和正文字块各自预渲染成 Bitmap，逐帧只做两次位图 blit，
 * 不再每帧重画 150 个圆 + 透视后的 StaticLayout（这是 1.6 上 PPT 帧的主因）。
 */
public class StarWarsCrawlView extends View {

    private static final int STAR_COUNT = 150;
    // 正文位图上限（像素），太大则退回直接画文字，避免老设备 OOM（ARGB_8888 每像素 4 字节）
    private static final long MAX_TEXT_PIXELS = 1000000L;

    private final Camera mCamera = new Camera();
    private final Matrix mMatrix = new Matrix();
    private final Handler mHandler = new Handler();
    private final Paint mPaint = new Paint();

    private int[] mStarX;
    private int[] mStarY;
    private int[] mStarA;

    private Bitmap mStarBmp;
    private Bitmap mTextBmp;

    private StaticLayout mLayout;
    private int mWidth;
    private int mHeight;

    private float mOffset;   // 文本顶边在倾斜平面上的 y
    private float mEnd;
    private float mStep;
    private boolean mScrolling;

    private String mPending;
    private Runnable mTicker;
    private OnCrawlEndListener mEndListener;

    public interface OnCrawlEndListener {
        void onCrawlEnd();
    }

    public StarWarsCrawlView(Context context) {
        super(context);
        setBackgroundColor(0xFF000000);
    }

    public void setEndListener(OnCrawlEndListener l) {
        mEndListener = l;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTicker();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        recycle(mStarBmp);
        mStarBmp = null;
        recycle(mTextBmp);
        mTextBmp = null;
        super.onDetachedFromWindow();
    }

    private void startTicker() {
        if (mTicker != null) {
            return;
        }
        mTicker = new Runnable() {
            public void run() {
                if (mScrolling) {
                    mOffset -= mStep;
                    if (mOffset <= mEnd) {
                        mScrolling = false;
                        if (mEndListener != null) {
                            mEndListener.onCrawlEnd();
                        }
                    }
                }
                invalidate();
                mHandler.postDelayed(this, 16);
            }
        };
        mHandler.post(mTicker);
    }

    public void stop() {
        if (mTicker != null) {
            mHandler.removeCallbacks(mTicker);
            mTicker = null;
        }
    }

    /** 传入整段文本，开始滚动；布局还没测量时会等测量完再开始 */
    public void start(String text) {
        if (text == null) {
            text = "";
        }
        if (mWidth <= 0 || mHeight <= 0) {
            mPending = text;
            return;
        }
        mPending = null;

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFE14A);            // 星战黄
        tp.setFakeBoldText(true);
        tp.setTextSize(Math.max(14f, mWidth * 0.032f));
        int layoutW = (int) (mWidth * 0.74f);
        if (layoutW < 1) {
            layoutW = 1;
        }
        mLayout = new StaticLayout(text, tp, layoutW,
                Layout.Alignment.ALIGN_CENTER, 1.35f, 0f, false);

        // 正文预渲染成「透明背景」位图：老设备每帧 blit 比每帧透视重画文字快得多；
        // 必须用 ARGB_8888（RGB_565 无 alpha，只能黑底，会把星空挡住）
        recycle(mTextBmp);
        mTextBmp = null;
        try {
            int bw = mLayout.getWidth();
            int bh = mLayout.getHeight();
            if (bw > 0 && bh > 0 && (long) bw * bh <= MAX_TEXT_PIXELS) {
                Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                mLayout.draw(c);
                mTextBmp = b;
            }
        } catch (Throwable t) {
            mTextBmp = null;
        }

        float blockH = mLayout.getHeight();
        mOffset = mHeight;                              // 文本顶边从屏幕底部进入
        mEnd = -blockH - mHeight * 0.1f;                // 完全滚出上方

        // 速度：一屏约 16 秒；总时长限制 25~240 秒（越慢越好读）
        float distance = mOffset - mEnd;
        float pxPerSec = Math.max(24f, mHeight * 0.06f);
        float secs = distance / pxPerSec;
        if (secs < 25f) {
            secs = 25f;
        }
        if (secs > 240f) {
            secs = 240f;
        }
        mStep = distance / (secs * 62.5f);

        mScrolling = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        if (w > 0 && h > 0 && (mStarBmp == null || oldw != w || oldh != h)) {
            makeStars(w, h);
        }
        if (mPending != null && w > 0 && h > 0) {
            String t = mPending;
            mPending = null;
            start(t);
        }
    }

    private void makeStars(int w, int h) {
        recycle(mStarBmp);
        mStarBmp = null;
        Random r = new Random();
        mStarX = new int[STAR_COUNT];
        mStarY = new int[STAR_COUNT];
        mStarA = new int[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            mStarX[i] = r.nextInt(w);
            mStarY[i] = r.nextInt(h);
            mStarA[i] = 90 + r.nextInt(165);
        }
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.drawColor(0xFF000000);
            for (int i = 0; i < STAR_COUNT; i++) {
                mPaint.setColor(0xFFFFFFFF);
                mPaint.setAlpha(mStarA[i]);
                float radius = mStarA[i] > 220 ? 1.7f : 1.0f;
                c.drawCircle(mStarX[i], mStarY[i], radius, mPaint);
            }
            mPaint.setAlpha(255);
            mStarBmp = b;
        } catch (Throwable t) {
            mStarBmp = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mStarBmp != null && !mStarBmp.isRecycled()) {
            canvas.drawBitmap(mStarBmp, 0, 0, null);
        } else {
            canvas.drawColor(0xFF000000);
            drawStarsDirect(canvas);
        }

        if (!mScrolling || mWidth <= 0 || mHeight <= 0) {
            return;
        }

        final float pivotX = mWidth / 2f;
        final float pivotY = mHeight;           // 近端在屏幕底部

        mCamera.save();
        mCamera.rotateX(20f);                   // 有立体感，又不会把上面的行压没
        mCamera.getMatrix(mMatrix);
        mCamera.restore();

        mMatrix.preTranslate(-pivotX, -pivotY);
        mMatrix.postTranslate(pivotX, pivotY);

        int save = canvas.save();
        canvas.concat(mMatrix);
        canvas.translate(pivotX - mLayout.getWidth() / 2f, mOffset);
        if (mTextBmp != null && !mTextBmp.isRecycled()) {
            canvas.drawBitmap(mTextBmp, 0, 0, null);
        } else {
            mLayout.draw(canvas);
        }
        canvas.restoreToCount(save);
    }

    /** mStarBmp 创建失败时逐帧画圆 */
    private void drawStarsDirect(Canvas canvas) {
        if (mStarX == null) {
            return;
        }
        for (int i = 0; i < mStarX.length; i++) {
            mPaint.setColor(0xFFFFFFFF);
            mPaint.setAlpha(mStarA[i]);
            float radius = mStarA[i] > 220 ? 1.7f : 1.0f;
            canvas.drawCircle(mStarX[i], mStarY[i], radius, mPaint);
        }
    }

    private static void recycle(Bitmap b) {
        try {
            if (b != null && !b.isRecycled()) {
                b.recycle();
            }
        } catch (Throwable t) {
        }
    }
}
