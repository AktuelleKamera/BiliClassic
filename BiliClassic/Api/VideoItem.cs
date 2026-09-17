using System;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    /// <summary>视频条目，各接口统一映射</summary>
    public sealed class VideoItem : INotifyPropertyChanged
    {
        private BitmapImage _cover;

        public event PropertyChangedEventHandler PropertyChanged;

        /// <summary>BV号，打开详情/播放时用</summary>
        public string Bvid { get; set; }

        /// <summary>封面原始地址，协议相对已补https:</summary>
        public string Pic { get; set; }

        /// <summary>标题，已去掉高亮标签</summary>
        public string Title { get; set; }

        /// <summary>UP主</summary>
        public string Author { get; set; }

        /// <summary>播放量，play/stat.view</summary>
        public string View { get; set; }

        /// <summary>弹幕数，video_review/stat.danmaku</summary>
        public string Danmaku { get; set; }

        /// <summary>第二行自定义文案</summary>
        public string Subtitle { get; set; }

        /// <summary>收藏夹ID，非收藏夹为0</summary>
        public long Fid { get; set; }

        /// <summary>封面右上角角标，空串不显示</summary>
        public string Badge { get; set; }

        /// <summary>角标显隐，供XAML直接绑</summary>
        public Visibility BadgeVisibility
        {
            get { return string.IsNullOrEmpty(Badge) ? Visibility.Collapsed : Visibility.Visible; }
        }

        /// <summary>封面位图，null为未加载</summary>
        public BitmapImage Cover
        {
            get { return _cover; }
            set
            {
                if (ReferenceEquals(_cover, value))
                {
                    return;
                }
                _cover = value;
                PropertyChangedEventHandler handler = PropertyChanged;
                if (handler != null)
                {
                    handler(this, new PropertyChangedEventArgs("Cover"));
                }
            }
        }

        /// <summary>CoverLoader用的缩略图地址</summary>
        public string CoverUrl
        {
            get { return BuildThumbUrl(Pic, 240, 150); }
        }

        /// <summary>
        /// 卡片第二行：显示播放/弹幕计数
        /// 两者都空时回退UP主
        /// </summary>
        public string InfoLine
        {
            get
            {
                if (!string.IsNullOrEmpty(Subtitle))
                {
                    return Subtitle;
                }

                bool noView = string.IsNullOrEmpty(View);
                bool noDanmaku = string.IsNullOrEmpty(Danmaku);
                if (noView && noDanmaku)
                {
                    return Author ?? "";
                }

                string text = "";
                if (!noView)
                {
                    text = FormatCount(View) + " 播放";
                }
                if (!noDanmaku)
                {
                    if (text.Length > 0)
                    {
                        text += "   ";
                    }
                    text += FormatCount(Danmaku) + " 弹幕";
                }
                return text;
            }
        }

        /// <summary>
        /// 数字缩写：万以下原样
        /// 万以上保留一位小数加万
        /// </summary>
        public static string FormatCount(string raw)
        {
            long n;
            if (!long.TryParse(raw, out n))
            {
                return raw ?? "";
            }
            if (n < 10000)
            {
                return n.ToString();
            }
            return ((double)(n / 1000) / 10.0).ToString("0.0") + "万";
        }

        /// <summary>
        /// CDN支持地址后加@宽w_高h_1c.jpg取缩小图
        /// 只处理hdslb.com的.jpg
        /// </summary>
        public static string BuildThumbUrl(string url, int width, int height)
        {
            if (string.IsNullOrEmpty(url))
            {
                return "";
            }
            if (url.IndexOf("hdslb.com") < 0)
            {
                return url;
            }
            string lower = url.ToLower();
            if (!lower.EndsWith(".jpg") && !lower.EndsWith(".jpeg"))
            {
                return url;
            }
            if (url.IndexOf('?') >= 0)
            {
                return url;
            }
            return url + "@" + width + "w_" + height + "h_1c.jpg";
        }
    }
}
