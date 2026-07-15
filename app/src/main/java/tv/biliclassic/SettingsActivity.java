package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import tv.biliclassic.subsettings.DecoderSettingsActivity;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;

public class SettingsActivity extends BaseActivity {

    // 播放器偏好
    private static final String KEY_PLAYER_PREFERENCE = "player_preference";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String KEY_DEFAULT_TAB = "default_tab";
    public static final String KEY_VIDEO_QUALITY = "video_quality";
    private static final String KEY_MODERN_MODE = "modern_mode";
    public static final String KEY_DECODER_TYPE = "decoder_type";
    private static final String KEY_BUILTIN_PLAYER = "use_builtin_player";
    private static final String KEY_ONLINE_PLAY = "online_play";

    // 视频画质（B站 API 标准值）
    private static final int QUALITY_360P = 16;
    private static final int QUALITY_480P = 32;
    private static final int QUALITY_720P = 64;
    private static final int QUALITY_1080P = 80;

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
    private static final int PLAYER_BUILTIN = 8;

    // 首页 Tab 索引
    private static final int TAB_PROFILE = 0;
    private static final int TAB_HOME = 1;
    private static final int TAB_NEW_ANIME = 2;
    private static final int TAB_TIMELINE = 3;
    private static final int TAB_RECOMMEND = 4;
    private static final int TAB_ABOUT = 5;

    // 解码方式
    private static final int DECODER_SYSTEM = 0;
    private static final int DECODER_IJK_HARD = 1;
    private static final int DECODER_IJK_SOFT = 2;

    // 内置播放器最低系统版本要求 (Android 2.3 / API 9)
    private static final int MIN_SDK_FOR_BUILTIN = 9;
    // IJK 硬解最低系统版本要求 (Android 4.1 / API 16)
    private static final int MIN_SDK_FOR_IJK_HARDWARE = 16;

    private TextView cacheSizeText;
    private LinearLayout clearCacheItem;
    private TextView playCacheSizeText;
    private LinearLayout clearPlayCacheItem;
    private LinearLayout playerChoiceItem;
    private TextView playerChoiceText;
    private LinearLayout decoderChoiceItem;
    private TextView decoderChoiceText;
    private LinearLayout defaultTabItem;
    private TextView defaultTabText;
    private LinearLayout videoQualityItem;
    private TextView videoQualityText;

    private TextView echoHoleText;
    private LinearLayout echoHoleItem;
    private int mLastEchoIndex = -1;

    private TextView crashLogSizeText;
    private LinearLayout clearCrashLogItem;

    private LinearLayout checkUpdateItem;
    private TextView checkUpdateText;

    private CheckBox checkboxAutoUpdate;
    private LinearLayout autoCheckUpdateItem;

    // 现代模式开关
    private CheckBox checkboxModernMode;
    private LinearLayout modernModeItem;

    // 在线播放开关
    private CheckBox checkboxOnlinePlay;
    private LinearLayout onlinePlayItem;
    private View onlinePlayWarning;

    // TV模式强制开关
    private CheckBox checkboxForceTvMode;
    private LinearLayout forceTvModeItem;
    private View forceTvModeWarning;

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
        LinearLayout threadItem = (LinearLayout) findViewById(R.id.image_thread_item);
        final TextView threadText = (TextView) findViewById(R.id.image_thread_text);
        updateImageThreadDisplay(threadText);

