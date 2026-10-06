package tv.biliclassic.player.danmaku;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.TextureView;

/**
 * BT-5 弹幕的 TextureView 宿主（API 14+）
 *
 * 本类单独存在并通过工厂实例化：TextureView 是 API 14+，若被老平台 verifier
 * 解析会拒载整个类，故与老平台隔离
 */
@SuppressLint("NewApi")
public class SimpleDanmakuTexturePresenter extends TextureView implements SimpleDanmakuEngine.Presenter {

    private final SimpleDanmakuEngine mEngine;

    public SimpleDanmakuTexturePresenter(Context context, SimpleDanmakuEngine engine) {
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
        // TextureView 的 onDraw 是 final，只能通过 lockCanvas 把离屏 Bitmap 画到纹理上
        Canvas canvas = null;
        try {
            canvas = lockCanvas();
        } catch (Throwable t) {
            return;
        }
        if (canvas == null) return;
        try {
            Bitmap bmp = mEngine.getOffscreen();
            if (bmp != null) {
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                canvas.drawBitmap(bmp, 0, 0, null);
            }
        } finally {
            try { unlockCanvasAndPost(canvas); } catch (Throwable t) {}
        }
    }
}
