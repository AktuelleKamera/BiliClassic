package tv.biliclassic.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.TextView;

public class MarqueeTextView extends TextView {
    private static final int STATE_PAUSE_START = 0;
    private static final int STATE_SCROLL = 1;
    private static final int STATE_PAUSE_END = 2;

    private float maxScrollX;
    private int scrollXPos;
    private boolean marqueeEnabled;
    private int state;
    private long stateStartTime;

    // 新增：控制是否自动启动滚动
    private boolean autoStartMarquee = true;

    public MarqueeTextView(Context context) {
        super(context);
    }

    public MarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarqueeTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
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
        if (!marqueeEnabled) return;

        long now = System.currentTimeMillis();
        long elapsed = now - stateStartTime;

        switch (state) {
            case STATE_PAUSE_START:
                if (elapsed >= 3000) {
                    state = STATE_SCROLL;
                    stateStartTime = now;
                }
                break;
            case STATE_SCROLL:
                scrollXPos++;
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
        postInvalidate();
    }

    /**
     * 初始化滚动（公开方法，可外部调用）
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
            // 文字没有超出，不启动滚动
            marqueeEnabled = false;
            scrollTo(0, 0);
            return;
        }

        marqueeEnabled = true;
        scrollXPos = 0;
        state = STATE_PAUSE_START;
        stateStartTime = System.currentTimeMillis();
        scrollTo(0, 0);
        postInvalidate();
    }

    /**
     * 停止滚动（公开方法，可外部调用）
     */
    public void stopMarquee() {
        marqueeEnabled = false;
        scrollTo(0, 0);
    }

    /**
     * 设置是否自动启动滚动
     * @param auto true 自动启动（默认），false 需要手动调用 initMarquee()
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