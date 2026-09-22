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
    public partial class WatchLaterPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items =
            new ObservableCollection<VideoItem>();

        private bool _loading;
        private bool _loaded;

        public WatchLaterPage()
        {
            InitializeComponent();
            WatchList.ItemsSource = _items;
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

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);
            _loaded = false;
        }

        private void Load()
        {
            if (_loading)
            {
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                ShowEmpty("请先登录");
                return;
            }

            _loading = true;
            ShowStatus("正在加载…");

            WatchLaterService.Fetch(delegate(List<VideoItem> items, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;

                    _items.Clear();
                    for (int i = 0; i < items.Count; i++)
                    {
                        _items.Add(items[i]);
                        CoverLoader.Request(items[i]);
                    }

                    if (!string.IsNullOrEmpty(error))
                    {
                        ShowStatus(error);
                    }
                    else if (_items.Count == 0)
                    {
                        ShowEmpty("还没有稍后再看");
                    }
                    else
                    {
                        StatusText.Visibility = Visibility.Collapsed;
                    }
                }));
            });
        }

        private void WatchList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = WatchList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            WatchList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.Bvid))
            {
                return;
            }
            NavigationService.Navigate(new Uri(
                "/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }

        private void DeleteButton_Click(object sender, RoutedEventArgs e)
        {
            Button button = sender as Button;
            VideoItem item = button == null ? null : button.DataContext as VideoItem;
            if (item == null || string.IsNullOrEmpty(item.Aid))
            {
                return;
            }

            MessageBoxResult answer = MessageBox.Show(
                "要把这个视频移出稍后再看吗？", "稍后再看", MessageBoxButton.OKCancel);
            if (answer != MessageBoxResult.OK)
            {
                return;
            }

            ShowStatus("正在移出…");
            WatchLaterService.Delete(item.Aid, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!ok)
                    {
                        ShowStatus(error);
                        return;
                    }

                    _items.Remove(item);
                    if (_items.Count == 0)
                    {
                        ShowEmpty("还没有稍后再看");
                    }
                    else
                    {
                        ShowStatus("已移出");
                    }
                }));
            });
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
            EmptyText.Visibility = Visibility.Collapsed;
            StatusText.Text = message;
            StatusText.Visibility = string.IsNullOrEmpty(message)
                ? Visibility.Collapsed
                : Visibility.Visible;
        }
    }
}
