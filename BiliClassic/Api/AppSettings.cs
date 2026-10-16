using System;

namespace BiliClassic.Api
{
    public static class AppSettings
    {
        private const string OfflinePlaybackKey = "offline_playback";
        private const string DanmakuEnabledKey = "danmaku_enabled";
        private const string AutoCheckUpdateKey = "auto_check_update";
        private const string PlayQualityKey = "play_quality";
        private const string DashPlaybackKey = "dash_playback";
        private const string WhiteThemeKey = "white_theme";
        //这仨是我加的——WinVistaSP
        private const string XiNaoEnabledKey = "xinao_enabled";//洗脑循环
        private const string ZiDongEnabledKey = "zidong_enabled";//自动播放
        private const string YinLiangValueKey = "yinliang_value";//音量


        public static int PlayQuality
        {
            get
            {
                int value;
                return int.TryParse(LocalStore.Get(PlayQualityKey), out value) && value > 0 && value != 6
                    ? value
                    : 32;
            }
            set { LocalStore.Set(PlayQualityKey, value.ToString()); }
        }

        public static bool AutoCheckUpdate
        {
            get { return LocalStore.Get(AutoCheckUpdateKey) != "0"; }
            set { LocalStore.Set(AutoCheckUpdateKey, value ? "1" : "0"); }
        }

        public static bool OfflinePlayback
        {
            get { return LocalStore.Get(OfflinePlaybackKey) == "1"; }
            set { LocalStore.Set(OfflinePlaybackKey, value ? "1" : "0"); }
        }

        public static bool DashSupported
        {
            get
            {
                Version version = Environment.OSVersion.Version;
                return version.Major > 7 || (version.Major == 7 && version.Minor >= 1);
            }
        }

        public static bool DashPlayback
        {
            get { return LocalStore.Get(DashPlaybackKey) == "1"; }
            set { LocalStore.Set(DashPlaybackKey, value ? "1" : "0"); }
        }

        public static bool WhiteTheme
        {
            get { return LocalStore.Get(WhiteThemeKey) == "1"; }
            set { LocalStore.Set(WhiteThemeKey, value ? "1" : "0"); }
        }

        public static bool DanmakuEnabled
        {
            get { return LocalStore.Get(DanmakuEnabledKey) != "0"; }
            set { LocalStore.Set(DanmakuEnabledKey, value ? "1" : "0"); }
        }

        public static bool XiNaoEnabled
        {
            get { return LocalStore.Get(XiNaoEnabledKey) != "0"; }
            set { LocalStore.Set(XiNaoEnabledKey, value ? "1" : "0"); }
        }

        public static bool ZiDongEnabled
        {
            get { return LocalStore.Get(ZiDongEnabledKey) != "0"; }
            set { LocalStore.Set(ZiDongEnabledKey, value ? "1" : "0"); }
        }

        public static double YinLiangValue
        {
            get
            {
                double readtemp_;
                if (double.TryParse(LocalStore.Get(YinLiangValueKey), out readtemp_))
                {
                    if (readtemp_ < 0 && readtemp_ > 1)
                    {
                        readtemp_ = 0.5;
                    }
                }
                else
                {
                    readtemp_ = 0.5;
                }
                return readtemp_;

            }
            set { LocalStore.Set(YinLiangValueKey, value.ToString()); }
        }
    }
}
