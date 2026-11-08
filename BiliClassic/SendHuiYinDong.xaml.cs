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
using System.Windows.Shapes;
using Microsoft.Phone.Controls;
using System.Windows.Navigation;
using System.Collections.ObjectModel;
using Microsoft.Phone.Shell;
using Microsoft.Phone.Info;

namespace BiliClassic
{
    public partial class SendHuiYinDong : PhoneApplicationPage
    {
        public SendHuiYinDong()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
        }


        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            object DeviceObj;
            DeviceExtendedProperties.TryGetValue("DeviceName", out DeviceObj);
            PhoneNTextBox.Text = DeviceObj as string;
            DateTextBox.Text = DateTime.Now.ToString("yyyy年M月d日");
        }

        private void SendButton_Click(object sender, RoutedEventArgs e)
        {
            string context = ContextTextBox.Text;
            string name = NameTextBox.Text;
            string device = PhoneNTextBox.Text;
            string date = DateTextBox.Text;
            if (context != "")
            {
                SendButton.IsEnabled = false;
                System.Threading.ThreadPool.QueueUserWorkItem((state) =>
                {
                    string respon = Api.EchoHoleService.SendEchoHole(context, name, device, date);
                    Deployment.Current.Dispatcher.BeginInvoke(() =>
                    {
                        if (respon.StartsWith("O"))
                        {
                            MessageBox.Show(respon.Substring(1), "发布成功", MessageBoxButton.OK);
                        }
                        else
                        {
                            System.Diagnostics.Debug.WriteLine(respon.Substring(1));
                            MessageBox.Show(respon.Substring(1), "发布失败", MessageBoxButton.OK);
                        }
                        SendButton.IsEnabled = true;
                    });
                });
            }
            else
            {
                MessageBox.Show("内容不能为空", "发布失败", MessageBoxButton.OK);
            }
        }

        
    }
}