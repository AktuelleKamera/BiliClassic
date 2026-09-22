namespace BiliClassic.Danmaku
{
    public sealed class DanmakuContext
    {
        public bool Enabled = true;

        public double Opacity = 1.0;

        public double FontScale = 1.0;

        public int MaxLines = 8;

        public int MaxConcurrent = 24;

        public int ScrollDurationMs = 7000;

        public int TopBottomDurationMs = 4500;

        public bool StrokeEnabled = true;

        public bool ShowScroll = true;
        public bool ShowTop = true;
        public bool ShowBottom = true;

        public double LineHeight
        {
            get { return 25.0 * FontScale * 1.35; }
        }

        public double FontSizeFor(int biliSize)
        {
            if (biliSize <= 0)
            {
                biliSize = 25;
            }
            return biliSize * FontScale;
        }

        public bool IsModeEnabled(DanmakuMode mode)
        {
            if (!mode.IsSupported())
            {
                return false;
            }
            if (mode.IsMoving())
            {
                return ShowScroll;
            }
            if (mode == DanmakuMode.Top)
            {
                return ShowTop;
            }
            return ShowBottom;
        }
    }
}
