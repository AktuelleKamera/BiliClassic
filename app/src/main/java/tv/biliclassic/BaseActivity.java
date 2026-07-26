package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import tv.biliclassic.util.SharedPreferencesUtil;

public abstract class BaseActivity extends FragmentActivity {

    protected static final String KEY_LANDSCAPE_ENABLED = "landscape_enabled";

    // 全局 Context，供 Qrcode 等工具类使用
    private static Context appContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 防止有心人直接跳转到 BaseActivity
        if (getClass() == BaseActivity.class) {
            Toast.makeText(this, "无法直接打开此页面", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 保存全局 Context
        if (appContext == null) {
            appContext = getApplicationContext();
        }

        // 设置横屏
        if (shouldEnableLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        super.onCreate(savedInstanceState);
    }

    /**
     * 获取全局 Context（供工具类使用）
     */
    public static Context getAppContext() {
        return appContext;
    }

    /**
     * 是否应该开启横屏模式？
     */
    protected boolean shouldEnableLandscape() {
        boolean landscapeEnabled = SharedPreferencesUtil.getBoolean(KEY_LANDSCAPE_ENABLED, true);
        if (!landscapeEnabled) {
            return false;
        }
        return isLandscapeDevice();
    }

    /**
     * 检测是否为横屏设备
     */
    protected boolean isLandscapeDevice() {
        String model = Build.MODEL;
        String device = Build.DEVICE;
        String manufacturer = Build.MANUFACTURER;
        String product = Build.PRODUCT;

        // HTC ChaCha 系列
        if ("HTC".equalsIgnoreCase(manufacturer)) {
            if ("A810e".equalsIgnoreCase(model) ||
                    "A810".equalsIgnoreCase(model) ||
                    "ChaCha".equalsIgnoreCase(model) ||
                    "Status".equalsIgnoreCase(model) ||
                    "PB86100".equalsIgnoreCase(model)) {
                return true;
            }
        }

        // 三星 Galaxy Y Pro / Galaxy Pro
        if ("samsung".equalsIgnoreCase(manufacturer)) {
            if ("GT-B5510".equalsIgnoreCase(model) ||
                    "GT-B5510L".equalsIgnoreCase(model) ||
                    "GT-B5510B".equalsIgnoreCase(model) ||
                    "GT-B7510".equalsIgnoreCase(model)) {
                return true;
            }
        }

        // device 名称检测
        if ("chacha".equalsIgnoreCase(device) ||
                "htc_chacha".equalsIgnoreCase(device) ||
                "b5510".equalsIgnoreCase(device) ||
                "b7510".equalsIgnoreCase(device)) {
            return true;
        }

        // 索尼A5100
        if ("ScalarA".equalsIgnoreCase(model) ||
                "ScalarA".equalsIgnoreCase(product) ||
                "dslr-diadem".equalsIgnoreCase(device)) {
            return true;
        }

        return false;
    }

    /**
     * 物理搜索键按下时跳转到搜索页面
     */
    @Override
    public boolean onSearchRequested() {
        // 如果当前已经是搜索页面，不再打开新的
        if (this instanceof SearchActivity) {
            return true;
        }
        startActivity(new Intent(this, SearchActivity.class));
        return true;
    }
}