package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;

public class SettingsActivity extends BaseActivity {

    private static final String KEY_PLAYER_PREFERENCE = "player_preference";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String KEY_DEFAULT_TAB = "default_tab";
    private static final String KEY_VIDEO_QUALITY = "video_quality";
    private static final String KEY_MODERN_MODE = "modern_mode";

    // 视频画质（B站 API 标准值）
    private static final int QUALITY_360P = 16;
    private static final int QUALITY_480P = 32;
    private static final int QUALITY_720P = 64;

    // 播放器偏好值
    private static final int PLAYER_AUTO = -1;
    private static final int PLAYER_MX_AD = 0;
    private static final int PLAYER_MX_PRO = 1;
    private static final int PLAYER_MOBO = 2;
    private static final int PLAYER_VLC = 3;
    private static final int PLAYER_VPLAYER = 4;
    private static final int PLAYER_ROCKPLAYER = 5;
    private static final int PLAYER_QQPLAYER = 6;
    private static final int PLAYER_SYSTEM = 7;

    // 首页 Tab 索引
    private static final int TAB_PROFILE = 0;
    private static final int TAB_HOME = 1;
    private static final int TAB_NEW_ANIME = 2;
    private static final int TAB_TIMELINE = 3;
    private static final int TAB_RECOMMEND = 4;
    private static final int TAB_ABOUT = 5;

    private TextView cacheSizeText;
    private LinearLayout clearCacheItem;
    private TextView playCacheSizeText;
    private LinearLayout clearPlayCacheItem;
    private LinearLayout playerChoiceItem;
    private TextView playerChoiceText;
    private LinearLayout defaultTabItem;
    private TextView defaultTabText;
    private LinearLayout videoQualityItem;
    private TextView videoQualityText;

    private TextView crashLogSizeText;
    private LinearLayout clearCrashLogItem;

    private LinearLayout checkUpdateItem;
    private TextView checkUpdateText;

    private CheckBox checkboxAutoUpdate;
    private LinearLayout autoCheckUpdateItem;

    // 现代模式开关
    private CheckBox checkboxModernMode;
    private LinearLayout modernModeItem;

    private Handler mainHandler = new Handler();

    private int currentVersionCode = -1;
    private String currentVersionName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        try {
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            currentVersionCode = 0;
            currentVersionName = "0.0.0";
        }

