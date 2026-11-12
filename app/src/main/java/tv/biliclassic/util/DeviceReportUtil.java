package tv.biliclassic.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.DisplayMetrics;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

import tv.biliclassic.R;

public final class DeviceReportUtil {

    private static final String PREFS = "device_report";
    private static final String KEY_LAST = "last_report";
    private static final String KEY_INSTALL = "install_id";
    private static final long MIN_INTERVAL_MS = 12L * 60L * 60L * 1000L;

    private DeviceReportUtil() {
    }

    public static void reportIfNeeded(Context context) {
        if (context == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        if (app == null) {
            return;
        }

        String url;
        try {
            url = app.getString(R.string.device_report_url);
        } catch (Throwable t) {
            return;
        }
        if (url == null || url.trim().length() == 0) {
            return;
        }
        final String reportUrl = url.trim();

        synchronized (DeviceReportUtil.class) {
            SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            long last = sp.getLong(KEY_LAST, 0L);
            if (last > 0L && now - last < MIN_INTERVAL_MS) {
                return;
            }
            sp.edit().putLong(KEY_LAST, now).commit();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!send(app, reportUrl)) {
                    SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    sp.edit().remove(KEY_LAST).commit();
                }
            }
        }).start();
    }

    private static boolean send(Context context, String reportUrl) {
        try {
            String key = deviceKey(context);
            if (key.length() == 0) {
                return false;
            }
            ArrayList headers = new ArrayList();
            headers.add("X-Skip-Cookie");
            headers.add("1");
            String body = buildBody(context, key);
            int code = NetWorkUtil.postNoReply(reportUrl, body, headers,
                    "application/x-www-form-urlencoded");
            return code >= 200 && code < 300;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String buildBody(Context context, String key) {
        StringBuilder body = new StringBuilder();
        add(body, "device_key", key);
        add(body, "platform", "android");
        String brand = DeviceUtils.getDeviceBrand();
        String manufacturer = DeviceUtils.getDeviceManufacturer();
        if (manufacturer.length() == 0) manufacturer = brand;
        String model = DeviceUtils.getDeviceModel();
        String osVersion = DeviceUtils.getAndroidVersion();
        add(body, "brand", brand);
        add(body, "manufacturer", manufacturer);
        add(body, "model", model);
        add(body, "os_version", osVersion);
        add(body, "sdk_int", String.valueOf(DeviceUtils.getSdkInt()));
        add(body, "locale", Locale.getDefault().toString());

        String versionName = "";
        String versionCode = "0";
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            versionName = info.versionName == null ? "" : info.versionName;
            versionCode = String.valueOf(info.versionCode);
        } catch (Throwable t) {
        }
        add(body, "app_version", versionName + "/" + versionCode);

        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            add(body, "screen_width", String.valueOf(metrics.widthPixels));
            add(body, "screen_height", String.valueOf(metrics.heightPixels));
        } catch (Throwable t) {
        }
        return body.toString();
    }

    private static void add(StringBuilder body, String name, String value) {
        try {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(name, "UTF-8"));
            body.append('=');
            body.append(URLEncoder.encode(value == null ? "" : value, "UTF-8"));
        } catch (Throwable t) {
        }
    }

    private static String deviceKey(Context context) {
        String raw = "";
        if (SdkHelper.getSdkInt() >= 3) {
            try {
                raw = Settings.Secure.getString(
                        context.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Throwable t) {
            }
        }
        if (raw == null || raw.length() == 0 || "9774d56d682e549c".equals(raw)) {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            raw = sp.getString(KEY_INSTALL, "");
            if (raw == null || raw.length() == 0) {
                raw = UUID.randomUUID().toString();
                sp.edit().putString(KEY_INSTALL, raw).commit();
            }
        }
        return sha256("biliclassic-device-v1|" + raw);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                if (b < 16) {
                    out.append('0');
                }
                out.append(Integer.toHexString(b));
            }
            return out.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
