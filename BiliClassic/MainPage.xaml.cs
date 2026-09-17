using System;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    /// <summary>
    /// 首页：推荐视频列表
    /// 列表项复用VideoItem，封面和卡片模板与搜索页共用
    /// </summary>
    public partial class MainPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items = new ObservableCollection<VideoItem>();

        private int _page = 1;
        private bool _hasMore;
        private bool _loading;

        /// <summary>「我的」名片首次切过去才拉</summary>
        private bool _mineLoaded;

        /// <summary>更新检查每次启动只做一次</summary>
        private bool _updateChecked;

        public MainPage()
        {
            InitializeComponent();
            RecommendList.ItemsSource = _items;
            Loaded += OnLoaded;

            // 滚到底部自动翻页，回调会连发，靠_loading挡重入
            BottomAutoLoader.Attach(RecommendList, delegate
            {
                MoreButton_Click(null, null);
            });
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            // 转场交给Toolkit的TransitionFrame，见App.xaml.cs
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            // 只在首次进页面加载，返回时保留已有列表
            if (_items.Count == 0 && !_loading)
            {
                Refresh();
                CheckUpdate();
            }
        }

        /// <summary>
        /// 启动时检查更新，静默
        /// 关掉自动检查就不查
        /// </summary>
        private void CheckUpdate()
        {
            if (_updateChecked || !AppSettings.AutoCheckUpdate)
            {
                return;
            }
            _updateChecked = true;

            UpdateChecker.Check(true, null);
        }

        /// <summary>应用栏放大镜：进搜索页</summary>
        private void SearchBarButton_Click(object sender, EventArgs e)
        {
            NavigationService.Navigate(new Uri("/SearchPage.xaml", UriKind.Relative));
        }

        /// <summary>应用栏菜单：进登录页</summary>
        private void LoginMenuItem_Click(object sender, EventArgs e)
        {
            NavigationService.Navigate(new Uri("/LoginPage.xaml", UriKind.Relative));
        }

        /// <summary>应用栏菜单：进设置页</summary>
        private void SettingsMenuItem_Click(object sender, EventArgs e)
        {
            NavigationService.Navigate(new Uri("/SettingsPage.xaml", UriKind.Relative));
        }

        /// <summary>应用栏菜单：进关于页</summary>
        private void AboutMenuItem_Click(object sender, EventArgs e)
        {
            NavigationService.Navigate(new Uri("/AboutPage.xaml", UriKind.Relative));
        }

        /// <summary>切到「我的」才拉名片，省一个请求</summary>
        private void HomePivot_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (HomePivot.SelectedIndex == 1 && !_mineLoaded)
            {
                _mineLoaded = true;
                LoadMineCard();
            }
        }

        private void LoadMineCard()
        {
            MineStatus.Text = "正在加载…";

            MineService.FetchCard(delegate(MineCard card, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!card.LoggedIn)
                    {
                        MineName.Text = "未登录";
                        MineLevel.Text = "";
                        MineSign.Text = "在应用栏菜单的「登录」里登录后，这里会显示你的信息喵~";
                        MineStatus.Text = "";
                        return;
                    }

                    MineName.Text = card.Name;
                    MineLevel.Text = "Lv" + card.Level
                        + "   粉丝 " + VideoItem.FormatCount(card.Fans.ToString());
                    MineSign.Text = card.Sign;
                    MineStatus.Text = "";

                    if (!string.IsNullOrEmpty(card.Face))
                    {
                        // 头像必须走AvatarLoader，自己带UA和Cookie下
                        // 直接给Image一个URL会被防盗链挡掉
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

        private void FollowingButton_Click(object sender, RoutedEventArgs e)
        {
            GoMine("follow");
        }

        private void HistoryButton_Click(object sender, RoutedEventArgs e)
        {
            GoMine("history");
        }

        private void FavoriteButton_Click(object sender, RoutedEventArgs e)
        {
            GoMine("fav");
        }

        private void GoMine(string mode)
        {
            NavigationService.Navigate(new Uri(
                "/MineListPage.xaml?mode=" + mode, UriKind.Relative));
        }

        /// <summary>
        /// 应用栏菜单：刷新
        /// 按当前tab决定刷什么，推荐重拉列表，我的重拉名片
        /// 手动刷新不走_mineLoaded，那个标记只管首次自动加载
        /// </summary>
        private void RefreshMenuItem_Click(object sender, EventArgs e)
        {
            if (HomePivot.SelectedIndex == 1)
            {
                LoadMineCard();
                return;
            }

            Refresh();
        }

        private void MoreButton_Click(object sender, RoutedEventArgs e)
        {
            if (_loading || !_hasMore)
            {
                return;
            }
            _page++;
            Fetch();
        }

        private void Refresh()
        {
            LoadPage(1);
        }

        /// <summary>
        /// 翻页：清空后加载第page页
        /// 加载更多是追加，底部箭头是替换，共用_page
        /// </summary>
        private void LoadPage(int page)
        {
            if (_loading)
            {
                return;
            }

            _page = page < 1 ? 1 : page;
            _hasMore = false;
            _items.Clear();
            MoreButton.Visibility = Visibility.Collapsed;
            Fetch();
        }

        /// <summary>
        /// 应用栏左箭头：切到「推荐」
        /// 列表翻页已交给滚动自动加载，箭头改成tab切换
        /// </summary>
        private void PrevPage_Click(object sender, EventArgs e)
        {
            HomePivot.SelectedIndex = 0;
        }

        /// <summary>应用栏右箭头：切到「我的」</summary>
        private void NextPage_Click(object sender, EventArgs e)
        {
            HomePivot.SelectedIndex = 1;
        }

        private void Fetch()
        {
            _loading = true;
            MoreButton.IsEnabled = false;
            ShowStatus("正在加载推荐…");

            int page = _page;
            RecommendService.Fetch(page, delegate(RecommendResult result)
            {
                // 回调不在UI线程，动集合前必须切回
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;
                    MoreButton.IsEnabled = true;
                    ApplyResult(result);
                }));
            });
        }

        private void ApplyResult(RecommendResult result)
        {
            int added = 0;
            for (int i = 0; i < result.Items.Count; i++)
            {
                VideoItem item = result.Items[i];
                if (ContainsBvid(item.Bvid))
                {
                    continue;
                }
                // 进集合之后再改Title不会刷新，绑定只读一次
                item.Title = LimitTitle(item.Title);
                _items.Add(item);
                added++;

                // CoverLoader负责限流去重和内存缓存
                CoverLoader.Request(item);
            }

            _hasMore = result.HasMore && added > 0;

            if (!string.IsNullOrEmpty(result.Error))
            {
                ShowStatus(result.Error);
            }
            else if (_items.Count == 0)
            {
                ShowStatus("没有拿到推荐内容");
            }
            else
            {
                StatusText.Visibility = Visibility.Collapsed;
            }

            // 按钮不显示，翻页由滚动触发，元素留着给MoreButton_Click复用
            MoreButton.Visibility = Visibility.Collapsed;
        }

        /// <summary>
        /// 标题最多两行，超出的截断加省略号
        /// WP7的TextBlock没有TextTrimming，换行文本只能自己截
        /// </summary>
        private static string LimitTitle(string title)
        {
            if (string.IsNullOrEmpty(title))
            {
                return "";
            }

            // 文字列宽约332px，字号20，按"10px一个单位"算两行是66
            const int Budget = 64;

            int used = 0;
            int cut = 0;
            for (int i = 0; i < title.Length; i++)
            {
                // 中日韩按全角算，宽度是拉丁字符的两倍
                int width = title[i] > 0x2E80 ? 2 : 1;
                if (used + width > Budget)
                {
                    break;
                }
                used += width;
                cut = i + 1;
            }

            return cut >= title.Length ? title : title.Substring(0, cut) + "…";
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

        /// <summary>点列表项进详情</summary>
        private void RecommendList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = RecommendList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            // 立刻清选中态，否则返回时还高亮
            RecommendList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.Bvid))
            {
                return;
            }
            NavigationService.Navigate(new Uri("/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusText.Visibility = Visibility.Visible;
        }
    }
}
