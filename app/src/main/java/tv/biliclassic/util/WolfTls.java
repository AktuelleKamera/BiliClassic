package tv.biliclassic.util;

public final class WolfTls {
    private static Boolean sLoaded;

    private WolfTls() {
    }

    public static native long connect(String host, int port, int timeoutMs);

    public static native int read(long handle, byte[] buffer, int offset, int length);

    public static native int write(long handle, byte[] buffer, int offset, int length);

    public static native void close(long handle);

    public static boolean isSupportedEnvironment() {
        return SdkHelper.getSdkInt() < 21;
    }

    public static synchronized boolean isAvailable() {
        if (!isSupportedEnvironment()
                || !SharedPreferencesUtil.getBoolean("tls12_wolfssl", false)) {
            return false;
        }
        if (sLoaded == null) {
            boolean loaded = false;
            try {
                System.loadLibrary("wolfssl");
                loaded = true;
            } catch (Throwable ignored) {
            }
            sLoaded = Boolean.valueOf(loaded);
        }
        return sLoaded.booleanValue();
    }
}
