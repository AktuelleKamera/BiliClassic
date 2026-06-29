package tv.biliclassic.download;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;

/**
 * 视频下载条目数据模型，对应原版 entry.json
 * 职责：存储已缓存视频的完整元数据（av/bv号、标题、分P、画质、封面等）
 * 可序列化为 JSON 写入文件，也可从 JSON 恢复
 */
public class VideoDownloadEntry implements Serializable {

    // ========== JSON key 常量 ==========
    private static final String KEY_AVID = "avid";
    private static final String KEY_BVID = "bvid";
    private static final String KEY_TITLE = "title";
    private static final String KEY_PAGE_TITLE = "page_title";
    private static final String KEY_CID = "cid";
    private static final String KEY_PAGE = "page";
    private static final String KEY_QUALITY = "quality";
    private static final String KEY_QUALITY_NAME = "quality_name";
    private static final String KEY_COVER_URL = "cover_url";
    private static final String KEY_TYPE_TAG = "type_tag";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_TOTAL_BYTES = "total_bytes";
    private static final String KEY_DOWNLOADED_BYTES = "downloaded_bytes";
    private static final String KEY_IS_COMPLETED = "is_completed";
    private static final String KEY_TIME_STAMP = "time_stamp";
    private static final String KEY_STORAGE_PATH = "storage_path";
    private static final String KEY_UP_NAME = "up_name";
    private static final String KEY_DESC = "description";
    private static final String KEY_TAGS = "tags";

    // ========== 状态常量 ==========
    public static final int STATE_STOPPED = 1000;
    public static final int STATE_PREPARING = 1001;
    public static final int STATE_IN_QUEUE = 1002;
    public static final int STATE_DOWNLOADING = 1003;

    // ========== 字段 ==========
    public long avid;
    public String bvid;
    public String title;
    public String pageTitle;
    public long cid;
    public int page;           // 分P序号（1-based）
    public int quality;        // qn值，如16=360P, 64=720P
    public String qualityName; // 画质名称，如"360P"
    public String coverUrl;    // 封面原始URL
    public String typeTag;     // "mp4" / "flv"
    public long duration;      // 视频时长（秒）
    public long totalBytes;
    public long downloadedBytes;
    public boolean isCompleted;
    public long timeStamp;
    public String storagePath;  // entry.json 所在路径
    public String upName;       // UP主名称
    public String description;  // 视频简介
    public String tags;         // 视频标签（逗号分隔）

    // 非持久化字段
    public transient int state = STATE_STOPPED;
    public transient VideoDownloadEnvironment downloadEnv;
    public transient boolean willStop;
    public transient boolean willBeRemoved;
    public transient boolean isDestroyed;
    public transient int lastErrorCode;
    public transient String lastErrorMessage;

    public VideoDownloadEntry() {
    }

    public boolean isValid() {
        return avid > 0 && cid > 0 && page > 0 && typeTag != null && typeTag.length() > 0;
    }

    public long getKey() {
        return ((long) avid << 32) | (page & 0xFFFFFFFFL);
    }

    public String getKeyName() {
        return "av" + avid + "-p" + page;
    }

    public int getProgressPercentage() {
        if (totalBytes <= 0) return 0;
        if (downloadedBytes <= 0) return 0;
        int pct = (int) (downloadedBytes * 100 / totalBytes);
        return Math.min(Math.max(pct, 0), 100);
    }

    // ========== JSON 序列化 ==========

    public void writeToJSONObject(JSONObject obj) throws JSONException {
        obj.put(KEY_AVID, avid);
        obj.put(KEY_BVID, bvid != null ? bvid : "");
        obj.put(KEY_TITLE, title != null ? title : "");
        obj.put(KEY_PAGE_TITLE, pageTitle != null ? pageTitle : "");
        obj.put(KEY_CID, cid);
        obj.put(KEY_PAGE, page);
        obj.put(KEY_QUALITY, quality);
        obj.put(KEY_QUALITY_NAME, qualityName != null ? qualityName : "");
        obj.put(KEY_COVER_URL, coverUrl != null ? coverUrl : "");
        obj.put(KEY_TYPE_TAG, typeTag != null ? typeTag : "mp4");
        obj.put(KEY_DURATION, duration);
        obj.put(KEY_TOTAL_BYTES, totalBytes);
        obj.put(KEY_DOWNLOADED_BYTES, downloadedBytes);
        obj.put(KEY_IS_COMPLETED, isCompleted);
        obj.put(KEY_TIME_STAMP, timeStamp);
        obj.put(KEY_STORAGE_PATH, storagePath != null ? storagePath : "");
        obj.put(KEY_UP_NAME, upName != null ? upName : "");
        obj.put(KEY_DESC, description != null ? description : "");
        obj.put(KEY_TAGS, tags != null ? tags : "");
    }

    public void readFromJSONObject(JSONObject obj) throws JSONException {
        avid = obj.optLong(KEY_AVID, 0);
        bvid = obj.optString(KEY_BVID, "");
        title = obj.optString(KEY_TITLE, "");
        pageTitle = obj.optString(KEY_PAGE_TITLE, "");
        cid = obj.optLong(KEY_CID, 0);
        page = obj.optInt(KEY_PAGE, 0);
        quality = obj.optInt(KEY_QUALITY, 0);
        qualityName = obj.optString(KEY_QUALITY_NAME, "");
        coverUrl = obj.optString(KEY_COVER_URL, "");
        typeTag = obj.optString(KEY_TYPE_TAG, "mp4");
        duration = obj.optLong(KEY_DURATION, 0);
        totalBytes = obj.optLong(KEY_TOTAL_BYTES, 0);
        downloadedBytes = obj.optLong(KEY_DOWNLOADED_BYTES, 0);
        isCompleted = obj.optBoolean(KEY_IS_COMPLETED, false);
        timeStamp = obj.optLong(KEY_TIME_STAMP, 0);
        storagePath = obj.optString(KEY_STORAGE_PATH, "");
        upName = obj.optString(KEY_UP_NAME, "");
        description = obj.optString(KEY_DESC, "");
        tags = obj.optString(KEY_TAGS, "");
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject obj = new JSONObject();
        writeToJSONObject(obj);
        return obj;
    }

    // ========== 文件读写 ==========

    /**
     * 保存到 entry.json
     */
    public boolean saveToFile(File entryFile) {
        try {
            JSONObject obj = new JSONObject();
            writeToJSONObject(obj);
            DownloadJsonUtil.writeToFile(obj, entryFile);
            this.storagePath = entryFile.getAbsolutePath();
            this.timeStamp = entryFile.lastModified();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 从 entry.json 读取
     */
    public static VideoDownloadEntry loadFromFile(File entryFile) {
        try {
            JSONObject obj = DownloadJsonUtil.readFromFile(entryFile);
            if (obj == null) return null;
            VideoDownloadEntry entry = new VideoDownloadEntry();
            entry.readFromJSONObject(obj);
            entry.storagePath = entryFile.getAbsolutePath();
            entry.timeStamp = entryFile.lastModified();
            return entry;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取视频文件路径
     */
    public File getVideoFile() {
        if (downloadEnv == null) return null;
        return downloadEnv.getVideoFile();
    }

    /**
     * 获取封面文件路径
     */
    public File getCoverFile() {
        if (downloadEnv == null) return null;
        return downloadEnv.getCoverFile(false);
    }

    /**
     * 获取弹幕文件路径
     */
    public File getDanmakuFile() {
        if (downloadEnv == null) return null;
        return downloadEnv.getDanmakuFile(false);
    }
}
