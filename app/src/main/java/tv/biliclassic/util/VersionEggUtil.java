package tv.biliclassic.util;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

/**
 * 关于页版本号连点彩蛋：短时间内连点若干次触发星战字幕动画。
 */
public final class VersionEggUtil {

    private static final int NEED = 5;
    private static final long RESET_MS = 1200L;

    private static int sCount = 0;
    private static long sLast = 0L;

    private VersionEggUtil() {
    }

    public static void attach(final Activity activity, View target) {
        if (activity == null || target == null) {
            return;
        }
        target.setClickable(true);
        target.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - sLast > RESET_MS) {
                    sCount = 0;
                }
                sLast = now;
                sCount++;
                if (sCount >= NEED) {
                    sCount = 0;
                    activity.startActivity(new Intent(activity,
                            tv.biliclassic.EasterEggActivity.class));
                }
            }
        });
    }
}
