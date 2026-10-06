package tv.biliclassic.player.danmaku;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/**
 * BT-5 弹幕的普通 View 宿主：在 onDraw 里把控制器的离屏 Bitmap 画出来
 */
public class SimpleDanmakuViewPresenter extends View implements SimpleDanmakuEngine.Presenter {

    private final SimpleDanmakuEngine mEngine;

    public SimpleDanmakuViewPresenter(Context context, SimpleDanmakuEngine engine) {
        super(context);
        mEngine = engine;
    }

    @Override
    public int getPresentWidth() {
        return getWidth();
    }

    @Override
    public int getPresentHeight() {
        return getHeight();
    }

    @Override
    public void onFrame() {
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap bmp = mEngine.getOffscreen();
        if (bmp != null) {
            canvas.drawBitmap(bmp, 0, 0, null);
        }
    }
}
