using System.Windows.Media;

namespace BiliClassic.Danmaku
{
    public sealed class DanmakuItem
    {
        public int TimeMs;

        public string Text = "";

        public DanmakuMode Mode = DanmakuMode.Scroll;

        public Color Color = Colors.White;

        public int TextSize = 25;

        public long PostTime;

        public long Dmid;
    }
}
