using System;
using System.Windows;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class ArticlePage : PhoneApplicationPage
    {
        private bool _loaded;
        private string _id = "";

        public ArticlePage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
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

            ArticleService.Fetch(_id, delegate(ArticleInfo article, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (article == null)
                    {
                        StatusText.Text = string.IsNullOrEmpty(error) ? "加载失败" : error;
                        return;
                    }

                    StatusText.Text = "";
                    TitleText.Text = article.Title;
                    MetaText.Text = article.AuthorName
                        + (article.Ctime > 0 ? "  " + FormatTime(article.Ctime) : "");
                    StatsText.Text = "阅读 " + VideoItem.FormatCount(article.View.ToString())
                        + "   点赞 " + VideoItem.FormatCount(article.Like.ToString())
                        + "   收藏 " + VideoItem.FormatCount(article.Favorite.ToString())
                        + "   评论 " + VideoItem.FormatCount(article.Reply.ToString());
                    ContentText.Text = ArticleService.HtmlToText(article.ContentHtml);
                }));
            });
        }

        private static string FormatTime(long seconds)
        {
            try
            {
                DateTime time = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)
                    .AddSeconds(seconds).ToLocalTime();
                return time.ToString("yyyy-MM-dd");
            }
            catch (Exception)
            {
                return "";
            }
        }
    }
}
