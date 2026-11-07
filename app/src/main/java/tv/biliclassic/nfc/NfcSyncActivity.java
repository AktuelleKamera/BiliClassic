package tv.biliclassic.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import tv.biliclassic.R;
import tv.biliclassic.VideoDetailActivity;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class NfcSyncActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!tv.biliclassic.SettingsActivity.isNfcSyncEnabled()) {
            finish();
            return;
        }
        Uri uri = getIntent() == null ? null : getIntent().getData();
        if (!NfcSyncPayload.isSyncUri(uri)) {
            finish();
            return;
        }
        String from = NfcSyncPayload.getString(uri, "from", "");
        if ("login".equals(from)) {
            handleLogin(uri);
            return;
        }
        if ("data".equals(from)) {
            handleData(uri);
            return;
        }
        long aid = NfcSyncPayload.getLong(uri, "aid", 0);
        if (aid <= 0) {
            finish();
            return;
        }
        int part = NfcSyncPayload.getInt(uri, "p", 0);
        long position = NfcSyncPayload.getLong(uri, "t", 0);
        boolean fromPlayer = "player".equals(from);

        Intent detail = new Intent(this, VideoDetailActivity.class);
        detail.putExtra("aid", aid);
        if (part > 0) {
            detail.putExtra("p", part);
        }
        if (fromPlayer) {
            detail.putExtra("autoplay", true);
            if (position > 0) {
                detail.putExtra("resume_position", (int) Math.min(Integer.MAX_VALUE, position));
            }
        } else {
            detail.putExtra("autoplay", false);
        }
        startActivity(detail);
        finish();
    }

    private void handleData(Uri uri) {
        String json = NfcSyncPayload.decodeGzipBase64(
                NfcSyncPayload.getString(uri, "d", ""));
        if (json == null || json.length() == 0) {
            Toast.makeText(this, R.string.nfc_sync_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        showDataDialog(json, null, null);
    }

    private void showDataDialog(final String json, final String cookies,
                               final String refreshToken) {
        NfcSyncOptionsDialog.show(this, json, cookies, refreshToken);
    }

    private void handleLogin(Uri uri) {
        String encoded = NfcSyncPayload.getString(uri, "cookie", "");
        final String cookies = NfcSyncPayload.decodeBase64(encoded);
        if (cookies == null || cookies.length() == 0) {
            finish();
            return;
        }
        final String refreshToken = NfcSyncPayload.decodeBase64(
                NfcSyncPayload.getString(uri, "rt", ""));
        String dataJson = NfcSyncPayload.decodeGzipBase64(
                NfcSyncPayload.getString(uri, "d", ""));
        if (dataJson != null && dataJson.length() > 0) {
            showDataDialog(dataJson, cookies, refreshToken);
            return;
        }
        new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(
                tv.biliclassic.util.DialogUtil.wrap(this)))
                .setTitle(getString(R.string.nfc_login_sync_title))
                .setMessage(getString(R.string.nfc_login_sync_message))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        applyLogin(cookies, refreshToken);
                        finish();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
                .show();
    }

    private void applyLogin(String cookies, String refreshToken) {
        String normalized = NetWorkUtil.boundCookie(cookies);
        if (normalized == null || normalized.length() == 0) {
            Toast.makeText(this, R.string.nfc_login_sync_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, normalized);
        String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", normalized);
        if (mid != null && mid.length() > 0) {
            try {
                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
            } catch (Throwable ignored) {
            }
        }
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", normalized);
        if (csrf != null && csrf.length() > 0) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);
        }
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token,
                refreshToken == null ? "" : refreshToken);
        NetWorkUtil.setCookieString(normalized);
        NetWorkUtil.refreshHeaders();
        NetWorkUtil.syncLoginState();
        Toast.makeText(this, R.string.nfc_login_sync_done, Toast.LENGTH_SHORT).show();
    }
}
