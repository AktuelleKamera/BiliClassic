using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class SettingsPage : PhoneApplicationPage
    {
        private bool _loading;

        public SettingsPage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            _loading = true;
            OfflinePlaybackCheck.IsChecked = AppSettings.OfflinePlayback;
            DashPlaybackCheck.IsChecked = AppSettings.DashPlayback;
            DanmakuCheck.IsChecked = AppSettings.DanmakuEnabled;
            AutoCheckUpdateCheck.IsChecked = AppSettings.AutoCheckUpdate;
            LoadQuality(AppSettings.PlayQuality);
            _loading = false;

            if (!AppSettings.DashSupported)
            {
                DashPlaybackCheck.Visibility = Visibility.Collapsed;
                DashHintText.Visibility = Visibility.Collapsed;
            }
        }

        private void LoadQuality(int quality)
        {
            switch (quality)
            {
                case 16:
                    Quality16.IsChecked = true;
                    break;
                case 64:
                    Quality64.IsChecked = true;
                    break;
                case 80:
                    Quality80.IsChecked = true;
                    break;
                default:
                    Quality32.IsChecked = true;
                    break;
            }
        }

        private void Quality_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            RadioButton button = sender as RadioButton;
            if (button == null)
            {
                return;
            }

            int quality;
            if (int.TryParse(button.Tag as string, out quality))
            {
                AppSettings.PlayQuality = quality;
                StatusText.Text = "清晰度已改为 " + button.Content;
            }
        }

        private void DashPlayback_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.DashPlayback = DashPlaybackCheck.IsChecked == true;
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
