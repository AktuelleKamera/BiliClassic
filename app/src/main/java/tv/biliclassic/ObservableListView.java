package tv.biliclassic;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class ObservableListView extends ListView {
    public ObservableListView(Context context) {
        super(context);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    public ObservableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    public ObservableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }
}