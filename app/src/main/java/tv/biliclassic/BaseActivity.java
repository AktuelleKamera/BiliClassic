package tv.biliclassic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import tv.biliclassic.util.DeviceUtil;
import tv.biliclassic.util.LocaleHelper;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SdkHelper;
import tv.biliclassic.util.SharedPreferencesUtil;

public abstract class BaseActivity extends FragmentActivity {

    protected static final String KEY_LANDSCAPE_ENABLED = "landscape_enabled";

    // 全局 Context，供 Qrcode 等工具类使用
    private static Context appContext;

    // 运行时权限：存储权限回调
    private Runnable mPendingStorageAction;

    @Override
    protected void attachBaseContext(Context newBase) {
        // 先包存储回退（内部目录满时退 SD 卡），再包 Locale
        Context wrapped = new tv.biliclassic.util.StorageFallbackContext(newBase);
        if (SdkHelper.getSdkInt() >= 17) {
            super.attachBaseContext(LocaleHelper.wrapContext(wrapped));
        } else {
            super.attachBaseContext(wrapped);
        }
    }

    @Override
    public Resources getResources() {
        if (SdkHelper.getSdkInt() >= 17) {
            return super.getResources();
        }
        Resources res = super.getResources();
        Configuration config = res.getConfiguration();
        config.locale = LocaleHelper.getLocale();
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 夜间模式：替换窗口背景纹理（bili_texture_1 -> bili_texture_2）
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NIGHT_MODE, false)) {
            setTheme(R.style.AppThemeNight);
        }

        // 防止有心人直接跳转到 BaseActivity
        if (getClass() == BaseActivity.class) {
            Toast.makeText(this, this.getString(R.string.baseactivity_toast_65e0), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 保存全局 Context
        if (appContext == null) {
            appContext = getApplicationContext();
        }

        // 屏幕方向设置
        // TV 模式：横屏
        if (DeviceUtil.isTv(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            // 判断是否为平板
            boolean isTablet = SdkHelper.getBooleanResource(getResources(), R.bool.is_tablet);
            if (isTablet) {
                // 平板：自动旋转（横竖屏都可）
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            } else if (shouldEnableLandscape()) {
                // 横屏设备（如 ChaCha 等）：横屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else if (isHardwareKeyboardDevice()) {
                // 带滑出式物理键盘的手机（HTC Dream 等）：不锁定方向
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } else {
                // 手机：强制竖屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        // 透明状态栏：API 21+ 状态栏透明，API 23+ 深色图标
        if (SdkHelper.getSdkInt() >= 21) {
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

        // 布局延伸到系统栏
        applyEdgeToEdge();
    }

    /**
     * 让应用内容绘制到系统栏之下（edge-to-edge）。
     * 不设置 LAYOUT 标志、也不加顶部 padding，避免多出一截状态栏空白。
     * 启用时：底部延伸到手势/导航区（LAYOUT_HIDE_NAVIGATION），并把内容顶部下移状态栏高度，
     * 顶部标题不会被状态栏遮挡；API 21+ 导航栏透明。
     */
    protected void applyEdgeToEdge() {
        if (SdkHelper.getSdkInt() < 28) return; // 仅手势导航设备启用
        try {
            int flags = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            android.view.View.class.getMethod("setSystemUiVisibility", int.class)
                    .invoke(getWindow().getDecorView(), Integer.valueOf(flags));
        } catch (Throwable t) {
        }
        if (SdkHelper.getSdkInt() >= 21) {
            try {
                android.view.Window.class.getMethod("setNavigationBarColor", int.class)
                        .invoke(getWindow(), Integer.valueOf(0));
            } catch (Throwable t) {
            }
        }
        // 状态栏会叠在内容上：把内容顶部下移状态栏高度，标题才不会被挡住（底部保持延伸到手势区）
        final android.view.View content = getWindow().getDecorView().findViewById(android.R.id.content);
        if (content != null) {
            content.setPadding(0, getStatusBarHeight(), 0, 0);
        } else {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    android.view.View c = getWindow().getDecorView().findViewById(android.R.id.content);
                    if (c != null) c.setPadding(0, getStatusBarHeight(), 0, 0);
                }
            });
        }
    }

    /** 状态栏高度（px）；读取失败回退 24dp。 */
    private int getStatusBarHeight() {
        try {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) {
                return getResources().getDimensionPixelSize(id);
            }
        } catch (Throwable t) {
        }
        return Math.round(getResources().getDisplayMetrics().density * 24f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 夜间模式：实时切换窗口背景纹理（返回已打开的界面时也生效）
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NIGHT_MODE, false)) {
            getWindow().setBackgroundDrawableResource(R.drawable.bili_texture_background_night);
        } else {
            getWindow().setBackgroundDrawableResource(R.drawable.bili_texture_background);
        }
    }

    /**
     * 获取全局 Context（供工具类使用）
     */
    public static Context getAppContext() {
        return appContext;
    }

    /**
     * 圆形屏幕（手表）适配：隐藏返回按钮，标题居中并点击返回。
     * 在子类 setContentView 之后调用；内部自动判断圆屏，非圆屏无副作用。
     * 除 VideoDetailActivity（有独立按键导航布局）外的带返回栏页面使用。
     */
    protected void initRoundTitleBar() {
        final View root = findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        root.post(new Runnable() {
            @Override
            public void run() {
                if (tv.biliclassic.util.DeviceUtil.isRoundScreen(root)) {
                    applyRoundTitleBar();
                }
            }
        });
    }

    /**
     * 圆屏：隐藏标题栏返回按钮，标题居中且点击返回。
     */
    private void applyRoundTitleBar() {
        try {
            final View back = findViewById(R.id.btn_back);
            if (back == null) {
                return;
            }
            final ViewGroup titleBar = (ViewGroup) back.getParent();
            if (titleBar == null) {
                return;
            }
            // 隐藏返回按钮
            back.setVisibility(View.GONE);

            // 标题居中
            View title = null;
            int n = titleBar.getChildCount();
            for (int i = 0; i < n; i++) {
                View v = titleBar.getChildAt(i);
                if (v == back || v.getVisibility() == View.GONE) {
                    continue;
                }
                if (v instanceof TextView) {
                    title = v;
                    break;
                }
            }
            if (title != null) {
                android.widget.RelativeLayout.LayoutParams tlp =
                        (android.widget.RelativeLayout.LayoutParams) title.getLayoutParams();
                tlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT, 0);
                tlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT, 0);
                tlp.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
                title.setLayoutParams(tlp);
                title.setClickable(true);
                title.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
        } catch (Throwable t) {
        }
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
                Toast.makeText(this, this.getString(R.string.baseactivity_toast_9700), Toast.LENGTH_SHORT).show();
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
        String model = android.os.Build.MODEL;
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
     * 检测是否带滑出式（或固定式）物理 QWERTY 键盘的手机，如 HTC Dream/G1。
     * 这类设备在键盘滑出时系统会自动切横屏，前提是应用不锁定竖屏。
     * 直接读运行时硬件键盘配置：存在物理 QWERTY 键盘即视为需要自动旋转。
     */
    protected boolean isHardwareKeyboardDevice() {
        try {
            Configuration cfg = getResources().getConfiguration();
            return cfg.keyboard == Configuration.KEYBOARD_QWERTY;
        } catch (Throwable t) {
            return false;
        }
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
            return (String) android.os.Build.class.getField("MANUFACTURER").get(null);
        } catch (Exception e) {
            return "";
        }
    }

    private static String getBuildField(String name) {
        try { return (String) android.os.Build.class.getField(name).get(null); }
        catch (Exception e) { return ""; }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        tv.biliclassic.util.GlobalImageCache.getInstance().clear();
    }
}