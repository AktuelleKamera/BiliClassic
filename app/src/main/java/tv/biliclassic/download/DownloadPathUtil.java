package tv.biliclassic.download;

import android.content.Context;
import android.os.Environment;

import java.io.File;

import tv.biliclassic.util.SdkHelper;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 离线缓存（下载）存储位置管理。
 * 支持三种位置：默认存储 / 外置SD卡 / 自定义目录，
 * 外置SD卡路径探测逻辑参考 Oldpods 的实现。
 */
public class DownloadPathUtil {

    public static final String KEY_DOWNLOAD_PATH = "download_path";
    public static final String KEY_CUSTOM_PATH = "custom_download_path";

    public static final String PATH_INTERNAL = "internal";
    public static final String PATH_EXTERNAL = "external";
    public static final String PATH_CUSTOM = "custom";

    /** 应用在存储根下的固定子目录 */
    private static final String SUB_DIR = "BiliClassic/Download";

    private DownloadPathUtil() {
    }

    public static String getChoice(Context context) {
        return SharedPreferencesUtil.getString(KEY_DOWNLOAD_PATH, PATH_INTERNAL);
    }

    public static boolean isExternalChosen(Context context) {
        return PATH_EXTERNAL.equals(getChoice(context));
    }

    public static boolean isCustomChosen(Context context) {
        return PATH_CUSTOM.equals(getChoice(context));
    }

    public static String getCustomPath(Context context) {
        return SharedPreferencesUtil.getString(KEY_CUSTOM_PATH, null);
    }

    public static void setCustomPath(Context context, String path) {
        SharedPreferencesUtil.putString(KEY_CUSTOM_PATH, path);
    }

    public static void setChoice(Context context, String choice) {
        SharedPreferencesUtil.putString(KEY_DOWNLOAD_PATH, choice);
    }

    /**
     * 获取离线缓存根目录。始终返回一个已尽力创建的目录；
     * 外置SD卡/自定义目录不可用时回退到默认存储，默认存储也不可用再回退到应用私有目录。
     */
    public static File getDownloadDir(Context context) {
        if (context == null) {
            context = SharedPreferencesUtil.getAppContext();
        }
        String choice = getChoice(context);

        if (PATH_CUSTOM.equals(choice)) {
            String custom = getCustomPath(context);
            File dir = resolveUnder(custom);
            if (dir != null) {
                return dir;
            }
        }

        if (PATH_EXTERNAL.equals(choice)) {
            String ext = getExternalSdPath(context);
            File dir = resolveUnder(ext);
            if (dir != null) {
                return dir;
            }
        }

        // 默认存储：主外部存储（旧设备上通常就是那张可移除 SD 卡）
        File primary = resolveUnder(Environment.getExternalStorageDirectory() == null
                ? null : Environment.getExternalStorageDirectory().getAbsolutePath());
        if (primary != null) {
            return primary;
        }

        // 外部存储不可用时回退到应用私有目录，保证缓存不失败
        File internal;
        if (context != null) {
            internal = new File(context.getFilesDir(), "Download");
        } else {
            internal = resolveUnder(Environment.getExternalStorageDirectory() == null
                    ? null : Environment.getExternalStorageDirectory().getAbsolutePath());
            if (internal == null) {
                internal = new File(Environment.getExternalStorageDirectory(), SUB_DIR);
            }
            return internal;
        }
        if (!internal.exists()) {
            internal.mkdirs();
        }
        return internal;
    }

    /** 当前缓存位置的显示文案（用于设置页摘要） */
    public static String getDownloadDirDisplay(Context context) {
        if (isCustomChosen(context)) {
            String custom = getCustomPath(context);
            if (custom != null && custom.length() > 0) {
                return custom;
            }
        }
        return getDownloadDir(context).getAbsolutePath();
    }

