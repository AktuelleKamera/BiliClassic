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
 * 修改时间：2026年6月20日
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

import tv.biliclassic.model.UserInfo;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class UserInfoApi {

    private static final String TAG = "UserInfoApi";

    private static ArrayList<String> buildHeaders() {
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        headers.add("Referer");
        headers.add("https://www.bilibili.com/");
        headers.add("Origin");
        headers.add("https://www.bilibili.com");

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies != null && cookies.length() > 0) {
            headers.add("Cookie");
            headers.add(cookies);
        }
        return headers;
    }

    public static UserInfo getUserInfo(long mid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/card?mid=" + mid;
        ArrayList<String> headers = buildHeaders();
        JSONObject all = NetWorkUtil.getJson(url, headers);

        if (all == null) {
            return null;
        }

        int code = all.optInt("code", -1);
        if (code != 0) {
            Log.e(TAG, "getUserInfo 失败: code=" + code);
            return null;
        }

        if (!all.has("data") || all.isNull("data")) {
            return null;
        }

        try {
            JSONObject data = all.getJSONObject("data");
            boolean followed = data.optBoolean("following", false);
            int fans = data.optInt("follower", 0);

            JSONObject card = data.getJSONObject("card");
            String name = card.getString("name");
            String avatar = card.getString("face");
            String sign = card.getString("sign");
            JSONObject levelInfo = card.getJSONObject("level_info");
            int level = levelInfo.getInt("current_level");

            int attention = card.optInt("attention", 0);
            if (attention == 0) {
                attention = card.optInt("friend", 0);
            }

            JSONObject officialData = card.getJSONObject("Official");
            int official = officialData.getInt("role");
            String officialDesc = officialData.getString("title");
            int isSeniorMember = card.optInt("is_senior_member", 0);

            String notice = "";
            try {
                JSONObject noticeAll = NetWorkUtil.getJson("https://api.bilibili.com/x/space/notice?mid=" + mid, headers);
                if (noticeAll != null && noticeAll.has("data") && !noticeAll.isNull("data")) {
                    notice = noticeAll.getString("data");
                }
            } catch (Exception e) {
                notice = "";
            }

            String sysNotice = "";
            try {
                JSONObject spaceInfo = getUserSpaceInfo(mid);
                if (spaceInfo != null && !spaceInfo.isNull("sys_notice")) {
                    sysNotice = spaceInfo.getJSONObject("sys_notice").optString("content", "");
                    if (sysNotice != null) {
                        sysNotice = sysNotice.replace("请点此查看纪念账号相关说明", "");
                    }
                }
            } catch (Exception e) {
                sysNotice = "";
            }

            JSONObject vip = card.getJSONObject("vip");
            if (vip.getInt("status") == 1) {
                UserInfo result = new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, vip.getInt("role"), sysNotice, isSeniorMember);
                result.vip_nickname_color = vip.optString("nickname_color", "");
                return result;
            } else {
                return new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, sysNotice, isSeniorMember);
            }

        } catch (Exception e) {
            Log.e(TAG, "解析用户信息异常: " + e.getMessage(), e);
            return null;
        }
    }

    public static JSONObject getUserSpaceInfo(long mid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/space/wbi/acc/info?mid=" + mid;
        url = ConfInfoApi.signWBI(url);
        ArrayList<String> headers = buildHeaders();
        JSONObject all = NetWorkUtil.getJson(url, headers);
        if (all != null && all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }

    public static UserInfo getCurrentUserInfo() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/myinfo";
        ArrayList<String> headers = buildHeaders();
        JSONObject all = NetWorkUtil.getJson(url, headers);
        if (all == null) {
            return new UserInfo(0, "加载失败", "", "", 0, 0, 0, false, "", 0, "", 0);
        }

        int code = all.optInt("code", -1);
        if (code == 0 && all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            long mid = data.getLong("mid");
            String name = data.getString("name");
            String avatar = data.getString("face");
            String sign = data.getString("sign");
            int fans = data.getInt("follower");
            int level = data.getInt("level");
            JSONObject officialData = data.getJSONObject("official");
            int official = officialData.getInt("role");
            String officialDesc = officialData.getString("desc");
            JSONObject levelExp = data.getJSONObject("level_exp");
            long currentExp = levelExp.getLong("current_exp");
            long nextExp = levelExp.getLong("next_exp");
            int isSeniorMember = data.optInt("is_senior_member", 0);
            return new UserInfo(mid, name, avatar, sign, fans, 0, level, false, "", official, officialDesc, currentExp, nextExp, isSeniorMember);
        } else {
            return new UserInfo(0, "加载失败", "", "", 0, 0, 0, false, "", 0, "", 0);
        }
    }

    public static int getCurrentUserCoin() {
        try {
            String url = "https://account.bilibili.com/site/getCoin";
            ArrayList<String> headers = buildHeaders();
            JSONObject all = NetWorkUtil.getJson(url, headers);
            if (all != null && all.has("data") && !all.isNull("data")) {
                JSONObject data = all.getJSONObject("data");
                if (data.has("money")) {
                    return data.getInt("money");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取硬币失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 获取用户发布的视频列表（已修复，移除 DmImgParamUtil）
     */
    public static int getUserVideos(long mid, int page, String searchKeyword, List<VideoCard> videoList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/wbi/arc/search?";
        url += "keyword=" + searchKeyword
                + "&mid=" + mid
                + "&order_avoided=true&order=pubdate&pn=" + page
                + "&ps=40&tid=0&web_location=333.999";

        // 直接使用 ConfInfoApi.signWBI，移除 DmImgParamUtil
        url = ConfInfoApi.signWBI(url);
        Log.e(TAG, "getUserVideos URL=" + url);

        ArrayList<String> headers = buildHeaders();
        JSONObject all = NetWorkUtil.getJson(url, headers);

        if (all != null) {
            int code = all.optInt("code", -1);
            Log.e(TAG, "getUserVideos code=" + code);

            if (code == 0 && all.has("data") && !all.isNull("data")) {
                JSONObject data = all.getJSONObject("data");
                JSONObject list = data.getJSONObject("list");
                if (list.has("vlist") && !list.isNull("vlist")) {
                    JSONArray vlist = list.getJSONArray("vlist");
                    if (vlist.length() == 0) {
                        Log.e(TAG, "没有更多视频");
                        return 1;
                    }
                    for (int i = 0; i < vlist.length(); i++) {
                        JSONObject card = vlist.getJSONObject(i);
                        String cover = card.getString("pic");
                        long play = card.getLong("play");
                        String playStr = StringUtil.toWan(play) + "观看";
                        long aid = card.getLong("aid");
                        String bvid = card.getString("bvid");
                        String upName = card.getString("author");
                        String title = card.getString("title");
                        videoList.add(new VideoCard(title, upName, playStr, cover, aid, bvid, "video"));
                    }
                    Log.e(TAG, "获取到 " + vlist.length() + " 个视频");
                    return 0;
                } else {
                    Log.e(TAG, "vlist 不存在");
                    return -1;
                }
            } else {
                String message = all != null ? all.optString("message", "") : "null";
                Log.e(TAG, "getUserVideos 失败: " + message);
                return -1;
            }
        } else {
            Log.e(TAG, "getUserVideos 响应为 null");
            return -1;
        }
    }

    public static int followUser(long mid, boolean isFollow) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/modify?";
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString("cookies", ""));
        String arg = "fid=" + mid + "&csrf=" + csrf;
        if (isFollow) {
            arg += "&act=1";
        } else {
            arg += "&act=2";
        }
        Log.e(TAG, "followUser: mid=" + mid + ", isFollow=" + isFollow + ", csrf=" + csrf);
        ArrayList<String> headers = buildHeaders();
        JSONObject all = new JSONObject(NetWorkUtil.post(url, arg, headers));
        int code = all.optInt("code", -1);
        Log.e(TAG, "followUser code=" + code);
        return code;
    }

    public static void exitLogin() {
        try {
            String url = "https://passport.bilibili.com/login/exit/v2";
            ArrayList<String> headers = buildHeaders();
            NetWorkUtil.get(url, headers);
            Log.e(TAG, "退出登录成功");
        } catch (Exception e) {
            Log.e(TAG, "退出登录失败: " + e.getMessage());
        }
    }
}