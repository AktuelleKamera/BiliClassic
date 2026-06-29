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

public class At implements Serializable {
    public final long id;
    public int start;
    public int end;
    public String name;

    public At(long id, int startIndex, int endIndex) {
        this.id = id;
        this.start = startIndex;
        this.end = endIndex;
    }

    public At(long id, String name) {
        this.id = id;
        this.name = name;
    }
}