using System.ComponentModel;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    public sealed class UserItem : INotifyPropertyChanged
    {
        private BitmapImage _avatar;

        public event PropertyChangedEventHandler PropertyChanged;

        public string Mid { get; set; }

        public string Name { get; set; }

        public string Sign { get; set; }

        public string SignOneLine
        {
            get { return LimitOneLine(Sign, 26); }
        }

        private static string LimitOneLine(string text, int budget)
        {
            if (string.IsNullOrEmpty(text))
            {
                return "";
            }
            int used = 0;
            int cut = 0;
            for (int i = 0; i < text.Length; i++)
            {
                int width = text[i] > 0x2E80 ? 2 : 1;
                if (used + width > budget)
                {
                    break;
                }
                used += width;
                cut = i + 1;
            }
            return cut >= text.Length ? text : text.Substring(0, cut) + "…";
        }

        public string AvatarUrl { get; set; }

        public BitmapImage Avatar
        {
            get { return _avatar; }
            set
            {
                if (ReferenceEquals(_avatar, value))
                {
                    return;
                }
                _avatar = value;

                PropertyChangedEventHandler handler = PropertyChanged;
                if (handler != null)
                {
                    handler(this, new PropertyChangedEventArgs("Avatar"));
                }
            }
        }
    }
}
