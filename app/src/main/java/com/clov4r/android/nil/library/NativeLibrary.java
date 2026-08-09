package com.clov4r.android.nil.library;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 软解库加载（对应 libcmplayer_*.so 导出的
 * Java_com_clov4r_android_nil_library_NativeLibrary_nativeLoadFFmpegLib 符号）。
 *
 * 优先使用设备上已安装的 MoboPlayer 解码器包（com.clov4r.android.nil.*）内的 so：
 *  - 通过 PackageManager 找到匹配包，从 APK(zip) 中提取 lib/armeabi/libcmplayer_*.so 与
 *    libffmpeg_*.so 到应用私有目录，再 System.load(path)；
 *  - 外部包没有或提取失败时，回退到应用自带 jniLibs（System.loadLibrary）。
 */
public class NativeLibrary {

    private static final String TAG = "SoftNativeLib";

    // 已安装 MoboPlayer 解码器包名候选（armv6_vfp / armv6 / armv7 / neon 等变体）
    private static final String[] DECODER_PACKAGES = {
            "com.clov4r.android.nil.armv7_neon",
            "com.clov4r.android.nil.armv7",
            "com.clov4r.android.nil.neon",
            "com.clov4r.android.nil.armv6_vfp",
            "com.clov4r.android.nil.armv6",
            "com.clov4r.android.nil.armv5te",
            "com.clov4r.android.nil"
    };

    private static int f1213c = 6; // CPU 架构：5 / 6 / 7
    private static HashSet<String> f1216f = new HashSet<String>();
    private static boolean f1205b = false;
    private static String sExtractedDir = null;

    private NativeLibrary() {
    }

