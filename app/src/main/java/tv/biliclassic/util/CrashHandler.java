package tv.biliclassic.util;

import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static CrashHandler instance;
    private Context context;

    private CrashHandler() {}

    public static CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context ctx) {
        context = ctx.getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    private static String getManufacturer() {
        try {
            return (String) android.os.Build.class.getField("MANUFACTURER").get(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        pw.flush();
        String mfr = getManufacturer();
        pw.close();
        android.util.Log.e("CrashHandler", "Device: " + mfr + ", Error: " + sw.toString());
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}
