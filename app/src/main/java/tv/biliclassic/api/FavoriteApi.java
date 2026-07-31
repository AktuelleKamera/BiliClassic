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
 * 修改时间：2026年6月30日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import tv.biliclassic.model.Collection;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class FavoriteApi {

    private static final String TAG = "FavoriteApi";
    private static final int TYPE_VIDEO = 2;

    // 构建带 Cookie 的请求头
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
            Log.d(TAG, "Cookie已添加，长度: " + cookies.length());
        } else {
            Log.w(TAG, "警告：Cookie为空");
        }

        return headers;
    }

    // 通过 bvid 获取 aid
    public static long getAidByBvid(String bvid) {
        if (bvid == null || bvid.length() == 0) {
            return 0L;
        }
        try {
            String url = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
            String response = NetWorkUtil.get(url);
            if (response == null || response.length() == 0) {
                return 0L;
            }
            JSONObject result = new JSONObject(response);
            if (result.optInt("code") == 0) {
                JSONObject data = result.optJSONObject("data");
                if (data != null) {
                    return data.optLong("aid", 0L);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "获取 aid 失败: " + e.getMessage());
        }
        return 0L;
    }

    // 快速获取收藏夹列表（不含封面）
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
            Log.w(TAG, "新接口返回错误: " + code);
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

        Log.d(TAG, "快速获取到 " + folderList.size() + " 个收藏夹");
        return folderList;
    }

    // 获取收藏夹封面映射
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
                                coverMap.put(Long.valueOf(fid), cover);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "获取旧接口封面失败: " + e.getMessage());
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

                                if (isPrivate && !coverMap.containsKey(Long.valueOf(fid))) {
                                    String cover = getFirstVideoCover(mid, fid, headers);
                                    if (cover != null && cover.length() > 0) {
                                        coverMap.put(Long.valueOf(fid), cover);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "获取私密收藏夹封面失败: " + e.getMessage());
            }
        }

        return coverMap;
    }

    // 获取收藏夹内第一个视频作为封面
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
            Log.w(TAG, "获取第一个视频封面失败: " + e.getMessage());
        }
        return "";
    }

    // 获取收藏夹内的视频列表
    // 返回 0=成功, 1=无更多数据, -1=失败
    public static int getFolderVideos(long mid, long fid, int page, ArrayList videoList) throws IOException, JSONException {
        // 如果是第一页，清空列表，防止重复
        if (page == 1) {
            videoList.clear();
        }

        String url = "https://api.bilibili.com/x/space/fav/arc?vmid=" + mid
                + "&ps=30&fid=" + fid + "&tid=0&keyword=&pn=" + page + "&order=fav_time";
        Log.d(TAG, "获取收藏夹视频: " + url);

        ArrayList headers = buildHeaders();

        String rawResponse = NetWorkUtil.get(url, headers);
        if (rawResponse == null || rawResponse.length() == 0) {
            Log.w(TAG, "getFolderVideos: 响应为空");
            return -1;
        }

        JSONObject result = new JSONObject(rawResponse);
        int code = result.optInt("code", -1);
        Log.d(TAG, "getFolderVideos 响应码: " + code);

        if (code != 0) {
            Log.w(TAG, "获取收藏夹视频失败: " + code + " - " + result.optString("message", ""));
            return -1;
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            Log.w(TAG, "data 为 null");
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
                    long viewCount = stat.optLong("view", 0);
                    String wanStr = StringUtil.toWan(viewCount);
                    view = (wanStr != null ? wanStr : "0") + "观看";
                }

                videoList.add(new VideoCard(title, upName, view, cover, aid, bvid));
            }
            return 0;
        } else {
            return -1;
        }
    }

    // 获取收藏的合集
    public static int getFavoritedCollections(long mid, int page, List collectionList) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/collected/list" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("platform", "web")
                .put("up_mid", mid)
                .put("pn", page)
                .put("ps", 10);
        Log.d(TAG, "获取收藏合集: " + url);

        ArrayList headers = buildHeaders();
        String rawResponse = NetWorkUtil.get(url, headers);

        if (rawResponse == null || rawResponse.length() == 0) {
            Log.w(TAG, "getFavoritedCollections: 响应为空");
            return -1;
        }

        JSONObject result = new JSONObject(rawResponse);
        int code = result.optInt("code", -1);
        if (code != 0) {
            Log.w(TAG, "获取收藏合集失败: " + code);
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

    // 获取视频的收藏状态
    public static void getFavoriteState(long aid, ArrayList folderList, ArrayList fidList, ArrayList stateList) throws IOException, JSONException {
        long mid = SharedPreferencesUtil.getLong("mid", 0);
        if (mid == 0) {
            Log.w(TAG, "未登录，无法获取收藏状态");
            return;
        }

        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&jsonp=jsonp&rid=" + aid + "&up_mid=" + mid;
        Log.d(TAG, "获取收藏状态: " + url);

        ArrayList headers = buildHeaders();
        String rawResponse = NetWorkUtil.get(url, headers);

        if (rawResponse == null || rawResponse.length() == 0) {
            Log.w(TAG, "getFavoriteState: 响应为空");
            return;
        }

        JSONObject result = new JSONObject(rawResponse);
        int code = result.optInt("code", -1);
        if (code != 0) {
            Log.w(TAG, "获取收藏状态失败: " + code + " - " + result.optString("message", ""));
            return;
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            Log.w(TAG, "data 为 null");
            return;
        }

        if (data.has("list") && !data.isNull("list")) {
            JSONArray list = data.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject folder = list.getJSONObject(i);
                    folderList.add(folder.optString("title", "未命名"));
                    fidList.add(Long.valueOf(folder.optLong("fid", 0)));
                    stateList.add(folder.optInt("fav_state", 0) == 1);
                }
            }
        }
    }

    // 获取 CSRF token（使用正则提取）
    private static String getCsrf() {
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null || cookies.length() == 0) {
            Log.w(TAG, "Cookie 为空");
            return null;
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("bili_jct=([a-f0-9]+)");
        java.util.regex.Matcher m = p.matcher(cookies);
        if (m.find()) {
            String csrf = m.group(1);
            Log.d(TAG, "提取 csrf: " + csrf);
            return csrf;
        }

        Log.w(TAG, "未找到 bili_jct");
        return null;
    }

    // 获取 mid 后两位
    private static String getMidSuffix() {
        String strMid = String.valueOf(SharedPreferencesUtil.getLong("mid", 0));
        if (strMid == null || strMid.length() < 2) {
            return "";
        }
        return strMid.substring(strMid.length() - 2);
    }

    // 添加收藏（支持 aid 或 bvid）
    public static int addFavorite(long aid, String bvid, long fid) throws IOException, JSONException {
        long finalAid = aid;
        if (finalAid == 0 && bvid != null && bvid.length() > 0) {
            finalAid = getAidByBvid(bvid);
            Log.d(TAG, "从 bvid 获取 aid: " + finalAid);
        }
        if (finalAid == 0) {
            Log.w(TAG, "aid 无效，无法添加收藏");
            return -1;
        }
        return addFavoriteByAid(finalAid, fid);
    }

    // 通过 aid 添加收藏
    public static int addFavoriteByAid(long aid, long fid) throws IOException, JSONException {
        String midSuffix = getMidSuffix();
        if (midSuffix == null || midSuffix.length() == 0) {
            Log.w(TAG, "mid 无效");
            return -1;
        }

        String addFid = fid + midSuffix;
        String url = "https://api.bilibili.com/x/v3/fav/resource/deal";
        String csrf = getCsrf();
        String cookies = SharedPreferencesUtil.getString("cookies", "");

        if (csrf == null || csrf.length() == 0) {
            Log.w(TAG, "csrf 无效");
            return -1;
        }

        String data = "rid=" + aid + "&type=" + TYPE_VIDEO + "&add_media_ids=" + addFid + "&del_media_ids=&csrf=" + csrf;

        Log.d(TAG, "=== 收藏调试 ===");
        Log.d(TAG, "aid: " + aid);
        Log.d(TAG, "fid: " + fid + ", midSuffix: " + midSuffix + ", addFid: " + addFid);
        Log.d(TAG, "csrf: " + csrf);
        Log.d(TAG, "请求数据: " + data);

        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPad; CPU OS 18_5 like Mac OS X) AppleWebKit/605.1.15");
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("Cookie", cookies);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Origin", "https://www.bilibili.com");

        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        Log.d(TAG, "添加收藏响应: " + sb.toString());

        JSONObject result = new JSONObject(sb.toString());
        return result.optInt("code", -1);
    }

    // 删除收藏（支持 aid 或 bvid）
    public static int deleteFavorite(long aid, String bvid, long fid) throws IOException, JSONException {
        long finalAid = aid;
        if (finalAid == 0 && bvid != null && bvid.length() > 0) {
            finalAid = getAidByBvid(bvid);
            Log.d(TAG, "从 bvid 获取 aid: " + finalAid);
        }
        if (finalAid == 0) {
            Log.w(TAG, "aid 无效，无法删除收藏");
            return -1;
        }
        return deleteFavoriteByAid(finalAid, fid);
    }

    // 通过 aid 删除收藏
    public static int deleteFavoriteByAid(long aid, long fid) throws IOException, JSONException {
        String midSuffix = getMidSuffix();
        if (midSuffix == null || midSuffix.length() == 0) {
            Log.w(TAG, "mid 无效，无法删除收藏");
            return -1;
        }

        String delFid = fid + midSuffix;
        String url = "https://api.bilibili.com/x/v3/fav/resource/batch-del";
        String csrf = getCsrf();

        Log.d(TAG, "=== 删除收藏调试 ===");
        Log.d(TAG, "aid: " + aid + ", delFid: " + delFid);
        Log.d(TAG, "csrf: " + csrf);

        if (csrf == null || csrf.length() == 0) {
            Log.w(TAG, "csrf 无效，无法删除收藏");
            return -1;
        }

        String data = "resources=" + aid + ":" + TYPE_VIDEO + "&media_id=" + delFid + "&csrf=" + csrf;

        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);

        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPad; CPU OS 18_5 like Mac OS X) AppleWebKit/605.1.15");
        conn.setRequestProperty("Referer", "https://space.bilibili.com/");
        conn.setRequestProperty("Cookie", SharedPreferencesUtil.getString("cookies", ""));
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Origin", "https://space.bilibili.com");

        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        Log.d(TAG, "删除收藏响应: " + sb.toString());

        JSONObject result = new JSONObject(sb.toString());
        return result.optInt("code", -1);
    }
}