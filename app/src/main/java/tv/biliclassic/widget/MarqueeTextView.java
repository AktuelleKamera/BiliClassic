package tv.biliclassic.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * 跑马灯 TextView
 *
 * 原实现每个 UI 帧 scrollXPos++ 并 postInvalidate，60fps 持续重绘整窗，
 * 老设备上会拖垮 GPU。现在改为按设定帧率（取播放器「限制帧数」max-fps）
 * 用 ticker 驱动，滚动位移按时间计算（60px/秒），视觉效果不变，重绘次数
 * 降到 max-fps 次/秒
 */
public class MarqueeTextView extends TextView {
    private static final int STATE_PAUSE_START = 0;
    private static final int STATE_SCROLL = 1;
    private static final int STATE_PAUSE_END = 2;

    // 滚动速度，原实现为 60fps 下每帧 1px，即 60px/秒
    private static final int SCROLL_PX_PER_SEC = 60;

    private float maxScrollX;
    private int scrollXPos;
    private boolean marqueeEnabled;
    private int state;
    private long stateStartTime;
    private long lastTickTime;

    private int fps = 60;

    // 是否自动开始跑马灯
    private boolean autoStartMarquee = true;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            if (!marqueeEnabled) return;
            tick();
            if (marqueeEnabled) {
                postDelayed(mTicker, Math.max(1, 1000 / fps));
            }
        }
    };

    public MarqueeTextView(Context context) {
        super(context);
    }

    public MarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarqueeTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /** 设置跑马灯帧率，来自播放器「限制帧数」设置 */
    public void setFps(int fps) {
        if (fps >= 1 && fps <= 120) this.fps = fps;
    }

    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(true, direction, previouslyFocusedRect);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        stopMarquee();
        if (autoStartMarquee) {
            post(new Runnable() {
                @Override
                public void run() {
                    initMarquee();
                }
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopMarquee();
        super.onDetachedFromWindow();
    }

    @Override
    public void computeScroll() {
        // 滚动由 mTicker 按帧率驱动，这里不再每帧 postInvalidate
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long dt = now - lastTickTime;
        if (dt < 0) dt = 0;
        lastTickTime = now;
        long elapsed = now - stateStartTime;

        switch (state) {
            case STATE_PAUSE_START:
                if (elapsed >= 3000) {
                    state = STATE_SCROLL;
                    stateStartTime = now;
                }
                break;
            case STATE_SCROLL:
                scrollXPos += (int) (dt * SCROLL_PX_PER_SEC / 1000);
                if (scrollXPos >= maxScrollX) {
                    scrollXPos = (int) maxScrollX;
                    scrollTo(scrollXPos, 0);
                    state = STATE_PAUSE_END;
                    stateStartTime = now;
                    break;
                }
                scrollTo(scrollXPos, 0);
                break;
            case STATE_PAUSE_END:
                if (elapsed >= 3000) {
                    scrollXPos = 0;
                    scrollTo(0, 0);
                    state = STATE_PAUSE_START;
                    stateStartTime = now;
                }
                break;
        }
        invalidate();
    }

    /**
     * 开始跑马灯，外部调用
     */
    public void initMarquee() {
        if (marqueeEnabled) return;
        if (getWidth() == 0 || getText() == null) return;

        android.text.Layout layout = getLayout();
        if (layout == null) return;
        float textWidth = layout.getLineWidth(0);
        float extra = getResources().getDisplayMetrics().density * 80;
        maxScrollX = textWidth - (getWidth() - getPaddingLeft() - getPaddingRight()) + extra;
        if (maxScrollX <= 0) {
            // 文字未超出可视宽度，无需滚动
            marqueeEnabled = false;
            scrollTo(0, 0);
            return;
        }

        marqueeEnabled = true;
        scrollXPos = 0;
        state = STATE_PAUSE_START;
        stateStartTime = System.currentTimeMillis();
        lastTickTime = stateStartTime;
        scrollTo(0, 0);
        removeCallbacks(mTicker);
        postDelayed(mTicker, Math.max(1, 1000 / fps));
    }

    /**
     * 停止跑马灯，外部调用
     */
    public void stopMarquee() {
        marqueeEnabled = false;
        removeCallbacks(mTicker);
        scrollTo(0, 0);
    }

    /**
     * 设置是否自动开始跑马灯
     * @param auto true 自动开始（默认），false 需要手动调用 initMarquee()
     */
    public void setAutoStartMarquee(boolean auto) {
        this.autoStartMarquee = auto;
        if (!auto) {
            stopMarquee();
        }
    }

    /**
     * 判断当前是否在滚动
     */
    public boolean isMarqueeEnabled() {
        return marqueeEnabled;
    }

    /**
     * 根据文本长度判断是否需要滚动
     * @param threshold 字符数阈值
     * @return true 需要滚动
     */
    public boolean shouldScroll(int threshold) {
        CharSequence text = getText();
        if (text == null) return false;
        return text.length() > threshold;
    }
}
