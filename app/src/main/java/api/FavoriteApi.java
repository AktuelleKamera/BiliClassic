/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年6月19日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import tv.biliclassic.model.Collection;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class FavoriteApi {

    private static final String TAG = "FavoriteApi";

    /**
     * 构建带 Cookie 的请求头
     */
    private static ArrayList buildHeaders() {
        ArrayList headers = new ArrayList();
        String cookies = SharedPreferencesUtil.getString("cookies", "");

        headers.add("User-Agent");
        headers.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.add("Referer");
        headers.add("https://space.bilibili.com/");
        headers.add("Origin");
        headers.add("https://space.bilibili.com");
        headers.add("Accept");
        headers.add("application/json, text/javascript, */*; q=0.01");
        headers.add("Accept-Language");
        headers.add("zh-CN,zh;q=0.9,en;q=0.8");

        if (cookies != null && cookies.length() > 0) {
            headers.add("Cookie");
            headers.add(cookies);
            Log.e(TAG, "Cookie已添加，长度: " + cookies.length());
        } else {
            Log.e(TAG, "警告：Cookie为空");
        }

        return headers;
    }

    /**
     * 快速获取收藏夹列表（不含封面，仅新接口，速度最快）
     */
    public static ArrayList getFavoriteFoldersFast(long mid) throws IOException, JSONException {
        ArrayList headers = buildHeaders();

        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid + "&type=0";
        String response = NetWorkUtil.get(url, headers);

        if (response == null || response.length() == 0) {
            return new ArrayList();
        }

        JSONObject result = new JSONObject(response);
        int code = result.optInt("code", -1);
        if (code != 0) {
            Log.e(TAG, "新接口返回错误: " + code);
            return new ArrayList();
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            return new ArrayList();
        }

        ArrayList folderList = new ArrayList();
        JSONArray list = data.optJSONArray("list");

        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject folder = list.getJSONObject(i);
                FavoriteFolder favoriteFolder = new FavoriteFolder();

                long fid = folder.optLong("fid", 0);
                favoriteFolder.id = fid;
                favoriteFolder.fid = fid;
                favoriteFolder.name = folder.optString("title", "未命名收藏夹");
                favoriteFolder.videoCount = folder.optInt("media_count", 0);
                favoriteFolder.maxCount = 50000;

                int attr = folder.optInt("attr", 0);
                favoriteFolder.isPrivate = (attr & 2) != 0;
                favoriteFolder.cover = "";

                folderList.add(favoriteFolder);
            }
        }

        Log.e(TAG, "快速获取到 " + folderList.size() + " 个收藏夹");
        return folderList;
    }

    /**
     * 获取收藏夹封面映射
     */
    public static HashMap getCoverMap(long mid) throws IOException, JSONException {
        HashMap coverMap = new HashMap();
        ArrayList headers = buildHeaders();

        String oldUrl = "https://space.bilibili.com/ajax/fav/getBoxList?mid=" + mid;
        String oldResponse = NetWorkUtil.get(oldUrl, headers);

        if (oldResponse != null && oldResponse.length() > 0) {
            try {
                JSONObject oldResult = new JSONObject(oldResponse);
                if (oldResult.optBoolean("status", false)) {
                    JSONObject oldData = oldResult.optJSONObject("data");
                    if (oldData != null) {
                        JSONArray oldList = oldData.optJSONArray("list");
                        if (oldList != null) {
                            for (int i = 0; i < oldList.length(); i++) {
                                JSONObject folder = oldList.getJSONObject(i);
                                long fid = folder.optLong("fav_box", 0);
                                String cover = "";
                                JSONArray videos = folder.optJSONArray("videos");
                                if (videos != null && videos.length() > 0) {
                                    JSONObject firstVideo = videos.optJSONObject(0);
                                    if (firstVideo != null) {
                                        cover = firstVideo.optString("pic", "");
                                    }
                                }
                                coverMap.put(new Long(fid), cover);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取旧接口封面失败: " + e.getMessage());
            }
        }

        String newUrl = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid + "&type=0";
        String newResponse = NetWorkUtil.get(newUrl, headers);

        if (newResponse != null && newResponse.length() > 0) {
            try {
                JSONObject newResult = new JSONObject(newResponse);
                if (newResult.optInt("code", -1) == 0) {
                    JSONObject newData = newResult.optJSONObject("data");
                    if (newData != null) {
                        JSONArray newList = newData.optJSONArray("list");
                        if (newList != null) {
                            for (int i = 0; i < newList.length(); i++) {
                                JSONObject folder = newList.getJSONObject(i);
                                long fid = folder.optLong("fid", 0);
                                int attr = folder.optInt("attr", 0);
                                boolean isPrivate = (attr & 2) != 0;

                                if (isPrivate && !coverMap.containsKey(new Long(fid))) {
                                    String cover = getFirstVideoCover(mid, fid, headers);
                                    if (cover != null && cover.length() > 0) {
                                        coverMap.put(new Long(fid), cover);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取私密收藏夹封面失败: " + e.getMessage());
            }
        }

        return coverMap;
    }

    /**
     * 获取收藏夹内第一个视频作为封面
     */
    private static String getFirstVideoCover(long mid, long fid, ArrayList headers) {
        try {
            String url = "https://api.bilibili.com/x/space/fav/arc?vmid=" + mid
                    + "&ps=1&fid=" + fid + "&tid=0&keyword=&pn=1&order=fav_time";
            String response = NetWorkUtil.get(url, headers);

            if (response == null || response.length() == 0) {
                return "";
            }

            JSONObject result = new JSONObject(response);
            int code = result.optInt("code", -1);
            if (code != 0) {
                return "";
            }

            JSONObject data = result.optJSONObject("data");
            if (data == null) {
                return "";
            }

            JSONArray archives = data.optJSONArray("archives");
            if (archives != null && archives.length() > 0) {
                JSONObject firstVideo = archives.getJSONObject(0);
                return firstVideo.optString("pic", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取第一个视频封面失败: " + e.getMessage());
        }
        return "";
    }

    /**
     * 获取收藏夹内的视频列表
     */
    public static int getFolderVideos(long mid, long fid, int page, ArrayList videoList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/fav/arc?vmid=" + mid
                + "&ps=30&fid=" + fid + "&tid=0&keyword=&pn=" + page + "&order=fav_time";
        Log.e(TAG, "获取收藏夹视频: " + url);

        ArrayList headers = buildHeaders();

        String rawResponse = NetWorkUtil.get(url, headers);
        if (rawResponse == null || rawResponse.length() == 0) {
            Log.e(TAG, "getFolderVideos: 响应为空");
            return -1;
        }

        JSONObject result = new JSONObject(rawResponse);
        int code = result.optInt("code", -1);
        Log.e(TAG, "getFolderVideos 响应码: " + code);

        if (code != 0) {
            Log.e(TAG, "获取收藏夹视频失败: " + code + " - " + result.optString("message", ""));
            return -1;
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            Log.e(TAG, "data 为 null");
            return -1;
        }

        if (data.has("archives") && !data.isNull("archives")) {
            JSONArray archives = data.optJSONArray("archives");
            if (archives == null || archives.length() == 0) {
                return 1;
            }

            for (int i = 0; i < archives.length(); i++) {
                JSONObject video = archives.getJSONObject(i);
                String title = video.optString("title", "无标题");
                String cover = video.optString("pic", "");
                long aid = video.optLong("aid", 0);

                String bvid = "";
                if (video.has("bvid")) {
                    bvid = video.optString("bvid", "");
                }

                String upName = "";
                if (video.has("owner") && !video.isNull("owner")) {
                    JSONObject owner = video.getJSONObject("owner");
                    upName = owner.optString("name", "未知UP主");
                }

                String view = "0观看";
                if (video.has("stat") && !video.isNull("stat")) {
                    JSONObject stat = video.getJSONObject("stat");
                    view = StringUtil.toWan(stat.optLong("view", 0)) + "观看";
                }

                videoList.add(new VideoCard(title, upName, view, cover, aid, bvid));
            }
            return 0;
        } else {
            return -1;
        }
    }

    /**
     * 获取收藏的合集
     */
    public static int getFavoritedCollections(long mid, int page, List collectionList) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/collected/list" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("platform", "web")
                .put("up_mid", mid)
                .put("pn", page)
                .put("ps", 10);
        Log.e(TAG, "获取收藏合集: " + url);

        ArrayList headers = buildHeaders();
        String rawResponse = NetWorkUtil.get(url, headers);

        if (rawResponse == null || rawResponse.length() == 0) {
            Log.e(TAG, "getFavoritedCollections: 响应为空");
            return -1;
        }

        JSONObject result = new JSONObject(rawResponse);
        int code = result.optInt("code", -1);
        if (code != 0) {
            Log.e(TAG, "获取收藏合集失败: " + code);
            return code;
        }

        JSONObject data = result.optJSONObject("data");
        if (data != null) {
            JSONArray list = data.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    Collection collection = new Collection();
                    collection.id = item.optInt("id", -1);
                    collection.mid = item.optLong("mid", -1);
                    collection.title = item.optString("title");
                    collection.cover = item.optString("cover");
                    collection.intro = item.optString("intro");
                    collection.view = StringUtil.toWan(item.optInt("view_count", -1));
                    collectionList.add(collection);
                }
                return 0;
            }
        }
        return -1;
    }

    /**
     * 获取视频的收藏状态
     */
    public static void getFavoriteState(long aid, ArrayList folderList, ArrayList fidList, ArrayList stateList) throws IOException, JSONException {
        long mid = SharedPreferencesUtil.getLong("mid", 0);
        if (mid == 0) {
            Log.e(TAG, "未登录，无法获取收藏状态");
            return;
        }

        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&jsonp=jsonp&rid=" + aid + "&up_mid=" + mid;
        Log.e(TAG, "获取收藏状态: " + url);

        ArrayList headers = buildHeaders();
        String rawResponse = NetWorkUtil.get(url, headers);

        if (rawResponse == null || rawResponse.length() == 0) {
            Log.e(TAG, "getFavoriteState: 响应为空");
            return;
        }

        JSONObject result = new JSONObject(rawResponse);
        int code = result.optInt("code", -1);
        if (code != 0) {
            Log.e(TAG, "获取收藏状态失败: " + code + " - " + result.optString("message", ""));
            return;
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            Log.e(TAG, "data 为 null");
            return;
        }

        if (data.has("list") && !data.isNull("list")) {
            JSONArray list = data.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject folder = list.getJSONObject(i);
                    folderList.add(folder.optString("title", "未命名"));
                    fidList.add(new Long(folder.optLong("fid", 0)));
                    stateList.add(new Boolean(folder.optInt("fav_state", 0) == 1));
                }
            }
        }
    }

    /**
     * 添加收藏
     */
    public static int addFavorite(long aid, long fid) throws IOException, JSONException {
        String strMid = String.valueOf(SharedPreferencesUtil.getLong("mid", 0));
        if (strMid == null || strMid.length() < 2) {
            Log.e(TAG, "mid 无效，无法添加收藏");
            return -1;
        }

        String addFid = fid + strMid.substring(strMid.length() - 2);
        String url = "https://api.bilibili.com/medialist/gateway/coll/resource/deal";

        String csrf = SharedPreferencesUtil.getString("csrf", "");
        if (csrf == null || csrf.length() == 0) {
            String cookies = SharedPreferencesUtil.getString("cookies", "");
            csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
        }

        String data = "rid=" + aid + "&type=2&add_media_ids=" + addFid + "&del_media_ids=&csrf=" + csrf;

        ArrayList headers = new ArrayList();
        String cookies = SharedPreferencesUtil.getString("cookies", "");

        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        headers.add("Referer");
        headers.add("https://www.bilibili.com/");
        headers.add("Content-Type");
        headers.add("application/x-www-form-urlencoded");

        if (cookies != null && cookies.length() > 0) {
            headers.add("Cookie");
            headers.add(cookies);
        }

        String response = NetWorkUtil.post(url, data, headers);
        Log.d(TAG, "添加收藏响应: " + response);

        JSONObject result = new JSONObject(response);
        return result.optInt("code", -1);
    }

    /**
     * 删除收藏
     */
    public static int deleteFavorite(long aid, long fid) throws IOException, JSONException {
        String strMid = String.valueOf(SharedPreferencesUtil.getLong("mid", 0));
        if (strMid == null || strMid.length() < 2) {
            Log.e(TAG, "mid 无效，无法删除收藏");
            return -1;
        }

        String delFid = fid + strMid.substring(strMid.length() - 2);
        String url = "https://api.bilibili.com/medialist/gateway/coll/resource/batch/del";

        String csrf = SharedPreferencesUtil.getString("csrf", "");
        if (csrf == null || csrf.length() == 0) {
            String cookies = SharedPreferencesUtil.getString("cookies", "");
            csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
        }

        String data = "resources=" + aid + ":2&media_id=" + delFid + "&csrf=" + csrf;

        ArrayList headers = new ArrayList();
        String cookies = SharedPreferencesUtil.getString("cookies", "");

        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        headers.add("Referer");
        headers.add("https://www.bilibili.com/");
        headers.add("Content-Type");
        headers.add("application/x-www-form-urlencoded");

        if (cookies != null && cookies.length() > 0) {
            headers.add("Cookie");
            headers.add(cookies);
        }

        String response = NetWorkUtil.post(url, data, headers);
        Log.d(TAG, "删除收藏响应: " + response);

        JSONObject result = new JSONObject(response);
        return result.optInt("code", -1);
    }
}