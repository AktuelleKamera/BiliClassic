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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class VideoInfo implements Serializable {

    public static final int COPYRIGHT_SELF = 1;
    public static final int COPYRIGHT_REPRINT = 2;

    public String bvid;
    public long aid;
    public String title;
    public ArrayList<UserInfo> staff = new ArrayList<UserInfo>(); // UP主列表
    public String cover;
    public String description;
    public String duration;
    public Stats stats;
    public String timeDesc;
    public ArrayList<String> pagenames = new ArrayList<String>();
    public ArrayList<Long> cids = new ArrayList<Long>();
    public List<At> descAts = new ArrayList<At>();

    public boolean upowerExclusive; // 充电专属
    public String argueMsg; // 争议信息
    public boolean isCooperation; // 联合投稿
    public boolean isSteinGate; // 互动视频
    public boolean is360; // 全景视频

    public long epid; // 如果是番剧，就自动跳转
    public int copyright; // 是否转载
    public Collection collection;

    // 新增：可用画质列表
    public List<Integer> qualities = new ArrayList<Integer>();
    public String tags;  // 标签字符串
    public List<Integer> pages = new ArrayList<Integer>(); // 分P的实际page号

    public VideoInfo() {
    }

    public VideoCard toCard() {
        String upName = "";
        if (staff != null && staff.size() > 0 && staff.get(0) != null) {
            upName = staff.get(0).name;
        }
        String viewStr = "";
        if (stats != null) {
            viewStr = StringUtil.toWan(stats.view);
        }
        return new VideoCard(title, upName, viewStr, cover, aid, bvid, "video");
    }

    public PlayerData toPlayerData(int index) {
        PlayerData data = new PlayerData();
        data.aid = aid;
        if (cids != null && index < cids.size()) {
            data.cid = cids.get(index);
        }
        if (pagenames != null) {
            data.title = (pagenames.size() == 1) ? title : pagenames.get(index);
        } else {
            data.title = title;
        }
        data.mid = SharedPreferencesUtil.getLong("mid", 0);
        data.qn = SharedPreferencesUtil.getInt("play_qn", 16);
        return data;
    }
}