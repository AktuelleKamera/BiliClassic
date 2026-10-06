package tv.biliclassic.player;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * no-VFP 老设备用的 IJK ffmpeg 加载器。
 *
 * 自带 libijkffmpeg.so 含 VFP 指令，ARMv5TE / 无 VFP 的 ARMv6 跑不了；
 * 这类设备改用 no-VFP 的 legacy 版 libijkffmpeg-legacy.so，
 * 该库单独打包在兼容包 APK（tv.biliclassic.libnovfp）里。
 * 从已安装兼容包的 APK 中提取到本应用私有目录后 System.load 绝对路径加载。
 */
public class IjkLegacyLoader {

    private static final String TAG = "IjkLegacy";
    private static final String PACKAGE_NAME = "tv.biliclassic.libnovfp";
    private static final String LIB_NAME = "libijkffmpeg-legacy.so";

    private static boolean sLoaded = false;

    private IjkLegacyLoader() {
    }

    /** 兼容包是否已安装。 */
    public static boolean isPackInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            ctx.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 从兼容包提取 no-VFP 版 ijkffmpeg 并加载。
     * 已加载过直接返回 true；未装包 / 提取失败返回 false。
     */
    public static boolean loadIjkFfmpegLegacy(Context ctx) {
        if (sLoaded) {
            return true;
        }
        try {
            if (ctx == null) {
                return false;
            }
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi;
            try {
                pi = pm.getPackageInfo(PACKAGE_NAME, 0);
            } catch (Throwable t) {
                Log.d(TAG, "compat pack not installed: " + PACKAGE_NAME);
                return false;
            }
            String apkPath = pi.applicationInfo != null ? pi.applicationInfo.sourceDir : null;
            if (apkPath == null || !new File(apkPath).exists()) {
                Log.e(TAG, "compat pack apk missing");
                return false;
            }
            // 提取成规范名 libijkffmpeg.so：libijksdl.so/libijkplayer.so 的 NEEDED 依赖就是它，
            // 旧 ROM 链接器按文件名解析依赖（不看 SONAME），用 -legacy 名字会解析不到
            File out = new File(getExtractDir(ctx), "libijkffmpeg.so");
            if (!(out.exists() && out.length() > 0)) {
                if (!extractLib(apkPath, out)) {
                    return false;
                }
            }
            System.load(out.getAbsolutePath());
            sLoaded = true;
            Log.d(TAG, "loaded legacy ijkffmpeg: " + out.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "load legacy ijkffmpeg failed", t);
            return false;
        }
    }

    /**
     * 从「自身 APK」提取指定 native 库并用绝对路径加载。
     * 绝对路径 System.load 可用），这里绕过它的目录搜索。
     */
    public static boolean loadAppLib(Context ctx, String libFileName) {
        try {
            if (ctx == null) {
                return false;
            }
            android.content.pm.ApplicationInfo ai = ctx.getApplicationInfo();
            String apkPath = ai != null ? ai.sourceDir : null;
            if (apkPath == null || !new File(apkPath).exists()) {
                Log.e(TAG, "own apk missing for " + libFileName);
                return false;
            }
            File out = new File(getExtractDir(ctx), libFileName);
            if (!(out.exists() && out.length() > 0)) {
                if (!extractNamedLib(apkPath, libFileName, out)) {
                    return false;
                }
            }
            System.load(out.getAbsolutePath());
            Log.d(TAG, "loaded app lib: " + libFileName);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "load app lib failed: " + libFileName, t);
            return false;
        }
    }

    private static boolean extractNamedLib(String apkPath, String libFileName, File out) throws Exception {
        ZipFile zip = new ZipFile(apkPath);
        try {
            ZipEntry entry = null;
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (name != null && name.endsWith("/" + libFileName)) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                Log.e(TAG, "entry not found in apk: " + libFileName);
                return false;
            }
            InputStream in = zip.getInputStream(entry);
            FileOutputStream fos = new FileOutputStream(out);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
            } finally {
                try {
                    fos.close();
                } catch (Throwable t) {
                }
                try {
                    in.close();
                } catch (Throwable t) {
                }
            }
            return true;
        } finally {
            zip.close();
        }
    }

    private static boolean extractLib(String apkPath, File out) throws Exception {        ZipFile zip = new ZipFile(apkPath);
        try {
            String entry = findLibEntry(zip);
            if (entry == null) {
                Log.e(TAG, "entry not found: " + LIB_NAME);
                return false;
            }
            InputStream in = zip.getInputStream(zip.getEntry(entry));
            FileOutputStream fos = new FileOutputStream(out);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
            } finally {
                try {
                    fos.close();
                } catch (Throwable t) {
                }
                try {
                    in.close();
                } catch (Throwable t) {
                }
            }
            return true;
        } finally {
            zip.close();
        }
    }

    /** 兼容包里的 native 库可能放在 lib/armeabi/ 也可能别的 ABI 目录，按文件名匹配。 */
    private static String findLibEntry(ZipFile zip) {
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String name = e.getName();
            if (name != null && name.endsWith("/" + LIB_NAME)) {
                return name;
            }
        }
        return null;
    }

    private static File getExtractDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "ijk_legacy");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
