package tv.biliclassic;

import tv.biliclassic.util.DeviceUtil;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.AnnouncementUtil;
import tv.biliclassic.metro.MetroHomeActivity;
import tv.biliclassic.metro.MetroSetupActivity;
import tv.biliclassic.util.DeviceInfoUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SdkHelper;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;
import tv.biliclassic.util.DialogUtil;

public class MainActivity extends BaseActivity {

    private static final String KEY_LANDSCAPE_TIP_SHOWN = "landscape_tip_shown";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String KEY_TV_UNSUPPORTED_SHOWN = "tv_unsupported_shown";
    private static final int TIP_DELAY_MS = 1500;

    private ViewPager mPager;
    private List<FragmentInfo> mFragments = new ArrayList<FragmentInfo>();
    private Handler mHandler = new Handler();

    // 当前可见（前台）的 Fragment，用于把方向键事件派发给它处理
    private Fragment mActiveFragment;
    // 选项菜单是否打开，打开时放行方向键给菜单自身导航
    private boolean mOptionsMenuOpen = false;

    // 持有崩溃提示对话框引用，避免被 GC 回收导致「闪现一下就不见」
    private AlertDialog mCrashDialog;

    private int currentVersionCode = -1;
    private String currentVersionName = "";

    private int logoClickCount = 0;
    private Handler logoClickHandler = new Handler();
    private Runnable logoClickReset = new Runnable() {
        @Override
        public void run() {
            logoClickCount = 0;
        }
    };

    private static class FragmentInfo {
        String title;
        Class<? extends Fragment> clss;
        FragmentInfo(String title, Class<? extends Fragment> clss) {
            this.title = title;
            this.clss = clss;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            currentVersionCode = 0;
            currentVersionName = "0.0.0";
        }

        boolean setupShown = SharedPreferencesUtil.getBoolean("setup_shown", false);
        int lastVersionCode = SharedPreferencesUtil.getInt("last_version_code", 0);
        boolean hasSetupKey = SharedPreferencesUtil.getSharedPreferences().contains("setup_shown");
        boolean hasLastVersionKey = SharedPreferencesUtil.getSharedPreferences().contains("last_version_code");

        if (!hasSetupKey && !hasLastVersionKey) {
            // 两个键都不存在：可能是首次安装，也可能是从旧版本（0.4.4 及以前）升级
            boolean hasOldData = SharedPreferencesUtil.getSharedPreferences().getAll().size() > 0;
            if (hasOldData) {
                // SharedPreferences 里有旧数据（设置、cookies等）→ 老用户升级
                // 跳过"初次使用"，设 last_version_code 为当前-1 以触发"升级完成"
                SharedPreferencesUtil.putBoolean("setup_shown", true);
                SharedPreferencesUtil.putInt("last_version_code", currentVersionCode > 0 ? currentVersionCode - 1 : 0);
                setupShown = true;
                lastVersionCode = currentVersionCode > 0 ? currentVersionCode - 1 : 0;
            } else {
                // 完全空文件 → 真首次安装，记录版本号，让 setupShown=false 走初次使用流程
                SharedPreferencesUtil.putInt("last_version_code", currentVersionCode);
                lastVersionCode = currentVersionCode;
            }
        }

        if (!setupShown) {
            Intent intent = new Intent(this, MetroSetupActivity.class);
            intent.putExtra("mode", "first");
            startActivity(intent);
            finish();
            return;
        } else if (lastVersionCode > 0 && currentVersionCode > lastVersionCode) {
            Intent intent = new Intent(this, MetroSetupActivity.class);
            intent.putExtra("mode", "upgrade");
            startActivity(intent);
            finish();
            return;
        }

        NetWorkUtil.refreshHeaders();
        int sdkInt = SdkHelper.getSdkInt();

        // 主题设置：Metro → 打开 MetroHome；Classic → 进入经典主界面（默认）
        if (SettingsActivity.getUiTheme() == SettingsActivity.THEME_METRO) {
            Intent intent = new Intent(this, MetroHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            // 只去掉 MainActivity 这一步的"退出"动画，保留 MetroHome 的单一进入动画（淡入），避免开屏动画被卡没
            tv.biliclassic.player.PlayerCompat.overridePendingTransition(this,
                    android.R.anim.fade_in, 0);
            finish();
            return;
        }

        // Android 4.0 以下：如果有 TV 标志或强制模式，强制横屏但不进入 TV UI
        if (sdkInt < 14) {
            boolean tvModeEnabled = tv.biliclassic.util.DeviceUtil.isTv(this);
            if (tvModeEnabled) {
                // 强制横屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                // 只显示一次提示
                boolean alreadyShown = SharedPreferencesUtil.getBoolean(KEY_TV_UNSUPPORTED_SHOWN, false);
                if (!alreadyShown) {
                    Toast.makeText(this, this.getString(R.string.tv_mode_need_android4), Toast.LENGTH_LONG).show();
                    SharedPreferencesUtil.putBoolean(KEY_TV_UNSUPPORTED_SHOWN, true);
                }
            }
        }

        setContentView(R.layout.activity_main);
        tv.biliclassic.util.PerfLog.init();
        tv.biliclassic.util.PerfLog.attachGlobalFrameWatcher(findViewById(android.R.id.content));
        checkLegacyVersionCompatibility();
        checkAndShowCrashDialog();

        // 圆形屏幕（手表）适配：顶栏 Bilibili 标题和搜索按钮水平居中
        {
            final View mainRoot = findViewById(R.id.title_bar);
            if (mainRoot != null) {
                mainRoot.post(new Runnable() {
                    @Override
                    public void run() {
                        if (tv.biliclassic.util.DeviceUtil.isRoundScreen(mainRoot)) {
                            centerTopBarForRoundScreen();
                        }
                    }
                });
            }
        }

        final PagerTabStrip tabStrip = (PagerTabStrip) findViewById(R.id.pager_tab_strip);
        if (tabStrip != null) {
            tabStrip.setTabIndicatorColor(0xFFFF9FC5);
            tabStrip.setBackgroundResource(R.drawable.tab_background);
            tabStrip.setTextColor(0xFFFFFFFF);
            // 手表（小屏）适配：固定 TAB 栏高度 + 缩小 padding 让背景随文字变小；
            // 手机上保持原生 wrap_content 行为
            float screenWidthDp = getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density;
            if (screenWidthDp <= 200) {
                try {
                    android.widget.FrameLayout.LayoutParams lp =
                            (android.widget.FrameLayout.LayoutParams) tabStrip.getLayoutParams();
                    lp.height = (int) getResources().getDimension(R.dimen.main_tab_bar_height);
                    tabStrip.setLayoutParams(lp);
                    // 文字垂直居中，固定高度 TAB 栏内上下空隙均匀
                    tabStrip.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    // PagerTabStrip 底部指示条 padding（mMinPaddingBottom）默认 6dp 固定，
                    // 小屏反射缩小，避免背景高度不随文字变小
                    try {
                        java.lang.reflect.Field minPadField =
                                android.support.v4.view.PagerTabStrip.class.getDeclaredField("mMinPaddingBottom");
                        minPadField.setAccessible(true);
                        int minPad = (int) (getResources().getDisplayMetrics().density * 2.0f + 0.5f);
                        minPadField.setInt(tabStrip, minPad);
                    } catch (Throwable t) {
                    }
                    tabStrip.setPadding(0, 0, 0, 0);
                    tabStrip.requestLayout();
                    tabStrip.invalidate();
                } catch (Throwable t) {
                }
            }
        }

        mPager = (ViewPager) findViewById(R.id.pager);
        // 优化：使用 FragmentStatePagerAdapter，只缓存当前页左右各1页
        mPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));
        mPager.setOffscreenPageLimit(1);

