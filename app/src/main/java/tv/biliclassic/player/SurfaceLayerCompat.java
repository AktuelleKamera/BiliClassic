package tv.biliclassic.player;

import android.view.Surface;
import android.view.SurfaceView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SurfaceLayerCompat {

    private static final String TAG = "SurfaceLayer";

    public static boolean setWindowType(SurfaceView view, int type) {
        try {
            Field layoutField = SurfaceView.class.getDeclaredField("mLayout");
            layoutField.setAccessible(true);
            Object layout = layoutField.get(view);
            Field typeField = layout.getClass().getField("type");
            typeField.setInt(layout, type);
            Field windowField = SurfaceView.class.getDeclaredField("mWindow");
            windowField.setAccessible(true);
            if (windowField.get(view) != null) {
                Method update = SurfaceView.class.getDeclaredMethod("updateWindow", boolean.class);
                update.setAccessible(true);
                update.invoke(view, Boolean.TRUE);
            }
            android.util.Log.e(TAG, "windowType SurfaceView=" + type);
            return true;
        } catch (Throwable t) {
            android.util.Log.e(TAG, "windowType SurfaceView failed", t);
            return false;
        }
    }

    public static boolean setLayer(SurfaceView view, int layer) {
        try {
            Field field = SurfaceView.class.getDeclaredField("mSurface");
            field.setAccessible(true);
            Object surface = field.get(view);
            if (surface == null) {
                android.util.Log.e(TAG, "setLayer " + view.getClass().getSimpleName()
                        + " layer=" + layer + " surface=null");
                return false;
            }
            Method setLayer = Surface.class.getMethod("setLayer", int.class);
            Method open = Surface.class.getMethod("openTransaction");
            Method close = Surface.class.getMethod("closeTransaction");
            setLayer.setAccessible(true);
            open.setAccessible(true);
            close.setAccessible(true);
            open.invoke(null);
            try {
                setLayer.invoke(surface, Integer.valueOf(layer));
            } finally {
                close.invoke(null);
            }
            android.util.Log.e(TAG, "setLayer " + view.getClass().getSimpleName()
                    + " layer=" + layer + " ok");
            return true;
        } catch (Throwable t) {
            android.util.Log.e(TAG, "setLayer " + view.getClass().getSimpleName()
                    + " layer=" + layer + " failed", t);
            return false;
        }
    }

    private SurfaceLayerCompat() {
    }
}
