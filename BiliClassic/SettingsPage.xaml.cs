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
#if WP81
            InitNfcUi();
#endif
        }

        private void SettingsPage_Loaded(object sender, RoutedEventArgs e)
        {
            // 可视化树这时才建好，黑色主题下把粉色实心按钮还原成空心
            ThemeHelper.ApplyAccentButtons(this);
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            _loading = true;
            OfflinePlaybackCheck.IsChecked = AppSettings.OfflinePlayback;
            DashPlaybackCheck.IsChecked = AppSettings.DashPlayback;
            DanmakuCheck.IsChecked = AppSettings.DanmakuEnabled;
            XiNaoCheck.IsChecked = AppSettings.XiNaoEnabled;
            ZiDongCheck.IsChecked = AppSettings.ZiDongEnabled;
            AutoCheckUpdateCheck.IsChecked = AppSettings.AutoCheckUpdate;
            LockCiTieCheck.IsChecked = AppSettings.LockCiTieEnabled;
            HideCiTieCheck.IsChecked = AppSettings.HideCiTieEnabled;
            LoadQuality(AppSettings.PlayQuality);
#if WP81
            NfcSyncCheck.IsChecked = AppSettings.NfcSyncEnabled;
#endif
            _loading = false;
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

        private void XiNaoCheck_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.XiNaoEnabled = XiNaoCheck.IsChecked == true;
        }

        private void ZiDongCheck_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.ZiDongEnabled = ZiDongCheck.IsChecked == true;
        }



        private void LockCiTieCheck_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.LockCiTieEnabled = LockCiTieCheck.IsChecked == true;
        }

        private void HideCiTieCheck_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }

            AppSettings.HideCiTieEnabled = HideCiTieCheck.IsChecked == true;
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

        private void NfcSync_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading)
            {
                return;
            }
#if WP81
            AppSettings.NfcSyncEnabled = NfcSyncCheck.IsChecked == true;
            if (AppSettings.NfcSyncEnabled)
            {
                StartNfc();
            }
            else
            {
                StopNfc();
            }
#endif
        }

        private void NfcSend_Click(object sender, RoutedEventArgs e)
        {
#if WP81
            StatusText.Text = NfcSync.SendLogin()
                    ? "NFC正在发送登录信息"
                    : "没有可发送的登录信息或NFC不可用";
#endif
        }

#if WP81
        private void InitNfcUi()
        {
            NfcSyncCheck.Visibility = Visibility.Visible;
            if (!NfcSync.Available)
            {
                NfcSyncCheck.IsEnabled = false;
                NfcSendButton.Visibility = Visibility.Collapsed;
                NfcUnsupportedText.Visibility = Visibility.Visible;
            }
            else
            {
                NfcSendButton.Visibility = Visibility.Visible;
                NfcUnsupportedText.Visibility = Visibility.Collapsed;
            }
        }

        private void StartNfc()
        {
            if (NfcSync.StartWithPrompt())
            {
                StatusText.Text = "NFC已开启，等待另一台设备碰一碰";
            }
        }

        private void StopNfc()
        {
            NfcSync.Stop();
        }
#endif

        private void ClearCache_Click(object sender, RoutedEventArgs e)
        {
            long freed = MediaCache.Clear();

            StatusText.Text = freed > 0
                ? "已清除离线缓存，释放 " + MediaCache.FormatSize(freed)
                : "没有可清除的离线缓存。";
        }
    }
}
