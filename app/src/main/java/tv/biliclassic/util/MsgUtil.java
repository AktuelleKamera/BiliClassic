package tv.biliclassic.util;

import android.content.Context;
import android.widget.Toast;

/**
 * 消息提示工具类
 * 需要传入 Context
 */
public class MsgUtil {

    private static Toast toast;
    private static String lastMsg;
    private static long lastMsgTime;
    private static final long DEDUP_MS = 3000;

    // 显示短提示
    public static void showMsg(Context context, String msg) {
        if (context == null || msg == null) return;
        long now = System.currentTimeMillis();
        if (msg.equals(lastMsg) && now - lastMsgTime < DEDUP_MS) return;
        lastMsg = msg;
        lastMsgTime = now;
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    // 显示长提示
    public static void showMsgLong(Context context, String msg) {
        if (context == null) return;
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
        toast.show();
    }
}