        if (threadItem != null) {
            threadItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showImageThreadDialog(threadText);
                }
            });
        }

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

                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启横屏模式，正在重启..." : "已关闭横屏模式，正在重启...",
                            Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
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

        // 在线播放开关 - 低版本完全隐藏
        checkboxOnlinePlay = (CheckBox) findViewById(R.id.checkbox_online_play);
        onlinePlayItem = (LinearLayout) findViewById(R.id.online_play_item);
        onlinePlayWarning = findViewById(R.id.online_play_warning);

        if (onlinePlayItem != null) {
            if (!isBuiltinPlayerSupported()) {
                onlinePlayItem.setVisibility(View.GONE);
                if (onlinePlayWarning != null) {
                    onlinePlayWarning.setVisibility(View.GONE);
                }
                SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, false);
            } else {
                onlinePlayItem.setVisibility(View.VISIBLE);
                if (onlinePlayWarning != null) {
                    onlinePlayWarning.setVisibility(View.VISIBLE);
                }

                boolean onlinePlayEnabled = SharedPreferencesUtil.getBoolean(KEY_ONLINE_PLAY, true);
                if (!SharedPreferencesUtil.contains(KEY_ONLINE_PLAY)) {
                    onlinePlayEnabled = true;
                    SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, true);
                }
                checkboxOnlinePlay.setChecked(onlinePlayEnabled);

                checkboxOnlinePlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            int playerPref = getPlayerPreference();
                            if (playerPref != PLAYER_BUILTIN) {
                                new AlertDialog.Builder(SettingsActivity.this)
                                        .setTitle("提示")
                                        .setMessage("在线播放需要配合内置播放器使用。\n\n是否切换到内置播放器并开启在线播放？")
                                        .setPositiveButton("切换并开启", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                SharedPreferencesUtil.putInt(KEY_PLAYER_PREFERENCE, PLAYER_BUILTIN);
                                                updatePlayerChoiceDisplay();
                                                SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, true);
                                                checkboxOnlinePlay.setChecked(true);
                                                Toast.makeText(SettingsActivity.this, "已切换到内置播放器并开启在线播放", Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                checkboxOnlinePlay.setChecked(false);
                                            }
                                        })
                                        .show();
                                return;
                            }

                            SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, true);
                            Toast.makeText(SettingsActivity.this, "已开启在线播放模式", Toast.LENGTH_SHORT).show();
                        } else {
                            SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, false);
                            Toast.makeText(SettingsActivity.this, "已关闭在线播放模式", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                onlinePlayItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxOnlinePlay.toggle();
                    }
                });
            }
        }

        // TV模式强制开关
        checkboxForceTvMode = (CheckBox) findViewById(R.id.checkbox_force_tv_mode);
        forceTvModeItem = (LinearLayout) findViewById(R.id.force_tv_mode_item);
        forceTvModeWarning = findViewById(R.id.force_tv_mode_warning);

        if (forceTvModeItem != null) {
            if (Build.VERSION.SDK_INT < 14) {
                forceTvModeItem.setVisibility(View.GONE);
                if (forceTvModeWarning != null) {
                    forceTvModeWarning.setVisibility(View.GONE);
                }
                SharedPreferencesUtil.putBoolean("force_tv_mode", false);
            } else {
                forceTvModeItem.setVisibility(View.VISIBLE);
                if (forceTvModeWarning != null) {
                    forceTvModeWarning.setVisibility(View.VISIBLE);
                }

                boolean forceTvMode = SharedPreferencesUtil.getBoolean("force_tv_mode", false);
                checkboxForceTvMode.setChecked(forceTvMode);

                checkboxForceTvMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferencesUtil.putBoolean("force_tv_mode", isChecked);
                        Toast.makeText(SettingsActivity.this,
                                isChecked ? "已开启TV模式强制开关，重启后生效" : "已关闭TV模式强制开关，重启后生效",
                                Toast.LENGTH_SHORT).show();
                    }
                });

                forceTvModeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxForceTvMode.toggle();
                    }
                });
            }
        }

        // 头像缓存管理
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

        // Cookie 导入导出
        LinearLayout cookieItem = (LinearLayout) findViewById(R.id.cookie_manage_item);
        if (cookieItem != null) {
            cookieItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCookieDialog();
                }
            });
        }

        // 回声洞
        echoHoleText = (TextView) findViewById(R.id.echo_hole_text);
        echoHoleItem = (LinearLayout) findViewById(R.id.echo_hole_item);

        if (echoHoleItem != null) {
            echoHoleItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadEchoHole();
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

        // 解码方式选择
        decoderChoiceItem = (LinearLayout) findViewById(R.id.decoder_choice_item);
        decoderChoiceText = (TextView) findViewById(R.id.decoder_choice_text);

        if (decoderChoiceItem != null) {
            updateDecoderChoiceDisplay();
            decoderChoiceItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDecoderChoiceDialog();
                }
            });
        }

        // 解码设置入口
        LinearLayout decoderSettingsItem = (LinearLayout) findViewById(R.id.decoder_settings_item);
        if (decoderSettingsItem != null) {
            decoderSettingsItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, DecoderSettingsActivity.class));
                }
            });
        }

        // 视频渲染方式选择 - 仅 API 14+ 可用
        LinearLayout rendererTypeItem = (LinearLayout) findViewById(R.id.renderer_type_item);
        final TextView rendererTypeText = (TextView) findViewById(R.id.renderer_type_text);
        if (rendererTypeItem != null && rendererTypeText != null) {
            if (Build.VERSION.SDK_INT < 14) {
                rendererTypeItem.setVisibility(View.GONE);
            } else {
                updateRendererTypeDisplay(rendererTypeText);
                rendererTypeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showRendererTypeDialog(rendererTypeText);
                    }
                });
            }
        }

        // 弹幕引擎选择
        LinearLayout danmakuEngineItem = (LinearLayout) findViewById(R.id.danmaku_engine_item);
        final TextView danmakuEngineText = (TextView) findViewById(R.id.danmaku_engine_text);
        if (danmakuEngineItem != null && danmakuEngineText != null) {
            updateDanmakuEngineDisplay(danmakuEngineText);
            danmakuEngineItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDanmakuEngineDialog(danmakuEngineText);
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

    // 判断是否支持 IJK 硬解 (Android 4.1+)
    private static boolean isIjkHardwareSupported() {
        return Build.VERSION.SDK_INT >= MIN_SDK_FOR_IJK_HARDWARE;
    }

    // 获取在线播放状态
    public static boolean isOnlinePlayEnabled() {
        // Android 2.3+ 且使用内置播放器时，默认开启在线播放
        boolean defaultEnabled = isBuiltinPlayerSupported() && getPlayerPreference() == PLAYER_BUILTIN;
        return SharedPreferencesUtil.getBoolean(KEY_ONLINE_PLAY, defaultEnabled);
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

    // 判断设备是否支持内置播放器（IJK V3 需要 Android 2.3+）
    private static boolean isBuiltinPlayerSupported() {
        return Build.VERSION.SDK_INT >= MIN_SDK_FOR_BUILTIN;
    }

    // 获取默认播放器偏好（低版本强制非内置）
    public static int getDefaultPlayerPreference() {
        if (!isBuiltinPlayerSupported()) {
            return PLAYER_AUTO;
        }
        return PLAYER_BUILTIN;
    }

    // 获取当前播放器偏好（带版本适配）
    public static int getPlayerPreference() {
        int pref = SharedPreferencesUtil.getInt(KEY_PLAYER_PREFERENCE, getDefaultPlayerPreference());
        if (pref == PLAYER_BUILTIN && !isBuiltinPlayerSupported()) {
            SharedPreferencesUtil.putInt(KEY_PLAYER_PREFERENCE, PLAYER_AUTO);
            return PLAYER_AUTO;
        }
        return pref;
    }

    // 获取播放器显示名称（带低版本提示）
    public static String getPlayerDisplayName() {
        int preference = getPlayerPreference();
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
            case PLAYER_BUILTIN:
                return isBuiltinPlayerSupported() ? "内置播放器" : "内置播放器 (不可用)";
            case PLAYER_SYSTEM:
            default:
                return "系统播放器";
        }
    }

    // 获取播放器包名
    public static String getPlayerPackageName() {
        int preference = getPlayerPreference();
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

    // 获取解码方式
    public static int getDecoderType() {
        // 默认值：4.1 以下默认软解，4.1 以上默认硬解
        int defaultDecoder = isIjkHardwareSupported() ? DECODER_IJK_HARD : DECODER_IJK_SOFT;
        int saved = SharedPreferencesUtil.getInt(KEY_DECODER_TYPE, defaultDecoder);

        // 如果保存的值是硬解但设备不支持，自动修正为软解
        if (saved == DECODER_IJK_HARD && !isIjkHardwareSupported()) {
            saved = DECODER_IJK_SOFT;
            SharedPreferencesUtil.putInt(KEY_DECODER_TYPE, saved);
        }

        return saved;
    }

    public static boolean useBuiltinPlayer() {
        return SharedPreferencesUtil.getBoolean(KEY_BUILTIN_PLAYER, Build.VERSION.SDK_INT >= 9);
    }

    // 视频画质选择
    private void showVideoQualityDialog() {
        final String[] qualities = {"360P 流畅", "480P 清晰", "720P 高清", "1080P 超清"};
        final int[] qualityValues = {QUALITY_360P, QUALITY_480P, QUALITY_720P, QUALITY_1080P};
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
        if (quality == QUALITY_1080P) {
            videoQualityText.setText("1080P 超清");
        } else if (quality == QUALITY_720P) {
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

    // 获取渲染方式
    public static int getRendererType() {
        if (Build.VERSION.SDK_INT < 14) {
            return 0; // TextureView 需要 API 14+
        }
        return SharedPreferencesUtil.getInt(SharedPreferencesUtil.RENDERER_TYPE, 1);
    }

    // 渲染方式选择
    private void showRendererTypeDialog(final TextView textView) {
        final String[] modes = {"SurfaceView", "TextureView"};
        final int[] values = {0, 1};
        int current = getRendererType();

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("视频渲染方式")
                .setSingleChoiceItems(modes, checkedIndex, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.RENDERER_TYPE, values[which]);
                        updateRendererTypeDisplay(textView);
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + modes[which],
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateRendererTypeDisplay(TextView textView) {
        int type = getRendererType();
        textView.setText(type == 1 ? "TextureView" : "SurfaceView");
    }

    // 弹幕引擎选择
    private void showDanmakuEngineDialog(final TextView textView) {
        final String[] modes = {"完整版（DanmakuFlameMaster）", "简易版（BT-5弹幕引擎）"};
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, 0);
        int checkedIndex = Math.min(current, 1);

        new AlertDialog.Builder(this)
                .setTitle("弹幕引擎")
                .setSingleChoiceItems(modes, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, which);
                        updateDanmakuEngineDisplay(textView);
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + modes[which],
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDanmakuEngineDisplay(TextView textView) {
        int mode = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, 0);
        textView.setText(mode == 1 ? "简易版（BT-5弹幕引擎）" : "完整版");
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

    // 播放器选择对话框
    private void showPlayerChoiceDialog() {
        final String[] allPlayers = {"内置播放器", "自动检测", "MX Player (免费版)", "MX Player (专业版)", "MoboPlayer", "VLC", "VPlayer", "RockPlaye Liter", "QQ影音", "系统播放器"};
        final int[] allValues = {PLAYER_BUILTIN, PLAYER_AUTO, PLAYER_MX_AD, PLAYER_MX_PRO, PLAYER_MOBO, PLAYER_VLC, PLAYER_VPLAYER, PLAYER_ROCKPLAYER, PLAYER_QQPLAYER, PLAYER_SYSTEM};

        // 低版本过滤掉内置播放器
        ArrayList filteredPlayers = new ArrayList();
        ArrayList filteredValues = new ArrayList();

        for (int i = 0; i < allPlayers.length; i++) {
            if (allValues[i] == PLAYER_BUILTIN && !isBuiltinPlayerSupported()) {
                continue;
            }
            filteredPlayers.add(allPlayers[i]);
            filteredValues.add(Integer.valueOf(allValues[i]));
        }

        final String[] players = (String[]) filteredPlayers.toArray(new String[filteredPlayers.size()]);
        final int[] playerValues = new int[filteredValues.size()];
        for (int i = 0; i < filteredValues.size(); i++) {
            playerValues[i] = ((Integer) filteredValues.get(i)).intValue();
        }

        int currentPreference = getPlayerPreference();

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
                        if (newPreference == PLAYER_BUILTIN) {
                            tip += "，使用内置播放器";
                        } else if (newPreference == PLAYER_SYSTEM) {
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

    private void updatePlayerChoiceDisplay() {
        String displayName = getPlayerDisplayName();
        playerChoiceText.setText(displayName);
    }

    // 解码方式显示
    private void updateDecoderChoiceDisplay() {
        int decoder = getDecoderType();
        switch (decoder) {
            case DECODER_SYSTEM:
                decoderChoiceText.setText("系统解码器");
                break;
            case DECODER_IJK_HARD:
            default:
                decoderChoiceText.setText("IJK 硬解");
                break;
            case DECODER_IJK_SOFT:
                decoderChoiceText.setText("IJK 软解");
                break;
        }
    }

    // 解码方式选择对话框
    private void showDecoderChoiceDialog() {
        final String[] decoders;
        final int[] decoderValues;

        if (isIjkHardwareSupported()) {
            // Android 4.1+ 显示三个选项
            decoders = new String[]{"系统解码器", "IJK 硬解", "IJK 软解"};
            decoderValues = new int[]{DECODER_SYSTEM, DECODER_IJK_HARD, DECODER_IJK_SOFT};
        } else {
            // Android 4.1 以下只显示两个选项
            decoders = new String[]{"系统解码器", "IJK 软解"};
            decoderValues = new int[]{DECODER_SYSTEM, DECODER_IJK_SOFT};
        }

        int currentDecoder = getDecoderType();

        int checkedIndex = 0;
        for (int i = 0; i < decoderValues.length; i++) {
            if (decoderValues[i] == currentDecoder) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择解码方式")
                .setSingleChoiceItems(decoders, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newDecoder = decoderValues[which];
                        SharedPreferencesUtil.putInt(KEY_DECODER_TYPE, newDecoder);
                        updateDecoderChoiceDisplay();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 图片加载线程
    private void updateImageThreadDisplay(TextView textView) {
        if (textView == null) return;
        int threads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 1);
        textView.setText(threads + "线程");
    }

    private void showImageThreadDialog(final TextView textView) {
        final String[] items = {"单线程", "三线程", "自定线程"};
        final int[] values = {1, 3, -1};
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 1);

        int checkedIndex = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { checkedIndex = i; break; }
        }
        if (checkedIndex == 1 && current != 3) checkedIndex = 2;

        new AlertDialog.Builder(this)
                .setTitle("图片加载线程")
                .setSingleChoiceItems(items, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 2) {
                            showCustomThreadDialog(textView);
                        } else {
                            SharedPreferencesUtil.putInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, values[which]);
                            updateImageThreadDisplay(textView);
                            Toast.makeText(SettingsActivity.this, "重启应用后生效", Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomThreadDialog(final TextView textView) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 3);
        input.setText(String.valueOf(current == -1 ? 3 : current));

        new AlertDialog.Builder(this)
                .setTitle("自定义线程数（1-15）")
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String s = input.getText().toString().trim();
                        if (s.length() == 0) return;
                        try {
                            final int val = Integer.parseInt(s);
                            if (val < 1 || val > 15) {
                                Toast.makeText(SettingsActivity.this, "请输入1-15之间的数字", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (val > 5) {
                                new AlertDialog.Builder(SettingsActivity.this)
                                        .setTitle("警告：线程数偏大")
                                        .setMessage("当前设置 " + val + " 个线程，超过安全建议值（5）。部分手机可能出现频繁卡顿甚至闪退的问题。若遇到此类问题，建议回到此处重新调低。\n\n确定继续吗？")
                                        .setPositiveButton("仍然设置", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int w) {
                                                saveThreadValue(val, textView);
                                            }
                                        })
                                        .setNegativeButton("取消", null)
                                        .show();
                            } else {
                                saveThreadValue(val, textView);
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(SettingsActivity.this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveThreadValue(int val, TextView textView) {
        SharedPreferencesUtil.putInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, val);
        updateImageThreadDisplay(textView);
        Toast.makeText(SettingsActivity.this, "重启应用后生效", Toast.LENGTH_SHORT).show();
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

    private boolean isLoggedIn() {
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        return mid != 0 && cookies != null && cookies.length() > 0;
    }

    private void showCookieDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cookie管理")
                .setItems(new String[]{"保存到本地", "复制到剪切板", "从本地导入", "从剪切板导入"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                if (!checkLogin()) return;
                                exportCookieToFile();
                                break;
                            case 1:
                                if (!checkLogin()) return;
                                exportCookieToClipboard();
                                break;
                            case 2: importCookieFromFile(); break;
                            case 3: importCookieFromClipboard(); break;
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean checkLogin() {
        if (!isLoggedIn()) {
            Toast.makeText(this, "请先登录的说~", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String getCookieJson() {
        JSONObject json = new JSONObject();
        try {
            String cookies = SharedPreferencesUtil.getString("cookies", "");
            String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
            json.put("cookies", cookies);
            json.put("refresh_token", refreshToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json.toString();
    }

    private File getCookieSaveFile() {
        File dir;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            dir = new File(Environment.getExternalStorageDirectory(), "BiliClassic");
        } else {
            dir = getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "cookie_backup.json");
    }

    private void exportCookieToFile() {
        try {
            File file = getCookieSaveFile();
            java.io.FileWriter fw = new java.io.FileWriter(file);
            fw.write(getCookieJson());
            fw.close();
            Toast.makeText(this, "已保存到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportCookieToClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setText(getCookieJson());
            Toast.makeText(this, "已复制到剪切板", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void importCookieFromFile() {
        File file = getCookieSaveFile();
        if (!file.exists()) {
            Toast.makeText(this, "未找到备份文件: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            applyCookieJson(sb.toString());
        } catch (Exception e) {
            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importCookieFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        final String clipText = cm.getText() != null ? cm.getText().toString() : "";
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(clipText);
        input.setMinLines(3);

        new AlertDialog.Builder(this)
                .setTitle("粘贴Cookie内容")
                .setView(input)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyCookieJson(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyCookieJson(String jsonStr) {
        if (jsonStr == null || jsonStr.length() == 0) {
            Toast.makeText(this, "内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            String cookies = json.optString("cookies", "");
            if (cookies == null || cookies.length() == 0) {
                Toast.makeText(this, "无效的Cookie数据", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferencesUtil.putString("cookies", cookies);
            String refreshToken = json.optString("refresh_token", "");
            if (refreshToken != null && refreshToken.length() > 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken);
            }
            String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
            if (mid != null && mid.length() > 0) {
                try {
                    SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                } catch (NumberFormatException e) {
                }
            }
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
            if (csrf != null && csrf.length() > 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);
            }
            NetWorkUtil.refreshHeaders();

            if (isLoggedIn()) {
                Toast.makeText(this, "导入成功，已登录", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "导入完成，但登录状态异常，请检查Cookie是否有效", Toast.LENGTH_LONG).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "格式错误，请检查", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

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

    // 回声洞
    private void loadEchoHole() {
        echoHoleText.setText("嘿咻…嘿咻…");
        echoHoleItem.setEnabled(false);
        new Thread(new Runnable() {
            public void run() {
                try {
                    URL url = new URL("http://www.biliclassic.cn/api/echo.json");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("GET");
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    final String jsonStr = sb.toString();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            echoHoleText.setText("随一个");
                            echoHoleItem.setEnabled(true);
                            if (isFinishing()) return;
                            try {
                                JSONArray arr = new JSONArray(jsonStr);
                                if (arr.length() > 0) {
                                    int idx = (int) (Math.random() * arr.length());
                                    if (arr.length() > 1) {
                                        while (idx == mLastEchoIndex) {
                                            idx = (int) (Math.random() * arr.length());
                                        }
                                    }
                                    mLastEchoIndex = idx;
                                    JSONObject item = arr.getJSONObject(idx);
                                    String text = item.optString("text", "");
                                    String author = item.optString("author", "匿名");
                                    String device = item.optString("device", null);
                                    String time = item.optString("time", "未知");
                                    String msg = text + "\n\n" + author;
                                    if (device != null && device.length() > 0) {
                                        msg += "\n来自 " + device;
                                    }
                                    msg += "\n" + time;
                                    new AlertDialog.Builder(SettingsActivity.this)
                                            .setTitle("回声洞")
                                            .setMessage(msg)
                                            .setPositiveButton("关闭", null)
                                            .show();
                                } else {
                                    Toast.makeText(SettingsActivity.this, "回声洞暂无内容", Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                Toast.makeText(SettingsActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (isFinishing()) return;
                            echoHoleText.setText("随一个");
                            echoHoleItem.setEnabled(true);
                            Toast.makeText(SettingsActivity.this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
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