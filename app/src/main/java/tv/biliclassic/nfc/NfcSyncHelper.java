package tv.biliclassic.nfc;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;

import java.lang.reflect.Method;

import tv.biliclassic.util.SdkHelper;

public final class NfcSyncHelper {

    public interface PayloadProvider {
        String getPayload();
    }

    public interface BeamListener {
        void onBeamStarted();
    }

    private NfcSyncHelper() {
    }

    public static void attach(Activity activity, PayloadProvider provider, BeamListener listener) {
        if (activity == null || SdkHelper.getSdkInt() < 9) return;
        NfcAdapter adapter = getAdapter(activity);
        if (adapter == null) return;
        if (SdkHelper.getSdkInt() >= 17) {
            NfcSyncHelper14.attach(adapter, activity, provider, listener);
            return;
        }
        try {
            String payload = provider == null ? null : provider.getPayload();
            if (payload == null || payload.length() == 0) return;
            Method method = NfcAdapter.class.getMethod("enableForegroundNdefPush",
                    Activity.class, NdefMessage.class);
            method.invoke(adapter, activity, buildMessage(payload, false));
        } catch (Throwable ignored) {
        }
    }

    public static void detach(Activity activity) {
        if (activity == null || SdkHelper.getSdkInt() < 9) return;
        NfcAdapter adapter = getAdapter(activity);
        if (adapter == null) return;
        if (SdkHelper.getSdkInt() >= 17) {
            NfcSyncHelper14.detach(adapter, activity);
            return;
        }
        try {
            Method method = NfcAdapter.class.getMethod("enableForegroundNdefPush",
                    Activity.class, NdefMessage.class);
            method.invoke(adapter, activity, new Object[]{null});
        } catch (Throwable ignored) {
        }
    }

    static NdefMessage buildMessage(String payload) {
        return buildMessage(payload, true);
    }

    static NdefMessage buildMessage(String payload, boolean includeAar) {
        try {
            byte[] uri = payload.getBytes("UTF-8");
            byte[] data = new byte[uri.length + 1];
            data[0] = 0x00;
            System.arraycopy(uri, 0, data, 1, uri.length);
            NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                    NdefRecord.RTD_URI, new byte[0], data);
            if (!includeAar) {
                return new NdefMessage(new NdefRecord[]{record});
            }
            NdefRecord aar = new NdefRecord((short) 0x04,
                    "android.com:pkg".getBytes("US-ASCII"), new byte[0],
                    "tv.biliclassic".getBytes("UTF-8"));
            return new NdefMessage(new NdefRecord[]{record, aar});
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NfcAdapter getAdapter(Activity activity) {
        try {
            Method method = NfcAdapter.class.getMethod("getDefaultAdapter", Activity.class);
            Object value = method.invoke(null, activity);
            if (value instanceof NfcAdapter) return (NfcAdapter) value;
        } catch (Throwable ignored) {
        }
        try {
            Method method = NfcAdapter.class.getMethod("getDefaultAdapter",
                    android.content.Context.class);
            Object value = method.invoke(null, activity);
            if (value instanceof NfcAdapter) return (NfcAdapter) value;
        } catch (Throwable ignored) {
        }
        return null;
    }
}
