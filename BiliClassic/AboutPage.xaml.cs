using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class AboutPage : PhoneApplicationPage
    {
        public AboutPage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);

#if WP8
            PlatformText.Text = "哔哩经典 for WP";
#else
            PlatformText.Text = "哔哩经典 for WP7";
#endif
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            VersionText.Text = "版本 " + UpdateService.DisplayVersion;
        }
    }
}
