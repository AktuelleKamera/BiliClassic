using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Threading;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    /// <summary>
    /// 登录页：扫码登录 + 手动Cookie登录
    ///
    /// 取码->渲染->每秒轮询->成功写BiliSession并验证
    /// 手动Cookie是验证CookieContainer链路的最短路径
    /// Cookie是受限头，只能靠容器发出去
    /// </summary>
    public partial class LoginPage : PhoneApplicationPage
    {
        // 每模块7像素，(45+8)*7=371，480宽放得下
        private const int QrScale = 7;

        /// <summary>Pivot下标：0验证码 1扫码 2信息</summary>
        private const int QrPivotIndex = 1;
        private const int InfoPivotIndex = 2;

        private DispatcherTimer _timer;

        /// <summary>发短信得到的captcha_key，登录时带回</summary>
        private string _captchaKey = "";

        /// <summary>二维码是否已取，切到扫码页才取，避免白请求</summary>
        private bool _qrLoaded;

        public LoginPage()
        {
            InitializeComponent();
            Loaded += OnLoaded;
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            // 转场动画交给Toolkit的TransitionFrame
            // 不再手动播动画
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            StopPolling();
            base.OnNavigatedFrom(e);
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            ShowAccount();
            ShowStatus(BiliSession.IsLoggedIn ? "就绪（已登录）" : "就绪");
        }

        /// <summary>
        /// 切到扫码页才取二维码，切走停轮询
        /// </summary>
        private void LoginPivot_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (LoginPivot == null)
            {
                return;
            }

            if (LoginPivot.SelectedIndex == QrPivotIndex)
            {
                if (!_qrLoaded)
                {
                    _qrLoaded = true;
                    RefreshQrCode();
                }
                else
                {
                    StartPolling();
                }
            }
            else
            {
                StopPolling();
            }

            if (LoginPivot.SelectedIndex == InfoPivotIndex)
            {
                ShowAccount();
            }
        }

        /// <summary>刷新信息页的登录状态</summary>
        private void ShowAccount()
        {
            if (AccountText == null)
            {
                return;
            }

            if (!BiliSession.IsLoggedIn)
            {
                AccountText.Text = "未登录";
                return;
            }

            if (string.IsNullOrEmpty(BiliSession.UserName))
            {
                AccountText.Text = "已保存登录态（尚未验证）";
            }
            else
            {
                AccountText.Text = "已登录：" + BiliSession.UserName
                    + "\nmid：" + BiliSession.Mid;
            }
        }

        private void LogoutButton_Click(object sender, RoutedEventArgs e)
        {
            BiliSession.Clear();
            ShowAccount();
            ShowStatus("已退出登录");
        }

        private void RefreshButton_Click(object sender, RoutedEventArgs e)
        {
            RefreshQrCode();
        }

        private void RefreshQrCode()
        {
            StopPolling();
            QrFrame.Visibility = Visibility.Collapsed;
            ShowStatus("正在获取二维码…");

            LoginService.RequestQrCode(delegate(string content, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!string.IsNullOrEmpty(error))
                    {
                        ShowStatus(error);
                        return;
                    }

                    try
                    {
                        bool[,] matrix = QrEncoder.Encode(content);
                        QrImage.Source = Render(matrix, QrScale);
                        QrFrame.Visibility = Visibility.Visible;
                        ShowStatus("请用 B站手机客户端扫码登录");
                        StartPolling();
                    }
                    catch (Exception ex)
                    {
                        ShowStatus("二维码生成失败: " + ex.Message);
                    }
                }));
            });
        }

        private void StartPolling()
        {
            if (_timer == null)
            {
                _timer = new DispatcherTimer();
                _timer.Interval = TimeSpan.FromSeconds(1);
                _timer.Tick += PollTick;
            }
            _timer.Start();
        }

        private void StopPolling()
        {
            if (_timer != null)
            {
                _timer.Stop();
            }
        }

        private void PollTick(object sender, EventArgs e)
        {
            LoginService.PollQrCode(delegate(QrPollResult result)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    switch (result.State)
                    {
                        case QrLoginState.NotScanned:
                            ShowStatus("请用 B站手机客户端扫码登录");
                            break;
                        case QrLoginState.Scanned:
                            ShowStatus("已扫码，请在手机上确认登录");
                            break;
                        case QrLoginState.Expired:
                            StopPolling();
                            ShowStatus("二维码已过期，请点“刷新二维码”");
                            break;
                        case QrLoginState.Success:
                            StopPolling();
                            ShowStatus("登录成功，正在验证…");
                            VerifySession();
                            break;
                        default:
                            if (!string.IsNullOrEmpty(result.Error))
                            {
                                ShowStatus(result.Error);
                            }
                            break;
                    }
                }));
            });
        }

        private void CookieButton_Click(object sender, RoutedEventArgs e)
        {
            string text = (CookieBox.Text ?? "").Trim();
            if (text.Length == 0)
            {
                ShowStatus("请先粘贴 Cookie");
                return;
            }

            LoginService.LoginWithCookie(text);
            ShowStatus("已保存 Cookie，正在验证…");
            VerifySession();
        }

        private void VerifySession()
        {
            BiliSession.Verify(delegate(bool ok)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (ok)
                    {
                        ShowAccount();
                        ShowStatus("登录成功：" + BiliSession.UserName
                            + "（mid " + BiliSession.Mid + "）");
                    }
                    else
                    {
                        ShowAccount();
                        ShowStatus("凭证已保存，但 nav 验证没通过 —— 多半是 cookie 没真正发出去");
                    }
                }));
            });
        }

        private void SendButton_Click(object sender, RoutedEventArgs e)
        {
            string tel = (TelBox.Text ?? "").Trim();
            if (tel.Length == 0)
            {
                ShowStatus("请输入手机号");
                return;
            }

            SendButton.IsEnabled = false;
            ShowStatus("正在发送验证码…");

            SmsLoginService.SendSms(tel, delegate(SmsSendResult result)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    SendButton.IsEnabled = true;
                    if (result.Ok)
                    {
                        // captcha_key要原样带回登录接口
                        // 它也是短信是否真发出的信号
                        _captchaKey = result.CaptchaKey;
                        ShowStatus(result.CaptchaKey.Length > 0
                            ? "验证码已发送，请查收短信"
                            : "接口返回成功，但没给 captcha_key，短信可能并未真正发出");
                    }
                    else
                    {
                        ShowStatus("发送失败 (" + result.Code + ") " + result.Message
                            + (result.RawBody.Length > 0 ? " ｜ " + result.RawBody : ""));
                    }
                }));
            });
        }

        private void SmsLoginButton_Click(object sender, RoutedEventArgs e)
        {
            string tel = (TelBox.Text ?? "").Trim();
            string code = (CodeBox.Text ?? "").Trim();
            if (tel.Length == 0 || code.Length == 0)
            {
                ShowStatus("请填写手机号和验证码");
                return;
            }

            SmsLoginButton.IsEnabled = false;
            ShowStatus("正在登录…");

            SmsLoginService.LoginBySms(tel, code, _captchaKey, delegate(SmsLoginResult result)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    SmsLoginButton.IsEnabled = true;
                    if (result.Ok)
                    {
                        ShowStatus("登录成功，正在验证…");
                        VerifySession();
                    }
                    else
                    {
                        ShowStatus("登录失败 (" + result.Code + ") " + result.Message
                            + (result.RawBody.Length > 0 ? " ｜ " + result.RawBody : ""));
                    }
                }));
            });
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
        }

        /// <summary>
        /// 模块矩阵画成位图，四周留4模块静区（QR规范）
        /// 白底黑块，直接能扫，不依赖页面背景色
        /// </summary>
        private static WriteableBitmap Render(bool[,] matrix, int scale)
        {
            const int Quiet = 4;
            int modules = QrEncoder.Size;
            int total = (modules + Quiet * 2) * scale;

            WriteableBitmap bitmap = new WriteableBitmap(total, total);
            int[] pixels = bitmap.Pixels;

            for (int y = 0; y < total; y++)
            {
                int my = y / scale - Quiet;
                for (int x = 0; x < total; x++)
                {
                    int mx = x / scale - Quiet;
                    bool dark = mx >= 0 && my >= 0 && mx < modules && my < modules && matrix[my, mx];
                    pixels[y * total + x] = dark
                        ? unchecked((int)0xFF000000)
                        : unchecked((int)0xFFFFFFFF);
                }
            }

            bitmap.Invalidate();
            return bitmap;
        }
    }
}
