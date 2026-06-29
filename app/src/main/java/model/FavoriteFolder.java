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

public class FavoriteFolder {
    public long id;           // 对应接口的 id
    public long fid;          // 对应接口的 fid
    public String name;       // 对应接口的 title
    public String cover;      // 封面（可能需要额外请求）
    public int videoCount;    // 对应接口的 media_count
    public int maxCount;      // 最大容量（默认50000）
    public boolean isPrivate; // 是否私密（fav_state == 0 为公开，!= 0 为私密）
    public int attr;          // 属性值（保留用于判断）
    public long mid;          // 用户ID

    public FavoriteFolder() {
        this.maxCount = 50000;
        this.isPrivate = false;
    }
}