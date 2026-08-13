package tv.biliclassic;

import android.app.Application;
import android.os.Handler;

import tv.biliclassic.util.CrashHandler;
import tv.biliclassic.util.LocaleHelper;
import tv.biliclassic.util.SdkHelper;
import tv.biliclassic.util.QRCodeUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateCheckService;

public class BiliApplication extends Application {

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