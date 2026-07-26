package tv.biliclassic.util;

import android.view.View;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import java.util.ArrayList;

public class ViewPagerShim {
    public static void addFocusables(View view, ArrayList<View> views, int direction, int focusableMode) {
        try {
            View.class.getMethod("addFocusables", ArrayList.class, int.class, int.class)
                .invoke(view, views, direction, focusableMode);
        } catch (Exception e) {
            view.addFocusables(views, direction);
        }
    }

    public static void computeCurrentVelocity(VelocityTracker tracker, int units, float maxVelocity) {
        try {
            VelocityTracker.class.getMethod("computeCurrentVelocity", int.class, float.class)
                .invoke(tracker, units, maxVelocity);
        } catch (Exception e) {
            tracker.computeCurrentVelocity(units);
        }
    }

    public static int getScaledMaximumFlingVelocity(ViewConfiguration config) {
        try {
            return (Integer) ViewConfiguration.class.getMethod("getScaledMaximumFlingVelocity").invoke(config);
        } catch (Exception e) {
            return 4000;
        }
    }
}
