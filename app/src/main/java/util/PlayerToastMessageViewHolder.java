package util;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;
import tv.biliclassic.R;

public class PlayerToastMessageViewHolder {
    private FrameLayout.LayoutParams mLayoutParams;
    private Animation mAnimIn;
    private Animation mAnimOut;
    private FrameLayout mParentView;
    private TextView mTipTextView;
    private ViewGroup mViewRoot;
    private boolean isAnimationIn;
    private boolean isAnimationOut;

    private Runnable dismissRunnable = new Runnable() {
        public void run() {
            if (mViewRoot != null && mViewRoot.isShown()) {
                dismiss();
            }
        }
    };

    private Runnable mDismissAction = new Runnable() {
        public void run() {
            if (mViewRoot != null && mViewRoot.isShown()) {
                cancelAnimation(mAnimIn);
                mViewRoot.startAnimation(mAnimOut);
            }
        }
    };

    private FAnimationListener animationListener = new FAnimationListener();

    // Animation.cancel() 是 API8(Android 2.2)+ 才有的方法，
    // 低版本(1.5/1.6)verifier 会因类里引用它而拒绝整个类，必须反射调用。
    private static java.lang.reflect.Method sAnimationCancelMethod;

    static {
        try {
            sAnimationCancelMethod = Animation.class.getMethod("cancel");
        } catch (Throwable t) {
            sAnimationCancelMethod = null;
        }
    }

    private static void cancelAnimation(Animation a) {
        if (a == null || sAnimationCancelMethod == null) return;
        try {
            sAnimationCancelMethod.invoke(a);
        } catch (Throwable t) {
        }
    }

    private class FAnimationListener implements Animation.AnimationListener {
        int fadeMS = 100;

        public void onAnimationStart(Animation animation) {
            if (animation == mAnimIn) {
                isAnimationIn = true;
            } else if (animation == mAnimOut) {
                isAnimationOut = true;
            }
        }

        public void onAnimationRepeat(Animation animation) {
        }

        public void onAnimationEnd(Animation animation) {
            if (animation == mAnimIn) {
                mViewRoot.setVisibility(android.view.View.VISIBLE);
                startFadeDismiss(fadeMS);
                isAnimationIn = false;
            } else if (animation == mAnimOut) {
                mViewRoot.clearAnimation();
                mViewRoot.setVisibility(android.view.View.GONE);
                isAnimationOut = false;
            }
        }
    }

    private void startFadeDismiss(int ms) {
        if (mViewRoot != null) {
            mViewRoot.removeCallbacks(dismissRunnable);
            mViewRoot.postDelayed(dismissRunnable, ms);
        }
    }

    public void initView(Activity activity, ViewGroup rootView) {
        if (!inited() && activity != null && (rootView instanceof FrameLayout)) {
            mParentView = (FrameLayout) rootView;
            LayoutInflater layoutInflater = (LayoutInflater) activity.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            mViewRoot = (ViewGroup) layoutInflater.inflate(R.layout.bili_app_player_toast_message, mParentView, false);
            mLayoutParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            setTipViewCenter(false);
            mParentView.addView(mViewRoot, mLayoutParams);
            if (mViewRoot != null) {
                mViewRoot.setVisibility(android.view.View.INVISIBLE);
                mTipTextView = (TextView) mViewRoot.findViewById(R.id.message);
                loadAnimations(activity);
            }
        }
    }

    private boolean inited() {
        return mViewRoot != null;
    }

    private void loadAnimations(Activity activity) {
        mAnimIn = AnimationUtils.loadAnimation(activity, R.anim.fade_in);
        mAnimOut = AnimationUtils.loadAnimation(activity, R.anim.fade_out);
        mAnimIn.setAnimationListener(animationListener);
        mAnimOut.setAnimationListener(animationListener);
        mAnimIn.setFillAfter(true);
        mAnimOut.setFillAfter(true);
    }

    private void setTipViewCenter(boolean center) {
        if (mLayoutParams != null && mViewRoot != null) {
            if (center) {
                mLayoutParams.bottomMargin = 0;
                mLayoutParams.gravity = android.view.Gravity.CENTER;
            } else {
                mLayoutParams.bottomMargin = mViewRoot.getResources().getDimensionPixelSize(R.dimen.player_bottom_controller_pannel_layout_height)
                        + mViewRoot.getResources().getDimensionPixelSize(R.dimen.player_bottom_controller_toast_message_bottom_margin);
                mLayoutParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            }
        }
    }

    public void show(String text, int ms, boolean showAtBottom) {
        if (mViewRoot != null && mTipTextView != null) {
            setTipViewCenter(!showAtBottom);
            mTipTextView.setText(text);
            mViewRoot.clearAnimation();
            cancelAnimation(mAnimIn);
            if (!mViewRoot.isShown() || isAnimationOut) {
                isAnimationOut = false;
                mViewRoot.startAnimation(mAnimIn);
            }
            startFadeDismiss(ms);
            animationListener.fadeMS = ms;
        }
    }

    public void show(String text) {
        show(text, 100, false);
    }

    public void dismiss() {
        if (mViewRoot != null && mTipTextView != null && !isAnimationOut) {
            mViewRoot.removeCallbacks(mDismissAction);
            mViewRoot.postDelayed(mDismissAction, mAnimIn.getDuration() + 100);
        }
    }

    public void forceDismissImmediately() {
        if (mViewRoot != null) {
            mViewRoot.removeCallbacks(mDismissAction);
            mViewRoot.setVisibility(android.view.View.INVISIBLE);
        }
    }

    public void release() {
        if (mAnimIn != null) {
            cancelAnimation(mAnimIn);
            mAnimIn = null;
        }
        if (mAnimOut != null) {
            cancelAnimation(mAnimOut);
            mAnimOut = null;
        }
        if (mViewRoot != null && mParentView != null) {
            mViewRoot.removeCallbacks(dismissRunnable);
            mParentView.removeView(mViewRoot);
        }
    }
}
