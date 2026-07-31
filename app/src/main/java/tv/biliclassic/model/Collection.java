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

public class Collection implements Serializable {
    public int id;
    public String title;
    public String cover;
    public String intro;
    public long mid;
    public List<Section> sections = new ArrayList<Section>();
    public List<VideoCard> cards = new ArrayList<VideoCard>();
    public String view;

    public Collection() {
    }

    public static class Section implements Serializable {
        public int season_id;
        public int id;
        public String title;
        public int type;
        public List<Episode> episodes = new ArrayList<Episode>();

        public Section() {
        }
    }

    public static class Episode implements Serializable {
        public int season_id;
        public int section_id;
        public long id;
        public long aid;
        public long cid;
        public String title;
        public String bvid;
        public VideoInfo arc;

        public Episode() {
        }
    }
}