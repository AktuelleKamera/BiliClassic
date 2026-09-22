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

/**
 * 生放送（直播）弹幕条目。
 * 直播弹幕没有视频时间轴，采用“收到即显示”的独立机制，
 * 数据来源为 gethistory 历史弹幕轮询（见 docs/live/danmaku.md）。
 */
public class LiveDanmaku {
    /** 弹幕唯一 ID（id_str），用于轮询去重；可能为空 */
    public String id = "";
    public String text = "";
    /** 十进制颜色值，默认白色 */
    public int color = 0xFFFFFF;
    /** 弹幕模式：1 滚动 / 4 底部 / 5 顶部 */
    public int mode = 1;
    public long uid;
    public String nickname = "";
    public String timeline = "";
    public boolean isAdmin;
    public int userLevel;
    public String medalName = "";
    public int medalLevel;

    /** 轮询去重键：优先 id_str，缺失时用 uid+时间+内容拼一个 */
    public String dedupKey() {
        if (id != null && id.length() > 0) return id;
        return uid + "|" + timeline + "|" + text;
    }
}
