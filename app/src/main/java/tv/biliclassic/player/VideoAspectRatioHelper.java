package tv.biliclassic.player;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.View;

public class VideoAspectRatioHelper {

    public static boolean isPortraitVideo(int videoWidth, int videoHeight) {
        return videoWidth > 0 && videoHeight > 0 && videoHeight > videoWidth;
    }

    /**
     * 检测到竖屏视频时自动将 Activity 切到竖屏方向，并适配竖屏下的手势。
     * 仅在首次检测时生效（firstTime == true）。
     */
    public static void autoRotateIfPortrait(Activity activity, int videoWidth, int videoHeight,
                                            boolean firstTime, View btnAspectRatio,
                                            GestureController gestureController,
                                            boolean enableGesture) {
        if (firstTime && isPortraitVideo(videoWidth, videoHeight)) {
            if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            if (btnAspectRatio != null) {
                btnAspectRatio.setVisibility(View.GONE);
            }
            if (gestureController != null && enableGesture) {
                gestureController.setEnableGesture(true);
                gestureController.onOrientationChanged();
            }
        }
    }

}
