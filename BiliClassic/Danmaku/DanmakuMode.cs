namespace BiliClassic.Danmaku
{
    /// <summary>
    /// 弹幕模式 DanmakuType
    /// </summary>
    public enum DanmakuMode
    {
        /// <summary>1/2/3 普通滚动弹幕</summary>
        Scroll = 1,

        /// <summary>4：底部固定弹幕</summary>
        Bottom = 4,

        /// <summary>5：顶部固定弹幕</summary>
        Top = 5,

        /// <summary>6：逆向滚动</summary>
        Reverse = 6,

        /// <summary>7：高级弹幕，靠坐标和路径脚本绘制，暂不支持</summary>
        Advanced = 7,

        /// <summary>8：BAS代码弹幕，不支持也不该执行</summary>
        Code = 8
    }

    public static class DanmakuModeExtensions
    {
        /// <summary>渲染暂时跳过78（并非91）</summary>
        public static bool IsSupported(this DanmakuMode mode)
        {
            return mode == DanmakuMode.Scroll
                || mode == DanmakuMode.Top
                || mode == DanmakuMode.Bottom
                || mode == DanmakuMode.Reverse;
        }

        /// <summary>是否是横向移动的神秘弹幕</summary>
        public static bool IsMoving(this DanmakuMode mode)
        {
            return mode == DanmakuMode.Scroll || mode == DanmakuMode.Reverse;
        }
    }
}
