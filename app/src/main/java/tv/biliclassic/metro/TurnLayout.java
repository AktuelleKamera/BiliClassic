package tv.biliclassic.metro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Camera;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * 可自绘旋转的页面容器：由外部每帧传入角度（绕 vertical edge 铰链），
 * 直接旋转整页内容。角度可前进也可回退，因此支持"真打断"。
 */
public class TurnLayout extends LinearLayout {

    private final Camera mCamera = new Camera();
    private final Matrix mMatrix = new Matrix();
    private float mAngle = 0f;
    private float mCx = 0f;
    private float mCy = 0f;

    public TurnLayout(Context c) {
        super(c);
    }

    public TurnLayout(Context c, AttributeSet a) {
        super(c, a);
    }

    /** 设置当前旋转角度（pivotRight=true 绕右边缘铰链；false 绕左边缘），并触发重绘。 */
    public void setTurn(float degrees, boolean pivotRight) {
        mAngle = degrees;
        mCx = pivotRight ? getWidth() : 0f;
        mCy = getHeight() / 2f;
        invalidate();
    }

    public void clearTurn() {
        mAngle = 0f;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mAngle != 0f) {
            canvas.save();
            mMatrix.reset();
            mCamera.save();
            mCamera.rotateY(mAngle);
            mCamera.getMatrix(mMatrix);
            mCamera.restore();
            mMatrix.preTranslate(-mCx, -mCy);
            mMatrix.postTranslate(mCx, mCy);
            canvas.concat(mMatrix);
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }
}
