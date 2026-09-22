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
    public partial class UserProfilePage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items =
            new ObservableCollection<VideoItem>();

        private string _mid = "";
        private int _page = 1;
        private bool _hasMore;
        private bool _loading;
        private bool _loaded;

        public UserProfilePage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
            VideoList.ItemsSource = _items;

            BottomAutoLoader.Attach(VideoList, delegate
            {
                LoadMore();
            });
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            string value;
            if (NavigationContext.QueryString.TryGetValue("mid", out value))
            {
                _mid = value;
            }

            if (_loaded)
            {
                return;
            }
            _loaded = true;

            LoadCard();
            LoadVideos(1);
        }

        private void LoadCard()
        {
            if (string.IsNullOrEmpty(_mid))
            {
                StatusText.Text = "没有收到 mid";
                return;
            }

            UserSpaceService.FetchCard(_mid, delegate(UserSpaceCard card, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!string.IsNullOrEmpty(error) || card.Name.Length == 0)
                    {
                        NameText.Text = "加载失败";
                        return;
                    }

                    NameText.Text = card.Name;
                    LevelText.Text = "Lv" + card.Level
                        + "   粉丝 " + VideoItem.FormatCount(card.Fans.ToString())
                        + "   关注 " + VideoItem.FormatCount(card.Following.ToString());
                    SignText.Text = card.Sign;

                    if (!string.IsNullOrEmpty(card.Face))
                    {
                        UserItem self = new UserItem();
                        self.AvatarUrl = card.Face;
                        self.PropertyChanged += delegate
                        {
                            AvatarImage.Source = self.Avatar;
                        };
                        AvatarLoader.Request(self);
                    }
                }));
            });
        }

        private void LoadMore()
        {
            if (_loading || !_hasMore)
            {
                return;
            }
            LoadVideos(_page + 1);
        }

        private void LoadVideos(int page)
        {
            if (_loading || string.IsNullOrEmpty(_mid))
            {
                return;
            }

            _loading = true;
            _page = page;
            StatusText.Visibility = Visibility.Visible;

            if (page <= 1)
            {
                _items.Clear();
            }

            if (_items.Count == 0)
            {
                StatusText.Text = "正在加载…";
            }

            UserSpaceService.FetchVideos(_mid, page,
                delegate(List<VideoItem> items, bool hasMore, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;

                    int added = 0;
                    if (items != null)
                    {
                        for (int i = 0; i < items.Count; i++)
                        {
                            _items.Add(items[i]);
                            CoverLoader.Request(items[i]);
                            added++;
                        }
                    }

                    _hasMore = hasMore && added > 0;
                    ApplyStatus(error);
                }));
            });
        }

        private void ApplyStatus(string error)
        {
            if (!string.IsNullOrEmpty(error))
            {
                StatusText.Text = error;
            }
            else if (_items.Count == 0)
            {
                StatusText.Text = "TA还没有投稿";
            }
            else
            {
                StatusText.Visibility = Visibility.Collapsed;
            }
        }

        private void VideoList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = VideoList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            VideoList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.Bvid))
            {
                return;
            }
            NavigationService.Navigate(new Uri("/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }
    }
}
