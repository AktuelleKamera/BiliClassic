package master.flame.danmaku.danmaku.model.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;

import java.util.concurrent.ConcurrentHashMap;

import master.flame.danmaku.danmaku.model.BaseDanmaku;

/**
 * Created by ch on 15-7-16.
 */
public class SimpleTextCacheStuffer extends BaseCacheStuffer {

    private final static ConcurrentHashMap<Float, Float> sTextHeightCache =
            new ConcurrentHashMap<Float, Float>();
    private final static ThreadLocal<Paint.FontMetricsInt> sFontMetrics =
            new ThreadLocal<Paint.FontMetricsInt>() {
                @Override
                protected Paint.FontMetricsInt initialValue() {
                    return new Paint.FontMetricsInt();
                }
            };

    protected Float getCacheHeight(BaseDanmaku danmaku, Paint paint) {
        Float textSize = Float.valueOf(paint.getTextSize());
        Float textHeight = sTextHeightCache.get(textSize);
        if (textHeight == null) {
            Paint.FontMetricsInt fontMetrics = sFontMetrics.get();
            paint.getFontMetricsInt(fontMetrics);
            textHeight = Float.valueOf(fontMetrics.descent - fontMetrics.ascent
                    + fontMetrics.leading);
            sTextHeightCache.put(textSize, textHeight);
        }
        return textHeight;
    }

    @Override
    public void measure(BaseDanmaku danmaku, TextPaint paint) {
        float w = 0;
        Float textHeight = 0f;
        if (danmaku.lines == null) {
            if (danmaku.text == null) {
                w = 0;
            } else {
                w = paint.measureText(danmaku.text.toString());
                textHeight = getCacheHeight(danmaku, paint);
            }
            danmaku.paintWidth = w;
            danmaku.paintHeight = textHeight;
        } else {
            textHeight = getCacheHeight(danmaku, paint);
            for (String tempStr : danmaku.lines) {
                if (tempStr.length() > 0) {
                    float tr = paint.measureText(tempStr);
                    w = Math.max(tr, w);
                }
            }
            danmaku.paintWidth = w;
            danmaku.paintHeight = danmaku.lines.length * textHeight;
        }
    }

    @Override
    public void drawStroke(BaseDanmaku danmaku, String lineText, Canvas canvas, float left, float top, Paint paint) {
        if (lineText != null) {
            canvas.drawText(lineText, left, top, paint);
        } else {
            canvas.drawText(danmaku.text.toString(), left, top, paint);
        }
    }

    @Override
    public void drawText(BaseDanmaku danmaku, String lineText, Canvas canvas, float left, float top, Paint paint) {
        if (lineText != null) {
            canvas.drawText(lineText, left, top, paint);
        } else {
            canvas.drawText(danmaku.text.toString(), left, top, paint);
        }
    }

    @Override
    public void clearCaches() {
        sTextHeightCache.clear();
    }

    @Override
    public void drawBackground(BaseDanmaku danmaku, Canvas canvas, float left, float top) {

    }
}
