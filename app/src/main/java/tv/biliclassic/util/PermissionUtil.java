package tv.biliclassic.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

public class PermissionUtil {

    public static final int REQUEST_WRITE_STORAGE = 1000;

    public static boolean hasWriteStorage(Context context) {
        if (SdkHelper.getSdkInt() >= 23) {
            try {
                Method checkMethod = Context.class.getMethod("checkSelfPermission", String.class);
                int result = (Integer) checkMethod.invoke(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return result == PackageManager.PERMISSION_GRANTED;
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    public static void requestWriteStorage(Activity activity) {
        requestWriteStorage(activity, REQUEST_WRITE_STORAGE);
    }

    public static void requestWriteStorage(Activity activity, int requestCode) {
        if (SdkHelper.getSdkInt() >= 23) {
            try {
                Method requestMethod = Activity.class.getMethod("requestPermissions", String[].class, int.class);
                requestMethod.invoke(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
            } catch (Exception e) {
            }
        }
    }
}
