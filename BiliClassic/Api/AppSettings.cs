namespace BiliClassic.Api
{
    /// <summary>
    /// 应用设置
    /// </summary>
    public static class AppSettings
    {
        private const string OfflinePlaybackKey = "offline_playback";
        private const string DanmakuEnabledKey = "danmaku_enabled";
        private const string AutoCheckUpdateKey = "auto_check_update";

        /// <summary>自动检查更新，默认开</summary>
        public static bool AutoCheckUpdate
        {
            get { return LocalStore.Get(AutoCheckUpdateKey) != "0"; }
            set { LocalStore.Set(AutoCheckUpdateKey, value ? "1" : "0"); }
        }

        /// <summary>
        /// 离线播放
        /// </summary>
        public static bool OfflinePlayback
        {
            get { return LocalStore.Get(OfflinePlaybackKey) == "1"; }
            set { LocalStore.Set(OfflinePlaybackKey, value ? "1" : "0"); }
        }

        /// <summary>
        /// 弹幕开关
        /// </summary>
        public static bool DanmakuEnabled
        {
            get { return LocalStore.Get(DanmakuEnabledKey) != "0"; }
            set { LocalStore.Set(DanmakuEnabledKey, value ? "1" : "0"); }
        }
    }
}
