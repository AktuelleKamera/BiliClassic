
package master.flame.danmaku.danmaku.util;

import android.content.Context;

import java.lang.reflect.Method;

public class AndroidUtils {

    public static int getMemoryClass(final Context context) {
        try {
            Object am = context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return 16;
            }
            Class<?> cls = am.getClass();
            try {
                Method method = cls.getMethod("getMemoryClass");
                return ((Integer) method.invoke(am)).intValue();
            } catch (NoSuchMethodException e) {
                return 16;
            }
        } catch (Exception e) {
            return 16;
        }
    }
}
