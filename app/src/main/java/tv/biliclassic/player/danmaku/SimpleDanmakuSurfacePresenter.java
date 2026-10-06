package tv.biliclassic.player.danmaku;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * BT-5 弹幕的 SurfaceView 宿主：把控制器的离屏 Bitmap 画到独立 surface 上
 */
public class SimpleDanmakuSurfacePresenter extends SurfaceView
        implements SimpleDanmakuEngine.Presenter, SurfaceHolder.Callback {

    private final SimpleDanmakuEngine mEngine;
    private final SurfaceHolder mHolder;
    private volatile boolean mSurfaceReady;
    private int mSurfaceW;
    private int mSurfaceH;

    public SimpleDanmakuSurfacePresenter(Context context, SimpleDanmakuEngine engine) {
        super(context);
        mEngine = engine;
        mHolder = getHolder();
        // setZOrderMediaOverlay 是 API 5+，反射调用兼容硬失败校验的老平台
        try {
            getClass().getMethod("setZOrderMediaOverlay", boolean.class)
                    .invoke(this, Boolean.TRUE);
        } catch (Throwable t) {
        }
        mHolder.addCallback(this);
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        setWillNotDraw(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceW = width;
        mSurfaceH = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceReady = false;
    }

    @Override
    public int getPresentWidth() {
        return mSurfaceW > 0 ? mSurfaceW : getWidth();
    }

    @Override
    public int getPresentHeight() {
        return mSurfaceH > 0 ? mSurfaceH : getHeight();
    }

    @Override
    public void onFrame() {
        if (!mSurfaceReady) return;
        Canvas canvas = null;
        try {
            canvas = mHolder.lockCanvas();
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
            try { mHolder.unlockCanvasAndPost(canvas); } catch (Throwable t) {}
        }
    }
}
