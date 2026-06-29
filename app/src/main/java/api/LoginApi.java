package tv.biliclassic.api;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.QRCodeUtil;

/*
 * 本登录API基于以下项目修改，致谢前辈：
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
 * 修改时间：2026年6月15日
 *
 * 安卓2也要看B站！
 */
public class LoginApi {

    private static String oauthKey = "";

    /**
     * 获取登录二维码
     * @param context 上下文（用于 QRCodeUtil 加载 assets）
     * @return 二维码 Bitmap
     */
    public static Bitmap getLoginQR(Context context) throws JSONException, IOException {
        String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F";

        String response = NetWorkUtil.get(url);
        if (response == null || response.length() == 0) {
            throw new IOException("获取二维码失败：网络返回为空");
        }

        JSONObject json = new JSONObject(response);
        JSONObject data = json.getJSONObject("data");
        oauthKey = data.getString("qrcode_key");
        String qrUrl = data.getString("url");
        // 传入 Context 参数
        return QRCodeUtil.createQRCodeBitmap(context, qrUrl, 320, 320);
    }

    /**
     * 获取登录状态
     * @return 响应字符串
     */
    public static String getLoginState() throws IOException {
        if (oauthKey == null || oauthKey.length() == 0) {
            throw new IOException("oauthKey 为空，请先调用 getLoginQR()");
        }
        String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?source=main-fe-header&qrcode_key=" + oauthKey;
        return NetWorkUtil.get(url);
    }

    /**
     * 解析登录状态响应
     * @param response 轮询返回的字符串
     * @return 0: 未扫描, 1: 已扫描等待确认, 2: 登录成功, -1: 已过期, -2: 错误
     */
    public static int parseLoginState(String response) {
        if (response == null || response.length() == 0) {
            return -2;
        }
        try {
            JSONObject json = new JSONObject(response);
            int code = json.optInt("code", -1);

            if (code == 0) {
                return 2;
            } else if (code == 86101) {
                return 0;
            } else if (code == 86090) {
                return 1;
            } else if (code == 86038) {
                return -1;
            } else {
                return -2;
            }
        } catch (JSONException e) {
            return -2;
        }
    }

    /**
     * 保存登录信息
     */
    public static void saveLoginInfo(String response) {
        if (response == null || response.length() == 0) return;
        try {
            JSONObject json = new JSONObject(response);
            JSONObject data = json.getJSONObject("data");

            if (data.has("mid")) {
                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, data.getLong("mid"));
            }
            if (data.has("csrf")) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, data.getString("csrf"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 请求 SSO 登录
     */
    public static void requestSSOs() throws JSONException, IOException {
        String listUrl = "https://passport.bilibili.com/x/passport-login/web/sso/list";
        String csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");

        NetWorkUtil.FormData formData = new NetWorkUtil.FormData();
        formData.put("csrf", csrf);

        String response = NetWorkUtil.post(listUrl, formData.toString());
        if (response == null || response.length() == 0) {
            return;
        }

        JSONObject listResult = new JSONObject(response);
        if (listResult.has("data") && !listResult.isNull("data")) {
            JSONObject data = listResult.getJSONObject("data");
            if (data.has("sso") && !data.isNull("sso")) {
                org.json.JSONArray sso = data.getJSONArray("sso");
                for (int i = 0; i < sso.length(); i++) {
                    String ssoUrl = sso.getString(i);
                    if (ssoUrl != null && ssoUrl.length() > 0) {
                        try {
                            NetWorkUtil.post(ssoUrl, "");
                        } catch (IOException e) {
                            // 忽略单个 SSO 失败
                        }
                    }
                }
            }
        }
    }
}