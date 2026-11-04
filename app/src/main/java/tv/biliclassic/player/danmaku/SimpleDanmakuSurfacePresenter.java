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
    private boolean mNeedOverlayWindow;
    private boolean mOverlayWindowApplied;

    public SimpleDanmakuSurfacePresenter(Context context, SimpleDanmakuEngine engine) {
        super(context);
        mEngine = engine;
        mHolder = getHolder();
        // setZOrderMediaOverlay 是 API 5+，反射调用兼容硬失败校验的老平台
        try {
            getClass().getMethod("setZOrderMediaOverlay", boolean.class)
                    .invoke(this, Boolean.TRUE);
            android.util.Log.e("SurfaceLayer", "mediaOverlay ok SimpleDanmakuSurfacePresenter");
        } catch (Throwable t) {
            android.util.Log.e("SurfaceLayer", "mediaOverlay failed SimpleDanmakuSurfacePresenter", t);
            mNeedOverlayWindow = true;
        }
        mHolder.addCallback(this);
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        setWillNotDraw(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyOverlayWindowAfterAttach();
    }

    private void applyOverlayWindowAfterAttach() {
        if (!mNeedOverlayWindow || mOverlayWindowApplied) return;
        try {
            java.lang.reflect.Field wf = SurfaceView.class.getDeclaredField("mWindow");
            wf.setAccessible(true);
            if (wf.get(this) == null) {
                android.util.Log.e("SurfaceLayer", "overlay window mWindow=null SimpleDanmakuSurfacePresenter");
                return;
            }
            java.lang.reflect.Field sf = SurfaceView.class.getDeclaredField("mSession");
            sf.setAccessible(true);
            Object session = sf.get(this);
            java.lang.reflect.Field lf = SurfaceView.class.getDeclaredField("mLayout");
            lf.setAccessible(true);
            Object layout = lf.get(this);
            java.lang.reflect.Field tf = layout.getClass().getField("type");
            tf.setInt(layout, 1003);
            java.lang.reflect.Field vf = SurfaceView.class.getDeclaredField("mVisible");
            vf.setAccessible(true);
            boolean visible = vf.getBoolean(this);
            java.lang.reflect.Field cf = SurfaceView.class.getDeclaredField("mContentInsets");
            cf.setAccessible(true);
            Object insets = cf.get(this);

            Class<?> iw = Class.forName("android.view.IWindow");
            Class<?> lp = Class.forName("android.view.WindowManager$LayoutParams");
            Class<?> rect = Class.forName("android.graphics.Rect");
            Class<?> myWindow = Class.forName("android.view.SurfaceView$MyWindow");
            java.lang.reflect.Constructor<?> ctor = myWindow.getDeclaredConstructor(SurfaceView.class);
            ctor.setAccessible(true);
            Object newWindow = ctor.newInstance(this);

            session.getClass().getMethod("remove", iw).invoke(session, wf.get(this));
            wf.set(this, null);
            session.getClass().getMethod("add", iw, lp, int.class, rect)
                    .invoke(session, newWindow, layout, Integer.valueOf(visible ? 0 : 8), insets);
            wf.set(this, newWindow);
            java.lang.reflect.Method update = SurfaceView.class
                    .getDeclaredMethod("updateWindow", boolean.class);
            update.setAccessible(true);
            update.invoke(this, Boolean.TRUE);
            mOverlayWindowApplied = true;
            android.util.Log.e("SurfaceLayer", "overlay window re-added SimpleDanmakuSurfacePresenter");
        } catch (Throwable t) {
            android.util.Log.e("SurfaceLayer", "overlay window re-add failed SimpleDanmakuSurfacePresenter", t);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceReady = true;
        clearSurface(holder);
        post(new Runnable() {
            public void run() {
                applyOverlayWindowAfterAttach();
                clearSurface(mHolder);
            }
        });
    }

    private void clearSurface(SurfaceHolder holder) {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            }
        } catch (Throwable ignored) {
        } finally {
            if (canvas != null) {
                try { holder.unlockCanvasAndPost(canvas); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceW = width;
        mSurfaceH = height;
        clearSurface(holder);
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
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            if (bmp != null) {
                canvas.drawBitmap(bmp, 0, 0, null);
            }
        } finally {
            try { mHolder.unlockCanvasAndPost(canvas); } catch (Throwable t) {}
        }
    }
}
