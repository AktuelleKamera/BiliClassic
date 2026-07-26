package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.SharedPreferencesUtil;

public class MainActivity extends BaseActivity {

    private static final String KEY_LANDSCAPE_TIP_SHOWN = "landscape_tip_shown";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final int TIP_DELAY_MS = 1500;
    private static final long AUTO_CHECK_INTERVAL = 24 * 60 * 60 * 1000; // 24小时

    private ViewPager mPager;
    private List<FragmentInfo> mFragments = new ArrayList<FragmentInfo>();
    private Handler mHandler = new Handler();

    // 当前版本信息（缓存）
    private int currentVersionCode = -1;
    private String currentVersionName = "";

    // 空间震彩蛋相关
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
        setContentView(R.layout.activity_main);

        // 获取当前版本信息
        try {
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            currentVersionCode = 0;
            currentVersionName = "0.0.0";
        }

        // 检查上次崩溃日志
        checkAndShowCrashDialog();

        PagerTabStrip tabStrip = (PagerTabStrip) findViewById(R.id.pager_tab_strip);
        if (tabStrip != null) {
            tabStrip.setTabIndicatorColor(0xFFFCA3C5);
            tabStrip.setBackgroundColor(0xFFD86DA5);
            tabStrip.setTextColor(0xFFFFFFFF);
        }

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));
        mPager.setOffscreenPageLimit(4);

        // 添加 Tab（顺序：个人中心 → 分区导航 → 新番专题 → 放送时间表 → 推荐视频 → 关于我们）
        addTab("个人中心", ProfileFragment.class);
        addTab("分区导航", HomeFragment.class);
        addTab("新番专题", NewAnimeFragment.class);
        addTab("放送时间表", TimelineFragment.class);
        addTab("推荐视频", RecommendFragment.class);  // 新增，在关于前面
        addTab("关于我们", AboutFragment.class);

        // 默认选中“新番专题”（索引2）
        int defaultTab = SettingsActivity.getDefaultTab();
        mPager.setCurrentItem(defaultTab);

        ImageView btnSearch = (ImageView) findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
            }
        });

        // Logo：单击弹出菜单 + 快速连点5次触发空间震
        ImageView logo = (ImageView) findViewById(R.id.logo);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 正常行为：弹出菜单
                openOptionsMenu();

                // 彩蛋计数
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

        // 每次进入应用时自动删除视频缓存
        clearVideoCache();

        // 自动检查更新（默认开启）
        checkAutoUpdate();
    }

    // 自动检查更新

    /**
     * 检查是否需要自动更新
     */
    private void checkAutoUpdate() {
        boolean autoUpdateEnabled = SharedPreferencesUtil.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
        if (!autoUpdateEnabled) {
            return;
        }

        // 检查上次检查时间
        long lastCheckTime = SharedPreferencesUtil.getLong("last_auto_check_time", 0);
        long currentTime = System.currentTimeMillis();

        if (lastCheckTime == 0 || (currentTime - lastCheckTime) > AUTO_CHECK_INTERVAL) {
            SharedPreferencesUtil.putLong("last_auto_check_time", currentTime);
            doAutoCheckUpdate();
        }
    }

    /**
     * 执行自动检查更新
     */
    private void doAutoCheckUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                String versionJson = null;

                // 1. 优先从主站获取
                try {
                    java.net.URL url = new java.net.URL("http://www.biliclassic.cn/api/version.json");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
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
                } catch (Exception e) {
                    // 主站失败了呜呜
                }

                // 2. 如果失败，从 mongou666 的备用站获取
                if (!success) {
                    try {
                        java.net.URL url = new java.net.URL(
                                "http://7891vip.top/biliclassic/update.php");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);
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
                    } catch (Exception e) {
                        // 7891vip 也失败了？
                    }
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

    /**
     * 处理更新检查结果
     */
    private void handleUpdateCheckResult(String versionJson) {
        try {
            JSONObject json = new JSONObject(versionJson);

            // ========== 使用 version_code 校验 ==========
            int latestVersionCode = json.optInt("version_code", 0);
            String latestVersionName = json.optString("version", "");
            String downloadUrl = json.optString("download_url", "");
            boolean forceUpdate = json.optBoolean("force_update", false);
            int minSdk = json.optInt("min_sdk", 0);

            // 解析 changelog
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

            // 检查是否有更新
            boolean hasUpdate = false;

            if (latestVersionCode > 0) {
                // 使用 version_code 比较
                hasUpdate = (latestVersionCode > currentVersionCode);
            } else {
                // 降级方案：使用版本名比较
                hasUpdate = compareVersions(currentVersionName, latestVersionName);
            }

            // SDK 版本检查
            int sdkVersion = android.os.Build.VERSION.SDK_INT;
            if (minSdk > 0 && sdkVersion < minSdk) {
                // SDK 太低，不他喵的提示
                return;
            }

            // 有新版本则弹出提示
            if (hasUpdate) {
                showAutoUpdateDialog(latestVersionName, changelog, downloadUrl, forceUpdate);
            }

        } catch (Exception e) {
            // 解析失败，静默忽略
        }
    }

    /**
     * 自动更新发现新版本对话框
     */
    private void showAutoUpdateDialog(String versionName, String changelog, final String downloadUrl, boolean forceUpdate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
                    Toast.makeText(MainActivity.this, "下载地址无效", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (!forceUpdate) {
            builder.setNegativeButton("稍后", null);
        }

        builder.setCancelable(!forceUpdate);
        builder.show();
    }

    private String getVersionName() {
        return currentVersionName;
    }

    private int getVersionCode() {
        return currentVersionCode;
    }

    /**
     * 比较版本号，支持 -r数字 和 -fix 后缀
     */
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
     * 检查并显示崩溃对话框
     */
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

        new AlertDialog.Builder(this)
                .setTitle("上次程序异常退出")
                .setMessage("程序上次运行时发生了异常，是否查看详细信息？")
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

    /**
     * 空间震彩蛋
     * 你也看约战？
     */
    private void triggerSpaceQuake() {
        Toast.makeText(this, "空間震！", Toast.LENGTH_SHORT).show();

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

    /**
     * 清除所有视频缓存文件
     */
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

                    if (isSDCardAvailable()) {
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
        new AlertDialog.Builder(this)
                .setTitle("设备适配提示")
                .setMessage("您的设备已自动适配横屏模式，以获得更好的使用体验。\n\n如您不需要横屏，可在「设置」中关闭哦~")
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
                loginItem.setTitle("登录/注销");
            }
        } else {
            loginItem.setTitle("登录/注销");
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_login_logout) {
            long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            if (mid != 0) {
                // 使用可爱的二次元风格退出弹窗
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

    /**
     * 菜单退出登录的二次元风格弹窗
     */
    private void showMenuLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("真的要离开了吗…？")
                .setMessage("呜…你确定要退出登录吗？\n退出后就不能愉快地看番了哦 (；′⌒`)")
                .setPositiveButton("留下来", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "嗯嗯！留下来陪我们一起看番吧！(＾▽＾)", Toast.LENGTH_SHORT).show();
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

    /**
     * 执行菜单退出登录
     */
    private void doMenuLogout() {
        SharedPreferencesUtil.removeValue("cookies");
        SharedPreferencesUtil.removeValue("mid");
        SharedPreferencesUtil.removeValue("csrf");
        SharedPreferencesUtil.removeValue("refresh_token");

        Toast.makeText(this, "已退出登录…随时欢迎回来哦(´；ω；`)", Toast.LENGTH_SHORT).show();

        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    private class ViewPagerAdapter extends FragmentPagerAdapter {
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
    }
}