using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    /// <summary>
    /// 视频详情页，导航参数传bvid
    ///
    /// 两个tab：详情（封面/标题/UP主/统计/简介/分P）和评论
    /// </summary>
    public partial class VideoDetailPage : PhoneApplicationPage
    {
        private const int CoverWidth = 360;
        private const int CoverHeight = 203;   // B站封面是 16:9

        private string _bvid = "";
        private bool _loading;

        /// <summary>最近一次详情，播放按钮取分P的cid</summary>
        private VideoDetail _detail;

        private readonly ObservableCollection<CommentItem> _comments =
            new ObservableCollection<CommentItem>();

        private int _commentPage = 1;
        private bool _commentHasMore;
        private bool _commentLoading;

        /// <summary>评论首次切过去才拉</summary>
        private bool _commentsLoaded;

        /// <summary>没有评论时的占位，代码创建，不走XAML的x:Name</summary>
        private readonly TextBlock _commentEmpty = new TextBlock();

        public VideoDetailPage()
        {
            InitializeComponent();
            CommentList.ItemsSource = _comments;
            BuildEmptyPlaceholder();

            // 评论也滚到底自动翻页
            BottomAutoLoader.Attach(CommentList, delegate
            {
                MoreCommentButton_Click(null, null);
            });
        }

        /// <summary>把占位文本叠在评论列表那一格，居中</summary>
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
                // 主题样式取不到就用默认字号，不影响显示
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
            // 转场动画交给Toolkit的TransitionFrame
            // 见App.xaml.cs与本页XAML的TransitionService

            string value;
            if (NavigationContext.QueryString.TryGetValue("bvid", out value))
            {
                _bvid = value;
            }

            if (!_loading)
            {
                Load();
            }
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
                // 回调不在UI线程
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

            // 逐项拼统计，缺的字段跳过
            // FormatCount对空值返回空串
            string stat = "";
            stat = AppendStat(stat, "播放", detail.View);
            stat = AppendStat(stat, "弹幕", detail.Danmaku);
            stat = AppendStat(stat, "点赞", detail.Like);
            stat = AppendStat(stat, "投币", detail.Coin);
            stat = AppendStat(stat, "收藏", detail.Favorite);
            stat = AppendStat(stat, "评论", detail.Reply);
            stat = AppendStat(stat, "时长", VideoPart.FormatDuration(detail.Duration));
            StatText.Text = stat;

            string pubDate = VideoDetailService.FormatPubDate(detail.PubDate);
            PubDateText.Text = pubDate.Length > 0 ? "发布于 " + pubDate : "";

            DescText.Text = detail.Desc;

            // 单P不占列表
            if (detail.Parts.Count > 1)
            {
                PartsHeader.Text = "分P（共 " + detail.Parts.Count + " 个）";
                PartsHeader.Visibility = Visibility.Visible;
                PartsList.ItemsSource = detail.Parts;
                PartsList.Visibility = Visibility.Visible;
            }

            LoadCover(detail.Pic);
            ShowStatus("已加载  " + detail.Bvid);

            // 详情一回来就拉评论，不等切tab
            // 原来只挂在Pivot的SelectionChanged上，事件不来就什么都不发生
            if (!_commentsLoaded)
            {
                _commentsLoaded = true;
                LoadComments();
            }
        }

        /// <summary>
        /// 封面单独下，不复用CoverLoader
        /// 详情页只有一张图，直接下即可
        /// </summary>
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

            // 没选过就播第一个
            int index = PartsList.SelectedIndex;
            PlayPart(index < 0 ? 0 : index);
        }

        private void PartsList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            // 点分P就直接播这一P
            // 不清选中态：「播放视频」按钮跟着选中分P走
            int index = PartsList.SelectedIndex;
            if (index >= 0)
            {
                PlayPart(index);
            }
        }

        /// <summary>
        /// 跳到播放页，cid随bvid一起传
        /// 播放页就不用再查一次详情
        /// </summary>
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

            // 只传bvid和cid，两者只含字母数字，拼URI安全
            // 标题转义成%XX会让Navigate抛IndexOutOfRangeException
            // 所以标题不走导航参数
            string url = "/VideoPlayerPage.xaml?bvid=" + _detail.Bvid + "&cid=" + part.Cid;
            NavigationService.Navigate(new Uri(url, UriKind.Relative));
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
        }

        /// <summary>切到评论tab时才拉</summary>
        private void DetailPivot_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (DetailPivot.SelectedIndex == 1 && !_commentsLoaded)
            {
                _commentsLoaded = true;
                LoadComments();
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

        private void LoadComments()
        {
            if (_detail == null)
            {
                // 详情还没回来，撤掉标记，下次切过来再试
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
                // 回调不在UI线程
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
                // 评论区关了是服务端的正常状态，不是加载失败
                CommentStatus.Text = "";
                _commentEmpty.Text = "评论区已关闭";
                _commentEmpty.Visibility = Visibility.Visible;
            }
            else if (!string.IsNullOrEmpty(error))
            {
                CommentStatus.Text = error;

                // 一条都没有时把原因当卡片摆出来，状态行太小容易被忽略
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
                // 真的没有评论：占位文字居中，状态行不重复
                CommentStatus.Text = "";
                _commentEmpty.Text = "暂无评论";
                _commentEmpty.Visibility = Visibility.Visible;
            }
            else
            {
                CommentStatus.Text = "";
                _commentEmpty.Visibility = Visibility.Collapsed;
            }

            // 按钮不显示，翻页由滚动触发
            MoreCommentButton.Visibility = Visibility.Collapsed;
        }

        /// <summary>评论区关闭这类错误码按正常状态处理</summary>
        private static bool IsClosed(string error)
        {
            return error.IndexOf("评论区不可用", StringComparison.Ordinal) >= 0;
        }

        /// <summary>拼统计项，值为空则整项跳过</summary>
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
