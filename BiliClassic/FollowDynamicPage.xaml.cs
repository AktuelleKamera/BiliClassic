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
    public partial class FollowDynamicPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<UserItem> _users =
            new ObservableCollection<UserItem>();
        private readonly ObservableCollection<VideoItem> _dynamics =
            new ObservableCollection<VideoItem>();

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

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
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

            DynamicService.FetchFeed("", delegate(List<VideoItem> items, string nextOffset, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _dynamicLoading = false;
                    _dynamics.Clear();
                    AppendDynamics(items);
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
            DynamicService.FetchFeed(_dynamicOffset, delegate(List<VideoItem> items, string nextOffset, string error)
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

        private void AppendDynamics(List<VideoItem> items)
        {
            if (items == null)
            {
                return;
            }
            for (int i = 0; i < items.Count; i++)
            {
                _dynamics.Add(items[i]);
                CoverLoader.Request(items[i]);
            }
        }

        private void DynamicList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = DynamicList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            DynamicList.SelectedIndex = -1;

            if (!string.IsNullOrEmpty(item.Bvid))
            {
                NavigationService.Navigate(new Uri(
                    "/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
            }
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
