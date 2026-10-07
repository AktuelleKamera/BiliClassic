package tv.biliclassic.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Vibrator;
import android.widget.Toast;

import tv.biliclassic.LiveRoomListActivity;
import tv.biliclassic.PrivateMsgListActivity;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.WebViewActivity;

/**
 * 搜索作弊码（彩蛋）。
 *
 * 独立工具类：主界面内联搜索与搜索页都在「真正进入搜索页之前」先判断，
 * 命中就直接跳转、不再打开搜索页——否则会先闪一下搜索界面，返回还停在空搜索页。
 */
public final class CheatCodeUtil {

    private CheatCodeUtil() {
    }

    /**
     * 命中作弊码则执行彩蛋并返回 true。
     */
    /** 命中 av号/BV号 时直接打开视频详情并返回 true（不打开搜索页，避免返回时残留空搜索页）。 */
    public static boolean tryOpenVideoById(Activity activity, String keyword) {
        if (activity == null || keyword == null) return false;
        String k = keyword.trim();
        java.util.regex.Matcher av = java.util.regex.Pattern
                .compile("av(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(k);
        if (av.find()) {
            try {
                Intent intent = new Intent(activity, tv.biliclassic.VideoDetailActivity.class);
                intent.putExtra("aid", Long.parseLong(av.group(1)));
                activity.startActivity(intent);
                return true;
            } catch (Throwable t) {
            }
        }
        java.util.regex.Matcher bv = java.util.regex.Pattern
                .compile("bv([a-zA-Z0-9]{10})", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(k);
        if (bv.find()) {
            try {
                String bvid = bv.group(1);
                if (!bvid.startsWith("BV") && !bvid.startsWith("bv")) {
                    bvid = "BV" + bvid;
                }
                Intent intent = new Intent(activity, tv.biliclassic.VideoDetailActivity.class);
                intent.putExtra("bvid", bvid);
                activity.startActivity(intent);
                return true;
            } catch (Throwable t) {
            }
        }
        return false;
    }

    public static boolean tryTrigger(final Activity activity, String keyword) {
        if (activity == null || keyword == null || keyword.length() == 0) {
            return false;
        }
        String k = keyword.toLowerCase();
        Intent intent = null;

        // GTA 作弊码 → 设置
        if (k.equals("nuttertools") || k.equals("professionaltools") || k.equals("thugstools")) {
            intent = new Intent(activity, SettingsActivity.class);
        } else if (k.equals("giveusatank")) {
            // 神秘页面
            intent = new Intent(activity, WebViewActivity.class);
            intent.putExtra("url", "http://www.biliclassic.cn/buy");
            intent.putExtra("title", "不必追求2.3版本");
        } else if (k.equals("gettherequickly")) {
            // 生放送（直播）
            intent = new Intent(activity, LiveRoomListActivity.class);
        } else if (k.equals("aspirine")) {
            // 私信
            intent = new Intent(activity, PrivateMsgListActivity.class);
        } else if (k.equals("weaknesspays")) {
            // EA GAMES：Ostwind 硬解 + 隐藏控制栏/加载动画，播完退出
            intent = new Intent(activity, tv.biliclassic.player.OstwindPlayerActivity.class);
            intent.putExtra("video_url", "http://www.biliclassic.cn/EA_GAMES.mp4");
            intent.putExtra("video_title", "EA GAMES");
            intent.putExtra("hide_controls", true);
            intent.putExtra("force_hw_decode", true);
            intent.putExtra("hide_loading", true);
        }

        if (intent == null) {
            return false;
        }

        try {
            Vibrator vibrator = (Vibrator) activity.getSystemService(Activity.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(200);
            }
        } catch (Exception e) {
        }

        Toast.makeText(activity, activity.getString(tv.biliclassic.R.string.cheat_code_enabled),
                Toast.LENGTH_LONG).show();

        final Intent toStart = intent;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    activity.startActivity(toStart);
                } catch (Throwable t) {
                }
            }
        }, 800);
        return true;
    }
}
