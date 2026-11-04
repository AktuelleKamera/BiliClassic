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
import java.util.zip.CRC32;

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
    private static boolean sCupcakeLoaded = false;

    /**
     * Android 1.5 loader: the three libraries stay split, but their NEEDED
     * entries are patched to this fixed extraction directory.
     */
    public static synchronized boolean loadCupcakeLibraries(Context ctx) {
        if (sCupcakeLoaded) return true;
        try {
            File dir = new File(ctx.getFilesDir(), "ijk_api3");
            if (!dir.isDirectory() && !dir.mkdirs()) return false;

            String appApk = ctx.getPackageManager()
                    .getApplicationInfo(ctx.getPackageName(), 0).sourceDir;
            File player = new File(dir, "libijkplayer.so");
            File sdl = new File(dir, "libijksdl.so");
            File ffmpeg = new File(dir, "libijkffmpeg.so");
            File cxxShim = new File(dir, "libcxxshim.so");
            ZipFile apk = new ZipFile(appApk);
            try {
                extractApkEntry(apk, "assets/ijk-api3/armeabi/libcxxshim.so", cxxShim);
                extractApkEntry(apk, "assets/ijk-api3/armeabi/libijksdl.so", sdl);
                extractApkEntry(apk, "assets/ijk-api3/armeabi/libijkplayer.so", player);

                if (tv.biliclassic.util.DeviceUtils.needsNoVfpIjk()) {
                    PackageInfo pi = ctx.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
                    String packApk = pi.applicationInfo != null
                            ? pi.applicationInfo.sourceDir : null;
                    if (packApk == null || !new File(packApk).exists()) {
                        Log.e(TAG, "legacy pack apk missing for API 3");
                        return false;
                    }
                    if (!extractNamedLib(packApk, LIB_NAME, ffmpeg)) {
                        return false;
                    }
                } else {
                    extractApkEntry(apk, "lib/armeabi/libijkffmpeg.so", ffmpeg);
                }
            } finally {
                apk.close();
            }

            System.load(cxxShim.getAbsolutePath());
            System.load(ffmpeg.getAbsolutePath());
            System.load(sdl.getAbsolutePath());
            System.load(player.getAbsolutePath());
            sCupcakeLoaded = true;
            Log.d(TAG, "loaded API 3 IJK libraries with cxx shim");
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "API 3 library load failed", error);
            return false;
        }
    }

    private static void extractApkEntry(ZipFile apk, String entryName, File out)
            throws java.io.IOException {
        ZipEntry entry = apk.getEntry(entryName);
        if (entry == null) {
            throw new java.io.IOException("APK entry missing: " + entryName);
        }
        if (matchesEntry(out, entry)) {
            return;
        }
        File temp = new File(out.getParentFile(), out.getName() + ".tmp");
        InputStream in = apk.getInputStream(entry);
        try {
            FileOutputStream stream = new FileOutputStream(temp);
            try {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) stream.write(buffer, 0, count);
            } finally {
                stream.close();
            }
        } finally {
            in.close();
        }
        if (!matchesEntry(temp, entry) || !temp.renameTo(out)) {
            throw new java.io.IOException("Cannot extract " + entryName);
        }
    }

    private static boolean matchesEntry(File file, ZipEntry entry) throws java.io.IOException {
        if (!file.isFile() || file.length() != entry.getSize()) return false;
        CRC32 crc = new CRC32();
        InputStream in = new java.io.FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) crc.update(buffer, 0, count);
        } finally { in.close(); }
        return crc.getValue() == entry.getCrc();
    }

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
            android.content.pm.ApplicationInfo ai = ctx.getPackageManager()
                    .getApplicationInfo(ctx.getPackageName(), 0);
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