        ImageView btnBack = (ImageView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        // 图片加载线程数设置
        RadioGroup rgThreads = (RadioGroup) findViewById(R.id.rg_threads);
        int currentThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        boolean isLowMemory = isLowMemoryDevice();

        if (currentThreads == 1 || (currentThreads == 0 && isLowMemory)) {
            rgThreads.check(R.id.rb_single);
        } else {
            rgThreads.check(R.id.rb_multi);
        }

        rgThreads.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int threads = (checkedId == R.id.rb_single) ? 1 : 3;
                SharedPreferencesUtil.putInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, threads);
                Toast.makeText(SettingsActivity.this, "重启应用后生效", Toast.LENGTH_SHORT).show();
            }
        });

        // 横屏适配开关
        final CheckBox landscapeCheckbox = (CheckBox) findViewById(R.id.checkbox_landscape);
        LinearLayout landscapeItem = (LinearLayout) findViewById(R.id.landscape_item);

        if (landscapeCheckbox != null) {
            boolean landscapeEnabled = SharedPreferencesUtil.getBoolean(KEY_LANDSCAPE_ENABLED, true);
            landscapeCheckbox.setChecked(landscapeEnabled);

            landscapeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(KEY_LANDSCAPE_ENABLED, isChecked);

                    if (isLandscapeDevice()) {
                        String tip = isChecked ? "已开启横屏模式，正在重启..." : "已关闭横屏模式，正在重启...";
                        Toast.makeText(SettingsActivity.this, tip, Toast.LENGTH_SHORT).show();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                                System.exit(0);
                            }
                        }, 500);
                    } else {
                        Toast.makeText(SettingsActivity.this, "设置已保存，重启后生效", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            if (landscapeItem != null) {
                landscapeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        landscapeCheckbox.toggle();
                    }
                });
            }
        }

        // 现代模式开关
        checkboxModernMode = (CheckBox) findViewById(R.id.checkbox_modern_mode);
        modernModeItem = (LinearLayout) findViewById(R.id.modern_mode_item);

        if (checkboxModernMode != null) {
            boolean modernModeEnabled = SharedPreferencesUtil.getBoolean(KEY_MODERN_MODE, false);
            checkboxModernMode.setChecked(modernModeEnabled);

            checkboxModernMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(KEY_MODERN_MODE, isChecked);
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启现代模式，重启后生效" : "已关闭现代模式，重启后生效",
                            Toast.LENGTH_SHORT).show();
                }
            });

            if (modernModeItem != null) {
                modernModeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxModernMode.toggle();
                    }
                });
            }
        }

        // 头像/图片缓存管理（整合）
        cacheSizeText = (TextView) findViewById(R.id.cache_size);
        clearCacheItem = (LinearLayout) findViewById(R.id.clear_cache_item);

        updateCacheSize();

        clearCacheItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearCacheDialog();
            }
        });

        // 播放缓存管理
        playCacheSizeText = (TextView) findViewById(R.id.play_cache_size);
        clearPlayCacheItem = (LinearLayout) findViewById(R.id.clear_play_cache_item);

        updatePlayCacheSize();

        if (clearPlayCacheItem != null) {
            clearPlayCacheItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showClearPlayCacheDialog();
                }
            });
        }

        // 播放器选择
        playerChoiceItem = (LinearLayout) findViewById(R.id.player_choice_item);
        playerChoiceText = (TextView) findViewById(R.id.player_choice_text);

        if (playerChoiceItem != null) {
            updatePlayerChoiceDisplay();
            playerChoiceItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPlayerChoiceDialog();
                }
            });
        }

        // 默认首页选择
        defaultTabItem = (LinearLayout) findViewById(R.id.default_tab_item);
        defaultTabText = (TextView) findViewById(R.id.default_tab_text);

        if (defaultTabItem != null) {
            updateDefaultTabDisplay();
            defaultTabItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDefaultTabDialog();
                }
            });
        }

        // 视频画质选择
        videoQualityItem = (LinearLayout) findViewById(R.id.video_quality_item);
        videoQualityText = (TextView) findViewById(R.id.video_quality_text);

        if (videoQualityItem != null) {
            updateVideoQualityDisplay();
            videoQualityItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showVideoQualityDialog();
                }
            });
        }

        // 设备信息入口
        LinearLayout deviceInfoItem = (LinearLayout) findViewById(R.id.device_info_item);
        if (deviceInfoItem != null) {
            deviceInfoItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, DeviceInfoActivity.class));
                }
            });
        }

        // 崩溃日志管理
        crashLogSizeText = (TextView) findViewById(R.id.crash_log_size);
        clearCrashLogItem = (LinearLayout) findViewById(R.id.clear_crash_log_item);

        updateCrashLogSize();

        if (clearCrashLogItem != null) {
            clearCrashLogItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showClearCrashLogDialog();
                }
            });
        }

        // 检查更新
        checkUpdateItem = (LinearLayout) findViewById(R.id.check_update_item);
        checkUpdateText = (TextView) findViewById(R.id.check_update_text);

        if (checkUpdateItem != null) {
            checkUpdateItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkForUpdate();
                }
            });
        }

        // 自动检查更新开关
        checkboxAutoUpdate = (CheckBox) findViewById(R.id.checkbox_auto_update);
        autoCheckUpdateItem = (LinearLayout) findViewById(R.id.auto_check_update_item);

        boolean autoUpdateEnabled = SharedPreferencesUtil.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
        if (checkboxAutoUpdate != null) {
            checkboxAutoUpdate.setChecked(autoUpdateEnabled);
        }

        if (autoCheckUpdateItem != null) {
            autoCheckUpdateItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkboxAutoUpdate.toggle();
                    boolean current = checkboxAutoUpdate.isChecked();
                    SharedPreferencesUtil.putBoolean(KEY_AUTO_CHECK_UPDATE, current);
                    Toast.makeText(SettingsActivity.this,
                            current ? "已开启自动检查更新" : "已关闭自动检查更新",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // 获取现代模式状态
    public static boolean isModernModeEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_MODERN_MODE, false);
    }

    // 获取放送时间表 API URL
    public static String getTimelineApiUrl() {
        if (isModernModeEnabled()) {
            return "http://www.biliclassic.cn/api/schedulereal.json";
        } else {
            return "http://www.biliclassic.cn/api/schedule.json";
        }
    }

    // 获取新番专题 API URL
    public static String getNewAnimeApiUrl() {
        if (isModernModeEnabled()) {
            return "http://www.biliclassic.cn/api/newanimreal.json";
        } else {
            return "http://www.biliclassic.cn/api/newanim.json";
        }
    }

    // 视频画质选择
    private void showVideoQualityDialog() {
        final String[] qualities = {"360P 流畅", "480P 清晰", "720P 高清"};
        final int[] qualityValues = {QUALITY_360P, QUALITY_480P, QUALITY_720P};
        int currentQuality = getVideoQuality();

        int checkedIndex = 0;
        for (int i = 0; i < qualityValues.length; i++) {
            if (qualityValues[i] == currentQuality) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择视频画质")
                .setSingleChoiceItems(qualities, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newQuality = qualityValues[which];
                        SharedPreferencesUtil.putInt(KEY_VIDEO_QUALITY, newQuality);
                        updateVideoQualityDisplay();
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + qualities[which],
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateVideoQualityDisplay() {
        int quality = getVideoQuality();
        if (quality == QUALITY_720P) {
            videoQualityText.setText("720P 高清");
        } else if (quality == QUALITY_480P) {
            videoQualityText.setText("480P 清晰");
        } else {
            videoQualityText.setText("360P 流畅");
        }
    }

    public static int getVideoQuality() {
        return SharedPreferencesUtil.getInt(KEY_VIDEO_QUALITY, QUALITY_360P);
    }

    // 默认首页选择
    public static int getDefaultTab() {
        return SharedPreferencesUtil.getInt(KEY_DEFAULT_TAB, TAB_NEW_ANIME);
    }

    private void updateDefaultTabDisplay() {
        String[] tabNames = {"个人中心", "分区导航", "新番专题", "放送时间表", "推荐视频", "关于我们"};
        int index = getDefaultTab();
        if (index >= 0 && index < tabNames.length) {
            defaultTabText.setText(tabNames[index]);
        } else {
            defaultTabText.setText("新番专题");
        }
    }

    private void showDefaultTabDialog() {
        final String[] tabNames = {"个人中心", "分区导航", "新番专题", "放送时间表", "推荐视频", "关于我们"};
        final int[] tabValues = {TAB_PROFILE, TAB_HOME, TAB_NEW_ANIME, TAB_TIMELINE, TAB_RECOMMEND, TAB_ABOUT};
        int currentIndex = getDefaultTab();

        int checkedIndex = 0;
        for (int i = 0; i < tabValues.length; i++) {
            if (tabValues[i] == currentIndex) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择默认首页")
                .setSingleChoiceItems(tabNames, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newIndex = tabValues[which];
                        SharedPreferencesUtil.putInt(KEY_DEFAULT_TAB, newIndex);
                        updateDefaultTabDisplay();
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + tabNames[which] + "，重启后生效",
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 播放器选择
    public static String getPlayerPackageName() {
        int preference = SharedPreferencesUtil.getInt(KEY_PLAYER_PREFERENCE, PLAYER_AUTO);
        switch (preference) {
            case PLAYER_MX_AD:
                return "com.mxtech.videoplayer.ad";
            case PLAYER_MX_PRO:
                return "com.mxtech.videoplayer.pro";
            case PLAYER_MOBO:
                return "com.clov4r.android.nil";
            case PLAYER_VLC:
                return "org.videolan.vlc";
            case PLAYER_VPLAYER:
                return "me.abitno.vplayer.t";
            case PLAYER_ROCKPLAYER:
                return "com.redirectin.rockplayer.android.unified.lite";
            case PLAYER_QQPLAYER:
                return "com.tencent.research.drop";
            case PLAYER_SYSTEM:
            case PLAYER_AUTO:
            default:
                return null;
        }
    }

    public static String getPlayerDisplayName() {
        int preference = SharedPreferencesUtil.getInt(KEY_PLAYER_PREFERENCE, PLAYER_AUTO);
        switch (preference) {
            case PLAYER_AUTO:
                return "自动检测";
            case PLAYER_MX_AD:
                return "MX Player (免费版)";
            case PLAYER_MX_PRO:
                return "MX Player (专业版)";
            case PLAYER_MOBO:
                return "MoboPlayer";
            case PLAYER_VLC:
                return "VLC";
            case PLAYER_VPLAYER:
                return "VPlayer";
            case PLAYER_ROCKPLAYER:
                return "RockPlaye Liter";
            case PLAYER_QQPLAYER:
                return "QQ影音";
            case PLAYER_SYSTEM:
            default:
                return "系统播放器";
        }
    }

    public static int getPlayerPreference() {
        return SharedPreferencesUtil.getInt(KEY_PLAYER_PREFERENCE, PLAYER_AUTO);
    }

    private void updatePlayerChoiceDisplay() {
        String displayName = getPlayerDisplayName();
        playerChoiceText.setText(displayName);
    }

    private void showPlayerChoiceDialog() {
        final String[] players = {"自动检测", "MX Player (免费版)", "MX Player (专业版)", "MoboPlayer", "VLC", "VPlayer", "RockPlaye Liter", "QQ影音", "系统播放器"};
        final int[] playerValues = {PLAYER_AUTO, PLAYER_MX_AD, PLAYER_MX_PRO, PLAYER_MOBO, PLAYER_VLC, PLAYER_VPLAYER, PLAYER_ROCKPLAYER, PLAYER_QQPLAYER, PLAYER_SYSTEM};
        int currentPreference = SharedPreferencesUtil.getInt(KEY_PLAYER_PREFERENCE, PLAYER_AUTO);

        int checkedIndex = 0;
        for (int i = 0; i < playerValues.length; i++) {
            if (playerValues[i] == currentPreference) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择默认播放器")
                .setSingleChoiceItems(players, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newPreference = playerValues[which];
                        SharedPreferencesUtil.putInt(KEY_PLAYER_PREFERENCE, newPreference);
                        updatePlayerChoiceDisplay();

                        String tip = "已切换为 " + players[which];
                        if (newPreference == PLAYER_SYSTEM) {
                            tip += "，低版本系统可能无法播放";
                        } else if (newPreference == PLAYER_AUTO) {
                            tip += "，将自动选择可用播放器";
                        }
                        Toast.makeText(SettingsActivity.this, tip, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 缓存管理
    private File getAvatarCacheFile() {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            try {
                File externalCache = new File(Environment.getExternalStorageDirectory(), "BiliClassic/avatar_cache");
                if (!externalCache.exists()) {
                    externalCache.mkdirs();
                }
                return new File(externalCache, "avatar_cache.jpg");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new File(getCacheDir(), "avatar_cache.jpg");
    }

    private long getAvatarCacheSize() {
        File avatarFile = getAvatarCacheFile();
        if (avatarFile.exists()) {
            return avatarFile.length();
        }
        return 0;
    }

    private long getAnimeCacheSize() {
        try {
            File animeCacheDir = new File(getCacheDir(), "anime_cache");
            if (!animeCacheDir.exists()) {
                return 0;
            }
            return getFolderSize(animeCacheDir);
        } catch (Exception e) {
            return 0;
        }
    }

    private long getFolderSize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                size += getFolderSize(file);
            } else {
                size += file.length();
            }
        }
        return size;
    }

    private long getTotalCacheSize() {
        return getAvatarCacheSize() + getAnimeCacheSize();
    }

    private void updateCacheSize() {
        long totalSize = getTotalCacheSize();
        if (totalSize > 0) {
            cacheSizeText.setText(formatFileSize(totalSize));
        } else {
            cacheSizeText.setText("无缓存");
        }
    }

    private void showClearCacheDialog() {
        long totalSize = getTotalCacheSize();
        String sizeText = formatFileSize(totalSize);
        new AlertDialog.Builder(this)
                .setTitle("清除图片缓存")
                .setMessage("将清除以下缓存：\n\n• 头像缓存\n• 番剧封面缓存\n\n共 " + sizeText + "，清除后下次启动会自动重新下载。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearAllCache();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearAllCache() {
        try {
            long freedSize = 0;
            int deletedCount = 0;

            File avatarFile = getAvatarCacheFile();
            if (avatarFile.exists()) {
                freedSize += avatarFile.length();
                if (avatarFile.delete()) {
                    deletedCount++;
                }
            }

            File animeCacheDir = new File(getCacheDir(), "anime_cache");
            if (animeCacheDir.exists()) {
                long size = getFolderSize(animeCacheDir);
                freedSize += size;
                deleteRecursive(animeCacheDir);
                deletedCount++;
            }

            Toast.makeText(this, "已清除 " + deletedCount + " 项缓存，释放 " + formatFileSize(freedSize), Toast.LENGTH_SHORT).show();
            updateCacheSize();

        } catch (Exception e) {
            Toast.makeText(this, "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    // 播放缓存管理
    private File getPlayCacheDir() {
        if (isSDCardAvailable()) {
            File sdCacheDir = new File(Environment.getExternalStorageDirectory(), "BiliClassic/cache");
            if (!sdCacheDir.exists()) {
                sdCacheDir.mkdirs();
            }
            return sdCacheDir;
        }
        return getCacheDir();
    }

    private boolean isSDCardAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private File[] getPlayCacheFiles() {
        File cacheDir = getPlayCacheDir();
        if (cacheDir == null || !cacheDir.exists()) {
            return new File[0];
        }

        File[] allFiles = cacheDir.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            return new File[0];
        }

        java.util.ArrayList<File> mp4Files = new java.util.ArrayList<File>();
        for (File file : allFiles) {
            if (file.isFile() && file.getName().endsWith(".mp4")) {
                mp4Files.add(file);
            }
        }

        return mp4Files.toArray(new File[mp4Files.size()]);
    }

    private long getPlayCacheTotalSize() {
        File[] cacheFiles = getPlayCacheFiles();
        long totalSize = 0;
        for (File file : cacheFiles) {
            totalSize += file.length();
        }
        return totalSize;
    }

    private int getPlayCacheFileCount() {
        return getPlayCacheFiles().length;
    }

    private void updatePlayCacheSize() {
        long totalSize = getPlayCacheTotalSize();
        int fileCount = getPlayCacheFileCount();

        if (totalSize > 0 && fileCount > 0) {
            String sizeText = formatFileSize(totalSize);
            playCacheSizeText.setText(sizeText + " (" + fileCount + "个视频)");
        } else {
            playCacheSizeText.setText("无播放缓存");
        }
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

    private void showClearPlayCacheDialog() {
        int fileCount = getPlayCacheFileCount();
        if (fileCount == 0) {
            Toast.makeText(this, "没有播放缓存", Toast.LENGTH_SHORT).show();
            return;
        }

        String totalSize = formatFileSize(getPlayCacheTotalSize());
        new AlertDialog.Builder(this)
                .setTitle("清除播放缓存")
                .setMessage("确定要清除所有播放缓存吗？\n" +
                        "共 " + fileCount + " 个视频文件，总计 " + totalSize + "\n" +
                        "清除后可释放存储空间。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearPlayCache();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearPlayCache() {
        try {
            File[] cacheFiles = getPlayCacheFiles();
            int deletedCount = 0;
            long freedSpace = 0;

            for (File file : cacheFiles) {
                if (file.exists()) {
                    freedSpace += file.length();
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }

            if (deletedCount > 0) {
                Toast.makeText(this, "已清除 " + deletedCount + " 个缓存文件，释放 " + formatFileSize(freedSpace), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "没有找到可清除的缓存文件", Toast.LENGTH_SHORT).show();
            }

            updatePlayCacheSize();

        } catch (Exception e) {
            Toast.makeText(this, "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    // 崩溃日志管理
    private File getCrashLogDir() {
        return new File(getFilesDir().getParentFile(), "crashlog");
    }

    private File[] getCrashLogFiles() {
        File crashDir = getCrashLogDir();
        if (crashDir == null || !crashDir.exists()) {
            return new File[0];
        }
        File[] files = crashDir.listFiles();
        return files == null ? new File[0] : files;
    }

    private long getCrashLogTotalSize() {
        File[] files = getCrashLogFiles();
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        return total;
    }

    private int getCrashLogFileCount() {
        return getCrashLogFiles().length;
    }

    private void updateCrashLogSize() {
        int count = getCrashLogFileCount();
        long size = getCrashLogTotalSize();
        if (count == 0) {
            crashLogSizeText.setText("无日志");
        } else {
            crashLogSizeText.setText(count + "个, " + formatFileSize(size));
        }
    }

    private void showClearCrashLogDialog() {
        int count = getCrashLogFileCount();
        if (count == 0) {
            Toast.makeText(this, "没有崩溃日志", Toast.LENGTH_SHORT).show();
            return;
        }

        String sizeText = formatFileSize(getCrashLogTotalSize());
        new AlertDialog.Builder(this)
                .setTitle("删除崩溃日志")
                .setMessage("确定要删除所有崩溃日志吗？\n共 " + count + " 个文件，总计 " + sizeText)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearCrashLog();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearCrashLog() {
        try {
            File[] files = getCrashLogFiles();
            int deleted = 0;
            for (File f : files) {
                if (f.delete()) {
                    deleted++;
                }
            }
            Toast.makeText(this, "已删除 " + deleted + " 个崩溃日志", Toast.LENGTH_SHORT).show();
            updateCrashLogSize();

            if (getCrashLogFileCount() == 0) {
                getSharedPreferences("crash", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_crash", false)
                        .commit();
            }
        } catch (Exception e) {
            Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 检查更新（使用 UpdateUtil）
    private void checkForUpdate() {
        checkUpdateText.setText("正在检查...");
        checkUpdateItem.setEnabled(false);

        UpdateUtil.checkUpdate(this, currentVersionCode, currentVersionName,
                new UpdateUtil.UpdateCallback() {
                    @Override
                    public void onCheckStart() {
                        // UI 已经在调用前设置了
                    }

                    @Override
                    public void onCheckComplete(boolean hasUpdate, String message) {
                        checkUpdateText.setText("检查完成");
                        checkUpdateItem.setEnabled(true);
                        if (!hasUpdate) {
                            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCheckFailed(String error) {
                        checkUpdateText.setText("检查完成");
                        checkUpdateItem.setEnabled(true);
                        Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}