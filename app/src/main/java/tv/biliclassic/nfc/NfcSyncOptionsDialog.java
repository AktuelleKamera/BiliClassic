package tv.biliclassic.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import tv.biliclassic.R;

public final class NfcSyncOptionsDialog {

    private NfcSyncOptionsDialog() {
    }

    public static void show(final Activity activity, final String json,
                            final String forceCookies,
                            final String forceRefreshToken) {
        show(activity, json, forceCookies, forceRefreshToken, true);
    }

    public static void show(final Activity activity, final String json,
                            final String forceCookies,
                            final String forceRefreshToken,
                            final boolean finishOnDone) {
        if (activity == null) return;
        final String[] labels = {
                activity.getString(R.string.nfc_sync_select_login),
                activity.getString(R.string.local_favorite_title),
                activity.getString(R.string.nfc_sync_select_history),
                activity.getString(R.string.Player_option_menu_title_danmaku),
                activity.getString(R.string.video_quality),
                activity.getString(R.string.nfc_sync_select_language),
                activity.getString(R.string.select_dialog_style),
                activity.getString(R.string.modern_style),
                activity.getString(R.string.image_load_threads)
        };
        final boolean[] checked = {true, true, true, true, true, true, true, true, true};
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * activity.getResources().getDisplayMetrics().density);
        list.setPadding(pad, pad, pad, pad);
        int textColor = tv.biliclassic.util.UiSkin.isNight()
                ? 0xFFE6E6E6 : 0xFF333333;
        for (int i = 0; i < labels.length; i++) {
            CheckBox box = new CheckBox(activity);
            box.setText(labels[i]);
            box.setTextColor(textColor);
            box.setTextSize(15);
            box.setButtonDrawable(activity.getResources().getDrawable(
                    R.drawable.ic_btn_holo_pink_checkbox));
            box.setChecked(checked[i]);
            final int index = i;
            box.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(android.widget.CompoundButton buttonView,
                                             boolean isChecked) {
                    checked[index] = isChecked;
                }
            });
            list.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(
                tv.biliclassic.util.DialogUtil.wrap(activity)))
                .setTitle(activity.getString(R.string.nfc_sync_receive_title))
                .setView(scroll)
                .setPositiveButton(activity.getString(R.string.nfc_sync_import),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                int mask = 0;
                                if (checked[0]) mask |= NfcSyncData.LOGIN;
                                if (checked[1]) mask |= NfcSyncData.FAVORITES;
                                if (checked[2]) mask |= NfcSyncData.HISTORY;
                                if (checked[3]) mask |= NfcSyncData.DANMAKU;
                                if (checked[4]) mask |= NfcSyncData.QUALITY;
                                if (checked[5]) mask |= NfcSyncData.LANGUAGE;
                                if (checked[6]) mask |= NfcSyncData.DIALOG_STYLE;
                                if (checked[7]) mask |= NfcSyncData.MODERN_MODE;
                                if (checked[8]) mask |= NfcSyncData.IMAGE_THREADS;
                                NfcSyncData.apply(json, mask);
                                if (forceCookies != null && forceCookies.length() > 0) {
                                    NfcSyncData.applyLogin(forceCookies, forceRefreshToken);
                                }
                                activity.sendBroadcast(new Intent(
                                        tv.biliclassic.util.BroadcastConstants.ACTION_FAVORITE_CHANGED));
                                Toast.makeText(activity, R.string.nfc_sync_complete,
                                        Toast.LENGTH_SHORT).show();
                                if (finishOnDone) activity.finish();
                            }
                        })
                .setNegativeButton(activity.getString(R.string.videodetail_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (finishOnDone) activity.finish();
                            }
                        })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        if (finishOnDone) activity.finish();
                    }
                })
                .show();
    }
}
