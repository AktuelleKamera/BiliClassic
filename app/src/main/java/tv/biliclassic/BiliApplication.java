package tv.biliclassic;

import android.app.Application;
import android.os.Handler;

import com.swetake.util.Qrcode;

import tv.biliclassic.util.CrashHandler;
import tv.biliclassic.util.QRCodeUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateCheckService;

public class BiliApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferencesUtil.init(this);
        CrashHandler.getInstance().init(this);
        Qrcode.init(this);
        QRCodeUtil.init(this);

        // 延迟启动更新检查（30秒后，不影响启动速度）
        if (SharedPreferencesUtil.getBoolean("auto_check_update", true)) {
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    UpdateCheckService.schedule(BiliApplication.this);
                }
            }, 30000);
        }
    }
}