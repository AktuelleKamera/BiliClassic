namespace BiliClassic.Danmaku
{
    /// <summary>
    /// 弹幕渲染配置移植版，对应DanmakuFlameMaster的DanmakuContext
    /// </summary>
    public sealed class DanmakuContext
    {
        /// <summary>总开关</summary>
        public bool Enabled = true;

        /// <summary>整层的不透明度</summary>
        public double Opacity = 1.0;

        /// <summary>
        /// 字号缩放
        /// </summary>
        public double FontScale = 1.0;

        /// <summary>
        /// 最多轨道数
        /// </summary>
        public int MaxLines = 8;

        /// <summary>同屏条数</summary>
        public int MaxConcurrent = 30;

        /// <summary>滚动时间。</summary>
        public int ScrollDurationMs = 7000;

        /// <summary>顶部/底部弹幕停留时间</summary>
        public int TopBottomDurationMs = 4500;

        /// <summary>
        /// 是否给文字加深色描边（容易掉帧XD）
        /// </summary>
        public bool StrokeEnabled = true;

        /// <summary>各类弹幕单独的显示开关</summary>
        public bool ShowScroll = true;
        public bool ShowTop = true;
        public bool ShowBottom = true;

        /// <summary>某条轨道的高度（像素），由字号推出</summary>
        public double LineHeight
        {
            get { return 25.0 * FontScale * 1.35; }
        }

        /// <summary>换算原始字号</summary>
        public double FontSizeFor(int biliSize)
        {
            if (biliSize <= 0)
            {
                biliSize = 25;
            }
            return biliSize * FontScale;
        }

        /// <summary>是否开启</summary>
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
