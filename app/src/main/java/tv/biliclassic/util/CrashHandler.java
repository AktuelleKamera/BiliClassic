package tv.biliclassic.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

        // 保存崩溃日志并标记 has_crash，下次启动弹出崩溃报告对话框
        saveCrashLog(ex);

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    private void saveCrashLog(Throwable ex) {
        try {
            if (context == null) return;
            // 保存到 /data/data/tv.biliclassic/crashlog/
            File crashDir = new File(context.getFilesDir().getParentFile(), "crashlog");
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.CHINA);
            String fileName = "crash_" + sdf.format(new Date()) + ".txt";
            File crashFile = new File(crashDir, fileName);

            StringBuilder sb = new StringBuilder();
            sb.append("时间: ").append(new Date()).append("\n");
            sb.append("设备: ").append(getBuildField("MODEL")).append("\n");
            sb.append("厂商: ").append(getManufacturer()).append("\n");
            sb.append("Android: ").append(getBuildField("RELEASE")).append("\n");
            sb.append("API: ").append(getSdkInt()).append("\n\n");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            sb.append(sw.toString());

            FileOutputStream fos = new FileOutputStream(crashFile);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();

            // 标记有新日志，供 MainActivity.checkAndShowCrashDialog 读取
            context.getSharedPreferences("crash", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_crash", true)
                    .commit();
        } catch (Exception e) {
            // 崩溃日志保存失败不影响崩溃流程
        }
    }

    private static String getBuildField(String name) {
        try {
            return String.valueOf(android.os.Build.class.getField(name).get(null));
        } catch (Exception e) {
            return "";
        }
    }

    private static int getSdkInt() {
        try {
            return android.os.Build.VERSION.class.getField("SDK_INT").getInt(null);
        } catch (Exception e) {
            try {
                return Integer.parseInt(String.valueOf(android.os.Build.VERSION.class.getField("SDK").get(null)));
            } catch (Exception e2) {
                return 0;
            }
        }
    }
}
