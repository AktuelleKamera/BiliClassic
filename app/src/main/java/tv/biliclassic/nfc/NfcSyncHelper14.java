package tv.biliclassic.nfc;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;

final class NfcSyncHelper14 {

    private NfcSyncHelper14() {
    }

    static void attach(NfcAdapter adapter, final Activity activity,
                       final NfcSyncHelper.PayloadProvider provider,
                       final NfcSyncHelper.BeamListener listener) {
        try {
            adapter.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
                public NdefMessage createNdefMessage(NfcEvent event) {
                    String payload = provider == null ? null : provider.getPayload();
                    if (payload == null || payload.length() == 0) return null;
                    if (listener != null) {
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onBeamStarted();
                            }
                        });
                    }
                    return NfcSyncHelper.buildMessage(payload);
                }
            }, activity);
        } catch (Throwable ignored) {
        }
    }

    static void detach(NfcAdapter adapter, Activity activity) {
        try {
            adapter.setNdefPushMessageCallback(null, activity);
        } catch (Throwable ignored) {
        }
    }
}
