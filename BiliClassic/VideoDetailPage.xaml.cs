using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Threading;
using BiliClassic.Api;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Tasks;

namespace BiliClassic
{
    public partial class VideoDetailPage : PhoneApplicationPage
    {
        private const int CoverWidth = 352;
        private const int CoverHeight = 198;

        private string _bvid = "";
        private bool _loading;

        private VideoDetail _detail;

        private readonly ObservableCollection<CommentItem> _comments =
            new ObservableCollection<CommentItem>();

        private int _commentPage = 1;
        private bool _commentHasMore;
        private bool _commentLoading;
        private bool _sendingComment;

        private bool _commentsLoaded;

        private readonly ObservableCollection<VideoItem> _related =
            new ObservableCollection<VideoItem>();

        private bool _relatedLoaded;

        private readonly TextBlock _commentEmpty = new TextBlock();

        private bool _caching;

        private bool _favLoading;

        private bool _favUpdating;

        private List<FavFolderItem> _favOptions = new List<FavFolderItem>();

        public VideoDetailPage()
        {
            InitializeComponent();
            CommentList.ItemsSource = _comments;
            RelatedList.ItemsSource = _related;
            BuildEmptyPlaceholder();
            ThemeHelper.ApplyPage(this);

            BottomAutoLoader.Attach(CommentList, delegate
            {
                MoreCommentButton_Click(null, null);
            });
        }

        private void BuildEmptyPlaceholder()
        {
            _commentEmpty.Text = "暂无评论";
            _commentEmpty.Margin = new Thickness(0);
            _commentEmpty.TextWrapping = TextWrapping.Wrap;
            _commentEmpty.HorizontalAlignment = HorizontalAlignment.Center;
            _commentEmpty.VerticalAlignment = VerticalAlignment.Center;
            _commentEmpty.Visibility = Visibility.Collapsed;

            try
            {
                Style subtle = Application.Current.Resources["PhoneTextSubtleStyle"] as Style;
                if (subtle != null)
                {
                    _commentEmpty.Style = subtle;
                }
            }
            catch (Exception)
            {
            }

            Grid host = CommentList.Parent as Grid;
            if (host != null)
            {
                Grid.SetRow(_commentEmpty, Grid.GetRow(CommentList));
                host.Children.Add(_commentEmpty);
            }
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            string value;
            if (NavigationContext.QueryString.TryGetValue("bvid", out value))
            {
                _bvid = value;
            }

            BvidText.Text = _bvid;

            if (!_loading)
            {
                Load();
            }
        }

        private DispatcherTimer _bvidHoldTimer;

        private void BvidText_Down(object sender, MouseButtonEventArgs e)
        {
            if (_bvidHoldTimer == null)
            {
                _bvidHoldTimer = new DispatcherTimer();
                _bvidHoldTimer.Interval = TimeSpan.FromMilliseconds(650);
                _bvidHoldTimer.Tick += BvidHoldTick;
            }
            _bvidHoldTimer.Stop();
            _bvidHoldTimer.Start();
        }

        private void BvidText_Up(object sender, MouseButtonEventArgs e)
        {
            if (_bvidHoldTimer != null)
            {
                _bvidHoldTimer.Stop();
            }
        }

        private void BvidHoldTick(object sender, EventArgs e)
        {
            _bvidHoldTimer.Stop();

            string text = BvidText.Text;
            if (string.IsNullOrEmpty(text))
            {
                return;
            }

#if WP8
            try
            {
                Clipboard.SetText(text);
            }
            catch (Exception)
            {
            }

            MessageBox.Show("已复制 " + text, "提示", MessageBoxButton.OK);
#else
            MessageBox.Show("复制功能需要WP8及以上系统", "提示", MessageBoxButton.OK);
#endif
        }

