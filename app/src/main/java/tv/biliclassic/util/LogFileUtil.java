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

    /** 读取完整日志文本（app.log + diagnose.log 合并，诊断在前） */
    public static String readAllLogs() {
        StringBuilder sb = new StringBuilder();
        sb.append("======== 诊断日志 ========\n");
        sb.append(readFileText(diagFile()));
        sb.append("\n======== 运行日志 ========\n");
        sb.append(readFileText(logFile()));
        return sb.toString();
    }

    private static String readFileText(File f) {
        if (f == null || !f.exists()) return "";
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            int off = 0, n;
            while (off < data.length && (n = fis.read(data, off, data.length - off)) > 0) {
                off += n;
            }
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** 用系统分享导出日志（可发微信/邮件/存网盘） */
    public static void shareLogs(Context ctx) {
        if (ctx == null) return;
        try {
            File f = logFile();
            if (!f.exists()) {
                android.widget.Toast.makeText(ctx, "暂无日志", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.activity_settings_run_log_subject));
            send.putExtra(Intent.EXTRA_TEXT, readAllLogs());
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.activity_settings_run_log_share_title)));
        } catch (Exception e) {
            android.widget.Toast.makeText(ctx,
                    ctx.getString(R.string.activity_settings_run_log_share_fail, e.getMessage()),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** 应用内查看日志（简单对话框） */
    public static void showLogsDialog(final Context ctx) {
        if (ctx == null) return;
        String logs = readAllLogs();
        if (logs == null || logs.trim().length() == 0) {
            logs = ctx.getString(R.string.activity_settings_run_log_empty);
        }
        new android.app.AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(ctx))
                .setTitle(ctx.getString(R.string.activity_settings_run_log_title))
                .setMessage(logs.length() > 60000 ? logs.substring(0, 60000) : logs)
                .setPositiveButton(ctx.getString(R.string.videodetail_share),
                        new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        shareLogs(ctx);
                    }
                })
                .setNegativeButton(ctx.getString(R.string.videodetail_close_dialog), null)
                .show();
    }
}
