using System;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    public sealed class VideoItem : INotifyPropertyChanged
    {
        private BitmapImage _cover;

        public event PropertyChangedEventHandler PropertyChanged;

        public string Bvid { get; set; }

        public string Aid { get; set; }

        public string Cid { get; set; }

        public string Pic { get; set; }

        private const int TitleBudget = 50;

        private string _title = "";

        public string Title
        {
            get { return _title; }
            set { _title = LimitTitle(value); }
        }

        public static string LimitTitle(string title)
        {
            if (string.IsNullOrEmpty(title))
            {
                return "";
            }

            int budget = TitleBudget - 2;

            int used = 0;
            int cut = 0;
            for (int i = 0; i < title.Length; i++)
            {
                int width = title[i] > 0x2E80 ? 2 : 1;
                if (used + width > budget)
                {
                    break;
                }
                used += width;
                cut = i + 1;
            }

            return cut >= title.Length ? title : title.Substring(0, cut) + "…";
        }

        public string Author { get; set; }

        public string View { get; set; }

        public string Danmaku { get; set; }

        public string Subtitle { get; set; }

        public long Fid { get; set; }

        public long SeasonId { get; set; }

        public long Epid { get; set; }

        public long RoomId { get; set; }

        public bool IsBangumi { get; set; }

        public string Badge { get; set; }

        public Visibility BadgeVisibility
        {
            get { return string.IsNullOrEmpty(Badge) ? Visibility.Collapsed : Visibility.Visible; }
        }

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

        public string CoverUrl
        {
            get
            {
                return IsBangumi
                    ? BuildThumbUrl(Pic, 180, 240)
                    : BuildThumbUrl(Pic, 320, 180);
            }
        }

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
