package tv.biliclassic.download;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理已缓存视频的目录结构
 * 目录布局（参考原版哔哩哔哩）：
 *   {downloadRoot}/
 *     {avid}/                    ← 每个av号一个文件夹
 *       {page}/                  ← 每个分P一个子文件夹
 *         entry.json             ← 视频元数据
 *         danmaku.xml            ← 已缓存弹幕文件
 *         cover.jpg              ← 视频封面
 *         video.mp4              ← 视频文件
 */
public class VideoDownloadEnvironment {

    private static final String ENTRY_FILE_NAME = "entry.json";
    private static final String DANMAKU_FILE_NAME = "danmaku.xml";
    private static final String COVER_FILE_NAME = "cover.jpg";
    private static final String VIDEO_FILE_NAME = "video.mp4";

    private final File downloadRootDir;
    public long avid;
    public int page;

    public VideoDownloadEnvironment(File downloadRootDir) {
        this.downloadRootDir = downloadRootDir;
    }

    public VideoDownloadEnvironment(File downloadRootDir, long avid, int page) {
        this.downloadRootDir = downloadRootDir;
        this.avid = avid;
        this.page = page;
    }

    // ========== 目录获取 ==========

    public File getDownloadRootDir() {
        return downloadRootDir;
    }

    private File getAvidDir(boolean create) throws IOException {
        File dir = new File(downloadRootDir, String.valueOf(avid));
        if (create && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getPageDir(boolean create) throws IOException {
        File dir = new File(getAvidDir(create), String.valueOf(page));
        if (create && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    // ========== 文件路径 ==========

    public File getEntryFile(boolean create) throws IOException {
        return new File(getPageDir(create), ENTRY_FILE_NAME);
    }

    public File getDanmakuFile(boolean create) {
        try {
            return new File(getPageDir(create), DANMAKU_FILE_NAME);
        } catch (IOException e) {
            return new File(downloadRootDir, avid + "/" + page + "/" + DANMAKU_FILE_NAME);
        }
    }

    public File getCoverFile(boolean create) {
        try {
            return new File(getPageDir(create), COVER_FILE_NAME);
        } catch (IOException e) {
            return new File(downloadRootDir, avid + "/" + page + "/" + COVER_FILE_NAME);
        }
    }

    public File getVideoFile() {
        try {
            return new File(getPageDir(false), VIDEO_FILE_NAME);
        } catch (IOException e) {
            return new File(downloadRootDir, avid + "/" + page + "/" + VIDEO_FILE_NAME);
        }
    }

    // ========== 保存 / 加载 entry ==========

    public boolean saveEntry(VideoDownloadEntry entry) {
        try {
            entry.downloadEnv = this;
            entry.storagePath = getEntryFile(true).getAbsolutePath();
            return entry.saveToFile(getEntryFile(true));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public VideoDownloadEntry loadEntry() {
        try {
            File entryFile = getEntryFile(false);
            if (!entryFile.isFile()) return null;
            VideoDownloadEntry entry = VideoDownloadEntry.loadFromFile(entryFile);
            if (entry != null) {
                entry.downloadEnv = this;
            }
            return entry;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ========== 扫描目录 ==========

    /**
     * 加载指定 avid 下所有分P的 entry
     */
    public ArrayList<VideoDownloadEntry> loadEntriesForAvid(long avid) {
        this.avid = avid;
        ArrayList<VideoDownloadEntry> entries = new ArrayList<VideoDownloadEntry>();
        try {
            File avDir = getAvidDir(false);
            if (!avDir.isDirectory()) return entries;
            String[] pageDirs = avDir.list();
            if (pageDirs == null) return entries;
            for (String pageDirName : pageDirs) {
                if (pageDirName == null || pageDirName.length() == 0) continue;
                if (!isDigitsOnly(pageDirName)) continue;
                try {
                    int p = Integer.parseInt(pageDirName);
                    VideoDownloadEnvironment subEnv = new VideoDownloadEnvironment(downloadRootDir, avid, p);
                    VideoDownloadEntry entry = subEnv.loadEntry();
                    if (entry != null && entry.isValid()) {
                        entries.add(entry);
                    }
                } catch (NumberFormatException e) {
                    // skip non-numeric folder
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return entries;
    }

    /**
     * 加载所有已缓存的视频条目
     */
    public static ArrayList<VideoDownloadEntry> loadAllEntries(File downloadRootDir) {
        ArrayList<VideoDownloadEntry> allEntries = new ArrayList<VideoDownloadEntry>();
        if (downloadRootDir == null || !downloadRootDir.isDirectory()) return allEntries;

        String[] avDirs = downloadRootDir.list();
        if (avDirs == null) return allEntries;

        for (String avDirName : avDirs) {
            if (avDirName == null || avDirName.length() == 0) continue;
            if (!isDigitsOnly(avDirName)) continue;
            try {
                long avidNum = Long.parseLong(avDirName);
                VideoDownloadEnvironment env = new VideoDownloadEnvironment(downloadRootDir);
                ArrayList<VideoDownloadEntry> entries = env.loadEntriesForAvid(avidNum);
                if (entries != null) {
                    allEntries.addAll(entries);
                }
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return allEntries;
    }

    /**
     * 删除整个下载条目（删除 avid/page 目录）
     */
    public boolean deleteEntry() {
        try {
            File pageDir = getPageDir(false);
            if (pageDir.isDirectory()) {
                deleteDir(pageDir);
            }
            // 如果 avid 目录为空也删除
            File avDir = getAvidDir(false);
            if (avDir.isDirectory()) {
                String[] remaining = avDir.list();
                if (remaining == null || remaining.length == 0) {
                    avDir.delete();
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                }
                f.delete();
            }
        }
        dir.delete();
    }

    // ========== 工具方法 ==========

    private static boolean isDigitsOnly(String str) {
        if (str == null || str.length() == 0) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) return false;
        }
        return true;
    }

    /**
     * 推断 typeTag，简单根据质量判断
     */
    public static String getTypeTagFromQuality(int qn) {
        // qn <= 16 → flv (360P以下)
        // qn > 16 → mp4
        return qn > 16 ? "mp4" : "flv";
    }

    /**
     * 将 qn 值转为画质名称
     */
    public static String getQualityName(int qn) {
        switch (qn) {
            case 6: return "240P 极速";
            case 16: return "360P 流畅";
            case 32: return "480P 清晰";
            case 64: return "720P 高清";
            case 74: return "720P60 高帧率";
            case 80: return "1080P 高清";
            case 112: return "1080P+ 高码率";
            case 116: return "1080P60 高帧率";
            case 120: return "4K 超清";
            default: return qn + "P";
        }
    }
}