        addTab(getString(R.string.mainactivity_tab_profile), ProfileFragment.class);
        addTab(getString(R.string.mainactivity_tab_home), HomeFragment.class);
        addTab(getString(R.string.mainactivity_tab_newanime), NewAnimeFragment.class);
        addTab(getString(R.string.mainactivity_tab_timeline), TimelineFragment.class);
        if (SettingsActivity.isPortraitModeEnabled()) {
            addTab(getString(R.string.mainactivity_tab_recommend), RecommendVerticalFragment.class);
        } else {
            addTab(getString(R.string.mainactivity_tab_recommend), RecommendFragment.class);
        }
        addTab(getString(R.string.mainactivity_tab_dynamic), DynamicFragment.class);
        addTab(getString(R.string.mainactivity_tab_about), AboutFragment.class);

        mPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                clearTopBarHighlight();
                updateOrientationForTab();
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        int targetTab = getIntent().getIntExtra("tab_index", -1);
        if (targetTab >= 0 && targetTab < mFragments.size()) {
            mPager.setCurrentItem(targetTab);
        } else {
            int defaultTab = SettingsActivity.getDefaultTab();
            mPager.setCurrentItem(defaultTab);
        }
        updateOrientationForTab();

        ImageView btnSearch = (ImageView) findViewById(R.id.btn_search);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    expandTitleSearch();
                }
            });
        }
        View btnHomeBack = findViewById(R.id.btn_home_back);
        if (btnHomeBack != null) {
            btnHomeBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    collapseTitleSearch();
                }
            });
        }
        // X：有文字则清空，无文字则收起搜索（模仿 1.8.4 SearchView 的关闭按钮）
        View titleSearchClear = findViewById(R.id.title_search_clear);
        if (titleSearchClear != null) {
            titleSearchClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EditText edit = (EditText) findViewById(R.id.title_search_edit);
                    if (edit != null && edit.getText().length() > 0) {
                        edit.setText("");
                    } else {
                        collapseTitleSearch();
                    }
                }
            });
        }
        View historyClear = findViewById(R.id.main_search_history_clear);
        if (historyClear != null) {
            historyClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferencesUtil.putString("search_history", "");
                    hideMainSearchHistory();
                }
            });
        }
        final EditText titleSearchEdit = (EditText) findViewById(R.id.title_search_edit);
        if (titleSearchEdit != null) {
            tv.biliclassic.util.SdkHelper.setOnEditorActionListener(titleSearchEdit,
                    new tv.biliclassic.util.SdkHelper.EditorActionHandler() {
                        @Override
                        public boolean onEditorAction(int actionId, android.view.KeyEvent event) {
                            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                                    || (event != null
                                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                                        && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                                        && event.getRepeatCount() == 0)) {
                                submitTitleSearch();
                                return true;
                            }
                            return false;
                        }
                    });
        }

        ImageView logo = (ImageView) findViewById(R.id.logo);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOptionsMenu();

                logoClickCount++;
                if (logoClickCount == 1) {
                    logoClickHandler.removeCallbacks(logoClickReset);
                    logoClickHandler.postDelayed(logoClickReset, 2000);
                } else if (logoClickCount >= 5) {
                    logoClickCount = 0;
                    logoClickHandler.removeCallbacks(logoClickReset);
                    triggerSpaceQuake();
                }
            }
        });

        // 启动就按比例定 logo 宽，避免老系统上以原图宽度顶着左边
        fitLogoWidth(logo);

        if (shouldEnableLandscape()) {
            boolean tipShown = SharedPreferencesUtil.getBoolean(KEY_LANDSCAPE_TIP_SHOWN, false);
            if (!tipShown) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showLandscapeTipDialog();
                    }
                }, TIP_DELAY_MS);
            }
        }

        clearVideoCache();
        checkAutoUpdate();

        // 检查并显示公告（延迟执行，确保界面加载完成）
        checkAnnouncement();
    }

    /**
     * 检查并显示公告
     */
    private void checkAnnouncement() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                AnnouncementUtil.checkMultipleAnnouncements(MainActivity.this,
                        new AnnouncementUtil.MultipleAnnouncementCallback() {
                            @Override
                            public void onSuccess(List<AnnouncementUtil.Announcement> announcements) {
                                // 公告已显示，无需额外处理
                            }

                            @Override
                            public void onFailed(String error) {
                                // 公告获取失败或无需显示，静默处理
                            }
                        });
            }
        }, 2000);
    }

    private void checkAutoUpdate() {
        boolean autoUpdateEnabled = SharedPreferencesUtil.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
        if (!autoUpdateEnabled) {
            return;
        }

        doAutoCheckUpdate();
    }

    private void doAutoCheckUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String versionJson = null;

                try {
                    versionJson = UpdateUtil.fetchVersionJson(
                            "http://www.biliclassic.cn/api/version.json");
                } catch (Exception e) {}

                if (versionJson == null) {
                    try {
                        versionJson = UpdateUtil.fetchVersionJson(
                                "http://7891vip.top/biliclassic/update.php");
                    } catch (Exception e) {}
                }

                final String finalVersionJson = versionJson;

                if (finalVersionJson != null && finalVersionJson.length() > 0) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleUpdateCheckResult(finalVersionJson);
                        }
                    });
                }
            }
        }).start();
    }

    private void handleUpdateCheckResult(String versionJson) {
        try {
            JSONObject json = new JSONObject(versionJson);

            int latestVersionCode = json.optInt("version_code", 0);
            String latestVersionName = json.optString("version", "");
            String downloadUrl = json.optString("download_url", "");
            boolean forceUpdate = json.optBoolean("force_update", false);
            int minSdk = json.optInt("min_sdk", 0);

            String changelog = "";
            try {
                org.json.JSONArray changelogArray = json.optJSONArray("changelog");
                if (changelogArray != null && changelogArray.length() > 0) {
                    StringBuilder logBuilder = new StringBuilder();
                    for (int i = 0; i < changelogArray.length(); i++) {
                        logBuilder.append("• ").append(changelogArray.getString(i));
                        if (i < changelogArray.length() - 1) {
                            logBuilder.append("\n");
                        }
                    }
                    changelog = logBuilder.toString();
                }
            } catch (Exception e) {
                changelog = json.optString("changelog", "");
            }

            boolean hasUpdate = false;

            if (latestVersionCode > 0) {
                hasUpdate = (latestVersionCode > currentVersionCode);
            } else {
                hasUpdate = compareVersions(currentVersionName, latestVersionName);
            }

            int sdkVersion = SdkHelper.getSdkInt();
            if (minSdk > 0 && sdkVersion < minSdk) {
                return;
            }

            if (hasUpdate) {
                showAutoUpdateDialog(latestVersionName, changelog, downloadUrl, forceUpdate);
            }

        } catch (Exception e) {}
    }

    private void showAutoUpdateDialog(String versionName, String changelog, final String downloadUrl, boolean forceUpdate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(DialogUtil.wrap(this)));
        builder.setTitle("发现新版本: " + versionName);

        String message = "当前: " + currentVersionName + "\n" +
                "最新: " + versionName + "\n\n" +
                changelog;
        builder.setMessage(message);

        builder.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (downloadUrl != null && downloadUrl.length() > 0) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(downloadUrl));
                    startActivity(intent);
                } else {
                    Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.invalid_download_url), Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (!forceUpdate) {
            builder.setNegativeButton("稍后", null);
        }

        builder.setCancelable(!forceUpdate);
        builder.show();
    }

    private boolean compareVersions(String current, String latest) {
        if (current == null || latest == null || current.length() == 0 || latest.length() == 0) {
            return false;
        }

        current = current.trim();
        latest = latest.trim();

        if (current.equals(latest)) {
            return false;
        }

        String[] currentParts = splitVersion(current);
        String[] latestParts = splitVersion(latest);

        String currentBase = currentParts[0];
        String latestBase = latestParts[0];
        String currentSuffix = currentParts[1];
        String latestSuffix = latestParts[1];

        int cmp = compareVersionNumbers(currentBase, latestBase);
        if (cmp != 0) {
            return cmp < 0;
        }

        return compareSuffix(currentSuffix, latestSuffix) < 0;
    }

    private String[] splitVersion(String version) {
        String base = version;
        String suffix = "";

        int rIndex = version.indexOf("-r");
        if (rIndex > 0) {
            base = version.substring(0, rIndex);
            suffix = version.substring(rIndex + 1);
        } else {
            int fixIndex = version.indexOf("-fix");
            if (fixIndex > 0) {
                base = version.substring(0, fixIndex);
                suffix = version.substring(fixIndex + 1);
            }
        }

        return new String[]{base, suffix};
    }

    private int compareVersionNumbers(String v1, String v2) {
        if (v1.indexOf('.') >= 0 || v2.indexOf('.') >= 0) {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");

            int len = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < len; i++) {
                int num1 = 0;
                int num2 = 0;
                try {
                    if (i < parts1.length) num1 = Integer.parseInt(parts1[i]);
                    if (i < parts2.length) num2 = Integer.parseInt(parts2[i]);
                } catch (NumberFormatException e) {
                    String s1 = (i < parts1.length) ? parts1[i] : "";
                    String s2 = (i < parts2.length) ? parts2[i] : "";
                    int cmp = s1.compareTo(s2);
                    if (cmp != 0) return cmp;
                    continue;
                }
                if (num1 != num2) {
                    return num1 - num2;
                }
            }
            return 0;
        }

        try {
            int n1 = Integer.parseInt(v1);
            int n2 = Integer.parseInt(v2);
            return n1 - n2;
        } catch (NumberFormatException e) {
            return v1.compareTo(v2);
        }
    }

    private int compareSuffix(String currentSuffix, String latestSuffix) {
        if (currentSuffix != null && currentSuffix.length() > 0 &&
                latestSuffix != null && latestSuffix.length() > 0) {

            if (currentSuffix.startsWith("r") && latestSuffix.startsWith("r")) {
                try {
                    int n1 = Integer.parseInt(currentSuffix.substring(1));
                    int n2 = Integer.parseInt(latestSuffix.substring(1));
                    return n1 - n2;
                } catch (NumberFormatException e) {
                    return currentSuffix.compareTo(latestSuffix);
                }
            }

            if (currentSuffix.startsWith("r") && latestSuffix.startsWith("fix")) {
                return -1;
            }
            if (currentSuffix.startsWith("fix") && latestSuffix.startsWith("r")) {
                return 1;
            }

            return currentSuffix.compareTo(latestSuffix);
        }

        if (currentSuffix != null && currentSuffix.length() > 0) {
            return 1;
        }

        if (latestSuffix != null && latestSuffix.length() > 0) {
            return -1;
        }

        return 0;
    }

    /**
     * 圆形屏幕（手表）适配：顶栏 Bilibili 标题和搜索按钮作为一组整体居中。
     * 用水平 LinearLayout 包裹两个图标（内部 logo 左、搜索右、间距约 2px），
     * 整组在 RelativeLayout 中水平居中；两个图标都缩小。
     */
    private void centerTopBarForRoundScreen() {
        try {
            android.widget.RelativeLayout bar =
                    (android.widget.RelativeLayout) findViewById(R.id.title_bar);
            View logo = findViewById(R.id.logo);
            View search = findViewById(R.id.btn_search);
            if (bar == null || logo == null || search == null) return;

            int logoW = (int) getResources().getDimension(R.dimen.round_bar_logo_width);
            int searchSize = (int) getResources().getDimension(R.dimen.round_bar_search_size);

            // 建居中容器
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.HORIZONTAL);
            group.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.RelativeLayout.LayoutParams glp =
                    new android.widget.RelativeLayout.LayoutParams(
                            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT);
            glp.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
            group.setLayoutParams(glp);
            bar.addView(group);

            // 从 title_bar 移除并移入 group
            bar.removeView(logo);
            bar.removeView(search);

            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                    logoW, LinearLayout.LayoutParams.MATCH_PARENT);
            logoLp.rightMargin = 2; // 间距约 2px
            logo.setLayoutParams(logoLp);

            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                    searchSize, searchSize);
            search.setLayoutParams(searchLp);

            group.addView(logo);
            group.addView(search);

            // 圆屏才隐藏更多按钮
            View more = findViewById(R.id.btn_title_more);
            if (more != null) {
                more.setVisibility(View.GONE);
            }
        } catch (Throwable t) {
        }
    }


    private void checkAndShowCrashDialog() {
        boolean hasCrash = getSharedPreferences("crash", MODE_PRIVATE)
                .getBoolean("has_crash", false);

        if (!hasCrash) {
            return;
        }

        getSharedPreferences("crash", MODE_PRIVATE)
                .edit()
                .putBoolean("has_crash", false)
                .commit();

        final String crashLog = getLatestCrashLog();

        if (crashLog == null || crashLog.length() == 0) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(DialogUtil.wrap(this)))
                .setTitle(getString(R.string.last_abnormal_exit))
                .setMessage(getString(R.string.last_crash_prompt))
                .setPositiveButton("查看", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(MainActivity.this, CrashReportActivity.class);
                        intent.putExtra("crash_info", crashLog);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("忽略", null)
                .create();
        // 持有引用 + 等窗口挂好后再 show，避免被 GC 回收 / 过早显示导致「闪现一下就没了」
        mCrashDialog = dialog;
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                mCrashDialog = null;
            }
        });
        getWindow().getDecorView().post(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && mCrashDialog != null && !mCrashDialog.isShowing()) {
                    try {
                        mCrashDialog.show();
                    } catch (Throwable t) {
                    }
                }
            }
        });
    }

    private String getLatestCrashLog() {
        try {
            File crashDir = new File(getFilesDir().getParentFile(), "crashlog");
            if (!crashDir.exists()) {
                return null;
            }

            File[] files = crashDir.listFiles();
            if (files == null || files.length == 0) {
                return null;
            }

            File latest = files[0];
            for (File f : files) {
                if (f.lastModified() > latest.lastModified()) {
                    latest = f;
                }
            }

            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(latest));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void triggerSpaceQuake() {
        Toast.makeText(this, this.getString(R.string.space_quake), Toast.LENGTH_SHORT).show();

        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        final View redOverlay = new View(this);
        redOverlay.setBackgroundColor(0xFFFF0000);
        final ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
        root.addView(redOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                root.removeView(redOverlay);
            }
        }, 200);
    }

    private void clearVideoCache() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int deletedCount = 0;
                    long freedSpace = 0;

                    File internalCache = getCacheDir();
                    if (internalCache != null && internalCache.exists()) {
                        File[] files = internalCache.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.isFile() && file.getName().endsWith(".mp4")) {
                                    freedSpace += file.length();
                                    if (file.delete()) {
                                        deletedCount++;
                                    }
                                }
                            }
                        }
                    }

                    if (isSDCardAvailable() && PermissionUtil.hasWriteStorage(MainActivity.this)) {
                        File sdCache = new File(Environment.getExternalStorageDirectory(), "BiliClassic/cache");
                        if (sdCache != null && sdCache.exists()) {
                            File[] files = sdCache.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    if (file.isFile() && file.getName().endsWith(".mp4")) {
                                        freedSpace += file.length();
                                        if (file.delete()) {
                                            deletedCount++;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    final int finalDeleted = deletedCount;
                    final long finalFreed = freedSpace;

                    if (finalDeleted > 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String sizeText = formatFileSize(finalFreed);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean isSDCardAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        } else if (size < 1024 * 1024 * 1024) {
            return (size / 1024 / 1024) + " MB";
        } else {
            return (size / 1024 / 1024 / 1024) + " GB";
        }
    }

    private void showLandscapeTipDialog() {
        new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(DialogUtil.wrap(this)))
                .setTitle(getString(R.string.device_adapt_notice))
                .setMessage(getString(R.string.landscape_auto_adapted_msg))
                .setPositiveButton("知道了", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putBoolean(KEY_LANDSCAPE_TIP_SHOWN, true);
                        dialog.dismiss();
                    }
                })
                .setNeutralButton("去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putBoolean(KEY_LANDSCAPE_TIP_SHOWN, true);
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        logoClickHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 遥控器 / 方向键支持：在事件分发给 ScrollView / ViewPager 之前，
     * 先把方向键与确认键派发给当前可见 Fragment（如推荐页），
     * 由其自身维护选中卡片并消费事件，避免被 ScrollView 滚动或 ViewPager 切 Tab 吞掉。
     *
     * Tab 切换规则：
     * - 左右方向键不再切换 Tab（推荐页内用于移动卡片光标，其他页面直接消费掉，防止 ViewPager 切页）；
     * - Tab 切换改用数字键 1（上一个）和 3（下一个）。
     */
    /** 直接改 logo 的左边距（避免用 toRightOf 依赖 GONE 的返回键，间距不稳定） */
    private void setLogoLeftMargin(int px) {
        ImageView logo = (ImageView) findViewById(R.id.logo);
        if (logo == null) {
            return;
        }
        android.view.ViewGroup.LayoutParams lp = logo.getLayoutParams();
        if (lp instanceof android.widget.RelativeLayout.LayoutParams) {
            android.widget.RelativeLayout.LayoutParams rp =
                    (android.widget.RelativeLayout.LayoutParams) lp;
            if (rp.leftMargin != px) {
                rp.leftMargin = px;
                logo.setLayoutParams(rp);
            }
        }
    }

    /** 按图片比例算 logo 宽度（清朝系统用） */
    private void fitLogoWidth(final ImageView logo) {
        fitLogoWidth(logo, 0);
    }

    /**
     * 老系统上 adjustViewBounds 不生效、且 view 尚未测量时 getHeight() 为 0，
     * 这里等测量完成再按比例设宽（最多重试 ~1s），否则 logo 会以原图宽度显示，
     * 看起来左边一大块透明留白，等某次布局后才突然「跑回最左边」。
     */
    private void fitLogoWidth(final ImageView logo, final int attempt) {
        if (logo == null) {
            return;
        }
        // 先同步算一次：高度已知时立即设宽，避免"换图标后先按旧宽度渲染一帧（露出一坨）
        // 再等下一帧复原"的跳动。只有 view 还没测量出来时才 post 重试。
        android.graphics.drawable.Drawable d = logo.getDrawable();
        int h = logo.getHeight();
        if (d != null && h > 0) {
            int ih = d.getIntrinsicHeight();
            int iw = d.getIntrinsicWidth();
            if (ih > 0 && iw > 0) {
                int w = (int) ((long) h * iw / ih);
                android.view.ViewGroup.LayoutParams lp = logo.getLayoutParams();
                if (lp != null && lp.width != w) {
                    lp.width = w;
                    logo.setLayoutParams(lp);
                }
            }
            return;
        }
        if (attempt < 30) {
            logo.postDelayed(new Runnable() {
                public void run() {
                    fitLogoWidth(logo, attempt + 1);
                }
            }, 30);
        }
    }

    /** 展开主界面标题栏的内联 Holo 搜索框（模仿 1.8.4 iconified SearchView） */
    private void expandTitleSearch() {
        if (isTitleSearchExpanded()) return; // 防重复展开（物理键盘回车/搜索键可能连发）
        clearTopBarHighlight();
        View btnSearch = findViewById(R.id.btn_search);
        View container = findViewById(R.id.title_search_container);
        final EditText edit = (EditText) findViewById(R.id.title_search_edit);
        View back = findViewById(R.id.btn_home_back);
        ImageView logo = (ImageView) findViewById(R.id.logo);
        if (btnSearch != null) btnSearch.setVisibility(View.GONE);
        // 返回键插到 logo 左边，logo 换成应用图标；同时把 logo 右移，避免和返回键重叠
        if (logo != null) {
            logo.setImageResource(R.drawable.ic_launcher);
            // 返回键宽 40dp，logo 紧贴其右侧（和二级页顶栏内容贴返回键一致），不再留 12dp 空
            setLogoLeftMargin(DeviceUtil.dpToPx(40));
            fitLogoWidth(logo);
        }
        if (back != null) back.setVisibility(View.VISIBLE);
        if (container != null) container.setVisibility(View.VISIBLE);
        if (edit != null) {
            edit.requestFocus();
            edit.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // 直接引用 InputMethodManager 会让老框架 verifier 拒绝整个类，
                    // 统一走反射（SHOW_IMPLICIT = 1）
                    tv.biliclassic.util.SdkHelper.showSoftInput(edit, 1);
                }
            }, 100);
        }
        showMainSearchHistory();
    }

    private boolean isTitleSearchExpanded() {
        View container = findViewById(R.id.title_search_container);
        return container != null && container.getVisibility() == View.VISIBLE;
    }

    /** 收起内联搜索框，恢复 logo 和搜索图标 */
    private void collapseTitleSearch() {
        clearTopBarHighlight();
        ImageView logo = (ImageView) findViewById(R.id.logo);
        View btnSearch = findViewById(R.id.btn_search);
        View container = findViewById(R.id.title_search_container);
        if (logo != null) {
            logo.setImageResource(R.drawable.ic_home);
            logo.setVisibility(View.VISIBLE);
            setLogoLeftMargin(DeviceUtil.dpToPx(12));
            fitLogoWidth(logo);
        }
        if (btnSearch != null) btnSearch.setVisibility(View.VISIBLE);
        View back = findViewById(R.id.btn_home_back);
        if (back != null) back.setVisibility(View.GONE);
        if (container != null) container.setVisibility(View.GONE);
        View focus = getCurrentFocus();
        if (focus != null) {
            tv.biliclassic.util.SdkHelper.hideSoftInputFromWindow(this, focus.getWindowToken(), 0);
        }
        hideMainSearchHistory();
        // 键盘收起动画结束后重算底部补白，避免界面停在被键盘挤扁的状态
        final android.view.View decor = getWindow().getDecorView();
        decor.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateNavBarPadding(decor);
            }
        }, 350);
    }

    /** 提交内联搜索：带关键词打开搜索页 */
    private void submitTitleSearch() {
        // 已收起（说明本次已提交过）就不再重复开搜索页（回车/IME 可能连发，曾一次开三个）
        if (!isTitleSearchExpanded()) return;
        EditText edit = (EditText) findViewById(R.id.title_search_edit);
        if (edit == null) {
            return;
        }
        String keyword = edit.getText().toString().trim();
        if (keyword.length() == 0) {
            return;
        }
        // 作弊码：命中直接执行彩蛋并收起搜索框，不打开搜索页
        //（否则会先闪一下搜索界面，返回还停在一个空搜索页）
        if (tv.biliclassic.util.CheatCodeUtil.tryTrigger(this, keyword)) {
            collapseTitleSearch();
            return;
        }
        // av号/BV号：直接跳视频，不打开搜索页（否则从视频返回会停在空搜索页）
        if (tv.biliclassic.util.CheatCodeUtil.tryOpenVideoById(this, keyword)) {
            collapseTitleSearch();
            return;
        }
        saveMainSearchHistory(keyword);
        Intent intent = new Intent(MainActivity.this, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        startActivity(intent);
        collapseTitleSearch();
    }

    /** 展开时在主搜索框下方显示搜索历史 */
    private void showMainSearchHistory() {
        LinearLayout container = (LinearLayout) findViewById(R.id.main_search_history);
        LinearLayout listContainer = (LinearLayout) findViewById(R.id.main_search_history_list);
        if (container == null || listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        java.util.List<String> items = new java.util.ArrayList<String>();
        String json = SharedPreferencesUtil.getString("search_history", "");
        try {
            if (json != null && json.length() > 0) {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    items.add(arr.getString(i));
                }
            }
        } catch (Exception e) {
        }
        final float density = getResources().getDisplayMetrics().density;
        int divH = (int) (1 * density + 0.5f);
        int padH = (int) (12 * density + 0.5f);
        int padV = (int) (8 * density + 0.5f);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(0xFFC8C8C8);
                listContainer.addView(div, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, divH));
            }
            final String kw = items.get(i);
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText(kw);
            tv.setTextSize(16);
            tv.setTextColor(0xFF525252);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
            // 先设背景再设 padding：setBackgroundResource → setBackgroundDrawable 会用 drawable
            // 的 padding 覆盖 View 的 padding（旧设备尤其明显），后设 padding 才不会被清成 0
            tv.setBackgroundResource(R.drawable.titlebar_pink_item_bg);
            tv.setPadding(padH, padV, padH, padV);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EditText edit = (EditText) findViewById(R.id.title_search_edit);
                    if (edit != null) {
                        edit.setText(kw);
                    }
                    submitTitleSearch();
                }
            });
            listContainer.addView(tv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        container.setVisibility(items.size() > 0 ? View.VISIBLE : View.GONE);
    }

    private void hideMainSearchHistory() {
        View container = findViewById(R.id.main_search_history);
        if (container != null) {
            container.setVisibility(View.GONE);
        }
    }

    /** 保存搜索历史（JSON 数组，最多 10 条，去重，最新在前） */
    private static final int MAX_SEARCH_KEYWORD_LEN = 100;

    private void saveMainSearchHistory(String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return;
        }
        // 防御：个别情况搜索框会被塞进超长文本，原样存历史会把 prefs 撑爆（曾达 14MB → 启动 OOM）
        if (keyword.length() > MAX_SEARCH_KEYWORD_LEN) {
            return;
        }
        java.util.List<String> historyList = new java.util.ArrayList<String>();
        String json = SharedPreferencesUtil.getString("search_history", "");
        try {
            if (json != null && json.length() > 0) {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String item = arr.getString(i);
                    if (item != null && item.length() <= MAX_SEARCH_KEYWORD_LEN
                            && !item.equals(keyword)) {
                        historyList.add(item);
                    }
                }
            }
        } catch (Exception e) {
        }
        historyList.add(0, keyword);
        if (historyList.size() > 10) {
            historyList = historyList.subList(0, 10);
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String item : historyList) {
                arr.put(item);
            }
            SharedPreferencesUtil.putString("search_history", arr.toString());
        } catch (Exception e) {
        }
    }

    private View mTopNavSearch;
    private View mTopNavMore;
    private int mTopNavIndex = -1;

    private java.util.ArrayList<View> getTopNavItems() {
        java.util.ArrayList<View> items = new java.util.ArrayList<View>();
        if (mTopNavSearch == null) mTopNavSearch = findViewById(R.id.btn_search);
        if (mTopNavMore == null) mTopNavMore = findViewById(R.id.btn_title_more);
        if (mTopNavSearch != null && mTopNavSearch.getVisibility() == View.VISIBLE) {
            items.add(mTopNavSearch);
        }
        if (mTopNavMore != null && mTopNavMore.getVisibility() == View.VISIBLE) {
            items.add(mTopNavMore);
        }
        return items;
    }

    /** 当前原生焦点若落在顶栏按钮上，返回其下标；否则 -1。 */
    private int topIndexIfFocused() {
        View f = getCurrentFocus();
        if (f == null) return -1;
        java.util.ArrayList<View> items = getTopNavItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == f) return i;
        }
        return -1;
    }

    /** 进顶栏：把原生焦点给第一个顶栏按钮（高亮由 selector 的 state_focused 渲染）。 */
    private void enterTopBar() {
        java.util.ArrayList<View> items = getTopNavItems();
        if (items.size() == 0) return;
        mTopNavIndex = 0;
        items.get(0).requestFocus();
    }

    /** 退出顶栏：清掉按钮的原生焦点（否则 selector 的 state_focused 粉色高亮残留）。 */
    private void clearTopBarHighlight() {
        mTopNavIndex = -1;
        if (mTopNavSearch == null) mTopNavSearch = findViewById(R.id.btn_search);
        if (mTopNavMore == null) mTopNavMore = findViewById(R.id.btn_title_more);
        if (mTopNavSearch != null) mTopNavSearch.clearFocus();
        if (mTopNavMore != null) mTopNavMore.clearFocus();
    }

    /** 顶栏导航：左右移动原生焦点，确认触发，上下退出；其它键退出并放行。 */
    private boolean handleTopBarNav(int action, int repeatCount) {
        java.util.ArrayList<View> items = getTopNavItems();
        if (items.size() == 0) {
            mTopNavIndex = -1;
            return false;
        }
        if (mTopNavIndex >= items.size()) mTopNavIndex = items.size() - 1;
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT) {
            if (repeatCount == 0 && mTopNavIndex > 0) {
                mTopNavIndex--;
                items.get(mTopNavIndex).requestFocus();
            }
            return true;
        }
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT) {
            if (repeatCount == 0 && mTopNavIndex < items.size() - 1) {
                mTopNavIndex++;
                items.get(mTopNavIndex).requestFocus();
            }
            return true;
        }
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            if (repeatCount == 0) {
                View target = items.get(mTopNavIndex);
                if (target == mTopNavSearch) {
                    // 搜索：清高亮后展开搜索框（输入框会接走焦点）
                    clearTopBarHighlight();
                    target.performClick();
                } else {
                    // 更多：弹出溢出菜单后把焦点留在它自己身上，
                    // 否则清掉焦点会让框架把焦点乱移到「搜索」上
                    target.performClick();
                    target.requestFocus();
                }
            }
            return true;
        }
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                || action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN) {
            clearTopBarHighlight();
            return true;
        }
        // 其它键（返回/菜单/刷新等）不归顶栏管：退出顶栏并放行
        clearTopBarHighlight();
        return false;
    }

    /** 当前页内容光标是否已在最顶部（再按上键进入顶栏）。 */
    private boolean activeContentAtTop() {
        if (mActiveFragment == null) return false;
        if (mActiveFragment instanceof RecommendFragment) {
            return ((RecommendFragment) mActiveFragment).isAtTabStrip();
        }
        if (mActiveFragment instanceof NewAnimeFragment) {
            return ((NewAnimeFragment) mActiveFragment).isAtTabStrip();
        }
        if (mActiveFragment instanceof AboutFragment) {
            return ((AboutFragment) mActiveFragment).isNavAtTop();
        }
        if (mActiveFragment instanceof ProfileFragment) {
            return ((ProfileFragment) mActiveFragment).isNavAtTop();
        }
        if (mActiveFragment instanceof TimelineFragment) {
            return ((TimelineFragment) mActiveFragment).isNavAtTop();
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // 内联搜索框展开时，返回键先收起搜索框
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                && isTitleSearchExpanded()) {
            collapseTitleSearch();
            return true;
        }
        // 遥控器「搜索」键：展开标题搜索框
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_SEARCH
                && event.getRepeatCount() == 0) {
            if (isTitleSearchExpanded()) {
                collapseTitleSearch();
            } else {
                expandTitleSearch();
            }
            return true;
        }
        // 兜底：顶栏「搜索」已获焦点时按 OK/回车 → 直接展开（防某些机型焦点对不上）
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0
                && (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (mTopNavSearch == null) mTopNavSearch = findViewById(R.id.btn_search);
            if (mTopNavSearch != null && getCurrentFocus() == mTopNavSearch) {
                clearTopBarHighlight();
                expandTitleSearch();
                return true;
            }
        }
        if (!mOptionsMenuOpen) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                boolean firstPress = (event.getRepeatCount() == 0);
                int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
                // 顶栏导航：原生焦点落在顶栏按钮上时，左右移动焦点、确认触发、上下退出
                mTopNavIndex = topIndexIfFocused();
                if (mTopNavIndex >= 0) {
                    // 物理键盘（如 HTC Chacha）的回车键默认不在绑定表里（classify 返回 -1），
                    // 会被当"其它键"清焦点放行 → 选中的搜索/更多按不出来。这里把回车当确认键。
                    int navAction = action;
                    if (navAction < 0 && (event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                            || event.getKeyCode() == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                        navAction = tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM;
                    }
                    return handleTopBarNav(navAction, event.getRepeatCount());
                }
                // 内容已在顶部再按上键：进入顶栏（选中搜索/更多）
                if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP && firstPress
                        && !isTitleSearchExpanded() && activeContentAtTop()) {
                    enterTopBar();
                    return true;
                }
                // 放送时间表：方向键/数字键 2/8 滚动列表
                if (mActiveFragment instanceof TimelineFragment) {
                    TimelineFragment tf = (TimelineFragment) mActiveFragment;
                    if (tf.handleRemoteKey(event)) {
                        return true;
                    }
                }
                // 个人中心：方向键/确认键导航菜单
                if (mActiveFragment instanceof ProfileFragment) {
                    ProfileFragment pf = (ProfileFragment) mActiveFragment;
                    if (pf.handleRemoteKey(event)) {
                        return true;
                    }
                }
                // 关于我们：方向键/确认键在链接间导航
                if (mActiveFragment instanceof AboutFragment) {
                    AboutFragment af = (AboutFragment) mActiveFragment;
                    if (af.handleRemoteKey(event)) {
                        return true;
                    }
                }
                // 新番专题：方向键行列导航卡片，确认键打开；
                // 光标在顶部指示器层时左右键切换 Tab
                if (mActiveFragment instanceof NewAnimeFragment) {
                    NewAnimeFragment naf = (NewAnimeFragment) mActiveFragment;
                    if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT
                            || action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT) {
                        if (firstPress && naf.isAtTabStrip()) {
                            if (switchTab(action)) {
                                return true;
                            }
                        }
                    }
                    if (naf.handleRemoteKey(event)) {
                        return true;
                    }
                }
                // 推荐页：方向键/确认键/数字键 2/8 移动光标与翻页
                // （所有 DOWN 都消费，含长按 repeat，防止 ScrollView 内置长按滚动干扰）
                RecommendFragment rf = getCurrentRecommendFragment();
                if (rf != null) {
                    if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT
                            || action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT) {
                        if (firstPress && rf.isAtTabStrip()) {
                            // 推荐页光标在顶部指示器层：左右键切换 Tab
                            if (switchTab(action)) {
                                return true;
                            }
                        }
                    }
                    if (rf.handleRemoteKey(event)) {
                        return true;
                    }
                }
                // 其他页面：左右方向键切换 Tab
                if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT
                        || action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT) {
                    if (firstPress && switchTab(action)) {
                        return true;
                    }
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 左右方向键切换 Tab：LEFT 上一个，RIGHT 下一个。
     * 返回 true 表示已切换（含已到边界无需切换）。
     */
    private boolean switchTab(int action) {
        if (mPager == null) {
            return false;
        }
        int cur = mPager.getCurrentItem();
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT) {
            if (cur > 0) {
                mPager.setCurrentItem(cur - 1);
            }
            return true;
        } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT) {
            if (mFragments != null && cur < mFragments.size() - 1) {
                mPager.setCurrentItem(cur + 1);
            }
            return true;
        }
        return false;
    }

    /**
     * 根据 ViewPager 当前页定位 RecommendFragment 实例。
     * 使用 setPrimaryItem 维护的 mActiveFragment（始终指向当前页），
     * 避免 ViewPager 预加载的相邻页 isVisible() 为 true 导致事件被错误消费。
     */
    private RecommendFragment getCurrentRecommendFragment() {
        if (mActiveFragment instanceof RecommendFragment) {
            return (RecommendFragment) mActiveFragment;
        }
        return null;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        mOptionsMenuOpen = true;
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        mOptionsMenuOpen = false;
        super.onPanelClosed(featureId, menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearTopBarHighlight();
        updateOrientationForTab();
    }

    /** 触屏操作时退出顶栏导航并清高亮（遥控器选中后直接点屏幕的残留场景）。 */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN && mTopNavIndex >= 0) {
            clearTopBarHighlight();
        }
        return super.dispatchTouchEvent(ev);
    }

    private void addTab(String title, Class<? extends Fragment> clss) {
        mFragments.add(new FragmentInfo(title, clss));
        if (mPager.getAdapter() != null) {
            mPager.getAdapter().notifyDataSetChanged();
        }
    }

    public void setCurrentTab(int index) {
        if (mPager != null) {
            final int idx = index;
            mPager.post(new Runnable() {
                @Override
                public void run() {
                    if (mPager != null) {
                        mPager.setCurrentItem(idx);
                    }
                }
            });
        }
    }

    private void updateOrientationForTab() {
        if (mPager == null) return;
        int cur = mPager.getCurrentItem();
        boolean recommendTab = cur >= 0 && cur < mFragments.size()
                && RecommendVerticalFragment.class.equals(mFragments.get(cur).clss);
        if (SettingsActivity.isPortraitModeEnabled() && recommendTab) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        // 非竖屏推荐页：恢复 BaseActivity 的设备默认方向，不强制横屏
        if (tv.biliclassic.util.DeviceUtil.isTv(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (tv.biliclassic.util.SdkHelper.getBooleanResource(getResources(), R.bool.is_tablet)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else if (shouldEnableLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (isHardwareKeyboardDevice()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem loginItem = menu.findItem(R.id.menu_login_logout);
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        String uname = SharedPreferencesUtil.getString("uname", "");

        if (mid != 0 && cookies != null && cookies.length() > 0) {
            if (uname != null && uname.length() > 0) {
                loginItem.setTitle(getString(R.string.login_or_logout));
            }
        } else {
            loginItem.setTitle(getString(R.string.login_or_logout));
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_login_logout) {
            long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            if (mid != 0) {
                showMenuLogoutDialog();
            } else {
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            }
            return true;
        } else if (id == R.id.menu_favorite_list) {
            startActivity(new Intent(MainActivity.this, FavoriteFolderListActivity.class));
            return true;
        } else if (id == R.id.menu_video_history_list) {
            startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            return true;
        } else if (id == R.id.menu_preferences) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_help) {
            startActivity(new Intent(MainActivity.this, AboutActivity.class));
            return true;
        } else if (id == R.id.menu_exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkLegacyVersionCompatibility() {
        if (DeviceInfoUtil.isLegacy) {
            boolean isLegacyDevice = DeviceInfoUtil.isLegacyDevice();
            if (!isLegacyDevice) {
                new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(DialogUtil.wrap(this)))
                        .setTitle(getString(R.string.version_notice))
                        .setMessage(getString(R.string.legacy_version_suggestion))
                        .setPositiveButton("立即下载", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setData(Uri.parse("http://www.biliclassic.cn/"));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.download_full_version_hint), Toast.LENGTH_LONG).show();
                                }
                            }
                        })
                        .setNegativeButton("忽略", null)
                        .setCancelable(true)
                        .show();
            }
        }
    }

    private class ViewPagerAdapter extends FragmentStatePagerAdapter {
        private Fragment mCurrentFragment;

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            FragmentInfo info = mFragments.get(position);
            return Fragment.instantiate(MainActivity.this, info.clss.getName(), null);
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragments.get(position).title;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            // 首页/个人中心/番剧/时间线/推荐/动态内容较重，保留不销毁，避免划回时重建卡顿
            if (position == 0 || position == 1 || position == 2 || position == 3
                    || position == 4 || position == 5) {
                return;
            }
            super.destroyItem(container, position, object);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            if (object instanceof Fragment) {
                mCurrentFragment = (Fragment) object;
                mActiveFragment = (Fragment) object;
                tv.biliclassic.util.PerfLog.setPage(getPageTitle(position).toString());
            }
        }
    }
}