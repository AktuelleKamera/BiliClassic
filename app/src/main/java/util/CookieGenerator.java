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
 * 安卓2也要看B站！
 */
package tv.biliclassic.util;

import android.util.Log;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class CookieGenerator {
    private static final String TAG = "CookieGenerator";
    private static final String CHARSET = "0123456789ABCDEF";
    private static final int[] PCK = {8, 4, 4, 4, 12};
    private static final String[] MP = {"1","2","3","4","5","6","7","8","9","A","B","C","D","E","F","10"};

    private static boolean isEnsuringCookies = false;

    public static void ensureCookies() {
        if (isEnsuringCookies) return;
        isEnsuringCookies = true;
        try {
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.BUVid3, "").length() == 0) {
                generateBuvids();
            }
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.BILI_TICKET, "").length() == 0) {
                generateBiliTicket();
            }
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.UUID, "").length() == 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.UUID, genUuidInfoc());
            }
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.B_LSID, "").length() == 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.B_LSID, genBlsid());
            }
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.BUVid_FP, "").length() == 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.BUVid_FP, genBuvidFp());
            }
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.B_NUT, "").length() == 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.B_NUT, String.valueOf(System.currentTimeMillis() / 1000));
            }
        } catch (Exception e) {
            Log.e(TAG, "确保 Cookie 失败", e);
        } finally {
            isEnsuringCookies = false;
        }
    }

    private static void generateBuvids() {
        try {
            String response = NetWorkUtil.get("https://api.bilibili.com/x/frontend/finger/spi");
            if (response == null || response.length() == 0) return;
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                String b3 = data.optString("b_3", "");
                String b4 = data.optString("b_4", "");
                if (b3 != null && b3.length() > 0) SharedPreferencesUtil.putString(SharedPreferencesUtil.BUVid3, b3);
                if (b4 != null && b4.length() > 0) SharedPreferencesUtil.putString(SharedPreferencesUtil.BUVid4, b4);
                android.util.Log.d("CookieGenerator", "生成 buvid3/buvid4 成功");
            }
        } catch (Exception e) {
            android.util.Log.e("CookieGenerator", "生成 buvid 失败", e);
        }
    }

    public static String getCookieString(boolean forVideoQuality) {
        StringBuilder sb = new StringBuilder();

        // 无痕模式下不携带登录 Cookie（视频清晰度除外）
        boolean incognitoMode = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.INCOGNITO_MODE, false);
        if (!incognitoMode || forVideoQuality) {
            String loggedCookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            if (loggedCookie != null && loggedCookie.length() > 0) {
                sb.append(loggedCookie);
            }
        }

        appendCookie(sb, "buvid3", SharedPreferencesUtil.getString(SharedPreferencesUtil.BUVid3, ""));
        appendCookie(sb, "buvid4", SharedPreferencesUtil.getString(SharedPreferencesUtil.BUVid4, ""));
        appendCookie(sb, "bili_ticket", SharedPreferencesUtil.getString(SharedPreferencesUtil.BILI_TICKET, ""));
        appendCookie(sb, "_uuid", SharedPreferencesUtil.getString(SharedPreferencesUtil.UUID, ""));
        appendCookie(sb, "b_lsid", SharedPreferencesUtil.getString(SharedPreferencesUtil.B_LSID, ""));
        appendCookie(sb, "buvid_fp", SharedPreferencesUtil.getString(SharedPreferencesUtil.BUVid_FP, ""));
        appendCookie(sb, "b_nut", SharedPreferencesUtil.getString(SharedPreferencesUtil.B_NUT, ""));
        appendCookie(sb, "bili_ticket_expires", SharedPreferencesUtil.getString(SharedPreferencesUtil.BILI_TICKET_EXPIRES, ""));

        return sb.toString();
    }

    private static void appendCookie(StringBuilder sb, String name, String value) {
        if (value == null || value.length() == 0) return;
        if (sb.length() > 0 && !sb.toString().endsWith("; ")) {
            sb.append("; ");
        }
        sb.append(name).append("=").append(value);
    }

    private static void generateBiliTicket() {
        try {
            int ts = (int) (System.currentTimeMillis() / 1000);
            String hexsign = hmacSha256("XgwSnGZ1p", "ts" + ts);
            String url = "https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket?key_id=ec02&hexsign=" + hexsign + "&context[ts]=" + ts;

            java.util.ArrayList<String> headers = new java.util.ArrayList<String>();
            headers.add("User-Agent");
            headers.add(NetWorkUtil.USER_AGENT_WEB);
            headers.add("Referer");
            headers.add("https://www.bilibili.com/");
            headers.add("Content-Type");
            headers.add("application/x-www-form-urlencoded");

            // NetWorkUtil.post 直接返回 String
            String response = NetWorkUtil.post(url, "", headers);
            if (response == null || response.length() == 0) return;

            JSONObject resp = new JSONObject(response);
            JSONObject data = resp.optJSONObject("data");
            if (data != null) {
                String ticket = data.optString("ticket", "");
                long createTime = data.optLong("created_at", 0);
                if (ticket != null && ticket.length() > 0) {
                    SharedPreferencesUtil.putString(SharedPreferencesUtil.BILI_TICKET, ticket);
                    SharedPreferencesUtil.putString(SharedPreferencesUtil.BILI_TICKET_EXPIRES, String.valueOf(createTime + 3 * 24 * 60 * 60));
                    Log.d(TAG, "生成 bili_ticket 成功");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "生成 bili_ticket 失败", e);
        }
    }

    private static String hmacSha256(String key, String message) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hashBytes = sha256Hmac.doFinal(message.getBytes("UTF-8"));
            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexHash.append('0');
                hexHash.append(hex);
            }
            return hexHash.toString();
        } catch (Exception e) {
            Log.e(TAG, "HMAC-SHA256 失败", e);
            return "";
        }
    }

    private static String genBlsid() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString() + "_" + Long.toHexString(System.currentTimeMillis()).toUpperCase(Locale.getDefault());
    }

    private static String genUuidInfoc() {
        long t = System.currentTimeMillis() % 100000;
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int len : PCK) {
            for (int i = 0; i < len; i++) {
                sb.append(MP[random.nextInt(16)]);
            }
            sb.append("-");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(String.format(Locale.getDefault(), "%05d", t)).append("infoc");
        return sb.toString();
    }

    private static String genBuvidFp() {
        return String.format("%016x%016x",
                System.currentTimeMillis() ^ 0x52DCE729L,
                System.nanoTime() ^ 0x38495AB5L);
    }
}