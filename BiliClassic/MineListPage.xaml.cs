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
    /// <summary>
    /// 「我的」下的列表，一个页面四种模式：
    ///
    /// follow     关注的人，UserItem + UserCard
    /// history    播放历史，游标分页
    /// fav        收藏夹列表
    /// favvideos  收藏夹内视频，额外带fid
    ///
    /// 后三种是视频，共用VideoItem + VideoCard
    /// 关注的是用户，模型/加载器/模板独立
    /// </summary>
    public partial class MineListPage : PhoneApplicationPage
    {
        private enum Mode
        {
            Following,
            History,
            Folders,
            FolderVideos
        }

        private readonly ObservableCollection<VideoItem> _videos =
            new ObservableCollection<VideoItem>();
        private readonly ObservableCollection<UserItem> _users =
            new ObservableCollection<UserItem>();

        private Mode _mode = Mode.Following;
        private long _mid;
        private long _fid;
        private int _page = 1;
        private bool _hasMore;
        private bool _loading;
        private bool _loaded;

        public MineListPage()
        {
            InitializeComponent();

            // 滚到底部自动翻页
            // 回调可能连发，靠_loading挡重入
            BottomAutoLoader.Attach(ResultList, delegate
            {
                MoreButton_Click(null, null);
            });
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            if (_loaded)
            {
                return;
            }
            _loaded = true;

            string value;
            if (NavigationContext.QueryString.TryGetValue("mode", out value))
            {
                _mode = ParseMode(value);
            }
            if (NavigationContext.QueryString.TryGetValue("fid", out value))
            {
                long.TryParse(value, out _fid);
            }

            PageTitle.Text = TitleFor(_mode);

            // 关注的是用户，其余是视频，集合模板都换
            if (_mode == Mode.Following)
            {
                ResultList.ItemsSource = _users;
                ResultList.ItemTemplate = Resources["UserCard"] as DataTemplate;
            }
            else
            {
                ResultList.ItemsSource = _videos;
                ResultList.ItemTemplate = Resources["VideoCard"] as DataTemplate;
            }

            // mid从登录态取，接口都要求它
            _mid = ParseLong(BiliSession.Mid);
            if (_mid > 0)
            {
                LoadPage(1);
                return;
            }

            // 没有mid就先让nav补一次，否则收藏那几个接口全是未登录
            ShowStatus("正在确认登录信息…");
            BiliSession.Verify(delegate(bool ok)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _mid = ParseLong(BiliSession.Mid);
                    LoadPage(1);
                }));
            });
        }

        private static Mode ParseMode(string value)
        {
            if (value == "history")
            {
                return Mode.History;
            }
            if (value == "fav")
            {
                return Mode.Folders;
            }
            if (value == "favvideos")
            {
                return Mode.FolderVideos;
            }
            return Mode.Following;
        }

        private static string TitleFor(Mode mode)
        {
            switch (mode)
            {
                case Mode.History:
                    return "播放历史";
                case Mode.Folders:
                    return "我的收藏";
                case Mode.FolderVideos:
                    return "收藏夹";
                default:
                    return "关注的人";
            }
        }

        /// <summary>当前列表条数，两种集合分开数</summary>
        private int CurrentCount
        {
            get { return _mode == Mode.Following ? _users.Count : _videos.Count; }
        }

        private void LoadPage(int page)
        {
            if (_loading)
            {
                return;
            }
            _loading = true;
            _page = page;

            if (page <= 1)
            {
                _videos.Clear();
                _users.Clear();
            }

            if (CurrentCount == 0)
            {
                ShowStatus("正在加载…");
            }

            switch (_mode)
            {
                case Mode.History:
                    MineService.FetchHistory(page, OnVideosLoaded);
                    break;

                case Mode.Folders:
                    MineService.FetchFavoriteFolders(_mid, OnFoldersLoaded);
                    break;

                case Mode.FolderVideos:
                    MineService.FetchFolderVideos(_mid, _fid, page, OnVideosLoaded);
                    break;

                default:
                    MineService.FetchFollowing(_mid, page, OnUsersLoaded);
                    break;
            }
        }

        private void MoreButton_Click(object sender, RoutedEventArgs e)
        {
            if (_loading || !_hasMore)
            {
                return;
            }
            LoadPage(_page + 1);
        }

        private void OnVideosLoaded(List<VideoItem> items, bool hasMore, string error)
        {
            Dispatcher.BeginInvoke(new Action(delegate
            {
                _loading = false;

                int added = 0;
                if (items != null)
                {
                    for (int i = 0; i < items.Count; i++)
                    {
                        _videos.Add(items[i]);
                        // 交给CoverLoader：限流/去重/缓存由它负责
                        CoverLoader.Request(items[i]);
                        added++;
                    }
                }

                _hasMore = hasMore && added > 0;
                Finish(error);
            }));
        }

        private void OnUsersLoaded(List<UserItem> users, bool hasMore, string error)
        {
            Dispatcher.BeginInvoke(new Action(delegate
            {
                _loading = false;

                int added = 0;
                if (users != null)
                {
                    for (int i = 0; i < users.Count; i++)
                    {
                        _users.Add(users[i]);
                        // 头像走AvatarLoader（1:1），不用CoverLoader
                        AvatarLoader.Request(users[i]);
                        added++;
                    }
                }

                _hasMore = hasMore && added > 0;
                Finish(error);
            }));
        }

        private void OnFoldersLoaded(List<FavFolder> folders, string error)
        {
            Dispatcher.BeginInvoke(new Action(delegate
            {
                _loading = false;

                if (folders != null)
                {
                    for (int i = 0; i < folders.Count; i++)
                    {
                        FavFolder folder = folders[i];

                        // 收藏夹不是视频，借VideoItem承载
                        // Fid存在它自己的字段上，封面是夹子里第一个视频的
                        VideoItem item = new VideoItem();
                        item.Fid = folder.Fid;
                        item.Title = folder.Title;
                        item.Pic = folder.Cover;
                        item.Subtitle = folder.Count + " 个视频";
                        item.Badge = folder.IsPrivate ? "私密" : "";
                        _videos.Add(item);

                        if (!string.IsNullOrEmpty(item.CoverUrl))
                        {
                            CoverLoader.Request(item);
                        }
                    }
                }

                // 收藏夹接口一次返回全部，不分页
                _hasMore = false;
                Finish(error);
            }));
        }

        private void Finish(string error)
        {
            if (!string.IsNullOrEmpty(error))
            {
                ShowStatus(error);
            }
            else if (CurrentCount == 0)
            {
                ShowStatus("这里还没有内容");
            }
            else
            {
                StatusText.Visibility = Visibility.Collapsed;
            }

            // 按钮不再显示，翻页由BottomAutoLoader触发
            // 元素留在XAML里，MoreButton_Click还复用它
            MoreButton.Visibility = Visibility.Collapsed;
        }

        private void ResultList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            // 关注模式选中UserItem，没有bvid可跳
            if (_mode == Mode.Following)
            {
                ResultList.SelectedIndex = -1;
                return;
            }

            VideoItem item = ResultList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            // 立刻清选中态，否则返回还高亮
            ResultList.SelectedIndex = -1;

            if (_mode == Mode.Folders)
            {
                if (item.Fid <= 0)
                {
                    return;
                }
                NavigationService.Navigate(new Uri(
                    "/MineListPage.xaml?mode=favvideos&fid=" + item.Fid, UriKind.Relative));
                return;
            }

            if (string.IsNullOrEmpty(item.Bvid))
            {
                return;
            }

            NavigationService.Navigate(new Uri(
                "/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }

        private static long ParseLong(string value)
        {
            long result;
            return long.TryParse(value, out result) ? result : 0;
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusText.Visibility = Visibility.Visible;
        }
    }
}
