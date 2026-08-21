package tv.biliclassic;

import android.app.Application;
import android.os.Handler;
import android.util.Log;

import java.io.File;

import tv.biliclassic.util.CrashHandler;
import tv.biliclassic.util.LocaleHelper;
import tv.biliclassic.util.SdkHelper;
import tv.biliclassic.util.QRCodeUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateCheckService;

public class BiliApplication extends Application {

    private tv.biliclassic.util.StorageFallbackContext mFallbackContext;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        // 注意：不能把这个 ContextWrapper 设为 base——framework（ActivityThread.handleReceiver）
        // 会强转 app.getBaseContext() 为 ContextImpl，包装过会让所有 manifest receiver 崩溃。
        // 这里保留原始 ContextImpl 给 framework，仅保留 wrapper 供 getCacheDir()/getFilesDir() 覆写用。
        super.attachBaseContext(base);
        mFallbackContext = new tv.biliclassic.util.StorageFallbackContext(base);
    }

    @Override
    public java.io.File getCacheDir() {
        return (mFallbackContext != null) ? mFallbackContext.getCacheDir() : super.getCacheDir();
    }

    @Override
    public java.io.File getFilesDir() {
        return (mFallbackContext != null) ? mFallbackContext.getFilesDir() : super.getFilesDir();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferencesUtil.init(this);
        LocaleHelper.init(this);
        if (SdkHelper.getSdkInt() < 17) {
            LocaleHelper.updateResourcesLocale(this);
        }
        CrashHandler.getInstance().init(this);
        QRCodeUtil.init(this);
        // 应用内日志（无 adb 用户排查用）：默认关闭，避免日志写入影响性能。
        // 开启后注入弹幕引擎诊断 sink，弹幕绘制耗时等写入日志文件。
        tv.biliclassic.util.LogFileUtil.init(this);
        master.flame.danmaku.util.DiagLogger.setSink(new master.flame.danmaku.util.DiagLogger.LogSink() {
            public void log(String tag, String msg) {
                tv.biliclassic.util.LogFileUtil.diag(tag, msg);
            }
        });

        // 延迟启动更新检查（5秒后，不影响启动速度）
        if (SharedPreferencesUtil.getBoolean("auto_check_update", true)) {
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    UpdateCheckService.schedule(BiliApplication.this);
                }
            }, 5000);
        }
    }
}