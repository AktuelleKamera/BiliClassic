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
    public partial class OfflinePage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items =
            new ObservableCollection<VideoItem>();

        public OfflinePage()
        {
            InitializeComponent();
            OfflineList.ItemsSource = _items;
            ThemeHelper.ApplyPage(this);
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            Load();
        }

        private void Load()
        {
            _items.Clear();

            List<OfflineEntry> entries = OfflineService.All();
            long total = 0;
            for (int i = 0; i < entries.Count; i++)
            {
                OfflineEntry entry = entries[i];
                total += entry.Size;

                VideoItem item = new VideoItem();
                item.Bvid = entry.Bvid;
                item.Aid = entry.Aid;
                item.Cid = entry.Cid;
                item.Title = string.IsNullOrEmpty(entry.Title) ? entry.Bvid : entry.Title;
                item.Pic = entry.Pic;
                item.Author = entry.Author;
                item.Subtitle = BuildSubtitle(entry);
                _items.Add(item);

                CoverLoader.Request(item);
            }

            if (_items.Count == 0)
            {
                ShowEmpty("还没有缓存的视频");
            }
            else
            {
                EmptyText.Visibility = Visibility.Collapsed;
                ShowStatus("共 " + _items.Count + " 个，占用 " + MediaCache.FormatSize(total));
            }
        }

        private static string BuildSubtitle(OfflineEntry entry)
        {
            string text = MediaCache.FormatSize(entry.Size);
            if (!string.IsNullOrEmpty(entry.PartTitle))
            {
                string page = string.IsNullOrEmpty(entry.Page) ? "" : "P" + entry.Page + " ";
                text += "  " + page + entry.PartTitle;
            }
            return text;
        }

        private void OfflineList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = OfflineList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            OfflineList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.Cid))
            {
                if (!string.IsNullOrEmpty(item.Bvid))
                {
                    NavigationService.Navigate(new Uri(
                        "/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
                }
                return;
            }

            string url = "/VideoPlayerPage.xaml?offline=1"
                + "&bvid=" + (item.Bvid ?? "")
                + "&aid=" + (item.Aid ?? "")
                + "&cid=" + item.Cid;
            NavigationService.Navigate(new Uri(url, UriKind.Relative));
        }

        private void DeleteButton_Click(object sender, RoutedEventArgs e)
        {
            Button button = sender as Button;
            VideoItem item = button == null ? null : button.DataContext as VideoItem;
            if (item == null || string.IsNullOrEmpty(item.Cid))
            {
                return;
            }

            MessageBoxResult answer = MessageBox.Show(
                "要删除这个本地视频吗？", "本地视频", MessageBoxButton.OKCancel);
            if (answer != MessageBoxResult.OK)
            {
                return;
            }

            OfflineEntry entry = new OfflineEntry();
            entry.Bvid = item.Bvid ?? "";
            entry.Cid = item.Cid ?? "";
            entry.Aid = item.Aid ?? "";
            string id = string.IsNullOrEmpty(entry.Bvid) ? entry.Aid : entry.Bvid;
            entry.FileName = MediaCache.FileNameFor(id, entry.Cid);

            OfflineService.Delete(entry);

            _items.Remove(item);
            if (_items.Count == 0)
            {
                ShowEmpty("还没有缓存的视频");
            }
            else
            {
                ShowStatus("已删除");
            }
        }

        private void RefreshMenuItem_Click(object sender, EventArgs e)
        {
            Load();
        }

        private void ClearMenuItem_Click(object sender, EventArgs e)
        {
            if (_items.Count == 0)
            {
                ShowStatus("没有可清理的缓存");
                return;
            }

            MessageBoxResult answer = MessageBox.Show(
                "要清空全部本地视频吗？", "本地视频", MessageBoxButton.OKCancel);
            if (answer != MessageBoxResult.OK)
            {
                return;
            }

            long freed = OfflineService.Clear();
            ShowEmpty("已清空，释放 " + MediaCache.FormatSize(freed));
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
