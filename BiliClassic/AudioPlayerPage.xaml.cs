using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using System.Windows.Threading;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class AudioPlayerPage : PhoneApplicationPage
    {
        private DispatcherTimer _timer;
        private bool _updatingSlider;
        private bool _opened;

        public AudioPlayerPage()
        {
            InitializeComponent();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            ThemeHelper.ApplyPage(this);

            string value;
            string src = "";
            if (NavigationContext.QueryString.TryGetValue("src", out value))
            {
                src = value;
            }
            if (NavigationContext.QueryString.TryGetValue("text", out value) && value.Length > 0)
            {
                TitleText.Text = value;
            }
            if (NavigationContext.QueryString.TryGetValue("dis", out value))
            {
                DisText.Text = value;
            }

            Uri uri;
            if (!Uri.TryCreate(src, UriKind.Absolute, out uri))
            {
                StatusText.Text = "音频地址无效";
                PlayButton.IsEnabled = false;
                return;
            }

            StatusText.Text = "正在加载…";
            Audio.Source = uri;
            Audio.Play();

            _timer = new DispatcherTimer();
            _timer.Interval = TimeSpan.FromMilliseconds(500);
            _timer.Tick += Timer_Tick;
            _timer.Start();
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);

            if (_timer != null)
            {
                _timer.Stop();
                _timer = null;
            }

            Audio.Stop();
            Audio.Source = null;
        }

        private void Timer_Tick(object sender, EventArgs e)
        {
            if (!_opened || !Audio.NaturalDuration.HasTimeSpan)
            {
                return;
            }

            _updatingSlider = true;
            SeekBar.Maximum = Audio.NaturalDuration.TimeSpan.TotalSeconds;
            SeekBar.Value = Audio.Position.TotalSeconds;
            _updatingSlider = false;
            TimeText.Text = Format(Audio.Position) + " / " + Format(Audio.NaturalDuration.TimeSpan);
        }

        private static string Format(TimeSpan span)
        {
            return ((int)span.TotalMinutes) + ":" + span.Seconds.ToString("00");
        }

        private void Audio_MediaOpened(object sender, RoutedEventArgs e)
        {
            _opened = true;
            StatusText.Text = "";
            PlayButton.Content = "暂停";
        }

        private void Audio_MediaEnded(object sender, RoutedEventArgs e)
        {
            PlayButton.Content = "播放";
        }

        private void Audio_MediaFailed(object sender, ExceptionRoutedEventArgs e)
        {
            _opened = false;
            StatusText.Text = "播放失败："
                + (e.ErrorException == null ? "" : e.ErrorException.Message);
        }

        private void Audio_CurrentStateChanged(object sender, RoutedEventArgs e)
        {
            PlayButton.Content =
                Audio.CurrentState == System.Windows.Media.MediaElementState.Playing
                    ? "暂停" : "播放";
        }

        private void PlayButton_Click(object sender, RoutedEventArgs e)
        {
            if (Audio.CurrentState == System.Windows.Media.MediaElementState.Playing)
            {
                Audio.Pause();
            }
            else
            {
                Audio.Play();
            }
        }

        private void SeekBar_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_updatingSlider || !_opened)
            {
                return;
            }

            try
            {
                Audio.Position = TimeSpan.FromSeconds(SeekBar.Value);
            }
            catch (Exception)
            {
            }
        }
    }
}
