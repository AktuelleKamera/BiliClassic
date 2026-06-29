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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

import tv.biliclassic.util.SharedPreferencesUtil;

public class UserInfo implements Serializable {
    public long mid;
    public String name;
    public String avatar;
    public String sign;
    public int fans;
    public int level;
    public int following;
    public boolean followed;
    public String notice;

    public int official;
    public String officialDesc;
    public long mtime;

    public int vip_role = 0;
    public String vip_nickname_color = "";

    public long current_exp = 0;
    public long next_exp = 0;

    public String medal_name = "";
    public int medal_level = 0;

    public String sys_notice = "";

    // 直播功能暂时不需要，注释掉
    // public LiveRoom live_room = null;

    public int is_senior_member = 0;

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.is_senior_member = isSeniorMember;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, String sysNotice, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.sys_notice = sysNotice;
        this.is_senior_member = isSeniorMember;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, long currentExp, long nextExp, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.current_exp = currentExp;
        this.next_exp = nextExp;
        this.is_senior_member = isSeniorMember;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, int vipRole, String sysNotice, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.vip_role = vipRole;
        this.sys_notice = sysNotice;
        this.is_senior_member = isSeniorMember;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, long mtime, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.mtime = mtime;
        this.is_senior_member = isSeniorMember;
    }

    public UserInfo() {
    }

    public UserInfo(JSONObject userInfoJson) throws JSONException {
        this.level = userInfoJson.getJSONObject("level_info").getInt("current_level");
        this.mid = userInfoJson.getLong("mid");
        this.name = userInfoJson.getString("uname");
        this.avatar = userInfoJson.getString("avatar");
        this.is_senior_member = userInfoJson.getInt("is_senior_member");
        JSONObject vip = userInfoJson.getJSONObject("vip");
        this.vip_role = vip.getInt("vipStatus");
        this.vip_nickname_color = vip.getString("nickname_color");
        if ((!userInfoJson.isNull("fans_detail")) && (!SharedPreferencesUtil.getBoolean("no_medal", false))) {
            JSONObject fansDetail = userInfoJson.getJSONObject("fans_detail");
            this.medal_name = fansDetail.getString("medal_name");
            this.medal_level = fansDetail.getInt("level");
        }
    }
}