package tv.biliclassic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
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

    // 整块高亮标记
    private static final String PRESS_TAG = "title_press_region";

    // 右上动作按钮标记
    private static final String ACTION_TAG = "title_actions";

    // 溢出菜单弹窗
    private PopupWindow mOverflow;

    // 无 id 标题的临时 id
    private static int sDynId = 0x00FFFF00;

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
            Toast.makeText(this, this.getString(R.string.cannot_open_page), Toast.LENGTH_SHORT).show();
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

        // 虚拟三键导航会盖住页面底部
        applyNavBarPadding();
    }

    /**
     * 表冠旋钮滚动：内容就绪后在 DecorView 挂泛动作监听（API<12 时内部自动跳过）。
     * 未被子视图消费的 ACTION_SCROLL 会落到这里，滚动页面上最大可见的可滚动容器。
     */
    @Override
    public void onContentChanged() {
        super.onContentChanged();
        tv.biliclassic.util.CrownScrollHelper.attachToWindow(this);
        wireTitleActions();
        wireTitlePress();
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

    private android.view.ViewTreeObserver.OnGlobalLayoutListener mNavBarPadListener;

    /**
     * 底部被虚拟导航键盖住时给内容补 padding。
     * 窗口实际底高于系统可见帧的差值就是被盖的部分，全屏页差值为 0 不受影响。
     */
    protected void applyNavBarPadding() {
        if (mNavBarPadListener != null) {
            return;
        }
        final android.view.View decor = getWindow().getDecorView();
        mNavBarPadListener = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    android.graphics.Rect r = new android.graphics.Rect();
                    decor.getWindowVisibleDisplayFrame(r);
                    int diff = decor.getHeight() - r.bottom;
                    android.view.View c = decor.findViewById(android.R.id.content);
                    if (c != null && diff > 0 && c.getPaddingBottom() != diff) {
                        c.setPadding(c.getPaddingLeft(), c.getPaddingTop(), c.getPaddingRight(), diff);
                    }
                } catch (Throwable t) {
                }
            }
        };
        decor.getViewTreeObserver().addOnGlobalLayoutListener(mNavBarPadListener);
    }

    @Override
    protected void onDestroy() {
        if (mNavBarPadListener != null) {
            try {
                getWindow().getDecorView().getViewTreeObserver()
                        .removeGlobalOnLayoutListener(mNavBarPadListener);
            } catch (Throwable t) {
            }
            mNavBarPadListener = null;
        }
        super.onDestroy();
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
        wireTitleActions();
        wireTitlePress();
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
     * 二级页是否加右上搜索与溢出按钮
     */
    protected boolean hasTitleActions() {
        return true;
    }

    /**
     * 标题栏右上角加搜索与溢出两枚按钮
     */
    private void wireTitleActions() {
        try {
            if (!hasTitleActions()) {
                return;
            }
            View back = findViewById(R.id.btn_back);
            if (back == null) {
                View more = findViewById(R.id.btn_title_more);
                if (more != null) {
                    more.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showOverflowMenu(v);
                        }
                    });
                }
                return;
            }
            View parent = (View) back.getParent();
            if (!(parent instanceof android.widget.RelativeLayout)) {
                return;
            }
            android.widget.RelativeLayout bar =
                    (android.widget.RelativeLayout) parent;
            if (bar.findViewWithTag(ACTION_TAG) != null) {
                return;
            }
            int btn = (int) (48 * bar.getResources()
                    .getDisplayMetrics().density + 0.5f);

            ImageView more = new ImageView(bar.getContext());
            android.widget.RelativeLayout.LayoutParams mlp =
                    new android.widget.RelativeLayout.LayoutParams(btn, btn);
            mlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
            mlp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
            more.setId(R.id.btn_title_more);
            more.setLayoutParams(mlp);
            more.setTag(ACTION_TAG);
            more.setBackgroundResource(R.drawable.titlebar_pink_item_bg);
            more.setImageResource(
                    R.drawable.abs__ic_menu_moreoverflow_holo_light);
            more.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showOverflowMenu(v);
                }
            });
            bar.addView(more);

            ImageView search = new ImageView(bar.getContext());
            android.widget.RelativeLayout.LayoutParams slp =
                    new android.widget.RelativeLayout.LayoutParams(btn, btn);
            slp.addRule(android.widget.RelativeLayout.LEFT_OF,
                    R.id.btn_title_more);
            slp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
            search.setId(R.id.btn_title_search);
            search.setLayoutParams(slp);
            search.setBackgroundResource(R.drawable.titlebar_pink_item_bg);
            search.setImageResource(R.drawable.abs__ic_search_api_holo_light);
            search.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            search.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(
                            new Intent(BaseActivity.this, SearchActivity.class));
                }
            });
            bar.addView(search);

            View logo = findViewById(R.id.logo_container);
            View title = findTitleText(bar, back, logo);
            if (title != null) {
                android.widget.RelativeLayout.LayoutParams tlp =
                        (android.widget.RelativeLayout.LayoutParams)
                                title.getLayoutParams();
                tlp.addRule(
                        android.widget.RelativeLayout.ALIGN_PARENT_RIGHT, 0);
                tlp.addRule(android.widget.RelativeLayout.LEFT_OF,
                        R.id.btn_title_search);
                title.setLayoutParams(tlp);
            }
        } catch (Throwable t) {
        }
    }

    /**
     * 右上溢出菜单弹出，样式对齐原版 Holo 弹层
     */
    protected void showOverflowMenu(View anchor) {
        try {
            XmlResourceParser xp = getResources().getXml(R.menu.main_menu);
            String ns = "http://schemas.android.com/apk/res/android";
            int[] ids = new int[6];
            String[] titles = new String[6];
            int n = 0;
            int event = xp.getEventType();
            while (event != XmlResourceParser.END_DOCUMENT && n < 6) {
                if (event == XmlResourceParser.START_TAG
                        && "item".equals(xp.getName())) {
                    int mid = xp.getAttributeResourceValue(ns, "id", 0);
                    String title = xp.getAttributeValue(ns, "title");
                    if (title != null && title.length() > 0
                            && title.charAt(0) == '@') {
                        int tid = xp.getAttributeResourceValue(
                                ns, "title", 0);
                        if (tid != 0) {
                            title = getString(tid);
                        }
                    }
                    if (mid != 0 && title != null && title.length() > 0) {
                        ids[n] = mid;
                        titles[n] = title;
                        n++;
                    }
                }
                event = xp.next();
            }
            xp.close();
            if (n == 0) {
                return;
            }

            if (mOverflow != null && mOverflow.isShowing()) {
                mOverflow.dismiss();
            }

            float dens = getResources().getDisplayMetrics().density;
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int rowH = (int) (48 * dens + 0.5f);
            int minW = (int) (196 * dens + 0.5f);
            int pad = (int) (16 * dens + 0.5f);
            int divH = (int) (1 * dens + 0.5f);

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setMinimumWidth(minW);

            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    View div = new View(this);
                    div.setBackgroundDrawable(getResources().getDrawable(
                            R.drawable.abs__list_divider_holo_light));
                    panel.addView(div, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, divH));
                }
                final int itemId = ids[i];
                TextView row = new TextView(this);
                row.setText(titles[i]);
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                row.setTextColor(0xFF000000);
                row.setSingleLine(true);
                row.setEllipsize(TextUtils.TruncateAt.END);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinWidth(minW);
                row.setPadding(pad, 0, pad, 0);
                row.setClickable(true);
                row.setFocusable(true);
                row.setBackgroundResource(
                        R.drawable.apptheme__app__list_selector_holo_light);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mOverflow != null) {
                            mOverflow.dismiss();
                        }
                        onMenuAction(itemId);
                    }
                });
                panel.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, rowH));
            }

            ScrollView scroll = new ScrollView(this);
            scroll.setBackgroundResource(
                    R.drawable.abs__menu_dropdown_panel_holo_light);
            scroll.addView(panel, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.WRAP_CONTENT,
                    ScrollView.LayoutParams.WRAP_CONTENT));

            // 弹窗宽度同原版
            panel.measure(
                    View.MeasureSpec.makeMeasureSpec(0,
                            View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0,
                            View.MeasureSpec.UNSPECIFIED));
            int maxW = (int) (320 * dens + 0.5f);
            if (screenW / 2 > maxW) {
                maxW = screenW / 2;
            }
            int contentW = panel.getMeasuredWidth();
            if (contentW > maxW) {
                contentW = maxW;
            }
            android.graphics.Rect bgRect = new android.graphics.Rect();
            getResources().getDrawable(
                    R.drawable.abs__menu_dropdown_panel_holo_light)
                    .getPadding(bgRect);

            mOverflow = new PopupWindow(scroll,
                    bgRect.left + bgRect.right + contentW,
                    ScrollView.LayoutParams.WRAP_CONTENT, true);
            mOverflow.setBackgroundDrawable(new ColorDrawable(0x00000000));
            mOverflow.setOutsideTouchable(true);
            mOverflow.showAsDropDown(anchor);
        } catch (Throwable t) {
        }
    }

    /**
     * 溢出菜单项动作，与 main_menu 对应
     */
    protected void onMenuAction(int id) {
        if (id == R.id.menu_login_logout) {
            long mid = SharedPreferencesUtil.getLong(
                    SharedPreferencesUtil.mid, 0);
            if (mid != 0) {
                showMenuLogoutDialog();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        } else if (id == R.id.menu_favorite_list) {
            startActivity(new Intent(this, FavoriteFolderListActivity.class));
        } else if (id == R.id.menu_video_history_list) {
            startActivity(new Intent(this, HistoryActivity.class));
        } else if (id == R.id.menu_preferences) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.menu_help) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (id == R.id.menu_exit) {
            finish();
        }
    }

    /**
     * 注销二次确认
     */
    protected void showMenuLogoutDialog() {
        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(
                        tv.biliclassic.util.SdkHelper.dialogContext(
                                tv.biliclassic.util.DialogUtil.wrap(this)));
        builder.setTitle(getString(R.string.really_leave_title));
        builder.setMessage(getString(R.string.logout_confirm_message));
        builder.setPositiveButton("留下来",
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog,
                            int which) {
                        Toast.makeText(BaseActivity.this, getString(
                                R.string.stay_message),
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                });
        builder.setNegativeButton("狠心离开",
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog,
                            int which) {
                        doMenuLogout();
                        dialog.dismiss();
                    }
                });
        builder.setCancelable(true);
        builder.show();
    }

    /**
     * 清除登录态并重启当前页
     */
    protected void doMenuLogout() {
        SharedPreferencesUtil.removeValue("cookies");
        SharedPreferencesUtil.removeValue("mid");
        SharedPreferencesUtil.removeValue("csrf");
        SharedPreferencesUtil.removeValue("refresh_token");
        Toast.makeText(this, getString(R.string.logged_out_message),
                Toast.LENGTH_SHORT).show();
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    /**
     * 标题栏按压联动：返回键、图标、标题共用一块高亮，点标题等同点返回
     */
    private void wireTitlePress() {
        try {
            View tmp = findViewById(R.id.btn_back);
            if (tmp == null) {
                tmp = findViewById(R.id.back);
            }
            if (tmp == null) {
                return;
            }
            final View back = tmp;
            View logo = findViewById(R.id.logo_container);
            android.widget.RelativeLayout bar = null;
            View title = null;
            View parent = (View) back.getParent();
            if (parent instanceof android.widget.RelativeLayout) {
                bar = (android.widget.RelativeLayout) parent;
                title = findTitleText(bar, back, logo);
            }

            // 整块高亮：从返回键一直亮到「标题文字末尾」，
            // 中间的 app 图标天然被包在里面（返回+图标+文字 一起亮）。
            // 不给各控件单独设背景，避免叠加出一条被拉长的多余高亮。
            if (logo != null) {
                logo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
            if (title != null) {
                title.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        back.performClick();
                    }
                });
            }

            View region = null;
            if (bar != null) {
                region = ensurePressRegion(bar, back, title, logo);
            }

            if (region != null) {
                // 清掉各控件自带的静态粉色背景，否则被撑满的标题
                // 会再亮出一条超长高亮，和整块区域叠成两层
                clearPressBackground(back);
                clearPressBackground(logo);
                clearPressBackground(title);
                syncPress(back, new View[]{region});
                if (logo != null && logo != region) {
                    syncPress(logo, new View[]{region});
                }
                if (title != null && title != region) {
                    syncPress(title, new View[]{region});
                }
            } else {
                // 兜底：拿不到标题栏时只亮返回键本身
                back.setBackgroundResource(R.drawable.titlebar_pink_item_bg);
            }
        } catch (Throwable t) {
        }
    }

    /**
     * 覆盖「返回键 -> 标题文字末尾」的整块高亮，图标被包在其中。
     * 垫在最下层，不与任何控件自身的高亮叠加。
     */
    private View ensurePressRegion(android.widget.RelativeLayout bar,
            final View back, final View title, final View logo) {
        if (bar == null) {
            return null;
        }
        View old = bar.findViewWithTag(PRESS_TAG);
        if (old != null) {
            return old;
        }
        // 右边界优先取标题文字；没有标题再退到图标、返回键
        View pick = title;
        if (pick == null) {
            pick = logo;
        }
        if (pick == null) {
            pick = back;
        }
        if (pick == null) {
            return null;
        }
        final View anchor = pick;
        final boolean clampText = anchor instanceof android.widget.TextView;
        if (anchor.getId() <= 0) {
            anchor.setId(sDynId--);
        }
        final View region = new View(bar.getContext());
        region.setTag(PRESS_TAG);
        android.widget.RelativeLayout.LayoutParams lp =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                        android.widget.RelativeLayout.LayoutParams.MATCH_PARENT);
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(android.widget.RelativeLayout.ALIGN_RIGHT, anchor.getId());
        region.setLayoutParams(lp);
        region.setBackgroundResource(R.drawable.titlebar_pink_item_bg);
        region.setClickable(true);
        region.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!back.performClick()) {
                    finish();
                }
            }
        });
        bar.addView(region, 0);
        // 布局完成后按「实际右缘」定宽
        anchor.post(new Runnable() {
            @Override
            public void run() {
                int right = anchor.getRight();
                // 锚点是标题文字时只亮到文字实宽，
                // 免得标题被撑满 back->search 后高亮一直顶到右上按钮
                if (clampText && anchor instanceof android.widget.TextView) {
                    android.widget.TextView tv = (android.widget.TextView) anchor;
                    CharSequence text = tv.getText();
                    float textW = tv.getPaint().measureText(
                            text == null ? "" : text.toString());
                    int textRight = tv.getLeft() + tv.getCompoundPaddingLeft()
                            + (int) Math.ceil((double) textW)
                            + tv.getCompoundPaddingRight();
                    if (textRight > 0 && textRight < right) {
                        right = textRight;
                    }
                }
                if (right <= 0) {
                    return;
                }
                android.view.ViewGroup.LayoutParams rlp = region.getLayoutParams();
                if (rlp == null) {
                    return;
                }
                // 关键：区域同时挂了「贴左 + 贴右」两个锚点，
                // RelativeLayout 会无视 width 直接铺满整条，必须先解除右锚点
                if (rlp instanceof android.widget.RelativeLayout.LayoutParams) {
                    ((android.widget.RelativeLayout.LayoutParams) rlp)
                            .addRule(android.widget.RelativeLayout.ALIGN_RIGHT, 0);
                }
                if (rlp.width == right) {
                    return;
                }
                rlp.width = right;
                region.setLayoutParams(rlp);
            }
        });
        return region;
    }

    /**
     * 去掉控件自带的按压背景，统一交给整块区域高亮
     */
    private static void clearPressBackground(View v) {
        if (v == null) {
            return;
        }
        try {
            v.setBackgroundDrawable(null);
        } catch (Throwable t) {
        }
    }

    /**
     * 标题栏里紧挨返回键或图标的标题文字
     */
    private static View findTitleText(android.widget.RelativeLayout bar, View back, View logo) {
        int backId = back == null ? 0 : back.getId();
        int logoId = logo == null ? 0 : logo.getId();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View c = bar.getChildAt(i);
            if (!(c instanceof android.widget.TextView)
                    || c instanceof android.widget.EditText) {
                continue;
            }
            if (c == back || c == logo) {
                continue;
            }
            try {
                android.widget.RelativeLayout.LayoutParams lp =
                        (android.widget.RelativeLayout.LayoutParams) c.getLayoutParams();
                int rightOf = lp.getRules()[android.widget.RelativeLayout.RIGHT_OF];
                if ((backId != 0 && rightOf == backId)
                        || (logoId != 0 && rightOf == logoId)) {
                    return c;
                }
            } catch (Throwable t) {
            }
        }
        return null;
    }

    /**
     * 按压状态同步到同组其余块，松手只清对方（本侧留给 onTouchEvent 触发点击）
     */
    private static void syncPress(final View self, final View[] others) {
        if (!self.isClickable()) {
            self.setClickable(true);
        }
        self.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    v.setPressed(true);
                    for (int i = 0; i < others.length; i++) {
                        others[i].setPressed(true);
                    }
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    for (int i = 0; i < others.length; i++) {
                        others[i].setPressed(false);
                    }
                    if (action == MotionEvent.ACTION_CANCEL || !v.isClickable()) {
                        v.setPressed(false);
                    }
                } else {
                    boolean inside = pointInside(v, e);
                    v.setPressed(inside);
                    for (int i = 0; i < others.length; i++) {
                        others[i].setPressed(inside);
                    }
                }
                return false;
            }
        });
    }

    /**
     * 触点在自身内
     */
    private static boolean pointInside(View v, MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        return x >= 0 && y >= 0 && x < v.getWidth() && y < v.getHeight();
    }

    /**
     * 圆屏：隐藏标题栏返回按钮与图标，只留标题文字居中且点击返回。
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
            // 整块高亮随之隐藏
            View region = titleBar.findViewWithTag(PRESS_TAG);
            if (region != null) {
                region.setVisibility(View.GONE);
            }

            // 圆屏只留文字：隐藏其余图标（含包着图标的容器）
            int m = titleBar.getChildCount();
            for (int i = 0; i < m; i++) {
                View c = titleBar.getChildAt(i);
                if (c == back) {
                    continue;
                }
                if (c instanceof android.widget.ImageView) {
                    c.setVisibility(View.GONE);
                } else if (c instanceof ViewGroup && hasImageView((ViewGroup) c)) {
                    c.setVisibility(View.GONE);
                }
            }

            // 标题居中（新建参数，避免沿用带 toRightOf 的旧规则）
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
                        new android.widget.RelativeLayout.LayoutParams(
                                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
                tlp.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
                title.setLayoutParams(tlp);
                title.setClickable(true);
                // 整块已隐藏，改回标题自高亮
                title.setBackgroundResource(R.drawable.titlebar_pink_item_bg);
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
     * 子树里是否有 ImageView
     */
    private boolean hasImageView(ViewGroup vg) {
        int n = vg.getChildCount();
        for (int i = 0; i < n; i++) {
            View c = vg.getChildAt(i);
            if (c instanceof android.widget.ImageView) {
                return true;
            }
            if (c instanceof ViewGroup && hasImageView((ViewGroup) c)) {
                return true;
            }
        }
        return false;
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
                Toast.makeText(this, this.getString(R.string.storage_permission_needed), Toast.LENGTH_SHORT).show();
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