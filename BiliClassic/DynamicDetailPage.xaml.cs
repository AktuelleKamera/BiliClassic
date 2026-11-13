using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Tasks;

namespace BiliClassic
{
    public partial class DynamicDetailPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<CommentItem> _comments =
            new ObservableCollection<CommentItem>();

        private DynamicItem _item;
        private string _id = "";
        private bool _loaded;

        private bool _commentLoading;
        private int _commentPage;
        private bool _commentHasMore;

        public DynamicDetailPage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
            CommentList.ItemsSource = _comments;
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            string value;
            if (NavigationContext.QueryString.TryGetValue("id", out value))
            {
                _id = value;
            }

            if (_loaded)
            {
                return;
            }
            _loaded = true;
            LoadDetail();
        }

        private void LoadDetail()
        {
            StatusText.Text = "正在加载动态…";
            DynamicService.FetchDetail(_id, delegate(DynamicItem item, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (item == null)
                    {
                        StatusText.Text = string.IsNullOrEmpty(error) ? "加载失败" : error;
                        return;
                    }

                    _item = item;
                    DataContext = item;
                    RequestImages(item);

                    _commentPage = 0;
                    _commentHasMore = false;
                    _comments.Clear();
                    MoreCommentButton.Visibility = Visibility.Collapsed;
                    StatusText.Text = "";
                    LoadComments(1);
                }));
            });
        }

        private void LoadComments(int page)
        {
            if (_commentLoading || _item == null)
            {
                return;
            }

            _commentLoading = true;
            MoreCommentButton.IsEnabled = false;
            MoreCommentButton.Content = "加载中…";

            CommentService.Fetch(_item.CommentId, page, _item.CommentType,
                delegate(List<CommentItem> items, bool hasMore, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _commentLoading = false;

                    if (page <= 1)
                    {
                        _comments.Clear();
                    }

                    if (items != null)
                    {
                        for (int i = 0; i < items.Count; i++)
                        {
                            _comments.Add(items[i]);
                            AvatarLoader.Request(items[i].Author);
                            RequestCommentImages(items[i]);
                        }
                    }

                    _commentPage = page;
                    _commentHasMore = hasMore;
                    MoreCommentButton.Content = "加载更多评论";
                    MoreCommentButton.IsEnabled = true;
                    MoreCommentButton.Visibility = hasMore
                        ? Visibility.Visible : Visibility.Collapsed;

                    if (!string.IsNullOrEmpty(error))
                    {
                        StatusText.Text = error;
                    }
                    else if (_comments.Count == 0)
                    {
                        StatusText.Text = "还没有评论";
                    }
                    else
                    {
                        StatusText.Text = "";
                    }
                }));
            });
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

        private void RequestCommentImages(CommentItem comment)
        {
            if (comment == null)
            {
                return;
            }

            for (int i = 0; i < comment.Thumbs.Count; i++)
            {
                PictureItem picture = comment.Thumbs[i];
                ImageLoader.Load(VideoItem.BuildThumbUrl(picture.Url, 240, 240),
                    delegate(BitmapImage bitmap) { picture.Image = bitmap; });
            }
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

        private void Picture_Click(object sender, RoutedEventArgs e)
        {
            FrameworkElement element = sender as FrameworkElement;
            PictureItem picture = element == null ? null : element.DataContext as PictureItem;
            if (picture == null || picture.Group == null || picture.Group.Count == 0)
            {
                return;
            }
            ImageViewerPage.Show(picture.Group, picture.Group.IndexOf(picture));
        }

        private void Card_Click(object sender, RoutedEventArgs e)
        {
            if (_item == null)
            {
                return;
            }

            if (!string.IsNullOrEmpty(_item.CardBvid))
            {
                NavigationService.Navigate(new Uri(
                    "/VideoDetailPage.xaml?bvid=" + _item.CardBvid, UriKind.Relative));
                return;
            }
            if (!string.IsNullOrEmpty(_item.CardAid))
            {
                OpenWeb("https://www.bilibili.com/video/av" + _item.CardAid);
                return;
            }
            if (!string.IsNullOrEmpty(_item.CardEpid))
            {
                NavigationService.Navigate(new Uri(
                    "/BangumiDetailPage.xaml?ep=" + _item.CardEpid, UriKind.Relative));
                return;
            }
            if (!string.IsNullOrEmpty(_item.CardRoomId))
            {
                OpenWeb("https://live.bilibili.com/" + _item.CardRoomId);
                return;
            }
            if (!string.IsNullOrEmpty(_item.CardArticleId))
            {
                NavigationService.Navigate(new Uri(
                    "/ArticlePage.xaml?id=" + _item.CardArticleId, UriKind.Relative));
            }
        }

        private void Forward_Click(object sender, RoutedEventArgs e)
        {
            if (_item == null || _item.Forward == null
                || string.IsNullOrEmpty(_item.Forward.DynamicId))
            {
                return;
            }

            NavigationService.Navigate(new Uri(
                "/DynamicDetailPage.xaml?id=" + _item.Forward.DynamicId, UriKind.Relative));
        }

        private void MoreCommentButton_Click(object sender, RoutedEventArgs e)
        {
            if (_commentLoading || !_commentHasMore)
            {
                return;
            }
            LoadComments(_commentPage + 1);
        }

        private void Refresh_Click(object sender, EventArgs e)
        {
            LoadDetail();
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
    }
}
