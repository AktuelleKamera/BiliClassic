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
import java.util.List;

/**
 * 生放送播放信息（getRoomPlayInfo 返回的流结构）。
 * 结构：stream（协议）→ format（封装）→ codec（编码）→ url_info（CDN 线路）。
 */
public class LivePlayInfo implements Serializable {
    public long roomid;
    public long short_id;
    public long uid;
    public int live_status;
    public long live_time;
    public PlayUrl playUrl;

    public static class PlayUrl implements Serializable {
        public long cid;
        public int dolby_qn;
        public List<QnDesc> g_qn_desc;
        public List<ProtocolInfo> stream;
    }

    public static class QnDesc implements Serializable {
        public int qn;
        public String desc;
    }

    public static class ProtocolInfo implements Serializable {
        public String protocol_name;
        public List<Format> format;
    }

    public static class Format implements Serializable {
        public String format_name;
        public String master_url;
        public List<Codec> codec;
    }

    public static class Codec implements Serializable {
        public String codec_name;
        public int current_qn;
        public List<Integer> accept_qn;
        public String base_url;
        public int hdr_qn;
        public int dolby_type;
        public String attr_name;
        public List<UrlInfo> url_info;
    }

    public static class UrlInfo implements Serializable {
        public String host;
        public String extra;
        public int stream_ttl;
    }

    /** 是否取到了可播放的流 */
    public boolean hasStream() {
        if (playUrl == null || playUrl.stream == null || playUrl.stream.isEmpty()) return false;
        for (int i = 0; i < playUrl.stream.size(); i++) {
            ProtocolInfo p = playUrl.stream.get(i);
            if (p != null && p.format != null && !p.format.isEmpty()) return true;
        }
        return false;
    }
}
