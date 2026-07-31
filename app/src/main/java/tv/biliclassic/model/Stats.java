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
package tv.biliclassic.model;

import org.json.JSONObject;

import java.io.Serializable;

public class Stats implements Serializable {
    public int view;
    public int like;
    public int reply;
    public int coin;
    public int share;
    public int danmaku;
    public int favorite;

    public boolean followed;
    public boolean liked;
    public boolean disliked;
    public boolean favoured;
    public int coined;

    public boolean like_disabled;
    public boolean coin_disabled;
    public boolean fav_disabled;
    public boolean reply_disabled;
    public boolean share_disabled;
    public int coin_limit;

    public Stats() {
    }

    /**
     * 从动态的 module_stat 解析统计信息
     */
    public static Stats fromOpus(JSONObject moduleStat) {
        Stats stats = new Stats();
        if (moduleStat == null) {
            return stats;
        }

        JSONObject coinObj = moduleStat.optJSONObject("coin");
        if (coinObj != null) {
            stats.coin = coinObj.optInt("count");
            stats.coin_disabled = coinObj.optBoolean("forbidden", true) || coinObj.optBoolean("hidden", true);
        }

        JSONObject commentObj = moduleStat.optJSONObject("comment");
        if (commentObj != null) {
            stats.reply = commentObj.optInt("count");
            stats.reply_disabled = commentObj.optBoolean("forbidden", true);
        }

        JSONObject favoriteObj = moduleStat.optJSONObject("favorite");
        if (favoriteObj != null) {
            stats.favorite = favoriteObj.optInt("count");
            stats.fav_disabled = favoriteObj.optBoolean("forbidden", true);
            stats.favoured = favoriteObj.optBoolean("status", true);
        }

        JSONObject forwardObj = moduleStat.optJSONObject("forward");
        if (forwardObj != null) {
            stats.share = forwardObj.optInt("count");
            stats.share_disabled = forwardObj.optBoolean("forbidden", true);
        }

        JSONObject likeObj = moduleStat.optJSONObject("like");
        if (likeObj != null) {
            stats.like = likeObj.optInt("count");
            stats.like_disabled = likeObj.optBoolean("forbidden", true);
            stats.liked = likeObj.optBoolean("status", true);
        }

        return stats;
    }
}