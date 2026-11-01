package tv.biliclassic.util;

import android.os.Handler;
import android.os.Looper;

import tv.biliclassic.api.DanmakuFilterApi;
import tv.biliclassic.api.UserInfoApi;

public final class UserBlockHelper {
    public interface Callback {
        void onResult(boolean success);
    }

    private static final Handler sMain = new Handler(Looper.getMainLooper());

    private UserBlockHelper() {
    }

    public static void block(final long mid, final Callback callback) {
        block(mid, false, callback);
    }

    public static void block(final long mid, final boolean alsoDanmaku, final Callback callback) {
        setBlocked(mid, true, alsoDanmaku, callback);
    }

    public static void unblock(final long mid, final Callback callback) {
        setBlocked(mid, false, false, callback);
    }

    public static void setBlocked(final long mid, final boolean blocked,
                                  final boolean alsoDanmaku, final Callback callback) {
        if (mid == 0) {
            if (callback != null) callback.onResult(false);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    success = UserInfoApi.blockUser(mid, blocked) == 0;
                    if (success && blocked && alsoDanmaku) {
                        DanmakuFilterApi.addUserFilter(mid);
                    }
                } catch (Throwable ignored) {
                }
                final boolean result = success;
                sMain.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onResult(result);
                    }
                });
            }
        }).start();
    }
}
