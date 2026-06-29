package tv.biliclassic;

import android.app.Application;

import com.swetake.util.Qrcode;

import tv.biliclassic.util.CrashHandler;
import tv.biliclassic.util.SharedPreferencesUtil;

public class BiliApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferencesUtil.init(this);
        CrashHandler.getInstance().init(this);
        Qrcode.init(this);
    }
}