package tv.biliclassic.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.model.VideoCard;

/**
 * 本地观看历史。不需要登录，也不会上传。
 */
public final class LocalHistoryStore {
    private static final String KEY = "local_history_v1";
    private static final int MAX_ENTRIES = 100;
    private static final long PROGRESS_SAVE_INTERVAL_MS = 10000L;

    private LocalHistoryStore() {
    }

    public static synchronized void record(long aid, long cid, String title,
                                           String cover, String upName,
                                           int progressMs) {
        if (aid <= 0 && cid <= 0) return;

        JSONArray old = readArray();
        JSONObject current = find(old, aid, cid);
        String keepTitle = current != null ? current.optString("title", "") : "";
        String keepCover = current != null ? current.optString("cover", "") : "";
        String keepUp = current != null ? current.optString("up", "") : "";
        String keepBvid = current != null ? current.optString("bvid", "") : "";

        if (title == null || title.length() == 0) title = keepTitle;
        if (cover == null || cover.length() == 0) cover = keepCover;
        if (upName == null || upName.length() == 0) upName = keepUp;
        if (title == null || title.length() == 0) title = "未知标题";
        if (upName == null || upName.length() == 0) upName = "本地观看";

        JSONObject item = new JSONObject();
        try {
            item.put("aid", aid);
            item.put("cid", cid);
            item.put("bvid", keepBvid);
            item.put("title", title);
            item.put("cover", cover == null ? "" : cover);
            item.put("up", upName == null ? "" : upName);
            item.put("progress", progressMs < 0 ? 0 : progressMs);
            item.put("saved_at", System.currentTimeMillis());
        } catch (Exception ignored) {
            return;
        }

        JSONArray out = new JSONArray();
        out.put(item);
        for (int i = 0; i < old.length() && out.length() < MAX_ENTRIES; i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry == null || same(entry, aid, cid)) continue;
            out.put(entry);
        }
        SharedPreferencesUtil.putString(KEY, out.toString());
    }

    public static synchronized void updateProgress(long aid, long cid, int progressMs) {
        if (aid <= 0 && cid <= 0) return;
        JSONArray old = readArray();
        JSONObject current = find(old, aid, cid);
        if (current == null) return;

        long now = System.currentTimeMillis();
        long savedAt = current.optLong("saved_at", 0);
        if (now - savedAt < PROGRESS_SAVE_INTERVAL_MS) return;

        try {
            current.put("progress", progressMs < 0 ? 0 : progressMs);
            current.put("saved_at", now);
        } catch (Exception ignored) {
            return;
        }

        JSONArray out = new JSONArray();
        out.put(current);
        for (int i = 0; i < old.length() && out.length() < MAX_ENTRIES; i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry == null || same(entry, aid, cid)) continue;
            out.put(entry);
        }
        SharedPreferencesUtil.putString(KEY, out.toString());
    }

    public static synchronized List<VideoCard> load() {
        JSONArray array = readArray();
        List<VideoCard> result = new ArrayList<VideoCard>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            long aid = item.optLong("aid", 0);
            long cid = item.optLong("cid", 0);
            String bvid = item.optString("bvid", "");
            if (aid <= 0 && (bvid == null || bvid.length() == 0)) continue;

            int progressMs = item.optInt("progress", 0);
            String view;
            if (progressMs <= 0) {
                view = "还没看过";
            } else {
                view = "看到" + StringUtil.toTime(progressMs / 1000);
            }

            VideoCard card = new VideoCard(
                    item.optString("title", "未知标题"),
                    item.optString("up", ""),
                    view,
                    item.optString("cover", ""),
                    aid,
                    bvid);
            card.cid = cid;
            result.add(card);
        }
        return result;
    }

    private static JSONArray readArray() {
        String raw = SharedPreferencesUtil.getString(KEY, "");
        if (raw == null || raw.length() == 0) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject find(JSONArray array, long aid, long cid) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && same(item, aid, cid)) return item;
        }
        return null;
    }

    private static boolean same(JSONObject item, long aid, long cid) {
        long oldAid = item.optLong("aid", 0);
        long oldCid = item.optLong("cid", 0);
        if (aid > 0 && oldAid > 0) return oldAid == aid;
        return cid > 0 && oldCid == cid;
    }
}
