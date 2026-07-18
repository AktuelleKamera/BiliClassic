package tv.biliclassic.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import tv.biliclassic.util.SdkHelper;

public class RadioButton extends android.widget.RadioButton {
    private Drawable mButtonDrawable;

    public RadioButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public RadioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RadioButton(Context context) {
        super(context);
    }

    @Override
    public void setButtonDrawable(Drawable d) {
        this.mButtonDrawable = d;
        super.setButtonDrawable(d);
    }

    @Override
    public int getCompoundPaddingLeft() {
        if (SdkHelper.getSdkInt() < 17) {
            return getPaddingLeft();
        }
        if (this.mButtonDrawable != null) {
            return this.mButtonDrawable.getMinimumWidth()
                    + (getPaddingLeft() - this.mButtonDrawable.getMinimumWidth());
        }
        return super.getCompoundPaddingLeft() - getPaddingLeft();
    }
}
