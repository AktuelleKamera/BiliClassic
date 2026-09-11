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
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.model;

import java.io.Serializable;

/**
 * 生放送（直播间）房间信息。
 * 字段名与 B 站接口保持一致，便于从 org.json 直接填充。
 */
public class LiveRoom implements Serializable {
    public long roomid;
    public long short_id;
    public long uid;
    public String title;
    public String uname;
    public String face;
    public String cover;
    public String user_cover;
    public String system_cover;
    public String keyframe;
    public String tags;
    public String description;
    public int online;
    public int attention;
    public String area_name;
    public String area_parent_name;
    public int live_status;
    public String liveTime;
    public String watched_text;

    /** 取一个可用的封面地址（用户封面 → 封面 → 关键帧 → 系统封面） */
    public String pickCover() {
        if (user_cover != null && user_cover.length() > 0) return user_cover;
        if (cover != null && cover.length() > 0) return cover;
        if (keyframe != null && keyframe.length() > 0) return keyframe;
        if (system_cover != null && system_cover.length() > 0) return system_cover;
        return "";
    }

    /** 真实房间号：roomid 缺失时用短号 */
    public long realRoomId() {
        if (roomid > 0) return roomid;
        return short_id > 0 ? short_id : 0;
    }
}
