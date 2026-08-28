package tv.biliclassic.metro;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * WP7 / Turnstile 风格的 3D 转门动画。
 * 基于 android.graphics.Camera。
 * 绕 Y 轴旋转 + 深度透视。centerX/centerY 可取页面右/左边缘实现"门扇翻开"效果。
 */
public class Rotate3dAnimation extends Animation {

    private final float mFromDegrees;
    private final float mToDegrees;
    private final float mCenterX;
    private final float mCenterY;
    private final float mDepthZ;
    private final boolean mReverse;
    private Camera mCamera;

    public Rotate3dAnimation(float fromDegrees, float toDegrees,
                             float centerX, float centerY, float depthZ, boolean reverse) {
        mFromDegrees = fromDegrees;
        mToDegrees = toDegrees;
        mCenterX = centerX;
        mCenterY = centerY;
        mDepthZ = depthZ;
        mReverse = reverse;
    }

    /**
     * 通用转门（Turnstile）进入动画：页面从垂直于屏幕转到平整，绕铰链侧翻开。
     * @param pivotRight true=绕右边缘铰链，false=绕左边缘铰链
     */
    public static Rotate3dAnimation turnstileIn(int w, int h, boolean pivotRight) {
        float cx = pivotRight ? w : 0;
        Rotate3dAnimation a = new Rotate3dAnimation(90, 0, cx, h / 2f, w / 2f, false);
        a.setFillBefore(true);
        a.setFillAfter(true);
        return a;
    }

    /**
     * 通用转门（Turnstile）退出动画：页面从平整转到垂直于屏幕，绕铰链侧合拢。
     * 与 turnstileIn 使用同一铰链侧，进入/退出互为反向。
     */
    public static Rotate3dAnimation turnstileOut(int w, int h, boolean pivotRight) {
        float cx = pivotRight ? w : 0;
        Rotate3dAnimation a = new Rotate3dAnimation(0, 90, cx, h / 2f, w / 2f, true);
        a.setFillBefore(true);
        a.setFillAfter(true);
        return a;
    }

    /**
     * 把视图立即渲染成指定角度（绕 vertical edge 铰链）。
     * 用于自驱动的可打断转门：每帧调用，角度可前进也可回退，支持中途反向。
     */
    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mCamera = new Camera();
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        final float fromDegrees = mFromDegrees;
        float degrees = fromDegrees + ((mToDegrees - fromDegrees) * interpolatedTime);
        final float centerX = mCenterX;
        final float centerY = mCenterY;
        final Camera camera = mCamera;
        final Matrix matrix = t.getMatrix();

        camera.save();
        if (mReverse) {
            camera.translate(0.0f, 0.0f, mDepthZ * interpolatedTime);
        } else {
            camera.translate(0.0f, 0.0f, mDepthZ * (1.0f - interpolatedTime));
        }
        camera.rotateY(degrees);
        camera.getMatrix(matrix);
        camera.restore();

        matrix.preTranslate(-centerX, -centerY);
        matrix.postTranslate(centerX, centerY);
    }
}