    /** 在 base 下创建/校验 BiliClassic/Download 子目录，可用则返回，否则返回 null */
    private static File resolveUnder(String base) {
        if (base == null || base.length() == 0) {
            return null;
        }
        try {
            File root = new File(base);
            if (!root.exists() && !root.mkdirs()) {
                return null;
            }
            if (!root.isDirectory() || !root.canWrite()) {
                return null;
            }
            File dir = new File(root, SUB_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (dir.isDirectory()) {
                return dir;
            }
        } catch (Throwable t) {
        }
        return null;
    }

    /** 目录是否可写（会尝试创建），供选路径时提示用户 */
    public static boolean isPathWritable(String dir) {
        return resolveUnder(dir) != null;
    }

    /** 是否探测到外置 SD 卡（含 4.4+ 上应用专属目录这种"可写但不在卡根"的情况） */
    public static boolean hasExternalSd(Context context) {
        return getExternalSdPath(context) != null;
    }

    // ================= 外置 SD 卡路径探测 =================

    /**
     * 探测外置 SD 卡的可用缓存路径。
     * 先找卡根目录；找到但不可写（如 4.4+ 整卡写入限制）或找不到时，
     * 退回 SD 卡上的应用专属目录（4.4+ 无需额外权限即可写）。
     */
    private static String getExternalSdPath(Context context) {
        String root = getExternalSdRootPath();
        if (root != null && root.length() > 0) return root;
        String appDir = getSecondarySdAppDir(context);
        if (appDir != null) {
            return appDir;
        }
        return null;
    }

    /** 探测外置 SD 卡根目录（仅路径探测，不校验可写） */
    private static String getExternalSdRootPath() {
        try {
            String secondary = System.getenv("SECONDARY_STORAGE");
            if (secondary != null && secondary.length() > 0) {
                int colon = secondary.indexOf(':');
                String first = colon >= 0 ? secondary.substring(0, colon) : secondary;
                if (new File(first).exists()) return first;
            }
        } catch (Exception e) {
        }
        // /storage/XXXX-XXXX：4.0+ vold 的 UUID 挂载点命名，最通用
        try {
            File[] list = new File("/storage").listFiles();
            if (list != null) {
                for (int i = 0; i < list.length; i++) {
                    File f = list[i];
                    String name = f.getName();
                    if ("emulated".equals(name) || "self".equals(name)) continue;
                    if (f.isDirectory() && name.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                        return f.getAbsolutePath();
                    }
                }
            }
        } catch (Throwable t) {
        }
        String[] candidates = new String[]{
                "/storage/sdcard1", "/storage/ext_sd", "/storage/extSdCard",
                "/mnt/sdcard1", "/mnt/sdcard2", "/mnt/external_sd",
                "/mnt/ext_sd", "/mnt/sdcard/ext_sd"
        };
        for (int i = 0; i < candidates.length; i++) {
            try {
                if (new File(candidates[i]).exists()) {
                    return candidates[i];
                }
            } catch (Exception e) {
            }
        }
        String mounts = getMountsExternalPath();
        if (mounts != null) {
            return mounts;
        }
        // Android 2 等旧设备大多只有一张可移除 SD 卡，它就挂载在主外部存储上（/mnt/sdcard）。
        // isExternalStorageRemovable() 是 API 9 才有的方法，1.5~2.2 上反射拿不到；
        // 但那之前的设备主外部存储本来就必然是可移除 SD 卡，直接视为可移除。
        try {
            boolean removable = SdkHelper.getSdkInt() < 9 || isExternalStorageRemovable();
            if (removable && isPrimaryMounted()) {
                String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
                File p = new File(primary);
                if (p.exists() && p.isDirectory()) {
                    return primary;
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }

    /**
     * 4.4+ 上第三方应用无权写 SD 卡根目录，但有权写卡上应用专属目录
     * /storage/XXXX-XXXX/Android/data/包名/files/。getExternalFilesDirs() 是 API 19 的方法。
     */
    private static String getSecondarySdAppDir(Context context) {
        if (context == null || SdkHelper.getSdkInt() < 19) return null;
        try {
            File[] dirs = FilesDirsApi19.get(context);
            if (dirs == null) return null;
            String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
            for (int i = 0; i < dirs.length; i++) {
                File d = dirs[i];
                if (d == null) continue;
                String p = d.getAbsolutePath();
                if (p.startsWith(primary)) continue;
                if (p.startsWith("/storage/emulated") || p.startsWith("/mnt/emulated")) continue;
                if (p.startsWith("/storage/") || p.startsWith("/mnt/")) return p;
            }
        } catch (Throwable t) {
        }
        return null;
    }

    /** getExternalFilesDirs 是 API 19 的方法，隔离成内部类：老平台被拒的只是这个类，不影响本体 */
    private static class FilesDirsApi19 {
        static File[] get(Context context) {
            return context.getExternalFilesDirs(null);
        }
    }

    /** 反射调用 Environment.isExternalStorageRemovable()（API 9+，minSdk=1 需反射） */
    private static boolean isExternalStorageRemovable() {
        try {
            java.lang.reflect.Method m = Environment.class.getMethod("isExternalStorageRemovable");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 主外部存储是否已挂载（MEDIA_MOUNTED，API 1+；SD 卡拔出/共享给 PC 时不算） */
    private static boolean isPrimaryMounted() {
        try {
            String state = Environment.getExternalStorageState();
            return Environment.MEDIA_MOUNTED.equals(state)
                    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
        } catch (Throwable t) {
            return true;
        }
    }

    private static String getMountsExternalPath() {
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        java.io.BufferedReader br = null;
        try {
            br = new java.io.BufferedReader(new java.io.FileReader("/proc/mounts"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length < 3) continue;
                String mnt = parts[1];
                String fs = parts[2];
                // vfat/exfat/fuseblk 之外，8.0+ 外置卡多为 sdcardfs 挂载，还有三星 texfat
                if (!"vfat".equals(fs) && !"exfat".equals(fs) && !"fuseblk".equals(fs)
                        && !"ntfs".equals(fs) && !"ntfs3".equals(fs)
                        && !"f2fs".equals(fs) && !"ext4".equals(fs)
                        && !"sdcardfs".equals(fs) && !"fuse".equals(fs) && !"texfat".equals(fs)) continue;
                if (mnt.equals(primary) || mnt.startsWith(primary + "/")) continue;
                if (!isRealSdMount(mnt)) continue;
                if (mnt.startsWith("/storage/") || mnt.startsWith("/mnt/")) {
                    File f = new File(mnt);
                    if (f.exists() && f.canRead()) return mnt;
                }
            }
        } catch (Exception e) {
        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception e) {
            }
        }
        return null;
    }

    /** 排除 ASEC 容器、安全区、obb、模拟挂载等非真实外置SD卡路径 */
    private static boolean isRealSdMount(String mnt) {
        if (mnt == null) return false;
        String[] bad = new String[]{
                "/mnt/asec", "/mnt/secure", "/mnt/obb", "/mnt/media_rw", "/mnt/media_rw/emulated",
                "/storage/emulated", "/data", "/system", "/cache", "/dev"
        };
        for (int i = 0; i < bad.length; i++) {
            if (mnt.equals(bad[i]) || mnt.startsWith(bad[i] + "/")) return false;
        }
        return true;
    }
}
