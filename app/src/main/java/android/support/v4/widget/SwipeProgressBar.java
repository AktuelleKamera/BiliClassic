package android.support.v4.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

final class SwipeProgressBar {
    private static final int ANIMATION_DURATION_MS = 2000;
    private static final int COLOR1 = -1291845632;
    private static final int COLOR2 = Integer.MIN_VALUE;
    private static final int COLOR3 = 1291845632;
    private static final int COLOR4 = 436207616;
    private static final int FINISH_ANIMATION_DURATION_MS = 1000;
    private static final Interpolator INTERPOLATOR = BakedBezierInterpolator.getInstance();
    private final Paint mPaint = new Paint();
    private final RectF mClipRect = new RectF();
    private Rect mBounds = new Rect();
    private long mFinishTime;
    private View mParent;
    private boolean mRunning;
    private long mStartTime;
    private float mTriggerPercentage;
    private int mColor1 = COLOR1;
    private int mColor2 = Integer.MIN_VALUE;
    private int mColor3 = COLOR3;
    private int mColor4 = COLOR4;

    public SwipeProgressBar(View parent) {
        this.mParent = parent;
    }

    void setColorScheme(int color1, int color2, int color3, int color4) {
        this.mColor1 = color1;
        this.mColor2 = color2;
        this.mColor3 = color3;
        this.mColor4 = color4;
    }

    void setTriggerPercentage(float triggerPercentage) {
        this.mTriggerPercentage = triggerPercentage;
        this.mStartTime = 0L;
        this.mParent.postInvalidate();
    }

    void start() {
        if (!this.mRunning) {
            this.mTriggerPercentage = 0.0f;
            this.mStartTime = AnimationUtils.currentAnimationTimeMillis();
            this.mRunning = true;
            this.mParent.postInvalidate();
        }
    }

    void stop() {
        if (this.mRunning) {
            this.mTriggerPercentage = 0.0f;
            this.mFinishTime = AnimationUtils.currentAnimationTimeMillis();
            this.mRunning = false;
            this.mParent.postInvalidate();
        }
    }

    boolean isRunning() {
        return this.mRunning || this.mFinishTime > 0;
    }

    void draw(Canvas canvas) {
        int width = this.mBounds.width();
        int height = this.mBounds.height();
        int cx = width / 2;
        int cy = height / 2;
        boolean drawTriggerWhileFinishing = false;
        int restoreCount = canvas.save();
        canvas.clipRect(this.mBounds);
        if (this.mRunning || this.mFinishTime > 0) {
            long now = AnimationUtils.currentAnimationTimeMillis();
            long elapsed = (now - this.mStartTime) % ANIMATION_DURATION_MS;
            long iterations = (now - this.mStartTime) / ANIMATION_DURATION_MS;
            float rawProgress = elapsed / 20.0f;
            if (!this.mRunning) {
                if (now - this.mFinishTime >= FINISH_ANIMATION_DURATION_MS) {
                    this.mFinishTime = 0L;
                    return;
                }
                long finishElapsed = (now - this.mFinishTime) % FINISH_ANIMATION_DURATION_MS;
                float finishProgress = finishElapsed / 10.0f;
                float pct = finishProgress / 100.0f;
                float clearRadius = (width / 2) * INTERPOLATOR.getInterpolation(pct);
                this.mClipRect.set(cx - clearRadius, 0.0f, cx + clearRadius, height);
                canvas.saveLayerAlpha(this.mClipRect, 0, 0);
                drawTriggerWhileFinishing = true;
            }
            if (iterations == 0) {
                canvas.drawColor(this.mColor1);
            } else if (rawProgress >= 0.0f && rawProgress < 25.0f) {
                canvas.drawColor(this.mColor4);
            } else if (rawProgress >= 25.0f && rawProgress < 50.0f) {
                canvas.drawColor(this.mColor1);
            } else if (rawProgress >= 50.0f && rawProgress < 75.0f) {
                canvas.drawColor(this.mColor2);
            } else {
                canvas.drawColor(this.mColor3);
            }
            if (rawProgress >= 0.0f && rawProgress <= 25.0f) {
                float pct2 = ((25.0f + rawProgress) * 2.0f) / 100.0f;
                drawCircle(canvas, cx, cy, this.mColor1, pct2);
            }
            if (rawProgress >= 0.0f && rawProgress <= 50.0f) {
                float pct3 = (2.0f * rawProgress) / 100.0f;
                drawCircle(canvas, cx, cy, this.mColor2, pct3);
            }
            if (rawProgress >= 25.0f && rawProgress <= 75.0f) {
                float pct4 = ((rawProgress - 25.0f) * 2.0f) / 100.0f;
                drawCircle(canvas, cx, cy, this.mColor3, pct4);
            }
            if (rawProgress >= 50.0f && rawProgress <= 100.0f) {
                float pct5 = ((rawProgress - 50.0f) * 2.0f) / 100.0f;
                drawCircle(canvas, cx, cy, this.mColor4, pct5);
            }
            if (rawProgress >= 75.0f && rawProgress <= 100.0f) {
                float pct6 = ((rawProgress - 75.0f) * 2.0f) / 100.0f;
                drawCircle(canvas, cx, cy, this.mColor1, pct6);
            }
            if (this.mTriggerPercentage > 0.0f && drawTriggerWhileFinishing) {
                canvas.restoreToCount(restoreCount);
                restoreCount = canvas.save();
                canvas.clipRect(this.mBounds);
                drawTrigger(canvas, cx, cy);
            }
            this.mParent.postInvalidate();
        } else if (this.mTriggerPercentage > 0.0f && this.mTriggerPercentage <= 1.0d) {
            drawTrigger(canvas, cx, cy);
        }
        canvas.restoreToCount(restoreCount);
    }

    private void drawTrigger(Canvas canvas, int cx, int cy) {
        this.mPaint.setColor(this.mColor1);
        canvas.drawCircle(cx, cy, cx * this.mTriggerPercentage, this.mPaint);
    }

    private void drawCircle(Canvas canvas, float cx, float cy, int color, float pct) {
        this.mPaint.setColor(color);
        canvas.save();
        canvas.translate(cx, cy);
        float radiusScale = INTERPOLATOR.getInterpolation(pct);
        canvas.scale(radiusScale, radiusScale);
        canvas.drawCircle(0.0f, 0.0f, cx, this.mPaint);
        canvas.restore();
    }

    void setBounds(int left, int top, int right, int bottom) {
        this.mBounds.left = left;
        this.mBounds.top = top;
        this.mBounds.right = right;
        this.mBounds.bottom = bottom;
    }
}
