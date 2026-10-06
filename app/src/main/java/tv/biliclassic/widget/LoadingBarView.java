package tv.biliclassic.widget;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import tv.biliclassic.R;

/**
 * 统一加载条：小转圈 + 嘿咻…嘿咻…
 * 全应用只有这一份样式，状态：加载中/只显示/隐藏
 */
public class LoadingBarView extends LinearLayout {

    private ProgressBar mSpinner;
    private TextView mText;

    public LoadingBarView(Context context) {
        super(context);
        init();
    }

    public LoadingBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        int pad = dp(16);
        setPadding(0, pad, 0, pad);

        mSpinner = new ProgressBar(getContext());
        addView(mSpinner, new LayoutParams(dp(16), dp(16)));

        mText = new TextView(getContext());
        mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mText.setTextColor(0xFF999999);
        mText.setGravity(Gravity.CENTER);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        addView(mText, lp);

        mText.setText(R.string.login_working_hard);
        syncSpinner();
    }

    /** 加载中：转圈 + 嘿咻…嘿咻… */
    public void showLoading() {
        mText.setText(R.string.login_working_hard);
        syncSpinner();
        setVisibility(VISIBLE);
    }

    /** 只恢复显示，文字和转圈状态保持原样 */
    public void show() {
        syncSpinner();
        setVisibility(VISIBLE);
    }

    /** 显示状态文字（无转圈），如为空/失败 */
    public void showStatus(CharSequence msg) {
        mText.setText(msg);
        syncSpinner();
        setVisibility(VISIBLE);
    }

    public void showStatus(int resId) {
        showStatus(getContext().getResources().getText(resId));
    }

    /** 隐藏 */
    public void hide() {
        syncSpinner();
        setVisibility(GONE);
    }

    /** 背景跟随参照视图（一般是列表），避免和列表有色差 */
    public void bindBackground(View ref) {
        if (ref == null) return;
        Drawable d = ref.getBackground();
        if (d == null) return;
        if (d instanceof ColorDrawable) {
            setBackgroundDrawable(new ColorDrawable(
                    tv.biliclassic.util.UiSkin.drawableColor(d)));
        } else {
            Drawable.ConstantState cs = d.getConstantState();
            setBackgroundDrawable(cs != null ? cs.newDrawable() : d);
        }
    }

    public void setTextColor(int color) {
        mText.setTextColor(color);
    }

    /** 转圈只在文字是「嘿咻…嘿咻…」时出现，避免状态不同步 */
    private void syncSpinner() {
        CharSequence t = mText.getText();
        boolean loading = t != null
                && t.toString().equals(getResources().getString(R.string.login_working_hard));
        mSpinner.setVisibility(loading ? VISIBLE : GONE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
