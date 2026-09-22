namespace BiliClassic.Danmaku
{
    public enum DanmakuMode
    {
        Scroll = 1,

        Bottom = 4,

        Top = 5,

        Reverse = 6,

        Advanced = 7,

        Code = 8
    }

    public static class DanmakuModeExtensions
    {
        public static bool IsSupported(this DanmakuMode mode)
        {
            return mode == DanmakuMode.Scroll
                || mode == DanmakuMode.Top
                || mode == DanmakuMode.Bottom
                || mode == DanmakuMode.Reverse;
        }

        public static bool IsMoving(this DanmakuMode mode)
        {
            return mode == DanmakuMode.Scroll || mode == DanmakuMode.Reverse;
        }
    }
}
