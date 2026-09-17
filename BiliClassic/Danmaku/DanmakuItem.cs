using System.Windows.Media;

namespace BiliClassic.Danmaku
{
    /// <summary>
    /// BaseDanmaku
    /// </summary>
    public sealed class DanmakuItem
    {
        /// <summary>出现时间</summary>
        public int TimeMs;

        public string Text = "";

        public DanmakuMode Mode = DanmakuMode.Scroll;

        /// <summary>颜色</summary>
        public Color Color = Colors.White;

        /// <summary>字号</summary>
        public int TextSize = 25;

        /// <summary>发送时间戳</summary>
        public long PostTime;

        /// <summary>弹幕ID</summary>
        public long Dmid;
    }
}
