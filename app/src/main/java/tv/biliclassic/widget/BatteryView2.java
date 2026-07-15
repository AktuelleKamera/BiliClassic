package tv.biliclassic.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ImageView;

import tv.biliclassic.R;

public class BatteryView2 extends ImageView {
    BroadcastReceiver mBatteryReceiver;
    private boolean mCharging;
    private int mLevel;
    private Paint mPaint;
    private Rect mRect;
    private boolean mReceiverRegistered = false;

    public BatteryView2(Context context) {
        super(context);
        initView(context, null);
    }

    public BatteryView2(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public BatteryView2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        this.mPaint = new Paint();
        this.mRect = new Rect();

        if (this.mBatteryReceiver == null) {
            this.mBatteryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context2, Intent intent) {
                    BatteryView2.this.refreshBattery(intent);
                }
            };
        }

        if (!mReceiverRegistered) {
            IntentFilter filter = new IntentFilter("android.intent.action.BATTERY_CHANGED");
            Intent intent = context.registerReceiver(this.mBatteryReceiver, filter);
            mReceiverRegistered = true;
            refreshBattery(intent);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        unregisterReceiver();
        super.onDetachedFromWindow();
    }

    public void release() {
        unregisterReceiver();
    }

    private void unregisterReceiver() {
        if (mReceiverRegistered && mBatteryReceiver != null) {
            try {
                getContext().unregisterReceiver(mBatteryReceiver);
                mReceiverRegistered = false;
            } catch (Exception e) {
                mReceiverRegistered = false;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.mLevel == 0 || this.mCharging) {
            super.onDraw(canvas);
            return;
        }
        Rect bounds = getDrawable().getBounds();
        int padding = (int) (dp2px(getContext(), 1.5f) + 0.5f);
        int w = (this.mLevel * (bounds.width() - (padding * 2))) / 100;
        if (w < padding + 2) {
            w = padding + 2;
        }
        this.mRect.set(bounds.left + padding, bounds.top + padding,
                bounds.left + w, bounds.bottom - padding);
        this.mPaint.setColor(this.mLevel < 20 ? 0xFFFF9751 : 0xFFFFFFFF);
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.concat(getImageMatrix());
        canvas.drawRect(this.mRect, this.mPaint);
        canvas.restoreToCount(saveCount);
        super.onDraw(canvas);
    }

    private void refreshBattery(Intent intent) {
        if (intent != null) {
            int level = intent.getIntExtra("level", 100);
            boolean plugged = intent.getIntExtra("plugged", 0) != 0;
            this.mCharging = plugged;
            int drawableResId;
            if (this.mCharging) {
                drawableResId = R.drawable.player_battery_charging_icon;
            } else if (level == 0 || level >= 20) {
                drawableResId = R.drawable.player_battery_icon;
            } else {
                drawableResId = R.drawable.player_battery_red_icon;
            }
            super.setImageResource(drawableResId);
            if (this.mLevel != level) {
                this.mLevel = level;
                invalidate();
            }
        }
    }

    private static float dp2px(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}