using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class SearchPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items = new ObservableCollection<VideoItem>();

        private string _keyword = "";
        private int _page = 1;
        private bool _hasMore;
        private bool _loading;

        private DateTime _lastSearchAt = DateTime.MinValue;

        private long _pubtimeBeginS;

        private long _pubtimeEndS;

        private int _timeFilterIndex;

        private int _sortIndex;

        private static readonly string[] TimeFilterNames =
            new string[] { "不限", "最近一天", "最近一周", "最近一月", "最近半年", "最近一年" };

        private static readonly long[] TimeFilterSeconds =
            new long[] { 0, 86400, 7 * 86400, 30 * 86400, 182 * 86400, 365 * 86400 };

        private static readonly string[] SortNames =
            new string[] { "综合排序", "最新发布", "最多播放", "最多弹幕", "最多收藏", "最多评论" };

        private static readonly string[] SortValues =
            new string[] { "", "pubdate", "click", "dm", "stow", "scores" };

        public SearchPage()
        {
            InitializeComponent();
            ResultList.ItemsSource = _items;
            ThemeHelper.ApplyPage(this);

            BottomAutoLoader.Attach(ResultList, delegate
            {
                MoreButton_Click(null, null);
            });
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
        }

        private void SearchButton_Click(object sender, RoutedEventArgs e)
        {
            StartNewSearch();
        }

        private void KeywordBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                StartNewSearch();
            }
        }

        private void MoreButton_Click(object sender, RoutedEventArgs e)
        {
            if (_loading || !_hasMore)
            {
                return;
            }
            _page++;
            RunSearch();
        }

        private void StartNewSearch()
        {
            string keyword = (KeywordBox.Text ?? "").Trim();
            if (keyword.Length == 0)
            {
                ShowStatus("请输入关键词");
                return;
            }
            if (_loading)
            {
                return;
            }

            if (TryOpenById(keyword))
            {
                return;
            }

            if ((DateTime.Now - _lastSearchAt).TotalMilliseconds < 800)
            {
                return;
            }
            _lastSearchAt = DateTime.Now;

            _keyword = keyword;
            _page = 1;
            _hasMore = false;
            _items.Clear();
            MoreButton.Visibility = Visibility.Collapsed;
            ShowStatus("正在搜索...");
            RunSearch();
        }

        private bool TryOpenById(string keyword)
        {
            string bvid = BiliId.ToBvid(keyword);
            if (bvid.Length == 0)
            {
                return false;
            }

            NavigationService.Navigate(new Uri(
                "/VideoDetailPage.xaml?bvid=" + bvid, UriKind.Relative));
            return true;
        }

        private void RunSearch()
        {
            _loading = true;

            if (_page <= 1)
            {
                SearchButton.IsEnabled = false;
            }

            MoreButton.IsEnabled = false;
            if (_items.Count > 0)
            {
                MoreButton.Content = "正在加载...";
            }

            string keyword = _keyword;
            int page = _page;

            if (page == 1)
            {
                BangumiService.SearchBangumi(keyword, 1, delegate(List<VideoItem> bangumi, bool full, string berror)
                {
                    Dispatcher.BeginInvoke(new Action(delegate { ApplyBangumi(bangumi); }));
                });
            }

            SearchService.Search(keyword, page, SortValues[_sortIndex], _pubtimeBeginS, _pubtimeEndS,
                delegate(SearchResult result)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;
                    SearchButton.IsEnabled = true;
                    MoreButton.IsEnabled = true;
                    MoreButton.Content = "加载更多";
                    ApplyResult(result);
                }));
            });
        }

        private void ApplyResult(SearchResult result)
        {
            int added = 0;
            for (int i = 0; i < result.Items.Count; i++)
            {
                VideoItem item = result.Items[i];
                if (ContainsBvid(item.Bvid))
                {
                    continue;
                }
                _items.Add(item);
                added++;
            }

            _hasMore = result.FullPage && added > 0;

            for (int i = 0; i < result.Items.Count; i++)
            {
                CoverLoader.Request(result.Items[i]);
            }

            if (!string.IsNullOrEmpty(result.Error))
            {
                ShowStatus(result.Error);
            }
            else if (_items.Count == 0)
            {
                ShowStatus("未找到相关视频");
            }
            else
            {
                StatusText.Visibility = Visibility.Collapsed;
            }

            MoreButton.Visibility = Visibility.Collapsed;
        }

        private void ApplyBangumi(List<VideoItem> bangumi)
        {
            if (bangumi == null)
            {
                return;
            }
            for (int i = 0; i < bangumi.Count; i++)
            {
                VideoItem item = bangumi[i];
                if (ContainsSeason(item.SeasonId))
                {
                    continue;
                }
                _items.Add(item);
                CoverLoader.Request(item);
            }
            if (_items.Count > 0)
            {
                StatusText.Visibility = Visibility.Collapsed;
            }
        }

        private bool ContainsSeason(long seasonId)
        {
            for (int i = 0; i < _items.Count; i++)
            {
                if (_items[i].SeasonId == seasonId)
                {
                    return true;
                }
            }
            return false;
        }

        private bool ContainsBvid(string bvid)
        {
            for (int i = 0; i < _items.Count; i++)
            {
                if (_items[i].Bvid == bvid)
                {
                    return true;
                }
            }
            return false;
        }

        private void ResultList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = ResultList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            ResultList.SelectedIndex = -1;

            if (item.IsBangumi && item.SeasonId > 0)
            {
                NavigationService.Navigate(new Uri(
                    "/BangumiDetailPage.xaml?season=" + item.SeasonId, UriKind.Relative));
                return;
            }

            if (string.IsNullOrEmpty(item.Bvid))
            {
                return;
            }
            NavigationService.Navigate(new Uri("/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }

        private void PrevPage_Click(object sender, EventArgs e)
        {
            LoadPage(_page - 1);
        }

        private void NextPage_Click(object sender, EventArgs e)
        {
            LoadPage(_page + 1);
        }

        private void LoadPage(int page)
        {
            if (_loading || _keyword.Length == 0)
            {
                return;
            }

            _page = page < 1 ? 1 : page;
            _hasMore = false;
            _items.Clear();
            MoreButton.Visibility = Visibility.Collapsed;
            ShowStatus("正在搜索第 " + _page + " 页…");
            RunSearch();
        }

        private void MoreMenuItem_Click(object sender, EventArgs e)
        {
            List<string> sorts = new List<string>();
            for (int i = 0; i < SortNames.Length; i++)
            {
                sorts.Add((i == _sortIndex ? "● " : "    ") + SortNames[i]);
            }
            SortList.ItemsSource = sorts;

            List<string> times = new List<string>();
            for (int i = 0; i < TimeFilterNames.Length; i++)
            {
                times.Add((i == _timeFilterIndex ? "● " : "    ") + TimeFilterNames[i]);
            }
            FilterList.ItemsSource = times;

            FilterOverlay.Visibility = Visibility.Visible;
        }

        private void SortList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            int index = SortList.SelectedIndex;
            if (index < 0)
            {
                return;
            }
            SortList.SelectedIndex = -1;
            FilterOverlay.Visibility = Visibility.Collapsed;

            _sortIndex = index;
            ShowStatus("排序：" + SortNames[index]);

            if (_keyword.Length > 0)
            {
                _page = 1;
                _hasMore = false;
                _items.Clear();
                RunSearch();
            }
        }

        private void FilterList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            int index = FilterList.SelectedIndex;
            if (index < 0)
            {
                return;
            }
            FilterList.SelectedIndex = -1;
            FilterOverlay.Visibility = Visibility.Collapsed;

            _timeFilterIndex = index;
            long seconds = TimeFilterSeconds[index];
            _pubtimeBeginS = seconds > 0 ? NowSeconds() - seconds : 0;
            _pubtimeEndS = 0;

            ShowStatus("起始时间：" + TimeFilterNames[index]);

            if (_keyword.Length > 0)
            {
                _page = 1;
                _hasMore = false;
                _items.Clear();
                RunSearch();
            }
        }

        private void CancelFilterButton_Click(object sender, RoutedEventArgs e)
        {
            FilterOverlay.Visibility = Visibility.Collapsed;
        }


        private void DateRangeButton_Click(object sender, RoutedEventArgs e)
        {
            FilterOverlay.Visibility = Visibility.Collapsed;

            DateTime start = new DateTime(2009, 6, 26);
            DateTime end = DateTime.Now.Date;
            if (_pubtimeBeginS > 0)
            {
                start = SecondsToDate(_pubtimeBeginS);
            }
            if (_pubtimeEndS > 0)
            {
                end = SecondsToDate(_pubtimeEndS);
            }

            StartDatePicker.Value = start;
            EndDatePicker.Value = end;
            DateRangeOverlay.Visibility = Visibility.Visible;
        }

        private void OkDateButton_Click(object sender, RoutedEventArgs e)
        {
            long begin = DateToSeconds(StartDatePicker.Value);
            long end = DateToSeconds(EndDatePicker.Value) + 86399;
            if (begin > end)
            {
                long t = begin;
                begin = end - 86399;
                end = t + 86399;
            }

            _pubtimeBeginS = begin;
            _pubtimeEndS = end;
            _timeFilterIndex = -1;

            DateRangeOverlay.Visibility = Visibility.Collapsed;
            ShowStatus("起始时间：自定义");
            ResearchWithFilter();
        }

        private void ClearDateButton_Click(object sender, RoutedEventArgs e)
        {
            _pubtimeBeginS = 0;
            _pubtimeEndS = 0;
            _timeFilterIndex = 0;

            DateRangeOverlay.Visibility = Visibility.Collapsed;
            ShowStatus("起始时间：不限");
            ResearchWithFilter();
        }

        private void CancelDateButton_Click(object sender, RoutedEventArgs e)
        {
            DateRangeOverlay.Visibility = Visibility.Collapsed;
        }

        private void ResearchWithFilter()
        {
            if (_keyword.Length == 0)
            {
                return;
            }
            _page = 1;
            _hasMore = false;
            _items.Clear();
            RunSearch();
        }

        private static long DateToSeconds(DateTime? value)
        {
            if (!value.HasValue)
            {
                return 0;
            }
            DateTime local = value.Value.Date;
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return (long)(local.ToUniversalTime() - epoch).TotalSeconds;
        }

        private static DateTime SecondsToDate(long seconds)
        {
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return epoch.AddSeconds(seconds).ToLocalTime().Date;
        }

        private static long NowSeconds()
        {
            return (long)(DateTime.UtcNow
                - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusText.Visibility = Visibility.Visible;
        }
    }
}
