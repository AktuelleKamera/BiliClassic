using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Threading;
using BiliClassic.Api;
using Microsoft.Devices;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;
using Microsoft.Phone.Tasks;
using System.Windows.Shapes;
using Microsoft.Xna.Framework.GamerServices;

namespace BiliClassic
{
    public partial class WelcomePage : PhoneApplicationPage
    {
        public WelcomePage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            V1.Text = "当前版本：" + UpdateService.VersionNumber;
            CheckUpdate();
            /*
            AgreementPageG.Visibility = Visibility.Collapsed;
            WelcomePageG.Visibility = Visibility.Collapsed;
            FinishPageG.Visibility = Visibility.Collapsed;
            G1.Visibility = Visibility.Collapsed;
            G2.Visibility = Visibility.Collapsed;
            G3.Visibility = Visibility.Collapsed;
            //G4.Visibility = Visibility.Collapsed;
            //G5.Visibility = Visibility.Collapsed;
             */
            AgreementHide.Begin();
            WelcomeHide.Begin();
            FinishHide.Begin();
            G1Hide.Begin();
            G2Hide.Begin();
            G3Hide.Begin();

            DoDongHua(0, false);

            DanmakuCheck.IsChecked = AppSettings.DanmakuEnabled;
            XiNaoCheck.IsChecked = AppSettings.XiNaoEnabled;
            ZiDongCheck.IsChecked = AppSettings.ZiDongEnabled;
            AutoCheckUpdateCheck.IsChecked = AppSettings.AutoCheckUpdate;
            LockCiTieCheck.IsChecked = AppSettings.LockCiTieEnabled;
            HideCiTieCheck.IsChecked = AppSettings.HideCiTieEnabled;
            LoadQuality(AppSettings.PlayQuality);

            if ((Visibility)Application.Current.Resources["PhoneLightThemeVisibility"] == System.Windows.Visibility.Visible)
            {//浅色
                BcgImage.Visibility = Visibility.Visible;
            }
            else
            {//深色
                BcgImage.Visibility = Visibility.Collapsed;
            }

        }

        private int PageIndex = 0;
        private string UpdateUrl = "";
        private bool IsCheckingUpdate = false;
        private bool CanBack = false;

        private void CheckUpdate()
        {
            V2.Text = "最新版本：检查中...";
            NewInfoLabel.Visibility = Visibility.Collapsed;
            scrollViewer3.Visibility = Visibility.Collapsed;
            button6.Visibility = Visibility.Collapsed;
            button7.IsEnabled = false;
            IsCheckingUpdate = true;


            UpdateService.Check(delegate(UpdateInfo info)
            {
                Deployment.Current.Dispatcher.BeginInvoke(delegate
                {
                    try
                    {
                        if (info.HasUpdate)
                        {
                            V2.Text = "最新版本：" + info.Latest;
                            UpdateUrl = info.DownloadUrl;
                            if (info.Changelog.Length > 0)
                            {
                                NewInfo.Text = info.Changelog;
                                NewInfoLabel.Visibility = Visibility.Visible;
                                scrollViewer3.Visibility = Visibility.Visible;
                            }
                            button6.Visibility = Visibility.Visible;
                        }
                        else
                        {
                            V2.Text = "最新版本：" + info.Latest;
                        }
                    }
                    catch
                    {
                        V2.Text = "最新版本：NAN";
                    }
                    finally
                    {
                        IsCheckingUpdate = false;
                        button7.IsEnabled = true;
                        if (PageIndex == 2)
                        {
                            CanBack = true;
                            button3.IsEnabled = true;
                        }
                    }
                });
            });
        }

        private void SwitchPage(int newIndex)
        {
            DoDongHua(PageIndex, true);
            DoDongHua(newIndex, false);
            PageIndex = newIndex; 
        }

