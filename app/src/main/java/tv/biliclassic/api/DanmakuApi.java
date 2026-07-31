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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 弹幕API
 * 发送、点赞、撤回弹幕
 */
public class DanmakuApi {

    // ========== 弹幕颜色常量 ==========
    public static final int COLOR_WHITE = 0xFFFFFF;   // 白色
    public static final int COLOR_RED = 0xFF0000;     // 红色
    public static final int COLOR_BLUE = 0x0000FF;    // 蓝色
    public static final int COLOR_GREEN = 0x00FF00;   // 绿色
    public static final int COLOR_YELLOW = 0xFFFF00;  // 黄色
    public static final int COLOR_ORANGE = 0xFFA500;  // 橙色
    public static final int COLOR_PINK = 0xFFC0CB;    // 粉色
    public static final int COLOR_PURPLE = 0x800080;  // 紫色

    // ========== 弹幕模式常量 ==========
    public static final int MODE_SCROLL = 1;      // 滚动弹幕
    public static final int MODE_TOP = 5;         // 顶部弹幕
    public static final int MODE_BOTTOM = 4;      // 底部弹幕

    /**
     * 通过BVID发送视频弹幕
     */
    public static int sendVideoDanmakuByBvid(long cid, String msg, String bvid, long progress, int color, int mode) {
        try {
            String url = "https://api.bilibili.com/x/v2/dm/post";
            String cookie = SharedPreferencesUtil.getString("cookies", "");
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookie);

            if (csrf == null || csrf.length() == 0) {
                android.util.Log.e("DanmakuApi", "csrf 为空，请重新登录");
                return -101;
            }

            String rnd = String.valueOf(System.currentTimeMillis() / 1000);

            String arg = "type=1&oid=" + cid
                    + "&msg=" + URLEncoder.encode(msg, "UTF-8")
                    + "&bvid=" + bvid
                    + "&progress=" + progress
                    + "&color=" + color
                    + "&mode=" + mode
                    + "&rnd=" + rnd
                    + "&csrf=" + csrf;

            NetWorkUtil.setCookieString(cookie);
            String response = NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders, "application/x-www-form-urlencoded");
            JSONObject result = new JSONObject(response);
            android.util.Log.e("DanmakuApi", "发送弹幕响应: " + response);
            return result.optInt("code", -1);
        } catch (Exception e) {
            android.util.Log.e("DanmakuApi", "发送弹幕失败: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 通过AID发送视频弹幕
     */
    public static int sendVideoDanmakuByAid(long cid, String msg, long aid, long progress, int color, int mode) {
        try {
            String url = "https://api.bilibili.com/x/v2/dm/post";
            String cookie = SharedPreferencesUtil.getString("cookies", "");
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookie);

            android.util.Log.e("DanmakuApi", "========== 发送弹幕开始 ==========");
            android.util.Log.e("DanmakuApi", "cookie: " + cookie);
            android.util.Log.e("DanmakuApi", "csrf: " + csrf);

            if (csrf == null || csrf.length() == 0) {
                android.util.Log.e("DanmakuApi", "csrf 为空，请重新登录");
                return -101;
            }

            long rnd = System.currentTimeMillis() / 1000;

            String arg = "type=1&oid=" + cid
                    + "&msg=" + URLEncoder.encode(msg, "UTF-8")
                    + "&aid=" + aid
                    + "&progress=" + progress
                    + "&color=" + color
                    + "&mode=" + mode
                    + "&rnd=" + rnd
                    + "&csrf=" + csrf;

            android.util.Log.e("DanmakuApi", "请求参数: " + arg);
            android.util.Log.e("DanmakuApi", "参数中的 progress: " + progress);

            NetWorkUtil.setCookieString(cookie);
            String resultStr = NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders, "application/x-www-form-urlencoded");

            android.util.Log.e("DanmakuApi", "发送弹幕响应: " + resultStr);

            if (resultStr != null && resultStr.length() > 0) {
                JSONObject result = new JSONObject(resultStr);
                int code = result.optInt("code", -1);
                android.util.Log.e("DanmakuApi", "返回码: " + code);
                android.util.Log.e("DanmakuApi", "========== 发送弹幕结束 ==========");
                return code;
            }
            return -1;
        } catch (Exception e) {
            android.util.Log.e("DanmakuApi", "发送弹幕异常: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * 点赞弹幕
     * @param dmid 弹幕ID
     * @param cid 视频CID
     * @param op 操作：1=点赞，0=取消点赞
     */
    public static int likeDanmaku(long dmid, long cid, int op) {
        try {
            String url = "https://api.bilibili.com/x/v2/dm/thumbup/add";
            String cookie = SharedPreferencesUtil.getString("cookies", "");
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookie);

            if (csrf == null || csrf.length() == 0) {
                return -101;
            }

            String arg = "oid=" + cid
                    + "&dmid=" + dmid
                    + "&op=" + op
                    + "&platform=web_player"
                    + "&csrf=" + csrf;

            NetWorkUtil.setCookieString(cookie);
            String response = NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders, "application/x-www-form-urlencoded");
            JSONObject result = new JSONObject(response);
            android.util.Log.e("DanmakuApi", "点赞弹幕响应: " + response);
            return result.optInt("code", -1);
        } catch (Exception e) {
            android.util.Log.e("DanmakuApi", "点赞弹幕失败: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 撤回弹幕
     * @param dmid 弹幕ID
     * @param cid 视频CID（文档中就是cid，不是oid）
     */
    public static int recallDanmaku(long dmid, long cid) {
        try {
            String url = "https://api.bilibili.com/x/dm/recall";
            String cookie = SharedPreferencesUtil.getString("cookies", "");
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookie);

            if (csrf == null || csrf.length() == 0) {
                return -101;
            }

            String arg = "cid=" + cid
                    + "&dmid=" + dmid
                    + "&csrf=" + csrf;

            NetWorkUtil.setCookieString(cookie);
            String response = NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders, "application/x-www-form-urlencoded");
            JSONObject result = new JSONObject(response);
            android.util.Log.e("DanmakuApi", "撤回弹幕响应: " + response);
            return result.optInt("code", -1);
        } catch (Exception e) {
            android.util.Log.e("DanmakuApi", "撤回弹幕失败: " + e.getMessage());
            return -1;
        }
    }
}