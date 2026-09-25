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

        private void Application_Launching(object sender, LaunchingEventArgs e)
        {
            //TiltEffect.TiltableItems.Add(typeof(WrapPanel));
            TiltEffect.TiltableItems.Add(typeof(Border));//有点bug先暂时不加了

            System.Threading.Thread.Sleep(SplashDelayMilliseconds);

            ThemeHelper.AutoApply();
            LoadSession();
        }

        private void Application_Activated(object sender, ActivatedEventArgs e)
        {
            ThemeHelper.AutoApply();
            LoadSession();
        }

        private static void LoadSession()
        {
            BiliClassic.Api.BiliSession.Load();

            BiliClassic.Api.CookieGenerator.Ensure(null);
        }

        private void Application_Deactivated(object sender, DeactivatedEventArgs e)
        {
        }

        private void Application_Closing(object sender, ClosingEventArgs e)
        {
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