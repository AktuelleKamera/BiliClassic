package tv.biliclassic.nfc;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import tv.biliclassic.R;

public class NfcSyncSenderActivity extends Activity {

    private TextView mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 29) {
            Toast.makeText(this, R.string.nfc_sync_android10_required,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, R.string.nfc_sync_nfc_required,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        tv.biliclassic.util.BgUtil.applyWindowBackground(this);
        buildUi();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NfcSyncHceService.setEnabled(this, true);
        startSender();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        int textColor = tv.biliclassic.util.UiSkin.isNight()
                ? 0xFFE6E6E6 : 0xFF333333;
        TextView title = new TextView(this);
        title.setText(R.string.nfc_sync_send_title);
        title.setTextColor(textColor);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mStatus = new TextView(this);
        mStatus.setText(R.string.nfc_sync_sender_waiting);
        mStatus.setTextColor(textColor);
        mStatus.setTextSize(15);
        mStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = pad / 2;
        root.addView(mStatus, statusParams);
        setContentView(root);
    }

    private void startSender() {
        new Thread(new Runnable() {
            public void run() {
                final String json = NfcSyncData.build(NfcSyncData.ALL);
                if (json == null) {
                    fail();
                    return;
                }
                NfcSyncHandshake.setData(json);
                try {
                    NfcSyncHandshake.setToken("BC2|"
                            + json.getBytes("UTF-8").length);
                } catch (Throwable ignored) {
                    NfcSyncHandshake.clear();
                    fail();
                }
            }
        }, "NfcSyncPrepare").start();
    }

    private void fail() {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(NfcSyncSenderActivity.this,
                        R.string.nfc_sync_failed, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        NfcSyncHandshake.clear();
        NfcSyncHceService.setEnabled(this, false);
        super.onDestroy();
    }
}
