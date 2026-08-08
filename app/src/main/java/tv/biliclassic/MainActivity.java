package tv.biliclassic;

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
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.AnnouncementUtil;
import tv.biliclassic.util.DeviceInfoUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
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

    // 兼容 Android 1.5 获取 SDK 版本
    private int getSdkInt() {
        try {
            java.lang.reflect.Field field = android.os.Build.VERSION.class.getField("SDK_INT");
            return field.getInt(null);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = android.os.Build.VERSION.class.getField("SDK");
                return Integer.parseInt(field.get(null).toString());
            } catch (Exception ex) {
                return 0;
            }
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
                // 完全空文件 → 真·首次安装，记录版本号，让 setupShown=false 走初次使用流程
                SharedPreferencesUtil.putInt("last_version_code", currentVersionCode);
                lastVersionCode = currentVersionCode;
            }
        }

        if (!setupShown) {
            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra("mode", "first");
            startActivity(intent);
            finish();
            return;
        } else if (lastVersionCode > 0 && currentVersionCode > lastVersionCode) {
            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra("mode", "upgrade");
            startActivity(intent);
            finish();
            return;
        }

        NetWorkUtil.refreshHeaders();
        int sdkInt = getSdkInt();

        // Android 4.0+ 正常检测 TV 模式
        if (sdkInt >= 14 && tv.biliclassic.util.DeviceUtil.isTv(this)) {
            Intent intent = new Intent(this, tv.biliclassic.tv.TvMainActivity.class);
            startActivity(intent);
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
                    Toast.makeText(this, this.getString(R.string.mainactivity_toast_6a21), Toast.LENGTH_LONG).show();
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
            tabStrip.setTabIndicatorColor(0xFFFCA3C5);
            tabStrip.setBackgroundColor(0xFFD86DA5);
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
        addTab(getString(R.string.mainactivity_tab_recommend), RecommendFragment.class);
        addTab(getString(R.string.mainactivity_tab_about), AboutFragment.class);

        int targetTab = getIntent().getIntExtra("tab_index", -1);
        if (targetTab >= 0 && targetTab < mFragments.size()) {
            mPager.setCurrentItem(targetTab);
        } else {
            int defaultTab = SettingsActivity.getDefaultTab();
            mPager.setCurrentItem(defaultTab);
        }

        ImageView btnSearch = (ImageView) findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
            }
        });

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
                boolean success = false;
                String versionJson = null;

                try {
                    java.net.URL url = new java.net.URL("http://www.biliclassic.cn/api/version.json");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "BiliClassic");

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        java.io.InputStream is = conn.getInputStream();
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(is, "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        is.close();
                        versionJson = sb.toString();
                        success = true;
                    }
                    conn.disconnect();
                } catch (Exception e) {}

                if (!success) {
                    try {
                        java.net.URL url = new java.net.URL(
                                "http://7891vip.top/biliclassic/update.php");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(12000);
                        conn.setReadTimeout(12000);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", "BiliClassic");

                        int responseCode = conn.getResponseCode();
                        if (responseCode == 200) {
                            java.io.InputStream is = conn.getInputStream();
                            java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(is, "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();
                            is.close();
                            versionJson = sb.toString();
                            success = true;
                        }
                        conn.disconnect();
                    } catch (Exception e) {}
                }

                final String finalVersionJson = versionJson;
                final boolean finalSuccess = success;

                if (finalSuccess && finalVersionJson != null && finalVersionJson.length() > 0) {
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

            int sdkVersion = getSdkInt();
            if (minSdk > 0 && sdkVersion < minSdk) {
                return;
            }

            if (hasUpdate) {
                showAutoUpdateDialog(latestVersionName, changelog, downloadUrl, forceUpdate);
            }

        } catch (Exception e) {}
    }

    private void showAutoUpdateDialog(String versionName, String changelog, final String downloadUrl, boolean forceUpdate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(DialogUtil.wrap(this));
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
                    Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.mainactivity_toast_4e0b), Toast.LENGTH_SHORT).show();
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
        } catch (Throwable t) {
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
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

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.mainactivity_settitle_4e0a))
                .setMessage(getString(R.string.mainactivity_setmessage_7a0b))
                .setPositiveButton("查看", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(MainActivity.this, CrashReportActivity.class);
                        intent.putExtra("crash_info", crashLog);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("忽略", null)
                .show();
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
        Toast.makeText(this, this.getString(R.string.mainactivity_toast_7a7a), Toast.LENGTH_SHORT).show();

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
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.mainactivity_settitle_8bbe))
                .setMessage(getString(R.string.mainactivity_setmessage_60a8))
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
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (!mOptionsMenuOpen) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                boolean firstPress = (event.getRepeatCount() == 0);
                int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
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
    }

    private void addTab(String title, Class<? extends Fragment> clss) {
        mFragments.add(new FragmentInfo(title, clss));
        if (mPager.getAdapter() != null) {
            mPager.getAdapter().notifyDataSetChanged();
        }
    }

    public void setCurrentTab(int index) {
        if (mPager != null) {
            mPager.setCurrentItem(index);
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
                loginItem.setTitle(getString(R.string.mainactivity_settitle_767b));
            }
        } else {
            loginItem.setTitle(getString(R.string.mainactivity_settitle_767b));
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

    private void showMenuLogoutDialog() {
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.mainactivity_settitle_771f))
                .setMessage(getString(R.string.mainactivity_setmessage_545c))
                .setPositiveButton("留下来", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.mainactivity_toast_55ef), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("狠心离开", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doMenuLogout();
                        dialog.dismiss();
                    }
                })
                .setCancelable(true)
                .show();
    }

    private void checkLegacyVersionCompatibility() {
        if (DeviceInfoUtil.isLegacy) {
            boolean isLegacyDevice = DeviceInfoUtil.isLegacyDevice();
            if (!isLegacyDevice) {
                new AlertDialog.Builder(DialogUtil.wrap(this))
                        .setTitle(getString(R.string.mainactivity_settitle_7248))
                        .setMessage(getString(R.string.mainactivity_setmessage_68c0))
                        .setPositiveButton("立即下载", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setData(Uri.parse("http://www.biliclassic.cn/"));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.mainactivity_toast_8bf7), Toast.LENGTH_LONG).show();
                                }
                            }
                        })
                        .setNegativeButton("忽略", null)
                        .setCancelable(true)
                        .show();
            }
        }
    }

    private void doMenuLogout() {
        SharedPreferencesUtil.removeValue("cookies");
        SharedPreferencesUtil.removeValue("mid");
        SharedPreferencesUtil.removeValue("csrf");
        SharedPreferencesUtil.removeValue("refresh_token");

        Toast.makeText(this, this.getString(R.string.mainactivity_toast_5df2), Toast.LENGTH_SHORT).show();

        Intent intent = getIntent();
        finish();
        startActivity(intent);
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
            // 首页/个人中心/番剧/时间线/推荐内容较重，保留不销毁，避免划回时重建卡顿
            if (position == 0 || position == 1 || position == 2 || position == 3 || position == 4) {
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