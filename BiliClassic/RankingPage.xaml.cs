using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class RankingPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items =
            new ObservableCollection<VideoItem>();

        private bool _loading;
        private bool _loaded;

        public RankingPage()
        {
            InitializeComponent();
            RankList.ItemsSource = _items;
            ThemeHelper.ApplyPage(this);
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            if (!_loaded)
            {
                _loaded = true;
                Load();
            }
        }

        private void Load()
        {
            if (_loading)
            {
                return;
            }
            _loading = true;

            if (_items.Count == 0)
            {
                ShowStatus("正在加载排行榜…");
            }

            RankingService.Fetch(delegate(List<VideoItem> items, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;

                    if (items != null && items.Count > 0)
                    {
                        _items.Clear();
                        for (int i = 0; i < items.Count; i++)
                        {
                            _items.Add(items[i]);
                            CoverLoader.Request(items[i]);
                        }
                        StatusText.Visibility = Visibility.Collapsed;
                        EmptyText.Visibility = Visibility.Collapsed;
                        return;
                    }

                    if (_items.Count == 0)
                    {
                        ShowEmpty(string.IsNullOrEmpty(error) ? "暂无排行榜数据" : error);
                    }
                    else
                    {
                        ShowStatus(error);
                    }
                }));
            });
        }

        private void RankList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = RankList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            RankList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.Bvid))
            {
                return;
            }
            NavigationService.Navigate(new Uri(
                "/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }

        private void RefreshMenuItem_Click(object sender, EventArgs e)
        {
            Load();
        }

        private void ShowEmpty(string message)
        {
            StatusText.Text = "";
            StatusText.Visibility = Visibility.Collapsed;
            EmptyText.Text = message;
            EmptyText.Visibility = Visibility.Visible;
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusText.Visibility = string.IsNullOrEmpty(message)
                ? Visibility.Collapsed
                : Visibility.Visible;
        }
    }
}