    public static boolean load() {
        if (f1205b) {
            return true;
        }
        try {
            detectCpu();
            Context ctx = tv.biliclassic.util.SharedPreferencesUtil.getAppContext();
            // 1) 优先从已安装的 MoboPlayer 解码器包加载
            if (ctx != null && loadFromInstalledDecoder(ctx)) {
                f1205b = true;
                return true;
            }
            // 2) 回退：应用自带 jniLibs
            String lib = libraryName();
            Log.d(TAG, "loadLibrary(bundled) " + lib);
            System.loadLibrary(lib);
            // bundled ffmpeg 在应用 lib 目录，同样用相对穿越路径让 cmplayer
            // 硬编码的 /data/data/<decoderPkg>/ffmpeg/ 前缀拼完后落到本应用 lib。
            String ffmpegArg = ctx != null
                    ? "../../" + ctx.getPackageName() + "/lib/" + ffmpegName()
                    : ffmpegName();
            Log.d(TAG, "loadFFmpeg " + ffmpegArg);
            if (nativeLoadFFmpegLib(ffmpegArg) != 0) {
                Log.e(TAG, "nativeLoadFFmpegLib failed");
                return false;
            }
            f1205b = true;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "load native lib error", t);
            return false;
        }
    }

    /**
     * 从已安装的 MoboPlayer 解码器包提取并加载 so。
     */
    private static boolean loadFromInstalledDecoder(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        if (pm == null) {
            return false;
        }
        List<PackageInfo> installed = null;
        try {
            installed = pm.getInstalledPackages(0);
        } catch (Throwable t) {
            Log.e(TAG, "getInstalledPackages failed", t);
            return false;
        }
        if (installed == null) {
            return false;
        }
        // 收集已安装的解码器包（按候选顺序优先）
        List<PackageInfo> candidates = new ArrayList<PackageInfo>();
        for (int i = 0; i < DECODER_PACKAGES.length; i++) {
            String pkg = DECODER_PACKAGES[i];
            for (PackageInfo pi : installed) {
                if (pi != null && pi.packageName != null && pkg.equals(pi.packageName)) {
                    candidates.add(pi);
                    break;
                }
            }
        }
        if (candidates.isEmpty()) {
            Log.d(TAG, "no installed MoboPlayer decoder package");
            return false;
        }
        // 按变体优先级遍历：cmplayer 与 ffmpeg 允许来自不同包
        //（有的机型装的是 armv6 包（只有 ffmpeg）+ 主包（只有 cmplayer），需跨包配对）。
        String[] variants = variantCandidates();
        for (int i = 0; i < variants.length; i++) {
            try {
                if (tryLoadVariant(ctx, candidates, variants[i])) {
                    return true;
                }
            } catch (Throwable t) {
                Log.e(TAG, "load variant " + variants[i] + " failed", t);
            }
        }
        return false;
    }

    /**
     * 尝试按指定变体加载：cmplayer 与 ffmpeg 可分别从不同的候选包提取配对。
     */
    private static boolean tryLoadVariant(Context ctx, List<PackageInfo> candidates, String variant) throws Exception {
        String lib = libraryName(variant);
        String ffmpeg = "libffmpeg_" + variant + ".so";

        String libEntry = null;
        String ffmpegEntry = null;
        PackageInfo libPkg = null;
        PackageInfo ffmpegPkg = null;

        for (PackageInfo pi : candidates) {
            String apkPath = pi.applicationInfo != null ? pi.applicationInfo.sourceDir : null;
            if (apkPath == null || !new File(apkPath).exists()) {
                continue;
            }
            ZipFile zip = new ZipFile(apkPath);
            try {
                if (libEntry == null) {
                    String e = findEntry(zip, "lib/armeabi/lib" + lib + ".so");
                    if (e != null) {
                        libEntry = e;
                        libPkg = pi;
                    }
                }
                if (ffmpegEntry == null) {
                    String e = findEntry(zip, "lib/armeabi/" + ffmpeg);
                    if (e != null) {
                        ffmpegEntry = e;
                        ffmpegPkg = pi;
                    }
                }
            } finally {
                zip.close();
            }
            if (libEntry != null && ffmpegEntry != null) {
                break;
            }
        }

        if (libEntry == null || ffmpegEntry == null) {
            Log.d(TAG, candidates + " no " + lib + "/" + ffmpeg
                    + " (libEntry=" + libEntry + " ffmpegEntry=" + ffmpegEntry + ")");
            return false;
        }

        File dir = getExtractDir(ctx);
        File libFile = extractFromApk(libPkg, libEntry, dir, lib + ".so");
        File ffmpegFile = extractFromApk(ffmpegPkg, ffmpegEntry, dir, ffmpeg);
        Log.d(TAG, "load external lib " + libFile.getAbsolutePath()
                + " (from " + libPkg.packageName + ")");
        System.load(libFile.getAbsolutePath());
        // cmplayer 内部硬编码 ffmpeg 目录 /data/data/<decoderPkg>/ffmpeg/，
        // 会把传入参数原样拼在该前缀后。传入绝对路径会拼成
        // /data/data/<decoderPkg>/ffmpeg//data/data/... 找不到。
        // 传相对穿越路径 ../../<本应用包名>/...，使拼接后的路径正好
        // 落到本应用私有目录（/data/data/<decoderPkg>/ffmpeg/../../<pkg>/...）。
        String ffmpegArg = ffmpegRelArg(ctx, ffmpegFile.getAbsolutePath());
        Log.d(TAG, "loadFFmpeg " + ffmpegArg + " (from " + ffmpegPkg.packageName + ")");
        if (nativeLoadFFmpegLib(ffmpegArg) != 0) {
            Log.e(TAG, "external nativeLoadFFmpegLib failed");
            return false;
        }
        Log.d(TAG, "loaded from installed packages variant=" + variant
                + " cmplayer=" + libPkg.packageName + " ffmpeg=" + ffmpegPkg.packageName);
        return true;
    }

    private static File extractFromApk(PackageInfo pi, String entryName, File dir, String outName) throws Exception {
        String apkPath = pi.applicationInfo != null ? pi.applicationInfo.sourceDir : null;
        if (apkPath == null) {
            throw new Exception("no apk path for " + pi.packageName);
        }
        ZipFile zip = new ZipFile(apkPath);
        try {
            return extract(zip, entryName, dir, outName);
        } finally {
            zip.close();
        }
    }

    /**
     * cmplayer 内部硬编码 ffmpeg 目录 /data/data/&lt;decoderPkg&gt;/ffmpeg/，调用方传入的
     * 路径会被原样拼在该前缀后。传入绝对路径会变成
     * /data/data/&lt;decoderPkg&gt;/ffmpeg//data/data/... 而找不到；
     * 这里把本应用私有目录中的绝对路径转成相对穿越路径
     * ../../&lt;appPkg&gt;/...，使拼接后的最终路径正确指向本应用文件。
     */
    private static String ffmpegRelArg(Context ctx, String absPath) {
        // absPath 形如 /data/data/<appPkg>/files/mobo_decoder/libffmpeg_*.so，
        // 去掉 /data/data/ 前缀后，加上 ../../ 使 cmplayer 前缀拼完后正好回到 /data/data/<appPkg>/...
        if (absPath.startsWith("/data/data/")) {
            return "../../" + absPath.substring("/data/data/".length());
        }
        return absPath;
    }

    // 变体候选（从最适配到最通用）
    private static String[] variantCandidates() {
        String primary = cpuVariant();
        // 去重并附加通用回退
        ArrayList<String> list = new ArrayList<String>();
        addVariant(list, primary);
        if (f1213c >= 7) {
            addVariant(list, "armv7_neon");
            addVariant(list, "armv7_vfpv3");
            addVariant(list, "armv7_vfp");
            addVariant(list, "armv7");
            // ARMv7+ 向下兼容，可回退 armv6/armv5
            addVariant(list, "armv6_vfp");
            addVariant(list, "armv6");
            addVariant(list, "armv5te");
        } else if (f1213c == 6) {
            addVariant(list, "armv6_vfp");
            addVariant(list, "armv6");
            // ARMv6 设备不能执行 armv7 指令，绝不回退到 armv7 系（否则 SIGILL）
            addVariant(list, "armv5te");
        } else {
            addVariant(list, "armv5te_vfp");
            addVariant(list, "armv5te");
        }
        return list.toArray(new String[list.size()]);
    }

    private static void addVariant(ArrayList<String> list, String v) {
        if (v != null && !list.contains(v)) {
            list.add(v);
        }
    }

    private static String findEntry(ZipFile zip, String prefix) {
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.getName().equals(prefix)) {
                return e.getName();
            }
        }
        return null;
    }

    private static File getExtractDir(Context ctx) {
        if (sExtractedDir == null) {
            File dir = new File(ctx.getFilesDir(), "mobo_decoder");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            sExtractedDir = dir.getAbsolutePath();
        }
        return new File(sExtractedDir);
    }

    private static File extract(ZipFile zip, String entryName, File dir, String outName) throws Exception {
        File out = new File(dir, outName);
        if (out.exists() && out.length() > 0) {
            return out;
        }
        ZipEntry e = zip.getEntry(entryName);
        InputStream in = zip.getInputStream(e);
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        } finally {
            fos.close();
            in.close();
        }
        return out;
    }

    public static int sdkVersion() {
        try {
            return Integer.valueOf(Build.VERSION.SDK).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void detectCpu() {
        f1213c = 6;
        f1216f = new HashSet<String>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                String strLine = line.trim();
                if (strLine.startsWith("Features")) {
                    int idx = strLine.indexOf(":");
                    if (idx >= 0) {
                        String[] features = strLine.substring(idx + 1).trim().split("\\s+");
                        for (String f : features) {
                            if (f != null && f.length() > 0) {
                                f1216f.add(f);
                            }
                        }
                    }
                } else if (strLine.startsWith("CPU architecture")) {
                    int idx = strLine.indexOf(":");
                    if (idx >= 0) {
                        // 兼容 "5TEJ"、"6"、"7"、"8" 等写法：取第一个数字
                        String s = strLine.substring(idx + 1).trim();
                        int numStart = -1;
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (c >= '0' && c <= '9') {
                                numStart = i;
                                break;
                            }
                        }
                        if (numStart >= 0) {
                            int numEnd = numStart;
                            while (numEnd < s.length()) {
                                char c = s.charAt(numEnd);
                                if (c >= '0' && c <= '9') {
                                    numEnd++;
                                } else {
                                    break;
                                }
                            }
                            try {
                                f1213c = Integer.parseInt(s.substring(numStart, numEnd));
                            } catch (NumberFormatException e) {
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
        }
        if (f1213c < 5) {
            f1213c = 5;
        } else if (f1213c >= 8) {
            f1213c = 7;
        }
        Log.d(TAG, "cpu arch=" + f1213c + " neon=" + hasFeature("neon"));
    }

    private static boolean hasFeature(String f) {
        return f1216f != null && f1216f.contains(f);
    }

    // NEON 支持：ARMv7 设备内核报 "neon"；ARMv8(AArch64) 上 32 位兼容层报 "asimd"
    private static boolean hasNeon() {
        return hasFeature("neon") || hasFeature("asimd");
    }

    private static boolean hasVfp() {
        return hasFeature("vfp") || hasFeature("vfpv3") || hasFeature("asimd");
    }

    /**
     * 与 MoboPlayer NativeLibrary.m459c() 一致：armv5te / armv6 / armv7(+_neon/_vfpv3/_vfp)。
     * ARMv8(64 位内核，32 位兼容执行) 按 armv7 处理。
     */
    private static String cpuVariant() {
        String str = "";
        if (f1213c == 5) {
            str = "armv5te";
        } else if (f1213c == 6) {
            str = "armv6";
        } else if (f1213c >= 7) {
            str = "armv7";
        }
        if (hasNeon()) {
            return str + "_neon";
        }
        if (hasFeature("vfpv3")) {
            return str + "_vfpv3";
        }
        if (hasVfp()) {
            return str + "_vfp";
        }
        return str;
    }

    private static String ffmpegName() {
        return "libffmpeg_" + cpuVariant() + ".so";
    }

    /**
     * 与 MoboPlayer NativeLibrary.m461e() 一致。
     * v7/ARMv8 设备优先选 cmplayer_v7（配合 neon 性能更好）。
     */
    private static String libraryName() {
        return libraryName(cpuVariant());
    }

    // 按变体选择 cmplayer 封装库名
    private static String libraryName(String variant) {
        String base = (variant != null && variant.startsWith("armv7")) ? "cmplayer_v7" : "cmplayer_v5";
        // API 4(1.6) 之前的系统没有 libbinder.so，而 libcmplayer_v5.so 依赖它；
        // libcmplayer_v5_4.so 不依赖 binder，所以 API 3(1.5)/API 4(1.6) 都用 v5_4。
        String str = sdkVersion() <= 4 ? "cmplayer_v5_4" : base;
        if (sdkVersion() >= 14) {
            str = str + "_14";
        } else if (sdkVersion() > 7) {
            str = str + "_8";
        }
        return str;
    }

    private static native int nativeLoadFFmpegLib(String libName);
}
