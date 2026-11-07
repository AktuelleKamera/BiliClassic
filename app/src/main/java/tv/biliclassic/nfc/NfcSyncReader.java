package tv.biliclassic.nfc;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Handler;
import android.os.Looper;

import tv.biliclassic.util.SdkHelper;

public final class NfcSyncReader {

    private static volatile boolean sLaunching;

    private NfcSyncReader() {
    }

    public static void attach(final Activity activity) {
        if (activity == null || SdkHelper.getSdkInt() < 19) return;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
        if (adapter == null || !adapter.isEnabled()) return;
        try {
            adapter.enableReaderMode(activity, new NfcAdapter.ReaderCallback() {
                @Override
                public void onTagDiscovered(final Tag tag) {
                    readTag(activity, tag);
                }
            }, NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F
                    | NfcAdapter.FLAG_READER_NFC_V
                    | NfcAdapter.FLAG_READER_NFC_BARCODE
                    | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        } catch (Throwable ignored) {
        }
    }

    public static void detach(Activity activity) {
        if (activity == null || SdkHelper.getSdkInt() < 19) return;
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
            if (adapter != null) adapter.disableReaderMode(activity);
        } catch (Throwable ignored) {
        }
    }

    private static void readTag(final Activity activity, Tag tag) {
        if (sLaunching) return;
        final String json = readJson(tag);
        if (json != null) launch(activity, json);
    }

    private static void launch(final Activity activity, final String json) {
        if (sLaunching) return;
        sLaunching = true;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                sLaunching = false;
            }
        }, 5000L);
        activity.runOnUiThread(new Runnable() {
            public void run() {
                NfcSyncOptionsDialog.show(activity, json, null, null, false);
            }
        });
    }

    static String readJson(Tag tag) {
        String token = readToken(tag);
        if (token == null || token.indexOf("BC2|") != 0) return null;
        int total;
        try {
            total = Integer.parseInt(token.substring(4));
        } catch (Throwable ignored) {
            return null;
        }
        if (total <= 0 || total > 2 * 1024 * 1024) return null;
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) return null;
        try {
            isoDep.connect();
            isoDep.setTimeout(5000);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int offset = 0;
            while (offset < total) {
                byte[] response = isoDep.transceive(new byte[]{
                        (byte) 0x00, (byte) 0xB1,
                        (byte) ((offset >> 16) & 0xFF),
                        (byte) ((offset >> 8) & 0xFF),
                        (byte) (offset & 0xFF),
                        (byte) 200
                });
                if (!isOk(response) || response.length <= 2) return null;
                int count = response.length - 2;
                out.write(response, 0, count);
                offset += count;
                if (offset > total) return null;
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { isoDep.close(); } catch (Throwable ignored) { }
        }
    }

    static String readToken(Tag tag) {
        if (tag == null) return null;
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) return null;
        String token = null;
        try {
            isoDep.connect();
            isoDep.setTimeout(3000);
            byte[] select = buildSelectApdu(NfcSyncHandshake.AID);
            byte[] response = isoDep.transceive(select);
            if (isOk(response)) {
                response = isoDep.transceive(new byte[]{
                        (byte) 0x00, (byte) 0xB0, (byte) 0x00,
                        (byte) 0x00, (byte) 0x00
                });
                if (isOk(response) && response.length > 2) {
                    token = new String(response, 0, response.length - 2, "UTF-8");
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { isoDep.close(); } catch (Throwable ignored) { }
        }
        return token;
    }

    private static byte[] buildSelectApdu(String aid) {
        byte[] aidBytes = hexToBytes(aid);
        byte[] apdu = new byte[6 + aidBytes.length];
        apdu[0] = (byte) 0x00;
        apdu[1] = (byte) 0xA4;
        apdu[2] = (byte) 0x04;
        apdu[3] = (byte) 0x00;
        apdu[4] = (byte) aidBytes.length;
        System.arraycopy(aidBytes, 0, apdu, 5, aidBytes.length);
        apdu[apdu.length - 1] = (byte) 0x00;
        return apdu;
    }

    private static boolean isOk(byte[] response) {
        return response != null && response.length >= 2
                && (response[response.length - 2] & 0xFF) == 0x90
                && (response[response.length - 1] & 0xFF) == 0x00;
    }

    private static byte[] hexToBytes(String value) {
        if (value == null || (value.length() & 1) != 0) return new byte[0];
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(value.charAt(i * 2), 16);
            int lo = Character.digit(value.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return new byte[0];
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }
}
