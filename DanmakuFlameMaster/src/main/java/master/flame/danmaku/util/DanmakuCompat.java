package master.flame.danmaku.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/**
 * 弹幕完整引擎（DanmakuFlameMaster）的高版本 API 辅助类。
 *
 * Dalvik/ART 按类懒验证：只有类真正被加载/验证时，其方法体内引用的高版本 API 才会被检查。
 * 因此把调用高版本 API 的方法拆到带版本后缀的独立类，主类按 SDK_INT 判断只在对应版本
 * 加载调用：
 *  - 低版本设备永不加载这些类 → 不触发 VerifyError（VFY 安全，Android 1.5 可用）
 *  - 高版本设备加载后是普通静态直接调用 → 无反射开销（不卡顿）
 */
public final class DanmakuCompat {

    private DanmakuCompat() {
    }

    // ===== API 4: Bitmap.setDensity() / Canvas.setDensity() =====

    public static final class V4 {
        public static void setBitmapDensity(Bitmap b, int density) {
            b.setDensity(density);
        }

        public static void setCanvasDensity(Canvas c, int density) {
            c.setDensity(density);
        }
    }

    // ===== API 14: Canvas.getMaximumBitmapWidth/Height() =====

    public static final class V14 {
        public static int getMaximumBitmapWidth(Canvas c) {
            return c.getMaximumBitmapWidth();
        }

        public static int getMaximumBitmapHeight(Canvas c) {
            return c.getMaximumBitmapHeight();
        }
    }

    // ===== API 16: View.postInvalidateOnAnimation() =====

    public static final class V16 {
        public static void postInvalidateOnAnimation(View v) {
            v.postInvalidateOnAnimation();
        }
    }

    // ===== API 17: Bitmap.isPremultiplied()/setPremultiplied() =====

    public static final class V17 {
        public static boolean isPremultiplied(Bitmap b) {
            return b.isPremultiplied();
        }

        public static void setPremultiplied(Bitmap b, boolean premultiplied) {
            b.setPremultiplied(premultiplied);
        }
    }
}
