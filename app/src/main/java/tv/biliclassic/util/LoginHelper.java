package tv.biliclassic.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 登录公共逻辑：Cookie 落库、会话 key 同步、后台 nav 校验。
 * 供扫码/验证码/网页/Cookie 四种登录方式共用。
 */
public class LoginHelper {

    public interface Callback {
        /** ok=true 表示凭证有效（或网络异常无法判定，按成功处理）；msg 为失败原因 */
        void onResult(boolean ok, String msg);
    }

    private static final Handler sUi = new Handler(Looper.getMainLooper());

    /** Cookie 是否包含登录所需的关键项 */
    public static boolean hasValidCookie(String cookie) {
        return cookie != null
                && cookie.indexOf("SESSDATA=") >= 0
                && cookie.indexOf("bili_jct=") >= 0;
    }

    /** 写入 Cookie 并同步 csrf/mid（不做网络校验） */
    public static void saveCookie(String cookie) {
        if (cookie == null) {
            return;
        }
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookie);
        NetWorkUtil.setCookieString(cookie);
        NetWorkUtil.refreshHeaders();
        NetWorkUtil.syncLoginState();
    }

    /**
     * 后台用 nav 校验候选 Cookie：仅 -101(账号未登录) 判定失败，
     * -412 等风控码 / 网络异常都不误杀（按成功处理）。
     * 校验前先落库让 nav 带上这套 Cookie；失败时回滚旧 Cookie。
     */
    public static void verifyThenSave(final Activity act, final String candidate, final Callback cb) {
        final String oldCookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        saveCookie(candidate);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int navCode = -999;
                try {
                    ArrayList<String> h = new ArrayList<String>();
                    h.add("User-Agent");
                    h.add(NetWorkUtil.USER_AGENT_WEB);
                    h.add("Referer");
                    h.add("https://www.bilibili.com/");
                    JSONObject nav = NetWorkUtil.getJson("https://api.bilibili.com/x/web-interface/nav", h);
                    navCode = nav.optInt("code", -1);
                } catch (Throwable e) {
                    navCode = -999; // 网络瞬时异常不判死
                }
                final boolean success = navCode != -101;
                final int code = navCode;
                sUi.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            if (cb != null) {
                                cb.onResult(true, "");
                            }
                        } else {
                            // -101：回滚旧 Cookie，避免残留“假登录”状态
                            saveCookie(oldCookie);
                            if (cb != null) {
                                cb.onResult(false, "登录未生效（账号未登录 code " + code + "）");
                            }
                        }
                    }
                });
            }
        }).start();
    }
}
