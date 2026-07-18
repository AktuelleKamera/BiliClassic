package tv.biliclassic.util;

public class SdkHelper {
    private SdkHelper() {}

    public static int getSdkInt() {
        try {
            return android.os.Build.VERSION.class.getField("SDK_INT").getInt(null);
        } catch (Exception e) {
            try {
                return Integer.parseInt(android.os.Build.VERSION.SDK);
            } catch (Exception ex) {
                return 0;
            }
        }
    }
}
