package tv.biliclassic.nfc;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class NfcSyncHceService extends HostApduService {

    private static final byte[] SW_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] SW_NOT_FOUND = {(byte) 0x6A, (byte) 0x82};

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) return;
        try {
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(
                    new ComponentName(context, NfcSyncHceService.class),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (apdu == null || apdu.length < 4) return SW_NOT_FOUND;
        if (apdu[1] == (byte) 0xA4) return SW_OK;
        if (apdu[0] == (byte) 0x00 && apdu[1] == (byte) 0xB0) {
            String token = NfcSyncHandshake.getToken();
            if (token == null || token.length() == 0) return SW_NOT_FOUND;
            try {
                byte[] value = token.getBytes("UTF-8");
                byte[] result = new byte[value.length + SW_OK.length];
                System.arraycopy(value, 0, result, 0, value.length);
                System.arraycopy(SW_OK, 0, result, value.length, SW_OK.length);
                return result;
            } catch (Throwable ignored) {
                return SW_NOT_FOUND;
            }
        }
        if (apdu[0] == (byte) 0x00 && apdu[1] == (byte) 0xB1 && apdu.length >= 6) {
            String data = NfcSyncHandshake.getData();
            if (data == null || data.length() == 0) return SW_NOT_FOUND;
            try {
                byte[] all = data.getBytes("UTF-8");
                int offset = ((apdu[2] & 0xFF) << 16)
                        | ((apdu[3] & 0xFF) << 8) | (apdu[4] & 0xFF);
                int length = apdu[5] & 0xFF;
                if (length == 0) length = 200;
                if (offset < 0 || offset >= all.length) return SW_NOT_FOUND;
                int count = Math.min(length, all.length - offset);
                byte[] result = new byte[count + SW_OK.length];
                System.arraycopy(all, offset, result, 0, count);
                System.arraycopy(SW_OK, 0, result, count, SW_OK.length);
                return result;
            } catch (Throwable ignored) {
                return SW_NOT_FOUND;
            }
        }
        return SW_NOT_FOUND;
    }

    @Override
    public void onDeactivated(int reason) {
    }
}
