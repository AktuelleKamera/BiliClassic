package tv.biliclassic.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import tv.biliclassic.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 应用内日志工具：把关键运行日志写入 app 私有目录的文件，
 * 用户无需 adb/logcat，直接在 App 内查看或通过系统分享导出。
 *
 * 日志目录：/data/data/tv.biliclassic/files/applog/
 * 文件：app.log（滚动追加，自动截断）、diagnose.log（弹幕/播放诊断，独立覆盖写）
 */
public class LogFileUtil {

    private static final String TAG = "LogFileUtil";
    private static final String DIR = "applog";
    private static final String LOG_FILE = "app.log";
    private static final String DIAG_FILE = "diagnose.log";
    private static final long MAX_LOG_SIZE = 512 * 1024; // 512KB 截断
    private static Context sContext;
    private static volatile boolean sEnabled = false; // 默认关闭，避免日志写入影响性能

    private LogFileUtil() {
    }

    /** 是否开启运行日志（默认关）。开启后才会写文件。 */
    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
        if (enabled && sContext != null) {
            File dir = new File(sContext.getFilesDir(), DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
    }

    public static boolean isEnabled() {
        return sEnabled;
    }

    public static void init(Context ctx) {
        sContext = ctx.getApplicationContext();
        if (sEnabled) {
            File dir = new File(sContext.getFilesDir(), DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
    }

    private static File logFile() {
        return new File(sContext.getFilesDir() + File.separator + DIR, LOG_FILE);
    }

    private static File diagFile() {
        return new File(sContext.getFilesDir() + File.separator + DIR, DIAG_FILE);
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    /** 追加一条日志（带时间戳） */
    public static void log(String tag, String msg) {
        if (sContext == null || !sEnabled) return;
        try {
            File f = logFile();
            if (f.length() > MAX_LOG_SIZE) {
                // 截断：保留后半段
                byte[] data = readAll(f);
                FileOutputStream fos = new FileOutputStream(f);
                if (data != null && data.length > MAX_LOG_SIZE / 2) {
                    fos.write(data, data.length - (int) (MAX_LOG_SIZE / 2), (int) (MAX_LOG_SIZE / 2));
                }
                fos.close();
            }
            FileWriter w = new FileWriter(f, true);
            w.write(now() + " [" + tag + "] " + msg + "\n");
            w.close();
        } catch (Exception e) {
        }
    }

    /** 诊断日志：独立文件，覆盖写（记录本次会话的弹幕/播放诊断） */
    public static void diag(String tag, String msg) {
        if (sContext == null || !sEnabled) return;
        try {
            FileWriter w = new FileWriter(diagFile(), true);
            w.write(now() + " [" + tag + "] " + msg + "\n");
            w.close();
        } catch (Exception e) {
        }
    }

    /** 清空诊断日志（新会话开始时调用） */
    public static void clearDiag() {
        if (sContext == null || !sEnabled) return;
        try {
            File f = diagFile();
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
        }
    }

    private static byte[] readAll(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = fis.read(buf, off, buf.length - off)) > 0) {
                off += n;
            }
            fis.close();
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

}
