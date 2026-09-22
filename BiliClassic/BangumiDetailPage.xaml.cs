using System;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class BangumiDetailPage : PhoneApplicationPage
    {
        public sealed class EpisodeRow
        {
            public BangumiEpisode Episode { get; set; }
            public string Number { get; set; }
            public string Title { get; set; }
            public string InfoLine { get; set; }
        }

        private readonly ObservableCollection<EpisodeRow> _rows =
            new ObservableCollection<EpisodeRow>();

        private long _seasonId;
        private int _seasonType = 1;
        private bool _loaded;

        public BangumiDetailPage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
            EpisodeList.ItemsSource = _rows;
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            string value;
            if (_seasonId <= 0 && NavigationContext.QueryString.TryGetValue("season", out value))
            {
                long.TryParse(value, out _seasonId);
            }

            long epId = 0;
            if (NavigationContext.QueryString.TryGetValue("ep", out value))
            {
                long.TryParse(value, out epId);
            }

            if (_loaded)
            {
                return;
            }
            _loaded = true;

            if (_seasonId <= 0 && epId <= 0)
            {
                StatusText.Text = "番剧ID为空";
                return;
            }

            StatusText.Text = "正在加载番剧…";
            if (_seasonId > 0)
            {
                BangumiService.FetchSeason(_seasonId, delegate(BangumiInfo info)
                {
                    Dispatcher.BeginInvoke(new Action(delegate { Apply(info); }));
                });
            }
            else
            {
                BangumiService.FetchSeasonByEp(epId, delegate(BangumiInfo info)
                {
                    Dispatcher.BeginInvoke(new Action(delegate { Apply(info); }));
                });
            }
        }

        private void Apply(BangumiInfo info)
        {
            if (!string.IsNullOrEmpty(info.Error))
            {
                StatusText.Text = info.Error;
                return;
            }

            PageTitle.Text = info.Title;
            _seasonType = info.SeasonType > 0 ? info.SeasonType : 1;
            ScoreText.Text = info.Score > 0 ? info.Score.ToString("0.0") + " 分" : "";
            AreaText.Text = info.Areas;
            EvaluateText.Text = info.Evaluate;

            if (!string.IsNullOrEmpty(info.Cover))
            {
                VideoItem cover = new VideoItem();
                cover.IsBangumi = true;
                cover.Pic = info.Cover;
                cover.PropertyChanged += delegate { CoverImage.Source = cover.Cover; };
                CoverLoader.Request(cover);
            }

            _rows.Clear();
            for (int i = 0; i < info.Episodes.Count; i++)
            {
                BangumiEpisode ep = info.Episodes[i];
                EpisodeRow row = new EpisodeRow();
                row.Episode = ep;
                row.Number = (i + 1).ToString();
                row.Title = ep.LongTitle.Length > 0 ? ep.LongTitle : ep.Title;
                row.InfoLine = ep.LongTitle.Length > 0 ? ep.Title : "";
                if (ep.Badge.Length > 0)
                {
                    row.InfoLine = row.InfoLine.Length > 0 ? row.InfoLine + "  " + ep.Badge : ep.Badge;
                }
                _rows.Add(row);
            }

            StatusText.Visibility = _rows.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
            if (_rows.Count == 0)
            {
                StatusText.Text = "没有可播放的分集";
            }
        }

        private void EpisodeList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            EpisodeRow row = EpisodeList.SelectedItem as EpisodeRow;
            if (row == null)
            {
                return;
            }
            EpisodeList.SelectedIndex = -1;

            BangumiEpisode ep = row.Episode;
            if (ep.Aid.Length == 0 || ep.Cid.Length == 0)
            {
                return;
            }

            string uri = "/VideoPlayerPage.xaml"
                + "?aid=" + ep.Aid
                + "&cid=" + ep.Cid
                + "&season=" + _seasonType
                + "&bvid=" + Uri.EscapeDataString(ep.Bvid)
                + "&title=" + Uri.EscapeDataString(row.Title);
            NavigationService.Navigate(new Uri(uri, UriKind.Relative));
        }
    }
}
