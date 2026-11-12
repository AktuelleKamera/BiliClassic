package tv.biliclassic.nfc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import tv.biliclassic.util.SdkHelper;

public final class NfcSyncCompat {

    private NfcSyncCompat() {
    }

    public static void setHceEnabled(Context context, boolean enabled) {
        if (context == null || SdkHelper.getSdkInt() < 19) return;
        try {
            Class<?> cls = Class.forName("tv.biliclassic.nfc.NfcSyncHceService");
            cls.getMethod("setEnabled", Context.class, boolean.class)
                    .invoke(null, context, Boolean.valueOf(enabled));
        } catch (Throwable t) {
        }
    }

    public static void startSender(Activity activity) {
        if (activity == null || SdkHelper.getSdkInt() < 29) return;
        try {
            Intent intent = new Intent();
            intent.setClassName(activity, "tv.biliclassic.nfc.NfcSyncSenderActivity");
            activity.startActivity(intent);
        } catch (Throwable t) {
        }
    }

    public static void attachReader(Activity activity) {
        if (activity == null || SdkHelper.getSdkInt() < 19) return;
        try {
            Class<?> cls = Class.forName("tv.biliclassic.nfc.NfcSyncReader");
            cls.getMethod("attach", Activity.class).invoke(null, activity);
        } catch (Throwable t) {
        }
    }

    public static void detachReader(Activity activity) {
        if (activity == null || SdkHelper.getSdkInt() < 19) return;
        try {
            Class<?> cls = Class.forName("tv.biliclassic.nfc.NfcSyncReader");
            cls.getMethod("detach", Activity.class).invoke(null, activity);
        } catch (Throwable t) {
        }
    }
}