        private void CommentCopy_Click(object sender, RoutedEventArgs e)
        {
            Microsoft.Phone.Controls.MenuItem menu =
                sender as Microsoft.Phone.Controls.MenuItem;
            CommentItem item = menu == null ? null : menu.DataContext as CommentItem;
            if (item == null || string.IsNullOrEmpty(item.Message))
            {
                return;
            }

#if WP8
            try
            {
                Clipboard.SetText(item.Message);
            }
            catch (Exception)
            {
            }

            MessageBox.Show("已复制评论", "提示", MessageBoxButton.OK);
#else
            MessageBox.Show("复制功能需要WP8及以上系统", "提示", MessageBoxButton.OK);
#endif
        }

        private void Load()
        {
            if (string.IsNullOrEmpty(_bvid))
            {
                ShowStatus("没有收到 BV 号");
                return;
            }

            _loading = true;
            ShowStatus("正在加载视频详情…");

            VideoDetailService.Fetch(_bvid, delegate(VideoDetail detail)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;
                    Apply(detail);
                }));
            });
        }

        private void Apply(VideoDetail detail)
        {
            if (!string.IsNullOrEmpty(detail.Error))
            {
                ShowStatus(detail.Error);
                return;
            }

            _detail = detail;

            TitleText.Text = detail.Title;
            AuthorText.Text = "UP主：" + detail.Author;

            string stat = "";
            stat = AppendStat(stat, "播放", detail.View);
            stat = AppendStat(stat, "弹幕", detail.Danmaku);
            stat = AppendStat(stat, "点赞", detail.Like);
            stat = AppendStat(stat, "投币", detail.Coin);
            stat = AppendStat(stat, "收藏", detail.Favorite);
            stat = AppendStat(stat, "评论", detail.Reply);
            StatText.Text = stat;

            string pubDate = VideoDetailService.FormatPubDate(detail.PubDate);
            PubDateText.Text = pubDate.Length > 0 ? "发布于 " + pubDate : "";

            DescText.Text = detail.Desc;

            if (detail.Parts.Count > 1)
            {
                PartsHeader.Text = "共 " + detail.Parts.Count + " 段视频";
                PartsBar.Visibility = Visibility.Visible;
                PartsList.ItemsSource = detail.Parts;
                PartsList.Visibility = Visibility.Visible;
            }

            LoadCover(detail.Pic);
            ShowStatus("");
            UpdateCacheButton();

            if (!_commentsLoaded)
            {
                _commentsLoaded = true;
                LoadComments();
            }

            if (!_relatedLoaded)
            {
                _relatedLoaded = true;
                LoadRelated();
            }
        }

        private void LoadCover(string pic)
        {
            string url = VideoItem.BuildThumbUrl(pic, CoverWidth, CoverHeight);
            if (string.IsNullOrEmpty(url))
            {
                return;
            }

            Http.GetBytes(url, Http.Referer, delegate(byte[] bytes, string error)
            {
                if (bytes == null || bytes.Length == 0)
                {
                    return;
                }
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    try
                    {
                        BitmapImage bitmap = new BitmapImage();
                        bitmap.SetSource(new MemoryStream(bytes));
                        CoverImage.Source = bitmap;
                    }
                    catch (Exception)
                    {
                    }
                }));
            });
        }

        private void PlayButton_Click(object sender, RoutedEventArgs e)
        {
            if (_detail == null || _detail.Parts.Count == 0)
            {
                ShowStatus("还没拿到分P信息，稍等一下");
                return;
            }

            int index = PartsList.SelectedIndex;
            PlayPart(index < 0 ? 0 : index);
        }

        private void PartsList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            int index = PartsList.SelectedIndex;
            if (index >= 0)
            {
                UpdateCacheButton();
                PlayPart(index);
            }
        }


        private void UpdateCacheButton()
        {
            if (CacheAppBarButton == null)
            {
                return;
            }
            if (_detail == null || _detail.Parts.Count == 0)
            {
                CacheAppBarButton.Text = "缓存";
                return;
            }

            CacheAppBarButton.Text = OfflineService.AreAllCached(_detail)
                ? "已缓存"
                : "缓存";
        }

        private void CacheAppBarButton_Click(object sender, EventArgs e)
        {
            if (_caching)
            {
                return;
            }
            if (_detail == null || _detail.Parts.Count == 0)
            {
                ShowStatus("还没拿到分P信息，稍等一下");
                return;
            }

            if (_detail.Parts.Count == 1)
            {
                CachePart(_detail.Parts[0]);
                return;
            }

            PartCacheStatus.Text = "";
            PartOverlay.Visibility = Visibility.Visible;
            PartCacheList.ItemsSource = _detail.Parts;
        }

        private void PartCacheList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoPart part = PartCacheList.SelectedItem as VideoPart;
            if (part == null || _caching)
            {
                return;
            }
            PartCacheList.SelectedIndex = -1;
            PartOverlay.Visibility = Visibility.Collapsed;
            CachePart(part);
        }

        private void CacheAllButton_Click(object sender, RoutedEventArgs e)
        {
            if (_caching || _detail == null || _detail.Parts.Count == 0)
            {
                return;
            }
            PartOverlay.Visibility = Visibility.Collapsed;
            CacheAll();
        }

        private void CancelPartButton_Click(object sender, RoutedEventArgs e)
        {
            PartOverlay.Visibility = Visibility.Collapsed;
        }

        private void CachePart(VideoPart part)
        {
            if (part == null || _detail == null)
            {
                return;
            }

            _caching = true;
            try
            {
                if (CacheAppBarButton != null)
                {
                    CacheAppBarButton.IsEnabled = false;
                }
            }
            catch (Exception)
            {
            }
            ShowStatus("正在缓存…");

            OfflineService.Cache(_detail, part,
                delegate(long received, long total)
                {
                    OnCacheProgress(part, received, total);
                },
                delegate(bool ok, string error)
                {
                    Dispatcher.BeginInvoke(new Action(delegate
                    {
                        _caching = false;
                        try { if (CacheAppBarButton != null) CacheAppBarButton.IsEnabled = true; }
                        catch (Exception) { }
                        UpdateCacheButton();
                        ShowStatus(ok && string.IsNullOrEmpty(error) ? "缓存完成" : error);
                    }));
                });
        }

        private void CacheAll()
        {
            _caching = true;
            CacheAppBarButton.IsEnabled = false;
            ShowStatus("正在缓存…");

            OfflineService.CacheAll(_detail, OnCacheProgress, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _caching = false;
                    CacheAppBarButton.IsEnabled = true;
                    UpdateCacheButton();
                    ShowStatus(ok && string.IsNullOrEmpty(error) ? "缓存完成" : error);
                }));
            });
        }

        private void OnCacheProgress(VideoPart part, long received, long total)
        {
            Dispatcher.BeginInvoke(new Action(delegate
            {
                string page = string.IsNullOrEmpty(part.Page) ? "" : "P" + part.Page + " ";
                string text = "正在缓存 " + page + MediaCache.FormatSize(received);
                if (total > 0)
                {
                    text += " / " + MediaCache.FormatSize(total)
                        + "（" + (received * 100 / total) + "%）";
                }
                ShowStatus(text);
            }));
        }

        private void FavoriteAppBarButton_Click(object sender, EventArgs e)
        {
            if (_favLoading)
            {
                return;
            }
            if (_detail == null || string.IsNullOrEmpty(_detail.Aid))
            {
                ShowStatus("还没拿到视频信息，稍等一下");
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                ShowStatus("请先登录");
                return;
            }

            long mid;
            if (!long.TryParse(BiliSession.Mid, out mid) || mid <= 0)
            {
                ShowStatus("登录信息不完整");
                return;
            }

            _favLoading = true;
            FavStatus.Text = "正在加载收藏夹…";
            FavFolderList.ItemsSource = null;
            FavOverlay.Visibility = Visibility.Visible;

            MineService.FetchFavoriteFolders(mid, false, delegate(List<FavFolder> folders, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _favLoading = false;

                    if (folders == null || folders.Count == 0)
                    {
                        FavStatus.Text = string.IsNullOrEmpty(error)
                            ? "还没有收藏夹，请先在网页上创建"
                            : error;
                        return;
                    }

                    _favOptions = new List<FavFolderItem>();
                    for (int i = 0; i < folders.Count; i++)
                    {
                        FavFolderItem option = new FavFolderItem();
                        option.Fid = folders[i].Fid;
                        option.Title = folders[i].Title;
                        option.Count = folders[i].Count;
                        _favOptions.Add(option);
                    }

                    FavStatus.Text = "";
                    FavFolderList.ItemsSource = _favOptions;
                }));
            });
        }

        private void FavFolderList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            FavFolderItem option = FavFolderList.SelectedItem as FavFolderItem;
            if (option == null || _favUpdating || _detail == null)
            {
                return;
            }
            FavFolderList.SelectedIndex = -1;

            _favUpdating = true;
            FavStatus.Text = "正在收藏…";

            FavoriteService.Add(_detail.Aid, _detail.Bvid, option.Fid, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _favUpdating = false;
                    if (ok)
                    {
                        FavOverlay.Visibility = Visibility.Collapsed;
                        ShowStatus("已收藏到「" + option.Title + "」");
                    }
                    else
                    {
                        FavStatus.Text = error;
                    }
                }));
            });
        }

        private void CancelFavButton_Click(object sender, RoutedEventArgs e)
        {
            FavOverlay.Visibility = Visibility.Collapsed;
        }

        private void PlayPart(int index)
        {
            if (_detail == null || index < 0 || index >= _detail.Parts.Count)
            {
                return;
            }

            VideoPart part = _detail.Parts[index];
            if (string.IsNullOrEmpty(part.Cid))
            {
                ShowStatus("这一P没有 cid，无法播放");
                return;
            }

            string url = "/VideoPlayerPage.xaml?bvid=" + _detail.Bvid + "&cid=" + part.Cid;
            NavigationService.Navigate(new Uri(url, UriKind.Relative));
        }

        private void LikeMenuItem_Click(object sender, EventArgs e)
        {
            if (_detail == null || string.IsNullOrEmpty(_detail.Aid))
            {
                ShowStatus("还没拿到视频信息，稍等一下");
                return;
            }

            ShowStatus("正在点赞…");
            InteractionService.Like(_detail.Aid, 1, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    ShowStatus(ok ? "点赞成功" : error);
                }));
            });
        }

        private void CoinMenuItem_Click(object sender, EventArgs e)
        {
            if (_detail == null || string.IsNullOrEmpty(_detail.Aid))
            {
                ShowStatus("还没拿到视频信息，稍等一下");
                return;
            }

            CoinStatus.Text = "";
            CoinOverlay.Visibility = Visibility.Visible;
        }

        private void CoinOne_Click(object sender, RoutedEventArgs e)
        {
            Coin(1);
        }

        private void CoinTwo_Click(object sender, RoutedEventArgs e)
        {
            Coin(2);
        }

        private void CancelCoin_Click(object sender, RoutedEventArgs e)
        {
            CoinOverlay.Visibility = Visibility.Collapsed;
        }

        private void Coin(int multiply)
        {
            CoinOverlay.Visibility = Visibility.Collapsed;

            ShowStatus("正在投币…");
            InteractionService.Coin(_detail.Aid, multiply, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    ShowStatus(ok ? ("已投 " + multiply + " 枚硬币") : error);
                }));
            });
        }

        private void WatchLaterMenuItem_Click(object sender, EventArgs e)
        {
            if (_detail == null || string.IsNullOrEmpty(_detail.Aid))
            {
                ShowStatus("还没拿到视频信息，稍等一下");
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                ShowStatus("请先登录");
                return;
            }

            ShowStatus("正在加入稍后再看…");
            WatchLaterService.Add(_detail.Aid, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    ShowStatus(ok ? "已加入稍后再看" : error);
                }));
            });
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;

            StatusText.Visibility = string.IsNullOrEmpty(message)
                ? Visibility.Collapsed
                : Visibility.Visible;
        }

        private void DetailPivot_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (DetailPivot.SelectedIndex == 1 && !_relatedLoaded)
            {
                _relatedLoaded = true;
                LoadRelated();
            }
            if (DetailPivot.SelectedIndex == 2 && !_commentsLoaded)
            {
                _commentsLoaded = true;
                LoadComments();
            }
        }

        private void LoadRelated()
        {
            if (_detail == null)
            {
                _relatedLoaded = false;
                RelatedStatus.Text = "还没拿到视频信息，稍等一下";
                return;
            }

            RelatedStatus.Text = "正在加载相关视频…";
            RelatedEmpty.Visibility = Visibility.Collapsed;

            RelatedService.Fetch(_detail.Bvid, delegate(RelatedResult result)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    ApplyRelated(result);
                }));
            });
        }

        private void ApplyRelated(RelatedResult result)
        {
            for (int i = 0; i < result.Items.Count; i++)
            {
                _related.Add(result.Items[i]);
                CoverLoader.Request(result.Items[i]);
            }

            if (!string.IsNullOrEmpty(result.Error))
            {
                RelatedStatus.Text = result.Error;
                RelatedEmpty.Visibility = Visibility.Collapsed;
            }
            else if (_related.Count == 0)
            {
                RelatedStatus.Text = "";
                RelatedEmpty.Text = "暂无相关视频";
                RelatedEmpty.Visibility = Visibility.Visible;
            }
            else
            {
                RelatedStatus.Text = "";
                RelatedEmpty.Visibility = Visibility.Collapsed;
            }
        }

        private void RelatedList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = RelatedList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            RelatedList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.Bvid) || item.Bvid == _bvid)
            {
                return;
            }
            NavigationService.Navigate(new Uri("/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }

        private void GoUser(string mid)
        {
            if (string.IsNullOrEmpty(mid))
            {
                return;
            }
            NavigationService.Navigate(new Uri("/UserProfilePage.xaml?mid=" + mid, UriKind.Relative));
        }

        private void AuthorText_Tap(object sender, MouseButtonEventArgs e)
        {
            if (_detail != null)
            {
                GoUser(_detail.AuthorMid);
            }
        }

        private void CommentAvatar_Tap(object sender, MouseButtonEventArgs e)
        {
            FrameworkElement element = sender as FrameworkElement;
            if (element == null)
            {
                return;
            }

            CommentItem item = element.DataContext as CommentItem;
            if (item != null && item.Author != null)
            {
                GoUser(item.Author.Mid);
            }
        }

        private void MoreCommentButton_Click(object sender, RoutedEventArgs e)
        {
            if (_commentLoading || !_commentHasMore)
            {
                return;
            }
            _commentPage++;
            LoadComments();
        }

        private void CommentAppBarButton_Click(object sender, EventArgs e)
        {
            if (_detail == null || string.IsNullOrEmpty(_detail.Aid))
            {
                ShowStatus("还没拿到视频信息，稍等一下");
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                ShowStatus("请先登录");
                return;
            }

            CommentBox.Text = "";
            CommentHint.Text = "";
            CommentOverlay.Visibility = Visibility.Visible;
            CommentBox.Focus();
        }

        private void CancelCommentButton_Click(object sender, RoutedEventArgs e)
        {
            CommentOverlay.Visibility = Visibility.Collapsed;
        }

        private void SendCommentButton_Click(object sender, RoutedEventArgs e)
        {
            SendComment();
        }

        private void CommentBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                SendComment();
            }
        }

        private void SendComment()
        {
            if (_sendingComment)
            {
                return;
            }
            string text = (CommentBox.Text ?? "").Trim();
            if (text.Length == 0)
            {
                CommentHint.Text = "评论不能为空";
                return;
            }
            if (_detail == null || string.IsNullOrEmpty(_detail.Aid))
            {
                CommentHint.Text = "还没拿到视频信息，稍等一下";
                return;
            }

            _sendingComment = true;
            SendCommentButton.IsEnabled = false;
            CommentHint.Text = "正在发送…";

            CommentService.Add(_detail.Aid, text, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _sendingComment = false;
                    SendCommentButton.IsEnabled = true;

                    if (!ok)
                    {
                        CommentHint.Text = error;
                        return;
                    }

                    CommentOverlay.Visibility = Visibility.Collapsed;
                    CommentBox.Text = "";
                    CommentStatus.Text = "评论已发送";
                    DetailPivot.SelectedIndex = 2;
                    ReloadComments();
                }));
            });
        }

        private void ShareAppBarButton_Click(object sender, EventArgs e)
        {
            if (_detail == null)
            {
                ShowStatus("还没拿到视频信息，稍等一下");
                return;
            }

            string url = !string.IsNullOrEmpty(_detail.Bvid)
                ? "https://www.bilibili.com/video/" + _detail.Bvid
                : "https://www.bilibili.com/video/av" + _detail.Aid;

            try
            {
#if WP8
                ShareLinkTask task = new ShareLinkTask();
                task.Title = _detail.Title;
                task.LinkUri = new Uri(url);
                task.Message = string.IsNullOrEmpty(_detail.Bvid) ? url : _detail.Bvid;
                task.Show();
#else
                EmailComposeTask task = new EmailComposeTask();
                task.Subject = _detail.Title;
                task.Body = url;
                task.Show();
#endif
            }
            catch (Exception ex)
            {
                ShowStatus("分享失败：" + ex.Message);
            }
        }

        private void ReloadComments()
        {
            _comments.Clear();
            _commentPage = 1;
            _commentHasMore = false;
            _commentEmpty.Visibility = Visibility.Collapsed;
            LoadComments();
        }

        private void LoadComments()
        {
            if (_detail == null)
            {
                _commentsLoaded = false;
                CommentStatus.Text = "还没拿到视频信息，稍等一下";
                return;
            }

            _commentLoading = true;
            _commentEmpty.Visibility = Visibility.Collapsed;
            if (_comments.Count == 0)
            {
                CommentStatus.Text = "正在加载评论…";
            }

            CommentService.Fetch(_detail.Aid, _commentPage,
                delegate(List<CommentItem> items, bool hasMore, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _commentLoading = false;
                    ApplyComments(items, hasMore, error);
                }));
            });
        }

        private void ApplyComments(List<CommentItem> items, bool hasMore, string error)
        {
            int added = 0;
            for (int i = 0; i < items.Count; i++)
            {
                _comments.Add(items[i]);
                AvatarLoader.Request(items[i].Author);
                added++;
            }

            _commentHasMore = hasMore && added > 0;

            if (!string.IsNullOrEmpty(error) && IsClosed(error))
            {
                CommentStatus.Text = "";
                _commentEmpty.Text = "评论区已关闭";
                _commentEmpty.Visibility = Visibility.Visible;
            }
            else if (!string.IsNullOrEmpty(error))
            {
                CommentStatus.Text = error;

                if (_comments.Count == 0)
                {
                    CommentItem problem = new CommentItem();
                    problem.Author.Name = "加载失败";
                    problem.Message = error;
                    _comments.Add(problem);
                }

                _commentEmpty.Visibility = Visibility.Collapsed;
            }
            else if (_comments.Count == 0)
            {
                CommentStatus.Text = "";
                _commentEmpty.Text = "暂无评论";
                _commentEmpty.Visibility = Visibility.Visible;
            }
            else
            {
                CommentStatus.Text = "";
                _commentEmpty.Visibility = Visibility.Collapsed;
            }

            MoreCommentButton.Visibility = Visibility.Collapsed;
        }

        private static bool IsClosed(string error)
        {
            return error.IndexOf("评论区不可用", StringComparison.Ordinal) >= 0;
        }

        private static string AppendStat(string text, string label, string raw)
        {
            string value = VideoItem.FormatCount(raw);
            if (value.Length == 0)
            {
                return text;
            }
            if (text.Length > 0)
            {
                text += "   ";
            }
            return text + label + " " + value;
        }
    }
}
