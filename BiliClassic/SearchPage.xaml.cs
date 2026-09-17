using System;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    /// <summary>
    /// 搜索页：上方结果列表，底部一行搜索框（拇指好够到）
    ///
    /// 满一页（20条）且跨页去重后仍有新增，才算还有下一页
    /// </summary>
    public partial class SearchPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items = new ObservableCollection<VideoItem>();

        private string _keyword = "";
        private int _page = 1;
        private bool _hasMore;
        private bool _loading;

        /// <summary>上次搜索时刻，防连点</summary>
        private DateTime _lastSearchAt = DateTime.MinValue;

        public SearchPage()
        {
            InitializeComponent();
            ResultList.ItemsSource = _items;

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
            // 转场动画交给Toolkit的TransitionFrame
            // 不再手动播动画
        }

        private void SearchButton_Click(object sender, RoutedEventArgs e)
        {
            StartNewSearch();
        }

        private void KeywordBox_KeyDown(object sender, KeyEventArgs e)
        {
            // 软键盘回车直接搜索
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

            // 输入BV/AV号直接进详情，不当关键词搜
            if (TryOpenById(keyword))
            {
                return;
            }

            // 防连点：_loading只覆盖请求在途
            // 键盘弹起收起会让页面位移，一次点击可能落两次
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

        /// <summary>
        /// BV/AV号直接进详情
        /// 纯数字不算AV号，照常搜索
        /// 只在提交时判断，边打字边跳会半路跳走
        /// </summary>
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

            // 只有新搜索才禁用搜索按钮
            // 翻页共用此方法，但和搜索按钮无关
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

            SearchService.Search(keyword, page, delegate(SearchResult result)
            {
                // 回调不在UI线程，切回UI线程再动集合
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

            // 交给CoverLoader：限流/去重/缓存由它负责
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

            // 按钮不再显示，翻页由BottomAutoLoader触发
            MoreButton.Visibility = Visibility.Collapsed;
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

        /// <summary>点结果进视频详情</summary>
        private void ResultList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = ResultList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            // 立刻清选中态，否则返回还高亮
            ResultList.SelectedIndex = -1;

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

        /// <summary>
        /// 翻页：清空后加载第page页
        /// 「加载更多」是追加，底部箭头是替换
        /// 未搜索过（_keyword为空）时箭头不生效
        /// </summary>
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

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusText.Visibility = Visibility.Visible;
        }
    }
}
