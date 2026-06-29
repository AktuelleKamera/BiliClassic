package tv.biliclassic.util;

import android.content.Context;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static CrashHandler instance;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context ctx) {
        this.context = ctx.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // 只保存日志
        saveCrashLog(ex);

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private void saveCrashLog(Throwable ex) {
        try {
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
            sb.append("设备: ").append(android.os.Build.MODEL).append("\n");
            sb.append("厂商: ").append(android.os.Build.MANUFACTURER).append("\n");
            sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            sb.append("API: ").append(android.os.Build.VERSION.SDK_INT).append("\n\n");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            sb.append(sw.toString());

            FileOutputStream fos = new FileOutputStream(crashFile);
            fos.write(sb.toString().getBytes());
            fos.close();

            // 标记有新日志
            context.getSharedPreferences("crash", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_crash", true)
                    .commit();
        } catch (Exception e) {
        }
    }
}