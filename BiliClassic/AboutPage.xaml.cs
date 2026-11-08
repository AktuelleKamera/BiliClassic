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
    public partial class AboutPage : PhoneApplicationPage
    {
        public AboutPage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);

        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            VersionText.Text = "版本 " + UpdateService.DisplayVersion;
        }

        private void button1_Click(object sender, System.Windows.RoutedEventArgs e)
        {
            //仅供测试！！！
            NavigationService.Navigate(new Uri("/WelcomePage.xaml", UriKind.Relative));
        }
    }
}
