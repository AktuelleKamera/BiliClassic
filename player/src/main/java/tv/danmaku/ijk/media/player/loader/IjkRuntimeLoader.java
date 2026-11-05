package tv.danmaku.ijk.media.player.loader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts the normal jniLibs build, patches its sibling DT_NEEDED entries to
 * the current app-private path, then loads it. No patched .so assets needed.
 */
public final class IjkRuntimeLoader {
    private static final String TAG = "IjkRuntimeLoader";
    private static boolean sLoaded;

    private IjkRuntimeLoader() {
    }

    public static synchronized boolean load(Context context, boolean noVfp,
                                            String compatPackage,
                                            String legacyFfmpegName) {
        if (sLoaded) return true;

        File dir = new File(context.getFilesDir(), "ijk_runtime");
        if (!dir.isDirectory() && !dir.mkdirs()) return false;

        try {
            String appApk = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), 0).sourceDir;
            File shim = new File(dir, "libcxxshim.so");
            File ffmpeg = new File(dir, "libijkffmpeg.so");
            File sdl = new File(dir, "libijksdl.so");
            File player = new File(dir, "libijkplayer.so");
            File sdlPatched = new File(dir, "libijksdl.patched.so");
            File playerPatched = new File(dir, "libijkplayer.patched.so");

            ZipFile apk = new ZipFile(appApk);
            try {
                extractEntry(apk, "lib/armeabi/libcxxshim.so", shim);
                extractEntry(apk, "lib/armeabi/libijksdl.so", sdl);
                extractEntry(apk, "lib/armeabi/libijkplayer.so", player);
                if (noVfp) {
                    PackageInfo pi = context.getPackageManager()
                            .getPackageInfo(compatPackage, 0);
                    String packApk = pi.applicationInfo != null
                            ? pi.applicationInfo.sourceDir : null;
                    if (packApk == null || !new File(packApk).exists()) {
                        return false;
                    }
                    extractNamedEntry(packApk, legacyFfmpegName, ffmpeg);
                } else {
                    extractEntry(apk, "lib/armeabi/libijkffmpeg.so", ffmpeg);
                }
            } finally {
                apk.close();
            }

            Map<String, String> sdlNeeded = new HashMap<String, String>();
            sdlNeeded.put("libijkffmpeg.so", ffmpeg.getAbsolutePath());
            ElfPatcher.patch(sdl, sdlPatched, sdlNeeded);

            Map<String, String> playerNeeded = new HashMap<String, String>();
            playerNeeded.put("libijkffmpeg.so", ffmpeg.getAbsolutePath());
            playerNeeded.put("libijksdl.so", sdlPatched.getAbsolutePath());
            ElfPatcher.patch(player, playerPatched, playerNeeded);

            System.load(shim.getAbsolutePath());
            System.load(ffmpeg.getAbsolutePath());
            System.load(sdlPatched.getAbsolutePath());
            System.load(playerPatched.getAbsolutePath());

            if (!isMapped(ffmpeg) || !isMapped(sdlPatched)
                    || !isMapped(playerPatched)) {
                throw new IOException("IJK runtime library not mapped");
            }

            sLoaded = true;
            Log.d(TAG, "loaded patched runtime IJK libraries");
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "runtime IJK load failed: " + error);
            return false;
        }
    }

    private static void extractEntry(ZipFile apk, String name, File out)
            throws IOException {
        ZipEntry entry = apk.getEntry(name);
        if (entry == null) throw new IOException("APK entry missing: " + name);
        if (out.isFile() && out.length() == entry.getSize()) return;
        copy(apk.getInputStream(entry), out);
    }

    private static void extractNamedEntry(String apkPath, String suffix, File out)
            throws IOException {
        ZipFile apk = new ZipFile(apkPath);
        try {
            ZipEntry found = null;
            java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith("/" + suffix)) {
                    found = entry;
                    break;
                }
            }
            if (found == null) throw new IOException("pack entry missing: " + suffix);
            if (out.isFile() && out.length() == found.getSize()) return;
            copy(apk.getInputStream(found), out);
        } finally {
            apk.close();
        }
    }

    private static void copy(InputStream in, File out) throws IOException {
        File temp = new File(out.getParentFile(), out.getName() + ".tmp");
        FileOutputStream fos = new FileOutputStream(temp);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) fos.write(buffer, 0, count);
        } finally {
            try { fos.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
        }
        if (!temp.renameTo(out)) throw new IOException("cannot rename " + temp);
    }

    private static boolean isMapped(File file) {
        try {
            String path = file.getAbsolutePath();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/self/maps"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.indexOf(path) >= 0) return true;
                }
            } finally {
                reader.close();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
