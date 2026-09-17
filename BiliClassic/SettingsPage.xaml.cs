using System;
using System.Windows;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    /// <summary>
    /// 设置页
    /// 离线播放、弹幕、自动检查更新、清缓存
    /// </summary>
    public partial class SettingsPage : PhoneApplicationPage
    {
        /// <summary>
        /// 给 CheckBox 赋初值会触发 Checked/Unchecked
        /// 不加标记会反写回存储
        /// </summary>
        private bool _loading;

        public SettingsPage()
        {
            InitializeComponent();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            _loading = true;
            OfflinePlaybackCheck.IsChecked = AppSettings.OfflinePlayback;
            DanmakuCheck.IsChecked = AppSettings.DanmakuEnabled;
            AutoCheckUpdateCheck.IsChecked = AppSettings.AutoCheckUpdate;
            _loading = false;
        }

        private void Danmaku_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.DanmakuEnabled = DanmakuCheck.IsChecked == true;
        }

        private void AutoCheckUpdate_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.AutoCheckUpdate = AutoCheckUpdateCheck.IsChecked == true;
        }

        private void CheckUpdate_Click(object sender, RoutedEventArgs e)
        {
            CheckUpdateButton.IsEnabled = false;
            StatusText.Text = "正在检查更新";

            UpdateChecker.Check(false, delegate
            {
                CheckUpdateButton.IsEnabled = true;
                StatusText.Text = "";
            });
        }

        private void OfflinePlayback_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.OfflinePlayback = OfflinePlaybackCheck.IsChecked == true;

            StatusText.Text = AppSettings.OfflinePlayback
                ? "已打开离线播放：播放时会先下载到本机。"
                : "已关闭离线播放：播放时直接在线播放。";
        }

        private void ClearCache_Click(object sender, RoutedEventArgs e)
        {
            long freed = MediaCache.Clear();

            StatusText.Text = freed > 0
                ? "已清除离线缓存，释放 " + MediaCache.FormatSize(freed)
                : "没有可清除的离线缓存。";
        }
    }
}
