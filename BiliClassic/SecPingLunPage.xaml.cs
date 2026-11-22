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
    public partial class SecPingLunPage : PhoneApplicationPage
    {
        public SecPingLunPage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
            PingLunGrid.DataContext = app._sec_comment_temp;
            CommentList.ItemsSource = _comments;

            BottomAutoLoader.Attach(CommentList, delegate
            {
                MoreCommentButton_Click(null, null);
            });
        }

        App app = Application.Current as App;
        private bool _loading = false;
        private bool _loaded = false;
        private bool _hasMore = true;
        private string _oid = "";
        private string _rpid = "";
        private int _commentPage = 1;
        private ObservableCollection<Sec_CommentItem> _comments =
            new ObservableCollection<Sec_CommentItem>();

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            if (!_loaded)
            {
                if (!_loading)
                {
                    Load();
                }
                _loaded = true;
            }
        }

        private void Load()
        {
            _oid = app._sec_comment_temp.Oid;
            _rpid = app._sec_comment_temp.Rpid;
            if (string.IsNullOrEmpty(_oid) && string.IsNullOrEmpty(_rpid))
            {
                MessageBox.Show("Rpid或Oid为空！");
                return;
            }
            _loading = true;
            //ShowStatus("正在加载视频详情…");

            //System.Diagnostics.Debug.WriteLine("AAA"+_oid + " " + _rpid);
            CommentService.SecFetch(_oid, _rpid, _commentPage,
                delegate(List<Sec_CommentItem> items, bool hasMore, string error)
                {
                    Dispatcher.BeginInvoke(new Action(delegate
                    {
                        System.Diagnostics.Debug.WriteLine(error);
                        _loading = false;
                        ApplyComments(items, hasMore, error);
                    }));
                });
             
        }

        private void ApplyComments(List<Sec_CommentItem> items, bool hasMore, string error)
        {
            System.Diagnostics.Debug.WriteLine("EEE" + _oid + " " + _rpid);
            int added = 0;
            for (int i = 0; i < items.Count; i++)
            {
                _comments.Add(items[i]);
                AvatarLoader.Request(items[i].Author);
                added++;
            }

            _hasMore = hasMore && added > 0;


            if (!string.IsNullOrEmpty(error) && IsClosed(error))
            {
                CommentStatus.Text = "评论区已关闭";
                //_commentEmpty.Text = "评论区已关闭";
                //_commentEmpty.Visibility = Visibility.Visible;
            }
            else if (!string.IsNullOrEmpty(error))
            {
                CommentStatus.Text = error;

                if (_comments.Count == 0)
                {
                    Sec_CommentItem problem = new Sec_CommentItem();
                    problem.Author.Name = "加载失败";
                    problem.Message = error;
                    _comments.Add(problem);
                }

                //_commentEmpty.Visibility = Visibility.Collapsed;
            }
            else if (_comments.Count == 0)
            {
                CommentStatus.Text = "暂无评论";
                //_commentEmpty.Text = "暂无评论";
                //_commentEmpty.Visibility = Visibility.Visible;
            }
            else
            {
                CommentStatus.Text = "";
                //_commentEmpty.Visibility = Visibility.Collapsed;
            }

            MoreCommentButton.Visibility = Visibility.Collapsed;
        }

        private static bool IsClosed(string error)
        {
            return error.IndexOf("评论区不可用", StringComparison.Ordinal) >= 0;
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

        private void CommentPicture_Click(object sender, RoutedEventArgs e)
        {
            FrameworkElement element = sender as FrameworkElement;
            PictureItem picture = element == null ? null : element.DataContext as PictureItem;
            if (picture == null || picture.Group == null || picture.Group.Count == 0)
            {
                return;
            }
            ImageViewerPage.Show(picture.Group, picture.Group.IndexOf(picture));
        }

        private void GoUser(string mid)
        {
            if (string.IsNullOrEmpty(mid))
            {
                return;
            }
            NavigationService.Navigate(new Uri("/UserProfilePage.xaml?mid=" + mid, UriKind.Relative));
        }

        private void MoreCommentButton_Click(object sender, RoutedEventArgs e)
        {
            
            if (_loading || !_hasMore)
            {
                return;
            }
            _commentPage++;
            Load();
             
        }

        private void Border_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            FrameworkElement element = sender as FrameworkElement;
            if (element == null)
            {
                return;
            }

            Sec_CommentItem item = element.DataContext as Sec_CommentItem;
            if (item != null && item.Author != null)
            {
                GoUser(item.Author.Mid);
            }
        }
    }
}