package tv.biliclassic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.WindowManager;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import tv.biliclassic.tv.util.TvUtil;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public abstract class BaseActivity extends FragmentActivity {

    protected static final String KEY_LANDSCAPE_ENABLED = "landscape_enabled";

    // 全局 Context，供 Qrcode 等工具类使用
    private static Context appContext;

    // 运行时权限：存储权限回调
    private Runnable mPendingStorageAction;

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

        // 屏幕方向设置
        // TV 模式：横屏
        if (TvUtil.isTv(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            // 判断是否为平板
            boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
            if (isTablet) {
                // 平板：自动旋转（横竖屏都可）
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            } else if (shouldEnableLandscape()) {
                // 横屏设备（如 ChaCha 等）：横屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                // 手机：强制竖屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        // 透明状态栏：API 21+ 状态栏透明，API 23+ 深色图标
        if (getSdkInt() >= 21) {
            try {
                android.view.Window window = getWindow();
                java.lang.reflect.Method addFlags = android.view.Window.class.getMethod("addFlags", int.class);
                java.lang.reflect.Field drawsBarBg = android.view.WindowManager.LayoutParams.class.getField("FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS");
                addFlags.invoke(window, drawsBarBg.getInt(null));
                java.lang.reflect.Method setColor = android.view.Window.class.getMethod("setStatusBarColor", int.class);
                setColor.invoke(window, 0x33000000);
            } catch (Exception e) {
            }
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
     * 检查并请求 WRITE_EXTERNAL_STORAGE 权限
     * 如果已有权限则立即执行 action，否则请求权限后执行
     */
    protected void runWithStoragePermission(Runnable action) {
        if (PermissionUtil.hasWriteStorage(this)) {
            action.run();
        } else {
            mPendingStorageAction = action;
            PermissionUtil.requestWriteStorage(this);
        }
    }

    /**
     * 运行时权限结果回调（Android 6.0+）
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PermissionUtil.REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mPendingStorageAction != null) {
                    mPendingStorageAction.run();
                    mPendingStorageAction = null;
                }
            } else {
                Toast.makeText(this, "需要存储权限才能使用此功能", Toast.LENGTH_SHORT).show();
                mPendingStorageAction = null;
            }
        }
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
        String device = getBuildField("DEVICE");
        String manufacturer = getManufacturer();
        String product = getBuildField("PRODUCT");

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

        // RK2818 CM7
        if ("Rockchip".equalsIgnoreCase(manufacturer) ||
                "rk2818".equalsIgnoreCase(device)) {
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

    private static String getManufacturer() {
        try {
            return (String) Build.class.getField("MANUFACTURER").get(null);
        } catch (Exception e) {
            return "";
        }
    }

    private static String getBuildField(String name) {
        try { return (String) Build.class.getField(name).get(null); }
        catch (Exception e) { return ""; }
    }

    private static int getSdkInt() {
        try {
            return Build.VERSION.class.getField("SDK_INT").getInt(null);
        } catch (Exception e) {
            try {
                return Integer.parseInt(Build.VERSION.SDK);
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        tv.biliclassic.util.GlobalImageCache.getInstance().clear();
    }
}