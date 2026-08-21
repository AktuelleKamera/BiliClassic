package tv.biliclassic.util;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * 存储回退 Context：内部 /data/data/... 目录建不出来（分区满，如 G1）时，
 * getCacheDir()/getFilesDir() 自动落到 SD 卡的 BiliClassic/{cache,files}，
 * 避免返回 null 导致全应用 NPE 崩溃。
 *
 * 同时挂在 Application.attachBaseContext 和 BaseActivity.attachBaseContext，
 * 覆盖 Application 上下文与 Activity 上下文两类调用方。
 */
public class StorageFallbackContext extends ContextWrapper {

    private static final String TAG = "StorageFallback";

    public StorageFallbackContext(Context base) {
        super(base);
    }

    @Override
    public File getCacheDir() {
        try {
            File internal = super.getCacheDir();
            if (internal != null && internal.exists()) {
                return internal;
            }
        } catch (Throwable t) {
        }
        File sd = getSdSubDir("cache");
        if (sd != null) {
            return sd;
        }
        try {
            return super.getCacheDir();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public File getFilesDir() {
        try {
            File internal = super.getFilesDir();
            if (internal != null && internal.exists()) {
                return internal;
            }
        } catch (Throwable t) {
        }
        File sd = getSdSubDir("files");
        if (sd != null) {
            return sd;
        }
        try {
            return super.getFilesDir();
        } catch (Throwable t) {
            return null;
        }
    }

    private File getSdSubDir(String name) {
        try {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                File sd = new File(Environment.getExternalStorageDirectory(),
                        "BiliClassic/" + name);
                if (!sd.exists()) {
                    sd.mkdirs();
                }
                if (sd.exists()) {
                    Log.w(TAG, "内部 " + name + " 目录不可用，退回 SD 卡: " + sd.getAbsolutePath());
                    return sd;
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }
}