        private void DoDongHua(int index,bool IsHide)
        {
            switch (index)
            {
                case 0:
                    if (!IsHide)
                    {
                        //进入页面动画
                        PageTitle.Text = "用户协议";

                        AgreementShow.Begin();
                        //AgreementPageG.Visibility = Visibility.Visible;
                    }
                    else
                    {
                        //离开页面动画
                        //G1.Visibility = Visibility.Collapsed;
                        //G2.Visibility = Visibility.Collapsed;
                        //G3.Visibility = Visibility.Collapsed;
                        AgreementHide.Begin();
                        WelcomeShow.Begin();
                        //G1.Visibility = Visibility.Visible;
                        //G2.Visibility = Visibility.Visible;
                        //G3.Visibility = Visibility.Visible;
                        //AgreementPageG.Visibility = Visibility.Collapsed;
                        //WelcomePageG.Visibility = Visibility.Visible;
                    }
                    break;

                case 1:
                    if (!IsHide)
                    {
                        //进入页面动画
                        PageTitle.Text = "初次见面";
                        CanBack = false;
                        button3.IsEnabled = true;
                        G1Show.Begin();
                        //G1.Visibility = Visibility.Visible;
                        //WelcomePageG.Visibility = Visibility.Visible;
                    }
                    else
                    {
                        //离开页面动画
                        G1Hide.Begin();
                        //G1.Visibility = Visibility.Collapsed;
                        //WelcomePageG.Visibility = Visibility.Collapsed;
                    }
                    break;


                case 2:
                    if (!IsHide)
                    {
                        //进入页面动画
                        PageTitle.Text = "检查更新";
                        if (IsCheckingUpdate)
                        {
                            CanBack = false;
                            button3.IsEnabled = false;
                        }
                        else
                        {
                            CanBack = true;
                            button3.IsEnabled = true;
                        }
                        G2Show.Begin();
                        //G2.Visibility = Visibility.Visible;
                        //WelcomePageG.Visibility = Visibility.Visible;
                    }
                    else
                    {
                        //离开页面动画
                        G2Hide.Begin();
                        //G2.Visibility = Visibility.Collapsed;
                        //WelcomePageG.Visibility = Visibility.Collapsed;
                    }
                    break;


                case 3:
                    if (!IsHide)
                    {
                        //进入页面动画
                        PageTitle.Text = "设置";
                        CanBack = true;
                        button3.IsEnabled = true;
                        G3Show.Begin();
                        //G3.Visibility = Visibility.Visible;
                        //WelcomePageG.Visibility = Visibility.Visible;
                    }
                    else
                    {
                        //离开页面动画
                        G3Hide.Begin();
                        //G3.Visibility = Visibility.Collapsed;
                        //WelcomePageG.Visibility = Visibility.Collapsed;
                    }
                    break;


                /*
            case 4:
                if (!IsHide)
                {
                    //进入页面动画
                    CanBack = true;
                    button3.IsEnabled = true;
                    G4.Visibility = Visibility.Visible;
                    //WelcomePageG.Visibility = Visibility.Visible;
                }
                else
                {
                    //离开页面动画
                    G4.Visibility = Visibility.Collapsed;
                    //WelcomePageG.Visibility = Visibility.Collapsed;
                }
                break;

            case 5:
                if (!IsHide)
                {
                    //进入页面动画
                    CanBack = true;
                    button3.IsEnabled = true;
                    G5.Visibility = Visibility.Visible;
                    //WelcomePageG.Visibility = Visibility.Visible;
                }
                else
                {
                    //离开页面动画
                    G5.Visibility = Visibility.Collapsed;
                    //WelcomePageG.Visibility = Visibility.Collapsed;
                }
                break;
                */
                default:
                    if (!IsHide)
                    {
                        //进入页面动画
                        PageTitle.Text = "完成"; 
                        WelcomeHide.Begin();
                        FinishShow.Begin();
                        //WelcomePageG.Visibility = Visibility.Collapsed;
                        //FinishPageG.Visibility = Visibility.Visible;
                    }
                    else
                    {
                        //离开页面动画
                        FinishHide.Begin();
                        WelcomeShow.Begin();
                        //WelcomePageG.Visibility = Visibility.Visible;
                        //FinishPageG.Visibility = Visibility.Collapsed;
                    }
                    break;
            }
        }

        //不同意
        private void button1_Click(object sender, RoutedEventArgs e)
        {
            throw new Exception();//WP7.0原生不支持清除导航栈
        }
        //同意
        private void button2_Click(object sender, RoutedEventArgs e)
        {
            SwitchPage(1);
        }
        //上一步
        private void button4_Click(object sender, RoutedEventArgs e)
        {
            SwitchPage(PageIndex - 1);
        }



        protected override void OnBackKeyPress(System.ComponentModel.CancelEventArgs e)
        {
            if (PageIndex - 1 < 0||PageIndex == 1)
            {
                if (MessageBox.Show("退出后将会直接回到主页", "确认退出欢迎页面？", MessageBoxButton.OKCancel) == MessageBoxResult.OK)
                {
                    NavigationService.Navigate(new Uri("/MainPage.xaml", UriKind.Relative));
                }
            }
            else if (CanBack)
            {
                SwitchPage(PageIndex - 1);
            }
            e.Cancel = true;
        }
        //下一步
        private void button3_Click(object sender, RoutedEventArgs e)
        {
            SwitchPage(PageIndex + 1);
        }
        //完成
        private void button5_Click(object sender, RoutedEventArgs e)
        {
            IsFirstLoad.IsLoaded = true;
            NavigationService.Navigate(new Uri("/MainPage.xaml", UriKind.Relative));
        }




        private void AutoCheckUpdate_Changed(object sender, RoutedEventArgs e)
        {
            AppSettings.AutoCheckUpdate = AutoCheckUpdateCheck.IsChecked == true;
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

            RadioButton button = sender as RadioButton;
            if (button == null)
            {
                return;
            }

            int quality;
            if (int.TryParse(button.Tag as string, out quality))
            {
                AppSettings.PlayQuality = quality;
            }
        }

        private void ZiDongCheck_Changed(object sender, RoutedEventArgs e)
        {
            AppSettings.ZiDongEnabled = ZiDongCheck.IsChecked == true;
        }

        private void XiNaoCheck_Changed(object sender, RoutedEventArgs e)
        {
            AppSettings.XiNaoEnabled = XiNaoCheck.IsChecked == true;
        }

        private void LockCiTieCheck_Changed(object sender, RoutedEventArgs e)
        {
            AppSettings.LockCiTieEnabled = LockCiTieCheck.IsChecked == true;
        }

        private void HideCiTieCheck_Changed(object sender, RoutedEventArgs e)
        {
            AppSettings.HideCiTieEnabled = HideCiTieCheck.IsChecked == true;
        }

        private void Danmaku_Changed(object sender, RoutedEventArgs e)
        {
            AppSettings.DanmakuEnabled = DanmakuCheck.IsChecked == true;
        }

        //立即更新
        private void button6_Click(object sender, RoutedEventArgs e)
        {
            WebBrowserTask task = new WebBrowserTask();
#if WP8
            task.Uri = new Uri(UpdateUrl);
#else
            task.URL = UpdateUrl;
#endif
            task.Show();
        }

        private void button7_Click(object sender, RoutedEventArgs e)
        {
            CheckUpdate();
        }
    }
}