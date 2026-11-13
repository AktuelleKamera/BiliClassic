using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Tasks;

namespace BiliClassic
{
    public partial class FollowDynamicPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<UserItem> _users =
            new ObservableCollection<UserItem>();
        private readonly ObservableCollection<DynamicItem> _dynamics =
            new ObservableCollection<DynamicItem>();

        private bool _userLoading;
        private bool _userLoaded;
        private int _userPage = 1;
        private bool _userHasMore;

        private bool _dynamicLoading;
        private bool _dynamicLoaded;
        private bool _dynamicHasMore;
        private string _dynamicOffset = "";

        public FollowDynamicPage()
        {
            InitializeComponent();
            UserList.ItemsSource = _users;
            DynamicList.ItemsSource = _dynamics;
            ThemeHelper.ApplyPage(this);

            BottomAutoLoader.Attach(UserList, delegate { LoadMoreUsers(); });
            BottomAutoLoader.Attach(DynamicList, delegate { LoadMoreDynamics(); });
        }
        App app = Application.Current as App;

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            app.Is_DY_Reading = true;
            Api.DynamicsMessegeTemp.DYN = 0;

            if (FollowPivot.SelectedIndex == 0)
            {
                if (!_userLoaded)
                {
                    LoadUsers(1);
                }
            }
            else if (!_dynamicLoaded)
            {
                LoadDynamics();
            }
        }

        private void FollowPivot_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (FollowPivot.SelectedIndex == 0)
            {
                if (!_userLoaded)
                {
                    LoadUsers(1);
                }
            }
            else if (!_dynamicLoaded)
            {
                LoadDynamics();
            }
        }


        private void LoadUsers(int page)
        {
            if (_userLoading)
            {
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                UserEmptyText.Text = "请先登录";
                UserEmptyText.Visibility = Visibility.Visible;
                return;
            }

            long mid;
            if (!long.TryParse(BiliSession.Mid, out mid) || mid <= 0)
            {
                UserEmptyText.Text = "请先登录";
                UserEmptyText.Visibility = Visibility.Visible;
                return;
            }

            _userLoading = true;
            _userLoaded = true;
            _userPage = page;
            if (page <= 1)
            {
                _users.Clear();
            }
            if (_users.Count == 0)
            {
                ShowStatus("正在加载…");
            }

            MineService.FetchFollowing(mid, page, delegate(List<UserItem> users, bool hasMore, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _userLoading = false;
                    int added = 0;
                    if (users != null)
                    {
                        for (int i = 0; i < users.Count; i++)
                        {
                            _users.Add(users[i]);
                            AvatarLoader.Request(users[i]);
                            added++;
                        }
                    }
                    _userHasMore = hasMore && added > 0;

                    if (!string.IsNullOrEmpty(error) && _users.Count == 0)
                    {
                        ShowStatus(error);
                    }
                    else if (_users.Count == 0)
                    {
                        ShowStatus("");
                        UserEmptyText.Text = "还没有关注的人";
                        UserEmptyText.Visibility = Visibility.Visible;
                    }
                    else
                    {
                        UserEmptyText.Visibility = Visibility.Collapsed;
                        StatusText.Visibility = Visibility.Collapsed;
                    }
                }));
            });
        }

        private void LoadMoreUsers()
        {
            if (_userLoading || !_userHasMore)
            {
                return;
            }
            LoadUsers(_userPage + 1);
        }

        private void UserList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            UserItem user = UserList.SelectedItem as UserItem;
            if (user == null)
            {
                return;
            }
            UserList.SelectedIndex = -1;

            long mid;
            if (long.TryParse(user.Mid, out mid) && mid > 0)
            {
                NavigationService.Navigate(new Uri(
                    "/UserProfilePage.xaml?mid=" + mid, UriKind.Relative));
            }
        }


        private void LoadDynamics()
        {
            if (_dynamicLoading)
            {
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                DynamicEmptyText.Text = "请先登录";
                DynamicEmptyText.Visibility = Visibility.Visible;
                return;
            }

            _dynamicLoading = true;
            _dynamicLoaded = true;
            _dynamicOffset = "";
            DynamicEmptyText.Visibility = Visibility.Collapsed;
            if (_dynamics.Count == 0)
            {
                ShowStatus("正在加载动态…");
            }

            DynamicService.FetchFeed("", delegate(List<DynamicItem> items, string nextOffset, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _dynamicLoading = false;
                    _dynamics.Clear();
                    AppendDynamics(items);
                    if (_dynamics.Count > 0)
                    {
                        Api.DynamicsMessegeTemp.DYI = _dynamics[0].DynamicId;
                    }
                    _dynamicOffset = nextOffset ?? "";
                    _dynamicHasMore = _dynamicOffset.Length > 0;

                    if (!string.IsNullOrEmpty(error) && _dynamics.Count == 0)
                    {
                        ShowStatus(error);
                    }
                    else if (_dynamics.Count == 0)
                    {
                        ShowStatus("");
                        DynamicEmptyText.Text = "还没有动态";
                        DynamicEmptyText.Visibility = Visibility.Visible;
                    }
                    else
                    {
                        DynamicEmptyText.Visibility = Visibility.Collapsed;
                        StatusText.Visibility = Visibility.Collapsed;
                    }
                }));
            });
        }

        private void LoadMoreDynamics()
        {
            if (_dynamicLoading || !_dynamicHasMore || _dynamicOffset.Length == 0)
            {
                return;
            }

            _dynamicLoading = true;
            DynamicService.FetchFeed(_dynamicOffset, delegate(List<DynamicItem> items, string nextOffset, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _dynamicLoading = false;
                    AppendDynamics(items);
                    _dynamicOffset = nextOffset ?? "";
                    _dynamicHasMore = _dynamicOffset.Length > 0;

                    if (!string.IsNullOrEmpty(error))
                    {
                        ShowStatus(error);
                    }
                }));
            });
        }

        private void AppendDynamics(List<DynamicItem> items)
        {
            if (items == null)
            {
                return;
            }
            for (int i = 0; i < items.Count; i++)
            {
                _dynamics.Add(items[i]);
                RequestImages(items[i]);
            }
        }

        private void RequestImages(DynamicItem item)
        {
            if (item == null)
            {
                return;
            }

            if (!string.IsNullOrEmpty(item.AvatarUrl))
            {
                ImageLoader.Load(item.AvatarUrl,
                    delegate(BitmapImage bitmap) { item.Avatar = bitmap; });
            }

            for (int i = 0; i < item.Pics.Count; i++)
            {
                PictureItem picture = item.Pics[i];
                ImageLoader.Load(VideoItem.BuildThumbUrl(picture.Url, 240, 240),
                    delegate(BitmapImage bitmap) { picture.Image = bitmap; });
            }

            if (!string.IsNullOrEmpty(item.CardCoverUrl))
            {
                ImageLoader.Load(VideoItem.BuildThumbUrl(item.CardCoverUrl, 320, 180),
                    delegate(BitmapImage bitmap) { item.CardCover = bitmap; });
            }
        }

        private void DynamicList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            DynamicItem item = DynamicList.SelectedItem as DynamicItem;
            if (item == null)
            {
                return;
            }
            DynamicList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.DynamicId))
            {
                return;
            }
            NavigationService.Navigate(new Uri(
                "/DynamicDetailPage.xaml?id=" + item.DynamicId, UriKind.Relative));
        }

        private void DynamicPicture_Click(object sender, RoutedEventArgs e)
        {
            FrameworkElement element = sender as FrameworkElement;
            PictureItem picture = element == null ? null : element.DataContext as PictureItem;
            if (picture == null || picture.Group == null || picture.Group.Count == 0)
            {
                return;
            }
            ImageViewerPage.Show(picture.Group, picture.Group.IndexOf(picture));
        }

        private void Author_Click(object sender, RoutedEventArgs e)
        {
            FrameworkElement element = sender as FrameworkElement;
            if (element == null)
            {
                return;
            }

            string mid = element.Tag as string;
            if (string.IsNullOrEmpty(mid))
            {
                return;
            }

            NavigationService.Navigate(new Uri(
                "/UserProfilePage.xaml?mid=" + mid, UriKind.Relative));
        }

        private void OpenDynamic(DynamicItem item)
        {
            DynamicItem card = item;
            if (string.IsNullOrEmpty(card.CardBvid)
                && string.IsNullOrEmpty(card.CardAid)
                && card.Forward != null)
            {
                card = card.Forward;
            }

            if (!string.IsNullOrEmpty(card.CardBvid))
            {
                NavigationService.Navigate(new Uri(
                    "/VideoDetailPage.xaml?bvid=" + card.CardBvid, UriKind.Relative));
                return;
            }
            if (!string.IsNullOrEmpty(card.CardAid))
            {
                OpenWeb("https://www.bilibili.com/video/av" + card.CardAid);
                return;
            }
            if (!string.IsNullOrEmpty(card.CardEpid))
            {
                NavigationService.Navigate(new Uri(
                    "/BangumiDetailPage.xaml?ep=" + card.CardEpid, UriKind.Relative));
                return;
            }
            if (!string.IsNullOrEmpty(card.CardRoomId))
            {
                OpenWeb("https://live.bilibili.com/" + card.CardRoomId);
                return;
            }
            if (!string.IsNullOrEmpty(card.CardArticleId))
            {
                NavigationService.Navigate(new Uri(
                    "/ArticlePage.xaml?id=" + card.CardArticleId, UriKind.Relative));
            }
        }

        private void OpenWeb(string url)
        {
            WebBrowserTask task = new WebBrowserTask();
#if WP8
            task.Uri = new Uri(url);
#else
            task.URL = url;
#endif
            task.Show();
        }


        private void RefreshMenuItem_Click(object sender, EventArgs e)
        {
            if (FollowPivot.SelectedIndex == 0)
            {
                _userLoaded = false;
                LoadUsers(1);
            }
            else
            {
                _dynamicLoaded = false;
                LoadDynamics();
            }
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
