using System.ComponentModel;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    /// <summary>
    /// 用户条目
    /// </summary>
    public sealed class UserItem : INotifyPropertyChanged
    {
        private BitmapImage _avatar;

        public event PropertyChangedEventHandler PropertyChanged;

        /// <summary>用户mid</summary>
        public string Mid { get; set; }

        public string Name { get; set; }

        /// <summary>个性签名</summary>
        public string Sign { get; set; }

        /// <summary>头像原始地址</summary>
        public string AvatarUrl { get; set; }

        /// <summary>头像位图，null为未加载</summary>
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

                // 异步加载，须通知绑定刷新
                PropertyChangedEventHandler handler = PropertyChanged;
                if (handler != null)
                {
                    handler(this, new PropertyChangedEventArgs("Avatar"));
                }
            }
        }
    }
}
