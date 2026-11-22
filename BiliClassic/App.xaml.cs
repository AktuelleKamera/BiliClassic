using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Navigation;
using System.Windows.Shapes;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;
using System.Threading;
using BiliClassic.Api;

namespace BiliClassic
{
    public partial class App : Application
    {
        public PhoneApplicationFrame RootFrame { get; private set; }

        public App()
        {
            UnhandledException += Application_UnhandledException;

            InitializeComponent();

            InitializePhoneApplication();

            if (System.Diagnostics.Debugger.IsAttached)
            {
                Application.Current.Host.Settings.EnableFrameRateCounter = true;



                PhoneApplicationService.Current.UserIdleDetectionMode = IdleDetectionMode.Disabled;
            }

        }
        private const int SplashDelayMilliseconds = 750;
        public bool Is_DY_Reading = false;
        public bool Is_MS_Reading = false;
        public bool Is_BackFromDeactivated = false;
        public bool Is_PlayingVideo = false;
        public string Is_PlayingPage = "";
        public double VideoValueTemp = 0;

        public CommentItem _sec_comment_temp = new CommentItem();//避免在加载二级评论时重新加载原评论
        

        private void Application_Launching(object sender, LaunchingEventArgs e)
        {
            //TiltEffect.TiltableItems.Add(typeof(WrapPanel));
            TiltEffect.TiltableItems.Add(typeof(Border));//有点bug先暂时不加了

            System.Threading.Thread.Sleep(SplashDelayMilliseconds);

            ThemeHelper.AutoApply();
            LoadSession();
            StartNfcIfEnabled();
            BiliClassic.Api.DeviceReport.ReportIfNeeded();
        }

        private static void LoadSession()
        {
            BiliClassic.Api.BiliSession.Load();

            BiliClassic.Api.CookieGenerator.Ensure(null);
        }

        /// <summary>NFC 零距同步开了就全局订阅，任何页面都能碰</summary>
        private static void StartNfcIfEnabled()
        {
#if WP81
            if (!BiliClassic.Api.AppSettings.NfcSyncEnabled)
            {
                return;
            }
            BiliClassic.Api.NfcSync.StartWithPrompt();
#endif
        }
        //恢复
        private void Application_Activated(object sender, ActivatedEventArgs e)
        {
            //System.Diagnostics.Debug.WriteLine("UUU" + Is_PlayingVideo);

            object savedValue_1;
            if (PhoneApplicationService.Current.State.TryGetValue("Is_PlayingVideo", out savedValue_1))
            {
                Is_PlayingVideo = (bool)savedValue_1;
            }
            object savedValue_2;
            if (PhoneApplicationService.Current.State.TryGetValue("Is_PlayingPage", out savedValue_2))
            {
                Is_PlayingPage = (string)savedValue_2;
            }
            /*
            object savedValue_3;
            if (PhoneApplicationService.Current.State.TryGetValue("VideoValueTemp", out savedValue_3))
            {
                VideoValueTemp = (double)savedValue_3;
            }
            */
            //System.Diagnostics.Debug.WriteLine("UUU " + VideoValueTemp);
            if (Is_PlayingVideo)
            {
                Is_BackFromDeactivated = true;
            }
            ThemeHelper.AutoApply();
            LoadSession();
            StartNfcIfEnabled();
            BiliClassic.Api.DeviceReport.ReportIfNeeded();
        }
        //墓碑化
        private void Application_Deactivated(object sender, DeactivatedEventArgs e)
        {
            System.Diagnostics.Debug.WriteLine("ZZZ " + VideoValueTemp);

            PhoneApplicationService.Current.State["Is_PlayingVideo"] = Is_PlayingVideo;
            PhoneApplicationService.Current.State["Is_PlayingPage"] = Is_PlayingPage;
            //PhoneApplicationService.Current.State["VideoValueTemp"] = VideoValueTemp;
#if WP81
            // 挂起后订阅会失效，先放掉，回到前台再按开关重新订阅
            BiliClassic.Api.NfcSync.Stop();
#endif
        }

        private void Application_Closing(object sender, ClosingEventArgs e)
        {
#if WP81
            BiliClassic.Api.NfcSync.Stop();
#endif
        }

        private void RootFrame_Navigating(object sender, NavigatingCancelEventArgs e)
        {
            ThemeHelper.ApplyFrameBackground(BiliClassic.Api.AppSettings.WhiteTheme);
        }

        private void RootFrame_Navigated(object sender, NavigationEventArgs e)
        {
            PhoneApplicationPage page = e.Content as PhoneApplicationPage;
            if (page == null)
            {
                return;
            }
            ThemeHelper.ApplyPage(page);
            page.Loaded += Page_Loaded;
        }

        private static void Page_Loaded(object sender, RoutedEventArgs e)
        {
            PhoneApplicationPage page = sender as PhoneApplicationPage;
            if (page == null)
            {
                return;
            }
            page.Loaded -= Page_Loaded;
            ThemeHelper.ApplyPage(page);
        }

        private void RootFrame_NavigationFailed(object sender, NavigationFailedEventArgs e)
        {
            if (System.Diagnostics.Debugger.IsAttached)
            {
                System.Diagnostics.Debugger.Break();
            }
        }

        private void Application_UnhandledException(object sender, ApplicationUnhandledExceptionEventArgs e)
        {
            if (System.Diagnostics.Debugger.IsAttached)
            {
                System.Diagnostics.Debugger.Break();
            }
        }

        #region 电话应用程序初始化

        private bool phoneApplicationInitialized = false;

        private void InitializePhoneApplication()
        {
            if (phoneApplicationInitialized)
                return;

            RootFrame = new TransitionFrame();
            RootFrame.Navigated += CompleteInitializePhoneApplication;

            RootFrame.Navigated += RootFrame_Navigated;

            RootFrame.Navigating += RootFrame_Navigating;

            RootFrame.NavigationFailed += RootFrame_NavigationFailed;

            phoneApplicationInitialized = true;
        }

        private void CompleteInitializePhoneApplication(object sender, NavigationEventArgs e)
        {
            if (RootVisual != RootFrame)
                RootVisual = RootFrame;

            RootFrame.Navigated -= CompleteInitializePhoneApplication;
        }

        #endregion
    }
}