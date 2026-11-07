package tv.biliclassic.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.model.VideoCard;

public final class LocalFavoriteStore {
    private static final String KEY = "local_favorites_v1";
    private static final String FOLDER_KEY = "local_favorite_folders_v1";
    private static final long DEFAULT_FOLDER = -1L;
    private static final String DEFAULT_FOLDER_NAME = "默认收藏夹";
    private static final int MAX_ENTRIES = 200;

    private LocalFavoriteStore() {
    }

    public static synchronized boolean toggle(long aid, long cid, String title,
                                              String cover, String upName) {
        return toggleInFolder(aid, cid, title, cover, upName, DEFAULT_FOLDER);
    }

    public static synchronized boolean toggleInFolder(long aid, long cid, String title,
                                                      String cover, String upName,
                                                      long folder) {
        if (aid <= 0) return false;
        if (!folderExists(folder)) folder = DEFAULT_FOLDER;
        JSONArray old = readArray();
        JSONObject current = find(old, aid, cid);
        if (current != null) {
            boolean sameFolder = current.optLong("folder", DEFAULT_FOLDER) == folder;
            if (sameFolder) {
                JSONArray out = new JSONArray();
                for (int i = 0; i < old.length(); i++) {
                    JSONObject item = old.optJSONObject(i);
                    if (item != null && same(item, aid, cid)) continue;
                    out.put(item);
                }
                writeArray(out);
                return false;
            }
            if ((title == null || title.length() == 0)) {
                title = current.optString("title", "");
            }
            if ((cover == null || cover.length() == 0)) {
                cover = current.optString("cover", "");
            }
            if ((upName == null || upName.length() == 0)) {
                upName = current.optString("up", "");
            }
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry != null && !same(entry, aid, cid)) out.put(entry);
        }
        JSONObject item = new JSONObject();
        try {
            item.put("aid", aid);
            item.put("cid", cid);
            item.put("folder", folder);
            item.put("title", title == null ? "" : title);
            item.put("cover", cover == null ? "" : cover);
            item.put("up", upName == null ? "" : upName);
            item.put("saved_at", System.currentTimeMillis());
        } catch (Exception ignored) {
            return false;
        }
        out.put(item);
        while (out.length() > MAX_ENTRIES) {
            out.remove(out.length() - 1);
        }
        writeArray(out);
        return true;
    }

    public static synchronized boolean isFavorite(long aid, long cid) {
        return find(readArray(), aid, cid) != null;
    }

    public static synchronized List<FavoriteFolder> getFolders() {
        JSONArray folders = readFolders();
        boolean hasDefault = false;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject item = folders.optJSONObject(i);
            if (item != null && item.optLong("fid", 0) == DEFAULT_FOLDER) {
                hasDefault = true;
                break;
            }
        }
        if (!hasDefault) {
            JSONObject def = new JSONObject();
            try {
                def.put("fid", DEFAULT_FOLDER);
                def.put("name", DEFAULT_FOLDER_NAME);
            } catch (Exception ignored) {
            }
            folders.put(def);
            writeFolders(folders);
        }
        return parseFolders(folders);
    }

    public static synchronized long createFolder(String name) {
        if (name == null || name.length() == 0) return DEFAULT_FOLDER;
        JSONArray folders = readFolders();
        long min = -1;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject item = folders.optJSONObject(i);
            if (item != null) min = Math.min(min, item.optLong("fid", -1));
        }
        long fid = min - 1;
        JSONObject def = new JSONObject();
        try {
            def.put("fid", fid);
            def.put("name", name);
        } catch (Exception ignored) {
            return DEFAULT_FOLDER;
        }
        folders.put(def);
        writeFolders(folders);
        return fid;
    }

    public static synchronized List<VideoCard> load(long folder) {
        JSONArray array = readArray();
        List<VideoCard> result = new ArrayList<VideoCard>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            if (item.optLong("folder", DEFAULT_FOLDER) != folder) continue;
            result.add(toCard(item));
        }
        return result;
    }

    public static synchronized List<VideoCard> load() {
        JSONArray array = readArray();
        List<VideoCard> result = new ArrayList<VideoCard>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) result.add(toCard(item));
        }
        return result;
    }

    private static VideoCard toCard(JSONObject item) {
        long aid = item.optLong("aid", 0);
        VideoCard card = new VideoCard(item.optString("title", ""),
                item.optString("up", ""), "", item.optString("cover", ""),
                aid, "");
        card.cid = item.optLong("cid", 0);
        return card;
    }

    public static synchronized boolean folderExists(long fid) {
        if (fid == DEFAULT_FOLDER) return true;
        JSONArray folders = readFolders();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject item = folders.optJSONObject(i);
            if (item != null && item.optLong("fid", 0) == fid) return true;
        }
        return false;
    }

    public static synchronized void updateMetadata(long aid, String title, String cover, String upName) {
        JSONArray array = readArray();
        boolean changed = false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null || item.optLong("aid", 0) != aid) continue;
            try {
                if (title != null && title.length() > 0) item.put("title", title);
                if (cover != null && cover.length() > 0) item.put("cover", cover);
                if (upName != null && upName.length() > 0) item.put("up", upName);
                changed = true;
            } catch (Exception ignored) {
            }
        }
        if (changed) writeArray(array);
    }

    public static synchronized void remove(long aid, long cid) {
        JSONArray old = readArray();
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && same(item, aid, cid)) continue;
            out.put(item);
        }
        writeArray(out);
    }

    private static List<FavoriteFolder> parseFolders(JSONArray folders) {
        List<FavoriteFolder> result = new ArrayList<FavoriteFolder>();
        JSONArray items = readArray();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject item = folders.optJSONObject(i);
            if (item == null) continue;
            FavoriteFolder folder = new FavoriteFolder();
            folder.fid = item.optLong("fid", DEFAULT_FOLDER);
            folder.name = item.optString("name", "");
            for (int j = 0; j < items.length(); j++) {
                JSONObject video = items.optJSONObject(j);
                if (video != null && video.optLong("folder", DEFAULT_FOLDER) == folder.fid) {
                    folder.videoCount++;
                    if (folder.cover == null || folder.cover.length() == 0) {
                        folder.cover = video.optString("cover", "");
                    }
                }
            }
            result.add(folder);
        }
        return result;
    }

    private static JSONArray readFolders() {
        String raw = SharedPreferencesUtil.getString(FOLDER_KEY, "");
        if (raw == null || raw.length() == 0) return new JSONArray();
        String plain = LocalJsonCrypto.decrypt(raw);
        if (plain == null) return new JSONArray();
        if (!LocalJsonCrypto.isEncrypted(raw)) {
            SharedPreferencesUtil.putString(FOLDER_KEY, LocalJsonCrypto.encrypt(plain));
        }
        try {
            return new JSONArray(plain);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void writeFolders(JSONArray folders) {
        SharedPreferencesUtil.putString(FOLDER_KEY, LocalJsonCrypto.encrypt(folders.toString()));
    }

    private static JSONArray readArray() {
        String raw = SharedPreferencesUtil.getString(KEY, "");
        if (raw == null || raw.length() == 0) return new JSONArray();
        String plain = LocalJsonCrypto.decrypt(raw);
        if (plain == null) return new JSONArray();
        if (!LocalJsonCrypto.isEncrypted(raw)) {
            SharedPreferencesUtil.putString(KEY, LocalJsonCrypto.encrypt(plain));
        }
        try {
            return new JSONArray(plain);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void writeArray(JSONArray array) {
        SharedPreferencesUtil.putString(KEY, LocalJsonCrypto.encrypt(array.toString()));
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
