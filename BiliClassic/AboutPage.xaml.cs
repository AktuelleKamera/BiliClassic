using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    /// <summary>关于页</summary>
    public partial class AboutPage : PhoneApplicationPage
    {
        public AboutPage()
        {
            InitializeComponent();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            // 显示用前三段：0.1.0，内部是0.1.0.0
            VersionText.Text = "版本 " + UpdateService.DisplayVersion;
        }
    }
}
