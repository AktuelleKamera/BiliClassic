package tv.biliclassic.download;

import android.os.Environment;

import java.io.File;

/**
 * 管理下载存储目录的位置
 */
public class VideoDownloadEnvironmentManager {

    private static File sDownloadDir;

    /**
     * 获取下载根目录
     */
    public static File getDownloadDir(Object contextOrHandler) {
        if (sDownloadDir != null) return sDownloadDir;

        // 尝试使用外部存储
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File extDir = new File(Environment.getExternalStorageDirectory(), "BiliClassic/Download");
            if (!extDir.exists()) {
                extDir.mkdirs();
            }
            if (extDir.isDirectory()) {
                sDownloadDir = extDir;
                return sDownloadDir;
            }
        }

        // 回退到内部存储（通过 context 路径推断）
        // 这是一个简化的fallback
        File dataDir = Environment.getDataDirectory();
        sDownloadDir = new File(dataDir, "data/tv.biliclassic/files/Download");
        if (!sDownloadDir.exists()) {
            sDownloadDir.mkdirs();
        }
        return sDownloadDir;
    }

    /**
     * 设置自定义下载目录
     */
    public static void setDownloadDir(File dir) {
        sDownloadDir = dir;
        if (!sDownloadDir.exists()) {
            sDownloadDir.mkdirs();
        }
    }

    /**
     * 获取封面缓存目录（兼容旧版 OfflineActivity）
     */
    public static File getCoverCacheDir() {
        return new File(getDownloadDir(null), ".covers");
    }
}
