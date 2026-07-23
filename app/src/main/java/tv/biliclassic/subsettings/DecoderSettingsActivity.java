package tv.biliclassic.subsettings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import tv.biliclassic.BaseActivity;
import tv.biliclassic.R;
import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class DecoderSettingsActivity extends BaseActivity {

    private static final String KEY_PICTQ_SIZE = "player_pictq_size";
    private static final String KEY_MAX_FPS = "player_max_fps";
    private static final String KEY_FRAMEDROP = "player_framedrop";
    private static final String KEY_SKIP_LOOP_FILTER = "codec_skip_loop_filter";
    private static final String KEY_SKIP_FRAME = "codec_skip_frame";
    private static final String KEY_VIDEO_DISABLED = "player_vn";
    private static final String KEY_AUDIO_DISABLED = "player_an";
    private static final String KEY_OPENSLES = "aout_opensles";

    // ---- UI ----
    private TextView textPictqSize;
    private TextView textMaxFps;
    private TextView textFramedrop;
    private TextView textSkipLoopFilter;
    private TextView textSkipFrame;
    private CheckBox checkboxVideoDisabled;
    private CheckBox checkboxAudioDisabled;
    private CheckBox checkboxOpenSLES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decoder_settings);

        ImageView btnBack = (ImageView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        initListItems();
        initCheckBoxItems();
        updateAllDisplays();
    }

    private void initListItems() {
        textPictqSize = (TextView) findViewById(R.id.text_pictq_size);
        textMaxFps = (TextView) findViewById(R.id.text_maxfps);
        textFramedrop = (TextView) findViewById(R.id.text_framedrop);
        textSkipLoopFilter = (TextView) findViewById(R.id.text_skip_loop_filter);
        textSkipFrame = (TextView) findViewById(R.id.text_skip_frame);

        setupListClick(R.id.item_pictq_size, KEY_PICTQ_SIZE,
                R.array.decoder_pictq_size_entries, R.array.decoder_pictq_size_values, "3", textPictqSize, "画面队列大小");
        setupListClick(R.id.item_maxfps, KEY_MAX_FPS,
                R.array.decoder_maxfps_entries, R.array.decoder_maxfps_values, "30", textMaxFps, "最大帧率");
        setupListClick(R.id.item_framedrop, KEY_FRAMEDROP,
                R.array.decoder_framedrop_entries, R.array.decoder_framedrop_values, "0", textFramedrop, "丢帧策略");
        setupListClick(R.id.item_skip_loop_filter, KEY_SKIP_LOOP_FILTER,
                R.array.decoder_skip_loop_filter_entries, R.array.decoder_skip_loop_filter_values, "48", textSkipLoopFilter, "跳过循环滤波");
        setupListClick(R.id.item_skip_frame, KEY_SKIP_FRAME,
                R.array.decoder_skip_frame_entries, R.array.decoder_skip_frame_values, "8", textSkipFrame, "跳过非参考帧");
    }

    private void initCheckBoxItems() {
        checkboxVideoDisabled = (CheckBox) findViewById(R.id.checkbox_video_disabled);
        checkboxAudioDisabled = (CheckBox) findViewById(R.id.checkbox_audio_disabled);
        checkboxOpenSLES = (CheckBox) findViewById(R.id.checkbox_opensles);

        setupCheckBoxClick(R.id.item_video_disabled, checkboxVideoDisabled, KEY_VIDEO_DISABLED, false);
        setupCheckBoxClick(R.id.item_audio_disabled, checkboxAudioDisabled, KEY_AUDIO_DISABLED, false);
        setupCheckBoxClick(R.id.item_opensles, checkboxOpenSLES, KEY_OPENSLES, false);
    }

    // ---- list处理 ----

    private void setupListClick(int itemId, final String key,
                                final int entriesResId, final int valuesResId,
                                final String defaultValue, final TextView valueView, final String title) {
        LinearLayout item = (LinearLayout) findViewById(itemId);
        if (item == null) return;
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showListDialog(key, entriesResId, valuesResId, defaultValue, valueView, title);
            }
        });
    }

    private void showListDialog(final String key, int entriesResId, final int valuesResId,
                                final String defaultValue, final TextView valueView, String title) {
        final String[] entries = getResources().getStringArray(entriesResId);
        final String[] values = getResources().getStringArray(valuesResId);
        String currentVal = SharedPreferencesUtil.getString(key, defaultValue);
        if (currentVal == null || currentVal.length() == 0) {
            currentVal = defaultValue;
        }

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentVal)) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(title)
                .setSingleChoiceItems(entries, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newVal = values[which];
                        SharedPreferencesUtil.putString(key, newVal);
                        updateListDisplay(valueView, entries, values, newVal);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateListDisplay(TextView view, String[] entries, String[] values, String currentVal) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentVal)) {
                view.setText(entries[i]);
                return;
            }
        }
        view.setText(currentVal);
    }

    // ---- checkbox ----

    private void setupCheckBoxClick(int itemId, final CheckBox checkbox, final String key, final boolean defaultValue) {
        boolean checked = SharedPreferencesUtil.getBoolean(key, defaultValue);
        checkbox.setChecked(checked);

        LinearLayout item = (LinearLayout) findViewById(itemId);
        if (item == null) return;
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkbox.toggle();
                SharedPreferencesUtil.putBoolean(key, checkbox.isChecked());
            }
        });
    }

    // ---- 显示更新 ----

    private void updateAllDisplays() {
        updateOneList(KEY_PICTQ_SIZE, "3",
                R.array.decoder_pictq_size_entries, R.array.decoder_pictq_size_values, textPictqSize);
        updateOneList(KEY_MAX_FPS, "30",
                R.array.decoder_maxfps_entries, R.array.decoder_maxfps_values, textMaxFps);
        updateOneList(KEY_FRAMEDROP, "0",
                R.array.decoder_framedrop_entries, R.array.decoder_framedrop_values, textFramedrop);
        updateOneList(KEY_SKIP_LOOP_FILTER, "48",
                R.array.decoder_skip_loop_filter_entries, R.array.decoder_skip_loop_filter_values, textSkipLoopFilter);
        updateOneList(KEY_SKIP_FRAME, "8",
                R.array.decoder_skip_frame_entries, R.array.decoder_skip_frame_values, textSkipFrame);
    }

    private void updateOneList(String key, String defaultValue,
                               int entriesResId, int valuesResId, TextView view) {
        String val = SharedPreferencesUtil.getString(key, defaultValue);
        if (val == null || val.length() == 0) {
            val = defaultValue;
        }
        String[] entries = getResources().getStringArray(entriesResId);
        String[] values = getResources().getStringArray(valuesResId);
        updateListDisplay(view, entries, values, val);
    }

    // ---- 播放器 Static helpers ----

    public static int getSkipLoopFilter() {
        return parseInt(SharedPreferencesUtil.getString(KEY_SKIP_LOOP_FILTER, "48"), 48);
    }

    public static int getSkipFrame() {
        return parseInt(SharedPreferencesUtil.getString(KEY_SKIP_FRAME, "8"), 8);
    }

    public static boolean isOpenSLESEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_OPENSLES, false);
    }

    public static int getFramedrop() {
        return parseInt(SharedPreferencesUtil.getString(KEY_FRAMEDROP, "0"), 0);
    }

    public static int getMaxFps() {
        return parseInt(SharedPreferencesUtil.getString(KEY_MAX_FPS, "30"), 30);
    }

    public static int getVideoPictqSize() {
        return parseInt(SharedPreferencesUtil.getString(KEY_PICTQ_SIZE, "3"), 3);
    }

    public static boolean isVideoDisabled() {
        return SharedPreferencesUtil.getBoolean(KEY_VIDEO_DISABLED, false);
    }

    public static boolean isAudioDisabled() {
        return SharedPreferencesUtil.getBoolean(KEY_AUDIO_DISABLED, false);
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}